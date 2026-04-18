package com.arcom.hp12c.engine.format

import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.NumericSeparator

/**
 * Conversão `CalculatorState -> String` do visor. Separado de `DefaultEngine` para
 * manter a aritmética de apresentação (separador de milhar, sinal de "-", notação
 * científica com `e`) testável isoladamente.
 *
 * Implementação entra na Fase 1, em conjunto com `Hp12cDecimal`.
 */
internal object DisplayFormatter {

    fun format(state: CalculatorState, separator: NumericSeparator): String =
        TODO("Fase 1 — formatar X de state.stack respeitando state.display e separator")
}
