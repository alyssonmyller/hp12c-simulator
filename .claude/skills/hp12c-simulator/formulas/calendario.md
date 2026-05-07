# Calendário — DATE, DYS, D.MY / M.DY

> Fonte de verdade: **manual HP 12C Platinum (bpia5314.pdf), Seção 9, p. 106–113** e
> **Apêndice D (p. 197)** para condições de Error 8.
> Toda afirmação com número de página está rastreada a essa fonte.

---

## 1. Formato de data e codificação decimal

A HP 12C Platinum suporta dois formatos de data:

| Modo | Codificação | Exemplo (30 jun 1994) |
|---|---|---|
| **M.DY** (padrão) | `MM.DDYYYY` | `6.301994` |
| **D.MY** | `DD.MMYYYY` | `30.061994` |

### 1.1 Regra de codificação

Para **M.DY**: o valor digitado é `MM.DDYYYY`, onde:
- parte inteira = mês (1–12)
- parte decimal = seis dígitos `DDYYYY`: dia (2 dígitos, com zero à esquerda se necessário) + ano (4 dígitos)

```
6.301994  →  mês=6, decimal=0.301994 × 10^6 = 301994  →  dia=30, ano=1994
1.011994  →  mês=1, decimal=0.011994 × 10^6 = 011994  →  dia=01, ano=1994
```

Para **D.MY**: o valor digitado é `DD.MMYYYY`, onde:
- parte inteira = dia (1–31)
- parte decimal = seis dígitos `MMYYYY`: mês (2 dígitos) + ano (4 dígitos)

```
30.061994  →  dia=30, decimal=0.061994 × 10^6 = 061994  →  mês=06, ano=1994
```

### 1.2 Decodificação em código

```kotlin
// Extrai (dia, mês, ano) de um Hp12cDecimal no formato HP
data class HpDate(val day: Int, val month: Int, val year: Int)

fun decodeDate(value: Hp12cDecimal, isMDY: Boolean): HpDate {
    val intPart = value.toLong().toInt()                   // MM (M.DY) ou DD (D.MY)
    val fracPart = ((value - Hp12cDecimal.of(intPart)) *   // DDYYYY ou MMYYYY como inteiro
                    Hp12cDecimal.of(1_000_000)).toLong().toInt()
    val hi = fracPart / 10_000   // DD (M.DY) ou MM (D.MY)
    val lo = fracPart % 10_000   // YYYY
    return if (isMDY) HpDate(day = hi, month = intPart, year = lo)
           else       HpDate(day = intPart, month = hi, year = lo)
}
```

### 1.3 Alternância de formato

- `g D.MY` → modo D.MY (dia.mês.ano)
- `g M.DY` → modo M.DY (mês.dia.ano) — padrão de fábrica

O modo é estado global da calculadora (`CalculatorState.dateFormat: DateFormat`).
`DateFormat.MDY` é o padrão em `CalculatorEngine.InitialState`.

---

## 2. Tecla DATE (`f DATE`) — data resultante

**Sequência de entrada (manual, p. 107):**

```
[data inicial]  ENTER  [nº de dias]  f DATE
```

- `nº de dias` positivo = data futura
- `nº de dias` negativo = data passada

**Saída (após `f DATE`):**

| Registrador | Antes | Depois |
|---|---|---|
| X | n (nº de dias) | data resultante (no modo atual M.DY ou D.MY) |
| Y | data inicial | número do dia da semana (1=Seg … 7=Dom) |
| Z | Z₀ | Z₀ |
| T | T₀ | T₀ |
| LAST X | L₀ | n antigo (X antes de `f DATE`) |

**Codificação do dia da semana** `(manual, p. 107)`:

| Código | Dia |
|---|---|
| 1 | Segunda-feira |
| 2 | Terça-feira |
| 3 | Quarta-feira |
| 4 | Quinta-feira |
| 5 | Sexta-feira |
| 6 | Sábado |
| 7 | Domingo |

