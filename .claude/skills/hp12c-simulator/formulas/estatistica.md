# Funções estatísticas (Σ+, Σ-, x̄, s, ŷ,r, x̂,r, x̄w)

> Fontes primárias:
> - `bpia5314.pdf` (manual oficial HP 12C Platinum), **Seção 6 "Funções estatísticas"**, p. 79-84 (comportamento observável, exemplos canônicos).
> - **Apêndice E "Fórmulas usadas"**, p. 204-205 (fórmulas fechadas de média, média ponderada, regressão linear, correlação e desvio-padrão).
> - **Apêndice D "Condições de erro"**, p. 194 (Error 2 — condições de disparo por tecla estatística).

Este é o terceiro arquivo de fórmulas da skill, depois de `tvm.md` (TVM fechado) e `transcendentais.md` (teclado matemático + %). Cobre as **sete teclas estatísticas** da HP 12C Platinum:

| Tecla | Sequência | Categoria | Saída em X | Saída em Y |
|---|---|---|---|---|
| `Σ+` | tecla amarela direta | acumular | `n` (após soma) | (sobe da X antiga) |
| `Σ-` | `g Σ+` | desacumular | `n` (após subtração) | (sobe da X antiga) |
| `x̄` | `g x̄` | média | x̄ (média de x) | ȳ (média de y) |
| `s` | `g s` | desvio-padrão | sₓ (amostral de x) | sᵧ (amostral de y) |
| `ŷ,r` | `g ŷ,r` | regressão | ŷ (estimativa) | r (correlação) |
| `x̂,r` | `g x̂,r` | regressão | x̂ (estimativa) | r (correlação) |
| `x̄w` | `g x̄w` | média ponderada | x̄w (pesos em x) | (inalterado) |

Ao contrário de TVM (que resolve **1 equação** isolando 5 variáveis) e das transcendentais (cada uma é 1 função fechada), as estatísticas são **stateful**: seis registradores (R1..R6) são alimentados por `Σ+`/`Σ-` e depois consultados pelas outras cinco teclas. A engine precisa preservar essa separação entre *acumulação* e *consulta* — é o motivo de um modelo mental limpo aqui pagar dividendos no reducer.

## 1. Convenções, registradores R1..R6 e compartilhamento com STO/RCL

### 1.1 Os seis registradores estatísticos

A HP 12C Platinum acumula pontos bi-variados `(x_i, y_i)` em **R1..R6**, conforme o manual p. 79-80 e o Apêndice E p. 204-205:

| Registro | Conteúdo | Inicial após `f CLEAR Σ` |
|---|---|---|
| R1 | `n` — número de pontos acumulados | 0 |
| R2 | `Σx` | 0 |
| R3 | `Σx²` | 0 |
| R4 | `Σy` | 0 |
| R5 | `Σy²` | 0 |
| R6 | `Σxy` | 0 |

Todas as estatísticas são funções destes seis valores — **nunca da lista original de pontos**. Isso é crítico: a HP jamais guarda a amostra. Por isso não existe `undo` de um `Σ+` que dá erro por saturação; a correção é manual via `Σ-` com o mesmo par `(x, y)`.

### 1.2 Decisão explícita: compartilhamento de R1..R6 com o STO/RCL do usuário

O manual p. 103 avisa: *"quando você usa as funções estatísticas, não deve armazenar números em R1..R6 com STO porque essas memórias são usadas pela calculadora para acumular estatísticas"*. Ou seja, **R1..R6 são fisicamente os mesmos slots** dos registradores de memória numérica R1..R6 acessados por `STO 1`..`STO 6` e `RCL 1`..`RCL 6`.

Esta skill adota **opção (a)** — compartilhar fisicamente os slots — por dois motivos:

1. **Fidelidade**: a HP física faz isso; imitar é o invariante #1 da skill.
2. **Simplicidade**: `MemoryRegisters` da Fase 1 já contém R0..R9 + Ri; não precisamos de um `StatisticsRegisters` paralelo com lógica de espelhamento.

Consequências observáveis, todas intencionais:

