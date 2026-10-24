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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.BuildConfig

/** The three pages with the bar along the bottom. */
fun isTab(s: Screen) = s == Screen.Home || s == Screen.Puzzles || s == Screen.More

/**
 * Home, Puzzles and More, with the tab bar under them and, on Home, the big Play button
 * above it (it opens the analysis board).
 */
@Composable
fun TabScaffold(current: Screen) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Crossfade(current, animationSpec = tween(180), label = "tab") { tab ->
                when (tab) {
                    Screen.Puzzles -> PuzzlesScreen()
                    Screen.More -> MoreScreen()
                    else -> HomeScreen()
                }
            }
        }
        if (current == Screen.Home) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                GreenButton("Play", height = 58.dp) { AppModel.screen = Screen.Play }
            }
        }
        TabBar(current)
    }
}

@Composable
private fun TabBar(current: Screen) {
    Row(Modifier.fillMaxWidth().background(C.bar).padding(vertical = 6.dp)) {
        Tab("Home", "home", current == Screen.Home, Modifier.weight(1f)) { AppModel.screen = Screen.Home }
        Tab("Puzzles", "puzzles", current == Screen.Puzzles, Modifier.weight(1f)) { AppModel.screen = Screen.Puzzles }
        Tab("More", "more", current == Screen.More, Modifier.weight(1f)) { AppModel.screen = Screen.More }
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
    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(28.dp)) {
            val u = size.width / 24f
            when (icon) {
                "home" -> {
                    // A pawn.
                    drawCircle(col, 3.6f * u, Offset(12f * u, 6.2f * u))
                    val p =
                        Path().apply {
                            moveTo(9.2f * u, 10f * u)
                            lineTo(14.8f * u, 10f * u)
                            lineTo(16.6f * u, 17f * u)
                            lineTo(7.4f * u, 17f * u)
                            close()
                        }
                    drawPath(p, col)
                    drawRoundRect(
                        col,
                        Offset(5.5f * u, 17.5f * u),
                        Size(13f * u, 3.6f * u),
                        androidx.compose.ui.geometry
                            .CornerRadius(1.2f * u),
                    )
                }
                "puzzles" -> {
                    // A jigsaw piece.
                    val p =
                        Path().apply {
                            moveTo(4f * u, 8f * u)
                            lineTo(9f * u, 8f * u)
                            cubicTo(8f * u, 3f * u, 15f * u, 3f * u, 14f * u, 8f * u)
                            lineTo(19f * u, 8f * u)
                            lineTo(19f * u, 12f * u)
                            cubicTo(23.5f * u, 11f * u, 23.5f * u, 18f * u, 19f * u, 17f * u)
                            lineTo(19f * u, 21f * u)
                            lineTo(4f * u, 21f * u)
                            close()
                        }
                    drawPath(p, col)
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
            MoreRow("Theme", "${AppModel.board.name} board · ${AppModel.pieces.name} pieces") { AppModel.screen = Screen.Theme }
            Section("You")
            MoreRow("Stats", "Ratings, records and puzzles") { AppModel.screen = Screen.Stats() }
            MoreRow("Game History", "Every game, reviewed or not") { AppModel.screen = Screen.AllGames }
            if (AppModel.username != null) {
                MoreRow("Chess.com account", "${AppModel.username} · tap to disconnect", danger = true) { confirmDisconnect = true }
            } else {
                MoreRow("Chess.com account", "Not connected · connect on Home") { AppModel.screen = Screen.Home }
            }
            Section("About")
            MoreRow("Source code", "github.com/Verisonder/SonderChess") {
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
    title: String,
    detail: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
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