### 2.1 Exemplo canônico (manual, p. 107)

> Qual é a data 90 dias após 30 de junho de 1994?

```
[em M.DY, FIX 6]
6.301994  ENTER  90  f DATE
→  X = 9.281994  (28 set 1994)
   Y = 3          (quarta-feira)
```

**Verificação:** JDN(30 jun 1994) = 2449534. JDN(28 set 1994) = 2449624. Diferença = 90 ✓.
28/09/1994: JDN mod 7 = 2 → HP code = 3 (quarta) ✓.

---

## 3. Tecla DYS (`f DYS`) — número de dias entre duas datas

**Sequência de entrada (manual, p. 108):**

```
[data anterior]  ENTER  [data posterior]  f DYS
```

**Saída (após `f DYS`):**

| Registrador | Antes | Depois |
|---|---|---|
| X | data posterior | número de dias (inteiro, ≥ 0 se data2 > data1) |
| Y | data anterior | data posterior (Y₀ da pilha antes da op, i.e., o X antigo) |
| Z | Z₀ | Z₀ |
| T | T₀ | T₀ |
| LAST X | L₀ | data posterior (X antes de `f DYS`) |

**Fórmula:** `DYS = JDN(data2) − JDN(data1)`, onde JDN é o Número de Dia Juliano.

O resultado é sempre **dias corridos** (calendário gregoriano real, não base 360).
Se `data1 > data2`, o resultado é negativo.

### 3.1 Exemplo canônico (manual, p. 108)

> Quantos dias entre 30 de junho de 1994 e 28 de setembro de 1994?

```
[em M.DY, FIX 2]
6.301994  ENTER  9.281994  f DYS
→  X = 90.00
```

---

## 4. Algoritmo Gregoriano

### 4.1 Calendário Gregoriano — dias em cada mês

```
Jan=31, Fev=28/29, Mar=31, Abr=30, Mai=31, Jun=30,
Jul=31, Ago=31, Set=30, Out=31, Nov=30, Dez=31
```

**Ano bissexto** se:
- divisível por 4 **E**
- não divisível por 100 **OU** divisível por 400

```
2000: bissexto (÷400) ✓    2100: não-bissexto (÷100, ÷400 não) ✗
1900: não-bissexto ✗        2004: bissexto ✓
```

### 4.2 Número de Dia Juliano (JDN) — implementação em Kotlin puro

**Gregoriano → JDN** (algoritmo de Jean Meeus, inteiros puros, sem `java.time`):

```kotlin
fun gregorianToJDN(day: Int, month: Int, year: Int): Long {
    val a = (14 - month) / 12
    val y = year + 4800 - a
    val m = month + 12 * a - 3
    return day + (153L * m + 2) / 5 +
           365L * y + y / 4 - y / 100 + y / 400 - 32045L
}
```

**JDN → Gregoriano**:

```kotlin
fun jdnToGregorian(jdn: Long): HpDate {
    val a = jdn + 32044L
    val b = (4 * a + 3) / 146097L
    val c = a - (146097L * b) / 4
    val d = (4 * c + 3) / 1461L
    val e = c - (1461L * d) / 4
    val m = (5 * e + 2) / 153L
    val day   = (e - (153L * m + 2) / 5 + 1).toInt()
    val month = (m + 3 - 12L * (m / 10)).toInt()
    val year  = (100L * b + d - 4800L + m / 10).toInt()
    return HpDate(day, month, year)
}
```

**Dia da semana a partir do JDN**:

```kotlin
fun dayOfWeekHP(jdn: Long): Int = ((jdn % 7) + 1).toInt()
// JDN mod 7: 0=Seg, 1=Ter, 2=Qua, 3=Qui, 4=Sex, 5=Sáb, 6=Dom
// HP code:    1=Seg, 2=Ter, 3=Qua, 4=Qui, 5=Sex, 6=Sáb, 7=Dom
```

### 4.3 Tabela de referência — JDN e dias da semana para datas de teste

