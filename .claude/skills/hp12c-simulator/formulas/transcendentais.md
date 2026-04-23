# Funções matemáticas, alteração de números e percentagens

> Fontes primárias:
> - `bpia5314.pdf` (manual oficial HP 12C Platinum), **Seção 7 "Funções matemáticas e de alteração de números"**, p. 85-87, e **Seção 2 "Funções de percentagem e calendário"**, p. 27-29.
> - Apêndice D "Condições de erro", p. 193-196 (Erro 0 e Erro 5).
> - Apêndice E "Fórmulas usadas", p. 197 (percentagens) e p. 205 (fatorial).

Este é o segundo arquivo de fórmulas da skill, depois de `tvm.md`. Cobre **todas as funções não-financeiras** que vivem no teclado — as que o manual organiza em duas famílias:

- **Funções de percentagem** (`%`, `Δ%`, `%T`) — 3 teclas amarelas acima de `+/−`, são "aritmética de conta de mercado" que a HP trata como funções financeiras pela razão histórica de manterem `y` na pilha (permitindo `net price` em dois toques). Manual, Seção 2.
- **Funções matemáticas e de alteração de números** (`√x`, `1/x`, `x²`, `LN`, `e^x`, `n!`, `y^x`, `INT`, `FRAC`, `RND`) — 10 teclas cobrindo desde álgebra elementar até manipulação explícita da parte inteira/fracionária de um número. Manual, Seção 7.

Todas compartilham dois traços que importam para a engine: **nenhuma muda registrador financeiro** (apenas X/Y/LASTx) e **todas são determinísticas em forma fechada** (nada de iteração numérica aqui — ao contrário de `Solve.I` do TVM). Isso torna o bloco barato de implementar e trivial de testar: cada função tem um input canônico e um output canônico, sem tolerância de convergência.

## 1. Convenções e notação

Ao longo deste documento, `x` é o valor no registrador X da pilha (o visor), `y` é o valor no registrador Y, `LASTx` é o X anterior à operação. A pilha da HP12C tem 4 níveis (X, Y, Z, T) e um registrador auxiliar LASTx; a semântica de cada operação sobre a pilha está documentada em `referencias/stack-behavior.md`. Aqui descrevemos apenas **o que cada função computa**; como a pilha desce/promove é responsabilidade da camada de reducer e está catalogada lá.

**Arredondamento interno.** Toda função abaixo opera sobre `Hp12cDecimal` com `MathContext(10, HALF_EVEN)` — o invariante #1 da skill. Isso vale inclusive para `√x`, `LN`, `e^x` e `y^x`, que em linguagens de programação modernas normalmente seriam `Double.sqrt/ln/exp/pow`. A impl correta é via `Hp12cDecimal.ln/exp/pow` (já presentes desde o passo 5, em `jvmCommonMain`), que fazem a conta em BigDecimal com arredondamento controlado. Double introduziria ruído de 1-2 ULP na última casa e quebraria vetores canônicos do manual.

## 2. Funções de percentagem (`%`, `Δ%`, `%T`)

### 2.1 `%` — Percentagem de um valor base

```
result = y · x / 100
```

Ref: manual, Apêndice E, p. 197 ("`% = Base(y) × Taxa(x) / 100`"); exemplo na Seção 2, p. 27.

**Exemplo canônico** (manual p. 27): `300 ENTER 14 %` → `42.00` (14% de 300).

**Comportamento da pilha** (idiossincrasia importante): a tecla `%` **não consome `y`**. Após `%`, o novo X é `y · x / 100` mas Y continua com o valor base original. Isso permite calcular valor líquido em dois toques:

```
13250 ENTER 8 % −       ;  X = 13250 − (13250 × 0.08) = 12190.00
```

Esse behavior é o motivo de `%` estar na categoria "funções financeiras" do Apêndice A do manual (p. 181) e não junto às aritméticas puras. A implementação precisa refletir isso — após consumir x (a taxa), retém y (a base) e empurra o resultado em X.

### 2.2 `Δ%` — Diferença percentual

```
result = 100 · (x − y) / y
```

Ref: manual, Apêndice E, p. 197; exemplo na Seção 2, p. 28.

**Exemplo canônico** (manual p. 29): `58.5 ENTER 53.25 Δ%` → `-8.97` (queda de ~9%).

**Sinal importa**: resposta positiva = aumento; negativa = redução. A função **não retém** `y` (ambos são consumidos, como aritmética binária normal).

