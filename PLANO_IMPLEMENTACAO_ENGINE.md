# Plano de Implementação — Engine HP 12C (Fase 1 Completa)

**Objetivo:** Fazer todos 111 testes passarem (verde em CI/CD)  
**Status inicial:** 18/111 testes verdes (TVM completo), 93/111 testes vermelhos  
**Prazo estimado:** 30-40 horas de desenvolvimento focado

---

## Sumário Executivo

A engine está **estruturalmente completa** — o Reducer e a arquitetura Event/State estão prontos. O que falta é **implementar 4 grupos de funções financeiras** que ainda têm stubs:

| Grupo | Testes | Status | Esforço |
|---|---|---|---|
| **Estatística** | 27 | 🔴 Vermelho | 10h |
| **Calendário** | 15 | 🔴 Vermelho | 8h |
| **Fluxo de Caixa** | 17 | 🔴 Vermelho | 12h |
| **Aritmética/Transf.** | 54 | 🟢 Verde | — |
| **TVM** | 18 | 🟢 Verde | — |
| **TOTAL** | 111 | | **30h** |

---

## Parte 1 — Estatística (27 testes, ~10h)

### O que falta

A suite `StatisticsVectorsTest` carrega 27 vetores de `test-vectors/estatistica-vectors.json`. Cada vetor testa uma sequência de operações estatísticas (`Σ+`, `Σ-`, `x̄`, `s`, `ŷ,r`, `x̂,r`, `x̄w`) com saídas simples ou duplas (X e Y simultâneos).

### Estrutura de dados (já em `FinancialRegisters`)

```kotlin
// Em CalculatorState.financialRegisters:
data class FinancialRegisters(
    // TVM (Fase 1)
    var n: Hp12cDecimal = Hp12cDecimal.ZERO
    var i: Hp12cDecimal = Hp12cDecimal.ZERO
    // ... PV, PMT, FV

    // Estatística (aqui, **shared** com R1..R6 do usuário — sem campo separado!)
    // A HP física usa os mesmos registradores:
    // R1 = Σx, R2 = Σx², R3 = Σy, R4 = Σy², R5 = Σxy, R6 = n
    // Quando o usuário faz STO 3, corrompe Σy do meio de uma regressão — isso é certo!
)
```

**Decisão arquitetural:** Não há campo separado `statisticalState.sumX`. Em vez disso, as operações `Σ+`/`Σ-` **escrevem diretamente** em `registers[1]`, `registers[2]`, etc., mantendo o aparelho fiel à física.

### Passos de implementação

#### Passo 1.1 — `reduceStatistics` handler no Reducer

Adicionar ao `DefaultEngine`:

```kotlin
private fun reduceStatistics(state: CalculatorState, event: Event.Statistics): CalculatorState {
    return when (event) {
        is Event.Statistics.Accumulate     -> reduceAccumulate(state, event)
        is Event.Statistics.Deaccumulate   -> reduceDeaccumulate(state, event)
        is Event.Statistics.Mean           -> reduceMean(state)
        is Event.Statistics.StdDev         -> reduceStdDev(state)
        is Event.Statistics.WeightedMean   -> reduceWeightedMean(state)
        is Event.Statistics.YHatR          -> reduceYHatR(state, event)
        is Event.Statistics.XHatR          -> reduceXHatR(state, event)
        is Event.Statistics.Clear          -> reduceClearStatistics(state)
    }
}
```

#### Passo 1.2 — Acumulação (`Σ+` / `Σ-`)

Cada `Σ+` lê X (regressão univariada) ou X e Y (bivariada) e atualiza:
- `n` (em R6) ← `n + 1`
- `Σx` (em R1) ← `Σx + x`
- `Σx²` (em R2) ← `Σx² + x²`
- `Σy` (em R3) ← `Σy + y` (se bivariada)
- `Σy²` (em R4) ← `Σy² + y²` (se bivariada)
- `Σxy` (em R5) ← `Σxy + x·y` (se bivariada)

**Pseudocódigo:**

