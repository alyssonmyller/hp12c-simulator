---
name: hp12c-simulator
description: Fonte canônica de fórmulas, vetores de teste e regras de comportamento da calculadora HP 12C Platinum, extraídas diretamente do manual oficial (bpia5314.pdf), da apostila Moretti e do livro FURG. Use sempre que houver qualquer trabalho de implementação, teste, revisão de código ou discussão de design envolvendo o simulador HP 12C — especialmente ao escrever a engine em Kotlin Multiplatform (commonMain), ao escrever testes de TVM/IRR/amortização, ao lidar com arredondamento BCD de 10 dígitos, com pilha RPN de 4 níveis, com códigos de erro 0..9, ou ao comparar resultados com a calculadora física. Se você está prestes a codar qualquer função financeira ou matemática da HP12C sem ter consultado esta skill, PARE e leia primeiro — os invariantes de precisão numérica aqui são obrigatórios e não-negociáveis.
---

# HP 12C Platinum Simulator — Skill de referência

## Propósito

Esta skill é a fonte única de verdade para o comportamento observável da calculadora HP 12C Platinum. Ela existe porque o simulador precisa ser **bit-idêntico** à calculadora física nos resultados visíveis ao usuário, e esse nível de fidelidade só é alcançável se as fórmulas, os vetores de teste e as regras de arredondamento estiverem documentados em um único lugar consultado antes de qualquer linha de código.

Três PDFs foram usados como fonte primária:

| Arquivo | Papel | Citação abreviada |
|---|---|---|
| `bpia5314.pdf` | Manual oficial HP 12C Platinum (pt-BR), 225 páginas | `manual` |
| `hp12c-matematica-financeira-apostila.pdf` | Apostila Prof. Moretti, 95 páginas, fórmulas explicadas passo a passo + exercícios propostos com gabarito | `moretti` |
| `livromfhp12c.pdf` | Livro FURG, 103 páginas, exercícios resolvidos adicionais | `furg` |

Toda afirmação não-trivial nesta skill cita a fonte no formato `(manual, p. NN)` ou `(moretti, p. NN, Ex. X)`. Sem citação rastreável, o conteúdo não entra.

## Quando invocar

Invoque esta skill **antes** de:

- Implementar ou modificar qualquer função financeira (`n`, `i`, `PV`, `PMT`, `FV`, `NPV`, `IRR`, amortização, depreciação, estatística, calendário).
- Escrever ou revisar testes da engine.
- Decidir sobre arredondamento, precisão interna ou formatação de display.
- Implementar a pilha RPN (ENTER, CHS, CLx, operações binárias, LAST X).
- Tratar um código de erro novo ou alterar o comportamento de um existente.
- Revisar código que toca qualquer uma das áreas acima em pull request.

Se você está "só ajustando um detalhe", invoque assim mesmo. O custo de ler é baixo; o custo de divergir da HP física (e descobrir meses depois com um usuário reclamando) é alto.

## Como usar o conteúdo

A skill é organizada em 4 pastas. Cada pasta tem uma responsabilidade clara e um critério de entrada:

### `formulas/` — equações canônicas
Fórmulas fechadas conforme aparecem no manual/apostila. **Uma fórmula aqui é a verdade matemática**; não a substitua por uma "equivalente algébrica" sem atualizar também a documentação de justificativa. Atualmente populado:

- `tvm.md` — equação TVM da HP12C (juros compostos, com variantes de período fracionário) + isolamento algébrico de cada uma das 5 variáveis (n, i, PV, PMT, FV) em modo BEGIN e END.

Planejado para próximas sessões: `juros-simples.md`, `juros-compostos.md`, `npv-irr.md`, `amortizacao.md`, `depreciacao.md`, `estatistica.md`, `calendario.md`.

### `test-vectors/*.json` — rede de segurança empírica
Cada vetor é um problema resolvido com resposta oficialmente publicada num dos 3 PDFs. Toda implementação de engine tem que passar em 100% dos vetores correspondentes. Atualmente populado:

- `tvm-vectors.json` — 18 vetores cobrindo FV, PV, n, i e PMT em modo END e BEGIN, retirados do manual e da apostila Moretti.

Schema de cada vetor (obrigatório):

