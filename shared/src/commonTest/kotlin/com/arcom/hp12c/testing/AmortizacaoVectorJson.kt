package com.arcom.hp12c.testing

import kotlinx.serialization.Serializable

/**
 * Deserializa `test-vectors/amortizacao-vectors.json` via `kotlinx.serialization`.
 * Campos desconhecidos ignorados via `ignoreUnknownKeys = true` do [VectorsJson].
 *
 * Schema stateful: `operations` é lista de `{op, value?}` aplicada ao InitialState.
 * Operações: `store_n`, `store_i`, `store_pv`, `store_pmt`, `store_fv`, `amort`.
 *
 * Após `amort`:
 * - `expected_x`  = totalInterest (X no visor)
 * - `expected_y`  = totalPrincipal (Y na pilha)
 * - `expected_pv` = saldo PV atualizado (lido de `state.financial.pv`)
 * - `error`       = "Error N" em vetores de erro
 */
@Serializable
internal data class AmortizacaoOperationJson(
    val op:    String,
    val value: Double? = null,
)

@Serializable
internal data class AmortizacaoVectorsFile(
    val vectors: List<AmortizacaoVectorJson>,
)

@Serializable
internal data class AmortizacaoVectorJson(
    val id:          String,
    val source:      String,
    val description: String,
    val operations:  List<AmortizacaoOperationJson>,
    val expected_x:  String? = null,
    val expected_y:  String? = null,
    val expected_pv: String? = null,
    val error:       String? = null,
    val format:      String,
    val notes:       String? = null,
)
