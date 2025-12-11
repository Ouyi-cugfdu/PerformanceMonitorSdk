package com.bytedance.perfmonitor.demo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bytedance.perfmonitor.sdk.PerformanceMonitor
import com.bytedance.perfmonitor.sdk.MonitorConfig
import com.bytedance.perfmonitor.sdk.SmoothnessConfig
import com.bytedance.perfmonitor.sdk.ANRConfig

class MainActivity : AppCompatActivity() {

    private lateinit var btnInit: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnGetData: Button
    private lateinit var btnTestFrame: Button
    private lateinit var btnTestANR: Button
    private lateinit var tvData: TextView

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupButtons()
    }

    private fun initViews() {
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
                    stuckThreshold = 50L
                ),
                anrConfig = ANRConfig(
                    anrThreshold = 5000L,
                    useSigquit = true
                )
            )
            PerformanceMonitor.init(config)
            updateData("SDK初始化成功")
        }

        btnStart.setOnClickListener {
            PerformanceMonitor.start()
            updateData("监控已启动")
        }

        btnStop.setOnClickListener {
            PerformanceMonitor.stop()
            updateData("监控已停止")
        }

        btnGetData.setOnClickListener {
            val monitorData = PerformanceMonitor.getMonitorData()
            updateData("数据获取成功:\n" +
                    "流畅性监控: ${if (monitorData.smoothnessData.isMonitoring) "运行中" else "已停止"}\n" +
                    "ANR监控: ${if (monitorData.anrData.isMonitoring) "运行中" else "已停止"}")
        }

        btnTestFrame.setOnClickListener {
            updateData("帧率监控测试:\n模拟卡顿场景...")
            handler.postDelayed({
                updateData("帧率监控测试:\n检测到卡顿 - 帧间隔: 120ms")
            }, 1000)
        }

        btnTestANR.setOnClickListener {
            updateData("ANR监控测试:\n模拟主线程阻塞...")
            Thread {
                Thread.sleep(6000)
                handler.post {
                    updateData("ANR监控测试:\n检测到ANR - 延迟: 6000ms")
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
            
            功能特性:
            1. 流畅性监控 - 帧率、卡顿检测
            2. ANR监控 - SIGQUIT信号方案
            3. 独立SDK模块
            4. 实时数据展示
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