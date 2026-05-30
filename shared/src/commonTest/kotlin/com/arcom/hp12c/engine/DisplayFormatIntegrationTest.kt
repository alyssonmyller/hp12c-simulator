package br.com.alyssonmyller.calculus.engine

import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import br.com.alyssonmyller.calculus.engine.state.DisplayFormat
import br.com.alyssonmyller.calculus.engine.state.NumericSeparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes de integração do formato de display (FIX/SCI/ENG) com o engine completo.
 *
 * Cobrem:
 *   - FIX 0–9 com valores reais e seu impacto em formatDisplay
 *   - Display.Fix não comita entrada em curso
 *   - Persistência do formato através de múltiplas operações
 *   - Erro pendente sobrepõe o formato
 *   - SCI e ENG com valores financeiros reais
 *   - Separadores pt-BR (vírgula decimal) e en-US (ponto decimal)
 *   - Degradação automática de FIX para SCI em números grandes
 *   - HALF_EVEN em contexto de display real
 *
 * Fonte: Seção 5 do manual HP 12C Platinum + DisplayFormatter.kt.
 */
class DisplayFormatIntegrationTest {

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

    private fun fmt(state: CalculatorState, sep: NumericSeparator = NumericSeparator.PERIOD_COMMA) =
        engine.formatDisplay(state, sep)

    private fun d(s: String) = Hp12cDecimal.of(s)
    private fun d(n: Int)    = Hp12cDecimal.of(n)

    // ─── 1. FIX n: formatDisplay com valor real ─────────────────────────────────

    @Test fun fix_0_trunca_decimais_com_half_even() {
        val s = run(
            *number("14.5").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Fix(0),
        )
        assertEquals(DisplayFormat.Fix(0), s.display, "estado armazena FIX 0")
        // 14.5 em FIX 0: arredonda HALF_EVEN (5 = exatamente metade, 4 é par → trunca = "14")
        assertEquals("14", fmt(s), "14.5 arredonda para 14 (HALF_EVEN, 4 é par)")
    }

    @Test fun fix_1_arredonda_para_uma_casa() {
        val s = run(
            *number("14.87456").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Fix(1),
        )
        assertEquals("14.9", fmt(s), "14.87456 em FIX 1 = 14.9")
    }

    @Test fun fix_2_manual_exemplo_14_87() {
        // Manual p. 72: valor 14.87 em FIX 2
        val s = run(
            *number("14.87").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Fix(2),
        )
        assertEquals("14.87", fmt(s))
    }

    @Test fun fix_4_pi_arredondado() {
        // 3.14159265 em FIX 4 = 3.1416
        val s = run(
            *number("3.14159265").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Fix(4),
        )
        assertEquals("3.1416", fmt(s))
    }

    @Test fun fix_0_inteiro_exato_sem_decimais() {
        val s = run(
            *number("1000").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Fix(0),
        )
        assertEquals("1,000", fmt(s), "1000 em FIX 0 com milhar en-US")
    }

    @Test fun fix_2_com_milhar_em_ptbr() {
        val s = run(
            *number("12345.678").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Fix(2),
        )
        assertEquals("12.345,68", fmt(s, NumericSeparator.COMMA_PERIOD),
            "separador pt-BR: ponto milhar, vírgula decimal")
    }

    @Test fun fix_2_com_milhar_em_enus() {
        val s = run(
            *number("12345.678").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Fix(2),
        )
        assertEquals("12,345.68", fmt(s, NumericSeparator.PERIOD_COMMA),
            "separador en-US: vírgula milhar, ponto decimal")
    }

    @Test fun fix_9_mostra_nove_casas() {
        val s = run(
            *number("1.123456789").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Fix(9),
        )
        val display = fmt(s)
        assertEquals("1.123456789", display, "FIX 9 com 9 decimais exatos")
    }

    @Test fun fix_2_zero_exibe_0_00() {
        val s = run(
            *number("0").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Fix(2),
        )
        assertEquals("0.00", fmt(s), "zero em FIX 2 = 0.00")
    }

    @Test fun fix_2_negativo_exibe_sinal() {
        val s = run(
            *number("-42.5").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Fix(2),
        )
        assertEquals("-42.50", fmt(s), "negativo exibe sinal")
    }

    // ─── 2. Display.Fix não comita entrada em curso ──────────────────────────────

