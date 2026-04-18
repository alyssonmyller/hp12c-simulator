package com.arcom.hp12c.engine.math

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Implementação JVM/Android do [Hp12cDecimal] baseada em [java.math.BigDecimal] com
 * [MathContext] de 10 dígitos significativos e [RoundingMode.HALF_EVEN] — exatamente a
 * combinação que a HP 12C Platinum usa em sua aritmética BCD.
 *
 * **Invariantes:**
 * - Toda operação aritmética é arredondada a 10 dígitos via `MC`.
 * - Igualdade compara por valor numérico (`compareTo == 0`), não por escala.
 * - `hashCode` deve ser consistente com `equals` — usamos `stripTrailingZeros()` para isso.
 * - Divisão por zero propaga `ArithmeticException` da biblioteca; o reducer converte.
 *
 * Ver `referencias/bcd-rounding.md` da skill `hp12c-simulator` para a justificativa dos 10
 * dígitos com HALF_EVEN e o catálogo de ambiguidades conhecidas.
 */
actual class Hp12cDecimal internal constructor(internal val value: BigDecimal)
    : Comparable<Hp12cDecimal> {

    actual operator fun plus(other: Hp12cDecimal):  Hp12cDecimal = Hp12cDecimal(value.add(other.value, MC))
    actual operator fun minus(other: Hp12cDecimal): Hp12cDecimal = Hp12cDecimal(value.subtract(other.value, MC))
    actual operator fun times(other: Hp12cDecimal): Hp12cDecimal = Hp12cDecimal(value.multiply(other.value, MC))

    /** Divisão por zero propaga `ArithmeticException` (behavior nativo de BigDecimal com MC). */
    actual operator fun div(other: Hp12cDecimal):   Hp12cDecimal = Hp12cDecimal(value.divide(other.value, MC))

    actual operator fun unaryMinus(): Hp12cDecimal = Hp12cDecimal(value.negate(MC))

    /**
     * Potenciação com expoente inteiro. [BigDecimal.pow] com [MathContext] aceita `Int` no
     * intervalo [-999_999_999, 999_999_999], mais que suficiente para `n` da HP (máx ~1e10).
     */
    actual fun powInt(exponent: Int): Hp12cDecimal = Hp12cDecimal(value.pow(exponent, MC))

    /** Potenciação geral. Requer `ln`/`exp` — implementação entra no Fase 1 passo 2. */
    actual fun pow(exponent: Hp12cDecimal): Hp12cDecimal =
        TODO("Fase 1 passo 2 — exp(exponent * ln(base)) via Transcendentals; por ora usar powInt quando n é inteiro")

    actual fun isZero(): Boolean = value.signum() == 0

    actual override operator fun compareTo(other: Hp12cDecimal): Int =
        value.compareTo(other.value)

    /** Igualdade por valor numérico: `of("1.00")` == `of("1.0")`. */
    actual override fun equals(other: Any?): Boolean =
        other is Hp12cDecimal && value.compareTo(other.value) == 0

    /** Consistente com `equals`: strip-trailing-zeros tira diferença de escala. */
    actual override fun hashCode(): Int =
        value.stripTrailingZeros().hashCode()

    /** `toPlainString` evita notação científica; a formatação de visor (FIX/SCI/ENG) é feita
     *  por `DisplayFormatter`, não aqui. */
    actual override fun toString(): String = value.toPlainString()

    actual companion object {
        /** Contrato numérico da HP 12C: 10 dígitos significativos, arredondamento banker's. */
        private val MC: MathContext = MathContext(10, RoundingMode.HALF_EVEN)

        actual val ZERO: Hp12cDecimal = Hp12cDecimal(BigDecimal.ZERO)
        actual val ONE:  Hp12cDecimal = Hp12cDecimal(BigDecimal.ONE)

        /** Parse de string decimal. Aplica `MC` na construção para descartar dígitos excedentes. */
        actual fun of(value: String): Hp12cDecimal = Hp12cDecimal(BigDecimal(value, MC))
        actual fun of(value: Int):    Hp12cDecimal = Hp12cDecimal(BigDecimal(value).round(MC))
        actual fun of(value: Long):   Hp12cDecimal = Hp12cDecimal(BigDecimal(value).round(MC))
    }
}
