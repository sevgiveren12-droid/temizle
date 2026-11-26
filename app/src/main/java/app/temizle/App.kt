package app.temizle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class Actions(
    val openAllFiles: () -> Unit,
    val openUsage: () -> Unit,
    val openAppSettings: (String) -> Unit,
    val uninstall: (List<String>) -> Unit,
    val clearSystemCache: () -> Unit,
    val openStorageSettings: () -> Unit
)

@Composable
fun AppRoot(vm: MainViewModel, act: Actions) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        val m = vm.message
        if (m != null) {
            snack.showSnackbar(m)
            vm.message = null
        }
    }
    BackHandler(enabled = vm.screen != Screen.Home) { vm.go(Screen.Home) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            when (val s = vm.screen) {
                Screen.Home -> HomeScreen(vm, act)
                is Screen.Files -> FilesScreen(vm, s.kind)
                Screen.Apps -> AppsScreen(vm, act)
                Screen.Cache -> CacheScreen(vm, act)
                Screen.Dups -> DupsScreen(vm)
            }
        }
        SnackbarHost(
            snack,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp)
        )
    }
}
