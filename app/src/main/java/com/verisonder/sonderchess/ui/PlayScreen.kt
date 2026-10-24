package com.verisonder.sonderchess.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.verisonder.sonderchess.core.Move
import com.verisonder.sonderchess.core.Piece
import com.verisonder.sonderchess.core.Position
import com.verisonder.sonderchess.core.PositionEval
import com.verisonder.sonderchess.core.fileOf
import com.verisonder.sonderchess.core.rankOf
import com.verisonder.sonderchess.core.sq
import com.verisonder.sonderchess.engine.Stockfish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.thread

/**
 * A board you play moves on: tap a piece to see where it can go (dots, rings on captures),
 * tap a square to move there. Pawns reaching the last rank ask what to become.
 * [marks] tints squares (a right or wrong puzzle answer, a hint).
 */
@Composable
fun InteractiveBoard(
    position: Position,
    flipped: Boolean,
    last: Move?,
    arrows: List<Move>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    marks: Map<Int, Color> = emptyMap(),
    label: com.verisonder.sonderchess.core.Label? = null,
    onMove: (Move) -> Unit,
) {
    val fen = position.fen()
    var selected by remember(fen) { mutableStateOf<Int?>(null) }
    var promo by remember(fen) { mutableStateOf<List<Move>?>(null) }
    val legal = remember(fen) { position.legalMoves() }
    Box(modifier) {
        // Tints go under the pieces: the selected piece's square, and any hint or answer marks.
        val tints = selected?.let { marks + (it to Color(0x80F7EC74)) } ?: marks
        ChessBoard(
            position,
            last = last,
            label = label,
            arrows = arrows,
            flipped = flipped,
            modifier = Modifier.fillMaxSize(),
            marks = tints,
        )
        Canvas(
            Modifier.fillMaxSize().pointerInput(fen, flipped, enabled) {
                detectTapGestures { off ->
                    if (!enabled) return@detectTapGestures
                    val s = size.width / 8f
                    val x = (off.x / s).toInt().coerceIn(0, 7)
                    val y = (off.y / s).toInt().coerceIn(0, 7)
                    val tapped = sq(if (flipped) 7 - x else x, if (flipped) y else 7 - y)
                    val here = selected?.let { from -> legal.filter { it.from == from && it.to == tapped } }.orEmpty()
                    when {
                        here.size > 1 -> promo = here
                        here.size == 1 -> {
                            selected = null
                            onMove(here[0])
                        }
                        legal.any { it.from == tapped } -> selected = if (selected == tapped) null else tapped
                        else -> selected = null
                    }
                }
            },
        ) {
            val s = size.width / 8f

            fun at(square: Int) =
                Offset(
                    (if (flipped) 7 - fileOf(square) else fileOf(square)) * s,
                    (if (flipped) rankOf(square) else 7 - rankOf(square)) * s,
                )
            // Only the move dots and capture rings are drawn over the pieces.
            selected?.let { from ->
                for (m in legal.filter { it.from == from }.distinctBy { it.to }) {
                    val c = at(m.to) + Offset(s / 2f, s / 2f)
                    if (position[m.to] != 0) {
                        drawCircle(Color(0x33000000), s * 0.46f, c, style = Stroke(s * 0.08f))
                    } else {
                        drawCircle(Color(0x33000000), s * 0.16f, c)
                    }
                }
            }
        }
    }
    promo?.let { options ->
        PromotionDialog(options, position.white) { m ->
            promo = null
            selected = null
            m?.let(onMove)
        }
    }
}

