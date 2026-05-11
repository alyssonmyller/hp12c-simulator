package com.arcom.hp12c.android.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
//  Tokens de design do skin
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Conjunto de tokens visuais que definem a aparência completa da calculadora.
 *
 * Um skin **nunca** altera o comportamento da engine — apenas tokens de cor.
 * Todos os composables leem o skin ativo via [LocalSkin].current, sem precisar
 * receber o skin como parâmetro explícito.
 */
data class Hp12cSkin(
    val name: String,

    // Corpo externo
    val body: Color,

    // Visor LCD
    val displayBg:    Color,
    val displayText:  Color,
    val indicatorOn:  Color,
    val indicatorOff: Color,

    // Teclas — fundos
    val keyNormal:    Color,
    val keyFinancial: Color,
    val keyF:         Color,
    val keyG:         Color,
    val keyOn:        Color,
    val keyEnter:     Color,

    // Textos
    val keyLabel:    Color,
    val fLabelColor: Color,
    val gLabelColor: Color,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Instâncias de skin disponíveis
// ─────────────────────────────────────────────────────────────────────────────

object Hp12cSkins {

    /** Replica a HP 12C Platinum física (dourado/champanhe, LCD verde). */
    val Classic = Hp12cSkin(
        name         = "Classic",
        body         = Color(0xFFC4A44A),
        displayBg    = Color(0xFF1A2C1A),
        displayText  = Color(0xFF90E050),
        indicatorOn  = Color(0xFF90E050),
        indicatorOff = Color(0xFF2A402A),
        keyNormal    = Color(0xFF2C2C2C),
        keyFinancial = Color(0xFF6A5828),
        keyF         = Color(0xFFCC7700),
        keyG         = Color(0xFF1F55BB),
        keyOn        = Color(0xFFAA1111),
        keyEnter     = Color(0xFF3A3A3A),
        keyLabel     = Color(0xFFFFFFFF),
        fLabelColor  = Color(0xFFFFAA44),
        gLabelColor  = Color(0xFF88AAFF),
    )

    /** Escuro/flat inspirado em Material3 Dark Theme. */
    val Modern = Hp12cSkin(
        name         = "Modern",
        body         = Color(0xFF111111),
        displayBg    = Color(0xFF0D1B2A),
        displayText  = Color(0xFF00E5FF),
        indicatorOn  = Color(0xFF00E5FF),
        indicatorOff = Color(0xFF1A2A3A),
        keyNormal    = Color(0xFF1E1E2E),
        keyFinancial = Color(0xFF16213E),
        keyF         = Color(0xFF7C3AED),
        keyG         = Color(0xFF0D9488),
        keyOn        = Color(0xFFE63946),
        keyEnter     = Color(0xFF252540),
        keyLabel     = Color(0xFFE2E8F0),
        fLabelColor  = Color(0xFFC084FC),
        gLabelColor  = Color(0xFF2DD4BF),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  CompositionLocal — injeção de skin na árvore de composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * `staticCompositionLocalOf` em vez de `compositionLocalOf`: mudanças de skin são
 * raras (toggle manual), então pagar a recomposição total da subárvore é mais simples
 * do que a observação line-by-line do `compositionLocalOf`.
 */
val LocalSkin = staticCompositionLocalOf { Hp12cSkins.Classic }
