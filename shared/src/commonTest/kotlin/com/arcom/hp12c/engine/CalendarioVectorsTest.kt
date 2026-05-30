package br.com.alyssonmyller.calculus.engine

import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import br.com.alyssonmyller.calculus.engine.state.DisplayFormat
import br.com.alyssonmyller.calculus.engine.state.NumericSeparator
import br.com.alyssonmyller.calculus.testing.CalendarioVectorJson
import br.com.alyssonmyller.calculus.testing.CalendarioVectorsFile
import br.com.alyssonmyller.calculus.testing.VectorsJson
import br.com.alyssonmyller.calculus.testing.readTestResource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Suíte Calendário — 12 vetores ponta-a-ponta.**
 *
 * Cobre `f DATE` (6 vetores de caminho feliz + 2 de Error 8) e `f DYS` (4 vetores).
 * Cada teste configura o modo M.DY ou D.MY, empilha as datas/dias e dispara a operação.
 *
 * Schema dos vetores: `op: "date"|"dys"`, `mode: "MDY"|"DMY"`, datas como strings HP
 * (ex.: `"6.301994"`), campo `days` inteiro para DATE. Resultado em `expected_x`;
 * `expected_y` opcional (dia-da-semana em DATE, data posterior em DYS).
 *
 * Fonte: `test-vectors/calendario-vectors.json` (skill `hp12c-simulator`).
 * Algoritmo JDN documentado em `formulas/calendario.md`, Seção 4.
 */
class CalendarioVectorsTest {

    private val engine: CalculatorEngine = CalculatorEngine.Default

    // ── DATE — caminho feliz ─────────────────────────────────────────────────
    @Test fun cal_date_001_90d_after_30jun1994_mdy()    = runVector("cal-date-001")
    @Test fun cal_date_002_neg90d_from_28sep1994_mdy()  = runVector("cal-date-002")
    @Test fun cal_date_003_1d_after_28feb2000_leap()    = runVector("cal-date-003")
    @Test fun cal_date_004_1d_after_29feb2000()         = runVector("cal-date-004")
    @Test fun cal_date_005_1d_after_28feb2001_noleap()  = runVector("cal-date-005")
    @Test fun cal_date_006_90d_after_30jun1994_dmy()    = runVector("cal-date-006")

    // ── DYS — caminho feliz ──────────────────────────────────────────────────
    @Test fun cal_dys_001_30jun_to_28sep1994()          = runVector("cal-dys-001")
    @Test fun cal_dys_002_leap_year_span_366()          = runVector("cal-dys-002")
    @Test fun cal_dys_003_normal_year_span_365()        = runVector("cal-dys-003")
    @Test fun cal_dys_004_same_date_zero()              = runVector("cal-dys-004")

    // ── Error 8 ──────────────────────────────────────────────────────────────
    @Test fun cal_error_001_feb30_invalid()             = runVector("cal-error-001")
    @Test fun cal_error_002_feb29_nonleap_invalid()     = runVector("cal-error-002")

    // ─────────────────────────────────────────────────────────────────────────
    //  Motor
    // ─────────────────────────────────────────────────────────────────────────

    private fun runVector(id: String) {
        val v: CalendarioVectorJson = vectors.firstOrNull { it.id == id }
            ?: error("Vetor '$id' não encontrado em calendario-vectors.json (total: ${vectors.size}).")

        var state: CalculatorState = CalculatorEngine.InitialState.copy(
            display = parseFormat(v.format),
        )

        // Configura o modo M.DY ou D.MY
        val modeEvent = if (v.mode == "MDY") Event.Calendar.SetMdy else Event.Calendar.SetDmy
        state = engine.reduce(state, modeEvent)

        // Monta pilha e dispara operação
        state = when (v.op) {
            "date" -> {
                val dateStr = requireNotNull(v.date)  { "${v.id}: campo 'date' ausente" }
                val daysVal = requireNotNull(v.days)  { "${v.id}: campo 'days' ausente" }
                enterDateAndDays(state, dateStr, daysVal)
            }
            "dys" -> {
                val d1 = requireNotNull(v.date1) { "${v.id}: campo 'date1' ausente" }
                val d2 = requireNotNull(v.date2) { "${v.id}: campo 'date2' ausente" }
                enterTwoDates(state, d1, d2)
            }
            else -> error("${v.id}: op desconhecido: '${v.op}'")
        }

        if (v.error != null) {
            // Vetor de erro: verifica que o código de erro esperado está em pendingError
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
            // Vetor de caminho feliz: verifica X e opcionalmente Y
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

            if (v.expected_y != null) {
                // Substitui X pelo Y para renderizar Y com o mesmo formatter
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

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers de entrada
    // ─────────────────────────────────────────────────────────────────────────

    /** Emite os eventos para `dateStr ENTER days f DATE`. */
    private fun enterDateAndDays(
        state: CalculatorState,
        dateStr: String,
        days: Int,
    ): CalculatorState {
        var s = state
        for (ev in stringToEvents(dateStr)) s = engine.reduce(s, ev)
        s = engine.reduce(s, Event.StackOp.Enter)
        for (ev in intToEvents(days)) s = engine.reduce(s, ev)
        return engine.reduce(s, Event.Calendar.Date)
    }

    /** Emite os eventos para `date1 ENTER date2 f DYS`. */
    private fun enterTwoDates(
        state: CalculatorState,
        date1: String,
        date2: String,
    ): CalculatorState {
        var s = state
        for (ev in stringToEvents(date1)) s = engine.reduce(s, ev)
        s = engine.reduce(s, Event.StackOp.Enter)
        for (ev in stringToEvents(date2)) s = engine.reduce(s, ev)
        return engine.reduce(s, Event.Calendar.Dys)
    }

    /**
     * Converte uma string de data HP (ex.: `"6.301994"`, `"-90"`) em eventos de dígito.
     * Usa conversão string→eventos para evitar perda de precisão de `Double`.
     */
    private fun stringToEvents(value: String): List<Event> {
        val out    = mutableListOf<Event>()
        val isNeg  = value.startsWith('-')
        val absStr = if (isNeg) value.drop(1) else value
        for (ch in absStr) {
            when {
                ch == '.'       -> out += Event.Entry.DecimalPoint
                ch in '0'..'9' -> out += Event.Entry.Digit(ch.digitToInt())
                else            -> error("${this::class.simpleName}: char inesperado '$ch' em \"$value\"")
            }
        }
        if (isNeg) out += Event.Entry.ChangeSign
        return out
    }

    /** Converte um `Int` (ex.: `90`, `-90`, `0`) em eventos de dígito. */
    private fun intToEvents(value: Int): List<Event> {
        val out    = mutableListOf<Event>()
        val absStr = abs(value).toString()
        for (ch in absStr) out += Event.Entry.Digit(ch.digitToInt())
        if (value < 0) out += Event.Entry.ChangeSign
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
        private val file: CalendarioVectorsFile by lazy {
            VectorsJson.decodeFromString(
                CalendarioVectorsFile.serializer(),
                readTestResource("test-vectors/calendario-vectors.json"),
            )
        }
        private val vectors: List<CalendarioVectorJson> get() = file.vectors
    }
}
