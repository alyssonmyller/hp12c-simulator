package br.com.alyssonmyller.calculus.engine

import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import br.com.alyssonmyller.calculus.engine.state.NumericSeparator
import br.com.alyssonmyller.calculus.engine.state.RegisterId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes do flag `canStoreToTvm` da pilha — a regra que determina se uma tecla TVM
 * (n, i, PV, PMT, FV) **armazena** X ou **resolve** a variável.
 *
 * ## Comportamento da HP 12C física (manual, Seção 3, p. 39-55)
 *
 * A HP 12C armazena o valor exibido quando há um número "fresco" em X:
 *  - Após digitação de dígitos (`isEntering = true`)
 *  - Após ENTER  (valor confirmado, mas ainda fresco)
 *  - Após aritmética (ex: `9 ENTER 12 ÷ → 0.75`, então `[i]` armazena 0.75)
 *  - Após RCL, LSTx, CLx, R↓, R↑, x⇆y
 *
 * A HP 12C **resolve** quando não há valor fresco:
 *  - Estado inicial (nenhuma entrada ainda)
 *  - Logo após outro Store/Solve TVM (sem nova entrada)
 *
 * ## Bug corrigido (2026-05)
 *
 * Antes deste commit `isEntering` era o critério — mas ENTER zera `isEntering`.
 * Então "10000 ENTER [PV]" chamava Solve.Pv em vez de Store.Pv,
 * retornando lixo dos registradores persistidos em vez de armazenar 10 000.
 * A solução foi adicionar `canStoreToTvm: Boolean` à pilha, setado por ENTER,
 * aritmética, etc., e zerado exclusivamente por TVM Store/Solve.
 */
class TvmDispatchTest {

    private val engine  = CalculatorEngine.Default
    private val initial = CalculatorEngine.InitialState

    private fun run(vararg events: Event): CalculatorState =
        engine.reduce(initial, events.toList())

    private fun run(state: CalculatorState, vararg events: Event): CalculatorState =
        engine.reduce(state, events.toList())

    private fun d(s: String) = Hp12cDecimal.of(s)
    private fun d(n: Int)    = Hp12cDecimal.of(n)

    private fun digits(s: String): List<Event> = buildList {
        var str = s
        val neg = str.startsWith("-").also { if (it) str = str.substring(1) }
        for (ch in str) {
            add(when (ch) {
                '.'         -> Event.Entry.DecimalPoint
                in '0'..'9' -> Event.Entry.Digit(ch.digitToInt())
                else        -> error("char inválido em digits(\"$s\"): '$ch'")
            })
        }
        if (neg) add(Event.Entry.ChangeSign)
    }

    // ─── 1. Estado inicial ────────────────────────────────────────────────────

    @Test fun estado_inicial_canStoreToTvm_false() {
        assertFalse(initial.stack.canStoreToTvm,
            "InitialState: canStoreToTvm=false — primeira tecla TVM deve resolver")
    }

    // ─── 2. Digitação seta canStoreToTvm=true ────────────────────────────────

    @Test fun digitar_numero_seta_canStoreToTvm() {
        val s = run(*digits("10000").toTypedArray())
        assertTrue(s.stack.canStoreToTvm, "Após digitar número: canStoreToTvm deve ser true")
        assertTrue(s.stack.isEntering, "isEntering também é true durante digitação")
    }

    // ─── 3. ENTER preserva canStoreToTvm=true (bug corrigido) ────────────────

    @Test fun enter_mantem_canStoreToTvm_true() {
        // Bug reportado: "10000 ENTER PV" dava -9.00 porque isEntering=false após ENTER
        // levava a UI a chamar Solve.Pv em vez de Store.Pv.
        val s = run(*digits("10000").toTypedArray(), Event.StackOp.Enter)
        assertTrue(s.stack.canStoreToTvm,
            "ENTER não deve zerar canStoreToTvm — o valor 10000 ainda é 'fresco'")
        assertFalse(s.stack.isEntering,
            "ENTER zera isEntering (fim da digitação), mas NÃO canStoreToTvm")
    }

    @Test fun enter_depois_de_digito_permite_store() {
        // "10000 ENTER" → canStoreToTvm=true → a UI deve chamar Store.Pv
        // Aqui simulamos diretamente o Store.Pv para confirmar que o valor é correto.
        val s = run(
            *digits("10000").toTypedArray(),
            Event.StackOp.Enter,
            Event.Financial.Store.Pv,
        )
        assertEquals(d("10000"), s.financial.pv,
            "PV deve ser 10000: ENTER não muda Store→Solve (disp-002)")
    }

    // ─── 4. Aritmética seta canStoreToTvm=true ────────────────────────────────

    @Test fun aritmetica_seta_canStoreToTvm() {
        // "9 ENTER 12 ÷" → resultado 0.75, canStoreToTvm=true, isEntering=false
        val s = run(
            *digits("9").toTypedArray(),
            Event.StackOp.Enter,
            *digits("12").toTypedArray(),
            Event.Arith.Divide,
        )
        assertTrue(s.stack.canStoreToTvm,
            "Após op binária: canStoreToTvm=true (resultado pronto para Store)")
        assertFalse(s.stack.isEntering,
            "isEntering=false após aritmética")
    }

    @Test fun aritmetica_seguida_de_store_i() {
        // "9 ENTER 12 ÷ i" → Store.I = 0.75  (manual, p. 43 — sequência do exemplo)
        val s = run(
            *digits("9").toTypedArray(),
            Event.StackOp.Enter,
            *digits("12").toTypedArray(),
            Event.Arith.Divide,
            Event.Financial.Store.I,
        )
        assertEquals(d("0.75"), s.financial.i,
            "i deve ser 0.75 após '9 ENTER 12 ÷ i' (disp-003)")
    }

