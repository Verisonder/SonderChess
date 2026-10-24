package com.verisonder.sonderchess.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.R
import com.verisonder.sonderchess.core.Position
import com.verisonder.sonderchess.data.ArchiveGame
import com.verisonder.sonderchess.data.ChessComApi
import com.verisonder.sonderchess.data.FetchedGame
import com.verisonder.sonderchess.data.Profile
import com.verisonder.sonderchess.data.SavedGame

/** One line in Game History: a reviewed game, a game from your archive, or both. */
private data class HistoryItem(
    val id: String,
    val kind: String,
    val opponent: String,
    val opponentRating: Int?,
    val opponentAvatar: String?,
    /** 1 win, 0 draw, -1 loss, null unknown. */
    val result: Int?,
    val accuracy: Double?,
    val time: Long,
    val saved: SavedGame?,
    val archived: ArchiveGame?,
)

private val drawResults = setOf("agreed", "repetition", "stalemate", "insufficient", "50move", "timevsinsufficient")

private fun buildHistory(
    saved: List<SavedGame>,
    archive: List<ArchiveGame>,
    me: String?,
): List<HistoryItem> {
    val byId = saved.associateBy { it.id }
    val out = ArrayList<HistoryItem>()
    for (g in archive) {
        val meWhite = !g.black.equals(me, true)
        val mine = if (meWhite) g.whiteResult else g.blackResult
        val s = byId[g.id]
        out.add(
            HistoryItem(
                id = g.id,
                kind = g.timeClass.ifBlank { "rapid" },
                opponent = if (meWhite) g.black else g.white,
                opponentRating = if (meWhite) g.blackRating else g.whiteRating,
                opponentAvatar = s?.let { if (meWhite) it.black.avatar else it.white.avatar },
                result =
                    when {
                        mine == "win" -> 1
                        mine in drawResults -> 0
                        else -> -1
                    },
                accuracy =
                    s?.let { if (it.meWhite) it.accuracyWhite else it.accuracyBlack }
                        ?: if (meWhite) g.whiteAccuracy else g.blackAccuracy,
                time = g.endTime,
                saved = s,
                archived = g,
            ),
        )
    }
    val inArchive = archive.map { it.id }.toSet()
    for (s in saved) {
        if (s.id in inArchive) continue
        val r = s.game.result
        val opp = if (s.meWhite) s.black else s.white
        out.add(
            HistoryItem(
                id = s.id,
                kind = s.id.substringBefore('-').let { if (it == "live") "rapid" else it },
                opponent = opp.name,
                opponentRating = opp.rating,
                opponentAvatar = opp.avatar,
                result =
                    when (r) {
                        "1/2-1/2" -> 0
                        "1-0" -> if (s.meWhite) 1 else -1
                        "0-1" -> if (s.meWhite) -1 else 1
                        else -> null
                    },
                accuracy = if (s.meWhite) s.accuracyWhite else s.accuracyBlack,
                time = s.savedAt,
                saved = s,
                archived = null,
            ),
        )
    }
    return out.sortedByDescending { it.time }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var settings by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedGame?>(null) }
    val rows = rememberHistory()
    val p = AppModel.profile
    PullToRefreshBox(
        isRefreshing = AppModel.accountBusy,
        onRefresh = {
            AppModel.refresh()
            AppModel.refreshAccount()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { ProfileHeader(onSettings = { settings = true }) }
            if (AppModel.username == null) item { ConnectCard() }
            AppModel.accountError?.let { err ->
                item { Text(err, style = body(13, color = C.error), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }
            if (p != null) item { StatsRow(p) }
            item { HistoryTitle(p?.totalGames ?: rows.size) }
            if (rows.isEmpty()) {
                item {
                    Text(
                        if (AppModel.username == null) {
                            "Share a game from Chess.com, or connect your account above."
                        } else {
                            "No games yet. Finish a game on Chess.com and it shows up here."
                        },
                        style = body(14, color = C.faint),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(rows.take(5), key = { it.id }) { h -> GameRow(h, onLong = { h.saved?.let { deleting = it } }) }
            if (rows.size > 5 || AppModel.username != null) {
                item {
                    Box(
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(C.panel2)
                            .clickable { AppModel.screen = Screen.AllGames },
                        contentAlignment = Alignment.Center,
                    ) { Text("View All Games", style = body(17, FontWeight.Bold, C.dim)) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (settings) SettingsDialog { settings = false }
    deleting?.let { g -> DeleteDialog(g) { deleting = null } }
}

/** Every game: reviewed ones and your archive, with "Load older games" for earlier months. */
@Composable
fun AllGamesScreen() {
    var deleting by remember { mutableStateOf<SavedGame?>(null) }
    val rows = rememberHistory()
    Column(Modifier.fillMaxSize()) {
        TopBar("Game History", onBack = { AppModel.screen = Screen.Home })
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.id }) { h -> GameRow(h, onLong = { h.saved?.let { deleting = it } }) }
            if (AppModel.username != null) {
                item {
                    Box(
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(C.panel2)
                            .clickable { AppModel.loadMore() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (AppModel.loadingMore) {
                            CircularProgressIndicator(color = C.green, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        } else {
                            Text("Load older games", style = body(17, FontWeight.Bold, C.dim))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    deleting?.let { g -> DeleteDialog(g) { deleting = null } }
}

@Composable
private fun rememberHistory(): List<HistoryItem> =
    remember(AppModel.history, AppModel.archive, AppModel.username) {
        buildHistory(AppModel.history, AppModel.archive, AppModel.username)
    }

@Composable
private fun DeleteDialog(
    g: SavedGame,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Delete this review?") },
        text = { Text("${g.white.name} vs ${g.black.name}") },
        confirmButton = {
            TextButton(onClick = {
                AppModel.delete(g.id)
                onClose()
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Keep") } },
        containerColor = C.panel,
    )
}

@Composable
private fun HistoryTitle(count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Game History", style = display(19))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(C.panel2).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(count.toString(), style = body(13, FontWeight.Bold, C.dim))
        }
        Spacer(Modifier.weight(1f))
        Text("Accuracy", style = body(15, color = C.dim))
    }
}

/** Banner of pieces, big picture, name, flag, real name, location, then joined/followers/games. */
@Composable
private fun ProfileHeader(onSettings: () -> Unit) {
    val p = AppModel.profile
    val name = AppModel.username
    val clipboard = LocalClipboardManager.current
    Column {
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            PieceBanner(Modifier.fillMaxWidth().height(170.dp))
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                // Paste a game link or PGN from the clipboard.
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable {
                        val t = clipboard.getText()?.text.orEmpty()
                        if (t.isBlank()) {
                            AppModel.screen = Screen.Failed("The clipboard is empty. Copy a Chess.com game link or a PGN first.")
                        } else {
                            AppModel.handleText(t)
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { PasteIcon() }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
                    GearIcon()
                }
            }
            Row(Modifier.align(Alignment.BottomStart).padding(start = 16.dp), verticalAlignment = Alignment.Bottom) {
                if (name != null) {
                    Avatar(name, true, 112.dp, p?.avatar)
                } else {
                    Box(Modifier.size(112.dp).clip(RoundedCornerShape(8.dp)).background(C.panel2), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.piece_wn), null, Modifier.size(84.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name ?: "SonderChess", style = display(24), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        p?.country?.let { Text(" " + flag(it), style = body(20)) }
                    }
                    p?.name?.let { Text(it, style = body(17, FontWeight.Bold, C.dim), maxLines = 1) }
                    p?.location?.let { Text(it, style = body(14, color = C.faint), maxLines = 1) }
                }
            }
        }
        if (p != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                p.joined?.let {
                    Fact(
                        java.text.DateFormat
                            .getDateInstance(java.text.DateFormat.MEDIUM)
                            .format(java.util.Date(it)),
                        "Joined",
                        Modifier.weight(1.3f),
                    )
                }
                p.followers?.let { Fact(it.toString(), "Followers", Modifier.weight(1f)) }
                p.totalGames?.let { Fact(it.toString(), "Games", Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Fact(
    value: String,
    label: String,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(value, style = body(19, FontWeight.Bold), maxLines = 1)
        Text(label, style = body(15, color = C.faint))
    }
}

/** A country code as its flag emoji ("MA" → Morocco's flag). */
private fun flag(code: String): String {
    val c = code.uppercase()
    if (c.length != 2 || !c.all { it in 'A'..'Z' }) return ""
    return String(Character.toChars(0x1F1E6 + (c[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (c[1] - 'A')))
}

/** Two rows of pieces on a dark board, fading into the page. */
@Composable
private fun PieceBanner(modifier: Modifier) {
    val pieces = piecePainters()
    Canvas(modifier) {
        val s = size.height / 2f
        val cols = (size.width / s).toInt() + 1
        val back = intArrayOf(4, 5, 6, 3, 2, 4, 5, 6)
        for (r in 0..1) {
            for (c in 0 until cols) {
                val light = (r + c) % 2 == 0
                drawRect(
                    if (light) Color(0xFF6E5A47) else Color(0xFF4F3F31),
                    Offset(c * s, r * s),
                    androidx.compose.ui.geometry
                        .Size(s, s),
                )
                val type = if (r == 0) 1 else back[c % back.size]
                val painter = pieces[-type + 6] ?: continue
                translate(c * s + s * 0.08f, r * s + s * 0.08f) {
                    with(painter) {
                        draw(
                            androidx.compose.ui.geometry
                                .Size(s * 0.84f, s * 0.84f),
                            alpha = 0.55f,
                        )
                    }
                }
            }
        }
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to C.bg.copy(alpha = 0.15f),
                0.55f to C.bg.copy(alpha = 0.45f),
                1f to C.bg,
            ),
        )
    }
}

/** Stats cards: Daily, Rapid, Bullet, Blitz, Puzzles; slides sideways. */
@Composable
private fun StatsRow(p: Profile) {
    val cards =
        listOfNotNull(
            p.daily?.let { Triple("daily", "Daily", it) },
            p.rapid?.let { Triple("rapid", "Rapid", it) },
            p.bullet?.let { Triple("bullet", "Bullet", it) },
            p.blitz?.let { Triple("blitz", "Blitz", it) },
            p.puzzles?.let { Triple("puzzles", "Puzzles", it) },
        )
    if (cards.isEmpty()) return
    Text("Stats", style = display(19), modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 10.dp))
    LazyRow(
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(cards.size) { i ->
            val (kind, label, value) = cards[i]
            Column(
                Modifier
                    .size(118.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.panel2)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                KindIcon(kind, 44.dp)
                Spacer(Modifier.height(8.dp))
                Text(value.toString(), style = display(20))
                Text(label, style = body(14, FontWeight.Medium, C.faint))
            }
        }
    }
}

@Composable
private fun PasteIcon() {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val st = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            C.dim,
            Offset(5f * u, 4.5f * u),
            androidx.compose.ui.geometry
                .Size(14f * u, 17f * u),
            androidx.compose.ui.geometry
                .CornerRadius(2f * u),
            style = st,
        )
        drawRoundRect(
            C.dim,
            Offset(9f * u, 2.5f * u),
            androidx.compose.ui.geometry
                .Size(6f * u, 4f * u),
            androidx.compose.ui.geometry
                .CornerRadius(1f * u),
        )
        drawLine(C.dim, Offset(8.5f * u, 11f * u), Offset(15.5f * u, 11f * u), 2f * u, StrokeCap.Round)
        drawLine(C.dim, Offset(8.5f * u, 15f * u), Offset(13.5f * u, 15f * u), 2f * u, StrokeCap.Round)
    }
}

@Composable
private fun ConnectCard() {
    var name by remember { mutableStateOf("") }
    Column(
        Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.panel)
            .padding(16.dp),
    ) {
        Text("Connect your Chess.com", style = display(16))
        Spacer(Modifier.height(4.dp))
        Text(
            "Your games and ratings show up here, ready to review. Type your username, or share your Chess.com profile to SonderChess.",
            style = body(13, color = C.dim),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Username") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { AppModel.connect(name) }),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.green,
                        unfocusedBorderColor = C.line,
                        cursorColor = C.green,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(110.dp)) {
                GreenButton(if (AppModel.accountBusy) "…" else "Connect", height = 52.dp) { AppModel.connect(name) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameRow(
    h: HistoryItem,
    onLong: () -> Unit,
) {
    val avatarUrl by produceState(h.opponentAvatar, h.opponent) {
        if (value == null && h.kind != "pgn") value = ChessComApi.avatar(h.opponent)
    }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        when {
                            h.saved != null -> AppModel.open(h.saved)
                            h.archived != null -> AppModel.reviewArchived(h.archived)
                        }
                    },
                    onLongClick = onLong,
                ).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KindIcon(h.kind, 28.dp)
            Spacer(Modifier.width(12.dp))
            Avatar(h.opponent, h.kind == "computer", 40.dp, avatarUrl)
            Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.opponent,
                    style = body(16, FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                h.opponentRating?.let { Text(" ($it)", style = body(16, color = C.dim), maxLines = 1) }
            }
            Spacer(Modifier.width(8.dp))
            ResultIcon(h.result)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                if (h.accuracy != null) {
                    Text("%.1f".format(h.accuracy), style = body(16, color = C.text))
                } else {
                    ReviewButton {
                        h.archived?.let { AppModel.reviewArchived(it) }
                    }
                }
            }
        }
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(C.line.copy(alpha = 0.6f)),
        )
    }
}

/** The green "review this game" key with a starred badge. */
@Composable
private fun ReviewButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp, 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.greenDark)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(45.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(C.green),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(26.dp)) {
                drawCircle(Color.White, size.width / 2f, style = Stroke(2.2.dp.toPx()))
                drawLabelIconStar(this)
            }
        }
    }
}

private fun drawLabelIconStar(d: androidx.compose.ui.graphics.drawscope.DrawScope) {
    with(d) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val outer = size.width * 0.3f
        val inner = outer * 0.45f
        val p =
            Path().apply {
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outer else inner
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = c.x + (r * Math.cos(a)).toFloat()
                    val y = c.y + (r * Math.sin(a)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        drawPath(p, Color.White)
    }
}

/** Win / draw / loss as a small coloured square with + = −. */
@Composable
private fun ResultIcon(result: Int?) {
    if (result == null) {
        Spacer(Modifier.size(22.dp))
        return
    }
    val bg =
        when (result) {
            1 -> C.green
            0 -> C.faint
            else -> C.error
        }
    Canvas(Modifier.size(22.dp)) {
        drawRoundRect(
            bg,
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(4.dp.toPx()),
        )
        val w = 3.dp.toPx()
        val m = size.width * 0.24f
        val cy = size.height / 2f
        if (result == 0) {
            drawLine(Color.White, Offset(m, cy - w * 0.8f), Offset(size.width - m, cy - w * 0.8f), w * 0.8f)
            drawLine(Color.White, Offset(m, cy + w * 0.8f), Offset(size.width - m, cy + w * 0.8f), w * 0.8f)
        } else {
            drawLine(Color.White, Offset(m, cy), Offset(size.width - m, cy), w)
            if (result == 1) drawLine(Color.White, Offset(size.width / 2f, m), Offset(size.width / 2f, size.height - m), w)
        }
    }
}

/** Game type icons, drawn: bot, rapid clock, blitz bolt, bullet, daily sun, puzzles, file. */
@Composable
fun KindIcon(
    kind: String,
    size: Dp,
) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f

        fun o(
            x: Float,
            y: Float,
        ) = Offset(x * u, y * u)
        val st = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            "computer" -> {
                val c = Color(0xFF7FA6D9)
                drawRoundRect(
                    c,
                    o(4f, 6f),
                    androidx.compose.ui.geometry
                        .Size(16f * u, 13f * u),
                    androidx.compose.ui.geometry.CornerRadius(
                        4f * u,
                    ),
                )
                drawLine(c, o(12f, 6f), o(12f, 3f), 2f * u, StrokeCap.Round)
                drawCircle(c, 1.6f * u, o(12f, 2.6f))
                drawCircle(C.bg, 1.8f * u, o(9f, 12f))
                drawCircle(C.bg, 1.8f * u, o(15f, 12f))
                drawLine(C.bg, o(9.5f, 16f), o(14.5f, 16f), 1.6f * u, StrokeCap.Round)
            }
            "blitz" -> {
                val p =
                    Path().apply {
                        moveTo(13.5f * u, 2f * u)
                        lineTo(5f * u, 13.5f * u)
                        lineTo(11f * u, 13.5f * u)
                        lineTo(9.5f * u, 22f * u)
                        lineTo(19f * u, 9.5f * u)
                        lineTo(13f * u, 9.5f * u)
                        close()
                    }
                drawPath(p, Color(0xFFF7C631))
            }
            "bullet" -> {
                // A fat gold bullet flying up and to the right, with speed streaks.
                drawLine(Color(0xFF8B8987), o(2f, 20f), o(6f, 16f), 1.5f * u, StrokeCap.Round)
                drawLine(Color(0xFF8B8987), o(4f, 22f), o(8f, 18f), 1.5f * u, StrokeCap.Round)
                withTransform({ rotate(45f, o(12f, 12f)) }) {
                    val body =
                        Path().apply {
                            moveTo(6.8f * u, 18.5f * u)
                            lineTo(6.8f * u, 11f * u)
                            cubicTo(6.8f * u, 6f * u, 9.5f * u, 2.5f * u, 12f * u, 2.5f * u)
                            cubicTo(14.5f * u, 2.5f * u, 17.2f * u, 6f * u, 17.2f * u, 11f * u)
                            lineTo(17.2f * u, 18.5f * u)
                            close()
                        }
                    drawPath(body, Color(0xFFF2B925))
                    // Shine on one side, darker base.
                    drawLine(Color(0xFFFFE28F), o(9.4f, 7.5f), o(9.4f, 14f), 1.8f * u, StrokeCap.Round)
                    drawRect(
                        Color(0xFFC98E12),
                        o(6.8f, 16.4f),
                        androidx.compose.ui.geometry
                            .Size(10.4f * u, 2.1f * u),
                    )
                }
            }
            "daily" -> {
                // Sun: pointed rays, golden disc, bright centre.
                val ray = Color(0xFFF5A623)
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0)
                    val a1 = Math.toRadians(i * 30.0 - 11.0)
                    val a2 = Math.toRadians(i * 30.0 + 11.0)
                    val p =
                        Path().apply {
                            moveTo(12f * u + (11.5f * u * Math.cos(a)).toFloat(), 12f * u + (11.5f * u * Math.sin(a)).toFloat())
                            lineTo(12f * u + (7f * u * Math.cos(a1)).toFloat(), 12f * u + (7f * u * Math.sin(a1)).toFloat())
                            lineTo(12f * u + (7f * u * Math.cos(a2)).toFloat(), 12f * u + (7f * u * Math.sin(a2)).toFloat())
                            close()
                        }
                    drawPath(p, ray)
                }
                drawCircle(Color(0xFFFFC52E), 7.4f * u, o(12f, 12f))
                drawCircle(Color(0xFFFFE07A), 5f * u, o(12f, 12f))
                drawCircle(Color(0xFFFFF1BF), 2.4f * u, o(11f, 11f))
            }
            "puzzles" -> {
                val c = Color(0xFFF38A3D)
                drawRoundRect(
                    c,
                    o(4f, 7f),
                    androidx.compose.ui.geometry
                        .Size(14f * u, 14f * u),
                    androidx.compose.ui.geometry.CornerRadius(
                        2f * u,
                    ),
                )
                drawCircle(c, 3f * u, o(11f, 6f))
                drawCircle(c, 3f * u, o(19f, 14f))
            }
            "pgn" -> {
                drawRoundRect(
                    C.dim,
                    o(5f, 3f),
                    androidx.compose.ui.geometry
                        .Size(14f * u, 18f * u),
                    androidx.compose.ui.geometry.CornerRadius(
                        2f * u,
                    ),
                    style = st,
                )
                drawLine(C.dim, o(8.5f, 9f), o(15.5f, 9f), 1.8f * u, StrokeCap.Round)
                drawLine(C.dim, o(8.5f, 13f), o(15.5f, 13f), 1.8f * u, StrokeCap.Round)
                drawLine(C.dim, o(8.5f, 17f), o(13f, 17f), 1.8f * u, StrokeCap.Round)
            }
            else -> {
                // Rapid (and live games of unknown speed): a solid green stopwatch.
                val g = Color(0xFF81B64C)
                val gd = Color(0xFF5D8F33)
                drawRoundRect(
                    g,
                    o(10f, 1.5f),
                    androidx.compose.ui.geometry
                        .Size(4f * u, 2.6f * u),
                    androidx.compose.ui.geometry.CornerRadius(
                        1f * u,
                    ),
                )
                drawRect(
                    gd,
                    o(11.2f, 3.8f),
                    androidx.compose.ui.geometry
                        .Size(1.6f * u, 2.2f * u),
                )
                drawCircle(gd, 9.4f * u, o(12f, 14.6f))
                drawCircle(g, 9.4f * u, o(12f, 14f))
                drawCircle(Color(0xFFF3F3F0), 6.4f * u, o(12f, 14f))
                // Needle, pointing a little left of twelve.
                val n =
                    Path().apply {
                        moveTo(11.1f * u, 14.3f * u)
                        lineTo(11.6f * u, 8.6f * u)
                        lineTo(12.9f * u, 14.3f * u)
                        close()
                    }
                drawPath(n, Color(0xFF6E6C69))
                drawCircle(Color(0xFF6E6C69), 1.3f * u, o(12f, 14.2f))
            }
        }
    }
}

@Composable
private fun GearIcon() {
    Canvas(Modifier.size(24.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.width * 0.3f
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val ca = Math.cos(a).toFloat()
            val sa = Math.sin(a).toFloat()
            drawLine(
                C.dim,
                Offset(c.x + ca * r, c.y + sa * r),
                Offset(c.x + ca * r * 1.45f, c.y + sa * r * 1.45f),
                size.width * 0.14f,
                StrokeCap.Round,
            )
        }
        drawCircle(C.dim, r, c)
        drawCircle(C.bar, r * 0.45f, c)
    }
}

@Composable
private fun SettingsDialog(onClose: () -> Unit) {
    var depth by remember { mutableIntStateOf(AppModel.depth) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.panel,
        title = { Text("Settings", style = display(18)) },
        text = {
            Column {
                Text("Analysis depth", style = body(13, FontWeight.Bold, C.faint))
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(C.bg)
                        .padding(4.dp),
                ) {
                    for ((d, n) in listOf(12 to "Fast", 16 to "Normal", 20 to "Deep")) {
                        val on = depth == d
                        Box(
                            Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) C.panel2 else C.bg)
                                .clickable {
                                    depth = d
                                    AppModel.setDepth(d)
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(n, style = body(14, FontWeight.Bold, if (on) C.text else C.faint)) }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Chess.com account", style = body(13, FontWeight.Bold, C.faint))
                Spacer(Modifier.height(6.dp))
                Text(AppModel.username ?: "Not connected", style = body(16, FontWeight.Bold))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        dismissButton = {
            if (AppModel.username != null) {
                TextButton(onClick = {
                    AppModel.disconnect()
                    onClose()
                }) { Text("Disconnect", color = C.error) }
            }
        },
    )
}

@Composable
fun AnalysingScreen(s: Screen.Analysing) {
    val progress by animateFloatAsState(if (s.total == 0) 0f else s.done.toFloat() / s.total, tween(250), label = "progress")
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Coach(52.dp, reactTo = s.done / 20)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Analysing your game", style = display(18))
                Text("${s.white} vs ${s.black}", style = body(13, color = C.dim))
            }
        }
        Spacer(Modifier.height(20.dp))
        // The game replays as each position is analysed.
        Box(Modifier.fillMaxWidth(0.82f).clip(RoundedCornerShape(6.dp))) {
            ChessBoard(Position.fromFen(s.fen), last = null, label = null, arrows = emptyList(), flipped = false)
        }
        Spacer(Modifier.height(22.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = C.green,
            trackColor = C.panel2,
        )
        Spacer(Modifier.height(10.dp))
        Text("Position ${s.done} of ${s.total}", style = body(13, color = C.faint))
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = { AppModel.cancel() }) { Text("Cancel", style = body(15, FontWeight.Bold, C.dim)) }
    }
}

@Composable
fun LoadingScreen(message: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = C.green)
        Spacer(Modifier.height(16.dp))
        Text(message, style = body(15, color = C.dim))
    }
}

@Composable
fun FailedScreen(message: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Couldn't review that", style = display(20))
        Spacer(Modifier.height(10.dp))
        Text(message, style = body(15, color = C.dim), textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        GreenButton("Back", height = 50.dp) { AppModel.screen = Screen.Home }
    }
}

@Composable
fun PickSideScreen(f: FetchedGame) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Coach(80.dp)
        Spacer(Modifier.height(18.dp))
        Text("Which one is you?", style = display(20))
        Spacer(Modifier.height(6.dp))
        Text("Asked once. The coach talks to you about your moves.", style = body(14, color = C.dim), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        for (white in listOf(true, false)) {
            val p = if (white) f.white else f.black
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.panel)
                    .clickable { AppModel.pickSide(f, white) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(p.name, white, 44.dp, p.avatar)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(p.name, style = body(16, FontWeight.Bold))
                    Text((if (white) "White" else "Black") + (p.rating?.let { " · $it" } ?: ""), style = body(13, color = C.faint))
                }
            }
        }
    }
}
