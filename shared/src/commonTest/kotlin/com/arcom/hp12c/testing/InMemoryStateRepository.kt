package com.arcom.hp12c.testing

import com.arcom.hp12c.engine.persistence.StateRepository
import com.arcom.hp12c.engine.state.CalculatorState

/**
 * Implementação de teste de [StateRepository]: guarda o estado em memória.
 * Zero I/O, zero dependências de plataforma — ideal para testes unitários em `commonTest`.
 */
class InMemoryStateRepository : StateRepository {
    private var stored: CalculatorState? = null

    override fun load(): CalculatorState? = stored
    override fun save(state: CalculatorState) { stored = state }
}
