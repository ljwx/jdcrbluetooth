package com.jdcr.jdcrble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.fragment.app.FragmentActivity
import com.jdcr.jdcrble.available.JdcrBleEnableReceiver
import com.jdcr.jdcrble.available.JdcrBleLocationEnableReceiver
import com.jdcr.jdcrble.config.JdcrBleConfig
import com.jdcr.jdcrble.config.JdcrBleConnectConfig
import com.jdcr.jdcrble.config.JdcrBleScanConfig
import com.jdcr.jdcrble.core.JdcrBleCore
import com.jdcr.jdcrble.core.communicator.JdcrBleCommunicatorAction
import com.jdcr.jdcrble.core.communicator.JdcrBleCommunicatorActionResult
import com.jdcr.jdcrble.data.JdcrBleServiceInfo
import com.jdcr.jdcrble.state.JdcrBleAvailableState
import com.jdcr.jdcrble.state.JdcrBleConnectState
import com.jdcr.jdcrble.state.JdcrBleScanResult
import com.jdcr.jdcrble.util.JdcrBleLog
import com.jdcr.jdcrble.util.JdcrBlePermissionUtils
import com.jdcr.jdcrble.util.JdcrBleUtils
import com.jdcr.jdcrpermission.JdcrPermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class JdcrBleHelper(context: Context, private val config: JdcrBleConfig = JdcrBleConfig()) {

    private val applicationContext = context.applicationContext

    private val bleEnableReceiver by lazy { JdcrBleEnableReceiver(applicationContext) }
    private val locationEnableReceiver by lazy { JdcrBleLocationEnableReceiver(applicationContext) }

    private var availableState =
        MutableStateFlow(
            JdcrBleUtils.getBleAvailableState(
                applicationContext,
                config.location
            )
        )

    private val bleCore by lazy { JdcrBleCore(applicationContext, config) }

    fun init(): JdcrBleHelper {
        JdcrBleLog.i("初始化蓝牙帮助类状态")
        if (JdcrBleUtils.isNeedLocationFeature(config.location.enableLocationFeature)) {
            locationEnableReceiver.register {
                changeAvailableState()
            }
        }
        bleEnableReceiver.register { enableResult ->
            changeAvailableState()
            if (enableResult.getOrNull() == false) {
                bleCore.onBluetoothDisabled()
            }
        }
        return this
    }

    fun changeAvailableState() {
        availableState.value =
            JdcrBleUtils.getBleAvailableState(applicationContext, config.location)
                .apply { JdcrBleLog.i("可用状态更新:${this.desc}") }
    }

    fun getAvailableStateFlow(): StateFlow<JdcrBleAvailableState> {
        return availableState
    }

    fun getAvailableState(): JdcrBleAvailableState {
        return availableState.value
    }

    fun isBleReady(): Boolean {
        JdcrBleLog.i("可用状态是否可用:${availableState.value.desc}")
        return availableState.value is JdcrBleAvailableState.Ready
    }

    fun requestAllPermission(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) {
        JdcrBleLog.i("触发请求所有权限")
        val permissions = JdcrBlePermissionUtils.getAllPermissions()
        JdcrPermissionUtils.request(activity, permissions, callback)
    }

    fun requestMissPermissions(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) {
        JdcrBleLog.i("触发请求缺失的所有权限")
        JdcrPermissionUtils.request(
            activity,
            JdcrBlePermissionUtils.getMissPermission(
                applicationContext,
                config.location
            ),
            callback
        )
    }

    fun openPermissionSetting(activity: FragmentActivity, callback: (() -> Unit)) {
        JdcrPermissionUtils.openAppSettings(activity, callback)
    }

    fun openBleEnableSetting(activity: FragmentActivity, callback: (() -> Unit)) {
        JdcrPermissionUtils.openBluetoothSettings(activity, callback)
    }

    fun openLocationEnableSetting(activity: FragmentActivity, callback: (() -> Unit)) {
        JdcrPermissionUtils.openLocationSettings(activity, callback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(config: JdcrBleScanConfig? = null): Result<SharedFlow<JdcrBleScanResult>> {
        return bleCore.startScan(config)
    }

    fun getScanResult(address: String) = bleCore.getScanResult(address)

    fun stopScan() {
        if (JdcrBlePermissionUtils.checkScanPermission(applicationContext)) {
            bleCore.stopScan()
        } else {
            JdcrBleLog.i("没有扫描权限,不执行停止扫描")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(
        address: String,
        config: JdcrBleConnectConfig? = null
    ): Result<StateFlow<JdcrBleConnectState>?> {
        return bleCore.connect(address, config)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(
        device: BluetoothDevice,
        config: JdcrBleConnectConfig? = null
    ): Result<StateFlow<JdcrBleConnectState>?> {
        return bleCore.connect(device, config)
    }

    fun getServiceStructure(address: String): List<JdcrBleServiceInfo>? {
        if (!JdcrBlePermissionUtils.checkConnectPermission(applicationContext)) {
            JdcrBleLog.w("没有连接权限,不获取服务列表")
            return null
        }
        return bleCore.getServiceStructure(address)
    }

    fun getServiceUuids(address: String): List<UUID>? {
        return getServiceStructure(address)?.map { it.uuid }?.distinct()
    }

    fun disconnect(address: String) {
        if (JdcrBlePermissionUtils.checkConnectPermission(applicationContext)) {
            bleCore.disconnect(address)
        } else {
            JdcrBleLog.i("没有连接权限,不执行断开连接")
        }
    }

    fun disconnectAll() {
        if (JdcrBlePermissionUtils.checkConnectPermission(applicationContext)) {
            bleCore.disconnectAll()
        } else {
            JdcrBleLog.i("没有连接权限,不执行断开所有连接")
        }
    }

    private inline fun <reified T : JdcrBleCommunicatorActionResult> handleResult(
        result: Result<JdcrBleCommunicatorActionResult>,
    ): Result<T> {
        if (result.isFailure) return result as Result<T>
        return when (val value = result.getOrNull()) {
            is T -> result as Result<T>
            else -> Result.failure(
                IllegalStateException("数据类型不匹配: 期望 ${T::class.simpleName}, 实际 ${value?.javaClass?.simpleName}")
            )
        }
    }

    fun enableNotification(
        notification: JdcrBleCommunicatorAction.EnableNotification,
        onMainThread: Boolean = false,
        onComplete: ((Result<JdcrBleCommunicatorActionResult.Notification>) -> Unit)?
    ) {
        if (JdcrBlePermissionUtils.checkConnectPermission(applicationContext)) {
            bleCore.sendAction(
                notification,
                onMainThread,
            ) {
                onComplete?.invoke(handleResult(it))
            }
        } else {
            JdcrBleLog.i("没有连接权限,不执行注册通知")
            onComplete?.invoke(Result.failure(IllegalStateException("没有权限")))
        }
    }

    fun write(
        write: JdcrBleCommunicatorAction.Write,
        onMainThread: Boolean = false,
        onComplete: ((Result<JdcrBleCommunicatorActionResult.Write>) -> Unit)?
    ) {
        if (JdcrBlePermissionUtils.checkConnectPermission(applicationContext)) {
            bleCore.sendAction(
                write,
                onMainThread,
            ) {
                onComplete?.invoke(handleResult(it))
            }
        } else {
            JdcrBleLog.i("没有连接权限,不执行写入数据")
            onComplete?.invoke(Result.failure(IllegalStateException("没有权限")))
        }

    }

    fun read(
        read: JdcrBleCommunicatorAction.Read,
        onMainThread: Boolean = false,
        onComplete: ((Result<JdcrBleCommunicatorActionResult.Read>) -> Unit)?
    ) {
        if (JdcrBlePermissionUtils.checkConnectPermission(applicationContext)) {
            bleCore.sendAction(
                read,
                onMainThread,
            ) {
                onComplete?.invoke(handleResult(it))
            }
        } else {
            JdcrBleLog.i("没有连接权限,不执行读取数据")
            onComplete?.invoke(Result.failure(IllegalStateException("没有权限")))
        }

    }

    fun getDevice(address: String) = bleCore.getDevice(address)

    fun getNotificationDataFlow() = bleCore.getNotificationDataFlow()

    fun onRelease() {
        bleEnableReceiver.close()
        locationEnableReceiver.close()
        bleCore.release()
    }

    fun onDestroy() {
        onRelease()
    }

}