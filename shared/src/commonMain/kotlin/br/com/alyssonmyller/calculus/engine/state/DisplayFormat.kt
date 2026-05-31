package br.com.alyssonmyller.calculus.engine.state

import kotlinx.serialization.Serializable

/**
 * Formato do visor: `FIX n`, `SCI n`, `ENG n` com `n` entre 0 e 9 — espelha a HP 12C Platinum.
 * Ver Seção 3.4 de `arquitetura/engine-interface.md`.
 *
 * O default da HP é `FIX 2`, mas a Seção 2 do manual lembra que a HP Platinum salva o formato
 * na memória contínua, então na primeira execução vale `FIX 2`, depois vale o que o usuário
 * configurou por último.
 */
@Serializable
sealed class DisplayFormat {
    @Serializable data class Fix(val places: Int) : DisplayFormat() { init { require(places in 0..9) } }
    @Serializable data class Sci(val places: Int) : DisplayFormat() { init { require(places in 0..9) } }
    @Serializable data class Eng(val places: Int) : DisplayFormat() { init { require(places in 0..9) } }

    companion object {
        val Default: DisplayFormat = Fix(2)
    }
}

/**
 * Separador numérico — preferência de UI (não afeta cálculo, só renderização).
 * Entra no `formatDisplay(state, separator)` como parâmetro explícito para manter
 * `CalculatorState` livre de preferências de apresentação.
 */
enum class NumericSeparator {
    /** `.` decimal, `,` milhar — padrão americano/UK. */
    PERIOD_COMMA,
    /** `,` decimal, `.` milhar — padrão pt-BR e europeu continental. */
    COMMA_PERIOD,
}
