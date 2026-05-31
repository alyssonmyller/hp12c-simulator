package br.com.alyssonmyller.calculus.engine

import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import br.com.alyssonmyller.calculus.engine.state.DisplayFormat
import br.com.alyssonmyller.calculus.engine.state.NumericSeparator
import br.com.alyssonmyller.calculus.engine.state.Stack
import br.com.alyssonmyller.calculus.testing.TranscendentalsVectorJson
import br.com.alyssonmyller.calculus.testing.TranscendentalsVectorsFile
import br.com.alyssonmyller.calculus.testing.VectorsJson
import br.com.alyssonmyller.calculus.testing.readTestResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * **Suíte Transcendentais + Percentagens — 34 vetores ponta-a-ponta.**
 *
 * Gêmea metodológica do [TvmVectorsTest]: carrega
 * `test-vectors/transcendentais-vectors.json` via [readTestResource], desserializa
 * em [TranscendentalsVectorsFile] com `kotlinx.serialization`, e roda cada vetor
 * como um `@Test` independente para que falhas apareçam nominadas no relatório
 * do CI.
 *
 * ### Pipeline por vetor
 *
 * 1. **Montar pilha** via [stackFromList]: cada string em `vector.stack` vira um
 *    [Hp12cDecimal] preenchendo X/Y/Z/T de baixo para cima. O último item é **X**
 *    (convenção do JSON — ver `conventions.stack_shape` no cabeçalho do arquivo).
 *    Níveis ausentes recebem zero. Isso é mais direto que emular digitação (como
 *    no `TvmVectorsTest`) porque aqui não estamos testando o buffer de entrada,
 *    estamos testando o reducer sobre uma pilha já montada.
 *
 * 2. **Definir display** via [parseFormat] — mesmo formato `"FIX n"` | `"SCI n"` |
 *    `"ENG n"` usado no TVM. Crítico para a tecla `RND` (que depende do formato
 *    corrente) e para renderizar o resultado final em `expected`.
 *
 * 3. **Dispatch da operação** via [operationToEvent]: mapa string → `Event` sealed
 *    instance. Literais como `"reciprocal"` batem com os `operation_codes` do JSON.
 *
 * 4. **Aplicar** `engine.reduce(state, listOf(op))` — a engine que o usuário final
 *    usa, sem shortcuts.
 *
 * 5. **Asserção**:
 *    - Se `vector.expected` está preenchido, render via
 *      `formatDisplay(final, PERIOD_COMMA)`, normaliza strip-comma (igual TVM) e
 *      compara com `expected`.
 *    - Se `vector.error` está preenchido, valida `final.pendingError.code` == N
 *      extraído da string `"Error N"`.
 *    - Se `vector.stack_after` está preenchido (vetores `trans-pct-retain-*`),
 *      checa Y/Z/T do estado final — prova da idiossincrasia `%` que retém Y.
 *
 * ### Por que um `@Test` por vetor?
 *
 * Idêntico ao racional do [TvmVectorsTest]: `kotlin.test` em commonTest não tem
 * `assertAll`; uma falha em loop esconderia as seguintes. Com 34 funções, cada
 * vetor (trans-pct-001 ... trans-err-fact-frac) aparece verde/vermelho direto
 * no relatório do Gradle, apontando culpado em 1 segundo.
 *
 * ### Cobertura após este teste
 *
 * Os 34 vetores + as 18 do TVM + as 7 suítes unitárias anteriores totalizam ≈ 220
 * casos de teste cobrindo 100% das teclas da Fase 2 Bloco 1 (Transcendentais +
 * Percentagens). Erros declarados em `meta.coverage_gaps_known` do JSON não
 * entram nesta suíte e serão adicionados em blocos posteriores da Fase 2.
 */
class TranscendentalsVectorsTest {

    private val engine: CalculatorEngine = CalculatorEngine.Default

    // ═══════════════════════════════════════════════════════════════════════
    // Um @Test por vetor — nome batizado com id + gist da operação para leitura
    // rápida do relatório.
    // ═══════════════════════════════════════════════════════════════════════

    // Percent — %, %T, Δ%
    @Test fun trans_pct_001_pct_300_14()                    = runVector("trans-pct-001")
    @Test fun trans_pct_retain_002_pct_retains_Y()          = runVector("trans-pct-retain-002")
    @Test fun trans_pct_003_pct_13250_8_desconto()          = runVector("trans-pct-003")
    @Test fun trans_dpct_001_delta_pct_acoes()              = runVector("trans-dpct-001")
    @Test fun trans_pctt_001_pct_total_vendas()             = runVector("trans-pctt-001")

    // Reciprocal, square, sqrt
    @Test fun trans_recip_001_reciprocal_0_258()            = runVector("trans-recip-001")
    @Test fun trans_recip_002_reciprocal_4()                = runVector("trans-recip-002")
    @Test fun trans_square_001_square_5()                   = runVector("trans-square-001")
    @Test fun trans_sqrt_001_sqrt_9()                       = runVector("trans-sqrt-001")
    @Test fun trans_sqrt_002_sqrt_2_fix9_BCD()              = runVector("trans-sqrt-002")

