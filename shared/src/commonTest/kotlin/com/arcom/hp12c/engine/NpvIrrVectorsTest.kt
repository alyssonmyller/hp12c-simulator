package com.arcom.hp12c.engine

import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.DisplayFormat
import com.arcom.hp12c.engine.state.NumericSeparator
import com.arcom.hp12c.testing.NpvIrrOperationJson
import com.arcom.hp12c.testing.NpvIrrVectorJson
import com.arcom.hp12c.testing.NpvIrrVectorsFile
import com.arcom.hp12c.testing.VectorsJson
import com.arcom.hp12c.testing.readTestResource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Suíte NPV/IRR — 13 vetores ponta-a-ponta.**
 *
 * Cobre `f NPV` (7 vetores de caminho feliz + 2 de Error 7) e `f IRR` (2 vetores de caminho
 * feliz + 1 Error 3 + 2 Error 7 compartilhados). Cada teste executa a sequência de operações
 * descritas em `operations` sobre o `InitialState` e verifica `expected_x` ou `error`.
 *
 * Schema stateful: `operations: [{op, value?}]` onde op ∈ {store_i, cfo, cfj, nj, npv, irr}.
 * Fonte: `test-vectors/npv-irr-vectors.json` (skill `hp12c-simulator`).
 * Fórmulas documentadas em `formulas/npv-irr.md`.
 */
class NpvIrrVectorsTest {

    private val engine: CalculatorEngine = CalculatorEngine.Default

    // ── NPV — caminho feliz ──────────────────────────────────────────────────
    @Test fun npv_001_zero_exact_i10()           = runVector("npv-001")
    @Test fun npv_002_positive_i10_2periods()    = runVector("npv-002")
    @Test fun npv_003_negative_nj3_i10()         = runVector("npv-003")
    @Test fun npv_004_degenerate_i0()            = runVector("npv-004")
    @Test fun npv_005_bond_coupon_i10()          = runVector("npv-005")
    @Test fun npv_006_nj2_second_group_i10()     = runVector("npv-006")
    @Test fun npv_007_cfo_reset_clears_flows()   = runVector("npv-007")

    // ── IRR — caminho feliz ──────────────────────────────────────────────────
    @Test fun irr_001_one_period_closed_form()   = runVector("irr-001")
    @Test fun irr_002_two_period_bond()          = runVector("irr-002")

    // ── Error 3 (IRR) ─────────────────────────────────────────────────────────
    @Test fun irr_error_001_no_sign_change()     = runVector("irr-error-001")

    // ── Error 7 (NPV/CF) ─────────────────────────────────────────────────────
    @Test fun npv_error_001_no_cfo()             = runVector("npv-error-001")
    @Test fun nj_error_001_nj_too_large()        = runVector("nj-error-001")
    @Test fun nj_error_002_nj_without_cfj()      = runVector("nj-error-002")

    // ─────────────────────────────────────────────────────────────────────────
    //  Motor
    // ─────────────────────────────────────────────────────────────────────────

    private fun runVector(id: String) {
        val v: NpvIrrVectorJson = vectors.firstOrNull { it.id == id }
            ?: error("Vetor '$id' não encontrado em npv-irr-vectors.json (total: ${vectors.size}).")

        var state: CalculatorState = CalculatorEngine.InitialState.copy(
            display = parseFormat(v.format),
        )

        // Executa cada operação da lista
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
            val renderedX = engine.formatDisplay(state, NumericSeparator.PERIOD_COMMA).replace(",", "")
            assertEquals(
                expected = v.expected_x,
                actual   = renderedX,
                message  = "${v.id} (${v.source}): esperado X=\"${v.expected_x}\", veio \"$renderedX\"" +
                           if (v.notes != null) " | ${v.notes}" else "",
            )
        }
    }

    /**
     * Executa uma operação individual sobre o estado atual.
     *
     * Para `store_i`, `cfo`, `cfj`, `nj`: o valor é inserido via eventos de dígito para
     * evitar conversão de Double (os valores nos vetores são todos inteiros ou frações simples).
     * Para `npv`, `irr`: sem valor, apenas dispara o evento.
     */
    private fun applyOperation(state: CalculatorState, op: NpvIrrOperationJson): CalculatorState {
        return when (op.op) {
            "store_i" -> {
                val v = requireNotNull(op.value) { "store_i requer 'value'" }
                var s = state
                for (ev in doubleToEvents(v)) s = engine.reduce(s, ev)
                engine.reduce(s, Event.Financial.Store.I)
            }
            "cfo" -> {
                val v = requireNotNull(op.value) { "cfo requer 'value'" }
                var s = state
                for (ev in doubleToEvents(v)) s = engine.reduce(s, ev)
                engine.reduce(s, Event.Cashflow.CashFlowZero)
            }
            "cfj" -> {
                val v = requireNotNull(op.value) { "cfj requer 'value'" }
                var s = state
                for (ev in doubleToEvents(v)) s = engine.reduce(s, ev)
                engine.reduce(s, Event.Cashflow.CashFlowJ)
            }
            "nj" -> {
                val v = requireNotNull(op.value) { "nj requer 'value'" }
                var s = state
                for (ev in doubleToEvents(v)) s = engine.reduce(s, ev)
                engine.reduce(s, Event.Cashflow.CountJ)
            }
            "npv" -> engine.reduce(state, Event.Cashflow.Npv)
            "irr" -> engine.reduce(state, Event.Cashflow.Irr)
            else  -> error("Operação desconhecida: '${op.op}'")
        }
    }

    /**
     * Converte um `Double` em eventos de entrada HP 12C.
     * Os valores dos vetores NPV/IRR são todos inteiros ou frações simples (ex.: -1000, 110, 10),
     * sem risco de perda de precisão via `Double`.
     * Negativo → dígitos do valor absoluto + `CHS`.
     */
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
        private val file: NpvIrrVectorsFile by lazy {
            VectorsJson.decodeFromString(
                NpvIrrVectorsFile.serializer(),
                readTestResource("test-vectors/npv-irr-vectors.json"),
            )
        }
        private val vectors: List<NpvIrrVectorJson> get() = file.vectors
    }
}
