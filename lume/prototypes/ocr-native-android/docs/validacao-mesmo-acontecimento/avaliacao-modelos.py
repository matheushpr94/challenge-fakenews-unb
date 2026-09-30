"""Avalia modelos locais gratuitos nos pares difíceis (pares-dificeis.json + leitura das capturas).
Saída: métricas por modelo e tempo por par. Não altera o app."""
import json, time, re, sys, os
import numpy as np, onnxruntime as ort, requests
from tokenizers import Tokenizer

HERE = os.path.dirname(os.path.abspath(__file__))
P = r"C:/Users/gbast/StudioProjects/challenge-fakenews-unb/lume/prototypes/ocr-native-android"
pairs = json.load(open(f"{P}/app/src/test/resources/pairs/pares-dificeis.json", encoding="utf-8"))
# Leitura das capturas (gerada pelo teste JVM a partir da saída real do ML Kit).
captures = json.load(open(f"{P}/app/build/lume-live/capturas-lidas.json", encoding="utf-8"))
SAME = {"mesmo_acontecimento", "divergente", "propria", "republicacao", "mesmo_veiculo"}

def orig_text(p, full=True):
    o = p["original"]
    if "captura" in o:
        c = captures[o["captura"]]; t, sub, body = c["afirmacao"], c.get("subtitulo"), c.get("corpo", "")
    else:
        t, sub, body = o["titulo"], o.get("subtitulo"), o.get("corpo", "")
    if not full: return t
    return " ".join(x for x in [t, sub, (body or "")[:300]] if x)

def cand_text(p, full=True):
    c = p["candidata"]
    if not full: return c["titulo"]
    return " ".join(x for x in [c["titulo"], c["trecho"], (c["pagina"] or "")[:400]] if x)

def gold_same(p): return p["esperado"] in SAME

class Embedder:
    def __init__(self, d, prefix=""):
        self.tok = Tokenizer.from_file(f"{HERE}/models/{d}/tokenizer.json"); self.tok.enable_truncation(256)
        self.s = ort.InferenceSession(f"{HERE}/models/{d}/onnx/model_int8.onnx", providers=["CPUExecutionProvider"])
        self.names = [i.name for i in self.s.get_inputs()]; self.prefix = prefix
    def __call__(self, text):
        e = self.tok.encode(self.prefix + text)
        feeds = {"input_ids": np.array([e.ids], dtype=np.int64), "attention_mask": np.array([e.attention_mask], dtype=np.int64)}
        if "token_type_ids" in self.names: feeds["token_type_ids"] = np.zeros_like(feeds["input_ids"])
        h = self.s.run(None, feeds)[0][0]; m = np.array(e.attention_mask)[:, None]
        v = (h * m).sum(0) / m.sum(); return v / np.linalg.norm(v)

class NLI:
    def __init__(self, d):
        self.tok = Tokenizer.from_file(f"{HERE}/models/{d}/tokenizer.json"); self.tok.enable_truncation(384)
        self.s = ort.InferenceSession(f"{HERE}/models/{d}/onnx/model_int8.onnx", providers=["CPUExecutionProvider"])
        cfg = json.load(open(f"{HERE}/models/{d}/config.json")); self.labels = [cfg["id2label"][str(i)] for i in range(len(cfg["id2label"]))]
        self.names = [i.name for i in self.s.get_inputs()]
    def __call__(self, premise, hypothesis):
        e = self.tok.encode(premise, hypothesis)
        feeds = {"input_ids": np.array([e.ids], dtype=np.int64), "attention_mask": np.array([e.attention_mask], dtype=np.int64)}
        if "token_type_ids" in self.names: feeds["token_type_ids"] = np.array([e.type_ids], dtype=np.int64)
        lo = self.s.run(None, feeds)[0][0]; pr = np.exp(lo - lo.max()); pr /= pr.sum()
        return dict(zip(self.labels, pr.round(3).tolist()))

def best_threshold(scores, gold):
    best = (0, 0)
    for t in sorted(set(scores)):
        acc = sum((s >= t) == g for s, g in zip(scores, gold)) / len(gold)
        if acc > best[0]: best = (acc, t)
    return best

