package com.jdcr.jdcrblecommon.selftest

import java.util.UUID

fun String.toUUID(): UUID {
    return UUID.fromString(this)
}

/**
 * Micro:bit BLE UUID 常量定义
 * 包含所有服务的 UUID 以及特征值的 UUID
 */
object MicrobitConstants {

    const val TEST_ADDRESS = "E7:31:CB:B0:F2:E5"
    const val TEST_ADDRESS_2 = "E9:6A:88:D8:22:3A"

    // ===========================
    // 按键服务 (Button Service)
    // ===========================
    const val BUTTON_SERVICE_UUID = "E95D9882-251D-470A-A062-FA1922DFA9A8"

    // 按键 A 状态 (可读/通知) - 0:松开, 1:按下, 2:长按
    const val BUTTON_A_STATE_UUID = "E95DDA90-251D-470A-A062-FA1922DFA9A8"

    // 按键 B 状态 (可读/通知) - 0:松开, 1:按下, 2:长按
    const val BUTTON_B_STATE_UUID = "E95DDA91-251D-470A-A062-FA1922DFA9A8"

    // ===========================
    // LED 服务 (LED Service)
    // ===========================
    const val LED_SERVICE_UUID = "E95DD91D-251D-470A-A062-FA1922DFA9A8"

    // LED 矩阵状态 (可读/可写) - 0/1 数组表示 5x5 LED 状态
    const val LED_MATRIX_STATE_UUID = "E95D7B77-251D-470A-A062-FA1922DFA9A8"

    // LED 文本 (可写) - 发送字符串在 LED 上滚动显示
    const val LED_TEXT_UUID = "E95D93EE-251D-470A-A062-FA1922DFA9A8"

    // ===========================
    // 温度服务 (Temperature Service)
    // ===========================
    const val TEMPERATURE_SERVICE_UUID = "E95D6100-251D-470A-A062-FA1922DFA9A8"

    // 温度数据 (可读/通知) - 摄氏度
    const val TEMPERATURE_DATA_UUID = "E95D9250-251D-470A-A062-FA1922DFA9A8"

    // 温度上报周期 (可读/可写) - 单位毫秒
    const val TEMPERATURE_PERIOD_UUID = "E95D1B25-251D-470A-A062-FA1922DFA9A8"

    // ===========================
    // UART 服务 (UART Service) - 像串口一样传输数据
    // ===========================
    const val UART_SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"

    // UART 接收 (RX) - 从 Micro:bit 接收数据 (通知)
    const val UART_RX_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

    // UART 发送 (TX) - 向 Micro:bit 发送数据 (可写/无回复写)
    const val UART_TX_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"

    // ===========================
    // IO 引脚服务 (IO Pin Service)
    // ===========================
    const val IO_PIN_SERVICE_UUID = "E95D127B-251D-470A-A062-FA1922DFA9A8"

    // 引脚数据 (可读/通知) - 包含引脚值
    const val IO_PIN_DATA_UUID = "E95D8D00-251D-470A-A062-FA1922DFA9A8"

    // 引脚 模拟/数字 配置 (可读/可写) - 掩码配置哪些引脚是模拟输入
    const val IO_PIN_AD_CONFIGURATION_UUID = "E95D5899-251D-470A-A062-FA1922DFA9A8"

    // 引脚 输入/输出 配置 (可读/可写) - 掩码配置哪些引脚是输入/输出
    const val IO_PIN_CONFIGURATION_UUID = "E95DB9FE-251D-470A-A062-FA1922DFA9A8"

    // PWM 控制 (可读/可写)
    const val IO_PIN_PWM_CONTROL_UUID = "E95DD822-251D-470A-A062-FA1922DFA9A8"

    // ===========================
    // 加速度计服务 (Accelerometer Service)
    // ===========================
    const val ACCELEROMETER_SERVICE_UUID = "E95D0753-251D-470A-A062-FA1922DFA9A8"

    // 加速度数据 (可读/通知) - X, Y, Z 三轴数据
    const val ACCELEROMETER_DATA_UUID = "E95DCA4B-251D-470A-A062-FA1922DFA9A8"

    // 加速度上报周期 (可读/可写) - 单位毫秒
    const val ACCELEROMETER_PERIOD_UUID = "E95DFB24-251D-470A-A062-FA1922DFA9A8"

    // ===========================
    // 磁力计服务 (Magnetometer Service)
    // ===========================
    const val MAGNETOMETER_SERVICE_UUID = "E95DF2D8-251D-470A-A062-FA1922DFA9A8"

    // 磁力计数据 (可读/通知) - X, Y, Z 三轴数据
    const val MAGNETOMETER_DATA_UUID = "E95DFB11-251D-470A-A062-FA1922DFA9A8"

    // 磁力计上报周期 (可读/可写) - 单位毫秒
    const val MAGNETOMETER_PERIOD_UUID = "E95D386C-251D-470A-A062-FA1922DFA9A8"

    // ===========================
    // 设备信息服务 (Device Info Service)
    // ===========================
    const val DEVICE_INFO_SERVICE_UUID = "0000180A-0000-1000-8000-00805F9B34FB"

    // 固件版本字符串
    const val FIRMWARE_VERSION_UUID = "00002A26-0000-1000-8000-00805F9B34FB"

    // ===========================
    // 客户端特征配置描述符 (CCCD)
    // ===========================
    // 用于开启 Notify 必须写入的描述符
    const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
}
