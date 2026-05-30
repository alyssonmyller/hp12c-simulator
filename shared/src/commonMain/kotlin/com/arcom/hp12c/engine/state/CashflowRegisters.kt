package br.com.alyssonmyller.calculus.engine.state

import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Uma entrada na lista de fluxos de caixa: o valor CF_j e o número de vezes que se repete
 * consecutivamente (N_j ∈ [1, 99]). O valor `count = 1` é o padrão após `g CFj`.
 *
 * Ver Seção 1 de `formulas/npv-irr.md` e manual HP 12C Platinum, Seção 8, p. 79-104.
 */
@Serializable
data class CashflowEntry(
    @Contextual val amount: Hp12cDecimal,
    val count: Int = 1,   // N_j ∈ [1, 99]; definido por `g Nj`
)

/**
 * Armazenamento dos fluxos de caixa irregulares da HP 12C.
 *
 * - `cf0 = null` significa que `g CFo` ainda não foi pressionado — condição que dispara
 *   Error 7 ao chamar `f NPV` ou `f IRR`.
 * - `flows` contém no máximo 79 entradas (`CashflowEntry`); somado ao CF0 = 80 fluxos
 *   totais, que é o limite da HP 12C Platinum.
 * - `g CFo` substitui toda a estrutura por `CashflowRegisters(cf0 = novoValor)`:
 *   quaisquer `g CFj` / `g Nj` anteriores são **apagados** (manual, p. 80).
 */
@Serializable
data class CashflowRegisters(
    @Contextual val cf0: Hp12cDecimal? = null,
    val flows: List<CashflowEntry> = emptyList(),
)
