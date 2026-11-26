package app.temizle

import java.util.Locale

enum class Kind(val title: String, val emoji: String, val grid: Boolean, val hint: String) {
    Photos("Fotoğraflar", "🖼️", true, "Dokun: seç. Basılı tut: önizle."),
    Videos("Videolar", "🎬", true, "Dokun: seç. Basılı tut: önizle."),
    Screenshots("Ekran görüntüleri", "📸", true, "Çoğu işe yaramaz. Gözden geçir."),
    Large("Büyük dosyalar", "📏", false, "50 MB ve üstü her dosya."),
    Archives("Arşivler", "🗜️", false, "zip, rar, 7z, tar... Açıldıysa gereksiz."),
    Installers("APK dosyaları", "📦", false, "Kurulum dosyaları. Kurulduysa gereksiz."),
    Junk("Gereksiz dosyalar", "🧹", false, "Geçici, boş, log ve yedek dosyalar."),
    Downloads("İndirilenler", "📥", false, "İndirilenler klasöründeki her şey."),
    Audio("Ses dosyaları", "🎵", false, "Müzik, ses notu, kayıt."),
    EmptyDirs("Boş klasörler", "📂", false, "İçinde hiçbir şey yok.")
}

sealed interface Screen {
    data object Home : Screen
    data class Files(val kind: Kind) : Screen
    data object Apps : Screen
    data object Cache : Screen
    data object Dups : Screen
}

enum class Sort { Size, OldFirst, Name }

enum class AppFilter(val label: String) {
    All("Hepsi"),
    Never("Hiç açılmamış"),
    D30("30+ gün"),
    D90("90+ gün"),
    Big("100 MB+")
}

data class FileItem(val path: String, val size: Long, val modified: Long) {
    val name: String get() = path.substringAfterLast('/')
    val ext: String get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val dir: String get() = path.substringBeforeLast('/', "")
    val folder: String get() = dir.substringAfterLast('/')
}

data class AppItem(
    val pkg: String,
    val label: String,
    val total: Long,
    val cache: Long,
    val sizeKnown: Boolean,
    val lastUsed: Long,
    val installed: Long,
    val fgMinutes30: Long
)

data class DupGroup(val size: Long, val files: List<FileItem>) {
    val waste: Long get() = size * (files.size - 1)
}

object Exts {
    val image = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "dng", "jfif")
    val video = setOf("mp4", "mkv", "mov", "avi", "3gp", "webm", "m4v", "mpg", "mpeg", "wmv", "flv")
    val audio = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr", "wma")
    val archive = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "iso")
    val installer = setOf("apk", "xapk", "apks", "apkm")
    val junk = setOf("tmp", "temp", "log", "bak", "old", "cache", "crdownload", "part", "partial", "download", "dmp")
}

fun emojiFor(ext: String): String = when {
    ext in Exts.image -> "🖼️"
    ext in Exts.video -> "🎬"
    ext in Exts.audio -> "🎵"
    ext in Exts.archive -> "🗜️"
    ext in Exts.installer -> "📦"
    ext == "pdf" -> "📕"
    else -> "📄"
}
