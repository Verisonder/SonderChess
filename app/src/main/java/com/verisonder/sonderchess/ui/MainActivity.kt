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
                    val o = AppModel.opened
                    when (s) {
                        Screen.Home -> HomeScreen()
                        is Screen.Loading -> LoadingScreen(s.message)
                        is Screen.Analysing -> AnalysingScreen(s)
                        is Screen.PickSide -> PickSideScreen(s.fetched)
                        is Screen.Failed -> FailedScreen(s.message)
                        is Screen.Summary -> if (o != null && o.saved.id == s.id) SummaryScreen(o) else HomeScreen()
                        is Screen.Board -> if (o != null && o.saved.id == s.id) ReviewScreen(o, s.ply) else HomeScreen()
                    }
                }
            }
        }
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
