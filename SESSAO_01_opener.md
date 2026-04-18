# Opener — Sessão 01: Criar skill `hp12c-simulator`

> **Como usar:** Abra uma nova sessão no Cowork, anexe a mesma pasta de trabalho (a que contém este arquivo e o `PROMPT_MESTRE.md`), confirme que os 3 PDFs estão em `/mnt/uploads/`, e cole o conteúdo do bloco **PROMPT** abaixo como primeira mensagem.

---

## PROMPT (copiar a partir daqui)

```text
Estou construindo um simulador perfeito da calculadora HP 12C Platinum 
(Android primeiro, iOS depois, via Kotlin Multiplatform). Esta é a 
Sessão 01 do projeto — Fase 0, passo 1 do roadmap.

## Contexto

- Leia PRIMEIRO o arquivo PROMPT_MESTRE.md na pasta do projeto 
  (hp12c-simulator/PROMPT_MESTRE.md). Ele tem todas as decisões 
  arquiteturais já tomadas, o roadmap em 4 fases e os invariantes 
  não-negociáveis do projeto. Não prossiga sem tê-lo lido.

- Material de apoio já disponível em /mnt/uploads/:
  - bpia5314.pdf — Manual oficial HP 12C Platinum (pt-BR)
  - hp12c-matematica-financeira-apostila.pdf — Apostila Prof. Moretti
  - livromfhp12c.pdf — Livro FURG

## Objetivo desta sessão

Criar a skill `hp12c-simulator` via skill-creator, com a estrutura 
definida na Seção 4 do PROMPT_MESTRE.md, e populá-la com um primeiro 
conteúdo mínimo viável que nos permita iniciar a Fase 1 (engine mínima) 
na próxima sessão.

## Entregáveis concretos ao final da sessão

1. Skill `hp12c-simulator` criada (rodar skill-creator) com:
   - SKILL.md descrevendo propósito, quando invocar, e como usar o 
     conteúdo de formulas/, test-vectors/, referencias/, arquitetura/.
   - Pastas: formulas/, test-vectors/, referencias/, arquitetura/.
2. Conteúdo seed mínimo:
   - formulas/tvm.md — equação TVM da HP12C e fórmulas para resolver 
     cada uma das 5 variáveis (n, i, PV, PMT, FV) nos dois modos 
     (BEGIN/END). Extrair do manual oficial (Seção 3).
   - test-vectors/tvm-vectors.json — mínimo 10 vetores extraídos do 
     manual e da apostila Moretti, no formato:
       {
         "id": "tvm-001",
         "source": "bpia5314.pdf, Seção 3, Exemplo X, p. NN",
         "description": "...",
         "inputs": { "n": ..., "i": ..., "PV": ..., "PMT": ..., "FV": ..., "mode": "END|BEGIN" },
         "solve_for": "n|i|PV|PMT|FV",
         "expected": "string com o valor exato esperado",
         "format": "FIX 2"
       }
   - referencias/bcd-rounding.md — regras de arredondamento HP 
     (10 dígitos BCD, HALF_EVEN, casos de borda).
   - referencias/error-codes.md — tabela Error 0..9 com condições 
     de disparo.
   - referencias/stack-behavior.md — semântica da pilha RPN de 4 níveis 
     (X, Y, Z, T, LAST X) em ENTER, operações binárias, CLx, etc.
3. Atualização da tabela "Progresso" em PROMPT_MESTRE.md com esta 
   sessão marcada como concluída.
4. Próximo passo sugerido em uma frase no final de PROMPT_MESTRE.md.

## Regras desta sessão

- Nada de código Kotlin ainda. Esta sessão é 100% de extração, 
  documentação e estruturação de skill.
- Cada vetor de teste DEVE citar a fonte (arquivo + seção + página). 
  Vetor sem fonte rastreável não entra.
- Se algum exemplo dos PDFs tiver ambiguidade (ex.: manual mostra 
  resultado com FIX 2 mas gabarito da apostila arredondou diferente), 
  registre a ambiguidade em referencias/bcd-rounding.md em vez de 
  escolher arbitrariamente.
- Use a skill skill-creator desde o início. Não crie arquivos soltos 
  "por fora" que depois teríamos que migrar.

## Como começar

1. Leia PROMPT_MESTRE.md integralmente.
2. Invoque a skill skill-creator.
3. Siga o fluxo da skill para criar hp12c-simulator.
4. Extraia o conteúdo dos PDFs conforme a lista de entregáveis acima.
5. Reporte o resultado final, incluindo quantos vetores foram extraídos 
   por PDF e quaisquer ambiguidades encontradas.
```

---

## Checklist antes de abrir a sessão nova

- [ ] Pasta de trabalho selecionada contém `PROMPT_MESTRE.md` e este opener.
- [ ] Os 3 PDFs estão em `/mnt/uploads/` na nova sessão (reanexe se necessário).
- [ ] Skill `skill-creator` está listada em available_skills.
- [ ] Você já copiou o bloco PROMPT acima para o clipboard.
