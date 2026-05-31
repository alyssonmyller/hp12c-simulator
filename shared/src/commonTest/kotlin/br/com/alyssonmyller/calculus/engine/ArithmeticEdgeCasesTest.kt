package br.com.alyssonmyller.calculus.engine

import br.com.alyssonmyller.calculus.engine.error.Hp12cError
import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes de casos de borda da aritmética: operações encadeadas, EEX, CHS, overflow,
 * e comportamento de pilha em sequências complexas não cobertas pelo ReducerTest básico.
 */
class ArithmeticEdgeCasesTest {

    private val engine  = CalculatorEngine.Default
    private val initial = CalculatorEngine.InitialState

    private fun run(vararg events: Event): CalculatorState =
        engine.reduce(initial, events.toList())

    private fun run(state: CalculatorState, vararg events: Event): CalculatorState =
        engine.reduce(state, events.toList())

    private fun number(s: String): List<Event> = buildList {
        var s2 = s
        val negate = s2.startsWith("-").also { if (it) s2 = s2.substring(1) }
        for (ch in s2) {
            add(when (ch) {
                '.' -> Event.Entry.DecimalPoint
                in '0'..'9' -> Event.Entry.Digit(ch.digitToInt())
                else -> error("dígito inválido: '$ch'")
            })
        }
        if (negate) add(Event.Entry.ChangeSign)
    }

    private fun d(s: String) = Hp12cDecimal.of(s)
    private fun d(n: Int)    = Hp12cDecimal.of(n)

    // ─── 1. OPERAÇÕES ENCADEADAS ────────────────────────────────────────────────

    @Test fun soma_em_cadeia_de_cinco_numeros_usando_t_sticky() {
        // 1+2+3+4+5 = 15. Após empilhar 1,2,3,4,5 e somar encadeado.
        // Forma HP: 1 ENTER 2 + 3 + 4 + 5 +
        val s = run(
            *number("1").toTypedArray(), Event.StackOp.Enter,
            *number("2").toTypedArray(), Event.Arith.Add,    // 3
            *number("3").toTypedArray(), Event.Arith.Add,    // 6
            *number("4").toTypedArray(), Event.Arith.Add,    // 10
            *number("5").toTypedArray(), Event.Arith.Add,    // 15
        )
        assertEquals(d(15), s.stack.x)
    }

