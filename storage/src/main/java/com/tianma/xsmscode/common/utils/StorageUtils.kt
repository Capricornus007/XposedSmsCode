package com.tianma.xsmscode.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Utils for storage.
 */
object StorageUtils {

    @JvmStatic
    fun isSDCardMounted(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    /**
     * 获取日志路径
     */
    @JvmStatic
    fun getLogDir(context: Context): File? = if (isSDCardMounted()) {
        context.getExternalFilesDir("log")
    } else {
        File(context.filesDir, "log")
    }

    /**
     * 获取Crash日志路径
     */
    @JvmStatic
    fun getCrashLogDir(context: Context): File? = if (isSDCardMounted()) {
        context.getExternalFilesDir("crash")
    } else {
        File(context.filesDir, "crash")
    }

    @JvmStatic
    fun getPublicDocumentsDir(context: Context): File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir

    @JvmStatic
    fun getInternalDataDir(context: Context): File = context.dataDir

    @JvmStatic
    fun getInternalFilesDir(context: Context): File = File(getInternalDataDir(context), "files")

    @JvmStatic
    fun getExternalFilesDir(context: Context): File = context.getExternalFilesDir(null) ?: context.filesDir

    @JvmStatic
    fun getFilesDir(context: Context): File {
        val externalFilesDir = getExternalFilesDir(context)
        if (!externalFilesDir.exists()) {
            externalFilesDir.mkdirs()
        }
        return externalFilesDir
    }

    /**
     * Set file world writable
     */
    @SuppressLint("SetWorldWritable", "SetWorldReadable")
    @JvmStatic
    fun setFileWorldWritable(file: File, parentDepth: Int) {
        var currentFile: File? = file
        if (currentFile == null || !currentFile.exists()) {
            return
        }
        val actualDepth = parentDepth + 1
        for (i in 0 until actualDepth) {
            currentFile?.setExecutable(true, false)
            currentFile?.setWritable(true, false)
            currentFile?.setReadable(true, false)
            currentFile = currentFile?.parentFile
            if (currentFile == null) {
                break
            }
        }
    }

    /**
     * Set file world readable
     */
    @SuppressLint("SetWorldReadable")
    @JvmStatic
    fun setFileWorldReadable(file: File, parentDepth: Int) {
        var currentFile: File? = file
        if (currentFile == null || !currentFile.exists()) {
            return
        }
        for (i in 0 until parentDepth) {
            currentFile?.setReadable(true, false)
            currentFile?.setExecutable(true, false)
            currentFile = currentFile?.parentFile
            if (currentFile == null) {
                break
            }
        }
    }
}
