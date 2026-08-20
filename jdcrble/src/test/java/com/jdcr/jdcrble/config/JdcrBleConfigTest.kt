package com.jdcr.jdcrble.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JdcrBleConfigTest {

    @Test
    fun libraryDefaultsMatchBleProtocolAndTimeoutExpectations() {
        val config = JdcrBleConfig()

        assertEquals(23, MTU_DEFAULT_SIZE)
        assertEquals(3, MTU_PLACEHOLDER)
        assertEquals(3, config.maxConnectDevice)
        assertEquals(10_000L, config.connectTimeoutMs)
        assertEquals(6_000L, config.disconnectTimeoutMs)
        assertEquals(7_000L, config.communicate.timeoutMills)
        assertTrue(config.location.forceLocationFeature)
        assertFalse(config.location.forceLocationPermission)
    }

    @Test
    fun connectionDefaultsDoNotRequestMtuOrAutoConnect() {
        val config = JdcrBleConnectConfig()

        assertNull(config.mtu)
        assertFalse(config.autoConnect)
    }
}
