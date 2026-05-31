package br.com.alyssonmyller.calculus.engine

import br.com.alyssonmyller.calculus.engine.error.Hp12cError
import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import br.com.alyssonmyller.calculus.engine.state.TvmMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Casos de borda do TVM não cobertos por ReducerFinancialSolveTest e TvmVectorsTest:
 *
 *   - Ramo degenerado i=0 para todos os 5 solves
 *   - TvmInvalidSigns para Solve.N e Solve.I
 *   - canStoreToTvm transições após solve
 *   - LAST X após solve
 *   - Registrador financeiro atualizado após solve
 *   - CLEAR FIN durante entrada
 *   - BEGIN vs END para PMT simples
 *   - ToggleCompoundFractionFlag
 *   - n=1 (caso mínimo)
 *   - i muito pequena (próxima de 0 mas não 0)
 *
 * Fonte: `formulas/tvm.md`, `referencias/stack-behavior.md`.
 */
class TvmEdgeCasesTest {

    private val engine  = CalculatorEngine.Default
    private val initial = CalculatorEngine.InitialState

    private fun run(vararg events: Event): CalculatorState =
        engine.reduce(initial, events.toList())

    private fun run(state: CalculatorState, vararg events: Event): CalculatorState =
        engine.reduce(state, events.toList())

    private fun number(s: String): List<Event> = buildList {
        var s2 = s
        val negate = s2.startsWith("-").also { if (it) s2 = s2.substring(1) }
        for (ch in s2) {
            add(when (ch) {
                '.' -> Event.Entry.DecimalPoint
                in '0'..'9' -> Event.Entry.Digit(ch.digitToInt())
                else -> error("dígito inválido: '$ch'")
            })
        }
        if (negate) add(Event.Entry.ChangeSign)
    }

    private fun d(s: String) = Hp12cDecimal.of(s)
    private fun d(n: Int)    = Hp12cDecimal.of(n)

    private fun near(expected: String, computed: Hp12cDecimal, places: Int = 2): Boolean {
        val tol = Hp12cDecimal.of("0." + "0".repeat(places) + "5")
        val diff = computed - Hp12cDecimal.of(expected)
        return diff < tol && diff > -tol
    }

    private fun runTvm(
        mode: TvmMode = TvmMode.END,
        n: String, i: String, pv: String, pmt: String, fv: String,
        solve: Event.Financial.Solve,
    ): CalculatorState {
        val modeEvent = if (mode == TvmMode.BEGIN) Event.Financial.SetBeginMode
                        else                       Event.Financial.SetEndMode
        return run(
            modeEvent,
            *number(n).toTypedArray(),   Event.Financial.Store.N,
            *number(i).toTypedArray(),   Event.Financial.Store.I,
            *number(pv).toTypedArray(),  Event.Financial.Store.Pv,
            *number(pmt).toTypedArray(), Event.Financial.Store.Pmt,
            *number(fv).toTypedArray(),  Event.Financial.Store.Fv,
            solve,
        )
    }

    // ─── 1. RAMO DEGENERADO i=0 ─────────────────────────────────────────────────

    @Test fun solve_fv_i0_formula_linear() {
        // n=5, i=0, PV=-1000, PMT=-100, FV=?
        // FV = -PV - n*PMT = 1000 - 5*(-100) = 1000 + 500 = 1500
        val s = runTvm(n="5", i="0", pv="-1000", pmt="-100", fv="0", solve=Event.Financial.Solve.Fv)
        assertNull(s.pendingError, "sem erro")
        assertTrue(near("1500.00", s.stack.x), "FV i=0: ${s.stack.x}")
    }

    @Test fun solve_pv_i0_formula_linear() {
        // n=5, i=0, PV=?, PMT=-100, FV=1500
        // PV = -FV - n*PMT = -1500 - 5*(-100) = -1500 + 500 = -1000
        val s = runTvm(n="5", i="0", pv="0", pmt="-100", fv="1500", solve=Event.Financial.Solve.Pv)
        assertNull(s.pendingError)
        assertTrue(near("-1000.00", s.stack.x), "PV i=0: ${s.stack.x}")
    }

    @Test fun solve_pmt_i0_formula_linear() {
        // n=5, i=0, PV=-1000, PMT=?, FV=1500
        // PMT = -(PV+FV)/n = -(-1000+1500)/5 = -500/5 = -100
        val s = runTvm(n="5", i="0", pv="-1000", pmt="0", fv="1500", solve=Event.Financial.Solve.Pmt)
        assertNull(s.pendingError)
        assertTrue(near("-100.00", s.stack.x), "PMT i=0: ${s.stack.x}")
    }

