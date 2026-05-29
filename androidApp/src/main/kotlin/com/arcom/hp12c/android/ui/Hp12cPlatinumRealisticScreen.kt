package com.arcom.hp12c.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.arcom.hp12c.engine.CalculatorEngine
import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.NumericSeparator
import com.arcom.hp12c.engine.state.TvmMode

// ─────────────────────────────────────────────────────────────────────────────
//  Tela principal — HP 12C Platinum Realistic
//  Layout fiel ao hardware: coluna esquerda (5 colunas TVM/funções) +
//  ENTER tall + coluna direita (4 colunas numéricas).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Hp12cPlatinumRealisticScreen(
    calcState: CalculatorState    = CalculatorEngine.InitialState,
    onEvent: (Event) -> Unit      = {},
    onToggleSkin: () -> Unit      = {},
    isRunning: Boolean            = false,
    modifier: Modifier            = Modifier,
) {
    var shift   by remember { mutableStateOf(ShiftState.NONE) }
    var pending by remember { mutableStateOf(PendingOp.NONE) }

    val handleKey: (String) -> Unit = handleKey@{ label ->
        if (isRunning && label != "R/S") return@handleKey
        val (event, newShift, newPending) = resolveRealisticKey(
            label   = label,
            state   = calcState,
            shift   = shift,
            pending = pending,
        )
        shift   = newShift
        pending = newPending
        event?.let { onEvent(it) }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color    = Color(0xFF1A1A1A),   // grafite escuro igual à carcaça física
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Cabeçalho metálico: display + logo HP ─────────────────────────
            MetallicHeader(
                calcState    = calcState,
                shift        = shift,
                pending      = pending,
                isRunning    = isRunning,
                onToggleSkin = onToggleSkin,
            )

            // ── Painel de teclas ──────────────────────────────────────────────
            KeypadSection(
                shift     = shift,
                pending   = pending,
                handleKey = handleKey,
                modifier  = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Resolução de toque → evento
// ─────────────────────────────────────────────────────────────────────────────

private data class RealisticKeyResult(
    val event:   Event?,
    val shift:   ShiftState,
    val pending: PendingOp,
)

private fun resolveRealisticKey(
    label:   String,
    state:   CalculatorState,
    shift:   ShiftState,
    pending: PendingOp,
): RealisticKeyResult {

    fun result(ev: Event?) = RealisticKeyResult(ev, ShiftState.NONE, PendingOp.NONE)

    // Operação pendente STO/RCL aguarda próxima tecla como registrador
    if (pending != PendingOp.NONE) {
        val regId = labelToRegisterId(label)
        if (regId != null) {
            val ev = if (pending == PendingOp.STO) Event.Memory.Store(regId)
                     else                          Event.Memory.Recall(regId)
            return result(ev)
        }
        if (label != "STO" && label != "RCL")
            return result(null)
    }

    // Teclas de shift
    if (label == "f") {
        val next = if (shift == ShiftState.F_SHIFT) ShiftState.NONE else ShiftState.F_SHIFT
        return RealisticKeyResult(null, next, PendingOp.NONE)
    }
    if (label == "g") {
        val next = if (shift == ShiftState.G_SHIFT) ShiftState.NONE else ShiftState.G_SHIFT
        return RealisticKeyResult(null, next, PendingOp.NONE)
    }

    // Shift ativo: despacha fEvent/gEvent do KeyDef
    if (shift != ShiftState.NONE) {
        // ── f + dígito (0–9) → FIX n (casas decimais do display) ────────────
        // Regra canônica do HP 12C Platinum: pressionar f seguido de qualquer
        // dígito 0–9 define o número de casas decimais (FIX 0..FIX 9).
        // Tem precedência sobre qualquer fEvent mapeado no KeyDef para dígitos.
        if (shift == ShiftState.F_SHIFT && label.length == 1 && label[0].isDigit()) {
            return result(Event.Display.Fix(label[0] - '0'))
        }
        val def = findKeyDefByLabel(label)
        val ev  = if (shift == ShiftState.F_SHIFT) def?.fEvent else def?.gEvent
        return result(ev)
    }

    // Sem shift: STO/RCL entram em modo pendente
    if (label == "STO") return RealisticKeyResult(null, ShiftState.NONE, PendingOp.STO)
    if (label == "RCL") return RealisticKeyResult(null, ShiftState.NONE, PendingOp.RCL)

    // Despacho normal
    val ev: Event? = when (label) {
        // ── Entrada numérica ──────────────────────────────────────────────────
        "0"  -> Event.Entry.Digit(0)
        "1"  -> Event.Entry.Digit(1)
        "2"  -> Event.Entry.Digit(2)
        "3"  -> Event.Entry.Digit(3)
        "4"  -> Event.Entry.Digit(4)
        "5"  -> Event.Entry.Digit(5)
        "6"  -> Event.Entry.Digit(6)
        "7"  -> Event.Entry.Digit(7)
        "8"  -> Event.Entry.Digit(8)
        "9"  -> Event.Entry.Digit(9)
        ".", "·" -> Event.Entry.DecimalPoint
        "EEX"    -> Event.Entry.Eex
        "CHS"    -> if (state.stack.isEntering) Event.Entry.ChangeSign else Event.Arith.Negate
        "ENTER"  -> Event.StackOp.Enter

        // ── Aritmética ────────────────────────────────────────────────────────
        "+"  -> Event.Arith.Add
        "−", "-" -> Event.Arith.Subtract
        "×", "x" -> Event.Arith.Multiply
        "÷", "/" -> Event.Arith.Divide

        // ── TVM — Store ou Solve conforme canStoreToTvm ──────────────────────
        "n"   -> if (state.stack.canStoreToTvm) Event.Financial.Store.N   else Event.Financial.Solve.N
        "i"   -> if (state.stack.canStoreToTvm) Event.Financial.Store.I   else Event.Financial.Solve.I
        "PV"  -> if (state.stack.canStoreToTvm) Event.Financial.Store.Pv  else Event.Financial.Solve.Pv
        "PMT" -> if (state.stack.canStoreToTvm) Event.Financial.Store.Pmt else Event.Financial.Solve.Pmt
        "FV"  -> if (state.stack.canStoreToTvm) Event.Financial.Store.Fv  else Event.Financial.Solve.Fv

        // ── Pilha ─────────────────────────────────────────────────────────────
        "CLX", "CLx" -> Event.StackOp.ClearX
        "R↓"         -> Event.StackOp.RollDown
        "x≷y", "x⇆y" -> Event.StackOp.SwapXY
        "LSTx"       -> Event.StackOp.LastX

        // ── Funções matemáticas ───────────────────────────────────────────────
        "yˣ", "yˣ"  -> Event.Transcendental.Power
        "1/x"        -> Event.Transcendental.Reciprocal
        "√x"         -> Event.Transcendental.Sqrt
        "eˣ"         -> Event.Transcendental.Exp
        "LN"         -> Event.Transcendental.Ln

        // ── Percentagem ───────────────────────────────────────────────────────
        "%"   -> Event.Percent.Of
        "Δ%"  -> Event.Percent.Delta
        "%T"  -> Event.Percent.OfTotal

        // ── Estatística ───────────────────────────────────────────────────────
        "Σ+", "Σ+" -> Event.Statistics.SigmaPlus
        "Σ−"        -> Event.Statistics.SigmaMinus

        // ── Programa ─────────────────────────────────────────────────────────
        "R/S"        -> Event.Program.RunStop
        "SST"        -> Event.Program.SingleStep
        "BST"        -> Event.Program.BackStep
        "P/R", "PRGM" -> Event.Program.TogglePrgmMode

        // ── Financeiro extra ─────────────────────────────────────────────────
        "NPV"        -> Event.Cashflow.Npv
        "IRR"        -> Event.Cashflow.Irr
        "AMORT"      -> Event.Financial.Amortize
        "INT"        -> Event.Financial.SimpleInterest
        "RND"        -> Event.Transcendental.Round

        // ON e desconhecidos: noop
        "ON"  -> null
        else  -> null
    }
    return result(ev)
}

/** Lookup de KeyDef pelo label principal — para despacho de f/g shift. */
private val LABEL_TO_KEY_DEF: Map<String, KeyDef> by lazy {
    (KEY_ROWS_PORTRAIT + KEY_ROWS_LANDSCAPE)
        .flatten()
        .associateBy { it.label }
}

private fun findKeyDefByLabel(label: String): KeyDef? =
    LABEL_TO_KEY_DEF[label]
        ?: LABEL_TO_KEY_DEF[when (label) {
            "CLX"  -> "CLx"
            "x≷y"  -> "x⇆y"
            ".", "·" -> "·"
            else   -> label
        }]

// ─────────────────────────────────────────────────────────────────────────────
//  Cabeçalho metálico (faixa de alumínio/prata com LCD + logo HP)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetallicHeader(
    calcState: CalculatorState,
    shift: ShiftState,
    pending: PendingOp,
    isRunning: Boolean,
    onToggleSkin: () -> Unit,
) {
    val engine = remember { CalculatorEngine.Default }

    val displayText = when {
        isRunning                -> "running..."
        pending == PendingOp.STO -> "STO _"
        pending == PendingOp.RCL -> "RCL _"
        else -> engine.formatDisplay(calcState, NumericSeparator.COMMA_PERIOD)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to Color(0xFFC8C8C8),
                    0.1f to Color(0xFFEAEAEA),
                    0.5f to Color(0xFFD4D4D4),
                    0.9f to Color(0xFFB4B4B4),
                    1.0f to Color(0xFFA0A0A0),
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── LCD ───────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF707078))
                    .padding(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF97A082))
                        .border(1.dp, Color(0x50000000))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy((-8).dp)) {
                        // Indicadores em linha
                        Row(
                            modifier          = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IndicatorChip("f",     active = shift == ShiftState.F_SHIFT)
                            Spacer(Modifier.width(6.dp))
                            IndicatorChip("g",     active = shift == ShiftState.G_SHIFT)
                            Spacer(Modifier.weight(1f))
                            IndicatorChip("BEGIN", active = calcState.financial.mode == TvmMode.BEGIN)
                            Spacer(Modifier.width(6.dp))
                            IndicatorChip("PRGM",  active = calcState.programState is com.arcom.hp12c.engine.state.ProgramState.Editing)
                            Spacer(Modifier.width(6.dp))
                            IndicatorChip("RUN",   active = isRunning)
                        }
                        // Número
                        Row(
                            modifier          = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text       = "RPN",
                                color      = Color(0xFF5A7050),
                                fontSize   = 7.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                text       = displayText,
                                color      = Color(0xFF0D1A0A),
                                fontSize   = 38.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                textAlign  = TextAlign.End,
                                maxLines   = 1,
                                softWrap   = false,
                                modifier   = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ── Logo HP + toggle ──────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFE0E0E0), Color(0xFFB0B0B0))
                            )
                        )
                        .border(1.dp, Color(0xFF888888), RoundedCornerShape(4.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                            onClick           = onToggleSkin,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Círculo interno do logo HP
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF282828)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text          = "hp",
                            color         = Color.White,
                            fontSize      = 20.sp,
                            fontStyle     = FontStyle.Italic,
                            fontWeight    = FontWeight.ExtraBold,
                            modifier      = Modifier.offset(y = (-1).dp, x = (-1).dp),
                            letterSpacing = (-1.5).sp,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text          = "12C PLATINUM",
                    color         = Color(0xFF222222),
                    fontSize      = 9.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

@Composable
private fun IndicatorChip(text: String, active: Boolean) {
    Text(
        text       = text,
        color      = if (active) Color(0xFF0D1A0A) else Color(0xFF5A7050),
        fontSize   = 8.sp,
        fontWeight = FontWeight.Bold,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Painel de teclas — 10 colunas, ENTER vertical (alto) na coluna 6
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KeypadSection(
    shift: ShiftState,
    pending: PendingOp,
    handleKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xFF141414))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ── Coluna 1: n yˣ R/S ON ────────────────────────────────────────
            KeyCol(1f) {
                HP12Key("n",   "AMORT", "12x",   shift, handleKey, Modifier.weight(1f))
                HP12Key("yˣ",  "PRICE", "√x",    shift, handleKey, Modifier.weight(1f))
                HP12Key("R/S", "P/R",   "PSE",   shift, handleKey, Modifier.weight(1f))
                HP12Key("ON",  "OFF",   "",      shift, handleKey, Modifier.weight(1f))
            }
            // ── Coluna 2: i 1/x SST f ────────────────────────────────────────
            KeyCol(1f) {
                HP12Key("i",   "INT",   "12÷",   shift, handleKey, Modifier.weight(1f))
                HP12Key("1/x", "YTM",   "eˣ",    shift, handleKey, Modifier.weight(1f))
                HP12Key("SST", "Σ",     "BST",   shift, handleKey, Modifier.weight(1f))
                HP12Key("f",   "",      "",      shift, handleKey, Modifier.weight(1f), isShiftKey = true)
            }
            // ── Coluna 3: PV %T R↓ g ─────────────────────────────────────────
            KeyCol(1f) {
                HP12Key("PV",  "NPV",   "CFo",   shift, handleKey, Modifier.weight(1f))
                HP12Key("%T",  "SL",    "LN",    shift, handleKey, Modifier.weight(1f))
                HP12Key("R↓",  "PRGM",  "GTO",   shift, handleKey, Modifier.weight(1f))
                HP12Key("g",   "",      "",      shift, handleKey, Modifier.weight(1f), isShiftKey = true)
            }
            // ── Coluna 4: PMT Δ% x≷y STO ─────────────────────────────────────
            KeyCol(1f) {
                HP12Key("PMT", "RND",   "CFj",   shift, handleKey, Modifier.weight(1f))
                HP12Key("Δ%",  "SOYD",  "FRAC",  shift, handleKey, Modifier.weight(1f))
                HP12Key("x≷y", "FIN",   "x≤y",   shift, handleKey, Modifier.weight(1f))
                HP12Key("STO", "",      "(",     shift, handleKey, Modifier.weight(1f))
            }
            // ── Coluna 5: FV % CLX RCL ───────────────────────────────────────
            KeyCol(1f) {
                HP12Key("FV",  "IRR",   "Nj",    shift, handleKey, Modifier.weight(1f))
                HP12Key("%",   "DB",    "INTG",  shift, handleKey, Modifier.weight(1f))
                HP12Key("CLX", "REG",   "x=0",   shift, handleKey, Modifier.weight(1f))
                HP12Key("RCL", "",      ")",     shift, handleKey, Modifier.weight(1f))
            }
            // ── Coluna 6: CHS EEX ENTER ──────────────────────────────────────
            Column(
                modifier              = Modifier.weight(1f),
                verticalArrangement   = Arrangement.spacedBy(1.dp),
            ) {
                HP12Key("CHS", "RPN",   "DATE",  shift, handleKey, Modifier.weight(1f))
                HP12Key("EEX", "ALG",   "ΔDYS",  shift, handleKey, Modifier.weight(1f))
                HP12KeyEnter(shift, handleKey, Modifier.weight(2.1f), gLabel = "LSTx")
            }
            // ── Coluna 7: 7 4 1 0 ────────────────────────────────────────────
            KeyCol(1f) {
                HP12Key("7",   "",      "BEG",   shift, handleKey, Modifier.weight(1f))
                HP12Key("4",   "",      "D.MY",  shift, handleKey, Modifier.weight(1f))
                HP12Key("1",   "",      "x̄,r",   shift, handleKey, Modifier.weight(1f))
                HP12Key("0",   "",      "x̄",     shift, handleKey, Modifier.weight(1f))
            }
            // ── Coluna 8: 8 5 2 · ────────────────────────────────────────────
            KeyCol(1f) {
                HP12Key("8",   "",      "END",   shift, handleKey, Modifier.weight(1f))
                HP12Key("5",   "",      "M.DY",  shift, handleKey, Modifier.weight(1f))
                HP12Key("2",   "",      "ŷ,r",   shift, handleKey, Modifier.weight(1f))
                HP12Key("·",   "",      "s",     shift, handleKey, Modifier.weight(1f))
            }
            // ── Coluna 9: 9 6 3 Σ+ ───────────────────────────────────────────
            KeyCol(1f) {
                HP12Key("9",   "",      "MEM",   shift, handleKey, Modifier.weight(1f))
                HP12Key("6",   "",      "x̄w",    shift, handleKey, Modifier.weight(1f))
                HP12Key("3",   "",      "n!",    shift, handleKey, Modifier.weight(1f))
                HP12Key("Σ+",  "",      "Σ-",    shift, handleKey, Modifier.weight(1f))
            }
            // ── Coluna 10: ÷ × − + ───────────────────────────────────────────
            KeyCol(1f) {
                HP12Key("÷",   "",      "x⇆y",   shift, handleKey, Modifier.weight(1f))
                HP12Key("×",   "",      "x²",    shift, handleKey, Modifier.weight(1f))
                HP12Key("−",   "",      "←",     shift, handleKey, Modifier.weight(1f))
                HP12Key("+",   "",      "LSTx",  shift, handleKey, Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Composables de tecla
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RowScope.KeyCol(
    weight: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier            = Modifier.weight(weight),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        content             = content,
    )
}

/** Tecla padrão da HP 12C Platinum. */
@Composable
private fun HP12Key(
    label: String,
    fLabel: String,
    gLabel: String,
    shift: ShiftState,
    handleKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: KeyStyle    = KeyStyle.Normal,
    keyColor: Color    = Color(0xFF282828),
    textColor: Color   = Color.White,
    isShiftKey: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Estilo Platinum: quase todas as teclas são grafite escuro
    val baseColor = when {
        isShiftKey && label == "f" -> Color(0xFFE69500) // Laranja-Dourado
        isShiftKey && label == "g" -> Color(0xFF0066CC) // Azul Profundo
        else -> Color(0xFF282828)
    }

    val isActiveShift = (isShiftKey && label == "f" && shift == ShiftState.F_SHIFT) ||
                        (isShiftKey && label == "g" && shift == ShiftState.G_SHIFT)

    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Label f ACIMA (Laranja)
        Box(modifier = Modifier.height(15.dp), contentAlignment = Alignment.BottomCenter) {
            if (fLabel.isNotBlank()) {
                AutosizeText(
                    text       = fLabel,
                    color      = Color(0xFFE69500),
                    fontSize   = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // Corpo da Tecla
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isActiveShift) baseColor.copy(alpha = 0.7f) else baseColor)
                .border(1.dp, Color(0xFF151515), RoundedCornerShape(4.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication        = null,
                ) { handleKey(label) },
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Metade superior: Label principal (Branco)
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AutosizeText(
                        text       = label,
                        color      = if (isShiftKey && label == "f") Color.Black else textColor,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Divisor e Metade inferior: gLabel (Ciano)
                if (gLabel.isNotBlank() && !isShiftKey) {
                    Box(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF3B3B3B)),
                        contentAlignment = Alignment.Center
                    ) {
                        AutosizeText(
                            text       = gLabel,
                            color      = Color(0xFF00E5FF),
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (isPressed) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0x20FFFFFF)))
            }
        }
    }
}

/** Tecla ENTER — alta, com g-label e design fiel ao Platinum. */
@Composable
private fun HP12KeyEnter(
    shift: ShiftState,
    handleKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    gLabel: String = "LSTx"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Label PREFIX acima (Laranja)
        Box(modifier = Modifier.height(15.dp), contentAlignment = Alignment.BottomCenter) {
            Text(
                text = "PREFIX",
                color = Color(0xFFE69500),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF282828))
                .border(1.dp, Color(0xFF151515), RoundedCornerShape(4.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication        = null,
                ) { handleKey("ENTER") },
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Parte superior: ENTER vertical
                Box(
                    modifier = Modifier.weight(3f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AutosizeVerticalText(
                        text = "ENTER",
                        maxFontSize = 16.sp, // Se a tela for grande, o limite é 16sp
                        minFontSize = 8.sp,  // Se a tela for minúscula, ele encolhe até 8sp
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                
                // Parte inferior: gLabel (Ciano)
                if (gLabel.isNotBlank()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF3B3B3B)),
                        contentAlignment = Alignment.Center
                    ) {
                        AutosizeText(
                            text       = gLabel,
                            color      = Color(0xFF00E5FF),
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (isPressed) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0x20FFFFFF)))
            }
        }
    }
}

