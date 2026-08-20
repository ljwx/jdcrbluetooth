package com.jdcr.jdcrble.core.communicator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class JdcrBleCommunicatorActionTest {

    private val address = "AA:BB:CC:DD:EE:FF"
    private val serviceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val characteristicUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    @Test
    fun actionsBuildKeysFromOperationDeviceAndAttribute() {
        val read = JdcrBleCommunicatorAction.Read(address, serviceUuid, characteristicUuid)
        val write = JdcrBleCommunicatorAction.Write(
            address,
            serviceUuid,
            characteristicUuid,
            byteArrayOf(1)
        )

        assertEquals("read_$address=$serviceUuid=$characteristicUuid", read.key)
        assertEquals("write_$address=$serviceUuid=$characteristicUuid", write.key)
        assertNotEquals(read.key, write.key)
    }

    @Test
    fun tagsDoNotChangeTheGattCallbackKey() {
        val first = JdcrBleCommunicatorAction.Read(address, serviceUuid, characteristicUuid, "first")
        val second = JdcrBleCommunicatorAction.Read(address, serviceUuid, characteristicUuid, "second")

        assertEquals(first.key, second.key)
        assertTrue(first.log.contains("first"))
        assertTrue(second.log.contains("second"))
    }

    @Test
    fun notificationUsesStandardCccdByDefault() {
        val action = JdcrBleCommunicatorAction.EnableNotification(
            address,
            serviceUuid,
            characteristicUuid,
            enable = true
        )

        assertEquals(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            action.descriptorUUID
        )
        assertEquals(
            "notify_$address=$serviceUuid=$characteristicUuid=${action.descriptorUUID}",
            action.key
        )
    }

    @Test
    fun writeDefaultsToNoResponse() {
        val action = JdcrBleCommunicatorAction.Write(
            address,
            serviceUuid,
            characteristicUuid,
            byteArrayOf(1, 2, 3)
        )

        assertEquals(JdcrBleCommunicatorAction.WRITE_TYPE_NO_RESPONSE, action.writeType)
    }
}
