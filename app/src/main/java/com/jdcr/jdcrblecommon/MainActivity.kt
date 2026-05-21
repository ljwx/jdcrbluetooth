package com.jdcr.jdcrblecommon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.jdcr.jdcrble.JdcrBleHelper
import com.jdcr.jdcrble.util.JdcrBleLog
import com.jdcr.jdcrble.util.JdcrBlePermissionUtils
import com.jdcr.jdcrblecommon.ui.theme.JdcrBleCommonTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val helper = JdcrBleHelper(applicationContext)
        lifecycleScope.launch {
            helper.init().apply {
                getAvailableState().collect {
                    JdcrBleLog.i("蓝牙状态:$it")
                }
            }
        }
        setContent {
            JdcrBleCommonTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column {
                        Greeting(
                            name = "Android2",
                            modifier = Modifier.padding(innerPadding)
                        )
                        Button(onClick = {
                            helper.requestAllPermission(this@MainActivity) { allGranted, _ ->
                                helper.changeAvailableState()
                            }
                        }) {
                            Text(text = "请求所有权限")
                        }
                        Button(onClick = {
                            helper.openBleEnableSetting(this@MainActivity) {
                                helper.changeAvailableState()
                            }
                        }) {
                            Text(text = "跳转蓝牙开关")
                        }
                        Button(onClick = {
                            helper.openLocationEnableSetting(this@MainActivity) {
                                helper.changeAvailableState()
                            }
                        }) {
                            Text(text = "跳转定位开关")
                        }
                        Button(onClick = {
                            helper.openPermissionSetting(this@MainActivity) {
                                helper.changeAvailableState()
                            }
                        }) {
                            Text(text = "跳转权限开关")
                        }
                        Button(onClick = {
                            if (JdcrBlePermissionUtils.checkScanPermission(applicationContext)) {
                                lifecycleScope.launch {
                                    helper.startScan(60000).onSuccess {
                                        it.collect {
                                            JdcrBleLog.i("扫描结果:$it")
                                        }
                                    }
                                }
                            }
                        }) {
                            Text(text = "扫描")
                        }
                        Button(onClick = {
                            helper.stopScan()
                        }) {
                            Text(text = "停止扫描")
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