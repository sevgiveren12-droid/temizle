package app.temizle

import android.app.Application
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx: Application get() = getApplication()

    // ---- genel ----
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var message by mutableStateOf<String?>(null)

    var hasAllFiles by mutableStateOf(false)
        private set
    var hasUsage by mutableStateOf(false)
        private set
    var storageTotal by mutableLongStateOf(0L)
        private set
    var storageFree by mutableLongStateOf(0L)
        private set

    // ---- dosya taraması ----
    var scanning by mutableStateOf(false)
        private set
    var scanCount by mutableIntStateOf(0)
        private set
    var scanned by mutableStateOf(false)
        private set
    var byKind by mutableStateOf<Map<Kind, List<FileItem>>>(emptyMap())
        private set
    private var allFiles: List<FileItem> = emptyList()
    var reclaimable by mutableLongStateOf(0L)
        private set

    // ---- seçim (dosyalar ve kopyalar) ----
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var sort by mutableStateOf(Sort.Size)
    var folderFilter by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set

    // ---- uygulamalar ----
    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set
    var appsLoading by mutableStateOf(false)
        private set
    private var appsLoadedUsage: Boolean? = null
    var selectedApps by mutableStateOf<Set<String>>(emptySet())
        private set
    var appFilter by mutableStateOf(AppFilter.All)
    var appSort by mutableStateOf(Sort.Size)

    // ---- kopyalar ----
    var dupGroups by mutableStateOf<List<DupGroup>>(emptyList())
        private set
    var dupRunning by mutableStateOf(false)
        private set
    var dupDone by mutableStateOf(false)
        private set
    var dupProgress by mutableFloatStateOf(0f)
        private set

    // ---------------------------------------------------------------

    fun go(s: Screen) {
        screen = s
        selected = emptySet()
        selectedApps = emptySet()
        folderFilter = null
        sort = Sort.Size
        appFilter = AppFilter.All
        appSort = Sort.Size
        if ((s == Screen.Apps || s == Screen.Cache) && apps.isEmpty()) loadApps()
        if (s == Screen.Dups && dupDone) autoSelectDups()
    }

    fun refreshPermissions() {
        hasAllFiles = Environment.isExternalStorageManager()
        val u = AppsRepo.hasUsageAccess(ctx)
        hasUsage = u
        updateStorage()
        if (appsLoadedUsage != u) loadApps()
        if (hasAllFiles && !scanned && !scanning) startScan()
    }

    private fun updateStorage() {
        try {
            val st = StatFs(Environment.getExternalStorageDirectory().path)
            storageTotal = st.totalBytes
            storageFree = st.availableBytes
        } catch (e: Exception) {
            // yok say
        }
    }

    // ---- tarama ----

    fun startScan() {
        if (scanning || !hasAllFiles) return
        scanning = true
        scanCount = 0
        viewModelScope.launch {
            try {
                val root = Environment.getExternalStorageDirectory()
                val (files, emptyDirs) = withContext(Dispatchers.IO) {
                    FileScanner.walk(root, { isActive }, { n -> scanCount = n })
                }
                allFiles = files
                byKind = FileScanner.classify(files, emptyDirs)
                dupGroups = emptyList()
                dupDone = false
                scanned = true
            } catch (e: Exception) {
                message = "Tarama hatası: ${e.message}"
            }
            scanning = false
            updateStorage()
            recompute()
        }
    }

    private fun recompute() {
        val seen = HashSet<String>()
        var sum = 0L
        for (k in listOf(Kind.Junk, Kind.Installers, Kind.Archives)) {
            for (f in byKind[k].orEmpty()) {
                if (seen.add(f.path)) sum += f.size
            }
        }
        for (g in dupGroups) {
            for (f in g.files.drop(1)) {
                if (seen.add(f.path)) sum += f.size
            }
        }
        sum += apps.sumOf { it.cache }
        reclaimable = sum
    }

    // ---- seçim ----

    fun toggle(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    fun selectAll(paths: Collection<String>) {
        selected = selected + paths
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun deleteSelected() {
        val paths = selected.toList()
        if (paths.isEmpty() || busy) return
        busy = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                var ok = 0
                var fail = 0
                var bytes = 0L
                val gone = HashSet<String>()
                for (p in paths) {
                    val f = File(p)
                    val size = if (f.isFile) f.length() else 0L
                    if (f.delete()) {
                        ok++
                        bytes += size
                        gone.add(p)
                    } else {
                        fail++
                    }
                }
                if (gone.isNotEmpty()) {
                    try {
                        MediaScannerConnection.scanFile(ctx, gone.toTypedArray(), null, null)
                    } catch (e: Exception) {
                        // yok say
                    }
                }
                DeleteResult(ok, fail, bytes, gone)
            }
            applyDeleted(result.gone)
            message = buildString {
                append("${result.ok} silindi · ${formatSize(result.bytes)} boşaldı")
                if (result.fail > 0) append(" · ${result.fail} silinemedi")
            }
            busy = false
            updateStorage()
            recompute()
        }
    }

    private class DeleteResult(val ok: Int, val fail: Int, val bytes: Long, val gone: Set<String>)

    private fun applyDeleted(gone: Set<String>) {
        if (gone.isEmpty()) return
        allFiles = allFiles.filter { it.path !in gone }
        byKind = byKind.mapValues { entry -> entry.value.filter { it.path !in gone } }
        dupGroups = dupGroups
            .map { g -> DupGroup(g.size, g.files.filter { it.path !in gone }) }
            .filter { it.files.size > 1 }
        selected = selected - gone
    }

    // ---- kopyalar ----

    fun startDupScan() {
        if (dupRunning || !scanned) return
        dupRunning = true
        dupDone = false
        dupProgress = 0f
        dupGroups = emptyList()
        selected = emptySet()
        val src = allFiles
        viewModelScope.launch {
            try {
                val groups = withContext(Dispatchers.IO) {
                    FileScanner.findDuplicates(src, { p -> dupProgress = p }, { isActive })
                }
                dupGroups = groups
                dupDone = true
                autoSelectDups()
            } catch (e: Exception) {
                message = "Kopya arama hatası: ${e.message}"
            }
            dupRunning = false
            recompute()
        }
    }

    /** Her grupta ilkini tut, kalanları seç. */
    fun autoSelectDups() {
        selected = dupGroups.flatMap { g -> g.files.drop(1).map { it.path } }.toSet()
    }

    fun toggleDup(path: String) {
        val g = dupGroups.firstOrNull { grp -> grp.files.any { it.path == path } } ?: return
        if (path in selected) {
            selected = selected - path
        } else {
            val already = g.files.count { it.path in selected }
            if (already >= g.files.size - 1) {
                message = "Her gruptan en az biri kalmalı"
                return
            }
            selected = selected + path
        }
    }

    // ---- uygulamalar ----

    fun loadApps() {
        if (appsLoading) return
        appsLoading = true
        val u = hasUsage
        appsLoadedUsage = u
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { AppsRepo.load(ctx, u) }
            apps = list
            appsLoading = false
            recompute()
            if (appsLoadedUsage != hasUsage) loadApps()
        }
    }

    fun toggleApp(pkg: String) {
        selectedApps = if (pkg in selectedApps) selectedApps - pkg else selectedApps + pkg
    }

    fun setSelectedApps(pkgs: Collection<String>) {
        selectedApps = pkgs.toSet()
    }

    fun onUninstallResult(pkg: String) {
        if (!AppsRepo.isInstalled(ctx, pkg)) {
            apps = apps.filter { it.pkg != pkg }
            selectedApps = selectedApps - pkg
            updateStorage()
            recompute()
        }
    }

    // ---- önbellek ----

    fun clearOwnCache() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) {
                var n = 0L
                val dirs = listOfNotNull(ctx.cacheDir, ctx.externalCacheDir, ctx.codeCacheDir)
                for (d in dirs) {
                    n += dirSize(d)
                    d.listFiles()?.forEach { it.deleteRecursively() }
                }
                n
            }
            message = "Kendi önbelleğimiz temizlendi (${formatSize(freed)})"
        }
    }

    private fun dirSize(f: File): Long =
        if (f.isFile) f.length() else (f.listFiles()?.sumOf { dirSize(it) } ?: 0L)
}
