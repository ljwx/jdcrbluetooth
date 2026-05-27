package com.jdcr.jdcrblecommon.selftest

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import com.jdcr.jdcrble.JdcrBleHelper
import com.jdcr.jdcrble.config.JdcrBleConfig
import com.jdcr.jdcrble.config.JdcrBleScanConfig
import com.jdcr.jdcrble.core.communicator.JdcrBleCommunicatorAction
import com.jdcr.jdcrble.state.JdcrBleConnectState
import com.jdcr.jdcrble.util.JdcrBleLog
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

object BluetoothDeviceTest {

    private lateinit var manager: JdcrBleHelper
    private val scop = GlobalScope

    private var connectJob: Job? = null

    fun init(context: Context) {
        val config = JdcrBleConfig().apply {
            location.forceLocationPermission = true
        }
        manager = JdcrBleHelper(context, config = config).init()
        scop.launch {
            manager.getAvailableStateFlow().collect {
                JdcrBleLog.i("蓝牙状态:$it")
            }
        }
        scop.launch {
            manager.getNotificationDataFlow().collect { data ->
                data.value?.let { value ->
                    when (data.characterUuid.toString().uppercase()) {
                        MicrobitConstants.BUTTON_A_STATE_UUID -> {
                            val state = value[0].toInt()
                            val stateStr =
                                if (state == 0) "Release (松开)" else if (state == 1) "Press (按下)" else "Long Press (长按)"
                            JdcrBleLog.d("[通知] 按钮 A 状态变更: $stateStr")
                        }

                        MicrobitConstants.BUTTON_B_STATE_UUID -> {
                            val state = value[0].toInt()
                            val stateStr =
                                if (state == 0) "Release (松开)" else if (state == 1) "Press (按下)" else "Long Press (长按)"
                            JdcrBleLog.d("[通知] 按钮 B 状态变更: $stateStr")
                        }

                        MicrobitConstants.UART_RX_UUID -> {
                            val text = String(value, StandardCharsets.UTF_8)
                            JdcrBleLog.d("[通知] 收到串口消息: $text")
                        }

                        MicrobitConstants.IO_PIN_DATA_UUID -> {
                            // 格式: [pin, value]
                            if (value.size >= 2) {
                                val pin = value[0].toInt()
                                val pinValue = value[1].toInt() and 0xFF // 0-255
                                JdcrBleLog.d("[通知] IO引脚数据: Pin=$pin, Value=$pinValue")
                            }
                        }

                        MicrobitConstants.TEMPERATURE_DATA_UUID -> {
                            // 格式: [temp] (摄氏度)
                            if (value.isNotEmpty()) {
                                val temp = value[0].toInt()
                                JdcrBleLog.d("[通知] 温度数据: $temp°C")
                            }
                        }

                    }
                }
            }
        }
        scop.launch {
            manager.getNotificationDataFlow()
                .filter {
                    it.characterUuid.toString()
                        .uppercase() == MicrobitConstants.MAGNETOMETER_DATA_UUID
                }.collect { data ->
                    data.value?.let { value ->
                        when (data.characterUuid.toString().uppercase()) {
                            MicrobitConstants.MAGNETOMETER_DATA_UUID -> {
                                // X, Y, Z (Little Endian, Short)
                                if (value.size >= 6) {
                                    val x = (value[0].toInt() and 0xFF) or (value[1].toInt() shl 8)
                                    val y = (value[2].toInt() and 0xFF) or (value[3].toInt() shl 8)
                                    val z = (value[4].toInt() and 0xFF) or (value[5].toInt() shl 8)
                                    JdcrBleLog.d("[通知] 磁力计数据: X=${x.toShort()}, Y=${y.toShort()}, Z=${z.toShort()}")
                                }
                            }
                        }
                    }
                }
        }
        scop.launch {
            manager.getNotificationDataFlow()
                .filter {
                    it.characterUuid.toString()
                        .uppercase() == MicrobitConstants.ACCELEROMETER_DATA_UUID
                }.collect { data ->
                    data.value?.let { value ->
                        when (data.characterUuid.toString().uppercase()) {
                            MicrobitConstants.ACCELEROMETER_DATA_UUID -> {
                                // X, Y, Z (Little Endian, Short)
                                // 数据格式: [xlow, xhigh, ylow, yhigh, zlow, zhigh]
                                if (value.size >= 6) {
                                    val x = (value[0].toInt() and 0xFF) or (value[1].toInt() shl 8)
                                    val y = (value[2].toInt() and 0xFF) or (value[3].toInt() shl 8)
                                    val z = (value[4].toInt() and 0xFF) or (value[5].toInt() shl 8)
                                    // 转换为有符号 short (因为 byte 转 int 是无符号扩展或直接补码，这里需要转回 short 再打印方便看)
                                    JdcrBleLog.d("[通知] 加速度数据: X=${x.toShort()}, Y=${y.toShort()}, Z=${z.toShort()}")
                                }
                            }
                        }
                    }
                }
        }

    }

