package com.arcom.hp12c.testing

import kotlinx.serialization.Serializable

/**
 * Deserializa `test-vectors/npv-irr-vectors.json` via `kotlinx.serialization`.
 * Campos desconhecidos são ignorados via `ignoreUnknownKeys = true` do [VectorsJson].
 *
 * Schema stateful: `operations` é lista de operações aplicadas ao estado inicial.
 * Operações disponíveis: `store_i`, `cfo`, `cfj`, `nj`, `npv`, `irr`.
 * `value` é `Double?` — suficiente para os valores inteiros e fracionários simples
 * usados nos vetores (ex.: -1000, 110, 10, 0.5). Nunca é uma data (sem risco de
 * perda de precisão como nos vetores de calendário).
 */
@Serializable
internal data class NpvIrrOperationJson(
    val op:    String,
    val value: Double? = null,
)

@Serializable
internal data class NpvIrrVectorsFile(
    val vectors: List<NpvIrrVectorJson>,
)

@Serializable
internal data class NpvIrrVectorJson(
    val id:          String,
    val source:      String,
    val description: String,
    val operations:  List<NpvIrrOperationJson>,
    val expected_x:  String? = null,
    val error:       String? = null,
    val format:      String,
    val notes:       String? = null,
)
