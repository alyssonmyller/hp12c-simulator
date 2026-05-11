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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
 *
 * [skin] e [onToggleSkin] controlam a aparência visual. O skin ativo é injetado
 * via [CompositionLocalProvider] para que todos os composables filhos o acessem
 * sem parâmetro extra (leem [LocalSkin].current).
 *
 * [isRunning] = `true` enquanto um programa executa em background (R/S assíncrono);
 * exibido como indicador `RUN` no visor e bloqueia o teclado (exceto R/S de cancel).
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
        // Durante execução de programa só R/S é permitido (para cancelar).
        if (isRunning && def.label != "R/S") return@keyPress
        val (event, newShift, newPending) = resolveKey(def, calcState, shift, pending)
        shift   = newShift
        pending = newPending
        event?.let { onEvent(it) }
    }

    CompositionLocalProvider(LocalSkin provides skin) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(skin.body)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Hp12cDisplay(
                calcState    = calcState,
                shift        = shift,
                pending      = pending,
                isRunning    = isRunning,
                onToggleSkin = onToggleSkin,
                modifier     = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))

            // Em modo de edição PRGM, exibe a listagem de passos acima do teclado.
            val prgmState = calcState.programState
            if (prgmState is ProgramState.Editing) {
                Hp12cProgramList(
                    memory   = calcState.programMemory,
                    cursor   = prgmState.cursor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.height(4.dp))
            }

            Hp12cKeyboard(
                onKeyPress = onKeyPress,
                modifier   = Modifier
                    .fillMaxWidth()
                    .weight(if (prgmState is ProgramState.Editing) 0.55f else 1f),
            )
        }
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
 *
 * Regras de prioridade:
 * 1. Se há operação pendente (STO/RCL), o próximo dígito ou ponto completa-a.
 * 2. f e g alternam o estado de shift (segundo toque no mesmo ← cancela).
 * 3. Teclas STO e RCL entram em modo pendente.
 * 4. f-shift e g-shift usam [KeyDef.fEvent]/[KeyDef.gEvent].
 * 5. CHS e teclas TVM têm resolução dependente de [CalculatorState].
 */
private fun resolveKey(
    def: KeyDef,
    state: CalculatorState,
    shift: ShiftState,
    pending: PendingOp,
): KeyResult {

    // ── 1. Operação pendente (STO/RCL + dígito) ──────────────────────────────
    if (pending != PendingOp.NONE) {
        val regId = labelToRegisterId(def.label)
        if (regId != null) {
            val ev = if (pending == PendingOp.STO) Event.Memory.Store(regId)
                     else                          Event.Memory.Recall(regId)
            return KeyResult(ev, ShiftState.NONE, PendingOp.NONE)
        }
        // Qualquer tecla não-registrador cancela o modo pendente sem evento.
        if (def.label != "STO" && def.label != "RCL") {
            return KeyResult(null, ShiftState.NONE, PendingOp.NONE)
        }
    }

    // ── 2. Teclas de shift (f e g) ───────────────────────────────────────────
    if (def.style == KeyStyle.ShiftF) {
        val next = if (shift == ShiftState.F_SHIFT) ShiftState.NONE else ShiftState.F_SHIFT
        return KeyResult(null, next, PendingOp.NONE)
    }
    if (def.style == KeyStyle.ShiftG) {
        val next = if (shift == ShiftState.G_SHIFT) ShiftState.NONE else ShiftState.G_SHIFT
        return KeyResult(null, next, PendingOp.NONE)
    }

    // ── Após qualquer tecla "funcional", shift reseta para NONE ──────────────
    fun result(ev: Event?) = KeyResult(ev, ShiftState.NONE, PendingOp.NONE)

    // ── 3. STO / RCL entram em modo pendente ─────────────────────────────────
    if (shift == ShiftState.NONE) {
        when (def.label) {
            "STO" -> return KeyResult(null, ShiftState.NONE, PendingOp.STO)
            "RCL" -> return KeyResult(null, ShiftState.NONE, PendingOp.RCL)
        }
    }
    if (shift == ShiftState.F_SHIFT) {
        // f+STO = CLR REG,  f+RCL = CLR FIN
        when (def.label) {
            "STO" -> return result(def.fEvent)
            "RCL" -> return result(def.fEvent)
        }
    }

    // ── 4. Teclas com f/g shift (evento estático) ─────────────────────────────
    val ev: Event? = when (shift) {
        ShiftState.F_SHIFT -> def.fEvent
        ShiftState.G_SHIFT -> def.gEvent
        ShiftState.NONE    -> resolveNoPressEvent(def, state)
    }
    return result(ev)
}

