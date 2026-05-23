package com.jdcr.jdcrble.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.jdcr.jdcrble.config.JdcrLocationConfig

object JdcrBlePermissionUtils {

    private fun getLocationPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private fun getBluetoothPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
//                Manifest.permission.BLUETOOTH_ADVERTISE // 如果需要广播才加
        )
    }

    fun getAllPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getBluetoothPermissions()
        } else {
            // Android 11- 蓝牙必须的定位权限
            getLocationPermissions()
        }
    }

    fun getScanPermission(): Array<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            null
        }
    }

    fun getConnectPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            //Android 12+：检查BLUETOOTH_CONNECT权限
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            //Android 12以下：BLUETOOTH/BLUETOOTH_ADMIN是普通权限，无需动态检查
            null
        }
    }

    fun checkScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasFine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            hasFine || hasCoarse
        } else {
            true
        }
    }

    fun checkConnectPermission(context: Context): Boolean {
        getConnectPermission()?.let {
            return ContextCompat.checkSelfPermission(
                context, it
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun isMissBlePermission(permissions: Array<String>): Boolean {
        permissions.forEach {
            if (it == Manifest.permission.BLUETOOTH_SCAN) {
                return true
            }
            if (it == Manifest.permission.BLUETOOTH_CONNECT) {
                return true
            }
        }
        return false
    }

    fun isMissLocationPermission(forceFineLocation: Boolean, permissions: Array<String>): Boolean {
        permissions.forEach {
            if (it == Manifest.permission.ACCESS_FINE_LOCATION) {
                return true
            }
            if (!forceFineLocation && it == Manifest.permission.ACCESS_COARSE_LOCATION) {
                return true
            }
        }
        return false
    }

    fun getMissPermission(
        context: Context,
        location: JdcrLocationConfig
    ): Array<String> {
        val missingList = mutableListOf<String>()

        fun location(): List<String> {
            val locationList = mutableListOf<String>()
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (location.forceFineLocation && !hasFine) {
                locationList.add(Manifest.permission.ACCESS_FINE_LOCATION)
            } else if (!hasFine && !hasCoarse) {
                // 通常申请精度高的即可
                locationList.add(Manifest.permission.ACCESS_FINE_LOCATION)
                locationList.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            return locationList
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // --- Android 12 (API 31) 及以上 ---
            getBluetoothPermissions().forEach {
                if (ContextCompat.checkSelfPermission(
                        context,
                        it
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    missingList.add(it)
                }
            }
            if (location.forceLocationPermission) {
                missingList.addAll(location())
            }
            return missingList.toTypedArray()
        } else {
            // --- Android 11 及以下 ---
            // 必须有定位权限才能搜到 BLE 设备
            val locationPermissions = location()
            if (locationPermissions.isNotEmpty()) {
                // 通常申请精度高的即可
                missingList.addAll(locationPermissions)
            }
            return missingList.toTypedArray()
        }
    }

}