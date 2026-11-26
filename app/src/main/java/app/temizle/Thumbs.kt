package app.temizle

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun peek(path: String, px: Int): Bitmap? = cache.get("$path#$px")

    fun load(path: String, px: Int): Bitmap? {
        val key = "$path#$px"
        val hit = cache.get(key)
        if (hit != null) return hit
        val f = File(path)
        val ext = f.name.substringAfterLast('.', "").lowercase()
        val bmp: Bitmap? = try {
            when {
                ext in Exts.video -> ThumbnailUtils.createVideoThumbnail(f, Size(px, px), null)
                ext in Exts.image -> ThumbnailUtils.createImageThumbnail(f, Size(px, px), null)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }
}

object IconCache {
    private val cache = LruCache<String, Bitmap>(300)

    fun peek(pkg: String): Bitmap? = cache.get(pkg)

    fun load(ctx: Context, pkg: String): Bitmap? {
        val hit = cache.get(pkg)
        if (hit != null) return hit
        val bmp: Bitmap? = try {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache.put(pkg, bmp)
        return bmp
    }
}

@Composable
fun Thumb(
    path: String,
    px: Int,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop
) {
    val bmp by produceState<Bitmap?>(ThumbCache.peek(path, px), path, px) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { ThumbCache.load(path, px) }
        }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = scale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(emojiFor(path.substringAfterLast('.', "").lowercase()))
        }
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(IconCache.peek(pkg), pkg) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { IconCache.load(ctx, pkg) }
        }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size))
    }
}

@Composable
fun PreviewDialog(f: FileItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
        title = {
            Text(
                f.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                if (f.ext in Exts.image || f.ext in Exts.video) {
                    Thumb(f.path, 900, Modifier.fillMaxWidth().height(280.dp), ContentScale.Fit)
                }
                Text("${formatSize(f.size)} · ${formatDate(f.modified)}")
                Text(
                    shortDir(f.dir),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
