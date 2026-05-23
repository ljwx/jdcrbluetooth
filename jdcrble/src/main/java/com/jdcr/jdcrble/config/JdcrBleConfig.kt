package com.jdcr.jdcrble.config

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import java.util.UUID

const val MTU_DEFAULT_SIZE = 23
const val MTU_PLACEHOLDER = 3

data class JdcrLocationConfig(
    var forceLocationPermission: Boolean = false,
    var forceFineLocation: Boolean = false,
    var enableLocationFeature: Boolean = true
)


data class JdcrBleScanConfig(
    var timeoutFinish: Long = 20000,
    var minRssi: Int = -150,
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
    val mtu: Int? = null,
    val autoConnect: Boolean = false
)

data class BleCommunicateConfig(var timeoutMills: Long = 7000)

data class JdcrBleConfig(
    val maxConnectDevice: Int = 3,
    val location: JdcrLocationConfig = JdcrLocationConfig(),
//    val scan: JdcrBleScanConfig = JdcrBleScanConfig(),
//    val connect: JdcrBleConnectConfig = JdcrBleConnectConfig(),
    val communicate: BleCommunicateConfig = BleCommunicateConfig()
)