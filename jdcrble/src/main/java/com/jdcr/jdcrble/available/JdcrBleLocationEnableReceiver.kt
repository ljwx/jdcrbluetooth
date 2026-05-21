package com.jdcr.jdcrble.available

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import com.jdcr.jdcrble.util.JdcrBleLog
import com.jdcr.jdcrble.util.JdcrBleUtils

class JdcrBleLocationEnableReceiver(context: Context) :
    BroadcastReceiver(), JdcrBleReceiver {

    private val applicationContext = context.applicationContext

    @Volatile
    private var listener: ((enable: Result<Boolean>) -> Unit)? = null

    @Volatile
    private var isRegistered = false

    override fun isRegistered(): Boolean {
        return isRegistered
    }

    @Synchronized
    override fun register(listener: (enable: Result<Boolean>) -> Unit) {
        this.listener = listener
        if (!isRegistered) {
            JdcrBleLog.i("注册定位开关广播")
            isRegistered = true
            val intentFilter = IntentFilter().apply {
                addAction(LocationManager.MODE_CHANGED_ACTION)
                addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationContext.registerReceiver(
                    this,
                    intentFilter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                applicationContext.registerReceiver(this, intentFilter)
            }
        } else {
            JdcrBleLog.i("定位广播已经注册，不执行重复注册")
        }
        val result = JdcrBleUtils.isLocationEnable(applicationContext)
        callListener(result)
    }

    @Synchronized
    override fun unregister() {
        JdcrBleLog.i("注销定位开关广播")
        if (isRegistered) {
            try {
                applicationContext.unregisterReceiver(this)
            } catch (e: Exception) {
                JdcrBleLog.e("注销定位开关广播异常", e)
            }
            isRegistered = false
            listener = null
        }
    }

    override fun onReceive(context: Context?, intent: Intent) {
        if (intent.action == LocationManager.MODE_CHANGED_ACTION || intent.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
            context ?: return
            val isLocationEnable = JdcrBleUtils.isLocationEnable(context)
            JdcrBleLog.i("定位开关变化,是否开启:$isLocationEnable")
            callListener(isLocationEnable)
        }
    }

    private fun callListener(enable: Result<Boolean>) {
        if (enable.isFailure) {
            JdcrBleLog.w("获取定位是否开启失败", enable.exceptionOrNull())
        } else {
            JdcrBleLog.i("定位开关状态:$enable")
        }
        listener?.invoke(enable)
    }

    override fun close() {
        JdcrBleLog.i("关闭定位开关广播")
        unregister()
    }

}