package com.jdcr.jdcrble.state

import com.jdcr.jdcrble.core.scanner.ScanResultWrapper

sealed class JdcrBleScanResult(val desc: String) {

    data class ScanningList(
        val results: List<ScanResultWrapper>,
        val timestamp: Long = System.currentTimeMillis()
    ) : JdcrBleScanResult("扫描中,多个结果")

    data class ScanningSingle(
        val result: ScanResultWrapper,
        val timestamp: Long = System.currentTimeMillis()
    ) : JdcrBleScanResult("扫描中,单个结果")

    object Finish : JdcrBleScanResult("扫描结束")

    data class Failure(val reason: Int, val errorCode: Int? = null, val t: Throwable? = null) :
        JdcrBleScanResult("扫描失败:$reason") {

        companion object {
            const val REASON_EXCEPTION = 5000
            const val REASON_ON_FAIL = 5001
        }

    }

}

sealed class JdcrBleConnectState(
    val desc: String,
    val stateStep: Int,
) {

    companion object {
        const val INITIAL_STATUS = 0
    }

    object Void : JdcrBleConnectState("没有记录", INITIAL_STATUS)

    data class Connecting(
        val address: String,
        val timestamp: Long = System.currentTimeMillis(),
    ) : JdcrBleConnectState("Connecting,连接中,$address", 1)

    data class Connected(
        val address: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : JdcrBleConnectState("Connected,硬件连接成功,$address", 2)

    data class DiscoveredServices(
        val address: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : JdcrBleConnectState("DiscoveredServices,服务启动中,$address", 3)

    data class ModifyMtu(
        val address: String,
        val requestMut: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : JdcrBleConnectState("ModifyMtu,修改mtu中,$address", 4)

    data class Ready(
        val address: String,
        val mtu: Int?,
        val timestamp: Long = System.currentTimeMillis()
    ) : JdcrBleConnectState("Ready,通信服务已可用,$address", 5)

    data class Disconnecting(
        val address: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : JdcrBleConnectState("Disconnecting,断开连接中,$address", -1)

    data class Disconnected(
        val address: String,
        val status: Int,
        val reason: DisconnectReason,
        val timestamp: Long = System.currentTimeMillis(),
    ) : JdcrBleConnectState(
        "Disconnected,设备已断开,$address,reason:${reason.desc}",
        -2
    )

    sealed class DisconnectReason(val desc: String) {
        object Active : DisconnectReason("主动断开")
        object ConnectTimeout : DisconnectReason("连接超时")
        object DisconnectTimeout : DisconnectReason("断开超时")
        object BluetoothOff : DisconnectReason("系统蓝牙关闭")
        data class Remote(val status: Int) : DisconnectReason("链路异常:$status")
    }

}

sealed class JdcrBleAvailableState(val desc: String) {
    object BleUnSupport : JdcrBleAvailableState("不支持蓝牙")
    data class MissPermission(val permission: Array<String>) :
        JdcrBleAvailableState("缺少权限,权限列表:${permission.contentToString()}")

    object BleDisable : JdcrBleAvailableState("蓝牙未开启")
    object LocationDisable : JdcrBleAvailableState("定位未开启")
    object Ready : JdcrBleAvailableState("可用")
}