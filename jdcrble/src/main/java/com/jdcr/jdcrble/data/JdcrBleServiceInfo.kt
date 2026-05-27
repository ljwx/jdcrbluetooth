package com.jdcr.jdcrble.data

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

data class JdcrBleServiceInfo(
    val uuid: UUID,
    val characteristics: List<JdcrBleCharacterInfo>
)

data class JdcrBleCharacterInfo(
    val uuid: UUID,
    val properties: Int,
    val descriptorUuids: List<UUID>
) {
    val canRead: Boolean get() = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
    val canWrite: Boolean get() = properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
    val canWriteNoResponse: Boolean get() = properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
    val canNotify: Boolean get() = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    val canIndicate: Boolean get() = properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
}