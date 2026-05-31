package br.com.alyssonmyller.calculus.engine.event

import br.com.alyssonmyller.calculus.engine.state.RegisterId

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
        /** f INT: calcula juros simples a partir dos registradores n, i e PV (manual, Seção 5, p. 61). */
        object SimpleInterest : Financial()
        /**
         * `f AMORT` — amortiza `n` períodos. Lê n, i, PV, PMT dos registradores financeiros.
         * X ← totalInterest, Y ← totalPrincipal (estilo dualOutputOp, Z/T inalterados).
         * Atualiza `financial.pv` com o saldo remanescente; `n` permanece inalterado.
         * Error 6 se `INT(n) ≤ 0`. Ver `formulas/amortizacao.md`.
         */
        object Amortize : Financial()

        /**
         * `f SL` — depreciação linear. Lê `j` de X (ano), `n`/`PV`/`FV` dos registradores.
         * X ← D_j = (PV−FV)/n, Y ← RDV_j = (n−j)×D_j. dualOutputOp: Z/T inalterados, LASTx←j.
         * Nenhum registrador financeiro é modificado. Error 6 se INT(n)≤0 ou INT(j)≤0.
         * Ver `formulas/depreciacao.md` §3.
         */
        object DepreciationSL   : Financial()

        /**
         * `f SOYD` — soma dos dígitos dos anos. Lê `j` de X, `n`/`PV`/`FV` dos registradores.
         * X ← D_j = (n−j+1)/SOYD × (PV−FV), Y ← RDV_j = (n−j)(n−j+1)/(n(n+1)) × (PV−FV).
         * dualOutputOp: Z/T inalterados, LASTx←j. Nenhum registrador financeiro é modificado.
         * Error 6 se INT(n)≤0 ou INT(j)≤0. Ver `formulas/depreciacao.md` §4.
         */
        object DepreciationSOYD : Financial()

        /**
         * `f DB` — declinante. Usa `i` como fator % (ex: 200 para duplo-declinante).
         * Lê `j` de X, `n`/`i`/`PV`/`FV` dos registradores. rate = i/(100×n).
         * X ← D_j = PV×(1−rate)^(j−1)×rate, Y ← RDV_j = PV×(1−rate)^j − FV.
         * dualOutputOp: Z/T inalterados, LASTx←j. Nenhum registrador financeiro é modificado.
         * Error 6 se INT(n)≤0 ou INT(j)≤0. Ver `formulas/depreciacao.md` §5.
         */
        object DepreciationDB   : Financial()
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

    // --- Fluxos de caixa irregular (Fase 2) ---
    /**
     * Cinco teclas de fluxo de caixa da Seção 8 do manual (p. 79-104). Fonte canônica:
     * `formulas/npv-irr.md` e `test-vectors/npv-irr-vectors.json`.
     *
     * - [CashFlowZero] (`g CFo`) — armazena CF0 e limpa toda a lista de fluxos anteriores.
     * - [CashFlowJ] (`g CFj`) — acrescenta próximo CF_j à lista (máx 79 entradas).
     * - [CountJ] (`g Nj`) — define número de repetições do último CF_j (1-99).
     * - [Npv] (`f NPV`) — calcula NPV com `i` do registrador financeiro.
     * - [Irr] (`f IRR`) — calcula IRR; atualiza `financial.i` com o resultado.
     */
    sealed class Cashflow : Event() {
        object CashFlowZero : Cashflow()   // g CFo
        object CashFlowJ    : Cashflow()   // g CFj
        object CountJ       : Cashflow()   // g Nj
        object Npv          : Cashflow()   // f NPV
        object Irr          : Cashflow()   // f IRR
    }

    // --- Modo algébrico (f EEX = ALG) ---
    /**
     * `f EEX` (tecla "ALG"): alterna entre modo RPN (padrão) e modo Algébrico.
     * Em modo ALG, operações binárias (+, −, ×, ÷) ficam pendentes com precedência e
     * `ENTER` / `=` disparam a avaliação da expressão acumulada.
     * Ver: manual HP 12C Platinum, Appendix A "Algebraic Mode", p. 185-193.
     */
    sealed class AlgebraicMode : Event() {
        object Toggle   : AlgebraicMode()   // f EEX — liga/desliga ALG
        object Equals   : AlgebraicMode()   // ENTER em ALG = "="
        object LParen   : AlgebraicMode()   // "(" (STO em ALG)
        object RParen   : AlgebraicMode()   // ")" (RCL em ALG)
    }

    // --- Placeholders Fase 2 restantes ---
    // sealed class Depreciation: Event() { ... }

    /**
     * Funções de calendário — Seção 9 do manual, p. 106-113.
     * Ver `formulas/calendario.md` para o algoritmo JDN, codificação de datas e Error 8.
     */
    sealed class Calendar : Event() {
        /** `f DATE` — calcula a data resultante após N dias (X=nDays, Y=startDate). */
        object Date   : Calendar()
        /** `f DYS` — calcula o número de dias entre duas datas (X=date2, Y=date1). */
        object Dys    : Calendar()
        /** `g D.MY` — ativa modo D.MY (formato europeu `DD.MMYYYY`). */
        object SetDmy : Calendar()
        /** `g M.DY` — ativa modo M.DY (formato norte-americano `MM.DDYYYY`). Padrão de fábrica. */
        object SetMdy : Calendar()
    }
    // ─── Fase 3 — Programação ────────────────────────────────────────────────

    /**
     * Eventos de controle da programação keystroke.
     *
     * No **modo de edição** (`ProgramState.Editing`): a maioria destes eventos são gravados como
     * `ProgramStep` específico (Goto→ProgramStep.Goto, Return→ProgramStep.Return etc.).
     * Eventos não-Program recebidos em modo Editing são gravados como `ProgramStep.KeyStep`.
     *
     * No **modo normal** (`ProgramState.Idle`): controlam modo e posicionamento.
     * No **modo de execução** (`ProgramState.Running`): apenas [RunStop] para interromper.
     *
     * Ver `arquitetura/programacao.md` §7.
     */
    sealed class Program : Event() {

        // ── Controle de modo ─────────────────────────────────────────────────

        /** `f PRGM` — alterna Idle↔Editing. Em Running: ignorado. */
        data object TogglePrgmMode : Program()

        /** `R/S` — inicia execução (Idle/Editing→Running) ou para (Running→Idle). */
        data object RunStop : Program()

        /**
         * `SST` — Single Step.
         * - Em Editing: avança cursor sem executar.
         * - Em Idle: executa exatamente 1 passo a partir do pc=0 e pausa.
         */
        data object SingleStep : Program()

        /** `BST` — Back Step. Em Editing: recua cursor. Fora de PRGM: posiciona cursor. */
        data object BackStep : Program()

        /** `f CLEAR PRGM` — apaga todos os passos (só efetivo em Editing). */
        data object ClearProgram : Program()

        // ── Controle de fluxo (gravados como ProgramStep em Editing) ─────────

        /**
         * `GTO target` — em Editing: grava `ProgramStep.Goto(target)`.
         * Em Idle: posiciona cursor/pc sem iniciar execução (manual p. 113).
         */
        data class Goto(val target: br.com.alyssonmyller.calculus.engine.state.ProgramTarget) : Program()

        /**
         * `GSB target` — em Editing: grava `ProgramStep.Gosub(target)`.
         * Em Idle: executa subrotina imediatamente.
         */
        data class Gosub(val target: br.com.alyssonmyller.calculus.engine.state.ProgramTarget) : Program()

        /** `RTN` — em Editing: grava `ProgramStep.Return`. Noop em Idle. */
        data object Return : Program()

        /** `LBL label` — em Editing: grava `ProgramStep.Label(label)`. */
        data class Lbl(val label: br.com.alyssonmyller.calculus.engine.state.ProgramLabel) : Program()

        // ── Condicionais (gravados como ProgramStep.Conditional em Editing) ──

        /** `g x=0?`  — TRUE se x = 0; se FALSE, pula próximo passo. */
        data object CondXEqZero : Program()

        /** `g x≤0?`  — TRUE se x ≤ 0; se FALSE, pula próximo passo. */
        data object CondXLeqZero : Program()

        /** `g x=y?`  — TRUE se x = y (exato); se FALSE, pula próximo passo. */
        data object CondXEqY : Program()

        /** `g x<y?`  — TRUE se x < y; se FALSE, pula próximo passo. */
        data object CondXLtY : Program()

        // ── Pausa ────────────────────────────────────────────────────────────

        /** `f PSE` — em Editing: grava `ProgramStep.Pause`. */
        data object Pse : Program()

        // ── Consulta ─────────────────────────────────────────────────────────

        /** `f MEM` — exibe alocação de memória (passos usados / disponíveis). */
        data object MemInquiry : Program()
    }
}
