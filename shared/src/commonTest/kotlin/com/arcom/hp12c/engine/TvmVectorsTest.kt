package br.com.alyssonmyller.calculus.engine

import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import br.com.alyssonmyller.calculus.engine.state.DisplayFormat
import br.com.alyssonmyller.calculus.engine.state.NumericSeparator
import br.com.alyssonmyller.calculus.testing.TvmInputsJson
import br.com.alyssonmyller.calculus.testing.TvmVectorJson
import br.com.alyssonmyller.calculus.testing.TvmVectorsFile
import br.com.alyssonmyller.calculus.testing.VectorsJson
import br.com.alyssonmyller.calculus.testing.readTestResource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Suíte TVM completa — 18 vetores ponta-a-ponta.**
 *
 * Carrega `test-vectors/tvm-vectors.json` uma vez via [readTestResource], desserializa em
 * [TvmVectorsFile] via `kotlinx.serialization`, e roda cada vetor individualmente:
 *
 *   1. Monta `CalculatorState` inicial a partir de `CalculatorEngine.InitialState`, com
 *      `display` parseado do campo `format` do JSON (`"FIX 2"` → [DisplayFormat.Fix] com
 *      `places = 2`, idem SCI/ENG).
 *   2. Gera a lista de `Event` via [inputsToEvents] — emite `SetEndMode`/`SetBeginMode` primeiro,
 *      depois `Store` de cada registrador que **não** é o `solve_for`, depois `Solve.X` para o
 *      campo-alvo. Campos com valor numérico no JSON (inclusive `0`) viram sequência
 *      `Digit/DecimalPoint/ChangeSign` fiel ao que o usuário HP digitaria.
 *   3. Aplica `engine.reduce(initial, events)`.
 *   4. Renderiza com `NumericSeparator.PERIOD_COMMA` (en-US: vírgula milhar, ponto decimal).
 *   5. **Normaliza removendo vírgulas** (milhar) — o campo `expected` do JSON é canônico
 *      sem separador (ex.: `"6083.26"`), enquanto a HP física em en-US exibe `"6,083.26"`.
 *      A normalização é simétrica e não ambígua porque em en-US a vírgula só aparece em
 *      milhar — o ponto decimal é `.`. Em pt-BR a convenção seria inversa mas para
 *      comparação usamos sempre en-US como lingua franca dos testes.
 *   6. Compara com `v.expected` como string via `assertEquals`. Qualquer divergência de
 *      dígito (arredondamento, sinal, casas decimais) quebra o teste com mensagem citando
 *      `id`, `source`, `description`, render cru e render canonizado.
 *
 * **Por que um `@Test` por vetor, e não um loop com `assertAll`?**
 * `kotlin.test` em `commonTest` não tem `assertAll` soft. Se eu colapsasse os 18 vetores em
 * um único `@Test`, uma falha no vetor 3 esconderia os 15 seguintes. Com 18 funções, cada
 * vetor aparece verde/vermelho independente no relatório do CI, apontando culpado direto.
 * O boilerplate é mínimo (uma linha por vetor delegando a [runVector]).
 *
 * **Cobertura após este passo.** Os 18 vetores + as 7 suítes unitárias anteriores
 * (`Hp12cDecimalTest`, `StackOpsTest`, `ReducerTest`, `ReducerFinancialStoreTest`,
 * `ReducerFinancialSolveTest`, `TranscendentalsTest`, `DisplayFormatterTest`) somam ≈ 185
 * casos de teste cobrindo 100% do caminho TVM da Fase 1 MVP.
 */
class TvmVectorsTest {

    private val engine: CalculatorEngine = CalculatorEngine.Default

