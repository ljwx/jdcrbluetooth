package com.jdcr.jdcrblecommon.selftest

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import com.jdcr.jdcrble.JdcrBleHelper
import com.jdcr.jdcrble.config.JdcrBleScanConfig
import com.jdcr.jdcrble.core.communicator.JdcrBleCommunicatorAction
import com.jdcr.jdcrble.state.JdcrBleConnectState
import com.jdcr.jdcrblecommon.selftest.usecase.JdcrTudaoControllerUtils
import com.jdcr.jdcrblecommon.selftest.usecase.JdcrTudaoNotifyUtils
import com.jdcr.jdcrble.util.JdcrBleHexUtils.toHexString
import com.jdcr.jdcrble.util.JdcrBleLog
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object BluetoothDeviceTudaoTest {

    private lateinit var manager: JdcrBleHelper
    private val scope = GlobalScope

    private var connectJob: Job? = null
    private var motorJob: Job? = null

    fun init(context: Context) {
        manager = JdcrBleHelper(context).init()
        scope.launch {
            manager.getAvailableStateFlow().collect {
                JdcrBleLog.i("蓝牙状态:$it")
            }
        }
        scope.launch {
            manager.getNotificationDataFlow().collect { data ->
                data.value?.let { raw ->
                    when (val frame = JdcrTudaoNotifyUtils.parseTudaoFrame(raw)) {
                        is JdcrTudaoNotifyUtils.TudaoFrame.SensorData ->
                            JdcrBleLog.i("SensorData:${frame.data}, raw=${raw.toHexString()}")

                        is JdcrTudaoNotifyUtils.TudaoFrame.Heartbeat ->
                            JdcrBleLog.i("Heartbeat, raw=${raw.toHexString()}")

                        is JdcrTudaoNotifyUtils.TudaoFrame.Unknown ->
                            JdcrBleLog.w("Unknown, raw=${raw.toHexString()}")

                        is JdcrTudaoNotifyUtils.TudaoFrame.MotorStatus -> Unit

                        null ->
                            JdcrBleLog.w("解析失败, raw=${raw.toHexString()}")
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
        manager.connect(address).onSuccess { flow ->
            connectJob?.cancel()
            connectJob = scope.launch {
                flow?.collect {
                    if (it is JdcrBleConnectState.Ready) {
                        registerNotification(address)
                    }
                }
            }
        }
    }

    fun disconnect(address: String) {
        motorJob?.cancel()
        motorJob = null
        manager.disconnect(address)
    }

    fun registerNotification(address: String) {
        manager.enableNotification(
            JdcrBleCommunicatorAction.EnableNotification(
                address,
                TudaoConstants.SeriviceId,
                TudaoConstants.NotifyCharacteristicId,
                enable = true,
                tag = "通知",
            )
        ) {
        }
    }

    fun motorForward(
        address: String,
        uiSpeed: Int = JdcrTudaoControllerUtils.DEFAULT_UI_MOTOR_SPEED
    ) {
        runMotor(
            address,
            JdcrTudaoControllerUtils.motorForward(JdcrTudaoControllerUtils.Motor.MOTOR_1, uiSpeed)
        )
    }

    fun motorDualForward(
        address: String,
        uiSpeed: Int = JdcrTudaoControllerUtils.DEFAULT_UI_MOTOR_SPEED
    ) {
        runMotor(address, JdcrTudaoControllerUtils.motorDualForward(uiSpeed))
    }

    fun stopMotor(address: String) {
        motorJob?.cancel()
        motorJob = null
        writeCommand(address, JdcrTudaoControllerUtils.stopAllMotors(), "停止电机")
    }

    /** 与官方一致：写一次转动，约 760ms 后停止 */
    private fun runMotor(address: String, command: ByteArray, runDurationMs: Long = 760L) {
        motorJob?.cancel()
        val stopCommand = JdcrTudaoControllerUtils.stopAllMotors()
        JdcrBleLog.i("电机指令 hex:${command.toHexString()}")
        motorJob = scope.launch {
            if (!writeCommandAwait(address, command, "电机转动")) return@launch
            delay(runDurationMs)
            writeCommandAwait(address, stopCommand, "电机停止")
        }
    }

    private suspend fun writeCommandAwait(
        address: String,
        command: ByteArray,
        tag: String,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        manager.write(
            JdcrBleCommunicatorAction.Write(
                address,
                TudaoConstants.SeriviceId,
                TudaoConstants.WriteCharacteristicId,
                command,
                writeType = JdcrBleCommunicatorAction.WRITE_TYPE_DEFAULT,
                tag = tag,
            )
        ) { result ->
            if (continuation.isActive) {
                continuation.resume(result.isSuccess)
            }
        }
    }

    private fun writeCommand(address: String, command: ByteArray, tag: String) {
        manager.write(
            JdcrBleCommunicatorAction.Write(
                address,
                TudaoConstants.SeriviceId,
                TudaoConstants.WriteCharacteristicId,
                command,
                writeType = JdcrBleCommunicatorAction.WRITE_TYPE_NO_RESPONSE,
                tag = tag,
            )
        ) {
            if (it.isFailure) JdcrBleLog.w("$tag 失败:$it")
        }
    }
}
