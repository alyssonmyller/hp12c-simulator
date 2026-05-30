package br.com.alyssonmyller.calculus.android.ui

import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.state.RegisterId
import br.com.alyssonmyller.calculus.engine.event.Event.AlgebraicMode

// ─────────────────────────────────────────────────────────────────────────────
//  Estado de modificação da UI
// ─────────────────────────────────────────────────────────────────────────────

internal enum class ShiftState { NONE, F_SHIFT, G_SHIFT }
internal enum class PendingOp  { NONE, STO, RCL }

// ─────────────────────────────────────────────────────────────────────────────
//  Definição de tecla
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Estilo visual de uma tecla.
 *
 * [Numeric]   — dígitos 0–9 e ponto decimal: fundo CLARO, texto preto.
 *              Corresponde às teclas cinza-claro (#D1D1D1) da HP 12C Platinum.
 * [Financial] — registradores TVM (n, i, PV, PMT, FV): fundo ligeiramente diferente.
 * [ShiftF]    — tecla "f" dourada.
 * [ShiftG]    — tecla "g" azul cobalto.
 * [Power]     — tecla ON.
 * [Enter]     — tecla ENTER.
 * [Normal]    — todas as demais operações: fundo escuro, texto branco.
 */
internal enum class KeyStyle { Normal, Numeric, Financial, ShiftF, ShiftG, Power, Enter }

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
    "·"  -> RegisterId.RI
    else -> null
}

// ─────────────────────────────────────────────────────────────────────────────
//  Grade portrait — 8 linhas × 5 colunas
//  Linha 6 (stack): CLx(1) + ENTER(2) + R↓(1) + x⇆y(1) = 5 unidades
// ─────────────────────────────────────────────────────────────────────────────

