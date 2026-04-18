package com.arcom.hp12c.engine.state

import com.arcom.hp12c.engine.error.Hp12cError

/**
 * Raiz do estado imutável da calculadora. Todo o aparelho — pilha, registradores financeiros,
 * memórias do usuário, formato do visor, flag de erro pendente e estado de programação —
 * cabe aqui. `reduce(state, event): state` é a única transição possível.
 *
 * Este é o objeto serializado na memória contínua (ver `PROMPT_MESTRE.md` invariante 5).
 *
 * Ver Seção 3.5 de `arquitetura/engine-interface.md`.
 */
data class CalculatorState(
    val stack:     Stack              = Stack(),
    val financial: FinancialRegisters = FinancialRegisters(),
    val memory:    MemoryRegisters    = MemoryRegisters(),
    val display:   DisplayFormat      = DisplayFormat.Default,

    /** Flag C da HP (STO EEX): `true` = juros compostos para período fracionário. Default = simples. */
    val compoundFractionFlag: Boolean = false,

    /**
     * Buffer de digitação em curso. **Invariante:** é não-nulo **se e somente se**
     * `stack.isEntering == true`.
     *
     * Mantemos a entrada como texto cru (e não como `Hp12cDecimal`) enquanto `isEntering` está
     * ligado porque:
     *   - estados intermediários como `"1."` (ponto pendurado), `"1.0"` (zero à direita
     *     digitado explicitamente), `"1E"` (expoente ainda não digitado depois de `EEX`) e
     *     `"1E-"` (sinal do expoente invertido antes dos dígitos) não têm representação válida
     *     como `Hp12cDecimal` sem perder informação visual;
     *   - `CHS` durante entrada inverte sinal da mantissa ou do expoente, algo simples sobre
     *     string e confuso sobre decimal.
     *
     * O reducer "comita" o buffer (parse para `Hp12cDecimal` → escreve em `stack.x` → zera o
     * buffer e marca `isEntering = false`) ao receber qualquer evento fora da família
     * `Event.Entry.*` (com a exceção óbvia de `Display` e `AcknowledgeError`, que preservam a
     * digitação em curso por serem puramente cosméticos).
     */
    val entryBuffer: String? = null,

    /**
     * Último erro ainda não limpado pelo usuário. Se não-nulo, o visor mostra "Error N"
     * e a próxima tecla é interpretada como `AcknowledgeError` antes de qualquer outro efeito.
     */
    val pendingError: Hp12cError? = null,

    /** Reservado para a Fase 3 (modo PRGM). Em Fase 0/1/2 permanece `Idle`. */
    val programState: ProgramState = ProgramState.Idle,
) {
    init {
        // Invariante documentada acima: buffer e flag andam juntos. Violações travam cedo.
        check((entryBuffer != null) == stack.isEntering) {
            "entryBuffer=${entryBuffer?.let { "'$it'" } ?: "null"} mas stack.isEntering=${stack.isEntering}"
        }
    }
}

/**
 * Estado de programação. Apenas `Idle` na Fase 1; a Fase 3 adicionará `Running`, `Editing`, etc.
 */
sealed class ProgramState {
    object Idle : ProgramState()
    // Fase 3: data class Running(val pc: Int, ...), data class Editing(val cursor: Int, ...)
}
