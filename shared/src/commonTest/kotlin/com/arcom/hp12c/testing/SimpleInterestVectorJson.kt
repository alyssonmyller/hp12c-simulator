package com.arcom.hp12c.testing

import kotlinx.serialization.Serializable

/**
 * Tipos de desserialização do arquivo canônico
 * `shared/src/commonTest/resources/test-vectors/juros-simples-vectors.json`.
 *
 * Schema: `inputs: {n, i, PV}` → `Event.Financial.SimpleInterest` → comparar
 * `expected_x` (INT) e opcionalmente `expected_y` (PV retido na pilha).
 */

@Serializable
internal data class SimpleInterestVectorsFile(
    val vectors: List<SimpleInterestVectorJson>,
)

@Serializable
internal data class SimpleInterestVectorJson(
    val id: String,
    val source: String,
    val description: String,
    val inputs: SimpleInterestInputsJson,
    val query: String,
    val expected_x: String,
    val expected_y: String? = null,
    val format: String,
    val notes: String? = null,
)

@Serializable
internal data class SimpleInterestInputsJson(
    val n: Double,
    val i: Double,
    val PV: Double,
)
