package com.arcom.hp12c.testing

/**
 * Leitura de recursos textuais do classpath/bundle de testes em multiplataforma.
 *
 * Existe para que a suíte `TvmVectorsTest` possa carregar o arquivo canônico
 * `test-vectors/tvm-vectors.json` (fonte única de verdade dos 18 vetores TVM,
 * extraídos dos 3 PDFs oficiais e mantidos em
 * `.claude/skills/hp12c-simulator/test-vectors/`, com cópia em
 * `shared/src/commonTest/resources/test-vectors/`) diretamente de `commonTest`,
 * sem precisar duplicar o conteúdo em cada plataforma-específica.
 *
 * **Contrato.**
 *   - `path` é relativo à raiz do classpath/bundle de testes. Para o JSON dos
 *     vetores o caminho canônico é `"test-vectors/tvm-vectors.json"`.
 *   - A string retornada é UTF-8 puro, sem BOM, com quebras de linha
 *     preservadas. O parser JSON (`kotlinx.serialization`) é tolerante a
 *     `\n`/`\r\n` então não normalizamos aqui.
 *   - Falha em achar o recurso **deve** lançar exceção (não devolver `null`
 *     ou string vazia) — um teste que perde seu vetor é bug de build, não
 *     condição recuperável.
 *
 * **Implementações.**
 *   - JVM/Android (`jvmCommonTest`): `object{}::class.java.classLoader
 *     .getResourceAsStream(path)`. KMP copia `commonTest/resources/**/*` para
 *     `processedResources/{jvm,android}/test/` automaticamente via Gradle.
 *   - iOS (`iosTest`): stub `TODO` — Fase 4 implementa via
 *     `NSBundle.mainBundle.pathForResource(...)` + `NSString
 *     .stringWithContentsOfFile`. Testes iOS não rodam até o setup Xcode
 *     estar pronto, então o `TODO` só dispara se alguém explicitamente
 *     chamar `./gradlew :shared:iosSimulatorArm64Test`.
 */
expect fun readTestResource(path: String): String
