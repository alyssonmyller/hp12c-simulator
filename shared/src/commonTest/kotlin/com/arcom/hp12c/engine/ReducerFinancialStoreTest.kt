package com.arcom.hp12c.engine

import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.math.Hp12cDecimal
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.TvmMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes do [DefaultEngine.reduce] para a família `Event.Financial` **parte comitada no
 * passo 4** — Store.{N, I, PV, PMT, FV}, SetBeginMode, SetEndMode, ClearFinancial.
 *
 * Os "Solves" (5 variantes de cálculo a partir dos outros 4 registradores) e o
 * `ToggleCompoundFractionFlag` ainda são `TODO` — eles são cobertos pelo passo 5 junto com
 * os vetores TVM.
 *
 * ### O que estamos validando aqui
 *
 * 1. **Regra 7 para Store financeiro.** STO em `n/i/PV/PMT/FV` preserva pilha igualzinho ao
 *    STO em registrador de usuário: X, Y, Z, T, LSTx e stackLift intactos.
 * 2. **Unidade de `i`.** O manual e a calculadora física guardam `i` em percentual (digita-se
 *    `4`, não `0.04`). A conversão para decimal só acontece dentro das fórmulas TVM.
 * 3. **Modos são cosméticos.** `g BEG` e `g END` alternam a flag sem comitar o buffer de
 *    digitação — igual `FIX`/`SCI`/`ENG`.
 * 4. **ClearFinancial.** `f CLEAR FIN` zera só os 5 registradores numéricos; preserva
 *    `mode`, pilha e memórias de usuário (Apêndice A do manual — "Clearing Operations").
 * 5. **Sequência canônica do manual, p.48** (Capítulo 2, "Simple Financial Calculations").
 */
class ReducerFinancialStoreTest {

    private val engine = CalculatorEngine.Default
    private val initial = CalculatorEngine.InitialState

    private fun run(vararg events: Event): CalculatorState =
        engine.reduce(initial, events.toList())

    private fun run(state: CalculatorState, vararg events: Event): CalculatorState =
        engine.reduce(state, events.toList())

    private fun number(s: String): List<Event> = buildList {
        var s2 = s
        val negate = s2.startsWith("-").also { if (it) s2 = s2.substring(1) }
        for (ch in s2) {
            add(
                when (ch) {
                    '.'       -> Event.Entry.DecimalPoint
                    in '0'..'9' -> Event.Entry.Digit(ch.digitToInt())
                    else -> error("digito inválido em number(\"$s\"): '$ch'")
                },
            )
        }
        if (negate) add(Event.Entry.ChangeSign)
    }

    private fun d(n: Int)    = Hp12cDecimal.of(n)
    private fun d(s: String) = Hp12cDecimal.of(s)

    // ─── 1. Store em cada registrador: comita X e preserva pilha ──────────────

    @Test fun store_n_copia_x_para_registrador_n() {
        val s = run(*number("12").toTypedArray(), Event.Financial.Store.N)
        assertEquals(d(12), s.financial.n)
        assertNull(s.financial.i,  "outros registradores intactos")
        assertNull(s.financial.pv)
        assertNull(s.financial.pmt)
        assertNull(s.financial.fv)
    }

    @Test fun store_i_guarda_taxa_em_percentual_nao_em_decimal() {
        // O usuário digita `4` para "4% ao período"; a HP física guarda 4.00, não 0.04.
        // A conversão p/ decimal é interna às fórmulas TVM (ver formulas/tvm.md Seção 3).
        val s = run(*number("4").toTypedArray(), Event.Financial.Store.I)
        assertEquals(d(4), s.financial.i, "i guardado exatamente como digitado")
    }

    @Test fun store_pv_aceita_valor_negativo() {
        // Saída de caixa: `5000 CHS PV`. A convenção de sinais da HP trata entradas e saídas
        // como sinais opostos; o sinal digitado é preservado literalmente no registrador.
        val s = run(*number("-5000").toTypedArray(), Event.Financial.Store.Pv)
        assertEquals(d(-5000), s.financial.pv)
    }

    @Test fun store_pmt_e_fv_copiam_x_para_seus_registradores() {
        val comPmt = run(*number("100").toTypedArray(), Event.Financial.Store.Pmt)
        assertEquals(d(100), comPmt.financial.pmt)

        val comFv = run(*number("6083.26").toTypedArray(), Event.Financial.Store.Fv)
        assertEquals(d("6083.26"), comFv.financial.fv)
    }

