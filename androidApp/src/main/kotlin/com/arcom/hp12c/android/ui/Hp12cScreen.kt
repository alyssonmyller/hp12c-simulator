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
    val keyFontSize: TextUnit,
    val keySubFontSize: TextUnit,  // f/g labels acima/abaixo das teclas
    val keyCorner: Dp,
    val keyRowSpacing: Dp,
    val keyColSpacing: Dp,
    val displayNumFontSize: TextUnit,
    val displayIndicatorSize: TextUnit,
    val outerPadH: Dp,
    val outerPadV: Dp,
)

private val PortraitTokens = LayoutTokens(
    keyFontSize          = 12.sp,
    keySubFontSize       = 6.5.sp,
    keyCorner            = 4.dp,
    keyRowSpacing        = 3.dp,
    keyColSpacing        = 3.dp,
    displayNumFontSize   = 30.sp,
    displayIndicatorSize = 8.sp,
    outerPadH            = 10.dp,
    outerPadV            = 6.dp,
)

private val LandscapeTokens = LayoutTokens(
    keyFontSize          = 10.sp,
    keySubFontSize       = 5.sp,
    keyCorner            = 3.dp,
    keyRowSpacing        = 2.dp,
    keyColSpacing        = 2.dp,
    displayNumFontSize   = 24.sp,
    displayIndicatorSize = 7.sp,
    outerPadH            = 6.dp,
    outerPadV            = 4.dp,
)

// Injeta os tokens de layout na árvore, igual ao skin
private val LocalLayout = androidx.compose.runtime.staticCompositionLocalOf { PortraitTokens }

