package ru.zamery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Читает фото уменьшенным до ~maxPx по большей стороне и сразу ставит прямо по EXIF. */
fun loadBitmap(file: File, maxPx: Int): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val deg = try {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) { 0f }
    if (deg == 0f) return bmp
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
}

/**
 * Сохраняем у себя уже повёрнутую и ужатую копию (до 2000 px, JPEG 88):
 * фото не ложится боком ни в одной программе и не занимает лишнего места.
 */
suspend fun storePhoto(context: Context, src: Uri, dest: File): Boolean = withContext(Dispatchers.IO) {
    try {
        val tmp = File(context.cacheDir, "in.jpg")
        context.contentResolver.openInputStream(src)?.use { inp -> FileOutputStream(tmp).use { inp.copyTo(it) } } ?: return@withContext false
        val bmp = loadBitmap(tmp, 2000) ?: return@withContext false
        FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        tmp.delete()
        true
    } catch (e: Exception) { false }
}

/** Снимок камеры уже лежит в dest — просто приводим его в порядок на месте. */
suspend fun normalizePhoto(dest: File): Boolean = withContext(Dispatchers.IO) {
    try {
        val bmp = loadBitmap(dest, 2000) ?: return@withContext false
        FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        true
    } catch (e: Exception) { false }
}

@Composable
fun PhotoImage(file: File, modifier: Modifier, maxPx: Int = 600, crop: Boolean = true) {
    val t = LocalTokens.current
    val img by produceState<ImageBitmap?>(null, file.path, maxPx) {
        value = withContext(Dispatchers.IO) { loadBitmap(file, maxPx)?.asImageBitmap() }
    }
    val i = img
    if (i == null) {
        Box(modifier.background(t.line))
    } else {
        Image(
            bitmap = i, contentDescription = null, modifier = modifier,
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
        )
    }
}
