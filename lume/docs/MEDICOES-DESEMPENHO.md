# Medições de desempenho da pesquisa (emulador, Mac, 09/10/2026)

Método: texto enviado pelo "compartilhar" do Android (`am start -a SEND`), campo conferido antes de tocar em Pesquisar,
tempo do toque até o log `status=`; tempos internos pelo log `tempos:` (ResearchService). Script: medir4.sh (scratchpad da sessão).
Notícias reais do dia: o conteúdo pode variar entre execuções.

## Antes (só logs de tempo)
| Pesquisa | toque→resultado | interno | desambiguação | busca | sentido | modelo | páginas | montagem | direto |
|---|---|---|---|---|---|---|---|---|---|
| Lula IR 5 mil | 9 s | 7,1 s | 1,5 | 1,3 | 0,6 | 0,9 | 3,7 | 0,5 | 13 (responde 1) |
| Seleção amistosos | 12 s | 10,0 s | 1,1 | 2,2 | 0,7 | 1,1 | 5,0 | 0,7 | 0 |
| Selic 15% | 13 s | 9,9 s | 1,9 | 1,6 | 0,5 | 0,8 | 6,1 | 0,6 | 10 (responde 2) |
| Vacina dengue SUS | 12 s | 9,2 s | 1,2 | 2,0 | 0,7 | 1,2 | 4,7 | 0,4 | 4 |
| Flamengo STF bets | 13 s | 11,1 s | 1,2 | 2,3 | 0,7 | 1,1 | 6,0 | 0,6 | 14 |

## Depois dos passos 2 e 3 (modelos em paralelo; páginas: janela de 3,5 s, máx. 6 s)
| Pesquisa | toque→resultado | interno | desambiguação | busca | sentido+modelo | páginas | montagem | direto |
|---|---|---|---|---|---|---|---|---|
| Lula IR 5 mil | 9 s | 6,7 s | 1,0 | 1,8 | 0,7 | 3,6 | 0,4 | 14 (responde 0) |
| Seleção amistosos | 9 s | 7,4 s | 1,3 | 2,2 | 0,9 | 3,5 | 0,4 | 0 |
| Selic 15% | 9 s | 6,9 s | 1,7 | 1,8 | 0,7 | 3,6 | 0,6 | 10 (responde 2) |
| Vacina dengue SUS | 8 s | 6,6 s | 1,7 | 1,7 | 1,0 | 3,6 | 0,2 | 4 |
| Flamengo STF bets | 10 s | 7,2 s | 1,4 | 2,0 | 0,9 | 3,5 | 0,5 | 14 |

Média interna 9,5 s → 7,0 s; do toque ao resultado 11,8 s → 9,0 s. Fontes "mesmo fato": iguais ou +1.
Atenção: em "Lula IR" o cartão "responde" (1 → 0) sumiu; pode ser a página que chegava depois de 3,5 s ou variação da notícia ao vivo.

## Checagem do "responde" do Lula
Repetida 3 vezes com a janela de 3,5 s: responde=0 nas três. Com a espera antiga (janela de 8 s), também responde=0.
Conclusão: a perda não veio do corte de tempo; foi mudança da notícia ao vivo. As fontes "mesmo fato" ficaram iguais (13).
Observação: nas últimas rodadas o Mac estava mais carregado (busca 2,5 a 3,7 s e montagem 2 a 4 s, etapas de CPU), então
os tempos variam com a carga da máquina; compare sempre antes e depois na mesma sessão.

## Passo 4: prévia (resultado da busca antes de ler as páginas)
Prévia entregue 2,1 a 3,0 s após o início da busca (mais ~1 a 1,9 s de desambiguação antes); resultado final 6,5 a 7,1 s.
Teste `previewDoesNotChangeFinalResult`: o resultado final (cartões, status, síntese e diagnóstico) é idêntico com e sem prévia.
Fontes "mesmo fato" nas 5 pesquisas: 13 / 0 / 10 / 4 / 14 (iguais à linha de base). 200 testes passam.
Próximo alvo de tempo: a desambiguação (1 a 1,9 s) roda antes da busca, em fila.
