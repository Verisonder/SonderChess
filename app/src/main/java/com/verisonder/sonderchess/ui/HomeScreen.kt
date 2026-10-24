package com.verisonder.sonderchess.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.R
import com.verisonder.sonderchess.data.FetchedGame
import com.verisonder.sonderchess.data.SavedGame
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen() {
    val clipboard = LocalClipboardManager.current
    var depth by remember { mutableIntStateOf(AppModel.depth) }
    var deleting by remember { mutableStateOf<SavedGame?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.piece_wn), null, Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                Text("SonderChess", style = display(26))
            }
        }
        item {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.panel)
                    .padding(18.dp),
            ) {
                Text("Review a game", style = body(18, FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                Text(
                    "On Chess.com, finish a game and tap the share button at the top. Pick SonderChess and the review starts.",
                    style = body(14, color = C.dim),
                )
                Spacer(Modifier.height(14.dp))
                GreenButton("Paste link or PGN", height = 50.dp) {
                    val t = clipboard.getText()?.text.orEmpty()
                    if (t.isBlank()) {
                        AppModel.screen =
                            Screen.Failed(
                                "The clipboard is empty. Copy a Chess.com game link or a PGN first.",
                            )
                    } else {
                        AppModel.handleText(t)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Analysis depth", style = body(13, FontWeight.Bold, C.faint))
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(C.panel)
                        .padding(4.dp),
                ) {
                    for ((d, name) in listOf(12 to "Fast", 16 to "Normal", 20 to "Deep")) {
                        val on = depth == d
                        Box(
                            Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) C.panel2 else C.panel)
                                .clickable {
                                    depth = d
                                    AppModel.setDepth(d)
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(name, style = body(14, FontWeight.Bold, if (on) C.text else C.faint)) }
                    }
                }
            }
        }
        if (AppModel.history.isNotEmpty()) {
            item {
                Text(
                    "Your reviews",
                    style = body(13, FontWeight.Bold, C.faint),
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
        }
        items(AppModel.history, key = { it.id }) { g -> HistoryRow(g, onLong = { deleting = g }) }
        item { Spacer(Modifier.height(32.dp)) }
    }
    deleting?.let { g ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this review?") },
            text = { Text("${g.white.name} vs ${g.black.name}") },
            confirmButton = {
                TextButton(onClick = {
                    AppModel.delete(g.id)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
            containerColor = C.panel,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    g: SavedGame,
    onLong: () -> Unit,
) {
    val mine = if (g.meWhite) g.accuracyWhite else g.accuracyBlack
    val r = g.game.result
    val won = (r == "1-0" && g.meWhite) || (r == "0-1" && !g.meWhite)
    val lost = (r == "1-0" && !g.meWhite) || (r == "0-1" && g.meWhite)
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(C.panel)
            .combinedClickable(onClick = { AppModel.open(g) }, onLongClick = onLong)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (won) {
                        C.green
                    } else if (lost) {
                        C.error
                    } else {
                        C.faint
                    },
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${g.white.name} vs ${g.black.name}", style = body(15, FontWeight.Bold), maxLines = 1)
            Text(
                "$r · ${DateFormat.getDateInstance(
                    DateFormat.MEDIUM,
                ).format(Date(g.savedAt))} · ${g.game.moves.size / 2 + g.game.moves.size % 2} moves",
                style = body(12, color = C.faint),
            )
        }
        Box(
            Modifier.clip(RoundedCornerShape(6.dp)).background(C.bubble).padding(horizontal = 10.dp, vertical = 6.dp),
        ) { Text("%.1f".format(mine), style = body(15, FontWeight.Bold, C.bubbleText)) }
    }
}

@Composable
fun AnalysingScreen(s: Screen.Analysing) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Coach(88.dp)
        Spacer(Modifier.height(20.dp))
        Text("Analysing your game", style = display(20))
        Spacer(Modifier.height(6.dp))
        Text("${s.white} vs ${s.black}", style = body(14, color = C.dim), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { if (s.total == 0) 0f else s.done.toFloat() / s.total },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = C.green,
            trackColor = C.panel2,
        )
        Spacer(Modifier.height(10.dp))
        Text("Position ${s.done} of ${s.total}", style = body(13, color = C.faint))
        Spacer(Modifier.height(32.dp))
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
                Avatar(p.name, white, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(p.name, style = body(16, FontWeight.Bold))
                    Text((if (white) "White" else "Black") + (p.rating?.let { " · $it" } ?: ""), style = body(13, color = C.faint))
                }
            }
        }
    }
}
