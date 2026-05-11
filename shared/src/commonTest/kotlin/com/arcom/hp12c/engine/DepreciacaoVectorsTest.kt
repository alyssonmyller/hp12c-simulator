package com.arcom.hp12c.engine

import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.DisplayFormat
import com.arcom.hp12c.engine.state.NumericSeparator
import com.arcom.hp12c.testing.DepreciacaoOperationJson
import com.arcom.hp12c.testing.DepreciacaoVectorJson
import com.arcom.hp12c.testing.DepreciacaoVectorsFile
import com.arcom.hp12c.testing.VectorsJson
import com.arcom.hp12c.testing.readTestResource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Suíte Depreciação — 11 vetores ponta-a-ponta.**
 *
 * Cobre `f SL` (3), `f SOYD` (3), `f DB` (3) + 2 vetores de Error 6.
 * Cada teste aplica a sequência `operations` sobre o `InitialState` e verifica
 * `expected_x` (D_j) e `expected_y` (RDV_j) — ou `error`.
 *
 * Fonte: `test-vectors/depreciacao-vectors.json` (skill `hp12c-simulator`).
 * Algoritmos documentados em `formulas/depreciacao.md`.
 */
class DepreciacaoVectorsTest {

    private val engine: CalculatorEngine = CalculatorEngine.Default

    // ── Straight-Line ────────────────────────────────────────────────────────
    @Test fun sl_001_year1()  = runVector("sl-001")
    @Test fun sl_002_year3()  = runVector("sl-002")
    @Test fun sl_003_year5()  = runVector("sl-003")

    // ── Sum-of-Years'-Digits ─────────────────────────────────────────────────
    @Test fun soyd_001_year1() = runVector("soyd-001")
    @Test fun soyd_002_year2() = runVector("soyd-002")
    @Test fun soyd_003_year5() = runVector("soyd-003")

    // ── Declining Balance ────────────────────────────────────────────────────
    @Test fun db_001_year1() = runVector("db-001")
    @Test fun db_002_year2() = runVector("db-002")
    @Test fun db_003_year3() = runVector("db-003")

    // ── Error 6 ──────────────────────────────────────────────────────────────
    @Test fun dep_error_001_n_zero()  = runVector("dep-error-001")
    @Test fun dep_error_002_j_zero()  = runVector("dep-error-002")

    // ─────────────────────────────────────────────────────────────────────────
    //  Motor
    // ─────────────────────────────────────────────────────────────────────────

    private fun runVector(id: String) {
        val v: DepreciacaoVectorJson = vectors.firstOrNull { it.id == id }
            ?: error("Vetor '$id' não encontrado em depreciacao-vectors.json (total: ${vectors.size}).")

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

            // Verifica X (D_j — depreciação do ano j)
            if (v.expected_x != null) {
                val renderedX = engine.formatDisplay(state, NumericSeparator.PERIOD_COMMA).replace(",", "")
                assertEquals(
                    expected = v.expected_x,
                    actual   = renderedX,
                    message  = "${v.id}: esperado X=\"${v.expected_x}\", veio \"$renderedX\"" +
                               if (v.notes != null) " | ${v.notes}" else "",
                )
            }

            // Verifica Y (RDV_j — valor residual) — substitui X pelo Y antes de formatar
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
        }
    }

    private fun applyOperation(state: CalculatorState, op: DepreciacaoOperationJson): CalculatorState {
        return when (op.op) {
            "store_n"  -> storeFinancial(state, op.value!!, Event.Financial.Store.N)
            "store_i"  -> storeFinancial(state, op.value!!, Event.Financial.Store.I)
            "store_pv" -> storeFinancial(state, op.value!!, Event.Financial.Store.Pv)
            "store_fv" -> storeFinancial(state, op.value!!, Event.Financial.Store.Fv)
            "enter_x"  -> enterX(state, requireNotNull(op.value) { "enter_x requer 'value'" })
            "sl"       -> engine.reduce(state, Event.Financial.DepreciationSL)
            "soyd"     -> engine.reduce(state, Event.Financial.DepreciationSOYD)
            "db"       -> engine.reduce(state, Event.Financial.DepreciationDB)
            else       -> error("Operação desconhecida: '${op.op}'")
        }
    }

    /** Digita `value` e pressiona a tecla de armazenamento financeiro. */
    private fun storeFinancial(
        state: CalculatorState,
        value: Double,
        storeEvent: Event.Financial.Store,
    ): CalculatorState {
        var s = state
        for (ev in doubleToEvents(value)) s = engine.reduce(s, ev)
        return engine.reduce(s, storeEvent)
    }

    /** Digita `value` em X sem armazenar em registrador financeiro. */
    private fun enterX(state: CalculatorState, value: Double): CalculatorState {
        var s = state
        for (ev in doubleToEvents(value)) s = engine.reduce(s, ev)
        return s
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
        private val file: DepreciacaoVectorsFile by lazy {
            VectorsJson.decodeFromString(
                DepreciacaoVectorsFile.serializer(),
                readTestResource("test-vectors/depreciacao-vectors.json"),
            )
        }
        private val vectors: List<DepreciacaoVectorJson> get() = file.vectors
    }
}
