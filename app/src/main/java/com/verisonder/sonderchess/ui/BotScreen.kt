package com.verisonder.sonderchess.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.core.Game
import com.verisonder.sonderchess.core.Move
import com.verisonder.sonderchess.core.Position
import com.verisonder.sonderchess.engine.Stockfish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

/** A bot strength: Stockfish's skill level, or a rating cap, and how long it thinks. */
private data class BotLevel(
    val name: String,
    val rating: String,
    val skill: Int,
    val elo: Int?,
    val thinkMs: Int,
)

private val levels =
    listOf(
        BotLevel("Beginner", "≈ 400", 0, null, 80),
        BotLevel("Easy", "≈ 800", 3, null, 150),
        BotLevel("Medium", "≈ 1200", 7, null, 300),
        BotLevel("Hard", "1600", 20, 1600, 500),
        BotLevel("Expert", "2000", 20, 2000, 700),
        BotLevel("Master", "2500", 20, 2500, 900),
        BotLevel("Stockfish", "Full strength", 20, null, 1200),
    )

/** Choose a bot and a colour, then play it; afterwards the game can be reviewed. */
@Composable
fun BotScreen() {
    var level by remember { mutableIntStateOf(AppModel.botLevel) }
    var colour by remember { mutableIntStateOf(0) } // 0 white, 1 random, 2 black
    var playing by remember { mutableStateOf<Pair<BotLevel, Boolean>?>(null) }
    val game = playing
    if (game == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Play a Bot", onBack = { AppModel.screen = Screen.Home })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text("Strength", style = body(13, FontWeight.Bold, C.faint), modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                for ((i, l) in levels.withIndex()) {
                    val on = i == level
                    val edge by androidx.compose.animation.animateColorAsState(if (on) C.green else C.panel, tween(180), label = "edge")
                    val lift by androidx.compose.animation.core
                        .animateFloatAsState(if (on) 1.02f else 1f, tween(180), label = "lift")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .appear(i, "bot" to i)
                            .graphicsLayer {
                                scaleX = lift
                                scaleY = lift
                            }.padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(C.panel)
                            .border(2.dp, edge, RoundedCornerShape(10.dp))
                            .clickable { level = i }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Coach(36.dp, reactTo = if (on) i else null)
                        Spacer(Modifier.width(12.dp))
                        Text(l.name, style = body(16, FontWeight.Bold), modifier = Modifier.weight(1f))
                        Text(l.rating, style = body(14, FontWeight.Bold, C.faint))
                    }
                }
                Text("You play", style = body(13, FontWeight.Bold, C.faint), modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                SlidingSegments(listOf("White", "Random", "Black"), colour, track = C.panel, pill = C.panel2) { colour = it }
                Spacer(Modifier.height(16.dp))
            }
            Column(Modifier.padding(16.dp)) {
                GreenButton("Play", height = 56.dp) {
                    AppModel.botLevel = level
                    val white =
                        when (colour) {
                            0 -> true
                            2 -> false
                            else -> Math.random() < 0.5
                        }
                    playing = levels[level] to white
                }
            }
        }
    } else {
        BotGame(game.first, game.second) { playing = null }
    }
}

