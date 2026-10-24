package com.verisonder.sonderchess.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.core.Piece

/** What the Play tab offers past the bots: Stockfish, and the custom board. */
private const val ENGINE = 7
private const val CUSTOM = 8

/**
 * The Play tab: the chosen opponent greets you at the top, the bots below in a grid, then
 * Stockfish and the custom board. Pick a colour and press Play.
 */
@Composable
fun PlayTabScreen() {
    var pick by remember { mutableIntStateOf(AppModel.botLevel.coerceIn(0, CUSTOM)) }
    var colour by remember { mutableIntStateOf(AppModel.botColour) } // 0 white, 1 random, 2 black
    val bot =
        when (pick) {
            in bots.indices -> bots[pick]
            ENGINE -> engineBot
            else -> null
        }
    Column(Modifier.fillMaxSize()) {
        TopBar("Play", onBack = null)
        // The opponent says hello.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(pick, transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(initialScale = 0.85f)).togetherWith(fadeOut(tween(100)))
            }, label = "face") { p ->
                when (p) {
                    in bots.indices -> BotFace(bots[p], 76.dp)
                    ENGINE -> EngineChip(76.dp)
                    else -> Coach(76.dp)
                }
            }
            Spacer(Modifier.width(4.dp))
            Bubble(Modifier.weight(1f)) {
                AnimatedContent(pick, transitionSpec = { fadeIn(tween(160)).togetherWith(fadeOut(tween(100))) }, label = "hello") { p ->
                    Text(
                        when (p) {
                            in bots.indices -> bots[p].greeting
                            ENGINE -> engineBot.greeting
                            else -> "Move both sides, set up any position, and ask Stockfish."
                        },
                        style = body(15, FontWeight.Medium, C.bubbleText),
                    )
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(bot?.name ?: "Custom Board", style = display(24))
            Spacer(Modifier.width(10.dp))
            Text(bot?.rating ?: "Analysis", style = body(18, color = C.dim))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Section2("Bots")
            Grid(bots.size) { i -> Tile(i, pick == i, { pick = i }) { BotFace(bots[i], it) } }
            Section2("Engine")
            Grid(1) { Tile(ENGINE, pick == ENGINE, { pick = ENGINE }) { EngineChip(it) } }
            Section2("Board")
            Grid(1) { Tile(CUSTOM, pick == CUSTOM, { pick = CUSTOM }) { MiniBoardTile(it) } }
            Spacer(Modifier.height(12.dp))
        }
        // Colour and Play.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pick != CUSTOM) {
                Text("You play", style = body(15, FontWeight.Bold, C.dim), modifier = Modifier.weight(1f))
                for (c in 0..2) ColourBox(c, colour == c) { colour = c }
            } else {
                Text("Play both sides", style = body(15, FontWeight.Bold, C.dim))
            }
        }
        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
            GreenButton("Play", height = 56.dp) {
                AppModel.botLevel = pick
                AppModel.botColour = colour
                if (pick == CUSTOM) {
                    AppModel.screen = Screen.Play
                } else {
                    AppModel.botWhite =
                        when (colour) {
                            0 -> true
                            2 -> false
                            else -> Math.random() < 0.5
                        }
                    AppModel.screen = Screen.Bot
                }
            }
        }
    }
}

@Composable
private fun Section2(title: String) {
    Text(title, style = body(16, FontWeight.Bold, C.dim), modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
}

/** Five tiles to a row. */
@Composable
private fun Grid(
    count: Int,
    tile: @Composable (Int) -> Unit,
) {
    for (row in 0 until (count + 4) / 5) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in 0 until 5) {
                val i = row * 5 + c
                Box(Modifier.weight(1f)) { if (i < count) tile(i) }
            }
        }
    }
}

/** A square tile that lifts and gets a green edge when picked. */
@Composable
private fun Tile(
    key: Int,
    on: Boolean,
    onClick: () -> Unit,
    content: @Composable (Dp) -> Unit,
) {
    val edge by animateColorAsState(if (on) C.green else Color.Transparent, tween(160), label = "edge")
    val lift by animateFloatAsState(if (on) 1.06f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "lift")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .appear(key, "playtile" to key)
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }.clip(RoundedCornerShape(8.dp))
            .background(C.panel)
            .border(3.dp, edge, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(56.dp) }
}

/** White, random or black: a king on its colour, or a half-and-half square with a question mark. */
@Composable
private fun ColourBox(
    c: Int,
    on: Boolean,
    onClick: () -> Unit,
) {
    val painters = piecePainters()
    val edge by animateColorAsState(if (on) C.green else Color.Transparent, tween(160), label = "cedge")
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(3.dp, edge, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))) {
            when (c) {
                0 -> {
                    drawRect(Color(0xFFE8E6E1))
                    painters[6 + Piece.KING]?.run { draw(size) }
                }
                2 -> {
                    drawRect(Color(0xFF3C3A37))
                    painters[6 - Piece.KING]?.run { draw(size) }
                }
                else -> {
                    drawRect(Color(0xFFE8E6E1), size = Size(size.width / 2f, size.height))
                    drawRect(Color(0xFF3C3A37), Offset(size.width / 2f, 0f), Size(size.width / 2f, size.height))
                }
            }
        }
        if (c == 1) Text("?", style = display(26, Color(0xFF8B8987)))
    }
}

