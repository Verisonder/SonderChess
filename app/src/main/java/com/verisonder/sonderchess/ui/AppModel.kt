package com.verisonder.sonderchess.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.verisonder.sonderchess.core.Classifier
import com.verisonder.sonderchess.core.Game
import com.verisonder.sonderchess.core.OpeningBook
import com.verisonder.sonderchess.core.Pgn
import com.verisonder.sonderchess.core.PositionEval
import com.verisonder.sonderchess.core.Review
import com.verisonder.sonderchess.data.ArchiveGame
import com.verisonder.sonderchess.data.ChessCom
import com.verisonder.sonderchess.data.ChessComApi
import com.verisonder.sonderchess.data.FetchedGame
import com.verisonder.sonderchess.data.Player
import com.verisonder.sonderchess.data.Profile
import com.verisonder.sonderchess.data.SavedGame
import com.verisonder.sonderchess.data.Store
import com.verisonder.sonderchess.engine.Stockfish
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen

    data object AllGames : Screen

    data object Theme : Screen

    /** Stats, with [open] (a time control such as "rapid") already unfolded. */
    data class Stats(
        val open: String? = null,
    ) : Screen

    data class Game(
        val id: String,
    ) : Screen

    data class Loading(
        val message: String,
    ) : Screen

    data class Analysing(
        val white: String,
        val black: String,
        val done: Int,
        val total: Int,
        val fen: String = com.verisonder.sonderchess.core.Position.START_FEN,
    ) : Screen

    data class PickSide(
        val fetched: FetchedGame,
    ) : Screen

    data class Summary(
        val id: String,
    ) : Screen

    data class Board(
        val id: String,
        val ply: Int,
    ) : Screen

    data class Failed(
        val message: String,
    ) : Screen
}

/** An open review: the saved game and the labels built from it. */
data class Opened(
    val saved: SavedGame,
    val review: Review,
)

/**
 * App-wide state. Lives as long as the process, so an analysis keeps going while the screen
 * turns or the app is in the background.
 */
object AppModel {
    var screen by mutableStateOf<Screen>(Screen.Home)
    var opened by mutableStateOf<Opened?>(null)
        private set
    var history by mutableStateOf<List<SavedGame>>(emptyList())
        private set

    /** Your Chess.com profile and recent games, when a username is known. */
    var profile by mutableStateOf<Profile?>(null)
        private set
    var archive by mutableStateOf<List<ArchiveGame>>(emptyList())
        private set
    var accountBusy by mutableStateOf(false)
        private set
    var accountError by mutableStateOf<String?>(null)
        private set
    var username by mutableStateOf<String?>(null)
        private set

    /** The board colours and piece set in use. */
    var board by mutableStateOf(Themes.boards[0])
        private set
    var pieces by mutableStateOf(Themes.pieces[0])
        private set

    fun useBoard(t: BoardTheme) {
        board = t
        store.boardTheme = t.id
    }

    fun usePieces(p: PieceSet) {
        pieces = p
        store.pieceSet = p.id
    }

    /** Look and feel options (Theme page). */
    var soundSet by mutableStateOf("default")
        private set
    var background by mutableStateOf("default")
        private set
    var coordinates by mutableStateOf(true)
        private set
    var highlightLast by mutableStateOf(true)
        private set
    var showArrows by mutableStateOf(true)
        private set
    var animation by mutableStateOf(2)
        private set

    /** Bumped when a new custom background picture is saved, so it reloads. */
    var backgroundVersion by mutableStateOf(0)
        private set

    fun useSoundSet(id: String) {
        soundSet = id
        store.soundSet = id
    }

    fun useBackground(id: String) {
        background = id
        store.background = id
        if (id == "custom") backgroundVersion++
    }

    fun useCoordinates(v: Boolean) {
        coordinates = v
        store.coordinates = v
    }

    fun useHighlightLast(v: Boolean) {
        highlightLast = v
        store.highlightLast = v
    }

    fun useShowArrows(v: Boolean) {
        showArrows = v
        store.showArrows = v
    }

    fun useAnimation(v: Int) {
        animation = v
        store.animation = v
    }

