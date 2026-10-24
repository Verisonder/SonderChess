package com.verisonder.sonderchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
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
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(C.panel)
                            .border(2.dp, if (on) C.green else C.panel, RoundedCornerShape(10.dp))
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
            if (!over && pos.white != youWhite) Text("Thinking…", style = body(13, color = C.faint))
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

    if (showEnd && result != null) {
        AlertDialog(
            onDismissRequest = { showEnd = false },
            containerColor = C.panel,
            title = {
                Text(
                    when (result) {
                        1 -> "You Won!"
                        -1 -> "You Lost"
                        else -> "Draw"
                    },
                    style = display(22),
                )
            },
            text = { Text(finishedGame().headers["Termination"].orEmpty(), style = body(15, color = C.dim)) },
            confirmButton = {
                TextButton(onClick = {
                    showEnd = false
                    AppModel.reviewPlayed(finishedGame(), youWhite, botName)
                }) { Text("Game Review", style = body(15, FontWeight.Bold, C.green)) }
            },
            dismissButton = { TextButton(onClick = { showEnd = false }) { Text("Close", style = body(15, FontWeight.Bold, C.dim)) } },
        )
    }
}
