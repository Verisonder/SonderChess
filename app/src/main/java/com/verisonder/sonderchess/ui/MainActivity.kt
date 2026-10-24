package com.verisonder.sonderchess.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(C.bg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(C.bar.toArgb()),
        )
        super.onCreate(savedInstanceState)
        AppModel.init(this)
        if (savedInstanceState == null) handle(intent)
        setContent {
            ChessTheme {
                Box(Modifier.fillMaxSize().background(C.bg).safeDrawingPadding()) {
                    val s = AppModel.screen
                    BackHandler(enabled = s != Screen.Home) {
                        AppModel.screen =
                            when (s) {
                                is Screen.Board -> Screen.Summary(s.id)
                                is Screen.Analysing, is Screen.Loading -> {
                                    AppModel.cancel()
                                    Screen.Home
                                }
                                else -> Screen.Home
                            }
                    }
                    AnimatedContent(
                        targetState = s,
                        contentKey = { if (isTab(it)) "tabs" else it::class },
                        transitionSpec = {
                            val deeper = depthOf(targetState) >= depthOf(initialState)
                            val dir = if (deeper) 1 else -1
                            (slideInHorizontally(tween(260)) { w -> dir * w / 5 } + fadeIn(tween(220)))
                                .togetherWith(slideOutHorizontally(tween(220)) { w -> -dir * w / 8 } + fadeOut(tween(160)))
                        },
                        label = "screen",
                    ) { st ->
                        val o = AppModel.opened
                        when (st) {
                            Screen.Home, Screen.PlayTab, Screen.Puzzles, Screen.Paste, Screen.More -> TabScaffold(st)
                            Screen.Play -> PlayScreen()
                            Screen.Bot -> BotScreen()
                            Screen.AllGames -> AllGamesScreen()
                            Screen.Theme -> ThemeScreen()
                            is Screen.Stats -> StatsScreen(st.open)
                            is Screen.Game -> AppModel.viewing?.takeIf { it.id == st.id }?.let { GameScreen(it) } ?: HomeScreen()
                            is Screen.Loading -> LoadingScreen(st.message)
                            is Screen.Analysing -> AnalysingScreen(AppModel.screen as? Screen.Analysing ?: st)
                            is Screen.PickSide -> PickSideScreen(st.fetched)
                            is Screen.ConfirmMe -> ConfirmMeScreen(st.fetched, st.white)
                            is Screen.Failed -> FailedScreen(st.message)
                            is Screen.Summary -> if (o != null && o.saved.id == st.id) SummaryScreen(o) else HomeScreen()
                            is Screen.Board -> if (o != null && o.saved.id == st.id) ReviewScreen(o, st.ply) else HomeScreen()
                        }
                    }
                }
            }
        }
    }

    private fun depthOf(s: Screen) =
        when (s) {
            Screen.Home, Screen.PlayTab, Screen.Puzzles, Screen.Paste, Screen.More -> 0
            is Screen.Game -> 2
            is Screen.Summary -> 3
            is Screen.Board -> 4
            else -> 1
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val text: String? =
            when (intent.action) {
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT) ?: streamUri(intent)?.let { read(it) }
                Intent.ACTION_VIEW -> intent.data?.let { u -> if (u.scheme == "content" || u.scheme == "file") read(u) else u.toString() }
                else -> null
            }
        if (!text.isNullOrBlank()) AppModel.handleText(text)
    }

    @Suppress("DEPRECATION")
    private fun streamUri(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >=
            33
        ) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun read(u: Uri): String? =
        runCatching {
            contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText().take(2_000_000) }
        }.getOrNull()
}