    /** The game open on the game page. */
    var viewing by mutableStateOf<GameView?>(null)
        private set

    /** Opens a game (from your archive and/or your reviews) on the game page. */
    fun openGame(
        saved: SavedGame?,
        archived: ArchiveGame?,
    ) {
        val v =
            runCatching {
                if (archived != null) {
                    val game = Pgn.parse(archived.pgn)
                    val meWhite = !archived.black.equals(username, true)
                    val mine = if (meWhite) archived.whiteResult else archived.blackResult
                    val myRating = if (meWhite) archived.whiteRating else archived.blackRating
                    val before =
                        archive
                            .filter { it.timeClass == archived.timeClass && it.endTime < archived.endTime }
                            .maxByOrNull { it.endTime }
                            ?.let { if (it.black.equals(username, true)) it.blackRating else it.whiteRating }
                    GameView(
                        id = archived.id,
                        game = game,
                        white = saved?.white ?: Player(archived.white, archived.whiteRating, false, null),
                        black = saved?.black ?: Player(archived.black, archived.blackRating, false, null),
                        meWhite = meWhite,
                        clocks = Pgn.clocks(archived.pgn),
                        ending = Pgn.endingOf(game.headers["Termination"]),
                        result =
                            when {
                                mine == "win" -> 1
                                mine in setOf("agreed", "repetition", "stalemate", "insufficient", "50move", "timevsinsufficient") -> 0
                                else -> -1
                            },
                        myRating = myRating,
                        ratingChange = if (myRating != null && before != null) myRating - before else null,
                        timeClass = archived.timeClass,
                        archived = archived,
                        saved = saved,
                    )
                } else {
                    val sv = saved ?: return
                    val r = sv.game.result
                    GameView(
                        id = sv.id,
                        game = sv.game,
                        white = sv.white,
                        black = sv.black,
                        meWhite = sv.meWhite,
                        clocks = emptyList(),
                        ending = Pgn.endingOf(sv.game.headers["Termination"]),
                        result =
                            when (r) {
                                "1/2-1/2" -> 0
                                "1-0" -> if (sv.meWhite) 1 else -1
                                "0-1" -> if (sv.meWhite) -1 else 1
                                else -> null
                            },
                        myRating = if (sv.meWhite) sv.white.rating else sv.black.rating,
                        ratingChange = null,
                        timeClass = sv.id.substringBefore('-'),
                        archived = null,
                        saved = sv,
                    )
                }
            }.getOrNull() ?: return
        viewing = v
        screen = Screen.Game(v.id)
    }

