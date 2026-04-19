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
 *               ← **este arquivo**
 *   ☐ passo 5 — reducer para `Event.Financial.Solve.*` (TVM) + flag C (STO EEX)
 *   ☐ passos 6-8 — Transcendentals, DisplayFormatter, iterar vetores TVM.
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
 * ### Ponto de extensão para Fase 1 passo 5
 *
 * `Event.Financial.Solve.*` (os 5 "calcula a partir das outras 4") e
 * `Event.Financial.ToggleCompoundFractionFlag` (Flag C, juros compostos para período
 * fracionário) caem em `TODO` pontual — o passo 5 preenche, destravando
 * `TvmVectorsTest.tvm-001` e os demais 17 vetores TVM.
 */
internal class DefaultEngine : CalculatorEngine {

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

            is Event.Financial.Solve                   -> TODO("Fase 1 passo 5 — Solve TVM")
            Event.Financial.ToggleCompoundFractionFlag -> TODO("Fase 1 passo 5 — Flag C (STO EEX)")
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
