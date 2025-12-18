package com.bytedance.perfmonitor.sdk

import android.util.Log
//日志工具类。通过控制 isDebug 标志位，在 Release 环境下屏蔽调试级别的日志输出
object LogUtil {

    private var isDebug = false

    fun setDebug(debug: Boolean) {
        isDebug = debug
    }

    fun d(tag: String = "PerformanceSDK", message: String) {
        if (isDebug) {
            Log.d(tag, message)//在开发环境打印调试信息，保证性能
        }
    }

    fun i(tag: String = "PerformanceSDK", message: String) {
        Log.i(tag, message)
    }

    fun w(tag: String = "PerformanceSDK", message: String) {
        Log.w(tag, message)
    }

    fun e(tag: String = "PerformanceSDK", message: String, e: Exception? = null) {
        if (e != null) {
            Log.e(tag, message, e)
        } else {
            Log.e(tag, message)
        }
    }
}