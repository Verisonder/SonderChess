package com.verisonder.sonderchess.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.core.Label
import com.verisonder.sonderchess.core.Position
import com.verisonder.sonderchess.core.fileOf
import com.verisonder.sonderchess.core.rankOf
import com.verisonder.sonderchess.data.Player
import kotlinx.coroutines.delay

/**
 * A finished game, like Chess.com's game page: moves along the top, both players with their
 * clocks, the board (step with Back/Forward or swipe), who won on the kings, and a result card.
 */
@Composable
fun GameScreen(v: GameView) {
    val positions = remember(v) { v.game.positions() }
    val last = v.game.moves.size - 1
    var ply by rememberSaveable(v.id) { mutableIntStateOf(last) }
    var flipped by rememberSaveable(v.id) { mutableStateOf(!v.meWhite) }
    var card by rememberSaveable(v.id) { mutableStateOf(true) }
    val sans = remember(v) { v.game.sans() }
    val firstShow = remember { booleanArrayOf(true) }

    fun go(p: Int) {
        ply = p.coerceIn(-1, last)
    }
    LaunchedEffect(ply) {
        if (firstShow[0]) {
            firstShow[0] = false
            return@LaunchedEffect
        }
        if (ply >= 0) Sounds.play(Sounds.eventFor(positions[ply], v.game.moves[ply]))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Game", onBack = { AppModel.screen = Screen.Home })
            GameMoveStrip(sans, ply) { go(it) }
            val top = if (flipped) v.white else v.black
            val bottom = if (flipped) v.black else v.white
            val topWhite = flipped
            PlayerBar(top, topWhite, clockAt(v, ply, topWhite), activeTurn = turnIsWhite(positions, ply) == topWhite && ply < last)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val size = maxWidth
                Box(
                    Modifier.size(size).pointerInput(ply) {
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
                ) {
                    val pos = positions[ply + 1]
                    ChessBoard(
                        position = pos,
                        last = if (ply >= 0) v.game.moves[ply] else null,
                        label = null,
                        arrows = emptyList(),
                        flipped = flipped,
                        modifier = Modifier.size(size),
                    )
                    if (ply == last) KingBadges(pos, v, flipped, size)
                }
            }
            PlayerBar(bottom, !topWhite, clockAt(v, ply, !topWhite), activeTurn = turnIsWhite(positions, ply) == !topWhite && ply < last)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().background(C.bar).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                GameBarButton("Flip") { flipped = !flipped }
                Box(Modifier.width(76.dp)) { ReviewKey(v) }
                GameBarButton("Back") { go(ply - 1) }
                GameBarButton("Forward") { go(ply + 1) }
            }
        }
        ResultCard(v, visible = card, onClose = { card = false })
    }
}

private fun turnIsWhite(
    positions: List<Position>,
    ply: Int,
) = positions[ply + 1].white

/** The clock a side had left after its latest move up to [ply]. */
private fun clockAt(
    v: GameView,
    ply: Int,
    white: Boolean,
): String? {
    if (v.clocks.isEmpty()) return null
    val startWhite = v.game.positions()[0].white
    var i = ply
    while (i >= 0) {
        val moverWhite = (i % 2 == 0) == startWhite
        if (moverWhite == white) return v.clocks.getOrNull(i)?.let(::shortClock)
        i--
    }
    return v.game.headers["TimeControl"]
        ?.substringBefore('+')
        ?.toIntOrNull()
        ?.let { "%d:%02d".format(it / 60, it % 60) }
}

/** "0:09:55.4" → "9:55". */
private fun shortClock(c: String): String {
    val parts = c.substringBefore('.').split(':').mapNotNull { it.toIntOrNull() }
    return when (parts.size) {
        3 -> if (parts[0] > 0) "%d:%02d:%02d".format(parts[0], parts[1], parts[2]) else "%d:%02d".format(parts[1], parts[2])
        2 -> "%d:%02d".format(parts[0], parts[1])
        else -> c
    }
}

