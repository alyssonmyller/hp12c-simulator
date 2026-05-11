# Funções de calendário (`g M.DY`, `g D.MY`, `g DATE`, `g ΔDYS`)

> Fontes primárias:
> - `bpia5314.pdf` (manual oficial HP 12C Platinum), **Seção 2 "Funções de percentagem e calendário"**, sub-seção "Funções de calendário", p. 30-33.
> - **Apêndice D "Condições de erro"**, p. 195-196 (Erro 8: Calendário).
> - **Apêndice E "Fórmulas usadas"**, sub-seção "Calendário", p. 200.

Este é o terceiro arquivo de fórmulas da skill, depois de `tvm.md`, `transcendentais.md` e `estatistica.md`. Cobre as **4 teclas de calendário** da HP12C — bloco menor que os anteriores em superfície de teclado, mas com complexidade própria por dois motivos: (1) representação de datas como número decimal `mm.ddyyyy` ou `dd.mmyyyy` (sintaticamente "fora do eixo" da pilha numérica usual), (2) duas bases de dias coexistindo simultaneamente (`exato` no visor + `30/360` em Y após `ΔDYS`).

As 4 teclas:

| Tecla         | Combinação    | Função                                                                  |
|---------------|---------------|-------------------------------------------------------------------------|
| `M.DY`        | `g M.DY`      | Define formato de data como mês-dia-ano (default da memória contínua).  |
| `D.MY`        | `g D.MY`      | Define formato de data como dia-mês-ano. Acende indicador `D.MY`.       |
| `DATE`        | `g DATE`      | Calcula data futura/passada: dado `DT₁` em Y e `n` dias em X, devolve `DT₂` em X com dia da semana. |
| `ΔDYS`        | `g ΔDYS`      | Calcula dias entre `DT₁` (Y) e `DT₂` (X) — exato em X, comercial em Y. |

Nenhuma das 4 toca registradores financeiros (`n`, `i`, `PV`, `PMT`, `FV`) nem memórias do usuário (`R0..R9`, `Ri`). Mexem apenas em pilha (X, Y, Z, T, LASTx) e — no caso de `M.DY`/`D.MY` — num **flag persistente** novo: `dateFormat: DateFormat`.

## 1. Convenções e notação

Datas válidas: **15 outubro 1582 ≤ DT ≤ 25 novembro 4046** (manual p. 30). Fora desse intervalo, qualquer operação de calendário dispara **Erro 8**.

A representação numérica de uma data segue um dos dois formatos:

- **`M.DY`** (mês-dia-ano): número decimal `mm.ddyyyy` — parte inteira é o mês (1-2 dígitos), depois ponto decimal, dois dígitos do dia, quatro dígitos do ano.
- **`D.MY`** (dia-mês-ano): número decimal `dd.mmyyyy` — parte inteira é o dia (1-2 dígitos), depois ponto decimal, dois dígitos do mês, quatro dígitos do ano.

Exemplos canônicos do manual (p. 31), 7 de abril de 2004:

| Formato ativo | Tecla digitada | Visor      |
|---------------|----------------|------------|
| `M.DY`        | `4.072004`     | `4,072004` |
| `D.MY`        | `7.042004`     | `7,042004` |

A regra de leitura é estritamente posicional — não há detecção heurística. Em `M.DY`, o dia precisa ter **dois** dígitos (escrever `4.72004` para 7 de abril dá `0.72004`, que seria interpretado como mês 0 e dispararia Erro 8).

A engine carrega `dateFormat: DateFormat = DateFormat.M_DY` em `CalculatorState`, com persistência via memória contínua. `f CLEAR REG`, `f CLEAR FIN`, etc. **não** alteram esse flag — só a re-inicialização da memória contínua o devolve a `M_DY` (manual p. 31, "Memória Contínua").

Ao longo deste documento, `DT₁`/`DT₂` denotam datas (objeto `(mm, dd, yyyy)` decodificado), e `f(DT)` denota a função de contagem de dias do Apêndice E p. 200, definida abaixo.

## 2. Decodificação `string-numérico → (mm, dd, yyyy)`

Algoritmo na engine:

1. Receber `x` da pilha como `Hp12cDecimal` (ex.: `4.072004`).
2. Separar parte inteira `int(x)` e parte fracionária `frac(x)`.
3. Conforme `dateFormat`:
   - `M.DY`: `mm = int(x)`, `dd = int(frac(x) · 100)`, `yyyy = int(frac(x) · 10⁶) mod 10⁴`.
   - `D.MY`: `dd = int(x)`, `mm = int(frac(x) · 100)`, `yyyy = int(frac(x) · 10⁶) mod 10⁴`.
