# Contraste entre veículos, pareamento por palavras (TF-IDF) (desenvolvimento, 4 folds por história)

Frases com par claro (similaridade >= 0.35) em outro veículo da mesma história: 15%. Similaridade máxima mediana: 0.12.

Entre as enviesadas reais: 6% têm par; entre as demais: 16%. Novidade média: enviesadas 0.858, demais 0.791.

| Modelo | AP (enviesada) |
|---|---:|
| só o logit do modelo | 0.587 |
| + novidade, par e diferença de carga | 0.586 |

Ganho de AP: **-0.001** [IC 95% -0.007; +0.003] — critério (>= +0,02 e IC acima de zero): **NÃO atendido**.
