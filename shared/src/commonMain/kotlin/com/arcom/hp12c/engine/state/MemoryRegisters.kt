package com.arcom.hp12c.engine.state

import com.arcom.hp12c.engine.math.Hp12cDecimal

/**
 * Identificador dos registradores de dados da HP 12C: R0..R9 + registrador indireto Ri.
 * O `code` é o caractere exibido quando o usuário pressiona `f MEM` ou navega via `RCL`.
 */
enum class RegisterId(val code: String) {
    R0("0"), R1("1"), R2("2"), R3("3"), R4("4"),
    R5("5"), R6("6"), R7("7"), R8("8"), R9("9"),
    RI("i"),
}

/**
 * Armazenamento de dados do usuário (STO/RCL). Registrador ausente lê como zero — espelha o
 * comportamento da HP após `CLEAR REG` ou após uma primeira inicialização. Ver Seção 3.3 de
 * `arquitetura/engine-interface.md`.
 *
 * A Fase 2 estende este modelo com os 6 registradores estatísticos (mapeados para R1..R6
 * quando `Σ+` é usado); a Fase 3 o mescla com o espaço de programa.
 */
data class MemoryRegisters(
    private val values: Map<RegisterId, Hp12cDecimal> = emptyMap(),
) {
    operator fun get(id: RegisterId): Hp12cDecimal =
        values[id] ?: Hp12cDecimal.ZERO

    fun store(id: RegisterId, value: Hp12cDecimal): MemoryRegisters =
        copy(values = values + (id to value))

    fun clearAll(): MemoryRegisters = MemoryRegisters()
}
