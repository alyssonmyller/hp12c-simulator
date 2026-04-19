package com.arcom.hp12c.engine

import com.arcom.hp12c.engine.error.Hp12cError
import com.arcom.hp12c.engine.event.Event
import com.arcom.hp12c.engine.math.Hp12cDecimal
import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.TvmMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes do [DefaultEngine.reduce] para `Event.Financial.Solve.*` e para
 * `Event.Financial.ToggleCompoundFractionFlag`.
 *
 * Cobre **Fase 1 passo 5** (Solve.Fv/Pv/Pmt + flag C) **e passo 6** (Solve.N/Solve.I via
 * `Hp12cDecimal.ln`/`exp`/`pow`).
 *
 * ### Estratégia
 *
 * Cada vetor é executado em duas etapas: (a) eventos de Store para preencher os 4
 * registradores conhecidos + modo; (b) um único `Event.Financial.Solve.<var>`. Comparamos
 * `stack.x` contra o valor esperado da skill `test-vectors/tvm-vectors.json` usando uma
 * tolerância compatível com o `format` do vetor:
 *
 *   FIX 0 → tolerância 0,5      (inteiros exatos — teto da HP em Solve.N)
 *   FIX 2 → tolerância 0,005    (meio ULP em 2 casas)
 *
 * Essa tolerância blinda o teste contra a formatação (ainda não implementada — passo 7)
 * sem perder fidelidade: se a BCD interna diverge mais do que meio ULP da resposta que a
 * HP física exibiria, é bug numérico.
 *
 * Os 18 vetores da skill são cobertos de ponta a ponta:
 *
 *   - tvm-001, 011, 012, 017        — Solve.Fv (END, sem PMT)
 *   - tvm-007                       — Solve.Fv (END, com PMT, PV=0)
 *   - tvm-010                       — Solve.Fv (BEGIN, com PMT, PV=0)
 *   - tvm-002, 013                  — Solve.Pv (END, sem PMT)
 *   - tvm-005                       — Solve.Pv (END, com PMT, FV=0)
 *   - tvm-006, 018                  — Solve.Pmt (END, FV=0)
 *   - tvm-008                       — Solve.Pmt (END, PV=0)
 *   - tvm-009                       — Solve.Pmt (BEGIN, FV=0)
 *   - tvm-003, 015, 016             — Solve.N  (END, PMT=0, teto)
 *   - tvm-004, 014                  — Solve.I  (END, PMT=0, forma fechada)
 *
 * Também validamos comportamento não-numérico (esses **não** dependem de tolerância):
 *   - pilha após Solve: `X ← resultado` via `pushValue`, `LSTx ← X antigo`, outros níveis
 *     deslocam normalmente (regra 4 da Seção 5 de `stack-behavior.md`)
 *   - registrador resolvido: `financial.<var>` passa de `null` para o valor computado
 *     (para `Solve.N`, o valor em `financial.n` é o teto; para `Solve.I`, é em percentual)
 *   - ramo degenerado `i = 0`: fórmula linear (FV = -PV - n·PMT etc.)
 *   - sinais inválidos em Solve.N/Solve.I → `Hp12cError.TvmInvalidSigns`, pilha intacta
 *   - `ToggleCompoundFractionFlag` alterna só o boolean, sem tocar em mais nada
 */
class ReducerFinancialSolveTest {

    private val engine  = CalculatorEngine.Default
    private val initial = CalculatorEngine.InitialState

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun number(s: String): List<Event> = buildList {
        var s2 = s
        val negate = s2.startsWith("-").also { if (it) s2 = s2.substring(1) }
        for (ch in s2) {
            add(
                when (ch) {
                    '.'         -> Event.Entry.DecimalPoint
                    in '0'..'9' -> Event.Entry.Digit(ch.digitToInt())
                    else        -> error("digito inválido em number(\"$s\"): '$ch'")
                },
            )
        }
        if (negate) add(Event.Entry.ChangeSign)
    }

    /** Sequência padronizada: configura modo, grava os 4 registradores conhecidos e resolve. */
    private fun runTvm(
        mode: TvmMode,
        n: String, i: String, pv: String, pmt: String, fv: String,
        solve: Event.Financial.Solve,
    ): CalculatorState {
        val events = buildList {
            add(if (mode == TvmMode.BEGIN) Event.Financial.SetBeginMode else Event.Financial.SetEndMode)
            addAll(number(n));   add(Event.Financial.Store.N)
            addAll(number(i));   add(Event.Financial.Store.I)
            addAll(number(pv));  add(Event.Financial.Store.Pv)
            addAll(number(pmt)); add(Event.Financial.Store.Pmt)
            addAll(number(fv));  add(Event.Financial.Store.Fv)
            add(solve)
        }
        return engine.reduce(initial, events)
    }

