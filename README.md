# HP 12C Platinum — Simulador (KMP)

Simulador bit-idêntico da HP 12C Platinum, construído em Kotlin Multiplatform com UI
nativa em Jetpack Compose (Android) e SwiftUI (iOS).

> O documento-mestre do projeto é `PROMPT_MESTRE.md`. Comece por ele.

## Estrutura do monorepo

```
hp12c-simulator/
├── PROMPT_MESTRE.md              Roadmap canônico (fases 0–4)
├── .claude/skills/hp12c-simulator/
│                                 Fonte de verdade: fórmulas, vetores, regras BCD,
│                                 contrato da engine — leitura obrigatória antes
│                                 de tocar código.
├── shared/                       Engine KMP
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/arcom/hp12c/engine/
│       └── commonTest/kotlin/com/arcom/hp12c/engine/
│                                 18 vetores TVM — primeiro teste já está vermelho.
├── androidApp/                   Stub Compose; UI real entra na Fase 4.
├── iosApp/                       README placeholder; Xcode project entra na Fase 4.
├── .github/workflows/ci.yml      CI rodando :shared:jvmTest a cada push/PR.
└── gradle/libs.versions.toml     Toolchain centralizada.
```

## Como rodar localmente

### Pré-requisitos

- JDK 17 (Temurin recomendado)
- Android Studio Ladybug+ com plugin Kotlin Multiplatform
- Xcode 15+ (só na Fase 4)

### Primeira execução

```bash
# Baixar o wrapper na primeira vez (a máquina precisa ter gradle instalado globalmente,
# ou abra o projeto no Android Studio para ele gerar o wrapper automaticamente):
gradle wrapper --gradle-version 8.10.2

# Rodar a suíte de testes (atualmente 1 teste, vermelho esperado — é o nosso marco).
./gradlew :shared:jvmTest
```

Abrir no Android Studio também funciona: `File → Open` na raiz do repo, esperar o sync,
`Run ':shared:jvmTest'`.

## Estado atual — Fase 0 concluída

Este commit é o ponto zero. Os stubs da engine estão em `commonMain` e compilam; a
implementação real entra na Fase 1. O teste `TvmVectorsTest.tvm_001` falha com
`NotImplementedError` — isso é proposital e serve de marcador de progresso.

## Invariantes não-negociáveis

Cinco regras que existem para ser protegidas (ver
`.claude/skills/hp12c-simulator/SKILL.md` para a versão longa):

1. **Exatidão numérica** — `BigDecimal` com `MathContext(10, HALF_EVEN)`. Nunca `Double`.
2. **Erros 0..9** disparam exatamente como no manual (Apêndice D).
3. **Pilha RPN de 4 níveis** com T sticky e `LAST X`.
4. **Todo exemplo resolvido do manual/Moretti/FURG vira teste automatizado.**
5. **Memória contínua** — fechar o app não apaga pilha, memórias nem programa.

## Licença

TBD — o simulador é um projeto pessoal/educacional de Alysson Myller (arcom.com.br).
A HP 12C Platinum e seu manual são propriedade da HP Inc. Este software é compatível
mas independente.
