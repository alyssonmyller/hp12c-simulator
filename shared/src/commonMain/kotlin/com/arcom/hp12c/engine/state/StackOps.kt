package br.com.alyssonmyller.calculus.engine.state

import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal

/**
 * Operações puras sobre [Stack]. Cada função retorna um `Stack` novo — toda mutação da
 * calculadora passa por aqui, para que o reducer fique simples de auditar e o comportamento
 * da pilha seja isolável em testes sem montar a engine inteira.
 *
 * Fonte de verdade: `referencias/stack-behavior.md` da skill `hp12c-simulator`, em particular
 * a tabela da Seção 3 "quem toca o quê" e o checklist da Seção 5. Se algo aqui divergir
 * daquele arquivo, o arquivo vence — arrume o código.
 *
 * ### Política sobre flags
 *
 * - `lastX` só é preenchido em operações que **destroem** o valor atual de X
 *   (binária, unária, percent, LSTx destino). `ENTER`, `CLx`, `R↓`, `R↑` e `x⇆y` não tocam.
 * - `stackLiftEnabled` ao fim de cada operação segue a coluna "stackLift após" da tabela.
 * - `isEntering` é zerado por qualquer operação de pilha: uma vez que um op rodou, o usuário
 *   não está mais no meio da digitação de X. Entrada de dígitos subsequente só acontece
 *   pelo reducer via [acceptNewNumber] / appending.
 */

// ─── Primitivas internas ────────────────────────────────────────────────────────

/**
 * "Sobe a pilha": T ← Z, Z ← Y, Y ← X, X ← [newX]. **Não** olha o flag `stackLiftEnabled`
 * e **não** toca `lastX`. É o bloco atômico de qualquer operação que empurra um novo valor
 * para X (primeira digitação de um número, `RCL`, `LSTx`, etc.).
 *
 * Caller é responsável por consultar `stackLiftEnabled` quando apropriado — veja
 * [acceptNewNumber] para a versão "esperta".
 */
internal fun Stack.lift(newX: Hp12cDecimal): Stack =
    copy(x = newX, y = x, z = y, t = z)

/**
 * "Desce a pilha": X ← Y, Y ← Z, Z ← T, **T permanece** (T é sticky). Usado por operações
 * binárias que consomem Y e X, mas em si não escreve o resultado em X — quem chama faz o
 * `.copy(x = resultado, lastX = xAntigo)` em seguida. Veja [binaryOp].
 */
internal fun Stack.drop(): Stack =
    copy(x = y, y = z, z = t /* t permanece */)

// ─── Digitação e limpeza de X ──────────────────────────────────────────────────

/**
 * Aceita um número "novo" em X, obedecendo o flag `stackLiftEnabled`:
 * - Se o flag está ligado, eleva a pilha (T ← Z, Z ← Y, Y ← X) e coloca [newX] em X.
 * - Se desligado (logo após `ENTER` ou `CLx`), sobrescreve X sem mover a pilha.
 *
 * Em ambos os casos, religa `stackLiftEnabled` e marca `isEntering = true`. É o que o
 * reducer invoca no **primeiro** dígito de uma nova entrada; dígitos subsequentes dentro da
 * mesma entrada modificam X no lugar, sem passar por aqui.
 */
fun Stack.acceptNewNumber(newX: Hp12cDecimal): Stack {
    val rebuilt = if (stackLiftEnabled) lift(newX) else copy(x = newX)
    return rebuilt.copy(stackLiftEnabled = true, isEntering = true, canStoreToTvm = true)
}

/**
 * `CLx` — zera X. `lastX` **inalterado**, `stackLiftEnabled` **desligado** (próxima
 * digitação sobrescreve X em vez de empurrar). Encerra a digitação em curso.
 */
fun Stack.clx(): Stack =
    copy(x = Hp12cDecimal.ZERO, stackLiftEnabled = false, isEntering = false, canStoreToTvm = true)

/**
 * Empurra um valor "externo" (resultado de `RCL`, `Solve.Fv`, etc.) para X, respeitando
 * `stackLiftEnabled` como o manual prescreve — RCL "comporta-se como uma nova digitação"
 * (Apêndice A, p. 181). Diferente de [acceptNewNumber], NÃO marca `isEntering = true`: o
 * valor já está finalizado, não há digitação em curso.
 */
fun Stack.pushValue(value: Hp12cDecimal): Stack {
    val rebuilt = if (stackLiftEnabled) lift(value) else copy(x = value)
    return rebuilt.copy(stackLiftEnabled = true, isEntering = false, canStoreToTvm = true)
}

// ─── ENTER ─────────────────────────────────────────────────────────────────────

/**
 * `ENTER` — duplica X em Y, empurra Z→T (o T antigo cai fora). `lastX` **inalterado**,
 * `stackLiftEnabled` **desligado** (próximo número sobrescreve X). Encerra a digitação.
 *
 * Diagrama:
 * ```
 * T₀ Z₀ Y₀ X₀  →  Z₀ Y₀ X₀ X₀
 * ```
 */