    // ═══════════════════════════════════════════════════════════════════════
    // Um @Test por vetor. Nome da função cita o id — o relatório do runner
    // mostra exatamente qual vetor está vermelho.
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun tvm_001_moretti_cap4_ex10_FV_5000_5m_4pc() = runVector("tvm-001")
    @Test fun tvm_002_moretti_cap4_ex11_PV_2000_24m_4pc() = runVector("tvm-002")
    @Test fun tvm_003_moretti_cap4_ex12_n_com_teto() = runVector("tvm-003")
    @Test fun tvm_004_moretti_cap4_ex13_i_PMT_zero() = runVector("tvm-004")
    @Test fun tvm_005_moretti_cap6_ex22_PV_6_parc_1500() = runVector("tvm-005")
    @Test fun tvm_006_moretti_cap6_ex23_PMT_financ_veic() = runVector("tvm-006")
    @Test fun tvm_007_moretti_cap6_ex24_FV_poupanca() = runVector("tvm-007")
    @Test fun tvm_008_moretti_cap6_ex25_PMT_acumulo() = runVector("tvm-008")
    @Test fun tvm_009_moretti_cap6_ex24ant_PMT_BEGIN_auto() = runVector("tvm-009")
    @Test fun tvm_010_moretti_cap6_ex25ant_FV_BEGIN_500_60m() = runVector("tvm-010")
    @Test fun tvm_011_moretti_prop_481_FV_4500_18m_85pc() = runVector("tvm-011")
    @Test fun tvm_012_moretti_prop_482_FV_500_84m_4pc() = runVector("tvm-012")
    @Test fun tvm_013_moretti_prop_488_PV_negativo() = runVector("tvm-013")
    @Test fun tvm_014_moretti_prop_4810_i_1000_para_2000() = runVector("tvm-014")
    @Test fun tvm_015_moretti_prop_4815_n_600_para_900() = runVector("tvm-015")
    @Test fun tvm_016_moretti_prop_4816_n_350_para_500() = runVector("tvm-016")
    @Test fun tvm_017_manual_sec3_FV_100k_30anos() = runVector("tvm-017")
    @Test fun tvm_018_manual_sec3_PMT_home_mortgage() = runVector("tvm-018")

    // ───────── motor do teste ─────────

    private fun runVector(id: String) {
        val v = vectors.firstOrNull { it.id == id }
            ?: error("Vetor '$id' não encontrado em tvm-vectors.json (total: ${vectors.size}).")

        val initial: CalculatorState = CalculatorEngine.InitialState.copy(
            display = parseFormat(v.format),
        )
        val events: List<Event> = inputsToEvents(v.inputs, v.solve_for)
        val finalState: CalculatorState = engine.reduce(initial, events)

        val rendered: String = engine.formatDisplay(finalState, NumericSeparator.PERIOD_COMMA)
        val canonical: String = rendered.replace(",", "")

        assertEquals(
            expected = v.expected,
            actual = canonical,
            message = buildString {
                append("${v.id} (${v.source}): ")
                append("esperado \"${v.expected}\", veio \"$canonical\" ")
                append("(render cru en-US: \"$rendered\"). ")
                append("Descrição: ${v.description}")
                if (v.notes != null) append(" | notes: ${v.notes}")
            },
        )
    }

    // ───────── parsing de formato ─────────

    private fun parseFormat(fmt: String): DisplayFormat {
        val parts: List<String> = fmt.split(' ')
        require(parts.size == 2) { "Formato de display inválido: \"$fmt\" (esperado \"FIX n\"|\"SCI n\"|\"ENG n\")" }
        val places: Int = parts[1].toInt()
        return when (parts[0]) {
            "FIX" -> DisplayFormat.Fix(places)
            "SCI" -> DisplayFormat.Sci(places)
            "ENG" -> DisplayFormat.Eng(places)
            else -> error("Modo de display desconhecido em \"$fmt\"")
        }
    }

    // ───────── tradução inputs → eventos ─────────

