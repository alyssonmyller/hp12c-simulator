package com.arcom.hp12c.engine.state

import kotlinx.serialization.Serializable

/**
 * Modo de formatação de datas da HP 12C Platinum. Controla a ordem dos componentes
 * na codificação decimal usada pelas teclas `f DATE`, `f DYS`, `g D.MY` e `g M.DY`.
 *
 * | Modo | Codificação | Exemplo (30 jun 1994) |
 * |------|-------------|----------------------|
 * | MDY  | `MM.DDYYYY` | `6.301994`           |
 * | DMY  | `DD.MMYYYY` | `30.061994`          |
 *
 * `MDY` é o padrão de fábrica da HP 12C. Ver Seção 1 de `formulas/calendario.md` e
 * manual Seção 9, p. 106-113.
 */
@Serializable
enum class DateFormat {
    /** Padrão de fábrica — `MM.DDYYYY` (ex.: `6.301994` = 30 jun 1994). */
    MDY,
    /** Modo europeu — `DD.MMYYYY` (ex.: `30.061994` = 30 jun 1994). */
    DMY,
}