- `STO 3 5000` após `Σ+` **corrompe Σx²** em R3, e a próxima chamada a `s`/`ŷ,r`/`x̂,r` devolve um valor errado sem disparar erro. A HP física faz exatamente isso; testes da engine devem cobrir esse cenário de colisão.
- `f CLEAR REG` (zera R0..R9 + Ri) **também** zera os registradores estatísticos — não existe "CLEAR REG só no usuário". De novo, HP faz isso.
- `f CLEAR Σ` zera **apenas** R1..R6 (e pilha — veja §2.1), preservando R0, R7..R9, Ri.

Ou seja: `MemoryRegisters` permanece o backing store único; `Σ+`/`Σ-` fazem aritmética direta sobre R1..R6. Não há duplicação de estado.

### 1.3 Notação

Ao longo deste documento:

- `x` e `y` são os valores **correntes** no registrador X e Y da pilha (o par sendo acumulado).
- `n, Σx, Σx², Σy, Σy², Σxy` são os **6 totais acumulados** em R1..R6.
- `x̄ = Σx/n`, `ȳ = Σy/n` são as médias calculadas; aparecem em fórmulas derivadas.
- Todas as fórmulas operam sobre `Hp12cDecimal` em `MathContext(10, HALF_EVEN)` — invariante #1. Nenhum cálculo intermediário (soma-de-quadrados, momento cruzado, raiz quadrada do desvio) pode escapar para `Double` em nenhum ponto.

## 2. Acumulação: `Σ+` e `Σ-`

### 2.1 `Σ+` — acumular um ponto `(x, y)`

A tecla consome o par `(y, x)` da pilha (Y = `y`, X = `x`, o manual convenciona que o **segundo** número digitado é o `x`; veja exemplo p. 81) e aplica, sobre R1..R6, as seis atualizações:

```
R1 ← R1 + 1                 (n)
R2 ← R2 + x                 (Σx)
R3 ← R3 + x²                (Σx²)
R4 ← R4 + y                 (Σy)
R5 ← R5 + y²                (Σy²)
R6 ← R6 + x · y             (Σxy)
```

Após a atualização, o **novo valor de R1** (ou seja, `n` pós-soma) é empurrado em X. Isso é documentado no manual p. 80 no exemplo de 7 vendedores:

```
f CLEAR Σ   → X = 0,00
32 ENTER 17000 Σ+   → X = 1,00   (primeiro par acumulado)
40 ENTER 25000 Σ+   → X = 2,00   (segundo par)
...
35 ENTER 15000 Σ+   → X = 7,00   (sétimo par; n=7)
```

**Comportamento da pilha** — detalhado em §7. Resumo: `Σ+` destrói X e Y (igual a uma operação binária), empurra `n` em X, desce a pilha Z→Y, T permanece em T (sticky). `LASTx` recebe o `x` antigo (o `x` do par acumulado).

**Monovariado (apenas `x`, sem y)**: quando o usuário digita um único valor e pressiona `Σ+` sem ter feito `ENTER`, a pilha tem `X = x_novo, Y = valor_anterior`. O manual p. 84 (exemplo de média ponderada) mostra o padrão **bivariado explícito** `item ENTER peso Σ+`. Casos monovariados podem ser forçados com `x ENTER 0 Σ+` (y=0) — nenhuma tecla diferenciada existe.

### 2.2 `Σ-` — desacumular um ponto `(x, y)`

Simétrica:

```
R1 ← R1 − 1                 (n)
R2 ← R2 − x                 (Σx)
R3 ← R3 − x²                (Σx²)
R4 ← R4 − y                 (Σy)
R5 ← R5 − y²                (Σy²)
R6 ← R6 − x · y             (Σxy)
```

Novo valor de R1 vai para X, como em `Σ+`.

**Uso canônico**: correção de ponto errado. Usuário digita `32 ENTER 17000 Σ+`, percebe que o correto era `17500`, e faz `32 ENTER 17000 Σ-` para desfazer, seguido de `32 ENTER 17500 Σ+` para re-acumular.

