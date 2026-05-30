package br.com.alyssonmyller.calculus.android.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.alyssonmyller.calculus.engine.CalculatorEngine
import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import br.com.alyssonmyller.calculus.engine.state.NumericSeparator
import br.com.alyssonmyller.calculus.engine.state.ProgramState
import br.com.alyssonmyller.calculus.engine.state.TvmMode

// ─────────────────────────────────────────────────────────────────────────────
//  Tokens de layout responsivo
// ─────────────────────────────────────────────────────────────────────────────

private data class LayoutTokens(
    val keyFontSize:     TextUnit,
    val keySubFontSize:  TextUnit,
    val showSubLabels:   Boolean,   // false em landscape: remove f/g labels para dar altura às teclas
    val keyCorner:       Dp,
    val keyRowSpacing:   Dp,
    val keyColSpacing:   Dp,
    val displayNumSize:  TextUnit,
    val displayIndSize:  TextUnit,
    val panelPadH:       Dp,
    val panelPadV:       Dp,
    val outerPadH:       Dp,
    val outerPadV:       Dp,
)

private val PortraitTokens = LayoutTokens(
    keyFontSize    = 15.sp,
    keySubFontSize = 7.sp,
    showSubLabels  = true,
    keyCorner      = 4.dp,
    keyRowSpacing  = 4.dp,
    keyColSpacing  = 4.dp,
    displayNumSize = 34.sp,
    displayIndSize = 8.sp,
    panelPadH      = 8.dp,
    panelPadV      = 6.dp,
    outerPadH      = 0.dp,
    outerPadV      = 0.dp,
)

// Landscape: teclas maiores, sem sub-labels, display compacto
private val LandscapeTokens = LayoutTokens(
    keyFontSize    = 14.sp,
    keySubFontSize = 5.sp,
    showSubLabels  = false,          // omite f/g labels: +30% de altura para o corpo da tecla
    keyCorner      = 3.dp,
    keyRowSpacing  = 4.dp,
    keyColSpacing  = 3.dp,
    displayNumSize = 22.sp,          // compacto para ceder altura ao teclado
    displayIndSize = 6.sp,
    panelPadH      = 6.dp,
    panelPadV      = 4.dp,
    outerPadH      = 0.dp,
    outerPadV      = 0.dp,
)

private val LocalLayout = staticCompositionLocalOf { PortraitTokens }

// ─────────────────────────────────────────────────────────────────────────────
//  Tela principal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tela da HP 12C Platinum — fiel à calculadora física.
 *
 * Estrutura (portrait e landscape):
 *   1. Faixa superior prateada: branding + LCD
 *   2. Painel preto fosco: teclado + rodapé "HEWLETT-PACKARD 12C PLATINUM"
 *
 * Em landscape: faixa prateada tem o logo HP à direita do LCD.
 * Em portrait:  a faixa ocupa apenas a parte superior.
 *
 * Grade de teclas:
 *   Portrait  → KEY_ROWS_PORTRAIT  (8 linhas × 5 colunas)
 *   Landscape → KEY_ROWS_LANDSCAPE (4 linhas × 10 colunas)
 */
