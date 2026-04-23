package com.arcom.hp12c.engine.format

import com.arcom.hp12c.engine.state.CalculatorState
import com.arcom.hp12c.engine.state.DisplayFormat
import com.arcom.hp12c.engine.state.NumericSeparator
import kotlin.math.abs

/**
 * Conversão `CalculatorState → String` do visor. Separado de [com.arcom.hp12c.engine.DefaultEngine]
 * para manter a aritmética de apresentação (separador de milhar, sinal de "-", notação científica
 * com espaço/menos entre mantissa e expoente) testável isoladamente.
 *
 * **Precedência** (Seção 5 do manual `bpia5314.pdf`, Apêndice D para `Error n`):
 *
 *   1. `state.pendingError != null` → `"Error ${code}"` — supera qualquer entrada/valor.
 *   2. `state.stack.isEntering` com `entryBuffer != null` → espelha o buffer com separadores
 *      da UI (CHS, ponto decimal parcial, EEX preservados). A pilha **não** é renderizada até o
 *      usuário terminar a digitação.
 *   3. Caso contrário → `state.stack.x` formatado conforme `state.display`.
 *
 * **FIX n** — arredonda `x` a `n` casas decimais em HALF_EVEN (invariante #1 da skill
 * `hp12c-simulator`) e agrupa milhar. Se a parte inteira não cabe em `10 - n` dígitos, degrada
 * para SCI automaticamente (manual p. 72: "números maiores que 9.999.999.999 automaticamente
 * aparecem em notação científica"; o limiar escala com `n`).
 *
 * **SCI n** — normaliza `x` como `m × 10^e` com `1 ≤ |m| < 10`, exibe mantissa com `n+1`
 * algarismos significativos e expoente de 2 dígitos com sinal (`' '` para positivo, `'-'`
 * para negativo). `n` é saturado a 6 — limite físico do display de 10 dígitos.
 *
 * **ENG n** — igual a SCI mas com expoente múltiplo de 3, mantissa em `[1, 1000)`. Agrupamento
 * de milhar aplicado à parte inteira da mantissa.
 *
 * **Agrupamento de milhar** — pt-BR usa `.` (milhar) + `,` (decimal); en-US usa `,` (milhar) +
 * `.` (decimal). Interno trabalhamos com `.` (padrão de [Hp12cDecimal.toString]); a troca de
 * caracteres acontece na última etapa.
 *
 * Referência canônica: Seção 5 "Características operacionais adicionais — formatos de
 * apresentação de números" do manual, e `referencias/bcd-rounding.md` para a regra HALF_EVEN.
 */
internal object DisplayFormatter {

    /** Largura física do display da HP 12C Platinum — 10 dígitos de mantissa. */
    private const val DISPLAY_DIGITS: Int = 10

    /** Limite prático para `n` em SCI/ENG dado que a mantissa cabe em `DISPLAY_DIGITS`. */
    private const val SCI_MAX_FRAC: Int = 6

