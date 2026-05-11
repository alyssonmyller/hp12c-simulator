package com.arcom.hp12c.android.ui

import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.state.RegisterId

// ─────────────────────────────────────────────────────────────────────────────
//  Estado de modificação da UI
// ─────────────────────────────────────────────────────────────────────────────

/** Estado do modificador shift da calculadora. */
internal enum class ShiftState { NONE, F_SHIFT, G_SHIFT }

/**
 * Operação pendente de dois toques (ex: STO→dígito = registrador de destino).
 * Enquanto pendente, o próximo toque em dígito/ponto completa a operação.
 */
internal enum class PendingOp { NONE, STO, RCL }

// ─────────────────────────────────────────────────────────────────────────────
//  Definição de tecla
// ─────────────────────────────────────────────────────────────────────────────

/** Estilo visual de uma tecla. */
internal enum class KeyStyle { Normal, Financial, ShiftF, ShiftG, Power, Enter }

/**
 * Descrição completa de uma tecla do teclado HP 12C Platinum.
 *
 * [label]       — texto principal (face da tecla, branco).
 * [fLabel]      — função f-shift (impresso em laranja ACIMA da tecla).
 * [gLabel]      — função g-shift (impresso em azul ABAIXO da tecla).
 * [event]       — evento primário. `null` = tratamento especial (CHS, teclas TVM,
 *                 f, g, STO, RCL — ver [resolveKey] em Hp12cScreen.kt).
 * [fEvent]      — evento f-shift; `null` = sem função.
 * [gEvent]      — evento g-shift; `null` = sem função.
 * [widthWeight] — peso de largura na Row de Compose (ENTER usa 2f, demais 1f).
 */
