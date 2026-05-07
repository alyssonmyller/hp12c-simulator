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

    /**
     * Modo de formatação de datas: M.DY (padrão) ou D.MY.
     * Controlado por `g D.MY` / `g M.DY` (ver `formulas/calendario.md`, Seção 1.3).
     */
    val dateFormat: DateFormat = DateFormat.MDY,

    /**
     * Registradores de fluxo de caixa irregular da HP 12C.
     * `cf0 == null` significa que `g CFo` ainda não foi pressionado — condição que dispara
     * Error 7 ao chamar `f NPV` ou `f IRR`.
     * Ver `formulas/npv-irr.md` §2 e `state/CashflowRegisters.kt`.
     */
    val cashflow: CashflowRegisters = CashflowRegisters(),

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

    /**
     * Flag interna da ambiguidade §8.6 de `formulas/estatistica.md`: `true` quando
     * `ŷ,r` ou `x̂,r` concluiu com sucesso mas o denominador de `r` é ≤ 0, tornando
     * a correlação inválida. O próximo `x⇆y` (para trazer `r` ao visor) falha com
     * Error 2 em vez de realizar o swap. Zerado por `Σ+`, `Σ-`, `f CLEAR Σ` e por
     * qualquer outra operação que mude o estado estatístico.
     */
    val statisticsRInvalid: Boolean = false,

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
