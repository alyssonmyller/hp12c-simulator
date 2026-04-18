package com.arcom.hp12c.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arcom.hp12c.engine.CalculatorEngine

/**
 * Atividade única da Fase 0: prova que o módulo :shared linka direito com o :androidApp.
 * A UI real (skin classic + modern) entra na Fase 4.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HelloHp12c()
                }
            }
        }
    }
}

@Composable
private fun HelloHp12c() {
    // Toca algo da engine só para garantir que o import funciona. Não faz nada útil ainda.
    val initialStateHash = CalculatorEngine.InitialState.hashCode()

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("HP 12C — scaffold Fase 0\nInitialState hash: $initialStateHash")
    }
}

@Preview
@Composable
private fun HelloHp12cPreview() {
    MaterialTheme { HelloHp12c() }
}
