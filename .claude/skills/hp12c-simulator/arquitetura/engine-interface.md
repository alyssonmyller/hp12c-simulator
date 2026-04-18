# Contrato público da `CalculatorEngine`

> Documento-alvo da Fase 0, passo 2. Define a **forma** do código que será escrito na Fase 1, sem ainda escrever implementação. Leituras cruzadas obrigatórias antes de editar este arquivo: `formulas/tvm.md`, `referencias/stack-behavior.md`, `referencias/bcd-rounding.md`, `referencias/error-codes.md`.

## 1. Princípios de design

Três princípios governam o desenho e, juntos, explicam todas as decisões de estilo daqui para frente:

**1.1 Reducer puro sobre estado imutável.** A engine inteira é uma função `reduce(state, event) -> state`. Não há singletons, não há mutação global, não há callbacks. Cada pressionar de tecla é um `Event`, cada snapshot do aparelho é um `CalculatorState`, e a transição é determinística. Esse modelo é o mesmo do Elm/Redux/TCA, traduz 1-para-1 para KMP, e tem duas virtudes para este projeto em particular: testes viram `assertEquals(expected, reduce(input, event))` sem mocks, e a **memória contínua** (invariante 5 do projeto) sai de graça — é só serializar `CalculatorState` no `onStop` da plataforma.

**1.2 Zero dependências de plataforma no `commonMain`.** A engine é 100% Kotlin puro. Sem `ViewModel`, sem Coroutines obrigatórias, sem `SharedFlow`. Quem orquestra entrada e renderização é a camada nativa (Compose em Android, SwiftUI em iOS), cada uma mantendo seu próprio adapter fino. Isso garante que o kernel numérico seja testável em qualquer lugar (JVM, browser Kotlin/JS, linha de comando) e remove a tentação de decisões arquiteturais se infiltrarem no kernel.

**1.3 Fidelidade acima de idiomaticidade.** Sempre que um idioma Kotlin "mais bonito" conflitar com um comportamento observável da HP12C física, a HP ganha. Exemplo: a "pilha sticky" do `T` é feia em código funcional puro (parece bug), mas é requisito. Esse princípio está formalizado nos 5 invariantes não-negociáveis do `PROMPT_MESTRE.md` e é o critério de tiebreaker em qualquer code review.

## 2. Pacote e organização

```
shared/src/commonMain/kotlin/com/arcom/hp12c/
├── engine/
│   ├── CalculatorEngine.kt         // interface pública + factory
│   ├── Reducer.kt                  // reduce(state, event) — impl
│   ├── state/
│   │   ├── CalculatorState.kt
│   │   ├── Stack.kt
│   │   ├── FinancialRegisters.kt
│   │   ├── MemoryRegisters.kt
│   │   └── DisplayFormat.kt
│   ├── event/
│   │   ├── Event.kt                // sealed class raiz
│   │   ├── DigitEvent.kt
│   │   ├── StackEvent.kt
│   │   ├── ArithmeticEvent.kt
│   │   ├── FinancialEvent.kt
│   │   ├── MemoryEvent.kt
│   │   └── DisplayEvent.kt
│   ├── error/
│   │   └── Hp12cError.kt           // sealed class com 10 subclasses
│   ├── math/
│   │   ├── Hp12cDecimal.kt         // wrapper sobre BigDecimal com MathContext(10, HALF_EVEN)
│   │   └── Transcendentals.kt      // ln, exp, pow — impl própria
│   └── format/
│       └── DisplayFormatter.kt     // estado -> string do visor
└── commonTest/...
```

Nome de pacote: `com.arcom.hp12c` (arcom.com.br é o domínio do autor). Prefixo `Hp12c` é usado onde a ambiguidade com tipos de `kotlin.math` poderia causar confusão (`Hp12cError`, `Hp12cDecimal`).

## 3. Modelos de dados

### 3.1 `Stack`

Reflete literalmente o diagrama da Seção 1 de `referencias/stack-behavior.md`. Importante: **todos os campos são `Hp12cDecimal` imutáveis**, e o `stackLiftEnabled` é parte do estado (não um flag global escondido).

```kotlin
data class Stack(
    val x: Hp12cDecimal = Hp12cDecimal.ZERO,
    val y: Hp12cDecimal = Hp12cDecimal.ZERO,
    val z: Hp12cDecimal = Hp12cDecimal.ZERO,
    val t: Hp12cDecimal = Hp12cDecimal.ZERO,
    val lastX: Hp12cDecimal = Hp12cDecimal.ZERO,
    val stackLiftEnabled: Boolean = true,
    /**
     * `true` enquanto o usuário está digitando um número (antes de ENTER ou op).
     * Algumas operações (ex.: CHS) se comportam diferente se estamos ou não em entrada.
     */
    val isEntering: Boolean = false,
)
```

