package com.jdcr.jdcrble.core.scanner

import android.Manifest
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import com.jdcr.jdcrble.config.JdcrBleScanConfig
import com.jdcr.jdcrble.state.JdcrBleScanResult
import com.jdcr.jdcrble.util.JdcrBleLog
import com.jdcr.jdcrble.util.JdcrBlePermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

open class JdcrBleScannerImpl(
    private val context: Context,
    private val scanner: BluetoothLeScanner,
    private val coroutine: CoroutineScope,
) : JdcrBleScanner {

    private var scanConfig: JdcrBleScanConfig = JdcrBleScanConfig()

    @Volatile
    private var isScanning = false
    private var sendScanResultJob: Job? = null
    private var handleScanResultJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private val scanResultSerial = Dispatchers.Default.limitedParallelism(1)
    private var scanResultChannel: Channel<ScanResult>? = null
    private val scanResultMap by lazy { ConcurrentHashMap<String, ScanResultWrapper>(32) }
    private val scanResultFlow = MutableSharedFlow<JdcrBleScanResult>(
        replay = 0,
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val scanCallback by lazy {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                this@JdcrBleScannerImpl.onScanResult(callbackType, result)
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                this@JdcrBleScannerImpl.onScanFailed(errorCode)
            }
        }
    }

    private fun startScanTask() {
        sendScanResultJob = coroutine.launch(scanResultSerial) {
            scanResultMap.clear()
            while (isActive) {
                delay(scanConfig.resultIntervalMills)
                val newResult = handleResult()
                scanResultFlow.emit(JdcrBleScanResult.ScanningList(newResult))
            }
        }

        fun mergeResult(result: ScanResult, connectPermission: Boolean) {
            val device = result.device
            val address = device.address
            val exist = scanResultMap[address] != null
            if (result.rssi < scanConfig.minRssi) {
                if (exist) {
                    JdcrBleLog.v("扫描结果,信号太弱,移除该设备:${device.address}")
                    scanResultMap.remove(address)
                }
            } else {
                val deviceName = if (connectPermission) device.name else null
                if (exist) {
                    JdcrBleLog.v("扫描结果,更新该设备:$deviceName,${device.address}")
                    scanResultMap[address] = ScanResultWrapper(result, deviceName)
                } else {
                    JdcrBleLog.v("扫描结果,添加该设备:$deviceName,${device.address}")
                    scanResultMap[address] = ScanResultWrapper(result, deviceName)
                }
            }
        }

        val ch = Channel<ScanResult>(
            capacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        scanResultChannel = ch
        handleScanResultJob = coroutine.launch(scanResultSerial) {
            val connectPermission = JdcrBlePermissionUtils.checkConnectPermission(context)
            try {
                for (result in ch) {
                    if (!isScanning) break
                    mergeResult(result, connectPermission)
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    JdcrBleLog.i("扫描结果处理任务取消")
                } else {
                    JdcrBleLog.e("处理扫描结果时异常", e)
                }
            }
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    override fun startScan(config: JdcrBleScanConfig?): Result<SharedFlow<JdcrBleScanResult>> {
        JdcrBleLog.i("触发蓝牙扫描")

        this.scanConfig = config ?: this.scanConfig

        fun startScanCountdown() {
            scanTimeoutJob = coroutine.launch {
                delay(scanConfig.timeoutFinish)
                if (isScanning) {
                    JdcrBleLog.w("扫描倒计时结束,停止扫描")
                    scanner.stopScan(scanCallback)
                    scanFinish(JdcrBleScanResult.Finish)
                }
            }
        }

        return runCatching {
            if (isScanning) {
                JdcrBleLog.w("正在扫描中,直接返回")
                return@runCatching scanResultFlow.asSharedFlow()
            }
            startScanTask()
            JdcrBleLog.i("执行系统扫描")
            isScanning = true
            scanner.startScan(scanConfig.scanFilters, scanConfig.settings, scanCallback)
            startScanCountdown()
            scanResultFlow.asSharedFlow()
        }.onFailure {
            scanFinish(
                JdcrBleScanResult.Failure(
                    JdcrBleScanResult.Failure.REASON_EXCEPTION,
                    t = it
                )
            )
        }

    }

    private fun onScanResult(callbackType: Int, result: ScanResult?) {
        if (!isScanning) return
        result ?: return
        scanResultChannel ?: return
        scanResultChannel?.trySend(result)
    }

    private fun onScanFailed(errorCode: Int) {
        JdcrBleLog.w("扫描失败:$errorCode")
        val state = JdcrBleScanResult.Failure(
            JdcrBleScanResult.Failure.REASON_ON_FAIL,
            errorCode
        )
        scanFinish(state)
    }

    private fun handleResult(): List<ScanResultWrapper> {
        val permission = JdcrBlePermissionUtils.checkConnectPermission(context)
        val copyResult = scanResultMap.values.toMutableList()
        fun clearDevice() {
            val currentBootMillis = SystemClock.elapsedRealtime()
            val iterator = copyResult.iterator()
            while (iterator.hasNext()) {
                val result = iterator.next()
                val deviceBootMillis = result.result.timestampNanos / 1000_000
                val interval = currentBootMillis - deviceBootMillis
                if (interval > scanConfig.expiredTimeMills) {
                    iterator.remove()
                    continue
                }
                if (scanConfig.filterNullName) {
                    if (permission && result.result.device.name.isNullOrEmpty()) {
                        iterator.remove()
                    }
                }
            }
        }

        fun sortByRssi() {
            copyResult.sortByDescending { it.result.rssi }
        }
        clearDevice()
        if (scanConfig.rssiSort) {
            sortByRssi()
        }
        JdcrBleLog.i("发送扫描结果:$copyResult")
        return copyResult
    }

    override fun getScanResult(address: String): ScanResultWrapper? {
        return scanResultMap[address]
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun stopScan() {
        if (!isScanning) return
        JdcrBleLog.i("手动停止扫描")
        try {
            scanner.stopScan(scanCallback)
        } catch (e: Exception) {
            JdcrBleLog.e("停止扫描异常", e)
        }
        scanFinish(JdcrBleScanResult.Finish)
    }

    private fun scanFinish(result: JdcrBleScanResult) {
        JdcrBleLog.i("扫描结束了,清理资源")
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        sendScanResultJob?.cancel()
        sendScanResultJob = null
        scanResultChannel?.close()
        scanResultChannel = null
        handleScanResultJob?.cancel()
        handleScanResultJob = null
        coroutine.launch(scanResultSerial) {
            isScanning = false
            scanResultMap.clear()
            scanResultFlow.tryEmit(result)
        }
    }

    override fun release() {
        if (JdcrBlePermissionUtils.checkScanPermission(context)) {
            stopScan()
        }
    }

}