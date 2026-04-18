# iosApp — placeholder

A UI iOS entra na **Fase 4** do roadmap. Durante as Fases 1–3, o módulo `:shared` é
testado exclusivamente via `jvmTest` (mais rápido) e, quando a Fase 2 adicionar iteração
Newton-Raphson, via `iosSimulatorArm64Test` para garantir paridade numérica.

## Como o framework é gerado

O Gradle do `:shared` já declara os três targets iOS (`iosX64`, `iosArm64`,
`iosSimulatorArm64`) com `baseName = "Hp12cShared"` e `isStatic = true`. Para produzir
o `.framework` consumido pelo Xcode:

```bash
./gradlew :shared:linkReleaseFrameworkIosSimulatorArm64
# saída: shared/build/bin/iosSimulatorArm64/releaseFramework/Hp12cShared.framework
```

Na Fase 4, este diretório ganhará:

- `iosApp.xcodeproj` com dois targets SwiftUI (skin `classic` e `modern`)
- `Podfile` + `Gemfile` — tooling mínimo de Cocoapods (`cocoapods` plugin do Kotlin)
- Uma `ViewModel` Swift fina que adapta `Event` → `reduce()` → `CalculatorState` → `formatDisplay`

## Por que não está aqui ainda

A prioridade é ter a engine 100% correta antes de duplicar a UI entre Compose e SwiftUI.
Bug numérico descoberto na Fase 1 é um fix; o mesmo bug descoberto na Fase 4 depois de
ter polido UI em duas plataformas é três fixes (engine + Compose + SwiftUI). Por isso a
ordem Android-primeiro-com-UI-mínima, iOS-por-último-com-UI-full no `PROMPT_MESTRE.md`.
