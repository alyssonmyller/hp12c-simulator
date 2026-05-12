package com.arcom.hp12c.android.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
//  Tokens de design do skin
// ─────────────────────────────────────────────────────────────────────────────

data class Hp12cSkin(
    val name: String,

    // Corpo externo (moldura/carcaça)
    val body:          Color,
    val bodyEdge:      Color,

    // Painel do teclado (bloco separado da moldura — mais escuro que as teclas)
    val keyboardPanel: Color,

    // Faixa de display (bezel metálico ao redor do LCD)
    val displayStrip:  Color,   // fundo da faixa prateada com o LCD
    val displayBezel:  Color,   // moldura interna ao redor do painel LCD
    val displayBg:     Color,   // fundo LCD (olive-green na Platinum física)
    val displayText:   Color,   // dígitos LCD
    val indicatorOn:   Color,
    val indicatorOff:  Color,

    // Teclas — fundos
    val keyNormal:       Color,   // operações: fundo escuro
    val keyNumeric:      Color,   // dígitos 0–9 e ponto: fundo CLARO (#D1D1D1 na física)
    val keyFinancial:    Color,   // TVM (n/i/PV/PMT/FV)
    val keyF:            Color,   // tecla [f] dourada
    val keyG:            Color,   // tecla [g] azul cobalto
    val keyOn:           Color,   // tecla ON
    val keyEnter:        Color,   // tecla ENTER
    val keyTopHighlight: Color,
    val keyBottomShadow: Color,

    // Texto das teclas
    val keyLabel:        Color,   // texto em teclas escuras (branco)
    val keyLabelNumeric: Color,   // texto em teclas claras (preto)
    val fLabelColor:     Color,   // silk-screen laranja/dourado acima das teclas
    val gLabelColor:     Color,   // silk-screen azul abaixo das teclas

    // Branding
    val brandingText:    Color,   // "HEWLETT-PACKARD 12C PLATINUM"
)

// ─────────────────────────────────────────────────────────────────────────────
//  Instâncias disponíveis
// ─────────────────────────────────────────────────────────────────────────────

object Hp12cSkins {

    /**
     * HP 12C Platinum — fiel à calculadora física.
     *
     * Referências visuais:
     * - Carcaça: grafite escuro (#1A1A1A), faixa metálica prata acima do teclado.
     * - LCD: fundo olive-acinzentado (#97A082), dígitos 7-segmentos pretos.
     * - Teclas de operação: cinza escuro (#2D2D2D) com texto branco.
     * - Teclas numéricas (0–9, ·): cinza CLARO (#CBCBCB) com texto preto.
     * - [f]: dourado (#CC8800). [g]: azul cobalto (#0056B3).
     */
    val Classic = Hp12cSkin(
        name             = "Platinum",

        // Carcaça grafite escura
        body             = Color(0xFF232323),
        bodyEdge         = Color(0xFF0E0E0E),

        // Painel do teclado (quase preto, abaixo da faixa do display)
        keyboardPanel    = Color(0xFF141414),

        // Faixa prateada do display
        displayStrip     = Color(0xFFB0B0B8),   // prata/alumínio (gradiente depois)
        displayBezel     = Color(0xFF888890),   // moldura interna do LCD
        displayBg        = Color(0xFF97A082),   // olive-green (LCD da HP Platinum física)
        displayText      = Color(0xFF0A1208),   // preto-esverdeado (segmentos LCD)
        indicatorOn      = Color(0xFF0A1208),
        indicatorOff     = Color(0xFF6A7A60),   // indicadores apagados

        // Teclas
        keyNormal        = Color(0xFF2D2D2D),   // cinza escuro — operações
        keyNumeric       = Color(0xFFCBCBCB),   // cinza CLARO — dígitos (grande diferença visual)
        keyFinancial     = Color(0xFF272724),   // levemente mais quente (TVM)
        keyF             = Color(0xFFCC8800),   // dourado HP
        keyG             = Color(0xFF0056B3),   // azul cobalto HP
        keyOn            = Color(0xFF252525),
        keyEnter         = Color(0xFF2D2D2D),
        keyTopHighlight  = Color(0x50FFFFFF),
        keyBottomShadow  = Color(0x70000000),

        // Texto
        keyLabel         = Color(0xFFEEEEEE),   // branco para teclas escuras
        keyLabelNumeric  = Color(0xFF111111),   // preto para teclas claras
        fLabelColor      = Color(0xFFCC8800),   // dourado (silk-screen acima)
        gLabelColor      = Color(0xFF1A72D4),   // azul (silk-screen abaixo)

        // Branding
        brandingText     = Color(0xFF888888),
    )

    /** Escuro/moderno — display fósforo ciano. */
    val Modern = Hp12cSkin(
        name             = "Modern",
        body             = Color(0xFF1A1A2E),
        bodyEdge         = Color(0xFF0A0A18),
        keyboardPanel    = Color(0xFF090912),
        displayStrip     = Color(0xFF1E1E38),
        displayBezel     = Color(0xFF0D0D22),
        displayBg        = Color(0xFF0D1B2A),
        displayText      = Color(0xFF00E5FF),
        indicatorOn      = Color(0xFF00E5FF),
        indicatorOff     = Color(0xFF1A2A3A),
        keyNormal        = Color(0xFF252538),
        keyNumeric       = Color(0xFF3A3A58),
        keyFinancial     = Color(0xFF1E2048),
        keyF             = Color(0xFF7C3AED),
        keyG             = Color(0xFF0D9488),
        keyOn            = Color(0xFFE63946),
        keyEnter         = Color(0xFF252540),
        keyTopHighlight  = Color(0x33FFFFFF),
        keyBottomShadow  = Color(0x66000000),
        keyLabel         = Color(0xFFE2E8F0),
        keyLabelNumeric  = Color(0xFFE2E8F0),
        fLabelColor      = Color(0xFFC084FC),
        gLabelColor      = Color(0xFF2DD4BF),
        brandingText     = Color(0xFF4A4A6A),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  CompositionLocal
// ─────────────────────────────────────────────────────────────────────────────

val LocalSkin = staticCompositionLocalOf { Hp12cSkins.Classic }
