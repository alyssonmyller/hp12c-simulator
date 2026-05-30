package br.com.alyssonmyller.calculus.engine.persistence

import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal
import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimalSerializer
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

/**
 * Módulo de serialização que registra o [Hp12cDecimalSerializer] contextual.
 * Deve ser passado para toda instância [Json] usada por persistência.
 *
 * Ver `arquitetura/persistence.md` §4.3.
 */
val PersistenceSerializersModule = SerializersModule {
    contextual(Hp12cDecimal::class, Hp12cDecimalSerializer)
}

/**
 * Instância [Json] configurada para persistência de [CalculatorState]:
 *
 * - [Json.serializersModule]: registra `Hp12cDecimal` via `@Contextual`.
 * - [Json.ignoreUnknownKeys]: tolera futuras adições de campos sem quebrar
 *   snapshots antigos.
 * - [Json.encodeDefaults]: `false` — campos com valor default são omitidos,
 *   mantendo o JSON compacto (< 300 bytes em estado típico, ≤ 2 KB no pior caso).
 *
 * Ver `arquitetura/persistence.md` §4.3 e §11.4.
 */
val PersistenceJson = Json {
    serializersModule  = PersistenceSerializersModule
    ignoreUnknownKeys  = true
    encodeDefaults     = false
}

// ─── Funções de encode/decode ────────────────────────────────────────────────

/** Serializa [state] para JSON string. */
fun encodeState(state: CalculatorState): String =
    PersistenceJson.encodeToString(CalculatorState.serializer(), state)

/**
 * Desserializa um JSON string para [CalculatorState].
 * Retorna `null` se o JSON for inválido ou incompatível.
 */
fun decodeState(json: String): CalculatorState? = try {
    PersistenceJson.decodeFromString(CalculatorState.serializer(), json)
} catch (_: Exception) {
    null
}