    /** Copies a picked picture (scaled down) to be the review background. */
    fun saveBackground(uri: android.net.Uri) {
        scope.launch(Dispatchers.IO) {
            val ok = Backgrounds.save(app, uri)
            if (ok) withContext(Dispatchers.Main) { useBackground("custom") }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var book: OpeningBook? = null
    private lateinit var store: Store
    private lateinit var app: Context

    fun init(context: Context) {
        if (::store.isInitialized) return
        app = context.applicationContext
        store = Store(app)
        username = store.username
        board = Themes.board(store.boardTheme)
        pieces = Themes.pieceSet(store.pieceSet)
        soundSet = store.soundSet
        background = store.background
        coordinates = store.coordinates
        highlightLast = store.highlightLast
        showArrows = store.showArrows
        animation = store.animation
        Sounds.init(app)
        ChessComApi.cacheDir = java.io.File(app.filesDir, "chesscom").apply { mkdirs() }
        refresh()
        refreshAccount()
        watchNetwork()
    }

    /** True while what's shown is the copy saved on the phone, because Chess.com can't be reached. */
    var offline by mutableStateOf(false)
        private set

    /** When the phone gets back online after showing saved data, load the fresh copy. */
    private fun watchNetwork() {
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java) ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(
                object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        if (offline || accountError != null) scope.launch { refreshAccount() }
                    }
                },
            )
        }
    }

    /**
     * Reloads your profile, ratings and recent games from Chess.com's public API. What was saved
     * last time shows straight away, and the fresh copy replaces it quietly when it arrives; the
     * spinner only shows when there is nothing to show yet or you pulled to refresh.
     */
    fun refreshAccount(pulled: Boolean = false) {
        val u = username ?: return
        scope.launch {
            if (profile == null) {
                runCatching {
                    ChessComApi.profile(u, savedOnly = true)?.let { profile = it }
                    ChessComApi.recentGames(u, archiveMonths, savedOnly = true).takeIf { it.isNotEmpty() }?.let { archive = it }
                }
            }
            accountBusy = pulled || profile == null
            accountError = null
            try {
                profile = ChessComApi.profile(u) ?: run {
                    accountError = "Chess.com has no player called $u."
                    null
                }
                archive = ChessComApi.recentGames(u, archiveMonths)
                offline = ChessComApi.offline
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                accountError = "Couldn't reach Chess.com. Pull down to try again."
            } finally {
                accountBusy = false
            }
        }
    }

    /** How many months of your archive are loaded; "Load more" adds to it. */
    private var archiveMonths = 2
    var loadingMore by mutableStateOf(false)
        private set

    fun loadMore() {
        val u = username ?: return
        if (loadingMore) return
        archiveMonths += 3
        scope.launch {
            loadingMore = true
            try {
                archive = ChessComApi.recentGames(u, archiveMonths)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                accountError = "Couldn't reach Chess.com."
            } finally {
                loadingMore = false
            }
        }
    }

    /** Connects a Chess.com account by username (checked against Chess.com first). */
    fun connect(name: String) {
        val u = name.trim().removePrefix("@")
        if (u.isEmpty()) return
        scope.launch {
            accountBusy = true
            accountError = null
            val p = runCatching { ChessComApi.profile(u) }.getOrNull()
            accountBusy = false
            if (p == null) {
                accountError = "Couldn't find $u on Chess.com."
            } else {
                store.username = p.username
                username = p.username
                profile = p
                refreshAccount()
            }
        }
    }

    fun disconnect() {
        store.username = null
        username = null
        profile = null
        archive = emptyList()
    }

    /** Reviews a game from your archive (opens it straight away if already reviewed). */
    fun reviewArchived(g: ArchiveGame) {
        store.load(g.id)?.let {
            open(it)
            return
        }
        start {
            val game = Pgn.parse(g.pgn)
            val me = username.orEmpty()
            val (wa, ba) = withContext(Dispatchers.IO) { ChessComApi.avatar(g.white) to ChessComApi.avatar(g.black) }
            val fetched =
                FetchedGame(
                    game,
                    Player(g.white, g.whiteRating, false, wa),
                    Player(g.black, g.blackRating, false, ba),
                    g.id.substringAfter('-'),
                    g.id.substringBefore('-'),
                )
            analyse(fetched, !g.black.equals(me, true))
        }
    }

    val depth: Int get() = store.depth

    fun setDepth(d: Int) {
        store.depth = d
    }

    fun refresh() {
        scope.launch { history = store.list() }
    }

    private suspend fun book(): OpeningBook =
        book ?: withContext(Dispatchers.IO) {
            OpeningBook.parse(
                app.assets
                    .open("openings.tsv")
                    .bufferedReader()
                    .use { it.readText() },
            )
        }.also { book = it }

    /** Shared or pasted text: a Chess.com link or a PGN. */
    fun handleText(text: String) {
        val ref = ChessCom.find(text)
        val member = ChessComApi.findMember(text)
        when {
            ref == null && member != null -> {
                screen = Screen.Home
                connect(member)
            }
            ref != null ->
                start {
                    screen = Screen.Loading("Getting the game from Chess.com…")
                    val fetched = ChessCom.fetch(ref)
                    chooseSide(fetched, text)
                }
            Pgn.looksLikePgn(text) ->
                start {
                    val game = Pgn.parse(text)
                    val fetched =
                        FetchedGame(
                            game,
                            Player(game.white, game.headers["WhiteElo"]?.toIntOrNull(), false, null),
                            Player(game.black, game.headers["BlackElo"]?.toIntOrNull(), false, null),
                            "pgn-" + Integer.toHexString(game.toPgn().hashCode()),
                            "pgn",
                        )
                    chooseSide(fetched, text)
                }
            else -> screen = Screen.Failed("That doesn't look like a Chess.com game link or a PGN.")
        }
    }

    private suspend fun chooseSide(
        f: FetchedGame,
        shared: String,
    ) {
        val known = store.username
        if (known != null && !f.white.name.equals(known, true) && !f.black.name.equals(known, true)) {
            // Someone else's game: review it from White's side and keep nothing afterwards.
            analyse(f, meWhite = true, keep = false)
            return
        }
        val past = withContext(Dispatchers.IO) { store.list() }
        val meWhite = whoAmI(f, shared, store.username, past.map { it.white.name to it.black.name })
        if (meWhite == null) {
            screen = Screen.PickSide(f)
        } else {
            val name = if (meWhite) f.white.name else f.black.name
            if (store.username == null) {
                store.username = name
                username = name
                refreshAccount()
            }
            analyse(f, meWhite)
        }
    }

    /**
     * Which side the person sharing played, without asking: a name learned before, the human
     * against a bot, the opponent named in Chess.com's share text ("I Beat Aerial Powers!"),
     * or the one name every earlier game has in common. Null when none of these tell.
     */
    fun whoAmI(
        f: FetchedGame,
        shared: String,
        known: String?,
        past: List<Pair<String, String>>,
    ): Boolean? {
        val w = f.white.name
        val b = f.black.name
        if (known != null && w.equals(known, true)) return true
        if (known != null && b.equals(known, true)) return false
        if (f.black.computer && !f.white.computer) return true
        if (f.white.computer && !f.black.computer) return false
        // Share text: the opponent is named, you are not.
        val text = norm(shared.replace(Regex("""https?://\S+"""), " "))
        val saysW = norm(w).let { it.length >= 3 && text.contains(it) }
        val saysB = norm(b).let { it.length >= 3 && text.contains(it) }
        if (saysB && !saysW) return true
        if (saysW && !saysB) return false
        // History: you are the one player in every game.
        if (past.isNotEmpty()) {
            val common =
                past
                    .map { setOf(it.first.lowercase(), it.second.lowercase()) }
                    .reduce { acc, s -> acc intersect s }
            val inW = w.lowercase() in common
            val inB = b.lowercase() in common
            if (inW && !inB) return true
            if (inB && !inW) return false
        }
        return null
    }

    /** Fills in profile pictures Chess.com's game data left out, from each player's public profile. */
    private suspend fun withAvatars(f: FetchedGame): FetchedGame {
        suspend fun pic(p: Player) = if (p.avatar != null) p else p.copy(avatar = runCatching { ChessComApi.avatar(p.name) }.getOrNull())
        return f.copy(white = pic(f.white), black = pic(f.black))
    }

    /** Lower case, letters and digits only: "Aerial-Powers-BOT" and "Aerial Powers" both read "aerialpowers…". */
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun pickSide(
        f: FetchedGame,
        white: Boolean,
    ) {
        store.username = if (white) f.white.name else f.black.name
        username = store.username
        refreshAccount()
        start { analyse(f, white) }
    }

    /**
     * Analyses [f]. Your own games ([keep]) are saved to your history; anyone else's game is
     * only held while you look at it.
     */
    private suspend fun analyse(
        fetched: FetchedGame,
        meWhite: Boolean,
        keep: Boolean = true,
        fresh: Boolean = false,
    ) {
        val id = "${fetched.kind}-${fetched.id}"
        val depth = store.depth
        store.load(id)?.takeIf { !fresh && it.depth >= depth }?.let {
            open(it)
            return
        }
        val f = withAvatars(fetched)
        val positions = f.game.positions()
        screen = Screen.Analysing(f.white.name, f.black.name, 0, positions.size)
        val evals = ArrayList<PositionEval>(positions.size)
        val engine = Stockfish.start(app)
        try {
            engine.newGame()
            withContext(Dispatchers.IO) {
                for ((i, p) in positions.withIndex()) {
                    ensureActive()
                    val over = p.legalMoves().isEmpty()
                    evals.add(if (over) PositionEval(emptyList()) else engine.analyse(p.fen(), depth))
                    screen = Screen.Analysing(f.white.name, f.black.name, i + 1, positions.size, p.fen())
                }
            }
        } finally {
            withContext(Dispatchers.IO) { engine.close() }
        }
        val review = Classifier.review(f.game, evals, book())
        val saved =
            SavedGame(
                id,
                f.game,
                f.white,
                f.black,
                meWhite,
                depth,
                evals,
                System.currentTimeMillis(),
                review.accuracyWhite,
                review.accuracyBlack,
            )
        if (keep) {
            synchronized(reviews) { reviews[id + "@" + saved.savedAt] = review }
            withContext(Dispatchers.IO) { store.save(saved) }
            refresh()
        }
        opened = Opened(saved, review)
        screen = Screen.Summary(id)
    }

    /** Reviews already worked out this session, by game and save time. */
    private val reviews = HashMap<String, Review>()

    /** The review of a saved game, worked out once and then reused. */
    suspend fun reviewOf(saved: SavedGame): Review {
        val k = saved.id + "@" + saved.savedAt
        synchronized(reviews) { reviews[k] }?.let { return it }
        val b = book()
        val r = withContext(Dispatchers.Default) { Classifier.review(saved.game, saved.evals, b) }
        synchronized(reviews) { reviews[k] = r }
        return r
    }

    fun open(saved: SavedGame) {
        scope.launch {
            val review = reviewOf(saved)
            opened = Opened(saved, review)
            screen = Screen.Summary(saved.id)
            // Games saved before pictures were kept: fetch them once and save them with the game.
            if (saved.white.avatar == null || saved.black.avatar == null) {
                val f = withAvatars(FetchedGame(saved.game, saved.white, saved.black, "", ""))
                if (f.white.avatar != saved.white.avatar || f.black.avatar != saved.black.avatar) {
                    val s = saved.copy(white = f.white, black = f.black)
                    if (opened?.saved?.id == s.id) opened = opened?.copy(saved = s)
                    withContext(Dispatchers.IO) { store.save(s) }
                    refresh()
                }
            }
        }
    }

    /** Flip which side is "you" for an open review. */
    fun flipMe() {
        val o = opened ?: return
        val s = o.saved.copy(meWhite = !o.saved.meWhite)
        opened = o.copy(saved = s)
        scope.launch(Dispatchers.IO) { store.save(s) }
    }

    fun delete(id: String) {
        scope.launch(Dispatchers.IO) {
            store.delete(id)
            history = store.list()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        screen = Screen.Home
    }

    private fun start(block: suspend () -> Unit) {
        job?.cancel()
        job =
            scope.launch {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    screen = Screen.Failed(e.message ?: e.javaClass.simpleName)
                }
            }
    }

    /** Re-run an open game at the current depth (e.g. after choosing Deep). */
    fun reanalyse() {
        opened?.let { reanalyse(it.saved) }
    }

    /**
     * Analyses a saved game again from scratch at the current depth. The old review stays
     * saved until the new one replaces it, so cancelling half way loses nothing.
     */
    fun reanalyse(saved: SavedGame) {
        val g: Game = saved.game
        val f = FetchedGame(g, saved.white, saved.black, saved.id.substringAfter('-'), saved.id.substringBefore('-'))
        start {
            val known = username
            val mine = known == null || f.white.name.equals(known, true) || f.black.name.equals(known, true)
            analyse(f, saved.meWhite, keep = mine, fresh = true)
        }
    }
}

/** A finished game as shown on the game page. */
data class GameView(
    val id: String,
    val game: Game,
    val white: Player,
    val black: Player,
    val meWhite: Boolean,
    /** Clock left after each move, when known. */
    val clocks: List<String?>,
    /** "by Resignation", "on Time"… */
    val ending: String?,
    /** For you: 1 win, 0 draw, -1 loss. */
    val result: Int?,
    val myRating: Int?,
    val ratingChange: Int?,
    val timeClass: String,
    val archived: ArchiveGame?,
    val saved: SavedGame?,
)
