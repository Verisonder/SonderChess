package com.verisonder.sonderchess.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.core.EngineLine
import com.verisonder.sonderchess.core.Explain
import com.verisonder.sonderchess.core.Label
import com.verisonder.sonderchess.core.Move
import com.verisonder.sonderchess.core.Position

/**
 * Move-by-move review. [startPly] is the move shown first (-1 = the starting position).
 */
@Composable
fun ReviewScreen(
    o: Opened,
    startPly: Int,
) {
    val r = o.review
    val positions = remember(o) { r.game.positions() }
    var ply by rememberSaveable(o.saved.id) { mutableStateOf(startPly) }
    var showBest by remember { mutableStateOf(false) }
    var arrows by rememberSaveable { mutableStateOf(AppModel.showArrows) }
    var flipped by rememberSaveable(o.saved.id) { mutableStateOf(!o.saved.meWhite) }
    val context = LocalContext.current
    val last = r.game.moves.size - 1

    fun go(p: Int) {
        val n = p.coerceIn(-1, last)
        showBest = false
        ply = n
    }

    val mr = if (ply >= 0) r.moves[ply] else null
    val bestMove =
        mr
            ?.bestUci
            ?.let { runCatching { Move.fromUci(it) }.getOrNull() }
            ?.takeIf { bm -> positions[ply].legalMoves().contains(bm) }
    val showingBest = showBest && mr != null && bestMove != null && mr.label != Label.Best
    val boardPos: Position =
        when {
            mr == null -> positions[0]
            showingBest -> positions[ply].play(bestMove!!)
            else -> positions[ply + 1]
        }
    val boardMove: Move? =
        when {
            mr == null -> null
            showingBest -> bestMove
            else -> r.game.moves[ply]
        }
    val boardLabel: Label? =
        when {
            mr == null -> null
            showingBest -> Label.Best
            else -> mr.label
        }
    val boardArrows =
        if (arrows &&
            mr != null &&
            !showingBest &&
            bestMove != null &&
            mr.uci != mr.bestUci &&
            mr.label != Label.Book &&
            mr.label != Label.Brilliant &&
            mr.label != Label.Best
        ) {
            listOf(bestMove)
        } else {
            emptyList()
        }
    val evalNow: EngineLine = r.evals[ply + 1]

    // A sound for every step, not for the first screen.
    val firstShow = remember { booleanArrayOf(true) }
    LaunchedEffect(ply, showingBest) {
        if (firstShow[0]) {
            firstShow[0] = false
            Sounds.preload(AppModel.soundSet)
            return@LaunchedEffect
        }
        if (ply < 0) return@LaunchedEffect
        val m = if (showingBest) bestMove else r.game.moves[ply]
        if (m != null) Sounds.play(Sounds.eventFor(positions[ply], m))
    }

    Box(Modifier.fillMaxSize()) {
        ReviewBackground()
        Column(Modifier.fillMaxSize()) {
            TopBar("Game Review", onBack = { AppModel.screen = Screen.Summary(o.saved.id) }) {
                Box(Modifier.size(48.dp).clickable { flipped = !flipped }, contentAlignment = Alignment.Center) {
                    FlipIcon()
                }
            }
            // Coach and speech bubble.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Coach(56.dp, reactTo = ply)
                Spacer(Modifier.width(4.dp))
                Bubble(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = ply to showingBest,
                        transitionSpec = {
                            (fadeIn(tween(180, delayMillis = 60)) + scaleIn(tween(220, delayMillis = 60), initialScale = 0.94f))
                                .togetherWith(fadeOut(tween(90)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "coach",
                    ) { (p, best) -> BubbleBody(o, positions, p, best) }
                }
            }
            // Board with the eval bar.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val boardSize = maxWidth - 18.dp
                Row(Modifier.height(boardSize)) {
                    EvalBar(evalNow, flipped, Modifier.fillMaxHeight())
                    ChessBoard(
                        position = boardPos,
                        last = boardMove,
                        label = boardLabel,
                        arrows = boardArrows,
                        flipped = flipped,
                        modifier =
                            Modifier.size(boardSize).pointerInput(ply) {
                                var dx = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { dx = 0f },
                                    onDragEnd = {
                                        if (dx < -60f) {
                                            go(ply + 1)
                                        } else if (dx > 60f) {
                                            go(ply - 1)
                                        }
                                    },
                                ) { _, d -> dx += d }
                            },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.background(if (backgroundIsDark) Color.Transparent else C.bg.copy(alpha = 0.88f))) {
                MoveStrip(o, ply, onPick = { go(it) })
            }
            Spacer(Modifier.weight(1f))
            // Bottom bar.
            Row(
                Modifier.fillMaxWidth().background(C.bar).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarButton("Show", on = arrows, enabled = true) { arrows = !arrows }
                BarButton(
                    "Best",
                    on = showingBest,
                    enabled = mr != null && bestMove != null && mr.label != Label.Best && mr.label != Label.Book,
                ) {
                    showBest = !showBest
                }
                BarButton("Share", on = false, enabled = true) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, r.game.toPgn())
                    context.startActivity(Intent.createChooser(send, "Share PGN"))
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    GreenButton(if (ply >= last) "Done" else "Next", height = 52.dp) {
                        if (ply >= last) AppModel.screen = Screen.Summary(o.saved.id) else go(ply + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveStrip(
    o: Opened,
    ply: Int,
    onPick: (Int) -> Unit,
) {
    val r = o.review
    val state = rememberLazyListState()
    LaunchedEffect(ply) { if (ply >= 0) state.animateScrollToItem(maxOf(0, ply - 2)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Chevron(left = true) { onPick(ply - 1) }
        LazyRow(state = state, modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items(r.moves.size) { i ->
                val m = r.moves[i]
                val on = i == ply
                Row(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(chipBg(on))
                        .clickable { onPick(i) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (m.white) Text("${i / 2 + 1}.", style = body(14, color = C.faint))
                    if (m.white) Spacer(Modifier.width(4.dp))
                    if (m.label in
                        setOf(Label.Brilliant, Label.Great, Label.Best, Label.Miss, Label.Mistake, Label.Blunder, Label.Inaccuracy)
                    ) {
                        LabelIcon(m.label, 15.dp)
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        figurine(m.san, m.white),
                        style =
                            body(
                                15,
                                FontWeight.Bold,
                                if (on) {
                                    C.text
                                } else if (m.label.good) {
                                    C.dim
                                } else {
                                    m.label.colour()
                                },
                            ),
                    )
                }
            }
        }
        Chevron(left = false) { onPick(ply + 1) }
    }
}

@Composable
private fun Chevron(
    left: Boolean,
    onClick: () -> Unit,
) {
    Box(Modifier.size(40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(14.dp)) {
            val w = 2.5.dp.toPx()
            val p =
                Path().apply {
                    if (left) {
                        moveTo(size.width * 0.7f, 0f)
                        lineTo(size.width * 0.25f, size.height / 2)
                        lineTo(size.width * 0.7f, size.height)
                    } else {
                        moveTo(size.width * 0.3f, 0f)
                        lineTo(size.width * 0.75f, size.height / 2)
                        lineTo(size.width * 0.3f, size.height)
                    }
                }
            drawPath(p, C.dim, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun BarButton(
    text: String,
    on: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val col =
        when {
            !enabled -> Color(0xFF5E5C59)
            on -> C.text
            else -> C.dim
        }
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // All three icons share one stroke width, round ends and outline style.
        Canvas(Modifier.size(24.dp)) {
            val st = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val u = size.width / 24f

            fun o(
                x: Float,
                y: Float,
            ) = Offset(x * u, y * u)
            val p = Path()
            when (text) {
                "Show" -> {
                    p.moveTo(5f * u, 19f * u)
                    p.lineTo(18f * u, 6f * u)
                    p.moveTo(9.5f * u, 6f * u)
                    p.lineTo(18f * u, 6f * u)
                    p.lineTo(18f * u, 14.5f * u)
                    drawPath(p, col, style = st)
                }
                "Best" -> {
                    drawCircle(col, 6.5f * u, o(10.5f, 10.5f), style = st)
                    drawLine(col, o(15.3f, 15.3f), o(20f, 20f), st.width, StrokeCap.Round)
                }
                else -> {
                    drawLine(col, o(8.4f, 10.8f), o(15.6f, 7.2f), st.width, StrokeCap.Round)
                    drawLine(col, o(8.4f, 13.2f), o(15.6f, 16.8f), st.width, StrokeCap.Round)
                    drawCircle(col, 2.6f * u, o(6f, 12f), style = st)
                    drawCircle(col, 2.6f * u, o(18f, 6f), style = st)
                    drawCircle(col, 2.6f * u, o(18f, 18f), style = st)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(text, style = body(12, FontWeight.Medium, col))
    }
}

@Composable
private fun FlipIcon() {
    Canvas(Modifier.size(22.dp)) {
        val w = 2.dp.toPx()
        drawLine(C.dim, Offset(size.width * 0.3f, size.height * 0.1f), Offset(size.width * 0.3f, size.height * 0.9f), w, StrokeCap.Round)
        drawLine(C.dim, Offset(size.width * 0.3f, size.height * 0.1f), Offset(size.width * 0.1f, size.height * 0.32f), w, StrokeCap.Round)
        drawLine(C.dim, Offset(size.width * 0.7f, size.height * 0.9f), Offset(size.width * 0.7f, size.height * 0.1f), w, StrokeCap.Round)
        drawLine(C.dim, Offset(size.width * 0.7f, size.height * 0.9f), Offset(size.width * 0.9f, size.height * 0.68f), w, StrokeCap.Round)
    }
}

/** What the coach says for move [p] (or the engine's best move there when [best]). */
@Composable
private fun BubbleBody(
    o: Opened,
    positions: List<Position>,
    p: Int,
    best: Boolean,
) {
    val r = o.review
    val mr = if (p >= 0) r.moves[p] else null
    if (mr == null) {
        Text(
            r.opening?.let { "This game opened with the $it. Tap next to step through it." }
                ?: "Tap next to step through the game.",
            style = body(14, FontWeight.Medium, C.bubbleText),
        )
        return
    }
    val words =
        if (best) {
            Explain.Words("${mr.bestSan} is best", "This is what the engine would have played.")
        } else {
            Explain.move(r, p, mr.white == o.saved.meWhite)
        }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabelIcon(if (best) Label.Best else mr.label, 20.dp)
            Spacer(Modifier.width(6.dp))
            val shownSan = if (best) mr.bestSan.orEmpty() else mr.san
            Text(
                figurine(shownSan, mr.white) + words.headline.removePrefix(shownSan),
                style = body(16, FontWeight.Bold, C.bubbleText),
                modifier = Modifier.weight(1f),
            )
            val ev = if (best) r.evals[p] else r.evals[p + 1]
            val over = if (!best && p == r.moves.size - 1 && positions.last().isCheckmate()) r.game.result else null
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFEEEEEE)).padding(horizontal = 6.dp, vertical = 2.dp),
            ) { Text(evalText(ev, over), style = body(12, FontWeight.Bold, C.bubbleText)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(words.line, style = body(14, color = C.bubbleText))
    }
}

@Composable
private fun chipBg(on: Boolean): Color {
    val c by animateColorAsState(if (on) C.panel2 else C.bg.copy(alpha = 0f), tween(180), label = "chip")
    return c
}
