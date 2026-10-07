"""Pré-processamento de texto na hora de classificar (não altera o treino).

O corpus de treino é de frases em caixa normal: só 15 de 6.191 estão em CAIXA ALTA. Em caixa alta o modelo (BERTimbau,
que distingue maiúsculas de minúsculas) muda de resposta, por exemplo "xerife das composições nas redes sociais" passa de
enviesada (0,98) para citação (0,96). Títulos lidos por OCR costumam vir assim.
"""
from __future__ import annotations

CAPS_RATIO = 0.7      # fração de letras maiúsculas a partir da qual o texto é tratado como caixa alta
MIN_LETTERS = 6       # textos muito curtos ("PT", "STF") não são alterados


def is_all_caps(text: str) -> bool:
    letters = [c for c in text if c.isalpha()]
    return len(letters) >= MIN_LETTERS and sum(c.isupper() for c in letters) / len(letters) >= CAPS_RATIO


def normalize_case(text: str) -> str:
    """Texto em caixa alta vira minúsculas com a primeira letra maiúscula; o resto fica como está.

    Nomes próprios no meio da frase perdem a inicial maiúscula (não há como saber quais eram), o que é um custo conhecido.
    """
    if not is_all_caps(text):
        return text
    low = text.lower()
    for i, c in enumerate(low):
        if c.isalpha():
            return low[:i] + c.upper() + low[i + 1:]
    return low
