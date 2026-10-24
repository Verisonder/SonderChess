package com.verisonder.sonderchess.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.core.Label
import com.verisonder.sonderchess.core.Move
import com.verisonder.sonderchess.core.OfflinePuzzle
import com.verisonder.sonderchess.core.Pgn
import com.verisonder.sonderchess.core.Position
import com.verisonder.sonderchess.core.PuzzleBook
import com.verisonder.sonderchess.data.ChessComApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A puzzle, from either source, in one shape. */
private data class PuzzleSpec(
    val key: String,
    val start: Position,
    val answer: List<Move>,
    /** Lichess puzzles open with the opponent's move; Chess.com's start with yours. */
    val opponentFirst: Boolean,
    val idea: String,
)

/** Where an attempt stands. */
private enum class Attempt { Solving, Right, Wrong, Solved, Shown }

/**
 * Two kinds of puzzle: the ones that come with the app (over 46,000 from Lichess, played
 * offline at your rating, which moves as you solve) and Chess.com's daily puzzle.
 */
@Composable
fun PuzzlesScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TopBar(if (tab == 0) "Puzzles" else "Daily Puzzle", onBack = null)
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            SlidingSegments(listOf("Puzzles", "Daily"), tab, track = C.panel, pill = C.panel2, height = 36.dp) { tab = it }
        }
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(120))) }, label = "ptab") { t ->
            if (t == 0) OfflinePuzzles() else DailyPuzzle()
        }
    }
}

@Composable
private fun OfflinePuzzles() {
    val scope = rememberCoroutineScope()
    var puzzle by remember { mutableStateOf<OfflinePuzzle?>(null) }
    var change by remember { mutableStateOf<Int?>(null) }

    fun next() {
        change = null
        scope.launch { puzzle = AppModel.nextPuzzle() }
    }
    LaunchedEffect(Unit) { if (puzzle == null) next() }
    val p = puzzle
    Column(Modifier.fillMaxSize()) {
        // Your rating and streak.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            KindIcon("puzzles", 24.dp)
            Spacer(Modifier.width(8.dp))
            CountUp(AppModel.puzzleRating, display(20), key = "puzzle-rating")
            change?.let { d ->
                Text(
                    (if (d >= 0) "  +" else "  ") + d,
                    style = body(15, FontWeight.Bold, if (d >= 0) C.green else C.error),
                    modifier = Modifier.appear(0, listOf("delta", p?.id, d)),
                )
            }
            Spacer(Modifier.weight(1f))
            if (AppModel.puzzleStreak > 0) Text("Streak ${AppModel.puzzleStreak}", style = body(14, FontWeight.Bold, C.dim))
        }
        if (p == null) {
            Text("Getting a puzzle…", style = body(15, color = C.faint), modifier = Modifier.padding(16.dp))
        } else {
            val spec =
                remember(p.id) {
                    PuzzleSpec(p.id, Position.fromFen(p.fen), p.moves, true, PuzzleBook.title(p.themes))
                }
            PuzzleBoard(spec, onResult = { clean -> change = AppModel.recordPuzzle(p, clean) }, onNext = ::next)
        }
    }
}