    @Test fun store_n_preserva_pilha_REGRA_7() {
        // Regra 7 de stack-behavior.md Seção 5 aplicada a registradores financeiros: STO,
        // em qualquer variante, não toca os registradores numéricos da pilha (X/Y/Z/T/lastX
        // e flags stackLiftEnabled/isEntering). Apenas canStoreToTvm muda para false —
        // intencionalmente, para que o próximo toque em n/i/PV/PMT/FV sem nova entrada resolva.
        val antes = run(
            *number("1").toTypedArray(), Event.StackOp.Enter,
            *number("2").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.StackOp.Enter,
        )
        val depois = engine.reduce(antes, Event.Financial.Store.N)
        // canStoreToTvm é zerado pelo Store (comportamento correto); os demais campos da pilha
        // permanecem idênticos.
        assertEquals(antes.stack.copy(canStoreToTvm = false), depois.stack,
            "Store financeiro não toca pilha (regra 7), só canStoreToTvm é zerado")
        assertEquals(d(4), depois.financial.n, "N recebeu o X atual")
    }

    @Test fun store_i_preserva_pilha_REGRA_7() {
        val antes = run(
            *number("10").toTypedArray(), Event.StackOp.Enter,
            *number("20").toTypedArray(), Event.StackOp.Enter,
            *number("30").toTypedArray(), Event.StackOp.Enter,
            *number("40").toTypedArray(), Event.StackOp.Enter,
        )
        val depois = engine.reduce(antes, Event.Financial.Store.I)
        assertEquals(antes.stack.copy(canStoreToTvm = false), depois.stack)
        assertEquals(d(40), depois.financial.i)
    }

    @Test fun store_pv_preserva_pilha_REGRA_7() {
        val antes = run(
            *number("1").toTypedArray(), Event.StackOp.Enter,
            *number("2").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("-5000").toTypedArray(), Event.StackOp.Enter,
        )
        val depois = engine.reduce(antes, Event.Financial.Store.Pv)
        assertEquals(antes.stack.copy(canStoreToTvm = false), depois.stack)
        assertEquals(d(-5000), depois.financial.pv)
    }

    @Test fun store_durante_entrada_comita_o_buffer_antes_de_copiar() {
        // Sem ENTER final: o número está só no buffer. STO precisa comitá-lo primeiro
        // para capturar o valor correto (não o seed ZERO que mora em `stack.x`).
        val s = run(*number("7").toTypedArray(), Event.Financial.Store.Fv)
        assertEquals(d(7), s.financial.fv)
        assertNull(s.entryBuffer, "Store comitou o buffer")
        assertFalse(s.stack.isEntering)
        assertEquals(d(7), s.stack.x, "commit escreveu o valor em X")
    }

    // ─── 2. SetBegin/EndMode — cosméticos (não comitam buffer) ────────────────

    @Test fun set_begin_mode_altera_flag_e_nao_comita_buffer() {
        // Estamos em plena digitação ("123" sem ENTER). BEG é cosmético igual FIX/SCI/ENG:
        // não deve tocar o buffer. Essa é a diferença que separa "família Display/mode" de
        // "família Store/Arith" — nenhuma operação numérica acontece aqui, apenas metadado.
        val s = run(*number("123").toTypedArray(), Event.Financial.SetBeginMode)
        assertEquals(TvmMode.BEGIN, s.financial.mode)
        assertEquals("123", s.entryBuffer, "buffer preservado")
        assertTrue(s.stack.isEntering, "ainda está digitando")
    }

    @Test fun set_end_mode_altera_flag_e_nao_comita_buffer() {
        val start = run(Event.Financial.SetBeginMode) // vai para BEGIN antes, pra o teste ter sentido
        val s = run(start, *number("99").toTypedArray(), Event.Financial.SetEndMode)
        assertEquals(TvmMode.END, s.financial.mode)
        assertEquals("99", s.entryBuffer)
        assertTrue(s.stack.isEntering)
    }

    @Test fun set_mode_preserva_registradores_e_pilha() {
        // Mudar o modo com TVM já preenchido é legítimo (e muda o próximo Solve). A mudança
        // não pode zerar ou perturbar os valores já armazenados.
        val s0 = run(
            *number("5").toTypedArray(),     Event.Financial.Store.N,
            *number("4").toTypedArray(),     Event.Financial.Store.I,
            *number("-5000").toTypedArray(), Event.Financial.Store.Pv,
            *number("0").toTypedArray(),     Event.Financial.Store.Pmt,
            *number("0").toTypedArray(),     Event.Financial.Store.Fv,
        )
        val s1 = run(s0, Event.Financial.SetBeginMode)
        assertEquals(TvmMode.BEGIN, s1.financial.mode)
        assertEquals(s0.financial.n,   s1.financial.n)
        assertEquals(s0.financial.i,   s1.financial.i)
        assertEquals(s0.financial.pv,  s1.financial.pv)
        assertEquals(s0.financial.pmt, s1.financial.pmt)
        assertEquals(s0.financial.fv,  s1.financial.fv)
        assertEquals(s0.stack, s1.stack)
    }

