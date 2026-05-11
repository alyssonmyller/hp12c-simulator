# Análise de fluxo de caixa (`g CFo`, `g CFj`, `g Nj`, `f NPV`, `f IRR`)

> Fontes primárias:
> - `bpia5314.pdf` (manual oficial HP 12C Platinum), **Seção 4 "Funções financeiras adicionais"**, sub-seção "Análise de fluxo de caixa descontado: NPV e IRR", p. 59-67.
> - **Apêndice C "Mais informações sobre a IRR"**, p. 191-192.
> - **Apêndice D "Condições de erro"**, p. 194-195 (Erro 3, Erro 6, Erro 7).
> - **Apêndice E "Fórmulas usadas"**, sub-seção "Análise de fluxo de caixa descontado", p. 199.

Quarto arquivo de `formulas/`, depois de `tvm.md`, `transcendentais.md`, `estatistica.md` e `calendario.md`. Cobre as **5 teclas de fluxo de caixa** da HP12C — bloco de complexidade média na superfície do teclado, mas com complexidade algorítmica significativa por causa do **Newton-Raphson da IRR** e pelo **estado novo** dos fluxos persistidos em registradores.

As 5 teclas:

| Tecla     | Combinação    | Função                                                                     |
|-----------|---------------|----------------------------------------------------------------------------|
| `CFo`     | `g CFo`       | Armazena `CF₀` em R0 e zera o contador `n`.                                |
| `CFj`     | `g CFj`       | Armazena `CFⱼ` em R_j e incrementa `n`. j passa a ser o `n` corrente.       |
| `Nj`      | `g Nj`        | Armazena fator de repetição `Nⱼ` para o último `CFⱼ` informado (1..99).    |
| `NPV`     | `f NPV`       | Calcula VPL usando os fluxos armazenados + `i` em registrador financeiro. |
| `IRR`     | `f IRR`       | Calcula TIR via Newton-Raphson sobre o polinômio NPV(i)=0.                 |

Tecla auxiliar usada com calendário cashflow: `f CLEAR REG` zera os 5 registradores financeiros + R0..R20 + contador `n` específico de cashflow (manual p. 61 — "Aperte `f CLEAR REG` para zerar os registros financeiros e de armazenamento"). Esse `n` é o **mesmo** registrador financeiro do TVM (`FinancialRegisters.n`) — reutilização documentada na §2 abaixo.

## 1. Convenções e notação

Ao longo deste documento:

- `CF₀, CF₁, …, CFₖ` denotam os valores distintos de fluxo de caixa armazenados na sequência.
  `CF₀` é o investimento inicial (no instante 0); `CFⱼ` para j ≥ 1 são fluxos posteriores em
  período `j` (após aplicar repetições — ver `Nⱼ` abaixo).
- `Nⱼ` para j ≥ 1 é o **fator de repetição** do `CFⱼ`: indica quantas vezes consecutivas
  aquele valor de fluxo se repete em períodos sucessivos (1 a 99). Default: `Nⱼ = 1`. `N₀`
  é sempre 1 (não há repetição em CF₀ — ver §6.4).
- `k` é o número de **valores distintos** armazenados (i.e. quantos `g CFⱼ` foram
  pressionados após o último `g CFo`); igual ao registrador `n` da HP. Faixa válida: `0 ≤ n ≤ 20` (manual Apêndice D p. 195 — ver §7.3 para discrepância com p. 60).
- `i` é a taxa periódica (em percentual no registrador financeiro, conforme convenção do
  `tvm.md` §3): usuário digita `13` para 13%; conversão para decimal acontece dentro das
  fórmulas.
- `NPV` (VPL — Valor Presente Líquido) e `IRR` (TIR — Taxa Interna de Retorno) são os
  resultados das duas teclas principais.

**Convenção de sinais** (idêntica ao TVM): saída de caixa negativa, entrada positiva. O
manual p. 60 é explícito: "Ao informar os valores dos fluxos de caixa - incluindo o
investimento inicial CF₀ - não se esqueça de seguir a convenção para sinais de fluxos de
caixa, apertando `CHS` depois de digitar um fluxo de caixa negativo."