Todas as transições da pilha (ENTER, binop, unop, R↓, x⇆y, CLx, LSTx) são extension functions puras sobre `Stack`, documentadas com os diagramas de `stack-behavior.md`.

### 3.2 `FinancialRegisters`

Os 5 registradores de TVM + o modo BEG/END. Mesmo princípio: imutável, comparável por igualdade, sem setters.

```kotlin
enum class TvmMode { END, BEGIN }

data class FinancialRegisters(
    val n:    Hp12cDecimal? = null,   // null = não-inicializado
    val i:    Hp12cDecimal? = null,   // em percentual (o usuário digita 4, não 0.04)
    val pv:   Hp12cDecimal? = null,
    val pmt:  Hp12cDecimal? = null,
    val fv:   Hp12cDecimal? = null,
    val mode: TvmMode       = TvmMode.END,
)
```

Decisão: `null` distingue "não-inicializado" de "zero explícito". O manual (Seção 3, p. 42) diz que um registrador não-inicializado *após* `f CLEAR FIN` vale zero; antes do CLEAR FIN, vale o que estava lá. Nossa engine modela isso como `null → ZERO` somente quando o cálculo de TVM precisa do valor; o status de null é preservado para consulta via `RCL n` / `RCL i` / etc.

### 3.3 `MemoryRegisters`

Os 10 registradores de dados `R0..R9` + um índice indireto `Ri`. Modelo: `Map<RegisterId, Hp12cDecimal>` com constantes.

```kotlin
enum class RegisterId(val code: String) {
    R0("0"), R1("1"), R2("2"), R3("3"), R4("4"),
    R5("5"), R6("6"), R7("7"), R8("8"), R9("9"),
    RI("i"),  // registrador indireto
}

data class MemoryRegisters(
    private val values: Map<RegisterId, Hp12cDecimal> = emptyMap(),
) {
    operator fun get(id: RegisterId): Hp12cDecimal =
        values[id] ?: Hp12cDecimal.ZERO

    fun store(id: RegisterId, value: Hp12cDecimal): MemoryRegisters =
        copy(values = values + (id to value))

    fun clearAll(): MemoryRegisters = MemoryRegisters()
}
```

A Fase 2 estende isso com 10 registradores adicionais de estatística (mapeados para `R1..R6` quando `Σ+` é usado — ver manual Seção 4) e, na Fase 3, com até 20 registradores mapeáveis como espaço de programa ou dados.

### 3.4 `DisplayFormat`

```kotlin
sealed class DisplayFormat {
    data class Fix(val places: Int) : DisplayFormat()   // FIX 0..9
    data class Sci(val places: Int) : DisplayFormat()   // SCI 0..9
    data class Eng(val places: Int) : DisplayFormat()   // ENG 0..9
    companion object {
        val Default: DisplayFormat = Fix(2)
    }
}

enum class NumericSeparator {
    /** `.` decimal, `,` milhar (padrão americano) */ PERIOD_COMMA,
    /** `,` decimal, `.` milhar (padrão brasileiro) */ COMMA_PERIOD,
}
```

O separador numérico é uma preferência de UI armazenada fora do `CalculatorState` principal (em `Settings`), já que não afeta cálculo — só renderização. Mas entra no `formatDisplay(state, separator)` como parâmetro explícito.

### 3.5 `CalculatorState` — a raiz

```kotlin
data class CalculatorState(
    val stack:     Stack              = Stack(),
    val financial: FinancialRegisters = FinancialRegisters(),
    val memory:    MemoryRegisters    = MemoryRegisters(),
    val display:   DisplayFormat      = DisplayFormat.Default,
    /** Flag C: `true` = juros compostos para período fracionário. Default: `false` (juros simples). */
    val compoundFractionFlag: Boolean = false,
    /** Último erro ainda não limpado pelo usuário; se não-nulo, o visor mostra "Error N" e a próxima tecla o limpa. */
    val pendingError: Hp12cError?     = null,
    // Reservado para Fase 3 (modo PRGM)
    val programState: ProgramState    = ProgramState.Idle,
)

sealed class ProgramState {
    object Idle : ProgramState()
    // Fase 3 adicionará Running(counter), Editing(cursor), etc.
}
```

O estado inteiro é serializável (todos os campos são data classes com tipos primitivos ou `Hp12cDecimal`). Isso é o que permite persistência contínua via `kotlinx.serialization` na camada nativa.

