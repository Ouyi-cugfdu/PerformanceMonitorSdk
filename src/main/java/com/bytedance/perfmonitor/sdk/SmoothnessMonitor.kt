package com.bytedance.perfmonitor.sdk

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
//计算帧率和检测掉帧（卡顿）情况
object SmoothnessMonitor {

    // 状态变量
    private var isInitialized = false
    private var isStarted = false
    private var config: MonitorConfig? = null
    private var callback: MonitorCallback? = null

    // 帧率计算相关
    private val frameTimes = CopyOnWriteArrayList<Long>()
    private var lastFrameTimeNanos: Long = 0
    private var frameCount = 0
    private var currentFrameRate = 60
    private var lastCalculateTime = SystemClock.elapsedRealtime()

    // 卡顿检测相关
    private var lastFrameTimeMs: Long = 0
    private val stuckFrames = mutableListOf<Long>()
    private val isStuckDetecting = AtomicBoolean(false)

    // 线程相关 - 实现原则1：异步处理数据
    private var frameMonitorThread: HandlerThread? = null
    private var frameMonitorHandler: Handler? = null
    private var frameListener: Choreographer.FrameCallback? = null
    private var fpsCalculator: Runnable? = null

    fun init(config: MonitorConfig) {
        if (isInitialized) return
        this.config = config
        isInitialized = true

        // 初始化帧监听器，在主线程注册帧回调
        frameListener = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                onFrame(frameTimeNanos)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        LogUtil.d(message = "SmoothnessMonitor initialized")
    }

    fun start() {
        if (!isInitialized || isStarted) return
        isStarted = true

        // 重置数据
        frameTimes.clear()
        frameCount = 0
        currentFrameRate = 60
        lastCalculateTime = SystemClock.elapsedRealtime()
        lastFrameTimeNanos = System.nanoTime()

        // ===== 原则1实现：创建子线程处理数据 =====
        frameMonitorThread = HandlerThread("FrameMonitor-Thread").apply {
            start()
            frameMonitorHandler = Handler(looper)
        }

        // 启动FPS计算任务（在子线程执行）
        fpsCalculator = object : Runnable {
            override fun run() {
                calculateFPS()
                // 每秒计算一次
                frameMonitorHandler?.postDelayed(this, 1000)
            }
        }
        fpsCalculator?.let { frameMonitorHandler?.post(it) }

        // 注册帧回调（在主线程）
        frameListener?.let {
            Choreographer.getInstance().postFrameCallback(it)
        }

        LogUtil.d(message = "SmoothnessMonitor started with async processing")
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false

        // 移除帧回调
        frameListener?.let {
            Choreographer.getInstance().removeFrameCallback(it)
        }

        // 停止子线程任务
        fpsCalculator?.let {
            frameMonitorHandler?.removeCallbacks(it)
        }
        fpsCalculator = null

        // 停止线程
        frameMonitorThread?.quitSafely()
        frameMonitorThread = null
        frameMonitorHandler = null

        LogUtil.d(message = "SmoothnessMonitor stopped")
    }

    fun setCallback(callback: MonitorCallback) {
        this.callback = callback
    }

    fun getData(): SmoothnessData {
        return SmoothnessData(
            isMonitoring = isStarted,
            frameRate = currentFrameRate,
            isNormal = currentFrameRate >= (config?.smoothnessConfig?.lowFrameRateThreshold ?: 45)
        )
    }

    // ==================== 核心监控逻辑 ====================

