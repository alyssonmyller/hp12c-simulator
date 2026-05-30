package br.com.alyssonmyller.calculus.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.alyssonmyller.calculus.engine.state.ConditionalTest
import br.com.alyssonmyller.calculus.engine.state.ProgramKeyCode
import br.com.alyssonmyller.calculus.engine.state.ProgramLabel
import br.com.alyssonmyller.calculus.engine.state.ProgramMemory
import br.com.alyssonmyller.calculus.engine.state.ProgramStep
import br.com.alyssonmyller.calculus.engine.state.ProgramTarget
import br.com.alyssonmyller.calculus.engine.state.RegisterId

// ─────────────────────────────────────────────────────────────────────────────
//  Composable principal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Lista rolável dos passos de programa no estilo HP 12C:
 * ```
 * 000-
 * 001-   2
 * 002-  ×
 * 003-  RTN
 * ```
 *
 * A linha [cursor] (próximo passo a ser inserido) é destacada com fundo levemente
 * diferente, replicando o comportamento do cursor de edição da HP física.
 *
 * [memory]  = fita de programa atual (imutável, snapshot do CalculatorState).
 * [cursor]  = índice 0-based da linha de cursor (ProgramState.Editing.cursor).
 * [modifier] = aplicado à LazyColumn para que o caller controle o peso de layout.
 */
