package com.bytedance.perfmonitor.sdk

object PerformanceMonitor {

    private var isInitialized = false
    private var config: MonitorConfig = MonitorConfig()

    //@Synchronized锁,同步锁，确保在多线程环境下初始化安全
    @Synchronized
    fun init(config: MonitorConfig = MonitorConfig()) {
        if (isInitialized) {
            LogUtil.w(message = "PerformanceMonitor already initialized")
            return
        }
        this.config = config
        isInitialized = true

        LogUtil.setDebug(config.debugMode)
        SmoothnessMonitor.init(config)
        ANRMonitor.init(config)

        LogUtil.d(message = "PerformanceMonitor initialized")
    }

    @Synchronized
    fun start() {
        if (!isInitialized) {
            throw IllegalStateException("PerformanceMonitor not initialized. Call init() first.")
        }
        SmoothnessMonitor.start()
        ANRMonitor.start()
        LogUtil.d(message = "PerformanceMonitor started")
    }

    @Synchronized
    fun stop() {
        SmoothnessMonitor.stop()
        ANRMonitor.stop()
        LogUtil.d(message = "PerformanceMonitor stopped")
    }

    fun getMonitorData(): MonitorData {
        return MonitorData(
            status = if (isInitialized) "Initialized" else "Not Initialized",
            smoothnessData = SmoothnessMonitor.getData(),
            anrData = ANRMonitor.getData()
        )
    }

    fun setCallback(callback: MonitorCallback) {
        SmoothnessMonitor.setCallback(callback)
        ANRMonitor.setCallback(callback)
    }
}

interface MonitorCallback {
    //检测到卡顿时，stackTrace 参数传快照
    fun onFrameStuck(duration: Long, stackTrace: String) {}
    //检测ANR时，传快照
    fun onANRDetected(delay: Long, stackTrace: String, logLine: String?) {}
    fun onLowFrameRate(fps: Int) {}
}

data class MonitorData(
    val status: String,
    val smoothnessData: SmoothnessMonitor.SmoothnessData,
    val anrData: ANRMonitor.ANRData
)