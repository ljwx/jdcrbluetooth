package com.jdcr.jdcrble.core.communicator

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import com.jdcr.jdcrble.config.MTU_DEFAULT_SIZE
import com.jdcr.jdcrble.config.MTU_PLACEHOLDER
import com.jdcr.jdcrble.exception.JdcrBleCommunicationException
import com.jdcr.jdcrble.util.JdcrBleLog
import com.jdcr.jdcrble.util.JdcrBlePermissionUtils
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class JdcrBleActionWrapper(
    val action: JdcrBleCommunicatorAction,
    val onComplete: ((Result<JdcrBleCommunicatorActionResult>) -> Unit)?,
    val inMainThread: Boolean
)

class JdcrBleCommunicatorImpl(
    private val context: Context,
    private val coroutine: CoroutineScope
) : JdcrBleCommunicator {

    private val gatts = ConcurrentHashMap<String, BluetoothGatt>()

    private var actionChannelMap = ConcurrentHashMap<String, Channel<JdcrBleActionWrapper>>()

    @Volatile
    private var currentAction: JdcrBleCommunicatorAction? = null
    private val pendingActions =
        ConcurrentHashMap<String, CancellableContinuation<Result<JdcrBleCommunicatorActionResult>>>()

    private val notification = MutableSharedFlow<NotificationData>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var dataMaxSize = MTU_DEFAULT_SIZE - MTU_PLACEHOLDER

    private fun initChannel(address: String) {

        fun resultComplete(
            wrapper: JdcrBleActionWrapper,
            result: Result<JdcrBleCommunicatorActionResult>
        ) {
            wrapper.onComplete?.let {
                coroutine.launch {
                    runCatching {
                        if (wrapper.inMainThread) {
                            withContext(Dispatchers.Main) {
                                it(result)
                            }
                        } else {
                            it(result)
                        }
                    }.onFailure {
                        JdcrBleLog.e("通讯完成回调执行异常", it)
                    }
                }
            }
        }

        fun looper(channel: Channel<JdcrBleActionWrapper>) {
            coroutine.launch {
                try {
                    for (wrapper in channel) {
                        val action = wrapper.action
                        val address = action.address
                        currentAction = action
                        val gatt = this@JdcrBleCommunicatorImpl.gatts[address]
                        if (gatt == null) {
                            "gatt为空,无法执行通讯:$address".let {
                                JdcrBleLog.w(it)
                                resultComplete(
                                    wrapper,
                                    Result.failure(JdcrBleCommunicationException(it))
                                )
                            }
                            continue
                        }
                        val result = when (action) {
                            is JdcrBleCommunicatorAction.Read -> {
                                JdcrBleLog.i("执行指令,读取:${action.tag},${action.key}")
                                read(gatt, action)
                            }

                            is JdcrBleCommunicatorAction.RegisterNotification -> {
                                JdcrBleLog.i("执行指令,注册通知:${action.tag},${action.key}")
                                registerNotification(gatt, action)
                            }

                            is JdcrBleCommunicatorAction.Write -> {
                                JdcrBleLog.i("执行指令,写入:${action.tag},${action.key}")
                                write(gatt, action)
                            }
                        }
                        resultComplete(wrapper, result)
                    }
                } catch (e: Exception) {
                    JdcrBleLog.e("执行通信出现异常", e)
                }
            }
        }

        val permission =
            JdcrBlePermissionUtils.checkConnectPermission(context.applicationContext)
        if (!permission) {
            JdcrBleLog.w("没有蓝牙连接权限,无法执行通信操作")
            return
        }
        var actionChannel = actionChannelMap[address]
        if (actionChannel == null) {
            synchronized(this) {
                val channel: Channel<JdcrBleActionWrapper> = Channel(
                    capacity = 150,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
                actionChannelMap[address] = channel
                JdcrBleLog.i("创建通信通道:$address")
                looper(channel)
            }
        }
    }

    fun setGatt(address: String, gatt: BluetoothGatt) {
        this.gatts[address] = gatt
    }

    fun removeGatt(address: String) {
        this.gatts.remove(address)
    }

    fun setMaxDataSize(size: Int) {
        this.dataMaxSize = size
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun sendAction(
        action: JdcrBleCommunicatorAction,
        inMainThread: Boolean,
        onComplete: ((Result<JdcrBleCommunicatorActionResult>) -> Unit)?
    ) {
        JdcrBleLog.i("收到指令:${action.tag},${action.key}")
        initChannel(action.address)
        coroutine.launch {
            actionChannelMap[action.address]?.send(
                JdcrBleActionWrapper(action, onComplete, inMainThread)
            )
        }
    }

    internal fun getCurrentAction(): JdcrBleCommunicatorAction? {
        return currentAction
    }

    private suspend fun getActionCancellableCoroutine(action: JdcrBleCommunicatorAction): Result<JdcrBleCommunicatorActionResult> {
        return suspendCancellableCoroutine { continuation ->
            pendingActions[action.key] = continuation
            continuation.invokeOnCancellation {
                pendingActions.remove(action.key)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun read(
        gatt: BluetoothGatt,
        action: JdcrBleCommunicatorAction.Read
    ): Result<JdcrBleCommunicatorActionResult> {
        val character = gatt.getService(action.serviceUUID)?.getCharacteristic(action.characterUUID)
        if (character == null) {
            "特征值为空,读取失败:${action.tag},${action.characterUUID}".let {
                JdcrBleLog.w(it)
                return Result.failure(JdcrBleCommunicationException(it))
            }
        } else {
            val success = gatt.readCharacteristic(character)
            if (!success) {
                "请求读取数据操作失败:${action.tag},${action.characterUUID}".let {
                    JdcrBleLog.w(it)
                    return Result.failure(JdcrBleCommunicationException(it))
                }
            }
            return getActionCancellableCoroutine(action)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun writeSingle(
        character: BluetoothGattCharacteristic,
        action: JdcrBleCommunicatorAction.Write,
        gatt: BluetoothGatt,
        packet: ByteArray
    ): Result<JdcrBleCommunicatorActionResult> {
        val properties = character.properties
        val writeType = action.writeType
            ?: when {
                properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 -> {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }

                properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 -> {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }

                else -> {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
            }
        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt.writeCharacteristic(character, packet, writeType)
            val success = status == BluetoothStatusCodes.SUCCESS
            success
        } else {
            character.value = packet
            character.writeType = writeType
            val success = gatt.writeCharacteristic(character)
            success
        }
        if (!success) {
            "请求写入数据操作失败:${action.tag},${action.characterUUID}".let {
                JdcrBleLog.w(it)
                return Result.failure(JdcrBleCommunicationException(it))
            }
        }
        JdcrBleLog.i("写入数据完成:${action.tag},${action.characterUUID}")
        return getActionCancellableCoroutine(action)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun write(
        gatt: BluetoothGatt,
        action: JdcrBleCommunicatorAction.Write
    ): Result<JdcrBleCommunicatorActionResult> {
        val character = gatt.getService(action.serviceUUID)?.getCharacteristic(action.characterUUID)
        val packet = action.writeData
        if (character != null) {
            if (packet.size > dataMaxSize) {
                var finalResult: Result<JdcrBleCommunicatorActionResult>? = null
                packet.toList()
                    .chunked(dataMaxSize)
                    .map { it.toByteArray() }.forEachIndexed { index, bytes ->
                        JdcrBleLog.i("分片写入,$index")
                        val result = writeSingle(character, action, gatt, bytes)
                        finalResult = result
                        if (!result.isSuccess) {
                            return result
                        }
                    }
                return finalResult ?: Result.failure(NullPointerException("分片写入没有结果"))
            } else {
                return writeSingle(character, action, gatt, packet)
            }
        } else {
            "特征值为空,写入失败:${action.tag},${action.characterUUID}".let {
                JdcrBleLog.w(it)
                return Result.failure(JdcrBleCommunicationException(it))
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun registerNotification(
        gatt: BluetoothGatt,
        action: JdcrBleCommunicatorAction.RegisterNotification
    ): Result<JdcrBleCommunicatorActionResult> {
        val character = gatt.getService(action.serviceUUID)?.getCharacteristic(action.characterUUID)
        if (character != null) {
            JdcrBleLog.i("设置通知特征值:${action.tag},${action.characterUUID}")
            gatt.setCharacteristicNotification(character, true)
            val value =
                if (action.isIndicationValue) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val descriptor = character.getDescriptor(action.descriptorUUID)
            if (descriptor != null) {
                val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = gatt.writeDescriptor(descriptor, value)
                    result == 0
                } else {
                    descriptor.value = value
                    gatt.writeDescriptor(descriptor)
                }
                if (!writeResult) {
                    "通知描述符写入指令失败:${action.tag},${action.characterUUID}".let {
                        JdcrBleLog.w(it)
                        return Result.failure(JdcrBleCommunicationException(it))
                    }
                }
                JdcrBleLog.i("通知描述符写入成功:${action.tag},${action.characterUUID}")
                return getActionCancellableCoroutine(action)
            } else {
                "描述符为空,注册通知失败:${action.tag},${action.descriptorUUID}".let {
                    JdcrBleLog.w(it)
                    return Result.failure(JdcrBleCommunicationException(it))
                }
            }
        } else {
            "特征值为空,注册通知失败:${action.tag},${action.characterUUID}".let {
                JdcrBleLog.w(it)
                return Result.failure(JdcrBleCommunicationException(it))
            }
        }
    }

    internal fun onNotification(data: NotificationData) {
        notification.tryEmit(data)
    }

    override fun getNotificationDataFlow(): SharedFlow<NotificationData> {
        return notification
    }

    internal fun onActionResult(
        success: Boolean,
        key: String,
        actionResult: JdcrBleCommunicatorActionResult
    ) {
        pendingActions.remove(key)?.apply {
            if (success) {
                resume(Result.success(actionResult), null)
            } else {
                "蓝牙服务回调返回失败:$key".let {
                    JdcrBleLog.w(it)
                    resume(Result.failure(JdcrBleCommunicationException(it)), null)
                }
            }
        }
    }

    fun clearAction(address: String) {
        actionChannelMap[address]?.close()
        actionChannelMap.remove(address)
    }

    override fun release() {
        JdcrBleLog.w("通信资源释放")
        gatts.clear()
        actionChannelMap.iterator().apply {
            while (hasNext()) {
                next().value.close()
                remove()
            }
        }
        pendingActions.iterator().apply {
            while (hasNext()) {
                next().value.cancel()
                remove()
            }
        }
    }

}