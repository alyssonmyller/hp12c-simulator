# Aritmética BCD de 10 dígitos e arredondamento HP

> Fonte primária: manual HP 12C Platinum, Apêndice B "Resultados do cálculo" p. 189-192 e Apêndice E p. 197-202. Complementado por observação empírica dos exemplos resolvidos em moretti (Cap. 4 e Cap. 6).

## 1. O modelo interno da HP12C

A HP12C Platinum usa aritmética **decimal codificada em binário (BCD)** com **10 dígitos de mantissa** e um expoente decimal no intervalo `-99..+99`. Internamente nenhum resultado é representado com mais de 10 dígitos significativos; qualquer operação que produziria mais é arredondada imediatamente ao último dígito representável.

O arredondamento é **HALF_EVEN** (banker's rounding): quando o dígito seguinte é exatamente 5 e não há nada depois, arredonda-se para o vizinho par.

Por que isso importa: nossa engine é escrita em Kotlin, onde o tipo numérico default (`Double`) é binário IEEE-754 e **não representa exatamente** a maioria das frações decimais (ex.: `0.1 + 0.2 != 0.3`). Se usarmos `Double`, acumulamos erro por caminho diferente da HP física e vamos divergir no último dígito de exemplos simples — que é exatamente o nível de fidelidade que o projeto não pode perder.

## 2. Decisão de implementação: `BigDecimal` com `MathContext(10, HALF_EVEN)`

Todo cálculo numérico visível ao usuário passa por:

```kotlin
val HP12C_CONTEXT = MathContext(10, RoundingMode.HALF_EVEN)

// Exemplo de uso:
val result = pv.multiply(onePlusI.pow(n, HP12C_CONTEXT), HP12C_CONTEXT)
```

Regras derivadas desta decisão:

1. **Proibido usar `Double` para valores financeiros.** Conversão `Double → BigDecimal` só em I/O (parseando string de entrada, formatando para o display). Nunca em etapa intermediária de cálculo.
2. **Sempre passar o `MathContext` explicitamente** em `BigDecimal.multiply`, `divide`, `pow` e funções derivadas. O default do `BigDecimal` é precisão ilimitada e não arredonda — o que é *mais* preciso que a HP, e portanto divergente.
3. **Funções transcendentais** (`ln`, `exp`, `pow` não-inteira) não têm implementação BCD nativa na JVM. Usar biblioteca tipo `ch.obermuhlner:big-math` ou implementação própria (série de Taylor) com precisão interna de 12 dígitos e arredondamento final para 10 — isso garante que o último dígito da mantissa esteja correto.
4. **Formatação de display** (`FIX n`, `SCI n`, `ENG n`) é uma camada separada que recebe o BCD interno de 10 dígitos e arredonda para o formato escolhido, mantendo o valor interno íntegro. O visor de 10 posições é limitado, mas o registrador X guarda 10 dígitos completos.

## 3. ULP e tolerância de teste

O teste "o resultado casou com a HP" significa: a string formatada pelo FIX declarado bate caractere-por-caractere com o valor esperado do vetor. Internamente pode haver divergência de `≤ 1 ULP na 10ª casa significativa`, mas após a formatação para FIX 2 (por exemplo) isso precisa ter sumido.

Se um teste falha por 1 centavo em FIX 2, a causa quase sempre é uma das três:

1. Perdeu precisão em etapa intermediária (usou `Double` em algum lugar ou não passou o `MathContext`).
2. Usou rounding mode errado (`HALF_UP` em vez de `HALF_EVEN`).
3. A fonte secundária (apostila/livro) arredondou diferente da HP física — ver Seção 5.

## 4. Arredondamento na amortização (regra especial)

Na função `f AMORT` da HP12C, os juros de cada período são calculados como:

```
J_k = ROUND_DISPLAY( PV_k · i )
```

onde `ROUND_DISPLAY` arredonda ao **número de casas decimais do FIX atual do display**. Isso significa que alterar o FIX antes de `f AMORT` **muda o resultado da amortização**. Documentado no manual, Apêndice E, p. 200. Esta é uma das raras situações em que a formatação de display realimenta o cálculo interno.

Implicação prática para a engine: `amortize()` recebe como parâmetro o FIX atual e aplica arredondamento por passo. Testes de amortização devem declarar o FIX explicitamente.

## 5. Catálogo de ambiguidades conhecidas

Quando uma fonte secundária apresenta um resultado ligeiramente diferente do que a HP física produziria, registramos aqui com nome, origem e resolução adotada.

### 5.1 Moretti Ex. 15, p. 38 — taxa equivalente

Moretti calcula `i_eq = (1,795856)^(1/12) - 1` e apresenta resultado **"4,999998% ≈ 5,00% a.m."**. A HP12C faria o cálculo intermediário com 10 dígitos de mantissa e apresentaria em FIX 2 exatamente `5,00`, mas se o usuário comparar em FIX 6 com o cálculo da apostila poderá ver divergência na última casa porque Moretti usou calculadora não-HP com precisão diferente.

**Resolução:** nosso vetor-teste para este exemplo (não incluído na sessão 01, planejado para `formulas/juros-compostos.md`) usa FIX 2 como formato oficial. Moretti concorda em FIX 2.

### 5.2 Moretti Ex. 13, p. 34-35 — taxa de juros em FIX 6

Moretti apresenta o passo a passo com `{1,038093580 − 1} · 100 = 3,81%` (FIX 2) mas também exibe na tabela da HP `i = 3,809358` (FIX 6). Ambos são consistentes com o mesmo BCD interno de 10 dígitos — a diferença é só de formato.

**Resolução:** vetor `tvm-004` usa FIX 2 com expected `"3.81"`. Um segundo vetor em futuras sessões pode validar o FIX 6 `"3.809358"`.

### 5.3 Moretti Ex. 12, p. 33 — cálculo manual vs HP

Moretti primeiro mostra o cálculo analítico dando `n = 13,36 meses`, depois a HP exibindo `14`. Não é divergência: é a regra de teto para `n` da HP12C (ver `formulas/tvm.md` seção 5.4). O valor fracionário exato é recuperável pelo usuário via `RCL n FRAC`.

**Resolução:** o vetor `tvm-003` registra `expected: "14"` com nota explicativa; um futuro vetor `tvm-003b` pode validar o recall fracionário `"0.361945"` após `FRAC`.

### 5.4 Ambiguidades pendentes para investigar nas próximas sessões

- O exemplo do manual p. 50 ("saldo devedor no final de 3 anos") usa `5 g 12x` para configurar `n = 60` (= 5 anos), não 3. Existe uma inconsistência entre o texto descritivo e as teclas exibidas. Antes de criar vetores de "saldo devedor de financiamento" a partir desse exemplo, precisamos reler atentamente o contexto e decidir se é erro de revisão do manual ou se o exemplo continua outro problema.
- Livro FURG (`livromfhp12c.pdf`) ainda não foi varrido nesta sessão — seus exemplos podem introduzir variantes de arredondamento.

## 6. Checklist antes de commitar código numérico

- [ ] Nenhum `Double` em cálculo intermediário.
- [ ] Todo `BigDecimal.multiply/divide/pow` recebeu `HP12C_CONTEXT`.
- [ ] Operação transcendental usada tem precisão interna ≥ 12 e arredondamento final para 10.
- [ ] Se a feature interage com `FIX`, os testes declaram o FIX explicitamente.
- [ ] Se a feature é nova: os vetores de teste foram extraídos de fonte rastreável (manual/moretti/furg).
