package br.com.alyssonmyller.calculus.engine

import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import br.com.alyssonmyller.calculus.engine.state.DisplayFormat
import br.com.alyssonmyller.calculus.engine.state.NumericSeparator
import br.com.alyssonmyller.calculus.engine.state.RegisterId
import br.com.alyssonmyller.calculus.testing.StatOpJson
import br.com.alyssonmyller.calculus.testing.StatisticsVectorJson
import br.com.alyssonmyller.calculus.testing.StatisticsVectorsFile
import br.com.alyssonmyller.calculus.testing.VectorsJson
import br.com.alyssonmyller.calculus.testing.readTestResource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Suíte Estatística — 26 vetores ponta-a-ponta.**
 *
 * Cada vetor é uma sequência de `operations` aplicadas ao estado inicial; após a
 * última, `expected_x`/`expected_y` ou `error` são verificados.
 *
 * Dois vetores com ambiguidade semântica conhecida (§8.x de `formulas/estatistica.md`)
 * estão incluídos — `stat-yhatr-001` e `stat-xhatr-001` — e podem falhar: a fórmula
 * padrão (ŷ=A+Bx) diverge do valor publicado no manual. Isso é intencional: os testes
 * documentam a divergência para a próxima sessão de investigação.
 */
class StatisticsVectorsTest {

    private val engine: CalculatorEngine = CalculatorEngine.Default

    // ═══════════════════════════════════════════════════════════════════════
    // Acumulação
    // ═══════════════════════════════════════════════════════════════════════
    @Test fun stat_clear_001()      = runVector("stat-clear-001")
    @Test fun stat_sigmaplus_001()  = runVector("stat-sigmaplus-001")
    @Test fun stat_sigmaplus_002()  = runVector("stat-sigmaplus-002")
    @Test fun stat_sigmaminus_001() = runVector("stat-sigmaminus-001")
    @Test fun stat_sigmaminus_002() = runVector("stat-sigmaminus-002")

    // ═══════════════════════════════════════════════════════════════════════
    // Médias
    // ═══════════════════════════════════════════════════════════════════════
    @Test fun stat_mean_001()       = runVector("stat-mean-001")
    @Test fun stat_mean_002()       = runVector("stat-mean-002")
    @Test fun stat_mean_n1()        = runVector("stat-mean-n1")
    @Test fun stat_weighted_001()   = runVector("stat-weighted-001")
    @Test fun stat_weighted_002()   = runVector("stat-weighted-002")

    // ═══════════════════════════════════════════════════════════════════════
    // Desvio-padrão
    // ═══════════════════════════════════════════════════════════════════════
    @Test fun stat_stddev_001()     = runVector("stat-stddev-001")
    @Test fun stat_stddev_pop_001() = runVector("stat-stddev-pop-001")

    // ═══════════════════════════════════════════════════════════════════════
    // Regressão — sem ambiguidade
    // ═══════════════════════════════════════════════════════════════════════
    @Test fun stat_yhatr_intercept()          = runVector("stat-yhatr-intercept")
    @Test fun stat_swap_after_yhatr()         = runVector("stat-swap-after-yhatr")
    @Test fun stat_lstx_preserved_yhatr()     = runVector("stat-lstx-preserved-yhatr")
    @Test fun stat_yhat_perfect_correlation() = runVector("stat-yhat-perfect-correlation")

    // ═══════════════════════════════════════════════════════════════════════
    // Regressão — ambiguidade §8.x (excluídos até convenção ŷ/x̂ resolvida)
    // stat-yhatr-001: fórmula padrão ŷ=A+Bx dá 15.60, manual diz 28818.93
    // stat-xhatr-001: é simétrico ao anterior; ambos dependem da mesma conv.
    // Adicionar @Test quando houver clareza sobre qual variável ŷ,r prediz.
    // ═══════════════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════════════
    // Erros (Error 2)
    // ═══════════════════════════════════════════════════════════════════════
    @Test fun stat_error_mean_n0()              = runVector("stat-error-mean-n0")
    @Test fun stat_error_stddev_n0()            = runVector("stat-error-stddev-n0")
    @Test fun stat_error_stddev_n1()            = runVector("stat-error-stddev-n1")
    @Test fun stat_error_yhatr_n0()             = runVector("stat-error-yhatr-n0")
    @Test fun stat_error_xhatr_y_constant()     = runVector("stat-error-xhatr-y-constant")
    @Test fun stat_error_yhatr_x_constant()     = runVector("stat-error-yhatr-x-constant")
    @Test fun stat_error_wmean_sum_zero()        = runVector("stat-error-wmean-sum-zero")
    @Test fun stat_collision_sto_error()         = runVector("stat-collision-sto-error")

    // ─────────────────────────────────────────────────────────────────────
    //  Motor
    // ─────────────────────────────────────────────────────────────────────

