"""Empacotar, baixar, verificar e instalar os pesos do modelo (sem dependências além da biblioteca padrão).

Os pesos (~209 MB) não ficam no Git: vão como arquivo anexo de uma Release do GitHub. `weights.json` (versionado) guarda o
endereço e o SHA-256 do pacote e de cada arquivo, para o Mac (ou qualquer máquina) baixar, conferir e colocar na pasta.
"""
from __future__ import annotations

import hashlib
import json
import shutil
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "weights.json"
FIXED_ZIP_TIME = (2026, 1, 1, 0, 0, 0)     # pacote reprodutível: mesmo conteúdo, mesmo SHA-256
CHUNK = 1 << 20


class WeightsError(Exception):
    """Falha esperada (rede, checksum, pacote inválido); a mensagem é para o usuário."""


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(CHUNK), b""):
            h.update(block)
    return h.hexdigest()


def load_manifest(path: Path = MANIFEST) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise WeightsError(f"{path} não existe. Gere-o com scripts/package_weights.py.") from None


def build_zip(models_dir: Path, name: str, out_zip: Path, extra: dict[str, Path] | None = None) -> dict:
    """Zip determinístico de `models_dir/name` (+ arquivos extras em `name/`). Devolve {caminho_relativo: {sha256, size}}."""
    src = models_dir / name
    if not (src / "factnews_config.json").exists():
        raise WeightsError(f"{src} não tem factnews_config.json (rode 04_context_ensemble.py --export primeiro).")
    files: dict[str, Path] = {p.relative_to(models_dir).as_posix(): p for p in src.rglob("*") if p.is_file()}
    for rel, p in (extra or {}).items():
        files[f"{name}/{rel}"] = p
    out_zip.parent.mkdir(parents=True, exist_ok=True)
    listing = {}
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for rel in sorted(files):
            info = zipfile.ZipInfo(rel, FIXED_ZIP_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, files[rel].read_bytes())
            listing[rel] = {"sha256": sha256_file(files[rel]), "size": files[rel].stat().st_size}
    return listing


def safe_extract(zip_path: Path, dest: Path, top: str) -> list[str]:
    """Extrai só para dentro de dest/top, recusando caminhos absolutos, '..' e nomes fora da pasta esperada (zip slip)."""
    dest = dest.resolve()
    written = []
    with zipfile.ZipFile(zip_path) as z:
        for info in z.infolist():
            if info.is_dir():
                continue
            p = PurePosixPath(info.filename)
            if p.is_absolute() or ".." in p.parts or "\\" in info.filename or not p.parts or p.parts[0] != top:
                raise WeightsError(f"pacote inválido: caminho não permitido '{info.filename}'.")
            target = (dest / Path(*p.parts)).resolve()
            if dest not in target.parents:
                raise WeightsError(f"pacote inválido: '{info.filename}' sairia da pasta de destino.")
            target.parent.mkdir(parents=True, exist_ok=True)
            with z.open(info) as src, target.open("wb") as out:
                for block in iter(lambda: src.read(CHUNK), b""):
                    out.write(block)
            written.append(p.as_posix())
    return written


def verify_install(models_dir: Path, manifest: dict) -> list[str]:
    """Lista de problemas (vazia = instalação íntegra): arquivo ausente, tamanho ou SHA-256 diferente."""
    problems = []
    for rel, meta in manifest["files"].items():
        f = models_dir / rel
        if not f.is_file():
            problems.append(f"falta {rel}")
        elif f.stat().st_size != meta["size"]:
            problems.append(f"tamanho diferente em {rel}")
        elif sha256_file(f) != meta["sha256"]:
            problems.append(f"SHA-256 diferente em {rel}")
    return problems


def download(url: str, dest: Path, expected_sha256: str, expected_size: int | None = None, say=print) -> None:
    """Baixa e confere o SHA-256. Escreve em `dest.part` e só troca `dest` depois de conferir: um download interrompido ou
    adulterado nunca apaga nem substitui um pacote bom que já exista."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    part = dest.with_name(dest.name + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": "lume-factnews-fetch/1"})
    h = hashlib.sha256()
    t0 = time.time(); got = 0; last = 0.0
    try:
        with urllib.request.urlopen(req, timeout=60) as resp, part.open("wb") as out:
            total = int(resp.headers.get("Content-Length") or expected_size or 0)
            for block in iter(lambda: resp.read(CHUNK), b""):
                out.write(block); h.update(block); got += len(block)
                if time.time() - last > 1.0:
                    last = time.time()
                    say(f"\r  baixando… {got / 2**20:6.1f} / {total / 2**20:.1f} MB" if total else f"\r  baixando… {got / 2**20:6.1f} MB", end="")
        say(f"\r  baixado: {got / 2**20:.1f} MB em {time.time() - t0:.0f}s" + " " * 20)
    except urllib.error.HTTPError as e:
        part.unlink(missing_ok=True)
        if e.code == 404:
            raise WeightsError(f"Não encontrei {url} (HTTP 404). Se a Release com os pesos ainda não foi publicada, "
                               "use --file com o arquivo .zip que alguém te enviou, ou treine o modelo (README).") from None
        raise WeightsError(f"Falha ao baixar {url}: HTTP {e.code}.") from None
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        part.unlink(missing_ok=True)
        raise WeightsError(f"Falha de rede ao baixar {url}: {e}.") from None
    if h.hexdigest() != expected_sha256:
        part.unlink(missing_ok=True)
        raise WeightsError(f"O SHA-256 do arquivo baixado não confere (recebido {h.hexdigest()[:16]}…, esperado {expected_sha256[:16]}…). "
                           "O arquivo baixado foi descartado; tente de novo.")
    part.replace(dest)


def install(manifest: dict, models_dir: Path, zip_path: Path, say=print) -> None:
    """Confere o pacote, extrai e confere cada arquivo. Em caso de erro não deixa instalação pela metade."""
    if sha256_file(zip_path) != manifest["sha256"]:
        raise WeightsError("O SHA-256 do pacote não confere com weights.json; não vou instalar.")
    name = manifest["name"]
    target = models_dir / name
    staging = models_dir / f".{name}.instalando"
    if staging.exists():
        _rmtree(staging)
    safe_extract(zip_path, staging, name)
    problems = verify_install(staging, manifest)
    if problems:
        _rmtree(staging)
        raise WeightsError("Instalação inválida: " + "; ".join(problems))
    if target.exists():
        _rmtree(target)
    (staging / name).rename(target)
    _rmtree(staging)
    say(f"  instalado em {target}")


def _rmtree(path: Path) -> None:
    shutil.rmtree(path, ignore_errors=True)
