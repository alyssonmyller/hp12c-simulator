# Estrutura de Sessões — Implementação Engine

Este arquivo orienta cada nova sessão de trabalho.

---

## 🎯 Objetivo Global

**Fazer 111/111 testes passarem** (verde em `:shared:jvmTest`)

Status atual: **18/111** (18 TVM verdes, 93 faltam)

---

## 📋 Checklist de Preparação (início de cada sessão)

Antes de codar, execute:

```bash
# 1. Ler a skill (sempre!)
/skill hp12c-simulator

# 2. Confirmar estado dos testes
cd /sessions/compassionate-practical-franklin/mnt/hp12c-simulator
./gradlew :shared:jvmTest --no-build-cache 2>&1 | grep -E "(PASSED|FAILED|ERROR)"

# 3. Abrir o arquivo de testes relevante (ex: StatisticsVectorsTest.kt)
# e o JSON correspondente (ex: estatistica-vectors.json)
```

---

## 🔴 Sessão 1 — Estatística (27 testes)

### Arquivos a modificar

```
shared/src/commonMain/kotlin/com/arcom/hp12c/engine/
├── DefaultEngine.kt              ← reduceStatistics() e handlers
├── state/
│   ├── FinancialRegisters.kt     ← adicionar getters para R1..R6 (acumuladores)
│   └── CalculatorState.kt        ← adicionar statisticalState? (ou reuse financialRegisters)
└── math/
    └── (já tem Hp12cDecimal para operações BCD)

shared/src/commonTest/kotlin/com/arcom/hp12c/engine/
├── StatisticsVectorsTest.kt      ← já existe, será verde após implementação
└── testing/
    └── StatisticsVectorJson.kt   ← já parseia JSON
```

### Passo-a-passo

#### 1.1 — Entender estrutura de acumuladores

Ler `.claude/skills/hp12c-simulator/formulas/estatistica.md`:
- Seção: "Registradores compartilhados"
- Seção: "Acumulação em Σ+/Σ-"
- Nota: R1..R6 **não têm campo separado** — vivem em `financialRegisters.registers[1..6]`

#### 1.2 — Adicionar getters em `FinancialRegisters`

```kotlin
// Em shared/src/commonMain/kotlin/com/arcom/hp12c/engine/state/FinancialRegisters.kt

fun getStatN(): Hp12cDecimal = registers.getOrNull(6) ?: Hp12cDecimal.ZERO
fun getSumX(): Hp12cDecimal = registers.getOrNull(1) ?: Hp12cDecimal.ZERO
fun getSumX2(): Hp12cDecimal = registers.getOrNull(2) ?: Hp12cDecimal.ZERO
fun getSumY(): Hp12cDecimal = registers.getOrNull(3) ?: Hp12cDecimal.ZERO
fun getSumY2(): Hp12cDecimal = registers.getOrNull(4) ?: Hp12cDecimal.ZERO
fun getSumXY(): Hp12cDecimal = registers.getOrNull(5) ?: Hp12cDecimal.ZERO
```

#### 1.3 — Implementar `reduceStatistics` em DefaultEngine

```kotlin
private fun reduceStatistics(state: CalculatorState, event: Event.Statistics): CalculatorState {
    return when (event) {
        is Event.Statistics.Accumulate     -> reduceAccumulate(state, event)
        is Event.Statistics.Deaccumulate   -> reduceDeaccumulate(state, event)
        is Event.Statistics.Mean           -> reduceMean(state)
        is Event.Statistics.StdDev         -> reduceStdDev(state)
        is Event.Statistics.WeightedMean   → reduceWeightedMean(state)
        is Event.Statistics.YHatR          -> reduceYHatR(state, event)
        is Event.Statistics.XHatR          -> reduceXHatR(state, event)
        is Event.Statistics.Clear          -> reduceClearStatistics(state)
    }
}

private fun reduceAccumulate(state: CalculatorState, event: Event.Statistics.Accumulate): CalculatorState {
    if (state.pendingError != null) return state
    
    val x = state.stack.x
    val y = state.stack.y
    val fr = state.financialRegisters
    
    val n = fr.getStatN() + Hp12cDecimal.ONE
    val sumX = fr.getSumX() + x
    val sumX2 = fr.getSumX2() + (x * x)
    
    val newRegisters = fr.registers.copyOf().apply {
        set(1, sumX)      // R1 ← Σx
        set(2, sumX2)     // R2 ← Σx²
        set(6, n)         // R6 ← n
        
        if (event.isBivariate) {
            set(3, fr.getSumY() + y)      // R3 ← Σy
            set(4, fr.getSumY2() + (y * y)) // R4 ← Σy²
            set(5, fr.getSumXY() + (x * y)) // R5 ← Σxy
        }
    }
    
    return state.copy(
        financialRegisters = fr.copy(registers = newRegisters),
        stack = state.stack.copy(x = n)  // X ← nova contagem
    )
}
```

