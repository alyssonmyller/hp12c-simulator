package com.arcom.hp12c.engine.state

import com.arcom.hp12c.engine.math.Hp12cDecimal

/**
 * Pilha RPN de 4 níveis da HP 12C (X, Y, Z, T) + registrador `LAST X`, conforme
 * `referencias/stack-behavior.md` da skill `hp12c-simulator`.
 *
 * Campos imutáveis; toda "operação de pilha" é uma extension function pura que
 * retorna um `Stack` novo. O flag `stackLiftEnabled` é parte do estado (e não uma
 * variável global escondida) porque algumas teclas (CLx, ENTER, Σ+) o alteram.
 *
 * Ver Seção 3.1 do `arquitetura/engine-interface.md`.
 */
data class Stack(
    val x:     Hp12cDecimal = Hp12cDecimal.ZERO,
    val y:     Hp12cDecimal = Hp12cDecimal.ZERO,
    val z:     Hp12cDecimal = Hp12cDecimal.ZERO,
    val t:     Hp12cDecimal = Hp12cDecimal.ZERO,
    val lastX: Hp12cDecimal = Hp12cDecimal.ZERO,

    /** `true` = próxima entrada de dígito levanta a pilha. Reset por CLx, ENTER, Σ+. */
    val stackLiftEnabled: Boolean = true,

    /** `true` enquanto o usuário está digitando um número. Afeta CHS, ENTER e as teclas financeiras. */
    val isEntering: Boolean = false,
)
