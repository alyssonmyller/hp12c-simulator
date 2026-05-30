package br.com.alyssonmyller.calculus.engine.format

import br.com.alyssonmyller.calculus.engine.error.Hp12cError
import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import br.com.alyssonmyller.calculus.engine.state.DisplayFormat
import br.com.alyssonmyller.calculus.engine.state.NumericSeparator
import br.com.alyssonmyller.calculus.engine.state.Stack
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testes do [DisplayFormatter]. Organizados em 10 blocos, cada bloco cobrindo um contrato
 * específico descrito em `referencias/bcd-rounding.md` e na Seção 5 do manual
 * `bpia5314.pdf` (Formatos de apresentação de números).
 *
 * Cada teste monta um `CalculatorState` mínimo, com `Stack(x = ...)` e a config de `display`
 * desejada, e checa a string renderizada. Usar "_" em nomes pra facilitar leitura do grep.
 *
 * Convenções:
 *   - `en-US` = `NumericSeparator.PERIOD_COMMA` (vírgula milhar, ponto decimal).
 *   - `pt-BR` = `NumericSeparator.COMMA_PERIOD` (ponto milhar, vírgula decimal).
 *   - Toda `Stack` sem pilha de entrada explícita usa `isEntering = false` (default).
 */
class DisplayFormatterTest {

    // ───────── helpers ─────────

    private fun stateOf(
        x: String,
        display: DisplayFormat = DisplayFormat.Default,
        isEntering: Boolean = false,
        entryBuffer: String? = null,
        pendingError: Hp12cError? = null,
    ): CalculatorState = CalculatorState(
        stack = Stack(x = Hp12cDecimal.of(x), isEntering = isEntering),
        display = display,
        entryBuffer = entryBuffer,
        pendingError = pendingError,
    )

    private fun render(
        state: CalculatorState,
        sep: NumericSeparator = NumericSeparator.COMMA_PERIOD,
    ): String = DisplayFormatter.format(state, sep)

    // ═══════════════════════════════════════════════════════════════════════
    // Bloco 1 — Error absorve tudo (precedência máxima)
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun bloco1_erro_pendente_vira_Error_com_codigo_mesmo_com_entrada_em_curso() {
        val s = CalculatorState(
            stack = Stack(x = Hp12cDecimal.of("42"), isEntering = true),
            entryBuffer = "42",
            pendingError = Hp12cError.DivisionByZero,
        )
        assertEquals("Error 0", render(s))
    }

