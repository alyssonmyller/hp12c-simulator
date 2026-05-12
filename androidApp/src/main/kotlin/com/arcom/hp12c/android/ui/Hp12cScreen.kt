package com.arcom.hp12c.android.ui

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcom.hp12c.engine.CalculatorEngine
import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.NumericSeparator
import com.arcom.hp12c.engine.state.ProgramState
import com.arcom.hp12c.engine.state.TvmMode

// ─────────────────────────────────────────────────────────────────────────────
//  Tela principal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tela completa da HP 12C Platinum.
 *
 * Recebe o [calcState] imutável e devolve eventos via [onEvent]. Todo o estado
 * de UI transitório (shift f/g e operação pendente STO/RCL) é mantido aqui
 * como Compose state local, não como parte do [CalculatorState] da engine.
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

    CompositionLocalProvider(LocalSkin provides skin) {
        // Carcaça externa com gradiente metálico
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            skin.body.copy(alpha = 1f),
                            skin.bodyEdge.copy(alpha = 1f),
                        ),
                    ),
                )
                .border(1.5.dp, skin.bodyEdge, RoundedCornerShape(0.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // ── Branding HP ──────────────────────────────────────────────
                HpBrandingBar(onToggleSkin = onToggleSkin)
                Spacer(Modifier.height(4.dp))

                // ── Visor LCD ────────────────────────────────────────────────
                Hp12cDisplay(
                    calcState = calcState,
                    shift     = shift,
                    pending   = pending,
                    isRunning = isRunning,
                    modifier  = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))

                // ── Listagem de programa (modo PRGM) ─────────────────────────
                val prgmState = calcState.programState
                if (prgmState is ProgramState.Editing) {
                    Hp12cProgramList(
                        memory   = calcState.programMemory,
                        cursor   = prgmState.cursor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.40f)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // ── Teclado ──────────────────────────────────────────────────
                Hp12cKeyboard(
                    onKeyPress = onKeyPress,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .weight(if (prgmState is ProgramState.Editing) 0.60f else 1f),
                )
            }
        }
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
        // "HP 12c Platinum" em itálico — igual à silk-screen da calculadora física
        Text(
            text       = "HP 12c Platinum",
            color      = skin.bodyEdge,
            fontSize   = 11.sp,
            fontStyle  = FontStyle.Italic,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        // Botão de toggle de skin — discreet
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

/**
 * Transforma um toque em (evento?, novoShift, novoPendente).
 */
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
    val skin   = LocalSkin.current
    val engine = remember { CalculatorEngine.Default }

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
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Indicadores de estado ────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment     = Alignment.CenterVertically,
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

                // ── Número principal ─────────────────────────────────────
                Text(
                    text       = displayText,
                    color      = skin.displayText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 30.sp,
                    textAlign  = TextAlign.End,
                    maxLines   = 1,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )

                // ── Modo (RPN) ───────────────────────────────────────────
                Text(
                    text       = "RPN",
                    color      = skin.indicatorOff,
                    fontSize   = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.align(Alignment.Start),
                )
            }
        }
    }
}

@Composable
private fun IndicatorLabel(text: String, active: Boolean) {
    val skin = LocalSkin.current
    Text(
        text       = text,
        color      = if (active) skin.indicatorOn else skin.indicatorOff,
        fontSize   = 8.sp,
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
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        KEY_ROWS.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
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
    val skin = LocalSkin.current
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
        modifier              = modifier,
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center,
    ) {

        // ── Label f (laranja) — impresso ACIMA da tecla ──────────────────
        Text(
            text      = def.fLabel,
            color     = if (def.fLabel.isNotBlank()) skin.fLabelColor else Color.Transparent,
            fontSize  = 6.5.sp,
            maxLines  = 1,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth(),
        )

        // ── Corpo da tecla com efeito 3D ─────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp,
                                         bottomStart = 3.dp, bottomEnd = 3.dp))
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
                    shape = RoundedCornerShape(4.dp),
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication        = null,
                ) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            // Overlay de pressed (clareia ao tocar)
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
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                maxLines   = 1,
                lineHeight = 11.sp,
            )
        }

        // ── Label g (azul) — impresso ABAIXO da tecla ───────────────────
        Text(
            text      = def.gLabel,
            color     = if (def.gLabel.isNotBlank()) skin.gLabelColor else Color.Transparent,
            fontSize  = 6.5.sp,
            maxLines  = 1,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth(),
        )
    }
}