**Caso de erro**: `y = 0` dispararia divisão por zero, mas o manual **não lista `Δ%` entre as operações que disparam Erro 0** (Apêndice D, p. 193). Comportamento observado na HP física: `y = 0` dá Error 0 na prática (é divisão por zero matematicamente), mas a documentação é silenciosa. Tratamos como Error 0 por consistência com `÷` (mesma condição `x=0`, só que com os papéis trocados).

### 2.3 `%T` — Percentagem do total

```
result = 100 · x / y
```

Ref: manual, Apêndice E, p. 197 ("`%T = 100 · [Valor(x) / Total(y)]`"); exemplo na Seção 2, p. 29.

**Exemplo canônico** (manual p. 29): somar as vendas regionais para o total (`3.92 + 2.36 + 1.67 = 7.95`), depois `2.36 %T` → `29.69` (Europa = 29,69% do total).

**Como `%`, mantém `y` na pilha** — permite repetir o cálculo com CLx + novo valor + %T para outras fatias do total. Essa é a função "irmã" de `%` para proporções.

**Caso de erro**: `y = 0` → Error 0 (divisão por zero, silêncio do manual análogo ao `Δ%`).

## 3. Funções matemáticas de um número

Todas consomem X e produzem resultado em X; Y/Z/T não se movem; LASTx guarda o X pré-operação (exceto `RND` — ver 3.7). Ref: manual Seção 7, p. 85-86.

### 3.1 `1/x` — Recíproco

```
result = 1 / x
```

**Domínio**: `x ≠ 0`. `x = 0` → Error 0 (Apêndice D, p. 193).

**Exemplo canônico** (manual p. 86): `.258` `1/x` → `3.88`.

### 3.2 `x²` — Quadrado

```
result = x · x
```

**Domínio**: universal (não há erro — `x² ≥ 0` sempre).

**Nota**: o manual p. 85 lista `x²` (acessado via `[g][x²]`) na Seção 7 como "função de um número", mas ele **não aparece na Seção 2/3/4** e nem tem exemplo canônico numérico no manual. Exemplo trivial: `5` `x²` → `25.00`.

### 3.3 `√x` — Raiz quadrada

```
result = √x
```

**Domínio**: `x ≥ 0`. `x < 0` → Error 0 (Apêndice D, p. 193).

**Impl**: via `Hp12cDecimal.sqrt` ou `pow(0.5)`. Na JVM base, `BigDecimal.sqrt(MathContext)` é a via canônica desde Java 9. Cuidado com `sqrt(2) ≈ 1.414213562` em FIX 9 — `Hp12cDecimal.pow(0.5)` **não** é equivalente numericamente a `sqrt` para o MC de 10 dígitos, porque `0.5` entra na `pow` genérica (via `exp(0.5 · ln(x))`) com propagação de erro. **Use `BigDecimal.sqrt` direto**.

### 3.4 `LN` — Logaritmo natural

```
result = ln(x)     (base e ≈ 2.718281828)
```

**Domínio**: `x > 0`. `x ≤ 0` → Error 0 (Apêndice D, p. 193).

**Impl**: `Hp12cDecimal.ln` já existe desde o passo 5 (usa série de Taylor adaptada + reduction via sqrt/square, conforme `referencias/bcd-rounding.md`).

**Nota sobre logaritmo comum (base 10)**: a HP12C **não tem tecla dedicada**. O manual (p. 85) instrui o procedimento manual: `x LN 10 LN ÷` = `ln(x) / ln(10) = log₁₀(x)`. A engine não precisa oferecer atalho — o usuário digita a sequência.

### 3.5 `e^x` — Exponencial natural

```
result = e^x       (e ≈ 2.718281828)
```

**Domínio**: universal na teoria. Na prática, `x > 230.2585` estoura o limite de `9.999999999 × 10^99` e dispara **Erro 1** (estouro do registro de armazenamento, Apêndice D, p. 194) — não Erro 0.

**Impl**: `Hp12cDecimal.exp` (passo 5). A reduction padrão `e^x = e^(INT(x)) · e^(FRAC(x))` evita overflow prematuro; o teste de estouro é responsabilidade do reducer antes de entregar o valor na pilha.

### 3.6 `n!` — Fatorial

```
0! = 1
n! = 1 · 2 · 3 · ... · n        para n inteiro, n ≥ 1
```

Ref: manual, Apêndice E, p. 205.

**Domínio**: `x ∈ {0, 1, 2, ...}` (inteiros não-negativos).

**Condição de erro — idiossincrasia histórica**: o manual lista `n!` sob **Erro 5 (Juros compostos)** na p. 195, não sob Erro 0. As condições:

