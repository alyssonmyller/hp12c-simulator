package com.arcom.hp12c.engine

import com.arcom.hp12c.engine.error.Hp12cError
import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.math.Hp12cDecimal
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.DisplayFormat
import com.arcom.hp12c.engine.state.NumericSeparator
import com.arcom.hp12c.engine.state.TvmMode
import com.arcom.hp12c.engine.state.acceptNewNumber
import com.arcom.hp12c.engine.state.binaryOp
import com.arcom.hp12c.engine.state.clx
import com.arcom.hp12c.engine.state.enter
import com.arcom.hp12c.engine.state.lstx
import com.arcom.hp12c.engine.state.pushValue
import com.arcom.hp12c.engine.state.rollDown
import com.arcom.hp12c.engine.state.swapXY
import com.arcom.hp12c.engine.state.unaryOp

/**
 * Implementação default de [CalculatorEngine]. Fase 1 em progresso:
 *
 *   ✔ passo 1 — [Hp12cDecimal] (aritmética BCD 10 dígitos HALF_EVEN)
 *   ✔ passo 2 — `StackOps` (pilha RPN pura com LSTx + flag stackLift)
 *   ✔ passo 3 — reducer para `Event.Entry`, `Event.StackOp`, `Event.Arith`, `Event.Memory`,
 *               `Event.Display` e `Event.AcknowledgeError`
 *   ✔ passo 4 — reducer para `Event.Financial.Store.*` + `Set(Begin|End)Mode` + `ClearFinancial`
 *   ✔ passo 5 — reducer para `Event.Financial.Solve.{Fv, Pv, Pmt}` (fechados via `powInt`)
 *               + `ToggleCompoundFractionFlag` (flag C).  ← **este arquivo**
 *   ☐ passo 6 — `Transcendentals` (ln/exp/pow) — habilita `Solve.N` e `Solve.I` (Newton).
 *   ☐ passos 7-8 — DisplayFormatter, iterar 18 vetores TVM no `TvmVectorsTest`.
 *
 * ### Contrato observado
 *
 * - **Nunca lança.** Toda exceção que vem de [Hp12cDecimal] ou de `StackOps` é capturada e
 *   traduzida para [Hp12cError] pendurado em `state.pendingError`, com a pilha preservada
 *   **tal como estava no início da operação** (regra 8 da Seção 5 de `stack-behavior.md`).
 * - **Buffer de digitação é fonte da verdade durante `isEntering`.** Quando um evento não-Entry
 *   chega (exceto `Display`/`AcknowledgeError`, puramente cosméticos), [commitEntry] faz o
 *   parse do `entryBuffer` para `stack.x` antes de qualquer outra coisa. Isso centraliza a
 *   conversão string→decimal em um único ponto.
 * - **Erro pendente absorve a próxima tecla.** Réplica fiel do aparelho físico: com "Error N"
 *   no visor, qualquer tecla limpa o erro e é ignorada de resto. A UI é encorajada (mas não
 *   obrigada) a mandar `Event.AcknowledgeError` explícito antes do evento real.
 *
 * ### Pontos de extensão restantes da Fase 1
 *
 * - `Event.Financial.Solve.N` e `Event.Financial.Solve.I` caem em `TODO("Fase 1 passo 6 —
 *   Transcendentals")` porque suas formas fechadas dependem de `ln` (n) e de
 *   `ln`/`exp` dentro de Newton-Raphson (i). `Hp12cDecimal.powInt` cobre as outras
 *   três variáveis (`Fv`, `Pv`, `Pmt`) enquanto `n` for inteiro — o que vale para
 *   todos os 18 vetores da skill `test-vectors/tvm-vectors.json`.
 * - `formatDisplay(...)` continua `TODO("Fase 1 passo 7 — DisplayFormatter")`. Isso
 *   mantém `TvmVectorsTest.tvm_001` vermelho mesmo após o passo 5, mas por motivo
 *   diferente (a parte Solve já computa o resultado correto em `state.stack.x`; é
 *   só a renderização final em FIX/SCI/ENG + separador que falta).
 */
