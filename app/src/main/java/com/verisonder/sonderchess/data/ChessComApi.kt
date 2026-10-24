package com.verisonder.sonderchess.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** A player's public profile and ratings. */
data class Profile(
    val username: String,
    val avatar: String?,
    val name: String? = null,
    val location: String? = null,
    /** Two-letter country code, e.g. "MA". */
    val country: String? = null,
    /** Milliseconds. */
    val joined: Long? = null,
    val followers: Int? = null,
    /** All finished games, summed over the rating records. */
    val totalGames: Int? = null,
    val rapid: Int?,
    val blitz: Int?,
    val bullet: Int?,
    val daily: Int?,
    val puzzles: Int?,
)

/** One game from your public archive (live and daily games; bot games are not in it). */
data class ArchiveGame(
    val id: String,
    val url: String,
    val pgn: String,
    val timeClass: String,
    val endTime: Long,
    val white: String,
    val black: String,
    val whiteRating: Int?,
    val blackRating: Int?,
    val whiteResult: String,
    val blackResult: String,
    /** Accuracy from a review done on Chess.com, when there was one. */
    val whiteAccuracy: Double? = null,
    val blackAccuracy: Double? = null,
)

/**
 * Chess.com's official public API (https://api.chess.com/pub). No account or key needed;
 * it serves any player's profile, ratings and finished games.
 */
object ChessComApi {
    private const val BASE = "https://api.chess.com/pub/player/"
    private val avatars = ConcurrentHashMap<String, String>()
    private val noAvatar = ConcurrentHashMap.newKeySet<String>()

    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "SonderChess (Android; https://github.com/Verisonder/SonderChess)")
        return try {
            when (conn.responseCode) {
                in 200..299 -> conn.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                else -> throw IllegalStateException("Chess.com answered with error ${conn.responseCode}.")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun user(u: String) = URLEncoder.encode(u.trim().lowercase(), "UTF-8")

    /** Profile and ratings, or null when there is no such player. */
    suspend fun profile(username: String): Profile? =
        withContext(Dispatchers.IO) {
            val p = get(BASE + user(username))?.let { JSONObject(it) } ?: return@withContext null
            val s = runCatching { get(BASE + user(username) + "/stats")?.let { JSONObject(it) } }.getOrNull()

            fun rating(key: String) =
                s
                    ?.optJSONObject(key)
                    ?.optJSONObject("last")
                    ?.optInt("rating")
                    ?.takeIf { it > 0 }
            val avatar = p.optString("avatar").takeIf { it.startsWith("https://") }
            avatar?.let { avatars[p.optString("username").lowercase()] = it }
            Profile(
                // "username" comes back lower case; the profile link keeps the real spelling.
                username =
                    p.optString("url").substringAfterLast('/', "").takeIf { it.equals(p.optString("username"), true) }
                        ?: p.optString("username").ifBlank { username },
                avatar = avatar,
                rapid = rating("chess_rapid"),
                blitz = rating("chess_blitz"),
                bullet = rating("chess_bullet"),
                daily = rating("chess_daily"),
                puzzles =
                    s
                        ?.optJSONObject("tactics")
                        ?.optJSONObject("highest")
                        ?.optInt("rating")
                        ?.takeIf { it > 0 },
                name = p.optString("name").takeIf { it.isNotBlank() },
                location = p.optString("location").trim().takeIf { it.isNotBlank() && it.any(Char::isLetterOrDigit) },
                country = p.optString("country").substringAfterLast('/', "").takeIf { it.length == 2 },
                joined = p.optLong("joined").takeIf { it > 0 }?.times(1000L),
                followers = if (p.has("followers")) p.optInt("followers") else null,
                totalGames =
                    listOf("chess_daily", "chess_rapid", "chess_blitz", "chess_bullet")
                        .sumOf { k ->
                            s?.optJSONObject(k)?.optJSONObject("record")?.let { r -> r.optInt("win") + r.optInt("loss") + r.optInt("draw") }
                                ?: 0
                        }.takeIf { it > 0 },
            )
        }

    /** Your most recent standard-chess games, newest first, from the last [months] monthly archives. */
    suspend fun recentGames(
        username: String,
        months: Int = 2,
    ): List<ArchiveGame> =
        withContext(Dispatchers.IO) {
            val list = get(BASE + user(username) + "/games/archives")?.let { JSONObject(it) } ?: return@withContext emptyList()
            val arr = list.optJSONArray("archives") ?: return@withContext emptyList()
            val urls = (maxOf(0, arr.length() - months) until arr.length()).map { arr.getString(it) }
            coroutineScope {
                urls.map { u -> async { runCatching { get(u) }.getOrNull() } }.awaitAll()
            }.filterNotNull()
                .flatMap { body ->
                    val games = JSONObject(body).optJSONArray("games") ?: return@flatMap emptyList()
                    (0 until games.length()).mapNotNull { i ->
                        val g = games.getJSONObject(i)
                        if (g.optString("rules") != "chess") return@mapNotNull null
                        val pgn = g.optString("pgn").ifBlank { return@mapNotNull null }
                        val url = g.optString("url")
                        val w = g.getJSONObject("white")
                        val b = g.getJSONObject("black")
                        val kind = if (url.contains("/daily/")) "daily" else "live"
                        ArchiveGame(
                            id = "$kind-" + url.substringAfterLast('/'),
                            url = url,
                            pgn = pgn,
                            timeClass = g.optString("time_class"),
                            endTime = g.optLong("end_time") * 1000L,
                            white = w.optString("username"),
                            black = b.optString("username"),
                            whiteRating = w.optInt("rating").takeIf { it > 0 },
                            blackRating = b.optInt("rating").takeIf { it > 0 },
                            whiteResult = w.optString("result"),
                            blackResult = b.optString("result"),
                            whiteAccuracy = g.optJSONObject("accuracies")?.optDouble("white")?.takeIf { !it.isNaN() },
                            blackAccuracy = g.optJSONObject("accuracies")?.optDouble("black")?.takeIf { !it.isNaN() },
                        )
                    }
                }.sortedByDescending { it.endTime }
        }

    /** A player's profile picture link (cached), or null when they have none. */
    suspend fun avatar(username: String): String? {
        val key = username.lowercase()
        avatars[key]?.let { return it }
        if (key in noAvatar) return null
        return withContext(Dispatchers.IO) {
            val a =
                runCatching {
                    get(BASE + user(username))?.let { JSONObject(it).optString("avatar") }?.takeIf { it.startsWith("https://") }
                }.getOrNull()
            if (a != null) avatars[key] = a else noAvatar.add(key)
            a
        }
    }

    /** A Chess.com profile link (chess.com/member/NAME) in shared text, if any. */
    fun findMember(text: String): String? =
        Regex("""chess\.com/(?:[a-z]{2}/)?member/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
}
