"""Confere o ambiente (rode depois de instalar requirements-bert.txt). Não baixa nada."""
from __future__ import annotations

import importlib
import sys

print("python", sys.version.split()[0])
for name in ("pandas", "numpy", "sklearn", "torch", "transformers", "accelerate", "sentencepiece"):
    try:
        mod = importlib.import_module(name)
        print(f"  {name:14s} {getattr(mod, '__version__', '?')}")
    except Exception as e:  # noqa: BLE001
        print(f"  {name:14s} NÃO INSTALADO ({type(e).__name__})")
try:
    import torch
    if torch.cuda.is_available():
        p = torch.cuda.get_device_properties(0)
        print(f"GPU: {p.name}  {p.total_memory / 2**30:.1f} GiB  capacidade {p.major}.{p.minor}  CUDA do torch {torch.version.cuda}")
        x = torch.randn(1024, 1024, device="cuda"); print("  multiplicação de matrizes na GPU: ok", float((x @ x).sum().isfinite()))
    elif getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
        print("GPU: Apple MPS disponível")
    else:
        print("Sem GPU utilizável: o treino rodaria na CPU (lento).")
except ImportError:
    pass
