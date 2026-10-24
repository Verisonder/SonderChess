package com.verisonder.sonderchess.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.core.Piece
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/*
 * A little game played out on a 3x3 tile, looping: pieces slide in and take each other, a
 * knight jumps in its L, a pawn reaches the far side and turns into a queen with a flash.
 * Solid white pieces are one side, outlined ones the other. Columns and rows count from the
 * top left.
 */

private data class Man(
    val id: Int,
    val kind: Int,
    val solid: Boolean,
    val c: Int,
    val r: Int,
)

private sealed interface Step {
    /** [id] goes to (c, r), taking whatever stands there. */
    data class Go(
        val id: Int,
        val c: Int,
        val r: Int,
    ) : Step

    /** A new piece fades in. */
    data class Enter(
        val man: Man,
    ) : Step

    /** [id] turns into [kind] on [solid]'s side, with a flash. */
    data class Become(
        val id: Int,
        val kind: Int,
        val solid: Boolean,
    ) : Step
}

private val opening =
    listOf(
        Man(1, Piece.BISHOP, false, 0, 0),
        Man(2, Piece.ROOK, false, 2, 0),
        Man(3, Piece.QUEEN, false, 0, 1),
        Man(4, Piece.ROOK, true, 1, 1),
        Man(5, Piece.PAWN, false, 0, 2),
    )

private val script =
    listOf(
        Step.Go(5, 1, 1), // the pawn takes the rook
        Step.Enter(Man(6, Piece.KNIGHT, true, 2, 2)),
        Step.Go(2, 2, 1),
        Step.Go(6, 0, 1), // the knight takes the queen
        Step.Go(2, 2, 0),
        Step.Go(6, 2, 0), // and the rook
        Step.Go(5, 0, 0), // the pawn takes the bishop on the last row
        Step.Become(5, Piece.QUEEN, true), // and turns into a queen
        Step.Enter(Man(7, Piece.BISHOP, true, 2, 2)),
        Step.Go(5, 2, 0), // the new queen takes the knight
        Step.Enter(Man(8, Piece.ROOK, true, 1, 2)),
        Step.Go(5, 2, 1),
        Step.Enter(Man(9, Piece.KNIGHT, true, 0, 1)),
        Step.Enter(Man(10, Piece.PAWN, false, 0, 2)),
    )

/** Where everyone stands after the first [n] steps. */
private fun after(n: Int): List<Man> {
    var men = opening
    for (s in script.take(n)) {
        men =
            when (s) {
                is Step.Go -> {
                    val m = men.first { it.id == s.id }
                    men.filter { it.id != s.id && !(it.c == s.c && it.r == s.r) } + m.copy(c = s.c, r = s.r)
                }
                is Step.Enter -> men + s.man
                is Step.Become -> men.map { if (it.id == s.id) it.copy(kind = s.kind, solid = s.solid) else it }
            }
    }
    return men
}

@Composable
fun PieceDance() {
    val set = AppModel.pieces
    val painters = (Piece.PAWN..Piece.KING).associateWith { painterResource(set.white[it - 1]) }
    // Step n of the script is playing; past the end the tile fades back to the opening.
    var n by remember { mutableIntStateOf(0) }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            for (i in 0..script.size) {
                n = i
                t.snapTo(0f)
                val last = i == script.size
                t.animateTo(1f, tween(if (last) 700 else 380, easing = LinearEasing))
                delay(if (last) 250 else 170)
            }
        }
    }
    Canvas(Modifier.size(196.dp, 204.dp)) {
        val w = size.width
        val s = w / 3f
        val corner = CornerRadius(w * 0.1f)
        // A darker edge under the board so it stands like a tile.
        drawRoundRect(Color(0xFF3B3A38), cornerRadius = corner)
        clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, w, w, corner)) }) {
            for (r in 0..2) {
                for (c in 0..2) {
                    drawRect(if ((r + c) % 2 == 0) Color(0xFF5F5E5B) else Color(0xFF4E4D4A), Offset(c * s, r * s), Size(s, s))
                }
            }
            val p = t.value
            if (n == script.size) {
                // Back to the start: the last scene fades out as the opening fades in.
                for (m in after(script.size)) man(painters, m, m.c * s, m.r * s, s, 1f - p)
                for (m in opening) man(painters, m, m.c * s, m.r * s, s, p)
                return@clipPath
            }
            val before = after(n)
            when (val st = script[n]) {
                is Step.Go -> {
                    val mover = before.first { it.id == st.id }
                    val victim = before.firstOrNull { it.c == st.c && it.r == st.r && it.id != st.id }
                    for (m in before) if (m.id != mover.id && m.id != victim?.id) man(painters, m, m.c * s, m.r * s, s, 1f)
                    // The taken piece fades as the attacker arrives.
                    victim?.let { man(painters, it, it.c * s, it.r * s, s, 1f - ((p - 0.55f) / 0.35f).coerceIn(0f, 1f)) }
                    val q = FastOutSlowInEasing.transform(p)
                    val knight = mover.kind == Piece.KNIGHT && abs(st.c - mover.c) + abs(st.r - mover.r) == 3
                    val x = mover.c + (st.c - mover.c) * q
                    val y = mover.r + (st.r - mover.r) * q
                    // A knight hops over; everything else slides.
                    val lift = if (knight) sin(Math.PI * q).toFloat() * s * 0.3f else 0f
                    man(painters, mover, x * s, y * s - lift, s, 1f, if (knight) 1f + sin(Math.PI * q).toFloat() * 0.12f else 1f)
                }
                is Step.Enter -> {
                    for (m in before) man(painters, m, m.c * s, m.r * s, s, 1f)
                    val q = FastOutSlowInEasing.transform(p)
                    man(painters, st.man, st.man.c * s, st.man.r * s, s, q, 0.7f + 0.3f * q)
                }
                is Step.Become -> {
                    val m = before.first { it.id == st.id }
                    for (o in before) if (o.id != m.id) man(painters, o, o.c * s, o.r * s, s, 1f)
                    val shown = if (p < 0.45f) m else m.copy(kind = st.kind, solid = st.solid)
                    man(painters, shown, m.c * s, m.r * s, s, 1f)
                    // A white flash that swells over the square and fades.
                    val flash = sin(Math.PI * p).toFloat()
                    drawCircle(
                        Color.White.copy(alpha = flash * 0.95f),
                        radius = s * (0.2f + 0.3f * p),
                        center = Offset(m.c * s + s / 2f, m.r * s + s / 2f),
                    )
                }
            }
        }
    }
}

/** One piece at (x, y): solid white, or outlined (white edge, tile-dark inside). */
private fun DrawScope.man(
    painters: Map<Int, Painter>,
    m: Man,
    x: Float,
    y: Float,
    s: Float,
    alpha: Float,
    grow: Float = 1f,
) {
    if (alpha <= 0f) return
    val painter = painters.getValue(m.kind)
    val size = s * 0.78f
    val pad = (s - size) / 2f
    translate(x + pad, y + pad) {
        scale(grow, grow, Offset(size / 2f, size / 2f)) {
            with(painter) {
                draw(Size(size, size), alpha = alpha, colorFilter = ColorFilter.tint(Color.White))
                if (!m.solid) {
                    // A smaller dark copy inside leaves a white outline.
                    val inner = 0.8f
                    scale(inner, inner, Offset(size / 2f, size * 0.55f)) {
                        draw(Size(size, size), alpha = alpha, colorFilter = ColorFilter.tint(Color(0xFF55544F)))
                    }
                }
            }
        }
    }
}
