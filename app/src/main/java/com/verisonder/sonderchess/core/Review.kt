package com.verisonder.sonderchess.core

import kotlin.math.exp

/** Move labels, best to worst, with their badge colour (ARGB) and symbol. */
enum class Label(
    val title: String,
    val color: Long,
    val symbol: String,
) {
    Brilliant("Brilliant", 0xFF1BACA6, "!!"),
    Great("Great", 0xFF5C8BB0, "!"),
    Best("Best", 0xFF81B64C, "★"),
    Excellent("Excellent", 0xFF96BC4B, "✓✓"),
    Good("Good", 0xFF95B776, "✓"),
    Book("Book", 0xFFA88865, "B"),
    Inaccuracy("Inaccuracy", 0xFFF7C631, "?!"),
    Mistake("Mistake", 0xFFFFA459, "?"),
    Miss("Miss", 0xFFFF7769, "✕"),
    Blunder("Blunder", 0xFFFA412D, "??"),
    ;

    /** How it reads in a sentence: "is brilliant", "is a mistake". */
    val phrase: String get() =
        when (this) {
            Book -> "is a book move"
            Inaccuracy -> "is an inaccuracy"
            Mistake -> "is a mistake"
            Miss -> "is a miss"
            Blunder -> "is a blunder"
            else -> "is ${title.lowercase()}"
        }

    val good: Boolean get() = ordinal <= Book.ordinal
}

/** One engine line: first move (UCI) and score from White's side. */
data class EngineLine(
    val move: String,
    val cp: Int? = null,
    val mate: Int? = null,
) {
    /** Centipawns, mates folded into ±10000 (sooner mates score higher). */
    val score: Int get() =
        when {
            mate != null && mate > 0 -> 10000 - mate * 10
            mate != null -> -10000 - mate * 10
            else -> cp ?: 0
        }
}

/** The engine's view of one position: best line first. Empty when the game is over. */
data class PositionEval(
    val lines: List<EngineLine>,
    val depth: Int = 0,
)

/**
 * The next move worth stopping on after [ply]: a brilliant, great or bad move, the reply
 * to one, or the first move out of the opening book. The last move if nothing else is left.
 */
fun nextKeyMove(
    moves: List<MoveReview>,
    ply: Int,
): Int {
    fun key(l: Label) = l in setOf(Label.Brilliant, Label.Great, Label.Inaccuracy, Label.Mistake, Label.Miss, Label.Blunder)
    for (i in (ply + 1)..moves.lastIndex) {
        if (i == 0 || key(moves[i].label)) return i
        val prev = moves[i - 1].label
        if (key(prev)) return i
        if (prev == Label.Book && moves[i].label != Label.Book) return i
    }
    return moves.lastIndex
}

data class MoveReview(
    val ply: Int,
    val white: Boolean,
    val san: String,
    val uci: String,
    val label: Label,
    /** Engine's best move before this one was played. */
    val bestUci: String?,
    val bestSan: String?,
    /** Evaluation after the move, White's side. */
    val evalAfter: EngineLine,
    /** Expected-points loss, 0..1. */
    val loss: Double,
    val winBefore: Double,
    val winAfter: Double,
    /** Per-move accuracy 0..100. */
    val accuracy: Double,
    /** The mover left a piece en prise worth this much (sacrifices), else 0. */
    val sacrificed: Int,
    /** For book moves: the opening's name at this point. */
    val opening: String? = null,
)

data class Review(
    val game: Game,
    val moves: List<MoveReview>,
    /** White-side evaluation of every position (moves.size + 1). */
    val evals: List<EngineLine>,
    val accuracyWhite: Double,
    val accuracyBlack: Double,
    val opening: String?,
    val eco: String?,
) {
    fun count(
        white: Boolean,
        label: Label,
    ) = moves.count { it.white == white && it.label == label }

    /** A rough game rating from accuracy (shown as an estimate). */
    fun rating(white: Boolean): Int {
        val a = if (white) accuracyWhite else accuracyBlack
        return (1450.0 + (a - 78.3) * 96.0).toInt().coerceIn(100, 3200) / 50 * 50
    }
}

/** Opening book: board key → (ECO, name). */
class OpeningBook(
    private val map: Map<String, Pair<String, String>>,
) {
    fun lookup(p: Position): Pair<String, String>? = map[p.bookKey()]

    companion object {
        fun parse(tsv: String): OpeningBook {
            val m = HashMap<String, Pair<String, String>>(9000)
            for (line in tsv.lineSequence()) {
                val parts = line.split('\t')
                if (parts.size >= 3) m[parts[0]] = parts[1] to parts[2]
            }
            return OpeningBook(m)
        }

        val EMPTY = OpeningBook(emptyMap())
    }
}

/**
 * Turns engine evaluations into labels and accuracy. The curve and thresholds were tuned on
 * real games against Chess.com's own review, so the numbers land close to what players expect.
 */
