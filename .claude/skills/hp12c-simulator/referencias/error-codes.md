# Códigos de erro da HP 12C Platinum

> Fonte: manual HP 12C Platinum, Apêndice D "Condições que causam indicação de erro", p. 193-196. Esta tabela é reproduzida na íntegra porque reproduzi-la fielmente é parte do invariante "comportamento idêntico de erros" do projeto.

Quando a HP12C detecta uma condição inválida, ela exibe **`Error N`** no visor (onde `N` é um dígito de 0 a 9), bloqueia a calculadora em modo de erro e espera qualquer tecla para limpar. Pilha, memórias e registradores financeiros **não** são alterados por uma condição de erro — o usuário pode ler o estado e retomar de onde parou depois de pressionar uma tecla.

Nossa engine Kotlin expõe isso como uma exceção tipada `Hp12cError(code: Int)` que a camada de UI intercepta e exibe no visor. Nenhum estado observável muda antes do erro: isso é crítico para manter a fidelidade de "retomar depois de apertar qualquer tecla".

## Tabela completa

| Código | Categoria | Condições de disparo |
|---|---|---|
| **Error 0** | Matemática básica | (1) divisão por zero. (2) `LN` ou `LOG` de `x ≤ 0`. (3) `y^x` com `y < 0` e `x` não-inteiro. (4) `y^x` com `y = 0` e `x ≤ 0`. (5) `√x` com `x < 0`. (6) `n!` (`g n!`) com `x < 0`, não-inteiro, ou `x > 69`. (7) `1/x` com `x = 0`. |
| **Error 1** | Registradores de memória | (1) tentativa de `STO` ou `RCL` em registrador não-existente. (2) overflow ao `STO+`, `STO-`, `STO×`, `STO÷` (resultado excede 9.999999999 × 10^99 em módulo). (3) operação que requer memória contínua mas memória foi corrompida. |
| **Error 2** | Estatística | (1) `x̄` com `n = 0` (sem dados acumulados em Σ). (2) `s` (desvio padrão) com `n ≤ 1`. (3) `ŷ,r` ou `x̂,r` em regressão linear com `n ≤ 1` ou com `Σx² - n·x̄² = 0` (dados colineares verticalmente). |
| **Error 3** | IRR / fluxo de caixa | (1) `IRR` não converge em ~100 iterações (provavelmente por falta de raiz real ou múltiplas raízes — ver manual p. 96-99). (2) `IRR` com fluxo de caixa sem pelo menos uma mudança de sinal. |
| **Error 4** | Programação | (1) tentativa de executar programa com mais passos do que cabe (`> 400 passos`). (2) `GTO` ou `GSB` para linha fora dos limites. (3) subrotina aninhada além da profundidade permitida. |
| **Error 5** | TVM / Financeiro | (1) resolução de `n` ou `i` que não converge. (2) combinação de sinais inválida entre PV/PMT/FV na equação TVM (ex.: tudo positivo). (3) iteração interna de TVM excedeu limite. |
| **Error 6** | Registradores financeiros | (1) `STO` em um dos 5 registradores financeiros (`n`, `i`, `PV`, `PMT`, `FV`) não-inicializado, em contexto onde a engine espera valor válido. (2) uso de `f AMORT` com `n` não-inteiro ou `i = 0` sem PMT consistente. |
| **Error 7** | Fluxo de caixa irregular | (1) `NPV` ou `IRR` sem nenhum `CFo` registrado. (2) `Nj` > 99 para algum fluxo. (3) total de fluxos (`CFo + ΣCFj`) excede 80 na HP12C Platinum. |
| **Error 8** | Calendário | (1) data inválida em `D.MY` ou `M.DY` (ex.: `13.2024` como mês). (2) `DYS` com datas fora do range suportado (jan/1582 a dez/9999). (3) `DATE` calculando mais de ~10^4 anos no futuro/passado. |
| **Error 9** | Manutenção / Auto-teste | Erro interno de auto-teste da HP física; para o simulador, reservado para situações de corrupção do estado que um soft-reset resolve. |

## Ordem de precedência

Quando duas condições de erro poderiam disparar simultaneamente (ex.: registrador de fluxo de caixa vazio + iteração de IRR chamada), o manual não especifica ordem explícita. Convenção adotada pela engine (documentada nos testes):

1. Erros de entrada/estado inválido (categorias 1, 6, 7, 8) têm precedência sobre erros de cálculo (0, 2, 3, 5).
2. Dentro de cada grupo, avalia-se na ordem das condições listadas acima.

## Exemplo de fluxo em Kotlin

```kotlin
sealed class Hp12cError(val code: Int, message: String) : RuntimeException(message) {
    object DivisionByZero        : Hp12cError(0, "divisão por zero")
    object InvalidLog            : Hp12cError(0, "log de valor não-positivo")
    // ...
    object StorageOverflow       : Hp12cError(1, "overflow em STO")
    object StatisticsUnderflow   : Hp12cError(2, "dados estatísticos insuficientes")
    object IrrNoConverge         : Hp12cError(3, "IRR não convergiu")
    object TvmNoConverge         : Hp12cError(5, "TVM não convergiu")
    object InvalidDate           : Hp12cError(8, "data inválida")
    // ...
}
```

## Cobertura de testes

Todo `Error N` precisa de ao menos um vetor em `test-vectors/errors-vectors.json` (a ser criado na Fase 1) que exercite cada condição listada. Convenção: o `expected` desses vetores é a string `"Error N"`, e a engine é testada tanto pela exceção lançada quanto pela string que a UI mostraria.

## Recuperação pelo usuário

Do ponto de vista da UX, após um `Error N`:

1. Qualquer tecla (exceto CHS/CLR-X) limpa a mensagem de erro.
2. O visor volta a mostrar o último `X` válido antes do erro.
3. Pilha, memórias R0..R9, registradores financeiros e programa **permanecem intactos**.

Esta é uma das poucas regras que nossa engine tem que preservar **mesmo que pareça estranho** do ponto de vista de engenharia de software moderna (normalmente um erro invalida o estado; aqui ele apenas sinaliza). É parte do contrato histórico da HP12C e não pode mudar.