#### 1.4 — Implementar cálculos derivados (média, desvio, regressão)

Cada um segue padrão semelhante — lê acumuladores, aplica fórmula, escreve X (e Y se dupla):

```kotlin
private fun reduceMean(state: CalculatorState): CalculatorState {
    val n = state.financialRegisters.getStatN()
    if (n == Hp12cDecimal.ZERO) {
        return state.copy(pendingError = Hp12cError.Error2)
    }
    val mean = state.financialRegisters.getSumX() / n
    return state.copy(
        stack = state.stack.copy(x = mean, y = mean)
    )
}
```

#### 1.5 — Rodar testes

```bash
./gradlew :shared:jvmTest --tests "*StatisticsVectorsTest*"
```

Esperado: 27/27 verdes.

### Commits recomendados

```
feat(statistics): implement Σ+ Σ- accumulation with R1..R6
feat(statistics): implement mean, stddev, weighted mean
feat(statistics): implement linear regression (ŷ,r, x̂,r)
test(statistics): StatisticsVectorsTest → 27 green
```

---

## 🔴 Sessão 2 — Calendário (15 testes)

### Arquivos a modificar

```
shared/src/commonMain/kotlin/com/arcom/hp12c/engine/
├── DefaultEngine.kt              ← reduceCalendar()
├── state/
│   └── CalculatorState.kt        ← já tem dateFormat: DateFormat
└── calendar/
    ├── DateUtils.kt              ← novo: encode/decode, serial conversions
    └── DayOfWeek.kt              ← novo: cálculo de dia da semana
```

### Passo-a-passo

#### 2.1 — Ler fórmulas na skill

`.claude/skills/hp12c-simulator/formulas/calendario.md`:
- Seção: "Codificação de datas"
- Seção: "Fórmula de serial"
- Seção: "Cálculo de dia da semana"
- Limite válido: 15 out 1582 ≤ data ≤ 25 nov 4046

#### 2.2 — Criar `DateUtils.kt`

```kotlin
// shared/src/commonMain/kotlin/com/arcom/hp12c/engine/calendar/DateUtils.kt

data class Date(val month: Int, val day: Int, val year: Int)

fun decodeDateMdy(encoded: Hp12cDecimal): Date {
    val str = encoded.toBigDecimal().toPlainString()
        .replace(".", "")
        .padStart(8, '0')
    
    val mm = str.substring(0, 2).toInt()
    val dd = str.substring(2, 4).toInt()
    val yyyy = str.substring(4, 8).toInt()
    
    return Date(mm, dd, yyyy)
}

fun encodeDateMdy(date: Date): Hp12cDecimal {
    val str = "%02d%02d%04d".format(date.month, date.day, date.year)
    return Hp12cDecimal.of("${str.substring(0, 2)}.${str.substring(2)}")
}

fun dateToSerial(date: Date): Int {
    // Validar: 15 out 1582 ≤ data ≤ 25 nov 4046
    if (date.year < 1582 || 
        (date.year == 1582 && (date.month < 10 || (date.month == 10 && date.day < 15)))) {
        throw Hp12cException(Hp12cError.Error8)
    }
    
    if (date.year > 4046 || 
        (date.year == 4046 && (date.month > 11 || (date.month == 11 && date.day > 25)))) {
        throw Hp12cException(Hp12cError.Error8)
    }
    
    val yearAdj = if (date.month <= 2) date.year - 1 else date.year
    val z = yearAdj
    val x = (0.4 * date.month + 2.3).toInt()
    
    return 365 * date.year + 31 * (date.month - 1) + date.day + 
           (z / 4) - x
}

fun serialToDate(serial: Int): Date {
    // Inverter dateToSerial (mais complexo, usar tabelas/iteração)
    // ...
}

fun computeDayOfWeek(serial: Int): Int {
    // 0 = segunda, 1 = terça, ..., 6 = domingo
    // HP: 1 = segunda, ..., 7 = domingo
    val dow = ((serial + 5) % 7)
    return if (dow == 0) 7 else dow
}
```

