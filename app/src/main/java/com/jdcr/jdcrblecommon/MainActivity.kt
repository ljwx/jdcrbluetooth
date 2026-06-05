package com.jdcr.jdcrblecommon

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.jdcr.jdcrble.util.JdcrBleLog
import com.jdcr.jdcrble.util.JdcrBlePermissionUtils
import com.jdcr.jdcrblecommon.selftest.BluetoothDeviceTest
import com.jdcr.jdcrblecommon.selftest.BluetoothDeviceTudaoTest
import com.jdcr.jdcrblecommon.selftest.MicrobitConstants
import com.jdcr.jdcrblecommon.selftest.TudaoConstants
import com.jdcr.jdcrblecommon.ui.theme.JdcrBleCommonTheme
import com.jdcr.jdcrlog.JdcrLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        JdcrLog.enable(true)
//        val address = TudaoConstants.TEST_ADDRESS
//        val helper = BluetoothDeviceTudaoTest
        val address = MicrobitConstants.TEST_ADDRESS_2
        val helper = BluetoothDeviceTest
        helper.init(this)
        var scanJob: Job? = null
        setContent {
            JdcrBleCommonTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column {
                        Greeting(
                            name = "Android2",
                            modifier = Modifier.padding(innerPadding)
                        )
                        Button(onClick = {
                            helper.getHelper()
                                .requestMissPermissions(this@MainActivity) { result ->
                                    helper.getHelper().changeAvailableState()
                                }
                        }) {
                            Text(text = "请求所有权限")
                        }
                        Button(onClick = {
                            helper.getHelper().openBleEnableSetting(this@MainActivity) {
                                helper.getHelper().changeAvailableState()
                            }
                        }) {
                            Text(text = "跳转蓝牙开关")
                        }
                        Button(onClick = {
                            helper.getHelper().openLocationEnableSetting(this@MainActivity) {
                                helper.getHelper().changeAvailableState()
                            }
                        }) {
                            Text(text = "跳转定位开关")
                        }
                        Button(onClick = {
                            helper.getHelper().openPermissionSetting(this@MainActivity) {
                                helper.getHelper().changeAvailableState()
                            }
                        }) {
                            Text(text = "跳转权限开关")
                        }
                        Button(onClick = {
                            if (JdcrBlePermissionUtils.checkScanPermission(applicationContext)) {
                                scanJob = lifecycleScope.launch {
                                    helper.startScan().onSuccess {
                                        it.collect {
//                                            JdcrBleLog.i("扫描结果:$it")
                                        }
                                    }
                                }
                            }
                        }) {
                            Text(text = "扫描")
                        }
                        Button(onClick = {
                            scanJob?.cancel()
                            helper.stopScan()
                        }) {
                            Text(text = "停止扫描")
                        }
                        Button(onClick = {
                            if (JdcrBlePermissionUtils.checkConnectPermission(applicationContext)) {
                                lifecycleScope.launch {
                                    helper.connect(address)
                                }
                            }
                        }) {
                            Text(text = "连接")
                        }
                        Button(onClick = {
                            helper.disconnect(address)
                        }) {
                            Text(text = "断开连接")
                        }
                        Button(onClick = {
//                            helper.motorDualForward(address)
                        }) {
                            Text(text = "双轮前进")
                        }
                        Button(onClick = {
//                            helper.motorForward(address)
                        }) {
                            Text(text = "M1 正转")
                        }
                        Button(onClick = {
//                            helper.stopMotor(address)
                        }) {
                            Text(text = "停止电机")
                        }
                        Button(onClick = {
                            helper.disableNotify(address)
                        }) {
                            Text(text = "关闭通知")
                        }
                        Button(onClick = {
                            JdcrBleLog.i(helper.getScanResult(address)?.result?.rssi?.toString())
                        }) {
                            Text(text = "信号强度")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    JdcrBleCommonTheme {
        Greeting("Android")
    }
}