    fun getHelper(): JdcrBleHelper = manager

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(config: JdcrBleScanConfig? = null) = manager.startScan(config)

    fun stopScan() = manager.stopScan()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(address: String) {
        manager.connect(address).onSuccess {
            val flow = it
            connectJob?.cancel()
            connectJob = scop.launch {
                flow?.collect {
                    JdcrBleLog.i("连接状态:$it")
                    if (it is JdcrBleConnectState.Ready) {
                        registerTemperature(address)
                        registerAccelerometer(address)
                        registerMagnetometer(address)
                        registerIO(address)
                        registerButton(address)
                    }
                }
            }
        }

    }

    fun disconnect(address: String) = manager.disconnect(address)

    fun registerButton(address: String) {
        manager.enableNotification(
            JdcrBleCommunicatorAction.EnableNotification(
                address,
                MicrobitConstants.BUTTON_SERVICE_UUID.toUUID(),
                MicrobitConstants.BUTTON_A_STATE_UUID.toUUID(),
                enable = true,
                MicrobitConstants.CCCD_UUID.toUUID(),
                tag = "按钮a",
                throttle = null,
            )
        ) {
            JdcrBleLog.i("开启按钮a通知结果:$it")
        }
        manager.enableNotification(
            JdcrBleCommunicatorAction.EnableNotification(
                address,
                MicrobitConstants.BUTTON_SERVICE_UUID.toUUID(),
                MicrobitConstants.BUTTON_B_STATE_UUID.toUUID(),
                enable = true,
                MicrobitConstants.CCCD_UUID.toUUID(),
                tag = "按钮b",
                throttle = 100
            )
        ) {
            JdcrBleLog.i("开启按钮b通知结果:$it")
        }
    }

    fun registerIO(address: String) {
        // 1. 注册 IO 引脚数据通知
        manager.enableNotification(
            JdcrBleCommunicatorAction.EnableNotification(
                address,
                MicrobitConstants.IO_PIN_SERVICE_UUID.toUUID(),
                MicrobitConstants.IO_PIN_DATA_UUID.toUUID(),
                enable = true,
                MicrobitConstants.CCCD_UUID.toUUID(),
                tag = "IO引脚"
            )
        ) {
            JdcrBleLog.i("开启IO引脚通知结果:$it")
        }
    }

    fun registerTemperature(address: String) {
        // 2. 注册 温度数据通知 (Temperature Service)
        manager.write(
            JdcrBleCommunicatorAction.Write(
                address,
                MicrobitConstants.TEMPERATURE_SERVICE_UUID.toUUID(),
                MicrobitConstants.TEMPERATURE_PERIOD_UUID.toUUID(),
                byteArrayOf(0x0e, 0x27),
                tag = "温度间隔"
            )
        ) {
            JdcrBleLog.i("开启温度通知间隔结果:$it")
        }
        manager.enableNotification(
            JdcrBleCommunicatorAction.EnableNotification(
                address,
                MicrobitConstants.TEMPERATURE_SERVICE_UUID.toUUID(),
                MicrobitConstants.TEMPERATURE_DATA_UUID.toUUID(),
                enable = true,
                MicrobitConstants.CCCD_UUID.toUUID(),
                tag = "温度通知"
            )
        ) {
            JdcrBleLog.i("开启温度通知结果:$it")
        }
    }

