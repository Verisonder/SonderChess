package com.verisonder.sonderchess.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.verisonder.sonderchess.R
import com.verisonder.sonderchess.core.Label
import com.verisonder.sonderchess.core.Move
import com.verisonder.sonderchess.core.Position
import com.verisonder.sonderchess.core.fileOf
import com.verisonder.sonderchess.core.rankOf
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The 12 piece images, indexed by piece int (+6 offset). */
@Composable
fun piecePainters(): Array<Painter?> {
    val w =
        listOf(R.drawable.piece_wp, R.drawable.piece_wn, R.drawable.piece_wb, R.drawable.piece_wr, R.drawable.piece_wq, R.drawable.piece_wk)
    val b =
        listOf(R.drawable.piece_bp, R.drawable.piece_bn, R.drawable.piece_bb, R.drawable.piece_br, R.drawable.piece_bq, R.drawable.piece_bk)
    val out = arrayOfNulls<Painter>(13)
    for (i in 0 until 6) {
        out[6 + i + 1] = painterResource(w[i])
        out[6 - i - 1] = painterResource(b[i])
    }
    return out
}

/** One piece gliding between squares. */
private data class Slide(
    val piece: Int,
    val from: Int,
    val to: Int,
)

/** How to get from one position to the next on screen: glides, fade-outs, fade-ins. */
private class BoardAnim(
    val slides: List<Slide>,
    val fadeOut: List<Pair<Int, Int>>,
    val fadeIn: Set<Int>,
)

/**
 * Works out the motion between any two positions: a piece that left one square and
 * appears on another glides there (nearest match), captured pieces fade out, pieces that come
 * back (stepping backwards) fade in. Handles one move, a jump of many moves, or "Best".
 */
private fun diff(
    a: Position,
    b: Position,
): BoardAnim {
    val gone = (0 until 64).filter { a[it] != 0 && a[it] != b[it] }.toMutableList()
    val came = (0 until 64).filter { b[it] != 0 && b[it] != a[it] }
    val slides = ArrayList<Slide>()
    val fadeIn = HashSet<Int>()

    fun dist(
        x: Int,
        y: Int,
    ) = abs(fileOf(x) - fileOf(y)) + abs(rankOf(x) - rankOf(y))
    for (t in came) {
        val p = b[t]
        val src = gone.filter { a[it] == p }.minByOrNull { dist(it, t) }
        if (src != null) {
            slides.add(Slide(p, src, t))
            gone.remove(src)
        } else {
            fadeIn.add(t)
        }
    }
    return BoardAnim(slides, gone.map { it to a[it] }, fadeIn)
}

/**
 * The board. [last] is the move that led here (tinted in [label]'s colour, badge on its
 * square); [arrows] are drawn on top (the engine's suggestion). Any change of [position]
 * animates: pieces glide, captures fade, then the badge pops and the arrow fades in.
 */