Em todas as fórmulas, `i_decimal = i / 100` (decimal interno).

## 2. Estado: registradores compartilhados R0..R20 + contador `n`

Decisão arquitetural fundamental, idêntica em padrão à do bloco 2 (estatística R1..R6):

- **`CF₀` vive fisicamente em `MemoryRegisters.R0`** — mesmo registrador acessível ao
  usuário via `STO 0` / `RCL 0`. Compartilhamento total: `STO 0 5000` durante uma sessão de
  cashflow corrompe `CF₀` sem disparar erro (manual p. 67 — "Para alterar um valor de fluxo
  de caixa: 1. Digite o valor no mostrador. 2. Aperte STO. 3. Digite o número do registro
  que contém o valor de fluxo de caixa a ser alterado.").
- **`CF₁..CFₖ` vivem em R1..R_k**. Por isso a operação `g CFj` precisa do contador `n` para
  saber qual registrador alocar. R0 é especial; R1..R20 são para os k = 1..20 fluxos distintos.
- **Os `Nⱼ` vivem em **registradores paralelos** invisíveis** ao usuário direto (não há `RCL Nj` indexado, mas há `RCL g Nj` para o último digitado — manual p. 66). Conceitualmente, são uma estrutura paralela `nj[1..20]` que o reducer mantém. Default `Nⱼ = 1` para qualquer j que não tenha recebido `g Nj` explícito.
- **`n` (contador de cashflow) é o **mesmo** registrador financeiro `FinancialRegisters.n` do TVM**. Manual p. 65 confirma: "armazena cada valor de fluxo de caixa em registrador especial na memória da calculadora" + Apêndice D p. 195 confirma a faixa "n > 20" como condição de erro NPV/IRR (não um contador separado).

Isso é coerente com a HP física — usar `n` para 5 anos de TVM e depois fazer cashflow força o usuário a `f CLEAR REG` antes (manual p. 61 prescreve isso explicitamente como passo 1). Engineer's note: o pacote `engine.state.FinancialRegisters` não precisa de campo novo — só nova forma de uso.

## 3. Tecla `g CFo` — armazena CF₀

```
R0  ← x
n   ← 0
```

Pré-condições: nenhuma (manual silencia sobre estado anterior). Pós-condição: pilha
preservada (regra 7 de `referencias/stack-behavior.md` — STO em qualquer variante preserva
X/Y/Z/T/LSTx).

**Detalhe importante** (manual p. 60, observação): "o investimento inicial não pode ser
igual a zero". Mas não há erro disparado se for; é uma restrição **semântica** (se for zero,
NPV ainda calcula corretamente, mas IRR não tem solução). Não validamos no `g CFo`; o erro
apropriado vem na hora do `f IRR` (Erro 7 conforme §6.5).

**Sinal**: se CF₀ é negativo (investimento), o usuário deve digitar `valor CHS g CFo`. A
engine não valida sinal — Apêndice C deixa explícito que o usuário pode ter qualquer
combinação de sinais (com algumas combinações dando Erro 7).

## 4. Tecla `g CFj` — armazena CFⱼ no próximo registrador

```
n         ← n + 1
R_n       ← x
nj[n]     ← 1   (default, sobrescrito pelo próximo g Nj se houver)
```

Pré-condição: `n + 1 ≤ 20` (manual Apêndice D — `n > 20` em NPV/IRR dispara Erro 6, mas o
limite efetivo na entrada é o **número de registradores disponíveis**, ver §7.3). Engine
deve disparar Erro 6 ao tentar **armazenar** o 21º CFⱼ (R21 não existe na HP 12C Platinum
sem partição de memória).

Pós-condição: pilha preservada. Retorna o valor armazenado no visor (mesmo padrão de `STO`).

## 5. Tecla `g Nj` — fator de repetição do último CFⱼ