@Composable
private fun PromotionDialog(
    options: List<Move>,
    white: Boolean,
    onPick: (Move?) -> Unit,
) {
    val painters = piecePainters()
    AlertDialog(
        onDismissRequest = { onPick(null) },
        containerColor = C.panel,
        title = { Text("Promote to", style = display(18)) },
        text = {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                for (kind in listOf(Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT)) {
                    val m = options.firstOrNull { it.promo == kind } ?: continue
                    val p = painters[6 + if (white) kind else -kind]
                    Canvas(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(C.panel2)
                            .clickable { onPick(m) },
                    ) {
                        p?.run { draw(size) }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/** Whether the board below is played on or set up. */
private enum class Mode { Play, Edit }

/**
 * A free board: play moves for both sides, go back and forth, set up any position, and turn
 * Stockfish on to see the evaluation, its two best lines and an arrow for the best move.
 */
@Composable
fun PlayScreen() {
    var positions by remember { mutableStateOf(listOf(Position.start())) }
    var moves by remember { mutableStateOf(listOf<Move>()) }
    var ply by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.Play) }
    var engineOn by remember { mutableStateOf(AppModel.playEngine) }
    var eval by remember { mutableStateOf<PositionEval?>(null) }
    var engine by remember { mutableStateOf<Stockfish?>(null) }
    val lock = remember { Mutex() }
    val context = LocalContext.current
    val pos = positions[ply]

    // Set-up state.
    var board by remember { mutableStateOf(IntArray(64)) }
    var tool by remember { mutableIntStateOf(Piece.PAWN) }
    var setupWhite by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun play(m: Move) {
        val keep = positions.take(ply + 1)
        Sounds.play(Sounds.eventFor(keep.last(), m))
        positions = keep + keep.last().play(m)
        moves = moves.take(ply) + m
        ply += 1
    }

    // The engine lives while it is switched on and this page is open.
    LaunchedEffect(engineOn) {
        AppModel.playEngine = engineOn
        if (engineOn && engine == null) {
            engine = runCatching { Stockfish.start(context) }.getOrNull()
        } else if (!engineOn) {
            engine?.let { e -> thread { lock.tryLockOrWait { e.close() } } }
            engine = null
            eval = null
        }
    }
    DisposableEffect(Unit) {
        onDispose { engine?.let { e -> thread { runCatching { e.stop() }.also { e.close() } } } }
    }
    // Every new position: stop the old search, start a new one that keeps deepening.
    LaunchedEffect(pos.fen(), engine, mode) {
        val e = engine ?: return@LaunchedEffect
        eval = null
        if (mode == Mode.Edit || pos.legalMoves().isEmpty()) return@LaunchedEffect
        lock.withLock {
            coroutineScope {
                var live = true
                val work = async(Dispatchers.IO) { runCatching { e.analyse(pos.fen(), 24) { pe -> if (live) eval = pe } } }
                try {
                    work.await()
                } finally {
                    if (!work.isCompleted) {
                        live = false
                        runCatching { e.stop() }
                    }
                }
            }
        }
    }

    val best =
        eval
            ?.lines
            ?.firstOrNull()
            ?.let { runCatching { Move.fromUci(it.move) }.getOrNull() }
            ?.takeIf { it in pos.legalMoves() }
    Column(Modifier.fillMaxSize()) {
        TopBar(if (mode == Mode.Edit) "Set Up Position" else "Analysis Board", onBack = { AppModel.screen = Screen.PlayTab })
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val boardSize = if (engineOn && mode == Mode.Play) maxWidth - 18.dp else maxWidth
            Row(Modifier.height(boardSize)) {
                if (engineOn && mode == Mode.Play) EvalBar(eval?.lines?.firstOrNull() ?: EngineLine(""), flipped, Modifier.fillMaxHeight())
                if (mode == Mode.Edit) {
                    SetupBoard(board, setupWhite, flipped, Modifier.size(boardSize)) { square ->
                        problem = null
                        board = board.copyOf().also { it[square] = if (it[square] == tool) 0 else tool }
                    }
                } else {
                    InteractiveBoard(
                        position = pos,
                        flipped = flipped,
                        last = moves.getOrNull(ply - 1),
                        arrows = if (engineOn) listOfNotNull(best) else emptyList(),
                        modifier = Modifier.size(boardSize),
                        onMove = ::play,
                    )
                }
            }
        }
        if (mode == Mode.Play) {
            if (engineOn) EngineLines(pos, eval)
            PlayMoveStrip(positions, moves, ply) { ply = it }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().background(C.bar).padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ToolButton("Set Up", "edit") {
                    board = pos.board.copyOf()
                    setupWhite = pos.white
                    mode = Mode.Edit
                }
                ToolButton("Flip", "flip") { flipped = !flipped }
                ToolButton(if (engineOn) "Engine On" else "Engine Off", "engine", on = engineOn) { engineOn = !engineOn }
                ToolButton("Best", "best", enabled = engineOn && best != null) { best?.let(::play) }
                ToolButton("Back", "back", enabled = ply > 0) { ply -= 1 }
                ToolButton("Forward", "forward", enabled = ply < moves.size) { ply += 1 }
            }
        } else {
            SetupPanel(
                tool = tool,
                white = setupWhite,
                problem = problem,
                onTool = { tool = it },
                onSide = { setupWhite = it },
                onClear = {
                    board = IntArray(64)
                    problem = null
                },
                onStart = {
                    board = Position.start().board.copyOf()
                    setupWhite = true
                    problem = null
                },
                onCancel = { mode = Mode.Play },
                onDone = {
                    val fen = setupFen(board, setupWhite)
                    problem = setupProblem(board, setupWhite, fen)
                    if (problem == null) {
                        positions = listOf(Position.fromFen(fen))
                        moves = emptyList()
                        ply = 0
                        mode = Mode.Play
                    }
                },
            )
        }
    }
}

/** Runs [block] once the engine is free (it may still be finishing a search). */
private fun Mutex.tryLockOrWait(block: () -> Unit) {
    kotlinx.coroutines.runBlocking { withLock { block() } }
}

/** The engine's two best moves with their evaluations. */
@Composable
private fun EngineLines(
    pos: Position,
    eval: PositionEval?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.panel)
            .animateContentSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (eval == null || eval.lines.isEmpty()) {
            Text(if (pos.legalMoves().isEmpty()) "Game over" else "Thinking…", style = body(14, color = C.faint))
        } else {
            for (line in eval.lines.take(2)) {
                val san = runCatching { pos.san(Move.fromUci(line.move)) }.getOrDefault(line.move)
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    val whiteAhead = line.score >= 0
                    Box(
                        Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (whiteAhead) C.bubble else C.evalBlack)
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(evalText(line), style = body(13, FontWeight.Bold, if (whiteAhead) C.bubbleText else C.text)) }
                    Spacer(Modifier.width(10.dp))
                    Text(san, style = body(15, FontWeight.Bold))
                }
            }
            Text("Depth ${eval.depth}", style = body(11, color = C.faint))
        }
    }
}

