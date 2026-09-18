# Dados

Os datasets **não são versionados** neste repositório (arquivos grandes ficam fora do git). Baixe localmente seguindo os passos abaixo.

## LIAR2 (principal)

```bash
# a partir da raiz do repositório:
git clone --depth 1 https://github.com/chengxuphd/liar2.git data/raw/liar2
```

Isso cria `data/raw/liar2/liar2/train.csv`, `valid.csv` e `test.csv` — os caminhos que os notebooks esperam.

Alternativa via Hugging Face: [`chengxuphd/liar2`](https://huggingface.co/datasets/chengxuphd/liar2).

## Estrutura esperada

```
data/
├── raw/           # datasets baixados (ignorado pelo git)
│   └── liar2/liar2/{train,valid,test}.csv
└── processed/     # splits/artefatos gerados pelos notebooks (ignorado pelo git)
```

Nunca faça commit dos arquivos de dados. O `.gitignore` já bloqueia `.csv`, `.xlsx`, `.jsonl`, mas confira antes de dar `git add`.