    /**
     * Compara `computed` com a string `expected` (resposta oficial da skill) sob tolerância
     * compatível com o `format`. Tolerância é meio ULP na última casa exibida: 0,005 para
     * FIX 2. Se `computed` sair fora disso, temos erro numérico real — não problema de
     * formatação.
     */
    private fun assertNear(expected: String, computed: Hp12cDecimal, places: Int, vectorId: String) {
        val expectedDec = Hp12cDecimal.of(expected)
        val diff = computed - expectedDec
        val tol = Hp12cDecimal.of(tolString(places))
        assertTrue(
            diff < tol && diff > -tol,
            "$vectorId: esperado≈$expected, veio=$computed, diff=$diff (> ±$tol)",
        )
    }

    private fun tolString(places: Int): String = when (places) {
        0 -> "0.5"
        1 -> "0.05"
        2 -> "0.005"
        3 -> "0.0005"
        4 -> "0.00005"
        5 -> "0.000005"
        6 -> "0.0000005"
        else -> error("places fora do range suportado pelo helper: $places")
    }

    // ─── 1. Solve.Fv — END sem PMT (juros compostos puros) ───────────────────

    @Test fun tvm_001_fv_5000_em_5_meses_a_4pc_rende_6083_26() {
        // Moretti Cap. 4 Ex. 10: `FV = 5000·(1.04)^5 = 6083.26`. Caso mais básico — sem anuidade.
        val s = runTvm(TvmMode.END, "5", "4", "-5000", "0", "0", Event.Financial.Solve.Fv)
        assertNear("6083.26", s.stack.x, places = 2, "tvm-001")
    }

    @Test fun tvm_011_fv_4500_em_18_meses_a_8_5pc_rende_19541_05() {
        val s = runTvm(TvmMode.END, "18", "8.5", "-4500", "0", "0", Event.Financial.Solve.Fv)
        assertNear("19541.05", s.stack.x, places = 2, "tvm-011")
    }

    @Test fun tvm_012_fv_500_em_84_meses_a_4pc_rende_13482_50() {
        val s = runTvm(TvmMode.END, "84", "4", "-500", "0", "0", Event.Financial.Solve.Fv)
        assertNear("13482.50", s.stack.x, places = 2, "tvm-012")
    }

    @Test fun tvm_017_fv_100k_em_360_meses_a_1pc_rende_3594964_13() {
        // Manual Seção 3 — fator de longo prazo. Sanity-check do expoente grande sobre BCD10.
        val s = runTvm(TvmMode.END, "360", "1", "-100000", "0", "0", Event.Financial.Solve.Fv)
        assertNear("3594964.13", s.stack.x, places = 2, "tvm-017")
    }

    // ─── 2. Solve.Fv — com PMT (anuidade) ─────────────────────────────────────

    @Test fun tvm_007_poupanca_105_por_mes_por_24_meses_a_1_5pc_rende_3006_52() {
        // Moretti Cap. 6 Ex. 24: postecipado (END), PV=0. Série uniforme pura.
        val s = runTvm(TvmMode.END, "24", "1.5", "0", "-105", "0", Event.Financial.Solve.Fv)
        assertNear("3006.52", s.stack.x, places = 2, "tvm-007")
    }

    @Test fun tvm_010_poupanca_500_por_60_meses_a_0_8pc_begin_rende_38618_43() {
        // Moretti p.68: antecipado (BEGIN) — a série ganha um período a mais de juros,
        // exatamente o `(1+i)` que multiplica o anuity factor no nosso `begAdj`.
        val s = runTvm(TvmMode.BEGIN, "60", "0.8", "0", "-500", "0", Event.Financial.Solve.Fv)
        assertNear("38618.43", s.stack.x, places = 2, "tvm-010")
    }

    // ─── 3. Solve.Pv ──────────────────────────────────────────────────────────

