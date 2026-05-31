package br.com.alyssonmyller.calculus.engine

import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes aprofundados do comportamento da pilha RPN de 4 níveis.
 *
 * Cobrem cenários não exercitados pelos testes existentes de StackOpsTest e ReducerTest:
 *   - T-sticky através de múltiplas operações
 *   - LAST X em cadeias de operações
 *   - Roll-down circular
 *   - Interações entre CLx, ENTER e digitação
 *   - Dois LSTx consecutivos
 *   - Comportamento do stackLiftEnabled em transições complexas
 *   - canStoreToTvm em diferentes caminhos
 *
 * Fonte: `referencias/stack-behavior.md` da skill hp12c-simulator.
 */
class StackBehaviorDeepTest {

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

    // ─── Helpers para construir pilha com 4 valores conhecidos ─────────────────
    // Sequência: n1 ENTER n2 ENTER n3 ENTER n4 → T=n1, Z=n2, Y=n3, X=n4
    private fun pilha4(t: String, z: String, y: String, x: String): CalculatorState =
        run(*number(t).toTypedArray(), Event.StackOp.Enter,
            *number(z).toTypedArray(), Event.StackOp.Enter,
            *number(y).toTypedArray(), Event.StackOp.Enter,
            *number(x).toTypedArray())

    // ─── 1. T-STICKY ─────────────────────────────────────────────────────────

    @Test fun t_sticky_atraves_de_tres_operacoes_binarias() {
        // Pilha inicial: T=1, Z=2, Y=3, X=4
        // + → X=7, Y=2, Z=1, T=1 (T sticky)
        // + → X=9, Y=1, Z=1, T=1
        // + → X=10, Y=1, Z=1, T=1
        val s = run(
            *number("1").toTypedArray(), Event.StackOp.Enter,
            *number("2").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(),
            Event.Arith.Add,
            Event.Arith.Add,
            Event.Arith.Add,
        )
        assertEquals(d(10), s.stack.x, "resultado final")
        assertEquals(d(1),  s.stack.t, "T sticky = 1 em todas as operações")
    }

    @Test fun t_sticky_em_multiplicacao_e_divisao_alternados() {
        // T=10, Z=2, Y=3, X=6 → *: X=18,Y=2,Z=10,T=10 → /: X=10/18? Não, Y/X
        // Após 3 ENTER 6: Y=3, X=6
        // 10 ENTER 2 ENTER 3 ENTER 6: T=10, Z=2, Y=3, X=6
        // *: result=Y*X=3*6=18, Y←Z=2, Z←T=10, T←T=10
        val s = run(
            *number("10").toTypedArray(), Event.StackOp.Enter,
            *number("2").toTypedArray(),  Event.StackOp.Enter,
            *number("3").toTypedArray(),  Event.StackOp.Enter,
            *number("6").toTypedArray(),
            Event.Arith.Multiply, // 3*6=18, Y=2, Z=10, T=10
            Event.Arith.Add,      // 2+18=20, Y=10, Z=10, T=10
        )
        assertEquals(d(20),  s.stack.x, "2+18=20")
        assertEquals(d(10),  s.stack.y, "Y=10 (vinha de Z)")
        assertEquals(d(10),  s.stack.t, "T=10 sticky")
    }

    @Test fun t_mantem_valor_apos_cinco_operacoes_binarias() {
        // T só é consumido quando a PILHA completa é usada (op binária quando só 2 na pilha)
        val s = pilha4("99", "1", "1", "1")
        // Cada + consome Y, mas T é sticky: Y←Z, Z←T, T←T
        val resultado = run(s,
            Event.Arith.Add, // 1+1=2, Y=1, Z=99, T=99
            Event.Arith.Add, // 1+2=3, Y=99, Z=99, T=99
            Event.Arith.Add, // 99+3=102, Y=99, Z=99, T=99
            Event.Arith.Add, // 99+102=201, Y=99, Z=99, T=99
            Event.Arith.Add, // 99+201=300, Y=99, Z=99, T=99
        )
        assertEquals(d(300), resultado.stack.x)
        assertEquals(d(99),  resultado.stack.t, "T=99 permanece sticky")
    }

    // ─── 2. LAST X ─────────────────────────────────────────────────────────────