```kotlin
private fun reduceAccumulate(state: CalculatorState, event: Event.Statistics.Accumulate): CalculatorState {
    if (state.pendingError != null) return state
    
    val x = state.stack.x
    val y = state.stack.y
    val n = state.financialRegisters.getStatN() + 1 // lê R6
    
    val newX = state.financialRegisters.getSumX() + x // lê R1
    val newX2 = state.financialRegisters.getSumX2() + (x * x) // lê R2
    
    val newState = state.copy(
        financialRegisters = state.financialRegisters.copy(
            registersSigma = state.financialRegisters.registersSigma.copy(
                // R1 ← newX, R2 ← newX2, R6 ← n
            )
        ),
        stack = state.stack.copy(x = Hp12cDecimal(n)) // X ← nova contagem
    )
    return newState
}
```

**Referência:** `.claude/skills/hp12c-simulator/formulas/estatistica.md` § "Acumulação em Σ+/Σ-"

#### Passo 1.3 — Cálculos derivados

Média, desvio-padrão, regressão — todos leem acumuladores e aplicam fórmulas fechadas:

**Média simples:**
```
x̄ = Σx / n
```

**Desvio-padrão amostral (Bessel):**
```
s = √[ (n·Σx² − (Σx)²) / (n(n−1)) ]
```

**Regressão linear (ŷ,r):**
```
b = (n·Σxy − Σx·Σy) / (n·Σx² − (Σx)²)
a = ȳ − b·x̄
ŷ = a + b·x_novo
r = (n·Σxy − Σx·Σy) / √[ (n·Σx² − (Σx)²)·(n·Σy² − (Σy)²) ]
```

Essas operações têm **saída dupla** — escrevem X **e** Y na pilha simultaneamente (inovação em relação a TVM):

```kotlin
private fun reduceMean(state: CalculatorState): CalculatorState {
    val n = state.financialRegisters.getStatN()
    val sumX = state.financialRegisters.getSumX()
    
    if (n == Hp12cDecimal.ZERO) {
        return state.copy(pendingError = Hp12cError.Error2) // Error 2: stat division by zero
    }
    
    val mean = sumX / n
    return state.copy(
        stack = state.stack.copy(
            x = mean,
            y = mean // X e Y recebem a mesma média
        )
    )
}
```

**Referência:** `.claude/skills/hp12c-simulator/formulas/estatistica.md` § "Fórmulas de saída"

#### Passo 1.4 — Testes

Rodar `StatisticsVectorsTest`:

```bash
./gradlew :shared:jvmTest --tests "*StatisticsVectorsTest*"
```

Esperado: 27 testes verdes.

**Nota sobre divergências conhecidas:** A skill documenta 7 ambiguidades em estatística (ex.: `r` inválido só dispara Error 2 no swap, não no cálculo). Todas estão no JSON com flags.

---

## Parte 2 — Calendário (15 testes, ~8h)

### O que falta

A suite `CalendarVectorsTest` carrega 15 vetores de `test-vectors/calendario-vectors.json`. Cada vetor testa conversão de datas em formatos D.MY ↔ M.DY, cálculo de diferença de dias (exato e comercial), e lookup de dia da semana.

### Estrutura de dados (já em `CalculatorState`)

```kotlin
data class CalculatorState(
    // ...
    var dateFormat: DateFormat = DateFormat.MDY, // flag persistente: M.DY ou D.MY
)

enum class DateFormat {
    MDY,  // "10.152005" = outubro 15 de 2005 (default HP)
    DMY   // "15.102005" = 15 de outubro de 2005 (Brasil)
}
```

### Passos de implementação

#### Passo 2.1 — Codificação/decodificação de datas

A HP guarda uma data como um número decimal com formato `mm.ddyyyy` (M.DY) ou `dd.mmyyyy` (D.MY), onde `.` é só visual (o registro contém um número com 10 dígitos de mantissa BCD).

**Decodificação M.DY → (mm, dd, yyyy):**

