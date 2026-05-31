package br.com.alyssonmyller.calculus.engine.persistence

import br.com.alyssonmyller.calculus.engine.state.CalculatorState

/**
 * Contrato de persistência da memória contínua da HP 12C.
 *
 * A engine ([br.com.alyssonmyller.calculus.engine.CalculatorEngine]) é um reducer puro — ela não tem I/O.
 * Persistência é responsabilidade exclusiva da camada de plataforma.
 * Ver `arquitetura/persistence.md` §1 e §5.
 *
 * **Fluxo típico:**
 * ```
 * // Startup
 * val state = repo.load() ?: CalculatorEngine.InitialState
 *
 * // A cada tecla
 * state = engine.reduce(state, event)
 * repo.save(engine.normalizeForPersistence(state))
 * ```
 *
 * **Contrato de [load]:** nunca lança — retorna `null` em qualquer falha.
 * **Contrato de [save]:** leve (≤ 2 KB de JSON); implementações devem ser síncronas
 * ou usar I/O em background transparente (ex: `SharedPreferences.apply()`).
 */
interface StateRepository {

    /**
     * Carrega o último snapshot persistido.
     * Retorna `null` se não houver snapshot (primeiro uso), se o JSON estiver corrompido,
     * ou se a versão do schema for incompatível.
     * Jamais lança — qualquer exceção deve ser capturada internamente.
     */
    fun load(): CalculatorState?

    /**
     * Persiste o snapshot normalizado. Deve receber o resultado de
     * [br.com.alyssonmyller.calculus.engine.CalculatorEngine.normalizeForPersistence] para garantir
     * que `entryBuffer == null` e `pendingError == null`.
     */
    fun save(state: CalculatorState)
}
