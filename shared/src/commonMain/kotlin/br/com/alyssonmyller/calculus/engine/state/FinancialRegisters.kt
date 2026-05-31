package br.com.alyssonmyller.calculus.engine.state

import br.com.alyssonmyller.calculus.engine.math.Hp12cDecimal
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Os 5 registradores de TVM + o modo BEG/END. `null` = "não-inicializado pelo usuário":
 * distingue-se de "zero explícito" porque o manual trata os dois casos diferentemente —
 * ver Seção 3.2 de `arquitetura/engine-interface.md` e Error 6 em `referencias/error-codes.md`.
 *
 * A taxa `i` é guardada em percentual (o usuário digita `4`, não `0.04`), conforme
 * convenção da HP física.
 */
@Serializable
data class FinancialRegisters(
    @Contextual val n:    Hp12cDecimal? = null,
    @Contextual val i:    Hp12cDecimal? = null,
    @Contextual val pv:   Hp12cDecimal? = null,
    @Contextual val pmt:  Hp12cDecimal? = null,
    @Contextual val fv:   Hp12cDecimal? = null,
    val mode: TvmMode = TvmMode.END,
)

@Serializable
enum class TvmMode { END, BEGIN }
