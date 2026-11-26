package app.temizle

import android.os.Environment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TR: Locale = Locale.forLanguageTag("tr-TR")

val STORAGE_ROOT: String = Environment.getExternalStorageDirectory().absolutePath

fun formatSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble() / 1024.0
    var i = 0
    while (v >= 1024.0 && i < units.size - 1) {
        v /= 1024.0
        i++
    }
    val fmt = if (v >= 100.0) "%.0f %s" else "%.1f %s"
    return String.format(TR, fmt, v, units[i])
}

fun formatDate(ms: Long): String {
    if (ms <= 0L) return "-"
    return SimpleDateFormat("d MMM yyyy", TR).format(Date(ms))
}

fun daysSince(ms: Long): Int {
    val d = (System.currentTimeMillis() - ms) / 86_400_000L
    return d.toInt().coerceAtLeast(0)
}

fun shortDir(dir: String): String = dir.removePrefix(STORAGE_ROOT).trimStart('/').ifEmpty { "/" }
