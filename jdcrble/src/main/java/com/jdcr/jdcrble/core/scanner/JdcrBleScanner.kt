package com.jdcr.jdcrble.core.scanner

import com.jdcr.jdcrble.config.JdcrBleScanConfig
import com.jdcr.jdcrble.state.JdcrBleScanResult
import kotlinx.coroutines.flow.SharedFlow

interface JdcrBleScanner {

    fun startScan(config: JdcrBleScanConfig? = null): Result<SharedFlow<JdcrBleScanResult>>

    fun getScanResult(address: String): ScanResultWrapper?

    fun stopScan()

    fun release()

}