package com.verisonder.sonderchess.data

import com.verisonder.sonderchess.core.Game
import com.verisonder.sonderchess.core.Position
import com.verisonder.sonderchess.core.Tcn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A player as Chess.com describes them. */
data class Player(
    val name: String,
    val rating: Int?,
    val computer: Boolean,
    val avatar: String?,
)

/** A game fetched from Chess.com, plus who played it. */
data class FetchedGame(
    val game: Game,
    val white: Player,
    val black: Player,
    val id: String,
    val kind: String,
)

/**
 * Reads games from the links Chess.com's share button sends ("I Beat Aerial Powers!
 * https://www.chess.com/game/computer/2413637064"). Uses the same public game data the
 * Chess.com game page loads; no account or subscription needed.
 */
object ChessCom {
    data class Ref(
        val kind: String,
        val id: String,
    )

    private val patterns =
        listOf(
            Regex("""chess\.com/(?:[a-z]{2}/)?(?:analysis/)?game/(live|daily|computer)/(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""chess\.com/(?:[a-z]{2}/)?(live|daily|computer)/game/(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""chess\.com/(?:[a-z]{2}/)?game/(\d+)""", RegexOption.IGNORE_CASE),
        )

    /** Finds a Chess.com game link anywhere in [text]. */
    fun find(text: String): Ref? {
        for ((i, p) in patterns.withIndex()) {
            val m = p.find(text) ?: continue
            return if (i == 2) Ref("live", m.groupValues[1]) else Ref(m.groupValues[1].lowercase(), m.groupValues[2])
        }
        return null
    }

    fun endpoint(ref: Ref): String =
        when (ref.kind) {
            "computer" -> "https://www.chess.com/computer/callback/game/${ref.id}"
            "daily" -> "https://www.chess.com/callback/daily/game/${ref.id}"
            else -> "https://www.chess.com/callback/live/game/${ref.id}"
        }

    suspend fun fetch(ref: Ref): FetchedGame =
        withContext(Dispatchers.IO) {
            val conn = URL(endpoint(ref)).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SonderChess")
            try {
                val code = conn.responseCode
                if (code == 404) throw IllegalStateException("Chess.com couldn't find that game.")
                if (code !in 200..299) throw IllegalStateException("Chess.com answered with error $code.")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(body, ref)
            } finally {
                conn.disconnect()
            }
        }

    fun parse(
        json: String,
        ref: Ref,
    ): FetchedGame {
        val root = JSONObject(json)
        val g = root.getJSONObject("game")
        val moveList = g.optString("moveList")
        require(moveList.isNotEmpty()) { "This game has no moves yet." }
        val headersJson = g.optJSONObject("pgnHeaders")
        val headers = LinkedHashMap<String, String>()
        headersJson?.keys()?.forEach { k -> headers[k] = headersJson.get(k).toString() }
        val start = g.optString("initialSetup").ifBlank { headers["FEN"] ?: Position.START_FEN }
        val moves = Tcn.decode(moveList, start)
        if (start != Position.START_FEN) {
            headers["SetUp"] = "1"
            headers["FEN"] = start
        }
        val players = root.optJSONObject("players")

        fun player(color: String): Player {
            val p =
                listOfNotNull(players?.optJSONObject("top"), players?.optJSONObject("bottom"))
                    .firstOrNull { it.optString("color") == color }
            val headerName = headers[if (color == "white") "White" else "Black"] ?: color.replaceFirstChar { it.uppercase() }
            return Player(
                name = p?.optString("username")?.takeIf { it.isNotBlank() } ?: headerName,
                rating =
                    p?.optInt("rating")?.takeIf { it > 0 }
                        ?: headers[if (color == "white") "WhiteElo" else "BlackElo"]?.toIntOrNull(),
                computer = p?.optBoolean("isComputer") ?: false,
                avatar = p?.optString("avatarUrl")?.takeIf { it.startsWith("http") },
            )
        }
        val white = player("white")
        val black = player("black")
        headers["White"] = white.name
        headers["Black"] = black.name
        if (!headers.containsKey("Result")) headers["Result"] = "*"
        return FetchedGame(Game(headers, start, moves), white, black, ref.id, ref.kind)
    }
}
