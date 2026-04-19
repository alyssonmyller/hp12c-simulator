package com.arcom.hp12c.engine.math

/**
 * Número decimal com **10 dígitos significativos** e arredondamento **HALF_EVEN** (banker's) —
 * espelha fielmente a aritmética BCD da HP 12C Platinum. Toda operação aritmética (`+`, `-`,
 * `×`, `÷`, `^`) executa o arredondamento a 10 dígitos no resultado, conforme
 * `referencias/bcd-rounding.md` da skill `hp12c-simulator`.
 *
 * Modelado como **expect class** para usar `java.math.BigDecimal` no alvo JVM/Android e uma
 * impl nativa (a ser implementada na Fase 4) em iOS. A API é deliberadamente mínima:
 * operadores Kotlin, um `powInt` para expoentes inteiros (que basta para TVM com `n` inteiro)
 * e um `pow` geral que depende de ln/exp (Fase 1 passo 2).
 *
 * **Igualdade por valor numérico.** Dois `Hp12cDecimal` iguais se `compareTo == 0` — ou seja,
 * `of("1.00") == of("1.0")`. Isso diverge de `java.math.BigDecimal.equals`, que compara
 * escala também, mas espelha melhor o que o usuário da HP enxerga.
 *
 * **Divisão por zero lança `ArithmeticException`.** O reducer é responsável por capturar
 * e mapear para `Hp12cError.DivisionByZero` no estado.
 */
expect class Hp12cDecimal : Comparable<Hp12cDecimal> {

    // --- Aritmética ---
    operator fun plus(other: Hp12cDecimal):  Hp12cDecimal
    operator fun minus(other: Hp12cDecimal): Hp12cDecimal
    operator fun times(other: Hp12cDecimal): Hp12cDecimal
    operator fun div(other: Hp12cDecimal):   Hp12cDecimal
    operator fun unaryMinus():               Hp12cDecimal

    /** `(1 + i)^n` com `n` inteiro; solução TVM fechada usa isso. Implementado na Fase 1 passo 1. */
    fun powInt(exponent: Int): Hp12cDecimal

    /** `base^exponent` com expoente arbitrário; delega a `exp(exponent * ln(base))`. Fase 1 passo 6. */
    fun pow(exponent: Hp12cDecimal): Hp12cDecimal

    /**
     * Logaritmo natural. Lança [ArithmeticException] se `this ≤ 0` (o reducer captura e mapeia
     * para `Hp12cError.LogOfNonPositive` ou `Hp12cError.TvmInvalidSigns`, conforme o contexto
     * da tecla que originou o cálculo). Implementado via série arctanh com redução de argumento
     * por potências de 2, precisão interna ≥ 13 dígitos arredondados ao final para 10 —
     * ver `referencias/bcd-rounding.md` da skill `hp12c-simulator`.
     */
    fun ln(): Hp12cDecimal

    /**
     * Função exponencial `e^this`. Implementada via série de Taylor com redução de argumento
     * por duplicação (`exp(x) = exp(x/2)²`), precisão interna ≥ 13 dígitos arredondados no
     * final para 10. Overflow (argumento demasiado grande) propaga [ArithmeticException] —
     * reducer captura e mapeia para `Hp12cError.TvmNoConverge` no contexto TVM.
     */
    fun exp(): Hp12cDecimal

    // --- Predicados / conversões ---
    fun isZero(): Boolean

    override operator fun compareTo(other: Hp12cDecimal): Int
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String

    companion object {
        val ZERO: Hp12cDecimal
        val ONE:  Hp12cDecimal

        fun of(value: String): Hp12cDecimal
        fun of(value: Int):    Hp12cDecimal
        fun of(value: Long):   Hp12cDecimal
    }
}