```
nj[n]  ← x   (com x ∈ {1, 2, ..., 99} inteiro)
```

Aplica-se ao **último** `CFⱼ` armazenado (i.e. no índice corrente `n`). Não é indexado por j
direto; o usuário precisa saber qual `n` está apontado. Manual p. 63 exemplifica:

```
4500 g CFj    ; armazena CF_j = 4500 em R_n
3 g Nj        ; nj[n] = 3, esse fluxo se repete 3 vezes (períodos n, n+1, n+2)
9100 g CFj    ; agora R_{n+1} = 9100 (período n+3, depois do 4500 ter ocupado n, n+1, n+2)
```

**Pré-condições** (Apêndice D p. 195):
- `x ∈ [1, 99]` inteiro → senão Erro 6 (`x > 99`, `x < 0`, `x não inteiro`).
- `n ≥ 1` (i.e. já houve pelo menos um `g CFj`) → senão Erro 6 ("Tentou-se informar Nj no lugar do CF₀").

Pós-condição: pilha preservada. O `n` do contador **não muda** com `g Nj` — só `g CFj` o
incrementa. `Nj` é "decoração" do CFⱼ corrente.

**Verificação** (manual p. 66): `RCL g Nj` (ou `RCL g CFj` para alternar) permite percorrer os pares (CFⱼ, Nⱼ). Implementação: equivalente a `RCL g CFj` na HP — recall do registrador específico baseado no `n` corrente, com decremento do `n` após cada chamada (manual p. 66: "cada vez que `RCL g CFj` é pressionada, o número no registro n é decrementado por 1"). Não é responsabilidade prioritária da Fase 2 implementar a verificação; foco em CFo/CFj/Nj/NPV/IRR.

## 6. Tecla `f NPV` — Valor Presente Líquido

Fórmula canônica (Apêndice E p. 199, expandida para grupos com repetição implícita):

```
       k                  Σ_{q<j} N_q + N_j
NPV = CF₀ + Σ CF_j · Σ (1+i)^(-(Σ_{q<j} N_q + p))
       j=1               p=1
```

Em palavras: para cada `j = 1..k` (k = `n` corrente), o fluxo `CF_j` ocorre em `Nⱼ`
períodos consecutivos a partir do período imediatamente após o anterior terminar. O período
de início do grupo j é `s_j = N_1 + N_2 + ... + N_{j-1} + 1`, e o grupo cobre os períodos
`s_j, s_j+1, ..., s_j + N_j - 1`.

A fórmula expandida é matematicamente equivalente a (versão fechada para uma anuidade
discreta):

```
                    1 - (1+i)^(-N_j)
NPV = CF₀ + Σ_{j=1..k} CF_j · ────────────── · (1+i)^(-(Σ_{q<j} N_q))
                          i
```

(Multiplicar a anuidade pelo fator de desconto até o início do grupo j.)

**Pré-condições para `f NPV`**:
- `i` deve estar populado (em registrador financeiro). Senão Erro 6 (mesma família que NPV
  com n ausente — manual silencia sobre o que acontece com i = `null`, mas a HP física
  trata como i = 0 → fórmula linear; engine pode optar por isso ou por Erro 6).
- `n ∈ [0, 20]` inteiro (Apêndice D p. 195). Note que `n = 0` (apenas CF₀) é válido — NPV =
  CF₀ trivialmente.
- `r` (registradores disponíveis pela MEM) ≥ n. Para Fase 2 sem programação, sempre
  satisfeito; entra como vetor de Erro 6 só na Fase 3.

**Pós-condição** (manual p. 61): "O VPL calculado aparecerá no mostrador e será armazenado
no registro PV automaticamente." Comportamento equivalente a `Solve.Pv` do TVM:

- X ← NPV computado (via `pushValue`, respeitando `stackLiftEnabled`)
- `financial.pv` ← NPV (sobrescreve)
- LASTx ← X antigo
- Y/Z/T sobem normalmente (lift se stackLiftEnabled, senão substitui X)
- `lastResultDow` ← null (não é resultado de DATE)