```json
{
  "id": "tvm-001",
  "source": "arquivo, Seção/Capítulo, Exemplo X, p. NN",
  "description": "descrição curta do problema em pt-BR",
  "inputs": {
    "n": 5, "i": 4, "PV": -5000, "PMT": 0, "FV": 0, "mode": "END"
  },
  "solve_for": "FV",
  "expected": "6083.26",
  "format": "FIX 2",
  "notes": "opcional, para ambiguidades ou observações"
}
```

Convenção de sinais segue rigorosamente a da HP12C: saída de caixa negativa, entrada positiva. Se o manual apresenta um exemplo com `5000 CHS PV`, o vetor registra `PV: -5000`. O campo `expected` é **string** para evitar ruído de ponto flutuante; a engine deve formatar sua saída conforme `format` e comparar como string.

### `referencias/` — regras de borda e comportamento observável
Conteúdo que não é "fórmula" mas governa resultados observáveis:

- `bcd-rounding.md` — aritmética BCD de 10 dígitos, HALF_EVEN, ULP na última casa, por que usamos `MathContext(10, HALF_EVEN)` em Kotlin, e o catálogo de ambiguidades conhecidas entre o manual e as fontes secundárias.
- `error-codes.md` — tabela completa `Error 0..9` com condição exata de disparo (do Apêndice D do manual).
- `stack-behavior.md` — semântica da pilha de 4 níveis (X, Y, Z, T) + `LAST X`, diagrama de antes/depois para ENTER, operações binárias, CLx, R↓, x⇆y, STO/RCL, funções que descem vs não descem a pilha.

### `arquitetura/` — contratos entre engine e UI

- `engine-interface.md` — contrato público da `CalculatorEngine`: princípios (reducer puro, zero deps de plataforma, fidelidade > idiomaticidade), organização de pacotes, modelos (`Stack`, `FinancialRegisters`, `MemoryRegisters`, `DisplayFormat`, `CalculatorState`), alfabeto completo de `Event` para Fase 1 (com placeholders documentados para Fase 2/3), sealed class `Hp12cError` mapeando todos os 10 códigos, e estratégia de testes conectando `tvm-vectors.json`.

Planejado para próximas sessões: `ui-skins.md` (tokens de design dos skins `classic`/`modern`).

## Invariantes não-negociáveis (resumo executivo)

Estes 5 pontos são o que esta skill existe para proteger:

1. **Exatidão numérica.** Toda aritmética interna em `BigDecimal` com `MathContext(10, HALF_EVEN)`. Nunca `Double`. Divergência de mais de 1 ULP na última casa em relação à HP física é bug, não escolha de design. Detalhes em `referencias/bcd-rounding.md`.

2. **Comportamento de erro idêntico.** Os 10 códigos `Error 0..9` disparam exatamente nas condições documentadas no manual. Detalhes em `referencias/error-codes.md`.

3. **Pilha RPN automática de 4 níveis.** `ENTER` duplica X em Y; após op binária, Z desce para Y e T permanece em T (T é "sticky"). `LAST X` guarda o operando destruído. Detalhes em `referencias/stack-behavior.md`.

4. **Todo exemplo resolvido dos PDFs é teste.** Quando a engine passa em `test-vectors/*.json` por inteiro, temos reproduzido fielmente o comportamento publicado. Adicionar novo exemplo lido em fonte oficial → adicionar vetor correspondente.

5. **Memória contínua.** Fechar o app não apaga pilha, memórias R0..R9, Ri, nem programa. Persistência nativa por plataforma.

## Idioma

Código e identificadores públicos: **inglês** (`CalculatorEngine`, `solveForFutureValue`, `StackRegister.X`). Comentários, docstrings, mensagens de commit, e todo conteúdo desta skill: **pt-BR**.

## Fluxo de trabalho recomendado

Ao começar qualquer tarefa na engine:

1. Abra o arquivo relevante desta skill (`formulas/<tópico>.md`).
2. Verifique se existem vetores de teste para a função (`test-vectors/<tópico>-vectors.json`). Se sim, esses vetores vêm **primeiro** que o código.
3. Consulte `referencias/bcd-rounding.md` se houver qualquer operação numérica não-trivial.
4. Consulte `referencias/stack-behavior.md` se a função toca a pilha.
5. Escreva o teste, veja-o falhar, implemente, veja-o passar.
6. Se o comportamento observado nos testes divergir do esperado e a divergência não estiver documentada, adicione entrada em `referencias/bcd-rounding.md` (seção de ambiguidades) antes de escolher um lado.

Essa ordem é cara no começo e barata a cada feature subsequente. Vale a pena.
