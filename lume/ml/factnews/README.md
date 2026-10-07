# FactNews — rotulador de frases (factual / citação / enviesada)

> **Registro completo de tudo (decisões, medidas, o que não deu certo, pendências): [`DOCUMENTACAO.md`](DOCUMENTACAO.md).**
> **Planilhas de rotulagem (para que servem, estado e o que falta): [`PLANILHAS.md`](PLANILHAS.md).**

Treina um modelo que, dada **uma frase de notícia em português**, diz se ela é um **fato relatado**, uma
**fala citada** ou um **trecho enviesado**. Não decide se algo é verdadeiro: "factual" quer dizer que a frase
relata algo, não que seja correto. Não sobrepõe o classificador de veracidade do desafio (`src/`, LIAR2).

Segue a disciplina do desafio: baseline honesto, caça a vazamento e **teste lacrado até o fim**.

## Estado (06/10/2026)

| Etapa | Situação |
|---|---|
| Dados baixados e verificados (checksum) | pronto |
| Auditoria dos dados (`reports/auditoria.md`) | pronta |
| Divisão por história, com teste lacrado | pronta e testada (8 testes) |
| Baseline TF-IDF + regressão logística | **executado** (`reports/baseline_dev.md`) |
| Ajuste fino do BERTimbau (`scripts/03_finetune_bert.py`) | **executado**: validação cruzada por história e treino final (resultados abaixo) |
| Contexto (manchete), conjunto de 5 sementes, limiar de "enviesada" | **testados só no desenvolvimento; nenhum passou na regra de decisão** (seção abaixo) |
| Modelo exportado (`models/factnews-v2`) e `scripts/predict.py` | **prontos e testados** (carregamento e linha de comando) |
| Calibração da confiança, que fixa as regras do app (`scripts/06_calibration.py`) | **feita** só no desenvolvimento (`reports/calibracao_dev.md`) |
| Servidor local (`scripts/serve.py`) e scripts de partida (Windows e macOS) | **prontos**; Windows testado com o app no emulador, **macOS não testado** |
| Integração no app Lume (seção "Linguagem do texto") | **funcionando no emulador, só neste PC** (ver `prototypes/ocr-native-android/SETUP.md`) |
| Pacote de pesos para outro computador (`package_weights.py`, `fetch_weights.py`, `weights.json`) | **pronto e testado** com um servidor HTTP local; **a Release do GitHub ainda não foi publicada** |
| Ideias 1 a 4 (detector de estilo desconhecido, rotulagem, léxico, contraste entre veículos) | **testadas; nenhuma passou no critério fixado antes** (ver `DOCUMENTACAO.md`, seção 8). Ideia 6 (voto local): só especificada |
| Segunda rodada (robustez a jornal/época/assunto, calibração, conforme, tipologia de erros, atalhos, anotadores) | **feita** (`DOCUMENTACAO.md`, seção 8b): generaliza entre jornais, depende do assunto, confiança bruta alta demais (a tela agora mostra a precisão medida) |
| Medida no teste | **feita uma vez** em 06/10/2026 (registrada em `reports/test_usage.log`); não repetir para escolher modelo |

### Baseline no desenvolvimento (4.942 frases, 81 histórias, 4 folds)

| Modelo / validação | F1 macro | factual | citação | enviesada |
|---|---:|---:|---:|---:|
| Dummy (classe mais frequente) | 0,272 | 0,817 | 0,000 | 0,000 |
| TF-IDF + LR, **por história** (correto) | **0,608** | 0,874 | 0,737 | 0,213 |
| TF-IDF + LR, ao acaso (inflado) | 0,687 | 0,889 | 0,806 | 0,367 |

Dividir ao acaso infla o F1 macro em ~0,08 (e o da classe enviesada em ~0,15). A classe "enviesada" é a difícil:
o baseline acerta só 16% delas (revocação 0,162). O artigo original reporta F1 de 0,67 para viés com BERT, mas
validou com 10-fold sem separar por história e com subamostragem; não é comparável com o número acima.

### BERTimbau (`neuralmind/bert-base-portuguese-cased`), mesma divisão

| Medida | F1 macro | factual | citação | enviesada |
|---|---:|---:|---:|---:|
| Validação por história (dev, 4 folds) | **0,804** (folds 0,78 a 0,82) | 0,938 | 0,921 | 0,552 |
| **Teste lacrado** (19 histórias, 1.249 frases) | **0,796** | 0,922 | 0,899 | 0,568 |