**Caso degenerado i = 0**: A fórmula `(1−(1+i)^(-N))/i` é indeterminada para i=0 mas tem
limite N. Substituindo, `NPV = CF₀ + Σ CF_j · N_j` (soma simples ponderada por
repetições). Engine deve tratar esse caso explicitamente para evitar `ArithmeticException`
em divisão por zero.

## 7. Tecla `f IRR` — Taxa Interna de Retorno

Definição: `IRR` (em percentual) é a taxa que torna `NPV(i) = 0`. Algoritmicamente, a HP12C
usa **Newton-Raphson** sobre a função `f(i) = NPV(i)` definida na §6, partindo de uma
estimativa inicial.

Fórmula canônica (Apêndice E p. 199):

```
      k                    -1 · n_q
0 = Σ CF_j · [1 - (1+i)^(-n_j)/i] · (1+i)^(Σ_{q<j} )  + CF₀
    j=1
```

Equivalente algebricamente à fórmula de NPV mas com NPV = 0 e i como incógnita.

### 7.1 Algoritmo (Apêndice C p. 191-192, 4 casos)

A HP12C tenta encontrar **uma** raiz de `NPV(i) = 0`. Comportamento dependente do dataset:

- **Caso 1 — Resposta positiva**: Encontrou IRR > 0. É a única raiz positiva (a HP garante
  isso); pode haver raízes negativas adicionais não reportadas.

- **Caso 2 — Resposta negativa**: Encontrou IRR < 0. Pode haver outras negativas e
  **possivelmente** uma única positiva. O usuário pode procurar a positiva via
  `RCL g R/S` com estimativa.

- **Caso 3 — `Error 3`**: Cálculo é "muito complexo, possivelmente envolvendo múltiplas
  respostas". O usuário deve fornecer estimativa via `valor RCL g R/S` para guiar.

- **Caso 4 — `Error 7`**: Não existe IRR para os dados informados. Causa típica: erro de
  magnitude, sinal ou número de ocorrências consecutivas. **`Error 7` será exibido se não
  houver pelo menos um fluxo positivo e pelo menos um negativo** (Apêndice C p. 191).
  Esta é a condição **mais limpa** de detectar: pré-cheque antes do iterativo dispara
  `Error 7` se os sinais não cobrem ambos os hemisférios.

### 7.2 Detalhes da iteração (não documentados explicitamente)

O manual NÃO documenta:

- A **estimativa inicial** que a HP12C usa para Newton-Raphson. Implementações conhecidas
  (HP41, HP12C ROM dump): chute em `i = 0` ou uma média ponderada do CF do tipo
  `(soma positivos - soma negativos) / soma_negativos / k`. **Decisão da engine**: começar
  com `i₀ = 0%` e fazer no máximo 100 iterações. Se `NPV(i₀) = 0` exatamente (caso de borda
  i=0), retornar 0% como IRR. Senão Newton-Raphson padrão.

- A **tolerância de convergência**: provavelmente `|NPV| < 10⁻⁸` ou diferença `|i_{k+1} − i_k| < 10⁻⁸`.
  Manual p. 65 nota 16 sugere "VPL muito próximo a zero" como o critério, sem número
  específico. **Decisão da engine**: tolerância `|NPV(i)| < 10⁻⁸` em valor absoluto + `|Δi|
  < 10⁻⁸` em diferença relativa entre iterações; máx 100 iterações; falha → Erro 3.

- O **critério de divergência** ou de "complexidade" que dispara Erro 3 vs Erro 7.
  **Decisão da engine** (sintetizada do Apêndice C):
   - Pré-cheque ANTES de Newton-Raphson: se `(min CF) ≥ 0` ou `(max CF) ≤ 0` → Erro 7
     ("não há mudança de sinal, IRR não existe").
   - Se NR convergir em ≤ 100 iter → reportar IRR.
   - Se NR não convergir em 100 iter → Erro 3.
   - Se NR divergir (algumas iterações produzem `i ≤ -100` ou overflow numérico) → Erro 3.