    // Ln, exp
    @Test fun trans_ln_001_ln_1_igual_0()                   = runVector("trans-ln-001")
    @Test fun trans_ln_002_ln_e_igual_1()                   = runVector("trans-ln-002")
    @Test fun trans_exp_001_exp_0_igual_1()                 = runVector("trans-exp-001")
    @Test fun trans_exp_002_exp_1_igual_e()                 = runVector("trans-exp-002")

    // Factorial
    @Test fun trans_fact_001_factorial_5_igual_120()        = runVector("trans-fact-001")
    @Test fun trans_fact_002_factorial_10()                 = runVector("trans-fact-002")
    @Test fun trans_fact_003_factorial_0_igual_1()          = runVector("trans-fact-003")

    // Round, int, frac
    @Test fun trans_round_001_round_3_876_em_FIX2()         = runVector("trans-round-001")
    @Test fun trans_int_001_int_3_88()                      = runVector("trans-int-001")
    @Test fun trans_int_002_int_neg_3_88()                  = runVector("trans-int-002")
    @Test fun trans_frac_001_frac_3_88()                    = runVector("trans-frac-001")
    @Test fun trans_frac_002_frac_neg_3_88_preserva_sinal() = runVector("trans-frac-002")

    // Power (y^x)
    @Test fun trans_pow_001_2_elev_1_4()                    = runVector("trans-pow-001")
    @Test fun trans_pow_002_2_elev_menos_1_4()              = runVector("trans-pow-002")
    @Test fun trans_pow_003_neg_2_elev_3()                  = runVector("trans-pow-003")
    @Test fun trans_pow_004_raiz_cubica_2()                 = runVector("trans-pow-004")

    // Errors (8)
    @Test fun trans_err_sqrt_neg_dispara_error_0()          = runVector("trans-err-sqrt-neg")
    @Test fun trans_err_ln_zero_dispara_error_0()           = runVector("trans-err-ln-zero")
    @Test fun trans_err_ln_neg_dispara_error_0()            = runVector("trans-err-ln-neg")
    @Test fun trans_err_recip_zero_dispara_error_0()        = runVector("trans-err-recip-zero")
    @Test fun trans_err_pow_neg_frac_dispara_error_0()      = runVector("trans-err-pow-neg-frac")
    @Test fun trans_err_pow_zero_neg_dispara_error_0()      = runVector("trans-err-pow-zero-neg")
    @Test fun trans_err_fact_neg_dispara_error_5()          = runVector("trans-err-fact-neg")
    @Test fun trans_err_fact_frac_dispara_error_5()         = runVector("trans-err-fact-frac")

    // ───────── motor do teste ─────────

    private fun runVector(id: String) {
        val v = vectors.firstOrNull { it.id == id }
            ?: error("Vetor '$id' não encontrado em transcendentais-vectors.json (total: ${vectors.size}).")

        val initial: CalculatorState = CalculatorEngine.InitialState.copy(
            display = parseFormat(v.format),
            stack = stackFromList(v.stack),
        )
        val op: Event = operationToEvent(v.operation)
        val finalState: CalculatorState = engine.reduce(initial, listOf(op))

        // Ramo de erro: esperamos pendingError com o código certo.
        if (v.error != null) {
            assertErrorMatches(v, finalState)
            return
        }

        // Ramo happy: esperamos expected como string renderizada.
        assertNotNull(v.expected,
            "Vetor '${v.id}' tem schema inválido: nem `expected` nem `error`. Corrija o JSON.")
        assertNull(finalState.pendingError,
            "Vetor '${v.id}' (${v.source}): esperado sucesso mas veio erro " +
                "${finalState.pendingError} (${finalState.pendingError?.reason}).")

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

        // Checagem opcional de níveis Y/Z/T — usada pelos vetores retain-Y de `%`.
        v.stack_after?.let { expected -> assertStackAfterMatches(v, finalState, expected) }
    }

    private fun assertErrorMatches(v: TranscendentalsVectorJson, s: CalculatorState) {
        val err = s.pendingError
            ?: fail("${v.id} (${v.source}): esperado pendingError \"${v.error}\", mas state " +
                "terminou sem erro (X=${s.stack.x}). Descrição: ${v.description}")
        val expectedCode: Int = parseErrorCode(v.error!!)
            ?: fail("${v.id}: campo error \"${v.error}\" inválido (esperado \"Error N\" com N ∈ 0..9).")
        assertEquals(
            expected = expectedCode,
            actual = err.code,
            message = "${v.id} (${v.source}): esperado ${v.error}, veio Error ${err.code} " +
                "(${err.reason}). Descrição: ${v.description}",
        )
    }

