package com.verisonder.sonderchess.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

/** A bot: its name, strength (Stockfish skill level or a rating cap), think time and look. */
data class Bot(
    val name: String,
    val rating: String,
    val skill: Int,
    val elo: Int?,
    val thinkMs: Int,
    val colour: Long,
    val greeting: String,
    /** "robot" (with [style] picking eyes and mouth), "mark" (the SR), "engine", "mirror" or "dial". */
    val look: String = "robot",
    val style: Int = 0,
)

val bots =
    listOf(
        Bot("Pebble", "400", 0, null, 80, 0xFF8BC34A, "Hi! I'm new at this too. Go easy on me?", style = 0),
        Bot("Bolt", "800", 3, null, 150, 0xFFFFB74D, "Zap! I know how the pieces move. Mostly.", style = 1),
        Bot("Gizmo", "1200", 7, null, 300, 0xFF4FC3F7, "I've read a chess book. Let's see what you've got.", style = 2),
        Bot("Rook-E", "1600", 20, 1600, 500, 0xFFBA68C8, "Beep. Calculating your weaknesses…", style = 3),
        Bot("Vector", "2000", 20, 2000, 700, 0xFFE57373, "I don't make many mistakes. Do you?", style = 4),
        Bot("Titan", "2500", 20, 2500, 900, 0xFF90A4AE, "Masters fear me. Shall we begin?", style = 5),
        // The maker. Says it's a beginner; plays full-strength Stockfish and thinks the longest.
        Bot("SR", "Beginner", 20, null, 2500, 0xFF379ED6, "Don't worry, I'm just a beginner. Be gentle with me!", look = "mark"),
    )

/** Stockfish itself, at full strength. */
val engineBot = Bot("Stockfish", "Full strength", 20, null, 1200, 0xFF81B64C, "No mercy. No mistakes. Good luck.", look = "engine")

/** What the Play tab can pick besides the bots in [bots]. */
const val ENGINE_PICK = 7
const val BOARD_PICK = 8
const val MIRROR_PICK = 9
const val DIAL_PICK = 10

/**
 * Stockfish set to a rating. From 1320 up it uses Stockfish's own rating limit; below that, its
 * skill level, scaled so 400 is the weakest it plays.
 */
fun botAt(
    name: String,
    elo: Int,
    colour: Long,
    greeting: String,
    look: String,
): Bot =
    if (elo >= 1320) {
        Bot(name, "$elo", 20, elo.coerceAtMost(3190), (300 + (elo - 1320) / 4).coerceAtMost(900), colour, greeting, look)
    } else {
        val skill = ((elo - 400).coerceAtLeast(0) * 9 / 920).coerceIn(0, 9)
        Bot(name, "$elo", skill, null, 80 + skill * 25, colour, greeting, look)
    }

/** Your rating on Chess.com (rapid, else blitz, daily or bullet), or null if it isn't known. */
fun yourRating(): Int? = AppModel.profile?.let { it.rapid ?: it.blitz ?: it.daily ?: it.bullet }

/** Mirror: plays at your own rating. */
fun mirrorBot(): Bot {
    val r = yourRating()
    return botAt(
        "Mirror",
        r ?: 1200,
        0xFFB0BEC5,
        if (r !=
            null
        ) {
            "I play just like you. Rated $r, same as you. Let's see who blinks first."
        } else {
            "Choose a Chess.com player on Home and I'll match their rating. Until then, I'm 1200."
        },
        "mirror",
    )
}

/** Custom: plays at the rating you set. */
fun dialBot(elo: Int): Bot = botAt("Custom", elo, 0xFF4DB6AC, "Set my rating anywhere from 400 to 3000. I'll play right at it.", "dial")

/** The opponent for a Play tab pick, or null for the custom board. */
fun botFor(pick: Int): Bot? =
    bots.getOrNull(pick) ?: when (pick) {
        ENGINE_PICK -> engineBot
        MIRROR_PICK -> mirrorBot()
        DIAL_PICK -> dialBot(AppModel.dialElo)
        else -> null
    }

/** A game against the bot and colour picked on the Play tab; afterwards it can be reviewed. */
@Composable
fun BotScreen() {
    val bot = botFor(AppModel.botLevel) ?: engineBot
    BotGame(bot, AppModel.botWhite) { AppModel.screen = Screen.PlayTab }
}

