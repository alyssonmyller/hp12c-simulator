package com.arcom.hp12c.engine

import com.arcom.hp12c.engine.error.Hp12cError
import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.math.Hp12cDecimal
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.DisplayFormat
import com.arcom.hp12c.engine.state.RegisterId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes do [DefaultEngine.reduce] — Fase 1 passo 3. Cobrem as famílias de `Event`
 * implementadas neste passo: `Entry`, `StackOp`, `Arith`, `Memory`, `Display` e
 * `AcknowledgeError`. A família `Financial` ainda é `TODO` e é coberta no passo 4.
 *
 * Organização:
 *
 *   1. Digitação (`Event.Entry.*` + buffer + commit)
 *   2. ENTER / CLx / R↓ / x⇆y / LSTx (via reducer, não só StackOps)
 *   3. Aritmética (inclui as duas regras críticas da Seção 5 do `stack-behavior.md`:
 *      STO preserva pilha — regra 7; erro preserva pilha — regra 8)
 *   4. Memória (STO/RCL/CLEAR REG + lift condicional do RCL)
 *   5. Display (FIX/SCI/ENG — NÃO comita a entrada)
 *   6. Erro pendente e AcknowledgeError
 *
 * Convenção de notação nos nomes: `_` ao invés de espaços, sem backticks com caracteres
 * especiais (compat com o target iOS Kotlin/Native).
 */
class ReducerTest {

    private val engine = CalculatorEngine.Default
    private val initial = CalculatorEngine.InitialState

    /** Executa uma sequência de eventos a partir do estado inicial canônico. */
    private fun run(vararg events: Event): CalculatorState =
        engine.reduce(initial, events.toList())

    /** Executa eventos a partir de um estado customizado. */
    private fun run(state: CalculatorState, vararg events: Event): CalculatorState =
        engine.reduce(state, events.toList())

    // Atalhos para compor sequências legíveis.
    private fun digits(vararg ds: Int): List<Event> = ds.map { Event.Entry.Digit(it) }
    private fun number(s: String): List<Event> = buildList {
        // Aceita "123", "1.23", "-1.5" (o `-` inicial vira um CHS final depois da entrada)
        var s2 = s
        val negate = s2.startsWith("-").also { if (it) s2 = s2.substring(1) }
        for (ch in s2) {
            add(
                when (ch) {
                    '.'       -> Event.Entry.DecimalPoint
                    in '0'..'9' -> Event.Entry.Digit(ch.digitToInt())
                    else -> error("digito inválido em number(\"$s\"): '$ch'")
                },
            )
        }
        if (negate) add(Event.Entry.ChangeSign)
    }

    private fun d(n: Int)    = Hp12cDecimal.of(n)
    private fun d(s: String) = Hp12cDecimal.of(s)

    // ─── 1. Digitação / buffer / commit ────────────────────────────────────────

    @Test fun digito_unico_nao_comita_ate_proximo_evento_nao_entry() {
        val s = run(Event.Entry.Digit(5))
        assertEquals("5", s.entryBuffer, "buffer contém a entrada crua")
        assertTrue(s.stack.isEntering)
        assertEquals(d(0), s.stack.x, "X só recebe o valor no commit")
    }

    @Test fun enter_comita_entrada_atual() {
        val s = run(*number("5").toTypedArray(), Event.StackOp.Enter)
        assertNull(s.entryBuffer)
        assertFalse(s.stack.isEntering)
        assertEquals(d(5), s.stack.x)
        assertEquals(d(5), s.stack.y, "ENTER duplicou X em Y")
    }

    @Test fun multiplos_digitos_compoe_o_buffer() {
        val s = run(*number("123").toTypedArray(), Event.StackOp.Enter)
        assertEquals(d(123), s.stack.x)
        assertEquals(d(123), s.stack.y)
    }

    @Test fun ponto_decimal_no_meio_da_entrada() {
        val s = run(*number("1.23").toTypedArray(), Event.StackOp.Enter)
        assertEquals(d("1.23"), s.stack.x)
    }

    @Test fun ponto_decimal_como_primeira_tecla_assume_zero_a_esquerda() {
        val s = run(Event.Entry.DecimalPoint, Event.Entry.Digit(5), Event.StackOp.Enter)
        assertEquals(d("0.5"), s.stack.x)
    }