@Composable
private fun PlayerBar(
    p: Player,
    white: Boolean,
    clock: String?,
    activeTurn: Boolean,
) {
    val avatar by androidx.compose.runtime.produceState(p.avatar, p.name) {
        if (value == null && !p.computer) {
            value =
                com.verisonder.sonderchess.data.ChessComApi
                    .avatar(p.name)
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.name, white, 38.dp, avatar)
        Spacer(Modifier.width(10.dp))
        Text(
            p.name + (p.rating?.let { " ($it)" } ?: ""),
            style = body(15, FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val clockBg by androidx.compose.animation.animateColorAsState(
            if (activeTurn) Color(0xFFE6E6E6) else C.panel2,
            tween(250),
            label = "clock",
        )
        if (clock != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(clockBg)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) { Text(clock, style = body(18, FontWeight.Bold, if (activeTurn) C.bubbleText else C.dim)) }
        }
    }
}

@Composable
private fun GameMoveStrip(
    sans: List<String>,
    ply: Int,
    onPick: (Int) -> Unit,
) {
    val state = rememberLazyListState()
    LaunchedEffect(ply) { if (ply >= 0) state.animateScrollToItem(maxOf(0, ply - 3)) }
    LazyRow(state = state, modifier = Modifier.fillMaxWidth().background(C.bar).padding(vertical = 4.dp, horizontal = 6.dp)) {
        items(sans.size) { i ->
            val on = i == ply
            Row(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(stripBg(on))
                    .clickable {
                        onPick(i)
                    }.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (i % 2 == 0) Text("${i / 2 + 1}.", style = body(14, color = C.faint))
                if (i % 2 == 0) Spacer(Modifier.width(4.dp))
                Text(figurine(sans[i], i % 2 == 0), style = body(15, FontWeight.Bold, if (on) C.text else C.dim))
            }
        }
    }
}

/** On the final position: crown for the winner's king, flag (or #) for the loser, ½ for a draw. */
@Composable
private fun KingBadges(
    pos: Position,
    v: GameView,
    flipped: Boolean,
    size: Dp,
) {
    val whiteWon =
        when (v.game.result) {
            "1-0" -> true
            "0-1" -> false
            else -> null
        }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(250)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
    }
    val mate = pos.isCheckmate()
    val ending = v.ending.orEmpty().lowercase()
    Canvas(Modifier.size(size).graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) }) {
        val s = this.size.width / 8f
        for (whiteKing in listOf(true, false)) {
            val sq = pos.kingSquare(whiteKing)
            if (sq < 0) continue
            val f = if (flipped) 7 - fileOf(sq) else fileOf(sq)
            val r = if (flipped) rankOf(sq) else 7 - rankOf(sq)
            val c = Offset(f * s + s * 0.84f, r * s + s * 0.16f)
            val rad = s * 0.19f * pop.value
            val won = whiteWon == whiteKing
            val color =
                when {
                    whiteWon == null -> Color(0xFF8B8987)
                    won -> C.green
                    else -> C.error
                }
            drawCircle(Color(0x40000000), rad * 1.08f, Offset(c.x, c.y + rad * 0.15f))
            drawCircle(color, rad, c)
            val w = Color.White
            when {
                whiteWon == null -> {
                    drawLine(
                        w,
                        Offset(c.x - rad * 0.45f, c.y - rad * 0.2f),
                        Offset(c.x + rad * 0.45f, c.y - rad * 0.2f),
                        rad * 0.22f,
                        StrokeCap.Round,
                    )
                    drawLine(
                        w,
                        Offset(c.x - rad * 0.45f, c.y + rad * 0.25f),
                        Offset(c.x + rad * 0.45f, c.y + rad * 0.25f),
                        rad * 0.22f,
                        StrokeCap.Round,
                    )
                }
                won -> {
                    // A small crown.
                    val p =
                        Path().apply {
                            moveTo(c.x - rad * 0.55f, c.y + rad * 0.35f)
                            lineTo(c.x - rad * 0.6f, c.y - rad * 0.35f)
                            lineTo(c.x - rad * 0.25f, c.y)
                            lineTo(c.x, c.y - rad * 0.5f)
                            lineTo(c.x + rad * 0.25f, c.y)
                            lineTo(c.x + rad * 0.6f, c.y - rad * 0.35f)
                            lineTo(c.x + rad * 0.55f, c.y + rad * 0.35f)
                            close()
                        }
                    drawPath(p, w)
                }
                mate || ending.contains("checkmate") -> {
                    // #
                    val k = rad * 0.32f
                    drawLine(
                        w,
                        Offset(c.x - k * 0.4f, c.y - k * 1.4f),
                        Offset(c.x - k * 0.9f, c.y + k * 1.4f),
                        rad * 0.16f,
                        StrokeCap.Round,
                    )
                    drawLine(
                        w,
                        Offset(c.x + k * 0.9f, c.y - k * 1.4f),
                        Offset(c.x + k * 0.4f, c.y + k * 1.4f),
                        rad * 0.16f,
                        StrokeCap.Round,
                    )
                    drawLine(
                        w,
                        Offset(c.x - k * 1.4f, c.y - k * 0.5f),
                        Offset(c.x + k * 1.5f, c.y - k * 0.5f),
                        rad * 0.16f,
                        StrokeCap.Round,
                    )
                    drawLine(
                        w,
                        Offset(c.x - k * 1.5f, c.y + k * 0.5f),
                        Offset(c.x + k * 1.4f, c.y + k * 0.5f),
                        rad * 0.16f,
                        StrokeCap.Round,
                    )
                }
                else -> {
                    // A flag: resigned, lost on time, abandoned.
                    drawLine(
                        w,
                        Offset(c.x - rad * 0.35f, c.y - rad * 0.55f),
                        Offset(c.x - rad * 0.35f, c.y + rad * 0.55f),
                        rad * 0.16f,
                        StrokeCap.Round,
                    )
                    val p =
                        Path().apply {
                            moveTo(c.x - rad * 0.3f, c.y - rad * 0.55f)
                            lineTo(c.x + rad * 0.55f, c.y - rad * 0.3f)
                            lineTo(c.x - rad * 0.3f, c.y)
                            close()
                        }
                    drawPath(p, w)
                }
            }
        }
    }
}

