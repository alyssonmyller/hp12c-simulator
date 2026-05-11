# Resumo Executivo — Engine HP 12C

## 🎯 Missão

Completar a implementação da engine para passar 111 testes (18 estão verdes, 93 faltam).

## 📊 Status Atual

```
┌─────────────────────────────────────────┐
│  Fase 1: MVP Engine                     │
├─────────────────────────────────────────┤
│ TVM (18 testes)           🟢 COMPLETO   │
│ Transcendentais (34)      🟢 COMPLETO   │
│ Aritmética (54)           🟢 COMPLETO   │
├─────────────────────────────────────────┤
│ Estatística (27)          🔴 FALTA      │
│ Calendário (15)           🔴 FALTA      │
│ Fluxo de Caixa (17)       🔴 FALTA      │
├─────────────────────────────────────────┤
│ TOTAL: 18/111 verde      ~16% concluído │
└─────────────────────────────────────────┘
```

## 🔧 O que precisa ser feito

| Tarefa | Testes | Tempo | Prioridade |
|--------|--------|-------|-----------|
| Implementar Σ+, Σ-, x̄, s, ŷ,r, x̂,r | 27 | 10h | 🔴 1ª |
| Implementar DATE, ΔDYS, D.MY/M.DY | 15 | 8h | 🔴 2ª |
| Implementar NPV, IRR (Newton-Raphson) | 17 | 12h | 🔴 3ª |
| **Subtotal** | **59** | **30h** | |

## 💡 Como fazer

1. **Leia sempre a skill primeiro**
   ```bash
   /skill hp12c-simulator
   ```

2. **Siga o padrão de cada sessão**
   - Estude a fórmula na skill
   - Abra o JSON de teste correspondente
   - Implemente handler no DefaultEngine
   - Rode suite de testes
   - Commit pequeno (1 feature)

3. **Use o plano detalhado**
   - `PLANO_IMPLEMENTACAO_ENGINE.md` — arquitetura e fórmulas
   - `ESTRUTURA_SESSOES.md` — passo-a-passo de cada parte

## 📁 Arquivos principais

```
hp12c-simulator/
├── RESUMO_EXECUTIVO.md               ← você está aqui
├── PLANO_IMPLEMENTACAO_ENGINE.md     ← leia isso (arquitetura)
├── ESTRUTURA_SESSOES.md              ← leia isso (passo-a-passo)
├── .claude/skills/hp12c-simulator/
│   ├── SKILL.md                      ← consulte sempre!
│   ├── formulas/
│   │   ├── estatistica.md
│   │   ├── calendario.md
│   │   └── cashflow.md
│   └── test-vectors/
│       ├── estatistica-vectors.json
│       ├── calendario-vectors.json
│       └── cashflow-vectors.json
└── shared/src/
    ├── commonMain/kotlin/com/arcom/hp12c/engine/
    │   ├── DefaultEngine.kt          ← edite aqui
    │   └── state/FinancialRegisters.kt
    └── commonTest/kotlin/com/arcom/hp12c/engine/
        ├── StatisticsVectorsTest.kt
        ├── CalendarVectorsTest.kt
        └── CashflowVectorsTest.kt
```

## 🚀 Comece aqui

```bash
# 1. Leia a skill (obrigatório)
/skill hp12c-simulator

# 2. Abra o plano detalhado
open PLANO_IMPLEMENTACAO_ENGINE.md

# 3. Siga a estrutura
# Sessão 1: Estatística (27 testes)
# Sessão 2: Calendário (15 testes)
# Sessão 3: Fluxo de Caixa (17 testes)

# 4. Valide no final
./gradlew :shared:jvmTest
# Esperado: 111/111 verde ✅
```

## ✅ Checklist final

- [ ] Todos 111 testes verdes em CI
- [ ] Sem `TODO`, `FIXME`, `NotImplementedError`
- [ ] Commits pequenos (Conventional Commits)
- [ ] PROMPT_MESTRE.md atualizado
- [ ] Skill consultada em cada sessão

## 🎓 Princípios não-negociáveis

1. **Exatidão numérica** — BigDecimal 10 dígitos HALF_EVEN, nunca Double
2. **Fidelidade ao aparelho** — toda ambiguidade documentada na skill
3. **Testes dirigem implementação** — TDD (teste antes de codar)
4. **Skill é verdade única** — se há conflito entre código e skill, skill vence

## 📞 Referência rápida

| Quando... | Leia... |
|-----------|---------|
| ...implementar estatística | `.claude/skills/hp12c-simulator/formulas/estatistica.md` |
| ...dúvida sobre datas | `.claude/skills/hp12c-simulator/formulas/calendario.md` |
| ...implementar IRR | `.claude/skills/hp12c-simulator/formulas/cashflow.md` |
| ...erro estranho | `.claude/skills/hp12c-simulator/referencias/error-codes.md` |
| ...pilar RPN | `.claude/skills/hp12c-simulator/referencias/stack-behavior.md` |

---

## 🎯 Expectativa

Ao final de **3 sessões de 10-12h cada**, você terá:
- ✅ 111/111 testes verdes
- ✅ Engine 100% MVP pronta
- ✅ Código bem-organizado e testado
- ✅ Próximo passo: UI (Android/iOS)

**Tempo total estimado:** 30-40 horas de desenvolvimento focado

Vamos lá! 🚀