    // ─── 5. Store zera canStoreToTvm ─────────────────────────────────────────

    @Test fun store_zera_canStoreToTvm() {
        val s = run(
            *digits("5").toTypedArray(),
            Event.Financial.Store.N,
        )
        assertFalse(s.stack.canStoreToTvm,
            "Após Store.N: canStoreToTvm=false — próximo TVM sem entrada resolve")
    }

    @Test fun solve_zera_canStoreToTvm() {
        // Prepara estado com n=5, i=10, PMT=0, FV=-1000, então Solve.Pv
        val s = run(
            *digits("5").toTypedArray(), Event.Financial.Store.N,
            *digits("10").toTypedArray(), Event.Financial.Store.I,
            *digits("0").toTypedArray(), Event.Financial.Store.Pmt,
            *digits("-1000").toTypedArray(), Event.Financial.Store.Fv,
            Event.Financial.Solve.Pv,
        )
        assertFalse(s.stack.canStoreToTvm,
            "Após Solve.Pv: canStoreToTvm=false — próximo TVM resolve de novo")
    }

    // ─── 6. Valor correto de Solve quando canStoreToTvm=false ────────────────

    @Test fun solve_pv_n5_i10_pmt0_fv_neg1000() {
        // n=5, i=10%, PMT=0, FV=-1000 → PV = 1000/1.1^5 = 620.92
        // canStoreToTvm=false após os stores → Solve.Pv calcula corretamente
        val s = run(
            *digits("5").toTypedArray(), Event.Financial.Store.N,
            *digits("10").toTypedArray(), Event.Financial.Store.I,
            *digits("0").toTypedArray(), Event.Financial.Store.Pmt,
            *digits("-1000").toTypedArray(), Event.Financial.Store.Fv,
            Event.Financial.Solve.Pv,
        )
        val display = engine.formatDisplay(s, NumericSeparator.PERIOD_COMMA)
        assertEquals("620.92", display, "PV = 1000/1.1^5 ≈ 620.92")
    }

    // ─── 7. Sequência completa: n, i, PV, FV → Solve PMT ────────────────────
    //   Vetor confirmado tvm-vectors.json: n=360, i=0.75, PV=50000, FV=0 → PMT=-402.31
    //   (Financiamento residencial, Seção 3 do manual HP 12C Platinum)
    //   O objetivo aqui é validar que o dispatch Store/Solve acontece nas ordens certas
    //   — a exatidão do cálculo TVM é coberta por TvmVectorsTest.

    @Test fun sequencia_completa_store_n_i_pv_fv_solve_pmt() {
        var s = initial

        // 360 n → Store.N, canStoreToTvm=false após
        s = run(s, *digits("360").toTypedArray(), Event.Financial.Store.N)
        assertEquals(d("360"), s.financial.n)
        assertFalse(s.stack.canStoreToTvm, "canStoreToTvm=false após Store.N")

        // 9 ENTER 12 ÷ i  → canStoreToTvm=true (aritmética) → Store.I=0.75
        s = run(s,
            *digits("9").toTypedArray(),
            Event.StackOp.Enter,
            *digits("12").toTypedArray(),
            Event.Arith.Divide,
        )
        assertTrue(s.stack.canStoreToTvm, "canStoreToTvm=true após aritmética '9÷12'")
        s = run(s, Event.Financial.Store.I)
        assertEquals(d("0.75"), s.financial.i)
        assertFalse(s.stack.canStoreToTvm, "canStoreToTvm=false após Store.I")

        // 50000 PV → canStoreToTvm=true durante digitação → Store.Pv
        s = run(s, *digits("50000").toTypedArray())
        assertTrue(s.stack.canStoreToTvm, "canStoreToTvm=true durante digitação de 50000")
        s = run(s, Event.Financial.Store.Pv)
        assertEquals(d("50000"), s.financial.pv)

        // 0 FV → Store.Fv
        s = run(s, *digits("0").toTypedArray(), Event.Financial.Store.Fv)
        assertFalse(s.stack.canStoreToTvm, "canStoreToTvm=false após Store.Fv")

        // PMT → Solve.Pmt (canStoreToTvm=false, nenhuma nova entrada)
        s = run(s, Event.Financial.Solve.Pmt)

        // PMT analítico = -PV·i / [1-(1+i)^-n] = -50000·0.0075/[1-1.0075^-360] = -402.31
        val display = engine.formatDisplay(s, NumericSeparator.PERIOD_COMMA)
        assertEquals("-402.31", display,
            "PMT = -402.31 (vetor tvm-vectors.json n=360 i=0.75 PV=50000 FV=0)")
    }

    // ─── 8. RCL seta canStoreToTvm=true ──────────────────────────────────────

    @Test fun rcl_seta_canStoreToTvm() {
        // Store 120 em R0, depois RCL R0 → canStoreToTvm=true → Store.N=120  (disp-007)
        val s = run(
            *digits("120").toTypedArray(),
            Event.Memory.Store(RegisterId.R0),
            Event.Memory.Recall(RegisterId.R0),
        )
        assertTrue(s.stack.canStoreToTvm,
            "Após RCL: canStoreToTvm=true — valor pode ser armazenado em TVM")

        val s2 = run(s, Event.Financial.Store.N)
        assertEquals(d("120"), s2.financial.n, "Store.N deve usar o valor recuperado via RCL")
    }
}
