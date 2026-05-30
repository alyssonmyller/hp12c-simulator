package br.com.alyssonmyller.calculus.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.alyssonmyller.calculus.android.ui.Hp12cSkin
import br.com.alyssonmyller.calculus.android.ui.Hp12cSkins
import br.com.alyssonmyller.calculus.engine.CalculatorEngine
import br.com.alyssonmyller.calculus.engine.event.Event
import br.com.alyssonmyller.calculus.engine.state.CalculatorState
import br.com.alyssonmyller.calculus.engine.state.ProgramState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado observável da calculadora para a UI Compose.
 *
 * Separa [calcState] (domínio puro da engine) de [isRunning] (estado de execução
 * assíncrona do R/S, irrelevante para a engine mas visível na UI como indicador)
 * e [skin] (aparência visual, sem efeito na engine).
 */
data class Hp12cUiState(
    val calcState: CalculatorState,
    val isRunning: Boolean = false,
    val skin: Hp12cSkin = Hp12cSkins.Classic,
)

/**
 * ViewModel da HP 12C Platinum.
 *
 * Responsabilidades:
 *  1. **Sobrevivência a mudanças de configuração** (rotação de tela): o estado em memória
 *     não é re-lido da persistência a cada `Activity.onCreate`.
 *  2. **R/S assíncrono**: `Event.Program.RunStop` executa `executeProgram` em
 *     [Dispatchers.Default] (thread de background) para não bloquear o main thread.
 *     `MAX_EXEC_STEPS = 40 000` garante que o loop sempre termina, mas pode levar
 *     dezenas de milissegundos — sensível no main thread, aceitável num background thread.
 *  3. **Cancelamento de R/S**: segundo toque em R/S durante execução cancela a coroutine
 *     e restaura o [isRunning] para `false`; o [calcState] reverte ao estado anterior
 *     à execução (a coroutine foi cancelada antes de publicar o resultado).
 *  4. **Persistência**: salva estado normalizado a cada redução (via [SharedPreferencesStateRepository]).
 */
class Hp12cViewModel : ViewModel() {

    private val engine = CalculatorEngine.Default
    private val repo   = SharedPreferencesStateRepository(ApplicationContextHolder.get())

    private val _uiState = MutableStateFlow(
        Hp12cUiState(
            calcState = repo.load() ?: CalculatorEngine.InitialState,
            skin      = skinFromName(repo.loadSkinName()),
        ),
    )
    val uiState: StateFlow<Hp12cUiState> = _uiState.asStateFlow()

    /** Referência à coroutine de execução de programa em andamento, se houver. */
    private var runningJob: Job? = null

    /**
     * Alterna entre Classic e Modern e persiste a escolha em [SharedPreferencesStateRepository]
     * para que o app reabra no skin preferido do usuário.
     */
    fun toggleSkin() {
        _uiState.update { current ->
            val next = if (current.skin == Hp12cSkins.Classic) Hp12cSkins.Modern
                       else Hp12cSkins.Classic
            repo.saveSkinName(next.name)
            current.copy(skin = next)
        }
    }

    companion object {
        /**
         * Mapeia o nome persistido de volta para a instância de skin correspondente.
         * Retorna [Hp12cSkins.Classic] para qualquer nome desconhecido, garantindo
         * que uma versão futura com um skin novo não quebre o app ao carregar um
         * nome que ainda não existe.
         */
        private fun skinFromName(name: String?): Hp12cSkin = when (name) {
            Hp12cSkins.Modern.name -> Hp12cSkins.Modern
            else                   -> Hp12cSkins.Classic
        }
    }

    /**
     * Despacha [event] para a engine, persistindo e atualizando o [uiState].
     *
     * Comportamento especial de **R/S**:
     * - Se não há execução em andamento: lança coroutine em [Dispatchers.Default].
     * - Se já há execução em andamento (segundo toque): cancela a coroutine e
     *   restaura [isRunning] = `false`. O [calcState] reverte ao snapshot pré-execução
     *   porque a coroutine cancelada nunca publicou o resultado.
     */
    fun onEvent(event: Event) {
        // ── R/S enquanto programa roda: cancela execução ──────────────────────
        if (event is Event.Program.RunStop && _uiState.value.isRunning) {
            runningJob?.cancel()
            _uiState.update { it.copy(isRunning = false) }
            return
        }

        // ── R/S em modo normal ou Editing: executa de forma assíncrona ─────────
        if (event is Event.Program.RunStop &&
            _uiState.value.calcState.programState !is ProgramState.Editing) {
            val snapshot = _uiState.value.calcState
            _uiState.update { it.copy(isRunning = true) }
            runningJob = viewModelScope.launch {
                val result = withContext(Dispatchers.Default) {
                    engine.reduce(snapshot, event)
                }
                repo.save(engine.normalizeForPersistence(result))
                _uiState.update { it.copy(calcState = result, isRunning = false) }
            }
            return
        }

        // ── Todos os outros eventos: síncrono no main thread ──────────────────
        val result = engine.reduce(_uiState.value.calcState, event)
        repo.save(engine.normalizeForPersistence(result))
        _uiState.update { it.copy(calcState = result) }
    }
}
