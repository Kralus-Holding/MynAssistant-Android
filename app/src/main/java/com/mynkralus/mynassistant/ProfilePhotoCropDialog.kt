package com.mynkralus.mynassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class ProfileCropSelection(
    val left: Int,
    val top: Int,
    val side: Int
)

@Composable
internal fun ProfilePhotoCropDialog(
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceBitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(sourceUri) { mutableStateOf(true) }
    var cropping by remember(sourceUri) { mutableStateOf(false) }
    var errorMessage by remember(sourceUri) { mutableStateOf<String?>(null) }
    var zoom by remember(sourceUri) { mutableFloatStateOf(0.72f) }
    var horizontalPosition by remember(sourceUri) { mutableFloatStateOf(0.5f) }
    var verticalPosition by remember(sourceUri) { mutableFloatStateOf(0.5f) }

    LaunchedEffect(sourceUri) {
        loading = true
        errorMessage = null
        try {
            sourceBitmap = withContext(Dispatchers.IO) {
                decodeProfileImage(context, sourceUri)
            }
            if (sourceBitmap == null) {
                errorMessage = "Bu fotoğraf okunamadı. Galeriden başka bir fotoğraf seç."
            }
        } catch (error: Exception) {
            sourceBitmap = null
            errorMessage = "Fotoğraf açılamadı: ${error.message.orEmpty().take(120)}"
        } finally {
            loading = false
        }
    }

    val bitmap = sourceBitmap
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    val selection = remember(bitmap, zoom, horizontalPosition, verticalPosition) {
        if (bitmap == null) {
            null
        } else {
            val minDimension = min(bitmap.width, bitmap.height)
            val side = (minDimension * zoom).roundToInt().coerceIn(1, minDimension)
            val left = ((bitmap.width - side) * horizontalPosition).roundToInt()
                .coerceIn(0, bitmap.width - side)
            val top = ((bitmap.height - side) * verticalPosition).roundToInt()
                .coerceIn(0, bitmap.height - side)
            ProfileCropSelection(left, top, side)
        }
    }

    Dialog(
        onDismissRequest = { if (!cropping) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 720.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Profil fotoğrafını kırp", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Kare 1:1 önizlemede görünecek alanı seç. Kaydedilecek fotoğraf da aynı kare olacak.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )

                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    imageBitmap != null && selection != null -> Canvas(
                        modifier = Modifier.fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        drawImage(
                            image = imageBitmap,
                            srcOffset = IntOffset(selection.left, selection.top),
                            srcSize = IntSize(selection.side, selection.side),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Medium
                        )
                    }
                }

                Text("Yakınlaştırma", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = zoom,
                    onValueChange = { zoom = it },
                    valueRange = 0.2f..1f,
                    enabled = imageBitmap != null && !cropping
                )
                Text("Yatay konum", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = horizontalPosition,
                    onValueChange = { horizontalPosition = it },
                    valueRange = 0f..1f,
                    enabled = imageBitmap != null && !cropping
                )
                Text("Dikey konum", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = verticalPosition,
                    onValueChange = { verticalPosition = it },
                    valueRange = 0f..1f,
                    enabled = imageBitmap != null && !cropping
                )

                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (cropping) {
                    androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !loading && !cropping,
                        modifier = Modifier.weight(1f)
                    ) { Text("Vazgeç") }
                    Button(
                        onClick = {
                            val original = sourceBitmap
                            val crop = selection
                            if (original == null || crop == null || loading || cropping) {
                                errorMessage = "Fotoğraf henüz hazır değil. Biraz bekleyip tekrar dene."
                            } else {
                                cropping = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val croppedUri = withContext(Dispatchers.IO) {
                                            writeSquareCrop(context, original, crop)
                                        }
                                        onConfirm(croppedUri)
                                    } catch (error: Exception) {
                                        errorMessage = "Kırpma kaydedilemedi: ${error.message.orEmpty().take(120)}"
                                    } finally {
                                        cropping = false
                                    }
                                }
                            }
                        },
                        enabled = !loading && !cropping && imageBitmap != null && selection != null,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (cropping) "Hazırlanıyor…" else "Kareyi kullan") }
                }
            }
        }
    }
}

private fun decodeProfileImage(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 1800) {
        sampleSize *= 2
    }
    val decoded = resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(
            stream,
            null,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    } ?: return null

    val orientation = try {
        resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> Unit
    }
    if (matrix.isIdentity) return decoded
    return try {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    } catch (_: Exception) {
        decoded
    }
}

private fun writeSquareCrop(context: Context, source: Bitmap, crop: ProfileCropSelection): Uri {
    val square = Bitmap.createBitmap(source, crop.left, crop.top, crop.side, crop.side)
    val file = File(context.cacheDir, "myn-profile-${System.currentTimeMillis()}.jpg")
    try {
        FileOutputStream(file).use { output ->
            if (!square.compress(Bitmap.CompressFormat.JPEG, 92, output)) {
                throw IllegalStateException("JPEG fotoğraf oluşturulamadı.")
            }
            output.flush()
        }
    } finally {
        square.recycle()
    }
    return Uri.fromFile(file)
}
