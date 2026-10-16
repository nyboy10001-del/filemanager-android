package com.filemanager.app.util

import android.os.Environment
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = -1
        do {
            v /= 1024
            i++
        } while (v >= 1024 && i < units.lastIndex)
        return String.format(Locale.getDefault(), "%.1f %s", v, units[i])
    }

    fun relative(ts: Long): String =
        DateUtils.getRelativeTimeSpanString(ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    fun date(ts: Long): String =
        SimpleDateFormat("d MMMM yyyy 'at' HH:mm", Locale.getDefault()).format(Date(ts))

    fun items(n: Int): String = if (n == 1) "1 item" else "$n items"

    fun pretty(path: String): String {
        val root = Environment.getExternalStorageDirectory().absolutePath
        return when {
            path == root -> "Internal storage"
            path.startsWith("$root/") -> "Internal storage" + path.removePrefix(root)
            else -> path
        }
    }

    fun breadcrumb(path: String): String = pretty(path).replace("/", " › ")
}
