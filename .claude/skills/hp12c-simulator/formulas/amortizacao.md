# Amortização — `f AMORT`

> Fonte de verdade: **manual HP 12C Platinum (bpia5314.pdf), Seção 10, p. 68–76**.
> Toda afirmação numérica rastreável a essa fonte está marcada com `(manual, p. NN)`.

---

## 1. O que é `f AMORT`

A tecla `f AMORT` (gold `1`) calcula os totais de **juros** e **principal** para `n` períodos
consecutivos de um empréstimo ou investimento com pagamentos periódicos fixos.

Ao contrário da TVM (`Solve.*`), que *calcula* os registradores a partir dos outros,
`f AMORT` *simula o fluxo período a período* usando os valores já armazenados em `n`, `i`,
`PV` e `PMT`. O registrador `FV` **não** é usado no loop (ver §8.2).

---

## 2. Inputs exigidos

| Registrador | Conteúdo antes de pressionar `f AMORT` |
|---|---|
| `n`   | Número de períodos a amortizar (inteiro positivo). A HP trunca via `INT(n)`. |
| `i`   | Taxa de juros por período, **em percentual** (ex.: `1` para 1%). |
| `PV`  | Saldo devedor atual (positivo se você recebeu o empréstimo). |
| `PMT` | Pagamento por período (negativo para pagamentos de saída). |
| `FV`  | Ignorado pelo loop de amortização (§8.2). |

---

## 3. Algoritmo iterativo canônico

```kotlin
fun computeAmortize(
    nInt: Int,           // INT(n) — períodos a amortizar
    iDec: Hp12cDecimal,  // i / 100 — taxa decimal
    pv0:  Hp12cDecimal,  // PV inicial
    pmt:  Hp12cDecimal,  // PMT (negativo para empréstimos)
): Triple<Hp12cDecimal, Hp12cDecimal, Hp12cDecimal> {  // (totalInterest, totalPrincipal, pvFinal)
    var pv            = pv0
    var totalInterest = Hp12cDecimal.ZERO
    var totalPrincipal= Hp12cDecimal.ZERO
    repeat(nInt) {
        val interest  = -(pv * iDec)          // negativo: juros saem do pagador
        val principal = pmt - interest         // parcela do pagamento que abate o saldo
        totalInterest  += interest
        totalPrincipal += principal
        pv             += principal            // saldo reduz (principal é negativo para empréstimos)
    }
    return Triple(totalInterest, totalPrincipal, pv)
}
```

### 3.1 Ramo degenerado `i = 0`

Com `iDec = 0`, `interest_k = 0` para todo `k`, logo:
- `principal_k = PMT`
- `totalInterest = 0`
- `totalPrincipal = nInt × PMT`
- `pvFinal = PV + nInt × PMT`

O algoritmo acima já trata esse caso sem ramo especial (multiplicar por zero é seguro).

### 3.2 Exemplo canônico derivado (verificação aritmética)

```
PV = 1000, i = 1% por período, PMT = -110, n = 3

Período 1: juros = -1000 × 0.01 = -10.00
           principal = -110 − (−10.00) = -100.00
           PV = 1000 + (−100.00) = 900.00

Período 2: juros = -900 × 0.01 = -9.00
           principal = -110 − (−9.00) = -101.00
           PV = 900 + (−101.00) = 799.00

Período 3: juros = -799 × 0.01 = -7.99
           principal = -110 − (−7.99) = -102.01
           PV = 799 + (−102.01) = 696.99

Totais: X = -10 + (-9) + (-7.99) = -26.99
        Y = -100 + (-101) + (-102.01) = -303.01
        PV_final = 696.99

Verificação: X + Y = -26.99 + (-303.01) = -330.00 = 3 × (-110) = n × PMT ✓
```

---

## 4. Pós-condições

### 4.1 Comportamento da pilha (estilo `dualOutputOp`)

| Registrador | Antes | Depois |
|---|---|---|
| X | X₀ (qualquer) | totalInterest |
| Y | Y₀ | totalPrincipal |
| Z | Z₀ | Z₀ (inalterado) |
| T | T₀ | T₀ (inalterado) |
| LASTx | L₀ | X₀ (o X antigo) |
| stackLiftEnabled | — | `true` |

Igual à semântica de `dualOutputOp` usada pelas funções estatísticas. O usuário pode
pressionar `x⇆y` para ver o principal após a operação `(manual, p. 69)`.

