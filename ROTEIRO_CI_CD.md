# Roteiro CI/CD — Validação de Progresso

Este documento define como validar progresso a cada commit.

---

## 🟢 Estado-alvo

```bash
./gradlew :shared:jvmTest
```

**Output esperado ao final:**
```
BUILD SUCCESSFUL

===================== Test Results =====================
:shared:jvmTest
  ArithmeticTests             : PASSED (18 tests)
  TvmVectorsTest              : PASSED (18 tests)
  TranscendentalsVectorsTest  : PASSED (34 tests)
  StatisticsVectorsTest       : PASSED (27 tests) ← será verde na Sessão 1
  CalendarVectorsTest         : PASSED (15 tests) ← será verde na Sessão 2
  CashflowVectorsTest         : PASSED (17 tests) ← será verde na Sessão 3
  (others)                    : PASSED (12 tests)
───────────────────────────────────────────────────
  TOTAL                       : 111/111 ✅
═══════════════════════════════════════════════════
```

---

## 📋 Checkpoints por Sessão

### Sessão 1 — Estatística

Antes:
```bash
./gradlew :shared:jvmTest --tests "*StatisticsVectorsTest*"
# RESULTADO: 27 FAILED (vermelho esperado no início)
```

Depois:
```bash
./gradlew :shared:jvmTest --tests "*StatisticsVectorsTest*"
# RESULTADO: 27 PASSED ✅
```

**Validação completa (confirmar que não quebrou nada):**
```bash
./gradlew :shared:jvmTest
# Esperado: 18 + 34 + 54 + 27 = 133 ou mais
# Sem degradação em TVM, Transcendentais, Aritmética
```

---

### Sessão 2 — Calendário

Antes:
```bash
./gradlew :shared:jvmTest --tests "*CalendarVectorsTest*"
# RESULTADO: 15 FAILED
```

Depois:
```bash
./gradlew :shared:jvmTest --tests "*CalendarVectorsTest*"
# RESULTADO: 15 PASSED ✅
```

**Validação completa:**
```bash
./gradlew :shared:jvmTest
# Esperado: todos ainda verdes, +15 novos
```

---

### Sessão 3 — Fluxo de Caixa

Antes:
```bash
./gradlew :shared:jvmTest --tests "*CashflowVectorsTest*"
# RESULTADO: 17 FAILED
```

Depois:
```bash
./gradlew :shared:jvmTest --tests "*CashflowVectorsTest*"
# RESULTADO: 17 PASSED ✅
```

**Validação final:**
```bash
./gradlew :shared:jvmTest
# RESULTADO: 111/111 PASSED ✅✅✅
```

---

## 🔍 Debugging de testes vermelhos

Se um teste falha com output diferente do esperado:

### 1. Rodar teste isolado com output detalhado

```bash
./gradlew :shared:jvmTest --tests "*StatisticsVectorsTest.stat_001*" -i
```

(Substitua `stat_001` pelo ID do teste falhando)

### 2. Coletar informações

O output mostrará:
```
FAILED - Statistics001Test
Expected: "12.34"
Got:      "12.35"
Vector:   stat-001 (moretti, Cap 8, Ex 15)
```

### 3. Verificar o JSON

```bash
grep -A 20 '"id": "stat-001"' \
  .claude/skills/hp12c-simulator/test-vectors/estatistica-vectors.json
```

Copiar exatamente os inputs e comparar com a HP física ou calculadora online.

### 4. Verificar a fórmula

```bash
# Abrir:
.claude/skills/hp12c-simulator/formulas/estatistica.md

# Procurar pela seção relevante (ex: "Desvio-padrão amostral")
# Validar que o código segue a fórmula exatamente
```

### 5. Debugar no código

```kotlin
// Em StatisticsVectorsTest.kt ou DefaultEngine.kt:
println("DEBUG: n=${n}, sumX=${sumX}, computed=${mean}")

// Rodar novamente:
./gradlew :shared:jvmTest --tests "*stat_001*"
```

---

## 📊 Métricas de progresso

Use este comando para acompanhar progresso ao longo das sessões:

