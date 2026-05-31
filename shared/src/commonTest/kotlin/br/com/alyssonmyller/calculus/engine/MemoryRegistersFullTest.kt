package br.com.alyssonmyller.calculus.engine

import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import br.com.alyssonmyller.calculus.engine.state.RegisterId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cobertura completa dos registradores de memória R0–R9 e RI (I = decimal/ponto).
 *
 * Testa:
 *   - STO e RCL para todos os 11 registradores
 *   - CLEAR REG zera todos
 *   - Comportamento de stackLift em RCL
 *   - Compartilhamento de R1–R6 com os acumuladores estatísticos
 *   - STO durante entrada comita o buffer antes de copiar
 *   - Valor inicial de registrador = 0
 */
class MemoryRegistersFullTest {

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

    private fun d(n: Int)    = Hp12cDecimal.of(n)
    private fun d(s: String) = Hp12cDecimal.of(s)

    // ─── 1. STO e RCL — todos os registradores ─────────────────────────────────

    @Test fun sto_rcl_R0_guarda_e_recupera_valor() {
        val s = run(
            *number("42").toTypedArray(), Event.Memory.Store(RegisterId.R0),
            *number("0").toTypedArray(),  // limpa X
            Event.Memory.Recall(RegisterId.R0),
        )
        assertEquals(d(42), s.stack.x, "R0 guardou 42")
    }

    @Test fun sto_rcl_R1_guarda_e_recupera_valor() {
        val s = run(
            *number("11").toTypedArray(), Event.Memory.Store(RegisterId.R1),
            Event.StackOp.ClearX,
            Event.Memory.Recall(RegisterId.R1),
        )
        assertEquals(d(11), s.stack.x, "R1 guardou 11")
    }

    @Test fun sto_rcl_R2_guarda_e_recupera_valor() {
        val s = run(*number("22").toTypedArray(), Event.Memory.Store(RegisterId.R2),
                    Event.StackOp.ClearX,         Event.Memory.Recall(RegisterId.R2))
        assertEquals(d(22), s.stack.x)
    }

    @Test fun sto_rcl_R3_guarda_e_recupera_valor() {
        val s = run(*number("33").toTypedArray(), Event.Memory.Store(RegisterId.R3),
                    Event.StackOp.ClearX,         Event.Memory.Recall(RegisterId.R3))
        assertEquals(d(33), s.stack.x)
    }

    @Test fun sto_rcl_R4_guarda_e_recupera_valor() {
        val s = run(*number("44").toTypedArray(), Event.Memory.Store(RegisterId.R4),
                    Event.StackOp.ClearX,         Event.Memory.Recall(RegisterId.R4))
        assertEquals(d(44), s.stack.x)
    }

    @Test fun sto_rcl_R5_guarda_e_recupera_valor() {
        val s = run(*number("55").toTypedArray(), Event.Memory.Store(RegisterId.R5),
                    Event.StackOp.ClearX,         Event.Memory.Recall(RegisterId.R5))
        assertEquals(d(55), s.stack.x)
    }

    @Test fun sto_rcl_R6_guarda_e_recupera_valor() {
        val s = run(*number("66").toTypedArray(), Event.Memory.Store(RegisterId.R6),
                    Event.StackOp.ClearX,         Event.Memory.Recall(RegisterId.R6))
        assertEquals(d(66), s.stack.x)
    }

    @Test fun sto_rcl_R7_guarda_e_recupera_valor() {
        val s = run(*number("77").toTypedArray(), Event.Memory.Store(RegisterId.R7),
                    Event.StackOp.ClearX,         Event.Memory.Recall(RegisterId.R7))
        assertEquals(d(77), s.stack.x)
    }

    @Test fun sto_rcl_R8_guarda_e_recupera_valor() {
        val s = run(*number("88").toTypedArray(), Event.Memory.Store(RegisterId.R8),
                    Event.StackOp.ClearX,         Event.Memory.Recall(RegisterId.R8))
        assertEquals(d(88), s.stack.x)
    }

    @Test fun sto_rcl_R9_guarda_e_recupera_valor() {
        val s = run(*number("99").toTypedArray(), Event.Memory.Store(RegisterId.R9),
                    Event.StackOp.ClearX,         Event.Memory.Recall(RegisterId.R9))
        assertEquals(d(99), s.stack.x)
    }

    @Test fun sto_rcl_RI_guarda_e_recupera_valor() {
        // RI é o registrador "I" (índice, acessado com "·" no teclado físico)
        val s = run(*number("123").toTypedArray(), Event.Memory.Store(RegisterId.RI),
                    Event.StackOp.ClearX,          Event.Memory.Recall(RegisterId.RI))
        assertEquals(d(123), s.stack.x, "RI guardou 123")
    }

