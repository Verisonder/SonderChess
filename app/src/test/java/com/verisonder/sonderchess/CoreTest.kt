package com.verisonder.sonderchess

import com.verisonder.sonderchess.core.Classifier
import com.verisonder.sonderchess.core.EngineLine
import com.verisonder.sonderchess.core.Game
import com.verisonder.sonderchess.core.Label
import com.verisonder.sonderchess.core.OpeningBook
import com.verisonder.sonderchess.core.Pgn
import com.verisonder.sonderchess.core.Position
import com.verisonder.sonderchess.core.PositionEval
import com.verisonder.sonderchess.core.Tcn
import com.verisonder.sonderchess.core.nextKeyMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoreTest {
    private fun perft(
        p: Position,
        d: Int,
    ): Long = if (d == 0) 1 else p.legalMoves().sumOf { perft(p.play(it), d - 1) }

    @Test
    fun perftMatchesKnownCounts() {
        assertEquals(8902L, perft(Position.start(), 3))
        assertEquals(2039L, perft(Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"), 2))
        assertEquals(2812L, perft(Position.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"), 3))
        assertEquals(9467L, perft(Position.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"), 3))
        assertEquals(1486L, perft(Position.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"), 2))
    }

    @Test
    fun decodesChessComMoveList() {
        val tcn =
            "mC0SgvZJfH6ZHZ5ZCJZTJS70eh3Nfe84S101lBYIks65jzIzcD9RDR54szT8vK1TRI8YdtYSKU?VI0!0U07B0JBtJTVTpxtBbsBAadAseSs6S06902TLoE" +
                "LnENnjNV9?V3XH2!4X!?XOiyjzdROG?6zF3~F?6?HyRdyqdaGH?7HIxFIHFNWOaqHzqOzHOaHQNVQPV3PXafXO3~OP?8PO8ZOG76Gz6QzqZXqyQO"
        val moves = Tcn.decode(tcn)
        assertEquals(115, moves.size)
        val g = Game(mapOf("Result" to "1-0"), Position.START_FEN, moves)
        assertEquals("O-O", g.sans()[12])
        assertEquals("Ra6#", g.sans().last())
        assertTrue(g.positions().last().isCheckmate())
        assertEquals(moves, Pgn.parse(g.toPgn()).moves)
    }

    @Test
    fun reviewMatchesChessComClosely() {
        val dir = File("src/test/resources")
        val game = Pgn.parse(File(dir, "nidal-vs-aerial-powers.pgn").readText())
        val txt = File(dir, "nidal-vs-aerial-powers.evals.json").readText()
        val evals =
            Regex("\"lines\": \\[(.*?)\\]\\}")
                .findAll(txt)
                .map { r ->
                    PositionEval(
                        Regex("\\{\"move\": \"(\\w+)\", \"(cp|mate)\": (-?\\d+)\\}")
                            .findAll(r.groupValues[1])
                            .map {
                                val v = it.groupValues[3].toInt()
                                if (it.groupValues[2] ==
                                    "cp"
                                ) {
                                    EngineLine(it.groupValues[1], cp = v)
                                } else {
                                    EngineLine(it.groupValues[1], mate = v)
                                }
                            }.toList(),
                    )
                }.toList()
        val book = OpeningBook.parse(File("src/main/assets/openings.tsv").readText())
        val r = Classifier.review(game, evals, book)
        // Chess.com: 78.3 / 72.6, two brilliant moves for White (26.Rad1, 27.Rxe6), two book moves each.
        assertEquals(78.3, r.accuracyWhite, 2.5)
        assertEquals(72.6, r.accuracyBlack, 2.5)
        val stops = generateSequence(-1) { p -> if (p >= r.moves.lastIndex) null else nextKeyMove(r.moves, p) }.drop(1).toList()
        println("stops: " + stops.joinToString { "${r.moves[it].san}(${r.moves[it].label})" })
        assertEquals(0, stops.first())
        assertEquals(r.moves.lastIndex, stops.last())
        assertEquals(2, r.count(true, Label.Brilliant))
        assertEquals(2, r.count(true, Label.Book))
        assertEquals(2, r.count(false, Label.Book))
        assertEquals(Label.Brilliant, r.moves[50].label)
        assertEquals(Label.Brilliant, r.moves[52].label)
        assertEquals("C00", r.eco)
    }
}