```kotlin
private fun decodeDateMdy(date: Hp12cDecimal): Triple<Int, Int, Int> {
    val str = date.toBigDecimal().toPlainString()
    // Retirar ponto (se houver): "10.152005" → "10152005"
    val digits = str.replace(".", "")
    
    // Primeiros 2: mês, próximos 2: dia, últimos 4: ano
    val mm = digits.substring(0, 2).toInt()
    val dd = digits.substring(2, 4).toInt()
    val yyyy = digits.substring(4, 8).toInt()
    
    return Triple(mm, dd, yyyy)
}
```

**Codificação (mm, dd, yyyy) → date:**

```kotlin
private fun encodeeDateMdy(mm: Int, dd: Int, yyyy: Int): Hp12cDecimal {
    val str = "%02d%02d%04d".format(mm, dd, yyyy)
    // Inserir ponto: "10152005" → "10.152005" (mas isso é só display)
    return Hp12cDecimal.of("${str.substring(0, 2)}.${str.substring(2)}")
}
```

**Referência:** `.claude/skills/hp12c-simulator/formulas/calendario.md` § "Codificação de datas"

#### Passo 2.2 — Cálculo de número serial de data

A HP interna converte datas em "dias desde uma época" usando a fórmula (Apêndice E, p. 200):

```
f(dd, mm, yyyy) = 365·yyyy + 31·(mm−1) + dd + INT(z/4) − x
```

onde:
- `z = yyyy` (ou `yyyy−1` se for antes do mês em questão)
- `x = INT(0.4·mm + 2.3)` (ajuste para mês)

```kotlin
private fun dateToSerial(mm: Int, dd: Int, yyyy: Int): Int {
    // Validar intervalo: 15 out 1582 ≤ data ≤ 25 nov 4046
    if (yyyy < 1582 || (yyyy == 1582 && (mm < 10 || (mm == 10 && dd < 15)))) {
        return error(Hp12cError.Error8)  // Underflow
    }
    if (yyyy > 4046 || (yyyy == 4046 && (mm > 11 || (mm == 11 && dd > 25)))) {
        return error(Hp12cError.Error8)  // Overflow
    }
    
    val yearAdj = if (mm <= 2) yyyy - 1 else yyyy
    val z = yearAdj
    val x = ((0.4 * mm + 2.3).toInt())
    
    return 365 * yyyy + 31 * (mm - 1) + dd + (z / 4).toInt() - x
}
```

**Referência:** `.claude/skills/hp12c-simulator/formulas/calendario.md` § "Fórmula de serial"

#### Passo 2.3 — Operações de calendário

```kotlin
private fun reduceCalendar(state: CalculatorState, event: Event.Calendar): CalculatorState {
    return when (event) {
        is Event.Calendar.ToggleDateFormat -> state.copy(
            dateFormat = if (state.dateFormat == DateFormat.MDY) 
                DateFormat.DMY else DateFormat.MDY
        )
        is Event.Calendar.DateDifference -> {
            val date1Serial = dateToSerial(...) // pop Y
            val date2Serial = dateToSerial(...) // pop X
            val exact = date2Serial - date1Serial
            val commercial = computeCommercial(date2Serial, date1Serial)
            state.copy(
                stack = state.stack.copy(
                    x = Hp12cDecimal(exact),
                    y = Hp12cDecimal(commercial)
                )
            )
        }
        is Event.Calendar.DateAdd -> {
            val serial = dateToSerial(...)
            val daysToAdd = state.stack.x.toInt()
            val newSerial = serial + daysToAdd
            val (mm, dd, yyyy) = serialToDate(newSerial)
            val dow = computeDayOfWeek(newSerial)
            state.copy(
                stack = state.stack.copy(x = Hp12cDecimal(encoded))
            )
        }
    }
}
```

#### Passo 2.4 — Testes

```bash
./gradlew :shared:jvmTest --tests "*CalendarVectorsTest*"
```

Esperado: 15 testes verdes.

**Nota importante:** O JSON contém 1 teste "vermelho esperado" (`cal-delta-mdy-erratum`) que documenta a errata conhecida do manual. A engine calcula o valor correto (499); o manual exibe 498 por erro de tipografia.

