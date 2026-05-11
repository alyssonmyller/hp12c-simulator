package com.arcom.hp12c.engine

import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.CalculatorEngine
import com.arcom.hp12c.engine.state.ConditionalTest
import com.arcom.hp12c.engine.state.ProgramLabel
import com.arcom.hp12c.engine.state.ProgramMemory
import com.arcom.hp12c.engine.state.ProgramState
import com.arcom.hp12c.engine.state.ProgramStep
import com.arcom.hp12c.engine.state.ProgramTarget
import com.arcom.hp12c.engine.state.NumericSeparator
import com.arcom.hp12c.engine.state.CalculatorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes de integração para a Fase 3 — Programação keystroke.
 *
 * Segue a mesma estrutura dos outros testes da engine: cada `@Test` é auto-contido,
 * usa apenas [CalculatorEngine.Default] e não depende de plataforma.
 *
 * Ver `arquitetura/programacao.md` e `test-vectors/programacao-vectors.json`.
 */
class ProgramacaoTest {

    private val engine = CalculatorEngine.Default

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun seq(vararg events: Event) = events.fold(CalculatorEngine.InitialState) { s, e ->
        engine.reduce(s, e)
    }

    private fun display(state: com.arcom.hp12c.engine.state.CalculatorState) =
        engine.formatDisplay(state, NumericSeparator.PERIOD_COMMA).replace(",", "")

    // ─────────────────────────────────────────────────────────────────────────
    //  1. Controle de modo PRGM
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `TogglePrgmMode entra em Editing com cursor no fim`() {
        val s0 = CalculatorEngine.InitialState  // programa vazio
        val s1 = engine.reduce(s0, Event.Program.TogglePrgmMode)
        assertIs<ProgramState.Editing>(s1.programState)
        assertEquals(0, (s1.programState as ProgramState.Editing).cursor)
    }

    @Test
    fun `TogglePrgmMode segundo toggle sai para Idle`() {
        val s = seq(Event.Program.TogglePrgmMode, Event.Program.TogglePrgmMode)
        assertIs<ProgramState.Idle>(s.programState)
    }

