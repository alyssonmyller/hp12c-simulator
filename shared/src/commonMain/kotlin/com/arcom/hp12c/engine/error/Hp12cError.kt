package com.arcom.hp12c.engine.error

/**
 * Os 10 códigos de erro da HP 12C Platinum, modelados como sealed class para que cada
 * condição tenha um tipo nomeado. O campo `code` duplica o valor para facilitar formatação
 * `"Error $code"` no visor e mapeamento estável em testes.
 *
 * Fonte autoritativa: Apêndice D do manual (`bpia5314.pdf`), mapeado em
 * `referencias/error-codes.md` da skill `hp12c-simulator`.
 *
 * **Contrato de uso:** o reducer NUNCA lança um `Hp12cError` como exceção Kotlin. Em vez disso,
 * coloca-o em `CalculatorState.pendingError` e mantém o resto do estado intacto
 * (pilha, memórias e registradores financeiros preservados, conforme o manual).
 */
sealed class Hp12cError(val code: Int, val reason: String) {

    // --- Error 0 — matemática básica ---
    object DivisionByZero        : Hp12cError(0, "divisão por zero")
    object LogOfNonPositive      : Hp12cError(0, "log de valor não-positivo")
    object InvalidYToX           : Hp12cError(0, "y^x inválido (y<0 com x não-inteiro, ou y=0 com x≤0)")
    object SqrtOfNegative        : Hp12cError(0, "raiz quadrada de número negativo")

    // --- Error 1 — registradores de memória ---
    object RegisterNotFound      : Hp12cError(1, "registrador inexistente")
    object StoreOverflow         : Hp12cError(1, "overflow em STO aritmético")
    object MemoryCorruption      : Hp12cError(1, "memória contínua corrompida")

    // --- Error 2 — estatística ---
    object StatisticsUnderflow   : Hp12cError(2, "dados estatísticos insuficientes")
    object StatisticsCollinear   : Hp12cError(2, "regressão: dados colineares verticalmente")

    // --- Error 3 — IRR ---
    object IrrNoConverge         : Hp12cError(3, "IRR não convergiu")
    object IrrNoSignChange       : Hp12cError(3, "IRR: fluxo sem mudança de sinal")

    // --- Error 4 — programação ---
    object ProgramOverflow       : Hp12cError(4, "> 400 passos de programa")
    object InvalidGoto           : Hp12cError(4, "GTO/GSB para linha inexistente")
    object SubroutineOverflow    : Hp12cError(4, "subrotinas aninhadas além do limite")

    // --- Error 5 — TVM + fatorial (idiossincrasia histórica) ---
    object TvmNoConverge         : Hp12cError(5, "TVM não convergiu")
    object TvmInvalidSigns       : Hp12cError(5, "TVM: combinação de sinais inválida")

    /**
     * Domínio inválido em `n!`: `x < 0` ou `x` não-inteiro. Manual `bpia5314.pdf` p. 195
     * (Apêndice D) lista fatorial sob **Erro 5** — não Erro 0 — por um motivo histórico:
     * na HP12C original, `n!` compartilhava o código de firmware com as rotinas TVM.
     * A fórmula `0! = 1` (Apêndice E p. 205) prevalece sobre a leitura literal de "x ≤ 0"
     * no Apêndice D — ambiguidade #1 de `formulas/transcendentais.md` §7.
     *
     * Overflow (`70! > 10^99`) é `Error 1` (categoria de armazenamento), não este.
     */
    object FactorialDomain       : Hp12cError(5, "n! inválido (x<0 ou x não-inteiro)")

    // --- Error 6 — registradores financeiros ---
    object FinancialUninit       : Hp12cError(6, "registrador financeiro não-inicializado")
    object AmortizeInvalidN      : Hp12cError(6, "AMORT requer n inteiro e i consistente")
    object DepreciationInvalidN  : Hp12cError(6, "depreciação requer n inteiro positivo (INT(n) > 0)")
    object DepreciationInvalidYear: Hp12cError(6, "depreciação requer ano j inteiro positivo (INT(j) > 0)")

    // --- Error 7 — fluxo de caixa ---
    object CashflowEmpty         : Hp12cError(7, "NPV/IRR sem CFo")
    object CashflowNjTooLarge    : Hp12cError(7, "Nj > 99")
    object CashflowTooManyFlows  : Hp12cError(7, "> 80 fluxos de caixa")

    // --- Error 8 — calendário ---
    object InvalidDate           : Hp12cError(8, "data inválida")
    object DateOutOfRange        : Hp12cError(8, "data fora do intervalo suportado")

    // --- Error 9 — auto-teste ---
    object SelfTestFailure       : Hp12cError(9, "falha em auto-teste / corrupção interna")
}