---

## Parte 3 — Fluxo de Caixa (17 testes, ~12h)

### O que falta

A suite `CashflowVectorsTest` carrega 17 vetores de `test-vectors/cashflow-vectors.json`. Cada vetor testa NPV (Valor Presente Líquido) e IRR (Taxa Interna de Retorno) sobre sequências de fluxos.

### Estrutura de dados (já em `FinancialRegisters`)

```kotlin
data class FinancialRegisters(
    // ... (TVM)
    var cashflows: Array<Hp12cDecimal> = Array(21) { Hp12cDecimal.ZERO } // CFo, CF1..CF20
    var cashflowCounts: Array<Int> = Array(21) { 1 } // Nj para cada CFi
)
```

### Passos de implementação

#### Passo 3.1 — Armazenamento de fluxos

```kotlin
private fun reduceCashflow(state: CalculatorState, event: Event.Cashflow): CalculatorState {
    return when (event) {
        is Event.Cashflow.SetInitialFlow -> state.copy(
            financialRegisters = state.financialRegisters.copy(
                cashflows[0] = state.stack.x
            ),
            stack = state.stack.rollDown()
        )
        is Event.Cashflow.SetFlow -> {
            // CFj → registra CF em R(i), onde i é o índice atual
            // Nj → define quantas vezes repete
        }
        is Event.Cashflow.Npv -> {
            // Ler i de FinancialRegisters.i
            // Computar: Σ CFk / (1+i)^tk para cada CF
            val npv = computeNpv(state.financialRegisters, state.financialRegisters.i)
            state.copy(stack = state.stack.copy(x = npv))
        }
        is Event.Cashflow.Irr -> {
            // Newton-Raphson para encontrar i tal que NPV(i) = 0
            val irr = computeIrr(state.financialRegisters)
            state.copy(
                financialRegisters = state.financialRegisters.copy(i = irr),
                stack = state.stack.copy(x = irr)
            )
        }
    }
}
```

#### Passo 3.2 — NPV (Valor Presente Líquido)

```kotlin
private fun computeNpv(
    cashflows: FinancialRegisters,
    discountRate: Hp12cDecimal
): Hp12cDecimal {
    var npv = Hp12cDecimal.ZERO
    var time = 0
    
    for (i in 0..20) {
        val cf = cashflows.cashflows[i]
        val count = cashflows.cashflowCounts[i]
        
        for (j in 0 until count) {
            val factor = Hp12cDecimal(1) + discountRate / HUNDRED
            val discounted = cf / factor.pow(time)
            npv += discounted
            time++
        }
    }
    
    return npv
}
```

**Referência:** `.claude/skills/hp12c-simulator/formulas/cashflow.md` § "Fórmula NPV"

#### Passo 3.3 — IRR (Taxa Interna de Retorno)

IRR é a raiz da equação `NPV(i) = 0`. A HP usa Newton-Raphson com diferença finita central:

```kotlin
private fun computeIrr(cashflows: FinancialRegisters): Hp12cDecimal {
    // Tentar chutes iniciais em ordem: 0%, 10%, -50%, 100%
    val initialGuesses = listOf(
        Hp12cDecimal.ZERO,
        Hp12cDecimal.of(10),
        Hp12cDecimal.of(-50),
        Hp12cDecimal.of(100)
    )
    
    for (guess in initialGuesses) {
        val irr = newtonRaphson(cashflows, guess)
        if (irr != null) {
            return irr
        }
    }
    
    // Nenhum convergiou
    return error(Hp12cError.Error3)  // IRR não convergiou
}

private fun newtonRaphson(
    cashflows: FinancialRegisters,
    initialGuess: Hp12cDecimal,
    maxIterations: Int = 100,
    tolerance: BigDecimal = BigDecimal("1E-6")
): Hp12cDecimal? {
    var x = initialGuess
    
    for (iter in 0 until maxIterations) {
        val h = Hp12cDecimal.of("1E-6")
        
        // Diferença finita central
        val f = computeNpv(cashflows, x)
        val fPlusH = computeNpv(cashflows, x + h)
        val fMinusH = computeNpv(cashflows, x - h)
        val derivative = (fPlusH - fMinusH) / (2 * h)
        
        if (derivative == Hp12cDecimal.ZERO) {
            return null  // Derivada nula, não converge neste chute
        }
        
        val xNext = x - (f / derivative)
        
        if ((xNext - x).abs() < tolerance) {
            return xNext  // Convergiu!
        }
        
        x = xNext
    }
    
    return null  // Máximas iterações atingidas
}
```

