package com.arcom.hp12c.engine.state

import com.arcom.hp12c.engine.math.Hp12cDecimal

/**
 * Os 5 registradores de TVM + o modo BEG/END. `null` = "não-inicializado pelo usuário":
 * distingue-se de "zero explícito" porque o manual trata os dois casos diferentemente —
 * ver Seção 3.2 de `arquitetura/engine-interface.md` e Error 6 em `referencias/error-codes.md`.
 *
 * A taxa `i` é guardada em percentual (o usuário digita `4`, não `0.04`), conforme
 * convenção da HP física.
 */
data class FinancialRegisters(
    val n:    Hp12cDecimal? = null,
    val i:    Hp12cDecimal? = null,
    val pv:   Hp12cDecimal? = null,
    val pmt:  Hp12cDecimal? = null,
    val fv:   Hp12cDecimal? = null,
    val mode: TvmMode       = TvmMode.END,
)

enum class TvmMode { END, BEGIN }
