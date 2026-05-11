package com.arcom.hp12c.testing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tipos de desserialização do arquivo canônico
 * `shared/src/commonTest/resources/test-vectors/estatistica-vectors.json`.
 *
 * O schema é stateful: cada vetor contém uma lista de `operations` que são
 * aplicadas sequencialmente à engine a partir do estado inicial. Após a última
 * operação, `expected_x` e opcionalmente `expected_y` são comparados com o
 * estado final da pilha renderizado pelo [DisplayFormatter].
 *
 * Operações com campo `y`/`x` explícitos (sigma_plus/sigma_minus) empurram os
 * valores para a pilha antes de disparar o evento estatístico — o runner cuida
 * disso. A operação `sigma_plus_from_stack` dispara SigmaPlus sem empurrar nada.
 */

@Serializable
internal data class StatisticsVectorsFile(
    val vectors: List<StatisticsVectorJson>,
)

@Serializable
internal data class StatisticsVectorJson(
    val id: String,
    val source: String,
    val description: String,
    val operations: List<StatOpJson>,
    val query: String,
    val expected_x: String? = null,
    val expected_y: String? = null,
    val error: String? = null,
    val format: String,
    val notes: String? = null,
)

@Serializable
internal data class StatOpJson(
    val op: String,
    val y: Double? = null,
    val x: Double? = null,
    val value: Double? = null,
    val register: Int? = null,
    val input: Double? = null,
)