| Data | JDN | JDN mod 7 | HP code | Dia |
|---|---|---|---|---|
| 30 jun 1994 | 2449534 | 3 | 4 | Quinta |
| 28 set 1994 | 2449624 | 2 | 3 | Quarta |
| 01 jan 2000 | 2451545 | 5 | 6 | Sábado |
| 29 fev 2000 | 2451604 | 1 | 2 | Terça |
| 01 mar 2000 | 2451605 | 2 | 3 | Quarta |
| 01 jan 2001 | 2451911 | 0 | 1 | Segunda |
| 28 fev 2001 | 2451969 | 2 | 3 | Quarta |
| 01 mar 2001 | 2451970 | 3 | 4 | Quinta |
| 01 jan 2002 | 2452276 | 1 | 2 | Terça |

---

## 5. Condições de Error 8 `(manual, Apêndice D, p. 197)`

| Condição | Exemplo |
|---|---|
| Mês fora de [1, 12] | 13.011994 (mês 13) |
| Dia fora de [1, máx do mês] | 2.301994 (fev 30), 4.311994 (abr 31) |
| Fevereiro 29 em ano não-bissexto | 2.292001 |
| Ano < 1 ou > 9999 | datas fora do Calendário Gregoriano |
| Data anterior ao corte Gregoriano (15 out 1582) | 10.141582 ou anterior |

A validação ocorre **antes** do cálculo: se a data de entrada já é inválida, Error 8 imediato.
Se a data resultante de `f DATE` for inválida (ex.: adicionar dias levaria além de 9999), também Error 8.

---

## 6. Formato de display para datas

A HP 12C **não** força FIX 6 automaticamente para resultados de data. O resultado é armazenado em X como `Hp12cDecimal` normal e formatado conforme a configuração corrente de display `(manual, p. 107 — nota)`.

**Prática recomendada:** usar FIX 6 ao trabalhar com datas (6 casas decimais = `DDYYYY`).
Com FIX 2, o ano seria perdido (mostraria só `9.28` em vez de `9.281994`).

**Implementação:** não há lógica especial no `DisplayFormatter` para datas — a responsabilidade é do usuário de ter FIX 6 configurado. Os vetores de teste usam `format: "FIX 6"` por convenção.

---

## 7. Ambiguidades conhecidas

### §7.1 — Y após `f DATE`: data inicial ou dia-da-semana?

O manual p. 107 afirma explicitamente: Y = dia da semana (1–7) após `f DATE`.
Não é a data inicial — a data inicial foi consumida como operando.
**Decisão:** implementar Y = código do dia da semana, inteiro de 1 a 7.

### §7.2 — Y após `f DYS`: data posterior ou data anterior?

Após `f DYS`, Y contém a data que estava em X antes da operação (a data posterior), ou a data original que estava em Y (a data anterior)?
O comportamento canônico de operação binária na HP 12C: X antigo vai para LAST X, e o Y original (data anterior) sobe para Y via drop, mas `f DYS` não é uma operação binária padrão que desce a pilha — ela é mais parecida com uma operação que gera novo X sem descer Z/T.

Comportamento mais provável baseado no padrão HP: Y = data posterior (o X antigo), Z e T inalterados. **Verificar em hardware real.**

### §7.3 — Corte gregoriano: 15 out 1582 ou 4 out 1582?

A reforma gregoriana ocorreu em datas diferentes por país. O manual HP 12C não especifica o corte exato para Error 8, mas usa o padrão internacional: **15 de outubro de 1582** (primeiro dia no novo calendário). Datas de 1 jan a 14 out 1582 são ambíguas (calendário juliano vs gregoriano). O simulador usa 15 out 1582 como limite inferior.

### §7.4 — Exibição de dias da semana com FIX 6

O código do dia (ex.: 3 para quarta) é exibido com FIX 6 como "3.000000".
Ao ler Y após `f DATE`, o usuário vê "3.000000" mas significa quarta-feira.
Isso é normal na HP 12C — o significado é contextual.
