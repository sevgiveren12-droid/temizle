@file:OptIn(ExperimentalMaterial3Api::class)

package app.temizle

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun CacheScreen(vm: MainViewModel, act: Actions) {
    val list = remember(vm.apps) { vm.apps.filter { it.cache > 0L }.sortedByDescending { it.cache } }
    val total = remember(list) { list.sumOf { it.cache } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Önbellek",
            if (vm.hasUsage) "${list.size} uygulama · ${formatSize(total)}" else null
        ) { vm.go(Screen.Home) }

        if (!vm.hasUsage) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                PermBanner(
                    "Kullanım erişimi gerekli",
                    "Başka uygulamaların önbellek boyutunu görmek için bu izin lazım.",
                    "İzin ver",
                    act.openUsage
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Android, başka uygulamanın önbelleğini bizim silmemize izin vermez. " +
                            "Listeden bir uygulamada \"Aç\" de, sonra Depolama → Önbelleği temizle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = act.clearSystemCache) { Text("Toplu dene") }
                        OutlinedButton(onClick = act.openStorageSettings) { Text("Depolama ayarı") }
                    }
                    TextButton(onClick = { vm.clearOwnCache() }) { Text("Kendi önbelleğimizi temizle") }
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(list, key = { it.pkg }) { a ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(a.pkg, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Önbellek ${formatSize(a.cache)} · toplam ${formatSize(a.total)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { act.openAppSettings(a.pkg) }) {
                            Text("Aç", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
