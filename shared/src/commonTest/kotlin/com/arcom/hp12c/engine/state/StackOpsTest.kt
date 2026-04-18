package com.arcom.hp12c.engine.state

import com.arcom.hp12c.engine.math.Hp12cDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes de [StackOps] — a camada de pilha pura. Cobre:
 *
 *   1. Cada primitiva conforme os diagramas da Seção 3 de `referencias/stack-behavior.md`
 *      (enter, clx, R↓, R↑, x⇆y, LSTx, binária, unária, percent).
 *   2. Flags: `stackLiftEnabled`, `isEntering` e `lastX` — quem mexe em cada um.
 *   3. Os 6 cenários de regressão da Seção 5 que exercitam só pilha (os dois restantes —
 *      STO preservar pilha e erro preservar pilha — são do reducer, chegam no passo 3).
 *
 * Todos os testes ficam em `commonTest` e rodam via `:shared:jvmTest` (o alvo mais rápido
 * enquanto o actual iOS é stub). Nomes dos testes evitam backtick com caracteres especiais
 * para compat com o alvo iOS Kotlin/Native.
 */
class StackOpsTest {

    // --- Helpers ---

    private fun d(n: Int) = Hp12cDecimal.of(n)
    private fun d(n: String) = Hp12cDecimal.of(n)

    /** Constrói um `Stack` didático com valores inteiros em cada nível. */
    private fun stackOf(
        x: Int, y: Int, z: Int, t: Int,
        lastX: Int = 0,
        stackLift: Boolean = true,
        entering: Boolean = false,
    ) = Stack(
        x = d(x), y = d(y), z = d(z), t = d(t),
        lastX = d(lastX),
        stackLiftEnabled = stackLift,
        isEntering = entering,
    )

    // ─── 1. ENTER ──────────────────────────────────────────────────────────────

    @Test fun enter_duplica_x_em_y_e_empurra() {
        // T₀ Z₀ Y₀ X₀ → Z₀ Y₀ X₀ X₀
        val antes = stackOf(x = 9, y = 7, z = 5, t = 3, lastX = 1)
        val depois = antes.enter()
        assertEquals(d(9), depois.x)
        assertEquals(d(9), depois.y)
        assertEquals(d(7), depois.z)
        assertEquals(d(5), depois.t)
        assertEquals(d(1), depois.lastX, "ENTER não toca LAST X")
        assertFalse(depois.stackLiftEnabled, "ENTER desliga stackLift")
        assertFalse(depois.isEntering, "ENTER encerra entrada")
    }

    // ─── 2. CLx ────────────────────────────────────────────────────────────────

    @Test fun clx_zera_x_sem_mexer_no_resto() {
        val antes = stackOf(x = 42, y = 7, z = 5, t = 3, lastX = 99)
        val depois = antes.clx()
        assertEquals(Hp12cDecimal.ZERO, depois.x)
        assertEquals(d(7), depois.y)
        assertEquals(d(5), depois.z)
        assertEquals(d(3), depois.t)
        assertEquals(d(99), depois.lastX, "CLx não toca LAST X")
        assertFalse(depois.stackLiftEnabled, "CLx desliga stackLift")
        assertFalse(depois.isEntering)
    }

    // ─── 3. Roll down / Roll up / x⇆y ─────────────────────────────────────────

    @Test fun roll_down_rotaciona_pilha_toda() {
        // T Z Y X → X T Z Y
        val antes = stackOf(x = 1, y = 2, z = 3, t = 4, lastX = 9)
        val depois = antes.rollDown()
        assertEquals(d(2), depois.x)
        assertEquals(d(3), depois.y)
        assertEquals(d(4), depois.z)
        assertEquals(d(1), depois.t)
        assertEquals(d(9), depois.lastX, "R↓ não toca LAST X")
        assertTrue(depois.stackLiftEnabled)
    }

    @Test fun roll_up_rotaciona_pilha_toda_na_direcao_oposta() {
        // T Z Y X → Z Y X T  (X pega T₀)
        val antes = stackOf(x = 1, y = 2, z = 3, t = 4)
        val depois = antes.rollUp()
        assertEquals(d(4), depois.x)
        assertEquals(d(1), depois.y)
        assertEquals(d(2), depois.z)
        assertEquals(d(3), depois.t)
        assertTrue(depois.stackLiftEnabled)
    }

    @Test fun swap_xy_troca_apenas_os_dois_niveis_inferiores() {
        val antes = stackOf(x = 1, y = 2, z = 3, t = 4, lastX = 9)
        val depois = antes.swapXY()
        assertEquals(d(2), depois.x)
        assertEquals(d(1), depois.y)
        assertEquals(d(3), depois.z)
        assertEquals(d(4), depois.t)
        assertEquals(d(9), depois.lastX, "x⇆y não toca LAST X")
    }

