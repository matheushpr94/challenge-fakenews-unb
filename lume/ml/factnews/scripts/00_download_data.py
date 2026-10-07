"""Baixa o FactNews v2.0.0 do Zenodo (CC BY 4.0), confere o checksum oficial e extrai em data/raw.

Por que a v2.0.0: o zip da v3.0.0 só traz as pastas `annotators/` e o readme (rótulos sem texto).
O texto das frases (`dataset/factnews_dataset.csv`, 1,1 MB) está na v2.0.0 e no GitHub oficial
https://github.com/franciellevargas/FactNews. Registro: https://zenodo.org/records/10794023
"""
from __future__ import annotations

import hashlib
import io
import sys
import zipfile
from pathlib import Path

import requests

RECORD = "https://zenodo.org/api/records/10794023"
DEST = Path(__file__).resolve().parents[1] / "data" / "raw"


def main() -> int:
    meta = requests.get(RECORD, timeout=60).json()
    info = meta["files"][0]
    algo, expected = info["checksum"].split(":")
    target = DEST / "FactNews-v2.0.0"
    if any(target.glob("*/dataset/factnews_dataset.csv")):
        print("Já baixado:", target)
        return 0
    print(f"Baixando {info['key']} ({info['size']} bytes, licença {meta['metadata']['license']['id']})...")
    data = requests.get(info["links"]["self"], timeout=180).content
    got = hashlib.new(algo, data).hexdigest()
    if got != expected:
        print(f"Checksum NÃO confere: esperado {expected}, obtido {got}", file=sys.stderr)
        return 1
    DEST.mkdir(parents=True, exist_ok=True)
    (DEST / "FactNews-v2.0.0.zip").write_bytes(data)
    zipfile.ZipFile(io.BytesIO(data)).extractall(target)
    print("OK:", target)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
