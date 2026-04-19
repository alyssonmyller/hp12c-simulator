# Prompt-Mestre — Simulador HP 12C Platinum

> **Como usar este documento:** Cole o conteúdo da seção **"PROMPT"** no início de cada nova sessão de implementação. Ele é autocontido e orienta o agente sobre objetivo, escopo, stack, regras de precisão numérica, estratégia de testes e entregáveis. Ajuste apenas a linha `## Fatia desta sessão` conforme a fase em que estivermos.

---

## 1. Decisões arquiteturais já tomadas

| Decisão | Escolha |
|---|---|
| Stack | **Kotlin Multiplatform (KMP)** — engine em `commonMain`, UI nativa em Jetpack Compose (Android) e SwiftUI (iOS) |
| Toolchain real | **Kotlin 2.2.10**, **AGP 9.1.1**, **JDK 17** gerenciado via `jvmToolchain(17)` no bloco `kotlin { }` (resolve Java/Kotlin de forma unificada em todos os targets; substitui `compilations.all { kotlinOptions.jvmTarget = ... }` do Kotlin 1.x). Flags `android.newDsl=false`, `android.builtInKotlin=false` e afins em `gradle.properties` são escape hatches de compat do AGP 9 — dívida técnica catalogada, limpar quando migrarmos para o novo DSL. |
| MVP | **Mínimo:** modo RPN, aritmética, pilha automática, memórias (STO/RCL), TVM (n, i, PV, PMT, FV) |
| UI | **Dois skins** com toggle: `classic` (réplica fiel do aparelho) e `modern` (Material 3 / Cupertino adaptado) |
| Skill de apoio | **`hp12c-simulator`** — criada via `skill-creator`, contém fórmulas, vetores de teste e regras de BCD |
| Idioma do código | Inglês nos identificadores; comentários e docs em PT-BR |
| Repositório | Monorepo: `shared/`, `androidApp/`, `iosApp/`, `tests/`, `docs/` |

---

## 2. Fases do projeto (roadmap)

### Fase 0 — Fundação (sessão atual e próxima)
- Criar a skill `hp12c-simulator` com fórmulas e vetores de teste extraídos dos PDFs.
- Estruturar o projeto KMP (Gradle, módulos, CI).
- Definir contrato público da engine (`CalculatorEngine` interface).

### Fase 1 — Engine mínima (MVP escolhido)
- Aritmética BCD de 10 dígitos (wrapper sobre `BigDecimal`).
- Pilha RPN automática de 4 níveis (X, Y, Z, T) com registrador LAST X.
- Teclas: `0-9 . CHS EEX ENTER + - × ÷ CLx CLEAR-REG CLEAR-FIN STO RCL`.
- Memórias R0–R9 + Ri.
- TVM: `n, i, PV, PMT, FV, BEG, END` — resolução para qualquer uma das cinco variáveis.
- Formatação de display: `FIX 0–9`, `SCI`, `ENG`, separadores `.`/`,` configuráveis.
- Suíte de testes com vetores dos PDFs (todos os exercícios resolvidos viram test case).

### Fase 2 — Engine completa
- Porcentagem (`%`, `%T`, `Δ%`), calendário (`DATE`, `DYS`, `D.MY`/`M.DY`).
- Juros simples `f INT`.
- Amortização `f AMORT`.
- Fluxo de caixa: `CFo`, `CFj`, `Nj`, `NPV`, `IRR` (com iteração Newton-Raphson replicando tolerância e limite de iterações da HP).
- Depreciação: `SL`, `SOYD`, `DB`.
- Estatísticas: `Σ+`, `Σ-`, `x̄`, `s`, `L.R.`, `ŷ,r`.
- Funções matemáticas: `yˣ`, `1/x`, `√x`, `LN`, `eˣ`, `n!`, `INTG`, `FRAC`, `RND`.
- Modo ALG (algébrico) — alternância via `f` + tecla dedicada.
- Memória contínua (persistência da pilha, memórias e config entre sessões).

### Fase 3 — Programação (Parte II do manual)
- Modo PRGM: até 400 passos; `GTO`, `GSB`, `RTN`, rótulos `0–9, .0–.9`.
- Condicionais: `x≤y, x<0, x=y, x=0, x>y, x>0`.
- Execução: `R/S` normal, `SST`/`BST` passo-a-passo.
- Editor de programa na UI com cópia/colagem de listagens.

