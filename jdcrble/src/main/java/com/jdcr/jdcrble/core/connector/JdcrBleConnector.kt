package com.jdcr.jdcrble.core.connector

import android.bluetooth.BluetoothDevice
import com.jdcr.jdcrble.config.JdcrBleConnectConfig
import com.jdcr.jdcrble.state.JdcrBleConnectState
import kotlinx.coroutines.flow.StateFlow

interface JdcrBleConnector {

    fun isConnect(address: String): Boolean

    fun connect(
        address: String,
        config: JdcrBleConnectConfig? = null
    ): Result<StateFlow<JdcrBleConnectState>?>

    fun connect(
        device: BluetoothDevice,
        config: JdcrBleConnectConfig? = null
    ): Result<StateFlow<JdcrBleConnectState>?>

    fun getFinalMtu(address: String): Int?

    fun disconnect(address: String)
    fun disconnectAll()
}