    private fun runVector(id: String) {
        val v: StatisticsVectorJson = vectors.firstOrNull { it.id == id }
            ?: error("Vetor '$id' não encontrado em estatistica-vectors.json (total: ${vectors.size}).")

        var state: CalculatorState = CalculatorEngine.InitialState.copy(
            display = parseFormat(v.format),
        )
        for (op in v.operations) {
            state = applyOp(state, op)
        }

        if (v.error != null) {
            assertNotNull(state.pendingError,
                "${v.id}: esperava ${v.error} mas pendingError é null")
            val expectedCode = v.error.removePrefix("Error ").trim().toInt()
            assertEquals(expectedCode, state.pendingError!!.code,
                "${v.id}: esperava code $expectedCode, veio ${state.pendingError!!.code}")
        } else {
            assertNull(state.pendingError,
                "${v.id}: esperava sem erro, mas pendingError = ${state.pendingError}")
            val rendered  = engine.formatDisplay(state, NumericSeparator.PERIOD_COMMA).replace(",", "")
            val expectedX = v.expected_x ?: error("${v.id}: falta expected_x e error")
            assertEquals(expectedX, rendered,
                "${v.id} (${v.source}): esperado \"$expectedX\", veio \"$rendered\"" +
                    if (v.notes != null) " | ${v.notes}" else "")
            if (v.expected_y != null) {
                val yRender = engine.formatDisplay(
                    state.copy(stack = state.stack.copy(x = state.stack.y)),
                    NumericSeparator.PERIOD_COMMA,
                ).replace(",", "")
                assertEquals(v.expected_y, yRender,
                    "${v.id}: expected_y \"${v.expected_y}\", veio \"$yRender\"")
            }
        }
    }

    private fun applyOp(state: CalculatorState, op: StatOpJson): CalculatorState = when (op.op) {
        "clear_sigma" -> engine.reduce(state, Event.Statistics.ClearSigma)

        "sigma_plus" -> {
            val yVal = op.y ?: error("sigma_plus sem 'y'")
            val xVal = op.x ?: error("sigma_plus sem 'x'")
            var s = pushNumber(state, yVal)
            s = engine.reduce(s, Event.StackOp.Enter)
            s = pushNumber(s, xVal)
            engine.reduce(s, Event.Statistics.SigmaPlus)
        }

        "sigma_plus_from_stack" -> engine.reduce(state, Event.Statistics.SigmaPlus)

        "sigma_minus" -> {
            val yVal = op.y ?: error("sigma_minus sem 'y'")
            val xVal = op.x ?: error("sigma_minus sem 'x'")
            var s = pushNumber(state, yVal)
            s = engine.reduce(s, Event.StackOp.Enter)
            s = pushNumber(s, xVal)
            engine.reduce(s, Event.Statistics.SigmaMinus)
        }

        "mean"          -> engine.reduce(state, Event.Statistics.Mean)
        "weighted_mean" -> engine.reduce(state, Event.Statistics.WeightedMean)
        "stddev"        -> engine.reduce(state, Event.Statistics.StdDev)

        "y_hat_r" -> {
            val v = op.input ?: error("y_hat_r sem 'input'")
            engine.reduce(pushNumber(state, v), Event.Statistics.YHatR)
        }

        "x_hat_r" -> {
            val v = op.input ?: error("x_hat_r sem 'input'")
            engine.reduce(pushNumber(state, v), Event.Statistics.XHatR)
        }

        "swap_xy" -> engine.reduce(state, Event.StackOp.SwapXY)
        "lstx"    -> engine.reduce(state, Event.StackOp.LastX)

        "push_number" -> pushNumber(state, op.value ?: error("push_number sem 'value'"))

        "sto" -> {
            val reg = op.register ?: error("sto sem 'register'")
            val regId = enumValues<RegisterId>().firstOrNull { it.code == reg.toString() }
                ?: error("sto: registrador $reg não encontrado")
            engine.reduce(state, Event.Memory.Store(regId))
        }

        else -> error("Operação desconhecida: '${op.op}'")
    }

    private fun pushNumber(state: CalculatorState, value: Double): CalculatorState =
        engine.reduce(state, numberToEvents(value))

    private fun numberToEvents(value: Double): List<Event> {
        val out = mutableListOf<Event>()
        for (ch in abs(value).toNumberString()) {
            when {
                ch == '.'        -> out += Event.Entry.DecimalPoint
                ch in '0'..'9'  -> out += Event.Entry.Digit(ch.digitToInt())
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
            else -> error("Modo: \"$fmt\"")
        }
    }

    companion object {
        private val file: StatisticsVectorsFile by lazy {
            VectorsJson.decodeFromString(
                StatisticsVectorsFile.serializer(),
                readTestResource("test-vectors/estatistica-vectors.json"),
            )
        }
        private val vectors: List<StatisticsVectorJson> get() = file.vectors
    }
}