## 4. `Hp12cError` — sealed class de erros

Cada erro é um tipo. O `code` duplica o valor para facilitar formatação `"Error $code"` no visor e mapeamento estável para testes.

```kotlin
sealed class Hp12cError(val code: Int, val reason: String) {

    // Error 0 — matemática básica
    object DivisionByZero       : Hp12cError(0, "divisão por zero")
    object LogOfNonPositive     : Hp12cError(0, "log de valor não-positivo")
    object InvalidYToX          : Hp12cError(0, "y^x inválido (y negativo com x não-inteiro, ou y=0 com x≤0)")
    object SqrtOfNegative       : Hp12cError(0, "raiz quadrada de número negativo")
    object InvalidFactorial     : Hp12cError(0, "n! inválido (x<0, não-inteiro, ou x>69)")

    // Error 1 — registradores de memória
    object RegisterNotFound     : Hp12cError(1, "registrador inexistente")
    object StoreOverflow        : Hp12cError(1, "overflow em STO aritmético")
    object MemoryCorruption     : Hp12cError(1, "memória contínua corrompida")

    // Error 2 — estatística
    object StatisticsUnderflow  : Hp12cError(2, "dados estatísticos insuficientes")
    object StatisticsCollinear  : Hp12cError(2, "regressão: dados colineares verticalmente")

    // Error 3 — IRR
    object IrrNoConverge        : Hp12cError(3, "IRR não convergiu")
    object IrrNoSignChange      : Hp12cError(3, "IRR: fluxo sem mudança de sinal")

    // Error 4 — programação
    object ProgramOverflow      : Hp12cError(4, "> 400 passos de programa")
    object InvalidGoto          : Hp12cError(4, "GTO/GSB para linha inexistente")
    object SubroutineOverflow   : Hp12cError(4, "subrotinas aninhadas além do limite")

    // Error 5 — TVM
    object TvmNoConverge        : Hp12cError(5, "TVM não convergiu")
    object TvmInvalidSigns      : Hp12cError(5, "TVM: combinação de sinais inválida")

    // Error 6 — registradores financeiros
    object FinancialUninit      : Hp12cError(6, "registrador financeiro não-inicializado")
    object AmortizeInvalidN     : Hp12cError(6, "AMORT requer n inteiro e i consistente")

    // Error 7 — fluxo de caixa
    object CashflowEmpty        : Hp12cError(7, "NPV/IRR sem CFo")
    object CashflowNjTooLarge   : Hp12cError(7, "Nj > 99")
    object CashflowTooManyFlows : Hp12cError(7, "> 80 fluxos de caixa")

    // Error 8 — calendário
    object InvalidDate          : Hp12cError(8, "data inválida")
    object DateOutOfRange       : Hp12cError(8, "data fora do intervalo suportado")

    // Error 9 — auto-teste
    object SelfTestFailure      : Hp12cError(9, "falha em auto-teste / corrupção interna")
}
```

**Contrato importante:** lançar um `Hp12cError` do reducer **não é exceção**; é um valor de retorno. O reducer captura internamente e coloca o erro em `state.pendingError`, mantendo o resto do estado intacto (ver Seção 2 de `error-codes.md`: "Pilha, memórias e registradores financeiros não são alterados por uma condição de erro"). O próximo evento, seja qual for, limpa o `pendingError` antes de ser processado (exceto ele mesmo ser um no-op).

## 5. `Event` — o alfabeto de teclas

A sealed class abaixo cobre **toda a Fase 1** com granularidade de 1 Event = 1 tecla lógica. Algumas teclas da HP precisam de modificador (`f`/`g` + algo), representadas por eventos compostos já resolvidos — o adapter de UI traduz `f` + `CLX` em `ClearReg`, não deixa `f` chegar no reducer.