@Composable
private fun GameBarButton(
    text: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val st = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            val u = size.width / 24f
            val p = Path()
            when (text) {
                "Back" -> {
                    p.moveTo(15f * u, 5f * u)
                    p.lineTo(8f * u, 12f * u)
                    p.lineTo(15f * u, 19f * u)
                }
                "Forward" -> {
                    p.moveTo(9f * u, 5f * u)
                    p.lineTo(16f * u, 12f * u)
                    p.lineTo(9f * u, 19f * u)
                }
                else -> {
                    p.moveTo(8f * u, 4f * u)
                    p.lineTo(8f * u, 20f * u)
                    p.moveTo(8f * u, 4f * u)
                    p.lineTo(4.5f * u, 7.5f * u)
                    p.moveTo(16f * u, 20f * u)
                    p.lineTo(16f * u, 4f * u)
                    p.moveTo(16f * u, 20f * u)
                    p.lineTo(19.5f * u, 16.5f * u)
                }
            }
            drawPath(p, C.dim, style = st)
        }
        Spacer(Modifier.height(3.dp))
        Text(text, style = body(12, FontWeight.Medium, C.dim))
    }
}

/** The green review key: opens the review (analysing first when needed). */
@Composable
private fun ReviewKey(v: GameView) {
    GreenButton("★", height = 50.dp) { startReview(v) }
}

private fun startReview(v: GameView) {
    when {
        v.saved != null -> AppModel.open(v.saved)
        v.archived != null -> AppModel.reviewArchived(v.archived)
    }
}

/**
 * The result card: "You Won! by Resignation", who it was against and the rating change over
 * a strip of board squares, a knight hopping on a little board, the coach with your best
 * move counts, and the Game Review button.
 */
