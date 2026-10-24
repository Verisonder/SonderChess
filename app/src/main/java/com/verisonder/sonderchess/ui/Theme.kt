package com.verisonder.sonderchess.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.verisonder.sonderchess.R
import com.verisonder.sonderchess.core.Label

/** The review look: warm dark greys, a green board, one green action colour. */
object C {
    val bg = Color(0xFF262522)
    val bar = Color(0xFF21201D)
    val panel = Color(0xFF312E2B)
    val panel2 = Color(0xFF3C3936)
    val line = Color(0xFF45423E)
    val text = Color(0xFFFFFFFF)
    val dim = Color(0xFFBFBDBA)
    val faint = Color(0xFF8B8987)
    val green = Color(0xFF81B64C)
    val greenDark = Color(0xFF45753C)
    val bubble = Color(0xFFFFFFFF)
    val bubbleText = Color(0xFF312E2B)
    val light = Color(0xFFEBECD0)
    val dark = Color(0xFF739552)
    val arrow = Color(0xCC96BC4B)
    val evalWhite = Color(0xFFF5F5F5)
    val evalBlack = Color(0xFF403D39)
    val error = Color(0xFFFA412D)
}

fun Label.colour() = Color(color)

/** Unbounded: headings and numbers. */
val Display =
    FontFamily(
        Font(R.font.unbounded_bold, FontWeight.Bold),
        Font(R.font.unbounded_extrabold, FontWeight.ExtraBold),
    )

/** DM Sans: everything else. */
val Body =
    FontFamily(
        Font(R.font.dmsans_regular, FontWeight.Normal),
        Font(R.font.dmsans_medium, FontWeight.Medium),
        Font(R.font.dmsans_bold, FontWeight.Bold),
    )

fun body(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    color: Color = C.text,
) = TextStyle(fontFamily = Body, fontWeight = weight, fontSize = size.sp, color = color, lineHeight = (size * 1.3f).sp)

fun display(
    size: Int,
    color: Color = C.text,
) = TextStyle(
    fontFamily = Display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp,
    color = color,
    letterSpacing = (-0.02f * size).sp,
)

@Composable
fun ChessTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = C.green,
                onPrimary = C.text,
                background = C.bg,
                onBackground = C.text,
                surface = C.panel,
                onSurface = C.text,
                surfaceVariant = C.panel2,
                onSurfaceVariant = C.dim,
                outline = C.line,
            ),
        typography =
            Typography().let { t ->
                t.copy(
                    bodyLarge = t.bodyLarge.copy(fontFamily = Body),
                    bodyMedium = t.bodyMedium.copy(fontFamily = Body),
                    bodySmall = t.bodySmall.copy(fontFamily = Body),
                    labelLarge = t.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
                    titleMedium = t.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
                )
            },
        content = content,
    )
}