    @Test fun lastx_apos_multiplicacao_guarda_operando_x() {
        // 3 ENTER 5 * → X=15, lastX=5 (o X destruído pela operação)
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("5").toTypedArray(),
            Event.Arith.Multiply,
        )
        assertEquals(d(15), s.stack.x)
        assertEquals(d(5),  s.stack.lastX, "lastX deve ser 5 (operando X do *)")
    }

    @Test fun lastx_apos_segunda_binaria_e_atualizado() {
        // 3 ENTER 4 + → X=7, lastX=4; depois 5 * → X=35, lastX=5
        // lastX = o operando X destruído pela operação binária (não Y)
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Arith.Add,      // X=7, lastX=4
            *number("5").toTypedArray(), Event.Arith.Multiply, // Y=7, X=5 → 7*5=35, lastX=5
        )
        assertEquals(d(35), s.stack.x)
        assertEquals(d(5),  s.stack.lastX, "lastX = X operando do *: 5, não Y=7")
    }

    @Test fun enter_nao_altera_lastx() {
        // 3 ENTER 4 + → lastX=4; ENTER não deve mudar lastX
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Arith.Add,   // lastX=4
            Event.StackOp.Enter,                            // duplica X=7, lastX deve ser 4
        )
        assertEquals(d(4), s.stack.lastX, "ENTER não altera lastX")
    }

    @Test fun clx_nao_altera_lastx() {
        // 3 ENTER 4 + → lastX=4; CLx não deve mudar lastX
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Arith.Add,
            Event.StackOp.ClearX,
        )
        assertEquals(d(4), s.stack.lastX, "CLx não altera lastX")
        assertEquals(d(0), s.stack.x, "CLx zera X")
    }

    @Test fun lstx_nao_altera_o_proprio_registrador_lastx() {
        // 3 ENTER 4 * → X=12, lastX=4; LSTx põe 4 em X mas lastX permanece 4
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Arith.Multiply,
            Event.StackOp.LastX,
        )
        assertEquals(d(4),  s.stack.x,     "LSTx pôs lastX=4 em X")
        assertEquals(d(4),  s.stack.lastX, "lastX ainda = 4 (LSTx não o altera)")
        assertEquals(d(12), s.stack.y,     "Y tem o X antigo antes do LSTx")
    }

    @Test fun dois_lstx_consecutivos_empilham_valor_lastx() {
        // 3 ENTER 4 * → X=12, lastX=4
        // LSTx → Y=12, X=4, lastX=4
        // LSTx → Y=4, X=4, Z=12, lastX=4
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Arith.Multiply,
            Event.StackOp.LastX,
            Event.StackOp.LastX,
        )
        assertEquals(d(4),  s.stack.x,  "X=4 após dois LSTx")
        assertEquals(d(4),  s.stack.y,  "Y=4 (primeiro LSTx ficou em Y)")
        assertEquals(d(12), s.stack.z,  "Z=12 (X original do *)")
        assertEquals(d(4),  s.stack.lastX, "lastX inalterado")
    }

    @Test fun lastx_funciona_com_operacao_unaria() {
        // √(25) → X=5, lastX=25
        val s = run(
            *number("25").toTypedArray(),
            Event.Transcendental.Sqrt,
        )
        assertEquals(d(5),  s.stack.x)
        assertEquals(d(25), s.stack.lastX, "lastX = 25 após unária √x")
    }

    // ─── 3. ROLL-DOWN CIRCULAR ──────────────────────────────────────────────────

    @Test fun roll_down_circular_quatro_vezes_restaura_pilha_original() {
        // T=1, Z=2, Y=3, X=4 → 4× R↓ = identidade
        val inicio = pilha4("1", "2", "3", "4")
        var s = inicio
        repeat(4) { s = run(s, Event.StackOp.RollDown) }
        assertEquals(d(1), s.stack.t, "T=1 restaurado")
        assertEquals(d(2), s.stack.z, "Z=2 restaurado")
        assertEquals(d(3), s.stack.y, "Y=3 restaurado")
        assertEquals(d(4), s.stack.x, "X=4 restaurado")
    }

    @Test fun roll_down_move_x_para_t() {
        // T=1, Z=2, Y=3, X=4 → R↓: T=4, Z=1, Y=2, X=3
        val s = run(pilha4("1", "2", "3", "4"), Event.StackOp.RollDown)
        assertEquals(d(4), s.stack.t, "X foi para T")
        assertEquals(d(1), s.stack.z, "T foi para Z")
        assertEquals(d(2), s.stack.y, "Z foi para Y")
        assertEquals(d(3), s.stack.x, "Y foi para X")
    }

    @Test fun tres_roll_downs_equivalem_a_roll_up() {
        val inicio = pilha4("10", "20", "30", "40")
        // 3× R↓ = 1× R↑
        var tres_down = inicio
        repeat(3) { tres_down = run(tres_down, Event.StackOp.RollDown) }
        assertEquals(d(10), tres_down.stack.x, "R↓³ equivale R↑: T vai para X")
        assertEquals(d(40), tres_down.stack.y)
        assertEquals(d(30), tres_down.stack.z)
        assertEquals(d(20), tres_down.stack.t)
    }

    // ─── 4. CLX E STACKLIFT ─────────────────────────────────────────────────────

    @Test fun clx_inibe_stacklift_proximo_digito_sobrescreve_x() {
        // 5 ENTER 3 CLx → X=0, stackLift=false
        // Entrar 7 e depois + : soma Y+7 (sem levantar pilha extra)
        // Se CLx NÃO inibisse o lift, após CLx+7+ENTER teríamos X=7,Y=0,Z=5.
        // Com CLx inibindo: Y=5 ainda, X=7 → 5+7=12 sem descarte de 5.
        val s = run(
            *number("5").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(),
            Event.StackOp.ClearX,       // X=0, stackLift=false
            *number("7").toTypedArray(), // sobrescreve X (não levanta): Y=5, X=7 em buffer
            Event.Arith.Add,            // comita 7; soma Y=5+X=7=12
        )
        assertEquals(d(12), s.stack.x, "5+7=12 — CLx inibiu lift extra")
        assertEquals(d(0),  s.stack.y, "Y=0 após + (pilha desceu via binary op)")
    }

    @Test fun enter_apos_clx_duplica_zero_sem_levantar_pilha_antes() {
        // 5 ENTER → Y=5, X=5, stackLift=false
        // CLx → X=0, stackLift=false
        // ENTER → Y←X=0, Z←Y=5, T←Z=0
        val s = run(
            *number("5").toTypedArray(), Event.StackOp.Enter,
            Event.StackOp.ClearX,  // X=0
            Event.StackOp.Enter,   // Y←X=0, Z←Y=5, T←Z=0
        )
        assertEquals(d(0), s.stack.x, "X=0")
        assertEquals(d(0), s.stack.y, "Y=0 (duplicou X=0)")
        assertEquals(d(5), s.stack.z, "Z=5 (veio do Y anterior)")
    }

    @Test fun digitacao_apos_binaria_levanta_pilha_normalmente() {
        // 3 ENTER 4 + → X=7, stackLift=true
        // Digitar 5: entryBuffer="5", stack.y já tem 7 (lift realizado ao criar espaço para X)
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Arith.Add,   // X=7, stackLift=true
            *number("5").toTypedArray(),                    // lift: Y←X=7, X=0 (buffer)
        )
        assertEquals("5", s.entryBuffer, "entryBuffer = 5 (digitação em curso)")
        assertEquals(d(7), s.stack.y, "Y=7 (levantado durante digitação)")
        assertTrue(s.stack.isEntering, "isEntering=true")
    }

    // ─── 5. SWAP (x≷y) ─────────────────────────────────────────────────────────

    @Test fun swap_xy_e_idempotente_duplo() {
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("7").toTypedArray(),
            Event.StackOp.SwapXY,
            Event.StackOp.SwapXY,
        )
        assertEquals(d(7), s.stack.x, "X restaurado após 2 swaps")
        assertEquals(d(3), s.stack.y, "Y restaurado após 2 swaps")
    }

    @Test fun swap_nao_altera_z_e_t() {
        // T=1, Z=2, Y=3, X=4 → swap: T=1, Z=2, Y=4, X=3
        val s = run(pilha4("1", "2", "3", "4"), Event.StackOp.SwapXY)
        assertEquals(d(3), s.stack.x, "X=3 (era Y)")
        assertEquals(d(4), s.stack.y, "Y=4 (era X)")
        assertEquals(d(2), s.stack.z, "Z inalterado")
        assertEquals(d(1), s.stack.t, "T inalterado")
    }

    @Test fun swap_seguido_de_operacao_binaria_usa_valores_trocados() {
        // 3 ENTER 12 swap → Y=12, X=3; / → 12/3=4
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("12").toTypedArray(),
            Event.StackOp.SwapXY,    // Y=12, X=3
            Event.Arith.Divide,      // 12/3=4
        )
        assertEquals(d(4), s.stack.x)
    }

    // ─── 6. ENTER E DUPLICAÇÃO ─────────────────────────────────────────────────

    @Test fun dois_enters_consecutivos_duplicam_e_empurram_corretamente() {
        // 5 ENTER → Y=5, X=5, stackLift=false
        // ENTER → Y←X=5, Z←Y=5, T←Z=0; stackLift=false
        // Stack: T=0, Z=5, Y=5, X=5
        val s = run(
            *number("5").toTypedArray(),
            Event.StackOp.Enter,
            Event.StackOp.Enter,
        )
        assertEquals(d(5), s.stack.x, "X=5")
        assertEquals(d(5), s.stack.y, "Y=5")
        assertEquals(d(5), s.stack.z, "Z=5")
        assertEquals(d(0), s.stack.t, "T=0 (só havia 3 slots)")
    }

    @Test fun quatro_enters_de_valor_unico_preenche_toda_pilha() {
        // 7 ENTER ENTER ENTER ENTER → T=Z=Y=X=7
        val s = run(
            *number("7").toTypedArray(),
            Event.StackOp.Enter, Event.StackOp.Enter,
            Event.StackOp.Enter, Event.StackOp.Enter,
        )
        assertEquals(d(7), s.stack.x)
        assertEquals(d(7), s.stack.y)
        assertEquals(d(7), s.stack.z)
        assertEquals(d(7), s.stack.t, "pilha toda preenchida com 7")
    }

    // ─── 7. can_store_to_tvm ────────────────────────────────────────────────────

    @Test fun can_store_to_tvm_falso_no_estado_inicial() {
        assertFalse(initial.stack.canStoreToTvm)
    }

    @Test fun can_store_to_tvm_true_apos_digitacao() {
        val s = run(*number("5").toTypedArray())
        assertTrue(s.stack.isEntering)
        // canStoreToTvm sobe durante entrada
        assertTrue(s.stack.canStoreToTvm)
    }

    @Test fun can_store_to_tvm_true_apos_enter() {
        val s = run(*number("5").toTypedArray(), Event.StackOp.Enter)
        assertTrue(s.stack.canStoreToTvm, "ENTER mantém canStoreToTvm=true")
    }

    @Test fun can_store_to_tvm_true_apos_aritmetica() {
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Arith.Add,
        )
        assertTrue(s.stack.canStoreToTvm, "resultado de binária seta canStoreToTvm")
    }

    @Test fun can_store_to_tvm_true_apos_rcl() {
        val s = run(
            *number("5").toTypedArray(),
            Event.Financial.Store.N,
            Event.Financial.Store.N, // segundo store não seta canStoreToTvm=true... aguarda RCL
        )
        // Após store: canStoreToTvm=false
        val s2 = run(s, Event.Memory.Recall(br.com.alyssonmyller.calculus.engine.state.RegisterId.R0))
        assertTrue(s2.stack.canStoreToTvm, "RCL seta canStoreToTvm=true")
    }

    @Test fun can_store_to_tvm_false_apos_store_financeiro() {
        val s = run(
            *number("12").toTypedArray(),
            Event.Financial.Store.N,
        )
        assertFalse(s.stack.canStoreToTvm, "store TVM zera canStoreToTvm")
    }

    // ─── 8. isEntering FLAG ─────────────────────────────────────────────────────

    @Test fun is_entering_true_durante_digitacao_false_apos_enter() {
        var s = run(*number("5").toTypedArray())
        assertTrue(s.stack.isEntering, "isEntering=true durante digitação")
        assertNotNull(s.entryBuffer, "buffer não nulo")

        s = run(s, Event.StackOp.Enter)
        assertFalse(s.stack.isEntering, "isEntering=false após ENTER")
        assertNull(s.entryBuffer, "buffer zerado")
    }

    @Test fun is_entering_false_apos_operacao_binaria() {
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Arith.Add,
        )
        assertFalse(s.stack.isEntering)
        assertNull(s.entryBuffer)
    }

    @Test fun is_entering_false_apos_clx() {
        val s = run(
            *number("3").toTypedArray(),
            Event.StackOp.ClearX,
        )
        assertFalse(s.stack.isEntering, "CLx interrompe digitação")
        assertNull(s.entryBuffer)
    }
}