**Idiossincrasia (ambiguidade #2 — §8)**: subtrair um ponto **que nunca foi adicionado** não dispara erro. R1..R6 simplesmente ficam com valores inconsistentes (R1 negativo, por exemplo), e a próxima chamada de `s`/`ŷ,r`/`x̂,r` pode ou não disparar Error 2, dependendo do teto `nΣx² − (Σx)² ≥ 0`. Esse comportamento é do manual p. 82 (a nota de rodapé 20) na fronteira, e a HP física não valida; a engine herda.

### 2.3 `f CLEAR Σ` — zerar apenas os registradores estatísticos

Comportamento documentado na Seção 6 do manual (p. 80 do exemplo inicial):

```
R1..R6 ← 0
stack.X ← 0
stack.Y, Z, T ← inalterados  (não está claro no manual — ver §8 ambiguidade #3)
LASTx ← inalterado
```

Impacto: `CLEAR Σ` é uma "limpeza focal" — ao contrário de `CLEAR REG`, não toca em R0, R7..R9, Ri nem na pilha inteira. Permite reiniciar uma amostra preservando valores auxiliares do usuário.

## 3. Médias: `g x̄` e `g x̄w`

### 3.1 `g x̄` — média aritmética bivariada

Fórmula (Apêndice E, p. 204):

```
x̄ = Σx / n
ȳ = Σy / n
```

Após a tecla, a pilha fica:

```
X ← x̄
Y ← ȳ
Z ← (Z antigo desce? ver §7 — HP p. 81 não deixa claro)
```

**Exemplo canônico** (manual p. 81 — 7 vendedores):

```
g x̄    → X = 21.714,29   (média das vendas mensais)
x⇆y    → X = 40,00       (média das horas semanais)
```

*Verificação*: R4 = Σy = 17000+25000+26000+20000+21000+28000+15000 = 152.000; n = 7; ȳ = 152.000/7 = 21.714,285714... → FIX 2 HALF_EVEN → 21.714,29. R2 = Σx = 32+40+45+40+38+50+35 = 280; x̄ = 280/7 = 40,00 exato. ✓

**Casos de erro**: `n = 0` dispara Error 2 (Apêndice D p. 194) — divisão por zero estatística, tratada especialmente.

### 3.2 `g x̄w` — média ponderada

Fórmula (Apêndice E, p. 204):

```
x̄w = Σ(w·x) / Σw
```

Onde a convenção do manual p. 84 é: **o item (x) vai em Y, o peso (w) vai em X** e acumula com `Σ+`. Ou seja, a "tecla do preço" é o **Y** da acumulação e a "tecla do peso" é o **X**. Invertendo o que muitos esperam — documentado no exemplo de combustível.

Assim, sob a fórmula do R1..R6:

- `R2 = Σw` (soma dos pesos = X acumulado)
- `R4 = Σv` (soma dos valores do item = Y acumulado)
- `R6 = Σwv` (soma ponderada = X·Y acumulado)

Substituindo na fórmula do Apêndice E (que usa letras diferentes mas posições idênticas):

```
x̄w = R6 / R2 = Σxy / Σx
```

**Exemplo canônico** (manual p. 84 — preço médio de combustível):

```
f CLEAR Σ
1.16 ENTER 15 Σ+    (preço=1,16; litros=15)
1.24 ENTER  7 Σ+
1.20 ENTER 10 Σ+
1.18 ENTER 17 Σ+
g x̄w                 → X = 1,19
```

*Verificação*: Σwv = 15·1,16 + 7·1,24 + 10·1,20 + 17·1,18 = 17,40 + 8,68 + 12,00 + 20,06 = 58,14; Σw = 15+7+10+17 = 49; 58,14/49 = 1,186530... → FIX 2 HALF_EVEN → 1,19. ✓

**Comportamento da pilha**: X ← x̄w. **Y é preservado** (diferente de `g x̄`, que escreve em Y também). Não há uma segunda saída.

**Caso de erro** (Apêndice D p. 194): `Σx = 0` dispara Error 2 — divisão por zero no denominador.

## 4. Desvio-padrão: `g s` amostral + truque para populacional

### 4.1 `g s` — desvio-padrão amostral bivariado

Fórmulas (Apêndice E, p. 205):

```
sₓ = √{ [n·Σx² − (Σx)²] / [n·(n−1)] }
sᵧ = √{ [n·Σy² − (Σy)²] / [n·(n−1)] }
```

Essas são **estimativas amostrais do desvio-padrão da população** (divisor `n−1`, correção de Bessel), não o desvio da amostra em si. O manual p. 82 explica explicitamente a convenção: *"a HP 12C Platinum calcula as melhores estimativas do desvio-padrão da população baseadas em uma amostra"*.

Pós-condição da tecla:

```
X ← sₓ
Y ← sᵧ
```

**Exemplo canônico** (manual p. 82 — continuação dos 7 vendedores):

```
g s     → X = 4.820,59   (desvio das vendas)
x⇆y     → X = 6,03       (desvio das horas)
```

**Casos de erro** (Apêndice D p. 194):

- `n = 0`: divisão por zero dupla.
- `n = 1`: denominador `n(n−1) = 0`.
- `n·Σx² − (Σx)² < 0`: raiz de número negativo (erro numérico — pode acontecer por erro de arredondamento em amostras quase-constantes, ou por uso de `Σ-` mal-casado).
- `n·Σy² − (Σy)² < 0`: idem para y.

Todos disparam Error 2.

### 4.2 Desvio-padrão populacional: truque `g x̄ Σ+ g s`

Quando a amostra **é** a população inteira, a HP não oferece tecla direta. A nota de rodapé 20 do manual (p. 82) descreve o *truque*: **adicionar a média como ponto extra** e recalcular `s`. O resultado é σ, não sₓ.

Justificativa algébrica (registrada aqui porque a skill precisa explicar o porquê, não só o como):

Seja a amostra original com n pontos, média x̄, soma-de-quadrados-em-torno-da-média `SSₓ = Σx² − (Σx)²/n`. Então:

```
sₓ² = SSₓ / (n − 1)          ← amostral (Bessel)
σₓ² = SSₓ / n                ← populacional
```

Ao adicionar o ponto `x̄`, a nova média continua sendo `x̄` (média + própria média = mesma média), mas a soma-de-quadrados-em-torno-da-média **também** fica `SSₓ` (o novo ponto contribui zero ao desvio). Com `n' = n+1`:

```
s'² = SSₓ / (n' − 1) = SSₓ / n = σₓ²
```

Logo `s'` sobre a amostra ampliada **é** o σ da amostra original. Idem para y.

**Exemplo canônico** (manual p. 82 — 7 vendedores como população):

```
g x̄    → X = 21.714,29, Y = 40,00
Σ+     → X = 8,00          (n virou 8)
g s    → X = 4.463,00      (σ das vendas)
x⇆y    → X = 5,58          (σ das horas)
```

*Verificação numérica (abaixo é algo que a engine precisa reproduzir com BCD puro)*:

- Σx = 280; Σx² = 32²+40²+45²+40²+38²+50²+35² = 1024+1600+2025+1600+1444+2500+1225 = 11418; após Σ+ de (40,00, 21714,29): Σx' = 320; Σx'² = 11418+1600 = 13018; n' = 8.
- sₓ'² = (8·13018 − 320²) / (8·7) = (104144 − 102400) / 56 = 1744 / 56 = 31,142857... → sₓ' = √31,142857... = 5,5805... → FIX 2 = 5,58. ✓

**Observação importante para a engine**: o truque só funciona quando a média é inserida **depois** do último ponto real. Não é uma fórmula alternativa; é uma sequência de teclas. A implementação de `s` permanece a do §4.1; nada muda em código.

## 5. Regressão linear: `g ŷ,r`, `g x̂,r` e a reta `y = A + Bx`

### 5.1 Coeficientes da regressão

Fórmulas (Apêndice E, p. 205):

```
B = [ Σxy − (Σx · Σy)/n ] / [ Σx² − (Σx)²/n ]
A = ȳ − B · x̄
```

Com eles, a reta de regressão por mínimos quadrados é:

```
ŷ(x) = A + B·x              (estimativa de y dado novo x)
x̂(y) = (y − A) / B          (estimativa de x dado novo y)
```

E o coeficiente de correlação de Pearson:

```
r = [ Σxy − (Σx · Σy)/n ] / √{ [Σx² − (Σx)²/n] · [Σy² − (Σy)²/n] }
```

Todos os três são calculados em **forma fechada** a partir de R1..R6. Não há iteração; a HP não faz gradient descent.

### 5.2 `g ŷ,r` — estimar y dado novo x

Entrada: X = novo valor de x.

Cálculo: `ŷ = A + B·x` e `r` em paralelo.

Pós-condição:

```
X ← ŷ
Y ← r
```

Ambos são computados **simultaneamente** — `r` não depende de `x`, só de R1..R6; o manual p. 83 explicita *"esse coeficiente é calculado automaticamente toda vez que ŷ ou x̂ é calculado"*.

**Exemplo canônico** (manual p. 83 — predição de vendas para 48h):

```
48 g ŷ,r    → X = 28.818,93   (vendas estimadas)
x⇆y        → X = 0,90          (correlação r)
```

### 5.3 `g x̂,r` — estimar x dado novo y

Entrada: X = novo valor de y.

Cálculo: `x̂ = (y − A) / B` e `r`.

Pós-condição:

```
X ← x̂
Y ← r
```

Mesmo `r` que `ŷ,r` (é uma propriedade dos 6 registradores, não das variáveis). Diferente apenas em qual estimativa sai em X.

### 5.4 Derivar A e B explicitamente (sem tecla dedicada)

A HP 12C Platinum **não tem** uma tecla para `A` ou `B` isolados — o manual p. 83 mostra como obter:

```
A = ŷ(0) = 0 g ŷ,r
B = ŷ(1) − A = 1 g ŷ,r  x⇆y R↓ x⇆y  −
```

(A segunda sequência é mais hacker: calcula `ŷ(1)`, guarda `r` em Y, recupera o A que estava lá via roll-down, troca de volta e subtrai.)

**Exemplo canônico** (manual p. 83 — 7 vendedores):

```
0 g ŷ,r                    → X = 15,55   (A)
1 g ŷ,r x⇆y R↓ x⇆y −      → X = 0,001   (B — inclinação mínima porque as escalas de x e y diferem muito)
```

Equação final: `y = 15,55 + 0,001·x`. (Na prática, reconstituir `vendas = 15,55 + 0,001·horas` parece absurdo porque as ordens de grandeza são distintas — vendas ≈ 20000, horas ≈ 40; o truque é que B tem a unidade `vendas/hora`, então multiplicando por 40h dá apenas 0,04 "vendas", contra o intercepto de 15,55. Isso é um lembrete de que regressão precisa de escalas comparáveis; a skill não julga — só computa.)

### 5.5 Casos de erro da regressão

Apêndice D p. 194 lista as condições exatas (Error 2):

- `g ŷ,r`: `n = 0` OR `n·Σx² − (Σx)² = 0` (divisor `Σx² − (Σx)²/n = 0` → dados com x constante, reta vertical, B indefinido).
- `g x̂,r`: `n = 0` OR `n·Σy² − (Σy)² = 0` (dados com y constante, B = 0, reta horizontal, x̂ indefinido).
- Correlação r mostrada via `x⇆y` após `ŷ,r` ou `x̂,r`: dispara Error 2 se `[nΣx² − (Σx)²] · [nΣy² − (Σy)²] ≤ 0` (a condição do `x⇆y` é verificada só quando o swap é solicitado, não no cálculo inicial de `ŷ,r`/`x̂,r` — essa é uma sutileza real do manual p. 194).

A última condição é uma idiossincrasia: `ŷ,r` e `x̂,r` podem **passar** (escrevendo um valor em X) e depois `x⇆y` falha ao tentar mover `r` para X. É comportamento de calculadora real; a engine precisa replicar.

## 6. Tabela de erros (Error 2 — Apêndice D p. 194)

| Tecla | Condição de disparo |
|---|---|
| `g x̄w` | `Σx = 0` (R2 = 0) |
| `g s` | `n = 0`, ou `n = 1`, ou `n·Σx² − (Σx)² < 0`, ou `n·Σy² − (Σy)² < 0` |
| `g ŷ,r` | `n = 0`, ou `n·Σx² − (Σx)² = 0` |
| `g x̂,r` | `n = 0`, ou `n·Σy² − (Σy)² = 0` |
| `g ŷ,r x⇆y`<br>`g x̂,r x⇆y` (acessar r) | `[n·Σx² − (Σx)²] · [n·Σy² − (Σy)²] ≤ 0` (verificada no swap, não no cálculo) |

**Nenhuma das teclas estatísticas dispara Error 0 ou Error 5** — estatística monopoliza Error 2.

**`g x̄` não aparece na tabela do manual**: tecnicamente `n = 0` → divisão por zero em `Σx/n`. A HP física dispara Error 2 nesse caso (consistente com as outras médias/desvios); a engine deve replicar — adiciona-se uma linha na tabela implementada mesmo que o Apêndice D seja silencioso.

**`Σ+` e `Σ-` nunca disparam erro**. Aceitam overflow (Error 1 via limite de BCD em R2..R6), mas isso é Error 1 (overflow em memória) e não Error 2 estatístico — documentado em Apêndice D p. 193 separadamente.

## 7. Comportamento da pilha e dos registradores

Tabela-resumo das sete teclas. "Y←…" descrito apenas quando a tecla escreve em Y (tecla com "saída dupla"); caso contrário Y permanece ou desce normalmente.

| Tecla | X pós-op | Y pós-op | LASTx preenchido? | Mexe em R1..R6? | stackLift após |
|---|---|---|---|---|---|
| `Σ+` | `n` novo | (Z desce; T sticky) | sim, com x antigo | **sim (escreve)** | ON |
| `Σ-` | `n` novo | (Z desce; T sticky) | sim, com x antigo | **sim (escreve)** | ON |
| `g x̄` | `x̄` | `ȳ` (sobrescrito) | sim, com X antigo | só lê | ON |
| `g s` | `sₓ` | `sᵧ` (sobrescrito) | sim, com X antigo | só lê | ON |
| `g ŷ,r` | `ŷ(x)` | `r` (sobrescrito) | sim, com X antigo (o x da entrada) | só lê | ON |
| `g x̂,r` | `x̂(y)` | `r` (sobrescrito) | sim, com X antigo (o y da entrada) | só lê | ON |
| `g x̄w` | `x̄w` | (preservado) | sim, com X antigo | só lê | ON |

Três observações:

1. **Teclas com saída dupla** (`x̄`, `s`, `ŷ,r`, `x̂,r`) **sobrescrevem Y diretamente**. Não é um "push-duplo" — Z **não** sobe. É uma escrita direta em Y, preservando Z e T. Esse é um comportamento exclusivo das estatísticas; na Fase 1 nenhum evento faz isso. A engine precisa de uma primitiva nova no `StackOps` ou de um `copy(y = ...)` direto no reducer.

2. **`Σ+`/`Σ-` são binárias** que consomem Y **e** X (e.g. `32 ENTER 17000 Σ+` consome tanto `32` quanto `17000`), mas **não seguem a regra clássica de op-binária** (que deixaria `x·y` em X e descida de pilha). Em vez disso, o resultado em X é `n_novo` (contador); Y é a descida normal de Z. Esse é um híbrido binária+consulta.

3. **`LASTx` preenchido em todas as sete**. Regra consistente com o resto da pilha RPN: qualquer operação que destrói o X registra o X antigo em LASTx. Permite desfazer um `Σ+` acidental via `g LSTx Σ-` (recupera x antigo, supondo y ainda em Y — limitação real da HP).

## 8. Ambiguidades catalogadas

Como em `transcendentais.md`, cada ambiguidade é uma decisão explícita que a engine precisa fazer; decisão documentada aqui para não perdermos o raciocínio.

### 8.1 `0! = 1` vs condição literal — resolvida

Não se aplica à estatística; herdada de `transcendentais.md`. Mencionada aqui por completude do paralelo com `fatorial`.

### 8.2 `Σ-` de ponto nunca adicionado

O manual não proíbe, não lança erro e nem sequer documenta o caso. A HP física simplesmente faz a aritmética — `n` pode ficar negativo, somas podem ficar negativas. `s` ou `ŷ,r` pós-`Σ-` errado pode ou não disparar Error 2 dependendo dos valores.

**Decisão**: engine herda exatamente o comportamento — nunca valida, nunca lança no `Σ-`. Valida só quando o usuário pede uma estatística que não faz sentido (e nesse caso dispara Error 2 pelos critérios do Apêndice D). Razão: validar no `Σ-` divergiria da HP e quebraria o truque clássico de "duplicar o `Σ-` para sair do modo estatístico com contadores zerados de um jeito sujo".

### 8.3 Pilha após `f CLEAR Σ`

O manual p. 80 no exemplo abre com `f CLEAR Σ → 0,00`. O `0,00` pode ser interpretado de duas formas:

- (a) pilha inteira zerada (CLX + CLEAR Σ).
- (b) só R1..R6 zerados, e o X foi sobrescrito com 0 como side-effect explícito.

O Apêndice A do manual (p. 181, "Clearing Operations") lista `CLEAR Σ` como "Clears statistical storage registers R1 through R6, Stack and display". **Decisão**: opção (a) — `CLEAR Σ` zera R1..R6 **e** a pilha inteira (X=Y=Z=T=0). LASTx é preservado (não consta na descrição). Registrar no reducer do passo 12.

### 8.4 `g x̄` quando `n = 0`

Não aparece explicitamente no Apêndice D p. 194. Por analogia com as outras estatísticas (`s`, `ŷ,r`, `x̂,r` todas disparam Error 2 em `n=0`), e porque `Σx/0` é matematicamente indefinido, a engine **dispara Error 2** nesse caso. Registrar na tabela de testes.

### 8.5 `Σ+` com a flag C (juros compostos) ativa

Flag C afeta apenas cálculos TVM (parte fracionária de `n`). A estatística ignora completamente. **Nenhuma ação** — flag C não muda nada para R1..R6 ou teclas estatísticas.

### 8.6 `r` via `x⇆y` após `ŷ,r` — a verificação extra do Apêndice D

O Apêndice D lista uma condição de disparo **específica** para acessar `r` via `x⇆y`:

```
[n·Σx² − (Σx)²] · [n·Σy² − (Σy)²] ≤ 0
```

**Decisão**: essa verificação é **no swap**, não em `ŷ,r`/`x̂,r`. Ou seja, a engine pode:

1. Em `ŷ,r`: computar `ŷ` e escrever em X; tentar computar `r` e escrever em Y. Se o denominador de `r` for zero/negativo, a engine **ainda assim escreve X = ŷ com sucesso** e escreve em Y algum valor (zero? o NaN-equivalente?) — mas marca internamente um flag "r inválido".
2. Em `x⇆y` subsequente: se o flag estiver set, **falha com Error 2** antes de fazer o swap.

Isso é o que o manual p. 194 descreve ao associar Error 2 a `ŷ,r x⇆y` (sequência de duas teclas) e não a `ŷ,r` isolado.

**Implementação sugerida**: calcular `r` junto com `ŷ`. Se denominador ≤ 0, não escrever nada em Y (manter o Y pré-`ŷ,r`) e registrar `statisticalState.rInvalid = true` em algum campo de estado. O `x⇆y` normal consulta esse flag antes de swapar e dispara Error 2 se true. Após qualquer tecla que descaracterize o modo (`Σ+`, `f CLEAR Σ`, etc.), o flag zera.

A alternativa — checar a condição **no ŷ,r** e disparar Error 2 lá — é mais simples mas diverge da HP física. A skill escolhe fidelidade; o custo é um flag de estado novo.

Esta é a ambiguidade mais custosa do bloco. É explicitada em vetor de teste dedicado no JSON.

### 8.7 Precisão numérica da raiz em `s`

Mesma regra de `transcendentais.md` §7: usar `Hp12cDecimal.sqrt()` real (implementado no passo 10, via `BigDecimal.sqrt(MathContext(10, HALF_EVEN))`), **nunca** `pow(0.5)`. O caminho genérico de `pow` acumula 1-2 ULP a mais na última casa; vetores como `s_x = 4.820,59` quebram se a raiz for aproximada.

Vetores afetados: `stat-stddev-001` (4.820,59), `stat-stddev-002` (6,03), `stat-stddev-pop-001` (4.463,00), `stat-stddev-pop-002` (5,58). Todos verificáveis.
