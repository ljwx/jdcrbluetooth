package com.jdcr.jdcrble.core

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.annotation.RequiresPermission
import com.jdcr.jdcrbase.coroutine.JdcrSafeCoroutineScope
import com.jdcr.jdcrble.config.JdcrBleConfig
import com.jdcr.jdcrble.config.JdcrBleConnectConfig
import com.jdcr.jdcrble.config.JdcrBleScanConfig
import com.jdcr.jdcrble.core.communicator.JdcrBleCommunicatorAction
import com.jdcr.jdcrble.core.communicator.JdcrBleCommunicatorActionResult
import com.jdcr.jdcrble.core.communicator.JdcrBleCommunicatorImpl
import com.jdcr.jdcrble.core.communicator.NotificationData
import com.jdcr.jdcrble.core.connector.JdcrBleConnector
import com.jdcr.jdcrble.core.connector.JdcrBleConnectorImpl
import com.jdcr.jdcrble.core.scanner.JdcrBleScanner
import com.jdcr.jdcrble.core.scanner.JdcrBleScannerImpl
import com.jdcr.jdcrble.core.scanner.ScanResultWrapper
import com.jdcr.jdcrble.data.JdcrBleServiceInfo
import com.jdcr.jdcrble.exception.JdcrBleAvailableException
import com.jdcr.jdcrble.state.JdcrBleConnectState
import com.jdcr.jdcrble.state.JdcrBleScanResult
import com.jdcr.jdcrble.util.JdcrBleLog
import com.jdcr.jdcrble.util.JdcrBlePermissionUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class JdcrBleCore(private val context: Context, private val config: JdcrBleConfig) : JdcrBle {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, e ->
        JdcrBleLog.e("蓝牙协程收到异常", e)
    }
    private val coroutine = JdcrSafeCoroutineScope(Dispatchers.IO, tag = "jdcrBle") {
        JdcrBleLog.e("蓝牙库协程发生异常", it)
    }

    private var scanner: JdcrBleScannerImpl? = null
    private val communicator by lazy {
        JdcrBleCommunicatorImpl(
            context,
            config.communicate,
            coroutine
        )
    }
    private var connector: JdcrBleConnectorImpl? = null

    private fun initAdapter(): BluetoothAdapter? {
        if (bluetoothAdapter == null) {
            synchronized(this) {
                if (bluetoothAdapter == null) {
                    bluetoothManager =
                        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = bluetoothManager?.adapter
                    JdcrBleLog.i("初始化adapter:$adapter")
                    bluetoothAdapter = adapter
                    return bluetoothAdapter
                }
            }
        }
        return bluetoothAdapter
    }

    private fun createScanner(): JdcrBleScanner? {
        val adapter = initAdapter() ?: return null
        if (scanner == null) {
            synchronized(this) {
                if (scanner == null) {
                    val bluetoothLeScanner = adapter.bluetoothLeScanner ?: return null
                    scanner = JdcrBleScannerImpl(
                        context.applicationContext,
                        bluetoothLeScanner,
                        coroutine,
                    )
                    JdcrBleLog.i("初始化scanner:$scanner")
                }
            }
        }
        return scanner
    }

    private fun createConnector(): JdcrBleConnector? {
        val adapter = initAdapter() ?: return null
        if (connector == null) {
            synchronized(this) {
                if (connector == null) {
                    connector = JdcrBleConnectorImpl(
                        context.applicationContext,
                        adapter,
                        config,
                        communicator,
                        coroutine,
                    )
                    JdcrBleLog.i("初始化connector:$connector")
                }
            }
        }
        return connector
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    override fun startScan(config: JdcrBleScanConfig?): Result<SharedFlow<JdcrBleScanResult>> {
        val scanner =
            createScanner() ?: return Result.failure(JdcrBleAvailableException("蓝牙不可用"))
        return scanner.startScan(config)
    }

    override fun getScanResult(address: String): ScanResultWrapper? =
        scanner?.getScanResult(address)

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun stopScan() {
        scanner?.stopScan()
    }

    override fun isConnect(address: String): Boolean {
        val connector = createConnector() ?: return false
        return connector.isConnect(address)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connect(
        address: String,
        config: JdcrBleConnectConfig?
    ): Result<StateFlow<JdcrBleConnectState>?> {
        val connector =
            createConnector() ?: return Result.failure(JdcrBleAvailableException("蓝牙不可用"))
        return connector.connect(address, config)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connect(
        device: BluetoothDevice,
        config: JdcrBleConnectConfig?
    ): Result<StateFlow<JdcrBleConnectState>?> {
        val connector =
            createConnector() ?: return Result.failure(JdcrBleAvailableException("蓝牙不可用"))
        return connector.connect(device, config)
    }

    override fun getDevice(address: String): BluetoothDevice? = connector?.getDevice(address)

    override fun getConnectDevices(): List<BluetoothDevice>? = connector?.getConnectDevices()

    override fun getServiceStructure(address: String): List<JdcrBleServiceInfo>? =
        connector?.getServiceStructure(address)

    override fun getFinalMtu(address: String): Int? = connector?.getFinalMtu(address)

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun disconnect(address: String) {
        connector?.disconnect(address)
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun disconnectAll() {
        connector?.disconnectAll()
    }

    fun onBluetoothDisabled() {
        if (JdcrBlePermissionUtils.checkConnectPermission(context)) {
            connector?.onBluetoothDisabled()
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun sendAction(
        action: JdcrBleCommunicatorAction,
        inMainThread: Boolean,
        onComplete: ((Result<JdcrBleCommunicatorActionResult>) -> Unit)?
    ) {
        communicator.sendAction(action, inMainThread, onComplete)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stopAllNotify(address: String) {
        coroutine.launch {
            communicator.stopAllNotify(address)
        }
    }

    override fun getNotificationDataFlow(): SharedFlow<NotificationData> {
        return communicator.getNotificationDataFlow()
    }

    override fun release() {
        scanner?.release()
        connector?.release()
        communicator.release()
        coroutine.cancelChildren()
    }

}