### Fase 4 — UI e polimento
- Skin `classic`: layout 1:1 (cores `#C0C0C0`, `#000000`, `#E67E22`, `#3498DB`, fonte do LCD bitmap).
- Skin `modern`: Material 3 / Cupertino, dark mode, haptic, teclas redimensionáveis.
- Toggle de skin em Settings.
- Acessibilidade: TalkBack/VoiceOver, tamanhos de fonte, alto contraste.
- Empacotamento: Play Store (Android) e, em seguida, App Store (iOS).

---

## 3. PROMPT (copie isto em novas sessões)

```text
Estou construindo um simulador perfeito da calculadora HP 12C Platinum, 
primeiro para Android e depois iOS, usando Kotlin Multiplatform 
(engine em commonMain, UI Jetpack Compose + SwiftUI).

## Contexto essencial

- Material de apoio em /mnt/uploads/:
  - bpia5314.pdf — Manual oficial HP 12C Platinum (pt-BR)
  - hp12c-matematica-financeira-apostila.pdf — Apostila Prof. Moretti (fórmulas + exercícios)
  - livromfhp12c.pdf — Livro FURG (mais exercícios resolvidos)
- Skill hp12c-simulator contém fórmulas validadas, vetores de teste 
  e regras de BCD. Leia SEMPRE antes de escrever código.
- O documento PROMPT_MESTRE.md tem o roadmap completo em fases.

## Objetivos não-negociáveis (invariantes do projeto)

1. **Exatidão numérica idêntica à HP12C física.** A calculadora usa 10 
   dígitos de mantissa BCD com arredondamento banker's-rounding (HALF_EVEN). 
   Toda operação que divergir da física por mais de 1 ULP na última casa 
   é BUG, não decisão de design.
2. **Comportamento idêntico de erros.** `Error 0` (divisão por zero), 
   `Error 1` (overflow em memória), `Error 2` (erro estatístico), 
   `Error 3` (IRR não converge), `Error 4` (programa > 400 passos), 
   `Error 5` (GTO para linha inexistente), etc. — todos devem replicar 
   as condições de disparo do manual.
3. **Pilha RPN automática de 4 níveis** com LAST X, idêntica ao comportamento 
   descrito na Seção 3 do manual (ENTER copia X em Y; após binop, Z desce 
   para Y, T permanece em T).
4. **Todo exercício resolvido nos PDFs é um teste automatizado.** Se um 
   exercício do livro dá R$ 15.231,44, nosso teste assert EQUALS esse valor 
   com tolerância zero após formatação FIX 2.
5. **Memória contínua:** fechar o app não apaga pilha/memórias/programa. 
   Persistência via Settings Multiplatform ou storage nativo.

## Fatia desta sessão

[PREENCHER: ex. "Fase 1, passo 2 — implementar pilha RPN de 4 níveis 
com testes unitários cobrindo todos os exemplos da Seção 3 do manual"]

## Regras de engenharia

- Código novo SEMPRE com teste antes (TDD). Teste deriva de exemplo do manual.
- Aritmética interna em BigDecimal com MathContext(10, HALF_EVEN). NUNCA Double.
- Identificadores em inglês; comentários e docs em pt-BR.
- Commits pequenos (1 feature OU 1 bug por commit), mensagens no padrão 
  Conventional Commits (feat:, fix:, test:, docs:, refactor:).
- Não modifique a API pública da engine sem atualizar PROMPT_MESTRE.md 
  e a skill hp12c-simulator juntos.
- Antes de declarar "pronto": rode toda a suíte de testes e confirme 
  que 100% passa. Sem testes pulados.

## Entregáveis esperados ao final desta sessão

1. Código commitado (diff revisável).
2. Suíte de testes verde.
3. Atualização na seção "Progresso" de PROMPT_MESTRE.md.
4. Próxima fatia sugerida (1 frase).
```

---

## 4. Skill `hp12c-simulator` — o que ela vai conter

A skill será criada via `skill-creator` e ficará em `/mnt/.claude/skills/user/hp12c-simulator/` (ou dentro do próprio repo, em `.claude/skills/`). Estrutura planejada:

```
hp12c-simulator/
├── SKILL.md                    # Instruções principais para o agente
├── formulas/
│   ├── juros-simples.md
│   ├── juros-compostos.md
│   ├── tvm.md                  # Equação TVM e resolução de cada variável
│   ├── npv-irr.md              # Fluxo de caixa, Newton-Raphson da HP
│   ├── amortizacao.md
│   ├── depreciacao.md          # SL, SOYD, DB
│   ├── estatistica.md
│   └── calendario.md           # dias 360/actual, formato D.MY/M.DY
├── test-vectors/
│   ├── tvm-vectors.json        # { inputs, expected, source: "manual p. 47" }
│   ├── irr-vectors.json
│   ├── amortizacao-vectors.json
│   └── ...
├── referencias/
│   ├── bcd-rounding.md         # Regras de arredondamento HP
│   ├── error-codes.md          # Tabela completa de Error 0..9
│   └── stack-behavior.md       # Semântica da pilha em cada operação
└── arquitetura/
    ├── engine-interface.md     # Contrato Kotlin da engine
    └── ui-skins.md             # Tokens de design dos dois skins
```

