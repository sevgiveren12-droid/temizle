package app.temizle

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager

object AppsRepo {

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean {
        return try {
            ctx.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun load(ctx: Context, usageAllowed: Boolean): List<AppItem> {
        val pm = ctx.packageManager
        val now = System.currentTimeMillis()
        val lastUsed = HashMap<String, Long>()
        var fg30: Map<String, UsageStats> = emptyMap()

        if (usageAllowed) {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val span = 2L * 365L * 86_400_000L
            val intervals = intArrayOf(
                UsageStatsManager.INTERVAL_YEARLY,
                UsageStatsManager.INTERVAL_MONTHLY,
                UsageStatsManager.INTERVAL_WEEKLY,
                UsageStatsManager.INTERVAL_DAILY
            )
            for (interval in intervals) {
                val list: List<UsageStats>? = try {
                    usm.queryUsageStats(interval, now - span, now)
                } catch (e: Exception) {
                    null
                }
                if (list != null) {
                    for (s in list) {
                        val cur = lastUsed[s.packageName] ?: 0L
                        if (s.lastTimeUsed > cur) lastUsed[s.packageName] = s.lastTimeUsed
                    }
                }
            }
            fg30 = try {
                usm.queryAndAggregateUsageStats(now - 30L * 86_400_000L, now)
            } catch (e: Exception) {
                emptyMap()
            }
        }

        val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val out = ArrayList<AppItem>()
        for (ai in pm.getInstalledApplications(0)) {
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            if (ai.packageName == ctx.packageName) continue

            var total = 0L
            var cache = 0L
            var known = false
            if (usageAllowed) {
                try {
                    val st = ssm.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        ai.packageName,
                        Process.myUserHandle()
                    )
                    total = st.appBytes + st.dataBytes
                    cache = st.cacheBytes
                    known = true
                } catch (e: Exception) {
                    // boyut okunamadı
                }
            }
            val installed = try {
                pm.getPackageInfo(ai.packageName, 0).firstInstallTime
            } catch (e: Exception) {
                0L
            }
            val fgMs = fg30[ai.packageName]?.totalTimeInForeground ?: 0L
            out.add(
                AppItem(
                    pkg = ai.packageName,
                    label = pm.getApplicationLabel(ai).toString(),
                    total = total,
                    cache = cache,
                    sizeKnown = known,
                    lastUsed = lastUsed[ai.packageName] ?: 0L,
                    installed = installed,
                    fgMinutes30 = fgMs / 60_000L
                )
            )
        }
        return out
    }
}
