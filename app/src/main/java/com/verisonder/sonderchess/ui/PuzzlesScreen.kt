package com.verisonder.sonderchess.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.core.Move
import com.verisonder.sonderchess.core.Pgn
import com.verisonder.sonderchess.core.Position
import com.verisonder.sonderchess.data.ChessComApi
import com.verisonder.sonderchess.data.Puzzle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where a puzzle attempt stands. */
private enum class Attempt { Solving, Wrong, Solved, Failed }

/**
 * Chess.com's daily puzzle and random ones from its public puzzle feed. You play the side to
 * move; the other side answers by itself. A wrong move flashes red and can be tried again;
 * Hint lights the piece to move, Solution plays the answer.
 */
@Composable
fun PuzzlesScreen() {
    val scope = rememberCoroutineScope()
    var puzzle by remember { mutableStateOf<Puzzle?>(null) }
    var daily by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    fun load(random: Boolean) {
        loading = true
        failed = false
        daily = !random
        scope.launch {
            val p = runCatching { ChessComApi.puzzle(random) }.getOrNull()
            puzzle = p
            failed = p == null
            loading = false
        }
    }
    LaunchedEffect(Unit) { load(random = false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Puzzles", onBack = null)
        val p = puzzle
        when {
            loading -> Text("Getting a puzzle…", style = body(15, color = C.faint), modifier = Modifier.padding(16.dp))
            failed || p == null ->
                Column(Modifier.padding(16.dp)) {
                    Text("Couldn't get a puzzle from Chess.com. Check your connection.", style = body(15, color = C.dim))
                    Spacer(Modifier.height(12.dp))
                    GreenButton("Try again", height = 48.dp) { load(random = !daily) }
                }
            else -> PuzzleBoard(p, daily) { load(random = true) }
        }
    }
}

@Composable
private fun PuzzleBoard(
    puzzle: Puzzle,
    daily: Boolean,
    onNext: () -> Unit,
) {
    val solution = remember(puzzle) { runCatching { Pgn.parse(puzzle.pgn) }.getOrNull() }
    val start = remember(puzzle) { solution?.positions()?.first() ?: Position.fromFen(puzzle.fen) }
    val answer = solution?.moves.orEmpty()
    // You play the side to move at the start.
    val youWhite = start.white
    var step by remember(puzzle) { mutableIntStateOf(0) }
    var pos by remember(puzzle) { mutableStateOf(start) }
    var last by remember(puzzle) { mutableStateOf<Move?>(null) }
    var state by remember(puzzle) { mutableStateOf(Attempt.Solving) }
    var marks by remember(puzzle) { mutableStateOf<Map<Int, Color>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    fun advance(m: Move) {
        Sounds.play(Sounds.eventFor(pos, m))
        pos = pos.play(m)
        last = m
        step += 1
    }

    // The other side's reply, a moment after your correct move.
    LaunchedEffect(step, state) {
        if (state != Attempt.Solving && state != Attempt.Wrong) return@LaunchedEffect
        if (step < answer.size && pos.white != youWhite) {
            delay(450)
            advance(answer[step])
        }
        if (step >= answer.size && answer.isNotEmpty() && state != Attempt.Failed) state = Attempt.Solved
    }

    fun tryMove(m: Move) {
        if (state == Attempt.Solved || state == Attempt.Failed) return
        val want = answer.getOrNull(step) ?: return
        // The listed move, or any other move that mates at once.
        val mates = pos.play(m).let { it.legalMoves().isEmpty() && it.inCheck() }
        if (m == want || mates) {
            marks = mapOf(m.to to Color(0x8881B64C))
            state = Attempt.Solving
            if (mates && m != want) {
                advance(m)
                step = answer.size
            } else {
                advance(m)
            }
        } else {
            Sounds.play(Sounds.eventFor(pos, m))
            state = Attempt.Wrong
            marks = mapOf(m.from to Color(0x88FA412D), m.to to Color(0x88FA412D))
            scope.launch {
                delay(700)
                if (state == Attempt.Wrong) marks = emptyMap()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (daily) "Daily Puzzle" else "Puzzle", style = display(18))
                if (puzzle.title.isNotBlank()) Text(puzzle.title, style = body(13, color = C.faint), maxLines = 1)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            InteractiveBoard(
                position = pos,
                flipped = !youWhite,
                last = last,
                arrows = emptyList(),
                modifier = Modifier.size(maxWidth),
                enabled = (state == Attempt.Solving || state == Attempt.Wrong) && pos.white == youWhite,
                marks = marks,
                onMove = ::tryMove,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Coach(48.dp, reactTo = state)
            Spacer(Modifier.width(4.dp))
            Bubble(Modifier.weight(1f)) {
                Text(
                    when (state) {
                        Attempt.Solving -> (if (youWhite) "White" else "Black") + " to move. Find the best move."
                        Attempt.Wrong -> "Not that one. Try again."
                        Attempt.Solved -> "Solved! Nicely done."
                        Attempt.Failed -> "That's the solution. Try the next one."
                    },
                    style = body(15, FontWeight.Medium, C.bubbleText),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == Attempt.Solved || state == Attempt.Failed) {
                GreenButton("Next Puzzle", Modifier.weight(1f), height = 50.dp, onClick = onNext)
            } else {
                TextButton(onClick = {
                    answer.getOrNull(step)?.let {
                        marks = mapOf(it.from to Color(0x885C8BB0))
                    }
                }) { Text("Hint", style = body(15, FontWeight.Bold, C.dim)) }
                TextButton(onClick = {
                    state = Attempt.Failed
                    marks = emptyMap()
                    scope.launch {
                        while (step < answer.size) {
                            advance(answer[step])
                            delay(550)
                        }
                    }
                }) { Text("Solution", style = body(15, FontWeight.Bold, C.dim)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onNext) { Text("Skip", style = body(15, FontWeight.Bold, C.dim)) }
            }
        }
    }
}