internal val KEY_ROWS_PORTRAIT: List<List<KeyDef>> = listOf(

    // ── Linha 0: TVM ─────────────────────────────────────────────────────────
    listOf(
        KeyDef("n",   "AMORT", "12÷",
            event  = null,
            fEvent = Event.Financial.Amortize,
            style  = KeyStyle.Financial),
        KeyDef("i",   "INT",   "12×",
            event  = null,
            fEvent = Event.Financial.SimpleInterest,
            style  = KeyStyle.Financial),
        KeyDef("PV",  "NPV",   "CFo",
            event  = null,
            fEvent = Event.Cashflow.Npv,
            gEvent = Event.Cashflow.CashFlowZero,
            style  = KeyStyle.Financial),
        KeyDef("PMT", "RND",   "CFj",
            event  = null,
            fEvent = Event.Transcendental.Round,
            gEvent = Event.Cashflow.CashFlowJ,
            style  = KeyStyle.Financial),
        KeyDef("FV",  "IRR",   "Nj",
            event  = null,
            fEvent = Event.Cashflow.Irr,
            gEvent = Event.Cashflow.CountJ,
            style  = KeyStyle.Financial),
    ),

    // ── Linha 1: 7 8 9 ÷ yˣ ─────────────────────────────────────────────────
    listOf(
        KeyDef("7",  "",    "BEG",
            event  = Event.Entry.Digit(7),
            gEvent = Event.Financial.SetBeginMode,
            style  = KeyStyle.Numeric),
        KeyDef("8",  "",    "END",
            event  = Event.Entry.Digit(8),
            gEvent = Event.Financial.SetEndMode,
            style  = KeyStyle.Numeric),
        KeyDef("9",  "",    "D.MY",
            event  = Event.Entry.Digit(9),
            gEvent = Event.Calendar.SetDmy,
            style  = KeyStyle.Numeric),
        KeyDef("÷",  "",    "M.DY",
            event  = Event.Arith.Divide,
            gEvent = Event.Calendar.SetMdy),
        KeyDef("yˣ", "√x",  "eˣ",
            event  = Event.Transcendental.Power,
            fEvent = Event.Transcendental.Sqrt,
            gEvent = Event.Transcendental.Exp),
    ),

    // ── Linha 2: 4 5 6 × 1/x ─────────────────────────────────────────────────
    listOf(
        KeyDef("4",   "",     "x=y?",
            event  = Event.Entry.Digit(4),
            gEvent = Event.Program.CondXEqY,
            style  = KeyStyle.Numeric),
        KeyDef("5",   "",     "x<y?",
            event  = Event.Entry.Digit(5),
            gEvent = Event.Program.CondXLtY,
            style  = KeyStyle.Numeric),
        KeyDef("6",   "",     "",
            event  = Event.Entry.Digit(6),
            style  = KeyStyle.Numeric),
        KeyDef("×",   "DYS",  "",
            event  = Event.Arith.Multiply,
            fEvent = Event.Calendar.Dys),
        KeyDef("1/x", "",     "x²",
            event  = Event.Transcendental.Reciprocal,
            gEvent = Event.Transcendental.Square),
    ),

    // ── Linha 3: 1 2 3 − % ───────────────────────────────────────────────────
    listOf(
        KeyDef("1",  "",    "PSE",
            event  = Event.Entry.Digit(1),
            gEvent = Event.Program.Pse,
            style  = KeyStyle.Numeric),
        KeyDef("2",  "",    "x=0?",
            event  = Event.Entry.Digit(2),
            gEvent = Event.Program.CondXEqZero,
            style  = KeyStyle.Numeric),
        KeyDef("3",  "",    "x≤0?",
            event  = Event.Entry.Digit(3),
            gEvent = Event.Program.CondXLeqZero,
            style  = KeyStyle.Numeric),
        KeyDef("−",  "",    "",
            event  = Event.Arith.Subtract),
        KeyDef("%",  "Δ%",  "%T",
            event  = Event.Percent.Of,
            fEvent = Event.Percent.Delta,
            gEvent = Event.Percent.OfTotal),
    ),

    // ── Linha 4: 0 · Σ+ + CHS ────────────────────────────────────────────────
    listOf(
        KeyDef("0",   "n!",  "x̄",
            event  = Event.Entry.Digit(0),
            fEvent = Event.Transcendental.Factorial,
            gEvent = Event.Statistics.Mean,
            style  = KeyStyle.Numeric),
        KeyDef("·",   "INT", "s",
            event  = Event.Entry.DecimalPoint,
            fEvent = Event.Transcendental.Integer,
            gEvent = Event.Statistics.StdDev,
            style  = KeyStyle.Numeric),
        KeyDef("Σ+",  "Σ−",  "ŷ,r",
            event  = Event.Statistics.SigmaPlus,
            fEvent = Event.Statistics.SigmaMinus,
            gEvent = Event.Statistics.YHatR),
        KeyDef("+",   "",    "x̂,r",
            event  = Event.Arith.Add,
            gEvent = Event.Statistics.XHatR),
        KeyDef("CHS", "EEX", "x̄w",
            event  = null,
            fEvent = Event.Entry.Eex,
            gEvent = Event.Statistics.WeightedMean),
    ),

    // ── Linha 5: f g STO RCL R/S ─────────────────────────────────────────────
    listOf(
        KeyDef("f",   "", "", style = KeyStyle.ShiftF),
        KeyDef("g",   "", "", style = KeyStyle.ShiftG),
        KeyDef("STO", "CLR REG", "",
            event  = null,
            fEvent = Event.Memory.ClearReg),
        KeyDef("RCL", "CLR FIN", "",
            event  = null,
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
        KeyDef("ON",   "", "", style = KeyStyle.Power),
        KeyDef("LSTx", "LN",     "FRAC",
            event  = Event.StackOp.LastX,
            fEvent = Event.Transcendental.Ln,
            gEvent = Event.Transcendental.Fractional),
        KeyDef("SST",  "PRGM",   "",
            event  = Event.Program.SingleStep,
            fEvent = Event.Program.TogglePrgmMode),
        KeyDef("BST",  "CLR PRG","",
            event  = Event.Program.BackStep,
            fEvent = Event.Program.ClearProgram),
        KeyDef("←",    "RTN",    "",
            event  = Event.StackOp.ClearX,
            fEvent = Event.Program.Return),
    ),
)

// ─────────────────────────────────────────────────────────────────────────────
//  Grade landscape — 4 linhas × 10 colunas
//  Fiel ao layout real da HP 12C Platinum em orientação horizontal.
//  Linha 3: ON(1)+CLx(1)+ENTER(2)+R↓(1)+x⇆y(1)+LSTx(1)+SST(1)+BST(1)+←(1) = 10
// ─────────────────────────────────────────────────────────────────────────────

internal val KEY_ROWS_LANDSCAPE: List<List<KeyDef>> = listOf(

    // ── Linha L0: TVM + CHS + dígitos topo + ÷ ───────────────────────────────
    listOf(
        KeyDef("n",   "AMORT", "12÷",
            event  = null,
            fEvent = Event.Financial.Amortize,
            style  = KeyStyle.Financial),
        KeyDef("i",   "INT",   "12×",
            event  = null,
            fEvent = Event.Financial.SimpleInterest,
            style  = KeyStyle.Financial),
        KeyDef("PV",  "NPV",   "CFo",
            event  = null,
            fEvent = Event.Cashflow.Npv,
            gEvent = Event.Cashflow.CashFlowZero,
            style  = KeyStyle.Financial),
        KeyDef("PMT", "RND",   "CFj",
            event  = null,
            fEvent = Event.Transcendental.Round,
            gEvent = Event.Cashflow.CashFlowJ,
            style  = KeyStyle.Financial),
        KeyDef("FV",  "IRR",   "Nj",
            event  = null,
            fEvent = Event.Cashflow.Irr,
            gEvent = Event.Cashflow.CountJ,
            style  = KeyStyle.Financial),
        KeyDef("CHS", "EEX",   "x̄w",
            event  = null,
            fEvent = Event.Entry.Eex,
            gEvent = Event.Statistics.WeightedMean),
        KeyDef("7",  "",    "BEG",
            event  = Event.Entry.Digit(7),
            gEvent = Event.Financial.SetBeginMode,
            style  = KeyStyle.Numeric),
        KeyDef("8",  "",    "END",
            event  = Event.Entry.Digit(8),
            gEvent = Event.Financial.SetEndMode,
            style  = KeyStyle.Numeric),
        KeyDef("9",  "",    "D.MY",
            event  = Event.Entry.Digit(9),
            gEvent = Event.Calendar.SetDmy,
            style  = KeyStyle.Numeric),
        KeyDef("÷",  "",    "M.DY",
            event  = Event.Arith.Divide,
            gEvent = Event.Calendar.SetMdy),
    ),

    // ── Linha L1: funções + dígitos meio + × ─────────────────────────────────
    listOf(
        KeyDef("yˣ", "√x",  "eˣ",
            event  = Event.Transcendental.Power,
            fEvent = Event.Transcendental.Sqrt,
            gEvent = Event.Transcendental.Exp),
        KeyDef("1/x","",    "x²",
            event  = Event.Transcendental.Reciprocal,
            gEvent = Event.Transcendental.Square),
        KeyDef("%",  "Δ%",  "%T",
            event  = Event.Percent.Of,
            fEvent = Event.Percent.Delta,
            gEvent = Event.Percent.OfTotal),
        KeyDef("Σ+", "Σ−",  "ŷ,r",
            event  = Event.Statistics.SigmaPlus,
            fEvent = Event.Statistics.SigmaMinus,
            gEvent = Event.Statistics.YHatR),
        KeyDef("+",  "",    "x̂,r",
            event  = Event.Arith.Add,
            gEvent = Event.Statistics.XHatR),
        KeyDef("4",  "",    "x=y?",
            event  = Event.Entry.Digit(4),
            gEvent = Event.Program.CondXEqY,
            style  = KeyStyle.Numeric),
        KeyDef("5",  "",    "x<y?",
            event  = Event.Entry.Digit(5),
            gEvent = Event.Program.CondXLtY,
            style  = KeyStyle.Numeric),
        KeyDef("6",  "DATE","",
            event  = Event.Entry.Digit(6),
            fEvent = Event.Calendar.Date,
            style  = KeyStyle.Numeric),
        KeyDef("×",  "DYS", "",
            event  = Event.Arith.Multiply,
            fEvent = Event.Calendar.Dys),
        KeyDef("−",  "",    "",
            event  = Event.Arith.Subtract),
    ),

    // ── Linha L2: shift + memórias + dígitos baixo + − ───────────────────────
    listOf(
        KeyDef("f",   "", "", style = KeyStyle.ShiftF),
        KeyDef("g",   "", "", style = KeyStyle.ShiftG),
        KeyDef("STO", "CLR REG", "",
            event  = null,
            fEvent = Event.Memory.ClearReg),
        KeyDef("RCL", "CLR FIN", "",
            event  = null,
            fEvent = Event.Financial.ClearFinancial),
        KeyDef("R/S", "PSE",  "P/R",
            event  = Event.Program.RunStop,
            fEvent = Event.Program.Pse,
            gEvent = Event.Program.TogglePrgmMode),
        KeyDef("1",  "",    "PSE",
            event  = Event.Entry.Digit(1),
            gEvent = Event.Program.Pse,
            style  = KeyStyle.Numeric),
        KeyDef("2",  "",    "x=0?",
            event  = Event.Entry.Digit(2),
            gEvent = Event.Program.CondXEqZero,
            style  = KeyStyle.Numeric),
        KeyDef("3",  "",    "x≤0?",
            event  = Event.Entry.Digit(3),
            gEvent = Event.Program.CondXLeqZero,
            style  = KeyStyle.Numeric),
        KeyDef("0",  "n!",  "x̄",
            event  = Event.Entry.Digit(0),
            fEvent = Event.Transcendental.Factorial,
            gEvent = Event.Statistics.Mean,
            style  = KeyStyle.Numeric),
        KeyDef("·",  "INT", "s",
            event  = Event.Entry.DecimalPoint,
            fEvent = Event.Transcendental.Integer,
            gEvent = Event.Statistics.StdDev,
            style  = KeyStyle.Numeric),
    ),

    // ── Linha L3: ON + stack + ENTER(2×) + navegação ─────────────────────────
    //  ON(1)+CLx(1)+ENTER(2)+R↓(1)+x⇆y(1)+LSTx(1)+SST(1)+BST(1)+←(1) = 10
    listOf(
        KeyDef("ON",    "", "",
            style       = KeyStyle.Power),
        KeyDef("CLx",   "CLR Σ", "",
            event       = Event.StackOp.ClearX,
            fEvent      = Event.Statistics.ClearSigma),
        KeyDef("ENTER", "",      "LSTx",
            event       = Event.StackOp.Enter,
            gEvent      = Event.StackOp.LastX,
            style       = KeyStyle.Enter,
            widthWeight = 2f),
        KeyDef("R↓",    "",      "",
            event       = Event.StackOp.RollDown),
        KeyDef("x⇆y",  "",      "",
            event       = Event.StackOp.SwapXY),
        KeyDef("LSTx",  "LN",    "FRAC",
            event       = Event.StackOp.LastX,
            fEvent      = Event.Transcendental.Ln,
            gEvent      = Event.Transcendental.Fractional),
        KeyDef("SST",   "PRGM",  "",
            event       = Event.Program.SingleStep,
            fEvent      = Event.Program.TogglePrgmMode),
        KeyDef("BST",   "CLR PRG","",
            event       = Event.Program.BackStep,
            fEvent      = Event.Program.ClearProgram),
        KeyDef("←",     "RTN",   "",
            event       = Event.StackOp.ClearX,
            fEvent      = Event.Program.Return),
    ),
)

// alias para compatibilidade com código que usa KEY_ROWS
internal val KEY_ROWS: List<List<KeyDef>> = KEY_ROWS_PORTRAIT

/**
 * KeyDefs extras que existem apenas para o lookup de f/g shift mas NÃO são
 * renderizadas em nenhuma linha do teclado.
 *
 * Caso: tecla "EEX" aparece como label principal no layout landscape
 * (HP12Key "EEX"/"ALG"/"ΔDYS"), mas nos KEY_ROWS ela só existe como
 * fLabel de "CHS". Adicionamos aqui para que findKeyDefByLabel("EEX")
 * resolva corretamente f+EEX → Event.AlgebraicMode.Toggle.
 */
internal val KEY_DEFS_LOOKUP_ONLY: List<KeyDef> = listOf(
    KeyDef("EEX", "ALG", "ΔDYS",
        event  = Event.Entry.Eex,
        fEvent = Event.AlgebraicMode.Toggle),
)
