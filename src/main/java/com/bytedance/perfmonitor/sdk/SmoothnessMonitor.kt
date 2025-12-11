package com.bytedance.perfmonitor.sdk

object SmoothnessMonitor {

    private var isInitialized = false
    private var isStarted = false
    private var config: MonitorConfig? = null
    private var callback: MonitorCallback? = null
    private var frameRate: Int = 60

    fun init(config: MonitorConfig) {
        if (isInitialized) return
        this.config = config
        isInitialized = true
        LogUtil.d(message = "SmoothnessMonitor initialized")
    }

    fun start() {
        if (!isInitialized || isStarted) return
        isStarted = true
        frameRate = 60
        LogUtil.d(message = "SmoothnessMonitor started")
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        LogUtil.d(message = "SmoothnessMonitor stopped")
    }

    fun setCallback(callback: MonitorCallback) {
        this.callback = callback
    }

    fun getData(): SmoothnessData {
        return SmoothnessData(
            isMonitoring = isStarted,
            frameRate = frameRate,
            isNormal = frameRate >= (config?.smoothnessConfig?.lowFrameRateThreshold ?: 45)
        )
    }

    data class SmoothnessData(
        val isMonitoring: Boolean,
        val frameRate: Int,
        val isNormal: Boolean
    )
}