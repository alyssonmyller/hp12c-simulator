plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    androidTarget()

    // JVM é o alvo preferencial para rodar os testes rápidos no CI —
    // commonMain é 100% puro, então o compilador JVM é o mais barato.
    jvm()

    // iOS: três targets cobrem device (arm64), simulador Apple Silicon e simulador Intel.
    // Todos compartilham a mesma source set `iosMain` via hierarchical project structure.
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "Hp12cShared"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // Source set intermediário que agrupa Android + JVM. O template default do KMP
        // (applyDefaultHierarchyTemplate) cria automaticamente iosMain, appleMain,
        // nativeMain, mas NÃO cria um pai comum entre `jvm` e `androidTarget` —
        // declaramos aqui para que `actual class Hp12cDecimal` baseada em
        // `java.math.BigDecimal` viva em um único arquivo, compartilhado pelas duas
        // plataformas JVM-like.
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
        }
        val jvmCommonTest by creating {
            dependsOn(commonTest.get())
        }

        androidMain.get().dependsOn(jvmCommonMain)
        jvmMain.get().dependsOn(jvmCommonMain)
        // Test counterparts: o source set de teste do Android no KMP 2.x é `androidUnitTest`.
        jvmTest.get().dependsOn(jvmCommonTest)
        androidUnitTest.get().dependsOn(jvmCommonTest)

        commonMain.dependencies {
            // A engine não depende de nada de plataforma, mas carregar os
            // vetores de teste em JSON (Fase 1) vai precisar de kotlinx-serialization.
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace  = "com.arcom.hp12c.shared"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Configuração de logging para qualquer task de teste JVM-like neste módulo
// (inclui :shared:jvmTest e :shared:testDebugUnitTest/:testReleaseUnitTest
// do Android). Por padrão o Gradle só imprime "BUILD SUCCESSFUL" e esconde
// a contagem de @Tests executados/passados/falhados — o que leva a pensar
// que nada rodou quando a suíte inteira está verde. Aqui pedimos:
//
//   - events: quais transições de teste são logadas. "passed" + "failed"
//     + "skipped" dá visão completa; "standardOut"/"standardError" ficam
//     fora para não poluir com `println` eventual de debug.
//   - showStandardStreams = false: idem.
//   - exceptionFormat = FULL: stack trace completo em falhas (default é
//     "short" que corta em 2-3 frames e esconde a causa real de falhas
//     em helpers como `inputsToEvents`).
//   - afterSuite: imprime o resumo agregado ("185 tests, 0 failures") no
//     final, já que o default do Gradle não mostra totais.
tasks.withType<Test>().configureEach {
    testLogging {
        events(
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
        )
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    afterSuite(
        KotlinClosure2<org.gradle.api.tasks.testing.TestDescriptor, org.gradle.api.tasks.testing.TestResult, Unit>(
            { descriptor, result ->
                // `parent == null` => é a raiz da suíte; só imprime uma vez.
                if (descriptor.parent == null) {
                    val total = result.testCount
                    val passed = result.successfulTestCount
                    val failed = result.failedTestCount
                    val skipped = result.skippedTestCount
                    val durationMs = result.endTime - result.startTime
                    println(
                        "\n=== ${this.name}: $total tests — " +
                            "$passed passed, $failed failed, $skipped skipped " +
                            "(${durationMs}ms) ==="
                    )
                }
            }
        )
    )
}
