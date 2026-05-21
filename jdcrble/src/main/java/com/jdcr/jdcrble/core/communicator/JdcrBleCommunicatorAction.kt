package com.jdcr.jdcrble.core.communicator

import android.bluetooth.BluetoothGattCharacteristic
import androidx.annotation.IntDef
import java.util.UUID

sealed class JdcrBleCommunicatorAction(open val address: String, val key: String) {
    companion object {

        const val WRITE_TYPE_DEFAULT = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        const val WRITE_TYPE_NO_RESPONSE = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        const val WRITE_TYPE_SIGNED = BluetoothGattCharacteristic.WRITE_TYPE_SIGNED

        @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
        @IntDef(WRITE_TYPE_DEFAULT, WRITE_TYPE_NO_RESPONSE, WRITE_TYPE_SIGNED)
        @Retention(AnnotationRetention.SOURCE)
        annotation class WriteType

        internal fun getReadKey(address: String, serviceUUID: UUID?, characterUUID: UUID): String {
            return "read_$address=$serviceUUID=$characterUUID"
        }

        internal fun getWriteKey(address: String, serviceUUID: UUID?, characterUUID: UUID): String {
            return "write_$address=$serviceUUID=$characterUUID"
        }

        internal fun getEnableNotifyKey(
            address: String,
            serviceUUID: UUID?,
            characterUUID: UUID,
            descriptorUUID: UUID
        ): String {
            return "notify_$address=$serviceUUID=$characterUUID=$descriptorUUID"
        }
    }

    data class Read(
        override val address: String,
        val serviceUUID: UUID,
        val characterUUID: UUID,
    ) : JdcrBleCommunicatorAction(address, getReadKey(address, serviceUUID, characterUUID))

    data class Write(
        override val address: String,
        val serviceUUID: UUID,
        val characterUUID: UUID,
        val writeData: ByteArray,
        @WriteType val writeType: Int? = null,
    ) : JdcrBleCommunicatorAction(address, getWriteKey(address, serviceUUID, characterUUID))

    data class RegisterNotification(
        override val address: String,
        val serviceUUID: UUID,
        val characterUUID: UUID,
        val descriptorUUID: UUID = StandardDescriptorUUID,
        val isIndicationValue: Boolean = false,
        val interval: Long = 50,
    ) :
        JdcrBleCommunicatorAction(
            address,
            getEnableNotifyKey(
                address,
                serviceUUID,
                characterUUID,
                descriptorUUID
            )
        ) {
        companion object {
            val StandardDescriptorUUID =
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        }
    }
}

sealed class JdcrBleCommunicatorActionResult {

    data class Notification(
        val address: String,
        val serviceUUID: UUID?,
        val characterUUID: UUID,
        val descriptorUUID: UUID,
    ) :
        JdcrBleCommunicatorActionResult()

    data class Read(
        val address: String,
        val serviceUUID: UUID?,
        val characterUUID: UUID,
        val result: ByteArray?
    ) :
        JdcrBleCommunicatorActionResult()

    data class Write(
        val address: String,
        val serviceUUID: UUID?,
        val characterUUID: UUID,
    ) :
        JdcrBleCommunicatorActionResult()

}

data class NotificationData(
    val address: String,
    val serviceUuid: UUID?,
    val characterUuid: UUID,
    val value: ByteArray?
)