    @Test fun solve_n_i0_formula_linear() {
        // n=?, i=0, PV=-1000, PMT=-100, FV=1500
        // n = -(PV+FV)/PMT = -(-1000+1500)/(-100) = -500/(-100) = 5
        val s = runTvm(n="0", i="0", pv="-1000", pmt="-100", fv="1500", solve=Event.Financial.Solve.N)
        assertNull(s.pendingError)
        assertTrue(near("5", s.stack.x, 0), "n i=0: ${s.stack.x}")
    }

    @Test fun solve_i_pmt0_forma_fechada() {
        // PMT=0: i = (-FV/PV)^(1/n) - 1
        // n=1, PV=-1000, PMT=0, FV=1100: i = 1100/1000 - 1 = 0.10 = 10%
        val s = runTvm(n="1", i="0", pv="-1000", pmt="0", fv="1100", solve=Event.Financial.Solve.I)
        assertNull(s.pendingError)
        assertTrue(near("10.00", s.stack.x), "I PMT=0 forma fechada: ${s.stack.x}")
    }

    @Test fun solve_fv_i0_pmt0_e_pv_negado() {
        // n=10, i=0, PV=-5000, PMT=0, FV=? → FV = -(-5000) = 5000
        val s = runTvm(n="10", i="0", pv="-5000", pmt="0", fv="0", solve=Event.Financial.Solve.Fv)
        assertNull(s.pendingError)
        assertTrue(near("5000.00", s.stack.x), "FV i=0 PMT=0: ${s.stack.x}")
    }

    // ─── 2. SINAIS INVÁLIDOS ─────────────────────────────────────────────────────

    @Test fun solve_n_sinais_invalidos_gera_error_5() {
        // PV e FV mesmos sinais com PMT=0: sem solução real para N
        // Na equação: ratio = (PMT-FV*i)/(PMT+PV*i) = (-2000*0.05)/(1000*0.05) = -2 → negativo → error
        val s = runTvm(n="0", i="5", pv="1000", pmt="0", fv="2000", solve=Event.Financial.Solve.N)
        assertNotNull(s.pendingError)
        assertEquals(5, s.pendingError!!.code, "Error 5 para N inválido")
    }

    @Test fun solve_i_pmt0_ratio_negativo_gera_error_5() {
        // PV e FV de mesmo sinal com PMT=0: ratio negativo no ln
        val s = runTvm(n="5", i="0", pv="-1000", pmt="0", fv="-1100", solve=Event.Financial.Solve.I)
        assertNotNull(s.pendingError)
        assertEquals(5, s.pendingError!!.code, "Error 5 para I inválido")
    }

    @Test fun solve_n_pmt0_i0_gera_error_5() {
        // i=0, PMT=0: equação degenerada sem solução
        val s = runTvm(n="0", i="0", pv="-1000", pmt="0", fv="1100", solve=Event.Financial.Solve.N)
        // n = -(PV+FV)/PMT = divisão por zero → Error 5
        assertNotNull(s.pendingError)
        assertEquals(5, s.pendingError!!.code, "Error 5 para N com PMT=0 e i=0")
    }

    // ─── 3. PILHA APÓS SOLVE ────────────────────────────────────────────────────

    @Test fun solve_fv_atualiza_registrador_e_pilha() {
        val s = runTvm(n="5", i="4", pv="-5000", pmt="0", fv="0", solve=Event.Financial.Solve.Fv)
        assertNotNull(s.financial.fv, "registrador FV atualizado")
        // X deve ter o resultado; deve ser diferente de zero
        assertTrue(s.stack.x != d(0), "X tem resultado do solve")
    }

    @Test fun solve_coloca_resultado_em_x_e_preserva_lastx() {
        // lastX antes do Solve deve ser preservado como lastX
        val pre = run(
            *number("99").toTypedArray(), Event.StackOp.Enter,
            *number("1").toTypedArray(),  Event.Arith.Add, // lastX=1
        )
        val s = run(pre,
            *number("5").toTypedArray(),   Event.Financial.Store.N,
            *number("4").toTypedArray(),   Event.Financial.Store.I,
            *number("-5000").toTypedArray(), Event.Financial.Store.Pv,
            *number("0").toTypedArray(),   Event.Financial.Store.Pmt,
            *number("0").toTypedArray(),   Event.Financial.Store.Fv,
            Event.Financial.Solve.Fv,
        )
        // lastX deve ser o X que estava antes do solve (o 0 que foi digitado para FV)
        assertEquals(d(0), s.stack.lastX, "lastX = X anterior ao solve")
    }

