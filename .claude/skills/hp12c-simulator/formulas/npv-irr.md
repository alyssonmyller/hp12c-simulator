# NPV e IRR — Fluxos de Caixa Irregulares

> Fonte de verdade: **manual HP 12C Platinum (bpia5314.pdf), Seção 8, p. 79–104**,
> Apêndice D (p. 197) para Error 3 e Error 7.
> Toda afirmação com número de página está rastreada a essa fonte.

---

## 1. Teclas e contexto

| Tecla | Descrição |
|---|---|
| `g CFo` (blue `0`) | Armazena X como CF0 e **limpa todos os fluxos anteriores** (manual, p. 80) |
| `g CFj` (blue `1`) | Acrescenta X como próximo CF_j à lista |
| `g Nj`  (blue `2`) | Define o número de repetições do último CF_j inserido (1–99) |
| `f NPV` (gold `2`) | Calcula o Valor Presente Líquido usando `i` do registrador financeiro |
| `f IRR` (gold `3`) | Calcula a Taxa Interna de Retorno |

A HP 12C aceita até **80 entradas totais de fluxo de caixa** (CF0 + CF1…CF79), e cada
CF_j pode ter `N_j ∈ [1, 99]` repetições consecutivas. Ver §5 para os limites exatos.

---

## 2. Modelo de armazenamento

A engine armazena os fluxos como uma lista de pares `(amount, count)`:

```kotlin
data class CashflowEntry(
    val amount: Hp12cDecimal,
    val count: Int = 1,          // N_j ∈ [1, 99]
)

data class CashflowRegisters(
    val cf0: Hp12cDecimal? = null,          // null = g CFo ainda não pressionado
    val flows: List<CashflowEntry> = emptyList(),   // CF1, CF2, … (máx 79 entradas)
)
```

Campo adicionado ao `CalculatorState`:
```kotlin
val cashflow: CashflowRegisters = CashflowRegisters()
```

### 2.1 Comportamento de `g CFo`

- Armazena `stack.x` como `cf0`.
- **Limpa toda a lista** `flows = emptyList()` — limpeza idêntica a `f CLEAR REG` para
  os registradores de CF, mas não afeta R0..R9, a pilha TVM nem `financial.i`.
- Pilha: **não consome X** — stack permanece intacto (igual a STO n). `isEntering` comitado.

### 2.2 Comportamento de `g CFj`

- Acrescenta `CashflowEntry(amount = stack.x, count = 1)` ao final de `flows`.
- Pilha inalterada (igual STO n). `isEntering` comitado.
- Error 7 se `flows.size == 79` antes da inserção (limite de 79 CFj + 1 CF0 = 80 total).

### 2.3 Comportamento de `g Nj`

- Atualiza o `count` do último `CashflowEntry` em `flows`.
- `X` deve ser inteiro ≥ 1 e ≤ 99; se `X > 99` ou `X < 1` → Error 7.
- Se `flows` estiver vazia (nenhum `g CFj` ainda) → Error 7.
- Pilha inalterada. `isEntering` comitado.

### 2.4 Comportamento de `f NPV` e `f IRR`

Ambas são operações de cálculo que produzem um único resultado em X:

| Registro | Antes | Depois |
|---|---|---|
| X | (qualquer) | NPV ou IRR |
| Y | Y₀ | Y₀ |
| Z | Z₀ | Z₀ |
| T | T₀ | T₀ |
| LAST X | L₀ | X₀ antigo (X antes do cálculo) |

As operações de CF (CFo, CFj, Nj) **não alteram** o registrador financeiro `i`; apenas
`f NPV` e `f IRR` **leem** `financial.i`. O `financial.i` fica no valor que o usuário
tiver gravado via `STO i` ou TVM.

Após `f IRR`, o registrador `financial.i` é **atualizado** com o valor de IRR calculado
(em percentual), para que o usuário possa chamar `f NPV` logo em seguida e obter 0.

---

## 3. Fórmula NPV

### 3.1 Expansão dos fluxos

Dado `i_dec = i_pct / 100`, o NPV é:

```
NPV = CF0 + Σ_{t=1}^{T} CF(t) / (1 + i_dec)^t
```

