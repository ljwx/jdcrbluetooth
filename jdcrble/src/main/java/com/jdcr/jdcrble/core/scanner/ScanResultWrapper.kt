package com.jdcr.jdcrble.core.scanner

import android.bluetooth.le.ScanResult

class ScanResultWrapper(val result: ScanResult, val deviceName: String?) {

    override fun toString(): String {
        return "ScanResultWrapper(name=${deviceName},address=${result.device.address},rssi=${result.rssi})"
    }

}