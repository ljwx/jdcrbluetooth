package com.jdcr.jdcrble.available

interface JdcrBleReceiver : AutoCloseable {

    fun isRegistered(): Boolean

    fun register(listener: ((enable: Result<Boolean>) -> Unit))

    fun unregister()

}