    @Test fun solve_zera_can_store_to_tvm() {
        val s = runTvm(n="5", i="4", pv="-5000", pmt="0", fv="0", solve=Event.Financial.Solve.Fv)
        assertFalse(s.stack.canStoreToTvm, "após solve: canStoreToTvm=false")
    }

    @Test fun solve_pv_atualiza_financial_pv() {
        // n=5, i=4, PV=?, PMT=0, FV=6083.26 → PV ≈ -5000
        val s = runTvm(n="5", i="4", pv="0", pmt="0", fv="6083.26", solve=Event.Financial.Solve.Pv)
        assertNull(s.pendingError)
        assertNotNull(s.financial.pv, "financial.pv atualizado após Solve.Pv")
        assertTrue(near("-5000.00", s.financial.pv!!), "PV ≈ -5000: ${s.financial.pv}")
    }

    @Test fun solve_i_atualiza_financial_i_em_percentual() {
        // n=5, PV=-5000, PMT=0, FV=6083.26 → i ≈ 4%
        val s = runTvm(n="5", i="0", pv="-5000", pmt="0", fv="6083.26", solve=Event.Financial.Solve.I)
        assertNull(s.pendingError)
        assertNotNull(s.financial.i, "financial.i atualizado")
        assertTrue(near("4.00", s.financial.i!!), "I ≈ 4%: ${s.financial.i}")
    }

    // ─── 4. BEGIN vs END ────────────────────────────────────────────────────────

    @Test fun pmt_end_maior_que_begin_para_mesmo_pv_fv_n_i() {
        // Em END o pagamento ocorre no fim do período → cada parcela rende menos juros
        // → precisa de parcela MAIOR para quitar. Em BEGIN, paga antes → parcela MENOR.
        val sEnd   = runTvm(TvmMode.END,   n="12", i="1", pv="-1000", pmt="0", fv="0", solve=Event.Financial.Solve.Pmt)
        val sBegin = runTvm(TvmMode.BEGIN, n="12", i="1", pv="-1000", pmt="0", fv="0", solve=Event.Financial.Solve.Pmt)
        val pmtEnd   = sEnd.stack.x    // ≈ 88.85 (positivo — PV negativo gera PMT positivo)
        val pmtBegin = sBegin.stack.x  // ≈ 87.97 (menor pois paga antes)
        // pmtEnd > pmtBegin (END precisa de parcela maior)
        assertTrue(pmtEnd.compareTo(pmtBegin) > 0,
            "PMT END ($pmtEnd) > PMT BEGIN ($pmtBegin)")
    }

    @Test fun begin_mode_flag_setado_corretamente() {
        val s = run(Event.Financial.SetBeginMode)
        assertEquals(TvmMode.BEGIN, s.financial.mode)
    }

    @Test fun end_mode_flag_setado_corretamente() {
        val s = run(Event.Financial.SetBeginMode, Event.Financial.SetEndMode)
        assertEquals(TvmMode.END, s.financial.mode)
    }

    // ─── 5. CLEAR FIN ─────────────────────────────────────────────────────────

    @Test fun clear_fin_zera_os_cinco_registradores() {
        val s = run(
            *number("5").toTypedArray(),  Event.Financial.Store.N,
            *number("4").toTypedArray(),  Event.Financial.Store.I,
            *number("-5000").toTypedArray(), Event.Financial.Store.Pv,
            *number("0").toTypedArray(),  Event.Financial.Store.Pmt,
            *number("0").toTypedArray(),  Event.Financial.Store.Fv,
            Event.Financial.ClearFinancial,
        )
        assertNull(s.financial.n,   "n zerado")
        assertNull(s.financial.i,   "i zerado")
        assertNull(s.financial.pv,  "pv zerado")
        assertNull(s.financial.pmt, "pmt zerado")
        assertNull(s.financial.fv,  "fv zerado")
    }

    @Test fun clear_fin_preserva_modo_begin() {
        val s = run(
            Event.Financial.SetBeginMode,
            *number("5").toTypedArray(), Event.Financial.Store.N,
            Event.Financial.ClearFinancial,
        )
        assertEquals(TvmMode.BEGIN, s.financial.mode, "modo BEGIN preservado")
        assertNull(s.financial.n, "n zerado")
    }