4. Validar: `1 ≤ mm ≤ 12`; `1 ≤ dd ≤ daysInMonth(mm, yyyy)`; `1582 ≤ yyyy ≤ 4046` (com refinamento para o limite `15 oct 1582` e `25 nov 4046`).
5. Falha em qualquer validação → **Erro 8** ("A data está no formato errado ou não existe", manual p. 195).

`daysInMonth` usa a regra de bissexto **gregoriana** (yyyy divisível por 4, exceto seculares não divisíveis por 400). Isso é **diferente** da fórmula `f(DT)` do Apêndice E, que usa `INT(z/4)` puro (regra juliana proléptica). Ver §6 abaixo — a divergência é intencional e calibrada para que `ΔDYS` produza diferenças corretas em datas modernas (pós-1900) apesar de `f` não ser um Julian Day "verdadeiro".

## 3. Fórmula `f(DT)` — base de dias exatos (Apêndice E p. 200)

Fórmula canônica:

```
f(DT) = 365 · yyyy + 31 · (mm − 1) + dd + INT(z / 4) − x
```

onde `INT(·)` é parte inteira (truncar em direção a zero) e:

- Se `mm ≤ 2`:    `x = 0`,                          `z = yyyy − 1`
- Se `mm > 2`:    `x = INT(0.4 · mm + 2.3)`,        `z = yyyy`

A "constante mágica" `INT(0.4 · mm + 2.3)` é uma tabela compacta para o offset de dias acumulados de meses anteriores, calibrada para que `f(DT)` seja monotônica e produza `f(DT₂) − f(DT₁) = (dias entre as datas)` para datas modernas:

| mm  | 0.4·mm + 2.3 | INT  | dias dos meses anteriores − 31·(mm−1) |
|-----|--------------|------|---------------------------------------|
| 3   | 3.5          | 3    | 59 − 62 = −3                          |
| 4   | 3.9          | 3    | 90 − 93 = −3                          |
| 5   | 4.3          | 4    | 120 − 124 = −4                        |
| 6   | 4.7          | 4    | 151 − 155 = −4                        |
| 7   | 5.1          | 5    | 181 − 186 = −5                        |
| 8   | 5.5          | 5    | 212 − 217 = −5                        |
| 9   | 5.9          | 5    | 243 − 248 = −5                        |
| 10  | 6.3          | 6    | 273 − 279 = −6                        |
| 11  | 6.7          | 6    | 304 − 310 = −6                        |
| 12  | 7.1          | 7    | 334 − 341 = −7                        |

Os meses 1 e 2 são tratados separadamente (`x = 0`) e o ajuste de leap year sai pelo `z = yyyy − 1` (a soma `INT(z/4)` então conta o ano corrente como "não bissexto" em jan/fev, decisão correta porque o dia 29-fev ainda não passou).

**Validação numérica** (manual p. 32):
- `f(14 maio 2004) = 365·2004 + 31·4 + 14 + INT(2004/4) − 4 = 732095`
- `f(11 set 2004) = 365·2004 + 31·8 + 11 + INT(2004/4) − 5 = 732215`
- `ΔDYS = 732215 − 732095 = 120` ✓

## 4. Fórmula `DIAS` — base 30/360 (ano comercial, Apêndice E p. 200)

```
DIAS = f₃₀(DT₂) − f₃₀(DT₁)
f₃₀(DT) = 360 · yyyy + 30 · mm + z
```

onde `z` depende do papel da data (anterior `DT₁` ou posterior `DT₂`) e do dia do mês:

**Para `f₃₀(DT₁)` (data anterior):**
- Se `dd₁ = 31`:   `z = 30`
- Senão:          `z = dd₁`

**Para `f₃₀(DT₂)` (data posterior):**
- Se `dd₂ = 31` e `dd₁ ∈ {30, 31}`:   `z = 30`
- Se `dd₂ = 31` e `dd₁ < 30`:         `z = dd₂` (ou seja, 31)
- Se `dd₂ < 31`:                      `z = dd₂`

A assimetria entre as duas regras é a **convenção 30E/360 ISDA-like com tratamento especial de fim-de-mês**: o dia 31 do mês anterior é "encolhido" para 30 (porque o mês comercial tem 30 dias), mas o dia 31 do mês posterior só é encolhido se a data anterior também era fim-de-mês — caso contrário, vale literalmente como 31, "estendendo" o intervalo.