@Composable
fun Hp12cScreen(
    calcState: CalculatorState,
    onEvent: (Event) -> Unit,
    skin: Hp12cSkin = Hp12cSkins.Classic,
    onToggleSkin: () -> Unit = {},
    isRunning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var shift   by remember { mutableStateOf(ShiftState.NONE) }
    var pending by remember { mutableStateOf(PendingOp.NONE) }

    val onKeyPress: (KeyDef) -> Unit = keyPress@{ def ->
        if (isRunning && def.label != "R/S") return@keyPress
        val (event, newShift, newPending) = resolveKey(def, calcState, shift, pending)
        shift   = newShift
        pending = newPending
        event?.let { onEvent(it) }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val tokens      = if (isLandscape) LandscapeTokens else PortraitTokens
    val keyRows     = if (isLandscape) KEY_ROWS_LANDSCAPE else KEY_ROWS_PORTRAIT

    CompositionLocalProvider(
        LocalSkin   provides skin,
        LocalLayout provides tokens,
    ) {
        // Carcaça principal: grafite escuro
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(skin.body),
        ) {
            // ── Faixa superior: display (full-width) ──────────────────────────
            DisplayStrip(
                calcState    = calcState,
                shift        = shift,
                pending      = pending,
                isRunning    = isRunning,
                isLandscape  = isLandscape,
                onToggleSkin = onToggleSkin,
            )

            // ── Painel preto do teclado (peso 1f = ocupa o restante) ──────────
            KeyboardPanel(
                calcState  = calcState,
                onKeyPress = onKeyPress,
                keyRows    = keyRows,
                modifier   = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Faixa prateada do display
//
//  Portrait:  [HP 12c Platinum ···········⊞ Platinum]
//             [         LCD display               ]
//
//  Landscape: [HP 12c Platinum] [LCD display] [hp / 12C PLATINUM]
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DisplayStrip(
    calcState: CalculatorState,
    shift: ShiftState,
    pending: PendingOp,
    isRunning: Boolean,
    isLandscape: Boolean,
    onToggleSkin: () -> Unit,
) {
    val skin   = LocalSkin.current
    val tokens = LocalLayout.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        skin.displayStrip.copy(red   = (skin.displayStrip.red   + 0.08f).coerceAtMost(1f),
                                               green = (skin.displayStrip.green + 0.08f).coerceAtMost(1f),
                                               blue  = (skin.displayStrip.blue  + 0.08f).coerceAtMost(1f)),
                        skin.displayStrip,
                        skin.displayStrip.copy(red   = (skin.displayStrip.red   - 0.10f).coerceAtLeast(0f),
                                               green = (skin.displayStrip.green - 0.10f).coerceAtLeast(0f),
                                               blue  = (skin.displayStrip.blue  - 0.10f).coerceAtLeast(0f)),
                    ),
                ),
            ),
    ) {
        if (isLandscape) {
            // Landscape: tudo numa linha horizontal única e COMPACTA
            // HP 12c | [LCD] | hp logo
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Branding
                Text(
                    text          = "HP 12c  Platinum",
                    color         = skin.body,
                    fontSize      = 9.sp,
                    fontStyle     = FontStyle.Italic,
                    fontWeight    = FontWeight.Bold,
                    modifier      = Modifier.weight(0.16f),
                    softWrap      = false,
                )
                // LCD — porção central (mais estreita = menos altura da faixa)
                Hp12cDisplay(
                    calcState = calcState,
                    shift     = shift,
                    pending   = pending,
                    isRunning = isRunning,
                    modifier  = Modifier.weight(0.68f),
                )
                // Logo HP + toggle skin (compacto)
                Column(
                    modifier            = Modifier.weight(0.16f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier         = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(skin.body)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text          = "hp",
                                color         = skin.displayStrip,
                                fontSize      = 11.sp,
                                fontStyle     = FontStyle.Italic,
                                fontWeight    = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                            )
                        }
                    }
                    Text(
                        text          = "12C PLATINUM",
                        color         = skin.body,
                        fontSize      = 5.5.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 0.2.sp,
                    )
                    Text(
                        text     = "⊞",
                        color    = skin.body.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                            onClick           = onToggleSkin,
                        ),
                    )
                }
            }
        } else {
            // Portrait: branding em cima, LCD embaixo
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text          = "HP 12c  Platinum",
                        color         = skin.body,
                        fontSize      = 11.sp,
                        fontStyle     = FontStyle.Italic,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                    )
                    Text(
                        text     = "⊞ ${skin.name}",
                        color    = skin.body.copy(alpha = 0.7f),
                        fontSize = 8.sp,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                            onClick           = onToggleSkin,
                        ),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Hp12cDisplay(
                    calcState = calcState,
                    shift     = shift,
                    pending   = pending,
                    isRunning = isRunning,
                    modifier  = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Painel preto do teclado
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KeyboardPanel(
    calcState: CalculatorState,
    onKeyPress: (KeyDef) -> Unit,
    keyRows: List<List<KeyDef>>,
    modifier: Modifier = Modifier,
) {
    val skin   = LocalSkin.current
    val tokens = LocalLayout.current

    Column(
        modifier = modifier.background(skin.keyboardPanel),
    ) {
        // Em modo PRGM, mostrar a lista de programa acima do teclado
        val prgmState = calcState.programState
        if (prgmState is ProgramState.Editing) {
            Hp12cProgramList(
                memory   = calcState.programMemory,
                cursor   = prgmState.cursor,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.30f)
                    .padding(horizontal = tokens.panelPadH, vertical = 4.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }

        // Teclado
        Hp12cKeyboard(
            onKeyPress = onKeyPress,
            keyRows    = keyRows,
            modifier   = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                    horizontal = tokens.panelPadH,
                    vertical   = tokens.panelPadV,
                ),
        )

        // Rodapé: "HEWLETT-PACKARD 12C PLATINUM"
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text          = "H E W L E T T · P A C K A R D   1 2 C   P L A T I N U M",
                color         = skin.brandingText,
                fontSize      = 6.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                textAlign     = TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Resolução de tecla → evento
// ─────────────────────────────────────────────────────────────────────────────

private data class KeyResult(val event: Event?, val shift: ShiftState, val pending: PendingOp)

private fun resolveKey(
    def: KeyDef,
    state: CalculatorState,
    shift: ShiftState,
    pending: PendingOp,
): KeyResult {
    if (pending != PendingOp.NONE) {
        val regId = labelToRegisterId(def.label)
        if (regId != null) {
            val ev = if (pending == PendingOp.STO) Event.Memory.Store(regId)
                     else                          Event.Memory.Recall(regId)
            return KeyResult(ev, ShiftState.NONE, PendingOp.NONE)
        }
        if (def.label != "STO" && def.label != "RCL")
            return KeyResult(null, ShiftState.NONE, PendingOp.NONE)
    }
    if (def.style == KeyStyle.ShiftF) {
        val next = if (shift == ShiftState.F_SHIFT) ShiftState.NONE else ShiftState.F_SHIFT
        return KeyResult(null, next, PendingOp.NONE)
    }
    if (def.style == KeyStyle.ShiftG) {
        val next = if (shift == ShiftState.G_SHIFT) ShiftState.NONE else ShiftState.G_SHIFT
        return KeyResult(null, next, PendingOp.NONE)
    }

    fun result(ev: Event?) = KeyResult(ev, ShiftState.NONE, PendingOp.NONE)

    if (shift == ShiftState.NONE) {
        when (def.label) {
            "STO" -> return KeyResult(null, ShiftState.NONE, PendingOp.STO)
            "RCL" -> return KeyResult(null, ShiftState.NONE, PendingOp.RCL)
        }
    }
    if (shift == ShiftState.F_SHIFT) {
        when (def.label) {
            "STO" -> return result(def.fEvent)
            "RCL" -> return result(def.fEvent)
        }
    }
    val ev: Event? = when (shift) {
        ShiftState.F_SHIFT -> def.fEvent
        ShiftState.G_SHIFT -> def.gEvent
        ShiftState.NONE    -> resolveNoPressEvent(def, state)
    }
    return result(ev)
}

private fun resolveNoPressEvent(def: KeyDef, state: CalculatorState): Event? = when (def.label) {
    "n"   -> if (state.stack.canStoreToTvm) Event.Financial.Store.N   else Event.Financial.Solve.N
    "i"   -> if (state.stack.canStoreToTvm) Event.Financial.Store.I   else Event.Financial.Solve.I
    "PV"  -> if (state.stack.canStoreToTvm) Event.Financial.Store.Pv  else Event.Financial.Solve.Pv
    "PMT" -> if (state.stack.canStoreToTvm) Event.Financial.Store.Pmt else Event.Financial.Solve.Pmt
    "FV"  -> if (state.stack.canStoreToTvm) Event.Financial.Store.Fv  else Event.Financial.Solve.Fv
    "CHS" -> if (state.stack.isEntering) Event.Entry.ChangeSign else Event.Arith.Negate
    else  -> def.event
}

// ─────────────────────────────────────────────────────────────────────────────
//  Visor LCD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Hp12cDisplay(
    calcState: CalculatorState,
    shift: ShiftState,
    pending: PendingOp,
    isRunning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val skin   = LocalSkin.current
    val tokens = LocalLayout.current
    val engine = remember { CalculatorEngine.Default }

    val displayText = when {
        isRunning                -> "running..."
        pending == PendingOp.STO -> "STO  _"
        pending == PendingOp.RCL -> "RCL  _"
        else                     -> engine.formatDisplay(calcState, NumericSeparator.COMMA_PERIOD)
    }

    val isLandscape = !tokens.showSubLabels  // landscape = modo compacto

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(skin.displayBezel)
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(skin.displayBg)
                .padding(
                    horizontal = 10.dp,
                    vertical   = if (isLandscape) 2.dp else 4.dp,
                ),
        ) {
            if (isLandscape) {
                // Landscape: número + indicadores em linha única (mínimo de altura)
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Indicadores compactos à esquerda
                    IndicatorLabel("f",  active = shift == ShiftState.F_SHIFT)
                    Spacer(Modifier.width(4.dp))
                    IndicatorLabel("g",  active = shift == ShiftState.G_SHIFT)
                    Spacer(Modifier.width(4.dp))
                    IndicatorLabel("B",  active = calcState.financial.mode == TvmMode.BEGIN)
                    // Número ocupa o restante, alinhado à direita
                    Text(
                        text       = displayText,
                        color      = skin.displayText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize   = tokens.displayNumSize,
                        textAlign  = TextAlign.End,
                        maxLines   = 1,
                        modifier   = Modifier.weight(1f),
                    )
                }
            } else {
                // Portrait: layout completo em coluna
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        IndicatorLabel("f",     active = shift == ShiftState.F_SHIFT)
                        Spacer(Modifier.width(5.dp))
                        IndicatorLabel("g",     active = shift == ShiftState.G_SHIFT)
                        Spacer(Modifier.weight(1f))
                        IndicatorLabel("BEGIN", active = calcState.financial.mode == TvmMode.BEGIN)
                        Spacer(Modifier.width(5.dp))
                        IndicatorLabel("PRGM",  active = calcState.programState is ProgramState.Editing)
                        Spacer(Modifier.width(5.dp))
                        IndicatorLabel("RUN",   active = isRunning)
                    }
                    Text(
                        text       = displayText,
                        color      = skin.displayText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize   = tokens.displayNumSize,
                        textAlign  = TextAlign.End,
                        maxLines   = 1,
                        modifier   = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                    Text(
                        text       = "RPN",
                        color      = skin.indicatorOff,
                        fontSize   = 6.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun IndicatorLabel(text: String, active: Boolean) {
    val skin   = LocalSkin.current
    val tokens = LocalLayout.current
    Text(
        text       = text,
        color      = if (active) skin.indicatorOn else skin.indicatorOff,
        fontSize   = tokens.displayIndSize,
        fontWeight = FontWeight.Bold,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Teclado — grid de linhas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Hp12cKeyboard(
    onKeyPress: (KeyDef) -> Unit,
    keyRows: List<List<KeyDef>>,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalLayout.current
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(tokens.keyRowSpacing),
    ) {
        keyRows.forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(tokens.keyColSpacing),
            ) {
                row.forEach { def ->
                    Hp12cKeyButton(
                        def      = def,
                        onClick  = { onKeyPress(def) },
                        modifier = Modifier.weight(def.widthWeight).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Botão individual
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Hp12cKeyButton(
    def: KeyDef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin   = LocalSkin.current
    val tokens = LocalLayout.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val isNumeric = def.style == KeyStyle.Numeric

    val keyBg = when (def.style) {
        KeyStyle.Numeric   -> skin.keyNumeric
        KeyStyle.Financial -> skin.keyFinancial
        KeyStyle.ShiftF    -> skin.keyF
        KeyStyle.ShiftG    -> skin.keyG
        KeyStyle.Power     -> skin.keyOn
        KeyStyle.Enter     -> skin.keyEnter
        KeyStyle.Normal    -> skin.keyNormal
    }

    val textColor = if (isNumeric) skin.keyLabelNumeric else skin.keyLabel

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // ── Label f acima (dourado) — omitido em landscape para dar mais altura à tecla ──
        if (tokens.showSubLabels) {
            Text(
                text      = def.fLabel,
                color     = if (def.fLabel.isNotBlank()) skin.fLabelColor else Color.Transparent,
                fontSize  = tokens.keySubFontSize,
                maxLines  = 1,
                textAlign = TextAlign.Center,
                softWrap  = false,
                modifier  = Modifier.fillMaxWidth(),
            )
        }

        // ── Corpo da tecla ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.keyCorner))
                // Gradiente vertical sutil para dar volume à tecla
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lighten(keyBg, 0.12f),
                            keyBg,
                            darken(keyBg, 0.10f),
                        ),
                    ),
                )
                // Borda fina com reflexo no topo
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            skin.keyTopHighlight,
                            Color.Transparent,
                            skin.keyBottomShadow,
                        ),
                    ),
                    shape = RoundedCornerShape(tokens.keyCorner),
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication        = null,
                ) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (isPressed) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0x40FFFFFF)))
            }
            Text(
                text       = def.label,
                color      = textColor,
                fontSize   = tokens.keyFontSize,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                maxLines   = 1,
                softWrap   = false,
                lineHeight = tokens.keyFontSize,
            )
        }

        // ── Label g abaixo (azul) — omitido em landscape ────────────────────
        if (tokens.showSubLabels) {
            Text(
                text      = def.gLabel,
                color     = if (def.gLabel.isNotBlank()) skin.gLabelColor else Color.Transparent,
                fontSize  = tokens.keySubFontSize,
                maxLines  = 1,
                textAlign = TextAlign.Center,
                softWrap  = false,
                modifier  = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers de cor
// ─────────────────────────────────────────────────────────────────────────────

private fun lighten(color: Color, amount: Float) = Color(
    red   = (color.red   + amount).coerceAtMost(1f),
    green = (color.green + amount).coerceAtMost(1f),
    blue  = (color.blue  + amount).coerceAtMost(1f),
    alpha = color.alpha,
)

private fun darken(color: Color, amount: Float) = Color(
    red   = (color.red   - amount).coerceAtLeast(0f),
    green = (color.green - amount).coerceAtLeast(0f),
    blue  = (color.blue  - amount).coerceAtLeast(0f),
    alpha = color.alpha,
)