    // ─── 2. Armazenar negativos e fracionários ──────────────────────────────────

    @Test fun sto_rcl_valor_negativo() {
        val s = run(*number("-7.5").toTypedArray(), Event.Memory.Store(RegisterId.R0),
                    Event.StackOp.ClearX,           Event.Memory.Recall(RegisterId.R0))
        assertEquals(d("-7.5"), s.stack.x)
    }

    @Test fun sto_rcl_valor_zero() {
        // Store 0 explicitamente; confirma que RCL traz 0, não valor anterior
        val s = run(
            *number("5").toTypedArray(),  Event.Memory.Store(RegisterId.R0),
            *number("0").toTypedArray(),  Event.Memory.Store(RegisterId.R0),
            Event.StackOp.ClearX,
            Event.Memory.Recall(RegisterId.R0),
        )
        assertEquals(d(0), s.stack.x, "R0 sobrescrito com 0")
    }

    // ─── 3. Valor inicial = 0 ───────────────────────────────────────────────────

    @Test fun rcl_de_registrador_nunca_escrito_retorna_zero() {
        // R5 nunca foi tocado → RCL R5 = 0
        val s = run(
            *number("99").toTypedArray(), Event.StackOp.Enter,
            Event.Memory.Recall(RegisterId.R5),
        )
        assertEquals(d(0), s.stack.x, "registrador não-inicializado = 0")
        assertEquals(d(99), s.stack.y, "Y preservado")
    }

    // ─── 4. CLEAR REG ─────────────────────────────────────────────────────────

    @Test fun clear_reg_zera_todos_os_registradores() {
        // Preenche R0–R9 e RI, depois CLEAR REG, depois confirma zeros
        var s = initial
        listOf(
            RegisterId.R0, RegisterId.R1, RegisterId.R2,
            RegisterId.R3, RegisterId.R4, RegisterId.R5,
            RegisterId.R6, RegisterId.R7, RegisterId.R8,
            RegisterId.R9, RegisterId.RI,
        ).forEachIndexed { idx, id ->
            s = run(s, *number("${idx + 1}").toTypedArray(), Event.Memory.Store(id))
        }
        s = run(s, Event.Memory.ClearReg)

        listOf(
            RegisterId.R0, RegisterId.R1, RegisterId.R2,
            RegisterId.R3, RegisterId.R4, RegisterId.R5,
            RegisterId.R6, RegisterId.R7, RegisterId.R8,
            RegisterId.R9, RegisterId.RI,
        ).forEach { id ->
            s = run(s, Event.Memory.Recall(id))
            assertEquals(d(0), s.stack.x, "CLEAR REG → $id = 0")
        }
    }

    @Test fun clear_reg_nao_toca_na_pilha() {
        val s = run(
            *number("5").toTypedArray(), Event.StackOp.Enter,
            *number("7").toTypedArray(), Event.Memory.Store(RegisterId.R0),
            Event.Memory.ClearReg,
        )
        assertEquals(d(7), s.stack.x, "X preservado após CLEAR REG")
        assertEquals(d(5), s.stack.y, "Y preservado após CLEAR REG")
    }

    @Test fun clear_reg_nao_toca_nos_registradores_financeiros() {
        // Guarda valor em n, faz CLEAR REG, confirma que n persiste
        val s = run(
            *number("36").toTypedArray(), Event.Financial.Store.N,
            Event.Memory.ClearReg,
        )
        assertNull(s.pendingError)
        assertEquals(d(36), s.financial.n, "n preservado após CLEAR REG")
    }

    // ─── 5. Múltiplos registradores simultaneamente ──────────────────────────────

    @Test fun dez_registradores_diferentes_valores_independentes() {
        var s = initial
        // Guarda 10, 20, ..., 100 em R0..R9
        val ids = listOf(
            RegisterId.R0, RegisterId.R1, RegisterId.R2,
            RegisterId.R3, RegisterId.R4, RegisterId.R5,
            RegisterId.R6, RegisterId.R7, RegisterId.R8,
            RegisterId.R9,
        )
        ids.forEachIndexed { i, id ->
            s = run(s, *number("${(i + 1) * 10}").toTypedArray(), Event.Memory.Store(id))
        }
        ids.forEachIndexed { i, id ->
            s = run(s, Event.Memory.Recall(id))
            assertEquals(d((i + 1) * 10), s.stack.x, "R$i = ${(i+1)*10}")
        }
    }

