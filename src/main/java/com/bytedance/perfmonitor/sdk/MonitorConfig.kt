package com.bytedance.perfmonitor.sdk

data class MonitorConfig(
    val smoothnessConfig: SmoothnessConfig = SmoothnessConfig(),
    val anrConfig: ANRConfig = ANRConfig(),
    val debugMode: Boolean = false
)

data class SmoothnessConfig(
    val sampleInterval: Long = 1000L,
    val lowFrameRateThreshold: Int = 45,
    val stuckThreshold: Long = 50L
)

data class ANRConfig(
    val anrThreshold: Long = 5000L,
    val useSigquit: Boolean = true
)