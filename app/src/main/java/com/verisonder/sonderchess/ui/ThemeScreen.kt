package com.verisonder.sonderchess.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.core.Move
import com.verisonder.sonderchess.core.Pgn
import com.verisonder.sonderchess.core.Position

private val tabs = listOf("Board", "Pieces", "Background", "Sounds", "More")

/** A middlegame for the preview, so arrows, highlights and sounds have something to show. */
private const val SAMPLE =
    "[FEN \"r4rk1/1bpR1p2/1p2pQp1/p1q1P2p/P1P4P/5N2/2P2PP1/3R2K1 w - - 0 1\"]\n\n1. Kh2 *"

/** Board, pieces, background, sounds and board options, with a live board on top. */
@Composable
fun ThemeScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sample = remember { runCatching { Pgn.parse(SAMPLE) }.getOrNull() }
    val start = remember { Position.start() }
    Box(Modifier.fillMaxSize()) {
        if (tab == 2) ReviewBackground()
        Column(Modifier.fillMaxSize()) {
            TopBar("Theme", onBack = { AppModel.screen = Screen.Home })
            Box(Modifier.padding(horizontal = 12.dp)) {
                if (tab >= 3 && sample != null) {
                    val before = sample.positions()[0]
                    val m: Move = sample.moves[0]
                    ChessBoard(
                        before.play(m),
                        last = m,
                        label = null,
                        arrows = if (AppModel.showArrows) listOf(m) else emptyList(),
                        flipped = false,
                    )
                } else {
                    ChessBoard(start, last = null, label = null, arrows = emptyList(), flipped = false)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                for ((i, t) in tabs.withIndex()) {
                    val on = tab == i
                    val line by animateColorAsState(if (on) C.text else C.line, tween(180), label = "tab")
                    Column(Modifier.clickable { tab = i }.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            t,
                            style = body(18, FontWeight.Bold, if (on) C.text else C.faint),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                        Box(Modifier.width(96.dp).height(if (on) 3.dp else 1.dp).background(line))
                    }
                }
            }
            androidx.compose.animation.Crossfade(tab, animationSpec = tween(220), label = "themeTab") { t ->
                Column {
                    when (t) {
                        0 -> BoardTab()
                        1 -> PiecesTab()
                        2 -> BackgroundTab()
                        3 -> SoundsTab()
                        else -> MoreTab()
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(t: String) {
    Text(
        t,
        style = body(17, FontWeight.Bold, C.dim),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BoardTab() {
    Title(AppModel.board.name)
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(Themes.boards, key = { it.id }) { t ->
            Swatch(selected = t.id == AppModel.board.id, onClick = { AppModel.useBoard(t) }) {
                Canvas(Modifier.fillMaxSize()) {
                    val h = size.width / 2f
                    drawRect(t.light, Offset.Zero, Size(h, h))
                    drawRect(t.dark, Offset(h, 0f), Size(h, h))
                    drawRect(t.dark, Offset(0f, h), Size(h, h))
                    drawRect(t.light, Offset(h, h), Size(h, h))
                }
            }
        }
    }
}

@Composable
private fun PiecesTab() {
    Title(AppModel.pieces.name)
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(Themes.pieces, key = { it.id }) { p ->
            Swatch(selected = p.id == AppModel.pieces.id, onClick = { AppModel.usePieces(p) }) {
                Box(Modifier.fillMaxSize().background(C.bar), contentAlignment = Alignment.Center) {
                    Image(painterResource(p.white[1]), p.name, Modifier.fillMaxSize().padding(5.dp))
                }
            }
        }
    }
}

@Composable
private fun BackgroundTab() {
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) AppModel.saveBackground(uri)
        }
    Title(if (AppModel.background == "custom") "Your picture" else Backgrounds.style(AppModel.background).name)
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "custom") {
            Swatch(selected = AppModel.background == "custom", onClick = {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Box(Modifier.fillMaxSize().background(C.bar), contentAlignment = Alignment.Center) { AddPictureIcon() }
            }
        }
        items(Backgrounds.styles, key = { it.id }) { b ->
            Swatch(selected = b.id == AppModel.background, onClick = { AppModel.useBackground(b.id) }) {
                Box(Modifier.fillMaxSize().background(b.brush))
            }
        }
    }
}

@Composable
private fun SoundsTab() {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        items(Sounds.sets) { (id, name) ->
            val on = AppModel.soundSet == id
            Row(
                Modifier.fillMaxWidth().height(64.dp).clickable { AppModel.useSoundSet(id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) { if (on) CheckIcon() }
                Text(
                    name,
                    style = body(18, if (on) FontWeight.Bold else FontWeight.Normal, if (on) C.text else C.dim),
                    modifier = Modifier.weight(1f),
                )
                if (id != "off") {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(C.panel2)
                            .clickable { playSample(id) },
                        contentAlignment = Alignment.Center,
                    ) { PlayIcon() }
                }
            }
        }
    }
}

/** Plays a short run of a set's sounds: move, capture, check. */
private fun playSample(id: String) {
    Sounds.preload(id)
    val h = android.os.Handler(android.os.Looper.getMainLooper())
    h.postDelayed({ Sounds.play(SoundEvent.Move, id) }, 60)
    h.postDelayed({ Sounds.play(SoundEvent.Capture, id) }, 520)
    h.postDelayed({ Sounds.play(SoundEvent.Check, id) }, 980)
}

@Composable
private fun MoreTab() {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Toggle("Coordinates", "Letters and numbers on the board edge", AppModel.coordinates) { AppModel.useCoordinates(it) }
            Toggle(
                "Highlight the last move",
                "Colour the squares it moved between",
                AppModel.highlightLast,
            ) { AppModel.useHighlightLast(it) }
            Toggle("Show best-move arrows", "On when a review opens", AppModel.showArrows) { AppModel.useShowArrows(it) }
            Spacer(Modifier.height(14.dp))
            Text("Piece animation", style = body(16, FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            SlidingSegments(listOf("Off", "Slow", "Normal", "Fast"), AppModel.animation, height = 40.dp) { AppModel.useAnimation(it) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Toggle(
    title: String,
    sub: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = body(16, FontWeight.Bold))
            Text(sub, style = body(13, color = C.faint))
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = C.green, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun Swatch(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val ring by animateColorAsState(if (selected) C.green else Color.Transparent, tween(180), label = "ring")
    Box(
        Modifier
            .aspectRatio(1f)
            .border(3.dp, ring, RoundedCornerShape(10.dp))
            .padding(5.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onClick),
    ) { content() }
}

@Composable
private fun CheckIcon() {
    Canvas(Modifier.size(26.dp)) {
        val p =
            Path().apply {
                moveTo(size.width * 0.15f, size.height * 0.52f)
                lineTo(size.width * 0.4f, size.height * 0.77f)
                lineTo(size.width * 0.88f, size.height * 0.25f)
            }
        drawPath(p, C.green, style = Stroke(size.width * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun PlayIcon() {
    Canvas(Modifier.size(18.dp)) {
        val p =
            Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.1f)
                lineTo(size.width * 0.9f, size.height * 0.5f)
                lineTo(size.width * 0.2f, size.height * 0.9f)
                close()
            }
        drawPath(p, C.text)
    }
}

@Composable
private fun AddPictureIcon() {
    Canvas(Modifier.size(30.dp)) {
        val u = size.width / 24f
        val st = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            C.dim,
            Offset(2f * u, 4f * u),
            Size(15f * u, 13f * u),
            androidx.compose.ui.geometry
                .CornerRadius(2f * u),
            style = st,
        )
        drawCircle(C.dim, 1.6f * u, Offset(7f * u, 8.5f * u))
        val m =
            Path().apply {
                moveTo(3f * u, 16f * u)
                lineTo(8f * u, 11.5f * u)
                lineTo(11f * u, 14f * u)
                lineTo(16f * u, 10f * u)
            }
        drawPath(m, C.dim, style = st)
        drawCircle(C.dim, 5f * u, Offset(18f * u, 18f * u))
        drawLine(C.bar, Offset(18f * u, 15.6f * u), Offset(18f * u, 20.4f * u), 1.8f * u, StrokeCap.Round)
        drawLine(C.bar, Offset(15.6f * u, 18f * u), Offset(20.4f * u, 18f * u), 1.8f * u, StrokeCap.Round)
    }
}
