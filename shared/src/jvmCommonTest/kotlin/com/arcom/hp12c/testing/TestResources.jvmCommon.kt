package com.arcom.hp12c.testing

/**
 * Impl JVM/Android de [readTestResource]. Serve os dois targets porque o
 * source set `jvmCommonTest` é pai comum de `jvmTest` e `androidUnitTest`
 * (ver `shared/build.gradle.kts`).
 *
 * Usa o `ClassLoader` do lambda anônimo (`object {}::class.java.classLoader`)
 * em vez de `TestResources::class.java` porque `TestResources` é um nome de
 * top-level declaration em Kotlin: `::class.java` dele dá o Kotlin object
 * wrapper, e dependendo do compilador/minificador isso às vezes resolve
 * para um classloader diferente do de teste. O idiom `object{}::class.java`
 * é o jeito canônico em KMP para pegar "o classloader deste arquivo".
 *
 * Kotlin Multiplatform copia `shared/src/commonTest/resources/**/*`
 * automaticamente para `build/processedResources/{jvm,android}/test/`
 * durante `processTestResources`, então qualquer arquivo dentro de
 * `commonTest/resources/` fica acessível pelo caminho relativo à raiz
 * do classpath.
 */
actual fun readTestResource(path: String): String {
    val classLoader = object {}::class.java.classLoader
        ?: error(
            "ClassLoader nulo ao tentar carregar '$path'. Ambiente de teste " +
                "provavelmente está executando via bootstrap classloader — " +
                "reporte em arquitetura/engine-interface.md."
        )
    val stream = classLoader.getResourceAsStream(path)
        ?: error(
            "Recurso de teste não encontrado no classpath: '$path'.\n" +
                "Verifique se o arquivo está em " +
                "shared/src/commonTest/resources/$path e se o build " +
                "processou os recursos de teste (./gradlew " +
                ":shared:processTestResources)."
        )
    return stream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
}
