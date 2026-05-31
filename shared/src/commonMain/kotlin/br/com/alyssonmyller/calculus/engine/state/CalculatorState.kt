package br.com.alyssonmyller.calculus.engine.state

import br.com.alyssonmyller.calculus.engine.error.Hp12cError
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Raiz do estado imutável da calculadora. Todo o aparelho — pilha, registradores financeiros,
 * memórias do usuário, formato do visor, flag de erro pendente e estado de programação —
 * cabe aqui. `reduce(state, event): state` é a única transição possível.
 *
 * Este é o objeto serializado na memória contínua (ver `PROMPT_MESTRE.md` invariante 5).
 *
 * Ver Seção 3.5 de `arquitetura/engine-interface.md`.
 */
@Serializable
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
     * Modo Algébrico (f EEX / "ALG"): `true` = modo algébrico ativo.
     * Em modo ALG os operadores binários acumulam com precedência e `ENTER` avalia.
     * Padrão de fábrica: `false` (modo RPN).
     * Manual HP 12C Platinum, Appendix A, p. 185.
     */
    val algebraicMode: Boolean = false,

    /**
     * Pilha de operadores do modo ALG (transiente — não persistida).
     * Cada elemento é o ordinal de [AlgOp]: 0=PLUS,1=MINUS,2=MUL,3=DIV,4=LPAREN.
     */
    @Transient val algOpStack: List<Int> = emptyList(),

    /**
     * Pilha de operandos do modo ALG (transiente — não persistida).
     * Armazena os valores já "commitados" aguardando um operador de menor precedência.
     */
    @Transient val algValStack: List<String> = emptyList(),

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
    /**
     * Excluído da persistência — `normalizeForPersistence()` garante que o snapshot salvo
     * sempre tem `entryBuffer = null` e `stack.isEntering = false`. Ver §3 de
     * `arquitetura/persistence.md`.
     */
    @Transient val entryBuffer: String? = null,

    /**
     * Último erro ainda não limpado pelo usuário. Excluído da persistência (estado
     * transiente de UI — restaurar um erro ao abrir o app seria confuso).
     */
    @Transient val pendingError: Hp12cError? = null,

    /**
     * Flag interna da ambiguidade §8.6 de `formulas/estatistica.md`: `true` quando
     * `ŷ,r` ou `x̂,r` concluiu com sucesso mas o denominador de `r` é ≤ 0, tornando
     * a correlação inválida. O próximo `x⇆y` (para trazer `r` ao visor) falha com
     * Error 2 em vez de realizar o swap. Zerado por `Σ+`, `Σ-`, `f CLEAR Σ` e por
     * qualquer outra operação que mude o estado estatístico.
     */
    val statisticsRInvalid: Boolean = false,

    /**
     * Estado de runtime da programação — qual modo está ativo e onde está o PC/cursor.
     * **@Transient**: não é persistido. Ao reabrir o app a engine volta a `Idle`.
     * Ver `arquitetura/programacao.md` §3 e `state/ProgramStep.kt`.
     */
    @Transient val programState: ProgramState = ProgramState.Idle(),

    /**
     * Fita de programa: lista de passos gravados pelo usuário no modo PRGM.
     * Faz parte da memória contínua (manual, Apêndice B, p. 191).
     *
     * Persistida via `encodeState`/`decodeState` — `ProgramStep` é `@Serializable`
     * (sealed class com discriminador de tipo automático do kotlinx.serialization).
     * `ProgramStep.KeyStep` armazena teclas como `(keyCode: String, keyParam: Int)`,
     * desacoplado da hierarquia `Event`.
     */
    val programMemory: ProgramMemory = ProgramMemory(),
) {
    init {
        // Invariante documentada acima: buffer e flag andam juntos. Violações travam cedo.
        check((entryBuffer != null) == stack.isEntering) {
            "entryBuffer=${entryBuffer?.let { "'$it'" } ?: "null"} mas stack.isEntering=${stack.isEntering}"
        }
    }
}
