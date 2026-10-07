"""Ideia 2 (passo 2): aprendizado ativo para rotulagem HUMANA de textos longos (ou de outro gênero).

Seleciona poucas frases (padrão ~100) que mais informam sobre o desempenho do modelo naquele texto: todos os destaques (enviesada com
confiança >= 0,80, até um teto), as mais incertas e uma amostra aleatória do restante (para estimar o que o modelo deixa passar).
Gera duas planilhas em data/rotulagem_humana/ (não versionada: contém texto de matérias):
  <nome>_para_rotular.csv   id, frase, rotulo_humano   <- o anotador preenche com F (factual), C (citação) ou E (enviesada), SEM ver o modelo
  <nome>_chave_modelo.csv   id, grupo, rotulo_modelo, confianca, peso   <- fica com quem vai calcular
Depois de preenchida:  python scripts/11_active_sample.py --score <nome>
  -> precisão do destaque, revocação estimada (com a amostra aleatória ponderada) e concordância (kappa) entre humano e modelo.
Uso:  python scripts/11_active_sample.py --text-file data/external/revista_analise/<arquivo>.txt --name piaui [--n 100]
"""
from __future__ import annotations

import argparse
import csv
import importlib.util
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "rotulagem_humana"
HIGHLIGHT = 0.80
CODE = {"F": "factual", "C": "citacao", "E": "enviesada"}


def load_predict():
    spec = importlib.util.spec_from_file_location("predict_mod", Path(__file__).with_name("predict.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def select(args) -> None:
    sents = [s.strip() for s in Path(args.text_file).read_text(encoding="utf-8").splitlines() if s.strip()]
    pm = load_predict()
    res = pm.predict(sents, "", pm.load())
    lab = np.array([r["rotulo"] for r in res]); conf = np.array([r["confianca"] for r in res])
    hl = np.where((lab == "enviesada") & (conf >= HIGHLIGHT))[0]
    rest = np.array([i for i in range(len(sents)) if i not in set(hl.tolist())])
    rng = np.random.default_rng(42)
    n_h = min(len(hl), args.n * 2 // 5); n_u = args.n * 3 // 10; n_r = args.n - n_h - n_u
    pick_h = rng.choice(hl, n_h, replace=False) if len(hl) else np.array([], int)
    unc = rest[np.argsort(conf[rest])[:n_u]]                                       # as menos confiantes
    pool = np.array([i for i in rest if i not in set(unc.tolist())])
    pick_r = rng.choice(pool, min(n_r, len(pool)), replace=False)
    groups = [("destaque", pick_h, len(hl) / max(1, len(pick_h))), ("incerta", unc, 1.0), ("aleatoria", pick_r, len(pool) / max(1, len(pick_r)))]
    rows = [(i, g, w) for g, idxs, w in groups for i in idxs]
    rng.shuffle(rows)
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT / f"{args.name}_para_rotular.csv").open("w", encoding="utf-8-sig", newline="") as f, \
         (OUT / f"{args.name}_chave_modelo.csv").open("w", encoding="utf-8", newline="") as k:
        wf, wk = csv.writer(f), csv.writer(k)
        wf.writerow(["id", "frase", "rotulo_humano"]); wk.writerow(["id", "grupo", "rotulo_modelo", "confianca", "peso"])
        for n, (i, g, w) in enumerate(rows):
            wf.writerow([n, sents[i], ""]); wk.writerow([n, g, lab[i], f"{conf[i]:.3f}", f"{w:.3f}"])
    print(f"{len(sents)} frases no texto; {len(hl)} destaques. Selecionadas {len(rows)}: "
          f"{len(pick_h)} destaques, {len(unc)} incertas, {len(pick_r)} aleatórias. Arquivos em {OUT}")


def score(args) -> None:
    from sklearn.metrics import cohen_kappa_score
    key = list(csv.DictReader((OUT / f"{args.name}_chave_modelo.csv").open(encoding="utf-8")))
    human = {r["id"]: r["rotulo_humano"].strip().upper() for r in csv.DictReader((OUT / f"{args.name}_para_rotular.csv").open(encoding="utf-8-sig"))}
    rows = [(r, human[r["id"]]) for r in key if human.get(r["id"]) in CODE]
    if len(rows) < len(key):
        print(f"Aviso: só {len(rows)} de {len(key)} frases têm rótulo F/C/E.")
    hl = [(r, h) for r, h in rows if r["grupo"] == "destaque"]; rd = [(r, h) for r, h in rows if r["grupo"] == "aleatoria"]
    sys.stdout.reconfigure(encoding="utf-8")
    if hl:
        print(f"Precisão do destaque (humano disse enviesada): {np.mean([h == 'E' for _, h in hl]):.2f}  (n={len(hl)})")
    if hl and rd:
        # Frases NÃO destacadas: as "incertas" entraram inteiras (peso 1) e as "aleatórias" são amostra do restante (peso = tamanho do restante / amostra).
        un = [(r, h) for r, h in rows if r["grupo"] == "incerta"]
        w_h = float(hl[0][0]["peso"]); w_r = float(rd[0][0]["peso"])
        tp = sum(h == "E" for _, h in hl) * w_h
        fn = sum(h == "E" for _, h in un) + sum(h == "E" for _, h in rd) * w_r
        print(f"Revocação estimada do destaque: {tp / max(tp + fn, 1e-9):.2f}  (E segundo o humano: {sum(h == 'E' for _, h in un)} de {len(un)} incertas e "
              f"{sum(h == 'E' for _, h in rd)} de {len(rd)} aleatórias; estimativa de 'enviesadas que o modelo deixou passar': {fn:.0f}, de ~{tp + fn:.0f} no texto)")
        n, k = len(hl), sum(h == "E" for _, h in hl); z = 1.96; ph = k / n
        c = (ph + z * z / (2 * n)) / (1 + z * z / n); d = z * np.sqrt(ph * (1 - ph) / n + z * z / (4 * n * n)) / (1 + z * z / n)
        print(f"Precisão do destaque, IC 95% (Wilson): [{c - d:.2f}; {c + d:.2f}]")
    pairs = [(CODE[h], r["rotulo_modelo"]) for r, h in rows]
    if len(pairs) >= 20:
        print(f"Concordância humano × modelo (kappa, {len(pairs)} frases; amostra não representativa do texto): "
              f"{cohen_kappa_score([a for a, _ in pairs], [b for _, b in pairs]):.2f}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--text-file"); ap.add_argument("--name", required=True); ap.add_argument("--n", type=int, default=100)
    ap.add_argument("--score", action="store_true")
    a = ap.parse_args()
    score(a) if a.score else select(a)