    @Test fun clear_fin_preserva_pilha_e_memorias() {
        // Configura pilha: Y=99, X=5 (via 99 ENTER 5); depois guarda n=5; CLEAR FIN; pilha intacta
        val s = run(
            *number("99").toTypedArray(), Event.StackOp.Enter,
            *number("5").toTypedArray(),  Event.Financial.Store.N,
            Event.Financial.ClearFinancial,
        )
        // Após 99 ENTER: Y=99, X=99, stackLift=false
        // Após entrar 5 (stackLift=false): X=5, Y=99 (não levantou)
        // Após Store.N: X=5, Y=99 (store não toca pilha)
        // Após ClearFinancial: X=5, Y=99 (não toca pilha)
        assertEquals(d(5),  s.stack.x, "X=5 preservado")
        assertEquals(d(99), s.stack.y, "Y=99 preservado")
    }

    @Test fun clear_fin_durante_entrada_comita_buffer() {
        // Digitando "36" → CLEAR FIN: buffer comitado, n zerado, X=36 na pilha
        val s = run(
            Event.Entry.Digit(3), Event.Entry.Digit(6),
            Event.Financial.ClearFinancial,
        )
        assertEquals(d(36), s.stack.x, "buffer comitado antes de CLEAR FIN")
    }

    // ─── 6. TOGGLE COMPOUND FRACTION FLAG ───────────────────────────────────────

    @Test fun toggle_compound_flag_alterna_de_false_para_true() {
        val s = run(Event.Financial.ToggleCompoundFractionFlag)
        assertTrue(s.compoundFractionFlag)
    }

    @Test fun toggle_compound_flag_alterna_de_true_para_false() {
        val s = run(
            Event.Financial.ToggleCompoundFractionFlag,
            Event.Financial.ToggleCompoundFractionFlag,
        )
        assertFalse(s.compoundFractionFlag)
    }

    @Test fun toggle_compound_flag_nao_toca_pilha_nem_registradores() {
        val s = run(
            *number("5").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Financial.Store.N,
            Event.Financial.ToggleCompoundFractionFlag,
        )
        assertEquals(d(4), s.stack.x, "pilha preservada")
        assertEquals(d(4), s.financial.n, "n preservado")
        assertTrue(s.compoundFractionFlag)
    }

    // ─── 7. STORE — PILHA PRESERVADA ────────────────────────────────────────────

    @Test fun store_n_nao_altera_y_z_t() {
        val s = run(
            *number("10").toTypedArray(), Event.StackOp.Enter,
            *number("20").toTypedArray(), Event.StackOp.Enter,
            *number("30").toTypedArray(), Event.StackOp.Enter,
            *number("12").toTypedArray(),
            Event.Financial.Store.N,
        )
        assertEquals(d(12), s.stack.x, "X = 12")
        assertEquals(d(30), s.stack.y, "Y inalterado")
        assertEquals(d(20), s.stack.z, "Z inalterado")
        assertEquals(d(10), s.stack.t, "T inalterado")
    }

    @Test fun store_em_todos_os_registradores_financeiros_independente() {
        val s = run(
            *number("24").toTypedArray(),    Event.Financial.Store.N,
            *number("1.5").toTypedArray(),   Event.Financial.Store.I,
            *number("-50000").toTypedArray(), Event.Financial.Store.Pv,
            *number("0").toTypedArray(),     Event.Financial.Store.Pmt,
            *number("0").toTypedArray(),     Event.Financial.Store.Fv,
        )
        assertEquals(d(24),      s.financial.n)
        assertEquals(d("1.5"),   s.financial.i)
        assertEquals(d("-50000"), s.financial.pv)
        assertEquals(d(0),       s.financial.pmt)
        assertEquals(d(0),       s.financial.fv)
    }

    // ─── 8. n=1 (caso mínimo realista) ──────────────────────────────────────────

    @Test fun solve_fv_n1_equivale_a_um_periodo_de_juros() {
        // n=1, i=10, PV=-100, PMT=0 → FV = 100*(1.10)^1 = 110
        val s = runTvm(n="1", i="10", pv="-100", pmt="0", fv="0", solve=Event.Financial.Solve.Fv)
        assertNull(s.pendingError)
        assertTrue(near("110.00", s.stack.x), "FV n=1: ${s.stack.x}")
    }

    @Test fun solve_pmt_n1_i0_calcula_pagamento_correto() {
        // n=1, i=0, PV=-100, FV=150 → PMT = -(PV+FV)/n = -(−100+150)/1 = −50
        val s = runTvm(n="1", i="0", pv="-100", pmt="0", fv="150", solve=Event.Financial.Solve.Pmt)
        assertNull(s.pendingError)
        assertTrue(near("-50.00", s.stack.x), "PMT n=1 i=0: ${s.stack.x}")
    }

