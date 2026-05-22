package com.jdcr.jdcrble.usecase

import com.jdcr.jdcrble.util.JdcrBleHexUtils.toHexString
import com.jdcr.jdcrble.util.JdcrBleHexUtils.toUnsignedInt
import com.jdcr.jdcrble.util.JdcrBleLog

object JdcrTudaoNotifyUtils {

    const val VOLTAGE_SCALE = 150.0f

    private const val FRAME_HEAD = 0xFF
    private const val FRAME_TYPE_HEARTBEAT = 0xFB
    private const val FRAME_TYPE_DATA = 0xFE
    private const val SENSOR_PAYLOAD_LENGTH = 0x0E
    private const val MODE_SENSOR = 0x00
    private const val MODE_COMMAND = 0x01
    private const val CONTROL_STATUS = 0x0F

    data class SensorFeedback(
        val voltageRaw: Int,
        val voltageV: Float,
        val distance1: Int,
        val gyroX: Int,
        val gyroY: Int,
        val gyroAccel: Int,
        val light: Int,
        val color: Int,
        val sound: Int,
    )

    data class DeviceStatus(
        val sequence: Int,
        val motor1Speed: Int,
        val motor2Speed: Int,
        val motor3Speed: Int,
    )

    sealed class TudaoFrame {
        object Heartbeat : TudaoFrame()
        data class SensorData(val data: SensorFeedback) : TudaoFrame()
        data class MotorStatus(val data: DeviceStatus) : TudaoFrame()
        data class Unknown(val raw: ByteArray) : TudaoFrame()
    }

    fun parseTudaoFrame(data: ByteArray?): TudaoFrame? {
        if (data == null || data.size < 5 || data[0].toUnsignedInt() != FRAME_HEAD) return null

        val type = data[1].toUnsignedInt()
        val payloadLength = data[2].toUnsignedInt()
        val payloadEnd = 3 + payloadLength
        if (payloadEnd > data.size) {
            JdcrBleLog.w("途道帧长度不足: len=$payloadLength, size=${data.size}")
            return null
        }

        val payload = data.copyOfRange(3, payloadEnd)
        return when (type) {
            FRAME_TYPE_HEARTBEAT -> TudaoFrame.Heartbeat
            FRAME_TYPE_DATA -> parseDataFrame(payload, data)
            else -> {
                JdcrBleLog.w("途道未知帧: type=0x${type.toString(16)}, raw=${data.toHexString()}")
                TudaoFrame.Unknown(data)
            }
        }
    }

    fun rawToVoltageV(voltageRaw: Int): Float = voltageRaw / VOLTAGE_SCALE

    private fun parseDataFrame(payload: ByteArray, raw: ByteArray): TudaoFrame? {
        if (payload.isEmpty()) return null
        return when (payload[0].toUnsignedInt()) {
            MODE_SENSOR -> {
                if (payload.size < SENSOR_PAYLOAD_LENGTH) return TudaoFrame.Unknown(raw)
                TudaoFrame.SensorData(parseSensorFeedback(payload))
            }
            MODE_COMMAND -> {
                if (payload.size < SENSOR_PAYLOAD_LENGTH) return TudaoFrame.Unknown(raw)
                if (payload[1].toUnsignedInt() == CONTROL_STATUS) {
                    TudaoFrame.MotorStatus(parseDeviceStatus(payload))
                } else {
                    TudaoFrame.Unknown(raw)
                }
            }
            else -> TudaoFrame.Unknown(raw)
        }
    }

    private fun parseSensorFeedback(payload: ByteArray): SensorFeedback {
        val voltageRaw = readUInt16BE(payload, 1)
        return SensorFeedback(
            voltageRaw = voltageRaw,
            voltageV = rawToVoltageV(voltageRaw),
            distance1 = readUInt16BE(payload, 3),
            gyroX = readInt16BE(payload, 5),
            gyroY = readInt16BE(payload, 7),
            gyroAccel = readInt16BE(payload, 9),
            light = payload[11].toUnsignedInt(),
            color = payload[12].toUnsignedInt(),
            sound = payload[13].toUnsignedInt(),
        )
    }

    /** 0x0F 状态帧：byte2=sequence，byte3~5=电机速度（有符号） */
    private fun parseDeviceStatus(payload: ByteArray): DeviceStatus = DeviceStatus(
        sequence = payload[2].toUnsignedInt(),
        motor1Speed = payload[3].toSignedSpeed(),
        motor2Speed = payload[4].toSignedSpeed(),
        motor3Speed = payload[5].toSignedSpeed(),
    )

    private fun Byte.toSignedSpeed(): Int {
        val value = toInt()
        return if (value > 0x7F) value - 0x100 else value
    }

    private fun readUInt16BE(data: ByteArray, index: Int): Int =
        (data[index].toUnsignedInt() shl 8) or data[index + 1].toUnsignedInt()

    private fun readInt16BE(data: ByteArray, index: Int): Int {
        val value = readUInt16BE(data, index)
        return if (value > 0x7FFF) value - 0x10000 else value
    }
}