@Composable
private fun BotGame(
    level: Bot,
    youWhite: Boolean,
    onNewGame: () -> Unit,
) {
    val context = LocalContext.current
    var positions by remember { mutableStateOf(listOf(Position.start())) }
    var moves by remember { mutableStateOf(listOf<Move>()) }
    var engine by remember { mutableStateOf<Stockfish?>(null) }
    var resigned by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    // Looking back through the game: the position shown, or null for the live one.
    var viewing by remember { mutableStateOf<Int?>(null) }
    var flipped by remember { mutableStateOf(!youWhite) }
    var hint by remember { mutableStateOf<Move?>(null) }
    var options by remember { mutableStateOf(false) }
    var saying by remember { mutableStateOf(level.greeting) }
    val pos = positions.last()
    val draw = drawReason(positions)
    val over = resigned || pos.legalMoves().isEmpty() || draw != null
    val botName = level.name

    LaunchedEffect(Unit) { engine = runCatching { Stockfish.start(context, threads = 1, hashMb = 16) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { engine?.let { e -> thread { runCatching { e.stop() }.also { e.close() } } } } }

    fun play(m: Move) {
        val before = positions.last()
        val mine = before.white == youWhite
        Sounds.play(Sounds.eventFor(before, m))
        val after = before.play(m)
        positions = positions + after
        moves = moves + m
        hint = null
        viewing = null
        saying = botSays(level, before, m, after, mine, moves.size) ?: saying
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
    LaunchedEffect(over) {
        if (over) {
            showEnd = true
            saying =
                when {
                    resigned -> "Good game! Want to try again?"
                    draw != null -> "A draw! Well held."
                    pos.white == youWhite -> "Checkmate! That one was mine."
                    else -> "You got me. Well played!"
                }
        }
    }

    val result =
        when {
            resigned -> -1
            pos.isCheckmate() -> if (pos.white == youWhite) -1 else 1
            pos.legalMoves().isEmpty() || draw != null -> 0
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
                draw != null -> "Game drawn by $draw"
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

    val shownPly = viewing ?: moves.size
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("vs $botName", onBack = { AppModel.screen = Screen.PlayTab })
            Spacer(Modifier.weight(1f))
            // The bot talks above the board, like the coach in a review.
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BotFace(level, 64.dp)
                Spacer(Modifier.width(4.dp))
                Bubble(Modifier.weight(1f)) {
                    androidx.compose.animation.AnimatedContent(
                        saying,
                        transitionSpec = {
                            (
                                androidx.compose.animation.fadeIn(tween(160)) +
                                    androidx.compose.animation.scaleIn(initialScale = 0.94f)
                            ).togetherWith(androidx.compose.animation.fadeOut(tween(100)))
                        },
                        label = "say",
                    ) { line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(line, style = body(15, FontWeight.Medium, C.bubbleText), modifier = Modifier.weight(1f))
                            if (!over && pos.white != youWhite && viewing == null) ThinkingDots()
                        }
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                InteractiveBoard(
                    position = positions[shownPly],
                    flipped = flipped,
                    last = moves.getOrNull(shownPly - 1),
                    arrows = listOfNotNull(hint),
                    modifier = Modifier.size(maxWidth),
                    enabled = !over && viewing == null && pos.white == youWhite && engine != null,
                    onMove = ::play,
                )
            }
            Spacer(Modifier.weight(1f))
            BotMoves(positions, moves, shownPly) { viewing = if (it >= moves.size) null else it }
            Row(
                Modifier.fillMaxWidth().background(C.bar).padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (over) {
                    ToolButton("Options", "options") { options = true }
                    GreenButton("Game Review", Modifier.weight(1f).padding(horizontal = 8.dp), height = 48.dp) {
                        AppModel.reviewPlayed(finishedGame(), youWhite, botName)
                    }
                    ToolButton("New", "retry") { onNewGame() }
                } else {
                    ToolButton("Options", "options") { options = true }
                    // Before the second move there is nothing to lose: Abort just leaves.
                    if (moves.size < 2) {
                        ToolButton("Abort", "flag") { onNewGame() }
                    } else {
                        ToolButton("Resign", "flag") { resigned = true }
                    }
                    ToolButton("Hint", "hint", enabled = pos.white == youWhite && viewing == null && engine != null) {
                        val e = engine ?: return@ToolButton
                        thread {
                            val uci = runCatching { e.bestMove(pos.fen(), 20, null, 400) }.getOrNull()
                            hint = uci?.let { runCatching { Move.fromUci(it) }.getOrNull() }
                            saying = "Look at the arrow. That's what I'd play."
                        }
                    }
                    // Take back your last move (and the bot's answer to it).
                    ToolButton("Undo", "undo", enabled = moves.isNotEmpty() && pos.white == youWhite) {
                        val back = if (moves.size >= 2) 2 else 1
                        positions = positions.dropLast(back)
                        moves = moves.dropLast(back)
                        viewing = null
                        hint = null
                        saying = "Take your time. Try another one."
                    }
                }
            }
        }

        if (options) {
            BotOptions(
                onFlip = {
                    flipped = !flipped
                    options = false
                },
                onShare = {
                    options = false
                    val send =
                        android.content
                            .Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, finishedGame().toPgn())
                    context.startActivity(android.content.Intent.createChooser(send, "Share game"))
                },
                onClose = { options = false },
            )
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

/** Why the game is drawn (repetition, fifty moves, too little material), or null if it isn't. */
private fun drawReason(positions: List<Position>): String? {
    val now = positions.last()
    val key =
        now
            .fen()
            .split(' ')
            .take(4)
            .joinToString(" ")
    if (positions.count {
            it
                .fen()
                .split(' ')
                .take(4)
                .joinToString(" ") == key
        } >= 3
    ) {
        return "repetition"
    }
    if ((
            now
                .fen()
                .split(' ')
                .getOrNull(4)
                ?.toIntOrNull() ?: 0
        ) >= 100
    ) {
        return "the 50-move rule"
    }
    val rest = (0 until 64).map { now[it] }.filter { it != 0 && kotlin.math.abs(it) != com.verisonder.sonderchess.core.Piece.KING }
    val minorOnly =
        rest.all {
            kotlin.math.abs(it) == com.verisonder.sonderchess.core.Piece.KNIGHT ||
                kotlin.math.abs(it) == com.verisonder.sonderchess.core.Piece.BISHOP
        }
    if (rest.isEmpty() || (rest.size == 1 && minorOnly)) return "insufficient material"
    return null
}

/** What the bot says after a move, or null to keep its last line. */
private fun botSays(
    bot: Bot,
    before: Position,
    m: Move,
    after: Position,
    mine: Boolean,
    n: Int,
): String? {
    fun pick(vararg lines: String) = lines[n % lines.size]
    return when {
        after.legalMoves().isEmpty() -> null
        after.inCheck() && mine -> pick("Whoa, check! Let me think…", "Hey, my king!", "Check? Rude.")
        after.inCheck() -> pick("Check!", "Your king looks a bit nervous.", "Check. Your move.")
        before.isCapture(m) && mine -> pick("Hey, I was using that!", "Ouch.", "Nice grab.", "I didn't need that one anyway.")
        before.isCapture(m) -> pick("Mine now!", "Thanks for that!", "Yoink.", "I'll take that.")
        before.isCastle(m) && !mine -> "Safety first."
        n == 1 || n == 2 -> bot.greeting
        else -> null
    }
}

/** The moves so far in one centred line, with arrows to step back and forth. */
@Composable
private fun BotMoves(
    positions: List<Position>,
    moves: List<Move>,
    ply: Int,
    onPick: (Int) -> Unit,
) {
    val state =
        androidx.compose.foundation.lazy
            .rememberLazyListState()
    LaunchedEffect(ply, moves.size) { if (moves.isNotEmpty()) state.animateScrollToItem(maxOf(0, ply - 3)) }
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹",
            style =
                display(
                    30,
                    if (ply >
                        0
                    ) {
                        C.dim
                    } else {
                        C.faint
                    },
                ),
            modifier = Modifier.clickable(enabled = ply > 0) { onPick(ply - 1) }.padding(horizontal = 16.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(
            state = state,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(moves.size) { i ->
                val before = positions[i]
                val on = i + 1 == ply
                Text(
                    (if (before.white) "${before.fullmove}. " else "") + before.san(moves[i]),
                    style = body(16, FontWeight.Bold, if (on) C.text else C.dim),
                    modifier =
                        Modifier
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (on) C.panel2 else Color.Transparent)
                            .clickable { onPick(i + 1) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        Text(
            "›",
            style = display(30, if (ply < moves.size) C.dim else C.faint),
            modifier = Modifier.clickable(enabled = ply < moves.size) { onPick(ply + 1) }.padding(horizontal = 16.dp),
        )
    }
}

/** The Options sheet: flip the board, share the game. */
@Composable
private fun BotOptions(
    onFlip: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .appear(0, listOf("botoptions", remember { System.nanoTime() }))
                .clip(RoundedCornerShape(14.dp))
                .background(C.panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        ) {
            Row(
                Modifier.fillMaxWidth().background(C.bar).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Options", style = body(18, FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("✕", style = body(20, FontWeight.Bold, C.dim), modifier = Modifier.clickable(onClick = onClose).padding(4.dp))
            }
            for ((label, action) in listOf("Flip Board" to onFlip, "Share Game" to onShare)) {
                Text(
                    label,
                    style = body(17, FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 18.dp, vertical = 16.dp),
                )
            }
        }
    }
}