@Composable
private fun ResultCard(
    v: GameView,
    visible: Boolean,
    onClose: () -> Unit,
) {
    val review by produceState<com.verisonder.sonderchess.core.Review?>(null, v.saved) {
        value = v.saved?.let { AppModel.reviewOf(it) }
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000)).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visibleState =
                    remember {
                        androidx.compose.animation.core
                            .MutableTransitionState(false)
                            .apply { targetState = true }
                    },
                enter =
                    slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 6 } + scaleIn(tween(320), initialScale = 0.92f) +
                        fadeIn(tween(220)),
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(C.panel)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(190.dp)) {
                        BannerStrip()
                        Text(
                            "✕",
                            style = body(24, FontWeight.Bold),
                            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).clickable(onClick = onClose),
                        )
                        Column(
                            Modifier.align(Alignment.Center).padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                when (v.result) {
                                    1 -> "You Won!"
                                    -1 -> "You Lost"
                                    0 -> "Draw"
                                    else -> "Game Over"
                                },
                                style = display(32),
                            )
                            v.ending?.let { Text(it, style = body(16, FontWeight.Bold, C.dim)) }
                            Spacer(Modifier.height(10.dp))
                            val opp = if (v.meWhite) v.black else v.white
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("vs ", style = body(18, FontWeight.Bold, C.dim))
                                Text(opp.name, style = display(20))
                            }
                            v.myRating?.let { r ->
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    KindIcon(v.timeClass, 28.dp)
                                    Spacer(Modifier.width(8.dp))
                                    CountUp(r, display(26), key = "result" to v.id, delayMs = 350)
                                    v.ratingChange?.let { d ->
                                        Text(
                                            (if (d >= 0) " +" else " ") + d,
                                            style = body(16, FontWeight.Bold, if (d >= 0) C.green else C.error),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PieceDance()
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Coach(64.dp, reactTo = review != null)
                        Spacer(Modifier.width(4.dp))
                        Bubble(Modifier.weight(1f)) {
                            Text(
                                review?.let {
                                    com.verisonder.sonderchess.core.Explain
                                        .summary(it, v.meWhite)
                                }
                                    ?: "Let's see how this one went, move by move.",
                                style = body(15, FontWeight.Medium, C.bubbleText),
                            )
                        }
                    }
                    review?.let { r ->
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for ((i, l) in highlights(r, v.meWhite).withIndex()) {
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .appear(i, listOf("chip", v.id, l))
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(C.panel2)
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        LabelIcon(l, 26.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(r.count(v.meWhite, l).toString(), style = display(26, l.colour()))
                                    }
                                    Text(l.title, style = body(14, FontWeight.Bold, l.colour()))
                                }
                            }
                        }
                    }
                    Box(Modifier.padding(16.dp)) {
                        GreenButton("Game Review") { startReview(v) }
                    }
                }
            }
        }
    }
}

/** Your three proudest counts: the best kinds of move you actually played, Best and Excellent to fill in. */
private fun highlights(
    r: com.verisonder.sonderchess.core.Review,
    meWhite: Boolean,
): List<Label> {
    val order = listOf(Label.Brilliant, Label.Great, Label.Best, Label.Excellent, Label.Good)
    val played = order.filter { r.count(meWhite, it) > 0 }
    return (played + listOf(Label.Best, Label.Excellent, Label.Good)).distinct().take(3).sortedBy { order.indexOf(it) }
}

/** The card's banner: board squares in your board colours, fading into the card. */
@Composable
private fun BannerStrip() {
    Canvas(Modifier.fillMaxSize()) {
        val s = size.width / 5f
        val rows = (size.height / s).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0..4) {
                drawRect(
                    if ((r + c) % 2 == 0) AppModel.board.light.copy(alpha = 0.6f) else AppModel.board.dark.copy(alpha = 0.6f),
                    Offset(c * s, r * s),
                    androidx.compose.ui.geometry
                        .Size(s, s),
                )
            }
        }
        drawRect(
            androidx.compose.ui.graphics.Brush
                .verticalGradient(listOf(C.panel.copy(alpha = 0.25f), C.panel.copy(alpha = 0.6f), C.panel)),
        )
    }
}

@Composable
private fun stripBg(on: Boolean): Color {
    val c by androidx.compose.animation.animateColorAsState(if (on) C.panel2 else C.bar.copy(alpha = 0f), tween(180), label = "strip")
    return c
}
