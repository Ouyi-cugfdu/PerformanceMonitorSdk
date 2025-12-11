package com.bytedance.perfmonitor.sdk

object ANRMonitor {

    private var isInitialized = false
    private var isStarted = false
    private var config: MonitorConfig? = null
    private var callback: MonitorCallback? = null

    fun init(config: MonitorConfig) {
        if (isInitialized) return
        this.config = config
        isInitialized = true
        LogUtil.d(message = "ANRMonitor initialized")
    }

    fun start() {
        if (!isInitialized || isStarted) return
        isStarted = true
        LogUtil.d(message = "ANRMonitor started")
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        LogUtil.d(message = "ANRMonitor stopped")
    }

    fun setCallback(callback: MonitorCallback) {
        this.callback = callback
    }

    fun getData(): ANRData {
        return ANRData(
            isMonitoring = isStarted,
            useSigquit = config?.anrConfig?.useSigquit ?: false,
            threshold = config?.anrConfig?.anrThreshold ?: 5000L
        )
    }

    data class ANRData(
        val isMonitoring: Boolean,
        val useSigquit: Boolean,
        val threshold: Long
    )
}