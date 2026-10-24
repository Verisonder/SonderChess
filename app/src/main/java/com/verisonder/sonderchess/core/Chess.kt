package com.verisonder.sonderchess.core

/**
 * A small, complete chess core: positions, legal moves, SAN, FEN. Plain Kotlin with no
 * Android imports, so it is unit-tested on the JVM.
 *
 * Squares are 0..63 with a1 = 0, b1 = 1 … h8 = 63. Pieces are ints: 0 empty, 1..6 white
 * P N B R Q K, -1..-6 black.
 */
object Piece {
    const val NONE = 0
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6

    /** Rough material value in pawns, used for sacrifice detection. */
    fun value(type: Int) =
        when (type) {
            PAWN -> 1
            KNIGHT, BISHOP -> 3
            ROOK -> 5
            QUEEN -> 9
            KING -> 100
            else -> 0
        }

    fun letter(type: Int) = " PNBRQK"[type]

    fun name(type: Int) =
        when (type) {
            PAWN -> "pawn"
            KNIGHT -> "knight"
            BISHOP -> "bishop"
            ROOK -> "rook"
            QUEEN -> "queen"
            KING -> "king"
            else -> ""
        }
}

fun sq(
    file: Int,
    rank: Int,
) = rank * 8 + file

fun fileOf(s: Int) = s and 7

fun rankOf(s: Int) = s shr 3

fun sqName(s: Int) = "${'a' + fileOf(s)}${'1' + rankOf(s)}"

fun sqParse(t: String): Int = sq(t[0] - 'a', t[1] - '1')

data class Move(
    val from: Int,
    val to: Int,
    val promo: Int = Piece.NONE,
) {
    val uci: String get() = sqName(from) + sqName(to) + if (promo != Piece.NONE) "nbrq"[promo - 2].toString() else ""

    companion object {
        fun fromUci(u: String): Move {
            val promo =
                if (u.length > 4) {
                    when (u[4]) {
                        'n' -> Piece.KNIGHT
                        'b' -> Piece.BISHOP
                        'r' -> Piece.ROOK
                        else -> Piece.QUEEN
                    }
                } else {
                    Piece.NONE
                }
            return Move(sqParse(u.substring(0, 2)), sqParse(u.substring(2, 4)), promo)
        }
    }
}