- `x ≤ 0` → Error 5 (exceto `x = 0`, que é válido por `0! = 1` — ver abaixo)
- `x não inteiro` → Error 5

A leitura literal da tabela do Apêndice D fala "`x ≤ 0`", mas isso entra em conflito com a fórmula "`0! = 1`" do Apêndice E. A HP física, testada por reports de fóruns, **aceita `x = 0` e devolve `1`**; só `x < 0` ou `x` fracionário disparam Error 5. Tratamos essa como **ambiguidade documentada**: engine aceita `0! = 1` (segue o Apêndice E que é a definição) e reporta `Error 5` para `x < 0` ou `x ∉ ℤ`. Entrada em `referencias/bcd-rounding.md` no catálogo de ambiguidades.

**Overflow**: `69! ≈ 1.71 × 10^98` cabe. `70! ≈ 1.20 × 10^100` estoura → **Erro 1** (Apêndice D p. 194), não Erro 5.

**Impl**: loop trivial (`fold(1) { acc, k -> acc.multiply(k) }`), sem recursão para evitar stack depth desnecessária. Manter MC de 10 dígitos — fatoriais grandes passam a ter truncamento na parte fracionária (isso é esperado e não é bug).

### 3.7 `RND` — Arredondar

```
result = arredondar(x, display_format.places)
```

A tecla `RND` (via `[f][RND]`) **materializa** o arredondamento visível na memória — altera o valor guardado em X para coincidir com o que o display mostra naquele formato. Ref: manual p. 85.

**Exemplo canônico** (manual p. 86): o valor `3.875968992` em FIX 2 mostra `3.88`. Antes de `RND`, `CLEAR PREFIX` revela `3875968992` (10 dígitos internos). Após `[f][RND]`, `CLEAR PREFIX` revela `3880000000` — o arredondamento foi "impresso" no registrador.

**Comportamento da pilha**: LASTx **não guarda** o x pré-RND (é o único caso excepcional entre as funções de um número — ver manual p. 86 que explicita que LSTx devolveria o original se fosse possível, mas na RND não há "original" a recuperar, pois a intenção é que o valor arredondado se torne o novo x). Isso é fonte recorrente de confusão; vale teste explícito.

**Depende** do formato de display atual (`DisplayFormat.Fix(n)`, `Sci(n)` ou `Eng(n)`). Em SCI/ENG, o arredondamento é sobre a mantissa normalizada.

### 3.8 `INT` — Parte inteira

```
result = x >= 0 ? floor(x) : ceil(x)      ; zera dígitos à direita do decimal
```

Ref: manual p. 85.

Sintaxe matemática: trunca em direção a zero. `INT(3.88) = 3`, `INT(-3.88) = -3`.

**Comportamento da pilha**: altera X e memória, **LASTx guarda o original** — permite `g LSTx` para recuperar.

**Domínio**: universal.

### 3.9 `FRAC` — Parte fracionária

```
result = x − INT(x)                     ; zera dígitos à esquerda do decimal
```

Ref: manual p. 86.

**Exemplo canônico** (manual p. 86): se x = 3.88, após `g INT` X vira 3.00; após `g LSTx` volta para 3.88; após `g FRAC` vira 0.88.

**Sinais**: `FRAC(-3.88) = -0.88` (mantém o sinal de x, consistente com `x = INT(x) + FRAC(x)`).

**Comportamento da pilha**: análogo a INT — altera X, LASTx guarda original.

## 4. Função de potenciação `y^x`

Ref: manual Seção 7, p. 87.

```
result = y^x
```

Consome `y` e `x` (operação binária normal): após a operação, Z promove para Y, T permanece em T (pilha desce uma posição), LASTx guarda o x consumido.

**Exemplos canônicos** (manual p. 87, todos em FIX 2):

| Cálculo       | Teclas                     | Resultado |
|---------------|----------------------------|-----------|
| `2^1.4`       | `2 ENTER 1.4 y^x`          | `2.64`    |
| `2^(-1.4)`    | `2 ENTER 1.4 CHS y^x`      | `0.38`    |
| `(-2)^3`      | `2 CHS ENTER 3 y^x`        | `-8.00`   |
| `2^(1/3) = ∛2`| `2 ENTER 3 1/x y^x`        | `1.26`    |

**Domínio e erros** (Apêndice D, p. 193):

- `y = 0 ∧ x ≤ 0` → Error 0 (`0^0` e `0^(-k)` ambos indefinidos).
- `y < 0 ∧ x não inteiro` → Error 0 (raízes de número negativo são complexas).
- `y > 0` → aceita qualquer `x` real.
- `y < 0 ∧ x inteiro` → aceita (via `(-y)^x · (-1)^x`; o manual p. 87 mostra `(-2)^3 = -8` como exemplo válido).

