package com.jdcr.jdcrblecommon.selftest

import java.util.UUID

/**
 * Micro:bit BLE UUID 常量定义
 * 包含所有服务的 UUID 以及特征值的 UUID
 */
object TudaoConstants {

    const val TEST_ADDRESS_OLD_BIG = "84:CC:A8:06:35:1E"
    const val TEST_ADDRESS = "CC:26:03:25:33:36"

    val SeriviceId = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val WriteCharacteristicId = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    val NotifyCharacteristicId = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
}