fun Stack.enter(): Stack =
    copy(t = z, z = y, y = x, stackLiftEnabled = false, isEntering = false, canStoreToTvm = true)

// ─── Operações de pilha sem "consumo" ──────────────────────────────────────────

/**
 * `R↓` — rotação circular para baixo dos 4 níveis. X→T, Y→X, Z→Y, T→Z.
 *
 * Diagrama:
 * ```
 * T₀ Z₀ Y₀ X₀  →  X₀ T₀ Z₀ Y₀
 * ```
 */
fun Stack.rollDown(): Stack =
    copy(x = y, y = z, z = t, t = x, stackLiftEnabled = true, isEntering = false, canStoreToTvm = true)

/**
 * `R↑` — rotação circular para cima dos 4 níveis. T→X, Z→T, Y→Z, X→Y.
 *
 * Diagrama:
 * ```
 * T₀ Z₀ Y₀ X₀  →  Z₀ Y₀ X₀ T₀
 * ```
 */
fun Stack.rollUp(): Stack =
    copy(x = t, y = x, z = y, t = z, stackLiftEnabled = true, isEntering = false, canStoreToTvm = true)

/**
 * `x⇆y` — troca X e Y. Z e T intactos.
 */
fun Stack.swapXY(): Stack =
    copy(x = y, y = x, stackLiftEnabled = true, isEntering = false, canStoreToTvm = true)

/**
 * `LSTx` — eleva a pilha e coloca `lastX` em X. O próprio `lastX` **não** muda.
 *
 * Útil para desfazer o último operando destruído por uma op binária/unária.
 */
fun Stack.lstx(): Stack =
    lift(lastX).copy(stackLiftEnabled = true, isEntering = false, canStoreToTvm = true)

// ─── Operações aritméticas (binárias, unárias, percent) ────────────────────────

/**
 * Operação binária (`+`, `-`, `×`, `÷`, `y^x`). Calcula `f(Y, X)`, escreve em X; Y←Z, Z←T,
 * T **permanece** (sticky), `lastX` recebe o X antigo, `stackLiftEnabled = true`.
 *
 * Se `f` lançar (típico: `ArithmeticException` em divisão por zero), a exceção sobe; o
 * reducer é quem captura e mapeia para `Hp12cError.DivisionByZero` preservando o estado
 * pré-operação (veja invariante da Seção 5 do stack-behavior).
 */
fun Stack.binaryOp(f: (y: Hp12cDecimal, x: Hp12cDecimal) -> Hp12cDecimal): Stack {
    val xOld  = x
    val result = f(y, x)
    return drop().copy(
        x = result,
        lastX = xOld,
        stackLiftEnabled = true,
        isEntering = false,
        canStoreToTvm = true,
    )
}

/**
 * Operação unária (`1/x`, `√x`, `LN`, `e^x`, `n!`, `RND`, `INTG`, `FRAC`). Só X muda;
 * Y, Z, T intactos. `lastX` ← X antigo.
 *
 * **Atenção:** `CHS` durante a **digitação** não é uma operação unária no sentido deste
 * helper — ele é parte da entrada e não preenche `lastX`. Isso o reducer trata à parte.
 */
fun Stack.unaryOp(f: (Hp12cDecimal) -> Hp12cDecimal): Stack {
    val xOld = x
    return copy(
        x = f(x),
        lastX = xOld,
        stackLiftEnabled = true,
        isEntering = false,
        canStoreToTvm = true,
    )
}

/**
 * Operação de "saída dupla" das teclas estatísticas (`g x̄`, `g s`, `g ŷ,r`, `g x̂,r`).
 * Escreve dois novos valores diretamente em X e Y **sem descer Z/T** — diferente de
 * push ou binaryOp, que movem a pilha. Z e T ficam exatamente como estavam.
 *
 * `lastX` ← X antigo, `stackLiftEnabled = true`.
 *
 * Fonte: `formulas/estatistica.md` §7, observação 1.
 */
fun Stack.dualOutputOp(f: (stack: Stack) -> Pair<Hp12cDecimal, Hp12cDecimal>): Stack {
    val xOld = x
    val (newX, newY) = f(this)
    return copy(
        x = newX,
        y = newY,
        lastX = xOld,
        stackLiftEnabled = true,
        isEntering = false,
        canStoreToTvm = true,
    )
}

/**
 * Percent (`%`, `%T`, `Δ%`) — formalmente binária (consome Y e X), mas **não desce a
 * pilha**: Y permanece em Y. Permite a idiomática `300 ENTER 15 % -` = 255 (= 300 − 45).
 *
 * `lastX` ← X antigo, `stackLiftEnabled = true`.
 */
fun Stack.percentOp(f: (y: Hp12cDecimal, x: Hp12cDecimal) -> Hp12cDecimal): Stack {
    val xOld = x
    return copy(
        x = f(y, x),
        lastX = xOld,
        stackLiftEnabled = true,
        isEntering = false,
        canStoreToTvm = true,
    )
}
