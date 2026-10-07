# Planilhas de rotulagem: para que servem, estado e o que falta

Servem para **medir o modelo fora do FactNews** (outros assuntos, veículos e gêneros) com rótulos dados por uma pessoa (F, C ou E).
Elas **não treinam** o modelo. Detalhes e resultados: `DOCUMENTACAO.md` (seções 8 e 8b).

## Estado (07/10/2026)

| Planilha | Frases | Estado | O que já se sabe |
|---|---:|---|---|
| **Piauí** (uma reportagem; `scripts/11_active_sample.py`) | 100 | Preenchida **duas vezes** por origens diferentes | Versão A: precisão dos destaques 0,79 (IC 95% 0,62 a 0,90), revocação estimada 0,56. Versão "final": 0,55 e 0,35. As duas concordam pouco entre si (kappa 0,18) e a "final" tem sinais de rótulos desalinhados (falas entre aspas marcadas como F). **Qual vale ainda não foi esclarecido.** |
| **Mista** (79 textos, 10 editorias de notícia + opinião, análise e reportagem; `scripts/17_mixed_sheet.py`) | 926 | **Gerada, ainda NÃO preenchida** | Sem resultado. É a que dá a visão geral por assunto e gênero. |

## O que falta (pendente)

1. **Preencher a planilha mista** (coluna `rotulo`: F, C ou E) e rodar `python scripts/17_mixed_sheet.py --score arquivo.xlsx`.
2. **Esclarecer a divergência da Piauí** (duas versões com 53% de rótulos diferentes) e, de preferência, ter **dois rotuladores nas mesmas frases** para medir a concordância entre eles.
3. Decidir **onde guardar as planilhas** (ver abaixo).

## Onde as planilhas ficam (e por quê não estão neste repositório)

As planilhas completas têm **frases de matérias de terceiros** (Piauí, CartaCapital, Brasil de Fato, The Conversation, Revista Oeste, Agência Brasil).
Por isso ficam em `data/rotulagem_humana/`, que **não é versionada**, e foram enviadas por fora a quem rotula. O que **é** versionado:

- `reports/rotulos_aproximados/`: rótulos que o Claude deu em amostras do FactNews (sem texto de matérias) e a concordância com o gabarito humano;
- `reports/externos_manifesto.json`: endereços e SHA-256 dos textos externos usados nos experimentos;
- os scripts que geram e pontuam as planilhas.

Se o repositório for **privado** e vocês quiserem versionar as planilhas completas, basta tirar `data/` do `.gitignore` só para essa pasta; em repositório público isso
significaria publicar trechos de matérias com direitos autorais. Alternativa segura em qualquer caso: guardar só `id`, rótulo, endereço da matéria e o SHA-256 da frase.

## Como regenerar

Com o servidor do modelo ligado (`scripts/start-factnews-server.ps1` ou `.sh`) e o ambiente do README:

```
python scripts/08_external_texts.py            # baixa os textos externos (só para data/external)
python scripts/11_active_sample.py --text-file data/external/revista_analise/<arquivo>.txt --name piaui   # planilha de uma reportagem
python scripts/17_mixed_sheet.py               # planilha mista (~1000 frases); gera misto_para_rotular.xlsx e misto_chave.csv
python scripts/17_mixed_sheet.py --score misto_preenchida.xlsx
```

A planilha **não mostra** a saída do modelo a quem rotula. A chave (`*_chave*.csv`) fica separada e é usada só na pontuação.
Os feeds mudam com o tempo: a planilha mista gerada em outro dia terá outros textos.

## Instruções para quem rotula

- **F (fato):** a frase relata uma informação ou acontecimento, sem juízo de valor do autor.
- **C (citação):** reproduz o que alguém disse ou escreveu (entre aspas ou em discurso indireto: "disse que", "segundo...").
- **E (enviesada):** linguagem carregada, avaliação ou opinião do próprio autor (adjetivos de juízo, ironia, ênfase, rótulos pejorativos ou elogiosos).
- Dúvida: escolha a opção mais próxima, sem deixar em branco. Lixo do site (menu, aviso, chamada para assinar): F.
- Rotule sem procurar o texto original e sem tentar adivinhar o que um programa diria.

## Limites

Poucos destaques do modelo por texto (≈85 na planilha mista) deixam os intervalos largos, e os números por assunto indicam tendência, não prova.
Rótulos de uma pessoa só medem o critério dela: com duas versões da Piauí, a precisão variou de 0,55 a 0,79. Se quem rotula for outra IA e não uma pessoa, a medida é aproximada.
