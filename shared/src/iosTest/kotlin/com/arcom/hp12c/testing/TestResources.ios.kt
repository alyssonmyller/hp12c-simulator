package br.com.alyssonmyller.calculus.testing

/**
 * Stub iOS de [readTestResource]. A Fase 4 vai substituir este corpo por:
 *
 * ```kotlin
 * import platform.Foundation.NSBundle
 * import platform.Foundation.NSString
 * import platform.Foundation.NSStringEncoding
 * import platform.Foundation.NSUTF8StringEncoding
 * import platform.Foundation.stringWithContentsOfFile
 *
 * actual fun readTestResource(path: String): String {
 *     val bundle = NSBundle.bundleForClass(object {}::class.java)
 *     val (name, ext) = path.substringAfterLast('/').split('.', limit = 2)
 *     val fullPath = bundle.pathForResource(name, ofType = ext, inDirectory =
 *         path.substringBeforeLast('/', missingDelimiterValue = ""))
 *         ?: error("Recurso não encontrado no bundle iOS: $path")
 *     return NSString.stringWithContentsOfFile(
 *         fullPath,
 *         encoding = NSUTF8StringEncoding,
 *         error = null,
 *     ) as String
 * }
 * ```
 *
 * Enquanto a suíte roda só em JVM/CI (Fase 1 MVP), este TODO nunca é
 * atingido — `:shared:jvmTest` e `:shared:androidUnitTest` pegam a impl
 * de `jvmCommonTest`. Chamar `./gradlew :shared:iosSimulatorArm64Test` vai
 * dar `NotImplementedError` na carga do primeiro recurso, o que é o
 * comportamento esperado até o setup Xcode.
 */
actual fun readTestResource(path: String): String =
    TODO("iOS test resources (Fase 4): implementar via NSBundle. path=$path")