Hiperparâmetros fixados antes de ver resultados (lr 2e-5, lote 32, 4 épocas, max_len 128), sem busca. Enviesada: precisão 0,65 e
revocação 0,50 no teste, ou seja, o modelo perde metade dessas frases. Reportado em `reports/bert_cv.json` e `reports/bert_teste.json`.
O baseline TF-IDF não foi medido no teste (só no desenvolvimento), então a comparação direta é feita na validação (0,608 contra 0,804).
O salto sobre o baseline é grande; a checagem de atalhos por subgrupo está na seção seguinte.

### Tentativas de melhorar (só no desenvolvimento; o teste não foi relido)

Regra fixada **antes** de ver os resultados (`scripts/05_analyze.py`): uma mudança só é adotada se o intervalo de 95% da
diferença em F1 macro (bootstrap pareado por história, 2.000 reamostragens) exclui zero. Achado que muda o plano: no CSV as
frases de cada artigo estão em **ordem alfabética**, então "frase anterior/posterior" não existe; o contexto testado foi a
**manchete** do artigo.

| Variante (4 folds por história) | F1 macro por semente (5 sementes) | "enviesada" F1 | Conjunto das 5 sementes |
|---|---|---:|---:|
| Sem contexto (configuração validada) | 0,806 ± 0,004 (0,801 a 0,810) | 0,559 | 0,806 |
| Com a manchete como contexto | 0,794 ± 0,009 (0,779 a 0,799) | 0,537 | 0,802 |

| Comparação pareada | Diferença em F1 macro | IC 95% | Decisão |
|---|---:|---|---|
| Manchete como contexto (conjunto) | −0,005 | [−0,015; +0,006] | não adotar |
| Manchete como contexto (semente única) | −0,012 | [−0,022; −0,003] | piora |
| Conjunto de 5 sementes − semente única | +0,0002 | [−0,005; +0,005] | não adotar |
| Limiar de "enviesada" (escolhido entre folds) − sem limiar | +0,005 | [−0,002; +0,013] | não adotar |

Ajustes simples também não mudam nada: lr 3e-5 → 0,805 e 6 épocas → 0,802, contra 0,804 da base (`reports/bert_cv_*.json`).
A variação entre sementes (±0,004) e o IC das comparações mostram que diferenças abaixo de ~0,01 são ruído.

**O limiar é uma escolha de uso, não um ganho comprovado.** Multiplicar a probabilidade de "enviesada" troca precisão por revocação
(semente 42, desenvolvimento, 421 enviesadas reais):

| Multiplicador | Precisão | Revocação | F1 "enviesada" | F1 macro | Marcadas |
|---:|---:|---:|---:|---:|---:|
| 1,0 (padrão) | 0,613 | 0,530 | 0,568 | 0,809 | 364 |
| 2,0 | 0,579 | 0,591 | 0,585 | 0,814 | 430 |
| 2,5 | 0,573 | 0,608 | 0,590 | 0,815 | 447 |

**Checagem de atalhos por subgrupo** (semente 42, fora-da-dobra, F1 macro):
- Veículo: Folha 0,822, Estadão 0,808, O Globo 0,793, sem queda em nenhum. Ano e veículo não são entradas do modelo.
- Editoria: esportes 0,860 (26% das frases são enviesadas), política 0,794, mundo 0,783, cultura 0,784 (161 frases),
  cotidiano 0,668 (359) e ciência 0,645 (75; o modelo previu 1,3% de enviesadas contra 9,3% reais). Parte do desempenho em
  "enviesada" acompanha o assunto (esportes), o que é plausível, mas não foi separado do estilo da frase.
- Manchetes: 0,697, contra 0,815 no corpo.
- Citação sem aspas: revocação 0,823 e precisão 0,854 (com aspas: 0,977 e 0,959). O modelo não depende só das aspas.

### Regras do app derivadas da calibração (`reports/calibracao_dev.md`)

O app Lume usa o modelo para um **sinal de linguagem do texto**, nunca para um veredito. Os limites vêm de medidas fora-da-dobra
no desenvolvimento (5 sementes), não de chute:

| Pergunta | Medida | Regra no app |
|---|---|---|
| Confiança alta em **manchete** "enviesada" é confiável? | **Não**: precisão 0,55 com 0,70 e 0,49 com 0,85 (15 manchetes), sem melhorar com a confiança | O **título nunca conclui sozinho**: no máximo "indício fraco" (confiança ≥ 0,70) e o corpo é lido |
| Frase do corpo com "enviesada" e confiança ≥ 0,80 | precisão 0,69, cobertura 0,42 | Vira "trecho com possível viés" (sugestão, com a confiança) |
| Quantas frases destacadas indicam viés na matéria? | Com ≥ 2 destaques **e** ≥ 10% do texto: 94% das matérias tinham ao menos uma enviesada real e 87% duas ou mais (só pela contagem: 92% e 83%) | "Sinal de viés" exige as duas condições; 1 destaque, ou poucos para o tamanho do texto, é "inconclusivo" |
| Frases em que o modelo fica em dúvida (confiança < 0,70) | ~3% por matéria (p90: 10%) | Mais de 25% em dúvida → "inconclusivo" (texto com ruído) |
| Textos curtos | a medida vale para 8 a 69 frases (maior matéria da base: 69) | Menos de 8 frases → "inconclusivo"; mais de ~70 → aviso de que o resultado é menos seguro |

Estados: **sinal de viés**, **sem sinal de viés** (não prova neutralidade: o modelo deixa passar cerca de metade dos trechos
enviesados), **inconclusivo**, **sem texto para analisar** e **indisponível** (servidor do modelo desligado; o app segue
funcionando). "Tom de relato" (a classe `factual`) descreve o jeito de escrever, **não** que o fato seja verdadeiro.