@Composable
private fun BotGame(
    level: BotLevel,
    youWhite: Boolean,
    onNewGame: () -> Unit,
) {
    val context = LocalContext.current
    var positions by remember { mutableStateOf(listOf(Position.start())) }
    var moves by remember { mutableStateOf(listOf<Move>()) }
    var engine by remember { mutableStateOf<Stockfish?>(null) }
    var resigned by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    val pos = positions.last()
    val over = resigned || pos.legalMoves().isEmpty()
    val botName = "${level.name} Bot"

    LaunchedEffect(Unit) { engine = runCatching { Stockfish.start(context, threads = 1, hashMb = 16) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { engine?.let { e -> thread { runCatching { e.stop() }.also { e.close() } } } } }

    fun play(m: Move) {
        Sounds.play(Sounds.eventFor(positions.last(), m))
        positions = positions + positions.last().play(m)
        moves = moves + m
    }

    // The bot's turn.
    LaunchedEffect(positions.size, engine) {
        val e = engine ?: return@LaunchedEffect
        if (over || pos.white == youWhite) return@LaunchedEffect
        delay(250)
        val uci = withContext(Dispatchers.IO) { runCatching { e.bestMove(pos.fen(), level.skill, level.elo, level.thinkMs) }.getOrNull() }
        val m = uci?.let { runCatching { Move.fromUci(it) }.getOrNull() }?.takeIf { it in pos.legalMoves() }
        if (m != null && !resigned) play(m)
    }
    LaunchedEffect(over) { if (over) showEnd = true }

    val result =
        when {
            resigned -> -1
            pos.isCheckmate() -> if (pos.white == youWhite) -1 else 1
            pos.legalMoves().isEmpty() -> 0
            else -> null
        }

    fun finishedGame(): Game {
        val me = AppModel.username ?: "You"
        val score =
            when (result) {
                1 -> if (youWhite) "1-0" else "0-1"
                -1 -> if (youWhite) "0-1" else "1-0"
                else -> "1/2-1/2"
            }
        val how =
            when {
                resigned -> "$botName won by resignation"
                result == 0 -> "Game drawn by stalemate"
                result == 1 -> "$me won by checkmate"
                else -> "$botName won by checkmate"
            }
        return Game(
            mapOf(
                "Event" to "SonderChess vs $botName",
                "White" to if (youWhite) me else botName,
                "Black" to if (youWhite) botName else me,
                "Result" to score,
                "Termination" to how,
            ),
            Position.START_FEN,
            moves,
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("vs $botName", onBack = { AppModel.screen = Screen.Home })
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Coach(40.dp, reactTo = moves.size)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(botName, style = body(16, FontWeight.Bold))
                    Text(level.rating, style = body(13, color = C.faint))
                }
                Spacer(Modifier.weight(1f))
                if (!over && pos.white != youWhite) ThinkingDots()
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                InteractiveBoard(
                    position = pos,
                    flipped = !youWhite,
                    last = moves.lastOrNull(),
                    arrows = emptyList(),
                    modifier = Modifier.size(maxWidth),
                    enabled = !over && pos.white == youWhite && engine != null,
                    onMove = ::play,
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(AppModel.username ?: "You", youWhite, 40.dp, AppModel.profile?.avatar)
                Spacer(Modifier.width(10.dp))
                Text(AppModel.username ?: "You", style = body(16, FontWeight.Bold))
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().background(C.bar).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (over) {
                    GreenButton("Game Review", Modifier.weight(1f), height = 50.dp) {
                        AppModel.reviewPlayed(finishedGame(), youWhite, botName)
                    }
                    TextButton(onClick = onNewGame) { Text("New Game", style = body(15, FontWeight.Bold, C.dim)) }
                } else {
                    // Take back your last move (and the bot's answer to it).
                    TextButton(
                        enabled = moves.isNotEmpty() && pos.white == youWhite,
                        onClick = {
                            val back = if (moves.size >= 2) 2 else 1
                            positions = positions.dropLast(back)
                            moves = moves.dropLast(back)
                        },
                    ) { Text("Undo", style = body(15, FontWeight.Bold, C.dim)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { resigned = true }) { Text("Resign", style = body(15, FontWeight.Bold, C.error)) }
                }
            }
        }

        // The end: a card that rises in over the board, like the game page's result card.
        androidx.compose.animation.AnimatedVisibility(
            visible = showEnd && result != null,
            enter = androidx.compose.animation.fadeIn(tween(200)),
            exit = androidx.compose.animation.fadeOut(tween(150)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showEnd = false },
                contentAlignment = Alignment.Center,
            ) {
                val rise =
                    remember {
                        androidx.compose.animation.core
                            .Animatable(0f)
                    }
                LaunchedEffect(Unit) {
                    rise.animateTo(
                        1f,
                        androidx.compose.animation.core
                            .spring(dampingRatio = 0.7f, stiffness = 300f),
                    )
                }
                Column(
                    Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = (1f - rise.value) * 120f
                            alpha = rise.value.coerceIn(0f, 1f)
                        }.clip(RoundedCornerShape(18.dp))
                        .background(C.panel)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        when (result) {
                            1 -> "You Won!"
                            -1 -> "You Lost"
                            else -> "Draw"
                        },
                        style = display(30),
                    )
                    Text(finishedGame().headers["Termination"].orEmpty(), style = body(15, FontWeight.Bold, C.dim))
                    Spacer(Modifier.height(14.dp))
                    PieceDance()
                    Spacer(Modifier.height(16.dp))
                    GreenButton("Game Review") {
                        showEnd = false
                        AppModel.reviewPlayed(finishedGame(), youWhite, botName)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = onNewGame) { Text("New Game", style = body(15, FontWeight.Bold, C.dim)) }
                        TextButton(onClick = { showEnd = false }) { Text("Look at the board", style = body(15, FontWeight.Bold, C.dim)) }
                    }
                }
            }
        }
    }
}

/** Three dots that pulse one after another while the bot thinks. */
@Composable
private fun ThinkingDots() {
    val t =
        androidx.compose.animation.core
            .rememberInfiniteTransition(label = "dots")
    val p by t.animateFloat(
        0f,
        3f,
        androidx.compose.animation.core
            .infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.LinearEasing)),
        label = "dot",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..2) {
            val on = (p - i).let { if (it < 0) it + 3 else it } < 1f
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(7.dp)
                    .graphicsLayer {
                        val k = if (on) 1.25f else 1f
                        scaleX = k
                        scaleY = k
                    }.clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (on) C.text else C.faint),
            )
        }
    }
}