@Composable
fun ChessBoard(
    position: Position,
    last: Move?,
    label: Label?,
    arrows: List<Move>,
    flipped: Boolean,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val pieces = piecePainters()
    val tm = rememberTextMeasurer()
    val key = position.fen()
    val shown = remember { arrayOf(position) }
    val glide = remember { Animatable(1f) }
    val pop = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }
    var anim by remember { mutableStateOf<BoardAnim?>(null) }
    LaunchedEffect(key) {
        val from = shown[0]
        shown[0] = position
        if (animate && from.fen() != key) {
            anim = diff(from, position)
            pop.snapTo(0f)
            fade.snapTo(0f)
            glide.snapTo(0f)
            val far = anim!!.slides.maxOfOrNull { abs(fileOf(it.to) - fileOf(it.from)) + abs(rankOf(it.to) - rankOf(it.from)) } ?: 1
            glide.animateTo(1f, tween((170 + far * 12).coerceAtMost(280), easing = FastOutSlowInEasing))
            anim = null
            launch { fade.animateTo(1f, tween(220)) }
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 520f))
        } else {
            anim = null
            glide.snapTo(1f)
            pop.snapTo(1f)
            fade.snapTo(1f)
        }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(1f)) {
        val s = size.width / 8f

        fun origin(sq: Int): Offset {
            val f = fileOf(sq)
            val r = rankOf(sq)
            val x = if (flipped) 7 - f else f
            val y = if (flipped) r else 7 - r
            return Offset(x * s, y * s)
        }
        for (sq in 0 until 64) {
            val light = (fileOf(sq) + rankOf(sq)) % 2 == 1
            drawRect(if (light) C.light else C.dark, origin(sq), Size(s, s))
        }
        if (last != null) {
            val tint = (label?.colour() ?: Color(0xFFF7EC74)).copy(alpha = 0.5f * (0.35f + 0.65f * glide.value))
            drawRect(tint, origin(last.from), Size(s, s))
            drawRect(tint, origin(last.to), Size(s, s))
        }
        drawCoordinates(tm, s, flipped)
        val a = anim
        val t = glide.value
        val moving =
            a
                ?.slides
                ?.map { it.to }
                ?.toSet()
                .orEmpty()
        for (sq in 0 until 64) {
            val p = position[sq]
            if (p == 0 || sq in moving || (a != null && sq in a.fadeIn)) continue
            val painter = pieces[p + 6] ?: continue
            val o = origin(sq)
            translate(o.x, o.y) { with(painter) { draw(Size(s, s)) } }
        }
        if (a != null) {
            for ((sq, p) in a.fadeOut) {
                val painter = pieces[p + 6] ?: continue
                val o = origin(sq)
                translate(o.x, o.y) { with(painter) { draw(Size(s, s), alpha = 1f - t) } }
            }
            for (sq in a.fadeIn) {
                val painter = pieces[position[sq] + 6] ?: continue
                val o = origin(sq)
                translate(o.x, o.y) { with(painter) { draw(Size(s, s), alpha = t) } }
            }
            for (sl in a.slides) {
                val painter = pieces[sl.piece + 6] ?: continue
                val f = origin(sl.from)
                val o = origin(sl.to)
                val x = f.x + (o.x - f.x) * t
                val y = f.y + (o.y - f.y) * t
                // Lifted a touch while it travels.
                val lift = 1f + 0.08f * sin(Math.PI.toFloat() * t)
                val sz = s * lift
                translate(x - (sz - s) / 2f, y - (sz - s) / 2f) { with(painter) { draw(Size(sz, sz)) } }
            }
        }
        if (a == null) {
            for (ar in arrows) drawArrow(origin(ar.from), origin(ar.to), s, isKnightJump(ar), fade.value)
        }
        if (last != null && label != null && a == null && pop.value > 0.01f) {
            val o = origin(last.to)
            val r = s * 0.2f
            val cx = min(o.x + s - r * 0.35f, size.width - r)
            val cy = maxOf(o.y + r * 0.35f, r)
            val k = pop.value.coerceAtLeast(0f)
            withTransform({ scale(k, k, Offset(cx, cy)) }) { drawLabelIcon(label, Offset(cx, cy), r, tm) }
        }
    }
}

private fun isKnightJump(m: Move): Boolean {
    val df = abs(fileOf(m.to) - fileOf(m.from))
    val dr = abs(rankOf(m.to) - rankOf(m.from))
    return (df == 1 && dr == 2) || (df == 2 && dr == 1)
}

private fun DrawScope.drawCoordinates(
    tm: TextMeasurer,
    s: Float,
    flipped: Boolean,
) {
    val style = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = (s / 5.5f).toSp())
    for (i in 0..7) {
        // Rank numbers down the left edge, file letters along the bottom.
        val rank = if (flipped) i + 1 else 8 - i
        val leftFile = if (flipped) 7 else 0
        val rankOnLight = (leftFile + rank - 1) % 2 == 1
        drawText(tm, rank.toString(), Offset(s * 0.05f, i * s + s * 0.02f), style.copy(color = if (rankOnLight) C.dark else C.light))
        val file = if (flipped) 7 - i else i
        val bottomRank = if (flipped) 7 else 0
        val fileOnLight = (file + bottomRank) % 2 == 1
        val t = tm.measure(('a' + file).toString(), style)
        drawText(
            t,
            color = if (fileOnLight) C.dark else C.light,
            topLeft =
                Offset(
                    (i + 1) * s - t.size.width - s * 0.05f,
                    8 * s - t.size.height,
                ),
        )
    }
}

