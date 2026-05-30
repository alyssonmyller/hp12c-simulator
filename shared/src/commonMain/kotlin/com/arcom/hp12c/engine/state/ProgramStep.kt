package br.com.alyssonmyller.calculus.engine.state

import kotlinx.serialization.Serializable

/**
 * Um único passo armazenado na fita de programa da HP 12C Platinum.
 *
 * A HP 12C é uma calculadora *keystroke-programmable*: cada tecla pressionada no
 * modo de edição (PRGM) é gravada como um `ProgramStep`. Na execução, a engine
 * "reprime" cada passo na ordem em que foi gravado.
 *
 * Ver `arquitetura/programacao.md` §2.
 */
@Serializable
sealed class ProgramStep {

    /**
     * Qualquer tecla de calculadora armazenada como código compacto.
     *
     * [keyCode] é uma string estável tipo `"MUL"`, `"SIGMA+"`, `"D"` (com [keyParam]=4
     * para o dígito 4). Mais estável do que guardar o `Event` diretamente — não quebra
     * se a sealed class `Event` for refatorada.
     *
     * Ver `ProgramKeyCode` para a tabela de mapeamento bidirecional.
     */
    @Serializable
    data class KeyStep(val keyCode: String, val keyParam: Int = PARAM_NONE) : ProgramStep() {
        companion object { const val PARAM_NONE = Int.MIN_VALUE }
    }

    /** GTO target — salta incondicionalmente para [target]. */
    @Serializable
    data class Goto(val target: ProgramTarget) : ProgramStep()

    /** GSB target — chama subrotina em [target]; empilha endereço de retorno. */
    @Serializable
    data class Gosub(val target: ProgramTarget) : ProgramStep()

    /** RTN — retorna de subrotina; se return-stack vazio, para a execução. */
    @Serializable
    data object Return : ProgramStep()

    /** LBL — marcador de início de subrotina ou destino de GTO/GSB por rótulo. */
    @Serializable
    data class Label(val label: ProgramLabel) : ProgramStep()

    /**
     * Teste condicional — semântica "do if true / skip-next if false":
     * - Se [test] é VERDADEIRO: próximo passo executado normalmente (pc += 1).
     * - Se [test] é FALSO: próximo passo **pulado** (pc += 2).
     * Ver `arquitetura/programacao.md` §2.4 e §5.
     */
    @Serializable
    data class Conditional(val test: ConditionalTest) : ProgramStep()

    /**
     * PSE — pausa a exibição por ~1 segundo durante execução automática.
     * A engine avança o PC normalmente; a UI detecta e atrasa o próximo evento.
     */
    @Serializable
    data object Pause : ProgramStep()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Destino de GTO / GSB
// ─────────────────────────────────────────────────────────────────────────────

/** Destino de um `GTO` ou `GSB`. */
@Serializable
sealed class ProgramTarget {
    /** Salta diretamente para a linha absoluta (000-399). */
    @Serializable
    data class LineTarget(val line: Int) : ProgramTarget()

    /** Escaneia o programa em busca do primeiro `Label(label)`. */
    @Serializable
    data class LabelTarget(val label: ProgramLabel) : ProgramTarget()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tipo de rótulo
// ─────────────────────────────────────────────────────────────────────────────

/** Rótulo de programa — numérico (linha direta) ou alfa (A–E). */
@Serializable
sealed class ProgramLabel {
    /** Rótulo numérico: endereço direto de linha (000-399). Só usado em `LineTarget`. */
    @Serializable
    data class NumericLine(val line: Int) : ProgramLabel()

    /** Rótulo alfa criado com `f A` … `f E`. Buscado linearmente a partir de 000. */
    @Serializable
    data class AlphaLabel(val name: Char) : ProgramLabel()  // 'A'..'E'
}

// ─────────────────────────────────────────────────────────────────────────────
//  Testes condicionais
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Os quatro testes condicionais da HP 12C (manual, Seção 12, p. 126).
 * Todos usam semântica "pula próximo se FALSO".
 */
@Serializable
enum class ConditionalTest {
    XEqZero,   // g x=0?   — TRUE se x = 0
    XLeqZero,  // g x≤0?   — TRUE se x ≤ 0
    XEqY,      // g x=y?   — TRUE se x = y (comparação exata de Hp12cDecimal)
    XLtY,      // g x<y?   — TRUE se x < y
}

// ─────────────────────────────────────────────────────────────────────────────
//  ProgramMemory — a fita
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Memória de programa: lista ordenada de passos com capacidade máxima [MAX_STEPS].
 *
 * Pertence a `CalculatorState` e É parte da memória contínua (manual, Apêndice B, p. 191).
 * Todos os tipos referenciados (`ProgramStep`, `ProgramTarget`, `ProgramLabel`,
 * `ConditionalTest`) são `@Serializable`. `ProgramStep.KeyStep` armazena a tecla como
 * `String keyCode + Int keyParam` — estável a refatorações da sealed class `Event`.
 */
@Serializable
data class ProgramMemory(
    val steps: List<ProgramStep> = emptyList(),
) {
    companion object {
        const val MAX_STEPS = 400
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ProgramState — runtime (@Transient em CalculatorState)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Estado de runtime da programação. **Não é persistido** — ao reabrir o app, a engine
 * retorna a `Idle`. O programa gravado (`ProgramMemory`) é restaurado; execuções em
 * andamento são descartadas.
 *
 * Ver `arquitetura/programacao.md` §3.
 */
sealed class ProgramState {

    /**
     * Estado normal: calculadora fora do modo de programação.
     *
     * [startPc] é o passo em que o próximo R/S iniciará a execução.
     * Normalmente 0 (início do programa), mas é atualizado pelo `GTO nnn` em Idle
     * para que o próximo R/S comece no passo apontado — comportamento idêntico à HP física.
     */
    data class Idle(val startPc: Int = 0) : ProgramState()

    /**
     * Modo de edição (PRGM ativo).
     *
     * [cursor] = índice do próximo passo a ser inserido (= número exibido no visor, 000-399).
     * Ao entrar no modo PRGM via `TogglePrgmMode`, cursor vai para `steps.size` (fim do programa).
     */
    data class Editing(val cursor: Int) : ProgramState()

    /**
     * Execução automática (R/S ativo).
     *
     * [pc]          = índice do próximo passo a executar (0-based).
     * [returnStack] = endereços de retorno de GSB (topo = último push).
     *                 Máximo: [MAX_RETURN_DEPTH] níveis.
     */
    data class Running(
        val pc: Int,
        val returnStack: List<Int> = emptyList(),
    ) : ProgramState() {
        companion object {
            const val MAX_RETURN_DEPTH = 6
        }
    }
}