### 7.3 Newton-Raphson implementação

Usar a derivada simbólica de NPV(i) com respeito a i:

```
d                 -CF_j · n_j · (1+i)^(-n_j-1)              ... (derivada do termo)
── (NPV(i)) = Σ ──────────────────────────────────── − ...
di                                                      (formulação completa abaixo)
```

A implementação canônica usa **diferença finita central** com `h = 10⁻⁶`:

```
NPV'(i) ≈ (NPV(i + h) - NPV(i - h)) / (2h)
i_{k+1} = i_k - NPV(i_k) / NPV'(i_k)
```

Vantagem: zero risco de erro algébrico na derivada simbólica (que envolve produtos triplos
de potências). Desvantagem: 2 chamadas a `NPV` por iteração em vez de 1. Custo total:
~200 avaliações de NPV no pior caso (100 iter × 2 calls). Cada NPV é Σ sobre k≤20 termos
com `(1+i)^(-N)` — cabível em ~10ms na JVM.

**Convergência**: o resultado deve ser convertido para percentual antes de armazenar em
`financial.i`: `IRR_pct = i_decimal * 100`. Idêntico à convenção de `Solve.I` da Fase 1
(passo 6).

**Pós-condição** (manual p. 65): "O valor de TIR calculado aparecerá no mostrador e será
automaticamente armazenado no registro i."

- X ← IRR (em percentual) via `pushValue`
- `financial.i` ← IRR (sobrescreve)
- LASTx ← X antigo
- `lastResultDow` ← null

## 8. Resumo das condições de erro

Coletadas dos Apêndices D (p. 194-195) e C (p. 191-192). **Diferente do que está em
`Hp12cError.kt` da Fase 1** (que mapeia tudo de cashflow para `Erro 7`) — ver §10.4
ambiguidade:

| Tecla     | Condição                                                 | Código        |
|-----------|----------------------------------------------------------|---------------|
| `CFo`     | (sem condição de erro — sempre aceita)                   | —             |
| `CFj`     | Tentar armazenar quando n+1 > capacidade de registradores | **Erro 6**   |
| `Nj`      | x > 99                                                    | **Erro 6**   |
| `Nj`      | x < 0                                                     | **Erro 6**   |
| `Nj`      | x não inteiro                                             | **Erro 6**   |
| `Nj`      | Pressionado antes de qualquer `g CFj` (n=0)               | **Erro 6**   |
| `NPV`     | n > 20                                                    | **Erro 6**   |
| `NPV`     | n > r (registradores disponíveis menos que necessário)    | **Erro 6**   |
| `NPV`     | n < 0 ou n não inteiro (impossível por construção)        | **Erro 6**   |
| `NPV`     | i ≤ −100                                                  | **Erro 5**   |
| `IRR`     | n > 20 (mesmo da NPV)                                     | **Erro 6**   |
| `IRR`     | Sem mudança de sinal (todos CFs ≥ 0 ou ≤ 0)               | **Erro 7**   |
| `IRR`     | Iteração não converge em 100 passos                       | **Erro 3**   |

## 9. Comportamento da pilha (resumo)

Detalhes completos em `referencias/stack-behavior.md`. Resumo das 5 teclas:

| Tecla     | Efeito na pilha                                                                       |
|-----------|---------------------------------------------------------------------------------------|
| `CFo`     | **Pilha intacta** (mesma regra de `STO` financeiro). Comita buffer antes de armazenar. |
| `CFj`     | **Pilha intacta**. Comita buffer.                                                     |
| `Nj`      | **Pilha intacta**. Comita buffer.                                                     |
| `NPV`     | X ← NPV (via `pushValue` respeitando stackLift), LASTx ← X antigo, Y/Z/T sobem.       |
| `IRR`     | X ← IRR%, LASTx ← X antigo, Y/Z/T sobem. Idêntico a `Solve.I` da Fase 1.              |

