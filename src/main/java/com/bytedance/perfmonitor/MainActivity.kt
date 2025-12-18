package com.bytedance.perfmonitor.demo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bytedance.perfmonitor.sdk.*

class MainActivity : AppCompatActivity() {
    //声明按钮
    private lateinit var btnInit: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnGetData: Button
    private lateinit var btnTestFrame: Button
    private lateinit var btnTestANR: Button
    private lateinit var tvData: TextView
    // 创建主线程的 Handler，用于在 UI 线程执行任务
    private val handler = Handler(Looper.getMainLooper())
    private val monitorCallback = object : MonitorCallback {
        override fun onFrameStuck(duration: Long, stackTrace: String) {
            runOnUiThread {
                updateData("卡顿检测: ${duration}ms\n${stackTrace.take(100)}...")
            }
        }

        override fun onANRDetected(delay: Long, stackTrace: String, logLine: String?) {
            runOnUiThread {
                updateData("ANR检测: ${delay}ms\n${stackTrace.take(100)}...")
            }
        }

        override fun onLowFrameRate(fps: Int) {
            runOnUiThread {
                updateData("低帧率警告: ${fps}FPS")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupButtons()
    }

    private fun initViews() {
        //通过 findViewById 把布局文件（XML）里的按钮和文本框和代码里的变量关联起来
        btnInit = findViewById(R.id.btnInit)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnGetData = findViewById(R.id.btnGetData)
        btnTestFrame = findViewById(R.id.btnTestFrame)
        btnTestANR = findViewById(R.id.btnTestANR)
        tvData = findViewById(R.id.tvData)
    }

    private fun setupButtons() {
        btnInit.setOnClickListener {
            val config = MonitorConfig(
                debugMode = true,
                smoothnessConfig = SmoothnessConfig(
                    sampleInterval = 1000L,
                    lowFrameRateThreshold = 45,
                    stuckThreshold = 50L// 卡顿阈值
                ),
                anrConfig = ANRConfig(
                    anrThreshold = 5000L,
                    useSigquit = true
                )
            )
            PerformanceMonitor.init(config)
            PerformanceMonitor.setCallback(monitorCallback)
            updateData("SDK初始化成功 - 已启用异步监控和看门狗")
        }

        btnStart.setOnClickListener {
            PerformanceMonitor.start()
            updateData("监控已启动\n• 帧率监控（异步）\n• ANR看门狗线程\n• SIGQUIT监听")
        }

        btnStop.setOnClickListener {
            PerformanceMonitor.stop()
            updateData("监控已停止")
        }

        btnGetData.setOnClickListener {
            val monitorData = PerformanceMonitor.getMonitorData()
            updateData("数据获取成功:\n" +
                    "流畅性监控: ${if (monitorData.smoothnessData.isMonitoring) "运行中" else "已停止"}\n" +
                    "当前帧率: ${monitorData.smoothnessData.frameRate}FPS\n" +
                    "ANR监控: ${if (monitorData.anrData.isMonitoring) "运行中" else "已停止"}\n" +
                    "SIGQUIT: ${if (monitorData.anrData.useSigquit) "已启用" else "未启用"}")
        }

        btnTestFrame.setOnClickListener {
            updateData("帧率监控测试:\n模拟卡顿场景...")
            // 模拟卡顿：在主线程执行耗时操作
            Thread.sleep(200)
            handler.postDelayed({
                updateData("帧率监控测试:\n主线程延迟200ms完成")
            }, 1000)
        }

        btnTestANR.setOnClickListener {
            updateData("ANR监控测试:\n模拟主线程阻塞6秒...")
            Thread {
                // 阻塞主线程6秒
                handler.post {
                    Thread.sleep(6000)
                }
                handler.post {
                    updateData("ANR监控测试:\n看门狗应已检测到ANR")
                }
            }.start()
        }
    }

    private fun updateData(message: String) {
        tvData.text = """
            性能监控SDK演示
            
            状态: ${PerformanceMonitor.getMonitorData().status}
            时间: ${System.currentTimeMillis() % 100000}
            
            $message
            
            实现特性:
            1.  流畅性监控 - 异步帧率计算
            2.  ANR监控 - 看门狗线程+SIGQUIT
            3.  独立SDK模块
            4.  实时数据展示
        """.trimIndent()
    }

    override fun onResume() {
        super.onResume()
        updateData("应用已恢复")
    }

    override fun onDestroy() {
        super.onDestroy()
        PerformanceMonitor.stop()
    }
}