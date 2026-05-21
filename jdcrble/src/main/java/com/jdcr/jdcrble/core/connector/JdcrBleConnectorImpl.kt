package com.jdcr.jdcrble.core.connector

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import com.jdcr.jdcrble.core.communicator.JdcrBleCommunicatorAction
import com.jdcr.jdcrble.core.communicator.JdcrBleCommunicatorActionResult
import com.jdcr.jdcrble.core.communicator.JdcrBleCommunicatorImpl
import com.jdcr.jdcrble.core.communicator.NotificationData
import com.jdcr.jdcrble.config.JdcrBleConnectConfig
import com.jdcr.jdcrble.exception.JdcrBleConnectException
import com.jdcr.jdcrble.state.JdcrBleConnectState
import com.jdcr.jdcrble.util.JdcrBleLog
import com.jdcr.jdcrble.util.JdcrBlePermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

open class JdcrBleConnectorImpl(
    private val context: Context,
    private val bleAdapter: BluetoothAdapter,
    private val connectConfig: JdcrBleConnectConfig,
    private val action: JdcrBleCommunicatorImpl
) : JdcrBleConnector {

    private val deviceStatusMap =
        LinkedHashMap<String, MutableStateFlow<JdcrBleConnectState>>()
    private var currentMtu: Int? = null

    private fun getGattCallback(): BluetoothGattCallback {
        return object : BluetoothGattCallback() {

            override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
                super.onReadRemoteRssi(gatt, rssi, status)
                JdcrBleLog.i("读取的信号强度:$rssi")
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                gatt?.device ?: return
                val device = gatt.device
                val address = device.address

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (JdcrBlePermissionUtils.checkConnectPermission(context.applicationContext)) {
                        serverExceptionAndGattDisconnect(device, status)
                    }
                    return
                }

                when (newState) {

                    BluetoothProfile.STATE_CONNECTING -> {
                        changeDeviceState(address, JdcrBleConnectState.Connecting(device, gatt))
                    }

                    BluetoothProfile.STATE_CONNECTED -> {
                        changeDeviceState(address, JdcrBleConnectState.Connected(device, gatt))
                        gatt.discoverServices()
                    }

                    BluetoothProfile.STATE_DISCONNECTING -> {
                        changeDeviceState(address, JdcrBleConnectState.Disconnecting(device, gatt))
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        val state = getDeviceStatus(address)
                        state?.gatt?.disconnect()
                        state?.gatt?.close()
                        changeDeviceState(
                            address,
                            JdcrBleConnectState.Disconnected(device, gatt, state, status)
                        )
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                gatt?.device ?: return
                val device = gatt.device
                val address = device.address
                JdcrBleLog.i("onServicesDiscovered:$address")
                changeDeviceState(address, JdcrBleConnectState.DiscoveredServices(device, gatt))
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val mtu = connectConfig.mtu
                    val permission =
                        JdcrBlePermissionUtils.checkConnectPermission(context.applicationContext)
                    if (permission && mtu != null) {
                        changeDeviceState(address, JdcrBleConnectState.ModifyMtu(device, mtu, gatt))
                        val result = gatt.requestMtu(mtu)
                        if (!result) {
                            changeDeviceState(address, JdcrBleConnectState.Ready(device, gatt))
                        }
                        JdcrBleLog.i("请求修改mtu大小:$result,$mtu")
                    } else {
                        changeDeviceState(address, JdcrBleConnectState.Ready(device, gatt))
                    }
                } else {
                    JdcrBleLog.w("onServicesDiscovered失败")
                    serverExceptionAndGattDisconnect(gatt.device, status)
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int
            ) {
                super.onDescriptorWrite(gatt, descriptor, status)
                gatt ?: return
                val characteristic = descriptor?.characteristic
                characteristic ?: return

                val uuid = characteristic.uuid
                val success = status == BluetoothGatt.GATT_SUCCESS
                val descriptorUUID = descriptor.uuid
                val currentAction = action.getCurrentAction()
                if (currentAction is JdcrBleCommunicatorAction.RegisterNotification && descriptorUUID == currentAction.descriptorUUID) {
                    JdcrBleLog.i("开启通知服务回调:$success, $uuid")
                    val address = gatt.device.address
                    val service = characteristic.service?.uuid
                    val key = JdcrBleCommunicatorAction.getEnableNotifyKey(
                        address,
                        service,
                        uuid,
                        descriptorUUID
                    )
                    val result = JdcrBleCommunicatorActionResult.Notification(
                        address,
                        service,
                        uuid,
                        descriptor.uuid
                    )
                    action.onActionResult(success, key, result)
                }
            }

            private fun onNotification(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                val uuid = characteristic.uuid
                JdcrBleLog.v("通知服务数据回调: $uuid")
                val address = gatt.device.address
                val service = characteristic.service?.uuid
                val result = NotificationData(address, service, uuid, value)
                action.onNotification(result)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                gatt ?: return
                characteristic ?: return
                onNotification(gatt, characteristic, characteristic.value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                onNotification(gatt, characteristic, value)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                gatt ?: return
                characteristic ?: return
                val success = status == BluetoothGatt.GATT_SUCCESS
                val uuid = characteristic.uuid
                JdcrBleLog.i("写入服务回调:$success, $uuid, $status")
                val address = gatt.device.address
                val service = characteristic.service?.uuid
                val key = JdcrBleCommunicatorAction.getWriteKey(address, service, uuid)
                val result = JdcrBleCommunicatorActionResult.Write(address, service, uuid)
                action.onActionResult(success, key, result)
            }

            private fun characteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray?,
                status: Int
            ) {
                val uuid = characteristic.uuid
                val success = status == BluetoothGatt.GATT_SUCCESS
                JdcrBleLog.i("读取服务回调:$success, $uuid")
                val address = gatt.device.address
                val service = characteristic.service?.uuid
                val key = JdcrBleCommunicatorAction.getReadKey(address, service, uuid)
                val result = JdcrBleCommunicatorActionResult.Read(address, service, uuid, value)
                action.onActionResult(success, key, result)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                gatt ?: return
                characteristic ?: return
                val value = characteristic?.value
                characteristicRead(gatt, characteristic, value, status)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                characteristicRead(gatt, characteristic, value, status)
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                gatt?.device ?: return
                val device = gatt.device
                val address = gatt.device.address
                currentMtu = mtu
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    JdcrBleLog.w("修改mtu失败:$status")
                }
                JdcrBleLog.i("实际mtu大小:$mtu")
                changeDeviceState(address, JdcrBleConnectState.Ready(device, gatt))
            }

        }
    }

    private fun getDevicesStatusFlow(address: String): MutableStateFlow<JdcrBleConnectState>? {
        return deviceStatusMap[address]
    }

    private fun getDeviceStatus(address: String): JdcrBleConnectState? {
        return getDevicesStatusFlow(address)?.value
    }

    private fun isConnectLimit(): Boolean {
        return deviceStatusMap.filter { it.value.value.stateStep > JdcrBleConnectState.INITIAL_STATUS }.size >= connectConfig.maxConnectDevice
    }

    override fun isConnect(address: String): Boolean {
        val state = getDeviceStatus(address) ?: JdcrBleConnectState.Void
        return state.stateStep > JdcrBleConnectState.INITIAL_STATUS
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connect(address: String): Result<StateFlow<JdcrBleConnectState>?> {
        val device = bleAdapter.getRemoteDevice(address)
        if (device != null) {
            return connect(device)
        } else {
            "未发现该设备,无法执行连接:$address".let {
                JdcrBleLog.w(it)
                return Result.failure(JdcrBleConnectException(it))
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connect(device: BluetoothDevice): Result<StateFlow<JdcrBleConnectState>?> {
        val address = device.address
        JdcrBleLog.i("触发设备连接:${address}")
        if (isConnect(address)) {
            JdcrBleLog.i("设备已连接:${address},直接返回结果")
            return Result.success(getDevicesStatusFlow(address))
        }
        if (isConnectLimit()) {
            "连接已达上限,无法执行连接:${address}".let {
                JdcrBleLog.w(it)
                return Result.failure(JdcrBleConnectException(it))
            }
        }
        val gatt = device.connectGatt(
            context.applicationContext,
            connectConfig.autoConnect,
            getGattCallback()
        )
        if (gatt == null) {
            "执行连接后,发现gatt为空,连接失败".let {
                JdcrBleLog.w(it)
                return Result.failure(JdcrBleConnectException(it))
            }
        } else {
            action.setGatt(address, gatt)
            deviceStatusMap[address] = MutableStateFlow(JdcrBleConnectState.Void)
            return Result.success(getDevicesStatusFlow(address))
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun disconnect(address: String) {
        JdcrBleLog.i("主动断开连接:$address")
        deviceStatusMap.remove(address)?.value?.let {
            it.gatt?.disconnect()
            it.gatt?.close()
            action.removeGatt(address)
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun disconnectAll() {
        JdcrBleLog.d("主动断开所有连接")
        deviceStatusMap.iterator().apply {
            while (hasNext()) {
                disconnect(next().key)
            }
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun serverExceptionAndGattDisconnect(device: BluetoothDevice, status: Int) {
        val address = device.address
        val state = getDeviceStatus(address)
        JdcrBleLog.d("蓝牙服务异常,执行gatt断开连接:${state?.desc}")
        state?.gatt?.disconnect()
        state?.gatt?.close()
        action.removeGatt(address)
        changeDeviceState(
            address,
            JdcrBleConnectState.Disconnected(device, null, state, status)
        )
    }

    private fun changeDeviceState(address: String, status: JdcrBleConnectState) {
        JdcrBleLog.i("触发连接状态变更:${status.desc},$address")
        deviceStatusMap[address]?.value = status
        if (status is JdcrBleConnectState.Disconnected) {
            deviceStatusMap.remove(address)
            action.removeGatt(address)
        }
    }

    fun release() {
        if (JdcrBlePermissionUtils.checkConnectPermission(context.applicationContext)) {
            disconnectAll()
        }
        deviceStatusMap.clear()
    }

}