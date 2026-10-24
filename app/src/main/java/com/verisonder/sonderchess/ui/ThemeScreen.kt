package com.verisonder.sonderchess.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.core.Position

/** Board colours and piece set, with a live board on top. */
@Composable
fun ThemeScreen() {
    var tab by remember { mutableIntStateOf(0) }
    val start = remember { Position.start() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Theme", onBack = { AppModel.screen = Screen.Home })
        Box(Modifier.padding(horizontal = 12.dp)) {
            ChessBoard(start, last = null, label = null, arrows = emptyList(), flipped = false)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            for ((i, t) in listOf("Board", "Pieces").withIndex()) {
                val on = tab == i
                val line by animateColorAsState(if (on) C.text else C.line, tween(180), label = "tab")
                Column(
                    Modifier.weight(1f).clickable { tab = i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(t, style = body(18, FontWeight.Bold, if (on) C.text else C.faint), modifier = Modifier.padding(vertical = 10.dp))
                    Box(Modifier.fillMaxWidth().height(if (on) 3.dp else 1.dp).background(line))
                }
            }
        }
        val title = if (tab == 0) AppModel.board.name else AppModel.pieces.name
        Text(
            title,
            style = body(17, FontWeight.Bold, C.dim),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (tab == 0) 5 else 4),
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tab == 0) {
                items(Themes.boards, key = { it.id }) { t ->
                    Swatch(selected = t.id == AppModel.board.id, onClick = { AppModel.setBoard(t) }) {
                        Canvas(Modifier.fillMaxSize()) {
                            val h = size.width / 2f
                            drawRect(t.light, Offset.Zero, Size(h, h))
                            drawRect(t.dark, Offset(h, 0f), Size(h, h))
                            drawRect(t.dark, Offset(0f, h), Size(h, h))
                            drawRect(t.light, Offset(h, h), Size(h, h))
                        }
                    }
                }
            } else {
                items(Themes.pieces, key = { it.id }) { p ->
                    Swatch(selected = p.id == AppModel.pieces.id, onClick = { AppModel.setPieces(p) }) {
                        Box(Modifier.fillMaxSize().background(AppModel.board.dark), contentAlignment = Alignment.Center) {
                            Image(painterResource(p.white[1]), p.name, Modifier.fillMaxSize().padding(4.dp))
                        }
                    }
                }
            }
        }
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