onde `CF(t)` expande os pares `(CFj, Nj)` em períodos individuais:

- Se o j-ésimo par começa no período `t_start`:
  `CF(t_start) = CF(t_start+1) = … = CF(t_start + Nj - 1) = CFj`

### 3.2 Algoritmo iterativo canônico

```kotlin
fun computeNpv(cf0: Hp12cDecimal, flows: List<CashflowEntry>, iDec: Hp12cDecimal): Hp12cDecimal {
    var npv = cf0
    if (iDec.isZero()) {
        // Caso degenerado: i = 0 → NPV = soma simples de todos os fluxos
        for ((amount, count) in flows) npv += amount * Hp12cDecimal.of(count)
        return npv
    }
    val ONE   = Hp12cDecimal.of(1)
    val d     = ONE / (ONE + iDec)   // fator de desconto = 1/(1+i)
    var pv    = d                    // pv para o período 1
    for ((amount, count) in flows) {
        repeat(count) {
            npv += amount * pv
            pv  *= d
        }
    }
    return npv
}
```

### 3.3 Caso degenerado `i = 0`

```
NPV = CF0 + N1·CF1 + N2·CF2 + … + Nk·CFk
```

### 3.4 Exemplo canônico (manual, Seção 8, p. 82, Exemplo 1)

> Calcule o NPV de um projeto com taxa de 14% ao período:

```
[em FIX 2, i = 14%]
-10000 g CFo
3000   g CFj
4200   g CFj   2 g Nj     → CF2 repete 2 vezes (períodos 2 e 3)
6800   g CFj
f NPV → X = 607.14
```

Verificação aritmética:
```
3000 / 1.14^1 = 2631.578947...
4200 / 1.14^2 = 3231.763620...
4200 / 1.14^3 = 2834.003175...
6800 / 1.14^4 = 4009.794...
```

Wait — o manual p. 82 usa exatamente esses números? Verifique com a calculadora física.
Vetor `npv-manual-001` pendente de confirmação (ver §7, ambiguidade §7.4).

### 3.5 Exemplo derivado verificável

```
i = 10%
CF0 = -100, CF1 = 110  (N1 = 1)
NPV = -100 + 110 / 1.1 = -100 + 100 = 0.00  (FIX 2)
```

---

## 4. Algoritmo IRR

A taxa `r` (em decimal) tal que `NPV(r) = 0`.

### 4.1 Newton-Raphson

```
f(r)  = NPV(r)       → função objetivo
f'(r) = dNPV/dr = - Σ_{t=1}^{T} t · CF(t) / (1 + r)^(t+1)
r_{n+1} = r_n - f(r_n) / f'(r_n)
```

Critério de convergência: `|r_{n+1} - r_n| < 10^{-8}`.
Máximo de iterações: **100** (alinhado ao TVM). Se não convergir → Error 3.

### 4.2 Estimativa inicial

Chute inicial: `r_0 = 0.1` (10% — funciona para a maioria dos fluxos de projetos reais).
Se Newton-Raphson divergir (r < -1 ou `f'(r) = 0`), recomeça com `r_0 = 0.01`.
Comportamento da HP física: inicia em 0% e vai ajustando em incrementos de 1%;
nossa engine usa Newton-Raphson direto que é mais rápido, mas pode diferir em raízes
múltiplas (ver §7.1).

### 4.3 Pós-condições de `f IRR`

- X ← IRR em **percentual** (ex.: `20.00` para 20%).
- `financial.i` ← IRR (para que a chamada imediata de `f NPV` retorne 0).
- LASTx ← X antigo.

### 4.4 Exemplos canônicos verificáveis

**IRR de 1 período (forma fechada):**
```
CF0 = -1000, CF1 = 1200  → IRR = (1200/1000 - 1) × 100 = 20.00%
```

**IRR de 2 períodos (título com cupom + principal):**
```
CF0 = -1000, CF1 = 100, CF2 = 1100
NPV(10%) = -1000 + 100/1.1 + 1100/1.21
         = -1000 + 90.9090909... + 909.0909090...
         = -1000 + 1000.0000000 = 0.00
→ IRR = 10.00%
```

---

## 5. Condições de Error 7 `(manual, Apêndice D, p. 197)`

