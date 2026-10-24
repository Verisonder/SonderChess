package com.verisonder.sonderchess.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.verisonder.sonderchess.R
import com.verisonder.sonderchess.core.Label
import com.verisonder.sonderchess.core.Move
import com.verisonder.sonderchess.core.Position
import com.verisonder.sonderchess.core.fileOf
import com.verisonder.sonderchess.core.rankOf
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

/**
 * The board. [last] is the move that led here (tinted in [label]'s colour, badge on its
 * square); [arrows] are drawn on top (the engine's suggestion).
 */
@Composable
fun ChessBoard(
    position: Position,
    last: Move?,
    label: Label?,
    arrows: List<Move>,
    flipped: Boolean,
    animateKey: Int,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val pieces = piecePainters()
    val tm = rememberTextMeasurer()
    val slide = remember { Animatable(1f) }
    LaunchedEffect(animateKey) {
        if (animate && last != null) {
            slide.snapTo(0f)
            slide.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
        } else {
            slide.snapTo(1f)
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
        // Squares and coordinates.
        for (sq in 0 until 64) {
            val light = (fileOf(sq) + rankOf(sq)) % 2 == 1
            drawRect(if (light) C.light else C.dark, origin(sq), Size(s, s))
        }
        if (last != null) {
            val tint = (label?.colour() ?: Color(0xFFF7EC74)).copy(alpha = 0.5f)
            drawRect(tint, origin(last.from), Size(s, s))
            drawRect(tint, origin(last.to), Size(s, s))
        }
        drawCoordinates(tm, s, flipped)
        // Pieces; the moved one slides in.
        for (sq in 0 until 64) {
            val p = position[sq]
            if (p == 0) continue
            val painter = pieces[p + 6] ?: continue
            var o = origin(sq)
            if (last != null && sq == last.to && slide.value < 1f) {
                val from = origin(last.from)
                o = Offset(from.x + (o.x - from.x) * slide.value, from.y + (o.y - from.y) * slide.value)
            }
            translate(o.x, o.y) { with(painter) { draw(Size(s, s)) } }
        }
        for (a in arrows) drawArrow(origin(a.from), origin(a.to), s, isKnightJump(a))
        if (last != null && label != null && slide.value >= 1f) {
            val o = origin(last.to)
            val r = s * 0.2f
            val cx = min(o.x + s - r * 0.35f, size.width - r)
            val cy = maxOf(o.y + r * 0.35f, r)
            drawLabelIcon(label, Offset(cx, cy), r, tm)
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
    drawPath(path, C.arrow, style = Stroke(width = w, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
    val nx = -sin(ang)
    val ny = cos(ang)
    val tri =
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(baseCenter.x + nx * head * 0.62f, baseCenter.y + ny * head * 0.62f)
            lineTo(baseCenter.x - nx * head * 0.62f, baseCenter.y - ny * head * 0.62f)
            close()
        }
    drawPath(tri, C.arrow)
}

/** A label badge: coloured disc with a white symbol. Drawn, so no images are needed. */
fun DrawScope.drawLabelIcon(
    label: Label,
    c: Offset,
    r: Float,
    tm: TextMeasurer,
) {
    drawCircle(Color(0x33000000), r * 1.08f, Offset(c.x, c.y + r * 0.12f))
    drawCircle(label.colour(), r, c)
    val white = Color.White
    val sw = r * 0.26f
    when (label) {
        Label.Best -> drawPath(starPath(c, r * 0.62f, r * 0.27f), white)
        Label.Good -> drawPath(checkPath(c, r * 0.55f, 0f), white, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        Label.Excellent -> {
            drawPath(checkPath(c, r * 0.45f, -r * 0.2f), white, style = Stroke(sw * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(checkPath(c, r * 0.45f, r * 0.22f), white, style = Stroke(sw * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Label.Book -> {
            // An open book: two pages meeting at the spine.
            val p =
                Path().apply {
                    moveTo(c.x, c.y - r * 0.32f)
                    lineTo(c.x - r * 0.55f, c.y - r * 0.45f)
                    lineTo(c.x - r * 0.55f, c.y + r * 0.35f)
                    lineTo(c.x, c.y + r * 0.48f)
                    lineTo(c.x + r * 0.55f, c.y + r * 0.35f)
                    lineTo(c.x + r * 0.55f, c.y - r * 0.45f)
                    close()
                }
            drawPath(p, white)
            drawLine(label.colour(), Offset(c.x, c.y - r * 0.3f), Offset(c.x, c.y + r * 0.45f), strokeWidth = r * 0.1f)
        }
        Label.Miss -> {
            val d = r * 0.36f
            drawLine(white, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), sw, StrokeCap.Round)
            drawLine(white, Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), sw, StrokeCap.Round)
        }
        else -> {
            val style =
                TextStyle(
                    fontFamily = Body,
                    fontWeight = FontWeight.Bold,
                    fontSize =
                        (
                            r *
                                if (label.symbol.length > 1) 1.0f else 1.25f
                        ).toSp(),
                    letterSpacing = (-0.5).sp,
                )
            val t = tm.measure(label.symbol, style)
            drawText(t, white, Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
        }
    }
}

private fun starPath(
    c: Offset,
    outer: Float,
    inner: Float,
) = Path().apply {
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) outer else inner
        val a = Math.toRadians(-90.0 + i * 36.0)
        val x = c.x + (rad * cos(a)).toFloat()
        val y = c.y + (rad * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun checkPath(
    c: Offset,
    size: Float,
    dx: Float,
) = Path().apply {
    moveTo(c.x + dx - size * 0.75f, c.y)
    lineTo(c.x + dx - size * 0.2f, c.y + size * 0.55f)
    lineTo(c.x + dx + size * 0.8f, c.y - size * 0.55f)
}
