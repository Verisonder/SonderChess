package com.verisonder.sonderchess.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.core.Explain
import com.verisonder.sonderchess.core.Label
import kotlin.math.roundToInt

/** The first page after analysis: coach, graph, accuracy, label counts. */
@Composable
fun SummaryScreen(o: Opened) {
    val r = o.review
    val me = o.saved.meWhite
    Column(Modifier.fillMaxSize()) {
        TopBar("Game Review", onBack = { AppModel.screen = Screen.Home })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Coach(60.dp)
                Spacer(Modifier.width(6.dp))
                Bubble(Modifier.weight(1f)) { Text(Explain.summary(r, me), style = body(15, FontWeight.Medium, C.bubbleText)) }
            }
            Spacer(Modifier.height(12.dp))
            val marks =
                r.moves
                    .filter { it.label in setOf(Label.Brilliant, Label.Great, Label.Miss, Label.Mistake, Label.Blunder) }
                    .map { it.ply to it.label }
            EvalGraph(r.evals, marks, null, onPick = { AppModel.screen = Screen.Board(o.saved.id, it - 1) })
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                RowTitle("Players")
                PlayerCell(o.saved.white.name, true, o.saved.white.avatar)
                Spacer(Modifier.width(28.dp))
                PlayerCell(o.saved.black.name, false, o.saved.black.avatar)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowTitle("Accuracy")
                CountedScore(r.accuracyWhite, true, Modifier.weight(1f), decimals = true)
                Spacer(Modifier.width(28.dp))
                CountedScore(r.accuracyBlack, false, Modifier.weight(1f), decimals = true)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            Spacer(Modifier.height(6.dp))
            for ((i, l) in Label.entries.withIndex()) {
                Row(Modifier.fillMaxWidth().height(36.dp).appear(i), verticalAlignment = Alignment.CenterVertically) {
                    RowTitle(l.title)
                    Count(r.count(true, l), l, Modifier.weight(1f))
                    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { LabelIcon(l, 22.dp) }
                    Count(r.count(false, l), l, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowTitle("Game Rating")
                CountedScore(r.rating(true).toDouble(), true, Modifier.weight(1f), decimals = false)
                Spacer(Modifier.width(28.dp))
                CountedScore(r.rating(false).toDouble(), false, Modifier.weight(1f), decimals = false)
            }
            r.opening?.let {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Top) {
                    RowTitle("Opening")
                    Text(it, style = body(14, FontWeight.Medium, C.dim), modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Depth ${o.saved.depth} · estimates, not official ratings",
                style = body(11, color = C.faint),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.padding(16.dp)) {
            GreenButton("Start Review") { AppModel.screen = Screen.Board(o.saved.id, -1) }
        }
    }
}

@Composable
private fun RowTitle(t: String) {
    Text(t, style = body(15, FontWeight.Bold, C.dim), modifier = Modifier.width(112.dp), maxLines = 1)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PlayerCell(
    name: String,
    white: Boolean,
    url: String?,
) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = body(13, FontWeight.Bold, C.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Avatar(name, white, 52.dp, url)
    }
}

@Composable
private fun CountedScore(
    value: Double,
    white: Boolean,
    modifier: Modifier,
    decimals: Boolean,
) {
    // Counts up from zero the first time the summary shows.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(value) { shown.animateTo(value.toFloat(), tween(900, delayMillis = 150, easing = FastOutSlowInEasing)) }
    val text = if (decimals) "%.1f".format(shown.value) else (shown.value.toInt() / 50 * 50).toString()
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(6.dp)).background(if (white) C.bubble else C.panel2),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = body(19, FontWeight.Bold, if (white) C.bubbleText else C.text)) }
}

@Composable
private fun Count(
    n: Int,
    l: Label,
    modifier: Modifier,
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(
        n,
    ) { shown.animateTo(n.toFloat(), tween(500 + n * 25, delayMillis = 250 + l.ordinal * 45, easing = FastOutSlowInEasing)) }
    val v = shown.value.roundToInt()
    Text(
        v.toString(),
        style = body(17, FontWeight.Bold, if (n == 0) Color(0xFF6E6C69) else l.colour()),
        modifier = modifier,
        textAlign = TextAlign.Center,
    )
}
