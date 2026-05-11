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
- `transcendentais.md` — funções matemáticas e de alteração de números (Seção 7 do manual: `√x`, `1/x`, `x²`, `LN`, `e^x`, `n!`, `y^x`, `INT`, `FRAC`, `RND`) e funções de percentagem (Seção 2: `%`, `Δ%`, `%T`), com domínio, condições de erro e comportamento da pilha para cada função. Inclui 6 ambiguidades documentadas (`0!`, `%`/`%T` com `y=0`, `y^0` com `y<0`, `RND` em SCI/ENG, `LN(1)`/`e^0`, precisão de `sqrt` vs `pow(0.5)`).
- `estatistica.md` — sete teclas estatísticas (Seção 6 do manual: `Σ+`, `Σ-`, `g x̄`, `g s`, `g ŷ,r`, `g x̂,r`, `g x̄w`), com: (a) convenções dos 6 registradores **R1..R6** acumulados (n, Σx, Σx², Σy, Σy², Σxy) e decisão explícita de compartilhar fisicamente os slots com `STO/RCL` de R1..R6 do usuário (invariante de fidelidade à HP física — `STO 3` durante modo estatístico corrompe Σx² sem disparar erro); (b) aritmética de acumulação em `Σ+`/`Σ-` com `n` novo empurrado em X; (c) fórmulas fechadas de Apêndice E p. 204-205 para média, média ponderada, desvio-padrão amostral (Bessel), regressão linear e correlação de Pearson; (d) truque `g x̄ Σ+ g s` para desvio populacional σ com justificativa algébrica; (e) comportamento de pilha "saída dupla" (`x̄`, `s`, `ŷ,r`, `x̂,r` escrevem X **e** Y diretamente — novidade arquitetural em relação às operações clássicas da Fase 1); (f) tabela completa de Error 2 (Apêndice D p. 194); (g) 7 ambiguidades catalogadas, incluindo a mais custosa (ambiguidade #8.6: `r` via `x⇆y` só dispara Error 2 no swap, não no cálculo — obriga a engine a carregar um flag `statisticalState.rInvalid`).
- `calendario.md` — quatro teclas de calendário (Seção 2 do manual: `g M.DY`, `g D.MY`, `g DATE`, `g ΔDYS`), com: (a) decodificação `mm.ddyyyy` ↔ `dd.mmyyyy` regida pelo flag persistente `dateFormat`; (b) fórmula `f(DT) = 365·yyyy + 31·(mm−1) + dd + INT(z/4) − x` do Apêndice E p. 200 com tabela explicativa do offset `INT(0.4·mm + 2.3)`; (c) fórmula 30/360 com regra assimétrica de fim-de-mês entre `DT₁` (data anterior) e `DT₂` (data posterior); (d) inversão de `f` para `g DATE` e cálculo de dia da semana (`dow = ((f + 5) mod 7); 0→7`) com convenção HP `1=segunda...7=domingo`; (e) limite válido `15 oct 1582 ≤ DT ≤ 25 nov 4046` (gregoriano histórico até overflow do mantissa BCD); (f) saída dupla `ΔDYS` (X=exato, Y=comercial) — segunda ocorrência do padrão "saída dupla" depois das estatísticas; (g) condições de Erro 8 (Apêndice D p. 195-196); (h) errata documentada do exemplo p. 33 (manual imprime `10.152005` mas o resultado `498` corresponde a 14 out 2005, não 15) — registrada como **vermelho esperado** no JSON; (i) limitação histórica do `dow` para datas pré-1900 (HP usa "Juliano proléptico" sem regra 100/400, manual nota 4 p. 32).
- `cashflow.md` — cinco teclas de fluxo de caixa (Seção 4 do manual: `g CFo`, `g CFj`, `g Nj`, `f NPV`, `f IRR`), com: (a) decisão arquitetural de **compartilhamento físico de R0..R20** com `STO/RCL` do usuário e do contador `n` com `FinancialRegisters.n` do TVM (terceira ocorrência do padrão "compartilhamento de registradores", depois de Σ R1..R6 da estatística); (b) fórmulas fechadas de Apêndice E p. 199 para NPV (`Σ CF·discount_factor` com agrupamento via Nⱼ) e IRR (raiz de `NPV(i)=0`); (c) **algoritmo Newton-Raphson** para IRR via diferença finita central (`h=10⁻⁶`, máx 100 iter, fallback chain `{0%, 10%, -50%, 100%}`) — referencial direto a `Solve.I` da Fase 1 passo 6; (d) interpretação dos 4 casos de Apêndice C: Caso 1 (positiva única), Caso 2 (negativa + busca por positiva), Caso 3 (Erro 3 — não convergiu), Caso 4 (Erro 7 — sem mudança de sinal); (e) **discrepância documentada Erro 6 vs Erro 7** com `Hp12cError.kt` da Fase 1 (atual mapeia tudo para Erro 7, manual prescreve Erro 6 para overflow de registradores e Nj inválido — correção fica para o passo 16); (f) caso de borda `i=0` com fórmula linear; (g) estado novo a adicionar em `FinancialRegisters` no passo 16: array paralelo `cashflowNj[0..20]` com defaults 1, sem campo novo em `CalculatorState`.

Planejado para próximas sessões: `juros-simples.md`, `juros-compostos.md`, `amortizacao.md`, `depreciacao.md`.

### `test-vectors/*.json` — rede de segurança empírica
Cada vetor é um problema resolvido com resposta oficialmente publicada num dos 3 PDFs. Toda implementação de engine tem que passar em 100% dos vetores correspondentes. Atualmente populado:

- `tvm-vectors.json` — 18 vetores cobrindo FV, PV, n, i e PMT em modo END e BEGIN, retirados do manual e da apostila Moretti.
- `transcendentais-vectors.json` — 34 vetores cobrindo as 13 teclas de função matemática/percentagem (`%`, `Δ%`, `%T`, `1/x`, `x²`, `√x`, `LN`, `e^x`, `n!`, `RND`, `INT`, `FRAC`, `y^x`). 27 vetores de caminho feliz + 7 vetores de erro (5× Error 0, 2× Error 5). Schema adaptado: `inputs` vira `stack` (pilha explícita) e o campo `solve_for` vira `operation` (a tecla pressionada). Vetores de erro usam o campo `error` (ex.: `"error": "Error 0"`) no lugar de `expected`.
- `estatistica-vectors.json` — 27 vetores cobrindo as 7 teclas estatísticas (`Σ+`, `Σ-`, `g x̄`, `g s`, `g ŷ,r`, `g x̂,r`, `g x̄w`), `f CLEAR Σ` e cenários colaterais (LASTx pós-`ŷ,r`, colisão `STO 3` com Σx²). Schema **stateful** novamente adaptado — `stack`/`operation` viram `operations: [...]` (sequência de teclas aplicadas em ordem) + `query: "display"` indicando que se compara o estado pós-última-operação. Cada `sigma_plus`/`sigma_minus` traz `{y, x}` explícitos respeitando a convenção do manual p. 81 (`y ENTER x Σ+`); `mean`/`stddev`/`weighted_mean` são sem-argumento; `y_hat_r`/`x_hat_r` recebem `input` (novo valor da variável). Vetores de saída dupla usam `expected_x` **e** `expected_y` (estatísticas escrevem X e Y simultaneamente — comportamento arquitetural novo). 20 vetores de caminho feliz + 7 vetores de erro (todos Error 2, conforme Apêndice D p. 194). Distribuição de fontes: 12 do manual Seção 6, 15 de derivação/cenários simétricos.
- `calendario-vectors.json` — 15 vetores cobrindo as 4 teclas de calendário (`g M.DY`, `g D.MY`, `g DATE`, `g ΔDYS`). Mantém o schema stateful (`operations: [...]` + `query`) já validado em estatística, com novo vocabulário de operações (`set_mdy`, `set_dmy`, `push_value`, `enter`, `chs`, `dyse`, `date`, `swap_xy`, `clear_fin`). Datas codificadas como string em `push_value.value` para preservar zeros à direita (ex.: `"6.032004"`). 10 vetores de caminho feliz cobrindo: ΔDYS exato e comercial em ambos formatos, x⇆y duplo (idempotência), DATE forward e backward (`CHS`), DATE com `n=0` (só dia da semana), 29-fev em ano bissexto, persistência de `dateFormat` através de `f CLEAR FIN`. 4 vetores de erro (todos Erro 8, Apêndice D p. 195-196): mês inválido, 29-fev em não-bissexto, overflow futuro (>4046), underflow passado (<1582). 1 vetor **vermelho esperado** (`cal-delta-mdy-erratum`): reproduz o input típo do livro (`10.152005`) e demonstra que a engine entrega o resultado matematicamente correto (499) — não o número errôneo do manual (498). Equivalente conceitual aos 3 vermelhos esperados do bloco de estatística. Para resultados de `g DATE`, o `format` é `"DATE"` e o `expected_x` inclui o dígito de dia da semana (ex.: `"11,09,2004 6"` = 11 set 2004, sábado em D.MY mode).
- `cashflow-vectors.json` — 17 vetores cobrindo as 5 teclas de fluxo de caixa (`g CFo`, `g CFj`, `g Nj`, `f NPV`, `f IRR`). Schema stateful idêntico ao do bloco anterior, com vocabulário novo de operações (`clear_reg`, `cfo {value}`, `cfj {value}`, `nj {value}`, `i {value}`, `n {value}`, `npv`, `irr`, `swap_xy`, `rcl_pv`, `rcl_i`, `rcl_n`, `rcl_mem {register}`). Distribuição: 11 caminho-feliz + 6 erros. Vetores-bandeira do bloco: `cf-npv-001` (manual p. 61, NPV=212.18, todos Nj=1), `cf-npv-002-grouped` (manual p. 64, NPV=907.77 com Nⱼ=3 e Nⱼ=2 — testa agrupamento), `cf-irr-001` (mesmo dataset, IRR=13.72% — testa Newton-Raphson), `cf-irr-simple-2-flows` (caso fechado algebraicamente, IRR=10%), `cf-npv-stores-pv` e `cf-irr-stores-i` (validam pós-condições de armazenamento automático em PV/i). 6 erros distribuídos: 2× Erro 7 (sem mudança de sinal positivo/negativo), 4× Erro 6 (Nj > 99, Nj < 0, Nj não inteiro, Nj sem CFj antes — todos vão **diretamente conflitar com `Hp12cError.kt` da Fase 1** que mapeia tudo para Erro 7 — força correção no passo 16; ver §10.4 da formula doc). Validação Python das fórmulas: 3 dos 4 casos numéricos canônicos (NPV ex.1=212.18, NPV ex.2=907.77, IRR ex.2=13.72%, IRR simples=10%) validados pré-commit.

Schema de cada vetor **TVM** (obrigatório):

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

Schema de cada vetor **transcendental/percentagem**:

```json
{
  "id": "trans-pct-001",
  "source": "manual, Seção 2, p. 27",
  "description": "14% de 300.",
  "stack": ["300", "14"],
  "operation": "percent",
  "expected": "42.00",
  "format": "FIX 2"
}
```

No `stack`, o último elemento é o X (visor), os anteriores são Y/Z/T empilhados nessa ordem. Para vetores binários como `y^x` o array tem 2 entradas; para unários, 1. Vetores de erro substituem `expected` por `error: "Error N"`.

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