    fun format(state: CalculatorState, separator: NumericSeparator): String {
        // 1. Erro pendente: tudo o mais é ignorado (manual, Apêndice D).
        state.pendingError?.let { err -> return "Error ${err.code}" }

        // 2. Entrada em curso: espelha o buffer com separadores da UI.
        if (state.stack.isEntering && state.entryBuffer != null) {
            return formatEntryBuffer(state.entryBuffer, separator)
        }

        // 3. Renderização normal de `x` conforme `state.display`.
        val plain = state.stack.x.toString()
        return when (val fmt = state.display) {
            is DisplayFormat.Fix -> formatFix(plain, fmt.places, separator)
            is DisplayFormat.Sci -> formatSci(plain, fmt.places, separator)
            is DisplayFormat.Eng -> formatEng(plain, fmt.places, separator)
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  FIX
    // ───────────────────────────────────────────────────────────────────────────

    private fun formatFix(plain: String, places: Int, sep: NumericSeparator): String {
        // Primeiro estima quantos dígitos inteiros o valor tem (sem mudar nada), para
        // decidir se o FIX n cabe no visor de 10 dígitos. Regra HP (manual p. 72):
        //   - Se `int_digits > 10`: degrada para SCI (o número não cabe de forma alguma).
        //   - Senão: cap `places` a `10 - int_digits` — a HP reduz casas decimais em vez
        //     de trocar pra SCI quando só as fracionárias estouram.
        //
        // Ex.: `14.87456320` em FIX 9 mostra `14,87456320` (8 casas, pois 2+9 > 10).
        val roundedMax = roundHalfEven(plain, places)
        val (_, intPartProbe, _) = decompose(roundedMax)
        val intDigitsForLimit = if (intPartProbe == "0") 1 else intPartProbe.length

        if (intDigitsForLimit > DISPLAY_DIGITS) {
            return formatSci(plain, minOf(places, SCI_MAX_FRAC), sep)
        }

        val effectivePlaces = minOf(places, DISPLAY_DIGITS - intDigitsForLimit)
        val rounded = if (effectivePlaces == places) roundedMax else roundHalfEven(plain, effectivePlaces)
        val (neg, intPart, fracPart) = decompose(rounded)

        val (thCh, decCh) = separatorChars(sep)
        val groupedInt = groupThousands(intPart, thCh)
        val core = if (effectivePlaces == 0) groupedInt else "$groupedInt$decCh$fracPart"
        val allZero = intPart == "0" && fracPart.all { it == '0' }
        return if (neg && !allZero) "-$core" else core
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  SCI
    // ───────────────────────────────────────────────────────────────────────────

    private fun formatSci(plain: String, places: Int, sep: NumericSeparator): String {
        val (_, decCh) = separatorChars(sep)
        val (neg, intPart, fracPart) = decompose(plain)
        val p = minOf(places, SCI_MAX_FRAC)

        // Caso especial: zero. Exibe `0.00…0 00` (sem sinal do expoente, é espaço).
        if (intPart == "0" && fracPart.all { it == '0' }) {
            val mant = if (p == 0) "0" else "0$decCh${"0".repeat(p)}"
            return "${if (neg) "-" else ""}$mant 00"
        }

        // Normaliza: extrai dígitos significativos e expoente de deslocamento do primeiro deles.
        val (sigDigits, exp) = normalize(intPart, fracPart)

        // Mantissa com `p+1` algarismos significativos via arredondamento HALF_EVEN sobre string.
        // Truque: constrói `d.dddd...` com `p` casas depois do ponto, arredonda com `roundHalfEven`
        // e depois corrige um eventual carry que produz 2 dígitos à esquerda do ponto.
        val padded = sigDigits.padEnd(p + 2, '0')   // garante 1 dígito de pivot para rounding
        val mantPreRound = buildString {
            append(padded[0])
            if (p > 0) {
                append('.')
                append(padded.substring(1, p + 1 + 1))   // p + 1 dígitos (arredonda o último)
            } else {
                // p == 0: mesmo assim precisamos do dígito-pivot para HALF_EVEN
                append('.')
                append(padded[1])
            }
        }
        val mantRounded = roundHalfEven(mantPreRound, p)

        // Corrige carry: "9.99" + 1 ULP → "10.00". Nesse caso empurra exp e corta para 1 dígito.
        val (finalInt, finalFrac, finalExp) = adjustMantissaCarry(mantRounded, exp, p)

        val expSignCh = if (finalExp < 0) '-' else ' '
        val expStr = pad2(abs(finalExp))
        val mantStr = if (p == 0) finalInt else "$finalInt$decCh$finalFrac"
        val prefix = if (neg) "-" else ""
        return "$prefix$mantStr$expSignCh$expStr"
    }

    /**
     * Dada uma mantissa arredondada `mantPlain` (esperado `d.dddd…`), devolve (intStr, fracStr,
     * expAjustado). Trata o caso em que o arredondamento produziu carry ("9.99" → "10.00"),
     * deslocando o ponto decimal para a esquerda e incrementando `exp`.
     */
    private fun adjustMantissaCarry(
        mantPlain: String,
        exp: Int,
        p: Int,
    ): Triple<String, String, Int> {
        val (_, intPart, fracPart) = decompose(mantPlain)
        if (intPart.length == 1) {
            val paddedFrac = fracPart.padEnd(p, '0')
            return Triple(intPart, paddedFrac, exp)
        }
        // Carry "10.00" → "1.000" com exp+1; "100.0" → "1.000" com exp+2; etc.
        val shift = intPart.length - 1
        val allDigits = (intPart + fracPart).padEnd(p + 1, '0')
        val newInt = allDigits.substring(0, 1)
        val newFrac = allDigits.substring(1, minOf(allDigits.length, 1 + p))
        return Triple(newInt, newFrac.padEnd(p, '0'), exp + shift)
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  ENG
    // ───────────────────────────────────────────────────────────────────────────

    private fun formatEng(plain: String, places: Int, sep: NumericSeparator): String {
        val (thCh, decCh) = separatorChars(sep)
        val (neg, intPart, fracPart) = decompose(plain)
        val p = minOf(places, SCI_MAX_FRAC)

        if (intPart == "0" && fracPart.all { it == '0' }) {
            val mant = if (p == 0) "0" else "0$decCh${"0".repeat(p)}"
            return "${if (neg) "-" else ""}$mant 00"
        }

        // SCI-normal primeiro: obtém `sigDigits` e `expSci` com 1 dígito antes do ponto.
        val (sigDigits, expSci) = normalize(intPart, fracPart)

        // Piso múltiplo de 3 ≤ expSci. `((expSci mod 3) + 3) mod 3` dá o resto sempre em [0,2].
        val rem = ((expSci % 3) + 3) % 3
        val engExp = expSci - rem
        val shift = rem                       // 0, 1 ou 2 dígitos adicionais antes do ponto

        // Total de dígitos significativos exibidos = p + 1 (mesma conta da SCI).
        val total = p + 1
        val padded = sigDigits.padEnd(total + 2, '0')

        // Monta `intFirst.fracRest` com `shift+1` dígitos antes do ponto. Sempre incluímos o
        // `.` para garantir que `roundHalfEven` possa enxergar o dígito-pivot como fracionário
        // (mesmo quando `fracLen == 0`, precisamos do próximo dígito para decidir HALF_EVEN).
        val intLen = shift + 1
        val fracLen = maxOf(0, total - intLen)
        val mantPreRound = buildString {
            append(padded.substring(0, intLen))
            append('.')
            append(padded.substring(intLen, minOf(padded.length, intLen + fracLen + 1)))
        }
        val mantRounded = roundHalfEven(mantPreRound, fracLen)

        // Trata carry: se arredondamento produziu dígito extra à esquerda (ex.: "999.9" → "1000"),
        // o expoente sobe 3 e a mantissa volta pra 1.xxx. Detecta por comprimento da parte inteira.
        val (rInt, rFrac) = splitPlain(mantRounded)
        val (finalInt, finalFrac, finalExp) = if (rInt.length > intLen) {
            // Carry estourou o bloco — re-normaliza subindo exp em múltiplo de 3.
            val carry = rInt.length - intLen
            val newExp = engExp + (((carry - 1) / 3) + 1) * 3
            val reShift = ((newExp % 3) + 3) % 3
            // Recomputa mantissa a partir dos dígitos originais com novo shift.
            val reIntLen = reShift + 1
            val reFracLen = maxOf(0, total - reIntLen)
            val rePadded = (rInt + rFrac).padEnd(reIntLen + reFracLen + 2, '0')
            val reMantPre = buildString {
                append(rePadded.substring(0, reIntLen))
                if (reFracLen > 0) append('.') else append('.')
                append(rePadded.substring(reIntLen, minOf(rePadded.length, reIntLen + reFracLen + 1)))
            }
            val reMantRounded = roundHalfEven(reMantPre, reFracLen)
            val (reI, reF) = splitPlain(reMantRounded)
            Triple(reI, reF.padEnd(reFracLen, '0'), newExp)
        } else {
            Triple(rInt, rFrac.padEnd(fracLen, '0'), engExp)
        }

        val groupedInt = groupThousands(finalInt, thCh)
        val mantStr = if (finalFrac.isEmpty()) groupedInt else "$groupedInt$decCh$finalFrac"
        val expSignCh = if (finalExp < 0) '-' else ' '
        val prefix = if (neg) "-" else ""
        return "$prefix$mantStr$expSignCh${pad2(abs(finalExp))}"
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Entry buffer mirroring
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Espelha o buffer de digitação no visor, trocando ponto decimal e aplicando agrupamento
     * de milhar conforme `sep`. O buffer pode ter:
     *
     *   - mantissa normal: `"5000"`, `"-5000"`, `"5."`, `"5.0"`, `"5.004"`
     *   - mantissa + EEX: `"1E"`, `"1E-"`, `"1E10"`, `"-1E-05"`
     *
     * Quando há `E`, separa mantissa e expoente, renderiza mantissa como normal e expoente
     * padronizado em 2 dígitos com sinal (`' '` positivo, `'-'` negativo).
     */
    private fun formatEntryBuffer(buffer: String, sep: NumericSeparator): String {
        val (thCh, decCh) = separatorChars(sep)

        val eIdx = buffer.indexOf('E')
        if (eIdx < 0) {
            return mirrorMantissa(buffer, thCh, decCh)
        }

        val mantPart = buffer.substring(0, eIdx)
        val expPart = buffer.substring(eIdx + 1)
        val mantDisp = mirrorMantissa(mantPart, thCh, decCh)

        val expSign: Char
        val expDigits: String
        when {
            expPart.isEmpty() -> {
                expSign = ' '
                expDigits = "00"
            }
            expPart == "-" -> {
                expSign = '-'
                expDigits = "00"
            }
            expPart.startsWith("-") -> {
                expSign = '-'
                expDigits = expPart.substring(1).padStart(2, '0')
            }
            else -> {
                expSign = ' '
                expDigits = expPart.padStart(2, '0')
            }
        }
        return "$mantDisp$expSign$expDigits"
    }

    private fun mirrorMantissa(buffer: String, thCh: Char, decCh: Char): String {
        if (buffer.isEmpty()) return "0"
        val neg = buffer.startsWith("-")
        val abs = if (neg) buffer.substring(1) else buffer
        val dotIdx = abs.indexOf('.')
        val intRaw = if (dotIdx < 0) abs else abs.substring(0, dotIdx)
        val fracPart: String? = if (dotIdx < 0) null else abs.substring(dotIdx + 1)
        val intForDisp = intRaw.ifEmpty { "0" }
        val groupedInt = groupThousands(intForDisp, thCh)
        val core = if (fracPart == null) groupedInt else "$groupedInt$decCh$fracPart"
        return if (neg) "-$core" else core
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Helpers — string-math
    // ───────────────────────────────────────────────────────────────────────────

    private fun separatorChars(sep: NumericSeparator): Pair<Char, Char> = when (sep) {
        NumericSeparator.PERIOD_COMMA -> ',' to '.'
        NumericSeparator.COMMA_PERIOD -> '.' to ','
    }

    /**
     * Decompõe uma string decimal `"[-]ddd[.ddd]"` em (negativo, parteInt, parteFrac).
     * `parteInt` nunca é vazia — se entrar `"."` ou `".5"`, trata como `"0.5"`.
     */
    private fun decompose(plain: String): Triple<Boolean, String, String> {
        val neg = plain.startsWith("-")
        val abs = if (neg) plain.substring(1) else plain
        val dotIdx = abs.indexOf('.')
        return if (dotIdx < 0) {
            Triple(neg, abs.ifEmpty { "0" }, "")
        } else {
            val intRaw = abs.substring(0, dotIdx).ifEmpty { "0" }
            val fracRaw = abs.substring(dotIdx + 1)
            Triple(neg, intRaw, fracRaw)
        }
    }

    private fun splitPlain(plain: String): Pair<String, String> {
        val (_, i, f) = decompose(plain)
        return i to f
    }

    /**
     * Agrupa dígitos em blocos de 3 a partir da direita, separados por `thCh`.
     *   `"1234567"` → `"1,234,567"` (en-US) ou `"1.234.567"` (pt-BR).
     */
    private fun groupThousands(digits: String, thCh: Char): String {
        if (digits.length <= 3) return digits
        val n = digits.length
        val firstBlock = ((n - 1) % 3) + 1
        val sb = StringBuilder(n + (n - 1) / 3)
        sb.append(digits, 0, firstBlock)
        var i = firstBlock
        while (i < n) {
            sb.append(thCh)
            sb.append(digits, i, i + 3)
            i += 3
        }
        return sb.toString()
    }

    private fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()

    /**
     * Arredondamento **HALF_EVEN** (banker's) sobre string decimal, fiel ao contrato BCD da HP12C
     * (ver `referencias/bcd-rounding.md`). Input `plain` é esperado no formato `[-]ddd[.ddd]`
     * (formato de [java.math.BigDecimal.toPlainString]). Output tem exatamente `scale` dígitos
     * fracionários (padding com zeros se faltar, ou arredondamento se sobrar).
     *
     * Casos de empate (pivot = 5, resto tudo zero): o dígito retido anterior define a direção
     * — se for par, trunca; se ímpar, soma 1 ULP com propagação de carry pela parte inteira.
     */
    internal fun roundHalfEven(plain: String, scale: Int): String {
        require(scale >= 0) { "scale deve ser não-negativo, veio $scale" }
        val (neg, intPart, fracPart) = decompose(plain)

        val (newIntRaw, newFrac) = if (fracPart.length <= scale) {
            intPart to fracPart.padEnd(scale, '0')
        } else {
            val kept = fracPart.substring(0, scale)
            val pivot = fracPart[scale]
            val rest = fracPart.substring(scale + 1)
            val roundUp = when {
                pivot < '5' -> false
                pivot > '5' -> true
                rest.any { it != '0' } -> true
                else -> {
                    // Empate exato: HALF_EVEN — soma 1 só se o último retido for ímpar.
                    val lastKept = when {
                        scale > 0 -> kept.last()
                        intPart.isNotEmpty() -> intPart.last()
                        else -> '0'
                    }
                    (lastKept - '0') % 2 == 1
                }
            }
            if (!roundUp) {
                intPart to kept
            } else {
                val combined = intPart + kept
                val bumped = addOne(combined)
                val intLen = bumped.length - scale
                bumped.substring(0, intLen) to bumped.substring(intLen)
            }
        }

        // Remove zeros à esquerda, mas mantém pelo menos um dígito.
        val cleanInt = newIntRaw.trimStart('0').ifEmpty { "0" }
        val core = if (scale == 0) cleanInt else "$cleanInt.$newFrac"
        val allZero = cleanInt == "0" && newFrac.all { it == '0' }
        return if (neg && !allZero) "-$core" else core
    }

    /**
     * Soma 1 a uma string de dígitos `digits` (sem sinal, sem ponto). Propaga carry para a
     * esquerda e adiciona novo dígito de ordem superior se necessário.
     *   `"999"` → `"1000"`, `"100"` → `"101"`.
     */
    private fun addOne(digits: String): String {
        val chars = digits.toCharArray()
        var i = chars.size - 1
        while (i >= 0) {
            if (chars[i] < '9') {
                chars[i] = chars[i] + 1
                return String(chars)
            }
            chars[i] = '0'
            i--
        }
        return "1" + String(chars)
    }

    /**
     * Normaliza um decimal absoluto (parteInt, parteFrac) em (sigDigits, exp) tais que
     *
     *   valor = 0.sigDigits × 10^(exp + 1)
     *
     * isto é, `sigDigits[0]` é o algarismo mais significativo e `exp` é o expoente "científico"
     * (posição do ponto de `1.xxx × 10^exp`).
     *
     * Exemplos:
     *   - `(intPart="6083", fracPart="26")` → (`"608326"`, `3`)    // 6.08326 × 10^3
     *   - `(intPart="0",    fracPart="005")` → (`"5"`, `-3`)       // 5.0 × 10^-3
     *   - `(intPart="0",    fracPart="0")`   → (`"0"`, `0`)        // caso zero tratado externo
     */
    private fun normalize(intPart: String, fracPart: String): Pair<String, Int> {
        val trimmedInt = intPart.trimStart('0')
        if (trimmedInt.isNotEmpty()) {
            val digits = (trimmedInt + fracPart).trimEnd('0').ifEmpty { trimmedInt.take(1) }
            return digits to (trimmedInt.length - 1)
        }
        val leadingZeros = fracPart.takeWhile { it == '0' }.length
        val sigPart = fracPart.drop(leadingZeros).trimEnd('0')
        if (sigPart.isEmpty()) return "0" to 0
        return sigPart to (-1 - leadingZeros)
    }
}
