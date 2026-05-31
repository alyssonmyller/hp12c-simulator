package br.com.alyssonmyller.calculus.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import br.com.alyssonmyller.calculus.engine.CalculatorEngine
import br.com.alyssonmyller.calculus.engine.state.ProgramMemory
import br.com.alyssonmyller.calculus.engine.state.ProgramState
import br.com.alyssonmyller.calculus.engine.state.ProgramStep
import br.com.alyssonmyller.calculus.engine.state.TvmMode

// ─────────────────────────────────────────────────────────────────────────────
//  Provider de parâmetros de preview
// ─────────────────────────────────────────────────────────────────────────────

/** Skin provider para `@PreviewParameter` — itera Classic e Modern. */
private class SkinProvider : PreviewParameterProvider<Hp12cSkin> {
    override val values = sequenceOf(Hp12cSkins.Classic, Hp12cSkins.Modern)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Previews — tela completa
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name          = "HP 12C — Classic (idle)",
    showBackground = true,
    widthDp       = 360,
    heightDp      = 700,
)
@Composable
private fun PreviewClassicIdle() {
    Hp12cScreen(
        calcState = CalculatorEngine.InitialState,
        onEvent   = {},
        skin      = Hp12cSkins.Classic,
    )
}

@Preview(
    name          = "HP 12C — Modern (idle)",
    showBackground = true,
    widthDp       = 360,
    heightDp      = 700,
)
@Composable
private fun PreviewModernIdle() {
    Hp12cScreen(
        calcState = CalculatorEngine.InitialState,
        onEvent   = {},
        skin      = Hp12cSkins.Modern,
    )
}

@Preview(
    name          = "HP 12C — Classic (running)",
    showBackground = true,
    widthDp       = 360,
    heightDp      = 700,
)
@Composable
private fun PreviewClassicRunning() {
    Hp12cScreen(
        calcState = CalculatorEngine.InitialState,
        isRunning = true,
        onEvent   = {},
        skin      = Hp12cSkins.Classic,
    )
}

@Preview(
    name          = "HP 12C — Classic (BEGIN mode)",
    showBackground = true,
    widthDp       = 360,
    heightDp      = 700,
)
@Composable
private fun PreviewClassicBegin() {
    val state = CalculatorEngine.InitialState.copy(
        financial = CalculatorEngine.InitialState.financial.copy(mode = TvmMode.BEGIN),
    )
    Hp12cScreen(
        calcState = state,
        onEvent   = {},
        skin      = Hp12cSkins.Classic,
    )
}

@Preview(
    name          = "HP 12C — ambos skins (Classic | Modern)",
    showBackground = true,
    widthDp       = 360,
    heightDp      = 700,
)
@Composable
private fun PreviewBothSkins(
    @PreviewParameter(SkinProvider::class) skin: Hp12cSkin,
) {
    Hp12cScreen(
        calcState = CalculatorEngine.InitialState,
        onEvent   = {},
        skin      = skin,
    )
}

@Preview(
    name           = "HP 12C — PRGM editing (Classic)",
    showBackground = true,
    widthDp        = 360,
    heightDp       = 700,
)
@Composable
private fun PreviewPrgmEditing() {
    // Programa de exemplo: 2 × RTN
    val memory = ProgramMemory(
        steps = listOf(
            ProgramStep.KeyStep("D", 2),
            ProgramStep.KeyStep("MUL"),
            ProgramStep.Return,
        ),
    )
    val state = CalculatorEngine.InitialState.copy(
        programMemory = memory,
        programState  = ProgramState.Editing(cursor = 3),
    )
    Hp12cScreen(
        calcState = state,
        onEvent   = {},
        skin      = Hp12cSkins.Classic,
    )
}