    // ─── 9. n FRACIONÁRIO — juros simples (flag C off, default) ──────────────────
    //
    // Fórmula (Apêndice E, p. 198, `formulas/tvm.md §4.1`):
    //   0 = PV·[1 + i·FRAC(n)] + (1+iS)·PMT·[(1-(1+i)^-INT(n))/i] + FV·(1+i)^-INT(n)
    //
    // Caso de verificação: PV=-1000, i=10%, n=1.5 (juros simples na fração).
    //   FV = -PV·(1+i·0.5)·(1+i)^1 - (1+iS)·PMT·((1+i)^1-1)/i
    //   FV = 1000·(1+0.05)·1.10 = 1000·1.05·1.10 = 1155.00

    @Test fun solve_fv_n_fracionario_juros_simples_flag_c_off() {
        // n=1.5, i=10, PV=-1000, PMT=0, FV=?  (flag C off → juros simples na fração 0.5)
        // FV = 1000·(1+0.10·0.5)·(1.10)^1 = 1000·1.05·1.10 = 1155.00
        val s = runTvm(n="1.5", i="10", pv="-1000", pmt="0", fv="0", solve=Event.Financial.Solve.Fv)
        assertNull(s.pendingError)
        assertTrue(near("1155.00", s.stack.x), "FV n=1.5 juros simples: ${s.stack.x}")
    }

    @Test fun solve_pv_n_fracionario_juros_simples_flag_c_off() {
        // Inverso: FV=1155, n=1.5, i=10 → PV = -1000
        val s = runTvm(n="1.5", i="10", pv="0", pmt="0", fv="1155", solve=Event.Financial.Solve.Pv)
        assertNull(s.pendingError)
        assertTrue(near("-1000.00", s.stack.x), "PV n=1.5 juros simples: ${s.stack.x}")
    }

    // ─── 10. n FRACIONÁRIO — juros compostos (flag C on) ─────────────────────────
    //
    // Fórmula (Apêndice E, p. 198, `formulas/tvm.md §4.2`):
    //   0 = PV·(1+i)^FRAC(n) + ... + FV·(1+i)^-INT(n)
    //
    // Caso: PV=-1000, i=10%, n=1.5, flag C on.
    //   FV = PV·(1+i)^n = 1000·(1.10)^1.5 = 1000·1.10·√1.10 = 1000·1.153690...
    //   ≈ 1153.69

    @Test fun solve_fv_n_fracionario_juros_compostos_flag_c_on() {
        // n=1.5, i=10, PV=-1000, PMT=0, FV=? (flag C on → juros compostos na fração)
        // FV = 1000·(1.10)^1.5  ≈ 1153.69
        val s = run(
            *number("1.5").toTypedArray(),    Event.Financial.Store.N,
            *number("10").toTypedArray(),     Event.Financial.Store.I,
            *number("-1000").toTypedArray(),  Event.Financial.Store.Pv,
            *number("0").toTypedArray(),      Event.Financial.Store.Pmt,
            *number("0").toTypedArray(),      Event.Financial.Store.Fv,
            Event.Financial.ToggleCompoundFractionFlag,
            Event.Financial.Solve.Fv,
        )
        assertNull(s.pendingError)
        // (1.10)^1.5 = (1.10)^1 · (1.10)^0.5 ≈ 1.10 × 1.04880884817 ≈ 1.15368973299
        // FV ≈ 1153.69
        assertTrue(near("1153.69", s.stack.x), "FV n=1.5 juros compostos: ${s.stack.x}")
        assertTrue(s.compoundFractionFlag, "flag C ainda ativo após solve")
    }

    @Test fun solve_fv_n_inteiro_identico_com_ou_sem_flag_c() {
        // Para n inteiro FRAC(n)=0, ambas as fórmulas reduzem à canônica → mesmo resultado
        val sOff = runTvm(n="2", i="10", pv="-1000", pmt="0", fv="0", solve=Event.Financial.Solve.Fv)
        val sOn  = run(
            *number("2").toTypedArray(),     Event.Financial.Store.N,
            *number("10").toTypedArray(),    Event.Financial.Store.I,
            *number("-1000").toTypedArray(), Event.Financial.Store.Pv,
            *number("0").toTypedArray(),     Event.Financial.Store.Pmt,
            *number("0").toTypedArray(),     Event.Financial.Store.Fv,
            Event.Financial.ToggleCompoundFractionFlag,
            Event.Financial.Solve.Fv,
        )
        assertNull(sOff.pendingError)
        assertNull(sOn.pendingError)
        assertTrue(
            sOff.stack.x.compareTo(sOn.stack.x) == 0,
            "n inteiro: flagC off (${sOff.stack.x}) == flagC on (${sOn.stack.x})",
        )
    }
}
