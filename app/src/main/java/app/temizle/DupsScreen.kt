@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.temizle

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface DupRow {
    class Header(val g: DupGroup) : DupRow
    class Item(val f: FileItem) : DupRow
}

@Composable
fun DupsScreen(vm: MainViewModel) {
    val groups = vm.dupGroups
    val rows = remember(groups) {
        val out = ArrayList<DupRow>()
        for (g in groups) {
            out.add(DupRow.Header(g))
            for (f in g.files) out.add(DupRow.Item(f))
        }
        out
    }
    val waste = remember(groups) { groups.sumOf { it.waste } }
    val selected = vm.selected
    val selBytes = remember(selected, groups) {
        groups.sumOf { g -> g.files.filter { it.path in selected }.sumOf { it.size } }
    }
    var confirm by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<FileItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Kopyalar",
            if (vm.dupDone) "${groups.size} grup · ${formatSize(waste)} boşa gidiyor" else null
        ) { vm.go(Screen.Home) }

        when {
            !vm.scanned -> EmptyNote("Önce ana ekranda tara.")

            vm.dupRunning -> Column(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Dosya içerikleri karşılaştırılıyor…", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { vm.dupProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Telefon çok doluysa birkaç dakika sürebilir. Ekranı kapatma.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            !vm.dupDone -> Column(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Aynı içerikli dosyaları bulur: foto, video, belge, her şey. " +
                        "Sadece birebir aynı olanları gösterir. Her gruptan biri hep kalır.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { vm.startDupScan() }) { Text("Kopyaları ara") }
            }

            groups.isEmpty() -> EmptyNote("Kopya yok 👍")

            else -> {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = { vm.autoSelectDups() }, label = { Text("Fazlaları seç") })
                    AssistChip(onClick = { vm.clearSelection() }, label = { Text("Seçimi kaldır") })
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(
                        rows,
                        key = { r ->
                            when (r) {
                                is DupRow.Header -> "h:" + r.g.files[0].path
                                is DupRow.Item -> r.f.path
                            }
                        }
                    ) { r ->
                        when (r) {
                            is DupRow.Header -> Text(
                                "${r.g.files.size} kopya · her biri ${formatSize(r.g.size)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                            )
                            is DupRow.Item -> DupItemRow(
                                f = r.f,
                                checked = r.f.path in selected,
                                onToggle = { vm.toggleDup(r.f.path) },
                                onPreview = { preview = r.f }
                            )
                        }
                    }
                }
                ActionBar(selected.size, selBytes, "Sil", vm.busy) { confirm = true }
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Silinsin mi?",
            text = "${selected.size} kopya silinecek (${formatSize(selBytes)}). Her gruptan en az biri kalır. Geri alınamaz.",
            confirmLabel = "Sil",
            onConfirm = {
                confirm = false
                vm.deleteSelected()
            },
            onDismiss = { confirm = false }
        )
    }
    val pv = preview
    if (pv != null) {
        PreviewDialog(pv) { preview = null }
    }
}

@Composable
private fun DupItemRow(f: FileItem, checked: Boolean, onToggle: () -> Unit, onPreview: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onPreview)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (f.ext in Exts.image || f.ext in Exts.video) {
                Thumb(f.path, 128, Modifier.fillMaxSize())
            } else {
                Text(emojiFor(f.ext), fontSize = 22.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${shortDir(f.dir)} · ${formatDate(f.modified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
