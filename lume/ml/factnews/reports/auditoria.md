# Auditoria do FactNews v2.0.0

## Estrutura
- 6191 frases, 100 histórias, 307 nomes-base de arquivo (o readme fala em 300 documentos; corpo e manchete às vezes têm nomes diferentes), 604 arquivos; 656 frases são manchetes.
- Linhas em que a letra do veículo em `id_article` diverge do nome no arquivo: 15 (história c78: artigo do Estadão com id `c78o`, igual ao de O Globo); 82 linhas têm data no lugar do nome.
- Histórias presentes nos 3 veículos: 100 de 100.
- Rótulos: {'factual': 4242, 'citacao': 1391, 'enviesada': 558}.

## Anotadores
- Concordam em 6138 frases; discordam em 53.
- Rótulo do dataset = rótulo dos dois quando concordam: 0.9949 (31 linhas diferem mesmo com os dois de acordo).
- ATENÇÃO: o artigo original reporta kappa 0,82 e o arquivo de anotadores do repositório dá um valor muito maior; não consegui reconciliar. O arquivo pode já refletir uma rodada de revisão.
- Kappa de Cohen (anotador 1 x 2): 0.982.
- Kappa um-contra-resto: factual 0.980, citacao 1.000, enviesada 0.946.
- Rótulo final nas 53 discordâncias: {'enviesada': 43, 'factual': 10}.

## Duplicatas exatas (texto normalizado)
- 252 frases em 116 grupos; 7 grupos com rótulos conflitantes.

## Gêmeas entre veículos (mesma história, similaridade de caracteres)
- Fração de frases com uma gêmea em outro veículo: >= 0.6: 10.2%, >= 0.8: 4.9%, >= 0.9: 3.3%.
- Quando a similaridade é >= 0,8, o rótulo é o mesmo em 96.7% dos casos (305 pares).

## Manchetes x corpo

| is_title   |   factual |   citacao |   enviesada |
|:-----------|----------:|----------:|------------:|
| corpo      |      3677 |      1356 |         502 |
| manchete   |       565 |        35 |          56 |

## Atalhos possíveis

| label_name   |   frases |   com_aspas |   palavras_mediana |   palavras_media |
|:-------------|---------:|------------:|-------------------:|-----------------:|
| factual      |     4242 |       0.059 |                 18 |           20.396 |
| citacao      |     1391 |       0.616 |                 14 |           17.391 |
| enviesada    |      558 |       0.066 |                 20 |           22.172 |

Taxa de frases enviesadas por veículo / editoria / ano:

| outlet   |   mean |   size |
|:---------|-------:|-------:|
| Estadão  |  0.087 |   2093 |
| Folha    |  0.106 |   2175 |
| O Globo  |  0.075 |   1923 |

| domain   |   mean |   size |
|:---------|-------:|-------:|
| culture  |  0.126 |    412 |
| daily    |  0.024 |    413 |
| politics |  0.077 |   3873 |
| science  |  0.057 |    123 |
| sports   |  0.208 |    490 |
| world    |  0.099 |    880 |

|   year |   mean |   size |
|-------:|-------:|-------:|
|   2006 |  0.081 |    676 |
|   2007 |  0.087 |   1996 |
|   2008 |  0.077 |     39 |
|   2021 |  0.076 |    277 |
|   2022 |  0.095 |   3203 |