// ─────────────────────────────────────────────────────────────────────────────
//  Tela principal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tela completa da HP 12C Platinum.
 *
 * Recebe o [calcState] imutável e devolve eventos via [onEvent]. Todo o estado
 * de UI transitório (shift f/g e operação pendente STO/RCL) é mantido aqui
 * como Compose state local, não como parte do [CalculatorState] da engine.
 *
 * Em **portrait** usa layout vertical (display em cima, teclado embaixo).
 * Em **landscape** usa layout horizontal (display à esquerda, teclado à direita)
 * para aproveitar a tela larga e dar altura suficiente às teclas.
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

    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val tokens        = if (isLandscape) LandscapeTokens else PortraitTokens

    CompositionLocalProvider(
        LocalSkin    provides skin,
        LocalLayout  provides tokens,
    ) {
        // Carcaça externa com gradiente metálico
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(skin.body, skin.bodyEdge),
                    ),
                ),
        ) {
            if (isLandscape) {
                LandscapeLayout(
                    calcState  = calcState,
                    shift      = shift,
                    pending    = pending,
                    isRunning  = isRunning,
                    onKeyPress = onKeyPress,
                    onToggleSkin = onToggleSkin,
                    tokens     = tokens,
                    skin       = skin,
                )
            } else {
                PortraitLayout(
                    calcState  = calcState,
                    shift      = shift,
                    pending    = pending,
                    isRunning  = isRunning,
                    onKeyPress = onKeyPress,
                    onToggleSkin = onToggleSkin,
                    tokens     = tokens,
                    skin       = skin,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Layout portrait — display em cima, teclado embaixo
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PortraitLayout(
    calcState: CalculatorState,
    shift: ShiftState,
    pending: PendingOp,
    isRunning: Boolean,
    onKeyPress: (KeyDef) -> Unit,
    onToggleSkin: () -> Unit,
    tokens: LayoutTokens,
    skin: Hp12cSkin,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
        Spacer(Modifier.height(6.dp))

        val prgmState = calcState.programState
        if (prgmState is ProgramState.Editing) {
            Hp12cProgramList(
                memory   = calcState.programMemory,
                cursor   = prgmState.cursor,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(4.dp))
        }

        Hp12cKeyboard(
            onKeyPress = onKeyPress,
            modifier   = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Layout landscape — display à esquerda, teclado à direita
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LandscapeLayout(
    calcState: CalculatorState,
    shift: ShiftState,
    pending: PendingOp,
    isRunning: Boolean,
    onKeyPress: (KeyDef) -> Unit,
    onToggleSkin: () -> Unit,
    tokens: LayoutTokens,
    skin: Hp12cSkin,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tokens.outerPadH, vertical = tokens.outerPadV),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Painel esquerdo: branding + display
        Column(
            modifier = Modifier
                .weight(0.30f)
                .fillMaxHeight(),
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

            // Em modo PRGM, lista de programa aparece abaixo do display
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
                // Logotipo HP discreto na parte inferior do painel
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomStart) {
                    Text(
                        text       = "hp",
                        color      = skin.bodyEdge,
                        fontSize   = 22.sp,
                        fontStyle  = FontStyle.Italic,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                    )
                }
            }
        }

        // Divisor vertical sutil
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(skin.bodyEdge.copy(alpha = 0.4f)),
        )

        // Painel direito: teclado ocupa o espaço restante
        Hp12cKeyboard(
            onKeyPress = onKeyPress,
            modifier   = Modifier
                .weight(0.70f)
                .fillMaxHeight(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Barra de branding HP (topo da calculadora)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HpBrandingBar(onToggleSkin: () -> Unit) {
    val skin = LocalSkin.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text          = "HP 12c Platinum",
            color         = skin.bodyEdge,
            fontSize      = 11.sp,
            fontStyle     = FontStyle.Italic,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.5.sp,
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

private data class KeyResult(
    val event: Event?,
    val shift: ShiftState,
    val pending: PendingOp,
)

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
        if (def.label != "STO" && def.label != "RCL") {
            return KeyResult(null, ShiftState.NONE, PendingOp.NONE)
        }
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

private fun resolveNoPressEvent(def: KeyDef, state: CalculatorState): Event? {
    return when (def.label) {
        // canStoreToTvm=true quando X tem valor "fresco": digitado, resultado de aritmética,
        // ENTER, RCL, etc. Garante "9 ENTER 12 ÷ i" → Store.I=0.75, e "10000 ENTER PV"
        // → Store.Pv=10000. Quando false → Resolve a variável a partir dos demais TVM.
        "n"   -> if (state.stack.canStoreToTvm) Event.Financial.Store.N   else Event.Financial.Solve.N
        "i"   -> if (state.stack.canStoreToTvm) Event.Financial.Store.I   else Event.Financial.Solve.I
        "PV"  -> if (state.stack.canStoreToTvm) Event.Financial.Store.Pv  else Event.Financial.Solve.Pv
        "PMT" -> if (state.stack.canStoreToTvm) Event.Financial.Store.Pmt else Event.Financial.Solve.Pmt
        "FV"  -> if (state.stack.canStoreToTvm) Event.Financial.Store.Fv  else Event.Financial.Solve.Fv
        "CHS" -> if (state.stack.isEntering) Event.Entry.ChangeSign else Event.Arith.Negate
        else  -> def.event
    }
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
    val skin    = LocalSkin.current
    val tokens  = LocalLayout.current
    val engine  = remember { CalculatorEngine.Default }

    val displayText = when {
        isRunning                -> "running..."
        pending == PendingOp.STO -> "STO  _"
        pending == PendingOp.RCL -> "RCL  _"
        else                     -> engine.formatDisplay(calcState, NumericSeparator.COMMA_PERIOD)
    }

    // Moldura LCD (bezel escuro)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(skin.displayBezel)
            .padding(4.dp),
    ) {
        // Painel LCD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(skin.displayBg)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Indicadores de estado ────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    IndicatorLabel("f",     active = shift == ShiftState.F_SHIFT, size = tokens.displayIndicatorSize)
                    Spacer(Modifier.width(4.dp))
                    IndicatorLabel("g",     active = shift == ShiftState.G_SHIFT, size = tokens.displayIndicatorSize)
                    Spacer(Modifier.weight(1f))
                    IndicatorLabel("BEGIN", active = calcState.financial.mode == TvmMode.BEGIN, size = tokens.displayIndicatorSize)
                    Spacer(Modifier.width(4.dp))
                    IndicatorLabel("PRGM",  active = calcState.programState is ProgramState.Editing, size = tokens.displayIndicatorSize)
                    Spacer(Modifier.width(4.dp))
                    IndicatorLabel("RUN",   active = isRunning, size = tokens.displayIndicatorSize)
                }

                // ── Número principal ─────────────────────────────────────
                Text(
                    text       = displayText,
                    color      = skin.displayText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize   = tokens.displayNumFontSize,
                    textAlign  = TextAlign.End,
                    maxLines   = 1,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 1.dp),
                )

                // ── Modo (RPN) ───────────────────────────────────────────
                Text(
                    text       = "RPN",
                    color      = skin.indicatorOff,
                    fontSize   = 6.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.align(Alignment.Start),
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
//  Teclado
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(tokens.keyColSpacing),
            ) {
                row.forEach { def ->
                    Hp12cKeyButton(
                        def      = def,
                        onClick  = { onKeyPress(def) },
                        modifier = Modifier
                            .weight(def.widthWeight)
                            .fillMaxHeight(),
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

        // ── Label f (laranja) — impresso ACIMA da tecla ──────────────────
        Text(
            text      = def.fLabel,
            color     = if (def.fLabel.isNotBlank()) skin.fLabelColor else Color.Transparent,
            fontSize  = tokens.keySubFontSize,
            maxLines  = 1,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth(),
        )

        // ── Corpo da tecla com efeito 3D ─────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart    = tokens.keyCorner,
                        topEnd      = tokens.keyCorner,
                        bottomStart = (tokens.keyCorner.value * 0.75f).dp,
                        bottomEnd   = (tokens.keyCorner.value * 0.75f).dp,
                    ),
                )
                .background(keyBg)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x33FFFFFF)),
                )
            }
            Text(
                text       = def.label,
                color      = skin.keyLabel,
                fontSize   = tokens.keyFontSize,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                maxLines   = 1,
                lineHeight = tokens.keyFontSize,
            )
        }

        // ── Label g (azul) — impresso ABAIXO da tecla ───────────────────
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