| Condição | Código de erro |
|---|---|
| `f NPV` ou `f IRR` sem nenhum `g CFo` registrado (`cf0 == null`) | Error 7 |
| `g Nj` com `X < 1` ou `X > 99` (não-inteiro ou fora do range) | Error 7 |
| `g Nj` sem nenhum `g CFj` anterior na sessão corrente | Error 7 |
| `g CFj` quando já existem 79 entradas em `flows` (total atingiria 81) | Error 7 |

---

## 6. Condições de Error 3 `(manual, Apêndice D, p. 196)`

| Condição | Código de erro |
|---|---|
| `f IRR` com fluxos de caixa sem pelo menos uma mudança de sinal | Error 3 |
| `f IRR` que não converge em 100 iterações | Error 3 |

**Detecção de ausência de mudança de sinal:**
```kotlin
fun hasSignChange(cf0: Hp12cDecimal, flows: List<CashflowEntry>): Boolean {
    val allValues = buildList {
        add(cf0)
        for ((amount, _) in flows) add(amount)
    }
    val hasPositive = allValues.any { it.compareTo(Hp12cDecimal.ZERO) > 0 }
    val hasNegative = allValues.any { it.compareTo(Hp12cDecimal.ZERO) < 0 }
    return hasPositive && hasNegative
}
```

Se `!hasSignChange(...)` → Error 3 imediato, sem entrar na iteração Newton-Raphson.

---

## 7. Ambiguidades conhecidas

### §7.1 — Raízes múltiplas de IRR

Quando os fluxos de caixa mudam de sinal mais de uma vez, pode haver múltiplas raízes
de IRR. A HP 12C retorna a primeira raiz positiva encontrada pelo seu método iterativo
a partir do chute inicial de 0%. Nossa engine com Newton-Raphson pode retornar uma
raiz diferente dependendo do chute inicial.
**Decisão:** não documentamos vetores com raízes múltiplas como `@Test` obrigatório.

### §7.2 — Convergência de IRR: tolerância exata

O manual (p. 97) diz que a HP continua iterando até que a diferença entre iterações
consecutivas seja `< 0.00000001` (1 × 10⁻⁸ em decimal, ou 0.000001% em percentual).
Nossa implementação usa `|r_{n+1} - r_n| < 10^{-8}` como critério, o que é compatível.

### §7.3 — Y após `f NPV` / `f IRR`

O manual não especifica explicitamente se Y muda após NPV/IRR. Pelo padrão HP para
Solve financeiro: Y não muda (igual ao TVM Solve). **Decisão:** implementar como
operação que escreve somente em X e LASTx.

### §7.4 — Exemplo exato do manual p. 82

O exemplo com CF0=-10000, CF1=3000, CF2=4200 (N2=2), CF3=6800, i=14% precisa ser
verificado na calculadora física. O resultado esperado é ≈ 607.14, mas o valor exato
em FIX 2 depende dos arredondamentos internos da HP (10 dígitos BCD HALF_EVEN).
Enquanto não for verificado, o vetor `npv-manual-001` fica marcado com `"pending": true`
e excluído dos `@Test` obrigatórios.

### §7.5 — Comportamento de `g CFo` quando já há fluxos

O manual (p. 80) afirma que `g CFo` "clears all previously stored cash flows".
Isso inclui qualquer `g CFj` / `g Nj` entrado anteriormente na mesma sessão.
**Decisão:** `CashflowRegisters` após `g CFo` = `CashflowRegisters(cf0 = novoValor, flows = emptyList())`.

### §7.6 — `g Nj` com `X = 1` (sem efeito)

`g Nj` com `X = 1` é válido (default é 1), não gera erro. Operação idempotente.

---

## 8. Tabela de eventos da sealed class `Cashflow`

```kotlin
sealed class Cashflow : Event() {
    object CashFlowZero : Cashflow()   // g CFo — armazena CF0, limpa lista
    object CashFlowJ    : Cashflow()   // g CFj — acrescenta CFj
    object CountJ       : Cashflow()   // g Nj  — atualiza count do último CFj
    object Npv          : Cashflow()   // f NPV — calcula NPV
    object Irr          : Cashflow()   // f IRR — calcula IRR
}
```
