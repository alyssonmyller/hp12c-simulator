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

    // --- 5.7 Funções matemáticas e de alteração de números (Fase 2, bloco 1) ---
    /**
     * Funções matemáticas unárias (exceto [Power], que é binária) da Seção 7 do manual
     * `bpia5314.pdf` (p. 85-87). Documentação canônica das fórmulas em
     * `formulas/transcendentais.md` §3 e §4; vetores em
     * `test-vectors/transcendentais-vectors.json`.
     *
     * Unárias (consomem X, produzem X, preservam Y/Z/T, preenchem `LASTx`):
     *   [Reciprocal], [Square], [Sqrt], [Ln], [Exp], [Factorial], [Integer], [Fractional]
     *
     * Excepcional — **não** preenche `LASTx**:
     *   [Round] — materializa o formato de display corrente no registrador X
     *            (`formulas/transcendentais.md` §3.7). Único caso em que "LSTx" não
     *            devolveria o valor pré-operação na HP física.
     *
     * Binária:
     *   [Power] — `y^x`. Opera como uma binária clássica: consome Y e X, desce a pilha
     *             (Z→Y, T fica sticky), preenche `LASTx`.
     */
    sealed class Transcendental : Event() {
        object Reciprocal : Transcendental()  // [1/x]
        object Square     : Transcendental()  // [g][x²]
        object Sqrt       : Transcendental()  // [g][√x]
        object Ln         : Transcendental()  // [g][LN]
        object Exp        : Transcendental()  // [g][e^x]
        object Factorial  : Transcendental()  // [g][n!]
        object Round      : Transcendental()  // [f][RND]
        object Integer    : Transcendental()  // [g][INT]
        object Fractional : Transcendental()  // [g][FRAC]
        object Power      : Transcendental()  // [y^x]  — binária
    }

    // --- 5.8 Funções de percentagem (Fase 2, bloco 1) ---
    /**
     * Três teclas de percentagem da Seção 2 do manual (p. 27-29). Documentação canônica em
     * `formulas/transcendentais.md` §2.
     *
     * Todas são operações binárias sobre Y e X, mas distinguem-se pelo comportamento da pilha:
     *
     *   - [Of] (`%`) e [OfTotal] (`%T`) **retêm Y** — Y continua com o valor base/total
     *     original após a operação (permite idioms como `300 ENTER 14 % −` para calcular valor
     *     líquido em dois toques). Implementadas via `Stack.percentOp` de `StackOps.kt`.
     *
     *   - [Delta] (`Δ%`) desce a pilha como binária clássica (Z→Y, T sticky). Implementada
     *     via `Stack.binaryOp`.
     *
     * Condição de erro: `y = 0` em [Delta] e [OfTotal] dispara Error 0 por paralelismo com
     * divisão por zero (ambiguidade #2 documentada em §7 de `formulas/transcendentais.md`).
     *
     * Nomes `Of`/`OfTotal`/`Delta` preservam os placeholders originais deste arquivo (pré-
     * Fase 2) e evitam colisão com o nome da sealed class `Percent` enclosing, que proibiria
     * um `object Percent` nested.
     */
    sealed class Percent : Event() {
        object Of      : Percent()  // [%]
        object OfTotal : Percent()  // [%T]
        object Delta   : Percent()  // [Δ%]
    }

    // --- 5.9 Estatística (Fase 2, bloco 2) ---
    /**
     * Sete teclas estatísticas da Seção 6 do manual (p. 79-84). Fonte canônica:
     * `formulas/estatistica.md` e `test-vectors/estatistica-vectors.json`.
     *
     * **Convenção de pilha:** [SigmaPlus]/[SigmaMinus] são operações binárias — consomem Y e X.
     * [Mean], [StdDev], [YHatR], [XHatR] são de "saída dupla": escrevem X **e** Y diretamente,
     * sem mover Z/T. [WeightedMean] é unária (escreve só X). [ClearSigma] é limpeza focal.
     *
     * **Compartilhamento de registradores:** R1..R6 de `MemoryRegisters` são fisicamente os
     * mesmos slots dos acumuladores estatísticos — decisão §1.2 de `formulas/estatistica.md`.
     */
    sealed class Statistics : Event() {
        /** `Σ+` — acumula o par (Y=y, X=x) da pilha; empurra novo n em X. */
        object SigmaPlus    : Statistics()
        /** `g Σ-` — desacumula o par (Y=y, X=x) da pilha; empurra novo n em X. */
        object SigmaMinus   : Statistics()
        /** `g x̄` — média aritmética; X ← x̄, Y ← ȳ (escrita direta, não push). */
        object Mean         : Statistics()
        /** `g s` — desvio-padrão amostral; X ← sₓ, Y ← sᵧ. */
        object StdDev       : Statistics()
        /** `g x̄w` — média ponderada; X ← x̄w, Y preservado. */
        object WeightedMean : Statistics()
        /** `g ŷ,r` — estimativa de ŷ dado novo x (atual X); Y ← r (correlação). */
        object YHatR        : Statistics()
        /** `g x̂,r` — estimativa de x̂ dado novo y (atual X); Y ← r (correlação). */
        object XHatR        : Statistics()
        /** `f CLEAR Σ` — zera R1..R6 e a pilha inteira (Apêndice A p. 181). */
        object ClearSigma   : Statistics()
    }

    // --- Placeholders Fase 2 restantes (comentados propositalmente) ---
    // sealed class Calendar       : Event() { object Date, Dys, DmyMode, MdyMode }
    // sealed class Cashflow       : Event() { object CashFlowZero, CashFlowJ, CountJ, Npv, Irr }
    // sealed class Depreciation   : Event() { object StraightLine, SumOfYears, DecliningBalance }
    // sealed class AlgebraicToggle: Event() { object AlgMode, RpnMode }
    //
    // --- Placeholders Fase 3 (programação) ---
    // sealed class Program        : Event() { object PrgmToggle, Goto, Gosub, Return, RunStop, SingleStep, BackStep }
}
