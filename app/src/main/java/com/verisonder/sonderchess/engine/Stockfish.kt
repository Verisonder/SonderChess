package com.verisonder.sonderchess.engine

import android.content.Context
import com.verisonder.sonderchess.core.EngineLine
import com.verisonder.sonderchess.core.PositionEval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Stockfish running as a child process, spoken to over UCI. The binary is installed as
 * libstockfish.so in the app's native library folder (the only app folder Android lets us run
 * programs from).
 */
class Stockfish private constructor(
    private val process: Process,
) : AutoCloseable {
    private val input = OutputStreamWriter(process.outputStream)
    private val output = BufferedReader(InputStreamReader(process.inputStream))

    @Synchronized
    private fun send(cmd: String) {
        input.write(cmd)
        input.write("\n")
        input.flush()
    }

    private fun readUntil(
        prefix: String,
        onLine: (String) -> Unit = {},
    ): String {
        while (true) {
            val line = output.readLine() ?: throw IllegalStateException("Stockfish stopped")
            onLine(line)
            if (line.startsWith(prefix)) return line
        }
    }

    private fun handshake(
        threads: Int,
        hashMb: Int,
    ) {
        send("uci")
        readUntil("uciok")
        send("setoption name Threads value $threads")
        send("setoption name Hash value $hashMb")
        send("setoption name MultiPV value 2")
        send("isready")
        readUntil("readyok")
    }

    fun newGame() {
        send("ucinewgame")
        send("isready")
        readUntil("readyok")
    }

    /** Ends the search in progress early; [analyse] then returns what it has. */
    fun stop() = send("stop")

    /**
     * Analyses [fen] to [depth] and returns up to two lines, scores from White's side.
     * [onUpdate], if given, hears the lines as they deepen. Blocking: call from a background thread.
     */
    fun analyse(
        fen: String,
        depth: Int,
        onUpdate: ((PositionEval) -> Unit)? = null,
    ): PositionEval {
        val whiteToMove = fen.split(' ').getOrNull(1) != "b"
        val lines = arrayOfNulls<EngineLine>(2)
        var reached = 0
        send("position fen $fen")
        send("go depth $depth")
        readUntil("bestmove") { line ->
            if (!line.startsWith("info ") || !line.contains(" pv ")) return@readUntil
            val t = line.split(' ')
            val d = t.valueAfter("depth")?.toIntOrNull() ?: return@readUntil
            val pv = (t.valueAfter("multipv")?.toIntOrNull() ?: 1) - 1
            if (pv !in 0..1) return@readUntil
            val si = t.indexOf("score")
            if (si < 0 || si + 2 >= t.size) return@readUntil
            val move = t.valueAfter("pv") ?: return@readUntil
            val v = t[si + 2].toIntOrNull() ?: return@readUntil
            val sign = if (whiteToMove) 1 else -1
            lines[pv] = if (t[si + 1] == "mate") EngineLine(move, mate = v * sign) else EngineLine(move, cp = v * sign)
            reached = maxOf(reached, d)
            onUpdate?.invoke(PositionEval(lines.filterNotNull(), reached))
        }
        return PositionEval(lines.filterNotNull(), reached)
    }

    /**
     * A move for a bot: [skill] 0..20 (Stockfish's Skill Level) or, when [elo] is set, a
     * strength limited to that rating; thinks for [movetimeMs]. Blocking.
     */
    fun bestMove(
        fen: String,
        skill: Int,
        elo: Int?,
        movetimeMs: Int,
    ): String? {
        send("setoption name MultiPV value 1")
        send("setoption name Skill Level value $skill")
        if (elo != null) {
            send("setoption name UCI_LimitStrength value true")
            send("setoption name UCI_Elo value $elo")
        } else {
            send("setoption name UCI_LimitStrength value false")
        }
        send("position fen $fen")
        send("go movetime $movetimeMs")
        return readUntil("bestmove").split(' ').getOrNull(1)?.takeIf { it != "(none)" }
    }

    override fun close() {
        runCatching { send("quit") }
        runCatching { process.waitFor() }
        process.destroy()
    }

    companion object {
        fun binary(context: Context) = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")

        suspend fun start(
            context: Context,
            threads: Int = defaultThreads(),
            hashMb: Int = 64,
        ): Stockfish =
            withContext(Dispatchers.IO) {
                val bin = binary(context)
                require(bin.exists()) { "The chess engine is missing from this build." }
                val p = ProcessBuilder(bin.absolutePath).redirectErrorStream(true).start()
                Stockfish(p).also { it.handshake(threads, hashMb) }
            }

        /** Leave a core free so the phone stays responsive. */
        fun defaultThreads() = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)

        private fun List<String>.valueAfter(key: String): String? {
            val i = indexOf(key)
            return if (i >= 0 && i + 1 < size) this[i + 1] else null
        }
    }
}