    private fun onFrame(frameTimeNanos: Long) {
        val currentTimeMs = SystemClock.elapsedRealtime()

        // 计算帧间隔（纳秒转毫秒）
        if (lastFrameTimeNanos > 0) {
            val frameIntervalNs = frameTimeNanos - lastFrameTimeNanos
            val frameIntervalMs = frameIntervalNs / 1_000_000

            // 将原始数据发送到子线程处理
            frameMonitorHandler?.post {
                // 在子线程中处理数据，不阻塞主线程
                processFrameData(frameIntervalMs, currentTimeMs)
            }
        }

        lastFrameTimeNanos = frameTimeNanos
        frameCount++

        // 记录当前帧时间（用于卡顿检测）
        val currentFrameTimeMs = System.currentTimeMillis()
        if (lastFrameTimeMs > 0) {
            val frameDuration = currentFrameTimeMs - lastFrameTimeMs

            // 快速判断是否可能卡顿（在主线程快速检查）
            val stuckThreshold = config?.smoothnessConfig?.stuckThreshold ?: 50L
            if (frameDuration > stuckThreshold && isStarted) {
                // 触发卡顿检测（在子线程执行）
                frameMonitorHandler?.post {
                    detectStuck(frameDuration, currentFrameTimeMs)
                }
            }
        }
        lastFrameTimeMs = currentFrameTimeMs
    }

    /**
     * 处理帧数据（在子线程执行）
     */
    private fun processFrameData(frameIntervalMs: Long, timestamp: Long) {
        // 存储帧间隔用于FPS计算
        frameTimes.add(frameIntervalMs)

        // 保持最近1秒的数据（假设60FPS，最多60帧）
        if (frameTimes.size > 60) {
            frameTimes.removeAt(0)
        }
    }

    /**
     * 计算FPS（在子线程执行）- 原则1实现
     */
    private fun calculateFPS() {
        if (frameTimes.isEmpty() || !isStarted) return

        // 计算平均帧间隔
        val totalFrameTime = frameTimes.sum()
        val averageFrameTime = totalFrameTime / frameTimes.size

        // 计算FPS
        currentFrameRate = if (averageFrameTime > 0) {
            (1000 / averageFrameTime).toInt()
        } else {
            60
        }

        // 限制在合理范围内
        currentFrameRate = currentFrameRate.coerceIn(0, 60)

        // 检查低帧率
        val lowThreshold = config?.smoothnessConfig?.lowFrameRateThreshold ?: 45
        if (currentFrameRate < lowThreshold && callback != null) {
            // 回调到主线程
            Handler(Looper.getMainLooper()).post {
                callback?.onLowFrameRate(currentFrameRate)
            }
        }

        // 清空数据，准备下一轮计算
        frameTimes.clear()
        frameCount = 0
    }

    /**
     * 检测卡顿（在子线程执行）
     */
    private fun detectStuck(frameDuration: Long, currentTime: Long) {
        if (!isStarted || isStuckDetecting.get()) return

        isStuckDetecting.set(true)

        // 记录卡顿帧
        stuckFrames.add(frameDuration)

        // 连续3帧超过阈值判定为卡顿
        if (stuckFrames.size >= 3) {
            val avgStuckTime = stuckFrames.average().toLong()

            // 获取主线程堆栈（安全的，在子线程调用）
            val stackTrace = getMainThreadStackTrace()

            // 回调到主线程
            Handler(Looper.getMainLooper()).post {
                callback?.onFrameStuck(avgStuckTime, stackTrace)
            }

            // 记录日志
            LogUtil.w(message = "Frame stuck detected: avg=${avgStuckTime}ms, stack: $stackTrace")

            // 清空记录
            stuckFrames.clear()
        }

        // 清理超过500ms的记录
        stuckFrames.removeAll { it < 100 }

        isStuckDetecting.set(false)
    }

    /**
     * 获取主线程堆栈（线程安全）
     */
    private fun getMainThreadStackTrace(): String {
        return try {
            val mainThread = Looper.getMainLooper().thread
            mainThread.stackTrace.joinToString("\n") { it.toString() }
        } catch (e: Exception) {
            "Unable to get stack trace: ${e.message}"
        }
    }

    data class SmoothnessData(
        val isMonitoring: Boolean,
        val frameRate: Int,
        val isNormal: Boolean
    )
}