internal data class KeyDef(
    val label: String,
    val fLabel: String     = "",
    val gLabel: String     = "",
    val event: Event?      = null,
    val fEvent: Event?     = null,
    val gEvent: Event?     = null,
    val style: KeyStyle    = KeyStyle.Normal,
    val widthWeight: Float = 1f,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Mapeamento de dígito → RegisterId (para STO/RCL)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Converte o label de uma tecla em [RegisterId] para completar STO/RCL.
 * Retorna `null` se a tecla não seleciona um registrador.
 */
internal fun labelToRegisterId(label: String): RegisterId? = when (label) {
    "0"  -> RegisterId.R0
    "1"  -> RegisterId.R1
    "2"  -> RegisterId.R2
    "3"  -> RegisterId.R3
    "4"  -> RegisterId.R4
    "5"  -> RegisterId.R5
    "6"  -> RegisterId.R6
    "7"  -> RegisterId.R7
    "8"  -> RegisterId.R8
    "9"  -> RegisterId.R9
    "·"  -> RegisterId.RI   // ponto decimal → registrador indireto
    else -> null
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tabela completa de teclas — 8 linhas × 5 colunas (linha 6 tem ENTER 2×)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Grade do teclado HP 12C Platinum.
 *
 * Linhas 0-5 e 7: 5 teclas uniformes (widthWeight = 1f cada).
 * Linha 6:  CLx(1) + ENTER(2) + R↓(1) + x⇆y(1) = 5 unidades de largura totais.
 *
 * Teclas com [event] = `null` têm tratamento especial em `resolveKey()`:
 *   - n, i, PV, PMT, FV → Store ou Solve conforme `stack.isEntering`.
 *   - CHS               → Entry.ChangeSign (entrada) ou Arith.Negate (pilha).
 *   - f, g              → altera [ShiftState] sem emitir evento.
 *   - STO, RCL          → entra em modo [PendingOp] sem emitir evento.
 *   - ON                → noop (sem conceito de desligar no simulador).
 */
internal val KEY_ROWS: List<List<KeyDef>> = listOf(

    // ── Linha 0: registradores financeiros TVM ────────────────────────────
    listOf(
        KeyDef("n",   "AMORT", "12÷",
            event  = null,                          // Store.N ou Solve.N (isEntering)
            fEvent = Event.Financial.Amortize,
            style  = KeyStyle.Financial),
        KeyDef("i",   "INT",   "12×",
            event  = null,                          // Store.I ou Solve.I
            fEvent = Event.Financial.SimpleInterest,
            style  = KeyStyle.Financial),
        KeyDef("PV",  "NPV",   "CFo",
            event  = null,                          // Store.Pv ou Solve.Pv
            fEvent = Event.Cashflow.Npv,
            gEvent = Event.Cashflow.CashFlowZero,
            style  = KeyStyle.Financial),
        KeyDef("PMT", "RND",   "CFj",
            event  = null,                          // Store.Pmt ou Solve.Pmt
            fEvent = Event.Transcendental.Round,
            gEvent = Event.Cashflow.CashFlowJ,
            style  = KeyStyle.Financial),
        KeyDef("FV",  "IRR",   "Nj",
            event  = null,                          // Store.Fv ou Solve.Fv
            fEvent = Event.Cashflow.Irr,
            gEvent = Event.Cashflow.CountJ,
            style  = KeyStyle.Financial),
    ),

    // ── Linha 1: 7 8 9 ÷ yˣ ────────────────────────────────────────────────
    listOf(
        KeyDef("7",  "",   "BEG",
            event  = Event.Entry.Digit(7),
            gEvent = Event.Financial.SetBeginMode),
        KeyDef("8",  "",   "END",
            event  = Event.Entry.Digit(8),
            gEvent = Event.Financial.SetEndMode),
        KeyDef("9",  "",   "D.MY",
            event  = Event.Entry.Digit(9),
            gEvent = Event.Calendar.SetDmy),
        KeyDef("÷",  "",   "M.DY",
            event  = Event.Arith.Divide,
            gEvent = Event.Calendar.SetMdy),
        KeyDef("yˣ", "√x", "eˣ",
            event  = Event.Transcendental.Power,
            fEvent = Event.Transcendental.Sqrt,
            gEvent = Event.Transcendental.Exp),
    ),

    // ── Linha 2: 4 5 6 × 1/x ────────────────────────────────────────────────
    listOf(
        KeyDef("4",   "",   "x=y?",
            event  = Event.Entry.Digit(4),
            gEvent = Event.Program.CondXEqY),
        KeyDef("5",   "",   "x<y?",
            event  = Event.Entry.Digit(5),
            gEvent = Event.Program.CondXLtY),
        KeyDef("6",   "DATE","",
            event  = Event.Entry.Digit(6),
            fEvent = Event.Calendar.Date),
        KeyDef("×",   "DYS", "",
            event  = Event.Arith.Multiply,
            fEvent = Event.Calendar.Dys),
        KeyDef("1/x", "",   "x²",
            event  = Event.Transcendental.Reciprocal,
            gEvent = Event.Transcendental.Square),
    ),

    // ── Linha 3: 1 2 3 − % ──────────────────────────────────────────────────
    listOf(
        KeyDef("1",  "",   "PSE",
            event  = Event.Entry.Digit(1),
            gEvent = Event.Program.Pse),
        KeyDef("2",  "",   "x=0?",
            event  = Event.Entry.Digit(2),
            gEvent = Event.Program.CondXEqZero),
        KeyDef("3",  "",   "x≤0?",
            event  = Event.Entry.Digit(3),
            gEvent = Event.Program.CondXLeqZero),
        KeyDef("−",  "",   "",
            event  = Event.Arith.Subtract),
        KeyDef("%",  "Δ%", "%T",
            event  = Event.Percent.Of,
            fEvent = Event.Percent.Delta,
            gEvent = Event.Percent.OfTotal),
    ),

    // ── Linha 4: 0 · Σ+ + CHS ───────────────────────────────────────────────
    listOf(
        KeyDef("0",   "n!",  "x̄",
            event  = Event.Entry.Digit(0),
            fEvent = Event.Transcendental.Factorial,
            gEvent = Event.Statistics.Mean),
        KeyDef("·",   "INT", "s",
            event  = Event.Entry.DecimalPoint,
            fEvent = Event.Transcendental.Integer,
            gEvent = Event.Statistics.StdDev),
        KeyDef("Σ+",  "Σ−",  "ŷ,r",
            event  = Event.Statistics.SigmaPlus,
            fEvent = Event.Statistics.SigmaMinus,
            gEvent = Event.Statistics.YHatR),
        KeyDef("+",   "",    "x̂,r",
            event  = Event.Arith.Add,
            gEvent = Event.Statistics.XHatR),
        KeyDef("CHS", "EEX", "x̄w",
            event  = null,          // Entry.ChangeSign (entrada) ou Arith.Negate (pilha)
            fEvent = Event.Entry.Eex,
            gEvent = Event.Statistics.WeightedMean),
    ),

    // ── Linha 5: f g STO RCL R/S ─────────────────────────────────────────────
    listOf(
        KeyDef("f",   "", "", style = KeyStyle.ShiftF),   // event = null → toggle shift
        KeyDef("g",   "", "", style = KeyStyle.ShiftG),   // event = null → toggle shift
        KeyDef("STO", "CLR REG", "",
            event  = null,          // null → entra em PendingOp.STO
            fEvent = Event.Memory.ClearReg),
        KeyDef("RCL", "CLR FIN", "",
            event  = null,          // null → entra em PendingOp.RCL
            fEvent = Event.Financial.ClearFinancial),
        KeyDef("R/S", "PSE",  "P/R",
            event  = Event.Program.RunStop,
            fEvent = Event.Program.Pse,
            gEvent = Event.Program.TogglePrgmMode),
    ),

    // ── Linha 6: CLx  ENTER(2×)  R↓  x⇆y ────────────────────────────────────
    listOf(
        KeyDef("CLx",   "CLR Σ", "",
            event       = Event.StackOp.ClearX,
            fEvent      = Event.Statistics.ClearSigma,
            widthWeight = 1f),
        KeyDef("ENTER", "",      "LSTx",
            event       = Event.StackOp.Enter,
            gEvent      = Event.StackOp.LastX,
            style       = KeyStyle.Enter,
            widthWeight = 2f),
        KeyDef("R↓",    "",      "",
            event       = Event.StackOp.RollDown,
            widthWeight = 1f),
        KeyDef("x⇆y",  "",      "",
            event       = Event.StackOp.SwapXY,
            widthWeight = 1f),
    ),

    // ── Linha 7: ON LSTx SST BST ← ───────────────────────────────────────────
    listOf(
        KeyDef("ON",   "", "", style = KeyStyle.Power),   // noop no simulador
        KeyDef("LSTx", "LN",    "FRAC",
            event  = Event.StackOp.LastX,
            fEvent = Event.Transcendental.Ln,
            gEvent = Event.Transcendental.Fractional),
        KeyDef("SST",  "PRGM",  "",
            event  = Event.Program.SingleStep,
            fEvent = Event.Program.TogglePrgmMode),
        KeyDef("BST",  "CLR PRG","",
            event  = Event.Program.BackStep,
            fEvent = Event.Program.ClearProgram),
        KeyDef("←",    "RTN",   "",
            event  = Event.StackOp.ClearX,   // CLx durante digitação = backspace visual
            fEvent = Event.Program.Return),
    ),
)