/**
 * A bot's face: a little pixel robot in its own colour. Each bot gets different eyes and a
 * different mouth, so they look like a family without being the same.
 */
@Composable
fun BotFace(
    bot: Bot,
    size: Dp,
) {
    if (bot.mark) {
        Coach(size)
        return
    }
    val i = bots.indexOf(bot).coerceAtLeast(0)
    val body = Color(bot.colour)
    val dark = lerp(body, Color.Black, 0.45f)
    val face = Color(0xFF26231F)
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 12f

        fun px(
            x: Float,
            y: Float,
            w: Float = 1f,
            h: Float = 1f,
            c: Color,
        ) = drawRect(c, Offset(x * u, y * u), Size(w * u, h * u))
        // Antenna.
        px(5.5f, 0.6f, 1f, 1.6f, dark)
        drawCircle(lerp(body, Color.White, 0.3f), 0.8f * u, Offset(6f * u, 0.8f * u))
        // Ears, head and the dark screen.
        px(0.8f, 4.6f, 1.2f, 3f, dark)
        px(10f, 4.6f, 1.2f, 3f, dark)
        drawRoundRect(dark, Offset(1.6f * u, 2.1f * u), Size(8.8f * u, 8.4f * u), CornerRadius(1.8f * u))
        drawRoundRect(body, Offset(1.9f * u, 2.2f * u), Size(8.2f * u, 7.8f * u), CornerRadius(1.6f * u))
        drawRoundRect(face, Offset(2.8f * u, 3.4f * u), Size(6.4f * u, 4.2f * u), CornerRadius(1f * u))
        val eye = Color.White
        when (i % 3) {
            0 -> { // round eyes
                px(4f, 4.4f, 1.3f, 1.6f, eye)
                px(6.7f, 4.4f, 1.3f, 1.6f, eye)
            }
            1 -> { // a visor
                px(3.6f, 4.8f, 4.8f, 0.9f, Color(0xFFFF5252))
            }
            else -> { // narrow, focused eyes
                px(3.8f, 4.9f, 1.6f, 0.7f, eye)
                px(6.6f, 4.9f, 1.6f, 0.7f, eye)
            }
        }
        // The mouth: a smile for the easy ones, flatter as they get stronger.
        when {
            i < 2 -> {
                px(4.4f, 6.4f, 0.6f, 0.5f, eye)
                px(5f, 6.8f, 2f, 0.4f, eye)
                px(7f, 6.4f, 0.6f, 0.5f, eye)
            }
            i < 4 -> px(4.6f, 6.6f, 2.8f, 0.4f, eye)
            else -> {
                for (k in 0..3) px(4.3f + k * 0.9f, 6.5f, 0.5f, 0.5f, eye)
            }
        }
        // Rivets and a crown of bolts for the strongest.
        px(2.4f, 8.6f, 0.6f, 0.6f, dark)
        px(9f, 8.6f, 0.6f, 0.6f, dark)
        if (i >= 5) for (k in 0..2) drawCircle(Color(0xFFFFD54F), 0.45f * u, Offset((4.6f + k * 1.4f) * u, 9.2f * u))
    }
}

/** Stockfish: a chip with "SF" on it. */
@Composable
private fun EngineChip(size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val u = this.size.width / 12f
            val pin = Color(0xFF8B8987)
            for (k in 0..3) {
                val p = 3f + k * 2f
                drawRect(pin, Offset(p * u, 0.8f * u), Size(0.9f * u, 1.6f * u))
                drawRect(pin, Offset(p * u, 9.6f * u), Size(0.9f * u, 1.6f * u))
                drawRect(pin, Offset(0.8f * u, p * u), Size(1.6f * u, 0.9f * u))
                drawRect(pin, Offset(9.6f * u, p * u), Size(1.6f * u, 0.9f * u))
            }
            drawRoundRect(Color(0xFF3C3A37), Offset(2.2f * u, 2.2f * u), Size(7.6f * u, 7.6f * u), CornerRadius(1.2f * u))
            drawRoundRect(
                C.green,
                Offset(2.2f * u, 2.2f * u),
                Size(7.6f * u, 7.6f * u),
                CornerRadius(1.2f * u),
                style =
                    androidx.compose.ui.graphics.drawscope.Stroke(
                        0.5f * u,
                    ),
            )
        }
        Text("SF", style = display((size.value / 4.4f).toInt(), C.text))
    }
}

/** The custom board: a 4x4 corner of your board with a knight on it. */
@Composable
private fun MiniBoardTile(size: Dp) {
    val knight = piecePainters()[6 + Piece.KNIGHT]
    Canvas(Modifier.size(size).clip(RoundedCornerShape(6.dp))) {
        val s = this.size.width / 4f
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
            androidx.compose.ui.graphics.drawscope
                .translate(s, s) { draw(Size(s * 2f, s * 2f)) }
        }
    }
}
