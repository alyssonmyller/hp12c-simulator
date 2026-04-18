package com.arcom.hp12c.engine.event

import com.arcom.hp12c.engine.state.RegisterId

/**
 * Alfabeto de teclas lógicas da HP 12C — um `Event` = uma tecla (após o adapter de UI
 * resolver prefixos `f`/`g`). Cobre toda a Fase 1; placeholders de Fase 2/3 estão
 * documentados nos comentários finais, sem subclasse ativa.
 *
 * Ver Seção 5 de `arquitetura/engine-interface.md`.
 */
sealed class Event {

    // --- 5.1 Entrada de dígitos ---
    sealed class Entry : Event() {
        data class Digit(val value: Int) : Entry() { init { require(value in 0..9) } }
        object DecimalPoint : Entry()
        /** CHS durante digitação: inverte sinal do número em curso. */
        object ChangeSign   : Entry()
        /** Entra em notação científica (tecla EEX). */
        object Eex          : Entry()
    }

    // --- 5.2 Pilha ---
    sealed class StackOp : Event() {
        object Enter       : StackOp()
        object ClearX      : StackOp()   // CLx
        object RollDown    : StackOp()   // R↓
        object SwapXY      : StackOp()   // x⇆y
        object LastX       : StackOp()   // g LSTx
    }

    // --- 5.3 Aritmética binária + CHS fora de digitação ---
    sealed class Arith : Event() {
        object Add      : Arith()
        object Subtract : Arith()
        object Multiply : Arith()
        object Divide   : Arith()
        /** CHS quando NÃO estamos em entrada: negata X na pilha. */
        object Negate   : Arith()
    }

    // --- 5.4 Memórias de usuário ---
    sealed class Memory : Event() {
        data class Store (val id: RegisterId) : Memory()
        data class Recall(val id: RegisterId) : Memory()
        /** f CLEAR REG: zera R0..R9 e Ri. Não afeta os registradores financeiros. */
        object ClearReg : Memory()
    }

    // --- 5.5 Financeiro (TVM) ---
    sealed class Financial : Event() {

        /** Armazena X no registrador correspondente (usuário pressiona a tecla APÓS digitar um valor). */
        sealed class Store : Financial() {
            object N   : Store()
            object I   : Store()
            object Pv  : Store()
            object Pmt : Store()
            object Fv  : Store()
        }

        /**
         * Resolve a variável a partir das outras quatro. A distinção Store-vs-Solve é feita pelo
         * reducer baseado em `state.stack.isEntering` (ver Seção 5.1 de engine-interface.md).
         */
        sealed class Solve : Financial() {
            object N   : Solve()
            object I   : Solve()
            object Pv  : Solve()
            object Pmt : Solve()
            object Fv  : Solve()
        }

        object SetBeginMode   : Financial()
        object SetEndMode     : Financial()
        /** f CLEAR FIN: zera n, i, PV, PMT, FV (não toca pilha nem memórias de usuário). */
        object ClearFinancial : Financial()
        /** STO EEX: alterna a flag C (juros simples vs compostos em período fracionário). */
        object ToggleCompoundFractionFlag : Financial()
    }

    // --- 5.6 Formato de display ---
    sealed class Display : Event() {
        data class Fix(val places: Int) : Display() { init { require(places in 0..9) } }
        data class Sci(val places: Int) : Display() { init { require(places in 0..9) } }
        data class Eng(val places: Int) : Display() { init { require(places in 0..9) } }
    }

    /** Consome um `pendingError`. Emitido pela UI automaticamente antes de qualquer outro evento
     *  quando o visor mostra "Error N". */
    object AcknowledgeError : Event()

    // --- Placeholders Fase 2 (comentados propositalmente) ---
    // sealed class Transcendental : Event() { object Ln, Exp, Sqrt, Reciprocal, YToX, NFactorial, Round, Integer, Fractional }
    // sealed class Percent        : Event() { object Of, OfTotal, Delta }
    // sealed class Statistics     : Event() { object SigmaPlus, SigmaMinus, Mean, StdDev, LinearRegression }
    // sealed class Calendar       : Event() { object Date, Dys, DmyMode, MdyMode }
    // sealed class Cashflow       : Event() { object CashFlowZero, CashFlowJ, CountJ, Npv, Irr }
    // sealed class Depreciation   : Event() { object StraightLine, SumOfYears, DecliningBalance }
    // sealed class AlgebraicToggle: Event() { object AlgMode, RpnMode }
    //
    // --- Placeholders Fase 3 (programação) ---
    // sealed class Program        : Event() { object PrgmToggle, Goto, Gosub, Return, RunStop, SingleStep, BackStep }
}