Fevereiro recebe tratamento implícito: o "mês comercial" tem 30 dias mesmo em fevereiro, então a base 30/360 nunca encurta fevereiro nem distingue ano bissexto. Isso é fonte recorrente de discrepância de ~5 dias por ano em juros simples comerciais vs exatos.

**Validação numérica** (manual p. 33, com correção de errata em §7.1):
- Exemplo: 3 jun 2004 a 14 out 2005 (manual originalmente imprime "10.152005" como tecla, mas o resultado `498` corresponde a 14 out — ver §7.1).
- `f(3 jun 2004) = 732115`,  `f(14 out 2005) = 732613` → ΔDYS = `498` ✓
- `f₃₀(3 jun 2004) = 360·2004 + 30·6 + 3 = 721623`
- `f₃₀(14 out 2005) = 360·2005 + 30·10 + 14 = 722114` → DIAS = `491` ✓

## 5. Tecla `DATE` — data futura ou passada

Procedimento (manual p. 31):

1. Digitar `DT₁` e apertar `ENTER` (`DT₁` agora em Y).
2. Digitar `n` (número de dias). Se `DT₂` está no passado, apertar `CHS`.
3. Apertar `g DATE`.

Resultado em X: `DT₂ = DT₁ + n dias`, **em formato especial** que codifica também o dia da semana. O visor mostra:

```
dd,mm,yyyy d
```

— meses, dias e ano sempre separados por vírgulas (independente do separador de milhares ativo via `f .`); o último dígito (após espaço) é o dia da semana, com convenção HP `1 = segunda-feira ... 7 = domingo`. Esse formato existe **só para a saída de `DATE`** — não é a representação de pilha. Internamente o `x` carrega um número que decodificado pelo formato `dateFormat` ativo dá `(mm, dd, yyyy)` consistente.

**Algoritmo da engine:**

1. Decodificar `DT₁` em Y conforme `dateFormat` (validar; falha → Erro 8).
2. Ler `n` em X (deve ser inteiro; **caso `n` não inteiro**: a HP física aceita e trunca pela parte inteira — ver §7.2).
3. Calcular `f(DT₂) = f(DT₁) + n`.
4. Inverter `f`: encontrar `(mm₂, dd₂, yyyy₂)` tal que `f(mm₂, dd₂, yyyy₂) = f(DT₂)`.
5. Validar limite: se `DT₂ < 15 oct 1582` ou `DT₂ > 25 nov 4046` → Erro 8 ("Quando se tenta adicionar dias além da capacidade de datas da calculadora", manual p. 195).
6. Calcular dia da semana: `dow(DT₂) = ((f(DT₂) + 5) mod 7); se dow == 0, dow = 7`.
7. Comportamento da pilha (saída dupla peculiar): X recebe a data + dia da semana codificada; Y/Z/T preservados; LASTx ← x antigo (n).

**Inversão de `f` na prática.** Como `f` é monotônica crescente, qualquer um dos seguintes funciona:

- **Estimativa + ajuste**: `yyyy ≈ INT(target / 365.25) + offset`; iterar mm/dd dentro do ano. Tipicamente 1-2 iterações.
- **Algoritmo de calendário "real"**: converter `DT₁` para Julian Day verdadeiro, somar `n`, converter de volta — funciona, mas então `DT₂` pode discordar de `f` em ~1 dia ao redor de séculos não-divisíveis-por-400 (ver §6).

A engine **deve** usar a inversão da própria `f` da HP (não Julian Day verdadeiro), porque `f` é a referência. A consequência prática: dias da semana de datas anteriores a 1900 podem divergir do calendário gregoriano histórico — comportamento documentado e idêntico à HP física (manual p. 32, nota de rodapé 4).

**Validação numérica** (manual p. 32):
- `14.052004 ENTER 120 g DATE` → `11,09,2004 6` (11 set 2004, sábado).
- `f(14 mai 2004) + 120 = 732215` → inverter para `(9, 11, 2004)` ✓
- `dow = ((732215 + 5) mod 7) = 1`; ajuste `1 → 1` (segunda? não): `(732215 mod 7 = 1) + 5 = 6` (sábado). ✓

## 6. Dia da semana (`dow`)

Dedução baseada em validação contra os exemplos do manual:

```
dow(DT) = ((f(DT) + 5) mod 7)
se dow == 0:  dow = 7
```

Convenção HP (manual p. 32): `1 = segunda-feira, 2 = terça, ..., 6 = sábado, 7 = domingo`.

