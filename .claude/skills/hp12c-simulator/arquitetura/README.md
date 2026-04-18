# Arquitetura — placeholder

Esta pasta conterá, a partir da Fase 0 passo 3 (próxima sessão), o contrato público da engine e os tokens de UI:

- `engine-interface.md` — assinatura da interface Kotlin `CalculatorEngine` em `commonMain`, incluindo modelo de dados (`Stack`, `FinancialRegisters`, `DisplayFormat`), sealed class de erros `Hp12cError`, e os pontos de extensão para programação (Fase 3).
- `ui-skins.md` — tokens de design para os dois skins (`classic` / `modern`): paleta de cores exata da HP12C física, tipografia do LCD bitmap, dimensionamento de teclas, labels primários/`f`/`g`, e guia de ports entre Compose e SwiftUI.

Ambos os arquivos serão criados em conjunto com o código inicial da engine, para manter contrato e implementação em sincronia desde o primeiro commit.
