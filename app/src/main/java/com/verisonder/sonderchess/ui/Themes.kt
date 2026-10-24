package com.verisonder.sonderchess.ui

import androidx.compose.ui.graphics.Color
import com.verisonder.sonderchess.R

/** Board colours: light and dark squares. */
data class BoardTheme(
    val id: String,
    val name: String,
    val light: Color,
    val dark: Color,
)

/**
 * A piece set: drawables in the order P N B R Q K for White, then the same for Black.
 */
data class PieceSet(
    val id: String,
    val name: String,
    val white: List<Int>,
    val black: List<Int>,
)

object Themes {
    val boards =
        listOf(
            BoardTheme("green", "Green", Color(0xFFEBECD0), Color(0xFF739552)),
            BoardTheme("brown", "Brown", Color(0xFFF0D9B5), Color(0xFFB58863)),
            BoardTheme("walnut", "Walnut", Color(0xFFE8C99B), Color(0xFF9C6A3E)),
            BoardTheme("navy", "Navy", Color(0xFF6B7A90), Color(0xFF2A3346)),
            BoardTheme("ice", "Ice", Color(0xFFE6EEF5), Color(0xFF8FB2CF)),
            BoardTheme("grey", "Grey", Color(0xFFCFCFCF), Color(0xFF8C8C8C)),
            BoardTheme("sky", "Sky", Color(0xFFF1F4F8), Color(0xFFBCD0E3)),
            BoardTheme("stone", "Stone", Color(0xFFC9CCCF), Color(0xFF8F9499)),
            BoardTheme("slate", "Slate", Color(0xFF8A8A8A), Color(0xFF4E4E4E)),
            BoardTheme("coral", "Coral", Color(0xFFF7D6B0), Color(0xFFD4743A)),
            BoardTheme("tournament", "Tournament", Color(0xFFF5F5F5), Color(0xFF5E9A3A)),
            BoardTheme("sand", "Sand", Color(0xFFDDD5C0), Color(0xFF8E887C)),
            BoardTheme("purple", "Purple", Color(0xFFF0F0F2), Color(0xFF8977BA)),
            BoardTheme("night", "Night", Color(0xFF7A7A7A), Color(0xFF5A5A5A)),
            BoardTheme("emerald", "Emerald", Color(0xFFF1F1EC), Color(0xFF2E6B4E)),
            BoardTheme("oak", "Oak", Color(0xFFD9A867), Color(0xFF8A4F27)),
            BoardTheme("cherry", "Cherry", Color(0xFFF0C9A0), Color(0xFF9A3B26)),
            BoardTheme("marine", "Marine", Color(0xFFEFEDD8), Color(0xFF4C709A)),
            BoardTheme("pink", "Pink", Color(0xFFF5DDE3), Color(0xFFC77D95)),
            BoardTheme("red", "Red", Color(0xFFF2DCC8), Color(0xFFB8574A)),
        )

    val pieces =
        listOf(
            PieceSet(
                "neo",
                "Neo",
                listOf(
                    R.drawable.piece_wp,
                    R.drawable.piece_wn,
                    R.drawable.piece_wb,
                    R.drawable.piece_wr,
                    R.drawable.piece_wq,
                    R.drawable.piece_wk,
                ),
                listOf(
                    R.drawable.piece_bp,
                    R.drawable.piece_bn,
                    R.drawable.piece_bb,
                    R.drawable.piece_br,
                    R.drawable.piece_bq,
                    R.drawable.piece_bk,
                ),
            ),
            png("warm", "Warm"),
            png("classic", "Classic"),
            png("chessnut", "Chessnut"),
            png("merida", "Merida"),
            png("mono", "Mono"),
            png("spatial", "Spatial"),
        )