**Impl**: o caminho canônico é `y^x = exp(x · ln(y))` para `y > 0`, e tratamento explícito de `y < 0` via `sign(y)^x · exp(x · ln(|y|))`. `Hp12cDecimal.pow(y, x)` (passo 5) já encapsula isso.

## 5. Resumo das condições de erro

Coletadas dos Apêndices D (p. 193-195):

| Função  | Condição de erro           | Código       |
|---------|----------------------------|--------------|
| `1/x`   | `x = 0`                    | Error 0      |
| `√x`    | `x < 0`                    | Error 0      |
| `LN`    | `x ≤ 0`                    | Error 0      |
| `e^x`   | `x > 230.2585...` (overflow)| Error 1     |
| `y^x`   | `y = 0 ∧ x ≤ 0`            | Error 0      |
| `y^x`   | `y < 0 ∧ x não inteiro`    | Error 0      |
| `n!`    | `x < 0`                    | Error 5      |
| `n!`    | `x não inteiro`            | Error 5      |
| `n!`    | `n! > 9.999...× 10^99`     | Error 1      |
| `%`     | —                           | (sem erro)   |
| `Δ%`    | `y = 0` (silêncio do manual)| Error 0 *  |
| `%T`    | `y = 0` (silêncio do manual)| Error 0 *  |
| `x²`    | —                           | (sem erro)  |
| `INT`   | —                           | (sem erro)  |
| `FRAC`  | —                           | (sem erro)  |
| `RND`   | —                           | (sem erro)  |

`*` ambiguidade documentada em `referencias/bcd-rounding.md` — tratamos como Error 0 por consistência com divisão por zero.

## 6. Comportamento da pilha (resumo)

Detalhes completos em `referencias/stack-behavior.md`. Resumo:

| Categoria                        | Funções                                          | Efeito na pilha        |
|----------------------------------|--------------------------------------------------|------------------------|
| Unária, LASTx guarda original    | `1/x, √x, x², LN, e^x, n!, INT, FRAC`            | X muda, Y/Z/T fixos, LASTx ← x_antigo |
| Unária, LASTx não guarda         | `RND`                                            | X é "carimbado" pelo display, LASTx fica no valor pré-operação anterior |
| Binária (desce pilha)            | `y^x`                                            | X ← y^x, Y ← Z, Z ← T, T ← T, LASTx ← x_antigo |
| "Binária-mas-mantém-y"           | `%, %T`                                          | X ← resultado, Y continua com base/total original, LASTx ← x_antigo |
| Binária clássica                 | `Δ%`                                             | X ← resultado, Y ← Z, Z ← T, T ← T, LASTx ← x_antigo |

A distinção entre `%/%T` (que retêm `y`) e `Δ%` (que desce a pilha) é sutil e frequentemente mal-documentada — essa linha 4 da tabela acima é a fonte de confusão #1 em tutoriais de HP12C. A engine precisa refletir isso literalmente; o teste deve ter cobertura explícita (idealmente: `100 ENTER 20 % +` deve dar `120` porque `100` ainda está em Y após `%`).

## 7. Ambiguidades e notas de implementação

1. **`0!`** — manual lista condição "`x ≤ 0`" em Apêndice D (que literalmente incluiria `0`), mas Apêndice E define `0! = 1`. Seguimos Apêndice E (mais específico e positivo).
2. **`Δ%` e `%T` com `y = 0`** — manual silencia; emitimos Error 0 por paralelismo com `÷` (divisão por zero).
3. **`y^x` com `y < 0 ∧ x = 0`** — `(-k)^0` é universalmente `1` em matemática, e a HP física confirma. Não há Error 0 aqui apesar do manual listar "`y < 0 ∧ x não inteiro`" genericamente (x=0 é inteiro, então a condição não dispara).
4. **`RND` em SCI/ENG** — arredonda a mantissa em `n+1` dígitos significativos (não `n` casas decimais). Testes precisam cobrir todos os 3 formatos separadamente.
5. **`LN(1) = 0`** e **`e^0 = 1`** — casos de borda que vale testar (consistência numérica do `Hp12cDecimal.ln/exp` nas fronteiras do domínio).
6. **Precisão de `sqrt` vs `pow(0.5)`** — documentado em 3.3; usar `BigDecimal.sqrt(MathContext)` direto, não a via genérica por `pow`.
