package com.arcom.hp12c.android.ui

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
    val body:       Color,
    val bodyEdge:   Color,   // borda/aresta lateral — sombra perimetral

    // Visor LCD
    val displayBezel:  Color,   // moldura escura ao redor do LCD
    val displayBg:     Color,   // fundo do painel LCD
    val displayText:   Color,
    val indicatorOn:   Color,
    val indicatorOff:  Color,

    // Teclas — fundos
    val keyNormal:       Color,
    val keyFinancial:    Color,
    val keyF:            Color,
    val keyG:            Color,
    val keyOn:           Color,
    val keyEnter:        Color,
    val keyTopHighlight: Color,  // reflexo superior que dá efeito 3D
    val keyBottomShadow: Color,  // sombra inferior que dá profundidade

    // Textos
    val keyLabel:    Color,
    val fLabelColor: Color,
    val gLabelColor: Color,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Instâncias de skin disponíveis
// ─────────────────────────────────────────────────────────────────────────────

object Hp12cSkins {

    /**
     * HP 12C Platinum — carcaça prateada/metálica, LCD verde-escuro fosforescente,
     * teclas pretas com labels laranja (f) e azul (g).
     * Referência visual: HP 12C Platinum física + screenshot do app de referência.
     */
    val Classic = Hp12cSkin(
        name             = "Platinum",
        // Corpo
        body             = Color(0xFF9E9EA2),   // prata metálico (HP Platinum)
        bodyEdge         = Color(0xFF6A6A6E),   // aresta mais escura
        // Display
        displayBezel     = Color(0xFF1A1A1A),   // moldura LCD quase preta
        displayBg        = Color(0xFF1E2A18),   // fundo LCD verde muito escuro
        displayText      = Color(0xFF92D040),   // fósforo verde LCD
        indicatorOn      = Color(0xFF92D040),
        indicatorOff     = Color(0xFF2C3C22),
        // Teclas
        keyNormal        = Color(0xFF1C1C1C),   // preto grafite
        keyFinancial     = Color(0xFF242420),   // levemente mais quente (TVM row)
        keyF             = Color(0xFFC05800),   // laranja HP
        keyG             = Color(0xFF0055AA),   // azul HP
        keyOn            = Color(0xFF2A2A2A),   // cinza escuro (ON é discreet)
        keyEnter         = Color(0xFF1C1C1C),   // igual normal
        keyTopHighlight  = Color(0x28FFFFFF),   // reflexo sutil no topo da tecla
        keyBottomShadow  = Color(0x44000000),   // sombra na base
        // Texto
        keyLabel         = Color(0xFFFFFFFF),
        fLabelColor      = Color(0xFFFF8C00),   // labels laranja acima das teclas
        gLabelColor      = Color(0xFF55AAFF),   // labels azul abaixo das teclas
    )

    /** Escuro/flat inspirado em Material3 Dark Theme. */
    val Modern = Hp12cSkin(
        name             = "Modern",
        body             = Color(0xFF111111),
        bodyEdge         = Color(0xFF000000),
        displayBezel     = Color(0xFF0D0D1A),
        displayBg        = Color(0xFF0D1B2A),
        displayText      = Color(0xFF00E5FF),
        indicatorOn      = Color(0xFF00E5FF),
        indicatorOff     = Color(0xFF1A2A3A),
        keyNormal        = Color(0xFF1E1E2E),
        keyFinancial     = Color(0xFF16213E),
        keyF             = Color(0xFF7C3AED),
        keyG             = Color(0xFF0D9488),
        keyOn            = Color(0xFFE63946),
        keyEnter         = Color(0xFF252540),
        keyTopHighlight  = Color(0x22FFFFFF),
        keyBottomShadow  = Color(0x55000000),
        keyLabel         = Color(0xFFE2E8F0),
        fLabelColor      = Color(0xFFC084FC),
        gLabelColor      = Color(0xFF2DD4BF),
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
