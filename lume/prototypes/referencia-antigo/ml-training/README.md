# Treinamento experimental com LIAR2

Este diretório treina um baseline local usando somente `statement`. O modelo devolve uma das seis classes originais do LIAR2; a previsão não substitui verificação factual.

## Executar no VS Code

Abra a pasta `ml-training` no VS Code. No terminal integrado, execute:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
python train.py \
  --train /Users/aluno1/Downloads/train.csv \
  --valid /Users/aluno1/Downloads/valid.csv \
  --test /Users/aluno1/Downloads/test.csv
```

O treinamento cria:

- `artifacts/liar2_tfidf_logistic.joblib`: modelo treinado;
- `artifacts/liar2_mobile_model.json.gz`: pacote compacto para futura leitura no Android;
- `artifacts/metrics.json`: métricas completas de validação e teste.

Para testar uma afirmação:

```bash
python predict.py "The unemployment rate is at an all-time low."
```

Para verificar que o pacote móvel reproduz a previsão do Python:

```bash
python verify_mobile_export.py "The unemployment rate is at an all-time low."
```

O LIAR2 está em inglês e contém afirmações curtas. Textos em português vindos do OCR estão fora do domínio de treinamento e podem produzir resultados sem valor, mesmo quando a probabilidade exibida for alta.
