package com.verisonder.sonderchess.data

import android.content.Context
import com.verisonder.sonderchess.core.EngineLine
import com.verisonder.sonderchess.core.Game
import com.verisonder.sonderchess.core.Pgn
import com.verisonder.sonderchess.core.PositionEval
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One analysed game as kept on the phone. The review itself is rebuilt from it on open. */
data class SavedGame(
    val id: String,
    val game: Game,
    val white: Player,
    val black: Player,
    /** The person using the app played White. */
    val meWhite: Boolean,
    val depth: Int,
    val evals: List<PositionEval>,
    val savedAt: Long,
    val accuracyWhite: Double,
    val accuracyBlack: Double,
)

/** Saved games (files) and settings (preferences). */
class Store(
    context: Context,
) {
    private val dir = File(context.filesDir, "games").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Your Chess.com username, learned from your games, so "you" is the right side. */
    var username: String?
        get() = prefs.getString("username", null)
        set(v) = prefs.edit().putString("username", v).apply()

    /** Chosen board colours and piece set (ids from Themes). */
    var boardTheme: String?
        get() = prefs.getString("board", null)
        set(v) = prefs.edit().putString("board", v).apply()

    var pieceSet: String?
        get() = prefs.getString("pieces", null)
        set(v) = prefs.edit().putString("pieces", v).apply()

    /** Engine depth per position. */
    var depth: Int
        get() = prefs.getInt("depth", 16)
        set(v) = prefs.edit().putInt("depth", v).apply()

    fun list(): List<SavedGame> =
        dir
            .listFiles { f -> f.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { runCatching { decode(it.readText()) }.getOrNull() }
            .sortedByDescending { it.savedAt }

    fun load(id: String): SavedGame? = file(id).takeIf { it.exists() }?.let { runCatching { decode(it.readText()) }.getOrNull() }

    fun save(g: SavedGame) {
        val f = file(g.id)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(encode(g).toString())
        tmp.renameTo(f)
    }

    fun delete(id: String) {
        file(id).delete()
    }

    private fun file(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    private fun encode(g: SavedGame) =
        JSONObject().apply {
            put("v", 1)
            put("id", g.id)
            put("pgn", g.game.toPgn())
            put("white", player(g.white))
            put("black", player(g.black))
            put("meWhite", g.meWhite)
            put("depth", g.depth)
            put("savedAt", g.savedAt)
            put("accW", g.accuracyWhite)
            put("accB", g.accuracyBlack)
            put(
                "evals",
                JSONArray().apply {
                    for (e in g.evals) {
                        put(
                            JSONObject().apply {
                                put("d", e.depth)
                                put(
                                    "l",
                                    JSONArray().apply {
                                        for (l in e.lines) {
                                            put(
                                                JSONObject().apply {
                                                    put("m", l.move)
                                                    l.cp?.let { put("cp", it) }
                                                    l.mate?.let { put("mate", it) }
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }

    private fun player(p: Player) =
        JSONObject().apply {
            put("name", p.name)
            p.rating?.let { put("rating", it) }
            put("computer", p.computer)
        }

    private fun decode(text: String): SavedGame {
        val o = JSONObject(text)
        val ev = o.getJSONArray("evals")
        val evals =
            (0 until ev.length()).map { i ->
                val e = ev.getJSONObject(i)
                val l = e.getJSONArray("l")
                PositionEval(
                    (0 until l.length()).map { j ->
                        val x = l.getJSONObject(j)
                        EngineLine(
                            x.getString("m"),
                            cp = if (x.has("cp")) x.getInt("cp") else null,
                            mate = if (x.has("mate")) x.getInt("mate") else null,
                        )
                    },
                    e.optInt("d"),
                )
            }

        fun p(x: JSONObject) =
            Player(
                x.getString(
                    "name",
                ),
                if (x.has("rating")) x.getInt("rating") else null,
                x.optBoolean("computer"),
                x.optString("avatar").takeIf {
                    it.startsWith("https://")
                },
            )
        return SavedGame(
            id = o.getString("id"),
            game = Pgn.parse(o.getString("pgn")),
            white = p(o.getJSONObject("white")),
            black = p(o.getJSONObject("black")),
            meWhite = o.optBoolean("meWhite", true),
            depth = o.optInt("depth", 16),
            evals = evals,
            savedAt = o.optLong("savedAt"),
            accuracyWhite = o.optDouble("accW", 0.0),
            accuracyBlack = o.optDouble("accB", 0.0),
        )
    }
}
