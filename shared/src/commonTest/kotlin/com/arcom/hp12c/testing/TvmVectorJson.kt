package com.arcom.hp12c.testing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tipos de desserialização do arquivo canônico
 * `shared/src/commonTest/resources/test-vectors/tvm-vectors.json` (cópia do
 * original em `.claude/skills/hp12c-simulator/test-vectors/`).
 *
 * Convenções:
 *   - Campos numéricos em [TvmInputsJson] são `Double` porque o JSON mistura
 *     inteiros (`5`, `24`, `360`) e decimais (`3.25`, `2.7214`). `kotlinx
 *     .serialization` aceita coerção implícita; a engine recebe essas
 *     strings via `Hp12cDecimal.of(Double.toString())`.
 *   - `solve_for` é `String` ("FV" | "PV" | "PMT" | "n" | "i") — enum seria
 *     overhead desnecessário: o `when` no helper `inputsToEvents` consome a
 *     string diretamente, e o JSON é a fonte de verdade do alfabeto.
 *   - `mode` idem ("END" | "BEGIN").
 *   - Campos do topo do JSON (`schema_version`, `description`,
 *     `sign_convention_note`, `meta`) são ignorados via `ignoreUnknownKeys
 *     = true` em [VectorsJson] — eles existem para documentação humana,
 *     não para execução.
 *
 * Se aparecer um vetor novo com campo que a engine não reconhece, o build
 * não quebra — só o comportamento fica ignorado com log de warning no
 * `kotlinx`. Isso é aceitável porque os campos canônicos são fixos pelo
 * schema v1.0 da skill.
 */

@Serializable
internal data class TvmVectorsFile(
    val vectors: List<TvmVectorJson>,
)

@Serializable
internal data class TvmVectorJson(
    val id: String,
    val source: String,
    val description: String,
    val inputs: TvmInputsJson,
    val solve_for: String,
    val expected: String,
    val format: String,
    val notes: String? = null,
)

@Serializable
internal data class TvmInputsJson(
    val n: Double,
    val i: Double,
    val PV: Double,
    val PMT: Double,
    val FV: Double,
    val mode: String,
)

/**
 * Parser com `ignoreUnknownKeys = true` (tolera `schema_version`, `meta` etc.).
 * `isLenient = false` mantido para não aceitar JSON malformado —
 * vetores vêm de fonte controlada, erro de sintaxe é sempre bug.
 */
internal val VectorsJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
}
