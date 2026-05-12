package com.arcom.hp12c.android.ui

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
import com.arcom.hp12c.engine.CalculatorEngine
import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.NumericSeparator
import com.arcom.hp12c.engine.state.ProgramState
import com.arcom.hp12c.engine.state.TvmMode

// ─────────────────────────────────────────────────────────────────────────────
//  Tokens de layout responsivo (portrait vs landscape)
// ─────────────────────────────────────────────────────────────────────────────

private data class LayoutTokens(
    val keyFontSize:     TextUnit,   // texto principal da tecla
    val keySubFontSize:  TextUnit,   // f/g labels acima/abaixo
    val keyCorner:       Dp,
    val keyRowSpacing:   Dp,
    val keyColSpacing:   Dp,
    val displayNumSize:  TextUnit,
    val displayIndSize:  TextUnit,
    val panelPadH:       Dp,         // padding interno do painel escuro do teclado
    val panelPadV:       Dp,
    val outerPadH:       Dp,         // padding externo da moldura
    val outerPadV:       Dp,
)

private val PortraitTokens = LayoutTokens(
    keyFontSize    = 14.sp,
    keySubFontSize = 7.sp,
    keyCorner      = 4.dp,
    keyRowSpacing  = 3.dp,
    keyColSpacing  = 3.dp,
    displayNumSize = 32.sp,
    displayIndSize = 8.sp,
    panelPadH      = 6.dp,
    panelPadV      = 5.dp,
    outerPadH      = 8.dp,
    outerPadV      = 6.dp,
)

private val LandscapeTokens = LayoutTokens(
    keyFontSize    = 14.sp,
    keySubFontSize = 6.sp,
    keyCorner      = 3.dp,
    keyRowSpacing  = 2.dp,
    keyColSpacing  = 2.dp,
    displayNumSize = 26.sp,
    displayIndSize = 7.sp,
    panelPadH      = 4.dp,
    panelPadV      = 3.dp,
    outerPadH      = 5.dp,
    outerPadV      = 4.dp,
)

private val LocalLayout = staticCompositionLocalOf { PortraitTokens }

