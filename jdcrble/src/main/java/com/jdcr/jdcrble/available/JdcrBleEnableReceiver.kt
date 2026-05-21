package com.jdcr.jdcrble.available

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.jdcr.jdcrble.util.JdcrBleLog
import com.jdcr.jdcrble.util.JdcrBleUtils

class JdcrBleEnableReceiver(context: Context) :
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
            JdcrBleLog.i("注册蓝牙开关广播")
            isRegistered = true
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationContext.registerReceiver(
                    this,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                applicationContext.registerReceiver(this, filter)
            }
        } else {
            JdcrBleLog.i("蓝牙开关广播已经注册，不执行重复注册")
        }
        val result = JdcrBleUtils.isBluetoothEnable(applicationContext)
        callListener(result)
    }

    @Synchronized
    override fun unregister() {
        JdcrBleLog.i("注销蓝牙开关广播")
        if (isRegistered) {
            try {
                applicationContext.unregisterReceiver(this)
            } catch (e: Exception) {
                JdcrBleLog.e("注销蓝牙开关广播异常", e)
            }
            isRegistered = false
            listener = null
        }
    }

    override fun onReceive(context: Context?, intent: Intent) {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            JdcrBleLog.i("蓝牙开关变化:$state(12开,10关)")
            when (state) {
                BluetoothAdapter.STATE_ON -> callListener(Result.success(true))
                BluetoothAdapter.STATE_TURNING_ON -> {
                    JdcrBleLog.i("蓝牙正在开启中")
                }

                BluetoothAdapter.STATE_TURNING_OFF -> {
                    JdcrBleLog.i("蓝牙正在关闭中")
                }

                BluetoothAdapter.STATE_OFF -> callListener(Result.success(false))
            }
        }
    }

    private fun callListener(enable: Result<Boolean>) {
        if (enable.isFailure) {
            JdcrBleLog.w("获取蓝牙是否开启失败", enable.exceptionOrNull())
        } else {
            JdcrBleLog.i("蓝牙开关状态:$enable")
        }
        listener?.invoke(enable)
    }

    override fun close() {
        JdcrBleLog.i("关闭蓝牙开关广播")
        unregister()
    }

}