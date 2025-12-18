package com.bytedance.perfmonitor.sdk

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object ANRMonitor {

    // 状态变量
    private var isInitialized = false
    private var isStarted = false
    private var config: MonitorConfig? = null
    private var callback: MonitorCallback? = null

    // ===== 原则2实现：看门狗线程相关 =====
    private var watchdogThread: HandlerThread? = null
    private var watchdogHandler: Handler? = null
    private var mainHandler: Handler? = null
    private val lastResponseTime = AtomicLong(SystemClock.uptimeMillis())
    private val isChecking = AtomicBoolean(false)
    private var watchdogTask: Runnable? = null

    fun init(config: MonitorConfig) {
        if (isInitialized) return
        this.config = config
        isInitialized = true

        // 初始化主线程Handler
        mainHandler = Handler(Looper.getMainLooper())

        LogUtil.d(message = "ANRMonitor initialized")
    }

    fun start() {
        if (!isInitialized || isStarted) return
        isStarted = true

        // 重置响应时间
        lastResponseTime.set(SystemClock.uptimeMillis())

        // ===== 原则2实现：启动看门狗线程 =====
        watchdogThread = HandlerThread("ANR-Watchdog").apply {
            start()
            watchdogHandler = Handler(looper)// 创建关联到这个线程的Handler
        }

        // 启动ANR检测任务
        watchdogTask = object : Runnable {
            override fun run() {
                checkANR()
                // 每500ms检查一次
                watchdogHandler?.postDelayed(this, 500)
            }
        }
        watchdogTask?.let { watchdogHandler?.post(it) }

        LogUtil.d(message = "ANRMonitor started with watchdog thread")
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false

        // 停止看门狗任务
        watchdogTask?.let {
            watchdogHandler?.removeCallbacks(it)
        }
        watchdogTask = null

        // 停止线程
        watchdogThread?.quitSafely()
        watchdogThread = null
        watchdogHandler = null

        LogUtil.d(message = "ANRMonitor stopped")
    }

    fun setCallback(callback: MonitorCallback) {
        this.callback = callback
    }

    fun getData(): ANRData {
        return ANRData(
            isMonitoring = isStarted,
            useSigquit = false,
            threshold = config?.anrConfig?.anrThreshold ?: 5000L
        )
    }

    /**
     * 检查ANR（在看门狗线程执行）
     */
    private fun checkANR() {
        if (!isStarted || isChecking.get()) return

        isChecking.set(true)

        val threshold = config?.anrConfig?.anrThreshold ?: 5000L
        val currentTime = SystemClock.uptimeMillis()
        val lastResponse = lastResponseTime.get()
        val elapsed = currentTime - lastResponse

        // 如果超过阈值，发送心跳检查
        if (elapsed > threshold) {
            // 发送心跳到主线程
            mainHandler?.post {
                // 主线程收到心跳，更新响应时间
                lastResponseTime.set(SystemClock.uptimeMillis())
            }

            // 等待一小段时间让主线程响应
            Thread.sleep(50)

            // 再次检查
            val newElapsed = SystemClock.uptimeMillis() - lastResponseTime.get()

            if (newElapsed > threshold) {
                onANRDetected(newElapsed, "Watchdog detected")
            }
        }

        isChecking.set(false)
    }

    /**
     * 检测到ANR时的处理
     */
    private fun onANRDetected(delay: Long, detectedBy: String) {
        if (!isStarted) return

        // ===== 原则2实现：在子线程获取主线程堆栈 =====
        Thread {
            try {
                // 获取主线程堆栈（在子线程中是安全的）
                val mainThread = Looper.getMainLooper().thread
                val stackTrace = mainThread.stackTrace.joinToString("\n") { it.toString() }

                // 获取所有线程堆栈
                val allTraces = Thread.getAllStackTraces()
                val allThreadsInfo = allTraces.entries.joinToString("\n\n") { entry ->
                    val thread = entry.key
                    val trace = entry.value.joinToString("\n") { it.toString() }
                    "Thread: ${thread.name} (${thread.id}, ${thread.state})\n$trace"
                }

                // 分析ANR原因
                val anrReason = analyzeAnrReason(stackTrace)

                // 构建日志
                val logLine = buildString {
                    append("ANR Detected by $detectedBy\n")
                    append("Delay: ${delay}ms\n")
                    append("Process: ${android.os.Process.myPid()}\n")
                    append("Reason: $anrReason\n")
                    append("Main Thread Stack:\n$stackTrace\n")
                    append("\nAll Threads:\n$allThreadsInfo")
                }

                // 回调到主线程
                Handler(Looper.getMainLooper()).post {
                    callback?.onANRDetected(delay, stackTrace, logLine)
                }

                LogUtil.w(message = "ANR detected: delay=${delay}ms, reason=$anrReason")

            } catch (e: Exception) {
                LogUtil.e(message = "Error processing ANR", e = e)
            }
        }.start()
    }

    /**
     * 分析ANR原因
     */
    private fun analyzeAnrReason(stackTrace: String): String {
        return when {
            stackTrace.contains("Lock.wait") || stackTrace.contains("Object.wait") ->
                "Waiting on lock/synchronization"
            stackTrace.contains("Thread.sleep") ->
                "Sleeping in main thread"
            stackTrace.contains("Socket.read") || stackTrace.contains("SocketInputStream") ->
                "Network I/O in main thread"
            stackTrace.contains("SQLite") || stackTrace.contains("Cursor") ->
                "Database operation in main thread"
            stackTrace.contains("Bitmap") || stackTrace.contains("decode") ->
                "Bitmap processing in main thread"
            stackTrace.contains("MessageQueue.nativePollOnce") ->
                "Message queue idle (可能等待消息)"
            else -> "Unknown blocking operation"
        }
    }

    data class ANRData(
        val isMonitoring: Boolean,
        val useSigquit: Boolean,
        val threshold: Long
    )
}