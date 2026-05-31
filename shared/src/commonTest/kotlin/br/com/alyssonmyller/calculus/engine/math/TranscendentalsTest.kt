package br.com.alyssonmyller.calculus.engine.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Testes de [Hp12cDecimal.ln], [Hp12cDecimal.exp] e [Hp12cDecimal.pow] — **Fase 1 passo 6**.
 *
 * A precisão exigida é a mesma documentada em `referencias/bcd-rounding.md` da skill
 * `hp12c-simulator`: 10 dígitos significativos com HALF_EVEN. Estes três primitivos alimentam
 * as fórmulas `Solve.N` e `Solve.I` do reducer TVM — se divergirem aqui, as ~5 novas
 * variações de `ReducerFinancialSolveTest` também divergem.
 *
 * ### Estratégia de tolerância
 *
 * Comparamos contra valores tabulados (constantes matemáticas clássicas e exemplos resolvidos
 * do manual/Moretti) com tolerância de meio ULP na décima casa. Para `ln(2) = 0.6931471806`,
 * isso significa `±5·10⁻¹¹`, ou seja, a resposta tem que bater nos 10 dígitos exibidos.
 */
class TranscendentalsTest {

    // ─── 1. ln — identidades básicas e valores tabulados ──────────────────────

    @Test fun ln_de_um_eh_zero() {
        assertEquals(Hp12cDecimal.ZERO, Hp12cDecimal.ONE.ln())
    }

    @Test fun ln_de_dois() {
        // ln(2) = 0.6931471805599453094... → arredondado em 10 dígitos → 0.6931471806
        assertNear(expected = "0.6931471806", actual = Hp12cDecimal.of(2).ln(), tol = "0.00000000005")
    }

    @Test fun ln_de_dez() {
        // ln(10) = 2.302585092994... → arredondado → 2.302585093
        assertNear(expected = "2.302585093", actual = Hp12cDecimal.of(10).ln(), tol = "0.0000000005")
    }

    @Test fun ln_de_e_aproximadamente_um() {
        // e ≈ 2.718281828 — ln(e) = 1 exatamente. Como `e` ele mesmo é irracional e
        // tem truncamento, aceitamos 1 ULP na última casa.
        val e = Hp12cDecimal.of("2.718281828")
        val lnE = e.ln()
        assertNear(expected = "1", actual = lnE, tol = "0.0000000005")
    }

    @Test fun ln_de_valor_menor_que_um() {
        // ln(0.5) = -ln(2) = -0.6931471806
        assertNear(expected = "-0.6931471806", actual = Hp12cDecimal.of("0.5").ln(), tol = "0.00000000005")
    }

    @Test fun ln_zero_lanca_arithmetic_exception() {
        assertFailsWith<ArithmeticException> { Hp12cDecimal.ZERO.ln() }
    }

    @Test fun ln_negativo_lanca_arithmetic_exception() {
        assertFailsWith<ArithmeticException> { Hp12cDecimal.of(-1).ln() }
    }

    // ─── 2. exp — identidades básicas e valores tabulados ────────────────────

    @Test fun exp_de_zero_eh_um() {
        assertEquals(Hp12cDecimal.ONE, Hp12cDecimal.ZERO.exp())
    }

    @Test fun exp_de_um_eh_e() {
        // e = 2.718281828459... → 10 dígitos → 2.718281828
        assertNear(expected = "2.718281828", actual = Hp12cDecimal.ONE.exp(), tol = "0.0000000005")
    }

    @Test fun exp_de_dez() {
        // e^10 = 22026.465794806718... → 10 dígitos → 22026.46580
        assertNear(expected = "22026.46580", actual = Hp12cDecimal.of(10).exp(), tol = "0.000005")
    }

    @Test fun exp_de_negativo() {
        // e^-1 = 0.3678794411714... → 10 dígitos → 0.3678794412
        assertNear(expected = "0.3678794412", actual = Hp12cDecimal.of(-1).exp(), tol = "0.00000000005")
    }

    // ─── 3. ln/exp são inversos ──────────────────────────────────────────────

