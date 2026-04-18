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