@Composable
private fun DailyPuzzle() {
    val scope = rememberCoroutineScope()
    var spec by remember { mutableStateOf<PuzzleSpec?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun load() {
        failed = false
        scope.launch {
            val p = runCatching { ChessComApi.puzzle(random = false) }.getOrNull()
            val game = p?.let { runCatching { Pgn.parse(it.pgn) }.getOrNull() }
            spec =
                if (p != null && game != null && game.moves.isNotEmpty()) {
                    PuzzleSpec("daily-" + p.url, game.positions().first(), game.moves, false, p.title.ifBlank { "Daily Puzzle" })
                } else {
                    null
                }
            failed = spec == null
        }
    }
    LaunchedEffect(Unit) { if (spec == null) load() }
    val s = spec
    when {
        s != null -> PuzzleBoard(s, onResult = {}, onNext = null)
        failed ->
            Column(Modifier.padding(16.dp)) {
                Text("Couldn't get today's puzzle from Chess.com. Check your connection.", style = body(15, color = C.dim))
                Spacer(Modifier.height(12.dp))
                GreenButton("Try again", height = 48.dp) { load() }
            }
        else -> Text("Getting today's puzzle…", style = body(15, color = C.faint), modifier = Modifier.padding(16.dp))
    }
}

/**
 * The puzzle itself, laid out like the review: the coach above the board, the board, and the
 * bar at the bottom. A right move gets a green tick on its square, a wrong one a red cross and a
 * shake before it goes back. [onResult] hears once whether it was solved cleanly.
 */
@Composable
private fun PuzzleBoard(
    spec: PuzzleSpec,
    onResult: (Boolean) -> Unit,
    onNext: (() -> Unit)?,
) {
    val youWhite = if (spec.opponentFirst) !spec.start.white else spec.start.white
    var step by remember(spec.key) { mutableIntStateOf(0) }
    var pos by remember(spec.key) { mutableStateOf(spec.start) }
    var last by remember(spec.key) { mutableStateOf<Move?>(null) }
    var badge by remember(spec.key) { mutableStateOf<Label?>(null) }
    var state by remember(spec.key) { mutableStateOf(Attempt.Solving) }
    var helped by remember(spec.key) { mutableStateOf(false) }
    var reported by remember(spec.key) { mutableStateOf(false) }
    var hint by remember(spec.key) { mutableStateOf<Int?>(null) }
    var busy by remember(spec.key) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }

    fun report(clean: Boolean) {
        if (!reported) {
            reported = true
            onResult(clean)
        }
    }

    fun advance(m: Move) {
        Sounds.play(Sounds.eventFor(pos, m))
        pos = pos.play(m)
        last = m
        step += 1
    }

    // The opponent's moves: the opening one on Lichess puzzles, and every reply.
    LaunchedEffect(step, spec.key) {
        if (state == Attempt.Shown || state == Attempt.Solved) return@LaunchedEffect
        if (step < spec.answer.size && pos.white != youWhite) {
            busy = true
            delay(if (step == 0) 650 else 450)
            badge = null
            advance(spec.answer[step])
            busy = false
            if (state == Attempt.Right) state = Attempt.Solving
        }
    }

    fun tryMove(m: Move) {
        if (busy || state == Attempt.Solved || state == Attempt.Shown) return
        val want = spec.answer.getOrNull(step) ?: return
        hint = null
        val after = pos.play(m)
        val mates = after.legalMoves().isEmpty() && after.inCheck()
        if (m == want || mates) {
            advance(m)
            badge = Label.Good
            if (mates || step >= spec.answer.size) {
                step = spec.answer.size
                state = Attempt.Solved
                report(!helped)
            } else {
                state = Attempt.Right
            }
        } else {
            // Show the wrong move, cross it, shake, and put it back.
            helped = true
            report(false)
            val before = pos
            val beforeLast = last
            Sounds.play(Sounds.eventFor(pos, m))
            pos = after
            last = m
            badge = Label.Miss
            state = Attempt.Wrong
            busy = true
            scope.launch {
                for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(45))
                delay(550)
                pos = before
                last = beforeLast
                badge = null
                busy = false
            }
        }
    }

    fun retry() {
        step = 0
        pos = spec.start
        last = null
        badge = null
        hint = null
        state = Attempt.Solving
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Coach(60.dp, reactTo = state)
            Spacer(Modifier.width(4.dp))
            Bubble(Modifier.weight(1f)) {
                AnimatedContent(
                    state,
                    transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.94f)).togetherWith(fadeOut(tween(120))) },
                    label = "say",
                ) { shown ->
                    val side = if (youWhite) "White" else "Black"
                    val (icon, title, line) =
                        when (shown) {
                            Attempt.Solving -> Triple(null, "$side to move", spec.idea)
                            Attempt.Right -> Triple(Label.Good, "Best move!", "Keep going…")
                            Attempt.Wrong -> Triple(Label.Miss, "Incorrect", "That's not it. Try again.")
                            Attempt.Solved ->
                                if (helped) {
                                    Triple(Label.Good, "Solved", "Next time, try it with no help.")
                                } else {
                                    Triple(Label.Good, "Puzzle Solved!", "Great job solving with no mistakes!")
                                }
                            Attempt.Shown -> Triple(Label.Book, "Solution", "Here's how it goes.")
                        }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            icon?.let {
                                LabelIcon(it, 22.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(title, style = body(17, FontWeight.Bold, C.bubbleText))
                        }
                        Text(line, style = body(15, color = C.bubbleText))
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            InteractiveBoard(
                position = pos,
                flipped = !youWhite,
                last = last,
                arrows = emptyList(),
                modifier = Modifier.size(maxWidth).graphicsLayer { translationX = shake.value * density },
                enabled = !busy && (state == Attempt.Solving || state == Attempt.Right) && pos.white == youWhite,
                marks =
                    hint
                        ?.let {
                            mapOf(
                                it to
                                    androidx.compose.ui.graphics
                                        .Color(0x885C8BB0),
                            )
                        }.orEmpty(),
                label = badge,
                onMove = ::tryMove,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().background(C.bar).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val open = state == Attempt.Solving || state == Attempt.Right || state == Attempt.Wrong
            ToolButton("Hint", "hint", enabled = open && !busy) {
                spec.answer.getOrNull(step)?.let {
                    helped = true
                    report(false)
                    hint = it.from
                }
            }
            ToolButton("Solution", "solution", enabled = open && !busy) {
                helped = true
                report(false)
                state = Attempt.Shown
                hint = null
                badge = null
                scope.launch {
                    while (step < spec.answer.size) {
                        advance(spec.answer[step])
                        delay(600)
                    }
                }
            }
            if (onNext != null) {
                GreenButton("Next", Modifier.width(110.dp), height = 48.dp) { onNext() }
            }
            ToolButton("Retry", "retry", enabled = !busy && step > 0) { retry() }
        }
    }
}
