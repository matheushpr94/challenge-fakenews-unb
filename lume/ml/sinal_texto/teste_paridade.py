"""Confere que sinal.py reproduz o pipeline original (decision_function) nos textos já lematizados do FakeRecogna."""
import sys, warnings, numpy as np, pandas as pd
warnings.simplefilter("ignore")
from exportar_modelo import carregar
from sinal import Sinal
pipe = carregar("/Users/aluno1/Downloads/modelo_fakerecogna_svm.pkl")
s = Sinal()
df = pd.read_csv("/Users/aluno1/Downloads/FakeRecogna.csv").dropna(subset=["Noticia", "Classe"]).sample(1500, random_state=7)
textos = df["Noticia"].astype(str).tolist()
ref = pipe.decision_function(textos)
meu = np.array([s.decisao(t)[0] for t in textos])
print(f"diferença máxima {np.abs(ref-meu).max():.2e} | médias: classe0 {meu[df.Classe.values==0].mean():+.3f}  classe1 {meu[df.Classe.values==1].mean():+.3f}")
print("acerto (decisão>0 => verdadeira):", round(float(((meu > 0).astype(int) == df.Classe.values.astype(int)).mean()), 4))
assert np.abs(ref - meu).max() < 1e-4
print("PARIDADE OK")