/**
 * Resolve o evento para toque direto (sem shift), lidando com os casos
 * dependentes de estado: teclas TVM (Store vs Solve) e CHS (digitação vs pilha).
 */
private fun resolveNoPressEvent(def: KeyDef, state: CalculatorState): Event? {
    return when (def.label) {
        // canStoreToTvm=true quando X tem valor "fresco": digitado, resultado de aritmética,
        // ENTER, RCL, etc. Garante "9 ENTER 12 ÷ i" → Store.I=0.75, e "10000 ENTER PV"
        // → Store.Pv=10000. Quando false (após Store/Solve anterior ou estado inicial)
        // → Resolve a variável a partir dos demais registradores TVM.
        "n"   -> if (state.stack.canStoreToTvm) Event.Financial.Store.N   else Event.Financial.Solve.N
        "i"   -> if (state.stack.canStoreToTvm) Event.Financial.Store.I   else Event.Financial.Solve.I
        "PV"  -> if (state.stack.canStoreToTvm) Event.Financial.Store.Pv  else Event.Financial.Solve.Pv
        "PMT" -> if (state.stack.canStoreToTvm) Event.Financial.Store.Pmt else Event.Financial.Solve.Pmt
        "FV"  -> if (state.stack.canStoreToTvm) Event.Financial.Store.Fv  else Event.Financial.Solve.Fv
        // CHS: inverte sinal do buffer de entrada ou nega X da pilha.
        "CHS" -> if (state.stack.isEntering) Event.Entry.ChangeSign else Event.Arith.Negate
        // Teclas com evento estático definido no KeyDef.
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
    onToggleSkin: () -> Unit,
    isRunning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val engine = remember { CalculatorEngine.Default }

    // Texto principal do visor.
    val displayText = when {
        isRunning                -> "running..."
        pending == PendingOp.STO -> "STO  _"
        pending == PendingOp.RCL -> "RCL  _"
        else                     -> engine.formatDisplay(calcState, NumericSeparator.COMMA_PERIOD)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(skin.displayBg)
            .border(1.dp, Color(0xFF0A1A0A), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Linha de indicadores + toggle de skin ────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IndicatorLabel("f",     active = shift == ShiftState.F_SHIFT)
                Spacer(Modifier.width(6.dp))
                IndicatorLabel("g",     active = shift == ShiftState.G_SHIFT)
                Spacer(Modifier.weight(1f))
                IndicatorLabel("BEGIN", active = calcState.financial.mode == TvmMode.BEGIN)
                Spacer(Modifier.width(6.dp))
                IndicatorLabel("PRGM",  active = calcState.programState is ProgramState.Editing)
                Spacer(Modifier.width(6.dp))
                IndicatorLabel("RUN",   active = isRunning)
                Spacer(Modifier.width(8.dp))
                // Botão de toggle de skin — pequeno, discreto, alinhado à direita
                SkinToggleButton(skinName = skin.name, onClick = onToggleSkin)
            }
            // ── Número principal ─────────────────────────────────────────────
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
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun IndicatorLabel(text: String, active: Boolean) {
    val skin = LocalSkin.current
    Text(
        text       = text,
        color      = if (active) skin.indicatorOn else skin.indicatorOff,
        fontSize   = 9.sp,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * Botão compacto que alterna entre os skins disponíveis.
 * Exibe o nome do skin atual precedido de "⊞".
 */
@Composable
private fun SkinToggleButton(skinName: String, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Text(
        text     = "⊞ $skinName",
        color    = skin.indicatorOff,
        fontSize = 8.sp,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication        = null,
            onClick           = onClick,
        ),
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
        modifier  = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        KEY_ROWS.forEachIndexed { _, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                row.forEach { def ->
                    Hp12cKeyButton(
                        def     = def,
                        onClick = { onKeyPress(def) },
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
    val pressedOverlay = if (isPressed) Color(0x55FFFFFF) else Color.Transparent

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Label f (laranja) acima da tecla
        Text(
            text      = def.fLabel,
            color     = if (def.fLabel.isNotBlank()) skin.fLabelColor else Color.Transparent,
            fontSize  = 7.sp,
            maxLines  = 1,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth(),
        )

        // Corpo da tecla
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(keyBg)
                .clickable(interactionSource = interactionSource, indication = null) { onClick() }
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Overlay de pressed state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(3.dp))
                    .background(pressedOverlay),
            )
            Text(
                text       = def.label,
                color      = skin.keyLabel,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                maxLines   = 1,
            )
        }

        // Label g (azul) abaixo da tecla
        Text(
            text      = def.gLabel,
            color     = if (def.gLabel.isNotBlank()) skin.gLabelColor else Color.Transparent,
            fontSize  = 7.sp,
            maxLines  = 1,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth(),
        )
    }
}
