package com.verisonder.sonderchess.core

/**
 * The coach's words for a move: a headline ("Nf3 is excellent") and one short line about
 * what the move did. [mine] is true when the move was played by the person reviewing.
 */
object Explain {
    data class Words(
        val headline: String,
        val line: String,
    )

    fun move(
        review: Review,
        index: Int,
        mine: Boolean,
    ): Words {
        val mr = review.moves[index]
        val ps = review.game.positions()
        val before = ps[index]
        val after = ps[index + 1]
        val m = review.game.moves[index]
        val headline = "${mr.san} ${mr.label.phrase}"
        val you = if (mine) "You" else "Your opponent"
        val your = if (mine) "your" else "their"
        val piece = Piece.name(Math.abs(before[m.from]))
        val captured =
            when {
                before[m.to] != 0 -> Piece.name(Math.abs(before[m.to]))
                before.isCapture(m) -> "pawn"
                else -> null
            }
        val line =
            when (mr.label) {
                Label.Book -> review.opening?.let { "A known opening move. This is the $it." } ?: "A well-known opening move."
                Label.Brilliant ->
                    if (captured != null) {
                        "$you gave up material on purpose, and it works. A real sacrifice."
                    } else {
                        "$you left a piece hanging on purpose. The engine agrees: it holds."
                    }
                Label.Great -> "The only move that keeps the edge. Everything else is worse."
                Label.Best, Label.Excellent, Label.Good -> goodLine(mr, before, after, m, you, your, piece, captured)
                Label.Inaccuracy, Label.Mistake, Label.Blunder -> badLine(mr, after, you, your)
                Label.Miss -> "${mr.bestSan ?: "Another move"} would have punished the last move. That chance is gone now."
            }
        return Words(headline, line)
    }

    private fun goodLine(
        mr: MoveReview,
        before: Position,
        after: Position,
        m: Move,
        you: String,
        your: String,
        piece: String,
        captured: String?,
    ): String {
        val close = if (mr.label == Label.Best) "" else " Close to the best move."
        return when {
            after.isCheckmate() -> "Checkmate. Game over."
            before.isCastle(m) -> "Castling: the king is safer and a rook joins the game.$close"
            m.promo != Piece.NONE -> "The pawn makes it to the end and becomes a ${Piece.name(m.promo)}.$close"
            captured != null && after.inCheck() -> "$you take the $captured, with check.$close"
            captured != null -> "$you take the $captured.$close"
            after.inCheck() -> "Check! The king has to deal with it.$close"
            isDevelopment(before, m) -> "Developing the $piece toward the center.$close"
            Math.abs(before[m.from]) == Piece.PAWN && fileOf(m.to) in 2..5 && before.fullmove <= 12 ->
                "A pawn grabs space in the center.$close"
            mr.label == Label.Best -> "The engine's top choice here."
            else -> "A solid move that keeps $your position healthy."
        }
    }

    private fun badLine(
        mr: MoveReview,
        after: Position,
        you: String,
        your: String,
    ): String {
        val better = mr.bestSan?.let { "$it was better." } ?: "There was a better move."
        val hang = if (after.isCheckmate()) 0 else after.hanging(mr.white)
        return when {
            mr.evalAfter.score.let { (if (mr.white) -it else it) >= 9000 } -> "This walks into a forced mate. $better"
            hang >= 3 -> "It leaves $your ${hangingName(after, mr.white)} open to capture. $better"
            mr.label == Label.Inaccuracy -> "Not terrible, but it lets the advantage slip a little. $better"
            else -> "$you gave away a big part of the advantage. $better"
        }
    }

    private fun hangingName(
        p: Position,
        owner: Boolean,
    ): String {
        var best = 0
        var name = "piece"
        for (s in 0 until 64) {
            val v = p[s]
            if (v == 0 || (v > 0) != owner) continue
            val t = Math.abs(v)
            if (t == Piece.PAWN || t == Piece.KING) continue
            val g = p.see(s)
            if (g > best) {
                best = g
                name = "${Piece.name(t)} on ${sqName(s)}"
            }
        }
        return name
    }

    private fun isDevelopment(
        before: Position,
        m: Move,
    ): Boolean {
        val t = Math.abs(before[m.from])
        if (t != Piece.KNIGHT && t != Piece.BISHOP) return false
        val home = if (before.white) 0 else 7
        return rankOf(m.from) == home && before.fullmove <= 15
    }

    /** One line for the summary card. */
    fun summary(
        review: Review,
        meWhite: Boolean,
    ): String {
        val mine = review.moves.filter { it.white == meWhite }
        val brilliant = mine.count { it.label == Label.Brilliant }
        val blunders = mine.count { it.label == Label.Blunder }
        val acc = if (meWhite) review.accuracyWhite else review.accuracyBlack
        val result = review.game.result
        val won = (result == "1-0" && meWhite) || (result == "0-1" && !meWhite)
        return when {
            brilliant > 0 && won -> "A brilliant move and a win. You were cooking! Let's review."
            brilliant > 0 -> "You found a brilliant move in this one. Let's take a look."
            won && blunders == 0 && acc >= 80 -> "Clean, accurate and a win. Great game!"
            won && blunders > 0 -> "You won, but there were a few slips on the way. Let's find them."
            won -> "A win! Let's see where it could have been even smoother."
            result == "1/2-1/2" -> "A draw. Let's see where the winning chances were."
            blunders > 0 -> "Bouncing back from a worse position is hard. Let's see where it turned."
            else -> "A close fight. Let's see what decided it."
        }
    }
}
