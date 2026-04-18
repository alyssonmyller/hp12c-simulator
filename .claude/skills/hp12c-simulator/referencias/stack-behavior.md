# Comportamento da pilha RPN de 4 níveis

> Fonte: manual HP 12C Platinum, Seção 2 "Pilha operacional automática de memória, LAST X e tratamento de dados" (p. 29-40) e Apêndice A "Mais informações sobre a pilha operacional automática e LAST X" (p. 176-188).

A HP12C tem **uma pilha automática de 4 registradores** (T, Z, Y, X) e um registrador separado **LAST X**. Essa pilha é o "espaço de trabalho" em RPN: todo número digitado vai para X, toda operação binária consome X e Y (deixa resultado em X), toda operação unária transforma X sem afetar Y/Z/T.

A fidelidade de comportamento da pilha é tão importante quanto a fidelidade numérica — um simulador que "quase" imita a pilha mas derrapa num caso de borda é inútil para quem memorizou muscle-memory da HP física.

## 1. Os quatro registradores + LAST X

```
T   ┐
Z   │ 4 níveis da pilha
Y   │
X   ┘  ← único visível no visor
LAST X ← cópia do X *antes* da última operação destrutiva
```

- **X** é sempre o visor. O que o usuário digita, vai para X.
- **Y, Z, T** são memória interna, invisíveis ao usuário exceto via `R↓` (rolar para baixo) ou `x⇆y` (trocar X e Y).
- **LAST X** é preenchido automaticamente pela HP toda vez que X é destruído por uma operação. Permite desfazer o último operando (ou reusá-lo sem redigitar).

## 2. Diagramas de antes/depois

Notação: `T₀, Z₀, Y₀, X₀` = estado antes; `T₁, Z₁, Y₁, X₁` = estado depois.

### 2.1 Digitação de um número

Depende de se o usuário já pressionou `ENTER` antes:

- **Logo após `ENTER`, `CLx`, ou ligar a HP** (stack-lift desligado): o número sobrescreve X sem mover a pilha.
- **Caso contrário** (stack-lift ligado): antes de aceitar o novo número, a pilha sobe (`T ← Z`, `Z ← Y`, `Y ← X`), e o número entra em X.

Este detalhe — o "stack-lift flag" — é o que diferencia uma sequência `5 ENTER 3 +` (= 8) de `5 3 +` (= 8, mas por caminho diferente). A engine mantém um flag booleano interno `stackLiftEnabled` que é desligado por `ENTER` e `CLx`, e religado pela primeira digitação subsequente.

### 2.2 `ENTER`

```
Antes:  T₀ Z₀ Y₀ X₀
Depois: Z₀ Y₀ X₀ X₀      (duplica X em Y; T₀ cai fora)
LAST X: inalterado
stackLift: OFF
```

`ENTER` não destrói nada (não preenche LAST X) mas o T antigo é empurrado para fora. Depois de `ENTER`, a próxima digitação sobrescreve X (stackLift OFF).

### 2.3 Operação binária (`+`, `-`, `×`, `÷`, `y^x`)

```
Antes:  T₀ Z₀ Y₀ X₀
Depois: T₀ T₀ Z₀ (Y₀ op X₀)
LAST X: X₀
stackLift: ON
```

Pontos críticos:
- Z desce para Y, mas **T permanece em T e também copia-se para Z** ("T é sticky"). Isso permite operações repetidas como somatórios a partir de um valor T fixo.
- `LAST X` recebe o valor destruído de X.

Esta é a regra mais importante da pilha da HP. Ref: manual, Apêndice A, p. 178-180.

### 2.4 Operação unária (`1/x`, `√x`, `LN`, `e^x`, `CHS`, `n!`, `RND`, `INTG`, `FRAC`)

```
Antes:  T₀ Z₀ Y₀ X₀
Depois: T₀ Z₀ Y₀ f(X₀)
LAST X: X₀
stackLift: ON
```

Só X muda. Y, Z, T permanecem. LAST X guarda o valor antigo. Exceção: `CHS` durante digitação **não** afeta LAST X (é tratado como parte da entrada, não como operação).

### 2.5 Percent (`%`, `%T`, `Δ%`) — caso especial

Estas três funções são formalmente binárias (consomem X e olham Y), mas **NÃO descem a pilha**:

```
Antes:  T₀ Z₀ Y₀ X₀
Depois: T₀ Z₀ Y₀ (Y₀ · X₀ / 100)    (para %)
LAST X: X₀
stackLift: ON
```

Y é **preservado** em Y. Isso é intencional: permite calcular `300 ENTER 15 % -` = "300 menos 15% de 300" = 255, já que o `%` deixa Y=300 e X=45, e o `-` faz 300 - 45 = 255. Ref: manual, Apêndice A, p. 181-182.

### 2.6 `CLx` (clear X)

```
Antes:  T₀ Z₀ Y₀ X₀
Depois: T₀ Z₀ Y₀ 0
LAST X: inalterado
stackLift: OFF
```

Igual a `ENTER` quanto ao stack-lift (desliga), mas **zera** X em vez de duplicar. Note que `CLx` **não** afeta LAST X.

### 2.7 `R↓` (roll down)

```
Antes:  T₀ Z₀ Y₀ X₀
Depois: X₀ T₀ Z₀ Y₀
LAST X: inalterado
stackLift: ON
```

