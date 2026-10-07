"""Baixa (ou instala a partir de um .zip local) os pesos do modelo, confere o SHA-256 e coloca em models/factnews-v2.

  python scripts/fetch_weights.py                       # baixa da Release do GitHub indicada em weights.json
  python scripts/fetch_weights.py --file caminho.zip    # instala de um arquivo que você já tem (pen drive, outro PC)
  python scripts/fetch_weights.py --verify              # só confere a instalação atual
Funciona igual no Windows e no macOS (só biblioteca padrão do Python). Se o SHA-256 não bater, nada é instalado.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from factnews import weights as W  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", help="outro endereço para o pacote (padrão: o de weights.json)")
    ap.add_argument("--file", type=Path, help="instalar de um .zip local em vez de baixar")
    ap.add_argument("--verify", action="store_true", help="só conferir a instalação atual")
    ap.add_argument("--force", action="store_true", help="reinstalar mesmo que já esteja íntegro")
    ap.add_argument("--models-dir", type=Path, default=W.ROOT / "models", help=argparse.SUPPRESS)
    a = ap.parse_args()
    try:
        m = W.load_manifest()
        problems = W.verify_install(a.models_dir, m)
        if a.verify:
            if problems:
                raise SystemExit("Instalação com problemas: " + "; ".join(problems))
            print(f"OK: {a.models_dir / m['name']} confere com weights.json ({len(m['files'])} arquivos).")
            return
        if not problems and not a.force:
            print(f"Já instalado e íntegro em {a.models_dir / m['name']} (use --force para reinstalar).")
            return
        if a.file:
            zip_path = a.file
        else:
            zip_path = W.ROOT / "dist" / m["asset"]
            print(f"Baixando {a.url or m['url']}")
            W.download(a.url or m["url"], zip_path, m["sha256"], m.get("size"))
        W.install(m, a.models_dir, zip_path)
        left = W.verify_install(a.models_dir, m)
        if left:
            raise SystemExit("Conferência final falhou: " + "; ".join(left))
        print("Pronto. Ligue o servidor: scripts/start-factnews-server.ps1 (Windows) ou ./scripts/start-factnews-server.sh (macOS).")
    except W.WeightsError as e:
        raise SystemExit(str(e))


if __name__ == "__main__":
    main()
