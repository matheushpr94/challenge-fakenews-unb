# Contraste entre veículos, pareamento semântico (Ollama, paraphrase-multilingual) (desenvolvimento, 4 folds por história)

Frases com par claro (similaridade >= 0.72) em outro veículo da mesma história: 32%. Similaridade máxima mediana: 0.65.

Entre as enviesadas reais: 31% têm par; entre as demais: 33%. Novidade média: enviesadas 0.359, demais 0.350.

| Modelo | AP (enviesada) |
|---|---:|
| só o logit do modelo | 0.587 |
| + novidade, par e diferença de carga | 0.585 |

Ganho de AP: **-0.002** [IC 95% -0.010; +0.007] — critério (>= +0,02 e IC acima de zero): **NÃO atendido**.
