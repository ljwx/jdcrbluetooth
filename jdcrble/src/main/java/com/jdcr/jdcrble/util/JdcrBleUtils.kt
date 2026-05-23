package com.jdcr.jdcrble.util

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationManagerCompat
import com.jdcr.jdcrble.config.JdcrLocationConfig
import com.jdcr.jdcrble.state.JdcrBleAvailableState

internal fun ScanResult.simpleInfo(deviceName: String?): String {
    return "${if (deviceName == null) "" else "名称:$deviceName,"}地址:${device.address},RSSI:${rssi},}"
}

object JdcrBleUtils {

    private fun isBleSupport(context: Context): Boolean {
        val manager =
            context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter != null
    }

    fun isBluetoothEnable(context: Context): Result<Boolean> {
        return if (JdcrBlePermissionUtils.checkConnectPermission(context)) {
            val manager =
                context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            Result.success(manager.adapter?.isEnabled == true)
        } else {
            Result.failure(IllegalStateException("没有蓝牙连接权限"))
        }
    }

    fun isLocationEnable(context: Context): Result<Boolean> {
        val manager =
            context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return Result.success(LocationManagerCompat.isLocationEnabled(manager))
    }

    fun isNeedLocationFeature(forceLocationEnable: Boolean): Boolean {
        if (forceLocationEnable) {
            return true //有些机型,定位开启才能扫描出设备
        } else {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.S && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        }
    }

    fun getBleAvailableState(
        context: Context,
        location: JdcrLocationConfig
    ): JdcrBleAvailableState {
        if (!isBleSupport(context)) return JdcrBleAvailableState.BleUnSupport

        val permissionResult =
            JdcrBlePermissionUtils.getMissPermission(context, location)
        if (permissionResult.isNotEmpty()) {
            return JdcrBleAvailableState.MissPermission(permissionResult)
        }

        if (!isBluetoothEnable(context).getOrElse { false }) {
            return JdcrBleAvailableState.BleDisable
        }

        if (isNeedLocationFeature(location.enableLocationFeature)) {
            if (!isLocationEnable(context).getOrElse { false }) {
                return JdcrBleAvailableState.LocationDisable
            }
        }

        return JdcrBleAvailableState.Ready
    }

}