    // ─── 4. LSTx ───────────────────────────────────────────────────────────────

    @Test fun lstx_eleva_pilha_e_coloca_lastx_em_x_sem_mudar_lastx() {
        val antes = stackOf(x = 10, y = 20, z = 30, t = 40, lastX = 7)
        val depois = antes.lstx()
        assertEquals(d(7),  depois.x,     "X recebe LAST X")
        assertEquals(d(10), depois.y,     "Y recebe X₀ (lift)")
        assertEquals(d(20), depois.z,     "Z recebe Y₀")
        assertEquals(d(30), depois.t,     "T recebe Z₀ (T₀ cai fora)")
        assertEquals(d(7),  depois.lastX, "LAST X não muda")
        assertTrue(depois.stackLiftEnabled)
    }

    // ─── 5. Binária ─────────────────────────────────────────────────────────────

    @Test fun binary_op_desce_pilha_e_mantem_t_sticky_salvando_lastx() {
        // T Z Y X → T T Z f(Y,X), LASTX = X₀
        val antes = stackOf(x = 3, y = 4, z = 5, t = 6)
        val depois = antes.binaryOp { y, x -> y + x }
        assertEquals(d(7), depois.x,     "X recebe f(Y,X) = 4+3")
        assertEquals(d(5), depois.y,     "Y recebe Z₀")
        assertEquals(d(6), depois.z,     "Z recebe T₀")
        assertEquals(d(6), depois.t,     "T permanece (sticky)")
        assertEquals(d(3), depois.lastX, "LAST X = X antigo")
        assertTrue(depois.stackLiftEnabled)
    }

    @Test fun binary_op_propaga_arithmetic_exception_na_divisao_por_zero() {
        // Contrato: o reducer captura e mapeia para Hp12cError.DivisionByZero.
        // Aqui só confirmamos que a exceção sobe.
        val antes = stackOf(x = 0, y = 10, z = 0, t = 0)
        kotlin.test.assertFailsWith<ArithmeticException> {
            antes.binaryOp { y, x -> y / x }
        }
    }

    // ─── 6. Unária ─────────────────────────────────────────────────────────────

    @Test fun unary_op_afeta_apenas_x_salvando_lastx() {
        val antes = stackOf(x = 5, y = 4, z = 3, t = 2)
        val depois = antes.unaryOp { -it } // CHS como exemplo (fora do contexto de entrada)
        assertEquals(d(-5), depois.x)
        assertEquals(d(4),  depois.y,     "Y intacto")
        assertEquals(d(3),  depois.z,     "Z intacto")
        assertEquals(d(2),  depois.t,     "T intacto")
        assertEquals(d(5),  depois.lastX, "LAST X = X antigo")
        assertTrue(depois.stackLiftEnabled)
    }

    // ─── 7. Percent ────────────────────────────────────────────────────────────

    @Test fun percent_op_preserva_y_e_salva_lastx() {
        // O mote: 300 ENTER 15 % → X = 45, Y = 300 (não desce pilha).
        val antes = stackOf(x = 15, y = 300, z = 7, t = 99)
        val depois = antes.percentOp { y, x ->
            // (Y × X) / 100 — forma do % usual da HP; só importa aqui a forma estrutural.
            (y * x) / d(100)
        }
        assertEquals(d(45),  depois.x)
        assertEquals(d(300), depois.y,     "Y NÃO desce")
        assertEquals(d(7),   depois.z,     "Z intacto")
        assertEquals(d(99),  depois.t,     "T intacto")
        assertEquals(d(15),  depois.lastX, "LAST X = X antigo")
    }

    // ─── 8. Digitação com stackLift ────────────────────────────────────────────

    @Test fun accept_new_number_eleva_quando_stacklift_ligado() {
        val antes = stackOf(x = 1, y = 2, z = 3, t = 4, stackLift = true)
        val depois = antes.acceptNewNumber(d(9))
        assertEquals(d(9), depois.x, "novo X")
        assertEquals(d(1), depois.y, "Y ← X₀ (lift)")
        assertEquals(d(2), depois.z, "Z ← Y₀")
        assertEquals(d(3), depois.t, "T ← Z₀ (T₀ cai fora)")
        assertTrue(depois.stackLiftEnabled)
        assertTrue(depois.isEntering, "usuário agora está no meio da digitação")
    }

    @Test fun accept_new_number_sobrescreve_quando_stacklift_desligado() {
        // Típico: logo após ENTER ou CLx, primeira digitação não empurra.
        val antes = stackOf(x = 1, y = 2, z = 3, t = 4, stackLift = false)
        val depois = antes.acceptNewNumber(d(9))
        assertEquals(d(9), depois.x, "novo X overwrite")
        assertEquals(d(2), depois.y, "Y intacto")
        assertEquals(d(3), depois.z, "Z intacto")
        assertEquals(d(4), depois.t, "T intacto")
        assertTrue(depois.stackLiftEnabled, "religa após aceitar")
        assertTrue(depois.isEntering)
    }

