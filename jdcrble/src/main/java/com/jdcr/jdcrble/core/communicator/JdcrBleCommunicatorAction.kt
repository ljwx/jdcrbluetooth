package com.jdcr.jdcrble.core.communicator

import android.bluetooth.BluetoothGattCharacteristic
import androidx.annotation.IntDef
import java.util.UUID

sealed class JdcrBleCommunicatorAction(
    open val address: String,
    val key: String,
    val desc: String,
    open val tag: String?,
    val log: String = "$desc,$tag,$key",
) {
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
        override val tag: String? = null,
    ) : JdcrBleCommunicatorAction(
        address,
        getReadKey(address, serviceUUID, characterUUID),
        "读数据",
        tag
    )

    data class Write(
        override val address: String,
        val serviceUUID: UUID,
        val characterUUID: UUID,
        val writeData: ByteArray,
        @WriteType val writeType: Int = WRITE_TYPE_NO_RESPONSE,
        override val tag: String? = null
    ) : JdcrBleCommunicatorAction(
        address,
        getWriteKey(address, serviceUUID, characterUUID),
        "写数据",
        tag
    )

    data class RegisterNotification(
        override val address: String,
        val serviceUUID: UUID,
        val characterUUID: UUID,
        val descriptorUUID: UUID = StandardDescriptorUUID,
        val isIndicationValue: Boolean = false,
        val throttle: Long? = null,
        override val tag: String? = null
    ) :
        JdcrBleCommunicatorAction(
            address,
            getEnableNotifyKey(
                address,
                serviceUUID,
                characterUUID,
                descriptorUUID,
            ), "订通知", tag
        ) {
        companion object {
            val StandardDescriptorUUID =
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        }

        internal var lastUpdate: Long? = null

    }
}

sealed class JdcrBleCommunicatorActionResult(
    val desc: String,
    open val tag: String?,
    val log: String = "$desc,$tag",
) {

    data class Notification(
        val address: String,
        val serviceUUID: UUID?,
        val characterUUID: UUID,
        val descriptorUUID: UUID,
        override val tag: String?
    ) :
        JdcrBleCommunicatorActionResult("开启通知", tag)

    data class Read(
        val address: String,
        val serviceUUID: UUID?,
        val characterUUID: UUID,
        val result: ByteArray?,
        override val tag: String?
    ) :
        JdcrBleCommunicatorActionResult("读取数据", tag)

    data class Write(
        val address: String,
        val serviceUUID: UUID?,
        val characterUUID: UUID,
        override val tag: String?
    ) :
        JdcrBleCommunicatorActionResult("写入数据", tag)

}

data class NotificationData(
    val address: String,
    val serviceUuid: UUID?,
    val characterUuid: UUID,
    val value: ByteArray?
)