#### 2.3 — Implementar `reduceCalendar`

```kotlin
private fun reduceCalendar(state: CalculatorState, event: Event.Calendar): CalculatorState {
    return when (event) {
        is Event.Calendar.ToggleDateFormat -> state.copy(
            dateFormat = if (state.dateFormat == DateFormat.MDY) 
                DateFormat.DMY else DateFormat.MDY
        )
        is Event.Calendar.DateDifference -> {
            val date1 = decodeDateMdy(state.stack.y)
            val date2 = decodeDateMdy(state.stack.x)
            val serial1 = dateToSerial(date1)
            val serial2 = dateToSerial(date2)
            
            val exact = Hp12cDecimal.of(serial2 - serial1)
            val commercial = Hp12cDecimal.of(serial2 - serial1)  // simplificado
            
            state.copy(
                stack = state.stack.copy(x = exact, y = commercial)
            )
        }
        // ... outros eventos
    }
}
```

#### 2.4 — Rodar testes

```bash
./gradlew :shared:jvmTest --tests "*CalendarVectorsTest*"
```

Esperado: 15/15 verdes.

### Commits recomendados

```
feat(calendar): add DateUtils encode/decode and serial conversion
feat(calendar): implement DATE, ΔDYS operations
feat(calendar): add day-of-week calculation
test(calendar): CalendarVectorsTest → 15 green
```

---

## 🔴 Sessão 3 — Fluxo de Caixa (17 testes)

### Arquivos a modificar

```
shared/src/commonMain/kotlin/com/arcom/hp12c/engine/
├── DefaultEngine.kt              ← reduceCashflow()
├── state/
│   └── FinancialRegisters.kt     ← cashflows[0..20], cashflowCounts[0..20]
├── financial/
│   ├── CashflowState.kt          ← novo
│   └── IrrSolver.kt              ← novo: Newton-Raphson
```

### Passo-a-passo

#### 3.1 — Ler fórmulas na skill

`.claude/skills/hp12c-simulator/formulas/cashflow.md`:
- Seção: "Armazenamento de fluxos"
- Seção: "Fórmula NPV"
- Seção: "Algoritmo Newton-Raphson"
- Nota: Erro 6 vs Erro 7 — implementação atual simplificada

#### 3.2 — Adicionar estado em FinancialRegisters

```kotlin
// Em FinancialRegisters
var cashflows: Array<Hp12cDecimal> = Array(21) { Hp12cDecimal.ZERO }
var cashflowCounts: Array<Int> = Array(21) { 1 }
```

#### 3.3 — Implementar `reduceCashflow`

```kotlin
private fun reduceCashflow(state: CalculatorState, event: Event.Cashflow): CalculatorState {
    return when (event) {
        is Event.Cashflow.SetInitialFlow -> {
            val newFr = state.financialRegisters.copy(
                cashflows = state.financialRegisters.cashflows.copyOf().apply {
                    set(0, state.stack.x)
                }
            )
            state.copy(financialRegisters = newFr, stack = state.stack.rollDown())
        }
        is Event.Cashflow.Npv -> {
            val npv = computeNpv(state.financialRegisters)
            state.copy(stack = state.stack.copy(x = npv))
        }
        is Event.Cashflow.Irr -> {
            val irr = computeIrr(state.financialRegisters)
            state.copy(
                financialRegisters = state.financialRegisters.copy(i = irr),
                stack = state.stack.copy(x = irr)
            )
        }
    }
}
```

#### 3.4 — Implementar NPV e IRR