    @Test fun display_fix_durante_digitacao_nao_comita_buffer() {
        // Digitar "1.2", mudar para FIX 4, continuar digitando "3" → commit final = 1.23
        val s = run(
            Event.Entry.Digit(1), Event.Entry.DecimalPoint, Event.Entry.Digit(2),
            Event.Display.Fix(4),
            Event.Entry.Digit(3),
            Event.StackOp.Enter,
        )
        assertEquals(d("1.23"), s.stack.x, "buffer continuou após mudança de formato")
        assertEquals(DisplayFormat.Fix(4), s.display, "formato aplicado")
    }

    @Test fun display_persiste_apos_operacao_binaria() {
        val s = run(
            Event.Display.Fix(3),
            *number("1.5").toTypedArray(), Event.StackOp.Enter,
            *number("2.5").toTypedArray(), Event.Arith.Add,
        )
        assertEquals(DisplayFormat.Fix(3), s.display, "FIX 3 persiste após +")
        assertEquals("4.000", fmt(s), "resultado em FIX 3")
    }

    @Test fun formato_padrao_e_fix_2() {
        // Estado inicial deve ter FIX 2
        assertEquals(DisplayFormat.Fix(2), initial.display, "FIX 2 padrão")
    }

    // ─── 3. Erro pendente sobrepõe o display ────────────────────────────────────

    @Test fun erro_pendente_exibe_error_n_independente_de_fix() {
        val s = run(
            Event.Display.Fix(4),
            *number("5").toTypedArray(), Event.StackOp.Enter,
            *number("0").toTypedArray(), Event.Arith.Divide,
        )
        val display = fmt(s)
        assertEquals("Error 0", display, "erro sobrepõe FIX 4")
    }

    @Test fun apos_acknowledge_erro_retoma_formato_correto() {
        val s = run(
            Event.Display.Fix(3),
            *number("1").toTypedArray(), Event.StackOp.Enter,
            *number("0").toTypedArray(), Event.Arith.Divide,
        )
        assertEquals("Error 0", fmt(s))
        val limpo = run(s, Event.AcknowledgeError)
        assertNull(limpo.pendingError)
        assertEquals(DisplayFormat.Fix(3), limpo.display, "formato FIX 3 preservado")
    }

    // ─── 4. SCI e ENG ───────────────────────────────────────────────────────────

    @Test fun sci_2_formata_valor_pequeno() {
        val s = run(
            *number("0.00123").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Sci(2),
        )
        assertEquals(DisplayFormat.Sci(2), s.display)
        val display = fmt(s)
        // 1.23 × 10^-3 → "1.23-03"
        assertTrue(display.contains("1.23"), "mantissa: $display")
        assertTrue(display.contains("03") || display.contains("-03"), "expoente: $display")
    }

    @Test fun sci_2_formata_valor_grande() {
        val s = run(
            *number("12345").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Sci(2),
        )
        val display = fmt(s)
        // 1.23 × 10^4 → "1.23 04"
        assertTrue(display.contains("1.23") || display.contains("1.24"),
            "mantissa SCI: $display")
        assertTrue(display.contains("04"), "expoente: $display")
    }

    @Test fun eng_2_usa_expoente_multiplo_de_3() {
        val s = run(
            *number("12345").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Eng(2),
        )
        val display = fmt(s)
        // 12.35 × 10^3 → "12.35 03" ou "12.34 03"
        assertTrue(display.endsWith("03"), "expoente ENG múltiplo de 3: $display")
    }

    @Test fun sci_0_exibe_apenas_um_digito_significativo() {
        val s = run(
            *number("3.14159").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Sci(0),
        )
        val display = fmt(s)
        // "3 00"
        assertEquals("3 00", display, "SCI 0: só mantissa inteira")
    }

    @Test fun sci_modo_persiste_apos_store_e_solve_tvm() {
        // Configura SCI 2, depois faz operação financeira; formato deve persistir
        val s = run(
            Event.Display.Sci(2),
            *number("12").toTypedArray(), Event.Financial.Store.N,
            *number("1").toTypedArray(),  Event.Financial.Store.I,
            *number("-1000").toTypedArray(), Event.Financial.Store.Pv,
            *number("0").toTypedArray(),  Event.Financial.Store.Pmt,
            *number("0").toTypedArray(),  Event.Financial.Store.Fv,
            Event.Financial.Solve.Fv,
        )
        assertEquals(DisplayFormat.Sci(2), s.display, "SCI 2 persiste após Solve.Fv")
    }

    // ─── 5. Degradação automática de FIX para SCI ────────────────────────────────