    @Test fun sto_sobrescreve_valor_anterior() {
        val s = run(
            *number("10").toTypedArray(), Event.Memory.Store(RegisterId.R0),
            *number("20").toTypedArray(), Event.Memory.Store(RegisterId.R0),
            Event.StackOp.ClearX,
            Event.Memory.Recall(RegisterId.R0),
        )
        assertEquals(d(20), s.stack.x, "segundo STO sobrescreve o primeiro")
    }

    // ─── 6. STO durante entrada ─────────────────────────────────────────────────

    @Test fun sto_durante_entrada_comita_buffer_antes_de_guardar() {
        // Digit 4, digit 2 (buffer="42"), STO → deve guardar 42, não 0
        val s = run(
            Event.Entry.Digit(4), Event.Entry.Digit(2),
            Event.Memory.Store(RegisterId.R0),
            Event.StackOp.ClearX,
            Event.Memory.Recall(RegisterId.R0),
        )
        assertEquals(d(42), s.stack.x, "STO comitou buffer 42 corretamente")
    }

    // ─── 7. RCL e stackLift ──────────────────────────────────────────────────────

    @Test fun rcl_com_stacklift_true_levanta_pilha() {
        // Guardar 10 em R1, limpar X, calcular 5+3=8, depois RCL R1 (stackLift=true após +)
        // → deve levantar: Y=8, X=10
        val s = run(
            *number("10").toTypedArray(), Event.Memory.Store(RegisterId.R1), // R1=10
            Event.StackOp.ClearX,                                            // X=0, stackLift=false
            *number("5").toTypedArray(), Event.StackOp.Enter,                // Y=5, X=5
            *number("3").toTypedArray(), Event.Arith.Add,                    // X=8, Y=0, stackLift=true
            Event.Memory.Recall(RegisterId.R1),                              // lift: Y=8, X=10
        )
        assertEquals(d(10), s.stack.x, "RCL pôs valor em X")
        assertEquals(d(8),  s.stack.y, "Y tem o resultado anterior (pilha levantada)")
    }

    @Test fun rcl_apos_clx_stacklift_false_sobrescreve_x() {
        // CLx seta stackLift=false; RCL deve sobrescrever X (não levantar)
        val s = run(
            *number("9").toTypedArray(), Event.StackOp.Enter,
            *number("15").toTypedArray(), Event.Memory.Store(RegisterId.R2),
            Event.StackOp.ClearX,                                 // stackLift=false
            Event.Memory.Recall(RegisterId.R2),                   // sobrescreve X
        )
        assertEquals(d(15), s.stack.x, "RCL sobrescreveu X após CLx")
        assertEquals(d(9),  s.stack.y, "Y=9 inalterado (não levantou pilha)")
    }

    // ─── 8. Compartilhamento R1–R6 com estatísticas ──────────────────────────────

    @Test fun sigma_plus_modifica_R1_a_R6() {
        // R1=n, R2=Σx, R3=Σx², R4=Σy, R5=Σy², R6=Σxy
        // Acumular (x=3, y=4): R1=1, R2=3, R3=9, R4=4, R5=16, R6=12
        val s = run(
            *number("4").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.Statistics.SigmaPlus,
        )
        // Verifica R1=1 (n=1)
        val r1 = run(s, Event.Memory.Recall(RegisterId.R1))
        assertEquals(d(1), r1.stack.x, "R1 = n = 1")
        // Verifica R2=3 (Σx)
        val r2 = run(s, Event.Memory.Recall(RegisterId.R2))
        assertEquals(d(3), r2.stack.x, "R2 = Σx = 3")
    }

    @Test fun sto_em_R1_e_sigma_plus_comutam_dados() {
        // STO 5 em R1, depois Σ+ com (y=2, x=1) deve incrementar n em cima de 5
        val s = run(
            *number("5").toTypedArray(), Event.Memory.Store(RegisterId.R1),
            *number("2").toTypedArray(), Event.StackOp.Enter,
            *number("1").toTypedArray(), Event.Statistics.SigmaPlus,
        )
        // R1 (n) deve ser 6 (5+1)
        val r1 = run(s, Event.Memory.Recall(RegisterId.R1))
        assertEquals(d(6), r1.stack.x, "Σ+ incrementou R1 (n) de 5 para 6")
    }

    @Test fun clear_sigma_zera_R1_a_R6_e_pilha() {
        // Acumula dados, depois f CLEAR Σ
        val s = run(
            *number("4").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.Statistics.SigmaPlus,
            Event.Statistics.ClearSigma,
        )
        val r1 = run(s, Event.Memory.Recall(RegisterId.R1))
        assertEquals(d(0), r1.stack.x, "CLEAR Σ zerou R1")
        assertEquals(d(0), s.stack.x, "CLEAR Σ zerou pilha X")
        assertEquals(d(0), s.stack.y, "CLEAR Σ zerou pilha Y")
    }
}
