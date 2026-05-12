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

    // Corpo externo (moldura prateada/metálica)
    val body:       Color,
    val bodyEdge:   Color,   // borda/aresta lateral — sombra perimetral

    // Painel do teclado (área preta abaixo do display — painel separado da moldura)
    val keyboardPanel: Color,

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
        // Corpo (moldura superior prateada/alumínio — igual à calculadora física)
        body             = Color(0xFFB8B8BC),   // alumínio claro
        bodyEdge         = Color(0xFF787880),   // aresta/sombra da moldura
        // Painel do teclado (área preta fosca que fica ABAIXO do display)
        keyboardPanel    = Color(0xFF141414),   // preto fosco quase puro
        // Display LCD — fundo claro como a HP 12C Platinum física (LCD reflexivo)
        displayBezel     = Color(0xFF888890),   // moldura prateada em volta do LCD
        displayBg        = Color(0xFFCDD4C0),   // verde-acinzentado claro (LCD platinum)
        displayText      = Color(0xFF1A2A10),   // dígitos verde-escuro (segmentos LCD)
        indicatorOn      = Color(0xFF1A2A10),
        indicatorOff     = Color(0xFFA4AE98),   // indicadores apagados (cinza-verde)
        // Teclas: escuras sobre painel preto — contraste explícito com o panel
        keyNormal        = Color(0xFF2E2E2E),   // cinza escuro (visível sobre #141414)
        keyFinancial     = Color(0xFF2E2E2A),   // levemente mais quente (TVM)
        keyF             = Color(0xFFCC5500),   // laranja HP característico
        keyG             = Color(0xFF1A5FAB),   // azul HP característico
        keyOn            = Color(0xFF262626),
        keyEnter         = Color(0xFF2E2E2E),
        keyTopHighlight  = Color(0x55FFFFFF),   // highlight mais intenso para 3D visível
        keyBottomShadow  = Color(0x88000000),   // sombra mais intensa
        // Texto
        keyLabel         = Color(0xFFEEEEEE),   // branco levemente warm
        fLabelColor      = Color(0xFFE07020),   // laranja f (silk-screen acima das teclas)
        gLabelColor      = Color(0xFF4A9FD8),   // azul g (silk-screen abaixo das teclas)
    )

    /** Escuro/flat inspirado em Material3 Dark Theme com display fósforo verde. */
    val Modern = Hp12cSkin(
        name             = "Modern",
        body             = Color(0xFF1E1E2E),
        bodyEdge         = Color(0xFF0D0D1A),
        keyboardPanel    = Color(0xFF090910),
        displayBezel     = Color(0xFF0D0D1A),
        displayBg        = Color(0xFF0D1B2A),
        displayText      = Color(0xFF00E5FF),
        indicatorOn      = Color(0xFF00E5FF),
        indicatorOff     = Color(0xFF1A2A3A),
        keyNormal        = Color(0xFF252538),
        keyFinancial     = Color(0xFF1E2048),
        keyF             = Color(0xFF7C3AED),
        keyG             = Color(0xFF0D9488),
        keyOn            = Color(0xFFE63946),
        keyEnter         = Color(0xFF252540),
        keyTopHighlight  = Color(0x33FFFFFF),
        keyBottomShadow  = Color(0x66000000),
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
