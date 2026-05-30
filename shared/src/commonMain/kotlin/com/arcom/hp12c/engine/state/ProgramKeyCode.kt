package br.com.alyssonmyller.calculus.engine.state

import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.state.ProgramStep.KeyStep

/**
 * Tabela de mapeamento bidirecional entre `Event` e `ProgramStep.KeyStep`.
 *
 * Cada tecla armazenável num programa tem um [keyCode] estável (string ASCII).
 * Para eventos com parâmetro inteiro (dígitos, memórias, formato de display) o
 * parâmetro vai em [KeyStep.keyParam]; para objetos singleton, [PARAM_NONE].
 *
 * **Eventos não armazenáveis** (retornam `null` em [encode]):
 *   - `Event.AcknowledgeError` — sentinela de UI, sem significado em programa.
 *   - `Event.Program.*` — controle de fluxo gravado como `ProgramStep` próprio
 *     (exceto `RunStop` que é gravado como `KeyStep(K_RUNSTOP)`).
 *
 * Ver `arquitetura/programacao.md` §7.1.
 */
object ProgramKeyCode {

    // ─── constantes de keyCode ────────────────────────────────────────────────

    // Entry
    const val K_DIGIT      = "D"
    const val K_DOT        = "DOT"
    const val K_CHS_ENTRY  = "CHSE"   // CHS durante digitação (Entry.ChangeSign)
    const val K_EEX        = "EEX"

    // Stack
    const val K_ENTER      = "ENTER"
    const val K_CLX        = "CLX"
    const val K_ROLLDOWN   = "ROLLDOWN"
    const val K_SWAPXY     = "SWAPXY"
    const val K_LASTX      = "LASTX"

    // Arith
    const val K_ADD        = "ADD"
    const val K_SUB        = "SUB"
    const val K_MUL        = "MUL"
    const val K_DIV        = "DIV"
    const val K_NEG        = "NEG"    // CHS fora de digitação (Arith.Negate)

    // Memory (param = RegisterId.ordinal)
    const val K_STO        = "STO"
    const val K_RCL        = "RCL"
    const val K_CLREG      = "CLREG"

    // Financial store
    const val K_STO_N      = "STON"
    const val K_STO_I      = "STOI"
    const val K_STO_PV     = "STOPV"
    const val K_STO_PMT    = "STOPMT"
    const val K_STO_FV     = "STOFV"

    // Financial solve
    const val K_SOL_N      = "SOLN"
    const val K_SOL_I      = "SOLI"
    const val K_SOL_PV     = "SOLPV"
    const val K_SOL_PMT    = "SOLPMT"
    const val K_SOL_FV     = "SOLFV"

    // Financial misc
    const val K_BEGIN      = "BEGIN"
    const val K_END        = "END"
    const val K_CLEARFIN   = "CLEARFIN"
    const val K_COMPOUND   = "COMPOUND"
    const val K_SIMPLE_INT = "SIMPLEINT"
    const val K_AMORT      = "AMORT"
    const val K_SL         = "SL"
    const val K_SOYD       = "SOYD"
    const val K_DB         = "DB"

    // Display (param = places)
    const val K_FIX        = "FIX"
    const val K_SCI        = "SCI"
    const val K_ENG        = "ENG"

    // Transcendental
    const val K_RECIP      = "RECIP"
    const val K_SQUARE     = "SQUARE"
    const val K_SQRT       = "SQRT"
    const val K_LN         = "LN"
    const val K_EXP        = "EXP"
    const val K_FACT       = "FACT"
    const val K_ROUND      = "ROUND"
    const val K_INT        = "INT"
    const val K_FRAC       = "FRAC"
    const val K_POWER      = "POWER"

    // Percent
    const val K_PCT_OF     = "PCTOF"
    const val K_PCT_TOTAL  = "PCTTOTAL"
    const val K_PCT_DELTA  = "PCTDELTA"

    // Statistics
    const val K_SIGMAPLUS  = "SIGMAPLUS"
    const val K_SIGMAMINUS = "SIGMAMINUS"
    const val K_MEAN       = "MEAN"
    const val K_STDDEV     = "STDDEV"
    const val K_WEIGHTMEAN = "WEIGHTMEAN"
    const val K_YHATR      = "YHATR"
    const val K_XHATR      = "XHATR"
    const val K_CLRSIGMA   = "CLRSIGMA"