    private fun png(
        id: String,
        name: String,
    ): PieceSet {
        fun r(k: String) =
            when ("${id}_$k") {
                "warm_wp" -> R.drawable.piece_warm_wp
                "warm_wn" -> R.drawable.piece_warm_wn
                "warm_wb" -> R.drawable.piece_warm_wb
                "warm_wr" -> R.drawable.piece_warm_wr
                "warm_wq" -> R.drawable.piece_warm_wq
                "warm_wk" -> R.drawable.piece_warm_wk
                "warm_bp" -> R.drawable.piece_warm_bp
                "warm_bn" -> R.drawable.piece_warm_bn
                "warm_bb" -> R.drawable.piece_warm_bb
                "warm_br" -> R.drawable.piece_warm_br
                "warm_bq" -> R.drawable.piece_warm_bq
                "warm_bk" -> R.drawable.piece_warm_bk
                "classic_wp" -> R.drawable.piece_classic_wp
                "classic_wn" -> R.drawable.piece_classic_wn
                "classic_wb" -> R.drawable.piece_classic_wb
                "classic_wr" -> R.drawable.piece_classic_wr
                "classic_wq" -> R.drawable.piece_classic_wq
                "classic_wk" -> R.drawable.piece_classic_wk
                "classic_bp" -> R.drawable.piece_classic_bp
                "classic_bn" -> R.drawable.piece_classic_bn
                "classic_bb" -> R.drawable.piece_classic_bb
                "classic_br" -> R.drawable.piece_classic_br
                "classic_bq" -> R.drawable.piece_classic_bq
                "classic_bk" -> R.drawable.piece_classic_bk
                "chessnut_wp" -> R.drawable.piece_chessnut_wp
                "chessnut_wn" -> R.drawable.piece_chessnut_wn
                "chessnut_wb" -> R.drawable.piece_chessnut_wb
                "chessnut_wr" -> R.drawable.piece_chessnut_wr
                "chessnut_wq" -> R.drawable.piece_chessnut_wq
                "chessnut_wk" -> R.drawable.piece_chessnut_wk
                "chessnut_bp" -> R.drawable.piece_chessnut_bp
                "chessnut_bn" -> R.drawable.piece_chessnut_bn
                "chessnut_bb" -> R.drawable.piece_chessnut_bb
                "chessnut_br" -> R.drawable.piece_chessnut_br
                "chessnut_bq" -> R.drawable.piece_chessnut_bq
                "chessnut_bk" -> R.drawable.piece_chessnut_bk
                "merida_wp" -> R.drawable.piece_merida_wp
                "merida_wn" -> R.drawable.piece_merida_wn
                "merida_wb" -> R.drawable.piece_merida_wb
                "merida_wr" -> R.drawable.piece_merida_wr
                "merida_wq" -> R.drawable.piece_merida_wq
                "merida_wk" -> R.drawable.piece_merida_wk
                "merida_bp" -> R.drawable.piece_merida_bp
                "merida_bn" -> R.drawable.piece_merida_bn
                "merida_bb" -> R.drawable.piece_merida_bb
                "merida_br" -> R.drawable.piece_merida_br
                "merida_bq" -> R.drawable.piece_merida_bq
                "merida_bk" -> R.drawable.piece_merida_bk
                "mono_wp" -> R.drawable.piece_mono_wp
                "mono_wn" -> R.drawable.piece_mono_wn
                "mono_wb" -> R.drawable.piece_mono_wb
                "mono_wr" -> R.drawable.piece_mono_wr
                "mono_wq" -> R.drawable.piece_mono_wq
                "mono_wk" -> R.drawable.piece_mono_wk
                "mono_bp" -> R.drawable.piece_mono_bp
                "mono_bn" -> R.drawable.piece_mono_bn
                "mono_bb" -> R.drawable.piece_mono_bb
                "mono_br" -> R.drawable.piece_mono_br
                "mono_bq" -> R.drawable.piece_mono_bq
                "mono_bk" -> R.drawable.piece_mono_bk
                "spatial_wp" -> R.drawable.piece_spatial_wp
                "spatial_wn" -> R.drawable.piece_spatial_wn
                "spatial_wb" -> R.drawable.piece_spatial_wb
                "spatial_wr" -> R.drawable.piece_spatial_wr
                "spatial_wq" -> R.drawable.piece_spatial_wq
                "spatial_wk" -> R.drawable.piece_spatial_wk
                "spatial_bp" -> R.drawable.piece_spatial_bp
                "spatial_bn" -> R.drawable.piece_spatial_bn
                "spatial_bb" -> R.drawable.piece_spatial_bb
                "spatial_br" -> R.drawable.piece_spatial_br
                "spatial_bq" -> R.drawable.piece_spatial_bq
                else -> R.drawable.piece_spatial_bk
            }
        val order = listOf("p", "n", "b", "r", "q", "k")
        return PieceSet(id, name, order.map { r("w$it") }, order.map { r("b$it") })
    }

    fun board(id: String?) = boards.firstOrNull { it.id == id } ?: boards[0]

    fun pieceSet(id: String?) = pieces.firstOrNull { it.id == id } ?: pieces[0]
}
