package br.com.alyssonmyller.calculus.engine.state

import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

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
@Serializable
data class Stack(
    @Contextual val x:     Hp12cDecimal = Hp12cDecimal.ZERO,
    @Contextual val y:     Hp12cDecimal = Hp12cDecimal.ZERO,
    @Contextual val z:     Hp12cDecimal = Hp12cDecimal.ZERO,
    @Contextual val t:     Hp12cDecimal = Hp12cDecimal.ZERO,
    @Contextual val lastX: Hp12cDecimal = Hp12cDecimal.ZERO,

    /** `true` = próxima entrada de dígito levanta a pilha. Reset por CLx, ENTER, Σ+. */
    val stackLiftEnabled: Boolean = true,

    /** `true` enquanto o usuário está digitando um número. Afeta CHS e digitação de dígitos. */
    val isEntering: Boolean = false,

    /**
     * `true` quando X contém um valor "fresco" que deve ser ARMAZENADO por uma tecla TVM
     * (n, i, PV, PMT, FV). Segue a semântica da HP 12C física:
     *
     * - Setado por: digitação de dígito, ENTER, resultado de operação aritmética/unária/percent,
     *   RCL, LSTx, CLx, R↓, R↑, x⇆y, estatística.
     * - Zerado por: TVM Store (após armazenar), TVM Solve (após calcular), CLR FIN,
     *   InitialState.
     *
     * Sem este flag, `9 ENTER 12 ÷ i` acionaria **Solve.I** (incorreto) em vez de **Store.I**
     * (0.75 → i), porque `isEntering` já é `false` após o `÷`.
     */
    val canStoreToTvm: Boolean = false,
)
