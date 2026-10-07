"""Empacota os pesos exportados em dist/factnews-v2-weights.zip e grava weights.json (SHA-256 do pacote e de cada arquivo).

Uso (na pasta lume/ml/factnews, depois de `04_context_ensemble.py --export --use-test --ctx none --seeds 42 --tag factnews-v2`):
  python scripts/package_weights.py
Depois: publique dist/factnews-v2-weights.zip como arquivo anexo de uma Release do GitHub com a tag indicada em weights.json
(isso é uma ação pública: faça só quando decidido) e versione weights.json, NOTICE-weights.txt e licenses/.
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from factnews import weights as W  # noqa: E402

REPO = "matheushpr94/challenge-fakenews-unb"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--name", default="factnews-v2")
    ap.add_argument("--tag", default=None, help="tag da Release (padrão: igual ao nome do modelo)")
    ap.add_argument("--repo", default=REPO)
    a = ap.parse_args()
    tag = a.tag or a.name
    models_dir, out = W.ROOT / "models", W.ROOT / "dist" / f"{a.name}-weights.zip"
    extra = {"NOTICE.txt": W.ROOT / "NOTICE-weights.txt", "BERTimbau-MIT.txt": W.ROOT / "licenses" / "BERTimbau-MIT.txt"}
    try:
        files = W.build_zip(models_dir, a.name, out, extra)
        cfg = json.loads((models_dir / a.name / "factnews_config.json").read_text(encoding="utf-8"))
    except W.WeightsError as e:
        raise SystemExit(str(e))
    manifest = {
        "name": a.name, "tag": tag, "asset": out.name,
        "url": f"https://github.com/{a.repo}/releases/download/{tag}/{out.name}",
        "sha256": W.sha256_file(out), "size": out.stat().st_size,
        "base_model": cfg.get("base_model"), "classes": cfg.get("classes"), "seeds": cfg.get("seeds"),
        "trained_on": cfg.get("treino"), "exported_on": cfg.get("treinado_em"), "packaged_on": date.today().isoformat(),
        "files": files,
    }
    W.MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"pacote: {out}  ({out.stat().st_size / 2**20:.1f} MB)\nsha256: {manifest['sha256']}\nurl prevista: {manifest['url']}")
    print(f"weights.json atualizado ({len(files)} arquivos).")


if __name__ == "__main__":
    main()
