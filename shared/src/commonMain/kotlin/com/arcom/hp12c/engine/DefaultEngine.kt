package com.arcom.hp12c.engine

import com.arcom.hp12c.engine.error.Hp12cError
import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.format.DisplayFormatter
import com.arcom.hp12c.engine.math.Hp12cDecimal
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.DisplayFormat
import com.arcom.hp12c.engine.state.NumericSeparator
import com.arcom.hp12c.engine.state.TvmMode
import com.arcom.hp12c.engine.state.RegisterId
import com.arcom.hp12c.engine.state.acceptNewNumber
import com.arcom.hp12c.engine.state.binaryOp
import com.arcom.hp12c.engine.state.clx
import com.arcom.hp12c.engine.state.dualOutputOp
import com.arcom.hp12c.engine.state.enter
import com.arcom.hp12c.engine.state.lstx
import com.arcom.hp12c.engine.state.percentOp
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
 *               + `ToggleCompoundFractionFlag` (flag C).
 *   ✔ passo 6 — `Transcendentals` (ln/exp/pow) habilitou `Solve.N` (fórmula fechada + teto)
 *               e `Solve.I` (Newton-Raphson sobre a equação TVM).
 *   ✔ passo 7 — [DisplayFormatter] (FIX/SCI/ENG + separador pt-BR/en-US + `"Error n"`);
 *               `formatDisplay` passa a delegar tudo pra lá.  ← **este arquivo**
 *   ☐ passo 8 — iterar os 18 vetores TVM no `TvmVectorsTest` via leitor de resource KMP.
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
 * - Passo 8: expandir `TvmVectorsTest` para consumir os 18 vetores de
 *   `commonTest/resources/test-vectors/tvm-vectors.json` via `expect/actual fun readTestResource`
 *   (cada plataforma lê seu resource). Hoje o `tvm_001` é inlinado.
 */
internal class DefaultEngine : CalculatorEngine {

    /**
     * Constante `100` como `Hp12cDecimal`. Usada só para o quociente `i_percentual / 100` antes
     * de alimentar as fórmulas de TVM — a HP guarda `i` em pontos percentuais, mas a matemática
     * exige `i` em decimal. Mantida aqui (e não em `Hp12cDecimal.Companion`) porque é um detalhe
     * do reducer financeiro, não da aritmética BCD em si.
     */
    private val HUNDRED: Hp12cDecimal = Hp12cDecimal.of(100)
    private val THIRTY_SIX_THOUSAND: Hp12cDecimal = Hp12cDecimal.of(36000)

    override fun reduce(state: CalculatorState, event: Event): CalculatorState {
        // Erro pendente: qualquer tecla limpa e retorna. Réplica do aparelho físico.
        if (state.pendingError != null) {
            return state.copy(pendingError = null)
        }

        return when (event) {
            is Event.Entry          -> reduceEntry(state, event)
            is Event.StackOp        -> reduceStackOp(state.commitEntry(), event)
            is Event.Arith          -> reduceArith(state.commitEntry(), event)
            is Event.Memory         -> reduceMemory(state.commitEntry(), event)
            is Event.Display        -> reduceDisplay(state, event)   // NÃO comita (entrada persiste)
            Event.AcknowledgeError  -> state                          // sem erro pendente: no-op
            is Event.Financial      -> reduceFinancial(state, event)
            is Event.Transcendental -> reduceTranscendental(state.commitEntry(), event)
            is Event.Percent        -> reducePercent(state.commitEntry(), event)
            is Event.Statistics     -> reduceStatistics(state.commitEntry(), event)
        }
    }

    override fun formatDisplay(state: CalculatorState, separator: NumericSeparator): String =
        DisplayFormatter.format(state, separator)

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
            // §8.6 de formulas/estatistica.md: se ŷ,r/x̂,r marcou r inválido, o swap
            // que tentaria trazer r ao visor dispara Error 2 em vez de realizar a troca.
            Event.StackOp.SwapXY   -> if (state.statisticsRInvalid) {
                state.copy(pendingError = Hp12cError.StatisticsCollinear, statisticsRInvalid = false)
            } else {
                state.copy(stack = state.stack.swapXY(), statisticsRInvalid = false)
            }
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
    //  Financial — Store.{N,i,PV,PMT,FV}, SetBeginMode, SetEndMode, ClearFinancial,
    //  Solve.{Fv,Pv,Pmt,N,I} e ToggleCompoundFractionFlag.
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

