package com.arcom.hp12c.engine

import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.DisplayFormat
import com.arcom.hp12c.engine.state.NumericSeparator
import com.arcom.hp12c.testing.AmortizacaoOperationJson
import com.arcom.hp12c.testing.AmortizacaoVectorJson
import com.arcom.hp12c.testing.AmortizacaoVectorsFile
import com.arcom.hp12c.testing.VectorsJson
import com.arcom.hp12c.testing.readTestResource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Suíte Amortização — 8 vetores ponta-a-ponta.**
 *
 * Cobre `f AMORT` (6 vetores de caminho feliz + 2 de Error 6).
 * Cada teste executa a sequência `operations` sobre o `InitialState` e verifica
 * `expected_x` (totalInterest), `expected_y` (totalPrincipal), `expected_pv`
 * (saldo atualizado em `financial.pv`) ou `error`.
 *
 * Fonte: `test-vectors/amortizacao-vectors.json` (skill `hp12c-simulator`).
 * Algoritmo documentado em `formulas/amortizacao.md`.
 * Invariante: totalInterest + totalPrincipal = n × PMT (verificado nos vetores).
 */
class AmortizacaoVectorsTest {

    private val engine: CalculatorEngine = CalculatorEngine.Default

    // ── Caminho feliz ────────────────────────────────────────────────────────
    @Test fun amort_001_1period_i1pct()          = runVector("amort-001")
    @Test fun amort_002_2periods_i1pct()         = runVector("amort-002")
    @Test fun amort_003_3periods_i1pct()         = runVector("amort-003")
    @Test fun amort_004_degenerate_i0()          = runVector("amort-004")
    @Test fun amort_005_1period_i10pct()         = runVector("amort-005")
    @Test fun amort_006_sequential_two_calls()   = runVector("amort-006")

    // ── Error 6 ──────────────────────────────────────────────────────────────
    @Test fun amort_error_001_n_zero()           = runVector("amort-error-001")
    @Test fun amort_error_002_n_negative()       = runVector("amort-error-002")

    // ─────────────────────────────────────────────────────────────────────────
    //  Motor
    // ─────────────────────────────────────────────────────────────────────────

    private fun runVector(id: String) {
        val v: AmortizacaoVectorJson = vectors.firstOrNull { it.id == id }
            ?: error("Vetor '$id' não encontrado em amortizacao-vectors.json (total: ${vectors.size}).")

        var state: CalculatorState = CalculatorEngine.InitialState.copy(
            display = parseFormat(v.format),
        )

        for (op in v.operations) {
            state = applyOperation(state, op)
        }

        if (v.error != null) {
            val expectedCode = v.error.removePrefix("Error ").toInt()
            assertNotNull(
                state.pendingError,
                "${v.id}: esperava Error $expectedCode mas pendingError é null",
            )
            assertEquals(
                expected = expectedCode,
                actual   = state.pendingError!!.code,
                message  = "${v.id}: esperado Error $expectedCode, veio Error ${state.pendingError!!.code}",
            )
        } else {
            assertNull(
                state.pendingError,
                "${v.id}: erro inesperado: ${state.pendingError}",
            )

            // Verifica X (totalInterest)
            if (v.expected_x != null) {
                val renderedX = engine.formatDisplay(state, NumericSeparator.PERIOD_COMMA).replace(",", "")
                assertEquals(
                    expected = v.expected_x,
                    actual   = renderedX,
                    message  = "${v.id}: esperado X=\"${v.expected_x}\", veio \"$renderedX\"" +
                               if (v.notes != null) " | ${v.notes}" else "",
                )
            }

            // Verifica Y (totalPrincipal) — substitui X pelo Y antes de formatar
            if (v.expected_y != null) {
                val renderedY = engine.formatDisplay(
                    state.copy(stack = state.stack.copy(x = state.stack.y)),
                    NumericSeparator.PERIOD_COMMA,
                ).replace(",", "")
                assertEquals(
                    expected = v.expected_y,
                    actual   = renderedY,
                    message  = "${v.id}: esperado Y=\"${v.expected_y}\", veio \"$renderedY\"",
                )
            }

            // Verifica PV atualizado no registrador financeiro
            if (v.expected_pv != null) {
                val pvValue = state.financial.pv
                    ?: error("${v.id}: financial.pv é null após AMORT")
                val renderedPv = engine.formatDisplay(
                    state.copy(stack = state.stack.copy(x = pvValue)),
                    NumericSeparator.PERIOD_COMMA,
                ).replace(",", "")
                assertEquals(
                    expected = v.expected_pv,
                    actual   = renderedPv,
                    message  = "${v.id}: esperado PV=\"${v.expected_pv}\", veio \"$renderedPv\"",
                )
            }
        }
    }