    // ─── 3. ClearFinancial ───────────────────────────────────────────────────

    @Test fun clear_financial_zera_os_cinco_registradores() {
        val cheio = run(
            *number("5").toTypedArray(),     Event.Financial.Store.N,
            *number("4").toTypedArray(),     Event.Financial.Store.I,
            *number("-5000").toTypedArray(), Event.Financial.Store.Pv,
            *number("100").toTypedArray(),   Event.Financial.Store.Pmt,
            *number("6000").toTypedArray(),  Event.Financial.Store.Fv,
        )
        assertNotNull(cheio.financial.n)   // sanity
        val limpo = engine.reduce(cheio, Event.Financial.ClearFinancial)
        assertNull(limpo.financial.n)
        assertNull(limpo.financial.i)
        assertNull(limpo.financial.pv)
        assertNull(limpo.financial.pmt)
        assertNull(limpo.financial.fv)
    }

    @Test fun clear_financial_preserva_mode() {
        val begin = run(
            Event.Financial.SetBeginMode,
            *number("12").toTypedArray(), Event.Financial.Store.N,
        )
        val depois = engine.reduce(begin, Event.Financial.ClearFinancial)
        assertEquals(TvmMode.BEGIN, depois.financial.mode, "`f CLEAR FIN` não toca no modo")
    }

    @Test fun clear_financial_preserva_pilha_e_memorias() {
        // CLEAR FIN é local ao bloco TVM: pilha intacta e memórias de usuário intactas.
        // Comitamos ENTER extra para comparar pilhas estáveis.
        val antes = run(
            *number("1").toTypedArray(), Event.StackOp.Enter,
            *number("2").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.StackOp.Enter,
            *number("5").toTypedArray(),     Event.Financial.Store.N,
            *number("4").toTypedArray(),     Event.Financial.Store.I,
            *number("-5000").toTypedArray(), Event.Financial.Store.Pv,
        )
        val depois = engine.reduce(antes, Event.Financial.ClearFinancial)
        assertEquals(antes.stack, depois.stack, "pilha preservada por CLEAR FIN")
        assertEquals(antes.memory, depois.memory, "memórias de usuário preservadas")
    }

    @Test fun clear_financial_durante_entrada_comita_buffer_antes() {
        // Consistência com Store/Arith/Memory: se veio entrada em curso, comita primeiro.
        // Assim o visor pós-CLEAR FIN mostra o número recém-digitado, que é como a HP física
        // se comporta quando a sequência é "42 f CLEAR FIN".
        val s = run(*number("42").toTypedArray(), Event.Financial.ClearFinancial)
        assertEquals(d(42), s.stack.x, "commit escreveu 42 em X")
        assertNull(s.entryBuffer)
        assertFalse(s.stack.isEntering)
        assertNull(s.financial.n, "CLEAR FIN zerou os financeiros")
    }

    // ─── 4. Sequência canônica do manual, Capítulo 2 ──────────────────────────

    @Test fun manual_cap_2_sequencia_canonica_armazena_n_i_pv_pmt_fv() {
        // Sequência típica do manual ao resolver TVM: digita → Store para cada registrador.
        // Validamos que a engine registra tudo fielmente, sem disparar erro e sem perturbar
        // a pilha (o LSTx guarda o último X destruído por operação binária, não por STO —
        // logo LSTx permanece no seu valor inicial).
        val s = run(
            *number("5").toTypedArray(),     Event.Financial.Store.N,
            *number("4").toTypedArray(),     Event.Financial.Store.I,
            *number("-5000").toTypedArray(), Event.Financial.Store.Pv,
            *number("0").toTypedArray(),     Event.Financial.Store.Pmt,
        )
        assertNull(s.pendingError, "nenhum erro nesta sequência")
        assertEquals(d(5),     s.financial.n)
        assertEquals(d(4),     s.financial.i)
        assertEquals(d(-5000), s.financial.pv)
        assertEquals(d(0),     s.financial.pmt)
        assertNull(s.financial.fv, "FV continua null — é o que seria resolvido")
        assertEquals(TvmMode.END, s.financial.mode, "default do manual é END")
    }

    @Test fun sequencia_beg_e_end_alterna_modo_sem_erro() {
        val s = run(
            Event.Financial.SetBeginMode,
            Event.Financial.SetEndMode,
            Event.Financial.SetBeginMode,
        )
        assertEquals(TvmMode.BEGIN, s.financial.mode)
        assertNull(s.pendingError)
    }
}
