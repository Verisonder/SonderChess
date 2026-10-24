package com.verisonder.sonderchess.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.BuildConfig

/** The three pages with the bar along the bottom. */
fun isTab(s: Screen) = s == Screen.Home || s == Screen.Puzzles || s == Screen.Paste || s == Screen.More

/**
 * Home, Puzzles and More, with the tab bar under them and, on Home, the big Play button
 * above it (it opens the analysis board).
 */
@Composable
fun TabScaffold(current: Screen) {
    var choosing by remember { mutableStateOf(false) }
    if (choosing) PlayChoice { choosing = false }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Crossfade(current, animationSpec = tween(180), label = "tab") { tab ->
                when (tab) {
                    Screen.Puzzles -> PuzzlesScreen()
                    Screen.Paste -> PasteScreen()
                    Screen.More -> MoreScreen()
                    else -> HomeScreen()
                }
            }
        }
        if (current == Screen.Home) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                GreenButton("Play", height = 58.dp) { choosing = true }
            }
        }
        TabBar(current)
    }
}

/** What Play opens: a game against a bot, or the free board. */
@Composable
private fun PlayChoice(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.panel,
        title = { Text("Play", style = display(20)) },
        text = {
            Column {
                val opened = remember { System.nanoTime() }
                ChoiceRow(0, opened, "bot", "Play a Bot", "A game against Stockfish, from beginner to full strength") {
                    onClose()
                    AppModel.screen = Screen.Bot
                }
                ChoiceRow(1, opened, "board", "Custom Board", "Move both sides, set up any position, ask Stockfish") {
                    onClose()
                    AppModel.screen = Screen.Play
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", style = body(15, FontWeight.Bold, C.dim)) } },
    )
}

@Composable
private fun ChoiceRow(
    index: Int,
    opened: Long,
    icon: String,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .appear(index, listOf("choice", opened, index))
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(C.panel2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon == "bot") {
            KindIcon("computer", 40.dp)
        } else {
            MiniBoard(Modifier.size(40.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = body(17, FontWeight.Bold))
            Text(detail, style = body(13, color = C.faint))
        }
        Text("›", style = body(22, color = C.dim))
    }
}

/** A tiny 4x4 board in your board colours, with a knight on it. */
@Composable
private fun MiniBoard(modifier: Modifier) {
    val knight = piecePainters()[6 + com.verisonder.sonderchess.core.Piece.KNIGHT]
    Canvas(modifier.clip(RoundedCornerShape(6.dp))) {
        val s = size.width / 4f
        for (r in 0..3) {
            for (c in 0..3) {
                drawRect(
                    if ((r + c) % 2 ==
                        0
                    ) {
                        AppModel.board.light
                    } else {
                        AppModel.board.dark
                    },
                    Offset(c * s, r * s),
                    Size(s, s),
                )
            }
        }
        knight?.run {
            translate(s, s) { draw(Size(s * 2f, s * 2f)) }
        }
    }
}

@Composable
private fun TabBar(current: Screen) {
    val index =
        when (current) {
            Screen.Puzzles -> 1
            Screen.Paste -> 2
            Screen.More -> 3
            else -> 0
        }
    // A short green line glides over to the tab you pick.
    val at by androidx.compose.animation.core.animateFloatAsState(
        index.toFloat(),
        androidx.compose.animation.core
            .spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "tabline",
    )
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().background(C.bar)) {
        val w = maxWidth / 4
        Box(
            Modifier
                .padding(start = w * at + w * 0.3f)
                .width(w * 0.4f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(C.green),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
            Tab("Home", "home", index == 0, Modifier.weight(1f)) { AppModel.screen = Screen.Home }
            Tab("Puzzles", "puzzles", index == 1, Modifier.weight(1f)) { AppModel.screen = Screen.Puzzles }
            Tab("Review", "paste", index == 2, Modifier.weight(1f)) { AppModel.screen = Screen.Paste }
            Tab("More", "more", index == 3, Modifier.weight(1f)) { AppModel.screen = Screen.More }
        }
    }
}

@Composable
private fun Tab(
    label: String,
    icon: String,
    on: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val col by animateColorAsState(if (on) C.text else Color(0xFF8B8987), tween(160), label = "tab")
    val pawn = piecePainters()[6 + com.verisonder.sonderchess.core.Piece.PAWN]
    // The icon gives a little hop when its tab is picked.
    val hop =
        remember {
            androidx.compose.animation.core
                .Animatable(1f)
        }
    androidx.compose.runtime.LaunchedEffect(on) {
        if (on) {
            hop.snapTo(0.82f)
            hop.animateTo(
                1f,
                androidx.compose.animation.core
                    .spring(dampingRatio = 0.4f, stiffness = 500f),
            )
        }
    }
    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier.size(28.dp).graphicsLayer {
                scaleX = hop.value
                scaleY = hop.value
            },
        ) {
            val u = size.width / 24f
            when (icon) {
                "home" -> {
                    // The pawn from your piece set, in the tab's colour.
                    pawn?.run {
                        draw(
                            size,
                            colorFilter =
                                androidx.compose.ui.graphics.ColorFilter
                                    .tint(col),
                        )
                    }
                }
                "puzzles" -> {
                    // A jigsaw piece, tilted: a rounded square with a knob on top and one on the right.
                    rotate(-14f) {
                        val p =
                            Path().apply {
                                addRoundRect(
                                    androidx.compose.ui.geometry.RoundRect(
                                        4f * u,
                                        8f * u,
                                        18f * u,
                                        22f * u,
                                        androidx.compose.ui.geometry
                                            .CornerRadius(2.2f * u),
                                    ),
                                )
                                addOval(
                                    androidx.compose.ui.geometry
                                        .Rect(Offset(11f * u, 5.6f * u), 3.3f * u),
                                )
                                addOval(
                                    androidx.compose.ui.geometry
                                        .Rect(Offset(20.4f * u, 15f * u), 3.3f * u),
                                )
                            }
                        drawPath(p, col)
                        // The socket on the left edge.
                        drawCircle(C.bar, 2.6f * u, Offset(4f * u, 15f * u))
                    }
                }
                "paste" -> {
                    // A clipboard, filled, with a lighter sheet showing.
                    drawRoundRect(
                        col,
                        Offset(4.5f * u, 4f * u),
                        Size(15f * u, 18f * u),
                        androidx.compose.ui.geometry
                            .CornerRadius(2.5f * u),
                    )
                    drawRoundRect(
                        C.bar,
                        Offset(7f * u, 8f * u),
                        Size(10f * u, 11.5f * u),
                        androidx.compose.ui.geometry
                            .CornerRadius(1.2f * u),
                    )
                    drawRoundRect(
                        col,
                        Offset(8.5f * u, 2f * u),
                        Size(7f * u, 4.5f * u),
                        androidx.compose.ui.geometry
                            .CornerRadius(1.5f * u),
                    )
                    for (y in listOf(11.5f, 14.5f)) drawLine(col, Offset(9f * u, y * u), Offset(15f * u, y * u), 1.6f * u, StrokeCap.Round)
                }
                else -> {
                    val st = Stroke(width = 2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    for (y in listOf(6f, 12f, 18f)) drawLine(col, Offset(4f * u, y * u), Offset(20f * u, y * u), st.width, StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = body(13, FontWeight.Bold, col))
    }
}

/** Settings and everything else: analysis depth, theme, stats, account, about. */
@Composable
fun MoreScreen() {
    val context = LocalContext.current
    var depth by remember { mutableIntStateOf(AppModel.depth) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            containerColor = C.panel,
            title = { Text("Disconnect ${AppModel.username}?", style = display(18)) },
            text = { Text("Your reviewed games stay on the phone.", style = body(15, color = C.dim)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    AppModel.disconnect()
                }) { Text("Disconnect", style = body(15, FontWeight.Bold, C.error)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDisconnect = false },
                ) { Text("Cancel", style = body(15, FontWeight.Bold, C.dim)) }
            },
        )
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("More", onBack = null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Section("Analysis")
            Text("Depth", style = body(15, FontWeight.Bold))
            Text("Deeper is more accurate but slower.", style = body(13, color = C.faint))
            Spacer(Modifier.height(8.dp))
            val depths = listOf(12, 16, 20)
            SlidingSegments(listOf("Fast", "Normal", "Deep"), depths.indexOf(depth).coerceAtLeast(0), track = C.panel, pill = C.panel2) {
                depth = depths[it]
                AppModel.setDepth(depths[it])
            }
            Section("Look")
            MoreRow(1, "Theme", "${AppModel.board.name} board · ${AppModel.pieces.name} pieces") { AppModel.screen = Screen.Theme }
            Section("You")
            MoreRow(2, "Stats", "Ratings, records and puzzles") { AppModel.screen = Screen.Stats() }
            MoreRow(3, "Game History", "Every game, reviewed or not") { AppModel.screen = Screen.AllGames }
            if (AppModel.username != null) {
                MoreRow(4, "Chess.com account", "${AppModel.username} · tap to disconnect", danger = true) { confirmDisconnect = true }
            } else {
                MoreRow(5, "Chess.com account", "Not connected · connect on Home") { AppModel.screen = Screen.Home }
            }
            Section("About")
            MoreRow(6, "Source code", "github.com/Verisonder/SonderChess") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Verisonder/SonderChess")))
            }
            Text(
                "SonderChess ${BuildConfig.VERSION_NAME} · GPL-3.0 · Stockfish 17.1\nNot affiliated with Chess.com.",
                style = body(12, color = C.faint),
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = body(13, FontWeight.Bold, C.faint), modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
}

@Composable
private fun MoreRow(
    index: Int,
    title: String,
    detail: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .appear(index, "more" to index)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(C.panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = body(16, FontWeight.Bold))
            Text(detail, style = body(13, color = if (danger) C.error else C.faint))
        }
        Spacer(Modifier.width(8.dp))
        Text("›", style = body(22, color = C.dim))
    }
}
