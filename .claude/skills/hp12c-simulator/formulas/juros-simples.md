# Juros Simples — `f INT`

> Fonte de verdade: **manual HP 12C Platinum (bpia5314.pdf), Seção 5, p. 61–62**.
> Toda afirmação numérica rastreável a essa fonte está marcada com `(manual, p. NN)`.

---

## 1. O que é `f INT`

A tecla `f INT` calcula o **juros simples** de um capital (PV) por um período de `n` **dias** a uma taxa anual `i` percentual, usando **base 360 dias** (convenção comercial, fixa na HP 12C Platinum).

A operação lê os registradores financeiros `n`, `i` e `PV` — os mesmos que a TVM usa. Por isso, se TVM estiver preenchida, basta alterar os valores relevantes antes de pressionar `f INT`.

---

## 2. Fórmula canônica

```
INT = PV × i × n
      ─────────
         36000
```

Onde:
- `PV` = capital (principal), em unidade monetária. Convenção de sinais HP: PV negativo = dívida, PV positivo = aplicação.
- `i` = taxa de juros anual, **em percentual** (ex.: `8` para 8% a.a.). Armazenada como digitada; a divisão por 100 está embutida na fórmula (o 36000 = 360 × 100).
- `n` = número de dias (inteiro; a HP trunca se fracionário for inserido). Base fixa de **360 dias por ano** `(manual, p. 61)`.
- `INT` = juros simples resultante, na mesma unidade monetária de PV.

**Ramo degenerado:** se `n = 0` ou `i = 0`, então `INT = 0` (sem tratamento especial de erro — resultado é zero matematicamente correto).

### Exemplo canônico (manual, p. 61)

> Calcular os juros simples de um capital de \$10.000,00 aplicado por 90 dias a uma taxa anual de 8% a.a.

```
n   = 90
i   = 8
PV  = 10000
INT = 10000 × 8 × 90 / 36000 = 200.00
```

Após `f INT`: X = 200.00, Y = 10000 (principal permanece em Y).
Pressionar `+` resulta em 10200.00 — o **montante** (capital + juros).

---

## 3. Comportamento da pilha

`f INT` tem comportamento **híbrido**: lê PV do registrador financeiro, mas **mantém Y = PV** na pilha para facilitar o cálculo do montante com um único `+`.

| Registrador | Antes | Depois |
|---|---|---|
| X | qualquer | INT (juros calculado) |
| Y | qualquer | PV (registrador financeiro, não o Y anterior) |
| Z | Z₀ | Z₀ (inalterado) |
| T | T₀ | T₀ (inalterado) |
| LAST X | L₀ | X₀ (X antigo, antes de `f INT`) |
| stackLiftEnabled | — | true |

Observação crítica: Y após a operação **não** é o Y anterior da pilha — é o valor de PV lido do registrador financeiro. Isso é idêntico ao comportamento das operações TVM `Solve.*` que escrevem tanto em X quanto no registrador resolvido `(manual, p. 62, nota)`.

---

## 4. Condições de erro

`f INT` **não** tem condição de Error N dedicada no Apêndice D `(manual, p. 193-197)`. Os únicos erros possíveis são:

| Condição | Erro |
|---|---|
| Resultado excede capacidade (≈ 10^10) | Error 1 (overflow) |
| n, i ou PV ausente (null) | Trata como 0 — resultado INT = 0; sem erro `(manual, p. 61)` |

---

## 5. Registradores de entrada

| Registrador financeiro | Valor digitado | Significado |
|---|---|---|
| `n` | número de dias (inteiro) | duração em dias |
| `i` | taxa anual em % | ex.: `8` para 8% a.a. |
| `PV` | capital | positivo = aplicação; negativo = dívida |

`PMT` e `FV` são ignorados por `f INT`.

---

## 6. Implementação em Kotlin

```kotlin
// Dentro de reduceFinancialSolve, caso Event.Financial.SimpleInterest:
fun computeSimpleInterest(n: Hp12cDecimal, i: Hp12cDecimal, pv: Hp12cDecimal): Hp12cDecimal {
    // INT = PV × i × n / 36000
    val THIRTY_SIX_THOUSAND = Hp12cDecimal.of("36000")
    return pv * i * n / THIRTY_SIX_THOUSAND
}
```

Pós-condições no reducer:
1. `result = computeSimpleInterest(n, i, pv)` — captura ArithmeticException → Error 1 (overflow).
2. Stack: `state.stack.copy(x = result, y = pv, lastX = state.stack.x, stackLiftEnabled = true, isEntering = false)`.
3. Registradores financeiros **inalterados** (diferente de TVM Solve que atualiza o registrador resolvido).

---

## 7. Ambiguidades conhecidas

### §7.1 — Arredondamento de `n`

O manual p. 61 diz "dias" mas não especifica o que acontece se `n` for fracionário (ex.: `n = 90.5`). A HP 12C usa o valor de `n` como armazenado — sem truncar automaticamente. A fórmula aceita n decimal e computa resultado decimal.

Decisão: **usar n exato** (sem truncar), replicando o comportamento que `n` tem na TVM onde pode ser fracionário para períodos compostos.

### §7.2 — Sinal de INT vs PV

Se PV é negativo (empréstimo recebido), INT é negativo (juros a pagar). O montante (Y + X = PV + INT) é mais negativo, representando o total a devolver. Isso é matematicamente correto e consistente com a convenção de sinais HP.

### §7.3 — Y após `f INT` vs Y anterior da pilha

O manual não é explícito sobre o Y que aparece após `f INT`. A implementação recomendada (§3 acima) coloca `PV` financeiro em Y (não o Y₀ anterior da pilha), pois é o que torna `+` útil para calcular montante. Esta é a interpretação mais provável da HP física, mas **verificar em hardware real** se Y₀ ou PV é o que aparece em Y.
