package com.bytedance.perfmonitor.sdk
//定义参数设置
data class MonitorConfig(
    val smoothnessConfig: SmoothnessConfig = SmoothnessConfig(),
    val anrConfig: ANRConfig = ANRConfig(),
    val debugMode: Boolean = false
)

data class SmoothnessConfig(
    val sampleInterval: Long = 1000L,
    val lowFrameRateThreshold: Int = 45,
    val stuckThreshold: Long = 50L//帧间隔持续达到 50ms，才判定为卡顿
)

data class ANRConfig(
    val anrThreshold: Long = 5000L,// 对应系统标准的 5 秒 ANR 阈值
    val useSigquit: Boolean = true
)