object Classifier {
    /** Logistic slope: centipawns → expected points. */
    const val K = 0.0025

    /** Per-move accuracy steepness (per percentage point of expected points lost). */
    const val A = 0.24

    fun win(cp: Int): Double = 1.0 / (1.0 + exp(-K * cp.coerceIn(-2000, 2000)))

    fun moveAccuracy(loss: Double): Double = (103.1668 * exp(-A * loss * 100.0) - 3.1669).coerceIn(0.0, 100.0)

    /** White-side score of a position, including finished ones. */
    private fun scoreOf(
        p: Position,
        e: PositionEval,
    ): EngineLine =
        when {
            e.lines.isNotEmpty() -> e.lines[0]
            // The side to move is mated.
            p.isCheckmate() -> EngineLine("", cp = if (p.white) -10000 else 10000)
            else -> EngineLine("", cp = 0)
        }

    fun review(
        game: Game,
        evals: List<PositionEval>,
        book: OpeningBook = OpeningBook.EMPTY,
    ): Review {
        val ps = game.positions()
        require(evals.size == ps.size) { "Need one evaluation per position" }
        val scores = ps.indices.map { scoreOf(ps[it], evals[it]) }
        val out = ArrayList<MoveReview>(game.moves.size)
        var inBook = true
        var opening: Pair<String, String>? = null
        for (i in game.moves.indices) {
            val before = ps[i]
            val after = ps[i + 1]
            val m = game.moves[i]
            val me = before.white
            val sg = if (me) 1 else -1
            val wb = win(sg * scores[i].score)
            val wa = win(sg * scores[i + 1].score)
            val loss = (wb - wa).coerceAtLeast(0.0)
            val lines = evals[i].lines
            val bestUci = lines.firstOrNull()?.move
            val bestMove = bestUci?.let { runCatching { Move.fromUci(it) }.getOrNull() }
            val bestSan = bestMove?.let { bm -> if (before.legalMoves().contains(bm)) before.san(bm) else null }
            val second = lines.getOrNull(1)?.let { win(sg * it.score) }

            val bookHit = if (inBook) book.lookup(after) else null
            if (bookHit != null) opening = bookHit else inBook = false

            // Sacrifice: after the move a piece can be won, more than what the move took, and
            // the danger is new (made by this move, or by the opponent's last move and ignored).
            val took = if (before.isCapture(m)) Piece.value(Math.abs(before[m.to]).let { if (it == 0) Piece.PAWN else it }) else 0
            val hangAfter = if (after.isCheckmate()) 0 else after.hanging(me)
            val hangBefore = before.passTurn().hanging(me)
            val hangPrev = if (i >= 1) ps[i - 1].hanging(me) else 0
            val sac = hangAfter > took && hangAfter >= 2 && (hangAfter > hangBefore || hangAfter > hangPrev)
            val matBefore = before.material(me) - before.material(!me)

            var label =
                when {
                    bookHit != null -> Label.Book
                    m.uci == bestUci || after.isCheckmate() -> Label.Best
                    loss <= 0.02 -> Label.Excellent
                    loss <= 0.05 -> Label.Good
                    loss <= 0.10 -> Label.Inaccuracy
                    loss <= 0.20 -> Label.Mistake
                    else -> Label.Blunder
                }
            if (label != Label.Book) {
                if (loss <= 0.05 && sac && wa >= 0.5 && wb < 0.90) {
                    label = Label.Brilliant
                } else if (label == Label.Best &&
                    second != null &&
                    wb - second >= 0.10 &&
                    wb < 0.90 &&
                    wa > 0.4 &&
                    !(before.isCapture(m) && matBefore < 0)
                ) {
                    label = Label.Great
                } else if (loss >= 0.10 && i >= 1) {
                    // The opponent just handed over a chance and this move gave it back.
                    val prev = out.last()
                    val winBeforeGift = 1.0 - prev.winBefore
                    if (prev.loss >= 0.10 && Math.abs(wa - winBeforeGift) <= 0.05) label = Label.Miss
                }
            }
            out.add(
                MoveReview(
                    ply = i,
                    white = me,
                    san = before.san(m),
                    uci = m.uci,
                    label = label,
                    bestUci = bestUci,
                    bestSan = bestSan,
                    evalAfter = scores[i + 1],
                    loss = loss,
                    winBefore = wb,
                    winAfter = wa,
                    accuracy = moveAccuracy(loss),
                    sacrificed = if (label == Label.Brilliant) hangAfter else 0,
                    opening = bookHit?.second,
                ),
            )
        }

        fun acc(white: Boolean): Double {
            val list = out.filter { it.white == white }.map { it.accuracy }
            return if (list.isEmpty()) 100.0 else list.average()
        }
        return Review(game, out, scores, acc(true), acc(false), opening?.second, opening?.first)
    }
}