    @Test fun tvm_002_pv_2000_em_24_meses_a_4pc_rende_780_24() {
        // Moretti Cap. 4 Ex. 11: `PV = 2000 / (1.04)^24 = 780.24`. Desconto composto puro.
        val s = runTvm(TvmMode.END, "24", "4", "0", "0", "-2000", Event.Financial.Solve.Pv)
        assertNear("780.24", s.stack.x, places = 2, "tvm-002")
    }

    @Test fun tvm_005_pv_de_6_parcelas_de_1500_a_3_5pc_end_rende_7992_83() {
        // Moretti Cap. 6 Ex. 22 — valor presente de série uniforme postecipada clássica.
        val s = runTvm(TvmMode.END, "6", "3.5", "0", "-1500", "0", Event.Financial.Solve.Pv)
        assertNear("7992.83", s.stack.x, places = 2, "tvm-005")
    }

    @Test fun tvm_013_pv_fv_25000_em_8_meses_a_3_5pc_rende_neg_18985_29() {
        // Moretti p.41 Ex. 4.8.8 — sinal negativo pela convenção HP (quem contratou paga).
        val s = runTvm(TvmMode.END, "8", "3.5", "0", "0", "25000", Event.Financial.Solve.Pv)
        assertNear("-18985.29", s.stack.x, places = 2, "tvm-013")
    }

    // ─── 4. Solve.Pmt ─────────────────────────────────────────────────────────

    @Test fun tvm_006_pmt_financiamento_12500_em_36_meses_a_2_7214pc_end_rende_neg_549() {
        // Moretti Cap. 6 Ex. 23: financiamento de veículo postecipado.
        val s = runTvm(TvmMode.END, "36", "2.7214", "12500", "0", "0", Event.Financial.Solve.Pmt)
        assertNear("-549.00", s.stack.x, places = 2, "tvm-006")
    }

    @Test fun tvm_008_pmt_para_acumular_5000_em_7_meses_a_4pc_end_rende_neg_633_05() {
        // Moretti Cap. 6 Ex. 25 — só FV e n/i: formação de poupança postecipada.
        val s = runTvm(TvmMode.END, "7", "4", "0", "0", "5000", Event.Financial.Solve.Pmt)
        assertNear("-633.05", s.stack.x, places = 2, "tvm-008")
    }

    @Test fun tvm_009_pmt_automovel_17800_em_36_meses_a_1_99pc_begin_rende_neg_683_62() {
        // Moretti p.67 — antecipado (BEGIN). PMT em BEGIN é `PMT_end / (1+i)`, o que a
        // nossa fórmula cobre dividindo pelo `begAdj = (1+i)` no denominador.
        val s = runTvm(TvmMode.BEGIN, "36", "1.99", "17800", "0", "0", Event.Financial.Solve.Pmt)
        assertNear("-683.62", s.stack.x, places = 2, "tvm-009")
    }

    @Test fun tvm_018_pmt_mortgage_50k_360_meses_a_0_75pc_end_rende_neg_402_31() {
        // Manual Seção 3 — "Home Mortgage". `PMT = PV·i / (1 - (1+i)^-n)` no caso END.
        val s = runTvm(TvmMode.END, "360", "0.75", "50000", "0", "0", Event.Financial.Solve.Pmt)
        assertNear("-402.31", s.stack.x, places = 2, "tvm-018")
    }

    // ─── 5. Solve.N — teto sempre arredondado para cima ──────────────────────

    @Test fun tvm_003_n_para_liquidar_4278_43_em_6559_68_a_3_25pc_eh_14_meses() {
        // Moretti Cap. 4 Ex. 12: valor exato ~13,36 meses → HP arredonda para 14.
        // Aceita tolerância 0,5 (FIX 0) porque o comparador usa diff < 0.5, mas na prática
        // a resposta tem que ser exatamente o inteiro 14 (teto da fração 13.36…).
        val s = runTvm(TvmMode.END, "0", "3.25", "-4278.43", "0", "6559.68", Event.Financial.Solve.N)
        assertNear("14", s.stack.x, places = 0, "tvm-003")
    }

    @Test fun tvm_015_n_para_600_virar_900_a_6pc_eh_7_meses() {
        // Moretti p.41 Ex. 4.8.15: valor exato ~6,96 meses → teto = 7.
        val s = runTvm(TvmMode.END, "0", "6", "-600", "0", "900", Event.Financial.Solve.N)
        assertNear("7", s.stack.x, places = 0, "tvm-015")
    }

