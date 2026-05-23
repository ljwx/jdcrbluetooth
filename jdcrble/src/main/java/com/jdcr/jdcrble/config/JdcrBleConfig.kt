package com.jdcr.jdcrble.config

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import java.util.UUID

const val MTU_DEFAULT_SIZE = 23
const val MTU_PLACEHOLDER = 3

data class JdcrBleScanConfig(
    var timeoutFinish: Long = 20000,
    var minRssi: Int = -100,
    var filterNullName: Boolean = true,
    var expiredTimeMills: Int = 2000,
    var resultIntervalMills: Long = 200,
    var rssiSort: Boolean = true,
    var scanFilters: List<ScanFilter>? = null,
    var settings: ScanSettings = getDefaultScanSettings(),
) {
    companion object {

        fun getUUIDFilter(uuid: UUID): List<ScanFilter> {
            return listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(uuid)).build())
        }

        fun getDefaultScanSettings(): ScanSettings {
            return ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        }

    }
}

data class JdcrBleConnectConfig(
    var mtu: Int? = null,
    val autoConnect: Boolean = false
)

data class BleCommunicateConfig(val timeoutMills: Long = 7000)

data class JdcrBleConfig(
    val maxConnectDevice: Int = 3,
//    val scan: JdcrBleScanConfig = JdcrBleScanConfig(),
//    val connect: JdcrBleConnectConfig = JdcrBleConnectConfig(),
    val communicate: BleCommunicateConfig = BleCommunicateConfig()
)