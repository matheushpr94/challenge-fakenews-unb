"""Pacote de pesos: reprodutível, instalação íntegra e proteções (zip malicioso, checksum errado, download falho).

Usa arquivos pequenos de mentira; não toca em models/ nem na rede de verdade (servidor HTTP local na porta 0).
"""
from __future__ import annotations

import http.server
import shutil
import sys
import tempfile
import threading
import unittest
import zipfile
from functools import partial
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from factnews import weights as W  # noqa: E402

NAME = "fake-model"


def make_model(models: Path, payload: bytes = b"pesos-de-mentira") -> None:
    d = models / NAME
    (d / "seed1").mkdir(parents=True)
    (d / "factnews_config.json").write_text('{"classes":["factual","citacao","enviesada"],"treino":"x"}', encoding="utf-8")
    (d / "seed1" / "model.safetensors").write_bytes(payload)
    (d / "seed1" / "vocab.txt").write_text("a\nb\n", encoding="utf-8")


def manifest_for(zip_path: Path, files: dict) -> dict:
    return {"name": NAME, "asset": zip_path.name, "sha256": W.sha256_file(zip_path), "size": zip_path.stat().st_size, "files": files}


class PackageTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, True)
        self.models = self.tmp / "models"
        make_model(self.models)
        self.notice = self.tmp / "NOTICE.txt"
        self.notice.write_text("aviso", encoding="utf-8")

    def build(self, out: Path):
        files = W.build_zip(self.models, NAME, out, {"NOTICE.txt": self.notice})
        return manifest_for(out, files)

    def test_zip_is_reproducible(self):
        a = self.build(self.tmp / "a.zip")
        b = self.build(self.tmp / "b.zip")
        self.assertEqual(a["sha256"], b["sha256"])

    def test_zip_has_the_model_files_and_the_notice_under_one_folder(self):
        m = self.build(self.tmp / "a.zip")
        with zipfile.ZipFile(self.tmp / "a.zip") as z:
            names = sorted(z.namelist())
        self.assertEqual([f"{NAME}/NOTICE.txt", f"{NAME}/factnews_config.json", f"{NAME}/seed1/model.safetensors", f"{NAME}/seed1/vocab.txt"], names)
        self.assertEqual(set(names), set(m["files"]))

    def test_install_then_verify_is_clean_and_replaces_an_old_install(self):
        m = self.build(self.tmp / "a.zip")
        dest = self.tmp / "dest"
        (dest / NAME).mkdir(parents=True)
        (dest / NAME / "velho.txt").write_text("resto de uma instalação antiga", encoding="utf-8")
        W.install(m, dest, self.tmp / "a.zip", say=lambda *a, **k: None)
        self.assertEqual([], W.verify_install(dest, m))
        self.assertFalse((dest / NAME / "velho.txt").exists())
        self.assertFalse(any(p.name.startswith(".") for p in dest.iterdir()), "não deixa pasta temporária")

    def test_verify_detects_missing_changed_and_resized_files(self):
        m = self.build(self.tmp / "a.zip")
        dest = self.tmp / "dest"
        W.install(m, dest, self.tmp / "a.zip", say=lambda *a, **k: None)
        self.assertEqual([], W.verify_install(dest, m))
        (dest / NAME / "seed1" / "vocab.txt").write_text("c\nd\n", encoding="utf-8")            # mesmo tamanho, outro conteúdo
        (dest / NAME / "seed1" / "model.safetensors").write_bytes(b"curto")                     # outro tamanho
        (dest / NAME / "NOTICE.txt").unlink()                                                    # ausente
        problems = " | ".join(W.verify_install(dest, m))
        self.assertIn("SHA-256 diferente em fake-model/seed1/vocab.txt", problems)
        self.assertIn("tamanho diferente em fake-model/seed1/model.safetensors", problems)
        self.assertIn("falta fake-model/NOTICE.txt", problems)

    def test_wrong_package_checksum_installs_nothing(self):
        m = self.build(self.tmp / "a.zip")
        m["sha256"] = "0" * 64
        dest = self.tmp / "dest"
        with self.assertRaises(W.WeightsError):
            W.install(m, dest, self.tmp / "a.zip", say=lambda *a, **k: None)
        self.assertFalse((dest / NAME).exists())

    def test_package_with_a_tampered_file_is_rejected_and_leaves_no_partial_install(self):
        m = self.build(self.tmp / "a.zip")
        tampered = self.tmp / "t.zip"
        with zipfile.ZipFile(self.tmp / "a.zip") as src, zipfile.ZipFile(tampered, "w") as out:
            for info in src.infolist():
                out.writestr(info.filename, b"adulterado" if info.filename.endswith("model.safetensors") else src.read(info.filename))
        m["sha256"] = W.sha256_file(tampered)          # pacote "íntegro" para o checksum externo, mas com arquivo diferente do manifesto
        dest = self.tmp / "dest"
        with self.assertRaises(W.WeightsError) as ctx:
            W.install(m, dest, tampered, say=lambda *a, **k: None)
        self.assertIn("Instalação inválida", str(ctx.exception))
        self.assertFalse((dest / NAME).exists())
        self.assertFalse(any(dest.glob(".*")), "a pasta temporária é removida")


class ZipSlipTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, True)

    def zip_with(self, member: str) -> Path:
        p = self.tmp / "evil.zip"
        with zipfile.ZipFile(p, "w") as z:
            z.writestr(member, "x")
        return p

    def test_rejects_parent_traversal_absolute_paths_backslashes_and_other_folders(self):
        for member in (f"{NAME}/../escapou.txt", "../escapou.txt", "/etc/passwd", f"{NAME}\\..\\escapou.txt", "outra-pasta/arquivo.txt", "arquivo-solto.txt"):
            with self.subTest(member=member):
                with self.assertRaises(W.WeightsError):
                    W.safe_extract(self.zip_with(member), self.tmp / "dest", NAME)
        self.assertFalse((self.tmp / "escapou.txt").exists())

    def test_accepts_a_normal_member(self):
        written = W.safe_extract(self.zip_with(f"{NAME}/seed1/model.safetensors"), self.tmp / "dest", NAME)
        self.assertEqual([f"{NAME}/seed1/model.safetensors"], written)


class DownloadTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = Path(tempfile.mkdtemp())
        (cls.tmp / "serve").mkdir()
        (cls.tmp / "serve" / "ok.bin").write_bytes(b"conteudo " * 5000)
        handler = partial(http.server.SimpleHTTPRequestHandler, directory=str(cls.tmp / "serve"))
        handler.log_message = lambda *a, **k: None
        cls.httpd = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
        cls.base = f"http://127.0.0.1:{cls.httpd.server_address[1]}"
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown(); cls.httpd.server_close()
        shutil.rmtree(cls.tmp, ignore_errors=True)

    def test_download_with_the_right_checksum(self):
        dest = self.tmp / "out" / "ok.bin"
        sha = W.sha256_file(self.tmp / "serve" / "ok.bin")
        W.download(f"{self.base}/ok.bin", dest, sha, say=lambda *a, **k: None)
        self.assertEqual(sha, W.sha256_file(dest))

    def test_wrong_checksum_deletes_the_file(self):
        dest = self.tmp / "out2" / "ok.bin"
        with self.assertRaises(W.WeightsError) as ctx:
            W.download(f"{self.base}/ok.bin", dest, "0" * 64, say=lambda *a, **k: None)
        self.assertIn("não confere", str(ctx.exception))
        self.assertFalse(dest.exists())
        self.assertFalse(dest.with_name(dest.name + ".part").exists())

    def test_a_failed_download_never_touches_an_existing_good_file(self):
        dest = self.tmp / "keep" / "ok.bin"
        dest.parent.mkdir()
        dest.write_bytes(b"pacote bom ja instalado")
        for url, sha in ((f"{self.base}/ok.bin", "0" * 64), (f"{self.base}/nao-existe.bin", "0" * 64), ("http://127.0.0.1:1/x", "0" * 64)):
            with self.assertRaises(W.WeightsError):
                W.download(url, dest, sha, say=lambda *a, **k: None)
        self.assertEqual(b"pacote bom ja instalado", dest.read_bytes())
        self.assertEqual([], list(dest.parent.glob("*.part")))

    def test_404_explains_that_the_release_may_not_exist_yet(self):
        with self.assertRaises(W.WeightsError) as ctx:
            W.download(f"{self.base}/nao-existe.zip", self.tmp / "out3" / "x.zip", "0" * 64, say=lambda *a, **k: None)
        self.assertIn("--file", str(ctx.exception))

    def test_connection_refused_is_a_clean_error(self):
        with self.assertRaises(W.WeightsError):
            W.download("http://127.0.0.1:1/x.zip", self.tmp / "out4" / "x.zip", "0" * 64, say=lambda *a, **k: None)


if __name__ == "__main__":
    unittest.main()
