package app.temizle

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val uninstallQueue = ArrayDeque<String>()
    private var currentUninstall: String? = null

    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val p = currentUninstall
        if (p != null) vm.onUninstallResult(p)
        currentUninstall = null
        nextUninstall()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val actions = Actions(
            openAllFiles = { openAllFiles() },
            openUsage = { openUsage() },
            openAppSettings = { pkg -> openAppSettings(pkg) },
            uninstall = { pkgs -> startUninstall(pkgs) },
            clearSystemCache = { clearSystemCache() },
            openStorageSettings = { openStorageSettings() }
        )
        setContent {
            TemizleTheme {
                AppRoot(vm, actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
    }

    private fun openAllFiles() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e2: Exception) {
                vm.message = "Ayarlar açılamadı"
            }
        }
    }

    private fun openUsage() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e2: Exception) {
                vm.message = "Ayarlar açılamadı"
            }
        }
    }

    private fun openAppSettings(pkg: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        } catch (e: Exception) {
            vm.message = "Uygulama ayarı açılamadı"
        }
    }

    private fun openStorageSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        } catch (e: Exception) {
            vm.message = "Depolama ayarı açılamadı"
        }
    }

    private fun clearSystemCache() {
        try {
            startActivity(Intent("android.os.storage.action.CLEAR_APP_CACHE"))
        } catch (e: Exception) {
            vm.message = "Bu telefonda toplu pencere yok. Tek tek aç."
        }
    }

    private fun startUninstall(pkgs: List<String>) {
        uninstallQueue.clear()
        uninstallQueue.addAll(pkgs)
        nextUninstall()
    }

    private fun nextUninstall() {
        val p = uninstallQueue.removeFirstOrNull()
        if (p == null) {
            vm.loadApps()
            return
        }
        currentUninstall = p
        try {
            uninstallLauncher.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:$p")))
        } catch (e: Exception) {
            currentUninstall = null
            nextUninstall()
        }
    }
}