    @Test fun ponto_decimal_duplicado_eh_ignorado() {
        val s = run(
            Event.Entry.Digit(1),
            Event.Entry.DecimalPoint,
            Event.Entry.DecimalPoint, // <-- ignorado
            Event.Entry.Digit(2),
            Event.StackOp.Enter,
        )
        assertEquals(d("1.2"), s.stack.x)
    }

    @Test fun chs_durante_entrada_inverte_sinal_da_mantissa() {
        val s = run(Event.Entry.Digit(5), Event.Entry.ChangeSign, Event.StackOp.Enter)
        assertEquals(d(-5), s.stack.x)
    }

    @Test fun chs_durante_entrada_nao_preenche_last_x() {
        // Diferente de `Arith.Negate` (operação unária), CHS na entrada não deve gravar lastX.
        // Estabelecemos um lastX conhecido via operação anterior:
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Arith.Add,   // lastX agora = 4
            *number("7").toTypedArray(), Event.Entry.ChangeSign, Event.StackOp.Enter,
        )
        assertEquals(d(-7), s.stack.x)
        assertEquals(d(4), s.stack.lastX, "CHS-durante-entrada NÃO deve alterar lastX")
    }

    @Test fun arith_negate_fora_da_entrada_ativa_lastx_como_op_unaria() {
        val s = run(*number("7").toTypedArray(), Event.StackOp.Enter, Event.Arith.Negate)
        assertEquals(d(-7), s.stack.x)
        assertEquals(d(7), s.stack.lastX, "Arith.Negate é unária e grava lastX")
    }

    @Test fun eex_basico_mantem_mantissa_e_adiciona_expoente() {
        // 1.5 EEX 3 → 1.5e3 = 1500
        val s = run(
            *number("1.5").toTypedArray(),
            Event.Entry.Eex,
            Event.Entry.Digit(3),
            Event.StackOp.Enter,
        )
        assertEquals(d(1500), s.stack.x)
    }

    @Test fun eex_com_chs_depois_inverte_sinal_do_expoente_nao_da_mantissa() {
        // 1.5 EEX 3 CHS → 1.5e-3 = 0.0015
        val s = run(
            *number("1.5").toTypedArray(),
            Event.Entry.Eex,
            Event.Entry.Digit(3),
            Event.Entry.ChangeSign,
            Event.StackOp.Enter,
        )
        assertEquals(d("0.0015"), s.stack.x)
    }

    @Test fun eex_vazio_eh_committable_como_1() {
        // EEX sem expoente digitado → "1E" → normaliza para "1E0" = 1
        val s = run(Event.Entry.Eex, Event.StackOp.Enter)
        assertEquals(d(1), s.stack.x)
    }

    @Test fun mantissa_nao_passa_de_10_digitos() {
        // Tenta digitar 11 dígitos; o 11º é ignorado.
        val s = run(
            *digits(1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 9).toTypedArray(), // 11 dígitos
            Event.StackOp.Enter,
        )
        assertEquals(d("1234567890"), s.stack.x, "11º dígito deve ter sido engolido")
    }

    // ─── 2. StackOp — ENTER / CLx / R↓ / x⇆y / LSTx ────────────────────────────

    @Test fun enter_preserva_t_e_empurra_z_y_x() {
        val s = run(
            *number("1").toTypedArray(), Event.StackOp.Enter,
            *number("2").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.StackOp.Enter, // T fora fica 1? Não: ENTER empurra sem destruir.
        )
        // T Z Y X:  após último ENTER: 2 3 4 4 (T antigo 1 caiu fora).
        assertEquals(d(4), s.stack.x)
        assertEquals(d(4), s.stack.y)
        assertEquals(d(3), s.stack.z)
        assertEquals(d(2), s.stack.t)
    }

    @Test fun clx_zera_x_e_desliga_stacklift_mesmo_no_meio_da_entrada() {
        val s = run(*number("1.5").toTypedArray(), Event.StackOp.ClearX)
        assertNull(s.entryBuffer, "CLx descarta o buffer de entrada")
        assertEquals(d(0), s.stack.x)
        assertFalse(s.stack.stackLiftEnabled)
    }

    @Test fun roll_down_funciona_com_pilha_cheia() {
        // Recria o cenário 6 do stack-behavior — agora via reducer, não só StackOps.
        val s = run(
            *number("1").toTypedArray(), Event.StackOp.Enter,
            *number("2").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(),
            Event.StackOp.RollDown,
        )
        assertEquals(d(3), s.stack.x)
        assertEquals(d(2), s.stack.y)
        assertEquals(d(1), s.stack.z)
        assertEquals(d(4), s.stack.t)
    }

    @Test fun swap_xy_no_reducer() {
        val s = run(
            *number("7").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(),
            Event.StackOp.SwapXY,
        )
        assertEquals(d(7), s.stack.x)
        assertEquals(d(3), s.stack.y)
    }

    @Test fun lstx_restaura_operando_destruido_por_binaria() {
        // 3 ENTER 4 × LSTx ÷ → 3 (cenário 3 da Seção 5 do stack-behavior)
        val s = run(
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.Arith.Multiply,
            Event.StackOp.LastX,
            Event.Arith.Divide,
        )
        assertEquals(d(3), s.stack.x)
    }

    // ─── 3. Aritmética ─────────────────────────────────────────────────────────

    @Test fun cenario_classico_cinco_enter_tres_mais_igual_oito() {
        val s = run(
            *number("5").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.Arith.Add,
        )
        assertEquals(d(8), s.stack.x)
        assertEquals(d(3), s.stack.lastX, "lastX ← X antigo da op binária")
    }

    @Test fun t_sticky_em_somatorio_em_cadeia() {
        // 5 ENTER 5 ENTER 5 ENTER 5 + + + → 20 (cenário 2 da Seção 5)
        val s = run(
            *number("5").toTypedArray(), Event.StackOp.Enter,
            *number("5").toTypedArray(), Event.StackOp.Enter,
            *number("5").toTypedArray(), Event.StackOp.Enter,
            *number("5").toTypedArray(),
            Event.Arith.Add, Event.Arith.Add, Event.Arith.Add,
        )
        assertEquals(d(20), s.stack.x)
        assertEquals(d(5), s.stack.t, "T sticky preservou o 5")
    }

    @Test fun clx_desliga_stacklift_e_proxima_digitacao_sobrescreve() {
        // 5 CLx 3 + → 3 (cenário 4 da Seção 5)
        val s = run(
            *number("5").toTypedArray(),
            Event.StackOp.ClearX,
            *number("3").toTypedArray(), Event.Arith.Add,
        )
        assertEquals(d(3), s.stack.x)
    }

    @Test fun divisao_por_zero_preserva_pilha_e_seta_pending_error_REGRA_8() {
        // Estabelecemos uma pilha conhecida, forçamos divisão por zero, validamos:
        //   1) pendingError == DivisionByZero
        //   2) pilha idêntica ao estado pré-operação
        val preErro = run(
            *number("5").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("2").toTypedArray(), Event.StackOp.Enter,
            *number("7").toTypedArray(), Event.StackOp.Enter,
            *number("0").toTypedArray(), // X=0, Y=7, Z=3, T=2 (últimos ENTER empurram 5 fora)
        )
        // Antes do Divide: commita X=0 → stack.x=0; buffer null; isEntering false.
        val estadoEsperadoPreDivisao = preErro.copy(
            stack = preErro.stack.copy(x = d(0), isEntering = false),
            entryBuffer = null,
        )

        val posErro = engine.reduce(preErro, Event.Arith.Divide)
        assertEquals(Hp12cError.DivisionByZero, posErro.pendingError)
        assertEquals(
            expected = estadoEsperadoPreDivisao.stack,
            actual   = posErro.stack,
            message  = "Divisão por zero DEVE preservar a pilha (regra 8 da Seção 5)",
        )
    }

    @Test fun operacoes_binarias_basicas() {
        assertEquals(d(10), run(*number("7").toTypedArray(), Event.StackOp.Enter, *number("3").toTypedArray(), Event.Arith.Add).stack.x)
        assertEquals(d(4),  run(*number("7").toTypedArray(), Event.StackOp.Enter, *number("3").toTypedArray(), Event.Arith.Subtract).stack.x)
        assertEquals(d(21), run(*number("7").toTypedArray(), Event.StackOp.Enter, *number("3").toTypedArray(), Event.Arith.Multiply).stack.x)
        assertEquals(d(2),  run(*number("6").toTypedArray(), Event.StackOp.Enter, *number("3").toTypedArray(), Event.Arith.Divide).stack.x)
    }

    // ─── 4. Memória ─────────────────────────────────────────────────────────────

    @Test fun sto_preserva_pilha_inteira_REGRA_7() {
        // Regra 7 fala de pilha **estável** (pós-commit). Fechamos a digitação com ENTER
        // extra para que `antes` já esteja comitado; assim a pilha resultante (4, 4, 3, 2)
        // serve de referência direta para comparar contra `depois`.
        val antes = run(
            *number("1").toTypedArray(), Event.StackOp.Enter,
            *number("2").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("4").toTypedArray(), Event.StackOp.Enter,
        )
        val depois = engine.reduce(antes, Event.Memory.Store(RegisterId.R3))
        assertEquals(antes.stack.x, depois.stack.x)
        assertEquals(antes.stack.y, depois.stack.y)
        assertEquals(antes.stack.z, depois.stack.z)
        assertEquals(antes.stack.t, depois.stack.t)
        assertEquals(antes.stack.lastX, depois.stack.lastX)
        assertEquals(antes.stack.stackLiftEnabled, depois.stack.stackLiftEnabled)
        assertEquals(d(4), depois.memory[RegisterId.R3], "STO escreveu X em R3")
    }

    @Test fun rcl_com_stacklift_ligado_eleva_a_pilha() {
        // Depois de 5 STO 3 CLx 7 → stackLift está ON (após acceptNewNumber no 7). RCL 3 eleva.
        val s = run(
            *number("5").toTypedArray(), Event.Memory.Store(RegisterId.R3),
            Event.StackOp.ClearX,
            *number("7").toTypedArray(),
            Event.Memory.Recall(RegisterId.R3),
        )
        assertEquals(d(5), s.stack.x, "RCL trouxe R3 para X")
        assertEquals(d(7), s.stack.y, "X₀=7 foi empurrado para Y (lift)")
    }

    @Test fun rcl_apos_enter_respeita_stacklift_desligado_sobrescrevendo_x() {
        // 5 STO 3  /  CLx  /  3 ENTER  /  RCL 3  —  depois do ENTER, stackLift=OFF.
        // Decisão de projeto: RCL "comporta-se como digitação" (pushValue), portanto
        // sobrescreve X em vez de empurrar. Se o aparelho físico mostrar comportamento
        // distinto, trocar pushValue por lift unconditional em Event.Memory.Recall.
        val s = run(
            *number("5").toTypedArray(), Event.Memory.Store(RegisterId.R3),
            Event.StackOp.ClearX,
            *number("3").toTypedArray(), Event.StackOp.Enter,
            Event.Memory.Recall(RegisterId.R3),
        )
        // Após "3 ENTER": stack=(3, 3, 0, 0), stackLift=OFF.
        // RCL 3 com stackLift=OFF: overwrite → stack=(5, 3, 0, 0).
        assertEquals(d(5), s.stack.x)
        assertEquals(d(3), s.stack.y)
    }

    @Test fun clear_reg_zera_memorias_mas_preserva_pilha() {
        val antes = run(
            *number("5").toTypedArray(), Event.Memory.Store(RegisterId.R3),
            *number("7").toTypedArray(), Event.Memory.Store(RegisterId.R7),
            *number("9").toTypedArray(), // digita X=9 mas não comita
            Event.StackOp.Enter,          // comita 9 e duplica
        )
        val depois = engine.reduce(antes, Event.Memory.ClearReg)
        assertEquals(Hp12cDecimal.ZERO, depois.memory[RegisterId.R3])
        assertEquals(Hp12cDecimal.ZERO, depois.memory[RegisterId.R7])
        assertEquals(antes.stack, depois.stack, "CLEAR REG não toca pilha")
    }

    // ─── 5. Display ─────────────────────────────────────────────────────────────

    @Test fun display_fix_nao_comita_entrada_em_curso() {
        // O usuário está digitando "1.2"; muda para FIX 4; continua digitando "3".
        val s = run(
            Event.Entry.Digit(1), Event.Entry.DecimalPoint, Event.Entry.Digit(2),
            Event.Display.Fix(4),
            Event.Entry.Digit(3),
            Event.StackOp.Enter,
        )
        assertEquals(d("1.23"), s.stack.x, "entrada continuou após mudança de formato")
        assertEquals(DisplayFormat.Fix(4), s.display)
    }

    @Test fun display_sci_e_eng_tambem_persistem_entrada() {
        val s1 = engine.reduce(initial, Event.Display.Sci(3))
        assertEquals(DisplayFormat.Sci(3), s1.display)
        val s2 = engine.reduce(initial, Event.Display.Eng(6))
        assertEquals(DisplayFormat.Eng(6), s2.display)
    }

    // ─── 6. Erro pendente + AcknowledgeError ───────────────────────────────────

    @Test fun qualquer_tecla_com_erro_pendente_limpa_erro_e_vira_no_op() {
        val comErro = run(*number("5").toTypedArray(), Event.StackOp.Enter,
                         *number("0").toTypedArray(), Event.Arith.Divide)
        assertNotNull(comErro.pendingError)

        // Qualquer tecla subsequente apenas limpa o erro — o "+" NÃO soma.
        val pos = engine.reduce(comErro, Event.Arith.Add)
        assertNull(pos.pendingError)
        assertEquals(comErro.stack, pos.stack, "o '+' foi engolido pela ACK implícita")
    }

    @Test fun acknowledge_error_explicito_limpa_erro() {
        val comErro = run(*number("1").toTypedArray(), Event.StackOp.Enter,
                         *number("0").toTypedArray(), Event.Arith.Divide)
        assertNotNull(comErro.pendingError)
        val pos = engine.reduce(comErro, Event.AcknowledgeError)
        assertNull(pos.pendingError)
    }

    @Test fun acknowledge_error_sem_erro_pendente_eh_no_op() {
        val s = engine.reduce(initial, Event.AcknowledgeError)
        assertEquals(initial, s)
    }

    // ─── 7. Regressões da Seção 5 — cobertura completa dos 8 cenários ─────────
    //  (1 a 6 já estão em StackOpsTest; 7 e 8 vivem aqui porque exigem o reducer.)

    @Test fun secao_5_cenario_7_sto_mantem_pilha_identica() {
        // "Após qualquer STO 3, a pilha permanece (X, Y, Z, T) idêntica."
        // Mesmo teste que `sto_preserva_pilha_inteira_REGRA_7`, repetido aqui com o rótulo
        // da Seção 5 para rastreabilidade do checklist. Fechamos a entrada com ENTER final
        // para comparar pilhas **estáveis** — antes do commit, stack.x é só o seed ZERO
        // (o valor real mora no entryBuffer). Isso não afeta a regra 7: ela diz respeito a
        // operar sobre estado estável, e antes/depois ambos estão nessa condição.
        val antes = run(
            *number("10").toTypedArray(), Event.StackOp.Enter,
            *number("20").toTypedArray(), Event.StackOp.Enter,
            *number("30").toTypedArray(), Event.StackOp.Enter,
            *number("40").toTypedArray(), Event.StackOp.Enter,
        )
        val depois = engine.reduce(antes, Event.Memory.Store(RegisterId.R3))
        assertEquals(antes.stack, depois.stack)
    }

    @Test fun secao_5_cenario_8_erro_deixa_pilha_identica_ao_estado_pre_op() {
        // "Qualquer Hp12cError deixa a pilha idêntica ao estado pré-operação."
        // Montamos pilha conhecida, forçamos divisão por zero, validamos que a pilha
        // pós-erro == pilha pós-commit (que é o estado exato que o reducer enxerga imediatamente
        // antes de tentar a operação).
        val antes = run(
            *number("1").toTypedArray(), Event.StackOp.Enter,
            *number("2").toTypedArray(), Event.StackOp.Enter,
            *number("3").toTypedArray(), Event.StackOp.Enter,
            *number("0").toTypedArray(), // X=0 ainda no buffer; commit acontece dentro do Divide
        )
        val preOpCommitted = antes.copy(
            stack = antes.stack.copy(x = d(0), isEntering = false),
            entryBuffer = null,
        )
        val posErro = engine.reduce(antes, Event.Arith.Divide)
        assertEquals(Hp12cError.DivisionByZero, posErro.pendingError)
        assertEquals(preOpCommitted.stack, posErro.stack)
    }
}