**Caixa alta:** só 15 de 6.191 frases do treino estão em CAIXA ALTA, e nesse formato o modelo muda de resposta ("xerife das
composições nas redes sociais" vai de enviesada 0,98 para citação 0,96). Títulos lidos por OCR costumam vir assim, então
`factnews/text.py` converte para caixa normal antes de classificar (custo: nomes próprios no meio da frase perdem a inicial).

## Dados

- **FactNews v2.0.0**, Vargas, Jaidka, Pardo e Benevenuto (RANLP 2023). 6.191 frases de 100 histórias; cada história
  sai em Folha, Estadão e O Globo (2006–2008 e 2021–2022). Licença **CC BY 4.0**.
  Registro: https://zenodo.org/records/10794023 · repositório: https://github.com/franciellevargas/FactNews
- **Cite** o artigo: https://aclanthology.org/2023.ranlp-1.127
- Arquivo usado: `dataset/factnews_dataset.csv` (colunas `file, id_sente, id_article, domain, year, sentences, classe`).
  `classe`: **0 = factual (4.242), −1 = citação (1.391), 1 = enviesada (558)**. Os modelos usam 0, 1, 2.
- **Por que a v2.0.0:** o zip da v3.0.0 (o mais recente no Zenodo) só traz `annotators/` e o readme, **sem o texto das frases**.
  O espelho do Hugging Face (`eduagarcia/FactNews`) é uma versão modificada com licença "unknown"; não é usado.
- Os dados ficam em `data/` (não versionado). Reproduza com `scripts/00_download_data.py`.

### O que a auditoria encontrou (`reports/auditoria.md`)

- **Manchetes são linhas do dataset:** 656 frases vêm de arquivos `_titulo` (35 citações, 56 enviesadas, 565 factuais).
- **Identificação:** na história c78 o artigo do Estadão tem o id `c78o`, igual ao de O Globo (15 frases). O veículo é
  tirado do nome do arquivo quando existe. 82 linhas têm data no lugar do nome.
- **Duplicatas:** 252 frases em 116 grupos de texto idêntico; 7 grupos com rótulos conflitantes.
- **Gêmeas entre veículos:** só 4,9% das frases têm uma gêmea (similaridade de caracteres ≥ 0,8) em outro veículo da mesma
  história, e 96,7% delas têm o mesmo rótulo. O vazamento por história existe, mas é menor do que eu supunha.
- **Anotadores — discrepância não resolvida:** o arquivo de anotadores do repositório dá kappa de **0,98** (53 discordâncias
  em 6.191), enquanto o artigo reporta 0,82. Em 31 frases o rótulo final difere do que os dois anotadores marcaram.
  Não sei qual das duas fontes reflete a anotação original; o treino usa o rótulo final do CSV de frases.
- **Atalhos possíveis:** 61,6% das citações têm aspas (e só ~6% das demais); a taxa de enviesadas varia por editoria
  (esportes 20,8%, cultura 12,6%, política 7,7%, cotidiano 2,4%) e um pouco por veículo (Folha 10,6%, Estadão 8,8%,
  O Globo 7,5%). `domain`, `year`, `file` e `id_article` **não** são entradas do modelo.
- **Ordem das frases:** dentro de cada artigo as frases estão em **ordem alfabética**, não na de leitura (a ordem original se
  perdeu). `id_sente` não serve para achar a frase anterior ou a posterior. Descoberto em 06/10/2026, ao tentar usar vizinhança
  como contexto; uma afirmação anterior minha de que `id_sente` dava a ordem estava errada.

## Regras deste projeto

1. **Divisão por história** (`factnews/splits.py`): todas as frases de uma história ficam do mesmo lado. O fold 0
   (19 histórias, 1.249 frases) é o **teste**; os folds 1–4 são o desenvolvimento. Semente 42; as histórias de teste
   estão em `splits/test_stories.txt`.
2. **Teste lacrado:** só é lido com `--use-test`, e cada leitura é registrada em `reports/test_usage.log`.
   Decida o modelo no desenvolvimento e meça no teste **uma vez**. Histórico: uma medida (10:28) e um uso como treino do
   modelo exportado (11:25), que não é medida. As comparações da seção "Tentativas de melhorar" não tocaram o teste.
3. Nenhum resultado aqui diz que o modelo "detecta fake news".

## Como reproduzir

Python 3.11. **Windows (PowerShell):**

```powershell
cd lume\ml\factnews
py -3.11 -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python scripts\00_download_data.py     # baixa 0,4 MB do Zenodo e confere o checksum
.\.venv\Scripts\python scripts\01_audit.py
.\.venv\Scripts\python scripts\02_baseline.py
.\.venv\Scripts\python -m unittest discover -s tests -v
```

**macOS (bash/zsh):**

```bash
cd lume/ml/factnews
python3.11 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python scripts/00_download_data.py && python scripts/01_audit.py && python scripts/02_baseline.py
python -m unittest discover -s tests -v
```

(Os passos do macOS não foram executados em um Mac.)

### Ajuste fino do BERTimbau (executado em 06/10/2026)

1. Instale o PyTorch e as dependências — veja `requirements-bert.txt`. No Windows com a RTX 5060 Ti (Blackwell) é preciso
   CUDA 12.8 ou superior: `pip install torch==2.11.0 --index-url https://download.pytorch.org/whl/cu128`
   (versão listada no índice; a instalação tem alguns GB).
2. `python scripts/check_env.py` confirma versões e GPU.
3. Teste de fumaça: `python scripts/03_finetune_bert.py --cv --epochs 1 --limit 400` (baixa o modelo, ~438 MB, licença MIT).
4. Validação cruzada por história: `python scripts/03_finetune_bert.py --cv`.
5. Só depois de decidir: `python scripts/03_finetune_bert.py --final --use-test`.
6. Comparar variantes com várias sementes (só desenvolvimento, ~20 min cada na RTX 5060 Ti):
   `python scripts/04_context_ensemble.py --cv --ctx none --tag none` e `... --ctx headline --tag headline`,
   depois `python scripts/05_analyze.py` (gera `reports/comparacao_dev.md` e `reports/decisao_dev.json`).

### Usar o modelo treinado

O modelo exportado (`models/factnews-v2`, ~209 MB em fp16) **não vai para o Git**. Há duas formas de tê-lo:

**A) Baixar o pacote de pesos** (o mesmo modelo, bit a bit; ~192 MB). Só biblioteca padrão do Python, igual no Windows e no macOS:

```bash
python scripts/fetch_weights.py             # baixa da Release do GitHub indicada em weights.json e confere o SHA-256
python scripts/fetch_weights.py --file factnews-v2-weights.zip   # ou instala de um .zip que você já tem
python scripts/fetch_weights.py --verify    # só confere a instalação atual
```

Se o SHA-256 não bater, nada é instalado. Testado aqui com um servidor HTTP local: o modelo instalado pelo pacote deu
probabilidades **idênticas** às do original. **A Release ainda não foi publicada**, então o download pelo endereço de
`weights.json` dará erro 404 até lá (use `--file`).

**B) Treinar de novo** (~2 min na RTX 5060 Ti; no Mac será mais lento e os pesos não saem idênticos):

```powershell
.\.venv\Scripts\python scripts\04_context_ensemble.py --export --use-test --ctx none --seeds 42 --tag factnews-v2
```

