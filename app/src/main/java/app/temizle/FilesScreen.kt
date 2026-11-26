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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FilesScreen(vm: MainViewModel, kind: Kind) {
    val all = vm.byKind[kind].orEmpty()
    val folders = remember(all) {
        all.groupBy { it.folder }
            .map { entry -> Pair(entry.key, entry.value.sumOf { it.size }) }
            .sortedByDescending { it.second }
            .take(8)
    }
    val sortMode = vm.sort
    val folderFilter = vm.folderFilter
    val list = remember(all, sortMode, folderFilter) {
        val base = if (folderFilter != null) all.filter { it.folder == folderFilter } else all
        when (sortMode) {
            Sort.Size -> base.sortedByDescending { it.size }
            Sort.OldFirst -> base.sortedBy { it.modified }
            Sort.Name -> base.sortedBy { it.name.lowercase() }
        }
    }
    val total = remember(list) { list.sumOf { it.size } }
    val selected = vm.selected
    val selBytes = remember(selected, all) { all.filter { it.path in selected }.sumOf { it.size } }
    var confirm by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<FileItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            kind.title,
            if (vm.scanned) "${list.size} öğe · ${formatSize(total)}" else null
        ) { vm.go(Screen.Home) }

        if (!vm.scanned) {
            EmptyNote("Önce ana ekranda tara.")
        } else {
            Text(
                kind.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = sortMode == Sort.Size,
                        onClick = { vm.sort = Sort.Size },
                        label = { Text("Büyükten küçüğe") }
                    )
                }
                item {
                    FilterChip(
                        selected = sortMode == Sort.OldFirst,
                        onClick = { vm.sort = Sort.OldFirst },
                        label = { Text("Eskiden yeniye") }
                    )
                }
                item {
                    FilterChip(
                        selected = sortMode == Sort.Name,
                        onClick = { vm.sort = Sort.Name },
                        label = { Text("Ada göre") }
                    )
                }
                item {
                    AssistChip(
                        onClick = { vm.selectAll(list.map { it.path }) },
                        label = { Text("Hepsini seç") }
                    )
                }
                item {
                    AssistChip(
                        onClick = { vm.clearSelection() },
                        label = { Text("Seçimi kaldır") }
                    )
                }
            }
            if (folders.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = folderFilter == null,
                            onClick = { vm.folderFilter = null },
                            label = { Text("Tüm klasörler") }
                        )
                    }
                    items(folders) { entry ->
                        FilterChip(
                            selected = folderFilter == entry.first,
                            onClick = {
                                vm.folderFilter = if (folderFilter == entry.first) null else entry.first
                            },
                            label = { Text("${entry.first} · ${formatSize(entry.second)}") }
                        )
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                if (list.isEmpty()) {
                    EmptyNote("Burada bir şey yok 👍")
                } else if (kind.grid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        gridItems(list, key = { it.path }) { f ->
                            GridCell(
                                f = f,
                                checked = f.path in selected,
                                onToggle = { vm.toggle(f.path) },
                                onPreview = { preview = f }
                            )
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(list, key = { it.path }) { f ->
                            FileRow(
                                f = f,
                                emoji = if (kind == Kind.EmptyDirs) "📂" else emojiFor(f.ext),
                                checked = f.path in selected,
                                onToggle = { vm.toggle(f.path) },
                                onPreview = { preview = f }
                            )
                        }
                    }
                }
            }
            ActionBar(selected.size, selBytes, "Sil", vm.busy) { confirm = true }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Silinsin mi?",
            text = "${selected.size} öğe silinecek (${formatSize(selBytes)}). Geri alınamaz.",
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
private fun GridCell(f: FileItem, checked: Boolean, onToggle: () -> Unit, onPreview: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onToggle, onLongClick = onPreview)
    ) {
        Thumb(f.path, 256, Modifier.fillMaxSize())
        if (checked) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            )
        }
        Text(
            formatSize(f.size),
            fontSize = 11.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color(0x99000000))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
        )
        if (f.ext in Exts.video) {
            Text("▶", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun FileRow(f: FileItem, emoji: String, checked: Boolean, onToggle: () -> Unit, onPreview: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onPreview)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 24.sp)
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
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatSize(f.size), fontWeight = FontWeight.SemiBold)
            Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}
