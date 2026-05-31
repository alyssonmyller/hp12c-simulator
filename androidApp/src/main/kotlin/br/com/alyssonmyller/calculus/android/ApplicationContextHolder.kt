package br.com.alyssonmyller.calculus.android

import android.content.Context

/**
 * Singleton que guarda o [Context] de aplicação para injeção em componentes KMP que
 * precisam de contexto Android (ex: [SharedPreferencesStateRepository]).
 *
 * Inicializado em [Hp12cApplication.onCreate] — antes de qualquer Activity.
 */
object ApplicationContextHolder {
    private lateinit var _context: Context

    /** Deve ser chamado uma única vez em [Hp12cApplication.onCreate]. */
    fun init(context: Context) {
        _context = context.applicationContext
    }

    /** Retorna o [Context] de aplicação. Lança se [init] não foi chamado ainda. */
    fun get(): Context = _context
}
