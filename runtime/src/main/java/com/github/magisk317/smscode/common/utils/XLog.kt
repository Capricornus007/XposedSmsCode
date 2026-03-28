package com.github.magisk317.smscode.common.utils

object XLog {
    @JvmStatic
    fun v(message: String, vararg args: Any?) {
        io.github.magisk317.smscode.xposed.utils.XLog.v(message, *args)
    }

    @JvmStatic
    fun d(message: String, vararg args: Any?) {
        io.github.magisk317.smscode.xposed.utils.XLog.d(message, *args)
    }

    @JvmStatic
    fun i(message: String, vararg args: Any?) {
        io.github.magisk317.smscode.xposed.utils.XLog.i(message, *args)
    }

    @JvmStatic
    fun w(message: String, vararg args: Any?) {
        io.github.magisk317.smscode.xposed.utils.XLog.w(message, *args)
    }

    @JvmStatic
    fun e(message: String, vararg args: Any?) {
        io.github.magisk317.smscode.xposed.utils.XLog.e(message, *args)
    }

    @JvmStatic
    fun setLogLevel(logLevel: Int) {
        io.github.magisk317.smscode.xposed.utils.XLog.setLogLevel(logLevel)
    }

    @JvmStatic
    fun getLogLevel(): Int = io.github.magisk317.smscode.xposed.utils.XLog.getLogLevel()
}