    private fun assertStackAfterMatches(
        v: TranscendentalsVectorJson,
        s: CalculatorState,
        expected: Map<String, String>,
    ) {
        expected.forEach { (level, expectedStr) ->
            val actual: Hp12cDecimal = when (level) {
                "y" -> s.stack.y
                "z" -> s.stack.z
                "t" -> s.stack.t
                else -> error("Vetor '${v.id}': stack_after tem nível desconhecido '$level' " +
                    "(esperado y|z|t).")
            }
            // Comparação por valor numérico, não string: `Hp12cDecimal.equals` usa `compareTo==0`
            // (documentado no `expect class`), de modo que `of("300") == of("300.00")`. Isso
            // blinda o teste contra diferenças de escala BCD entre empilhamento e resultado.
            val expectedDecimal: Hp12cDecimal = Hp12cDecimal.of(expectedStr)
            assertEquals(
                expected = expectedDecimal,
                actual = actual,
                message = "${v.id} (${v.source}): stack_after.$level esperado " +
                    "\"$expectedStr\", veio \"$actual\". Descrição: ${v.description}",
            )
        }
    }

    // ───────── helpers ─────────

    private fun parseFormat(fmt: String): DisplayFormat {
        val parts: List<String> = fmt.split(' ')
        require(parts.size == 2) { "Formato inválido: \"$fmt\" (esperado \"FIX n\"|\"SCI n\"|\"ENG n\")" }
        val places: Int = parts[1].toInt()
        return when (parts[0]) {
            "FIX" -> DisplayFormat.Fix(places)
            "SCI" -> DisplayFormat.Sci(places)
            "ENG" -> DisplayFormat.Eng(places)
            else -> error("Modo de display desconhecido em \"$fmt\"")
        }
    }

    /**
     * Monta uma [Stack] a partir do array JSON. Convenção do schema v1.0:
     *   - Último elemento = X
     *   - Penúltimo = Y
     *   - Antepenúltimo = Z
     *   - Primeiro (se lista de 4) = T
     *   - Níveis ausentes → zero.
     *
     * Flag `stackLiftEnabled = true` (default pós-operação), `isEntering = false`
     * (não estamos digitando), `lastX = 0` (a operação preencherá via unaryOp/binaryOp).
     */
    private fun stackFromList(xs: List<String>): Stack {
        val vals: List<Hp12cDecimal> = xs.map { Hp12cDecimal.of(it) }
        // Preenchemos do topo X para baixo em direção a T, usando zero para níveis faltantes.
        val x = vals.getOrNull(vals.size - 1) ?: Hp12cDecimal.ZERO
        val y = vals.getOrNull(vals.size - 2) ?: Hp12cDecimal.ZERO
        val z = vals.getOrNull(vals.size - 3) ?: Hp12cDecimal.ZERO
        val t = vals.getOrNull(vals.size - 4) ?: Hp12cDecimal.ZERO
        return Stack(
            x = x,
            y = y,
            z = z,
            t = t,
            lastX = Hp12cDecimal.ZERO,
            stackLiftEnabled = true,
            isEntering = false,
        )
    }

    /**
     * Dispatcher `"operation"` JSON → [Event]. Os literais correspondem ao mapa
     * `conventions.operation_codes` do arquivo canônico — se alguém renomear a tecla
     * na engine, este mapa quebra o teste em vez de silenciosamente passar-sem-cobertura.
     */
    private fun operationToEvent(op: String): Event = when (op) {
        "reciprocal"    -> Event.Transcendental.Reciprocal
        "square"        -> Event.Transcendental.Square
        "sqrt"          -> Event.Transcendental.Sqrt
        "ln"            -> Event.Transcendental.Ln
        "exp"           -> Event.Transcendental.Exp
        "factorial"     -> Event.Transcendental.Factorial
        "round"         -> Event.Transcendental.Round
        "int"           -> Event.Transcendental.Integer
        "frac"          -> Event.Transcendental.Fractional
        "power"         -> Event.Transcendental.Power
        "percent"       -> Event.Percent.Of
        "delta_percent" -> Event.Percent.Delta
        "percent_total" -> Event.Percent.OfTotal
        else -> error("Operação JSON desconhecida: \"$op\" — atualizar o dispatcher " +
            "`operationToEvent` ou conferir o JSON canônico.")
    }

    /** Extrai `N` de `"Error N"`. Retorna `null` se o formato não bater (contrato do schema v1.0). */
    private fun parseErrorCode(raw: String): Int? {
        val prefix = "Error "
        if (!raw.startsWith(prefix)) return null
        return raw.substring(prefix.length).trim().toIntOrNull()
    }

    companion object {
        /**
         * Cache do JSON carregado (lazy) e compartilhado entre os 34 `@Test`. Reutiliza o
         * `VectorsJson` (com `ignoreUnknownKeys = true`) já definido em `TvmVectorJson.kt`.
         */
        private val file: TranscendentalsVectorsFile by lazy {
            val raw: String = readTestResource("test-vectors/transcendentais-vectors.json")
            VectorsJson.decodeFromString(TranscendentalsVectorsFile.serializer(), raw)
        }

        private val vectors: List<TranscendentalsVectorJson> get() = file.vectors
    }
}
