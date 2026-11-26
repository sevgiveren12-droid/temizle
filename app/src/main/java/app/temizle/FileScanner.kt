package app.temizle

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale

const val LARGE_BYTES: Long = 50L * 1024L * 1024L
const val MIN_DUP_BYTES: Long = 20L * 1024L

object FileScanner {

    /** Tüm depolamayı gezer. Dosyaları ve boş klasörleri döner. */
    fun walk(root: File, alive: () -> Boolean, onCount: (Int) -> Unit): Pair<List<FileItem>, List<String>> {
        val files = ArrayList<FileItem>(40_000)
        val emptyDirs = ArrayList<String>()
        val rootPath = root.absolutePath
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty() && alive()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            if (children.isEmpty()) {
                val parentPath = dir.parentFile?.absolutePath
                if (dir.absolutePath != rootPath && parentPath != rootPath) {
                    emptyDirs.add(dir.absolutePath)
                }
                continue
            }
            for (c in children) {
                if (c.isDirectory) {
                    if (!shouldSkip(c, rootPath)) stack.addLast(c)
                } else {
                    files.add(FileItem(c.absolutePath, c.length(), c.lastModified()))
                    if (files.size % 500 == 0) onCount(files.size)
                }
            }
        }
        onCount(files.size)
        return Pair(files, emptyDirs)
    }

    private fun shouldSkip(dir: File, rootPath: String): Boolean {
        val rel = dir.absolutePath.removePrefix(rootPath).trimStart('/')
        if (rel == "Android/data" || rel == "Android/obb") return true
        return try {
            Files.isSymbolicLink(dir.toPath())
        } catch (e: Exception) {
            true
        }
    }

    private fun isScreenshot(lowerPath: String): Boolean = lowerPath.contains("screenshot")

    private fun isJunk(f: FileItem, lowerPath: String): Boolean {
        val n = f.name.lowercase(Locale.ROOT)
        if (n == ".nomedia") return false
        if (f.size == 0L) return true
        if (f.ext in Exts.junk) return true
        if (n == "thumbs.db" || n == ".ds_store" || n == "desktop.ini") return true
        if (lowerPath.contains("/.thumbnails/") || lowerPath.contains("/.thumbcache")) return true
        if (n.endsWith("~")) return true
        return false
    }

    fun classify(files: List<FileItem>, emptyDirs: List<String>): Map<Kind, List<FileItem>> {
        val m = HashMap<Kind, MutableList<FileItem>>()
        fun add(k: Kind, f: FileItem) {
            m.getOrPut(k) { ArrayList() }.add(f)
        }
        for (f in files) {
            val ext = f.ext
            val lp = f.path.lowercase(Locale.ROOT)
            when {
                ext in Exts.image -> {
                    add(Kind.Photos, f)
                    if (isScreenshot(lp)) add(Kind.Screenshots, f)
                }
                ext in Exts.video -> add(Kind.Videos, f)
                ext in Exts.audio -> add(Kind.Audio, f)
                ext in Exts.installer -> add(Kind.Installers, f)
                ext in Exts.archive -> add(Kind.Archives, f)
            }
            if (isJunk(f, lp)) add(Kind.Junk, f)
            if (f.size >= LARGE_BYTES) add(Kind.Large, f)
            if (f.path.contains("/Download/")) add(Kind.Downloads, f)
        }
        for (d in emptyDirs) {
            add(Kind.EmptyDirs, FileItem(d, 0L, File(d).lastModified()))
        }
        return m
    }

    private fun hashFile(f: File, full: Boolean): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            RandomAccessFile(f, "r").use { raf ->
                val buf = ByteArray(64 * 1024)
                if (full) {
                    var n = raf.read(buf)
                    while (n > 0) {
                        md.update(buf, 0, n)
                        n = raf.read(buf)
                    }
                } else {
                    var n = raf.read(buf)
                    if (n > 0) md.update(buf, 0, n)
                    val len = raf.length()
                    if (len > 128L * 1024L) {
                        raf.seek(len - 64L * 1024L)
                        n = raf.read(buf)
                        if (n > 0) md.update(buf, 0, n)
                    }
                }
            }
            md.digest().joinToString("") { b -> "%02x".format(b) }
        } catch (e: Exception) {
            null
        }
    }

    private fun penalty(p: String): Int =
        if (p.contains("/Download/") || p.contains("WhatsApp") || p.contains("Telegram") || p.contains("Screenshots")) 1 else 0

    /** Aynı içerikli dosyaları bulur. Önce boyut, sonra hızlı özet, sonra tam özet. */
    fun findDuplicates(files: List<FileItem>, onProgress: (Float) -> Unit, alive: () -> Boolean): List<DupGroup> {
        val bySize = files
            .filter { it.size >= MIN_DUP_BYTES }
            .groupBy { it.size }
            .filter { it.value.size > 1 }
        val totalSteps = (bySize.values.sumOf { it.size } * 2).coerceAtLeast(1)
        var done = 0
        val out = ArrayList<DupGroup>()
        val order = compareBy<FileItem>({ penalty(it.path) }, { it.modified }, { it.path.length })

        for ((size, group) in bySize) {
            if (!alive()) break
            val quick = HashMap<String, MutableList<FileItem>>()
            for (f in group) {
                val h = hashFile(File(f.path), false)
                done++
                if (h != null) quick.getOrPut(h) { ArrayList() }.add(f)
            }
            for (qGroup in quick.values) {
                if (qGroup.size < 2) {
                    done += qGroup.size
                    continue
                }
                if (size <= 128L * 1024L) {
                    done += qGroup.size
                    out.add(DupGroup(size, qGroup.sortedWith(order)))
                    continue
                }
                val full = HashMap<String, MutableList<FileItem>>()
                for (f in qGroup) {
                    val h = hashFile(File(f.path), true)
                    done++
                    if (h != null) full.getOrPut(h) { ArrayList() }.add(f)
                }
                for (fg in full.values) {
                    if (fg.size >= 2) out.add(DupGroup(size, fg.sortedWith(order)))
                }
            }
            onProgress((done.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f))
        }
        onProgress(1f)
        return out.sortedByDescending { it.waste }
    }
}