**Validação contra exemplo canônico**: 11 set 2004 era um sábado. `f = 732215; (732215 + 5) mod 7 = 732220 mod 7 = 6` ✓.

**Limitação documentada (manual p. 32 nota 4)**: `f(DT)` da HP usa `INT(z/4)` "Juliano proléptico" — não distingue anos seculares não-divisíveis-por-400 (1700, 1800, 1900) do regime gregoriano. Para datas pós-1900, `dow` coincide com o calendário civil padrão; para datas anteriores, a HP entrega o `dow` da extrapolação juliana, que pode diferir do registro histórico (especialmente datas anteriores à adoção gregoriana em cada país — Inglaterra: 14 set 1752; outros: variável). A engine reproduz literalmente esse comportamento.

## 7. Ambiguidades e idiossincrasias

### 7.1 Errata do exemplo da p. 33 do manual

O exemplo da seção "Número de dias entre datas" (p. 33) afirma textualmente "3 de junho de 2004 a 14 de outubro de 2005" e reporta resultados `498,00` (exato) e `491,00` (comercial). Mas a tecla mostrada é `10.152005 g ΔDYS` — que em formato M.DY é `15 outubro 2005`, não 14. Verificação:

- Com `dd₂ = 15`: `f(15 out 2005) − f(3 jun 2004) = 732614 − 732115 = 499`; comercial = `492`.
- Com `dd₂ = 14`: `f(14 out 2005) − f(3 jun 2004) = 732613 − 732115 = 498`; comercial = `491`.

O resultado `498 / 491` corresponde a **Oct 14**, não Oct 15. A keystroke impressa é typo do manual (provavelmente devia ser `10.142005`). Os vetores de teste em `test-vectors/calendario-vectors.json` registram **as duas variações** (14 e 15) e identificam claramente qual confirma o resultado do livro — o de Oct 14.

### 7.2 Argumento `n` não inteiro em `DATE`

O manual não documenta o que acontece se `n` (número de dias) for fracionário. A HP física **trunca** silenciosamente (i.e., usa `INT(n)`). A engine reproduz: o reducer chama `n.intValue()` antes do cálculo; nenhum erro é disparado. Isso é consistente com `[g][INT]` aplicado ao argumento.

`n` negativo: o manual indica explicitamente "se a outra data estiver no passado, aperte CHS" — então `n < 0` é input legítimo. A engine deve aceitar e calcular `DT₂ = DT₁ + n` com aritmética assinada normal.

### 7.3 Pilha após `ΔDYS` — cohabitação exato/comercial

Após `g ΔDYS`, X tem dias exatos e **Y tem dias comerciais (30/360)**. O usuário acessa o comercial via `x⇆y`. Pressionar `x⇆y` de novo restaura X (manual p. 32).

Isso é uma **saída dupla** análoga à de `g x̄`/`g s` (estatística), mas com dois valores **diferentes** em X e Y — não a mesma operação devolvendo dois aspectos correlatos. Z/T são preservados sticky. LASTx recebe o `dd.mmyyyy` (ou `mm.ddyyyy`) consumido.

Pergunta de design: o flag `rInvalid` do `StatisticalState` foi necessário para `x̂/ŷ`. **Aqui não há equivalente** — ambas as bases (exato e comercial) são sempre computáveis dadas duas datas válidas, sem casos patológicos. Logo nenhum flag novo precisa ser adicionado a `CalculatorState`.

### 7.4 `f CLEAR FIN` e `f CLEAR REG` não tocam o `dateFormat`

Como mencionado na §1, somente a re-inicialização da memória contínua devolve `dateFormat` para `M_DY`. As 4 teclas `CLEAR` (`f CLEAR FIN`, `f CLEAR REG`, `f CLEAR Σ`, `f CLEAR PRGM`) preservam o flag — é estado de configuração do usuário, não memória de cálculo.

### 7.5 Limite superior `25 nov 4046`

O limite superior é estranho — não é nenhum aniversário óbvio. Vem da combinação de: (a) range de `f(DT)` cabendo em mantissa BCD de 10 dígitos sem overflow; (b) cálculo `f(DT) + n` com `n` máximo razoável também cabendo. O limite inferior `15 out 1582` é histórico (adoção do calendário gregoriano).

A engine valida ambos os limites antes de qualquer cálculo, inclusive na inversão de `DATE` — se a inversão produzir um ano `> 4046` ou data `> 25 nov 4046`, dispara Erro 8 mesmo que `DT₁` e `n` fossem válidos isoladamente.

### 7.6 Diferenças entre `M.DY` e `D.MY` durante a digitação