class Position private constructor(
    val board: IntArray,
    /** true = white to move. */
    val white: Boolean,
    /** Bits: 1 white O-O, 2 white O-O-O, 4 black O-O, 8 black O-O-O. */
    val castling: Int,
    /** En-passant target square or -1. */
    val ep: Int,
    val halfmove: Int,
    val fullmove: Int,
) {
    operator fun get(s: Int) = board[s]

    fun kingSquare(whiteKing: Boolean): Int {
        val k = if (whiteKing) Piece.KING else -Piece.KING
        for (s in 0 until 64) if (board[s] == k) return s
        return -1
    }

    /** Is [s] attacked by the side [byWhite]? */
    fun attacked(
        s: Int,
        byWhite: Boolean,
    ): Boolean = attackers(s, byWhite).isNotEmpty()

    /** Squares of [byWhite]'s pieces that attack [s]. */
    fun attackers(
        s: Int,
        byWhite: Boolean,
    ): List<Int> {
        val out = ArrayList<Int>(4)
        val sign = if (byWhite) 1 else -1
        val f = fileOf(s)
        val r = rankOf(s)
        // Pawns: a white pawn attacks upward, so it sits one rank below.
        val pr = if (byWhite) r - 1 else r + 1
        if (pr in 0..7) {
            for (df in intArrayOf(-1, 1)) {
                val pf = f + df
                if (pf in 0..7 && board[sq(pf, pr)] == sign * Piece.PAWN) out.add(sq(pf, pr))
            }
        }
        for ((df, dr) in KNIGHT_D) {
            val nf = f + df
            val nr = r + dr
            if (nf in 0..7 && nr in 0..7 && board[sq(nf, nr)] == sign * Piece.KNIGHT) out.add(sq(nf, nr))
        }
        for ((df, dr) in KING_D) {
            val nf = f + df
            val nr = r + dr
            if (nf in 0..7 && nr in 0..7 && board[sq(nf, nr)] == sign * Piece.KING) out.add(sq(nf, nr))
        }
        for ((df, dr) in ROOK_D) slideAttacker(f, r, df, dr, sign, Piece.ROOK)?.let { out.add(it) }
        for ((df, dr) in BISHOP_D) slideAttacker(f, r, df, dr, sign, Piece.BISHOP)?.let { out.add(it) }
        return out
    }

    private fun slideAttacker(
        f: Int,
        r: Int,
        df: Int,
        dr: Int,
        sign: Int,
        kind: Int,
    ): Int? {
        var nf = f + df
        var nr = r + dr
        while (nf in 0..7 && nr in 0..7) {
            val p = board[sq(nf, nr)]
            if (p != 0) {
                return if (p == sign * kind || p == sign * Piece.QUEEN) sq(nf, nr) else null
            }
            nf += df
            nr += dr
        }
        return null
    }

    fun inCheck(): Boolean {
        val k = kingSquare(white)
        return k >= 0 && attacked(k, !white)
    }

    /** All pseudo-legal moves, then filtered to legal ones. */
    fun legalMoves(): List<Move> =
        pseudoMoves().filter { m ->
            val n = play(m)
            val k = n.kingSquare(white)
            k >= 0 && !n.attacked(k, !white)
        }

    fun isCheckmate() = inCheck() && legalMoves().isEmpty()

    fun isStalemate() = !inCheck() && legalMoves().isEmpty()

    private fun pseudoMoves(): List<Move> {
        val out = ArrayList<Move>(48)
        val sign = if (white) 1 else -1
        for (s in 0 until 64) {
            val p = board[s] * sign
            if (p <= 0) continue
            val f = fileOf(s)
            val r = rankOf(s)
            when (p) {
                Piece.PAWN -> {
                    val dir = if (white) 1 else -1
                    val start = if (white) 1 else 6
                    val last = if (white) 7 else 0
                    val one = r + dir
                    if (one in 0..7 && board[sq(f, one)] == 0) {
                        addPawn(out, s, sq(f, one), one == last)
                        val two = r + 2 * dir
                        if (r == start && board[sq(f, two)] == 0) out.add(Move(s, sq(f, two)))
                    }
                    for (df in intArrayOf(-1, 1)) {
                        val nf = f + df
                        if (nf !in 0..7 || one !in 0..7) continue
                        val t = sq(nf, one)
                        if (board[t] * sign < 0 || t == ep) addPawn(out, s, t, one == last)
                    }
                }
                Piece.KNIGHT -> steps(out, s, KNIGHT_D, sign)
                Piece.KING -> {
                    steps(out, s, KING_D, sign)
                    castles(out, s)
                }
                Piece.BISHOP -> slides(out, s, BISHOP_D, sign)
                Piece.ROOK -> slides(out, s, ROOK_D, sign)
                Piece.QUEEN -> {
                    slides(out, s, BISHOP_D, sign)
                    slides(out, s, ROOK_D, sign)
                }
            }
        }
        return out
    }

    private fun addPawn(
        out: MutableList<Move>,
        from: Int,
        to: Int,
        promo: Boolean,
    ) {
        if (promo) {
            for (k in intArrayOf(Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT)) out.add(Move(from, to, k))
        } else {
            out.add(Move(from, to))
        }
    }

    private fun steps(
        out: MutableList<Move>,
        s: Int,
        d: Array<IntArray>,
        sign: Int,
    ) {
        val f = fileOf(s)
        val r = rankOf(s)
        for ((df, dr) in d) {
            val nf = f + df
            val nr = r + dr
            if (nf in 0..7 && nr in 0..7 && board[sq(nf, nr)] * sign <= 0) out.add(Move(s, sq(nf, nr)))
        }
    }

    private fun slides(
        out: MutableList<Move>,
        s: Int,
        d: Array<IntArray>,
        sign: Int,
    ) {
        val f = fileOf(s)
        val r = rankOf(s)
        for ((df, dr) in d) {
            var nf = f + df
            var nr = r + dr
            while (nf in 0..7 && nr in 0..7) {
                val p = board[sq(nf, nr)]
                if (p * sign > 0) break
                out.add(Move(s, sq(nf, nr)))
                if (p != 0) break
                nf += df
                nr += dr
            }
        }
    }

    private fun castles(
        out: MutableList<Move>,
        s: Int,
    ) {
        val home = if (white) 4 else 60
        if (s != home || inCheck()) return
        val (kBit, qBit) = if (white) 1 to 2 else 4 to 8
        val rook = if (white) Piece.ROOK else -Piece.ROOK
        if (castling and kBit != 0 &&
            board[home + 3] == rook &&
            board[home + 1] == 0 &&
            board[home + 2] == 0 &&
            !attacked(home + 1, !white) &&
            !attacked(home + 2, !white)
        ) {
            out.add(Move(home, home + 2))
        }
        if (castling and qBit != 0 &&
            board[home - 4] == rook &&
            board[home - 1] == 0 &&
            board[home - 2] == 0 &&
            board[home - 3] == 0 &&
            !attacked(home - 1, !white) &&
            !attacked(home - 2, !white)
        ) {
            out.add(Move(home, home - 2))
        }
    }

    fun isCapture(m: Move): Boolean = board[m.to] != 0 || (Math.abs(board[m.from]) == Piece.PAWN && m.to == ep)

    fun isCastle(m: Move): Boolean = Math.abs(board[m.from]) == Piece.KING && Math.abs(m.to - m.from) == 2

    /** Plays [m] (assumed legal) and returns the new position. */
    fun play(m: Move): Position {
        val b = board.copyOf()
        val p = b[m.from]
        val type = Math.abs(p)
        val captured = b[m.to]
        b[m.to] = if (m.promo != Piece.NONE) (if (white) m.promo else -m.promo) else p
        b[m.from] = 0
        if (type == Piece.PAWN && m.to == ep) b[m.to + if (white) -8 else 8] = 0
        if (type == Piece.KING && Math.abs(m.to - m.from) == 2) {
            if (m.to > m.from) {
                b[m.from + 1] = b[m.from + 3]
                b[m.from + 3] = 0
            } else {
                b[m.from - 1] = b[m.from - 4]
                b[m.from - 4] = 0
            }
        }
        var c = castling
        for (x in intArrayOf(m.from, m.to)) {
            c = c and
                when (x) {
                    4 -> 3.inv()
                    0 -> 2.inv()
                    7 -> 1.inv()
                    60 -> 12.inv()
                    56 -> 8.inv()
                    63 -> 4.inv()
                    else -> 15
                }
        }
        val newEp = if (type == Piece.PAWN && Math.abs(m.to - m.from) == 16) (m.from + m.to) / 2 else -1
        val half = if (type == Piece.PAWN || captured != 0) 0 else halfmove + 1
        return Position(b, !white, c, newEp, half, if (white) fullmove else fullmove + 1)
    }

    /** Standard algebraic notation for a legal move. */
    fun san(m: Move): String {
        val p = Math.abs(board[m.from])
        val sb = StringBuilder()
        if (isCastle(m)) {
            sb.append(if (m.to > m.from) "O-O" else "O-O-O")
        } else {
            val cap = isCapture(m)
            if (p == Piece.PAWN) {
                if (cap) sb.append('a' + fileOf(m.from)).append('x')
                sb.append(sqName(m.to))
                if (m.promo != Piece.NONE) sb.append('=').append(Piece.letter(m.promo))
            } else {
                sb.append(Piece.letter(p))
                val others = legalMoves().filter { it.to == m.to && it.from != m.from && Math.abs(board[it.from]) == p }
                if (others.isNotEmpty()) {
                    val sameFile = others.any { fileOf(it.from) == fileOf(m.from) }
                    val sameRank = others.any { rankOf(it.from) == rankOf(m.from) }
                    when {
                        !sameFile -> sb.append('a' + fileOf(m.from))
                        !sameRank -> sb.append('1' + rankOf(m.from))
                        else -> sb.append(sqName(m.from))
                    }
                }
                if (cap) sb.append('x')
                sb.append(sqName(m.to))
            }
        }
        val n = play(m)
        if (n.inCheck()) sb.append(if (n.legalMoves().isEmpty()) '#' else '+')
        return sb.toString()
    }

    /** Finds the legal move written as [text] in SAN (tolerant of +, #, !, ?, and "0-0"). */
    fun parseSan(text: String): Move? {
        val clean = text.trim().replace("0", "O").trimEnd('+', '#', '!', '?')
        return legalMoves().firstOrNull { san(it).trimEnd('+', '#') == clean }
    }

    fun fen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0..7) {
                val p = board[sq(f, r)]
                if (p == 0) {
                    empty++
                } else {
                    if (empty > 0) sb.append(empty).also { empty = 0 }
                    val ch = Piece.letter(Math.abs(p))
                    sb.append(if (p > 0) ch else ch.lowercaseChar())
                }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(if (white) " w " else " b ")
        val cs =
            buildString {
                if (castling and 1 != 0) append('K')
                if (castling and 2 != 0) append('Q')
                if (castling and 4 != 0) append('k')
                if (castling and 8 != 0) append('q')
            }
        sb.append(cs.ifEmpty { "-" })
        sb.append(' ').append(if (ep >= 0) sqName(ep) else "-")
        sb
            .append(' ')
            .append(halfmove)
            .append(' ')
            .append(fullmove)
        return sb.toString()
    }

    /** Board, side and castling: the key for opening-book lookups. */
    fun bookKey(): String = fen().split(' ').take(3).joinToString(" ")

    /** The same position with the other side to move (no en passant). For threat checks. */
    fun passTurn(): Position = Position(board, !white, castling, -1, halfmove, fullmove)

    /** Legal captures landing on [s]. */
    fun capturesOn(s: Int): List<Move> {
        if (board[s] == 0) return emptyList()
        return legalMoves().filter { it.to == s }
    }

    /**
     * Static exchange on [s] for the side to move: what it wins by capturing there and letting
     * both sides keep recapturing with their cheapest piece (either side may stop). 0 when
     * capturing doesn't pay.
     */
    fun see(s: Int): Int {
        val caps = capturesOn(s)
        if (caps.isEmpty()) return 0
        val m =
            caps.minBy {
                val t = Math.abs(board[it.from])
                if (t == Piece.KING) 100 else Piece.value(t)
            }
        val gain = Piece.value(Math.abs(board[s])) - play(m).see(s)
        return if (gain > 0) gain else 0
    }

    /**
     * The most the side to move could win by capturing one of [owner]'s pieces (pawns and
     * king aside) right now.
     */
    fun hanging(owner: Boolean): Int {
        if (white == owner) return 0
        var best = 0
        for (s in 0 until 64) {
            val p = board[s]
            if (p == 0 || (p > 0) != owner) continue
            val t = Math.abs(p)
            if (t == Piece.PAWN || t == Piece.KING) continue
            best = maxOf(best, see(s))
        }
        return best
    }

    /** Total material of one side in pawns (king excluded). */
    fun material(ofWhite: Boolean): Int {
        var t = 0
        for (p in board) if (p != 0 && (p > 0) == ofWhite && Math.abs(p) != Piece.KING) t += Piece.value(Math.abs(p))
        return t
    }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        private val KNIGHT_D =
            arrayOf(
                intArrayOf(1, 2),
                intArrayOf(2, 1),
                intArrayOf(2, -1),
                intArrayOf(1, -2),
                intArrayOf(-1, -2),
                intArrayOf(-2, -1),
                intArrayOf(-2, 1),
                intArrayOf(-1, 2),
            )
        private val KING_D =
            arrayOf(
                intArrayOf(1, 0),
                intArrayOf(1, 1),
                intArrayOf(0, 1),
                intArrayOf(-1, 1),
                intArrayOf(-1, 0),
                intArrayOf(-1, -1),
                intArrayOf(0, -1),
                intArrayOf(1, -1),
            )
        private val ROOK_D = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
        private val BISHOP_D = arrayOf(intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1))

        fun start() = fromFen(START_FEN)

        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            val b = IntArray(64)
            var r = 7
            var f = 0
            for (ch in parts[0]) {
                when {
                    ch == '/' -> {
                        r--
                        f = 0
                    }
                    ch.isDigit() -> f += ch - '0'
                    else -> {
                        val type = " PNBRQK".indexOf(ch.uppercaseChar())
                        require(type > 0) { "Bad FEN piece $ch" }
                        b[sq(f, r)] = if (ch.isUpperCase()) type else -type
                        f++
                    }
                }
            }
            val white = parts.getOrElse(1) { "w" } == "w"
            var c = 0
            parts.getOrElse(2) { "-" }.forEach {
                c = c or
                    when (it) {
                        'K' -> 1
                        'Q' -> 2
                        'k' -> 4
                        'q' -> 8
                        else -> 0
                    }
            }
            val epTxt = parts.getOrElse(3) { "-" }
            val ep = if (epTxt == "-") -1 else sqParse(epTxt)
            return Position(
                b,
                white,
                c,
                ep,
                parts.getOrElse(4) { "0" }.toIntOrNull() ?: 0,
                parts.getOrElse(5) { "1" }.toIntOrNull() ?: 1,
            )
        }
    }
}
