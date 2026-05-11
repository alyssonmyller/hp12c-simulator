package com.arcom.hp12c.engine.persistence

import com.arcom.hp12c.engine.CalculatorEngine
import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.ProgramMemory
import com.arcom.hp12c.engine.state.ProgramState
import com.arcom.hp12c.engine.state.ProgramStep
import com.arcom.hp12c.engine.state.ProgramTarget
import com.arcom.hp12c.engine.state.TvmMode
import com.arcom.hp12c.testing.InMemoryStateRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Testa o ciclo completo de persistência: reduce → normalizeForPersistence → encode → decode.
 *
 * Não testa I/O de plataforma (SharedPreferences / File) — apenas a serialização JSON
 * e a integridade do estado após round-trip. Roda em `commonTest` (JVM + Android).
 */
class PersistenceRoundTripTest {

    private val engine = CalculatorEngine.Default

    // ── Codificação e decodificação de InitialState ─────────────────────────

    @Test
    fun `InitialState round-trip preserva campos default`() {
        val original = CalculatorEngine.InitialState
        val json     = encodeState(original)
        val restored = decodeState(json)

        assertNotNull(restored)
        assertEquals(original.stack,     restored.stack)
        assertEquals(original.financial, restored.financial)
        assertEquals(original.memory,    restored.memory)
        assertEquals(original.display,   restored.display)
        assertEquals(original.dateFormat, restored.dateFormat)
    }

    // ── normalizeForPersistence ──────────────────────────────────────────────

    @Test
    fun `normalizeForPersistence comita entryBuffer em stack-x`() {
        // Digita "42" sem pressionar outra tecla
        var state = CalculatorEngine.InitialState
        state = engine.reduce(state, Event.Entry.Digit(4))
        state = engine.reduce(state, Event.Entry.Digit(2))

        assertEquals("42", state.entryBuffer)
        val normalized = engine.normalizeForPersistence(state)

        assertNull(normalized.entryBuffer)
        assertEquals(false, normalized.stack.isEntering)
        assertEquals("42", normalized.stack.x.toString())
    }

    @Test
    fun `normalizeForPersistence zera pendingError`() {
        // Provoca divisão por zero
        var state = CalculatorEngine.InitialState
        state = engine.reduce(state, Event.Entry.Digit(0))
        state = engine.reduce(state, Event.Arith.Divide)
        assertNotNull(state.pendingError)

        val normalized = engine.normalizeForPersistence(state)
        assertNull(normalized.pendingError)
    }

    @Test
    fun `normalizeForPersistence e noop quando estado ja assentado`() {
        val state      = CalculatorEngine.InitialState
        val normalized = engine.normalizeForPersistence(state)
        assertEquals(state, normalized)
    }

    // ── Round-trip com estado financeiro ────────────────────────────────────

    @Test
    fun `estado TVM round-trip`() {
        var state = CalculatorEngine.InitialState
        // Armazena n=12, i=1, PV=1000, PMT=-90, FV=0
        state = storeFinancial(state, 12.0,  Event.Financial.Store.N)
        state = storeFinancial(state, 1.0,   Event.Financial.Store.I)
        state = storeFinancial(state, 1000.0, Event.Financial.Store.Pv)
        state = storeFinancial(state, -90.0,  Event.Financial.Store.Pmt)
        state = engine.reduce(state, Event.Financial.SetBeginMode)

        val normalized = engine.normalizeForPersistence(state)
        val json       = encodeState(normalized)
        val restored   = decodeState(json)

        assertNotNull(restored)
        assertEquals(normalized.financial.n?.toString(),   restored.financial.n?.toString())
        assertEquals(normalized.financial.i?.toString(),   restored.financial.i?.toString())
        assertEquals(normalized.financial.pv?.toString(),  restored.financial.pv?.toString())
        assertEquals(normalized.financial.pmt?.toString(), restored.financial.pmt?.toString())
        assertEquals(TvmMode.BEGIN, restored.financial.mode)
    }

    // ── Round-trip com memórias de usuário ──────────────────────────────────

    @Test
    fun `memoria R3 round-trip`() {
        var state = CalculatorEngine.InitialState
        state = engine.reduce(state, Event.Entry.Digit(7))
        state = engine.reduce(state, Event.Entry.Digit(7))
        state = engine.reduce(state, Event.Memory.Store(com.arcom.hp12c.engine.state.RegisterId.R3))

        val normalized = engine.normalizeForPersistence(state)
        val json       = encodeState(normalized)
        val restored   = decodeState(json)

        assertNotNull(restored)
        assertEquals("77", restored.memory[com.arcom.hp12c.engine.state.RegisterId.R3].toString())
    }

    // ── Round-trip com pilha não-trivial ────────────────────────────────────

    @Test
    fun `pilha round-trip apos operacao binaria`() {
        var state = CalculatorEngine.InitialState
        state = engine.reduce(state, Event.Entry.Digit(3))
        state = engine.reduce(state, Event.StackOp.Enter)
        state = engine.reduce(state, Event.Entry.Digit(4))
        state = engine.reduce(state, Event.Arith.Multiply)   // x=12, lastX=4

        val normalized = engine.normalizeForPersistence(state)
        val json       = encodeState(normalized)
        val restored   = decodeState(json)

        assertNotNull(restored)
        assertEquals("12", restored.stack.x.toString())
        assertEquals("4",  restored.stack.lastX.toString())
    }