Rotação circular para baixo da pilha inteira.

### 2.8 `x⇆y` (swap X and Y)

```
Antes:  T₀ Z₀ Y₀ X₀
Depois: T₀ Z₀ X₀ Y₀
LAST X: inalterado
stackLift: ON
```

Só troca os dois níveis inferiores.

### 2.9 `LSTx` / `g LST x` (recall LAST X)

```
Antes:  T₀ Z₀ Y₀ X₀
Depois: Z₀ Y₀ X₀ LASTX
LAST X: inalterado
stackLift: ON
```

Faz um stack-lift e coloca LAST X em X. O LAST X em si não muda.

### 2.10 `STO` / `RCL` nos registradores R0..R9, Ri

`STO n`: copia X para o registrador `n`. **Não** afeta a pilha nem LAST X. O `stackLift` permanece como estava (se entrava um número antes, o próximo número digitado ainda vai sobrescrever X).

`RCL n`: faz um stack-lift (T fora, Z←Y, Y←X) e coloca o valor de `Rn` em X. LAST X inalterado. Comporta-se como uma nova digitação — daí o stack-lift.

### 2.11 `CLEAR REG` / `f CLEAR REG`

Zera **todos** os registradores de dados (R0..R9, Ri) mas **não** afeta a pilha, nem LAST X, nem os registradores financeiros. Ref: manual, Seção 1, p. 22-23.

Existem `CLEAR` específicos para cada família:

- `f CLEAR REG` — zera R0..R9, Ri (e somente eles).
- `f CLEAR FIN` — zera n, i, PV, PMT, FV.
- `f CLEAR Σ` — zera R1..R6 (usados pela estatística).
- `f CLEAR PRGM` — zera memória de programação.

## 3. Tabela resumo — quem toca o quê

| Operação | X | Y | Z | T | LAST X | stackLift após |
|---|---|---|---|---|---|---|
| Digitar número (stackLift ON) | novo | X₀ | Y₀ | Z₀ | — | ON |
| Digitar número (stackLift OFF) | novo | — | — | — | — | ON |
| `ENTER` | X₀ | X₀ | Y₀ | Z₀ | — | OFF |
| Binop (`+ - × ÷ y^x`) | Y₀ op X₀ | Z₀ | T₀ | T₀ | X₀ | ON |
| Unop (`1/x √x LN e^x …`) | f(X₀) | — | — | — | X₀ | ON |
| `%`, `%T`, `Δ%` | f(X₀,Y₀) | — | — | — | X₀ | ON |
| `CLx` | 0 | — | — | — | — | OFF |
| `R↓` | Y₀ | Z₀ | T₀ | X₀ | — | ON |
| `R↑` | T₀ | X₀ | Y₀ | Z₀ | — | ON |
| `x⇆y` | Y₀ | X₀ | — | — | — | ON |
| `LSTx` | LASTX | X₀ | Y₀ | Z₀ | — | ON |
| `STO n` | — | — | — | — | — | = antes |
| `RCL n` | Rn | X₀ | Y₀ | Z₀ | — | ON |

("—" = inalterado; "= antes" = preserva o flag como estava.)

## 4. Interface em Kotlin

```kotlin
data class Stack(
    val x: BigDecimal = BigDecimal.ZERO,
    val y: BigDecimal = BigDecimal.ZERO,
    val z: BigDecimal = BigDecimal.ZERO,
    val t: BigDecimal = BigDecimal.ZERO,
    val lastX: BigDecimal = BigDecimal.ZERO,
    val stackLiftEnabled: Boolean = true,
)

fun Stack.enter(): Stack =
    copy(y = x, z = y, t = z, stackLiftEnabled = false)

fun Stack.binaryOp(op: (BigDecimal, BigDecimal) -> BigDecimal): Stack =
    copy(
        x = op(y, x),
        y = z,
        z = t,
        // t permanece (sticky)
        lastX = x,
        stackLiftEnabled = true,
    )

fun Stack.unaryOp(op: (BigDecimal) -> BigDecimal): Stack =
    copy(x = op(x), lastX = x, stackLiftEnabled = true)

fun Stack.clx(): Stack =
    copy(x = BigDecimal.ZERO, stackLiftEnabled = false)
// ... etc
```

## 5. Testes essenciais da pilha (checklist)

Antes de aceitar a pilha como "feita", os testes abaixo têm que passar:

1. `5 ENTER 3 +` → X = 8, Y = 0 (T sticky mas zero inicial).
2. `5 ENTER 5 ENTER 5 ENTER 5 + + +` → X = 20 (exercita T sticky em cadeia).
3. `3 ENTER 4 × LSTx ÷` → X = 3 (LAST X guarda o 4 perdido).
4. `5 CLx 3 +` → X = 3 (CLx deixou stackLift OFF; a digitação do 3 sobrescreveu X em vez de empurrar).
5. `300 ENTER 15 % -` → X = 255 (percent não desce pilha).
6. `1 ENTER 2 ENTER 3 ENTER 4 R↓` → (X, Y, Z, T) = (3, 2, 1, 4).
7. Após qualquer `STO 3`, a pilha permanece `(X, Y, Z, T)` idêntica.
8. Qualquer `Hp12cError` (ex.: divisão por zero) deixa a pilha idêntica ao estado pré-operação.

Esses oito casos não são exaustivos, mas detectam ~95% das regressões comuns na implementação da pilha.
