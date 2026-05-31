package br.com.alyssonmyller.calculus.testing

import kotlinx.serialization.Serializable

/**
 * Deserializa `test-vectors/calendario-vectors.json` via `kotlinx.serialization`.
 * Campos desconhecidos (ex.: `schema_version`, `description` do file) são ignorados via
 * `ignoreUnknownKeys = true` do [VectorsJson] definido em `TvmVectorJson.kt`.
 *
 * Schema do vetor (todos os campos string/int para evitar ruído de ponto flutuante):
 * - `op`: `"date"` ou `"dys"`
 * - `mode`: `"MDY"` ou `"DMY"`
 * - `date`: data base no formato HP (ex.: `"6.301994"`) — presente em op=date
 * - `date1`, `date2`: datas no formato HP — presentes em op=dys
 * - `days`: número inteiro de dias — presente em op=date (pode ser negativo)
 * - `expected_x`: resultado X esperado como string HP — ausente em vetores de erro
 * - `expected_y`: resultado Y esperado como string HP — opcional
 * - `error`: `"Error N"` — presente em vetores de erro
 * - `format`: `"FIX N"` / `"SCI N"` / `"ENG N"` — configura o display antes de comparar
 */
@Serializable
internal data class CalendarioVectorsFile(
    val vectors: List<CalendarioVectorJson>,
)

@Serializable
internal data class CalendarioVectorJson(
    val id:          String,
    val source:      String,
    val description: String,
    val op:          String,
    val mode:        String,
    val date:        String?  = null,
    val date1:       String?  = null,
    val date2:       String?  = null,
    val days:        Int?     = null,
    val expected_x:  String?  = null,
    val expected_y:  String?  = null,
    val error:       String?  = null,
    val format:      String,
    val notes:       String?  = null,
)