/** The moves played so far; tap one to go there. */
@Composable
private fun PlayMoveStrip(
    positions: List<Position>,
    moves: List<Move>,
    ply: Int,
    onPick: (Int) -> Unit,
) {
    val state = rememberLazyListState()
    LaunchedEffect(ply) { if (ply > 0) state.animateScrollToItem(maxOf(0, ply - 3)) }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().height(40.dp).background(C.bar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(moves) { i, m ->
            val before = positions[i]
            val label = (if (before.white) "${before.fullmove}. " else "") + before.san(m)
            val on = i + 1 == ply
            Text(
                label,
                style = body(15, FontWeight.Bold, if (on) C.text else C.dim),
                modifier =
                    Modifier
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (on) C.panel2 else Color.Transparent)
                        .clickable { onPick(i + 1) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/** The board while setting up: plain squares and pieces, tap to place or remove. */
@Composable
private fun SetupBoard(
    board: IntArray,
    white: Boolean,
    flipped: Boolean,
    modifier: Modifier,
    onTap: (Int) -> Unit,
) {
    val shown = remember(board.contentHashCode(), white) { Position.fromFen(setupFen(board, white)) }
    Box(modifier) {
        ChessBoard(
            shown,
            last = null,
            label = null,
            arrows = emptyList(),
            flipped = flipped,
            modifier = Modifier.fillMaxSize(),
            animate = false,
        )
        Box(
            Modifier.fillMaxSize().pointerInput(flipped) {
                detectTapGestures { off ->
                    val s = size.width / 8f
                    val x = (off.x / s).toInt().coerceIn(0, 7)
                    val y = (off.y / s).toInt().coerceIn(0, 7)
                    onTap(sq(if (flipped) 7 - x else x, if (flipped) y else 7 - y))
                }
            },
        )
    }
}

@Composable
private fun SetupPanel(
    tool: Int,
    white: Boolean,
    problem: String?,
    onTool: (Int) -> Unit,
    onSide: (Boolean) -> Unit,
    onClear: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val painters = piecePainters()
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Pick a piece, then tap squares. Tap a piece again to remove it.", style = body(13, color = C.faint))
        Spacer(Modifier.height(8.dp))
        for (side in listOf(1, -1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (kind in Piece.PAWN..Piece.KING) {
                    val v = kind * side
                    Canvas(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (tool == v) C.green else C.panel2)
                            .clickable { onTool(v) },
                    ) { painters[6 + v]?.run { draw(size) } }
                }
                if (side == 1) {
                    // The eraser: tap squares to empty them.
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (tool == 0) C.green else C.panel2)
                            .clickable { onTool(0) },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", style = body(18, FontWeight.Bold)) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(4.dp))
        SlidingSegments(listOf("White to move", "Black to move"), if (white) 0 else 1, track = C.panel, pill = C.panel2) { onSide(it == 0) }
        problem?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = body(13, FontWeight.Bold, C.error))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClear) { Text("Clear", style = body(15, FontWeight.Bold, C.dim)) }
            TextButton(onClick = onStart) { Text("Start", style = body(15, FontWeight.Bold, C.dim)) }
            TextButton(onClick = onCancel) { Text("Cancel", style = body(15, FontWeight.Bold, C.dim)) }
            Spacer(Modifier.weight(1f))
            GreenButton("Done", Modifier.width(110.dp), height = 46.dp, onClick = onDone)
        }
    }
}

/** A FEN for a set-up board; castling is allowed where king and rook still stand at home. */
internal fun setupFen(
    board: IntArray,
    white: Boolean,
): String {
    val rows =
        (7 downTo 0).joinToString("/") { r ->
            val sb = StringBuilder()
            var empty = 0
            for (f in 0..7) {
                val v = board[sq(f, r)]
                if (v == 0) {
                    empty++
                } else {
                    if (empty > 0) sb.append(empty).also { empty = 0 }
                    val c = Piece.letter(kotlin.math.abs(v))
                    sb.append(if (v > 0) c else c.lowercaseChar())
                }
            }
            if (empty > 0) sb.append(empty)
            sb.toString()
        }
    val k = Piece.KING
    val rk = Piece.ROOK
    var castle = ""
    if (board[sq(4, 0)] == k && board[sq(7, 0)] == rk) castle += "K"
    if (board[sq(4, 0)] == k && board[sq(0, 0)] == rk) castle += "Q"
    if (board[sq(4, 7)] == -k && board[sq(7, 7)] == -rk) castle += "k"
    if (board[sq(4, 7)] == -k && board[sq(0, 7)] == -rk) castle += "q"
    return "$rows ${if (white) "w" else "b"} ${castle.ifEmpty { "-" }} - 0 1"
}

/** Why a set-up position can't be played, or null when it can. */
internal fun setupProblem(
    board: IntArray,
    white: Boolean,
    fen: String,
): String? {
    if (board.count { it == Piece.KING } != 1 || board.count { it == -Piece.KING } != 1) return "Each side needs exactly one king."
    if ((0..7).any { f -> kotlin.math.abs(board[sq(f, 0)]) == Piece.PAWN || kotlin.math.abs(board[sq(f, 7)]) == Piece.PAWN }) {
        return "Pawns can't stand on the first or last rank."
    }
    val p = Position.fromFen(fen)
    if (p.attacked(p.kingSquare(!white), white)) {
        return "The side not to move is in check. Switch who is to move."
    }
    return null
}

/** A bottom-bar button with a line icon drawn in the same style as the review bar. */
@Composable
internal fun ToolButton(
    text: String,
    icon: String,
    on: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val col =
        when {
            !enabled -> Color(0xFF5E5C59)
            on -> C.green
            else -> C.dim
        }
    Column(
        Modifier
            .width(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val st = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val u = size.width / 24f

            fun o(
                x: Float,
                y: Float,
            ) = Offset(x * u, y * u)

            fun line(vararg pts: Pair<Float, Float>) {
                val p = Path()
                pts.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x * u, y * u) else p.lineTo(x * u, y * u) }
                drawPath(p, col, style = st)
            }
            when (icon) {
                "edit" -> {
                    line(4f to 20f, 4.5f to 15.5f, 15.5f to 4.5f, 19.5f to 8.5f, 8.5f to 19.5f, 4f to 20f)
                    line(13f to 7f, 17f to 11f)
                }
                "flip" -> {
                    line(8f to 3f, 8f to 21f)
                    line(4f to 7f, 8f to 3f, 12f to 7f)
                    line(16f to 21f, 16f to 3f)
                    line(12f to 17f, 16f to 21f, 20f to 17f)
                }
                "engine" -> {
                    drawRoundRect(
                        col,
                        o(6f, 6f),
                        Size(12f * u, 12f * u),
                        androidx.compose.ui.geometry
                            .CornerRadius(2f * u),
                        style = st,
                    )
                    for (k in listOf(9f, 12f, 15f)) {
                        line(k to 2.5f, k to 6f)
                        line(k to 18f, k to 21.5f)
                        line(2.5f to k, 6f to k)
                        line(18f to k, 21.5f to k)
                    }
                }
                "best" -> {
                    drawCircle(col, 6.5f * u, o(10.5f, 10.5f), style = st)
                    drawLine(col, o(15.3f, 15.3f), o(20f, 20f), st.width, StrokeCap.Round)
                }
                "back" -> line(15f to 5f, 8f to 12f, 15f to 19f)
                "hint" -> {
                    // A light bulb.
                    drawCircle(col, 6f * u, o(12f, 10f), style = st)
                    line(9.5f to 16f, 9.5f to 19f, 14.5f to 19f, 14.5f to 16f)
                    line(10.5f to 21.5f, 13.5f to 21.5f)
                }
                "solution" -> {
                    // An eye.
                    val p = Path()
                    p.moveTo(2.5f * u, 12f * u)
                    p.quadraticTo(12f * u, 3f * u, 21.5f * u, 12f * u)
                    p.quadraticTo(12f * u, 21f * u, 2.5f * u, 12f * u)
                    drawPath(p, col, style = st)
                    drawCircle(col, 3f * u, o(12f, 12f), style = st)
                }
                "options" -> {
                    for (y in listOf(6f, 12f, 18f)) {
                        drawCircle(col, 1.4f * u, o(5f, y))
                        line(9f to y, 20f to y)
                    }
                }
                "flag" -> {
                    line(6f to 21f, 6f to 3.5f)
                    val p = Path()
                    p.moveTo(6f * u, 4f * u)
                    p.quadraticTo(10f * u, 2f * u, 13f * u, 4.5f * u)
                    p.quadraticTo(16f * u, 7f * u, 19.5f * u, 5f * u)
                    p.lineTo(19.5f * u, 13f * u)
                    p.quadraticTo(16f * u, 15f * u, 13f * u, 12.5f * u)
                    p.quadraticTo(10f * u, 10f * u, 6f * u, 12f * u)
                    drawPath(p, col, style = st)
                }
                "undo" -> {
                    line(9f to 5f, 4f to 10f, 9f to 15f)
                    val p = Path()
                    p.moveTo(4.5f * u, 10f * u)
                    p.lineTo(14f * u, 10f * u)
                    p.quadraticTo(20f * u, 10f * u, 20f * u, 15.5f * u)
                    p.quadraticTo(20f * u, 20f * u, 14f * u, 20f * u)
                    drawPath(p, col, style = st)
                }
                "retry" -> {
                    // A circle going back round, with the arrowhead at the top pointing left.
                    drawArc(col, -90f, 290f, false, o(5f, 6f), Size(14f * u, 14f * u), style = st)
                    val p = Path()
                    p.moveTo(8f * u, 6f * u)
                    p.lineTo(12.8f * u, 2.2f * u)
                    p.lineTo(12.8f * u, 9.8f * u)
                    p.close()
                    drawPath(p, col)
                }
                else -> line(9f to 5f, 16f to 12f, 9f to 19f)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(text, style = body(11, FontWeight.Medium, col), maxLines = 1)
    }
}
