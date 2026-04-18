package com.arcom.hp12c.engine

import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.DisplayFormat
import com.arcom.hp12c.engine.state.NumericSeparator
import com.arcom.hp12c.engine.state.TvmMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Primeira rodada da suíte TVM — consome o vetor `tvm-001` do arquivo canônico
 * `.claude/skills/hp12c-simulator/test-vectors/tvm-vectors.json` (cópia em
 * `commonTest/resources/test-vectors/tvm-vectors.json`).
 *
 * **Status atual (Fase 0): VERMELHO esperado.** `DefaultEngine.reduce` ainda é `TODO()` —
 * este teste lança `NotImplementedError` e é isso que queremos. É o nosso marco: Fase 1
 * começa aqui, peça por peça, até o teste ficar verde. Depois os outros 17 vetores entram
 * num loop (após `expect/actual fun readTestResource(...)` ser implementado).
 *
 * Vetor de referência (inlinado):
 * ```
 * tvm-001 — moretti, Cap. 4, Ex. 10, p. 30
 * Montante de R$ 5.000 por 5 meses a 4% a.m.
 * inputs:    n=5, i=4, PV=-5000, PMT=0, FV=0, mode=END
 * solve_for: FV
 * expected:  "6083.26"  (FIX 2)
 * ```
 */
class TvmVectorsTest {

    private val engine: CalculatorEngine = CalculatorEngine.Default

    @Test
    fun tvm_001_montante_simples_5000_em_5_meses_a_4pc_ao_mes_rende_6083_26() {
        val initial: CalculatorState = CalculatorEngine.InitialState.copy(
            display   = DisplayFormat.Fix(2),
            financial = CalculatorEngine.InitialState.financial.copy(mode = TvmMode.END),
        )

        val events: List<Event> = listOf(
            // Formato e modo (explícitos apesar de baterem com o default)
            Event.Display.Fix(2),
            Event.Financial.SetEndMode,

            // n = 5
            Event.Entry.Digit(5),
            Event.Financial.Store.N,

            // i = 4
            Event.Entry.Digit(4),
            Event.Financial.Store.I,

            // PV = -5000  (Moretti digita `5000 CHS PV`)
            Event.Entry.Digit(5),
            Event.Entry.Digit(0),
            Event.Entry.Digit(0),
            Event.Entry.Digit(0),
            Event.Entry.ChangeSign,
            Event.Financial.Store.Pv,

            // PMT = 0 (explícito)
            Event.Entry.Digit(0),
            Event.Financial.Store.Pmt,

            // Resolver FV
            Event.Financial.Solve.Fv,
        )

        val finalState: CalculatorState = engine.reduce(initial, events)
        val visor: String = engine.formatDisplay(finalState, NumericSeparator.PERIOD_COMMA)

        assertEquals(
            expected = "6083.26",
            actual   = visor,
            message  = "tvm-001 (Moretti Cap. 4 Ex. 10): FV esperado \"6083.26\", veio \"$visor\"",
        )
    }
}
