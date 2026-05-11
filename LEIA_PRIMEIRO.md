# 📖 LEIA PRIMEIRO — Guia de Navegação

Bem-vindo! Você está aqui porque quer **completar a engine HP 12C para passar 111 testes**.

Este arquivo é seu índice. Comece por aqui.

---

## 🎯 Seu objetivo

**Estado atual:** 18/111 testes verdes (TVM completo)  
**Objetivo:** 111/111 testes verdes (todas as funções implementadas)  
**Tempo estimado:** 30-40 horas de trabalho focado  

---

## 📚 Documentos (nesta ordem)

### 1. **RESUMO_EXECUTIVO.md** (5 min)
   - O quê: 4 funções principais faltam (Estatística, Calendário, Fluxo de Caixa)
   - Quanto: ~59 testes faltam = ~30 horas de trabalho
   - Como começar: checklist de "comece aqui"
   - **Leia isto PRIMEIRO**

### 2. **PLANO_IMPLEMENTACAO_ENGINE.md** (30 min)
   - Explicação detalhada de cada função
   - Fórmulas matemáticas canônicas
   - Pseudocódigo de cada handler
   - Padrão arquitetural (reducer, eventos, estado)
   - **Leia isto antes de codar**

### 3. **ESTRUTURA_SESSOES.md** (20 min + referência)
   - Passo-a-passo **exato** de cada sessão
   - Lista de arquivos a modificar
   - Código pronto pra copiar/colar (com gaps para preencher)
   - Commits recomendados
   - **Use isto como guia durante a implementação**

### 4. **ROTEIRO_CI_CD.md** (10 min + referência)
   - Como validar progresso com `./gradlew :shared:jvmTest`
   - Checkpoints esperados a cada sessão
   - Como debugar testes falhando
   - Métricas de progresso
   - **Consulte isto a cada commit**

### 5. **PROMPT_MESTRE.md** (leitura obrigatória antes de codar)
   - Roadmap oficial do projeto (Fases 0-4)
   - Decisões arquiteturais tomadas
   - Invariantes não-negociáveis
   - **Este é o documento-fonte; tudo mais é derivado dele**

---

## 🛠️ Skill de apoio (obrigatória)

Antes de codar qualquer coisa, invoque:

```bash
/skill hp12c-simulator
```

Esta skill contém:
- **Fórmulas canônicas** de estatística, calendário, fluxo de caixa
- **Vetores de teste** em JSON (exemplos exatos do manual)
- **Regras de comportamento** (quando Error 2 dispara, como pilha funciona, etc.)
- **Ambiguidades documentadas** (edge cases com soluções claras)

**Regra de ouro:** Se a skill diz X e seu código diz Y, a skill vence. Sempre.

---

## 🚀 Fluxo recomendado

```
┌─────────────────────────────────────────────────┐
│ Dia 0: Preparação (2h)                          │
│ • Ler RESUMO_EXECUTIVO.md                       │
│ • Invocar /skill hp12c-simulator                │
│ • Ler PLANO_IMPLEMENTACAO_ENGINE.md (primeiras) │
│ • Entender estrutura: shared/src/*Test.kt       │
└─────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────┐
│ Sessão 1: Estatística (10h, 27 testes)          │
│ • Ler ESTRUTURA_SESSOES.md — Sessão 1           │
│ • Implementar handlers no DefaultEngine         │
│ • Rodar ./gradlew :shared:jvmTest               │
│ • Resultado: StatisticsVectorsTest 27/27 ✅    │
└─────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────┐
│ Sessão 2: Calendário (8h, 15 testes)            │
│ • Ler ESTRUTURA_SESSOES.md — Sessão 2           │
│ • Implementar DateUtils.kt + handlers           │
│ • Rodar ./gradlew :shared:jvmTest               │
│ • Resultado: CalendarVectorsTest 15/15 ✅      │
└─────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────┐
│ Sessão 3: Fluxo de Caixa (12h, 17 testes)       │
│ • Ler ESTRUTURA_SESSOES.md — Sessão 3           │
│ • Implementar NPV, IRR (Newton-Raphson)         │
│ • Rodar ./gradlew :shared:jvmTest               │
│ • Resultado: CashflowVectorsTest 17/17 ✅      │
└─────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────┐
│ Validação Final (2h)                            │
│ ./gradlew :shared:jvmTest                       │
│ RESULTADO: 111/111 PASSED ✅✅✅               │
└─────────────────────────────────────────────────┘
```

---

## 📁 Estrutura de arquivos importantes

