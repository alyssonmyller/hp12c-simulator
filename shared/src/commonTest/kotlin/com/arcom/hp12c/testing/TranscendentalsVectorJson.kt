package br.com.alyssonmyller.calculus.testing

import kotlinx.serialization.Serializable

/**
 * Tipos de desserialização do arquivo canônico
 * `shared/src/commonTest/resources/test-vectors/transcendentais-vectors.json`
 * (cópia mantida em sync com `.claude/skills/hp12c-simulator/test-vectors/`).
 *
 * Schema v1.0 — 34 vetores cobrindo as 10 teclas matemáticas da Seção 7 do manual
 * + as 3 teclas de percentagem da Seção 2. Cada vetor descreve **uma única operação**
 * aplicada a uma pilha montada via o campo [stack].
 *
 * ### Convenções
 *
 * - **[stack]**: array de 1 a 4 strings onde o **último** elemento é X (visor) e os
 *   anteriores são Y, Z, T na ordem em que foram empilhados. Ex.: `["300","14"]` significa
 *   Y=300, X=14. Níveis não citados são 0 (default após `f CLEAR REG`).
 *
 * - **[operation]**: string literal do JSON que mapeia para uma instância concreta de
 *   [br.com.alyssonmyller.calculus.engine.event.Event.Transcendental] ou
 *   [br.com.alyssonmyller.calculus.engine.event.Event.Percent]. O mapping é feito no runner do teste
 *   (não em código de produção) — dupla de freios: se alguém renomear o literal do JSON,
 *   o teste quebra explícito em vez de silenciosamente passar-sem-cobertura.
 *
 * - **[expected]** vs **[error]**: mutuamente exclusivos. Um dos dois é `null` em cada vetor:
 *   vetores de "caminho feliz" trazem `expected` (string já formatada em [format]); vetores
 *   de erro trazem `error` = `"Error N"` (N ∈ 0..9) para validar o `Hp12cError` esperado.
 *
 * - **[format]**: `"FIX 2"`, `"FIX 9"`, `"SCI 4"`, `"ENG 2"`. Parseado pelo mesmo helper
 *   que o `TvmVectorsTest` usa. Define `CalculatorState.display` antes da operação.
 *
 * - **[stack_after]**: opcional, mapa `"y"|"z"|"t"` → valor esperado **como string crua**
 *   (não formatada — `"300"` literal, não `"300.00"`) para asserções sobre níveis da pilha
 *   além de X. Usado pelos vetores `trans-pct-retain-*` que provam a idiossincrasia
 *   "% retém Y". Vazio/null significa "não checar demais níveis".
 *
 * - **[notes]**: documentação humana, ignorada pelo runner.
 *
 * Campos do topo do JSON (`schema_version`, `description`, `conventions`, `meta`) são
 * ignorados via `ignoreUnknownKeys = true` do [VectorsJson] já definido em
 * [TvmVectorJson.kt] — reutilizamos o mesmo `Json` configurado.
 */

@Serializable
internal data class TranscendentalsVectorsFile(
    val vectors: List<TranscendentalsVectorJson>,
)

@Serializable
internal data class TranscendentalsVectorJson(
    val id: String,
    val source: String,
    val description: String,
    val stack: List<String>,
    val operation: String,
    val expected: String? = null,
    val error: String? = null,
    val format: String,
    val stack_after: Map<String, String>? = null,
    val notes: String? = null,
)
