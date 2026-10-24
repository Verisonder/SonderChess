package com.verisonder.sonderchess.core

/** A finished game: who played, from where, and every move. */
data class Game(
    val headers: Map<String, String>,
    val startFen: String,
    val moves: List<Move>,
) {
    val white: String get() = headers["White"] ?: "White"
    val black: String get() = headers["Black"] ?: "Black"
    val result: String get() = headers["Result"] ?: "*"

    /** Every position, from the start to after the last move (moves.size + 1 entries). */
    fun positions(): List<Position> {
        val out = ArrayList<Position>(moves.size + 1)
        var p = Position.fromFen(startFen)
        out.add(p)
        for (m in moves) {
            p = p.play(m)
            out.add(p)
        }
        return out
    }

    fun sans(): List<String> {
        val ps = positions()
        return moves.mapIndexed { i, m -> ps[i].san(m) }
    }

    fun toPgn(): String {
        val sb = StringBuilder()
        for ((k, v) in headers) {
            sb
                .append("[")
                .append(k)
                .append(" \"")
                .append(v.replace("\"", "'"))
                .append("\"]\n")
        }
        sb.append('\n')
        val ps = positions()
        val sans = sans()
        for (i in moves.indices) {
            val p = ps[i]
            if (p.white) {
                sb.append(p.fullmove).append(". ")
            } else if (i == 0) {
                sb.append(p.fullmove).append("... ")
            }
            sb.append(sans[i]).append(' ')
        }
        sb.append(result)
        return sb.toString()
    }
}

/** Chess.com's compact "TCN" move list: two characters per move. */
object Tcn {
    private const val T = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!?{~}(^)[_]@#\$,./&-*++="

    fun decode(
        moveList: String,
        startFen: String = Position.START_FEN,
    ): List<Move> {
        var p = Position.fromFen(startFen)
        val out = ArrayList<Move>(moveList.length / 2)
        var i = 0
        while (i + 1 < moveList.length) {
            val from = T.indexOf(moveList[i])
            var to = T.indexOf(moveList[i + 1])
            require(from in 0..63 && to >= 0) { "Bad move code at $i" }
            var promo = Piece.NONE
            if (to > 63) {
                promo =
                    when ((to - 64) / 3) {
                        0 -> Piece.QUEEN
                        1 -> Piece.KNIGHT
                        2 -> Piece.ROOK
                        else -> Piece.BISHOP
                    }
                to = from + (if (from < 16) -8 else 8) + (to - 1) % 3 - 1
            }
            // Castling is written king-takes-own-rook (e1h1); turn it into the king's real step.
            val piece = p[from]
            if (Math.abs(piece) == Piece.KING && p[to] == (if (piece > 0) Piece.ROOK else -Piece.ROOK)) {
                to = if (to > from) from + 2 else from - 2
            }
            val m = Move(from, to, promo)
            require(p.legalMoves().contains(m)) { "Illegal move ${m.uci} at ply ${out.size + 1}" }
            out.add(m)
            p = p.play(m)
            i += 2
        }
        return out
    }
}

object Pgn {
    private val header = Regex("""\[(\w+)\s+"((?:[^"\\]|\\.)*)"\]""")

    /** Reads the first game in [text]. Throws IllegalArgumentException when it can't. */
    fun parse(text: String): Game {
        val headers = LinkedHashMap<String, String>()
        header.findAll(text).forEach { headers[it.groupValues[1]] = it.groupValues[2] }
        var body = text.replace(header, " ")
        body = stripNested(body.replace(Regex("""\{[^}]*\}"""), " ").replace(Regex(""";[^\n]*"""), " "))
        val startFen = headers["FEN"]?.takeIf { it.isNotBlank() } ?: Position.START_FEN
        var p = Position.fromFen(startFen)
        val moves = ArrayList<Move>()
        for (raw in body.split(Regex("\\s+"))) {
            var tok = raw.trim()
            if (tok.isEmpty()) continue
            if (tok in setOf("1-0", "0-1", "1/2-1/2", "*")) break
            if (tok.startsWith("$")) continue
            tok = tok.replace(Regex("""^\d+\.+"""), "")
            if (tok.isEmpty()) continue
            val m = p.parseSan(tok) ?: throw IllegalArgumentException("Can't read move \"$tok\" (move ${p.fullmove})")
            moves.add(m)
            p = p.play(m)
        }
        require(moves.isNotEmpty()) { "No moves found" }
        return Game(headers, startFen, moves)
    }

    /** Drops (variations), which may nest. */
    private fun stripNested(s: String): String {
        val sb = StringBuilder()
        var depth = 0
        for (ch in s) {
            when {
                ch == '(' -> depth++
                ch == ')' -> if (depth > 0) depth--
                depth == 0 -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun looksLikePgn(text: String) =
        text.contains(Regex("""\[\s*(Event|White|Black)\s+"""")) ||
            text.contains(Regex("""(^|\s)1\.\s*[a-hNBRQKO]"""))
}
