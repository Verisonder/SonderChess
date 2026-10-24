package com.verisonder.sonderchess.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.core.Classifier
import com.verisonder.sonderchess.core.EngineLine
import com.verisonder.sonderchess.core.Label
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun LabelIcon(
    label: Label,
    size: Dp = 20.dp,
) {
    val tm = rememberTextMeasurer()
    Canvas(Modifier.size(size)) {
        drawLabelIcon(label, Offset(this.size.width / 2f, this.size.height / 2f), this.size.width / 2f * 0.92f, tm)
    }
}

/**
 * The coach: the Verisonder SR mark (blue pixel S, white pixel R), traced from the logo into
 * rectangles on a 256x256 grid so it stays sharp at any size.
 */
private object SrMark {
    private fun p(d: String) =
        androidx.compose.ui.graphics.vector
            .PathParser()
            .parsePathString(d)
            .toPath()

    val s by lazy {
        p(
            "M41 112h71v7h-71zM40 119h72v1h-72zM32 120h80v7h-80zM81 127h28v1h-28zM82 128h27v14h-27zM82 142h17v8h-17zM82 150h8v7h-8zM24 127h27v45h-27zM9 172h108v8h-108zM18 180h99v7h-99zM77 187h28v15h-28zM41 202h9v8h-9zM80 202h27v15h-27zM32 210h18v7h-18zM25 217h81v7h-81zM25 224h73v1h-73zM16 225h81v7h-81z",
        )
    }
    val r by lazy {
        p(
            "M139 25h72v7h-72zM139 32h80v1h-80zM139 33h81v7h-81zM196 40h27v30h-27zM139 40h26v37h-26zM202 70h26v7h-26zM147 77h90v7h-90zM147 84h91v1h-91zM154 85h90v7h-90zM154 92h89v1h-89zM199 93h27v6h-27zM175 99h8v1h-8zM166 99h8v1h-8zM148 99h17v1h-17zM184 99h53v1h-53zM139 99h8v1h-8zM139 100h107v7h-107zM139 107h99v1h-99zM139 108h98v7h-98zM139 115h27v1h-27zM196 115h27v45h-27zM139 116h26v44h-26z",
        )
    }
    val blue = Color(0xFF379ED6)
    val white = Color(0xFFF8F8F8)
}

/**
 * The coach. When it has something new to say it hops, the S and then the R give a little
 * bounce of their own, and both letters brighten for a moment.
 */
@Composable
fun Coach(
    size: Dp = 64.dp,
    reactTo: Any? = null,
) {
    val hop = remember { Animatable(0f) }
    val sJump = remember { Animatable(0f) }
    val rJump = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(reactTo) {
        if (reactTo == null) return@LaunchedEffect
        launch {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(650, easing = FastOutSlowInEasing))
        }
        launch {
            sJump.snapTo(0f)
            sJump.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
            sJump.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
        launch {
            kotlinx.coroutines.delay(90)
            rJump.snapTo(0f)
            rJump.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
            rJump.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
        hop.snapTo(0f)
        hop.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
        hop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 400f))
    }
    Canvas(
        Modifier
            .size(size)
            .graphicsLayer {
                translationY = -hop.value * size.toPx() * 0.06f
                scaleX = 1f + hop.value * 0.04f
                scaleY = 1f + hop.value * 0.04f
            },
    ) {
        val w = this.size.width
        val k = w / 256f
        val pad = (w - 256f * k) / 2f
        val g = glow.value
        withTransform({
            translate(pad, pad - sJump.value * w * 0.07f)
            scale(k, k, Offset.Zero)
        }) {
            drawPath(
                SrMark.s,
                androidx.compose.ui.graphics
                    .lerp(SrMark.blue, Color(0xFF9FD6F5), g * 0.6f),
            )
        }
        withTransform({
            translate(pad, pad - rJump.value * w * 0.07f)
            scale(k, k, Offset.Zero)
        }) {
            drawPath(
                SrMark.r,
                androidx.compose.ui.graphics
                    .lerp(SrMark.white, Color.White, g),
            )
        }
    }
}