    @Test fun tvm_016_n_para_350_virar_500_a_4pc_eh_10_meses() {
        // Moretti p.42 Ex. 4.8.16: valor exato ~9,09 meses → teto = 10. Fração >0, portanto
        // +1 sobre a parte inteira — validação direta do caminho `hasNonZero` da `ceil`.
        val s = runTvm(TvmMode.END, "0", "4", "-350", "0", "500", Event.Financial.Solve.N)
        assertNear("10", s.stack.x, places = 0, "tvm-016")
    }

    // ─── 6. Solve.I — forma fechada quando PMT = 0 ───────────────────────────

    @Test fun tvm_004_i_para_1210_72_virar_1695_01_em_9_meses_eh_3_81pc() {
        // Moretti Cap. 4 Ex. 13: i = (1695.01/1210.72)^(1/9) - 1 = 0.03809358 → 3,81% a.m.
        // A HP em FIX 6 mostra "3,809358"; em FIX 2 "3,81". Tolerância FIX 2 = 0,005 absorve
        // a diferença entre o valor computado e o display arredondado.
        val s = runTvm(TvmMode.END, "9", "0", "-1210.72", "0", "1695.01", Event.Financial.Solve.I)
        assertNear("3.81", s.stack.x, places = 2, "tvm-004")
    }

    @Test fun tvm_014_i_para_1000_virar_2000_em_7_meses_eh_10_41pc() {
        // Moretti p.41 Ex. 4.8.10: dobrar em 7 meses → i = 2^(1/7) - 1 ≈ 0,104089 → 10,41% a.m.
        // Inversão de sinal (PV positivo, FV negativo) — caminho `ratio = -FV/PV = 2`, positivo.
        val s = runTvm(TvmMode.END, "7", "0", "1000", "0", "-2000", Event.Financial.Solve.I)
        assertNear("10.41", s.stack.x, places = 2, "tvm-014")
    }

    // ─── 7. Ramo degenerado i = 0 ─────────────────────────────────────────────

    @Test fun solve_fv_com_i_zero_eh_somatorio_linear() {
        // i=0 colapsa juros compostos em aritmética simples:
        //   FV = -PV - n·PMT  =  1000 - 10·100  =  0
        // O ramo especial dentro de `computeFv` existe exatamente para evitar
        // divisão por `i` e retornar o valor fechado exato.
        val s = runTvm(TvmMode.END, "10", "0", "-1000", "100", "0", Event.Financial.Solve.Fv)
        assertEquals(Hp12cDecimal.ZERO, s.stack.x, "com i=0: FV = -(-1000) - 10·100 = 0")
    }

    @Test fun solve_pv_com_i_zero_eh_somatorio_linear() {
        // PV = -FV - n·PMT  =  -1000 - 5·0  =  -1000
        val s = runTvm(TvmMode.END, "5", "0", "0", "0", "1000", Event.Financial.Solve.Pv)
        assertEquals(Hp12cDecimal.of(-1000), s.stack.x, "com i=0: PV = -1000 - 5·0 = -1000")
    }

    @Test fun solve_pmt_com_i_zero_eh_razao_linear() {
        // PMT = -(PV + FV) / n  =  -(1000 + 0) / 10  =  -100
        val s = runTvm(TvmMode.END, "10", "0", "1000", "0", "0", Event.Financial.Solve.Pmt)
        assertEquals(Hp12cDecimal.of(-100), s.stack.x, "com i=0: PMT = -(1000+0)/10 = -100")
    }

    // ─── 8. Efeitos colaterais: pilha, LSTx, registrador resolvido ────────────

    @Test fun solve_fv_atualiza_registrador_financial_fv() {
        // Após Solve.Fv, o registrador `fv` não pode mais ser `null` — ele vira a resposta,
        // e um subsequente `RCL FV` (não coberto aqui) deve devolver o mesmo valor.
        val s = runTvm(TvmMode.END, "5", "4", "-5000", "0", "0", Event.Financial.Solve.Fv)
        assertNotNull(s.financial.fv, "Solve.Fv sobrescreveu fv com o resultado")
        assertEquals(s.stack.x, s.financial.fv, "mesmo valor em X e em fv")
        // Registradores "independentes" continuam com os valores digitados
        assertEquals(Hp12cDecimal.of(5),     s.financial.n)
        assertEquals(Hp12cDecimal.of(4),     s.financial.i)
        assertEquals(Hp12cDecimal.of(-5000), s.financial.pv)
    }