            Event.Financial.SimpleInterest -> reduceSimpleInterest(state.commitEntry())
        }

    /**
     * `f INT` — Juros Simples. Lê `n`, `i` (percentual) e `PV` dos registradores financeiros,
     * calcula `INT = PV × i × n / 36000` (base 360 dias, manual Seção 5 p. 61) e escreve:
     *
     * - X ← INT
     * - Y ← PV (do registrador financeiro, não Y₀ da pilha — para que `+` dê o montante)
     * - Z, T inalterados
     * - LASTx ← X antigo
     *
     * Registradores financeiros **não** são atualizados (diferente de TVM `Solve.*`).
     * Overflow numérico → Error 1 via [Hp12cError.Overflow].
     */
    private fun reduceSimpleInterest(state: CalculatorState): CalculatorState {
        val f = state.financial
        val n  = f.n  ?: Hp12cDecimal.ZERO
        val i  = f.i  ?: Hp12cDecimal.ZERO   // percentual, ex: 8 para 8%
        val pv = f.pv ?: Hp12cDecimal.ZERO

        val result = try {
            computeSimpleInterest(n, i, pv)
        } catch (e: ArithmeticException) {
            return state.copy(pendingError = Hp12cError.StoreOverflow)   // Error 1 — overflow numérico
        }

        val newStack = state.stack.copy(
            x = result,
            y = pv,
            lastX = state.stack.x,
            stackLiftEnabled = true,
            isEntering = false,
        )
        return state.copy(stack = newStack)
    }

    /** `INT = PV × i × n / 36000` — base 360 dias fixa, `i` em percentual. */
    private fun computeSimpleInterest(
        n: Hp12cDecimal, i: Hp12cDecimal, pv: Hp12cDecimal,
    ): Hp12cDecimal = pv * i * n / THIRTY_SIX_THOUSAND

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
     * - `Solve.N`:   `n = ln(((1+iS)·PMT - FV·i) / ((1+iS)·PMT + PV·i)) / ln(1+i)`, sempre
     *                arredondado para cima (Moretti Ex. 12: `13,36 → 14` no visor).
     * - `Solve.I`:   forma fechada `i = (-FV/PV)^(1/n) - 1` quando `PMT = 0`; Newton-Raphson
     *                sobre a equação TVM canônica caso contrário, com derivada por diferença
     *                central. Resultado percentual.
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
     *   Para `Solve.N`, o valor armazenado em `financial.n` é o **teto** (igual ao mostrado
     *   no visor); para `Solve.I`, o valor armazenado é em **percentual** (forma como `i` foi
     *   digitado originalmente).
     *
     * **Erros** (pilha preservada — regra 8):
     *
     * - [TvmSignMismatch] (ratio ≤ 0 em `Solve.N`/`Solve.I` com PMT=0, ou `n ≤ 0` em
     *   `Solve.I`) → `Hp12cError.TvmInvalidSigns`. Reproduz `Error 5` do manual quando a
     *   combinação de sinais de PV/PMT/FV é inconsistente com a equação TVM.
     * - [ArithmeticException] genérica (divisão por zero, overflow no BCD, Newton-Raphson
     *   não convergiu em 100 iterações) → `Hp12cError.TvmNoConverge`.
     */
    private fun reduceFinancialSolve(state: CalculatorState, event: Event.Financial.Solve): CalculatorState {
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
                Event.Financial.Solve.N   -> computeN(iDec, pv, pmt, fv, isBegin)
                Event.Financial.Solve.I   -> computeI(n, pv, pmt, fv, isBegin)
            }
        } catch (e: TvmSignMismatch) {
            // Combinação de sinais inviável (PV e FV não opostos, ratio ≤ 0 no argumento de ln
            // etc.). É o clássico `Error 5` do manual quando o usuário esquece um CHS.
            return state.copy(pendingError = Hp12cError.TvmInvalidSigns)
        } catch (e: ArithmeticException) {
            // Divisão por zero, overflow na aritmética BCD, ou Newton-Raphson não convergiu —
            // pilha preservada (regra 8).
            return state.copy(pendingError = Hp12cError.TvmNoConverge)
        }

        // Atualiza o registrador resolvido; pilha ganha resultado em X respeitando stackLift; LSTx
        // guarda o X destruído pelo Solve.
        val newFinancial = when (event) {
            Event.Financial.Solve.Fv  -> f.copy(fv  = result)
            Event.Financial.Solve.Pv  -> f.copy(pv  = result)
            Event.Financial.Solve.Pmt -> f.copy(pmt = result)
            Event.Financial.Solve.N   -> f.copy(n   = result)
            Event.Financial.Solve.I   -> f.copy(i   = result)
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
     * `n = ln(ratio) / ln(1+i)` com teto no final, onde
     * `ratio = ((1+iS)·PMT - FV·i) / ((1+iS)·PMT + PV·i)` — algebricamente equivalente a
     * isolar `n` na equação canônica da Seção 3 de `formulas/tvm.md` (derivação reproduzida
     * no Apêndice E do manual e em Moretti Cap. 4 §§12).
     *
     * Casos especiais:
     *
     * - `i = 0` → equação linear: `0 = PV + n·PMT + FV`, logo `n = -(PV+FV)/PMT`. Se `PMT` também
     *   é zero, a equação não tem solução em `n` (só tem solução se `PV = -FV`, e então `n`
     *   é livre): sinal de `Error 5`.
     * - `PMT = 0` → ratio reduz a `-FV·i / (PV·i) = -FV/PV` (o `i` cancela, mesma fórmula que
     *   a linha do manual "Finding the Number of Periods (Simple Case)").
     *
     * A HP sempre **arredonda `n` para cima** (Moretti Ex. 12 p. 32-33 + nota em
     * `test-vectors/tvm-vectors.json` tvm-003). A parte fracionária exata é recuperável via
     * `RCL n f FRAC 30 ×`; fora de escopo para Fase 1.
     *
     * Lança [TvmSignMismatch] se o ratio for ≤ 0 (argumento inválido para `ln`) ou se o
     * denominador for zero.
     */
    private fun computeN(
        i: Hp12cDecimal, pv: Hp12cDecimal, pmt: Hp12cDecimal, fv: Hp12cDecimal, isBegin: Boolean,
    ): Hp12cDecimal {
        if (i.isZero()) {
            if (pmt.isZero()) throw TvmSignMismatch()
            val nRaw = -(pv + fv) / pmt
            if (nRaw.compareTo(Hp12cDecimal.ZERO) <= 0) throw TvmSignMismatch()
            return nRaw.ceil()
        }
        val begAdj = if (isBegin) Hp12cDecimal.ONE + i else Hp12cDecimal.ONE
        val num = begAdj * pmt - fv * i
        val den = begAdj * pmt + pv * i
        if (den.isZero()) throw TvmSignMismatch()
        val ratio = num / den
        if (ratio.compareTo(Hp12cDecimal.ZERO) <= 0) throw TvmSignMismatch()
        val nRaw = ratio.ln() / (Hp12cDecimal.ONE + i).ln()
        return nRaw.ceil()
    }

    /**
     * Resolve `i` na equação TVM canônica. Produz o resultado em **percentual** (o que a HP
     * guarda em `financial.i`), não em decimal.
     *
     * Dois caminhos:
     *
     * 1. `PMT = 0` → forma fechada exata: `i = (-FV/PV)^(1/n) - 1 = exp(ln(-FV/PV) / n) - 1`.
     *    É o que o manual descreve em "Finding the Periodic Interest Rate (Simple Case)" e é
     *    exato em BCD de 10 dígitos dada a precisão estendida de `ln`/`exp`.
     *
     * 2. `PMT ≠ 0` → Newton-Raphson sobre a equação residual
     *    `f(i) = PV + (1+iS)·PMT·(1-(1+i)^(-n))/i + FV·(1+i)^(-n)`
     *    com derivada por diferença central (`h = 10⁻⁶`). Chute inicial `i₀ = 1%` funciona
     *    para os cenários usuais (o manual também começa em 1% no algoritmo interno da HP).
     *    Tolerância `|f(i)| < 10⁻⁸`, máximo 100 iterações. Se não converge, lança
     *    [ArithmeticException] e o caller mapeia para `Hp12cError.TvmNoConverge`.
     *
     * Nota sobre `begAdj` no caso 2: como `begAdj = (1+i)` em BEGIN, ele depende de `i` e
     * participa da derivada. A diferença central cuida disso sem precisar de derivação analítica.
     */
    private fun computeI(
        n: Int, pv: Hp12cDecimal, pmt: Hp12cDecimal, fv: Hp12cDecimal, isBegin: Boolean,
    ): Hp12cDecimal {
        if (n <= 0) throw TvmSignMismatch()

        // Caminho 1: forma fechada quando PMT = 0.
        if (pmt.isZero()) {
            if (pv.isZero()) throw TvmSignMismatch()
            val ratio = -fv / pv
            if (ratio.compareTo(Hp12cDecimal.ZERO) <= 0) throw TvmSignMismatch()
            val iDec = (ratio.ln() / Hp12cDecimal.of(n)).exp() - Hp12cDecimal.ONE
            return iDec * HUNDRED
        }

        // Caminho 2: Newton-Raphson com derivada por diferença central.
        val tolerance = Hp12cDecimal.of("0.00000001")   // 10⁻⁸
        val h = Hp12cDecimal.of("0.000001")             // 10⁻⁶
        val two = Hp12cDecimal.of(2)
        var iDec = Hp12cDecimal.of("0.01")              // chute inicial 1%

        repeat(100) {
            val fVal = tvmResidual(iDec, n, pv, pmt, fv, isBegin)
            val fAbs = if (fVal.compareTo(Hp12cDecimal.ZERO) < 0) -fVal else fVal
            if (fAbs < tolerance) return iDec * HUNDRED

            val fPlus  = tvmResidual(iDec + h, n, pv, pmt, fv, isBegin)
            val fMinus = tvmResidual(iDec - h, n, pv, pmt, fv, isBegin)
            val df = (fPlus - fMinus) / (h * two)
            if (df.isZero()) throw ArithmeticException("Newton-Raphson: derivada nula")
            iDec -= fVal / df
        }
        throw ArithmeticException("Newton-Raphson não convergiu em 100 iterações")
    }

    /**
     * Resíduo da equação TVM canônica (Seção 3 de `formulas/tvm.md`). Retorna zero exatamente
     * quando `iDec` é a taxa-solução. Usado como `f(i)` do Newton-Raphson em [computeI].
     *
     * Limite degenerado `i = 0`: a equação vira `PV + n·PMT + FV`, forma linear.
     */
    private fun tvmResidual(
        iDec: Hp12cDecimal, n: Int, pv: Hp12cDecimal, pmt: Hp12cDecimal, fv: Hp12cDecimal,
        isBegin: Boolean,
    ): Hp12cDecimal {
        if (iDec.isZero()) {
            return pv + Hp12cDecimal.of(n) * pmt + fv
        }
        val discount = (Hp12cDecimal.ONE + iDec).powInt(-n)
        val annuity = (Hp12cDecimal.ONE - discount) / iDec
        val begAdj = if (isBegin) Hp12cDecimal.ONE + iDec else Hp12cDecimal.ONE
        return pv + begAdj * pmt * annuity + fv * discount
    }

    /**
     * Arredondamento para cima (`ceiling`): inteiros ficam iguais, fracionários sobem ao
     * próximo inteiro **na direção +∞** (para negativos, isso significa truncar para zero).
     *
     * Impl via `toString()` do actual class: o `toPlainString()` do JVM nunca usa notação
     * científica, então `substringBefore('.')` isola a parte inteira. Para números negativos
     * com parte fracionária (ex.: `-3.5 → -3`) o parse da parte inteira já dá `-3`, que é o
     * ceil correto. Para positivos (`3.5 → 4`), somamos 1.
     *
     * **Onde entra:** aplicado ao final de [computeN] porque a HP sempre exibe `n` arredondado
     * para cima (Moretti Ex. 12 + nota em `test-vectors/tvm-vectors.json` tvm-003).
     */
    private fun Hp12cDecimal.ceil(): Hp12cDecimal {
        val s = toString()
        val dotIdx = s.indexOf('.')
        if (dotIdx < 0) return this
        val intPart = s.substring(0, dotIdx)
        val fracPart = s.substring(dotIdx + 1)
        val hasNonZero = fracPart.any { it != '0' }
        if (!hasNonZero) return Hp12cDecimal.of(intPart)
        val intVal = intPart.toLong()
        val result = if (s.startsWith("-")) intVal else intVal + 1
        return Hp12cDecimal.of(result)
    }

    /**
     * Exceção sentinela: sinaliza que a combinação de sinais dos registradores é incompatível
     * com a equação TVM (ratio ≤ 0 em `Solve.N`/`Solve.I`, ou `n ≤ 0`). O caller no
     * [reduceFinancialSolve] mapeia para [Hp12cError.TvmInvalidSigns]. Separada de
     * [ArithmeticException] só para diferenciar "você esqueceu um CHS" (Error 5 imediato) de
     * "o Newton-Raphson divergiu" (também Error 5, mas com semântica de "tentou e falhou").
     */
    private class TvmSignMismatch : ArithmeticException("TVM sign mismatch")

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

    // ───────────────────────────────────────────────────────────────────────────
    //  Transcendental — 1/x, x², √x, LN, e^x, n!, RND, INT, FRAC, y^x
    //  Fonte canônica: formulas/transcendentais.md §3 (unárias) e §4 (y^x binária).
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Reducer das 10 teclas matemáticas/alteração-de-número. Usa `unaryOp` para as unárias
     * "normais" (preenchem LASTx), `binaryOp` para `y^x` e `applyRound` para `RND` (único
     * caso em que LASTx **não** é atualizado — ver §3.7 de `formulas/transcendentais.md`).
     *
     * ### Mapeamento de erros (Apêndice D do manual, p. 193-195)
     *
     * | Tecla       | Condição                 | Código HP |
     * |-------------|--------------------------|-----------|
     * | `1/x`       | `x = 0`                  | Error 0   |
     * | `√x`        | `x < 0`                  | Error 0   |
     * | `LN`        | `x ≤ 0`                  | Error 0   |
     * | `e^x`       | overflow `x > 230.2585`  | Error 1 * |
     * | `y^x`       | `y=0 ∧ x≤0` ou `y<0 ∧ x∉ℤ`| Error 0  |
     * | `n!`        | `x < 0` ou `x ∉ ℤ`       | Error 5   |
     * | `n!`        | `n! > 10⁹⁹` (n ≥ 70)     | Error 1 * |
     * | `x²`, `INT`, `FRAC`, `RND` | (sem erro possível) | — |
     *
     * (*) Casos de overflow (e^x e n! grande) ainda não têm vetor no JSON — declarados como
     *     `coverage_gaps_known` no meta. O mapeamento conservador é `StoreOverflow` (Error 1)
     *     para qualquer `ArithmeticException` não-catalogada desses dois callers; na prática o
     *     controle de domínio de `factorial()` abaixo já prende o caso comum.
     *
     * A pilha é preservada tal como estava no início da operação em caso de erro (regra 8 de
     * `referencias/stack-behavior.md` — mesma política de `reduceArith`).
     */
    private fun reduceTranscendental(state: CalculatorState, event: Event.Transcendental): CalculatorState {
        return try {
            when (event) {
                Event.Transcendental.Reciprocal ->
                    state.copy(stack = state.stack.unaryOp { x -> Hp12cDecimal.ONE / x })

                Event.Transcendental.Square ->
                    state.copy(stack = state.stack.unaryOp { x -> x * x })

                Event.Transcendental.Sqrt ->
                    state.copy(stack = state.stack.unaryOp { x -> x.sqrt() })

                Event.Transcendental.Ln ->
                    state.copy(stack = state.stack.unaryOp { x -> x.ln() })

                Event.Transcendental.Exp ->
                    state.copy(stack = state.stack.unaryOp { x -> x.exp() })

                Event.Transcendental.Factorial ->
                    state.copy(stack = state.stack.unaryOp { x -> factorial(x) })

                Event.Transcendental.Integer ->
                    state.copy(stack = state.stack.unaryOp { x -> x.truncateTowardsZero() })

                Event.Transcendental.Fractional ->
                    state.copy(stack = state.stack.unaryOp { x -> x - x.truncateTowardsZero() })

                Event.Transcendental.Round ->
                    applyRound(state)

                Event.Transcendental.Power ->
                    state.copy(stack = state.stack.binaryOp { y, x -> y.pow(x) })
            }
        } catch (e: FactorialDomainException) {
            // Fatorial `x<0` ou `x` fracionário — idiossincrasia histórica, Error 5 (não 0).
            state.copy(pendingError = Hp12cError.FactorialDomain)
        } catch (e: ArithmeticException) {
            state.copy(pendingError = errorForTranscendental(event))
        }
    }

    /**
     * Mapeamento de `ArithmeticException` genérica → código de erro HP, por tecla. Vale para
     * exceções lançadas pelas primitivas de [Hp12cDecimal] (div/0, ln de não-positivo,
     * pow inválido). Exceção sentinela [FactorialDomainException] é tratada em um `catch`
     * separado acima para não depender de reflexão de mensagem.
     *
     * `Square`, `Integer`, `Fractional`, `Round` estão listadas como "sem erro possível"
     * no manual — se caírem no catch, é bug da engine. Mapeamos defensivamente para
     * `DivisionByZero` (Error 0 genérico) para expor o problema sem travar o processo.
     */
    private fun errorForTranscendental(event: Event.Transcendental): Hp12cError = when (event) {
        Event.Transcendental.Reciprocal -> Hp12cError.DivisionByZero
        Event.Transcendental.Sqrt       -> Hp12cError.SqrtOfNegative
        Event.Transcendental.Ln         -> Hp12cError.LogOfNonPositive
        // Overflow de e^x: sem vetor dedicado na Fase 2 bloco 1 (meta.coverage_gaps_known);
        // StoreOverflow (Error 1) é o mapeamento conservador para a única exceção que a impl
        // atual lança — `exp overflow` quando o argumento estoura ~231.
        Event.Transcendental.Exp        -> Hp12cError.StoreOverflow
        // `Factorial` aqui cobre apenas overflow (>69). Domínio inválido sobe como
        // FactorialDomainException e é tratado em catch dedicado.
        Event.Transcendental.Factorial  -> Hp12cError.StoreOverflow
        Event.Transcendental.Power      -> Hp12cError.InvalidYToX
        // Defesa profunda — estas teclas não deveriam lançar na prática.
        Event.Transcendental.Square,
        Event.Transcendental.Integer,
        Event.Transcendental.Fractional,
        Event.Transcendental.Round      -> Hp12cError.DivisionByZero
    }

    /**
     * Fatorial via produto em `BigDecimal` (com arredondamento HALF_EVEN a 10 dígitos em cada
     * multiplicação — o MC é intrínseco ao `Hp12cDecimal`). Ref: manual Apêndice E p. 205.
     *
     * Validação de domínio:
     *   - Parte fracionária não-zero → [FactorialDomainException] (Error 5).
     *   - `x < 0` → [FactorialDomainException] (Error 5).
     *   - `x = 0` → `1` (`0! = 1`, Apêndice E). Segue Apêndice E mesmo contra a leitura literal
     *     de "x ≤ 0" em Apêndice D — ambiguidade #1 de `formulas/transcendentais.md` §7.
     *   - `x > 69` → [ArithmeticException] "overflow" — mapeado para Error 1 pelo caller
     *     (`70! ≈ 1,20 × 10^100` estoura o visor da HP; Apêndice D p. 194).
     *
     * Loop iterativo com `fold(1, 2..n)` — evita recursão (stack depth) e é O(n) com `n ≤ 69`,
     * ou seja ≤ 69 multiplicações BCD. Trivial.
     */
    private fun factorial(x: Hp12cDecimal): Hp12cDecimal {
        val s = x.toString()
        val dotIdx = s.indexOf('.')
        val fracPart = if (dotIdx >= 0) s.substring(dotIdx + 1) else ""
        if (fracPart.any { it != '0' }) {
            throw FactorialDomainException("n! de não-inteiro")
        }
        val intPart = if (dotIdx >= 0) s.substring(0, dotIdx) else s
        val n = try {
            intPart.toLong()
        } catch (e: NumberFormatException) {
            throw FactorialDomainException("n! fora de range inteiro representável")
        }
        if (n < 0) throw FactorialDomainException("n! de negativo")
        if (n > 69) throw ArithmeticException("n! overflow (n > 69)")
        var result = Hp12cDecimal.ONE
        var k = 2L
        while (k <= n) {
            result = result * Hp12cDecimal.of(k)
            k++
        }
        return result
    }

    /**
     * `RND` — materializa o arredondamento visível no visor dentro do registrador X.
     *
     * **Caso único**: não atualiza `LASTx` (ver `formulas/transcendentais.md` §3.7 e
     * `referencias/stack-behavior.md`: LSTx após RND devolve o valor pré-operação **anterior**,
     * não o pré-RND). Por isso não usamos `Stack.unaryOp` — fazemos a cópia manualmente.
     *
     * **Precisão de arredondamento**:
     *   - FIX n → arredonda X a `n` casas decimais HALF_EVEN via [DisplayFormatter.roundHalfEven].
     *   - SCI n / ENG n → manual manda arredondar a `n+1` algarismos significativos. Esta é
     *     uma [lacuna declarada](test-vectors/transcendentais-vectors.json `coverage_gaps_known`)
     *     da suíte de vetores — ambiguidade #4 de `formulas/transcendentais.md` §7. Aqui usamos
     *     `n+1` via `toPlainString` + `roundHalfEven` sobre a representação normalizada, que é
     *     fidedigno para valores típicos mas pode divergir na última casa para mantissas com
     *     `n ≥ 6` em magnitudes extremas — caso que entrará na Fase 2 com vetores SCI/ENG.
     */
    private fun applyRound(state: CalculatorState): CalculatorState {
        val x = state.stack.x
        val newX: Hp12cDecimal = when (val fmt = state.display) {
            is DisplayFormat.Fix -> {
                val roundedStr = DisplayFormatter.roundHalfEven(x.toString(), fmt.places)
                Hp12cDecimal.of(roundedStr)
            }
            is DisplayFormat.Sci, is DisplayFormat.Eng -> {
                // TODO(fase 2): vetores SCI/ENG RND. Por ora tratamos como se fosse FIX n+1,
                // o que é um best-effort — o behavior correto é arredondar a mantissa em `n+1`
                // algarismos significativos, independente da posição do ponto.
                val places = when (fmt) {
                    is DisplayFormat.Sci -> fmt.places
                    is DisplayFormat.Eng -> fmt.places
                    else -> 0
                }
                val roundedStr = DisplayFormatter.roundHalfEven(x.toString(), places)
                Hp12cDecimal.of(roundedStr)
            }
        }
        // LASTx permanece — comportamento excepcional de RND (manual p. 86).
        val newStack = state.stack.copy(
            x = newX,
            stackLiftEnabled = true,
            isEntering = false,
        )
        return state.copy(stack = newStack)
    }

    /**
     * Trunca `Hp12cDecimal` em direção a zero, preservando sinal: `3.88 → 3`, `-3.88 → -3`,
     * `0.5 → 0`, `-0.5 → 0`. Usado pelas teclas `INT` e `FRAC` (esta última como
     * `x - truncateTowardsZero(x)`), conforme §§3.8-3.9 de `formulas/transcendentais.md`.
     *
     * Impl via `toString()` + `Hp12cDecimal.of(parteInt)`: `toPlainString` do JVM nunca
     * usa notação científica para valores nesta faixa (a HP representa no máximo
     * `9,999999999 × 10^99`, inclusive inteiros gigantes entram como `0.E+100` bem fora da
     * faixa de truncamento prático). Para valores extremos fora do domínio prático da HP, o
     * comportamento pode degradar — não testamos.
     */
    private fun Hp12cDecimal.truncateTowardsZero(): Hp12cDecimal {
        val s = toString()
        val dotIdx = s.indexOf('.')
        if (dotIdx < 0) return this
        val intStr = s.substring(0, dotIdx).ifEmpty { "0" }
        // Parse "-0" ou "0" ou "-3" ou "3" — Hp12cDecimal.of lida via BigDecimal, que
        // normaliza "-0" para zero. Preserva sinal em negativos.
        return Hp12cDecimal.of(intStr)
    }

    /**
     * Sentinela interna para `n!` com domínio inválido (x<0 ou x não-inteiro). Separada de
     * [ArithmeticException] só para que o caller diferencie "Error 5" (domínio de fatorial,
     * idiossincrasia histórica do manual) de "Error 1" (overflow, que sobe como
     * ArithmeticException genérica). A mensagem é consumida pelo Kotlin runtime; o importante
     * é o tipo.
     */
    private class FactorialDomainException(message: String) : ArithmeticException(message)

    // ───────────────────────────────────────────────────────────────────────────
    //  Statistics — Σ+, Σ-, g x̄, g s, g x̄w, g ŷ,r, g x̂,r, f CLEAR Σ
    //  Fonte canônica: formulas/estatistica.md + test-vectors/estatistica-vectors.json
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Reducer das 8 teclas estatísticas. Lê/grava R1..R6 diretamente em `MemoryRegisters`
     * (compartilhamento físico — §1.2 de `formulas/estatistica.md`).
     *
     * Política de erros (todos Error 2 — Apêndice D p. 194):
     *
     * | Tecla        | Condição                                         |
     * |--------------|--------------------------------------------------|
     * | Mean/StdDev/YHatR/XHatR | `n = 0`                             |
     * | StdDev       | `n = 1`; ou discriminante < 0                   |
     * | YHatR        | `nΣx² − (Σx)² = 0`                             |
     * | XHatR        | `nΣy² − (Σy)² = 0`                             |
     * | WeightedMean | `Σx = 0`                                        |
     * | SwapXY após ŷ,r/x̂,r | `[nΣx²−(Σx)²]·[nΣy²−(Σy)²] ≤ 0`    |
     *
     * Pilha preservada em caso de erro (regra 8 de `stack-behavior.md`).
     */
    private fun reduceStatistics(state: CalculatorState, event: Event.Statistics): CalculatorState {
        return when (event) {
            Event.Statistics.ClearSigma -> {
                // Apêndice A p. 181: zera R1..R6 E a pilha inteira. LASTx preservado.
                val clearedMem = state.memory
                    .store(RegisterId.R1, Hp12cDecimal.ZERO)
                    .store(RegisterId.R2, Hp12cDecimal.ZERO)
                    .store(RegisterId.R3, Hp12cDecimal.ZERO)
                    .store(RegisterId.R4, Hp12cDecimal.ZERO)
                    .store(RegisterId.R5, Hp12cDecimal.ZERO)
                    .store(RegisterId.R6, Hp12cDecimal.ZERO)
                val clearedStack = state.stack.copy(
                    x = Hp12cDecimal.ZERO, y = Hp12cDecimal.ZERO,
                    z = Hp12cDecimal.ZERO, t = Hp12cDecimal.ZERO,
                    stackLiftEnabled = true, isEntering = false,
                )
                state.copy(stack = clearedStack, memory = clearedMem, statisticsRInvalid = false)
            }

            Event.Statistics.SigmaPlus -> {
                // Consome (y=stack.y, x=stack.x), atualiza R1..R6, empurra novo n via binaryOp.
                val x = state.stack.x
                val y = state.stack.y
                val n    = state.memory[RegisterId.R1] + Hp12cDecimal.ONE
                val sumX = state.memory[RegisterId.R2] + x
                val sumX2= state.memory[RegisterId.R3] + x * x
                val sumY = state.memory[RegisterId.R4] + y
                val sumY2= state.memory[RegisterId.R5] + y * y
                val sumXY= state.memory[RegisterId.R6] + x * y
                val newMem = state.memory
                    .store(RegisterId.R1, n)
                    .store(RegisterId.R2, sumX)
                    .store(RegisterId.R3, sumX2)
                    .store(RegisterId.R4, sumY)
                    .store(RegisterId.R5, sumY2)
                    .store(RegisterId.R6, sumXY)
                // binaryOp: Z→Y, T sticky, lastX=x antigo (o x-variável acumulado).
                val newStack = state.stack.binaryOp { _, _ -> n }
                state.copy(stack = newStack, memory = newMem, statisticsRInvalid = false)
            }

            Event.Statistics.SigmaMinus -> {
                val x = state.stack.x
                val y = state.stack.y
                val n    = state.memory[RegisterId.R1] - Hp12cDecimal.ONE
                val sumX = state.memory[RegisterId.R2] - x
                val sumX2= state.memory[RegisterId.R3] - x * x
                val sumY = state.memory[RegisterId.R4] - y
                val sumY2= state.memory[RegisterId.R5] - y * y
                val sumXY= state.memory[RegisterId.R6] - x * y
                val newMem = state.memory
                    .store(RegisterId.R1, n)
                    .store(RegisterId.R2, sumX)
                    .store(RegisterId.R3, sumX2)
                    .store(RegisterId.R4, sumY)
                    .store(RegisterId.R5, sumY2)
                    .store(RegisterId.R6, sumXY)
                val newStack = state.stack.binaryOp { _, _ -> n }
                state.copy(stack = newStack, memory = newMem, statisticsRInvalid = false)
            }

            Event.Statistics.Mean -> {
                val n = state.memory[RegisterId.R1]
                if (n.isZero()) return state.copy(pendingError = Hp12cError.StatisticsUnderflow)
                val meanX = state.memory[RegisterId.R2] / n
                val meanY = state.memory[RegisterId.R4] / n
                val newStack = state.stack.dualOutputOp { _ -> Pair(meanX, meanY) }
                state.copy(stack = newStack, statisticsRInvalid = false)
            }

            Event.Statistics.StdDev -> {
                val n = state.memory[RegisterId.R1]
                if (n.isZero()) return state.copy(pendingError = Hp12cError.StatisticsUnderflow)
                val nMinusOne = n - Hp12cDecimal.ONE
                if (nMinusOne.isZero()) return state.copy(pendingError = Hp12cError.StatisticsUnderflow)
                val sumX  = state.memory[RegisterId.R2]
                val sumX2 = state.memory[RegisterId.R3]
                val sumY  = state.memory[RegisterId.R4]
                val sumY2 = state.memory[RegisterId.R5]
                // sₓ² = (n·Σx² − (Σx)²) / (n·(n−1))
                val discX = n * sumX2 - sumX * sumX
                val discY = n * sumY2 - sumY * sumY
                if (discX.compareTo(Hp12cDecimal.ZERO) < 0 || discY.compareTo(Hp12cDecimal.ZERO) < 0)
                    return state.copy(pendingError = Hp12cError.StatisticsUnderflow)
                val denom = n * nMinusOne
                val sx = (discX / denom).sqrt()
                val sy = (discY / denom).sqrt()
                val newStack = state.stack.dualOutputOp { _ -> Pair(sx, sy) }
                state.copy(stack = newStack, statisticsRInvalid = false)
            }

            Event.Statistics.WeightedMean -> {
                // x̄w = R6 / R2 = Σxy / Σx — §3.2 de formulas/estatistica.md
                val sumX = state.memory[RegisterId.R2]
                if (sumX.isZero()) return state.copy(pendingError = Hp12cError.StatisticsUnderflow)
                val xw = state.memory[RegisterId.R6] / sumX
                val xOld = state.stack.x
                val newStack = state.stack.copy(
                    x = xw, lastX = xOld,
                    stackLiftEnabled = true, isEntering = false,
                )
                state.copy(stack = newStack, statisticsRInvalid = false)
            }

            Event.Statistics.YHatR -> {
                // ŷ = A + B·x_new  onde B e A são da regressão y=A+Bx (x=R2, y=R4)
                val n = state.memory[RegisterId.R1]
                if (n.isZero()) return state.copy(pendingError = Hp12cError.StatisticsUnderflow)
                val sumX  = state.memory[RegisterId.R2]
                val sumX2 = state.memory[RegisterId.R3]
                val discX = n * sumX2 - sumX * sumX
                if (discX.isZero()) return state.copy(pendingError = Hp12cError.StatisticsCollinear)
                val sumY  = state.memory[RegisterId.R4]
                val sumY2 = state.memory[RegisterId.R5]
                val sumXY = state.memory[RegisterId.R6]
                val xNew = state.stack.x
                val (yhat, r, rInvalid) = computeRegression(n, sumX, sumX2, sumY, sumY2, sumXY, xNew, isYHat = true)
                val newStack = state.stack.dualOutputOp { _ -> Pair(yhat, r) }
                state.copy(stack = newStack, statisticsRInvalid = rInvalid)
            }

            Event.Statistics.XHatR -> {
                val n = state.memory[RegisterId.R1]
                if (n.isZero()) return state.copy(pendingError = Hp12cError.StatisticsUnderflow)
                val sumY  = state.memory[RegisterId.R4]
                val sumY2 = state.memory[RegisterId.R5]
                val discY = n * sumY2 - sumY * sumY
                if (discY.isZero()) return state.copy(pendingError = Hp12cError.StatisticsCollinear)
                val sumX  = state.memory[RegisterId.R2]
                val sumX2 = state.memory[RegisterId.R3]
                val sumXY = state.memory[RegisterId.R6]
                val yNew = state.stack.x
                val (xhat, r, rInvalid) = computeRegression(n, sumX, sumX2, sumY, sumY2, sumXY, yNew, isYHat = false)
                val newStack = state.stack.dualOutputOp { _ -> Pair(xhat, r) }
                state.copy(stack = newStack, statisticsRInvalid = rInvalid)
            }
        }
    }

    /**
     * Calcula coeficientes da regressão linear e produz a estimativa pedida + correlação r.
     *
     * Fórmulas (Apêndice E p. 205):
     * ```
     * B = (nΣxy − ΣxΣy) / (nΣx² − (Σx)²)
     * A = ȳ − B·x̄
     * ŷ(x_new) = A + B·x_new          [isYHat=true]
     * x̂(y_new) = (y_new − A) / B      [isYHat=false]
     * r = (nΣxy − ΣxΣy) / √[(nΣx²−(Σx)²)·(nΣy²−(Σy)²)]
     * ```
     *
     * Retorna `Triple(estimativa, r, rInvalid)` onde `rInvalid=true` significa que o
     * denominador de `r` é ≤ 0 (§8.6 de `formulas/estatistica.md`) — nesse caso `r` é
     * zero (placeholder) e o flag é propagado para `state.statisticsRInvalid`.
     *
     * Pré-condição: o caller já verificou que o discriminante relevante (discX para ŷ,r;
     * discY para x̂,r) é não-zero — logo B é sempre calculável aqui.
     */
    private fun computeRegression(
        n: Hp12cDecimal,
        sumX: Hp12cDecimal, sumX2: Hp12cDecimal,
        sumY: Hp12cDecimal, sumY2: Hp12cDecimal,
        sumXY: Hp12cDecimal,
        input: Hp12cDecimal,
        isYHat: Boolean,
    ): Triple<Hp12cDecimal, Hp12cDecimal, Boolean> {
        val discX = n * sumX2 - sumX * sumX       // nΣx² − (Σx)²
        val discY = n * sumY2 - sumY * sumY       // nΣy² − (Σy)²
        val cross = n * sumXY - sumX * sumY       // nΣxy − ΣxΣy

        val b = cross / discX                     // slope
        val meanX = sumX / n
        val meanY = sumY / n
        val a = meanY - b * meanX                 // intercept

        val estimate = if (isYHat) {
            a + b * input                         // ŷ = A + Bx
        } else {
            (input - a) / b                       // x̂ = (y − A) / B
        }

        // r = cross / √(discX · discY)
        val rDenomSq = discX * discY
        val rInvalid = rDenomSq.compareTo(Hp12cDecimal.ZERO) <= 0
        val r = if (rInvalid) Hp12cDecimal.ZERO else cross / rDenomSq.sqrt()
        return Triple(estimate, r, rInvalid)
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Percent — %, %T, Δ%
    //  Fonte canônica: formulas/transcendentais.md §2 + Apêndice E do manual (p. 197).
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Reducer das 3 teclas de percentagem. Duas famílias distintas:
     *
     * - **Retêm Y** (`%` e `%T`) — usam [Stack.percentOp], que preserva Y no visor (`300 ENTER
     *   14 %` deixa `42` em X mas `300` continua em Y, permitindo `300 ENTER 14 % −` = 258).
     *   Fórmulas: `% = y·x/100`, `%T = 100·x/y`. Ver §2.1 e §2.3 de
     *   `formulas/transcendentais.md`.
     *
     * - **Desce a pilha** (`Δ%`) — binária clássica via [Stack.binaryOp]. Fórmula:
     *   `Δ% = 100·(x−y)/y`. Ver §2.2.
     *
     * **Erros**: divisão por zero em `%T` e `Δ%` (quando `y=0`) → `Error 0`. O manual **silencia**
     * esse caso na tabela de `Error 0`; tratamos por paralelismo com `÷` (ambiguidade #2 de
     * `formulas/transcendentais.md` §7). A tecla `%` nunca lança — `y·x/100` é universal.
     *
     * Pilha é preservada no início da operação caso haja erro (regra 8 de
     * `referencias/stack-behavior.md`).
     */
    private fun reducePercent(state: CalculatorState, event: Event.Percent): CalculatorState {
        return try {
            val newStack = when (event) {
                Event.Percent.Of      -> state.stack.percentOp { y, x -> y * x / HUNDRED }
                Event.Percent.OfTotal -> state.stack.percentOp { y, x -> HUNDRED * x / y }
                Event.Percent.Delta   -> state.stack.binaryOp  { y, x -> HUNDRED * (x - y) / y }
            }
            state.copy(stack = newStack)
        } catch (e: ArithmeticException) {
            // Divisão por zero (y=0 em %T ou Δ%) — manual silencia, tratamos como Error 0.
            state.copy(pendingError = Hp12cError.DivisionByZero)
        }
    }
}
