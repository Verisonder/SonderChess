package com.verisonder.sonderchess.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verisonder.sonderchess.BuildConfig
import java.io.File

/**
 * Keeps the error when the app crashes, and shows it the next time it opens, ready to copy,
 * so a crash can be reported exactly. Nothing leaves the phone unless you send it.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                file(app).writeText(
                    "SonderChess ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · " +
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n\n" + e.stackTraceToString(),
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }

    @Composable
    fun Report(context: Context) {
        var text by remember { mutableStateOf(runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()) }
        val t = text ?: return

        fun close() {
            runCatching { file(context).delete() }
            text = null
        }
        AlertDialog(
            onDismissRequest = ::close,
            containerColor = C.panel,
            title = { Text("SonderChess closed unexpectedly", style = display(18)) },
            text = {
                Text(
                    t,
                    style = body(11, color = C.dim),
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("SonderChess crash", t))
                    close()
                }) { Text("Copy", style = body(15, FontWeight.Bold, C.green)) }
            },
            dismissButton = { TextButton(onClick = ::close) { Text("Close", style = body(15, FontWeight.Bold, C.dim)) } },
        )
    }
}