/** Speech bubble pointing left toward the coach. */
@Composable
fun Bubble(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp, 16.dp)) {
            val p =
                Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(0f, size.height / 2f)
                    lineTo(size.width, size.height)
                    close()
                }
            drawPath(p, C.bubble)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .animateContentSize(spring(stiffness = 600f))
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(C.bubble)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) { content() }
    }
}

/** Score text: "+1.25", "M3", "1-0". */
fun evalText(
    e: EngineLine,
    gameOver: String? = null,
): String {
    if (gameOver != null) return gameOver
    return when {
        e.mate != null -> "M${abs(e.mate)}"
        abs(e.score) >= 9000 -> if (e.score > 0) "1-0" else "0-1"
        else -> (if (e.score >= 0) "+" else "") + "%.2f".format(e.score / 100.0)
    }
}

/** Vertical bar beside the board: White's share from the bottom (top when flipped). */
@Composable
fun EvalBar(
    e: EngineLine,
    flipped: Boolean,
    modifier: Modifier = Modifier,
) {
    val target =
        when {
            e.mate != null -> if (e.mate > 0) 1f else 0f
            abs(e.score) >= 9000 -> if (e.score > 0) 1f else 0f
            else -> Classifier.win(e.score).toFloat().coerceIn(0.04f, 0.96f)
        }
    val share by animateFloatAsState(target, tween(400), label = "eval")
    val whiteAhead = e.score >= 0
    val tm = rememberTextMeasurer()
    Canvas(modifier.width(18.dp).fillMaxHeight()) {
        drawRect(C.evalBlack)
        val h = size.height * share
        if (flipped) {
            drawRect(
                C.evalWhite,
                Offset.Zero,
                Size(size.width, h),
            )
        } else {
            drawRect(C.evalWhite, Offset(0f, size.height - h), Size(size.width, h))
        }
        val txt =
            evalText(e).removePrefix("+").let { if (it.startsWith("-")) it.substring(1) else it }.let {
                if (it.length >
                    4
                ) {
                    it.take(4)
                } else {
                    it
                }
            }
        val style = body(8, FontWeight.Bold)
        val t = tm.measure(txt, style)
        val atBottom = whiteAhead != flipped
        val y = if (atBottom) size.height - t.size.height - 3.dp.toPx() else 3.dp.toPx()
        drawText(t, if (whiteAhead) C.evalBlack else C.evalWhite, Offset((size.width - t.size.width) / 2f, y))
    }
}

/** The whole game's evaluation as a filled curve; tap to jump. Dots on big moments. */
@Composable
fun EvalGraph(
    evals: List<EngineLine>,
    marks: List<Pair<Int, Label>>,
    current: Int?,
    modifier: Modifier = Modifier,
    onPick: ((Int) -> Unit)? = null,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(evals.size) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(C.evalBlack)
            .let { m ->
                if (onPick == null) {
                    m
                } else {
                    m.pointerInput(evals.size) {
                        detectTapGestures { pos ->
                            val n = evals.size - 1
                            val i = ((pos.x / size.width) * n).toInt().coerceIn(0, n)
                            onPick(i)
                        }
                    }
                }
            },
    ) {
        if (evals.size < 2) return@Canvas
        val n = evals.size - 1

        fun y(e: EngineLine): Float {
            val w =
                when {
                    abs(e.score) >= 9000 -> if (e.score > 0) 1.0 else 0.0
                    else -> Classifier.win(e.score)
                }
            return (size.height * (1.0 - w)).toFloat().coerceIn(1f, size.height - 1f)
        }
        val path =
            Path().apply {
                moveTo(0f, size.height)
                for (i in 0..n) lineTo(size.width * i / n, y(evals[i]))
                lineTo(size.width, size.height)
                close()
            }
        clipRect(right = size.width * reveal.value) { drawPath(path, C.evalWhite) }
        drawLine(Color(0x55888888), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        if (current != null) {
            val x = size.width * current / n
            drawLine(C.green, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
        }
        for ((ply, l) in marks) {
            val i = ply + 1
            val x = size.width * i / n
            if (x > size.width * reveal.value) continue
            drawCircle(l.colour(), 3.5.dp.toPx(), Offset(x, y(evals[i])))
            drawCircle(Color.White, 3.5.dp.toPx(), Offset(x, y(evals[i])), style = Stroke(1.dp.toPx()))
        }
    }
}

/** The big green action button with its darker lip. */
@Composable
fun GreenButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    onClick: () -> Unit,
) {
    // Pressing pushes the face down onto its lip, like a real key.
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sink by animateDpAsState(if (pressed) 3.dp else 0.dp, tween(70), label = "press")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(C.greenDark)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .offset(y = sink)
                .fillMaxWidth()
                .height(height - 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (pressed) Color(0xFF8CC157) else C.green),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = display(20))
        }
    }
}

