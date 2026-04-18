package com.arcom.hp12c.engine.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Testes de [Hp12cDecimal] — a camada numérica fundamental. Se algo aqui quebra, toda a
 * engine quebra. Cobre:
 *
 *   1. Aritmética básica exata (4 operações sem arredondamento)
 *   2. Precisão a 10 dígitos com HALF_EVEN (banker's rounding)
 *   3. Potenciação com expoente inteiro (`powInt`)
 *   4. `isZero`, `compareTo`, `equals`/`hashCode` por valor numérico
 *   5. Divisão por zero lança `ArithmeticException` (é o reducer que converte para `Hp12cError`)
 *
 * Os testes rodam em `:shared:jvmTest`. O alvo iOS ainda é stub — quando ganhar impl real na
 * Fase 4, estes mesmos testes devem passar sem alteração (é o ponto do `expect class`).
 */
class Hp12cDecimalTest {

    // --- 1. Aritmética básica exata ---

    @Test fun soma_exata() {
        assertEquals(Hp12cDecimal.of(5), Hp12cDecimal.of(2) + Hp12cDecimal.of(3))
    }

    @Test fun subtracao_exata() {
        assertEquals(Hp12cDecimal.of(3), Hp12cDecimal.of(10) - Hp12cDecimal.of(7))
    }

    @Test fun multiplicacao_exata() {
        assertEquals(Hp12cDecimal.of(42), Hp12cDecimal.of(6) * Hp12cDecimal.of(7))
    }

    @Test fun divisao_exata() {
        assertEquals(Hp12cDecimal.of(5), Hp12cDecimal.of(15) / Hp12cDecimal.of(3))
    }

    @Test fun negacao_unaria() {
        assertEquals(Hp12cDecimal.of(-7), -Hp12cDecimal.of(7))
        assertEquals(Hp12cDecimal.ZERO, -Hp12cDecimal.ZERO)
    }

    // --- 2. Precisão 10 dígitos com HALF_EVEN ---

    @Test fun divisao_1_sobre_3_trunca_em_10_digitos() {
        // 1/3 = 0.333333333333... — 11º dígito é 3 (< 5) → trunca → 10 treses
        assertEquals(
            expected = Hp12cDecimal.of("0.3333333333"),
            actual   = Hp12cDecimal.of(1) / Hp12cDecimal.of(3),
        )
    }

    @Test fun divisao_2_sobre_3_arredonda_para_cima_em_10_digitos() {
        // 2/3 = 0.666666666666... — 11º dígito é 6 (> 5) → arredonda para cima
        assertEquals(
            expected = Hp12cDecimal.of("0.6666666667"),
            actual   = Hp12cDecimal.of(2) / Hp12cDecimal.of(3),
        )
    }

    @Test fun divisao_1_sobre_7_arredondamento_composto() {
        // 1/7 = 0.1428571428 | 571428... — 11º dígito é 5 seguido de 7, ou seja, > meio →
        // arredonda para cima → 0.1428571429
        assertEquals(
            expected = Hp12cDecimal.of("0.1428571429"),
            actual   = Hp12cDecimal.of(1) / Hp12cDecimal.of(7),
        )
    }

    @Test fun half_even_com_tie_em_digito_par_mantem() {
        // "12.345678905" tem 11 dígitos significativos. 11º é 5 exato; 10º é 0 (par) → mantém.
        // Resultado: 12.34567890 (scale preservada).
        assertEquals(
            expected = Hp12cDecimal.of("12.34567890"),
            actual   = Hp12cDecimal.of("12.345678905"),
        )
    }

    @Test fun half_even_com_tie_em_digito_impar_arredonda_para_cima() {
        // "12.345678915" — 11º é 5 exato; 10º é 1 (ímpar) → arredonda para cima → 12.34567892
        assertEquals(
            expected = Hp12cDecimal.of("12.34567892"),
            actual   = Hp12cDecimal.of("12.345678915"),
        )
    }

    // --- 3. powInt ---

    @Test fun pow_int_expoente_positivo() {
        assertEquals(Hp12cDecimal.of(1024), Hp12cDecimal.of(2).powInt(10))
    }

    @Test fun pow_int_expoente_zero_retorna_um() {
        assertEquals(Hp12cDecimal.ONE, Hp12cDecimal.of(7).powInt(0))
    }

    @Test fun pow_int_expoente_negativo() {
        // 10^-2 = 0.01
        assertEquals(Hp12cDecimal.of("0.01"), Hp12cDecimal.of(10).powInt(-2))
    }

    @Test fun pow_int_financeiro_HP_manual() {
        // (1,01)^360 — sanity check pra TVM de tvm-017 (100k × 1,01^360 = 3.594.964,13).
        // Esperado analítico: 35,9496413... → arredondado a 10 dígitos → 35,94964132
        val um_mais_i = Hp12cDecimal.of("1.01")
        val resultado = um_mais_i.powInt(360)
        // Comparamos multiplicado por 100.000 pra ficar mais legível e bater com o vetor:
        val fv = Hp12cDecimal.of(100_000) * resultado
        // Precisão de 10 dígitos: 3594964,132 — mas como o nosso MC arredonda em 10 significativos,
        // a multiplicação pode ter perdido casas. Usamos tolerância via truncamento final:
        // o vetor tvm-017 espera "3594964.13" em FIX 2; nosso `toString` dá valor "cru".
        assertTrue(
            actual = fv.toString().startsWith("3594964.13"),
            message = "fv = \"$fv\" — esperado prefixo \"3594964.13\"",
        )
    }

    // --- 4. Predicados e igualdade ---

    @Test fun is_zero() {
        assertTrue(Hp12cDecimal.ZERO.isZero())
        assertTrue(Hp12cDecimal.of("0").isZero())
        assertTrue(Hp12cDecimal.of("0.0").isZero())
        assertTrue(Hp12cDecimal.of("-0").isZero())
        assertFalse(Hp12cDecimal.ONE.isZero())
        assertFalse(Hp12cDecimal.of("-1").isZero())
    }

    @Test fun compare_to() {
        assertTrue(Hp12cDecimal.of(5) > Hp12cDecimal.of(3))
        assertTrue(Hp12cDecimal.of(-1) < Hp12cDecimal.ZERO)
        assertEquals(0, Hp12cDecimal.of(5).compareTo(Hp12cDecimal.of(5)))
        assertEquals(0, Hp12cDecimal.of("1.00").compareTo(Hp12cDecimal.of("1.0")))
    }

    @Test fun equals_por_valor_numerico_ignora_escala() {
        // Divergência proposital com java.math.BigDecimal.equals (que compara escala).
        // Para o usuário da HP, 1,00 e 1,0 são o mesmo número.
        assertEquals(Hp12cDecimal.of("1.00"), Hp12cDecimal.of("1.0"))
        assertEquals(Hp12cDecimal.of("100"),  Hp12cDecimal.of("1.00E+2"))
        assertNotEquals(Hp12cDecimal.of("1.0"), Hp12cDecimal.of("1.1"))
    }

    @Test fun hash_code_consistente_com_equals() {
        val a = Hp12cDecimal.of("1.00")
        val b = Hp12cDecimal.of("1.0")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test fun to_string_sem_notacao_cientifica() {
        assertEquals("1", Hp12cDecimal.of(1).toString())
        assertEquals("-3.14", Hp12cDecimal.of("-3.14").toString())
    }

    // --- 5. Divisão por zero ---

    @Test fun divisao_por_zero_lanca_arithmetic_exception() {
        // Contrato: o reducer captura e converte para Hp12cError.DivisionByZero.
        assertFailsWith<ArithmeticException> {
            Hp12cDecimal.of(1) / Hp12cDecimal.ZERO
        }
    }
}