    /**
     * Traduz `TvmInputsJson + solveFor` numa sequência de [Event] que reproduz a digitação
     * do usuário HP. Ordem:
     *   1. `SetEndMode` ou `SetBeginMode` conforme `mode`.
     *   2. `Store` de cada registrador (n/i/PV/PMT/FV) que **não** é o `solve_for`, mesmo
     *      que o valor seja 0 — a HP aceita store de zero e a fórmula usa o 0 literal.
     *   3. `Solve.X` para o campo-alvo.
     *
     * Registradores com valor 0 no JSON quando são o `solve_for` servem só como placeholder
     * do schema — por isso pulamos o store deles.
     */
    private fun inputsToEvents(inp: TvmInputsJson, solveFor: String): List<Event> {
        val out: MutableList<Event> = mutableListOf()

        out += when (inp.mode) {
            "END" -> Event.Financial.SetEndMode
            "BEGIN" -> Event.Financial.SetBeginMode
            else -> error("Modo desconhecido: \"${inp.mode}\" (esperado END|BEGIN)")
        }

        if (solveFor != "n") out += numberToEvents(inp.n) + Event.Financial.Store.N
        if (solveFor != "i") out += numberToEvents(inp.i) + Event.Financial.Store.I
        if (solveFor != "PV") out += numberToEvents(inp.PV) + Event.Financial.Store.Pv
        if (solveFor != "PMT") out += numberToEvents(inp.PMT) + Event.Financial.Store.Pmt
        if (solveFor != "FV") out += numberToEvents(inp.FV) + Event.Financial.Store.Fv

        out += when (solveFor) {
            "n" -> Event.Financial.Solve.N
            "i" -> Event.Financial.Solve.I
            "PV" -> Event.Financial.Solve.Pv
            "PMT" -> Event.Financial.Solve.Pmt
            "FV" -> Event.Financial.Solve.Fv
            else -> error("solve_for desconhecido: \"$solveFor\" (esperado n|i|PV|PMT|FV)")
        }

        return out
    }

    /**
     * Converte um `Double` do JSON numa sequência de `Event.Entry` que reproduz a digitação
     * do usuário. Exemplos:
     *   - `5.0`     → `[Digit(5)]`
     *   - `0.0`     → `[Digit(0)]`
     *   - `-5000.0` → `[Digit(5), Digit(0), Digit(0), Digit(0), ChangeSign]`
     *   - `3.25`    → `[Digit(3), DecimalPoint, Digit(2), Digit(5)]`
     *   - `-4278.43` → `[Digit(4), Digit(2), Digit(7), Digit(8), DecimalPoint, Digit(4),
     *                     Digit(3), ChangeSign]`
     *
     * Usa `Double.toNumberString()` para:
     *   - Omitir `.0` final em valores inteiros (`5.0` → `"5"`, não `"5.0"`).
     *   - Preservar casas decimais em valores fracionários (`3.25` → `"3.25"`).
     *   - Evitar notação científica — todos os valores do JSON ficam em `[-10^5, 10^6]`,
     *     faixa em que `Double.toString()` do Kotlin/JVM usa decimal fixo.
     *
     * O `ChangeSign` vai sempre **depois** dos dígitos, imitando a convenção do manual
     * (`5000 CHS PV`).
     */
    private fun numberToEvents(value: Double): List<Event> {
        val out: MutableList<Event> = mutableListOf()
        val absStr: String = abs(value).toNumberString()
        for (ch in absStr) {
            when {
                ch == '.' -> out += Event.Entry.DecimalPoint
                ch in '0'..'9' -> out += Event.Entry.Digit(ch.digitToInt())
                else -> error("Caractere inesperado em numberToEvents('$absStr'): '$ch'")
            }
        }
        if (value < 0.0) out += Event.Entry.ChangeSign
        return out
    }

    /**
     * Formatação "limpa" de `Double`: omite `.0` quando o valor é inteiro.
     *
     *   - `5.0`      → `"5"`
     *   - `0.0`      → `"0"`
     *   - `360.0`    → `"360"`
     *   - `3.25`     → `"3.25"`
     *   - `2.7214`   → `"2.7214"`
     *
     * Implementação via `toLong()` + round-trip comparison: se o valor é representável como
     * `Long` exato (ou seja, a fração é zero), devolvemos a forma inteira. Senão, delegamos
     * ao `Double.toString()` do runtime — todos os valores do JSON canônico ficam dentro da
     * faixa `[0, 10^6]` onde Kotlin/JVM usa formato decimal fixo sem notação científica.
     */
    private fun Double.toNumberString(): String {
        val asLong: Long = toLong()
        return if (asLong.toDouble() == this) asLong.toString() else toString()
    }

    companion object {
        /**
         * Cache do JSON carregado. Lazy para não pagar I/O se nenhum teste rodar;
         * compartilhado entre os 18 `@Test` via `companion object`.
         */
        private val file: TvmVectorsFile by lazy {
            val raw: String = readTestResource("test-vectors/tvm-vectors.json")
            VectorsJson.decodeFromString(TvmVectorsFile.serializer(), raw)
        }

        private val vectors: List<TvmVectorJson> get() = file.vectors
    }
}
