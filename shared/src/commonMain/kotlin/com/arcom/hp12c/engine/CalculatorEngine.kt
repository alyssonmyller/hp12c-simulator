package com.arcom.hp12c.engine

import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.NumericSeparator

/**
 * Contrato público da engine HP 12C. Um reducer puro — dada um `CalculatorState` e um
 * `Event`, devolve o próximo `CalculatorState`. Sem singletons, sem mutação, sem callbacks;
 * a UI nativa (Compose/SwiftUI) é a única que sabe que existe um mundo com tempo.
 *
 * Princípios documentados em `arquitetura/engine-interface.md`, Seção 1:
 *   1. Reducer puro sobre estado imutável
 *   2. Zero dependências de plataforma no commonMain
 *   3. Fidelidade à HP 12C física acima de idiomaticidade Kotlin
 *
 * **Contrato forte:** `reduce` nunca lança. Erros viram `state.pendingError`.
 */
interface CalculatorEngine {

    /** Transição pura: dado (estado, evento), produz próximo estado. Nunca lança. */
    fun reduce(state: CalculatorState, event: Event): CalculatorState

    /** Conveniência: reduz uma sequência de eventos, acumulando estado à esquerda. */
    fun reduce(state: CalculatorState, events: List<Event>): CalculatorState =
        events.fold(state) { acc, ev -> reduce(acc, ev) }

    /**
     * Renderiza o valor atual do visor como string, respeitando `state.display`
     * (FIX/SCI/ENG) e o `separator` pedido pela UI. Se `state.pendingError != null`,
     * retorna "Error N".
     */
    fun formatDisplay(
        state: CalculatorState,
        separator: NumericSeparator = NumericSeparator.COMMA_PERIOD,
    ): String

    companion object {
        /** Engine default do app. Trocável para testes que queiram mockar. */
        val Default: CalculatorEngine = DefaultEngine()

        /** Estado inicial canônico (pilha zerada, FIX 2, END, sem erro pendente). */
        val InitialState: CalculatorState = CalculatorState()
    }
}
