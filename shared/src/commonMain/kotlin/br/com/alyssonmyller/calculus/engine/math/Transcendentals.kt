package br.com.alyssonmyller.calculus.engine.math

/**
 * Fachada das funções transcendentes `ln`, `exp`, `pow` e `sqrt` sobre [Hp12cDecimal].
 *
 * A partir da Fase 1 passo 6, a aritmética real vive em [Hp12cDecimal.ln], [Hp12cDecimal.exp]
 * e [Hp12cDecimal.pow] (impl em `jvmCommonMain` via `BigDecimal` com precisão estendida,
 * stub em `iosMain`). Este objeto continua útil como ponto único de documentação e como
 * lugar onde futuras funções sem contraparte na `expect class` (p.ex. `sqrt` na Fase 2)
 * vão aterrissar.
 *
 * Decisão de design reafirmada: nenhum uso de `kotlin.math.*` nem de `Double` — toda a
 * aritmética passa pela `expect class`, preservando o invariante de 10 dígitos BCD com
 * HALF_EVEN documentado em `referencias/bcd-rounding.md` da skill `hp12c-simulator`.
 */
internal object Transcendentals {

    /** Logaritmo natural. Delega a [Hp12cDecimal.ln]. */
    fun ln(x: Hp12cDecimal): Hp12cDecimal = x.ln()

    /** Exponencial. Delega a [Hp12cDecimal.exp]. */
    fun exp(x: Hp12cDecimal): Hp12cDecimal = x.exp()

    /** `base^exponent` com expoente arbitrário. Delega a [Hp12cDecimal.pow]. */
    fun pow(base: Hp12cDecimal, exponent: Hp12cDecimal): Hp12cDecimal = base.pow(exponent)

    /** Raiz quadrada via Newton-Raphson sobre [Hp12cDecimal]. Fase 2. */
    fun sqrt(x: Hp12cDecimal): Hp12cDecimal =
        TODO("Fase 2 — Newton-Raphson sobre Hp12cDecimal (tecla √x)")
}