private fun DrawScope.drawArrow(
    fromO: Offset,
    toO: Offset,
    s: Float,
    knight: Boolean,
    alpha: Float = 1f,
) {
    val half = s / 2f
    val a = Offset(fromO.x + half, fromO.y + half)
    val b = Offset(toO.x + half, toO.y + half)
    val w = s * 0.2f
    val head = s * 0.42f
    val points =
        if (knight) {
            // L-shaped: along the long leg first.
            val corner = if (abs(b.x - a.x) > abs(b.y - a.y)) Offset(b.x, a.y) else Offset(a.x, b.y)
            listOf(a, corner, b)
        } else {
            listOf(a, b)
        }
    val last = points[points.size - 2]
    val ang = atan2(b.y - last.y, b.x - last.x)
    val tip = b
    val baseCenter = Offset(tip.x - cos(ang) * head, tip.y - sin(ang) * head)
    val path =
        Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size - 1) lineTo(points[i].x, points[i].y)
            lineTo(baseCenter.x, baseCenter.y)
        }
    drawPath(path, C.arrow, alpha = alpha, style = Stroke(width = w, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
    val nx = -sin(ang)
    val ny = cos(ang)
    val tri =
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(baseCenter.x + nx * head * 0.62f, baseCenter.y + ny * head * 0.62f)
            lineTo(baseCenter.x - nx * head * 0.62f, baseCenter.y - ny * head * 0.62f)
            close()
        }
    drawPath(tri, C.arrow, alpha = alpha)
}

/**
 * Glyphs for the badges, 24x24 path data from Google's Material icons (Apache 2.0):
 * star, thumb_up, check, menu_book and close.
 */
private object Glyphs {
    private fun p(d: String) = PathParser().parsePathString(d).toPath()

    val star by lazy { p("M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z") }
    val thumb by lazy {
        p(
            "M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 " +
                "8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z",
        )
    }
    val check by lazy { p("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z") }
    val book by lazy {
        p(
            "M21 5c-1.11-.35-2.33-.5-3.5-.5-1.95 0-4.05.4-5.5 1.5-1.45-1.1-3.55-1.5-5.5-1.5S2.45 4.9 1 6v14.65c0 .25.25.5.5.5.1 0 " +
                ".15-.05.25-.05C3.1 20.45 5.05 20 6.5 20c1.95 0 4.05.4 5.5 1.5 1.35-.85 3.8-1.5 5.5-1.5 1.65 0 3.35.3 4.75 " +
                "1.05.1.05.15.05.25.05.25 0 .5-.25.5-.5V6c-.6-.45-1.25-.75-2-1zm0 13.5c-1.1-.35-2.3-.5-3.5-.5-1.7 0-4.15.65-5.5 " +
                "1.5V8c1.35-.85 3.8-1.5 5.5-1.5 1.2 0 2.4.15 3.5.5v11.5z",
        )
    }
    val close by lazy { p("M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z") }
}

private fun DrawScope.glyph(
    path: Path,
    c: Offset,
    size: Float,
) {
    val k = size / 24f
    withTransform({
        translate(c.x - 12f * k, c.y - 12f * k)
        scale(k, k, Offset.Zero)
    }) { drawPath(path, Color.White) }
}

/** A label badge: coloured disc with a darker lip and a white symbol. Drawn, so it scales. */
fun DrawScope.drawLabelIcon(
    label: Label,
    c: Offset,
    r: Float,
    tm: TextMeasurer,
) {
    val base = label.colour()
    val lip = Color(base.red * 0.72f, base.green * 0.72f, base.blue * 0.72f, 1f)
    drawCircle(Color(0x40000000), r, Offset(c.x, c.y + r * 0.16f))
    drawCircle(lip, r, Offset(c.x, c.y + r * 0.08f))
    drawCircle(base, r, c)
    when (label) {
        Label.Best -> glyph(Glyphs.star, c, r * 1.45f)
        Label.Excellent -> glyph(Glyphs.thumb, Offset(c.x, c.y - r * 0.02f), r * 1.2f)
        Label.Good -> glyph(Glyphs.check, c, r * 1.55f)
        Label.Book -> glyph(Glyphs.book, c, r * 1.3f)
        Label.Miss -> glyph(Glyphs.close, c, r * 1.5f)
        else -> {
            val style =
                TextStyle(
                    fontFamily = Display,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (r * if (label.symbol.length > 1) 1.05f else 1.3f).toSp(),
                    letterSpacing = (-0.06f * r).toSp(),
                )
            val t = tm.measure(label.symbol, style)
            drawText(t, Color.White, Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
        }
    }
}
