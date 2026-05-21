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
import com.jdcr.jdcrble.util.JdcrBlePermissionUtils
import com.jdcr.jdcrblecommon.selftest.BluetoothDeviceTest
import com.jdcr.jdcrblecommon.selftest.MicrobitConstants
import com.jdcr.jdcrblecommon.ui.theme.JdcrBleCommonTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val address = MicrobitConstants.TEST_ADDRESS
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
                                .requestAllPermission(this@MainActivity) { allGranted, _ ->
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
                                    helper.startScan(10000).onSuccess {
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
                            helper.readTemperature(address)
                        }) {
                            Text(text = "读取温度")
                        }
                        Button(onClick = {
                            helper.writeTextToLed(address)
                        }) {
                            Text(text = "写入文案")
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