// ─────────────────────────────────────────────────────────────────────────────
//  Tela principal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tela da HP 12C Platinum.
 *
 * Estrutura fiel à calculadora física:
 *  - Moldura prateada/alumínio com o display LCD
 *  - Painel preto fosco separado para o teclado (keyboardPanel)
 *
 * Em **portrait**: moldura + display em cima; painel escuro + teclas embaixo.
 * Em **landscape**: moldura + display à esquerda; painel escuro + teclas à direita.
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

    CompositionLocalProvider(
        LocalSkin   provides skin,
        LocalLayout provides tokens,
    ) {
        // Fundo: gradiente da moldura (alumínio/platina)
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(skin.body, skin.bodyEdge),
                    ),
                ),
        ) {
            if (isLandscape) {
                LandscapeLayout(calcState, shift, pending, isRunning, onKeyPress, onToggleSkin)
            } else {
                PortraitLayout(calcState, shift, pending, isRunning, onKeyPress, onToggleSkin)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Layout portrait
//  ┌──────────────────────────┐
//  │  [moldura prata]         │
//  │  HP 12c Platinum   ⊞     │
//  │  ┌──── LCD ─────────┐   │
//  │  │  1.0 0            │   │
//  │  └───────────────────┘   │
//  ├──────────────────────────┤
//  │  [painel preto — teclas] │
//  │  [ n ][ i ][PV]...       │
//  │  ...                     │
//  └──────────────────────────┘
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PortraitLayout(
    calcState: CalculatorState,
    shift: ShiftState,
    pending: PendingOp,
    isRunning: Boolean,
    onKeyPress: (KeyDef) -> Unit,
    onToggleSkin: () -> Unit,
) {
    val skin   = LocalSkin.current
    val tokens = LocalLayout.current

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Seção superior — moldura prateada com display ─────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.outerPadH, vertical = tokens.outerPadV),
        ) {
            HpBrandingBar(onToggleSkin = onToggleSkin)
            Spacer(Modifier.height(4.dp))
            Hp12cDisplay(
                calcState = calcState,
                shift     = shift,
                pending   = pending,
                isRunning = isRunning,
                modifier  = Modifier.fillMaxWidth(),
            )
        }

        // ── Seção inferior — painel preto com o teclado ───────────────────
        KeyboardPanel(
            calcState  = calcState,
            onKeyPress = onKeyPress,
            modifier   = Modifier
                .fillMaxWidth()
                .weight(1f),
            cornerTop  = 4.dp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Layout landscape
//  ┌────────────────┬──────────────────────────────────────┐
//  │ [moldura prata]│ [painel preto — teclas]               │
//  │ HP 12c Platinum│ [ n ][ i ][PV][PMT][FV][CHS][7][8][9][÷] │
//  │ ┌─ LCD ──────┐ │ [y^x][1/x][%T][Δ%][%][EEX][4][5][6][×]  │
//  │ │  1.0 0      │ │ [R/S][SST][R↓][x⇄y][CLx][ENT][1][2][3][-]│
//  │ └────────────┘ │ [ON][f][g][STO][RCL][   ][0][.][Σ+][+] │
//  │ hp             │                                        │
//  └────────────────┴──────────────────────────────────────┘
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LandscapeLayout(
    calcState: CalculatorState,
    shift: ShiftState,
    pending: PendingOp,
    isRunning: Boolean,
    onKeyPress: (KeyDef) -> Unit,
    onToggleSkin: () -> Unit,
) {
    val skin   = LocalSkin.current
    val tokens = LocalLayout.current

    Row(modifier = Modifier.fillMaxSize()) {

        // ── Coluna esquerda — moldura prateada com display ────────────────
        Column(
            modifier = Modifier
                .weight(0.24f)
                .fillMaxHeight()
                .padding(horizontal = tokens.outerPadH, vertical = tokens.outerPadV),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HpBrandingBar(onToggleSkin = onToggleSkin)

            Hp12cDisplay(
                calcState = calcState,
                shift     = shift,
                pending   = pending,
                isRunning = isRunning,
                modifier  = Modifier.fillMaxWidth(),
            )

            // Área restante: lista de programa (modo PRGM) ou logotipo hp
            val prgmState = calcState.programState
            if (prgmState is ProgramState.Editing) {
                Hp12cProgramList(
                    memory   = calcState.programMemory,
                    cursor   = prgmState.cursor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp)),
                )
            } else {
                Box(
                    modifier         = Modifier.weight(1f),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        text          = "hp",
                        color         = skin.bodyEdge,
                        fontSize      = 20.sp,
                        fontStyle     = FontStyle.Italic,
                        fontWeight    = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                    )
                }
            }
        }

        // ── Coluna direita — painel preto com o teclado ───────────────────
        KeyboardPanel(
            calcState  = calcState,
            onKeyPress = onKeyPress,
            modifier   = Modifier
                .weight(0.76f)
                .fillMaxHeight(),
            cornerTop  = 4.dp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Painel escuro do teclado (o "bloco preto" da HP física)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KeyboardPanel(
    calcState: CalculatorState,
    onKeyPress: (KeyDef) -> Unit,
    modifier: Modifier = Modifier,
    cornerTop: Dp = 0.dp,
) {
    val skin   = LocalSkin.current
    val tokens = LocalLayout.current

    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart    = cornerTop,
                    topEnd      = cornerTop,
                    bottomStart = 0.dp,
                    bottomEnd   = 0.dp,
                ),
            )
            .background(skin.keyboardPanel)
            .padding(horizontal = tokens.panelPadH, vertical = tokens.panelPadV),
    ) {
        val prgmState = calcState.programState
        if (prgmState is ProgramState.Editing) {
            // Em modo PRGM (portrait): lista cima + teclado baixo
            Column(modifier = Modifier.fillMaxSize()) {
                Hp12cProgramList(
                    memory   = calcState.programMemory,
                    cursor   = prgmState.cursor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.35f)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.height(4.dp))
                Hp12cKeyboard(
                    onKeyPress = onKeyPress,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .weight(0.65f),
                )
            }
        } else {
            Hp12cKeyboard(
                onKeyPress = onKeyPress,
                modifier   = Modifier.fillMaxSize(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Barra de branding
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HpBrandingBar(onToggleSkin: () -> Unit) {
    val skin = LocalSkin.current
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text          = "HP 12c Platinum",
            color         = skin.bodyEdge,
            fontSize      = 10.sp,
            fontStyle     = FontStyle.Italic,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        )
        Text(
            text     = "⊞ ${skin.name}",
            color    = skin.bodyEdge,
            fontSize = 8.sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onToggleSkin,
            ),
        )
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

    // Moldura do LCD (levemente inset na moldura prateada)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(skin.displayBezel)
            .padding(3.dp),
    ) {
        // Painel LCD propriamente dito
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(skin.displayBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Indicadores ──────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    IndicatorLabel("f",     active = shift == ShiftState.F_SHIFT, size = tokens.displayIndSize)
                    Spacer(Modifier.width(4.dp))
                    IndicatorLabel("g",     active = shift == ShiftState.G_SHIFT, size = tokens.displayIndSize)
                    Spacer(Modifier.weight(1f))
                    IndicatorLabel("BEGIN", active = calcState.financial.mode == TvmMode.BEGIN, size = tokens.displayIndSize)
                    Spacer(Modifier.width(4.dp))
                    IndicatorLabel("PRGM",  active = calcState.programState is ProgramState.Editing, size = tokens.displayIndSize)
                    Spacer(Modifier.width(4.dp))
                    IndicatorLabel("RUN",   active = isRunning, size = tokens.displayIndSize)
                }
                // ── Número principal ─────────────────────────────────────
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
                // ── Indicador de modo ───────────────────────────────────
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

@Composable
private fun IndicatorLabel(text: String, active: Boolean, size: TextUnit = 8.sp) {
    val skin = LocalSkin.current
    Text(
        text       = text,
        color      = if (active) skin.indicatorOn else skin.indicatorOff,
        fontSize   = size,
        fontWeight = FontWeight.Bold,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Teclado — grid de linhas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Hp12cKeyboard(
    onKeyPress: (KeyDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalLayout.current
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(tokens.keyRowSpacing),
    ) {
        KEY_ROWS.forEach { row ->
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

    val keyBg = when (def.style) {
        KeyStyle.Financial -> skin.keyFinancial
        KeyStyle.ShiftF    -> skin.keyF
        KeyStyle.ShiftG    -> skin.keyG
        KeyStyle.Power     -> skin.keyOn
        KeyStyle.Enter     -> skin.keyEnter
        KeyStyle.Normal    -> skin.keyNormal
    }

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // ── Label f acima ────────────────────────────────────────────────
        Text(
            text      = def.fLabel,
            color     = if (def.fLabel.isNotBlank()) skin.fLabelColor else Color.Transparent,
            fontSize  = tokens.keySubFontSize,
            maxLines  = 1,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth(),
        )

        // ── Corpo da tecla ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.keyCorner))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            keyBg.copy(red   = (keyBg.red   + 0.10f).coerceAtMost(1f),
                                       green = (keyBg.green + 0.10f).coerceAtMost(1f),
                                       blue  = (keyBg.blue  + 0.10f).coerceAtMost(1f)),
                            keyBg,
                            keyBg.copy(red   = (keyBg.red   - 0.06f).coerceAtLeast(0f),
                                       green = (keyBg.green - 0.06f).coerceAtLeast(0f),
                                       blue  = (keyBg.blue  - 0.06f).coerceAtLeast(0f)),
                        ),
                    ),
                )
                .border(
                    width = 0.5.dp,
                    color = skin.keyTopHighlight.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(tokens.keyCorner),
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication        = null,
                ) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            // Overlay de pressed
            if (isPressed) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0x44FFFFFF)))
            }
            Text(
                text       = def.label,
                color      = skin.keyLabel,
                fontSize   = tokens.keyFontSize,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                maxLines   = 1,
                lineHeight = tokens.keyFontSize,
                softWrap   = false,
            )
        }

        // ── Label g abaixo ───────────────────────────────────────────────
        Text(
            text      = def.gLabel,
            color     = if (def.gLabel.isNotBlank()) skin.gLabelColor else Color.Transparent,
            fontSize  = tokens.keySubFontSize,
            maxLines  = 1,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth(),
        )
    }
}