internal class DefaultEngine : CalculatorEngine {

    /**
     * Constante `100` como `Hp12cDecimal`. Usada só para o quociente `i_percentual / 100` antes
     * de alimentar as fórmulas de TVM — a HP guarda `i` em pontos percentuais, mas a matemática
     * exige `i` em decimal. Mantida aqui (e não em `Hp12cDecimal.Companion`) porque é um detalhe
     * do reducer financeiro, não da aritmética BCD em si.
     */
    private val HUNDRED: Hp12cDecimal = Hp12cDecimal.of(100)

    override fun reduce(state: CalculatorState, event: Event): CalculatorState {
        // Erro pendente: qualquer tecla limpa e retorna. Réplica do aparelho físico.
        if (state.pendingError != null) {
            return state.copy(pendingError = null)
        }

        return when (event) {
            is Event.Entry     -> reduceEntry(state, event)
            is Event.StackOp   -> reduceStackOp(state.commitEntry(), event)
            is Event.Arith     -> reduceArith(state.commitEntry(), event)
            is Event.Memory    -> reduceMemory(state.commitEntry(), event)
            is Event.Display   -> reduceDisplay(state, event)   // NÃO comita (entrada persiste)
            Event.AcknowledgeError -> state                      // sem erro pendente: no-op
            is Event.Financial -> reduceFinancial(state, event)
        }
    }

    override fun formatDisplay(state: CalculatorState, separator: NumericSeparator): String =
        TODO("Fase 1 passo 7 — DisplayFormatter (FIX/SCI/ENG + separator + 'Error N')")

    // ───────────────────────────────────────────────────────────────────────────
    //  Entry — digit, decimal point, CHS (durante entrada), EEX
    // ───────────────────────────────────────────────────────────────────────────

    private fun reduceEntry(state: CalculatorState, event: Event.Entry): CalculatorState =
        when (event) {
            is Event.Entry.Digit        -> appendDigit(state, event.value)
            is Event.Entry.DecimalPoint -> appendDecimalPoint(state)
            is Event.Entry.ChangeSign   -> flipSignInBuffer(state)
            is Event.Entry.Eex          -> appendEex(state)
        }

    private fun appendDigit(state: CalculatorState, digit: Int): CalculatorState {
        val digitChar = ('0' + digit)
        return if (state.entryBuffer == null) {
            // Primeiro dígito: eleva/overwrite conforme stackLift, seeded com ZERO.
            // O valor real vai para stack.x no commit; durante a entrada, o buffer é o visor.
            val stack0 = state.stack.acceptNewNumber(Hp12cDecimal.ZERO)
            state.copy(entryBuffer = digitChar.toString(), stack = stack0)
        } else {
            val buf = state.entryBuffer
            // Limite de 10 dígitos na mantissa (HP 12C Platinum). Para o expoente, delegamos
            // o cap a `appendExponentDigit`.
            if ('E' in buf) {
                appendExponentDigit(state, buf, digitChar)
            } else if (countMantissaDigits(buf) >= 10) {
                state // mantissa cheia — ignora
            } else {
                state.copy(entryBuffer = buf + digitChar)
            }
        }
    }

    /** Dígito adicional no expoente (após `EEX`), limitado a 2 casas — HP 12C mostra só 2. */
    private fun appendExponentDigit(state: CalculatorState, buf: String, digitChar: Char): CalculatorState {
        val expStart = buf.indexOf('E') + 1
        val expPart = buf.substring(expStart)
        val expDigits = expPart.count { it.isDigit() }
        return if (expDigits >= 2) {
            state // expoente cheio (`Error 5` real da HP entra só ao operar)
        } else {
            state.copy(entryBuffer = buf + digitChar)
        }
    }

