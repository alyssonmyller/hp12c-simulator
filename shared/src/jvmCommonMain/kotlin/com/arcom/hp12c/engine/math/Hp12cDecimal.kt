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

    /**
     * Potenciação geral `base^exponent`.
     *
     * Trata as condições do Apêndice D do manual (`Error 0` para `y^x` inválido — ver
     * `referencias/error-codes.md` Seção Error 0):
     *  - `0^x` com `x ≤ 0` → [ArithmeticException]
     *  - `0^x` com `x > 0` → `ZERO`
     *  - base `< 0` com expoente não-inteiro → [ArithmeticException]
     *  - base `< 0` com expoente inteiro → delega a [powInt] (resultado exato em BCD)
     *  - base `> 0` → `exp(exponent · ln(base))` com precisão estendida
     *
     * Para expoente inteiro com base positiva, **também** delegamos a [powInt] para preservar
     * exatidão: `2^10 = 1024` tem que bater exatamente, e `exp(10·ln(2))` poderia perder 1 ULP.
     */
    actual fun pow(exponent: Hp12cDecimal): Hp12cDecimal {
        if (value.signum() == 0) {
            return if (exponent.value.signum() > 0) ZERO
            else throw ArithmeticException("0^x com x ≤ 0 (Error 0 na HP)")
        }
        // Se o expoente é exatamente inteiro, usar powInt (preciso e rápido).
        val expInt = exponent.value.toIntExactOrNull()
        if (expInt != null) return powInt(expInt)

        if (value.signum() < 0) {
            // base negativa com expoente fracionário — Error 0 na HP.
            throw ArithmeticException("base negativa com expoente não-inteiro (Error 0)")
        }
        // base > 0 e expoente não-inteiro: compor via extended precision.
        val lnExt = lnExtended(value)
        val prodExt = exponent.value.multiply(lnExt, MC_EXT)
        return Hp12cDecimal(expExtended(prodExt).round(MC))
    }

    /**
     * Logaritmo natural. Impl sobre `BigDecimal` com [MC_EXT] (13 dígitos) durante a série e
     * arredondamento final em [MC] (10 dígitos). Estratégia:
     *
     *   1. Redução de argumento: divide por 2 até `x ∈ [1, 2)`, registrando `k` potências de 2.
     *   2. Série arctanh: seja `t = (x - 1) / (x + 1)`; então
     *          `ln(x) = 2·(t + t³/3 + t⁵/5 + ...)`
     *      com `|t| < 1/3` para `x ∈ [1, 2)`, convergência geométrica rápida.
     *   3. `ln(this) = k·ln(2) + série`, onde `ln(2)` é constante pré-computada a 25 dígitos.
     *
     * Lançamos [ArithmeticException] para `this ≤ 0` (contrato do expect class — o reducer
     * captura e mapeia para o erro HP apropriado).
     */
    actual fun ln(): Hp12cDecimal =
        Hp12cDecimal(lnExtended(value).round(MC))

    /**
     * `e^this`. Redução por duplicação (`exp(x) = exp(x/2)²`) até `|x/2^k| < 0.5`, depois série
     * de Taylor `exp(y) = Σ yⁿ/n!` até o termo ser < 10⁻¹⁴, e então elevar ao quadrado `k` vezes.
     * Precisão interna 13 dígitos; resultado arredondado a 10.
     *
     * Overflow (expoente extremo) propaga [ArithmeticException] via BigDecimal — o reducer
     * traduz para `Hp12cError.TvmNoConverge` no contexto TVM.
     */
    actual fun exp(): Hp12cDecimal =
        Hp12cDecimal(expExtended(value).round(MC))

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

        /**
         * Contexto estendido (13 dígitos) usado nas séries de Taylor de `ln`/`exp`/`pow`.
         * Motivo: acumular ~3 dígitos de "gordura" além dos 10 finais garante que o último
         * dígito do resultado arredonde corretamente em HALF_EVEN. Justificativa em
         * `referencias/bcd-rounding.md` Seção 2.3 — "funções transcendentais precisam de
         * precisão interna ≥ 12 dígitos e arredondamento final para 10".
         */
        private val MC_EXT: MathContext = MathContext(13, RoundingMode.HALF_EVEN)

        /** `ln(2)` a 25 dígitos — mais do que suficiente para alimentar a redução de argumento. */
        private val LN2: BigDecimal = BigDecimal("0.6931471805599453094172321")

        actual val ZERO: Hp12cDecimal = Hp12cDecimal(BigDecimal.ZERO)
        actual val ONE:  Hp12cDecimal = Hp12cDecimal(BigDecimal.ONE)

        /** Parse de string decimal. Aplica `MC` na construção para descartar dígitos excedentes. */
        actual fun of(value: String): Hp12cDecimal = Hp12cDecimal(BigDecimal(value, MC))
        actual fun of(value: Int):    Hp12cDecimal = Hp12cDecimal(BigDecimal(value).round(MC))
        actual fun of(value: Long):   Hp12cDecimal = Hp12cDecimal(BigDecimal(value).round(MC))

        // ─── Helpers privados de ln/exp (BigDecimal com MC_EXT) ──────────────────

        /**
         * Retorna `this` como `Int` se for exatamente um inteiro dentro do intervalo de `Int`;
         * `null` caso contrário (inclui valores com parte fracionária não-nula ou fora do range).
         * Usado em `pow` para short-circuit a expoentes inteiros e não perder exatidão via
         * `exp(y · ln(x))`.
         */
        private fun BigDecimal.toIntExactOrNull(): Int? = try {
            this.toBigIntegerExact().let {
                if (it.bitLength() > 30) null else it.toInt()
            }
        } catch (e: ArithmeticException) {
            null
        }

        /**
         * `ln(x)` em precisão [MC_EXT]. Assume `x > 0`. Estratégia em 3 passos — ver docstring
         * de [Hp12cDecimal.ln] para o racional.
         */
        internal fun lnExtended(x: BigDecimal): BigDecimal {
            if (x.signum() <= 0) {
                throw ArithmeticException("ln de valor não-positivo (Error 0 na HP)")
            }
            // Passo 1: reduz para [1, 2) por fator de 2.
            val two = BigDecimal.valueOf(2L)
            var reduced = x
            var k = 0
            while (reduced.compareTo(two) >= 0) {
                reduced = reduced.divide(two, MC_EXT)
                k++
            }
            while (reduced.compareTo(BigDecimal.ONE) < 0) {
                reduced = reduced.multiply(two, MC_EXT)
                k--
            }
            // Passo 2: série arctanh — `ln(reduced) = 2·Σ t^(2i-1)/(2i-1)` com `t = (r-1)/(r+1)`.
            val t = reduced.subtract(BigDecimal.ONE, MC_EXT)
                .divide(reduced.add(BigDecimal.ONE, MC_EXT), MC_EXT)
            val t2 = t.multiply(t, MC_EXT)
            val tolerance = BigDecimal("1E-14")
            var sum = BigDecimal.ZERO
            var term = t                 // t^1 inicialmente
            var denom = 1L
            var i = 0
            while (i < 200) {
                val contribution = term.divide(BigDecimal.valueOf(denom), MC_EXT)
                sum = sum.add(contribution, MC_EXT)
                if (contribution.abs().compareTo(tolerance) < 0) break
                term = term.multiply(t2, MC_EXT)
                denom += 2
                i++
            }
            sum = sum.multiply(BigDecimal.valueOf(2L), MC_EXT)
            // Passo 3: combina com `k · ln(2)`.
            return sum.add(BigDecimal.valueOf(k.toLong()).multiply(LN2, MC_EXT), MC_EXT)
        }

        /**
         * `exp(x)` em precisão [MC_EXT]. Redução por duplicação até `|x/2^k| < 0.5`, soma a série
         * `Σ xⁿ/n!` com `n` crescente até o termo ficar abaixo de 10⁻¹⁴, e eleva ao quadrado
         * `k` vezes. Divergência vira [ArithmeticException] quando o expoente `k` explode além
         * do que o [BigDecimal] representa.
         */
        internal fun expExtended(x: BigDecimal): BigDecimal {
            // Short-circuit para zero (BigDecimal.pow sobre resultado = 1 é trivial, mas
            // evitamos rodar a série inteira).
            if (x.signum() == 0) return BigDecimal.ONE
            // Redução: y = x / 2^k com |y| < 0.5
            val half = BigDecimal("0.5")
            var y = x
            var k = 0
            while (y.abs().compareTo(half) > 0) {
                y = y.divide(BigDecimal.valueOf(2L), MC_EXT)
                k++
                // sanidade: expoente extremo — a HP12C também estoura em x ≈ 230 (e^230 ≈ 10^100).
                if (k > 400) throw ArithmeticException("exp overflow")
            }
            // Soma Taylor: 1 + y + y²/2! + y³/3! + ...
            val tolerance = BigDecimal("1E-14")
            var sum = BigDecimal.ONE
            var term = BigDecimal.ONE
            var n = 1
            while (n < 200) {
                term = term.multiply(y, MC_EXT).divide(BigDecimal.valueOf(n.toLong()), MC_EXT)
                sum = sum.add(term, MC_EXT)
                if (term.abs().compareTo(tolerance) < 0) break
                n++
            }
            // Eleva ao quadrado `k` vezes. Cada quadrado preserva MC_EXT.
            repeat(k) { sum = sum.multiply(sum, MC_EXT) }
            return sum
        }
    }
}
