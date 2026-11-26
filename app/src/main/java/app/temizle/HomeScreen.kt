@file:OptIn(ExperimentalMaterial3Api::class)

package app.temizle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class TileData(
    val emoji: String,
    val title: String,
    val sub: String,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(vm: MainViewModel, act: Actions) {
    val unused = remember(vm.apps, vm.hasUsage) {
        if (vm.hasUsage) vm.apps.count { it.lastUsed == 0L || daysSince(it.lastUsed) >= 30 } else 0
    }
    val kindStats = remember(vm.byKind) {
        Kind.entries.associateWith { k ->
            val l = vm.byKind[k].orEmpty()
            Pair(l.size, l.sumOf { it.size })
        }
    }
    val dupWaste = remember(vm.dupGroups) { vm.dupGroups.sumOf { it.waste } }
    val totalCache = remember(vm.apps) { vm.apps.sumOf { it.cache } }

    val tiles = buildList<TileData> {
        val appsSub = when {
            vm.appsLoading && vm.apps.isEmpty() -> "Yükleniyor…"
            vm.hasUsage -> "${vm.apps.size} uygulama · $unused seyrek"
            else -> "${vm.apps.size} uygulama"
        }
        add(TileData("📱", "Uygulamalar", appsSub, true) { vm.go(Screen.Apps) })
        add(
            TileData(
                "🧽", "Önbellek",
                if (vm.hasUsage) formatSize(totalCache) else "İzin gerekli",
                true
            ) { vm.go(Screen.Cache) }
        )
        val dupSub = when {
            !vm.hasAllFiles -> "İzin gerekli"
            !vm.scanned -> "Taranıyor…"
            vm.dupDone -> "${vm.dupGroups.size} grup · ${formatSize(dupWaste)}"
            else -> "Aramak için dokun"
        }
        add(TileData("👯", "Kopyalar", dupSub, vm.hasAllFiles && vm.scanned) { vm.go(Screen.Dups) })
        for (k in Kind.entries) {
            val st = kindStats[k] ?: Pair(0, 0L)
            val sub = when {
                !vm.hasAllFiles -> "İzin gerekli"
                !vm.scanned -> "Taranıyor…"
                else -> "${st.first} · ${formatSize(st.second)}"
            }
            add(TileData(k.emoji, k.title, sub, vm.hasAllFiles && vm.scanned) { vm.go(Screen.Files(k)) })
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Temizle", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        StorageCard(vm)
        if (!vm.hasAllFiles) {
            PermBanner(
                "Tüm dosyalara erişim",
                "Foto, video, kopya ve büyük dosya taraması için gerekli. Açılan ekranda Temizle'yi aç.",
                "İzin ver",
                act.openAllFiles
            )
        }
        if (!vm.hasUsage) {
            PermBanner(
                "Kullanım erişimi",
                "Uygulama boyutu, önbellek ve son kullanım için gerekli. Açılan listede Temizle'yi aç.",
                "İzin ver",
                act.openUsage
            )
        }
        ScanCard(vm)
        for (pair in tiles.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (t in pair) {
                    Tile(t, Modifier.weight(1f).height(112.dp))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StorageCard(vm: MainViewModel) {
    val used = (vm.storageTotal - vm.storageFree).coerceAtLeast(0L)
    val frac = if (vm.storageTotal > 0L) used.toFloat() / vm.storageTotal.toFloat() else 0f
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Depolama", fontWeight = FontWeight.SemiBold)
            Text(
                "${formatSize(used)} dolu / ${formatSize(vm.storageTotal)}",
                style = MaterialTheme.typography.titleMedium
            )
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                "Boş: ${formatSize(vm.storageFree)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScanCard(vm: MainViewModel) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.scanned) {
                Text(
                    "Kazanılabilir (tahmini)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatSize(vm.reclaimable),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Gereksiz + APK + arşiv + kopya fazlası + uygulama önbelleği.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("Önce tara. Sonra kategorilere gir.")
            }
            Button(onClick = { vm.startScan() }, enabled = vm.hasAllFiles && !vm.scanning) {
                Text(
                    when {
                        vm.scanning -> "Taranıyor… ${vm.scanCount} dosya"
                        vm.scanned -> "Yeniden tara"
                        else -> "Telefonu tara"
                    }
                )
            }
        }
    }
}

@Composable
private fun Tile(t: TileData, modifier: Modifier) {
    Surface(
        onClick = t.onClick,
        enabled = t.enabled,
        modifier = modifier.alpha(if (t.enabled) 1f else 0.5f),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(t.emoji, fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                t.sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