    private fun appendDecimalPoint(state: CalculatorState): CalculatorState {
        if (state.entryBuffer == null) {
            val stack0 = state.stack.acceptNewNumber(Hp12cDecimal.ZERO)
            return state.copy(entryBuffer = "0.", stack = stack0)
        }
        val buf = state.entryBuffer
        return if ('.' in buf || 'E' in buf) {
            state // ponto duplicado ou após EEX: ignora (HP engole a tecla)
        } else {
            state.copy(entryBuffer = "$buf.")
        }
    }

    /**
     * `CHS` durante digitação. Inverte sinal da **mantissa** (se ainda não teve `EEX`) ou do
     * **expoente** (se já teve). Se o buffer é nulo, o dispatcher da UI deveria ter mandado
     * `Event.Arith.Negate`; por robustez, apenas não fazemos nada aqui.
     */
    private fun flipSignInBuffer(state: CalculatorState): CalculatorState {
        val buf = state.entryBuffer ?: return state
        val flipped = if ('E' in buf) flipExponentSign(buf) else flipMantissaSign(buf)
        return state.copy(entryBuffer = flipped)
    }

    private fun flipMantissaSign(buf: String): String =
        if (buf.startsWith("-")) buf.substring(1) else "-$buf"

    private fun flipExponentSign(buf: String): String {
        val eIdx = buf.indexOf('E')
        val mantissa = buf.substring(0, eIdx)
        val exp = buf.substring(eIdx + 1)
        val flipped = when {
            exp.startsWith("-") -> exp.substring(1)
            exp.startsWith("+") -> "-" + exp.substring(1)
            else                -> "-$exp"
        }
        return "${mantissa}E$flipped"
    }

