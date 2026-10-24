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

    fun setBoard(t: BoardTheme) {
        board = t
        store.boardTheme = t.id
    }

    fun setPieces(p: PieceSet) {
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

    fun setSoundSet(id: String) {
        soundSet = id
        store.soundSet = id
    }

    fun setBackground(id: String) {
        background = id
        store.background = id
        if (id == "custom") backgroundVersion++
    }

    fun setCoordinates(v: Boolean) {
        coordinates = v
        store.coordinates = v
    }

    fun setHighlightLast(v: Boolean) {
        highlightLast = v
        store.highlightLast = v
    }

    fun setShowArrows(v: Boolean) {
        showArrows = v
        store.showArrows = v
    }

    fun setAnimation(v: Int) {
        animation = v
        store.animation = v
    }

    /** Copies a picked picture (scaled down) to be the review background. */
    fun saveBackground(uri: android.net.Uri) {
        scope.launch(Dispatchers.IO) {
            val ok = Backgrounds.save(app, uri)
            if (ok) withContext(Dispatchers.Main) { setBackground("custom") }
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
        refresh()
        refreshAccount()
    }

    /** Reloads your profile, ratings and recent games from Chess.com's public API. */
    fun refreshAccount() {
        val u = username ?: return
        scope.launch {
            accountBusy = true
            accountError = null
            try {
                profile = ChessComApi.profile(u) ?: run {
                    accountError = "Chess.com has no player called $u."
                    null
                }
                archive = ChessComApi.recentGames(u, archiveMonths)
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

    private suspend fun analyse(
        f: FetchedGame,
        meWhite: Boolean,
    ) {
        val id = "${f.kind}-${f.id}"
        val depth = store.depth
        store.load(id)?.takeIf { it.depth >= depth }?.let {
            open(it)
            return
        }
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
        withContext(Dispatchers.IO) { store.save(saved) }
        refresh()
        opened = Opened(saved, review)
        screen = Screen.Summary(id)
    }

    fun open(saved: SavedGame) {
        scope.launch {
            val review = Classifier.review(saved.game, saved.evals, book())
            opened = Opened(saved, review)
            screen = Screen.Summary(saved.id)
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
        val o = opened ?: return
        val g: Game = o.saved.game
        val f = FetchedGame(g, o.saved.white, o.saved.black, o.saved.id.substringAfter('-'), o.saved.id.substringBefore('-'))
        start {
            store.delete(o.saved.id)
            analyse(f, o.saved.meWhite)
        }
    }
}
