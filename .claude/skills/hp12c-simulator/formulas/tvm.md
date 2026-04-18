# TVM — Time Value of Money na HP 12C Platinum

> Fonte primária: `bpia5314.pdf` (manual oficial HP 12C Platinum), Apêndice E "Fórmulas usadas", p. 197-202, complementada pela Seção 3 "Funções financeiras básicas", p. 41-58, e pelos exemplos da apostila Moretti, Capítulos 4 e 6.

Toda a matemática financeira da HP12C gira em torno de uma única **equação de balanço de fluxos de caixa** com 5 variáveis (`n`, `i`, `PV`, `PMT`, `FV`) e um parâmetro binário de modo (`BEGIN`/`END`). Esta seção formaliza essa equação, documenta suas variantes (período fracionário) e apresenta as fórmulas algébricas de resolução para cada uma das 5 variáveis. É o que `CalculatorEngine.solveTvm(...)` tem que implementar.

## 1. Convenção de sinais (obrigatória)

A HP12C segue a convenção padrão de fluxos de caixa: **entradas** de dinheiro são positivas e **saídas** são negativas, do ponto de vista do "eu" que opera a calculadora.

| Situação | Sinal de `PV` | Sinal de `PMT` / `FV` |
|---|---|---|
| Tomar um empréstimo e devolver em prestações | `PV > 0` (entra) | `PMT, FV < 0` (saem) |
| Fazer uma aplicação e resgatar no futuro | `PV < 0` (sai) | `FV > 0` (entra) |

Esta convenção não é "recomendação"; é **requisito matemático** da equação de balanço. Quando um exemplo do manual/apostila digita `5000 CHS PV`, está explicitamente aplicando essa convenção. Nossa engine **não deve** "corrigir" sinais por heurística: se o usuário inserir PV e FV com o mesmo sinal em uma situação de financiamento, a equação de balanço retorna um resultado sem solução real (Error 5 para `n`, Error 8 para `i`) — e isso é parte da fidelidade à HP física.

## 2. Modo: BEGIN vs END

Controlado pelas teclas `g BEG` e `g END`. Default de fábrica: **END** (pagamento postecipado, no fim de cada período). BEG liga o indicador `BEGIN` no visor. Introduzimos o parâmetro binário `S`:

- **END**: `S = 0`  → pagamentos ocorrem no final de cada período (série postecipada).
- **BEGIN**: `S = 1` → pagamentos ocorrem no início de cada período (série antecipada).

No modo BEGIN, cada PMT rende um período extra de juros em relação ao END, de onde vem o fator `(1 + iS)` na equação.

## 3. Equação canônica (juros compostos, `n` inteiro)

Do Apêndice E, p. 197:

```
            ┌  1 - (1 + i)^(-n)  ┐
0 = PV + (1 + iS) · PMT · │ ─────────────────── │  + FV · (1 + i)^(-n)
                          └         i          ┘
```

onde `i` está em forma **decimal por período** (ex.: 4% a.m. → `i = 0.04`, e não 4). A tecla `[i]` da HP12C aceita o valor em **percentual** (`4.00`) e divide internamente por 100 antes de usar na equação — a engine segue a mesma convenção de interface pública.

Caso especial `i = 0`: a equação degenera para `0 = PV + n·PMT·(1 + iS)_lim + FV = PV + n·PMT + FV`. A engine deve detectar `i == 0` e usar o ramo degenerado, não tentar dividir por zero.

## 4. Variantes de período fracionário (`n` não-inteiro)

Se o usuário entrar `n` com parte fracionária (ex.: `n = 5.5`), a HP12C tem dois modos de tratar a parte fracionária, controlados pelo flag `C` (tecla `STO EEX`):

- **Modo padrão (`C` desligado)**: período fracionário usa **juros simples**.
- **Modo `C`** (tecla `STO EEX` ligada; indica-se com ponto à direita no visor, ex.: `5,5.`): período fracionário usa **juros compostos**.

### 4.1 Juros simples para parte fracionária (default)

```
0 = PV · [1 + i · FRAC(n)]
  + (1 + iS) · PMT · [ (1 - (1+i)^(-INT(n))) / i ]
  + FV · (1+i)^(-INT(n))
```

`INT(n)` e `FRAC(n)` são parte inteira e fracionária de n. Ref: manual, Apêndice E, p. 198.

### 4.2 Juros compostos para parte fracionária (flag C ativo)

```
0 = PV · (1+i)^FRAC(n)
  + (1 + iS) · PMT · [ (1 - (1+i)^(-INT(n))) / i ]
  + FV · (1+i)^(-INT(n))
```

Ref: manual, Apêndice E, p. 198.

Para `n` inteiro, `FRAC(n) = 0` e ambas as variantes se reduzem à equação canônica da Seção 3. A Fase 1 (MVP) implementa apenas a equação canônica; as variantes fracionárias entram na Fase 2.

