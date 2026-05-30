package br.com.alyssonmyller.calculus.android

import android.content.Context
import br.com.alyssonmyller.calculus.engine.persistence.StateRepository
import br.com.alyssonmyller.calculus.engine.persistence.decodeState
import br.com.alyssonmyller.calculus.engine.persistence.encodeState
import br.com.alyssonmyller.calculus.engine.state.CalculatorState

private const val PREFS_NAME = "hp12c_state"
private const val KEY_STATE  = "state_json"
private const val KEY_SKIN   = "skin_name"

/**
 * Implementação Android de [StateRepository] via [android.content.SharedPreferences].
 *
 * - [load]: lê a string JSON salva; retorna `null` se ausente ou corrompida.
 * - [save]: usa [android.content.SharedPreferences.Editor.apply] — escrita assíncrona
 *   em background pelo próprio Android, imperceptível para o usuário.
 * - [loadSkinName] / [saveSkinName]: persistência leve do skin preferido (só o nome,
 *   ex: `"Classic"` ou `"Modern"`). Chave separada no mesmo arquivo de prefs para não
 *   misturar estado de engine com preferência de UI.
 *
 * Ver `arquitetura/persistence.md` §6.1.
 */
class SharedPreferencesStateRepository(context: Context) : StateRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): CalculatorState? {
        val json = prefs.getString(KEY_STATE, null) ?: return null
        return decodeState(json)
    }

    override fun save(state: CalculatorState) {
        prefs.edit().putString(KEY_STATE, encodeState(state)).apply()
    }

    /** Retorna o nome do último skin salvo, ou `null` se nunca foi persistido. */
    fun loadSkinName(): String? = prefs.getString(KEY_SKIN, null)

    /** Persiste o nome do skin de forma assíncrona (apply). */
    fun saveSkinName(name: String) {
        prefs.edit().putString(KEY_SKIN, name).apply()
    }
}
