package com.verisonder.sonderchess.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * One puzzle from the Lichess puzzle database (CC0). As in Lichess: [fen] is the position
 * before the opponent's move, [moves] starts with that move, and you play every second move
 * after it.
 */
data class OfflinePuzzle(
    val id: String,
    val fen: String,
    val moves: List<Move>,
    val rating: Int,
    val themes: List<String>,
)

/** The puzzles shipped with the app, sorted by rating, and how to pick and score them. */
class PuzzleBook(
    val puzzles: List<OfflinePuzzle>,
) {
    /**
     * A puzzle near [rating] you haven't seen ([seen] holds ids), widening the window when the
     * close ones are used up.
     */
    fun pick(
        rating: Int,
        seen: Set<String>,
        random: Random = Random.Default,
    ): OfflinePuzzle? {
        for (window in listOf(75, 150, 300, 600, 3000)) {
            val near = puzzles.filter { abs(it.rating - rating) <= window && it.id !in seen }
            if (near.isNotEmpty()) return near[random.nextInt(near.size)]
        }
        return puzzles.randomOrNull(random)
    }

    companion object {
        /** Reads the tab-separated list: id, FEN, moves (UCI), rating, themes. */
        fun parse(text: String): PuzzleBook =
            PuzzleBook(
                text
                    .lineSequence()
                    .mapNotNull { line ->
                        val f = line.split('\t')
                        if (f.size < 4) return@mapNotNull null
                        runCatching {
                            OfflinePuzzle(
                                f[0],
                                f[1],
                                f[2].split(' ').filter { it.isNotBlank() }.map { Move.fromUci(it) },
                                f[3].toInt(),
                                f
                                    .getOrNull(4)
                                    ?.split(' ')
                                    ?.filter { it.isNotBlank() }
                                    .orEmpty(),
                            )
                        }.getOrNull()
                    }.toList(),
            )

        /**
         * Your new puzzle rating: Elo against the puzzle's rating. [solved] means solved
         * cleanly, with no wrong move, hint or shown solution. Moves faster while you are new.
         */
        fun newRating(
            yours: Int,
            puzzle: Int,
            solved: Boolean,
            played: Int,
        ): Int {
            val expected = 1.0 / (1.0 + 10.0.pow((puzzle - yours) / 400.0))
            val k = if (played < 20) 48.0 else 24.0
            return (yours + k * ((if (solved) 1.0 else 0.0) - expected)).toInt().coerceIn(100, 3300)
        }

        /** A short name for the puzzle's main idea, from its themes. */
        fun title(themes: List<String>): String {
            val mate = themes.firstOrNull { it.startsWith("mateIn") }?.removePrefix("mateIn")
            if (mate != null) return "Mate in $mate"
            val names =
                linkedMapOf(
                    "fork" to "Fork",
                    "pin" to "Pin",
                    "skewer" to "Skewer",
                    "discoveredAttack" to "Discovered attack",
                    "doubleCheck" to "Double check",
                    "sacrifice" to "Sacrifice",
                    "deflection" to "Deflection",
                    "attraction" to "Attraction",
                    "hangingPiece" to "Hanging piece",
                    "trappedPiece" to "Trapped piece",
                    "promotion" to "Promotion",
                    "zugzwang" to "Zugzwang",
                    "quietMove" to "Quiet move",
                    "defensiveMove" to "Defend",
                    "endgame" to "Endgame",
                    "crushing" to "Win material",
                    "advantage" to "Get the advantage",
                    "equality" to "Hold the balance",
                )
            return names.entries.firstOrNull { it.key in themes }?.value ?: "Find the best move"
        }
    }
}