```
hp12c-simulator/
├── 📄 LEIA_PRIMEIRO.md                    ← você está aqui
├── 📄 RESUMO_EXECUTIVO.md                 ← comece aqui
├── 📄 PLANO_IMPLEMENTACAO_ENGINE.md       ← leia antes de codar
├── 📄 ESTRUTURA_SESSOES.md                ← guia passo-a-passo
├── 📄 ROTEIRO_CI_CD.md                    ← valide progresso
│
├── 📄 PROMPT_MESTRE.md                    ← documento-fonte
├── 🗂️ .claude/skills/hp12c-simulator/
│   ├── 📄 SKILL.md                        ← invoque com /skill
│   ├── 🗂️ formulas/
│   │   ├── tvm.md                         ✅ completo
│   │   ├── transcendentais.md             ✅ completo
│   │   ├── estatistica.md                 ← Sessão 1
│   │   ├── calendario.md                  ← Sessão 2
│   │   └── cashflow.md                    ← Sessão 3
│   └── 🗂️ test-vectors/
│       ├── tvm-vectors.json               ✅ 18 testes verdes
│       ├── transcendentais-vectors.json   ✅ 34 testes verdes
│       ├── estatistica-vectors.json       ← 27 testes (faltam)
│       ├── calendario-vectors.json        ← 15 testes (faltam)
│       └── cashflow-vectors.json          ← 17 testes (faltam)
│
├── 🗂️ shared/src/commonMain/
│   └── 🗂️ com/arcom/hp12c/engine/
│       ├── 📄 DefaultEngine.kt            ← você EDITA aqui
│       ├── 🗂️ state/
│       │   ├── FinancialRegisters.kt      ← dados financeiros
│       │   └── CalculatorState.kt         ← estado global
│       └── 🗂️ math/
│           └── Hp12cDecimal.kt            ← aritmética BCD
│
└── 🗂️ shared/src/commonTest/
    └── 🗂️ com/arcom/hp12c/engine/
        ├── 📄 StatisticsVectorsTest.kt    ← roda 27 testes
        ├── 📄 CalendarVectorsTest.kt      ← roda 15 testes
        └── 📄 CashflowVectorsTest.kt      ← roda 17 testes
```

---

## ⚡ Comece AGORA em 3 passos

### Passo 1: Entenda o status (5 min)
Abra `RESUMO_EXECUTIVO.md` e leia até "🚀 Comece aqui".

### Passo 2: Invoque a skill (2 min)
```bash
/skill hp12c-simulator
```
Leia a seção "Propósito" e "Quando invocar".

### Passo 3: Mapeie a Sessão 1 (15 min)
Abra `ESTRUTURA_SESSOES.md` e ler "🔴 Sessão 1 — Estatística".

---

## ✅ Checklist de "estou pronto"

Antes de abrir um editor de código, confirme:

- [ ] Li RESUMO_EXECUTIVO.md
- [ ] Invoquei `/skill hp12c-simulator` e entendi a estrutura
- [ ] Li PLANO_IMPLEMENTACAO_ENGINE.md (primeiras 100 linhas mínimo)
- [ ] Consegui rodar `./gradlew :shared:jvmTest` e ver os testes
- [ ] Entendi que arquivos vou modificar (DefaultEngine.kt, FinancialRegisters.kt, etc.)

---

## 🆘 Quando travar

1. **Dúvida sobre fórmula?**
   → Consulte `.claude/skills/hp12c-simulator/formulas/estatistica.md` (ou a relevante)

2. **Teste falhando com output diferente?**
   → Abra ROTEIRO_CI_CD.md § "Debugging de testes vermelhos"

3. **Não entendo padrão arquitetural?**
   → Releia PLANO_IMPLEMENTACAO_ENGINE.md § "Arquitetura" (primeiras 50 linhas)

4. **Onde exatamente editar?**
   → Consulte ESTRUTURA_SESSOES.md § "Arquivos a modificar"

---

## 📞 Referência rápida

| Quando preciso de... | Arquivo |
|---|---|
| Roteiro de começo | RESUMO_EXECUTIVO.md |
| Entender fórmulas | PLANO_IMPLEMENTACAO_ENGINE.md |
| Guia passo-a-passo | ESTRUTURA_SESSOES.md |
| Validar progresso | ROTEIRO_CI_CD.md |
| Fórmula exata (estatística) | /skill hp12c-simulator → formulas/estatistica.md |
| Fórmula exata (calendário) | /skill hp12c-simulator → formulas/calendario.md |
| Fórmula exata (fluxo caixa) | /skill hp12c-simulator → formulas/cashflow.md |
| Exemplos de teste | /skill hp12c-simulator → test-vectors/*.json |
| Comportamento de erro | /skill hp12c-simulator → referencias/error-codes.md |

---

## 🎓 Princípios sagrados

1. **Leia a skill ANTES de codar**
   - Não é opcional. Não há atalho aqui.
   - A skill é 100% verdade; seu código é 0% até estar testado.

2. **Teste dirigido por exemplos do mundo real**
   - Cada vetor no JSON é um exercício de um livro publicado.
   - Se seu código diverge do livro, é BUG.

3. **Arquitetura: Redux-like reducer + eventos imutáveis**
   - Sem singletons, sem mutação após criação.
   - UI não conhece engine; engine não conhece UI.

4. **Precisão numérica não é negoçável**
   - 10 dígitos BCD, arredondamento HALF_EVEN.
   - Nunca, nunca use Double.

---

## 🚀 Próximos passos (APÓS 111 testes verdes)

1. **Fase 2** — Implementar amortização, depreciação, juros simples (~20h)
2. **Fase 3** — Programação (modo PRGM, GTO, GSB, ~40h)
3. **Fase 4** — UI Android + iOS (Compose/SwiftUI, ~80h)

Mas por enquanto: **foco em 111/111 testes verdes**.

---

## 🎯 Você vai conseguir!

Este projeto é bem-estruturado, tem documentação completa, testes bem-definidos, e fórmulas validadas. Tudo o que você precisa está aqui.

A única barreira agora é **tempo** e **atenção aos detalhes**.

**Comece agora. Leia RESUMO_EXECUTIVO.md. Boa sorte!** 🚀

---

*Última atualização: 6 de maio de 2026*  
*Autor: Alysson Myller (arcom.com.br)*