def main():
    gold = [gold_same(p) for p in pairs]
    out = f"{P}/app/build/lume-live/avaliacao-modelos.json"
    report = json.load(open(out, encoding="utf-8")) if os.path.exists(out) else {}
    for name, d, pre in [("MiniLM-L12 multilíngue (paráfrase)", "paraphrase-multilingual-MiniLM-L12-v2", ""),
                         ("multilingual-e5-small", "multilingual-e5-small", "query: ")]:
        emb = Embedder(d, pre); t0 = time.time()
        rows = []
        for p in pairs:
            a, b = emb(orig_text(p, False)), emb(cand_text(p, False)); rows.append(float(a @ b))
        ms = (time.time() - t0) * 1000 / len(pairs) / 2
        acc, thr = best_threshold(rows, gold)
        # Pares de fatos diferentes com similaridade acima do limiar escolhido = aceitos por engano.
        fp = [p["id"] for p, s, g in zip(pairs, rows, gold) if s >= thr and not g]
        fn = [p["id"] for p, s, g in zip(pairs, rows, gold) if s < thr and g]
        report[name] = dict(acuracia_melhor_limiar=round(acc, 3), limiar=round(thr, 3), ms_por_texto=round(ms, 1),
                            aceitos_por_engano=fp, perdidos=fn, scores={p["id"]: round(s, 3) for p, s in zip(pairs, rows)})
        print(name, report[name]["acuracia_melhor_limiar"], "limiar", report[name]["limiar"], f"{ms:.1f} ms/texto")
    for label, d in [("mDeBERTa-v3 xnli int8 (inferência)", "mDeBERTa-v3-base-xnli-multilingual-nli-2mil7"),
                     ("MiniLMv2-L6 mnli-xnli (inferência)", "multilingual-MiniLMv2-L6-mnli-xnli")]:
      nli = NLI(d); t0 = time.time(); rows = []
      for p in pairs:
        r = nli(cand_text(p, True), orig_text(p, False)); rows.append(r)
      ms = (time.time() - t0) * 1000 / len(pairs)
      pred = ["mesmo" if r["entailment"] >= 0.5 else "divergente" if r["contradiction"] >= 0.5 else "nao" for r in rows]
      acc = sum((pr in ("mesmo", "divergente")) == g for pr, g in zip(pred, gold)) / len(gold)
      print(label, round(acc, 3), f"{ms:.0f} ms/par")
      report[label] = dict(acuracia=round(acc, 3), ms_por_par=round(ms, 1),
        aceitos_por_engano=[p["id"] for p, pr, g in zip(pairs, pred, gold) if pr != "nao" and not g],
        perdidos=[p["id"] for p, pr, g in zip(pairs, pred, gold) if pr == "nao" and g],
        contradicao_detectada=[p["id"] for p, pr in zip(pairs, pred) if pr == "divergente"],
        probs={p["id"]: r for p, r in zip(pairs, rows)})
    # LLM local (já instalado): pede relação + trecho literal, confere se o trecho existe no texto da fonte.
    if "--sem-llm" not in sys.argv:
        t0 = time.time(); rows = []; invented = 0
        for p in pairs:
            prompt = ("Compare duas notícias em português. NOTÍCIA A (importada): " + orig_text(p, True)[:600] +
                      "\nNOTÍCIA B (fonte): " + cand_text(p, True)[:900] +
                      "\nB relata o MESMO acontecimento de A (mesmo agente, mesma ação, mesmo objeto, mesma data)? "
                      "Responda só JSON {\"relacao\":\"mesmo|divergente|contexto|outro|incerto\",\"trecho\":\"frase copiada literalmente de B\"}. "
                      "Não decida se é verdadeiro ou falso.")
            r = requests.post("http://127.0.0.1:11434/api/generate", json={"model": "qwen3.5:4b", "prompt": prompt, "stream": False,
                              "think": False, "format": "json", "options": {"temperature": 0, "num_predict": 200}}, timeout=120).json()
            try: o = json.loads(r["response"])
            except Exception: o = {}
            quote = (o.get("trecho") or "").strip()
            ok_quote = bool(quote) and quote.lower() in cand_text(p, True).lower()
            if quote and not ok_quote: invented += 1
            rows.append((o.get("relacao", "?"), ok_quote))
        ms = (time.time() - t0) * 1000 / len(pairs)
        acc = sum((rel in ("mesmo", "divergente")) == g for (rel, _), g in zip(rows, gold)) / len(gold)
        report["qwen3.5:4b (Ollama)"] = dict(acuracia=round(acc, 3), ms_por_par=round(ms, 1), trechos_nao_encontrados=invented,
            aceitos_por_engano=[p["id"] for p, (rel, _), g in zip(pairs, rows, gold) if rel in ("mesmo", "divergente") and not g],
            perdidos=[p["id"] for p, (rel, _), g in zip(pairs, rows, gold) if rel not in ("mesmo", "divergente") and g],
            saidas={p["id"]: rel for p, (rel, _) in zip(pairs, rows)})
        print("qwen", round(acc, 3), f"{ms:.0f} ms/par", "trechos inventados:", invented)
    report.pop("mDeBERTa-v3 xnli (inferência)", None)
    json.dump(report, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

main()