    private fun applyOperation(state: CalculatorState, op: AmortizacaoOperationJson): CalculatorState {
        return when (op.op) {
            "store_n" -> {
                val v = requireNotNull(op.value) { "store_n requer 'value'" }
                var s = state
                for (ev in doubleToEvents(v)) s = engine.reduce(s, ev)
                engine.reduce(s, Event.Financial.Store.N)
            }
            "store_i" -> {
                val v = requireNotNull(op.value) { "store_i requer 'value'" }
                var s = state
                for (ev in doubleToEvents(v)) s = engine.reduce(s, ev)
                engine.reduce(s, Event.Financial.Store.I)
            }
            "store_pv" -> {
                val v = requireNotNull(op.value) { "store_pv requer 'value'" }
                var s = state
                for (ev in doubleToEvents(v)) s = engine.reduce(s, ev)
                engine.reduce(s, Event.Financial.Store.Pv)
            }
            "store_pmt" -> {
                val v = requireNotNull(op.value) { "store_pmt requer 'value'" }
                var s = state
                for (ev in doubleToEvents(v)) s = engine.reduce(s, ev)
                engine.reduce(s, Event.Financial.Store.Pmt)
            }
            "store_fv" -> {
                val v = requireNotNull(op.value) { "store_fv requer 'value'" }
                var s = state
                for (ev in doubleToEvents(v)) s = engine.reduce(s, ev)
                engine.reduce(s, Event.Financial.Store.Fv)
            }
            "amort" -> engine.reduce(state, Event.Financial.Amortize)
            else    -> error("Operação desconhecida: '${op.op}'")
        }
    }

    private fun doubleToEvents(value: Double): List<Event> {
        val out    = mutableListOf<Event>()
        val isNeg  = value < 0.0
        val absStr = abs(value).toBigDecimal().stripTrailingZeros().toPlainString()
        for (ch in absStr) {
            when {
                ch == '.'       -> out += Event.Entry.DecimalPoint
                ch in '0'..'9' -> out += Event.Entry.Digit(ch.digitToInt())
                else            -> error("char inesperado '$ch' em doubleToEvents($value)")
            }
        }
        if (isNeg) out += Event.Entry.ChangeSign
        return out
    }

    private fun parseFormat(fmt: String): DisplayFormat {
        val p = fmt.split(' ')
        require(p.size == 2) { "Formato inválido: \"$fmt\"" }
        return when (p[0]) {
            "FIX" -> DisplayFormat.Fix(p[1].toInt())
            "SCI" -> DisplayFormat.Sci(p[1].toInt())
            "ENG" -> DisplayFormat.Eng(p[1].toInt())
            else  -> error("Modo de display desconhecido: \"$fmt\"")
        }
    }

    companion object {
        private val file: AmortizacaoVectorsFile by lazy {
            VectorsJson.decodeFromString(
                AmortizacaoVectorsFile.serializer(),
                readTestResource("test-vectors/amortizacao-vectors.json"),
            )
        }
        private val vectors: List<AmortizacaoVectorJson> get() = file.vectors
    }
}