    @Test fun solve_pv_atualiza_registrador_financial_pv() {
        val s = runTvm(TvmMode.END, "24", "4", "0", "0", "-2000", Event.Financial.Solve.Pv)
        assertNotNull(s.financial.pv)
        assertEquals(s.stack.x, s.financial.pv)
    }

    @Test fun solve_pmt_atualiza_registrador_financial_pmt() {
        val s = runTvm(TvmMode.END, "7", "4", "0", "0", "5000", Event.Financial.Solve.Pmt)
        assertNotNull(s.financial.pmt)
        assertEquals(s.stack.x, s.financial.pmt)
    }

    @Test fun solve_empurra_resultado_em_X_e_guarda_X_antigo_em_LSTx() {
        // Solve é uma operação que "destrói X": ela produz o resultado em X via pushValue
        // (respeitando stackLift, assim como RCL) e joga o X antigo para LSTx, permitindo
        // recuperá-lo depois com g LSTx. O X imediatamente antes do Solve veio do commit de
        // `0 Store.Fv` — portanto stack.x = 0 naquele momento, e LSTx deve preservar esse 0
        // após o Solve (enquanto stack.x passa a ser o resultado computado).
        val s = runTvm(TvmMode.END, "5", "4", "-5000", "0", "0", Event.Financial.Solve.Fv)
        assertEquals(Hp12cDecimal.ZERO, s.stack.lastX, "LSTx ← X antigo (0) do último Store.Fv")
        assertNear("6083.26", s.stack.x, places = 2, "stack.x = resultado calculado")
    }

    // ─── 9. Registradores não-inicializados tratados como zero ───────────────

    @Test fun solve_fv_com_registradores_null_assume_zero() {
        // Convenção do manual (formulas/tvm.md §6): registrador não-setado vira zero
        // dentro de Solve. Aqui só preenchemos n, i e PV — PMT e FV ficam null.
        // Resultado esperado deve bater com tvm-001 (que preenche PMT=0 e FV=0 explícitos).
        val events = buildList {
            addAll(number("5"));     add(Event.Financial.Store.N)
            addAll(number("4"));     add(Event.Financial.Store.I)
            addAll(number("-5000")); add(Event.Financial.Store.Pv)
            add(Event.Financial.Solve.Fv)
        }
        val s = engine.reduce(initial, events)
        assertNull(s.pendingError, "null não é erro no Solve — é zero implícito")
        assertNear("6083.26", s.stack.x, places = 2, "mesmo resultado de tvm-001")
    }

    // ─── 9b. Efeitos colaterais específicos de Solve.N e Solve.I ─────────────

    @Test fun solve_n_atualiza_registrador_financial_n_com_o_teto() {
        // Após Solve.N, o registrador `n` guarda o **teto** (igual ao que o visor mostra) —
        // um `RCL n` subsequente devolve 14 (não 13.36). A HP física age assim desde o
        // Apêndice E do manual; ver nota em tvm-vectors.json tvm-003.
        val s = runTvm(TvmMode.END, "0", "3.25", "-4278.43", "0", "6559.68", Event.Financial.Solve.N)
        assertNotNull(s.financial.n, "Solve.N sobrescreveu n com o resultado")
        assertEquals(s.stack.x, s.financial.n, "mesmo valor em X e em financial.n")
        assertEquals(Hp12cDecimal.of(14), s.financial.n, "teto exato (não 13.36)")
    }

    @Test fun solve_i_atualiza_registrador_financial_i_em_percentual() {
        // O valor guardado em `financial.i` é o mesmo que o usuário digitaria: em percentual.
        // Um `RCL i` subsequente deve retornar ~3,81 (e não ~0,0381 em decimal).
        val s = runTvm(TvmMode.END, "9", "0", "-1210.72", "0", "1695.01", Event.Financial.Solve.I)
        assertNotNull(s.financial.i, "Solve.I sobrescreveu i com o resultado")
        assertEquals(s.stack.x, s.financial.i, "mesmo valor em X e em financial.i")
        val diff = s.financial.i!! - Hp12cDecimal.of("3.81")
        val tol = Hp12cDecimal.of("0.005")
        assertTrue(diff < tol && diff > -tol, "i em percentual, ~3.81: veio=${s.financial.i}")
    }