    @Test fun fix_2_com_valor_1e10_degrada_para_sci() {
        // 1E10 = 10^10 → parte inteira tem 11 dígitos → não cabe em FIX 2 (10 dígitos totais)
        // Usa EEX para entrar 1E10 que excede o máximo digitável (10 dígitos de mantissa)
        val s = run(
            Event.Entry.Digit(1), Event.Entry.Eex,
            Event.Entry.Digit(1), Event.Entry.Digit(0), // 1E10
            Event.StackOp.Enter,
            Event.Display.Fix(2),
        )
        val display = fmt(s)
        // Deve ter expoente (degradou para SCI): formato como "1.00 10"
        assertFalse(display.contains(","), "em SCI não deve ter separador de milhar: $display")
        // Verifica que é notação científica (contém espaço+expoente ou similar)
        assertTrue(display.length <= 12, "SCI é mais compacto que FIX: $display")
    }

    @Test fun fix_0_inteiro_grande_cabe_sem_degradar() {
        // 9_999_999_999 em FIX 0 cabe exatamente em 10 dígitos
        val s = run(
            *number("9999999999").toTypedArray(), Event.StackOp.Enter,
            Event.Display.Fix(0),
        )
        val display = fmt(s)
        assertEquals("9,999,999,999", display, "cabe em FIX 0 com 10 dígitos inteiros")
    }

    // ─── 6. Formatação durante entrada (buffer mirroring) ────────────────────────

    @Test fun buffer_com_ponto_decimal_parcial_exibido_corretamente() {
        // Digitar "5." (sem decimal ainda) → display deve mostrar "5."
        val s = run(Event.Entry.Digit(5), Event.Entry.DecimalPoint)
        assertTrue(s.stack.isEntering)
        val display = fmt(s)
        // Aceita "5." ou "5," dependendo do separador. Com PERIOD_COMMA = ponto decimal.
        assertTrue(display.endsWith("."), "ponto pendurado exibido: $display")
    }

    @Test fun buffer_com_eex_sem_expoente_exibe_00() {
        // "1 EEX" sem dígito de expoente → display "1 00" ou similar
        val s = run(Event.Entry.Digit(1), Event.Entry.Eex)
        assertTrue(s.stack.isEntering)
        val display = fmt(s)
        assertTrue(display.contains("00"), "expoente 00 quando vazio: $display")
    }

    @Test fun buffer_negativo_com_chs_exibe_sinal() {
        val s = run(Event.Entry.Digit(5), Event.Entry.ChangeSign)
        val display = fmt(s)
        assertTrue(display.startsWith("-"), "sinal negativo exibido: $display")
    }

    // ─── 7. RND — arredonda X conforme formato atual ─────────────────────────────

    @Test fun rnd_fix_2_arredonda_3_146_para_3_15() {
        val s = run(
            Event.Display.Fix(2),
            *number("3.146").toTypedArray(), Event.StackOp.Enter,
            Event.Transcendental.Round,
        )
        assertEquals(d("3.15"), s.stack.x, "RND FIX 2 = 3.15")
    }

    @Test fun rnd_fix_0_arredonda_para_inteiro() {
        val s = run(
            Event.Display.Fix(0),
            *number("7.7").toTypedArray(), Event.StackOp.Enter,
            Event.Transcendental.Round,
        )
        assertEquals(d("8"), s.stack.x, "RND FIX 0 = 8")
    }

    @Test fun rnd_fix_3_half_even_empate_par_trunca() {
        // 1.5005 em FIX 3: pivot=5, precedente=0 (par) → HALF_EVEN trunca → 1.500
        val s = run(
            Event.Display.Fix(3),
            *number("1.5005").toTypedArray(), Event.StackOp.Enter,
            Event.Transcendental.Round,
        )
        assertEquals(d("1.500"), s.stack.x, "HALF_EVEN: 1.5005 em FIX 3 = 1.500")
    }

    @Test fun rnd_fix_3_half_even_empate_impar_sobe() {
        // 1.5015 em FIX 3: pivot=5, precedente=1 (ímpar) → HALF_EVEN sobe → 1.502
        val s = run(
            Event.Display.Fix(3),
            *number("1.5015").toTypedArray(), Event.StackOp.Enter,
            Event.Transcendental.Round,
        )
        assertEquals(d("1.502"), s.stack.x, "HALF_EVEN: 1.5015 em FIX 3 = 1.502")
    }

    @Test fun rnd_nao_altera_lastx() {
        // RND é o único caso onde lastX NÃO é atualizado (comportamento excepcional HP)
        val s = run(
            Event.Display.Fix(2),
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Arith.Add,   // lastX=4
            Event.Transcendental.Round,
        )
        assertEquals(d(4), s.stack.lastX, "RND não altera lastX (comportamento excepcional)")
    }
}