    // Cashflow
    const val K_CFO        = "CFO"
    const val K_CFJ        = "CFJ"
    const val K_NJ         = "NJ"
    const val K_NPV        = "NPV"
    const val K_IRR        = "IRR"

    // Calendar
    const val K_DATE       = "DATE"
    const val K_DYS        = "DYS"
    const val K_SETDMY     = "SETDMY"
    const val K_SETMDY     = "SETMDY"

    // Special: R/S stored in program = stop execution when reached
    const val K_RUNSTOP    = "RUNSTOP"

    // ─── encode ──────────────────────────────────────────────────────────────

    /**
     * Converte um `Event` em `KeyStep`. Retorna `null` para eventos não armazenáveis.
     */
    fun encode(event: Event): KeyStep? = when (event) {
        // Entry
        is Event.Entry.Digit      -> KeyStep(K_DIGIT, event.value)
        Event.Entry.DecimalPoint  -> KeyStep(K_DOT)
        Event.Entry.ChangeSign    -> KeyStep(K_CHS_ENTRY)
        Event.Entry.Eex           -> KeyStep(K_EEX)

        // Stack
        Event.StackOp.Enter       -> KeyStep(K_ENTER)
        Event.StackOp.ClearX      -> KeyStep(K_CLX)
        Event.StackOp.RollDown    -> KeyStep(K_ROLLDOWN)
        Event.StackOp.SwapXY      -> KeyStep(K_SWAPXY)
        Event.StackOp.LastX       -> KeyStep(K_LASTX)

        // Arith
        Event.Arith.Add           -> KeyStep(K_ADD)
        Event.Arith.Subtract      -> KeyStep(K_SUB)
        Event.Arith.Multiply      -> KeyStep(K_MUL)
        Event.Arith.Divide        -> KeyStep(K_DIV)
        Event.Arith.Negate        -> KeyStep(K_NEG)

        // Memory
        is Event.Memory.Store     -> KeyStep(K_STO, event.id.ordinal)
        is Event.Memory.Recall    -> KeyStep(K_RCL, event.id.ordinal)
        Event.Memory.ClearReg     -> KeyStep(K_CLREG)

        // Financial store
        Event.Financial.Store.N   -> KeyStep(K_STO_N)
        Event.Financial.Store.I   -> KeyStep(K_STO_I)
        Event.Financial.Store.Pv  -> KeyStep(K_STO_PV)
        Event.Financial.Store.Pmt -> KeyStep(K_STO_PMT)
        Event.Financial.Store.Fv  -> KeyStep(K_STO_FV)

        // Financial solve
        Event.Financial.Solve.N   -> KeyStep(K_SOL_N)
        Event.Financial.Solve.I   -> KeyStep(K_SOL_I)
        Event.Financial.Solve.Pv  -> KeyStep(K_SOL_PV)
        Event.Financial.Solve.Pmt -> KeyStep(K_SOL_PMT)
        Event.Financial.Solve.Fv  -> KeyStep(K_SOL_FV)

        // Financial misc
        Event.Financial.SetBeginMode               -> KeyStep(K_BEGIN)
        Event.Financial.SetEndMode                 -> KeyStep(K_END)
        Event.Financial.ClearFinancial             -> KeyStep(K_CLEARFIN)
        Event.Financial.ToggleCompoundFractionFlag -> KeyStep(K_COMPOUND)
        Event.Financial.SimpleInterest             -> KeyStep(K_SIMPLE_INT)
        Event.Financial.Amortize                   -> KeyStep(K_AMORT)
        Event.Financial.DepreciationSL             -> KeyStep(K_SL)
        Event.Financial.DepreciationSOYD           -> KeyStep(K_SOYD)
        Event.Financial.DepreciationDB             -> KeyStep(K_DB)

        // Display
        is Event.Display.Fix -> KeyStep(K_FIX, event.places)
        is Event.Display.Sci -> KeyStep(K_SCI, event.places)
        is Event.Display.Eng -> KeyStep(K_ENG, event.places)

        // Transcendental
        Event.Transcendental.Reciprocal  -> KeyStep(K_RECIP)
        Event.Transcendental.Square      -> KeyStep(K_SQUARE)
        Event.Transcendental.Sqrt        -> KeyStep(K_SQRT)
        Event.Transcendental.Ln          -> KeyStep(K_LN)
        Event.Transcendental.Exp         -> KeyStep(K_EXP)
        Event.Transcendental.Factorial   -> KeyStep(K_FACT)
        Event.Transcendental.Round       -> KeyStep(K_ROUND)
        Event.Transcendental.Integer     -> KeyStep(K_INT)
        Event.Transcendental.Fractional  -> KeyStep(K_FRAC)
        Event.Transcendental.Power       -> KeyStep(K_POWER)

        // Percent
        Event.Percent.Of      -> KeyStep(K_PCT_OF)
        Event.Percent.OfTotal -> KeyStep(K_PCT_TOTAL)
        Event.Percent.Delta   -> KeyStep(K_PCT_DELTA)

        // Statistics
        Event.Statistics.SigmaPlus    -> KeyStep(K_SIGMAPLUS)
        Event.Statistics.SigmaMinus   -> KeyStep(K_SIGMAMINUS)
        Event.Statistics.Mean         -> KeyStep(K_MEAN)
        Event.Statistics.StdDev       -> KeyStep(K_STDDEV)
        Event.Statistics.WeightedMean -> KeyStep(K_WEIGHTMEAN)
        Event.Statistics.YHatR        -> KeyStep(K_YHATR)
        Event.Statistics.XHatR        -> KeyStep(K_XHATR)
        Event.Statistics.ClearSigma   -> KeyStep(K_CLRSIGMA)

        // Cashflow
        Event.Cashflow.CashFlowZero -> KeyStep(K_CFO)
        Event.Cashflow.CashFlowJ    -> KeyStep(K_CFJ)
        Event.Cashflow.CountJ       -> KeyStep(K_NJ)
        Event.Cashflow.Npv          -> KeyStep(K_NPV)
        Event.Cashflow.Irr          -> KeyStep(K_IRR)

        // Calendar
        Event.Calendar.Date   -> KeyStep(K_DATE)
        Event.Calendar.Dys    -> KeyStep(K_DYS)
        Event.Calendar.SetDmy -> KeyStep(K_SETDMY)
        Event.Calendar.SetMdy -> KeyStep(K_SETMDY)

        // Program: RunStop is the only Program event stored as KeyStep
        is Event.Program -> if (event is Event.Program.RunStop) KeyStep(K_RUNSTOP) else null

        // AlgebraicMode toggle pode ser gravado em programa (como qualquer tecla)
        Event.AlgebraicMode.Toggle -> KeyStep("ALG_TOGGLE")
        Event.AlgebraicMode.Equals -> KeyStep("ALG_EQUALS")
        Event.AlgebraicMode.LParen -> KeyStep("ALG_LPAREN")
        Event.AlgebraicMode.RParen -> KeyStep("ALG_RPAREN")

        // Not storable
        Event.AcknowledgeError -> null
    }