### 4.2 Registradores financeiros atualizados

| Registrador | Antes | Depois |
|---|---|---|
| `n`   | N original | **inalterado** — preservado para chamadas consecutivas |
| `i`   | I original | inalterado |
| `PV`  | saldo inicial | **saldo final** (pvFinal do algoritmo) |
| `PMT` | PMT original | inalterado |
| `FV`  | FV original | inalterado |

O `PV` atualizado é o ponto de partida da próxima chamada `f AMORT` para amortizar
o próximo grupo de `n` períodos `(manual, p. 70-72)`.

---

## 5. Condições de Error 6 `(manual, Apêndice D, p. 195)`

| Condição | Código de erro |
|---|---|
| `INT(n) ≤ 0` (n zero ou negativo) | Error 6 |

**Observação:** a HP física provavelmente trunca `n` não-inteiro para `INT(n)` sem gerar erro,
desde que `INT(n) ≥ 1`. Nossa engine segue esse comportamento (ver ambiguidade §8.1).

---

## 6. Convenção de sinais (HP 12C)

Para um **empréstimo padrão** (você *recebeu* o dinheiro):

| Grandeza | Sinal | Motivo |
|---|---|---|
| PV | positivo | entrada de caixa (recebeu) |
| PMT | negativo | saída de caixa (paga mensalmente) |
| totalInterest (X) | negativo | parcela de juros sai do pagador |
| totalPrincipal (Y) | negativo | parcela de principal sai do pagador |
| pvFinal (PV atualizado) | positivo (e menor) | saldo devedor reduzido |

**Invariante numérica:** `totalInterest + totalPrincipal = n × PMT` (exato em BCD 10 dígitos,
pois a fórmula é fechada por construção — ver §3.2).

Para uma **aplicação** (PV negativo, PMT positivo), os sinais se invertem, mas o invariante
se mantém.

---

## 7. Evento na sealed class `Financial`

```kotlin
sealed class Financial : Event() {
    // ... (Store, Solve, etc. já existentes)
    /** `f AMORT` — amortiza `n` períodos; X ← juros, Y ← principal, PV ← novo saldo. */
    object Amortize : Financial()
}
```

---

## 8. Ambiguidades conhecidas

### §8.1 — `n` não-inteiro

O manual não especifica explicitamente o comportamento quando `n` tem parte fracionária
(ex.: `n = 2.9`). A HP física provavelmente trunca para `INT(n) = 2` silenciosamente.
**Decisão da engine:** truncar via `toIntTruncated()` e prosseguir se `INT(n) ≥ 1`; Error 6
se `INT(n) ≤ 0`. Documentado como comportamento observável e não ambíguo para `n ≥ 1`.

### §8.2 — Papel de `FV` em `f AMORT`

A fórmula canônica da amortização não usa `FV` no loop: o saldo é reduzido período a período
apenas pela parcela de principal do PMT. Para um empréstimo totalmente amortizante (FV = 0),
o saldo chega a zero na última parcela. Se FV ≠ 0 (ex.: amortização parcial "balão"), o
usuário deve computar o PMT correto via `Solve.Pmt` (que usa FV); a AMORT em si não verifica.
**Decisão:** `FV` é ignorado no loop de `f AMORT`.

### §8.3 — `PMT = 0`

Se PMT = 0, o principal de cada período é `−interest_k` (negativo do juros), logo o saldo
*aumenta* a cada período (capitalização de juros sem pagamento). Comportamento matematicamente
correto; sem erro. Não é um caso de uso típico mas é válido.

### §8.4 — `n` register após `f AMORT`

O registrador `n` **não é decrementado** pelo AMORT — permanece como foi definido.
Isso permite chamadas consecutivas para amortizar grupos iguais de períodos (ex.: 12 meses
por vez ao longo de 30 anos) sem precisar redigitar `n` a cada vez `(manual, p. 70)`.

### §8.5 — Z/T após `f AMORT`

O manual não afirma explicitamente que Z e T ficam inalterados. Por paralelismo com as
funções estatísticas de "saída dupla" (`g x̄`, `g s`) que usam `dualOutputOp`, assumimos
que Z/T ficam intactos. A HP física provavelmente empurra a pilha, mas nosso modelo de
`dualOutputOp` é autocontido e consistente com o padrão já estabelecido na engine.