    fun registerAccelerometer(address: String) {
        // 3. 注册 加速度计数据通知 (Accelerometer Service)
        manager.write(
            JdcrBleCommunicatorAction.Write(
                address,
                MicrobitConstants.ACCELEROMETER_SERVICE_UUID.toUUID(),
                MicrobitConstants.ACCELEROMETER_PERIOD_UUID.toUUID(),
                byteArrayOf(0xD0.toByte(), 0x07.toByte()),
                tag = "加速度间隔"
            )
        ) {
            JdcrBleLog.i("开启加速度通知间隔结果:$it")
        }
        manager.enableNotification(
            JdcrBleCommunicatorAction.EnableNotification(
                address,
                MicrobitConstants.ACCELEROMETER_SERVICE_UUID.toUUID(),
                MicrobitConstants.ACCELEROMETER_DATA_UUID.toUUID(),
                enable = true,
                MicrobitConstants.CCCD_UUID.toUUID(),
                throttle = 100,
                tag = "加速度通知"
            )
        ) {
            JdcrBleLog.i("开启加速度通知结果:$it")
        }
    }

    fun registerMagnetometer(address: String) {
        // 4. 注册 磁力计数据通知 (Magnetometer Service)
        manager.write(
            JdcrBleCommunicatorAction.Write(
                address,
                MicrobitConstants.MAGNETOMETER_SERVICE_UUID.toUUID(),
                MicrobitConstants.MAGNETOMETER_PERIOD_UUID.toUUID(),
                byteArrayOf(0x80.toByte(), 0x02.toByte())
            )
        ) {
            JdcrBleLog.i("开启磁力计通知间隔结果:$it")
        }
        manager.enableNotification(
            JdcrBleCommunicatorAction.EnableNotification(
                address,
                MicrobitConstants.MAGNETOMETER_SERVICE_UUID.toUUID(),
                MicrobitConstants.MAGNETOMETER_DATA_UUID.toUUID(),
                enable = true,
                MicrobitConstants.CCCD_UUID.toUUID(),
                tag = "磁力计通知",
                throttle = 1000,
            )
        ) {
            JdcrBleLog.i("开启磁力计通知结果:$it")
        }
    }

    fun readTemperature(address: String) {
        manager.read(
            JdcrBleCommunicatorAction.Read(
                address,
                MicrobitConstants.TEMPERATURE_SERVICE_UUID.toUUID(),
                MicrobitConstants.TEMPERATURE_DATA_UUID.toUUID(),
                tag = "获取温度"
            )
        ) {
            JdcrBleLog.i("读取温度结果:$it")
        }
    }

    fun writeTextToLed(address: String) {
        manager.write(
            JdcrBleCommunicatorAction.Write(
                address,
                MicrobitConstants.LED_SERVICE_UUID.toUUID(),
                MicrobitConstants.LED_TEXT_UUID.toUUID(),
                "a---b---c---d---e---0123".toByteArray(),
//                "1".toByteArray(),
                tag = "写入LED",
            )
        ) {
            JdcrBleLog.i("写入LED结果:$it")
        }
    }

    fun disableNotify(address: String) {
        manager.enableNotification(
            JdcrBleCommunicatorAction.EnableNotification(
                address,
                MicrobitConstants.MAGNETOMETER_SERVICE_UUID.toUUID(),
                MicrobitConstants.MAGNETOMETER_DATA_UUID.toUUID(),
                enable = false,
                MicrobitConstants.CCCD_UUID.toUUID(),
                tag = "磁力计通知",
                throttle = 1000,
            )
        ) {
            JdcrBleLog.i("开启磁力计通知结果:$it")
        }
    }

    fun getScanResult(address: String) = manager.getScanResult(address)

}