`CFo`/`CFj`/`Nj` são análogos a `STO N`, `STO I`, etc. da Fase 1 — preservam pilha porque
são operações de **armazenamento** sem cálculo. `NPV`/`IRR` são análogos a `Solve.Pv`/`Solve.I`
— calculam um valor e o empurram em X.

`lastResultDow` é zerado por `commitEntry()` em todos os 5 casos (porque todas comitam
buffer); a decoração de `g DATE` não sobrevive a operações de cashflow.

## 10. Ambiguidades e idiossincrasias

### 10.1 Discrepância "30 fluxos" (p. 60) vs "n > 20" (Apêndice D p. 195)

Manual p. 60 diz "problemas de VPL (e TIR) com até 30 fluxos de caixa (além do
investimento inicial CF₀) podem ser resolvidos". Apêndice D p. 195 lista `n > 20` como
condição de Erro 6 para NPV/IRR.

Possíveis explicações:
- **A**: Erro 6 dispara em `n > 20` por restrição "rígida" do firmware da HP 12C Platinum;
  a "30" da p. 60 reflete o número total possível **com Nj** (i.e. 20 entradas distintas ×
  algumas com Nj > 1 → 30 períodos efetivos representáveis). Mas isso contradiz o "fluxos
  de caixa" plural na p. 60.
- **B**: A frase da p. 60 é herdada do manual original do HP 12C (não-Platinum) e não foi
  atualizada para o limite real do Platinum. Hipótese mais provável.
- **C**: O manual descreve dois limites: 20 = limit "guaranteed by default", 30 = "if
  memory partition allows". Mas a p. 60 não menciona partição.

**Decisão da engine**: usar **`n > 20` como Erro 6**, seguindo Apêndice D que é
operacionalmente preciso. A alegação de p. 60 fica como ambiguidade documentada — pode
ser revista se um vetor canônico do livro Moretti exigir 30.

### 10.2 Limite efetivo de registradores

Apêndice D mensiona `n > r (como definido por MEM)`. `r` é o número de registradores
disponíveis após programação consumir parte da memória. Para Fase 2 (sem programação), `r =
20` (constante para Platinum). Engine implementa só o limite de 20; tecla `MEM` e
particionamento de memória ficam para a Fase 3.

### 10.3 IRR caso de borda i = 0

Se a soma dos fluxos for exatamente zero (`Σ CF_j · N_j = 0`), então `NPV(0) = 0` e `IRR =
0%`. Manual silencia mas o NR padrão atinge isso na primeira iteração. Engine retorna 0%
diretamente — sem entrar no loop NR — quando `Σ CF_j · N_j = 0`.

### 10.4 Erro 6 vs Erro 7 — discrepância com `Hp12cError.kt` da Fase 1

A sealed class `Hp12cError` (Fase 1) tem cashflow errors mapeados como **Erro 7**:

```kotlin
object CashflowEmpty         : Hp12cError(7, "NPV/IRR sem CFo")
object CashflowNjTooLarge    : Hp12cError(7, "Nj > 99")
object CashflowTooManyFlows  : Hp12cError(7, "> 80 fluxos de caixa")
```

O manual prescreve **Erro 6** (não Erro 7) para todas essas três condições. Isso é uma
divergência **introduzida no design original** (passo 1 da Fase 0) que precisa ser
corrigida durante a implementação (passo 16). Detalhamento:

- `CashflowEmpty` — manual silencia sobre "NPV/IRR sem CFo"; HP física trata `n=0` como NPV
  = R0 (que é 0 se não foi setado) sem disparar erro. **Reclassificar**: remover; substituir por
  `CashflowNoCFo` (Erro 7) só para o caso de IRR sem CF₀ = 0 (caso degenerado de
  "sem mudança de sinal").