```kotlin
sealed class Event {

    // --- 5.1 Entrada de dígitos ---
    sealed class Entry : Event() {
        data class Digit(val value: Int) : Entry() { init { require(value in 0..9) } }
        object DecimalPoint   : Entry()
        object ChangeSign     : Entry()   // CHS durante digitação: inverte sinal do número em curso
        object Eex            : Entry()   // entra em notação científica
    }

    // --- 5.2 Pilha ---
    sealed class StackOp : Event() {
        object Enter       : StackOp()
        object ClearX      : StackOp()   // CLx
        object RollDown    : StackOp()   // R↓
        object SwapXY      : StackOp()   // x⇆y
        object LastX       : StackOp()   // LSTx
    }

    // --- 5.3 Aritmética binária / unária (Fase 1: 4 operações + CHS fora de entrada) ---
    sealed class Arith : Event() {
        object Add      : Arith()
        object Subtract : Arith()
        object Multiply : Arith()
        object Divide   : Arith()
        object Negate   : Arith()   // CHS quando NÃO estamos em entrada: negata X
    }

    // --- 5.4 Memórias ---
    sealed class Memory : Event() {
        data class Store(val id: RegisterId)  : Memory()
        data class Recall(val id: RegisterId) : Memory()
        object ClearReg                        : Memory()   // f CLEAR REG → R0..R9, Ri
    }

    // --- 5.5 Financeiro (TVM) ---
    sealed class Financial : Event() {
        /** Armazena X no registrador correspondente (ex.: pressionar `n` após digitar). */
        sealed class Store : Financial() {
            object N   : Store()
            object I   : Store()
            object Pv  : Store()
            object Pmt : Store()
            object Fv  : Store()
        }
        /** Resolve a variável correspondente (= Store quando X tem valor; = Solve quando não). A distinção é feita pelo reducer baseado em `stack.isEntering`. */
        sealed class Solve : Financial() {
            object N   : Solve()
            object I   : Solve()
            object Pv  : Solve()
            object Pmt : Solve()
            object Fv  : Solve()
        }
        object SetBeginMode  : Financial()
        object SetEndMode    : Financial()
        object ClearFinancial: Financial()   // f CLEAR FIN
        object ToggleCompoundFractionFlag : Financial()  // STO EEX (flag C)
    }

    // --- 5.6 Formato de display ---
    sealed class Display : Event() {
        data class Fix(val places: Int) : Display() { init { require(places in 0..9) } }
        data class Sci(val places: Int) : Display() { init { require(places in 0..9) } }
        data class Eng(val places: Int) : Display() { init { require(places in 0..9) } }
    }

    /** Evento que consome/limpa um erro pendente. Emitido automaticamente pela UI ao tocar qualquer tecla após um Error. */
    object AcknowledgeError : Event()
}
```

### 5.1 O "dual Store/Solve" dos registradores financeiros

Na HP12C física, pressionar `PMT` depois de digitar `100` **armazena** 100 em PMT; pressionar `PMT` sozinho (sem digitação pendente) **resolve** PMT a partir de n, i, PV, FV. O reducer faz essa desambiguação olhando `stack.isEntering`:

```
if (state.stack.isEntering) → Store.Pmt, copia X para FinancialRegisters.pmt
else                          → Solve.Pmt, resolve e copia resultado para X e para pmt
```

Não quisemos codar isso como *um* evento ambíguo porque o adapter de UI (Compose/SwiftUI) pode escolher emitir explicitamente, e os testes ficam mais legíveis. Mas o reducer aceita as duas formas.

### 5.2 Eventos que a Fase 1 **não** inclui (placeholders Fase 2/3)

Já ficam previstos aqui para evitar refactor grande depois — são só comentados no arquivo, sem subclasses ativas:

```kotlin
// Fase 2:
// sealed class Transcendental : Arith() { object Ln, Exp, Sqrt, Reciprocal, YToX, NFactorial, Round, Integer, Fractional ... }
// sealed class Percent : Event() { object Of, OfTotal, Delta }
// sealed class Statistics : Event() { object SigmaPlus, SigmaMinus, Mean, StdDev, ... }
// sealed class Calendar : Event() { object Date, Dys, DmyMode, MdyMode }
// sealed class Cashflow : Event() { object CashFlowZero, CashFlowJ, CountJ, Npv, Irr }
// sealed class Depreciation : Event() { object StraightLine, SumOfYears, DecliningBalance }
// sealed class AlgebraicToggle : Event() { object AlgMode, RpnMode }

// Fase 3:
// sealed class Program : Event() { object PrgmToggle, Goto, Gosub, Return, RunStop, SingleStep, BackStep, ... }
```

## 6. Interface pública

```kotlin
interface CalculatorEngine {

    /** Transição pura: dado (estado, evento), produz próximo estado. Nunca lança. */
    fun reduce(state: CalculatorState, event: Event): CalculatorState

    /** Conveniência: reduz uma sequência de eventos. */
    fun reduce(state: CalculatorState, events: List<Event>): CalculatorState =
        events.fold(state) { acc, ev -> reduce(acc, ev) }

    /** Renderiza o valor do visor para string, conforme `state.display` e separador escolhido. */
    fun formatDisplay(
        state: CalculatorState,
        separator: NumericSeparator = NumericSeparator.COMMA_PERIOD,
    ): String

    companion object {
        val Default: CalculatorEngine = DefaultEngine()
        val InitialState: CalculatorState = CalculatorState()
    }
}
```