    private fun appendEex(state: CalculatorState): CalculatorState {
        if (state.entryBuffer == null) {
            // EEX "zerado" começa com mantissa 1 (HP mostra "1.        00").
            val stack0 = state.stack.acceptNewNumber(Hp12cDecimal.ZERO)
            return state.copy(entryBuffer = "1E", stack = stack0)
        }
        val buf = state.entryBuffer
        return if ('E' in buf) state else state.copy(entryBuffer = "${buf}E")
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  StackOp — ENTER, CLx, R↓, x⇆y, LSTx
    // ───────────────────────────────────────────────────────────────────────────

    private fun reduceStackOp(state: CalculatorState, event: Event.StackOp): CalculatorState =
        when (event) {
            Event.StackOp.Enter    -> state.copy(stack = state.stack.enter())
            Event.StackOp.ClearX   -> state.copy(stack = state.stack.clx())
            Event.StackOp.RollDown -> state.copy(stack = state.stack.rollDown())
            Event.StackOp.SwapXY   -> state.copy(stack = state.stack.swapXY())
            Event.StackOp.LastX    -> state.copy(stack = state.stack.lstx())
        }

    // ───────────────────────────────────────────────────────────────────────────
    //  Arith — +, −, ×, ÷, CHS (fora de entrada)
    // ───────────────────────────────────────────────────────────────────────────

    private fun reduceArith(state: CalculatorState, event: Event.Arith): CalculatorState {
        return try {
            val newStack = when (event) {
                Event.Arith.Add      -> state.stack.binaryOp { y, x -> y + x }
                Event.Arith.Subtract -> state.stack.binaryOp { y, x -> y - x }
                Event.Arith.Multiply -> state.stack.binaryOp { y, x -> y * x }
                Event.Arith.Divide   -> state.stack.binaryOp { y, x -> y / x }
                Event.Arith.Negate   -> state.stack.unaryOp  { x -> -x }
            }
            state.copy(stack = newStack)
        } catch (e: ArithmeticException) {
            // Divisão por zero (ou overflow pós-MC). Pilha preservada (regra 8 da
            // Seção 5 de stack-behavior.md).
            state.copy(pendingError = Hp12cError.DivisionByZero)
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Memory — STO, RCL, CLEAR REG
    // ───────────────────────────────────────────────────────────────────────────

    private fun reduceMemory(state: CalculatorState, event: Event.Memory): CalculatorState =
        when (event) {
            is Event.Memory.Store  -> state.copy(
                // STO não toca pilha nem stackLift (regra 7 da Seção 5). O commit da entrada já
                // foi feito antes de entrar aqui, então state.stack.x é o valor a armazenar.
                memory = state.memory.store(event.id, state.stack.x),
            )

            is Event.Memory.Recall -> state.copy(
                // RCL lê da memória e empurra para X respeitando stackLift, igual digitação nova.
                stack = state.stack.pushValue(state.memory[event.id]),
            )

            Event.Memory.ClearReg -> state.copy(memory = state.memory.clearAll())
        }

    // ───────────────────────────────────────────────────────────────────────────
    //  Financial — Store.{N,i,PV,PMT,FV}, SetBeginMode, SetEndMode, ClearFinancial
    //  (Solve.* e ToggleCompoundFractionFlag ficam para o passo 5)
    // ───────────────────────────────────────────────────────────────────────────

    private fun reduceFinancial(state: CalculatorState, event: Event.Financial): CalculatorState =
        when (event) {
            // Store.* e ClearFinancial comitam o buffer antes de tocar em `financial`, porque
            // o usuário acabou de digitar um número (Store) ou porque o efeito é funcional e
            // não puramente cosmético (ClearFinancial zera registradores).
            is Event.Financial.Store       -> reduceFinancialStore(state.commitEntry(), event)
            Event.Financial.ClearFinancial -> state.commitEntry().copy(
                // `f CLEAR FIN` zera só os 5 registradores de TVM — não toca pilha, memórias de
                // usuário, nem o modo BEG/END (manual, Apêndice A — "Clearing Operations").
                financial = state.financial.copy(
                    n = null, i = null, pv = null, pmt = null, fv = null,
                ),
            )

            // Mudança de modo é puramente cosmética: não comita o buffer em digitação, não toca
            // nem na pilha nem nos registradores numéricos — apenas alterna o flag BEG/END. O
            // manual indica que trocar o modo enquanto n/i/PV/PMT/FV já estão preenchidos é
            // legítimo (e muda o resultado do próximo Solve).
            Event.Financial.SetBeginMode   -> state.copy(
                financial = state.financial.copy(mode = TvmMode.BEGIN),
            )
            Event.Financial.SetEndMode     -> state.copy(
                financial = state.financial.copy(mode = TvmMode.END),
            )

            is Event.Financial.Solve -> reduceFinancialSolve(state.commitEntry(), event)

            // STO EEX: alterna o flag C (juros compostos em período fracionário). Na Fase 1 o
            // flag fica wired mas não é observável: só `n` inteiro está implementado, e nesse
            // caso a equação canônica da Seção 3 de `formulas/tvm.md` não depende do flag. O
            // efeito real entra na Fase 2 junto com as variantes da Seção 4. Não comita buffer:
            // o usuário normalmente alterna antes de iniciar uma nova conta.
            Event.Financial.ToggleCompoundFractionFlag -> state.copy(
                compoundFractionFlag = !state.compoundFractionFlag,
            )
        }

    /**
     * Armazena `stack.x` no registrador de TVM correspondente. **Não toca na pilha** (regra 7
     * da Seção 5 de `stack-behavior.md`): STO, em qualquer variante, preserva X/Y/Z/T/LSTx.
     * A única mudança em `stack` já aconteceu em [commitEntry], no caminho de entrada.
     *
     * `i` é armazenado em percentual exatamente como o usuário o digitou (`4`, não `0.04`).
     * A conversão para decimal acontece só dentro das fórmulas de TVM, no passo 5 — ver
     * Seção 3 de `formulas/tvm.md` e Seção 3.2 de `arquitetura/engine-interface.md`.
     */
    private fun reduceFinancialStore(state: CalculatorState, event: Event.Financial.Store): CalculatorState {
        val x = state.stack.x
        val newFinancial = when (event) {
            Event.Financial.Store.N   -> state.financial.copy(n   = x)
            Event.Financial.Store.I   -> state.financial.copy(i   = x)
            Event.Financial.Store.Pv  -> state.financial.copy(pv  = x)
            Event.Financial.Store.Pmt -> state.financial.copy(pmt = x)
            Event.Financial.Store.Fv  -> state.financial.copy(fv  = x)
        }
        return state.copy(financial = newFinancial)
    }

    /**
     * Resolve uma variável TVM a partir das outras quatro. Segue rigorosamente as fórmulas
     * fechadas da Seção 5 de `formulas/tvm.md`:
     *
     * - `Solve.Fv`:  `FV = -PV·(1+i)^n - (1+iS)·PMT·[((1+i)^n - 1)/i]`
     * - `Solve.Pv`:  `PV = -(1+iS)·PMT·[(1-(1+i)^(-n))/i] - FV·(1+i)^(-n)`
     * - `Solve.Pmt`: `PMT = (-PV·(1+i)^n - FV) / ((1+iS)·[((1+i)^n - 1)/i])`
     * - `Solve.N` e `Solve.I`: dependem de `ln` (e Newton-Raphson para `i`) — `TODO` do passo 6.
     *
     * Caso degenerado `i = 0`: a equação colapsa para somatórios lineares (Seção 3 final de
     * `formulas/tvm.md`) — tratamos em ramo separado para evitar divisão por zero.
     *
     * Convenção para registradores não-inicializados (`null`): são tratados como `ZERO`,
     * replicando o comportamento da HP física descrito em Seção 6 de `formulas/tvm.md`
     * ("um valor não-explicitamente fornecido é assumido zero se o registrador foi limpo
     * antes"). `ClearFinancial` define explicitamente tudo como `null`; os registradores
     * começam `null` na engine inicial, que é equivalente a "recém-limpo".
     *
     * **Pós-condições** (regras 4 e 5 da Seção 5 de `stack-behavior.md` aplicadas a Solve):
     *
     * - O resultado calculado é empurrado em X via [Stack.pushValue] — respeita `stackLift`
     *   como se fosse uma nova digitação (comportamento documentado em Apêndice A p.181 para
     *   todas as funções que produzem novo X sem consumir operandos da pilha).
     * - `LASTx` ← X antigo. O HP considera Solve uma operação que "destrói X" e preenche
     *   LSTx, para permitir `LSTx` após o cálculo.
     * - `financial.<var>` ← valor calculado (substitui o `null` original). É como a HP física
     *   se comporta: após `Solve.Fv`, pressionar `RCL FV` devolve o valor recém-calculado.
     *
     * **Erros** (captura [ArithmeticException] → `pendingError`, pilha intacta — regra 8):
     * as fórmulas são numericamente estáveis para os vetores da skill; divisão por zero só
     * ocorreria se uma condição de borda passar despercebida pelos ramos degenerados.
     */
    private fun reduceFinancialSolve(state: CalculatorState, event: Event.Financial.Solve): CalculatorState {
        // Solve.N e Solve.I dependem de `ln`/`exp`: gating pelo passo 6.
        if (event is Event.Financial.Solve.N || event is Event.Financial.Solve.I) {
            return TODO("Fase 1 passo 6 — Solve.${event::class.simpleName} requer Transcendentals (ln/exp/pow sobre Hp12cDecimal)")
        }

        val f = state.financial
        val nDecimal = f.n ?: Hp12cDecimal.ZERO
        val iPct     = f.i ?: Hp12cDecimal.ZERO   // percentual
        val pv       = f.pv  ?: Hp12cDecimal.ZERO
        val pmt      = f.pmt ?: Hp12cDecimal.ZERO
        val fv       = f.fv  ?: Hp12cDecimal.ZERO

        // `i` em decimal por período — conversão única aqui, já documentada em formulas/tvm.md §3.
        val iDec = iPct / HUNDRED
        // `n` como inteiro: Fase 1 suporta apenas `n` inteiro (ver Seção 4 de formulas/tvm.md —
        // variantes fracionárias ficam para Fase 2 junto com o flag C). `powInt` exige `Int`.
        val n = nDecimal.toIntTruncated()
        val isBegin = f.mode == TvmMode.BEGIN

        val result = try {
            when (event) {
                Event.Financial.Solve.Fv  -> computeFv(n, iDec, pv, pmt, isBegin)
                Event.Financial.Solve.Pv  -> computePv(n, iDec, pmt, fv, isBegin)
                Event.Financial.Solve.Pmt -> computePmt(n, iDec, pv, fv, isBegin)
                is Event.Financial.Solve.N, is Event.Financial.Solve.I ->
                    error("unreachable: filtrado acima")
            }
        } catch (e: ArithmeticException) {
            // Divisão por zero ou overflow na aritmética BCD — pilha preservada (regra 8).
            return state.copy(pendingError = Hp12cError.TvmNoConverge)
        }

        // Atualiza o registrador resolvido; pilha ganha resultado em X respeitando stackLift; LSTx
        // guarda o X destruído pelo Solve.
        val newFinancial = when (event) {
            Event.Financial.Solve.Fv  -> f.copy(fv  = result)
            Event.Financial.Solve.Pv  -> f.copy(pv  = result)
            Event.Financial.Solve.Pmt -> f.copy(pmt = result)
            else -> f
        }
        val newStack = state.stack.copy(lastX = state.stack.x).pushValue(result)
        return state.copy(stack = newStack, financial = newFinancial)
    }

    /**
     * `FV = -PV·(1+i)^n - (1+iS)·PMT·[((1+i)^n - 1)/i]`.
     * Ramo degenerado `i = 0`: `FV = -PV - n·PMT`.
     */
    private fun computeFv(
        n: Int, i: Hp12cDecimal, pv: Hp12cDecimal, pmt: Hp12cDecimal, isBegin: Boolean,
    ): Hp12cDecimal {
        if (i.isZero()) {
            return -pv - Hp12cDecimal.of(n) * pmt
        }
        val factor = (Hp12cDecimal.ONE + i).powInt(n)        // (1+i)^n
        val annuity = (factor - Hp12cDecimal.ONE) / i        // ((1+i)^n - 1)/i
        val begAdj = if (isBegin) Hp12cDecimal.ONE + i else Hp12cDecimal.ONE
        return -pv * factor - begAdj * pmt * annuity
    }

    /**
     * `PV = -(1+iS)·PMT·[(1-(1+i)^(-n))/i] - FV·(1+i)^(-n)`.
     * Ramo degenerado `i = 0`: `PV = -FV - n·PMT`.
     */
    private fun computePv(
        n: Int, i: Hp12cDecimal, pmt: Hp12cDecimal, fv: Hp12cDecimal, isBegin: Boolean,
    ): Hp12cDecimal {
        if (i.isZero()) {
            return -fv - Hp12cDecimal.of(n) * pmt
        }
        val discount = (Hp12cDecimal.ONE + i).powInt(-n)     // (1+i)^(-n)
        val annuity = (Hp12cDecimal.ONE - discount) / i      // (1 - (1+i)^(-n)) / i
        val begAdj = if (isBegin) Hp12cDecimal.ONE + i else Hp12cDecimal.ONE
        return -begAdj * pmt * annuity - fv * discount
    }

    /**
     * `PMT = (-PV·(1+i)^n - FV) / ((1+iS)·[((1+i)^n - 1)/i])`.
     * Ramo degenerado `i = 0`: `PMT = -(PV + FV) / n`.
     *
     * Se `n = 0` também (caso absurdo), propaga `ArithmeticException` no `/ n` e o caller
     * traduz para `Hp12cError.TvmNoConverge`.
     */
    private fun computePmt(
        n: Int, i: Hp12cDecimal, pv: Hp12cDecimal, fv: Hp12cDecimal, isBegin: Boolean,
    ): Hp12cDecimal {
        if (i.isZero()) {
            return -(pv + fv) / Hp12cDecimal.of(n)
        }
        val factor = (Hp12cDecimal.ONE + i).powInt(n)        // (1+i)^n
        val annuity = (factor - Hp12cDecimal.ONE) / i        // ((1+i)^n - 1)/i
        val begAdj = if (isBegin) Hp12cDecimal.ONE + i else Hp12cDecimal.ONE
        return (-pv * factor - fv) / (begAdj * annuity)
    }

    /**
     * Converte um `Hp12cDecimal` em `Int` truncando a parte fracionária. Usado só para alimentar
     * `Hp12cDecimal.powInt(Int)` com o `n` da TVM. **Limitação da Fase 1:** qualquer parte
     * fracionária é silenciosamente descartada — a Seção 4 de `formulas/tvm.md` descreve o
     * tratamento correto via flag C (juros simples vs compostos no pedaço fracionário), que
     * entra na Fase 2. Para os 18 vetores da skill, `n` é sempre inteiro, então não há perda.
     *
     * Implementação via `toString()` + parse: o actual class de JVM usa `toPlainString()` (sem
     * notação científica), então `substringBefore('.')` isola a parte inteira corretamente.
     * Para valores fora do intervalo de `Int` (que TVM não produz na prática — `n` real cabe em
     * `powInt` de qualquer jeito), `toInt()` lança e o caller translata para erro TVM.
     */
    private fun Hp12cDecimal.toIntTruncated(): Int {
        val s = toString()
        val intPart = if ('.' in s) s.substringBefore('.') else s
        return intPart.toInt()
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Display — FIX, SCI, ENG. Não comita o buffer (entrada em curso persiste).
    // ───────────────────────────────────────────────────────────────────────────

    private fun reduceDisplay(state: CalculatorState, event: Event.Display): CalculatorState {
        val newFormat: DisplayFormat = when (event) {
            is Event.Display.Fix -> DisplayFormat.Fix(event.places)
            is Event.Display.Sci -> DisplayFormat.Sci(event.places)
            is Event.Display.Eng -> DisplayFormat.Eng(event.places)
        }
        return state.copy(display = newFormat)
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Helpers de commit + utilidades
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Parse do `entryBuffer` para `stack.x` e limpeza do buffer. No-op quando já não há
     * entrada em curso. Tolera formas como `"1E"`, `"1E-"` ou terminando em `.` — todas
     * canônicas enquanto o usuário ainda não completou, inválidas para `Hp12cDecimal`.
     */
    private fun CalculatorState.commitEntry(): CalculatorState {
        val buf = entryBuffer ?: return this
        val parsed = try {
            Hp12cDecimal.of(normalizeForParse(buf))
        } catch (e: NumberFormatException) {
            // Buffer malformado em um caminho inesperado. Defensivo: zera e segue. Nenhum
            // teste de passo 3 deve cair aqui — se cair, é bug no dispatcher Entry.
            Hp12cDecimal.ZERO
        }
        return copy(
            stack = stack.copy(x = parsed, isEntering = false),
            entryBuffer = null,
        )
    }

    private fun normalizeForParse(buf: String): String {
        // Casos válidos durante digitação mas inválidos para BigDecimal:
        //   "1E"   → "1E0"
        //   "1E-"  → "1E-0"
        //   "1E+"  → "1E+0"
        //   "1."   → "1." (BigDecimal aceita, mantemos)
        return when {
            buf.endsWith("E")  -> "${buf}0"
            buf.endsWith("E-") -> "${buf}0"
            buf.endsWith("E+") -> "${buf}0"
            else               -> buf
        }
    }

    private fun countMantissaDigits(buf: String): Int {
        val beforeE = buf.substringBefore('E')
        return beforeE.count { it.isDigit() }
    }
}