    @Test fun bloco1_cada_codigo_renderiza_seu_numero() {
        // Um representante por código (0..9)
        val representantes: List<Pair<Hp12cError, Int>> = listOf(
            Hp12cError.DivisionByZero      to 0,
            Hp12cError.RegisterNotFound    to 1,
            Hp12cError.StatisticsUnderflow to 2,
            Hp12cError.IrrNoConverge       to 3,
            Hp12cError.ProgramOverflow     to 4,
            Hp12cError.TvmNoConverge       to 5,
            Hp12cError.FinancialUninit     to 6,
            Hp12cError.CashflowEmpty       to 7,
            Hp12cError.InvalidDate         to 8,
            Hp12cError.SelfTestFailure     to 9,
        )
        for ((err, code) in representantes) {
            assertEquals(
                expected = "Error $code",
                actual = render(stateOf(x = "0", pendingError = err)),
                message = "erro $err (código=${err.code}) esperado \"Error $code\"",
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bloco 2 — Buffer de entrada espelhado no visor
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun bloco2_buffer_inteiro_curto_sem_milhar() {
        val s = stateOf(x = "0", isEntering = true, entryBuffer = "42")
        assertEquals("42", render(s, NumericSeparator.PERIOD_COMMA))
        assertEquals("42", render(s, NumericSeparator.COMMA_PERIOD))
    }

    @Test fun bloco2_buffer_com_4_digitos_ganha_milhar_em_cada_locale() {
        val s = stateOf(x = "0", isEntering = true, entryBuffer = "9987")
        assertEquals("9,987", render(s, NumericSeparator.PERIOD_COMMA))
        assertEquals("9.987", render(s, NumericSeparator.COMMA_PERIOD))
    }

    @Test fun bloco2_buffer_com_ponto_pendurado_preserva() {
        val s = stateOf(x = "0", isEntering = true, entryBuffer = "5.")
        assertEquals("5.", render(s, NumericSeparator.PERIOD_COMMA))
        assertEquals("5,", render(s, NumericSeparator.COMMA_PERIOD))
    }

    @Test fun bloco2_buffer_negativo_CHS() {
        val s = stateOf(x = "0", isEntering = true, entryBuffer = "-5000")
        assertEquals("-5,000", render(s, NumericSeparator.PERIOD_COMMA))
        assertEquals("-5.000", render(s, NumericSeparator.COMMA_PERIOD))
    }

    @Test fun bloco2_buffer_com_EEX_vazio_mostra_expoente_00() {
        val s = stateOf(x = "0", isEntering = true, entryBuffer = "1E")
        assertEquals("1 00", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco2_buffer_EEX_com_expoente_negativo() {
        val s = stateOf(x = "0", isEntering = true, entryBuffer = "1.5E-10")
        assertEquals("1.5-10", render(s, NumericSeparator.PERIOD_COMMA))
        assertEquals("1,5-10", render(s, NumericSeparator.COMMA_PERIOD))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bloco 3 — FIX n com n variando (manual p. 72)
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun bloco3_FIX_0_com_valor_14_87_arredonda_para_15() {
        // HP mostra "15," com vírgula presa no fim. Nós mostramos "15" limpo.
        val s = stateOf(x = "14.87", display = DisplayFormat.Fix(0))
        assertEquals("15", render(s, NumericSeparator.COMMA_PERIOD))
        assertEquals("15", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco3_FIX_1_com_14_87456_arredonda_para_14_9() {
        val s = stateOf(x = "14.87456", display = DisplayFormat.Fix(1))
        assertEquals("14,9", render(s, NumericSeparator.COMMA_PERIOD))
        assertEquals("14.9", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco3_FIX_2_do_manual_14_87() {
        val s = stateOf(x = "14.87456320", display = DisplayFormat.Fix(2))
        assertEquals("14,87", render(s, NumericSeparator.COMMA_PERIOD))
        assertEquals("14.87", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco3_FIX_4_truncado_para_4_casas() {
        val s = stateOf(x = "14.87456320", display = DisplayFormat.Fix(4))
        assertEquals("14,8746", render(s, NumericSeparator.COMMA_PERIOD))
    }

    @Test fun bloco3_FIX_9_cap_em_10_digitos_totais_manual_p72() {
        // Manual p. 72: com `14.87456320` em FIX 9, HP mostra `14,87456320` (8 casas), pois
        // 2 int + 9 frac = 11 estoura o visor de 10. HP reduz casas decimais em vez de ir pra SCI.
        val s = stateOf(x = "14.87456320", display = DisplayFormat.Fix(9))
        assertEquals("14.87456320", render(s, NumericSeparator.PERIOD_COMMA))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bloco 4 — Separadores pt-BR e en-US no mesmo valor
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun bloco4_valor_grande_com_milhar_em_cada_locale() {
        val s = stateOf(x = "1234567.89", display = DisplayFormat.Fix(2))
        assertEquals("1,234,567.89", render(s, NumericSeparator.PERIOD_COMMA))
        assertEquals("1.234.567,89", render(s, NumericSeparator.COMMA_PERIOD))
    }

    @Test fun bloco4_valor_negativo_grande() {
        val s = stateOf(x = "-429000", display = DisplayFormat.Fix(2))
        assertEquals("-429,000.00", render(s, NumericSeparator.PERIOD_COMMA))
        assertEquals("-429.000,00", render(s, NumericSeparator.COMMA_PERIOD))
    }

    @Test fun bloco4_valor_sem_milhar_ainda_assim_usa_ponto_decimal_correto() {
        val s = stateOf(x = "42", display = DisplayFormat.Fix(2))
        assertEquals("42.00", render(s, NumericSeparator.PERIOD_COMMA))
        assertEquals("42,00", render(s, NumericSeparator.COMMA_PERIOD))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bloco 5 — Degradação FIX → SCI
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun bloco5_FIX_2_com_valor_10bi_degrada_para_SCI() {
        // 10^10 = 10000000000 — parte inteira tem 11 dígitos, não cabe em 10-2 = 8.
        val s = stateOf(x = "10000000000", display = DisplayFormat.Fix(2))
        val out = render(s, NumericSeparator.PERIOD_COMMA)
        // Mantissa arredondada a 3 sig dig (FIX 2 → SCI 2 via saturação): "1.00 10"
        assertEquals("1.00 10", out)
    }

    @Test fun bloco5_FIX_0_com_valor_que_cabe_exatamente_nao_degrada() {
        // 9999999999 tem 10 dígitos — cabe em FIX 0 (max int digits = 10).
        val s = stateOf(x = "9999999999", display = DisplayFormat.Fix(0))
        assertEquals("9,999,999,999", render(s, NumericSeparator.PERIOD_COMMA))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bloco 6 — SCI n
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun bloco6_SCI_2_numero_pequeno_positivo() {
        val s = stateOf(x = "123.456", display = DisplayFormat.Sci(2))
        assertEquals("1.23 02", render(s, NumericSeparator.PERIOD_COMMA))
        assertEquals("1,23 02", render(s, NumericSeparator.COMMA_PERIOD))
    }

    @Test fun bloco6_SCI_2_numero_pequeno_negativo() {
        val s = stateOf(x = "-123.456", display = DisplayFormat.Sci(2))
        assertEquals("-1.23 02", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco6_SCI_2_numero_muito_pequeno_expoente_negativo() {
        val s = stateOf(x = "0.00042", display = DisplayFormat.Sci(2))
        assertEquals("4.20-04", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco6_SCI_com_zero() {
        val s = stateOf(x = "0", display = DisplayFormat.Sci(2))
        assertEquals("0.00 00", render(s, NumericSeparator.PERIOD_COMMA))
        assertEquals("0,00 00", render(s, NumericSeparator.COMMA_PERIOD))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bloco 7 — ENG n (expoente múltiplo de 3)
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun bloco7_ENG_2_valor_medio() {
        // 1.234 × 10^3 (SCI 3) → 1.23 × 10^3 (ENG 2, exp já é múltiplo de 3)
        val s = stateOf(x = "1234", display = DisplayFormat.Eng(2))
        assertEquals("1.23 03", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco7_ENG_2_valor_que_ocupa_dois_digitos_antes_do_ponto() {
        // 12.345 × 10^3 (exp SCI = 4, ENG baixa pra 3 e mantissa vira 12.35 arred)
        val s = stateOf(x = "12345", display = DisplayFormat.Eng(2))
        assertEquals("12.3 03", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco7_ENG_2_valor_que_ocupa_tres_digitos_antes_do_ponto() {
        // 123.45 × 10^3 (exp SCI = 5, ENG baixa pra 3 e mantissa fica 123 arred)
        val s = stateOf(x = "123450", display = DisplayFormat.Eng(2))
        assertEquals("123 03", render(s, NumericSeparator.PERIOD_COMMA))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bloco 8 — Vetor canônico tvm-001 ponta a ponta
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun bloco8_tvm_001_FV_6083_26_em_en_US() {
        // Após o Solve.Fv, x = 6083.264512 (exato via powInt).
        val s = stateOf(x = "6083.264512", display = DisplayFormat.Fix(2))
        // HP fiel em en-US: parte inteira agrupa em milhar.
        assertEquals("6,083.26", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco8_tvm_001_FV_6083_26_em_pt_BR() {
        val s = stateOf(x = "6083.264512", display = DisplayFormat.Fix(2))
        assertEquals("6.083,26", render(s, NumericSeparator.COMMA_PERIOD))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bloco 9 — Pós-Solve.N com teto renderiza como inteiro FIX 0
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun bloco9_solve_N_teto_14_em_FIX_0() {
        // tvm-003: ceil(13.36194504) = 14; HP mostra "14".
        val s = stateOf(x = "14", display = DisplayFormat.Fix(0))
        assertEquals("14", render(s, NumericSeparator.COMMA_PERIOD))
    }

    @Test fun bloco9_solve_N_teto_em_FIX_2() {
        // Mesmo valor, com FIX 2 → "14,00" (pt-BR) / "14.00" (en-US).
        val s = stateOf(x = "14", display = DisplayFormat.Fix(2))
        assertEquals("14,00", render(s, NumericSeparator.COMMA_PERIOD))
        assertEquals("14.00", render(s, NumericSeparator.PERIOD_COMMA))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bloco 10 — HALF_EVEN (banker's) em empates
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun bloco10_half_even_tie_com_par_trunca() {
        // 2.5 → 2 (par é "vizinho par" do 2.5)
        val s = stateOf(x = "2.5", display = DisplayFormat.Fix(0))
        assertEquals("2", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco10_half_even_tie_com_impar_sobe() {
        // 3.5 → 4 (par é "vizinho par" do 3.5)
        val s = stateOf(x = "3.5", display = DisplayFormat.Fix(0))
        assertEquals("4", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco10_half_even_1_005_fix_2_trunca() {
        // 1.005 → 1.00 (último retido é 0, par, trunca)
        val s = stateOf(x = "1.005", display = DisplayFormat.Fix(2))
        assertEquals("1.00", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco10_half_even_1_015_fix_2_sobe() {
        // 1.015 → 1.02 (último retido é 1, ímpar, soma 1)
        val s = stateOf(x = "1.015", display = DisplayFormat.Fix(2))
        assertEquals("1.02", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco10_half_up_claro_1_006_fix_2() {
        // 1.006 → 1.01 (não é empate, 6 > 5)
        val s = stateOf(x = "1.006", display = DisplayFormat.Fix(2))
        assertEquals("1.01", render(s, NumericSeparator.PERIOD_COMMA))
    }

    @Test fun bloco10_carry_em_arredondamento_9_99_fix_1() {
        // 9.99 → 10.0 (carry propaga por toda a parte inteira)
        val s = stateOf(x = "9.99", display = DisplayFormat.Fix(1))
        assertEquals("10.0", render(s, NumericSeparator.PERIOD_COMMA))
    }
}
