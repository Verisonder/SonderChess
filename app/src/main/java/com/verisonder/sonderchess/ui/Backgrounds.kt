package com.verisonder.sonderchess.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A background behind the review board: a colour or gradient, or your own picture. */
data class BackgroundStyle(
    val id: String,
    val name: String,
    val brush: Brush,
    /** Dark backgrounds keep white text; light ones get a dark band behind text. */
    val dark: Boolean = true,
)

object Backgrounds {
    val styles =
        listOf(
            BackgroundStyle("default", "Standard", Brush.verticalGradient(listOf(C.bg, C.bg))),
            BackgroundStyle("felt", "Felt", Brush.radialGradient(listOf(Color(0xFF4F8A45), Color(0xFF2F5A2A)), radius = 2200f)),
            BackgroundStyle("smoke", "Smoke", Brush.verticalGradient(listOf(Color(0xFF1E1D1B), Color(0xFF55524E)))),
            BackgroundStyle("light", "Light", Brush.verticalGradient(listOf(Color(0xFFD2D2D2), Color(0xFFBDBDBD))), dark = false),
            BackgroundStyle("walnut", "Walnut", Brush.linearGradient(listOf(Color(0xFF5A3A22), Color(0xFF3B2414), Color(0xFF5E3D25)))),
            BackgroundStyle("midnight", "Midnight", Brush.linearGradient(listOf(Color(0xFF1B2638), Color(0xFF2E3D57), Color(0xFF141B27)))),
            BackgroundStyle(
                "maple",
                "Maple",
                Brush.linearGradient(listOf(Color(0xFFE9C891), Color(0xFFD6A866), Color(0xFFEBCB97))),
                dark = false,
            ),
            BackgroundStyle("plum", "Plum", Brush.verticalGradient(listOf(Color(0xFF2A1E33), Color(0xFF4B3560)))),
        )

    fun style(id: String) = styles.firstOrNull { it.id == id } ?: styles[0]

    fun file(context: Context) = File(context.filesDir, "background.jpg")

    /** Scales the picture down to phone size and keeps a copy. */
    fun save(
        context: Context,
        uri: Uri,
    ): Boolean =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1440 && bounds.outHeight / (sample * 2) >= 1440) sample *= 2
            val bmp =
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching false
            file(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        }.getOrDefault(false)
}

/** Draws the chosen background, edge to edge. */
@Composable
fun ReviewBackground(modifier: Modifier = Modifier) {
    val id = AppModel.background
    if (id == "custom") {
        val context = LocalContext.current
        val image by produceState<ImageBitmap?>(null, AppModel.backgroundVersion) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching { BitmapFactory.decodeFile(Backgrounds.file(context).path)?.asImageBitmap() }.getOrNull()
                }
        }
        Box(modifier.fillMaxSize().background(C.bg)) {
            image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Color(0x40000000)))
        }
    } else {
        Box(modifier.fillMaxSize().background(Backgrounds.style(id).brush))
    }
}

/** Whether text can sit straight on the background (false: give it a dark band). */
val backgroundIsDark: Boolean
    @Composable get() = AppModel.background == "custom" || Backgrounds.style(AppModel.background).dark
