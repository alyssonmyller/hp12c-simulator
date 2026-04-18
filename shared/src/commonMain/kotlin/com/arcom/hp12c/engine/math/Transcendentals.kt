package com.arcom.hp12c.engine.math

/**
 * Funções transcendentes (`ln`, `exp`, `pow`, `sqrt`) implementadas sobre [Hp12cDecimal]
 * com precisão compatível com os 10 dígitos BCD da HP 12C.
 *
 * Stub de Fase 0. A implementação real entra na Fase 1 (para `pow`, necessária a TVM) e
 * se expande na Fase 2 (quando `LN`, `EXP`, `√x` ganham teclas próprias).
 *
 * Decisão de design: não depender de `kotlin.math.*` — a biblioteca-padrão usa `Double`,
 * o que perde precisão após ~15 dígitos. Precisamos de séries/Newton sobre o próprio
 * `Hp12cDecimal` para manter fidelidade com a HP.
 */
internal object Transcendentals {

    fun ln(x: Hp12cDecimal): Hp12cDecimal =
        TODO("Fase 1 — série de Taylor com redução de argumento, precisão 10 dígitos")

    fun exp(x: Hp12cDecimal): Hp12cDecimal =
        TODO("Fase 1 — série de Taylor com redução por ln(2)/ln(10)")

    fun pow(base: Hp12cDecimal, exponent: Hp12cDecimal): Hp12cDecimal =
        TODO("Fase 1 — exp(exponent * ln(base)) com short-circuits para inteiros")

    fun sqrt(x: Hp12cDecimal): Hp12cDecimal =
        TODO("Fase 2 — Newton-Raphson sobre Hp12cDecimal")
}
