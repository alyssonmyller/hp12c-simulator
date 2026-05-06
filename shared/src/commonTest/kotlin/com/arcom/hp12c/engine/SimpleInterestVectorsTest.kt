package com.arcom.hp12c.engine

import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.DisplayFormat
import com.arcom.hp12c.engine.state.NumericSeparator
import com.arcom.hp12c.testing.SimpleInterestVectorJson
import com.arcom.hp12c.testing.SimpleInterestVectorsFile
import com.arcom.hp12c.testing.VectorsJson
import com.arcom.hp12c.testing.readTestResource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **Suíte Juros Simples — 9 vetores ponta-a-ponta.**
 *
 * Cada vetor armazena n, i e PV nos registradores financeiros via [Event.Financial.Store.*],
 * dispara [Event.Financial.SimpleInterest] e verifica `expected_x` (INT) e opcionalmente
 * `expected_y` (PV retido na pilha).
 *
 * Fórmula canônica: `INT = PV × i × n / 36000` (base 360 dias, manual p. 61).
 */
class SimpleInterestVectorsTest {

    private val engine: CalculatorEngine = CalculatorEngine.Default

    @Test fun jsimp_001_manual_90d_8pc_10000()    = runVector("jsimp-001")
    @Test fun jsimp_002_moretti_60d_10p5pc_5000() = runVector("jsimp-002")
    @Test fun jsimp_003_moretti_30d_12pc_20000()  = runVector("jsimp-003")
    @Test fun jsimp_004_moretti_180d_15pc_1500()  = runVector("jsimp-004")
    @Test fun jsimp_005_pv_negativo_45d_18pc()    = runVector("jsimp-005")
    @Test fun jsimp_006_borda_n_zero()            = runVector("jsimp-006")
    @Test fun jsimp_007_borda_i_zero()            = runVector("jsimp-007")
    @Test fun jsimp_008_fix4_73d_11pc_7500()      = runVector("jsimp-008")
    @Test fun jsimp_009_taxa_fracionaria_13p75pc() = runVector("jsimp-009")

    // ─────────────────────────────────────────────────────────────────────
    //  Motor
    // ─────────────────────────────────────────────────────────────────────

    private fun runVector(id: String) {
        val v: SimpleInterestVectorJson = vectors.firstOrNull { it.id == id }
            ?: error("Vetor '$id' não encontrado em juros-simples-vectors.json (total: ${vectors.size}).")

        var state: CalculatorState = CalculatorEngine.InitialState.copy(
            display = parseFormat(v.format),
        )

        // Armazena n, i, PV nos registradores financeiros
        state = storeInput(state, v.inputs.n, Event.Financial.Store.N)
        state = storeInput(state, v.inputs.i, Event.Financial.Store.I)
        state = storeInput(state, v.inputs.PV, Event.Financial.Store.Pv)

        // Dispara f INT
        state = engine.reduce(state, Event.Financial.SimpleInterest)

        assertNull(state.pendingError, "${v.id}: erro inesperado: ${state.pendingError}")

        val renderedX = engine.formatDisplay(state, NumericSeparator.PERIOD_COMMA).replace(",", "")
        assertEquals(
            expected = v.expected_x,
            actual = renderedX,
            message = "${v.id} (${v.source}): esperado X=\"${v.expected_x}\", veio \"$renderedX\"" +
                if (v.notes != null) " | ${v.notes}" else "",
        )

        if (v.expected_y != null) {
            val renderedY = engine.formatDisplay(
                state.copy(stack = state.stack.copy(x = state.stack.y)),
                NumericSeparator.PERIOD_COMMA,
            ).replace(",", "")
            assertEquals(
                expected = v.expected_y,
                actual = renderedY,
                message = "${v.id}: esperado Y=\"${v.expected_y}\", veio \"$renderedY\"",
            )
        }
    }

    private fun storeInput(state: CalculatorState, value: Double, store: Event.Financial.Store): CalculatorState {
        var s = state
        for (ev in numberToEvents(value)) s = engine.reduce(s, ev)
        return engine.reduce(s, store)
    }

    private fun numberToEvents(value: Double): List<Event> {
        val out = mutableListOf<Event>()
        for (ch in abs(value).toNumberString()) {
            when {
                ch == '.'       -> out += Event.Entry.DecimalPoint
                ch in '0'..'9' -> out += Event.Entry.Digit(ch.digitToInt())
                else -> error("Char inesperado: '$ch'")
            }
        }
        if (value < 0.0) out += Event.Entry.ChangeSign
        return out
    }

    private fun Double.toNumberString(): String {
        val l = toLong()
        return if (l.toDouble() == this) l.toString() else toString()
    }

    private fun parseFormat(fmt: String): DisplayFormat {
        val p = fmt.split(' ')
        require(p.size == 2) { "Formato inválido: \"$fmt\"" }
        return when (p[0]) {
            "FIX" -> DisplayFormat.Fix(p[1].toInt())
            "SCI" -> DisplayFormat.Sci(p[1].toInt())
            "ENG" -> DisplayFormat.Eng(p[1].toInt())
            else -> error("Modo desconhecido: \"$fmt\"")
        }
    }

    companion object {
        private val file: SimpleInterestVectorsFile by lazy {
            VectorsJson.decodeFromString(
                SimpleInterestVectorsFile.serializer(),
                readTestResource("test-vectors/juros-simples-vectors.json"),
            )
        }
        private val vectors: List<SimpleInterestVectorJson> get() = file.vectors
    }
}