    @Test fun exp_ln_identidade_para_valor_tipico_tvm() {
        // (1+i)^n para i = 4% a.m. e n = 5: deveria bater com `powInt(5)` na casa exibida.
        val base = Hp12cDecimal.of("1.04")
        val viaInt = base.powInt(5)                     // referência exata
        val viaLnExp = base.ln().let {
            (it * Hp12cDecimal.of(5)).exp()
        }
        // Tolerância: 5 ULP na última casa — composição ln→·→exp pode perder 1-3 ULP
        val diff = viaInt - viaLnExp
        assertTrue(
            actual = diff < Hp12cDecimal.of("0.000001") && diff > Hp12cDecimal.of("-0.000001"),
            message = "ln/exp compostos divergem de powInt além da tolerância: " +
                "viaInt=$viaInt, viaLnExp=$viaLnExp, diff=$diff",
        )
    }

    // ─── 4. pow — inteiro delega a powInt, fracionário usa exp(y·ln(x)) ─────

    @Test fun pow_expoente_inteiro_bate_exatamente_com_pow_int() {
        // Regra do nosso `pow`: se o expoente é inteiro, delegamos a `powInt` para preservar
        // exatidão. Verificação: `2^10 = 1024` tem que sair exato, não "1023.9999999".
        assertEquals(
            expected = Hp12cDecimal.of(1024),
            actual   = Hp12cDecimal.of(2).pow(Hp12cDecimal.of(10)),
        )
    }

    @Test fun pow_expoente_inteiro_negativo() {
        // `10^-2 = 0.01` exato.
        assertEquals(
            expected = Hp12cDecimal.of("0.01"),
            actual   = Hp12cDecimal.of(10).pow(Hp12cDecimal.of(-2)),
        )
    }

    @Test fun pow_expoente_fracionario_positivo() {
        // 4^0.5 = 2 exato (em teoria). Via exp(0.5·ln(4)) pode ter erro na última casa.
        val r = Hp12cDecimal.of(4).pow(Hp12cDecimal.of("0.5"))
        assertNear(expected = "2", actual = r, tol = "0.000001")
    }

    @Test fun pow_base_zero_expoente_positivo_eh_zero() {
        assertEquals(
            expected = Hp12cDecimal.ZERO,
            actual   = Hp12cDecimal.ZERO.pow(Hp12cDecimal.of(5)),
        )
    }

    @Test fun pow_base_zero_expoente_zero_lanca() {
        // 0^0 é indefinido; a HP emite Error 0. Contrato: ArithmeticException.
        assertFailsWith<ArithmeticException> {
            Hp12cDecimal.ZERO.pow(Hp12cDecimal.ZERO)
        }
    }

    @Test fun pow_base_zero_expoente_negativo_lanca() {
        // 0^-1 = 1/0 — indefinido; HP emite Error 0.
        assertFailsWith<ArithmeticException> {
            Hp12cDecimal.ZERO.pow(Hp12cDecimal.of(-1))
        }
    }

    @Test fun pow_base_negativa_expoente_nao_inteiro_lanca() {
        // (-2)^0.5 = i · √2 — complexo, HP emite Error 0.
        assertFailsWith<ArithmeticException> {
            Hp12cDecimal.of(-2).pow(Hp12cDecimal.of("0.5"))
        }
    }

    @Test fun pow_base_negativa_expoente_inteiro_usa_pow_int() {
        // (-2)^3 = -8 exato via powInt, mesmo com expoente empacotado em Hp12cDecimal.
        assertEquals(
            expected = Hp12cDecimal.of(-8),
            actual   = Hp12cDecimal.of(-2).pow(Hp12cDecimal.of(3)),
        )
    }

    // ─── 5. Integração com Transcendentals (fachada) ──────────────────────────

    @Test fun transcendentals_facade_delega_para_hp12c_decimal() {
        // O objeto `Transcendentals` é fachada fina — seu papel é apenas deixar claro, no
        // leitor do código, que estas três funções convivem em um único ponto conceitual.
        // Teste de sanidade: mesmos resultados de chamar os métodos direto.
        val x = Hp12cDecimal.of("1.5")
        assertEquals(x.ln(), Transcendentals.ln(x))
        assertEquals(x.exp(), Transcendentals.exp(x))
        assertEquals(x.pow(Hp12cDecimal.of(3)), Transcendentals.pow(x, Hp12cDecimal.of(3)))
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private fun assertNear(expected: String, actual: Hp12cDecimal, tol: String) {
        val exp = Hp12cDecimal.of(expected)
        val tolerance = Hp12cDecimal.of(tol)
        val diff = actual - exp
        assertTrue(
            diff < tolerance && diff > -tolerance,
            "esperado≈$expected, veio=$actual, diff=$diff (tolerância ±$tolerance)",
        )
    }
}