    @Test fun formula_classica_rpn_3_enter_4_mais_5_enter_2_menos_vezes() {
        // ((3+4) × (5-2)) = 7 × 3 = 21
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Arith.Add,         // X=7
            *number("5").toTypedArray(), Event.StackOp.Enter,
            *number("2").toTypedArray(), Event.Arith.Subtract,    // X=3, Y=7
            Event.Arith.Multiply,                                  // X=21
        )
        assertEquals(d(21), s.stack.x)
    }

    @Test fun divisao_exata_e_precisa() {
        // 1 ÷ 3 = 0.3333333333 (10 dígitos BCD)
        val s = run(
            *number("1").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.Arith.Divide,
        )
        // Deve ter ~10 dígitos de precisão; não deve ser 0.333 truncado
        val result = s.stack.x.toString()
        assertTrue(result.startsWith("0.333333333"), "precisão BCD 10 dígitos: $result")
    }

    @Test fun subtracao_produce_negativo() {
        // 3 - 7 = -4
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("7").toTypedArray(), Event.Arith.Subtract,
        )
        assertEquals(d("-4"), s.stack.x)
    }

    @Test fun produto_de_negativos_e_positivo() {
        // -3 × -4 = 12
        val s = run(
            *number("-3").toTypedArray(), Event.StackOp.Enter,
            *number("-4").toTypedArray(), Event.Arith.Multiply,
        )
        assertEquals(d(12), s.stack.x)
    }

    @Test fun soma_com_neutro_additive_zero_nao_altera_x() {
        val s = run(
            *number("42").toTypedArray(), Event.StackOp.Enter,
            *number("0").toTypedArray(),  Event.Arith.Add,
        )
        assertEquals(d(42), s.stack.x)
    }

    @Test fun multiplicacao_por_um_nao_altera_x() {
        val s = run(
            *number("42").toTypedArray(), Event.StackOp.Enter,
            *number("1").toTypedArray(),  Event.Arith.Multiply,
        )
        assertEquals(d(42), s.stack.x)
    }

    @Test fun divisao_de_mesmo_numero_da_um() {
        val s = run(
            *number("42").toTypedArray(), Event.StackOp.Enter,
            *number("42").toTypedArray(), Event.Arith.Divide,
        )
        assertEquals(d(1), s.stack.x)
    }

    @Test fun negate_fora_de_entrada_inverte_sinal_e_atualiza_lastx() {
        // 5 [arith negate] → X=-5, lastX=5
        val s = run(
            *number("5").toTypedArray(), Event.StackOp.Enter,
            Event.Arith.Negate,
        )
        assertEquals(d("-5"), s.stack.x, "X=-5")
        assertEquals(d(5),    s.stack.lastX, "lastX=5 (antes da negação)")
    }

    @Test fun negate_duplo_restaura_valor() {
        val s = run(
            *number("7").toTypedArray(), Event.StackOp.Enter,
            Event.Arith.Negate,
            Event.Arith.Negate,
        )
        assertEquals(d(7), s.stack.x, "dupla negação restaura 7")
    }

    // ─── 2. CHS (CHANGE SIGN) DURANTE ENTRADA ──────────────────────────────────

    @Test fun chs_durante_entrada_inverte_numero_positivo() {
        val s = run(
            *number("15").toTypedArray(),
            Event.Entry.ChangeSign,
            Event.StackOp.Enter,
        )
        assertEquals(d("-15"), s.stack.x)
        assertEquals(d("-15"), s.stack.y)
    }

    @Test fun chs_duplo_durante_entrada_restaura_positivo() {
        val s = run(
            *number("15").toTypedArray(),
            Event.Entry.ChangeSign,
            Event.Entry.ChangeSign,
            Event.StackOp.Enter,
        )
        assertEquals(d(15), s.stack.x)
    }

    @Test fun chs_no_zero_durante_entrada_nao_produz_menos_zero() {
        // -0 deve ser tratado como 0
        val s = run(
            Event.Entry.Digit(0),
            Event.Entry.ChangeSign,
            Event.StackOp.Enter,
        )
        // A HP física não exibe -0; o engine deve retornar 0
        val display = engine.formatDisplay(
            s, br.com.alyssonmyller.calculus.engine.state.NumericSeparator.PERIOD_COMMA
        )
        // "0.00" ou similar, não "-0.00"
        assertTrue(!display.startsWith("-"), "display não deve mostrar sinal negativo para -0: $display")
    }

    // ─── 3. EEX (NOTAÇÃO CIENTÍFICA NA ENTRADA) ─────────────────────────────────

    @Test fun eex_basico_1e3_igual_a_1000() {
        val s = run(
            Event.Entry.Digit(1),
            Event.Entry.Eex,
            Event.Entry.Digit(3),
            Event.StackOp.Enter,
        )
        assertEquals(d(1000), s.stack.x)
    }

    @Test fun eex_com_mantissa_fracionaria_1_5e2_igual_a_150() {
        val s = run(
            Event.Entry.Digit(1), Event.Entry.DecimalPoint, Event.Entry.Digit(5),
            Event.Entry.Eex,
            Event.Entry.Digit(2),
            Event.StackOp.Enter,
        )
        assertEquals(d(150), s.stack.x)
    }

    @Test fun eex_com_expoente_negativo_via_chs() {
        // 2.5E-3 = 0.0025
        val s = run(
            Event.Entry.Digit(2), Event.Entry.DecimalPoint, Event.Entry.Digit(5),
            Event.Entry.Eex,
            Event.Entry.ChangeSign, // inverte sinal do expoente
            Event.Entry.Digit(3),
            Event.StackOp.Enter,
        )
        assertEquals(d("0.0025"), s.stack.x)
    }

    @Test fun eex_expoente_maximo_dois_digitos_terceiro_ignorado() {
        // 1E231 → 3º dígito ignorado → 1E23
        val s = run(
            Event.Entry.Eex,      // buffer="1E"
            Event.Entry.Digit(2), // buffer="1E2"
            Event.Entry.Digit(3), // buffer="1E23"
            Event.Entry.Digit(1), // ignorado — expoente já tem 2 dígitos
            Event.StackOp.Enter,
        )
        val x = s.stack.x.toString()
        // 1E23 = 10^23; não deve ser 1E231
        assertTrue(x.contains("E") || x.length <= 25, "expoente não excedeu 2 dígitos: $x")
        assertEquals(d("1E23"), s.stack.x, "1E23 exato")
    }

    @Test fun eex_vazio_inicia_com_mantissa_1() {
        // EEX sem dígito de mantissa antes → assume mantissa 1
        val s = run(
            Event.Entry.Eex,
            Event.Entry.Digit(2),
            Event.StackOp.Enter,
        )
        assertEquals(d(100), s.stack.x, "1E2 = 100")
    }

    @Test fun eex_seguido_de_operacao_comita_como_1e0_igual_1() {
        // EEX sem dígito nenhum, depois +: comita "1E0"=1 e soma com Y
        val s = run(
            *number("5").toTypedArray(), Event.StackOp.Enter,
            Event.Entry.Eex,
            Event.Arith.Add,   // comita "1E0"=1 no commit; 5+1=6
        )
        assertEquals(d(6), s.stack.x)
    }

    // ─── 4. DIVISÃO POR ZERO E ERROS ARITMÉTICOS ────────────────────────────────

    @Test fun divisao_por_zero_gera_error_0() {
        val s = run(
            *number("5").toTypedArray(), Event.StackOp.Enter,
            *number("0").toTypedArray(), Event.Arith.Divide,
        )
        assertNotNull(s.pendingError)
        assertEquals(0, s.pendingError!!.code, "código do erro = 0")
        // Pilha preservada (regra 8 — estado pré-operação)
        assertEquals(d(5), s.stack.y, "Y preservado com 5")
        assertEquals(d(0), s.stack.x, "X preservado com 0")
    }

    @Test fun zero_dividido_por_zero_gera_error_0() {
        val s = run(
            *number("0").toTypedArray(), Event.StackOp.Enter,
            *number("0").toTypedArray(), Event.Arith.Divide,
        )
        assertEquals(0, s.pendingError!!.code)
    }

    @Test fun apos_error_0_pilha_preservada_e_erro_limpo_na_proxima_tecla() {
        val comErro = run(
            *number("5").toTypedArray(), Event.StackOp.Enter,
            *number("0").toTypedArray(), Event.Arith.Divide,
        )
        assertNotNull(comErro.pendingError)
        // Próxima tecla limpa o erro
        val limpo = run(comErro, Event.Entry.Digit(1))
        assertNull(limpo.pendingError, "erro limpo pela próxima tecla")
    }

    @Test fun log_de_zero_gera_error_0() {
        val s = run(
            *number("0").toTypedArray(),
            Event.Transcendental.Ln,
        )
        assertEquals(0, s.pendingError?.code)
    }

    @Test fun log_de_negativo_gera_error_0() {
        val s = run(
            *number("-1").toTypedArray(),
            Event.Transcendental.Ln,
        )
        assertEquals(0, s.pendingError?.code)
    }

    @Test fun sqrt_de_negativo_gera_error_0() {
        val s = run(
            *number("-4").toTypedArray(),
            Event.Transcendental.Sqrt,
        )
        assertEquals(0, s.pendingError?.code)
    }

    @Test fun reciprocal_de_zero_gera_error_0() {
        val s = run(
            *number("0").toTypedArray(),
            Event.Transcendental.Reciprocal,
        )
        assertEquals(0, s.pendingError?.code)
    }

    // ─── 5. OPERAÇÕES MATEMÁTICAS FUNDAMENTAIS ──────────────────────────────────

    @Test fun sqrt_de_4_da_2() {
        val s = run(*number("4").toTypedArray(), Event.Transcendental.Sqrt)
        assertEquals(d(2), s.stack.x)
    }

    @Test fun sqrt_de_0_da_0() {
        val s = run(*number("0").toTypedArray(), Event.Transcendental.Sqrt)
        assertEquals(d(0), s.stack.x)
        assertNull(s.pendingError, "√0 não é erro")
    }

    @Test fun reciprocal_de_4_da_0_25() {
        val s = run(*number("4").toTypedArray(), Event.Transcendental.Reciprocal)
        assertEquals(d("0.25"), s.stack.x)
    }

    @Test fun quadrado_de_7_da_49() {
        val s = run(*number("7").toTypedArray(), Event.Transcendental.Square)
        assertEquals(d(49), s.stack.x)
    }

    @Test fun ln_de_e_da_1() {
        // ln(e) = 1 — tolerância ampla por arredondamento BCD de e^1 seguido de ln
        val s = run(
            Event.Entry.Digit(1), Event.Transcendental.Exp, // X=e
            Event.Transcendental.Ln,                         // X≈1
        )
        val diff = s.stack.x - Hp12cDecimal.ONE
        val tol   = Hp12cDecimal.of("0.000000001") // 1e-9: BCD 10 dígitos, round-trip e^1→ln
        assertTrue(diff < tol && diff > -tol, "ln(e) ≈ 1, got ${s.stack.x}")
    }

    @Test fun exp_de_0_da_1() {
        val s = run(*number("0").toTypedArray(), Event.Transcendental.Exp)
        assertEquals(d(1), s.stack.x)
    }

    @Test fun factorial_de_0_da_1() {
        // 0! = 1, conforme ambiguidade #1 de formulas/transcendentais.md
        val s = run(*number("0").toTypedArray(), Event.Transcendental.Factorial)
        assertEquals(d(1), s.stack.x, "0! = 1")
        assertNull(s.pendingError, "0! não é erro")
    }

    @Test fun factorial_de_5_da_120() {
        val s = run(*number("5").toTypedArray(), Event.Transcendental.Factorial)
        assertEquals(d(120), s.stack.x)
    }

    @Test fun factorial_negativo_gera_error_5() {
        val s = run(*number("-1").toTypedArray(), Event.Transcendental.Factorial)
        assertNotNull(s.pendingError)
        assertEquals(5, s.pendingError!!.code, "n! de negativo → Error 5")
    }

    @Test fun factorial_fracionario_gera_error_5() {
        val s = run(*number("1.5").toTypedArray(), Event.Transcendental.Factorial)
        assertNotNull(s.pendingError)
        assertEquals(5, s.pendingError!!.code, "n! fracionário → Error 5")
    }

    // ─── 6. INTEIRO E FRAÇÃO ────────────────────────────────────────────────────

    @Test fun int_de_3_7_da_3() {
        val s = run(*number("3.7").toTypedArray(), Event.Transcendental.Integer)
        assertEquals(d(3), s.stack.x)
    }

    @Test fun int_de_negativo_trunca_em_direcao_ao_zero() {
        // INT(-3.7) = -3 (trunca em direção a zero, não piso)
        val s = run(*number("-3.7").toTypedArray(), Event.Transcendental.Integer)
        assertEquals(d("-3"), s.stack.x)
    }

    @Test fun frac_de_3_7_da_0_7() {
        val s = run(*number("3.7").toTypedArray(), Event.Transcendental.Fractional)
        val diff = s.stack.x - d("0.7")
        val tol  = Hp12cDecimal.of("0.0000000001")
        assertTrue(diff < tol && diff > -tol, "FRAC(3.7) ≈ 0.7, got ${s.stack.x}")
    }

    @Test fun int_mais_frac_iguala_original_para_positivo() {
        // INT(x) + FRAC(x) = x para x positivo
        val x = d("7.345")
        val xState = run(*number("7.345").toTypedArray())
        val sInt  = run(xState, Event.Transcendental.Integer)
        val sFrac = run(xState, Event.Transcendental.Fractional)
        val soma = sInt.stack.x + sFrac.stack.x
        val diff = soma - x
        val tol = Hp12cDecimal.of("0.0000000001")
        assertTrue(diff < tol && diff > -tol, "INT+FRAC=original: $soma vs $x")
    }

    // ─── 7. PERCENTAGEM ─────────────────────────────────────────────────────────

    @Test fun percent_of_300_por_14_da_42() {
        // 300 ENTER 14 % → X=42 (14% de 300), Y=300 (preservado)
        val s = run(
            *number("300").toTypedArray(), Event.StackOp.Enter,
            *number("14").toTypedArray(),  Event.Percent.Of,
        )
        assertEquals(d(42),  s.stack.x, "14% de 300 = 42")
        assertEquals(d(300), s.stack.y, "Y preservado (comportamento especial de %)")
    }

    @Test fun percent_delta_50_para_75_da_50_pct() {
        // ΔY%X: (X-Y)/Y * 100 = (75-50)/50*100 = 50%
        val s = run(
            *number("50").toTypedArray(), Event.StackOp.Enter,
            *number("75").toTypedArray(), Event.Percent.Delta,
        )
        assertEquals(d(50), s.stack.x, "Δ% de 50→75 = 50%")
    }

    @Test fun percent_of_total_50_de_200_da_25() {
        // 200 ENTER 50 %T → X=25 (50 representa 25% de 200)
        val s = run(
            *number("200").toTypedArray(), Event.StackOp.Enter,
            *number("50").toTypedArray(),  Event.Percent.OfTotal,
        )
        assertEquals(d(25),  s.stack.x, "50 é 25% de 200")
        assertEquals(d(200), s.stack.y, "Y=200 preservado em %T")
    }
}
