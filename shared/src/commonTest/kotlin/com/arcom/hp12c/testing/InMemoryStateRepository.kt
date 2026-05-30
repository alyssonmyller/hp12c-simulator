package br.com.alyssonmyller.calculus.testing

import br.com.alyssonmyller.calculus.engine.persistence.StateRepository
import br.com.alyssonmyller.calculus.engine.state.CalculatorState

/**
 * Implementação de teste de [StateRepository]: guarda o estado em memória.
 * Zero I/O, zero dependências de plataforma — ideal para testes unitários em `commonTest`.
 */
class InMemoryStateRepository : StateRepository {
    private var stored: CalculatorState? = null

    override fun load(): CalculatorState? = stored
    override fun save(state: CalculatorState) { stored = state }
}
