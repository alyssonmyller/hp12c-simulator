package com.arcom.hp12c.android

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arcom.hp12c.android.ui.Hp12cPlatinumRealisticScreen
import androidx.core.graphics.toColorInt

/**
 * Ponto de entrada da Application — inicializa o [ApplicationContextHolder] antes de
 * qualquer Activity, para que [SharedPreferencesStateRepository] tenha um Context válido.
 * Declarado em AndroidManifest.xml com `android:name=".android.Hp12cApplication"`.
 */
class Hp12cApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApplicationContextHolder.init(this)
    }
}

/**
 * Atividade única. Delega todo o estado da calculadora ao [Hp12cViewModel]:
 *
 * - O estado sobrevive a rotações de tela (ViewModel não é destruído em mudanças
 *   de configuração), sem re-leitura desnecessária da persistência.
 * - R/S executa em background ([Hp12cViewModel] usa `Dispatchers.Default`);
 *   a UI mostra "running..." e permanece responsiva durante a execução.
 * - R/S uma segunda vez cancela a execução em andamento.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: Hp12cViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController =
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)

        // 2. TRUE diz ao Android que o fundo é claro (então ele pinta os ícones de PRETO)
        windowInsetsController.isAppearanceLightStatusBars = true
        windowInsetsController.isAppearanceLightNavigationBars = false

        // 3. Força a cor cinza da HP (0xFFD4D4D4) nas barras por cima do tema antigo
        val cinzaHp = "#D4D4D4".toColorInt()
        val backgroundBar = "#141414".toColorInt()
        window.statusBarColor = cinzaHp
        window.navigationBarColor = backgroundBar

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Hp12cPlatinumRealisticScreen(
                        calcState = uiState.calcState,
                        isRunning = uiState.isRunning,
                        onToggleSkin = viewModel::toggleSkin,
                        onEvent = viewModel::onEvent,
                    )
                }
            }
        }
    }
}