    // ─── decode ──────────────────────────────────────────────────────────────

    /**
     * Converte um `KeyStep` de volta para um `Event`. Retorna `null` para key codes
     * desconhecidos (compatibilidade futura via `ignoreUnknownKeys`).
     */
    fun decode(step: KeyStep): Event? {
        val p = step.keyParam
        return when (step.keyCode) {
            K_DIGIT      -> if (p in 0..9) Event.Entry.Digit(p) else null
            K_DOT        -> Event.Entry.DecimalPoint
            K_CHS_ENTRY  -> Event.Entry.ChangeSign
            K_EEX        -> Event.Entry.Eex

            K_ENTER      -> Event.StackOp.Enter
            K_CLX        -> Event.StackOp.ClearX
            K_ROLLDOWN   -> Event.StackOp.RollDown
            K_SWAPXY     -> Event.StackOp.SwapXY
            K_LASTX      -> Event.StackOp.LastX

            K_ADD        -> Event.Arith.Add
            K_SUB        -> Event.Arith.Subtract
            K_MUL        -> Event.Arith.Multiply
            K_DIV        -> Event.Arith.Divide
            K_NEG        -> Event.Arith.Negate

            K_STO        -> RegisterId.entries.getOrNull(p)?.let { Event.Memory.Store(it) }
            K_RCL        -> RegisterId.entries.getOrNull(p)?.let { Event.Memory.Recall(it) }
            K_CLREG      -> Event.Memory.ClearReg

            K_STO_N      -> Event.Financial.Store.N
            K_STO_I      -> Event.Financial.Store.I
            K_STO_PV     -> Event.Financial.Store.Pv
            K_STO_PMT    -> Event.Financial.Store.Pmt
            K_STO_FV     -> Event.Financial.Store.Fv

            K_SOL_N      -> Event.Financial.Solve.N
            K_SOL_I      -> Event.Financial.Solve.I
            K_SOL_PV     -> Event.Financial.Solve.Pv
            K_SOL_PMT    -> Event.Financial.Solve.Pmt
            K_SOL_FV     -> Event.Financial.Solve.Fv

            K_BEGIN      -> Event.Financial.SetBeginMode
            K_END        -> Event.Financial.SetEndMode
            K_CLEARFIN   -> Event.Financial.ClearFinancial
            K_COMPOUND   -> Event.Financial.ToggleCompoundFractionFlag
            K_SIMPLE_INT -> Event.Financial.SimpleInterest
            K_AMORT      -> Event.Financial.Amortize
            K_SL         -> Event.Financial.DepreciationSL
            K_SOYD       -> Event.Financial.DepreciationSOYD
            K_DB         -> Event.Financial.DepreciationDB

            K_FIX        -> if (p in 0..9) Event.Display.Fix(p) else null
            K_SCI        -> if (p in 0..9) Event.Display.Sci(p) else null
            K_ENG        -> if (p in 0..9) Event.Display.Eng(p) else null

            K_RECIP      -> Event.Transcendental.Reciprocal
            K_SQUARE     -> Event.Transcendental.Square
            K_SQRT       -> Event.Transcendental.Sqrt
            K_LN         -> Event.Transcendental.Ln
            K_EXP        -> Event.Transcendental.Exp
            K_FACT       -> Event.Transcendental.Factorial
            K_ROUND      -> Event.Transcendental.Round
            K_INT        -> Event.Transcendental.Integer
            K_FRAC       -> Event.Transcendental.Fractional
            K_POWER      -> Event.Transcendental.Power

            K_PCT_OF     -> Event.Percent.Of
            K_PCT_TOTAL  -> Event.Percent.OfTotal
            K_PCT_DELTA  -> Event.Percent.Delta

            K_SIGMAPLUS  -> Event.Statistics.SigmaPlus
            K_SIGMAMINUS -> Event.Statistics.SigmaMinus
            K_MEAN       -> Event.Statistics.Mean
            K_STDDEV     -> Event.Statistics.StdDev
            K_WEIGHTMEAN -> Event.Statistics.WeightedMean
            K_YHATR      -> Event.Statistics.YHatR
            K_XHATR      -> Event.Statistics.XHatR
            K_CLRSIGMA   -> Event.Statistics.ClearSigma

            K_CFO        -> Event.Cashflow.CashFlowZero
            K_CFJ        -> Event.Cashflow.CashFlowJ
            K_NJ         -> Event.Cashflow.CountJ
            K_NPV        -> Event.Cashflow.Npv
            K_IRR        -> Event.Cashflow.Irr

            K_DATE       -> Event.Calendar.Date
            K_DYS        -> Event.Calendar.Dys
            K_SETDMY     -> Event.Calendar.SetDmy
            K_SETMDY     -> Event.Calendar.SetMdy

            // K_RUNSTOP is handled specially in executeStep (stops execution)
            K_RUNSTOP    -> null   // caller checks for RUNSTOP before decode

            // AlgebraicMode
            "ALG_TOGGLE" -> Event.AlgebraicMode.Toggle
            "ALG_EQUALS" -> Event.AlgebraicMode.Equals
            "ALG_LPAREN" -> Event.AlgebraicMode.LParen
            "ALG_RPAREN" -> Event.AlgebraicMode.RParen

            else         -> null   // unknown keycode — skip gracefully
        }
    }
}
