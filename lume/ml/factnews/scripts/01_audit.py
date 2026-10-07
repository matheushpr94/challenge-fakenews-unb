"""Auditoria do FactNews antes de treinar: rótulos, anotadores, duplicatas e atalhos.

Só usa os dados inteiros para DESCREVER (contagens, concordância). Nenhum modelo é treinado aqui.
Saída: reports/auditoria.md e reports/auditoria.json.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import cohen_kappa_score

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from factnews.data import CLASS_NAMES, ORIGINAL_TO_INDEX, load_annotators, load_sentences  # noqa: E402

REPORTS = Path(__file__).resolve().parents[1] / "reports"
QUOTES = re.compile(r"[\"“”«»]")


def norm(s: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[^\w\s]", "", s.lower())).strip()


def main() -> None:
    df = load_sentences()
    out: dict = {}
    md: list[str] = ["# Auditoria do FactNews v2.0.0", ""]

    # 1) Estrutura e consistência dos identificadores
    out["linhas"] = len(df)
    out["historias"] = int(df["story"].nunique())
    out["artigos"] = int(df["article"].nunique())
    out["arquivos"] = int(df["file"].nunique())
    out["frases_de_manchete"] = int(df["is_title"].sum())
    known = df["outlet_from_file"].notna()
    out["linhas_letra_do_id_diverge_do_arquivo"] = int((df.loc[known, "outlet_from_id"] != df.loc[known, "outlet_from_file"]).sum())
    out["linhas_sem_nome_de_veiculo_no_arquivo"] = int((~known).sum())
    por_historia = df.groupby("story")["outlet"].nunique()
    out["historias_com_3_veiculos"] = int((por_historia == 3).sum())
    out["rotulos"] = df["label_name"].value_counts().reindex(CLASS_NAMES).to_dict()
    md += ["## Estrutura", f"- {out['linhas']} frases, {out['historias']} histórias, {out['artigos']} nomes-base de arquivo (o readme fala em 300 documentos; corpo e manchete às vezes têm nomes diferentes), {out['arquivos']} arquivos; "
           f"{out['frases_de_manchete']} frases são manchetes.",
           f"- Linhas em que a letra do veículo em `id_article` diverge do nome no arquivo: {out['linhas_letra_do_id_diverge_do_arquivo']} (história c78: artigo do Estadão com id `c78o`, igual ao de O Globo); "
           f"{out['linhas_sem_nome_de_veiculo_no_arquivo']} linhas têm data no lugar do nome.",
           f"- Histórias presentes nos 3 veículos: {out['historias_com_3_veiculos']} de {out['historias']}.",
           f"- Rótulos: {out['rotulos']}.", ""]

    # 2) Rótulo final x anotadores
    ann = load_annotators()
    assert len(ann) == len(df), "arquivo de anotadores com tamanho diferente"
    final = df["classe_original"].to_numpy()
    a1, a2 = ann["annotator1"].to_numpy(), ann["annotator2"].to_numpy()
    both = a1 == a2
    out["anotadores_concordam"] = int(both.sum())
    out["anotadores_discordam"] = int((~both).sum())
    out["final_igual_aos_dois_quando_concordam"] = float((final[both] == a1[both]).mean())
    out["linhas_final_difere_dos_dois_anotadores_que_concordam"] = int((final[both] != a1[both]).sum())
    out["kappa_geral"] = float(cohen_kappa_score(a1, a2))
    kappas = {}
    for orig, idx in ORIGINAL_TO_INDEX.items():
        kappas[CLASS_NAMES[idx]] = float(cohen_kappa_score(a1 == orig, a2 == orig))
    out["kappa_um_contra_resto"] = kappas
    dis = pd.crosstab(pd.Series(a1[~both], name="anotador1"), pd.Series(a2[~both], name="anotador2"))
    out["discordancias_por_par"] = {f"{i}|{j}": int(v) for i, r in dis.iterrows() for j, v in r.items() if v}
    por_classe_final = pd.Series(final[~both]).map(ORIGINAL_TO_INDEX).map(dict(enumerate(CLASS_NAMES))).value_counts()
    out["rotulo_final_nas_discordancias"] = por_classe_final.to_dict()
    md += ["## Anotadores", f"- Concordam em {out['anotadores_concordam']} frases; discordam em {out['anotadores_discordam']}.",
           f"- Rótulo do dataset = rótulo dos dois quando concordam: {out['final_igual_aos_dois_quando_concordam']:.4f} "
           f"({out['linhas_final_difere_dos_dois_anotadores_que_concordam']} linhas diferem mesmo com os dois de acordo).",
           "- ATENÇÃO: o artigo original reporta kappa 0,82 e o arquivo de anotadores do repositório dá um valor muito maior; "
           "não consegui reconciliar. O arquivo pode já refletir uma rodada de revisão.",
           f"- Kappa de Cohen (anotador 1 x 2): {out['kappa_geral']:.3f}.",
           "- Kappa um-contra-resto: " + ", ".join(f"{k} {v:.3f}" for k, v in kappas.items()) + ".",
           f"- Rótulo final nas {out['anotadores_discordam']} discordâncias: {out['rotulo_final_nas_discordancias']}.", ""]

    # 3) Duplicatas exatas
    df["norm"] = df["text"].map(norm)
    dup = df[df.duplicated("norm", keep=False)]
    conflitos = dup.groupby("norm")["label"].nunique()
    out["frases_em_grupos_duplicados"] = int(len(dup))
    out["grupos_duplicados"] = int(dup["norm"].nunique())
    out["grupos_duplicados_com_rotulos_conflitantes"] = int((conflitos > 1).sum())
    md += ["## Duplicatas exatas (texto normalizado)",
           f"- {out['frases_em_grupos_duplicados']} frases em {out['grupos_duplicados']} grupos; "
           f"{out['grupos_duplicados_com_rotulos_conflitantes']} grupos com rótulos conflitantes.", ""]

    # 4) Gêmeas entre veículos na mesma história (o que inflaria uma divisão ao acaso)
    vec = TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=1).fit(df["text"])
    X = vec.transform(df["text"])
    twin_best = np.zeros(len(df))
    twin_label_agree = []
    for _, g in df.groupby("story"):
        idx = g.index.to_numpy()
        sim = (X[idx] @ X[idx].T).toarray()
        for a, i in enumerate(idx):
            outras = [b for b, j in enumerate(idx) if df.at[j, "outlet"] != df.at[i, "outlet"]]
            if not outras:
                continue
            b = outras[int(np.argmax(sim[a, outras]))]
            twin_best[i] = sim[a, b]
            if sim[a, b] >= 0.8:
                twin_label_agree.append(df.at[i, "label"] == df.at[idx[b], "label"])
    for th in (0.6, 0.8, 0.9):
        out[f"frases_com_gemea_em_outro_veiculo_sim>={th}"] = float((twin_best >= th).mean())
    out["rotulo_igual_entre_gemeas_sim>=0.8"] = float(np.mean(twin_label_agree)) if twin_label_agree else None
    md += ["## Gêmeas entre veículos (mesma história, similaridade de caracteres)",
           "- Fração de frases com uma gêmea em outro veículo: "
           + ", ".join(f">= {th}: {out[f'frases_com_gemea_em_outro_veiculo_sim>={th}']:.1%}" for th in (0.6, 0.8, 0.9)) + ".",
           f"- Quando a similaridade é >= 0,8, o rótulo é o mesmo em {out['rotulo_igual_entre_gemeas_sim>=0.8']:.1%} dos casos "
           f"({len(twin_label_agree)} pares).", ""]

    # 4b) Manchetes x corpo
    ti = df.groupby("is_title")["label_name"].value_counts().unstack().reindex(columns=CLASS_NAMES).fillna(0).astype(int)
    out["manchete_x_corpo"] = {str(k): v for k, v in ti.to_dict(orient="index").items()}
    md += ["## Manchetes x corpo", "", ti.rename(index={False: "corpo", True: "manchete"}).to_markdown(), ""]

    # 5) Atalhos possíveis
    df["tem_aspas"] = df["text"].map(lambda s: bool(QUOTES.search(s)))
    df["n_palavras"] = df["text"].str.split().str.len()
    tab = df.groupby("label_name").agg(frases=("text", "size"), com_aspas=("tem_aspas", "mean"),
                                      palavras_mediana=("n_palavras", "median"), palavras_media=("n_palavras", "mean")).reindex(CLASS_NAMES)
    out["por_classe"] = tab.round(3).to_dict(orient="index")
    taxa = lambda col: (df.assign(env=(df["label"] == 2)).groupby(col)["env"].agg(["mean", "size"]).round(3))  # noqa: E731
    out["taxa_enviesada_por_veiculo"] = taxa("outlet").to_dict(orient="index")
    out["taxa_enviesada_por_editoria"] = taxa("domain").to_dict(orient="index")
    out["taxa_enviesada_por_ano"] = {str(k): v for k, v in taxa("year").to_dict(orient="index").items()}
    md += ["## Atalhos possíveis", "", tab.round(3).to_markdown(), "",
           "Taxa de frases enviesadas por veículo / editoria / ano:", "",
           taxa("outlet").to_markdown(), "", taxa("domain").to_markdown(), "", taxa("year").to_markdown(), ""]

    REPORTS.mkdir(parents=True, exist_ok=True)
    (REPORTS / "auditoria.json").write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    (REPORTS / "auditoria.md").write_text("\n".join(md), encoding="utf-8")
    print("\n".join(md))


if __name__ == "__main__":
    main()