@Composable
fun AutosizeText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    BoxWithConstraints(modifier = modifier) {
        val textMeasurer = rememberTextMeasurer()
        val textLayoutResult = textMeasurer.measure(text, style)
        val adjustedFontSize = if (textLayoutResult.size.width > maxWidth.toPx()) {
            fontSize * ((maxWidth.toPx() / textLayoutResult.size.width) * 0.9f)
        } else {
            fontSize
        }

        Text(
            text = text,
            style = style,
            maxLines = 1,
            fontSize = adjustedFontSize,
            color = color,
            fontWeight = fontWeight,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AutosizeVerticalText(
    text: String,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 16.sp, // Tamanho máximo que a letra pode ter
    minFontSize: TextUnit = 8.sp,  // Tamanho mínimo para não ficar ilegível
    color: Color = Color.White,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    style: TextStyle = TextStyle.Default
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()

        // Converte a altura máxima disponível de DP para Pixels
        val maxHeightPx = with(density) { maxHeight.toPx() }

        // Estado para encontrar o tamanho ideal da fonte
        val adjustedFontSize = remember(text, maxHeightPx) {
            var currentSize = if (maxFontSize.isUnspecified) 16.sp else maxFontSize
            val minSize = if (minFontSize.isUnspecified) 8.sp else minFontSize

            // Loop para diminuir a fonte até que a altura total caiba no container
            while (currentSize > minSize) {
                var totalHeight = 0f

                // Calcula a altura que a coluna teria com o tamanho de fonte atual
                text.forEach { ch ->
                    val layoutResult = textMeasurer.measure(
                        text = ch.toString(),
                        style = style.copy(fontSize = currentSize, fontWeight = fontWeight)
                    )
                    totalHeight += layoutResult.size.height
                }

                // Se a soma da altura das letras couber na tela, encontramos o tamanho ideal!
                if (totalHeight <= maxHeightPx) {
                    break
                }

                // Se não couber, diminui 1sp e tenta de novo
                currentSize = (currentSize.value - 1f).sp
            }
            currentSize
        }

        // Renderiza a coluna com o tamanho de fonte ajustado dinamicamente
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            text.forEach { ch ->
                Text(
                    text = ch.toString(),
                    color = color,
                    fontSize = adjustedFontSize,
                    fontWeight = fontWeight,
                    style = style
                )
            }
        }
    }
}

@Composable
fun Dp.toPx() = with(LocalDensity.current) { this@toPx.toPx() }

// ─────────────────────────────────────────────────────────────────────────────
//  Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(widthDp = 900, heightDp = 480, name = "Landscape")
@Composable
private fun Hp12cPlatinumRealisticPreview() {
    MaterialTheme {
        Hp12cPlatinumRealisticScreen()
    }
}
