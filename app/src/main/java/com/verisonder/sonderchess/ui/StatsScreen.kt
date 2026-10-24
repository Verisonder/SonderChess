package com.verisonder.sonderchess.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.core.Label
import com.verisonder.sonderchess.data.ArchiveGame
import com.verisonder.sonderchess.data.ChessComApi

private val periods = listOf("7D" to 7, "30D" to 30, "90D" to 90, "1Y" to 365, "ALL" to 0)

/** Ratings, games and rating changes over a period; tap a time control for its graph and record. */
@Composable
fun StatsScreen() {
    val p = AppModel.profile
    val user = AppModel.username
    var period by rememberSaveable { mutableIntStateOf(2) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val days = periods[period].second
    val since = if (days == 0) 0L else System.currentTimeMillis() - days * 86_400_000L
    val games by produceState(StatsCache.games[user to period], user, period) {
        val cached = StatsCache.games[user to period]
        value = cached
        if (cached == null) {
            val got = if (user == null) emptyList() else runCatching { ChessComApi.gamesSince(user, since) }.getOrDefault(emptyList())
            StatsCache.games[user to period] = got
            value = got
        }
    }
    // Brilliant moves from your reviews in the period; each review is worked out once per session.
    val reviewed = AppModel.history.filter { since == 0L || it.savedAt >= since }
    val brilliantGames by produceState<List<Pair<com.verisonder.sonderchess.data.SavedGame, Int>>?>(null, AppModel.history, period) {
        value = reviewed.map { it to AppModel.reviewOf(it).count(it.meWhite, Label.Brilliant) }
    }
    val brilliant = brilliantGames?.sumOf { it.second }
    Column(Modifier.fillMaxSize()) {
        TopBar("Stats", onBack = { AppModel.screen = Screen.Home })
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user ?: "?", true, 60.dp, p?.avatar)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(user ?: "Not connected", style = display(18))
                        p?.name?.let { Text(it, style = body(14, color = C.dim)) }
                        p?.joined?.let {
                            Text(
                                "Joined " +
                                    java.text.DateFormat
                                        .getDateInstance(java.text.DateFormat.MEDIUM)
                                        .format(java.util.Date(it)),
                                style = body(13, color = C.faint),
                            )
                        }
                    }
                }
            }
            item {
                SlidingSegments(periods.map { it.first }, period, Modifier.padding(horizontal = 16.dp), height = 36.dp) { period = it }
            }
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    KindIcon("rapid", 30.dp)
                    Spacer(Modifier.width(10.dp))
                    if (games == null) {
                        CircularProgressIndicator(color = C.green, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    } else {
                        CountUp(games!!.size, display(28), key = "stats-games" to period)
                        Spacer(Modifier.width(8.dp))
                        Text("Games", style = body(17, color = C.dim))
                    }
                }
            }
            val list = games.orEmpty()
            for ((kind, name, rating) in listOf(
                Triple("daily", "Daily", p?.daily),
                Triple("rapid", "Rapid", p?.rapid),
                Triple("bullet", "Bullet", p?.bullet),
                Triple("blitz", "Blitz", p?.blitz),
            )) {
                val mine = list.filter { it.timeClass == kind }.sortedBy { it.endTime }
                val series = mine.mapNotNull { g -> if (g.black.equals(user, true)) g.blackRating else g.whiteRating }
                val change = if (series.size >= 2) series.last() - series.first() else null
                item(key = kind) {
                    Box(Modifier.appear(listOf("daily", "rapid", "bullet", "blitz").indexOf(kind), "stats" to kind)) {
                        StatRow(kind, name, rating?.toString() ?: "–", change, open == kind) { open = if (open == kind) null else kind }
                    }
                    AnimatedVisibility(open == kind, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            RatingChart(series)
                            Spacer(Modifier.height(10.dp))
                            val wins = mine.count { (if (it.black.equals(user, true)) it.blackResult else it.whiteResult) == "win" }
                            val draws =
                                mine.count {
                                    (if (it.black.equals(user, true)) it.blackResult else it.whiteResult) in
                                        setOf("agreed", "repetition", "stalemate", "insufficient", "50move", "timevsinsufficient")
                                }
                            RecordBar(wins, mine.size - wins - draws, draws)
                            p?.records?.get(kind)?.let { r ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "All time: ${r.win} W · ${r.loss} L · ${r.draw} D" + (r.best?.let { " · best $it" } ?: ""),
                                    style = body(13, color = C.faint),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            item(key = "brilliant") {
                StatRowIcon(
                    { LabelIcon(Label.Brilliant, 26.dp) },
                    "Brilliant Moves",
                    brilliant?.toString() ?: "…",
                    null,
                    open == "brilliant",
                ) { open = if (open == "brilliant") null else "brilliant" }
                AnimatedVisibility(open == "brilliant", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            "From the ${reviewed.size} game" + (if (reviewed.size == 1) "" else "s") +
                                " you reviewed in SonderChess" + (if (days == 0) "" else " in this period") +
                                ". Chess.com doesn't share its own count.",
                            style = body(13, color = C.faint),
                        )
                        for ((g, n) in brilliantGames.orEmpty().filter { it.second > 0 }) {
                            val opp = if (g.meWhite) g.black.name else g.white.name
                            Row(
                                Modifier.fillMaxWidth().clickable { AppModel.open(g) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("vs $opp", style = body(15, FontWeight.Medium), modifier = Modifier.weight(1f))
                                Text("$n", style = body(15, FontWeight.Bold, C.text))
                                Spacer(Modifier.width(6.dp))
                                LabelIcon(Label.Brilliant, 18.dp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            item(key = "puzzles") {
                StatRowIcon(
                    { KindIcon("puzzles", 26.dp) },
                    "Puzzles (best)",
                    p?.puzzles?.toString() ?: "–",
                    null,
                    open == "puzzles",
                ) { open = if (open == "puzzles") null else "puzzles" }
                AnimatedVisibility(open == "puzzles", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        val day = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
                        PuzzleLine("Highest", p?.puzzles?.toString(), p?.puzzlesBestAt?.let { day.format(java.util.Date(it)) })
                        PuzzleLine("Lowest", p?.puzzlesLowest?.toString(), null)
                        PuzzleLine("Puzzle Rush best", p?.puzzleRush?.toString(), null)
                        Text(
                            "Chess.com only shares your highest and lowest puzzle ratings, not the current one.",
                            style = body(12, color = C.faint),
                            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    kind: String,
    name: String,
    value: String,
    change: Int?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    StatRowIcon({ KindIcon(kind, 26.dp) }, name, value, change, expanded, onClick)
}

@Composable
private fun StatRowIcon(
    icon: @Composable () -> Unit,
    name: String,
    value: String,
    change: Int?,
    expanded: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val turn by animateFloatAsState(if (expanded) 90f else 0f, tween(200), label = "chev")
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Text(name, style = body(17, FontWeight.Bold), modifier = Modifier.weight(1f))
        Text(value, style = body(17, FontWeight.Bold))
        if (change != null && change != 0) {
            Text(
                (if (change > 0) "  ↑ " else "  ↓ ") + kotlin.math.abs(change),
                style = body(13, FontWeight.Bold, if (change > 0) C.green else C.error),
            )
        }
        Spacer(Modifier.width(10.dp))
        if (onClick != null) {
            Canvas(Modifier.size(14.dp).graphicsLayer { rotationZ = turn }) {
                val p =
                    Path().apply {
                        moveTo(size.width * 0.3f, size.height * 0.1f)
                        lineTo(size.width * 0.75f, size.height * 0.5f)
                        lineTo(size.width * 0.3f, size.height * 0.9f)
                    }
                drawPath(p, C.dim, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        } else {
            Spacer(Modifier.width(14.dp))
        }
    }
}

@Composable
private fun PuzzleLine(
    name: String,
    value: String?,
    note: String?,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = body(15, FontWeight.Medium, C.dim), modifier = Modifier.weight(1f))
        note?.let { Text(it + "  ", style = body(13, color = C.faint)) }
        Text(value ?: "–", style = body(15, FontWeight.Bold))
    }
}

/** Archive games already fetched this session, by player and period. */
private object StatsCache {
    val games = HashMap<Pair<String?, Int>, List<ArchiveGame>?>()
}

/** Rating over the period: a green line with a soft fill, drawn in from the left. */
@Composable
private fun RatingChart(series: List<Int>) {
    if (series.size < 2) {
        Text("Not enough games in this period for a graph.", style = body(13, color = C.faint))
        return
    }
    val fresh = remember(series) { Played.first(listOf("chart", series)) }
    val reveal = remember(series) { Animatable(if (fresh) 0f else 1f) }
    LaunchedEffect(series) { if (fresh) reveal.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val lo = series.min()
    val hi = series.max()
    Column {
        Row {
            Text(hi.toString(), style = body(11, color = C.faint), modifier = Modifier.weight(1f))
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(C.panel),
        ) {
            val n = series.size - 1
            val range = (hi - lo).coerceAtLeast(1)

            fun pt(i: Int) =
                Offset(size.width * i / n, size.height * (1f - (series[i] - lo).toFloat() / range) * 0.84f + size.height * 0.08f)
            val line =
                Path().apply {
                    moveTo(pt(0).x, pt(0).y)
                    for (i in 1..n) lineTo(pt(i).x, pt(i).y)
                }
            val fill =
                Path().apply {
                    addPath(line)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
            clipRect(right = size.width * reveal.value) {
                drawPath(fill, Brush.verticalGradient(listOf(C.green.copy(alpha = 0.35f), Color.Transparent)))
                drawPath(line, C.green, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Text(lo.toString(), style = body(11, color = C.faint))
    }
}

/** Wins, losses and draws as one bar. */
@Composable
private fun RecordBar(
    w: Int,
    l: Int,
    d: Int,
) {
    val total = (w + l + d).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        if (w > 0) Box(Modifier.weight(w.toFloat() / total).fillMaxSize().background(C.green))
        if (d > 0) Box(Modifier.weight(d.toFloat() / total).fillMaxSize().background(C.faint))
        if (l > 0) Box(Modifier.weight(l.toFloat() / total).fillMaxSize().background(C.error))
        if (w + l + d == 0) Box(Modifier.weight(1f).fillMaxSize().background(C.panel2))
    }
    Spacer(Modifier.height(6.dp))
    Text("$w won · $l lost · $d drawn", style = body(13, color = C.dim))
}