**Referência:** `.claude/skills/hp12c-simulator/formulas/cashflow.md` § "Algoritmo Newton-Raphson"

#### Passo 3.4 — Testes

```bash
./gradlew :shared:jvmTest --tests "*CashflowVectorsTest*"
```

Esperado: 17 testes verdes.

**Nota sobre Erro 6 vs Erro 7:** O JSON contém 4 vetores que disparam Erro 6 (overflow de registrador, Nj inválido). A implementação atual de `Hp12cError.kt` mapeia tudo para Erro 7 — isso é uma limitação conhecida documentada na skill. Correção fica para Fase 2.

---

## Parte 4 — Transversais (já verde, validação)

As seguintes suites já têm testes verdes e **não precisam de work** nesta sessão:

- **Transcendentais** (34 testes) — ln, exp, pow, sin/cos/tan (se implementado), raiz, fatorial
- **Aritmética básica** (54 testes via ReducerTest) — +, −, ×, ÷, %, percentagem, pilha
- **TVM** (18 testes) — PV, PMT, FV, n, i, modo BEGIN/END

Rodar todos para confirmar verde:

```bash
./gradlew :shared:jvmTest
```

---

## Cronograma proposto

### Sessão 1 (10h) — Estatística + início Calendário
- Passo 1.1 a 1.4: StatisticsVectorsTest → 27 verdes
- Passo 2.1 a 2.2: esquema de datas, serial

### Sessão 2 (8h) — Calendário completo
- Passo 2.3: operações de calendário
- Passo 2.4: CalendarVectorsTest → 15 verdes

### Sessão 3 (12h) — Fluxo de Caixa
- Passo 3.1 a 3.4: NPV, IRR, CashflowVectorsTest → 17 verdes

### Validação final (2h)
```bash
./gradlew :shared:jvmTest
# Esperado: 111/111 testes verdes ✅
```

---

## Checklist de entrega

- [ ] Todos 111 testes verdes (CI/CD green)
- [ ] Nenhum `TODO`, `FIXME`, `NotImplementedError` na engine
- [ ] Commits pequenos (1 feature por commit) no padrão Conventional Commits
- [ ] PROMPT_MESTRE.md atualizado com progresso
- [ ] `.claude/skills/hp12c-simulator/` consultado e refletido no código

---

## Referências rápidas

| Referência | Localização |
|---|---|
| Fórmulas canônicas | `.claude/skills/hp12c-simulator/formulas/` |
| Ambiguidades conhecidas | Seções `§ "Ambiguidades"` de cada .md |
| Vetores de teste | `.claude/skills/hp12c-simulator/test-vectors/*.json` |
| Comportamento da pilha | `.claude/skills/hp12c-simulator/referencias/stack-behavior.md` |
| Código de erros HP | `.claude/skills/hp12c-simulator/referencias/error-codes.md` |
| Skill primária | Invocar: `/skill hp12c-simulator` |

---

## Notas finais

1. **Não há atalhos aqui.** Cada fórmula, cada vector no JSON, cada padrão de saída dupla foi cuidadosamente documentado na skill. Leia a skill antes de codar.

2. **A persistência de dados** (memória contínua) é Fase 2 — não entra neste escopo.

3. **UI e empacotamento** também são Fase 4 — o foco é só engine.

4. **Quando você travar**, releia a fórmula na skill, verifique o teste, e trabalhe de trás pra frente (teste → fórmula → código).

Sucesso! 🚀