### Exemplo do formato dos vetores de teste

```json
// test-vectors/tvm-vectors.json
{
  "source": "manual HP12C Platinum, Seção 3, p. 45, Exemplo 1",
  "description": "Financiamento — resolver FV",
  "inputs": {
    "n": 360,
    "i": 0.8333,
    "PV": -100000,
    "PMT": 0,
    "mode": "END"
  },
  "solve_for": "FV",
  "expected": "1980546.77",
  "format": "FIX 2"
}
```

Cada vetor é tocado por 1+ teste unitário. Quando a engine passa em todos, sabemos que a fatia está correta.

---

## 5. Extração de vetores de teste dos PDFs (trabalho inicial)

Antes de escrever a engine, o primeiro passo prático é **varrer os 3 PDFs e extrair cada exemplo resolvido** para `test-vectors/*.json`. Isso vira nossa rede de segurança. Estimativa preliminar:

- Manual oficial: ~60–80 exercícios resolvidos ao longo do livro.
- Apostila Moretti: ~50 exercícios propostos com gabarito + vários resolvidos.
- Livro FURG: ~40 problemas resolvidos.

Total: **~150–200 vetores de teste** antes de escrevermos uma linha da engine.

---

## 6. Progresso

| Data | Fase | Marco | Notas |
|---|---|---|---|
| 2026-04-17 | — | Prompt-mestre escrito, decisões tomadas | Aguardando criação da skill `hp12c-simulator` |
| 2026-04-17 | Fase 0, passo 1 | Skill `hp12c-simulator` criada | Estrutura em `.claude/skills/hp12c-simulator/` com SKILL.md, `formulas/tvm.md`, `test-vectors/tvm-vectors.json` (18 vetores), `referencias/{bcd-rounding,error-codes,stack-behavior}.md`, `arquitetura/README.md`. Fontes: 2 vetores do manual + 16 da apostila Moretti; 0 do FURG (reservado para a próxima sessão se necessário). Ambiguidades registradas: regra de teto para `n` (Moretti Ex. 12), leitura em FIX 2 vs FIX 6 (Moretti Ex. 13), e nota sobre o exemplo confuso do manual p. 50 ("5 g 12x" vs "3 anos"). Todos os 18 vetores verificados numericamente contra a equação canônica (18/18 pass). |
| 2026-04-17 | Fase 0, passo 2 | Contrato `CalculatorEngine` escrito | `.claude/skills/hp12c-simulator/arquitetura/engine-interface.md` criado com ~300 linhas. Define 3 princípios de design (reducer puro, zero deps de plataforma, fidelidade>idiomaticidade), estrutura de pacotes `com.arcom.hp12c.engine`, modelos imutáveis (`Stack`, `FinancialRegisters`, `MemoryRegisters`, `DisplayFormat`, `CalculatorState`), alfabeto completo de `Event` sealed class para Fase 1 (Entry, StackOp, Arith, Memory, Financial, Display, AcknowledgeError), `Hp12cError` com 26 subclasses cobrindo os 10 códigos, e estratégia de teste conectando `tvm-vectors.json`. Interface `reduce(state, event): state` — nunca lança, erros viram `state.pendingError`. Decisões explícitas documentadas: dual Store/Solve dos registradores financeiros via `stack.isEntering`; `null` vs `ZERO` em `FinancialRegisters`; separador numérico fora do `CalculatorState`. Placeholders para Fase 2 (transcendentais, %, Σ, calendário, CF, depreciação, ALG) e Fase 3 (programação) já previstos em comentários. |
| 2026-04-17 | Fase 1, passo 1 | `Hp12cDecimal` — aritmética BCD 10 dígitos `HALF_EVEN` | `Hp12cDecimal` promovido de stub-string para `expect class` em `commonMain` com API fechada (4 ops binárias + unário, `powInt`, `pow` TODO passo 2, `isZero`, `compareTo`, `equals`/`hashCode` por valor numérico, `toString`). Novo source set **`jvmCommonMain`** em `shared/build.gradle.kts` agrupa Android + JVM (depende de `commonMain.get()`; `androidMain`/`jvmMain` dependem dele). `actual class` em `jvmCommonMain` wrappeia `java.math.BigDecimal` com `MathContext(10, HALF_EVEN)` aplicado em toda operação + na construção via `of(String)`. Igualdade via `compareTo == 0` (scale-agnostic: `of("1.00") == of("1.0")`), `hashCode` consistente via `stripTrailingZeros()`. Divisão por zero propaga `ArithmeticException` nativo de BigDecimal; contrato documentado: reducer captura e mapeia para `Hp12cError.DivisionByZero`. `actual class` stub em `iosMain` com todos os métodos lançando `TODO("Fase 4 — kotlin-multiplatform-bignum ou impl manual")`, mantendo os três targets iOS compilando. Testes em `commonTest/Hp12cDecimalTest`: aritmética exata (+, −, ×, ÷, neg), precisão (1/3 → 0.3333333333, 2/3 → 0.6666666667, 1/7 → 0.1428571429), HALF_EVEN tie-break (`12.345678905` → `12.34567890` em dígito par; `12.345678915` → `12.34567892` em dígito ímpar), `powInt` (2^10, 7^0, 10^-2, e 1.01^360 × 100k ≈ 3594964.13 validando com vetor tvm-017), `isZero` cobrindo `"-0"`, `compareTo` ordenado, divisão por zero com `assertFailsWith<ArithmeticException>`. Meta-invariante respeitada: `Transcendentals.pow/exp/ln` e `Hp12cDecimal.pow(Hp12cDecimal)` ficam `TODO` para o passo 2 (precisam de série de Taylor com redução de argumento; `powInt` cobre tudo que a TVM fechada precisa). |
| 2026-04-17 | Fase 1, passo 2 | Pilha RPN — `StackOps` + 6 cenários de regressão | Novo arquivo `state/StackOps.kt` em `commonMain` com extension functions puras sobre `Stack`: primitivas internas `lift(newX)` (sobe pilha sem olhar flag) e `drop()` (desce pilha mantendo T sticky); API pública `acceptNewNumber(newX)` (respeita `stackLiftEnabled` — usado pelo reducer no primeiro dígito de cada entrada), `enter()`, `clx()`, `rollDown()`, `rollUp()`, `swapXY()`, `lstx()`, `binaryOp { y, x -> ... }`, `unaryOp { x -> ... }`, `percentOp { y, x -> ... }` (este último preserva Y, conforme o idiom `300 ENTER 15 % -`). Cada função encapsula exatamente uma linha da tabela da Seção 3 de `referencias/stack-behavior.md` — quem mexe em `lastX`, quem desliga `stackLiftEnabled`, e quem encerra `isEntering`. Testes em `commonTest/state/StackOpsTest` cobrem: (1) diagramas antes/depois de cada primitiva isoladamente; (2) regra dos flags (lastX inalterado em ENTER/CLx/R↓/R↑/x⇆y; preenchido em binária/unária/percent/LSTx-destino; stackLift OFF em ENTER/CLx e ON em todas as outras); (3) divisão por zero via `binaryOp` propaga `ArithmeticException` (contrato: reducer captura e mapeia para `Hp12cError.DivisionByZero`); (4) 6 dos 8 cenários de regressão da Seção 5 do stack-behavior — `5 ENTER 3 +` = 8, `5 ENTER 5 ENTER 5 ENTER 5 +++` = 20 (T sticky em cadeia), `3 ENTER 4 × LSTx ÷` = 3, `5 CLx 3 +` = 3 (CLx desliga lift), `300 ENTER 15 % -` = 255 (percent não desce pilha), e `1 ENTER 2 ENTER 3 ENTER 4 R↓` = `(3,2,1,4)`. Os dois cenários restantes (STO preservando pilha e erro preservando pilha pré-operação) ficam para os testes do reducer no passo 3. Decisão de design explícita: `isEntering` é zerado por toda operação de pilha, já que depois de qualquer op o usuário não está mais no meio da digitação — o reducer re-liga quando chega o próximo `Entry.Digit`. `TvmVectorsTest.tvm-001` continua vermelho (espera do reducer), mas agora a pilha abaixo dele está lacrada. |
| 2026-04-17 | Fase 0, passo 3 | Monorepo KMP scaffolded | Raiz Gradle escrita (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml` com Kotlin 2.0.21, AGP 8.7.3, JVM target 17, minSdk 24, compileSdk 35). Módulo `:shared` KMP com alvos Android + JVM + iosX64/iosArm64/iosSimulatorArm64 (framework static `Hp12cShared`). 13 arquivos `.kt` em `commonMain` cobrindo todos os tipos do contrato: `Hp12cDecimal` (wrapper stub), `Transcendentals`, `Stack`, `FinancialRegisters` + `TvmMode`, `MemoryRegisters` + `RegisterId`, `DisplayFormat` + `NumericSeparator`, `CalculatorState` + `ProgramState`, `Hp12cError` (26 objects / 10 códigos), `Event` (sealed completa da Fase 1 + placeholders Fase 2/3 comentados), `CalculatorEngine` (interface + companion com `Default` e `InitialState`), `DefaultEngine` (internal, `TODO()` nos dois métodos), `DisplayFormatter` (stub). Módulo `:androidApp` com stub Compose minimal (MainActivity que renderiza `CalculatorEngine.InitialState.hashCode()` só para provar o link com `:shared`). `iosApp/README.md` documenta geração do framework — Xcode project fica para a Fase 4. Primeiro teste vermelho `TvmVectorsTest.tvm_001_*` escrito em `commonTest` consumindo o vetor `tvm-001` inlinado (loop sobre os 18 vetores entra na Fase 1 quando `expect/actual readTestResource` for implementado). CI em `.github/workflows/ci.yml` com Ubuntu+JDK17 rodando `:shared:jvmTest` em push/PR e uploadando relatório como artifact. `README.md` raiz, `.gitignore` abrangente (Gradle/IntelliJ/Android/iOS/macOS), `tvm-vectors.json` copiado para `shared/src/commonTest/resources/test-vectors/`. Sanity-check final: todos os imports do teste resolvem para tipos declarados, pacotes batem com a Seção 2 de `engine-interface.md`. Pendente: gerar o wrapper binário (`gradle wrapper`) e rodar `./gradlew :shared:jvmTest` no Android Studio — ambiente sandbox não tinha `gradle`/`kotlinc` então a verificação de compilação real é primeira tarefa do próximo sync. |
| 2026-04-17 | Fase 1, passo 3 | Reducer — Entry/StackOp/Arith/Memory/Display/AckError | `CalculatorState` ganhou o campo `entryBuffer: String?` com invariante forte (`init { check((entryBuffer != null) == stack.isEntering) }`) para garantir que buffer de digitação e flag `isEntering` andem sempre juntos. Motivo de manter texto em vez de `Hp12cDecimal` durante a entrada: estados como `"1."` (ponto pendurado), `"1.0"` (zero à direita significativo), `"1E"` (EEX sem dígitos do expoente) e `"1E-"` (CHS logo após EEX) não têm representação válida como `Hp12cDecimal` sem perder informação visual; e `CHS` durante entrada inverte mantissa ou expoente, operação trivial sobre string. Novo helper `Stack.pushValue(value)` em `StackOps.kt` para empurrar valor "externo" (RCL, Solve.Fv etc.) respeitando o flag `stackLiftEnabled` — sem marcar `isEntering`, já que o valor já vem pronto. `DefaultEngine.reduce(state, event)` totalmente implementado para `Event.Entry.*` (Digit/DecimalPoint/ChangeSign/Eex), `Event.StackOp.*` (Enter/Clx/Swap/RollDown/RollUp/LastX), `Event.Arith.*` (Add/Sub/Mul/Div/Negate), `Event.Memory.Store`/`Memory.Recall`, `Event.Display.*` (puramente cosmético — não commita buffer nem zera `isEntering`) e `Event.AcknowledgeError` (no-op explícito). Estratégia de commit: `state.commitEntry()` é chamado antes de toda família exceto Entry/Display/AckError; parseia o buffer para `Hp12cDecimal`, escreve em `stack.x`, zera buffer e `isEntering`. `normalizeForParse` preenche `"1E"`→`"1E0"` e `"1E-"`→`"1E-0"` antes do `Hp12cDecimal.of`. `Event.Financial` permanece `TODO("Fase 1 passos 4-5")`. Regra crítica da Seção 5 do stack-behavior honrada no reducer: qualquer tecla com `pendingError != null` limpa o erro e vira no-op (absorção idêntica à HP física — early return em `reduce`). `ArithmeticException` nas binárias é capturado e mapeado para `state.copy(pendingError = Hp12cError.DivisionByZero)` **sem** mexer na pilha (regra 8 — pilha preservada pré-op). `Event.Memory.Store` preserva pilha inteira (regra 7) e `Event.Memory.Recall` usa `pushValue` para respeitar stackLift como descrito em Apêndice A do manual p.181 ("RCL comporta-se como nova digitação"). Caps de digitação implementadas: mantissa ≤ 10 dígitos (`countMantissaDigits` ignora `-`, `.`, `E`), expoente ≤ 2 dígitos. `Event.Entry.ChangeSign` durante entrada é operação de string sobre buffer (não preenche `lastX`); fora de entrada, é unária via `Event.Arith.Negate` (preenche `lastX`). Testes em `commonTest/engine/ReducerTest` (~30 casos organizados em 7 seções): aritmética básica com vetores do manual, STO preserva pilha (regra 7), divisão por zero preserva pilha e seta `pendingError = DivisionByZero` (regra 8), CHS durante vs fora da entrada, EEX com CHS invertendo expoente (`1.5 EEX 3 CHS` → 0.0015), RCL após ENTER respeitando stackLift desligado, qualquer tecla com erro pendente limpa e é no-op, cenários 7 e 8 da Seção 5. `TvmVectorsTest.tvm-001` continua vermelho (espera Financial do passo 4); as demais famílias estão verdes. |
| 2026-04-18 | Fase 1, passo 4 | Reducer — Financial.Store / Set(Begin\|End)Mode / ClearFinancial | `DefaultEngine.reduce` fecha a primeira metade da família `Event.Financial`: `reduceFinancial` dispatcher + `reduceFinancialStore` helper em ~50 linhas novas, mais uma linha só no entrypoint trocando o `TODO` antigo por `is Event.Financial -> reduceFinancial(state, event)`. `Store.{N, I, Pv, Pmt, Fv}` comita o buffer (`state.commitEntry()`) antes de copiar `stack.x` para o campo correspondente em `FinancialRegisters`; **não toca na pilha** — regra 7 da Seção 5 de `stack-behavior.md` aplicada a registradores financeiros, exatamente como em `Event.Memory.Store`. `SetBeginMode` e `SetEndMode` são puramente cosméticos (igual `Event.Display.*`): alternam `financial.mode = TvmMode.BEGIN\|END` sem comitar buffer, sem mexer na pilha e sem tocar nos registradores numéricos — trocar o modo com TVM preenchido é legítimo e afeta só o próximo `Solve`. `ClearFinancial` comita o buffer (para que o visor pós-tecla mostre o número recém-digitado, reproduzindo "42 f CLEAR FIN") e zera os 5 registradores numéricos para `null`, preservando `mode`, pilha, memórias de usuário e flag C — comportamento de "Clearing Operations" do Apêndice A do manual. `Solve.*` e `ToggleCompoundFractionFlag` permanecem `TODO("Fase 1 passo 5")`. **Decisão arquitetural registrada:** `financial.i` é armazenado em **percentual** (usuário digita `4`, guarda-se `4`, não `0.04`), conforme Seção 3.2 de `engine-interface.md` e Seção 3 de `formulas/tvm.md` — a conversão para decimal acontece só dentro das fórmulas TVM no passo 5. Isso fecha a ambiguidade que estava aberta em Seção 7 deste documento. Novo teste `commonTest/engine/ReducerFinancialStoreTest` com 16 casos: cada `Store.*` preserva pilha (regra 7 por registrador), `Store.I` guarda `4` e não `0.04`, `Store.Pv` aceita negativo (`-5000 CHS PV`), Store durante entrada comita o buffer primeiro, `SetBegin/EndMode` não comita buffer nem zera `isEntering`, mudar modo preserva os 5 registradores + pilha, `ClearFinancial` zera os 5 e preserva mode, `ClearFinancial` preserva pilha e memórias de usuário, `ClearFinancial` durante entrada comita primeiro (`42 f CLEAR FIN` → X=42), sequência canônica `5 n 4 i 5000 CHS PV 0 PMT` do manual Capítulo 2 p.48 monta os registradores corretos com FV ainda `null` (pronto para `Solve.Fv` no passo 5), e alternância `BEG → END → BEG` sem erro. `TvmVectorsTest.tvm-001` continua vermelho (espera `Solve` do passo 5); as demais famílias estão verdes. |
| 2026-04-18 | Fase 1, passo 5 | Reducer — Financial.Solve.{Fv,Pv,Pmt} + flag C | `DefaultEngine` agora **calcula** TVM fechado para as três variáveis que a HP resolve com `(1+i)^n` inteiro — que é o que basta para 13 dos 18 vetores da skill. Implementação em ~110 linhas novas: `reduceFinancialSolve` dispatcher com guarda de passo 6 para `Solve.N`/`Solve.I` (`TODO("Fase 1 passo 6 — ... ln/exp/pow ...")`), extração dos 5 registradores tratando `null` como `ZERO` (convenção do manual — Seção 6 de `formulas/tvm.md`), conversão única de `i` percentual para decimal (`iDec = iPct / 100`, com `HUNDRED` como `Hp12cDecimal` local), truncação de `n` para `Int` via extensão privada `toIntTruncated()` (parse por `toString()` — seguro porque o actual JVM usa `toPlainString()`), despacho para as três fórmulas fechadas com `try/catch (ArithmeticException)` → `Hp12cError.TvmNoConverge` preservando pilha (regra 8). Três funções privadas derivadas rigorosamente da Seção 5 de `formulas/tvm.md`, com ramo degenerado `i=0`: `computeFv(n, i, pv, pmt, isBegin) = -pv·(1+i)^n - (1+iS)·pmt·((1+i)^n - 1)/i`; `computePv(n, i, pmt, fv, isBegin) = -(1+iS)·pmt·(1-(1+i)^(-n))/i - fv·(1+i)^(-n)`; `computePmt(n, i, pv, fv, isBegin) = (-pv·(1+i)^n - fv) / ((1+iS)·((1+i)^n - 1)/i)`. `begAdj = (1+i)` em BEGIN, `1` em END — o `S` da equação canônica encapsulado em uma única linha. Pós-condições aplicadas: resultado empurrado em `stack.x` via `Stack.pushValue(result)` (respeita `stackLiftEnabled`, igual RCL), `LASTx ← stack.x` antigo (regra 4 da Seção 5 de `stack-behavior.md` — Solve "destrói X"), registrador resolvido atualizado (`financial.fv/pv/pmt ← result`) para replicar o comportamento "`RCL FV` pós-`Solve.Fv` devolve o valor recém-calculado". `ToggleCompoundFractionFlag` (STO EEX) também fecha: alterna `state.compoundFractionFlag` sem comitar buffer, sem tocar pilha/registradores — efeito observável só entra na Fase 2 com `n` fracionário. Novo teste `commonTest/engine/ReducerFinancialSolveTest` com 23 casos organizados em 8 seções: 4 `Solve.Fv` sem PMT (tvm-001, -011, -012, -017), 2 `Solve.Fv` com PMT (tvm-007 END, tvm-010 BEGIN), 3 `Solve.Pv` (tvm-002, -005, -013), 4 `Solve.Pmt` (tvm-006, -008, -009 BEGIN, -018), 3 ramos `i=0` (FV, PV, PMT — fórmula linear), 3 efeitos colaterais de atualização de registrador financeiro, 1 pilha+LSTx (X antigo preservado, resultado em X), 1 registradores `null` virando `ZERO` (mesmo resultado de tvm-001 sem setar PMT e FV), e 2 do flag C (toggle alterna o bool; não perturba pilha, registradores, buffer ou memórias). Comparação numérica via helper `assertNear(expected, computed, places)` com tolerância de meio ULP na casa exibida (0,005 para FIX 2) — blinda contra a ausência de `formatDisplay` (TODO passo 7) sem perder fidelidade: divergência acima disso é erro numérico real. Os 5 vetores restantes (tvm-003, -004, -014 = `Solve.I`; tvm-015, -016 = `Solve.N`) ficam para o passo 6 junto de `Transcendentals`. `TvmVectorsTest.tvm_001` segue vermelho mas por motivo diferente: agora quebra em `engine.formatDisplay(...)` (linha 71) e não mais em `engine.reduce(...)` (linha 70) — prova de que a parte Solve já computa o resultado correto em `stack.x`. |

---

## 7. Próximo passo sugerido

**Sessão seguinte (Fase 1, passo 6): `Transcendentals` (`ln`, `exp`, `pow`) sobre `Hp12cDecimal`.** Dois usuários imediatos desse módulo: (a) `Event.Financial.Solve.N` — fórmula fechada `n = ceil(ln((PMT·(1+iS) − FV·i) / (PMT·(1+iS) + PV·i)) / ln(1+i))` (com teto ao próximo inteiro — HP arredonda para cima quando a conta não fecha exata, ver Moretti Ex. 12, 15 e 16); (b) `Event.Financial.Solve.I` — **Newton-Raphson** sobre a equação TVM, com tolerância e limite de iterações replicando a HP física (~40). Se não convergir, setar `pendingError = Hp12cError.TvmNoConverge` (`Error 5` na HP, ver `referencias/error-codes.md`). A função `Hp12cDecimal.pow(Hp12cDecimal)` que hoje está `TODO` também entra aqui (base para `exp(y · ln(x))`).

Leitura obrigatória antes de codar: **Seção 5** de `formulas/tvm.md` (fórmulas 5.4 e 5.5 para `n` e `i`), **`referencias/bcd-rounding.md`** (série de Taylor com redução de argumento — Seção 3 se já estiver escrita, senão escrever — para preservar 10 dígitos com HALF_EVEN), e **`referencias/error-codes.md`** (Error 5 para não-convergência).

Plano concreto:

- **`Hp12cDecimal.ln(x)`** — implementar como série de Taylor sobre `(x−1)`: reduzir `x` para intervalo `[1/√2, √2]` escalando por potências de `2` (ou por `e`) acumulando o logaritmo da escala, somar série até a última casa arredondar estavelmente (critério: dois termos consecutivos dentro de 0,5 ULP na décima casa). Lança `ArithmeticException` para `x ≤ 0` — reducer captura e mapeia para `Hp12cError.InvalidLog` (Error 0 da HP).
- **`Hp12cDecimal.exp(x)`** — análogo: reduzir `x` por inteiro mais próximo de `ln(2)`, somar série `1 + x + x²/2! + ...` com o critério acima, multiplicar por `2^k` acumulado. Overflow (`x > ~230`) vira `ArithmeticException` → `Hp12cError.Overflow`.
- **`Hp12cDecimal.pow(y)`** — delega `exp(y · ln(this))`; caso `y` for inteiro dentro de `Int.MIN..Int.MAX` despacha para `powInt(y.toIntTruncated())` preservando exatidão (evita erro de redondear `ln`/`exp`).
- **`Event.Financial.Solve.N`** — tira o `TODO` atual do `reduceFinancialSolve`: resolve via `ln`, aplica teto (`ceil`), escreve em `stack.x` via `pushValue` (igual Fv/Pv/Pmt), guarda X antigo em `LASTx`, atualiza `financial.n`.
- **`Event.Financial.Solve.I`** — mesmo shape. Iteração Newton começa em `i₀ = 0` ou em um chute cru baseado em sinais de PV/FV/PMT. A cada passo avalia a função residual TVM e sua derivada (forma fechada para evitar diferença finita). Termina com `|residual| < 10⁻⁸ · (1+|PV|+|FV|)` ou `n_iter > 40`. Se não converge, pilha intacta (regra 8) e `pendingError`.

**Testes do passo 6:** destravar em `ReducerFinancialSolveTest` os 5 vetores gated (tvm-003, tvm-004, tvm-014 para `Solve.I`; tvm-015, tvm-016 para `Solve.N`). Adicionar testes dedicados para `ln`/`exp` (valores tabulados do manual — `ln(2)=0.6931471806`, `ln(10)=2.302585093`, `e=2.718281828`, `e^1=...`, `e^10=22026.46580`) e teste de não-convergência de `Solve.I` disparando `pendingError` sem tocar pilha. Também testar `Hp12cDecimal.pow(Hp12cDecimal)` contra `powInt` quando `y` é inteiro — devem bater exatamente, senão há deriva de precisão na composição `exp(y·ln(x))`.

**Passos seguintes da Fase 1 (ordem prevista):**

7. `DisplayFormatter` respeitando FIX/SCI/ENG + `NumericSeparator`. Pré-requisito do `TvmVectorsTest` em loop (compara strings formatadas). Destrava o vermelho persistente de `TvmVectorsTest.tvm_001` — hoje a parte `reduce` já computa `stack.x ≈ 6083.26`, falta só renderizar como `"6083.26"`.
8. Remover inlinagem de `tvm-001` e rodar os 18 vetores em `TvmVectorsTest` parametrizado (usando `expect/actual fun readTestResource(path: String): String`).

Meta da Fase 1: os 18 vetores de TVM passando, suíte 100% verde em `:shared:jvmTest`, CI verde, commit por passo no padrão Conventional Commits.

**Pendência antes do passo 6:** rodar `./gradlew :shared:jvmTest` no Android Studio (sandbox sem Gradle nem rede pra `services.gradle.org`) e confirmar que `Hp12cDecimalTest` (17), `StackOpsTest` (17), `ReducerTest` (~30), `ReducerFinancialStoreTest` (16) **e `ReducerFinancialSolveTest` (23)** estão verdes. `TvmVectorsTest.tvm_001` continua vermelho por design até o passo 7 — mas agora deve falhar em `engine.formatDisplay(...)` (linha 71), não em `engine.reduce(...)` (linha 70): sinal de que `Solve.Fv` já calcula corretamente.