/** A player's profile picture; their initial until it loads (or when there is none). */
@Composable
fun Avatar(
    name: String,
    white: Boolean,
    size: Dp = 56.dp,
    url: String? = null,
) {
    val image = rememberAvatar(url)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(if (white) C.light else C.evalBlack),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            style = display((size.value * 0.42f).toInt(), if (white) C.bg else C.text),
        )
        androidx.compose.animation.AnimatedVisibility(
            visible = image != null,
            enter = androidx.compose.animation.fadeIn(tween(250)),
        ) {
            image?.let {
                Image(it, contentDescription = name, modifier = Modifier.size(size), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(96.dp)) {
            if (onBack != null) {
                Box(Modifier.size(48.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(22.dp)) {
                        val w = 2.5.dp.toPx()
                        drawLine(C.dim, Offset(size.width * 0.9f, size.height / 2), Offset(size.width * 0.1f, size.height / 2), w)
                        drawLine(C.dim, Offset(size.width * 0.1f, size.height / 2), Offset(size.width * 0.45f, size.height * 0.15f), w)
                        drawLine(C.dim, Offset(size.width * 0.1f, size.height / 2), Offset(size.width * 0.45f, size.height * 0.85f), w)
                    }
                }
            }
        }
        Text(title, style = display(20), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(96.dp),
        ) { actions() }
    }
}

/** "Nxe7" → "♘xe7": piece letters as chess symbols (outline for White, solid for Black). */
fun figurine(
    san: String,
    white: Boolean,
): String {
    val i = "KQRBN".indexOf(san.firstOrNull() ?: ' ')
    if (i < 0) return san
    val glyphs = if (white) "♔♕♖♗♘" else "♚♛♜♝♞"
    return glyphs[i] + san.substring(1)
}

/** Fades and slides in, each [index] a beat after the one before (capped so long lists don't wait). */
@Composable
fun Modifier.appear(index: Int): Modifier {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(320, delayMillis = 80 + index.coerceAtMost(10) * 45, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = t.value
        translationY = (1f - t.value) * 14.dp.toPx()
    }
}

/** A number that counts up to [value] the first time it shows, and glides when it changes. */
@Composable
fun CountUp(
    value: Int,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    delayMs: Int = 150,
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(value) { shown.animateTo(value.toFloat(), tween(800, delayMillis = delayMs, easing = FastOutSlowInEasing)) }
    Text(shown.value.toInt().toString(), style = style, modifier = modifier)
}

/** Segmented buttons with a pill that slides to the picked one. */
@Composable
fun SlidingSegments(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    track: Color = C.panel,
    pill: Color = C.panel2,
    height: Dp = 38.dp,
    onSelect: (Int) -> Unit,
) {
    val pos by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.75f, stiffness = 500f), label = "seg")
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(track)
            .padding(4.dp),
    ) {
        val w = maxWidth / labels.size
        Box(
            Modifier
                .offset(x = w * pos)
                .width(w)
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(pill),
        )
        Row {
            labels.forEachIndexed { i, l ->
                val on = i == selected
                val col by androidx.compose.animation.animateColorAsState(if (on) C.text else C.faint, tween(200), label = "segText")
                Box(
                    Modifier
                        .width(w)
                        .height(height)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(l, style = body(14, FontWeight.Bold, col)) }
            }
        }
    }
}