    // ─── 9. Cenários de regressão (Seção 5 do stack-behavior.md) ───────────────
    //
    // Simulação: dígitos são sintetizados via `acceptNewNumber`. O reducer real fará a
    // composição digito-a-dígito; aqui basta validar o comportamento *pós-aceitação*.

    @Test fun cenario_1_cinco_enter_tres_mais_da_oito() {
        // 5 ENTER 3 +  → X = 8, Y = 0
        val s = Stack()
            .acceptNewNumber(d(5))      // 5 0 0 0
            .enter()                    // 5 5 0 0, stackLift OFF
            .acceptNewNumber(d(3))      // 3 5 0 0 (overwrite porque stackLift OFF)
            .binaryOp { y, x -> y + x } // 8 0 0 0
        assertEquals(d(8), s.x)
        assertEquals(d(0), s.y)
        assertEquals(d(3), s.lastX)
    }

    @Test fun cenario_2_soma_em_cadeia_de_quatro_cincos_da_vinte() {
        // 5 ENTER 5 ENTER 5 ENTER 5 + + +  → X = 20 (exercita T sticky)
        val s = Stack()
            .acceptNewNumber(d(5)).enter()  // 5 5 0 0
            .acceptNewNumber(d(5)).enter()  // 5 5 5 0
            .acceptNewNumber(d(5)).enter()  // 5 5 5 5
            .acceptNewNumber(d(5))          // 5 5 5 5 (stackLift OFF do último ENTER)
            .binaryOp { y, x -> y + x }     // 10 5 5 5 (T sticky = 5)
            .binaryOp { y, x -> y + x }     // 15 5 5 5
            .binaryOp { y, x -> y + x }     // 20 5 5 5
        assertEquals(d(20), s.x)
        assertEquals(d(5),  s.y, "T sticky preservou o 5")
        assertEquals(d(5),  s.z)
        assertEquals(d(5),  s.t)
    }

    @Test fun cenario_3_lstx_resgata_operando_destruido_por_multiplicacao() {
        // 3 ENTER 4 × LSTx ÷  → X = 3 (recupera o 4 via LAST X)
        val s = Stack()
            .acceptNewNumber(d(3)).enter()  // 3 3 0 0
            .acceptNewNumber(d(4))          // 4 3 0 0
            .binaryOp { y, x -> y * x }     // 12 0 0 0, lastX=4
            .lstx()                         // 4 12 0 0, lastX=4
            .binaryOp { y, x -> y / x }     // 3 0 0 0
        assertEquals(d(3), s.x)
    }

    @Test fun cenario_4_clx_desliga_stacklift_e_proxima_digitacao_sobrescreve() {
        // 5 CLx 3 +  → X = 3 (CLx deixou stackLift OFF; 3 sobrescreveu X)
        val s = Stack()
            .acceptNewNumber(d(5))          // 5 0 0 0
            .clx()                          // 0 0 0 0, stackLift OFF
            .acceptNewNumber(d(3))          // 3 0 0 0 (overwrite, não lift)
            .binaryOp { y, x -> y + x }     // 3 0 0 0 (0 + 3)
        assertEquals(d(3), s.x)
    }

    @Test fun cenario_5_trezentos_enter_quinze_percent_menos_da_duzentos_e_cinquenta_e_cinco() {
        // 300 ENTER 15 % -  → X = 255 (percent NÃO desce pilha)
        val s = Stack()
            .acceptNewNumber(d(300)).enter()                  // 300 300 0 0
            .acceptNewNumber(d(15))                           // 15 300 0 0
            .percentOp { y, x -> (y * x) / d(100) }           // 45 300 0 0
            .binaryOp { y, x -> y - x }                       // 255 0 0 0
        assertEquals(d(255), s.x)
    }

    @Test fun cenario_6_roll_down_sobre_pilha_cheia() {
        // 1 ENTER 2 ENTER 3 ENTER 4 R↓  → (X, Y, Z, T) = (3, 2, 1, 4)
        val s = Stack()
            .acceptNewNumber(d(1)).enter()   // 1 1 0 0
            .acceptNewNumber(d(2)).enter()   // 2 2 1 0
            .acceptNewNumber(d(3)).enter()   // 3 3 2 1
            .acceptNewNumber(d(4))           // 4 3 2 1
            .rollDown()                      // 3 2 1 4
        assertEquals(d(3), s.x)
        assertEquals(d(2), s.y)
        assertEquals(d(1), s.z)
        assertEquals(d(4), s.t)
    }
}
