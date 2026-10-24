package com.verisonder.sonderchess.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.verisonder.sonderchess.R
import com.verisonder.sonderchess.core.Move
import com.verisonder.sonderchess.core.Piece
import com.verisonder.sonderchess.core.Position

/** What happened on the board, which picks the sound. */
enum class SoundEvent { Move, Capture, Castle, Check, Promote, End, Brilliant }

/**
 * Board sounds. All of them are synthesised by tools/make_sounds.py; five sets, seven events each.
 */
object Sounds {
    val sets =
        listOf(
            "default" to "Default",
            "nature" to "Nature",
            "metal" to "Metal",
            "marble" to "Marble",
            "space" to "Space",
            "off" to "Off",
        )

    private val res =
        mapOf(
            "default" to
                listOf(
                    R.raw.snd_default_move,
                    R.raw.snd_default_capture,
                    R.raw.snd_default_castle,
                    R.raw.snd_default_check,
                    R.raw.snd_default_promote,
                    R.raw.snd_default_end,
                    R.raw.snd_default_brilliant,
                ),
            "nature" to
                listOf(
                    R.raw.snd_nature_move,
                    R.raw.snd_nature_capture,
                    R.raw.snd_nature_castle,
                    R.raw.snd_nature_check,
                    R.raw.snd_nature_promote,
                    R.raw.snd_nature_end,
                    R.raw.snd_nature_brilliant,
                ),
            "metal" to
                listOf(
                    R.raw.snd_metal_move,
                    R.raw.snd_metal_capture,
                    R.raw.snd_metal_castle,
                    R.raw.snd_metal_check,
                    R.raw.snd_metal_promote,
                    R.raw.snd_metal_end,
                    R.raw.snd_metal_brilliant,
                ),
            "marble" to
                listOf(
                    R.raw.snd_marble_move,
                    R.raw.snd_marble_capture,
                    R.raw.snd_marble_castle,
                    R.raw.snd_marble_check,
                    R.raw.snd_marble_promote,
                    R.raw.snd_marble_end,
                    R.raw.snd_marble_brilliant,
                ),
            "space" to
                listOf(
                    R.raw.snd_space_move,
                    R.raw.snd_space_capture,
                    R.raw.snd_space_castle,
                    R.raw.snd_space_check,
                    R.raw.snd_space_promote,
                    R.raw.snd_space_end,
                    R.raw.snd_space_brilliant,
                ),
        )

    private var pool: SoundPool? = null
    private val ids = HashMap<String, IntArray>()
    private lateinit var app: Context

    fun init(context: Context) {
        if (pool != null) return
        app = context.applicationContext
        pool =
            SoundPool
                .Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                ).build()
    }

    /** Loads a set on first use (a few milliseconds; the very first play of a set may be silent). */
    private fun load(set: String): IntArray? {
        val p = pool ?: return null
        ids[set]?.let { return it }
        val r = res[set] ?: return null
        return IntArray(r.size) { p.load(app, r[it], 1) }.also { ids[set] = it }
    }

    fun preload(set: String) {
        load(set)
    }

    fun play(
        event: SoundEvent,
        set: String = AppModel.soundSet,
    ) {
        if (set == "off") return
        val a = load(set) ?: return
        pool?.play(a[event.ordinal], 1f, 1f, 1, 0, 1f)
    }

    /** The sound for [m] played from [before]. */
    fun eventFor(
        before: Position,
        m: Move,
    ): SoundEvent {
        val after = before.play(m)
        return when {
            after.legalMoves().isEmpty() -> SoundEvent.End
            after.inCheck() -> SoundEvent.Check
            m.promo != Piece.NONE -> SoundEvent.Promote
            before.isCastle(m) -> SoundEvent.Castle
            before.isCapture(m) -> SoundEvent.Capture
            else -> SoundEvent.Move
        }
    }
}