```kotlin
private fun computeNpv(fr: FinancialRegisters): Hp12cDecimal {
    var npv = Hp12cDecimal.ZERO
    var time = 0
    val i = fr.i / HUNDRED  // converter de percentual
    val discountFactor = Hp12cDecimal.ONE + i
    
    for (index in 0..20) {
        val cf = fr.cashflows[index]
        val count = fr.cashflowCounts[index]
        
        for (j in 0 until count) {
            val discounted = cf / discountFactor.pow(time)
            npv += discounted
            time++
        }
    }
    
    return npv
}

private fun computeIrr(fr: FinancialRegisters): Hp12cDecimal {
    val guesses = listOf(
        Hp12cDecimal.ZERO,
        Hp12cDecimal.of(10),
        Hp12cDecimal.of(-50),
        Hp12cDecimal.of(100)
    )
    
    for (guess in guesses) {
        val result = solveViaNewtonRaphson(fr, guess)
        if (result != null) return result
    }
    
    // Nenhum convergiou
    return error(Hp12cError.Error3)
}

private fun solveViaNewtonRaphson(
    fr: FinancialRegisters,
    guess: Hp12cDecimal,
    maxIter: Int = 100,
    tolerance: BigDecimal = BigDecimal("1E-6")
): Hp12cDecimal? {
    var x = guess
    val h = Hp12cDecimal.of("1E-6")
    
    for (iter in 0 until maxIter) {
        val frWithX = fr.copy(i = x)
        val f = computeNpv(frWithX)
        
        val frWithXPlusH = fr.copy(i = x + h)
        val fPlusH = computeNpv(frWithXPlusH)
        
        val frWithXMinusH = fr.copy(i = x - h)
        val fMinusH = computeNpv(frWithXMinusH)
        
        val derivative = (fPlusH - fMinusH) / (2 * h)
        
        if (derivative == Hp12cDecimal.ZERO) return null
        
        val xNext = x - (f / derivative)
        
        if ((xNext - x).abs() < tolerance) {
            return xNext
        }
        
        x = xNext
    }
    
    return null
}
```

#### 3.5 — Rodar testes

```bash
./gradlew :shared:jvmTest --tests "*CashflowVectorsTest*"
```

Esperado: 17/17 verdes.

### Commits recomendados

```
feat(cashflow): add CFo, CFj, Nj storage
feat(cashflow): implement NPV calculation
feat(cashflow): implement IRR via Newton-Raphson
test(cashflow): CashflowVectorsTest → 17 green
```

---

## ✅ Validação Final

Após as 3 sessões:

```bash
./gradlew :shared:jvmTest
```

Esperado:
```
✓ TvmVectorsTest: 18/18
✓ StatisticsVectorsTest: 27/27
✓ CalendarVectorsTest: 15/15
✓ CashflowVectorsTest: 17/17
✓ (outros): 34 (transcendentais, aritmética)
─────────────────────────────────
✓ TOTAL: 111/111 GREEN ✅
```

---

## 🚀 Próximos passos (Fase 2 e além)

Uma vez que todos 111 testes estão verdes:

1. **Fase 2** — Funções adicionais (amortização, depreciação, juros simples)
2. **Fase 3** — Programação (modo PRGM, GTO, GSB, condicionais)
3. **Fase 4** — UI (Android Compose + iOS SwiftUI)

---

## ⚠️ Troubleshooting

### Teste falha, output diferente do esperado

1. Copiar o input do JSON
2. Rodar manualmente na HP 12C física (ou emulador online)
3. Comparar resultado com test vector
4. Ler fórmula na skill
5. Debugar no código

### Teste falha com NPE ou exception

1. Verificar se estado inicial está correto (CalculatorState.InitialState)
2. Verificar se os getters em FinancialRegisters retornam defaults seguros
3. Usar `.copy()` em vez de mutação

### Teste falha com "Error N" inesperado

1. Ler `.claude/skills/hp12c-simulator/referencias/error-codes.md`
2. Verificar condição de erro no JSON
3. Confirmar se a condição está sendo testada no código

---

## 📞 Suporte

Qualquer dúvida sobre fórmulas, comportamento esperado ou edge cases: **sempre consulte a skill primeiro**.

```bash
/skill hp12c-simulator
```

Bom trabalho! 🎯