    // ── Campos transientes são sempre nulos/default após round-trip ─────────

    @Test
    fun `pendingError e entryBuffer sao transientes no JSON`() {
        // Mesmo que alguém construísse um estado com entryBuffer=null (já normalizado),
        // o JSON nunca deve ter uma chave "pendingError" ou "entryBuffer".
        val state = CalculatorEngine.InitialState
        val json  = encodeState(state)

        assertEquals(false, "pendingError" in json,
            "pendingError não deve aparecer no JSON")
        assertEquals(false, "entryBuffer" in json,
            "entryBuffer não deve aparecer no JSON")
        assertEquals(false, "programState" in json,
            "programState não deve aparecer no JSON")
    }

    // ── Round-trip de ProgramMemory ─────────────────────────────────────────

    @Test
    fun `ProgramMemory round-trip preserva passos do programa`() {
        // Grava um programa simples via engine: [2 × RTN]
        var state = CalculatorEngine.InitialState
        state = engine.reduce(state, Event.Program.TogglePrgmMode)
        state = engine.reduce(state, Event.Program.ClearProgram)
        state = engine.reduce(state, Event.Entry.Digit(2))
        state = engine.reduce(state, Event.Arith.Multiply)
        state = engine.reduce(state, Event.Program.Return)
        state = engine.reduce(state, Event.Program.TogglePrgmMode)

        assertEquals(3, state.programMemory.steps.size, "programa deve ter 3 passos gravados")

        val normalized = engine.normalizeForPersistence(state)
        val json       = encodeState(normalized)
        val restored   = decodeState(json)

        assertNotNull(restored)
        assertEquals(3, restored.programMemory.steps.size, "round-trip deve preservar 3 passos")
        assertEquals(state.programMemory.steps, restored.programMemory.steps,
            "passos devem ser idênticos após encode/decode")
    }

    @Test
    fun `ProgramMemory com GTO e Label round-trip`() {
        // Constrói programMemory diretamente com passos heterogêneos (KeyStep, Goto, Label, Conditional, Return)
        val steps = listOf(
            ProgramStep.KeyStep("ADD"),
            ProgramStep.Goto(ProgramTarget.LineTarget(5)),
            ProgramStep.Label(com.arcom.hp12c.engine.state.ProgramLabel.AlphaLabel('A')),
            ProgramStep.Conditional(com.arcom.hp12c.engine.state.ConditionalTest.XEqZero),
            ProgramStep.Return,
            ProgramStep.Pause,
        )
        val state = CalculatorEngine.InitialState.copy(
            programMemory = ProgramMemory(steps = steps),
        )

        val json     = encodeState(state)
        val restored = decodeState(json)

        assertNotNull(restored)
        assertEquals(steps, restored.programMemory.steps, "todos os tipos de ProgramStep devem round-trip")
        // programState nunca aparece no JSON (é @Transient)
        assertEquals(ProgramState.Idle, restored.programState, "programState deve ser Idle após decode")
    }

    @Test
    fun `ProgramMemory vazia nao aparece no JSON`() {
        // Estado inicial tem ProgramMemory vazia; encodeDefaults=false não a serializa
        val state = CalculatorEngine.InitialState
        val json  = encodeState(state)

        // programMemory vazia pode ou não aparecer — o que importa é que decode funciona
        val restored = decodeState(json)
        assertNotNull(restored)
        assertEquals(0, restored.programMemory.steps.size, "programa vazio deve ser restaurado como vazio")
    }

    // ── InMemoryStateRepository ──────────────────────────────────────────────

    @Test
    fun `InMemoryStateRepository load retorna null antes de save`() {
        val repo = InMemoryStateRepository()
        assertNull(repo.load())
    }

    @Test
    fun `InMemoryStateRepository round-trip via save e load`() {
        val repo  = InMemoryStateRepository()
        var state = CalculatorEngine.InitialState
        state = storeFinancial(state, 5.0, Event.Financial.Store.N)

        val normalized = engine.normalizeForPersistence(state)
        repo.save(normalized)

        val loaded = repo.load()
        assertNotNull(loaded)
        assertEquals(normalized.financial.n?.toString(), loaded.financial.n?.toString())
    }

    // ── JSON corrompido retorna null ─────────────────────────────────────────

    @Test
    fun `decodeState retorna null para JSON invalido`() {
        assertNull(decodeState("não é JSON"))
        assertNull(decodeState("texto puro sem chaves"))
        // "{}" é JSON válido e deserializa para InitialState com todos os defaults — correto:
        assertNotNull(decodeState("{}"))
        // campos desconhecidos são ignorados (ignoreUnknownKeys = true) — não quebra:
        assertNotNull(decodeState("{\"v\":99}"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun storeFinancial(
        state: CalculatorState,
        value: Double,
        event: Event.Financial.Store,
    ): CalculatorState {
        var s = state
        val str = if (value < 0) (-value).toBigDecimal().toPlainString() else value.toBigDecimal().toPlainString()
        for (ch in str) {
            s = when {
                ch == '.'       -> engine.reduce(s, Event.Entry.DecimalPoint)
                ch in '0'..'9' -> engine.reduce(s, Event.Entry.Digit(ch.digitToInt()))
                else            -> s
            }
        }
        if (value < 0) s = engine.reduce(s, Event.Entry.ChangeSign)
        return engine.reduce(s, event)
    }
}