## 5. Resolução algébrica de cada variável

Mesmas fórmulas para BEGIN e END via o fator `(1 + iS)`; onde o fator aparece multiplicando o PMT, ele precisa ser propagado quando isolamos outras variáveis.

### 5.1 Resolver para `FV` (forma fechada)

```
FV = -(1 + iS) · PMT · [ (1 - (1+i)^(-n)) / i ] - PV · (1+i)^n · ... 
```

Cuidado: a manipulação direta pode gerar erros de sinal. A forma canônica da HP12C (p. 199 do manual) é:

```
FV = -PV · (1+i)^n - (1 + iS) · PMT · [ ((1+i)^n - 1) / i ]
```

Caso degenerado `i = 0`: `FV = -PV - n·PMT`.

### 5.2 Resolver para `PV` (forma fechada)

```
PV = -(1 + iS) · PMT · [ (1 - (1+i)^(-n)) / i ] - FV · (1+i)^(-n)
```

Caso degenerado `i = 0`: `PV = -FV - n·PMT`.

### 5.3 Resolver para `PMT` (forma fechada)

Isolando PMT da equação canônica:

```
          - PV - FV · (1+i)^(-n)
PMT = ───────────────────────────────────
        (1 + iS) · [ (1 - (1+i)^(-n)) / i ]
```

Equivalente (multiplicando numerador e denominador por `(1+i)^n`):

```
          - PV · (1+i)^n - FV
PMT = ──────────────────────────────────
        (1 + iS) · [ ((1+i)^n - 1) / i ]
```

Caso degenerado `i = 0`: `PMT = -(PV + FV) / n`.

### 5.4 Resolver para `n` (forma fechada)

Para `PMT = 0`:

```
n = log(-FV / PV) / log(1 + i)
```

Para `PMT ≠ 0`, isola-se `(1+i)^n` da equação canônica:

```
              (1 + iS) · PMT - i · FV
(1+i)^n = ─────────────────────────────
              (1 + iS) · PMT + i · PV
```

e então:

```
n = log[ ((1 + iS)·PMT - i·FV) / ((1 + iS)·PMT + i·PV) ] / log(1 + i)
```

**Regra especial da HP12C para `n`**: o valor é sempre **arredondado para cima** para o próximo inteiro (teto), exceto se o `n` calculado já for exatamente inteiro. Ref: manual, Seção 3, p. 43-44 ("O prazo retorna em períodos inteiros"), e confirmado em moretti, p. 32-33, Ex. 12 onde `n ≈ 13.36` é exibido como `14`.

A parte fracionária perdida pode ser recuperada pelo usuário com `RCL n` → `FRAC` após calcular. Nossa engine armazena o `n` exato internamente e aplica o teto apenas no valor de display, para permitir esse "recall" posterior.

### 5.5 Resolver para `i` (forma iterativa)

Não existe forma fechada. A HP12C usa **iteração de Newton-Raphson** sobre a equação canônica, tipicamente com chute inicial derivado de aproximação linear. Detalhes de implementação (tolerância, limite de iterações, chute inicial) são da Fase 2 — ver `referencias/bcd-rounding.md` para as tolerâncias herdadas e `formulas/npv-irr.md` (a ser criado) para o padrão Newton-Raphson usado em IRR, que se aplica aqui também.

Se a iteração não converge em ~100 iterações, a HP dispara `Error 5`. Ver `referencias/error-codes.md`.

## 6. Ordem de entrada e reset

Entre dois cálculos de TVM **o registrador financeiro deve ser limpo** com `f CLEAR FIN` — caso contrário valores residuais das 5 variáveis contaminam o próximo cálculo. Observação prática (manual, Seção 3, p. 42): um valor não-explicitamente fornecido é assumido zero **se o registrador foi limpo antes**; sem limpeza, vale o último valor que lá estava.

A engine deve oferecer `clearFinancialRegisters()` como operação pública e documentar que o usuário típico chama isso antes de cada novo cenário.

## 7. Referências cruzadas

- Fórmulas de juros simples: `formulas/juros-simples.md` (Fase 2).
- Fórmulas de juros compostos puros (sem PMT): caso particular com `PMT = 0` desta equação. Os exemplos 10, 11, 12, 13 de Moretti (p. 30-35) usam essa forma simplificada.
- Amortização (`f AMORT`): usa a rounded-interest rule `|PV × i|_RND`. Ver `formulas/amortizacao.md` (Fase 2) e o manual, Apêndice E, p. 200.
- Fluxo de caixa genérico (`NPV`, `IRR`): generalização desta equação para PMTs variáveis. Ver `formulas/npv-irr.md` (Fase 2).
- Vetores de teste que exercitam esta seção: `test-vectors/tvm-vectors.json`.
