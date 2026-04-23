package com.arcom.hp12c.engine.math

/**
 * **Stub iOS do [Hp12cDecimal].** Mantém os targets iOS compilando; todas as operações
 * lançam [NotImplementedError]. A impl real entra na Fase 4, com duas opções candidatas:
 *
 *   (a) `com.ionspin.kotlin:bignum` — biblioteca multiplatform com `BigDecimal`
 *        configurável (precisão + rounding mode). É o caminho pragmático.
 *   (b) Impl manual sobre `ULong`/`String` simulando BCD 10 dígitos. Mais trabalho, mais
 *        controle, útil só se `(a)` tiver divergência numérica frente à HP.
 *
 * Como os testes da engine rodam todos em `:shared:jvmTest` (rápido, suficiente para
 * cobrir invariante numérica), esse stub não bloqueia a Fase 1/2/3. Quando a UI SwiftUI
 * for empacotada, escolhemos entre (a) e (b) olhando benchmarks reais.
 */
actual class Hp12cDecimal : Comparable<Hp12cDecimal> {

    actual operator fun plus(other: Hp12cDecimal):  Hp12cDecimal = todoIos()
    actual operator fun minus(other: Hp12cDecimal): Hp12cDecimal = todoIos()
    actual operator fun times(other: Hp12cDecimal): Hp12cDecimal = todoIos()
    actual operator fun div(other: Hp12cDecimal):   Hp12cDecimal = todoIos()
    actual operator fun unaryMinus():               Hp12cDecimal = todoIos()

    actual fun powInt(exponent: Int):          Hp12cDecimal = todoIos()
    actual fun pow(exponent: Hp12cDecimal):    Hp12cDecimal = todoIos()
    actual fun ln():                           Hp12cDecimal = todoIos()
    actual fun exp():                          Hp12cDecimal = todoIos()
    actual fun sqrt():                         Hp12cDecimal = todoIos()

    actual fun isZero(): Boolean = todoIos()

    actual override operator fun compareTo(other: Hp12cDecimal): Int = todoIos()
    actual override fun equals(other: Any?): Boolean = todoIos()
    actual override fun hashCode(): Int = todoIos()
    actual override fun toString(): String = todoIos()

    actual companion object {
        actual val ZERO: Hp12cDecimal get() = todoIos()
        actual val ONE:  Hp12cDecimal get() = todoIos()

        actual fun of(value: String): Hp12cDecimal = todoIos()
        actual fun of(value: Int):    Hp12cDecimal = todoIos()
        actual fun of(value: Long):   Hp12cDecimal = todoIos()
    }
}

private fun todoIos(): Nothing =
    TODO("Fase 4 — Hp12cDecimal iOS sobre kotlin-multiplatform-bignum ou impl manual (ver stub)")