```bash
echo "=== Progresso ==="
echo "TVM: $(./gradlew :shared:jvmTest --tests "*TvmVectorsTest*" -q 2>&1 | grep -c PASSED)/18"
echo "Transcendentais: $(./gradlew :shared:jvmTest --tests "*TranscendentalsVectorsTest*" -q 2>&1 | grep -c PASSED)/34"
echo "Aritmética: $(./gradlew :shared:jvmTest --tests "*ReducerTest*" -q 2>&1 | grep -c PASSED)/54"
echo "Estatística: $(./gradlew :shared:jvmTest --tests "*StatisticsVectorsTest*" -q 2>&1 | grep -c PASSED)/27"
echo "Calendário: $(./gradlew :shared:jvmTest --tests "*CalendarVectorsTest*" -q 2>&1 | grep -c PASSED)/15"
echo "Fluxo de Caixa: $(./gradlew :shared:jvmTest --tests "*CashflowVectorsTest*" -q 2>&1 | grep -c PASSED)/17"
```

---

## 🚨 Problemas comuns

### "Workspace still starting" ao rodar gradlew

Aguarde 5-10s e tente novamente. A primeira execução é mais lenta.

### "FAILED — Hp12cDecimal.of()"

Verificar se está usando `Hp12cDecimal.of(String)` ou `Hp12cDecimal.of(Double)`. Sempre use String:
```kotlin
// ❌ ERRADO
Hp12cDecimal.of(1.23)

// ✅ CERTO
Hp12cDecimal.of("1.23")
```

### "Error 2 not thrown when expected"

Validar que:
1. Condição de erro está sendo checada no código
2. `Hp12cError.Error2` é o código correto (não Error 1, 3, etc.)
3. Skill documenta exatamente quando Error 2 dispara

### Teste passa localmente mas falha no CI

Possíveis causas:
1. Diferença de timezone → usar UTC em testes
2. Diferença de locale → usar sempre HALF_EVEN
3. Dependência de ordem → rode testes 2x, verificar idempotência

---

## ✅ Antes de fazer commit

```bash
# 1. Rodar todos os testes
./gradlew :shared:jvmTest

# 2. Verificar que não quebrou nada
# (nenhuma regressão em testes que estavam verdes)

# 3. Formato de código (opcional mas recomendado)
./gradlew :shared:detekt

# 4. Mensagem de commit
git commit -m "feat(statistics): implement Σ+ accumulation

- Add Hp12cDecimal support for R1..R6 accumulators
- Implement Event.Statistics.Accumulate handler
- Add helper methods in FinancialRegisters

Fixes test suite: StatisticsVectorsTest (27/27 green)"
```

---

## 🎯 CI/CD no GitHub

O repositório já tem `.github/workflows/ci.yml` que roda:

```bash
./gradlew :shared:jvmTest
```

A cada push/PR. Status aparecer:
- ✅ "All checks passed" → branch está saudável
- ❌ "Build failed" → reparar antes de mergear

Para visualizar:
```
GitHub → Project → Actions
```

---

## 📈 Exemplo de progresso esperado

**Dia 1-2: Sessão 1 (Estatística)**
```
Before:  18/111 (16%)
After:   45/111 (41%)  ← +27
```

**Dia 3-4: Sessão 2 (Calendário)**
```
Before:  45/111 (41%)
After:   60/111 (54%)  ← +15
```

**Dia 5-6: Sessão 3 (Fluxo de Caixa)**
```
Before:  60/111 (54%)
After:   111/111 (100%) ✅
```

---

## 🔗 Links rápidos

| Link | Descrição |
|------|-----------|
| `./gradlew :shared:jvmTest` | Rodar todos os testes |
| `.gradle/7.x/...` | Cache local (pode limpar com `./gradlew clean` se travar) |
| `.claude/skills/hp12c-simulator/` | Consultar fórmulas |
| `shared/src/commonTest/kotlin/...` | Testes (leia para entender padrão) |

---

## 🚀 Próximo passo após todos verdes

```bash
# 1. Atualizar PROMPT_MESTRE.md
# Mudar "Fase 0 concluída" para "Fase 1 concluída"

# 2. Criar branch para Fase 2
git checkout -b feature/phase-2-functions

# 3. Próximas functions: amortização, depreciação, juros simples
# (fora do escopo deste documento)
```

---

Boa sorte! 🚀