    @Test
    fun `ClearProgram zera passos e cursor`() {
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Entry.Digit(3),          // grava passo
            Event.Arith.Add,               // grava passo
            Event.Program.ClearProgram,
        )
        assertEquals(0, s.programMemory.steps.size, "passos após clear")
        assertEquals(0, (s.programState as ProgramState.Editing).cursor, "cursor após clear")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  2. Modo de edição — gravação de passos
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `eventos nao-Program em Editing sao gravados como KeyStep`() {
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Entry.Digit(2),
            Event.Arith.Multiply,
        )
        assertEquals(2, s.programMemory.steps.size)
        assertIs<ProgramStep.KeyStep>(s.programMemory.steps[0])
        assertIs<ProgramStep.KeyStep>(s.programMemory.steps[1])
    }

    @Test
    fun `cursor avanca a cada passo gravado`() {
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Entry.Digit(1),    // cursor → 1
            Event.Entry.Digit(2),    // cursor → 2
            Event.Arith.Add,         // cursor → 3
        )
        assertEquals(3, (s.programState as ProgramState.Editing).cursor)
    }

    @Test
    fun `BST recua cursor`() {
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Entry.Digit(5),
            Event.Arith.Multiply,    // cursor = 2
            Event.Program.BackStep,  // cursor = 1
        )
        assertEquals(1, (s.programState as ProgramState.Editing).cursor)
    }

    @Test
    fun `BST nao recua abaixo de zero`() {
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Program.BackStep,
            Event.Program.BackStep,
        )
        assertEquals(0, (s.programState as ProgramState.Editing).cursor)
    }

    @Test
    fun `SST em Editing avanca cursor sem executar`() {
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Entry.Digit(9),    // step 0
            Event.Program.BackStep,  // cursor = 0
            Event.Program.SingleStep,// cursor = 1 (não executou)
        )
        assertEquals(1, (s.programState as ProgramState.Editing).cursor)
        // Se tivesse executado, stack.x seria 9; deve continuar 0 (inicial)
        assertEquals("0", display(s).replace(".00","").replace(",",""))
    }

    @Test
    fun `Return gravado em Editing vira ProgramStep-Return`() {
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Program.Return,
        )
        assertEquals(1, s.programMemory.steps.size)
        assertIs<ProgramStep.Return>(s.programMemory.steps[0])
    }

    @Test
    fun `Conditional gravado em Editing vira ProgramStep-Conditional`() {
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Program.CondXEqZero,
        )
        val step = s.programMemory.steps[0]
        assertIs<ProgramStep.Conditional>(step)
        assertEquals(ConditionalTest.XEqZero, step.test)
    }

    @Test
    fun `Label gravado em Editing vira ProgramStep-Label`() {
        val label = ProgramLabel.AlphaLabel('A')
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Program.Lbl(label),
        )
        val step = s.programMemory.steps[0]
        assertIs<ProgramStep.Label>(step)
        assertEquals(label, step.label)
    }

    @Test
    fun `Goto gravado em Editing vira ProgramStep-Goto`() {
        val target = ProgramTarget.LineTarget(5)
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Program.Goto(target),
        )
        val step = s.programMemory.steps[0]
        assertIs<ProgramStep.Goto>(step)
        assertEquals(target, step.target)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  3. Execução básica — prog-001 a prog-003
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `prog-001 dobra X (2 mul RTN)`() {
        // Grava: 2 × RTN
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Entry.Digit(2),
            Event.Arith.Multiply,
            Event.Program.Return,
            Event.Program.TogglePrgmMode,  // sai de PRGM
            // Alimenta X = 5
            Event.Entry.Digit(5),
            Event.Program.RunStop,
        )
        assertIs<ProgramState.Idle>(s.programState, "deve terminar em Idle")
        assertEquals("10.00", display(s), "5 × 2 = 10")
    }

    @Test
    fun `prog-002 soma 3 ao X`() {
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Entry.Digit(3),
            Event.Arith.Add,
            Event.Program.Return,
            Event.Program.TogglePrgmMode,
            Event.Entry.Digit(7),
            Event.Program.RunStop,
        )
        assertEquals("10.00", display(s))
    }

    @Test
    fun `prog-007 ENTER 3 mul X=4 resulta 12`() {
        // Programa: ENTER 3 × RTN
        // ENTER duplica X (4→4,4); depois 3 empurra; × consome 4×3=12
        // Mas ENTER no programa duplica o valor atual de X em Y antes de 3 ser digitado.
        // Fluxo: X=4 → ENTER → (X=4,Y=4) → Digit(3) → (X=3,Y=4) → MUL → X=12
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.StackOp.Enter,
            Event.Entry.Digit(3),
            Event.Arith.Multiply,
            Event.Program.Return,
            Event.Program.TogglePrgmMode,
            Event.Entry.Digit(4),
            Event.Program.RunStop,
        )
        assertEquals("12.00", display(s))
    }

    @Test
    fun `prog-012 RTN explicito termina normalmente`() {
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Entry.Digit(1),
            Event.Arith.Add,
            Event.Program.Return,
            Event.Program.TogglePrgmMode,
            Event.Entry.Digit(4),
            Event.Entry.Digit(1),
            Event.Program.RunStop,
        )
        assertIs<ProgramState.Idle>(s.programState)
        assertEquals("42.00", display(s))
    }

    @Test
    fun `programa sem RTN para ao atingir ultimo passo`() {
        // Programa: 1 + (sem RTN explícito)
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Entry.Digit(1),
            Event.Arith.Add,
            Event.Program.TogglePrgmMode,  // sai sem gravar RTN
            Event.Entry.Digit(9),
            Event.Program.RunStop,
        )
        assertIs<ProgramState.Idle>(s.programState)
        assertEquals("10.00", display(s))
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  4. Condicional x=0?
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `cond-x=0 semantica do-if-TRUE skip-if-FALSE`() {
        // Semântica HP 12C: TRUE = executa próximo passo; FALSE = pula próximo passo.
        // Programa: g x=0? → Return → Negate → Return
        //   step 0: CondXEqZero
        //   step 1: Return          ← executado se TRUE (x=0), pulado se FALSE
        //   step 2: Negate
        //   step 3: Return
        //
        // X=0:  TRUE  → executa Return (step 1) → para → X=0 (inalterado)
        // X=5:  FALSE → pula Return   (step 1) → executa Negate → X=-5 → para
        // X=-3: FALSE → pula Return   (step 1) → executa Negate → X=3  → para
        val enterProgram = listOf(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Program.CondXEqZero,   // step 0
            Event.Program.Return,        // step 1 — executado se x=0 (TRUE)
            Event.Arith.Negate,          // step 2 — executado se x≠0 (FALSE, pulo step 1)
            Event.Program.Return,        // step 3
            Event.Program.TogglePrgmMode,
        )
        val s0 = enterProgram.fold(CalculatorEngine.InitialState) { st, e -> engine.reduce(st, e) }

        // X=0: TRUE → executa Return (step 1) → para → X=0
        val r0 = engine.reduce(engine.reduce(s0, Event.StackOp.ClearX), Event.Program.RunStop)
        assertEquals("0.00", display(r0), "x=0 TRUE: executa Return, X inalterado")

        // X=5: FALSE → pula Return → Negate → X=-5
        val r5 = engine.reduce(engine.reduce(s0, Event.Entry.Digit(5)), Event.Program.RunStop)
        assertEquals("-5.00", display(r5), "x=5 FALSE: pula Return, executa Negate")

        // X=-3: FALSE → pula Return → Negate → X=3
        var sNeg = s0
        sNeg = engine.reduce(sNeg, Event.Entry.Digit(3))
        sNeg = engine.reduce(sNeg, Event.Arith.Negate)
        sNeg = engine.reduce(sNeg, Event.Program.RunStop)
        assertEquals("3.00", display(sNeg), "x=-3 FALSE: pula Return, Negate → 3")
    }

    @Test
    fun `cond-x-leq-zero semantica do-if-TRUE skip-if-FALSE`() {
        // Programa: g x≤0? → Return → Negate → Return
        //   step 0: CondXLeqZero
        //   step 1: Return   ← TRUE se x≤0 (executa Return, para sem alterar X)
        //   step 2: Negate   ← FALSE se x>0 (pula Return, executa Negate)
        //   step 3: Return
        //
        // X=-3: TRUE  (x≤0) → executa Return → para → X=-3 (inalterado)
        // X=0:  TRUE  (x≤0) → executa Return → para → X=0  (inalterado)
        // X=4:  FALSE (x>0) → pula Return → Negate → X=-4 → para
        val enterProgram = listOf(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Program.CondXLeqZero,  // step 0
            Event.Program.Return,        // step 1 — executado se x≤0
            Event.Arith.Negate,          // step 2 — executado se x>0
            Event.Program.Return,        // step 3
            Event.Program.TogglePrgmMode,
        )
        val s0 = enterProgram.fold(CalculatorEngine.InitialState) { st, e -> engine.reduce(st, e) }

        // X=-3: TRUE → executa Return (step 1) → para → X=-3
        var sNeg = s0
        sNeg = engine.reduce(sNeg, Event.Entry.Digit(3))
        sNeg = engine.reduce(sNeg, Event.Arith.Negate)
        sNeg = engine.reduce(sNeg, Event.Program.RunStop)
        assertEquals("-3.00", display(sNeg), "x=-3 TRUE: executa Return, X=-3 inalterado")

        // X=0: TRUE → executa Return → para → X=0
        val r0 = engine.reduce(engine.reduce(s0, Event.StackOp.ClearX), Event.Program.RunStop)
        assertEquals("0.00", display(r0), "x=0 TRUE: executa Return, X=0 inalterado")

        // X=4: FALSE → pula Return → Negate → X=-4
        val r4 = engine.reduce(engine.reduce(s0, Event.Entry.Digit(4)), Event.Program.RunStop)
        assertEquals("-4.00", display(r4), "x=4 FALSE: pula Return, Negate → -4")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  5. GSB / RTN
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `prog-006 GSB linha chama subrotina e RTN retorna`() {
        // Programa:
        //   step 0: GSB 4        ← salta para step 4 (subrotina)
        //   step 1: Digit(1)
        //   step 2: Add
        //   step 3: Return       ← fim do programa principal
        //   step 4: Digit(2)     ← início subrotina
        //   step 5: Multiply
        //   step 6: Return       ← retorna para step 1
        // X=5: GSB → 5×2=10 → +1 → 11
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Program.Gosub(ProgramTarget.LineTarget(4)),   // step 0
            Event.Entry.Digit(1),                              // step 1
            Event.Arith.Add,                                   // step 2
            Event.Program.Return,                              // step 3
            Event.Entry.Digit(2),                              // step 4 — início subrotina
            Event.Arith.Multiply,                              // step 5
            Event.Program.Return,                              // step 6
            Event.Program.TogglePrgmMode,
            Event.Entry.Digit(5),
            Event.Program.RunStop,
        )
        assertIs<ProgramState.Idle>(s.programState)
        assertEquals("11.00", display(s))
    }

    @Test
    fun `RTN com return-stack vazio para execucao em Idle`() {
        // Programa só com RTN: executa o RTN, stack vazio → Idle sem erro
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Program.Return,
            Event.Program.TogglePrgmMode,
            Event.Entry.Digit(7),
            Event.Program.RunStop,
        )
        assertIs<ProgramState.Idle>(s.programState)
        assertNull(s.pendingError, "RTN com stack vazio não deve gerar erro")
        assertEquals("7.00", display(s), "X deve estar intacto")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  6. Error 4 — programação
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `GTO para linha inexistente causa Error 4 durante execucao`() {
        // Programa: GTO 99 (que não existe se houver só 1 passo)
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Program.Goto(ProgramTarget.LineTarget(99)),  // step 0 — aponta para 99 inexistente
            Event.Program.TogglePrgmMode,
            Event.Program.RunStop,
        )
        assertNotNull(s.pendingError, "deve ter erro")
        assertEquals(4, s.pendingError!!.code, "deve ser Error 4")
        assertIs<ProgramState.Idle>(s.programState)
    }

    @Test
    fun `GSB para label nao existente causa Error 4`() {
        val label = ProgramLabel.AlphaLabel('E')
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Program.Gosub(ProgramTarget.LabelTarget(label)),
            Event.Program.TogglePrgmMode,
            Event.Program.RunStop,
        )
        assertNotNull(s.pendingError)
        assertEquals(4, s.pendingError!!.code)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  7. GTO por rótulo alfa
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `GTO label A salta para LBL A`() {
        // Programa:
        //   step 0: GTO label A     ← pula para step 2
        //   step 1: Digit(9)        ← nunca executado
        //   step 2: LBL A
        //   step 3: Digit(1)
        //   step 4: Add
        //   step 5: Return
        val labelA = ProgramLabel.AlphaLabel('A')
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Program.Goto(ProgramTarget.LabelTarget(labelA)),  // step 0
            Event.Entry.Digit(9),                                    // step 1 — pulado
            Event.Program.Lbl(labelA),                               // step 2
            Event.Entry.Digit(1),                                    // step 3
            Event.Arith.Add,                                         // step 4
            Event.Program.Return,                                    // step 5
            Event.Program.TogglePrgmMode,
            Event.Entry.Digit(4),
            Event.Program.RunStop,
        )
        assertEquals("5.00", display(s), "4+1=5, o passo '9' foi pulado")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  8. RunStop para execução durante Running
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `RunStop durante execucao sincrona ja concluida retorna Idle`() {
        // Na execução síncrona o programa já terminou quando reduce retorna.
        // Verificamos apenas que o estado final é Idle.
        val s = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Entry.Digit(2),
            Event.Arith.Multiply,
            Event.Program.Return,
            Event.Program.TogglePrgmMode,
            Event.Entry.Digit(6),
            Event.Program.RunStop,
        )
        assertIs<ProgramState.Idle>(s.programState)
        assertEquals("12.00", display(s))
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  9. SST em modo Idle
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `SST em Idle executa um passo a partir do inicio`() {
        // Programa: +1, +1, +1 RTN. SST executa apenas o primeiro +1.
        val s0 = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Entry.Digit(1), Event.Arith.Add,  // step 0-1
            Event.Entry.Digit(1), Event.Arith.Add,  // step 2-3
            Event.Entry.Digit(1), Event.Arith.Add,  // step 4-5
            Event.Program.Return,
            Event.Program.TogglePrgmMode,
            Event.Entry.Digit(0),  // X=0
        )
        val s1 = engine.reduce(s0, Event.Program.SingleStep)  // executa step 0: Digit(1) → X=1
        // Após SST: X pode ser 1 (o dígito foi gravado em X via entry)
        // Nota: step 0 é KeyStep("D",1) que executa Entry.Digit(1) → X="1" em entry mode
        assertIs<ProgramState.Idle>(s1.programState)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  10. Múltiplos programas sequenciais (estado não contamina)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `segundo RunStop reexecuta o mesmo programa`() {
        val base = seq(
            Event.Program.TogglePrgmMode,
            Event.Program.ClearProgram,
            Event.Entry.Digit(2),
            Event.Arith.Multiply,
            Event.Program.Return,
            Event.Program.TogglePrgmMode,
        )
        val run1 = engine.reduce(
            engine.reduce(base, Event.Entry.Digit(3)),
            Event.Program.RunStop,
        )
        assertEquals("6.00", display(run1))

        val run2 = engine.reduce(
            engine.reduce(run1, Event.Entry.Digit(5)),
            Event.Program.RunStop,
        )
        assertEquals("10.00", display(run2))
    }
}
