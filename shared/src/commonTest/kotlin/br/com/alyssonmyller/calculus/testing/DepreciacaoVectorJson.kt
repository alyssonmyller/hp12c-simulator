package br.com.alyssonmyller.calculus.testing

import kotlinx.serialization.Serializable

/**
 * Deserializa `test-vectors/depreciacao-vectors.json` via `kotlinx.serialization`.
 * Campos desconhecidos ignorados via `ignoreUnknownKeys = true` do [VectorsJson].
 *
 * Schema stateful: `operations` é lista de `{op, value?}` aplicada ao InitialState.
 * Operações: `store_n`, `store_i`, `store_pv`, `store_fv`, `enter_x`, `sl`, `soyd`, `db`.
 *
 * `enter_x` insere `value` diretamente em X (digitação sem armazenar em registrador financeiro).
 *
 * Após a tecla de depreciação:
 * - `expected_x` = D_j (depreciação do ano j)
 * - `expected_y` = RDV_j (valor residual ao fim do ano j)
 * - `error`      = "Error N" em vetores de erro
 */
@Serializable
internal data class DepreciacaoOperationJson(
    val op:    String,
    val value: Double? = null,
)

@Serializable
internal data class DepreciacaoVectorsFile(
    val vectors: List<DepreciacaoVectorJson>,
)

@Serializable
internal data class DepreciacaoVectorJson(
    val id:          String,
    val source:      String,
    val description: String,
    val operations:  List<DepreciacaoOperationJson>,
    val expected_x:  String? = null,
    val expected_y:  String? = null,
    val error:       String? = null,
    val format:      String,
    val notes:       String? = null,
)