**Por que `reduce` nunca lança.** Erros da HP12C (ver Seção 4) são modelados como `pendingError` dentro de `CalculatorState`. Uma exceção Java/Kotlin só aparece se houver *bug* na engine (IllegalArgument no init de Event, falha de `require()`). O contrato é: se você passou um estado válido e um evento válido, você receberá um estado válido — possivelmente com `pendingError != null`, mas estruturalmente válido. Isso torna o reducer trivialmente testável e elimina o padrão `try/catch` da camada de UI.

## 7. Como os testes se conectam aos vetores

O arquivo `test-vectors/tvm-vectors.json` é consumido assim em `commonTest`:

```kotlin
class TvmVectorsTest {
    private val engine = CalculatorEngine.Default

    @Test
    fun `all TVM vectors must reproduce published result`() {
        val vectors = loadVectors("test-vectors/tvm-vectors.json")
        for (v in vectors) {
            val setup = buildEvents(v.inputs, v.mode)
            val solve = solveEventFor(v.solveFor)
            val state = engine.reduce(
                state  = CalculatorState().applyFormat(v.format),
                events = setup + solve
            )
            val got = engine.formatDisplay(state, NumericSeparator.PERIOD_COMMA)
            assertEquals(v.expected, got, "vector ${v.id}: ${v.description}")
        }
    }
}
```

Três observações sobre o teste:

1. A comparação é **string == string**. Os vetores guardam `expected` como string precisamente para isso; dispensa tolerância de float e torna o erro de diagnóstico trivial (basta olhar o diff).
2. A lista de eventos `setup + solve` é reconstruída mecanicamente a partir dos `inputs` — nenhum conhecimento especial do problema entra no test code.
3. Cada vetor cobre **uma transição inteira**, do estado inicial até o visor final. Se qualquer passo no meio estiver quebrado, o vetor falha.

A Fase 1 entra em `commonTest` com dois níveis de granularidade: (a) testes de unidade por função pura (cada transição de pilha, cada op), e (b) estes testes integrados sobre `tvm-vectors.json`. A cobertura combinada é o que nos dá confiança de "bit-idêntico à HP".

## 8. Regras para evoluir este contrato

Mudanças nesta interface **quebram** testes e implementação simultaneamente. Por isso:

1. Adicionar evento novo → adicionar subclasse da sealed class, tratar no reducer, criar teste. Uma PR.
2. Modificar assinatura de `reduce` → atualizar `PROMPT_MESTRE.md` Seção 3 (Regras de engenharia), skill `hp12c-simulator` Seção 1 (Propósito), e todos os testes. É uma PR grande e deve ser evitada; prefira adicionar métodos de conveniência a quebrar `reduce`.
3. Renomear campo de `CalculatorState` → exige migração de memória contínua persistida (`kotlinx.serialization` tem hooks para isso, mas é trabalho). Evite em versões já distribuídas no Play Store / App Store.

O critério: **a interface pública é um ativo** e seu custo de mudança cresce com o tempo. Vale a pena gastar tempo agora para chegar num desenho que aguente Fase 1 e Fase 2 sem cirurgia grande.

## 9. O que ainda não está aqui (honestidade explícita)

Este contrato é deliberadamente parcial. Listo o que ficou de fora, por quê, e onde entrará:

- **Iteração interna de `Solve.I` e IRR** (Newton-Raphson com tolerâncias da HP): será detalhada em `formulas/npv-irr.md` na Fase 2. A assinatura pública não muda — só a impl do reducer ganha um caso novo.
- **Interface `Skin`** dos dois layouts `classic`/`modern`: vai em `arquitetura/ui-skins.md`, separado; a engine não precisa conhecer a skin.
- **Formato de serialização da memória contínua**: `@Serializable` em todos os data classes resolve 80%; os 20% restantes (versionamento, compatibilidade entre versões do app) serão tratados quando publicarmos a primeira build.
- **Modo ALG** (algébrico): placeholder em `Event` já deixado. O desenho do flag `algMode: Boolean` em `CalculatorState` e a reinterpretação de `Event.Arith.*` quando ligado é tarefa da Fase 2.
- **Modo PRGM** (Fase 3): `ProgramState` já está como campo sealed, mas só tem `Idle`. O alfabeto de eventos de programação (`Goto`, `Gsb`, `R/S`, `SST`, `BST`, rótulos) será adicionado quando chegarmos lá.

A omissão não é esquecimento — é uma decisão de não sobre-projetar agora o que não sabemos ainda.