- `CashflowNjTooLarge` — Apêndice D p. 195 explicita Erro 6. **Reclassificar para 6.**
- `CashflowTooManyFlows` — Apêndice D p. 195 diz `n > 20`, é Erro 6 (não Erro 7) e o
  número 80 está errado (era do HP 12C original). **Reclassificar para 6 e corrigir limite
  para 20.**

A correção é relativamente segura porque NENHUM teste atual exercita esses errors
(`Hp12cError.Cashflow*` são objects nunca instanciados pelos vetores de teste de outros
blocos). A engine nova pode introduzir os objects corretos em paralelo (`object
CashflowNTooLarge : Hp12cError(6, "n > 20")`, etc.) e marcar os antigos como `@Deprecated`
durante uma janela de transição. Decisão final fica para o passo 16.

### 10.5 IRR sem CF₀

Manual p. 60 nota: "o investimento inicial não pode ser igual a zero". HP física: se CF₀ = 0,
NPV ainda calcula (resultado = soma descontada dos CF₁..CFₖ); IRR não consegue (sem âncora,
todos CFs do mesmo sinal → sem mudança de sinal → Erro 7). Engine preserva: NPV aceita CF₀
= 0, IRR dispara Erro 7 conforme §7.2 caso 4.

### 10.6 Tolerância de convergência exata

Manual p. 65 nota 16 é vago: "VPL pode não chegar exatamente a zero. De qualquer maneira,
a taxa de juros que resulta em um VPL muito pequeno e muito próxima à TIR verdadeira."
Sem número. Engine usa `|NPV(i)| < 10⁻⁸` (epsilon razoável para 10 dígitos BCD) + `|Δi| <
10⁻⁸`. Vetores canônicos do livro Moretti (passo 16) vão validar essa escolha — divergência
de ≥ 1 ULP na 2ª casa decimal indica que precisa ajustar a tolerância.

### 10.7 Estimativa inicial de Newton-Raphson

Manual silencia. Engine começa em `i₀ = 0%` (seguro por construção: `NPV(0) = Σ CF · N` é
fácil de avaliar e produz derivada bem-definida em `i ≈ 0`). Se NR diverge a partir de 0%,
fallback: tentar `i₁ = 10%`, depois `i₂ = -50%`, depois `i₃ = 100%`. Quatro chutes; se todos
falham → Erro 3.

## 11. Decisões arquiteturais resumidas

1. **Estado em registradores existentes** (§2): R0..R20 do `MemoryRegisters` (compartilhamento
   com STO/RCL do usuário) + `n` do `FinancialRegisters` (compartilhamento com TVM). **Sem novo
   campo em `CalculatorState`** — apenas nova convenção de uso. Estrutura paralela `nj[1..20]`
   só pode ser modelada como campo novo (`val cashflowNj: List<Int> = List(20) { 1 }` em
   `FinancialRegisters` ou `MemoryRegisters`); decisão fica para o passo 16 quando o reducer
   for escrito.

2. **Sem `Hp12cError` novo limpo**, mas **reclassificação dos existentes** (§10.4) para
   alinhar com Apêndice D — Erros 6/3/7 corretos. Mudança "data only" (códigos e mensagens),
   sem novos types.

3. **Newton-Raphson via diferença finita central** (§7.3) por simplicidade e segurança
   numérica. Custo aceitável.

4. **Pré-cheque de sinal antes do NR** (§7.1 caso 4): O(k) sobre os fluxos. Se não há sinais
   opostos → Erro 7 imediato sem entrar na iteração. Vetor `cf-irr-no-sign-change` valida.

5. **Caso de borda `i = 0` em NPV** (§6 fim): tratar com fórmula linear separada
   (`Σ CF · N`) para evitar divisão por zero numérica.

6. **`f CLEAR REG` zera CF**: já implementado na Fase 1 (§reducer Memory.ClearReg que zera
   R0..R9 + Ri). Para cobrir R10..R20, estender `MemoryRegisters.clearAll()` no passo 16.
   `f CLEAR REG` também zera o contador `n` por extensão (manual p. 61 — passo 1 da
   sequência canônica).
