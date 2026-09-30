# Corpus de capturas: leitura e foco da pesquisa

Este corpus reúne seis capturas enviadas em 30/09/2026 e a captura anterior da matéria sobre Lei Seca. Elas estão em `app/src/androidTest/assets/eval-*.png`; a saída **real do ML Kit no emulador** está em `app/src/test/resources/ocr/eval-*.json`. As expectativas de cada caso ficam em `app/src/test/resources/ocr/evaluation-corpus.json`.

O corpus avalia quatro etapas separadamente: presença dos detalhes no OCR, identificação de post ou matéria, preservação do foco na sugestão editável e isolamento de respostas ou elementos da interface. **O texto visível nos prints não foi verificado como fato**; nenhum caso recebe rótulo de verdadeiro ou falso. O teste de avaliação gera um relatório sem reprovar a compilação por falhas de qualidade já conhecidas. Isso permite acompanhar a evolução sem esconder os erros nem quebrar a suíte regular.

## Linha de base

No emulador atual: **7/7** tipos identificados, **6/7** com todos os detalhes essenciais no OCR, **6/7** com todos esses detalhes na sugestão e **6/7** sem os elementos indevidos marcados. O [relatório completo](BASELINE.md) mostra o resultado de cada captura.

| Captura | Desafio principal | Resultado inicial |
| --- | --- | --- |
| Post sobre Huck e Vorcaro | Isolar a resposta de outro perfil | Foco preservado; `URGENTE` teve ruído de OCR (`EURGENTE`) |
| Post sobre almoço | Isolar resposta e fotos | Foco preservado; OCR juntou `foi o` em `foio` |
| Post sobre sobretaxa | Preservar 55%, produto e referência temporal | Foco preservado; texto da foto não substituiu o post |
| Post sobre Flamengo | Não usar a consulta da barra de busca como afirmação | Foco preservado |
| Matéria g1 sobre investigação | Separar manchete, anúncio e cookies | OCR leu `OpenAI` como `OpenAl`; requer revisão/correção editável |
| Matéria g1 sobre WhatsApp | Não usar texto dentro da imagem como manchete | Foco preservado |
| Matéria sobre Lei Seca | Manter estados e `1º turno` após os dois-pontos | Foco preservado; botão “Adicione o Metrópoles” ainda entra no corpo |

As métricas verificam detalhes essenciais, não igualdade literal. Pequenos erros de OCR como `foio` ficam explícitos no relatório, mas não derrubam a métrica de foco se as entidades e a ação continuarem legíveis. A origem da falha de `OpenAI` é o OCR, pois o erro já aparece na fixture antes da escolha da afirmação. O botão da matéria sobre Lei Seca é um problema de organização dos blocos; a sugestão principal não foi contaminada.

## Reproduzir

Na pasta deste projeto Android:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' bash gradlew :app:testDebugUnitTest --tests '*CaptureCorpusEvaluationTest*'
```

O relatório atualizado sai em `app/build/lume-corpus/capture-baseline.md`. Para regenerar as fixtures após atualizar o ML Kit ou as imagens, execute `OcrFixtureDumpTest` no emulador e copie os arquivos `eval-*.json` de `/sdcard/Android/data/com.example.lumeocrtest/files/ocr-fixtures/` para `app/src/test/resources/ocr/`. Alterações no OCR exigem uma nova linha de base; alterações só na seleção da afirmação podem usar as fixtures existentes.

Para ampliar a coleção, adicione capturas de outros veículos, layouts, tamanhos de fonte, posts com texto em imagens, recortes incompletos e casos que já funcionam. Registre antes o foco visível esperado e os elementos que não devem entrar nele. Aumentar o corpus não substitui avaliar separadamente se as fontes encontradas realmente esclarecem a afirmação.