Trocar `dateFormat` **não** "re-interpreta" um número já digitado em pilha. Apenas afeta: (a) parsing futuro; (b) renderização de saídas via `DATE`. Conseqüência prática: se o usuário digita `7.042004` em modo `M.DY` (= mês 7 dia 04), depois aperta `g D.MY`, o número `7.042004` em X é o mesmo, mas se ele agora apertar `g DATE` para uma operação, será decodificado como `D.MY` → dia 7 mês 04 ano 2004 = mesma data por coincidência. Trocar formato no meio de uma sessão é caminho seguro para erro do usuário — engine não trata como inválido, só decodifica o que está em pilha conforme o flag corrente.

## 8. Resumo das condições de erro (Erro 8)

Coletadas do Apêndice D p. 195-196:

| Operação        | Condição                                                        | Código    |
|-----------------|-----------------------------------------------------------------|-----------|
| `ΔDYS`, `DATE`  | "A data está no formato errado ou não existe."                  | Error 8   |
| `DATE`          | "Quando se tenta adicionar dias além da capacidade da calc."    | Error 8   |

A primeira condição cobre:
- `mm ∉ [1, 12]`
- `dd ∉ [1, daysInMonth(mm, yyyy)]` (incluindo 29-fev em ano não bissexto)
- `yyyy ∉ [1582, 4046]`
- Caso fronteira: `15 out 1582` é a primeira data válida, `25 nov 4046` a última.

A segunda condição cobre apenas `DATE`: se `f(DT₁) + n` cair fora do range válido após a inversão.

## 9. Comportamento da pilha (resumo)

Detalhes completos em `referencias/stack-behavior.md`. Resumo das 4 teclas:

| Tecla     | Efeito na pilha                                                                                    |
|-----------|----------------------------------------------------------------------------------------------------|
| `M.DY`    | Pilha intacta. Atualiza `dateFormat` no estado.                                                    |
| `D.MY`    | Pilha intacta. Atualiza `dateFormat` no estado.                                                    |
| `DATE`    | X ← (data com dia da semana embutido), Y/Z/T preservados, LASTx ← x antigo (n).                    |
| `ΔDYS`    | X ← dias exatos, Y ← dias comerciais (30/360), Z/T preservados sticky, LASTx ← x antigo (DT₂).     |

`ΔDYS` é a **segunda** tecla "saída dupla simultânea X+Y" da engine após o bloco de estatísticas (`Mean`, `StdDev`, `PredictY`, `PredictX`). O padrão de implementação é o mesmo registrado em `formulas/estatistica.md` §7: `state.copy(stack = stack.copy(x = exato, y = comercial))` direto no reducer, sem primitiva nova em `StackOps.kt`.

`M.DY` e `D.MY` são as **primeiras** teclas da engine que mexem em flag persistente no `CalculatorState` sem tocar pilha — comportamento análogo ao de `STO EEX` (`ToggleCompoundFractionFlag`), só que com um flag enumerado (`DateFormat`) em vez de booleano.

## 10. Decisões arquiteturais resumidas

1. **`dateFormat` como enum em `CalculatorState`**: novo campo `val dateFormat: DateFormat = DateFormat.M_DY`. Sealed class `DateFormat` com objects `M_DY` e `D_MY` — permite `when` exaustivo no decodificador. Default `M_DY` consistente com manual p. 31 ("Se a Memória Contínua for reinicializada, o formato de data será configurado para mês-dia-ano.").

2. **Sem novo `Hp12cError`**: a Fase 1 já tem `Hp12cError.InvalidDate` e `Hp12cError.DateOutOfRange`, ambos `(8, ...)`. Reusamos os dois objects existentes — `InvalidDate` para falhas de validação de formato/existência, `DateOutOfRange` para overflow do limite na inversão de `DATE`.

3. **Inversão de `f`**: implementação iterativa (estima ano, ajusta mês, ajusta dia) será o caminho na fase de implementação; performance trivial (≤10 operações aritméticas por chamada).

4. **`f(DT)` em `BigDecimal`**: a fórmula tem multiplicações por `yyyy ≤ 4046`, somas pequenas — cabe em `Long`, mas mantemos `Hp12cDecimal` para consistência com a engine. MC(10, HALF_EVEN) é mais que suficiente — números nunca passam de ~10⁶.

5. **Sem novo flag transitório em `CalculatorState`**: ao contrário de `statisticalState.rInvalid`, o calendário não tem ambiguidade dependente de operação anterior. Pilha é suficiente para carregar o resultado dual de `ΔDYS` (X e Y).
