@file:OptIn(ExperimentalMaterial3Api::class)

package app.temizle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private fun matches(a: AppItem, f: AppFilter, usage: Boolean): Boolean = when (f) {
    AppFilter.All -> true
    AppFilter.Never -> usage && a.lastUsed == 0L
    AppFilter.D30 -> usage && (a.lastUsed == 0L || daysSince(a.lastUsed) >= 30)
    AppFilter.D90 -> usage && (a.lastUsed == 0L || daysSince(a.lastUsed) >= 90)
    AppFilter.Big -> a.total >= 100L * 1024L * 1024L
}

@Composable
fun AppsScreen(vm: MainViewModel, act: Actions) {
    val filter = vm.appFilter
    val sortMode = vm.appSort
    val usage = vm.hasUsage
    val list = remember(vm.apps, filter, sortMode, usage) {
        val base = vm.apps.filter { matches(it, filter, usage) }
        when (sortMode) {
            Sort.Size -> base.sortedByDescending { it.total }
            Sort.OldFirst -> base.sortedBy { it.lastUsed }
            Sort.Name -> base.sortedBy { it.label.lowercase() }
        }
    }
    val total = remember(list) { list.sumOf { it.total } }
    val selected = vm.selectedApps
    val selBytes = remember(selected, vm.apps) { vm.apps.filter { it.pkg in selected }.sumOf { it.total } }
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Uygulamalar",
            if (vm.appsLoading && vm.apps.isEmpty()) "Yükleniyor…" else "${list.size} uygulama · ${formatSize(total)}"
        ) { vm.go(Screen.Home) }

        if (!usage) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                PermBanner(
                    "Kullanım erişimi yok",
                    "Boyut ve son kullanım görünmüyor. İzin verirsen seyrek uygulamalar da bulunur.",
                    "İzin ver",
                    act.openUsage
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AppFilter.entries.toList()) { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { vm.appFilter = f },
                    label = { Text(f.label) }
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = sortMode == Sort.Size,
                    onClick = { vm.appSort = Sort.Size },
                    label = { Text("Boyut") }
                )
            }
            item {
                FilterChip(
                    selected = sortMode == Sort.OldFirst,
                    onClick = { vm.appSort = Sort.OldFirst },
                    label = { Text("En eski kullanım") }
                )
            }
            item {
                FilterChip(
                    selected = sortMode == Sort.Name,
                    onClick = { vm.appSort = Sort.Name },
                    label = { Text("Ad") }
                )
            }
            item {
                AssistChip(
                    onClick = { vm.setSelectedApps(list.map { it.pkg }) },
                    label = { Text("Hepsini seç") }
                )
            }
            item {
                AssistChip(
                    onClick = { vm.setSelectedApps(emptyList()) },
                    label = { Text("Seçimi kaldır") }
                )
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(list, key = { it.pkg }) { a ->
                AppRow(a, usage, a.pkg in selected) { vm.toggleApp(a.pkg) }
            }
        }
        ActionBar(selected.size, selBytes, "Kaldır", false) { confirm = true }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Kaldırılsın mı?",
            text = "${selected.size} uygulama kaldırılacak. Android her biri için ayrıca onay soracak.",
            confirmLabel = "Kaldır",
            onConfirm = {
                confirm = false
                act.uninstall(selected.toList())
            },
            onDismiss = { confirm = false }
        )
    }
}

@Composable
private fun AppRow(a: AppItem, usage: Boolean, checked: Boolean, onToggle: () -> Unit) {
    val used = when {
        !usage -> "Kullanım izni yok"
        a.lastUsed == 0L -> "Kayıt yok (hiç açılmamış / çok eski)"
        else -> {
            val d = daysSince(a.lastUsed)
            if (d == 0) "Bugün açıldı" else "$d gün önce açıldı"
        }
    }
    val fg = if (usage) " · son 30 gün ~${a.fgMinutes30} dk" else ""
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(a.pkg, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                used + fg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(if (a.sizeKnown) formatSize(a.total) else "?", fontWeight = FontWeight.SemiBold)
            Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}
