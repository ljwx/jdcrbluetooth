package com.jdcr.jdcrble.usecase

/**
 * 途道/编程猫 BLE 电机控制帧构建。
 *
 * 实测 14 字节格式：
 * `FF FE 09 01 02 m1 m2 m3 l1 l2 l3 l4 FD FC`
 */
object JdcrTudaoControllerUtils {

    const val MOTOR_SPEED_MIN = -13
    const val MOTOR_SPEED_MAX = 13
    const val DEFAULT_UI_MOTOR_SPEED = 5

    /** UI 档位 n 对应线速 (n + 2)，如 UI 5 -> 7 */
    private const val UI_SPEED_OFFSET = 2

    private const val FRAME_SIZE = 14
    private const val BODY_LENGTH = 0x09
    private const val MODE_COMMAND = 0x01
    private const val CONTROL_MOTOR = 0x02

    enum class Motor {
        MOTOR_1,
        MOTOR_2,
        MOTOR_3,
    }

    fun mapUiSpeedToWire(uiSpeed: Int): Int =
        (uiSpeed + UI_SPEED_OFFSET).coerceIn(MOTOR_SPEED_MIN, MOTOR_SPEED_MAX)

    fun motorForward(motor: Motor, uiSpeed: Int = DEFAULT_UI_MOTOR_SPEED): ByteArray =
        buildMotorFrame(motor, mapUiSpeedToWire(uiSpeed))

    fun motorReverse(motor: Motor, uiSpeed: Int = DEFAULT_UI_MOTOR_SPEED): ByteArray =
        buildMotorFrame(motor, -mapUiSpeedToWire(uiSpeed))

    /** 双轮同速前进：M1=+wire，M2=-wire（左轮反接，与官方「左5右5」一致） */
    fun motorDualForward(uiSpeed: Int = DEFAULT_UI_MOTOR_SPEED): ByteArray {
        val wireSpeed = mapUiSpeedToWire(uiSpeed)
        return buildFrame(
            motor1 = wireSpeed,
            motor2 = -wireSpeed,
        )
    }

    /** 停止全部电机：`FF FE 09 01 02 00... FD FC` */
    fun stopAllMotors(): ByteArray = buildFrame()

    private fun buildMotorFrame(motor: Motor, wireSpeed: Int): ByteArray {
        val speed = wireSpeed.coerceIn(MOTOR_SPEED_MIN, MOTOR_SPEED_MAX)
        return when (motor) {
            Motor.MOTOR_1 -> buildFrame(motor1 = speed)
            Motor.MOTOR_2 -> buildFrame(motor2 = speed)
            Motor.MOTOR_3 -> buildFrame(motor3 = speed)
        }
    }

    private fun buildFrame(
        motor1: Int = 0,
        motor2: Int = 0,
        motor3: Int = 0,
        led1: Int = 0,
        led2: Int = 0,
        led3: Int = 0,
        led4: Int = 0,
    ): ByteArray = byteArrayOf(
        0xFF.toByte(),
        0xFE.toByte(),
        BODY_LENGTH.toByte(),
        MODE_COMMAND.toByte(),
        CONTROL_MOTOR.toByte(),
        motor1.coerceIn(MOTOR_SPEED_MIN, MOTOR_SPEED_MAX).toByte(),
        motor2.coerceIn(MOTOR_SPEED_MIN, MOTOR_SPEED_MAX).toByte(),
        motor3.coerceIn(MOTOR_SPEED_MIN, MOTOR_SPEED_MAX).toByte(),
        led1.toByte(),
        led2.toByte(),
        led3.toByte(),
        led4.toByte(),
        0xFD.toByte(),
        0xFC.toByte(),
    ).also {
        require(it.size == FRAME_SIZE) { "帧长度应为 $FRAME_SIZE，实际 ${it.size}" }
    }
}