Depois, rotular frases pela linha de comando ou ligar o servidor para o app:

```powershell
.\.venv\Scripts\python scripts\predict.py "O Senado aprovou o projeto por 45 votos a 30."
# frases com aspas: use arquivo (uma frase por linha, UTF-8); o PowerShell 5.1 apaga aspas duplas dentro de argumentos
.\.venv\Scripts\python scripts\predict.py --json --file frases.txt
# servidor local do app (escuta só em 127.0.0.1:8765; também faz `adb reverse tcp:8765 tcp:8765`)
.\scripts\start-factnews-server.ps1
```

No macOS: `python` no lugar de `.\.venv\Scripts\python` e `bash scripts/start-factnews-server.sh` (ou `chmod +x` e `./scripts/...`) (usa o backend MPS; **não testado em um Mac**).

API do servidor (`scripts/serve.py`): `GET /health` e `POST /classify` com `{"sentences": ["...", ...]}` (JSON, até 300 frases e
1 MB por chamada). Devolve `{"results": [{"label", "confidence", "probs"}, ...]}`. Só aceita requisições cujo `Host` seja
`127.0.0.1` ou `localhost` e `Content-Type: application/json` (contra páginas web maliciosas no navegador); não registra o texto.

Para gerar o pacote de pesos (a publicação da Release é uma ação pública, feita só com decisão explícita): `python scripts/package_weights.py`
(zip determinístico, `weights.json` com o SHA-256 do pacote e de cada arquivo). O pacote leva `NOTICE.txt` e `BERTimbau-MIT.txt`
(atribuições: BERTimbau, MIT; FactNews, CC BY 4.0).

O modelo exportado foi treinado nas 100 histórias, então **não tem medida própria em dados separados**: os números reportados
acima são da mesma receita treinada só no desenvolvimento. Em `models/factnews-v2/factnews_config.json`, o campo
`decision_weights` (padrão `[1, 1, 1]`) permite o limiar da tabela acima, por exemplo `[1, 1, 2.5]`.

## Estrutura

```
factnews/            código reutilizável (data.py, splits.py, text.py, weights.py)
scripts/             00 download · 01 auditoria · 02 baseline · 03 ajuste fino · 04 contexto/sementes/exportação ·
                     05 análise pareada · 06 calibração · predict (inferência) · serve (servidor local) ·
                     start-factnews-server.ps1/.sh · package_weights · fetch_weights · check_env
tests/               garantias do pipeline, da API do servidor e do pacote de pesos (unittest)
splits/              histórias do teste (versionado)
reports/             auditoria, baseline, resultados do BERT, probabilidades fora-da-dobra (oof_*.npz), comparação e calibração
licenses/ NOTICE-weights.txt   atribuições que acompanham os pesos
weights.json         endereço e SHA-256 do pacote de pesos (versionado)
models/ dist/        pesos exportados e pacote (NÃO versionados)
data/                dados baixados e divisão (NÃO versionado)
```

## Limites

- 100 histórias, política = 62,6% das frases; períodos 2006–2008 e 2021–2022. Sem garantia fora desse recorte.
- Poucas frases enviesadas (558): as métricas dessa classe têm variância alta.
- "Enviesada" é um julgamento subjetivo (critérios AllSides); o teto de desempenho é limitado por isso.
- "Factual" não implica verdade e a frase "factual" não é, por si, a que deve ser checada: isso não foi anotado.
- Manchetes (0,70), cotidiano e ciência têm desempenho pior; "enviesada" perde cerca de metade das frases (revocação ~0,5).
- Frases de teste **inventadas por mim** (não é medida): opiniões explícitas e ofensivas, fora do estilo jornalístico do
  corpus, saíram como "citação" com confiança 0,62 e 0,75 (com "enviesada" em segundo) e, em outra, com 0,98: baixa confiança
  não é garantida nesses casos. Não use o modelo como detector de opinião em texto que não seja notícia desses jornais.
- **Matérias longas e revistas:** a maior matéria da base tem 69 frases. Uma reportagem de revista com 153 frases foi lida no app
  (19% das frases com sinal de viés); como o gênero (texto interpretativo) e o tamanho estão fora do que foi medido, o app avisa
  que o resultado é menos seguro.
- **Afirmações digitadas** (por exemplo "X matou 700 mil pessoas") saem como "tom de relato": o modelo descreve o jeito de escrever
  e não detecta falsidade. Isso é do classificador de veracidade (separado), não deste.