@Composable
fun Hp12cProgramList(
    memory: ProgramMemory,
    cursor: Int,
    modifier: Modifier = Modifier,
) {
    val skin      = LocalSkin.current
    val listState = rememberLazyListState()

    // Rola automaticamente para manter o cursor visível toda vez que ele muda.
    LaunchedEffect(cursor) {
        val target = (cursor).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }

    LazyColumn(
        state    = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(skin.displayBg),
    ) {
        // Linha 000- sempre existe (programa começa vazio com cursor em 000).
        // As linhas 001..N correspondem a steps[0..N-1].
        val totalRows = maxOf(memory.steps.size + 1, cursor + 1)

        itemsIndexed(
            items = List(totalRows) { it },   // índices 0..totalRows-1
            key   = { _, index -> index },
        ) { _, lineIndex ->
            val isCursor = lineIndex == cursor
            ProgramStepRow(
                lineNumber  = lineIndex,
                step        = memory.steps.getOrNull(lineIndex),
                isCursor    = isCursor,
                skin        = skin,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Linha individual
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProgramStepRow(
    lineNumber: Int,
    step: ProgramStep?,
    isCursor: Boolean,
    skin: Hp12cSkin,
) {
    val bgColor = if (isCursor) skin.keyEnter else Color.Transparent
    val label   = step?.toDisplayLabel() ?: ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Número da linha (NNN-)
            Text(
                text       = "%03d-".format(lineNumber),
                color      = skin.indicatorOff,
                fontFamily = FontFamily.Monospace,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Normal,
                modifier   = Modifier.padding(end = 6.dp),
            )
            // Rótulo do passo (label)
            Text(
                text       = label,
                color      = skin.displayText,
                fontFamily = FontFamily.Monospace,
                fontSize   = 13.sp,
                fontWeight = if (isCursor) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Formatador: ProgramStep → string legível pelo usuário
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Converte um [ProgramStep] na string exibida na listagem de programa.
 * Replica a nomenclatura do teclado HP 12C (teclas, funções e operadores).
 */
internal fun ProgramStep.toDisplayLabel(): String = when (this) {

    // ── Passos de tecla simples ───────────────────────────────────────────────
    is ProgramStep.KeyStep -> keyStepLabel(keyCode, keyParam)

    // ── Controle de fluxo ────────────────────────────────────────────────────
    is ProgramStep.Goto   -> "GTO ${targetLabel(target)}"
    is ProgramStep.Gosub  -> "GSB ${targetLabel(target)}"
    ProgramStep.Return    -> "RTN"
    is ProgramStep.Label  -> "LBL ${programLabelName(label)}"

    // ── Condicionais ─────────────────────────────────────────────────────────
    is ProgramStep.Conditional -> when (test) {
        ConditionalTest.XEqZero  -> "x=0?"
        ConditionalTest.XLeqZero -> "x≤0?"
        ConditionalTest.XEqY     -> "x=y?"
        ConditionalTest.XLtY     -> "x<y?"
    }

    // ── Pausa ─────────────────────────────────────────────────────────────────
    ProgramStep.Pause -> "PSE"
}

/** Converte um `keyCode` (de [ProgramKeyCode]) na etiqueta legível equivalente. */
private fun keyStepLabel(keyCode: String, param: Int): String = when (keyCode) {
    // Entrada
    ProgramKeyCode.K_DIGIT     -> "$param"
    ProgramKeyCode.K_DOT       -> "."
    ProgramKeyCode.K_CHS_ENTRY -> "CHS"
    ProgramKeyCode.K_EEX       -> "EEX"

    // Pilha
    ProgramKeyCode.K_ENTER     -> "ENTER"
    ProgramKeyCode.K_CLX       -> "CLx"
    ProgramKeyCode.K_ROLLDOWN  -> "R↓"
    ProgramKeyCode.K_SWAPXY    -> "x⇆y"
    ProgramKeyCode.K_LASTX     -> "LSTx"

    // Aritmética
    ProgramKeyCode.K_ADD       -> "+"
    ProgramKeyCode.K_SUB       -> "−"
    ProgramKeyCode.K_MUL       -> "×"
    ProgramKeyCode.K_DIV       -> "÷"
    ProgramKeyCode.K_NEG       -> "CHS"

    // Memórias
    ProgramKeyCode.K_STO       -> "STO ${regName(param)}"
    ProgramKeyCode.K_RCL       -> "RCL ${regName(param)}"
    ProgramKeyCode.K_CLREG     -> "CLR REG"

    // TVM — armazenar
    ProgramKeyCode.K_STO_N     -> "n"
    ProgramKeyCode.K_STO_I     -> "i"
    ProgramKeyCode.K_STO_PV    -> "PV"
    ProgramKeyCode.K_STO_PMT   -> "PMT"
    ProgramKeyCode.K_STO_FV    -> "FV"

    // TVM — resolver
    ProgramKeyCode.K_SOL_N     -> "n"
    ProgramKeyCode.K_SOL_I     -> "i"
    ProgramKeyCode.K_SOL_PV    -> "PV"
    ProgramKeyCode.K_SOL_PMT   -> "PMT"
    ProgramKeyCode.K_SOL_FV    -> "FV"

    // Modo TVM
    ProgramKeyCode.K_BEGIN     -> "BEG"
    ProgramKeyCode.K_END       -> "END"
    ProgramKeyCode.K_CLEARFIN  -> "CLR FIN"
    ProgramKeyCode.K_COMPOUND  -> "STO EEX"
    ProgramKeyCode.K_SIMPLE_INT -> "INT"
    ProgramKeyCode.K_AMORT     -> "AMORT"
    ProgramKeyCode.K_SL        -> "SL"
    ProgramKeyCode.K_SOYD      -> "SOYD"
    ProgramKeyCode.K_DB        -> "DB"

    // Display
    ProgramKeyCode.K_FIX       -> "FIX $param"
    ProgramKeyCode.K_SCI       -> "SCI $param"
    ProgramKeyCode.K_ENG       -> "ENG $param"

    // Transcendentais
    ProgramKeyCode.K_RECIP     -> "1/x"
    ProgramKeyCode.K_SQUARE    -> "x²"
    ProgramKeyCode.K_SQRT      -> "√x"
    ProgramKeyCode.K_LN        -> "LN"
    ProgramKeyCode.K_EXP       -> "eˣ"
    ProgramKeyCode.K_FACT      -> "n!"
    ProgramKeyCode.K_ROUND     -> "RND"
    ProgramKeyCode.K_INT       -> "INT"
    ProgramKeyCode.K_FRAC      -> "FRAC"
    ProgramKeyCode.K_POWER     -> "yˣ"

    // Percentagem
    ProgramKeyCode.K_PCT_OF    -> "%"
    ProgramKeyCode.K_PCT_TOTAL -> "%T"
    ProgramKeyCode.K_PCT_DELTA -> "Δ%"

    // Estatística
    ProgramKeyCode.K_SIGMAPLUS  -> "Σ+"
    ProgramKeyCode.K_SIGMAMINUS -> "Σ−"
    ProgramKeyCode.K_MEAN       -> "x̄"
    ProgramKeyCode.K_STDDEV     -> "s"
    ProgramKeyCode.K_WEIGHTMEAN -> "x̄w"
    ProgramKeyCode.K_YHATR      -> "ŷ,r"
    ProgramKeyCode.K_XHATR      -> "x̂,r"
    ProgramKeyCode.K_CLRSIGMA   -> "CLR Σ"

    // Fluxo de caixa
    ProgramKeyCode.K_CFO        -> "CFo"
    ProgramKeyCode.K_CFJ        -> "CFj"
    ProgramKeyCode.K_NJ         -> "Nj"
    ProgramKeyCode.K_NPV        -> "NPV"
    ProgramKeyCode.K_IRR        -> "IRR"

    // Calendário
    ProgramKeyCode.K_DATE       -> "DATE"
    ProgramKeyCode.K_DYS        -> "DYS"
    ProgramKeyCode.K_SETDMY     -> "D.MY"
    ProgramKeyCode.K_SETMDY     -> "M.DY"

    // Especial
    ProgramKeyCode.K_RUNSTOP    -> "R/S"

    else -> keyCode   // fallback: exibe o keyCode bruto para versões futuras
}

/** Nome legível de um `RegisterId` a partir do seu ordinal. */
private fun regName(ordinal: Int): String =
    RegisterId.entries.getOrNull(ordinal)?.code ?: "?"

/** Label do destino de GTO/GSB. */
private fun targetLabel(target: ProgramTarget): String = when (target) {
    is ProgramTarget.LineTarget  -> "%03d".format(target.line)
    is ProgramTarget.LabelTarget -> programLabelName(target.label)
}

/** Nome do rótulo de programa. */
private fun programLabelName(label: ProgramLabel): String = when (label) {
    is ProgramLabel.NumericLine -> "%03d".format(label.line)
    is ProgramLabel.AlphaLabel  -> label.name.toString()
}
