package com.bytedance.perfmonitor.sdk

import android.util.Log

object LogUtil {

    private var isDebug = false

    fun setDebug(debug: Boolean) {
        isDebug = debug
    }

    fun d(tag: String = "PerformanceSDK", message: String) {
        if (isDebug) {
            Log.d(tag, message)
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