    @Test fun solve_n_empurra_resultado_em_X_e_guarda_X_antigo_em_LSTx() {
        // Regra 4 aplicada a Solve.N: X antigo (o 6559.68 do último Store.Fv, já comitado)
        // vai para LSTx; X novo é o `n` ceiled.
        val s = runTvm(TvmMode.END, "0", "3.25", "-4278.43", "0", "6559.68", Event.Financial.Solve.N)
        assertEquals(Hp12cDecimal.of("6559.68"), s.stack.lastX, "LSTx ← X antigo")
        assertEquals(Hp12cDecimal.of(14), s.stack.x, "X ← teto(13.36) = 14")
    }

    @Test fun solve_i_empurra_resultado_em_X_e_guarda_X_antigo_em_LSTx() {
        val s = runTvm(TvmMode.END, "9", "0", "-1210.72", "0", "1695.01", Event.Financial.Solve.I)
        assertEquals(Hp12cDecimal.of("1695.01"), s.stack.lastX, "LSTx ← X antigo")
    }

    @Test fun solve_n_com_sinais_inconsistentes_dispara_tvm_invalid_signs() {
        // PV e FV com o mesmo sinal e PMT=0: não existe `n` positivo que satisfaça a equação.
        // Ratio `-FV/PV` fica negativo, `ln` lança, reducer mapeia para TvmInvalidSigns.
        // Pilha e registradores preservados (regra 8).
        val events = buildList {
            addAll(number("10"));    add(Event.Financial.Store.I)     // i = 10%
            addAll(number("-1000")); add(Event.Financial.Store.Pv)
            addAll(number("-500"));  add(Event.Financial.Store.Fv)    // mesmo sinal que PV
            add(Event.Financial.Solve.N)
        }
        val s = engine.reduce(initial, events)
        assertEquals(Hp12cError.TvmInvalidSigns, s.pendingError)
        assertNull(s.financial.n, "n permanece null após erro — registrador preservado")
    }

    @Test fun solve_i_com_sinais_inconsistentes_dispara_tvm_invalid_signs() {
        // PV e FV mesmo sinal, PMT=0: ratio `-FV/PV` ≤ 0, `ln` inválido.
        val events = buildList {
            addAll(number("5"));     add(Event.Financial.Store.N)
            addAll(number("1000"));  add(Event.Financial.Store.Pv)
            addAll(number("500"));   add(Event.Financial.Store.Fv)
            add(Event.Financial.Solve.I)
        }
        val s = engine.reduce(initial, events)
        assertEquals(Hp12cError.TvmInvalidSigns, s.pendingError)
        assertNull(s.financial.i, "i permanece null após erro — registrador preservado")
    }

    // ─── 10. Flag C (STO EEX / ToggleCompoundFractionFlag) ───────────────────

    @Test fun toggle_compound_fraction_flag_alterna_booleano() {
        // Estado inicial: flag desligada (juros simples no período fracionário — default HP).
        assertEquals(false, initial.compoundFractionFlag)

        val s1 = engine.reduce(initial, Event.Financial.ToggleCompoundFractionFlag)
        assertEquals(true,  s1.compoundFractionFlag)

        val s2 = engine.reduce(s1, Event.Financial.ToggleCompoundFractionFlag)
        assertEquals(false, s2.compoundFractionFlag, "duas aplicações voltam ao default")
    }

    @Test fun toggle_compound_fraction_flag_nao_altera_pilha_registradores_nem_buffer() {
        // STO EEX é pura flag: não comita buffer em digitação, não toca em pilha nem em TVM.
        // (O efeito real da flag só aparece na Fase 2 com `n` fracionário.)
        val antes = engine.reduce(
            initial,
            buildList {
                addAll(number("5"));     add(Event.Financial.Store.N)
                addAll(number("4"));     add(Event.Financial.Store.I)
                addAll(number("-5000")); add(Event.Financial.Store.Pv)
                addAll(number("42"))                    // buffer em curso "42"
            },
        )
        val depois = engine.reduce(antes, Event.Financial.ToggleCompoundFractionFlag)
        assertEquals(true, depois.compoundFractionFlag)
        assertEquals(antes.stack,      depois.stack,      "pilha intacta")
        assertEquals(antes.financial,  depois.financial,  "registradores TVM intactos")
        assertEquals(antes.entryBuffer, depois.entryBuffer, "buffer de digitação preservado")
        assertEquals(antes.memory,     depois.memory,     "memórias de usuário intactas")
    }
}
