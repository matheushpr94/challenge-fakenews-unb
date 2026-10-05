"""Estrutura verificável da afirmação: tipo, entidade principal, propriedade, quantidade,
período, negação e qualificadores.

As regras cobrem formatos estruturados (números, unidades, anos, comparadores). Elas não
entendem o sentido de frases livres; para isso o serviço usa um modelo opcional (llm.py)."""
import re
from dataclasses import asdict, dataclass, field

from .textutil import MONTHS, NEG_NORM, STOP_NORM, WEEKDAYS, norm, stem

NUM_WORDS = {"dois": 2, "duas": 2, "tres": 3, "quatro": 4, "cinco": 5, "seis": 6, "sete": 7, "oito": 8,
             "nove": 9, "dez": 10, "onze": 11, "doze": 12, "treze": 13, "catorze": 14, "quatorze": 14,
             "quinze": 15, "dezesseis": 16, "dezessete": 17, "dezoito": 18, "dezenove": 19, "vinte": 20,
             "trinta": 30, "quarenta": 40, "cinquenta": 50, "sessenta": 60, "setenta": 70, "oitenta": 80,
             "noventa": 90, "cem": 100, "nenhum": 0, "nenhuma": 0}
# Ordinal seguido da unidade no singular: "nono título" = 9 no total (contagem acumulada).
ORDINALS = {"primeiro": 1, "primeira": 1, "segundo": 2, "segunda": 2, "terceiro": 3, "terceira": 3,
            "quarto": 4, "quarta": 4, "quinto": 5, "quinta": 5, "sexto": 6, "sexta": 6, "setimo": 7,
            "setima": 7, "oitavo": 8, "oitava": 8, "nono": 9, "nona": 9, "decimo": 10, "decima": 10}
# "um/uma" só contam como número nas fontes, quando seguidos da mesma unidade da afirmação.
WEAK_NUM_WORDS = {"um": 1, "uma": 1}
SCALE = {"mil": 1e3, "milhao": 1e6, "milhoes": 1e6, "bilhao": 1e9, "bilhoes": 1e9, "trilhao": 1e12,
         "trilhoes": 1e12}
# Construção do português "tricampeão", "hexacampeã" = número de títulos.
MULT_RE = re.compile(r"^(bi|tri|tetra|penta|hexa|hepta|octa|enea|deca)-?campe")
MULT = {"bi": 2, "tri": 3, "tetra": 4, "penta": 5, "hexa": 6, "hepta": 7, "octa": 8, "enea": 9, "deca": 10}
COMPARATORS = [("mais de", "min_excl"), ("acima de", "min_excl"), ("superior a", "min_excl"),
               ("pelo menos", "min"), ("ao menos", "min"), ("no minimo", "min"), ("menos de", "max_excl"),
               ("abaixo de", "max_excl"), ("inferior a", "max_excl"), ("no maximo", "max"),
               ("cerca de", "approx"), ("aproximadamente", "approx"), ("quase", "approx"),
               ("em torno de", "approx"), ("mais ou menos", "approx")]
QUAL_STOP = {"em", "segundo", "desde", "ate", "apos", "para", "por", "com",
             "que", "e", "ou", "entre", "durante", "contra", "sobre", "ao", "aos", "como"}
QUAL_STOP |= {norm(w) for w in WEEKDAYS} | {norm(w) for w in MONTHS} | {
    "nesta", "neste", "nessa", "nesse", "hoje", "ontem", "agora", "atualmente", "feira", "segundo", "conforme"}
QUAL_LINK = {"de", "da", "do", "das", "dos", "no", "na", "nos", "nas"}
TOKEN_RE = re.compile(r"R\$|US\$|€|\d+[ºª°]|\d+(?:[.,]\d+)*|%|[^\W\d_][\w\-]*|[.,;:!?()\"“”]", re.U)
VERB_RE = re.compile(r"^[a-z]{3,}(ou|eu|iu|aram|eram|iram)$")
PARTICIPLE_RE = re.compile(r"^[a-z]{3,}(ados|idos|adas|idas|ado|ido|ada|ida)$")
YEAR_RE = re.compile(r"\b(1[5-9]\d\d|20[0-9]\d)\b")
GENERIC_UNITS = {"%", "pessoas", "reais", "dolares", "euros", "anos", "vezes", "casos", "mortes", "mortos"}

FUTURE_RE = re.compile(r"\b(vai|v[aã]o|ir[aá]|ir[aã]o|ser[aá]|ser[aã]o|dever[aá]|pretende|pretendem|"
                       r"previs[aã]o|prev[eê]|promete|prometem|pr[oó]xim[oa]s?|futuramente)\b", re.I)
OPINION_RE = re.compile(r"\b(melhor(?:es)?|pior(?:es)?|lind[oa]s?|fei[oa]s?|incr[ií]ve(?:l|is)|horr[ií]ve(?:l|is)|"
                        r"injust[oa]s?|absurd[oa]s?|vergonh\w*|deveria\w*|acho|acredito|opini[aã]o|"
                        r"genial|rid[ií]cul\w*|maravilhos\w*|p[eé]ssim\w*|[oó]tim[oa]s?)\b", re.I)
CUMULATIVE_RE = re.compile(r"\b(tem|t[eê]m|possui|possuem|acumula\w*|soma\w*|total|ao todo|j[aá]|"
                           r"conquist\w*|ganh\w*|venc\w*)\b", re.I)
RECENT_RE = re.compile(r"\b(hoje|ontem|anteontem|agora|nesta|neste|esta semana|recentemente|"
                       r"acaba de|acabou de|atualmente)\b", re.I)

TYPE_LABEL = {
    "contagem": "Quantidade que pode mudar com o tempo",
    "quantidade_periodo": "Quantidade referente a um período",
    "fato_historico": "Fato histórico ou estável",
    "acontecimento_recente": "Acontecimento recente",
    "acontecimento": "Acontecimento (período não informado)",
    "opiniao": "Opinião ou valoração",
    "previsao": "Previsão ou promessa",
}


@dataclass
class Quantity:
    valor: float
    texto: str
    unidade: str
    chave_unidade: str
    qualificadores: list = field(default_factory=list)
    comparador: str = "eq"
    negada: bool = False  # "não tem 5 títulos": a entrada nega este valor
    posicao: int = 0

    def to_dict(self):
        d = asdict(self)
        d.pop("posicao")
        return d


def to_float(s):
    if re.fullmatch(r"\d{1,3}(\.\d{3})+(,\d+)?", s):
        s = s.replace(".", "").replace(",", ".")
    elif re.fullmatch(r"\d{1,3}(,\d{3})+", s) and len(s) > 5:
        s = s.replace(",", "")
    else:
        s = s.replace(",", ".")
    try:
        return float(s)
    except ValueError:
        return None


def unit_key(word):
    w = norm(word)
    if word == "%" or w in ("porcento", "por cento"):
        return "%"
    return stem(w)


def fmt_value(v):
    if v is None:
        return "?"
    if v >= 1e6 and v % 1e5 == 0:
        return f"{v / 1e6:g} milhões".replace(".", ",")
    return f"{v:,.2f}".rstrip("0").rstrip(".").replace(",", "X").replace(".", ",").replace("X", ".").rstrip(",")


def extract_quantities(text, exclude_tokens=frozenset(), weak_units=None):
    """Encontra 'número + unidade (+ qualificadores)'. `weak_units` habilita 'um/uma' para essas unidades."""
    toks = TOKEN_RE.findall(text or "")
    ntoks = [norm(t) if t not in ("%", "R$", "US$", "€") else t for t in toks]
    out = []
    for i, t in enumerate(toks):
        n = ntoks[i]
        value, j, unit, ukey, mult_form = None, i + 1, None, None, False
        if re.fullmatch(r"\d+(?:[.,]\d+)*", t):
            nxt = ntoks[j] if j < len(toks) else ""
            if YEAR_RE.fullmatch(t) and (not nxt or nxt in STOP_NORM or not re.match(r"\w", nxt) or nxt in QUAL_STOP):
                continue  # ano, não quantidade
            value = to_float(t)
        elif n in NUM_WORDS:
            value = NUM_WORDS[n]
        elif weak_units and n in WEAK_NUM_WORDS:
            value = WEAK_NUM_WORDS[n]
        elif n in ORDINALS and i + 1 < len(toks) and not ntoks[i + 1] in STOP_NORM \
                and re.match(r"[^\W\d_]", toks[i + 1]) and weak_units is not None \
                and unit_key(toks[i + 1]) in weak_units:
            value = ORDINALS[n]
        elif re.fullmatch(r"\d+[ºª°]", t or "") and weak_units is not None:
            value = float(t[:-1])
        elif MULT_RE.match(n or ""):
            value, unit, ukey, mult_form = MULT[MULT_RE.match(n).group(1)], t, unit_key("titulos"), True
        if value is None:
            continue
        raw = [t]
        if not mult_form:
            if j < len(toks) and ntoks[j] in SCALE:
                value *= SCALE[ntoks[j]]
                raw.append(toks[j])
                j += 1
            if i > 0 and toks[i - 1] in ("R$", "US$", "€"):
                unit = {"R$": "reais", "US$": "dólares", "€": "euros"}[toks[i - 1]]
                raw.insert(0, toks[i - 1])
            elif j < len(toks) and toks[j] == "%":
                unit, j = "%", j + 1
                raw.append("%")
            elif j + 1 < len(toks) and ntoks[j] == "por" and ntoks[j + 1] == "cento":
                unit, j = "%", j + 2
                raw.append("por cento")
            else:
                while j < len(toks) and ntoks[j] in QUAL_LINK:
                    j += 1
                if j < len(toks) and re.match(r"[^\W\d_]", toks[j]) and ntoks[j] not in STOP_NORM \
                        and ntoks[j] not in QUAL_STOP and ntoks[j] not in SCALE:
                    unit = toks[j]
                    raw.append(toks[j])
                    j += 1
            if not unit:
                continue
            ukey = unit_key(unit)
            if weak_units is not None and n in WEAK_NUM_WORDS and ukey not in weak_units:
                continue
        quals, k = [], j
        while k < len(toks) and k < j + 6 and len(quals) < 3:
            nk = ntoks[k]
            if not re.match(r"[^\W\d_]", toks[k]) or nk in QUAL_STOP or nk in NEG_NORM:
                break
            if VERB_RE.search(nk) or nk in ("foram", "eram", "sao", "estao", "tem", "tinha", "tinham", "ha"):
                break  # verbo encerra a descrição do número
            if PARTICIPLE_RE.search(nk):
                k += 1
                continue  # "títulos conquistados": particípio descreve, não é categoria
            if nk not in QUAL_LINK and nk not in STOP_NORM and nk not in exclude_tokens and len(nk) >= 3:
                quals.append(toks[k])
            k += 1
        comp = "eq"
        before = " ".join(ntoks[max(0, i - 3):i])
        if toks[i - 1:i] in (["R$"], ["US$"], ["€"]):
            before = " ".join(ntoks[max(0, i - 4):i - 1])
        for phrase, c in COMPARATORS:
            if before.endswith(phrase):
                comp = c
                break
        negated = n not in ("nenhum", "nenhuma") and any(
            ntoks[k2] in ("nao", "nunca", "jamais") for k2 in range(max(0, i - 5), i))
        out.append(Quantity(valor=value, texto=" ".join(raw), unidade=unit, chave_unidade=ukey, negada=negated,
                            qualificadores=quals, comparador=comp, posicao=i))
    return out


def interval(q):
    v = q.valor
    eq = (v * 0.995, v * 1.005) if v >= 1000 else (v, v)  # números grandes costumam ser arredondados
    return {"eq": eq, "approx": (v * 0.9, v * 1.1), "min": (v, float("inf")),
            "min_excl": (v * 1.0000001 + 1e-9, float("inf")), "max": (0.0, v),
            "max_excl": (0.0, v * 0.9999999)}.get(q.comparador, eq)


def compatible(a, b):
    a0, a1 = interval(a)
    b0, b1 = interval(b)
    return a0 <= b1 and b0 <= a1


def qualifier_keys(q):
    # Prefixo curto: "mundo"/"mundiais", "nacional"/"nacionais", "Brasileirão"/"brasileiros".
    return {norm(w)[:4] for w in q.qualificadores if len(norm(w)) >= 4}


def classify(text, quantity, years, now_year, relative, is_question):
    n = text or ""
    if OPINION_RE.search(n) and not quantity:
        return "opiniao"
    if FUTURE_RE.search(n) and not any(y < now_year for y in years):
        return "previsao"
    if quantity or re.match(r"^\s*(é verdade que\s+)?quant[oa]s\b", n, re.I):
        if years and all(y < now_year for y in years) and not CUMULATIVE_RE.search(n):
            return "quantidade_periodo"
        return "contagem"
    if relative or RECENT_RE.search(n) or (years and max(years) >= now_year):
        return "acontecimento_recente"
    if years and max(years) < now_year:
        return "fato_historico"
    return "acontecimento"


def detail_text(q, entity):
    unit_txt = ("%" if q.chave_unidade == "%" else q.unidade) + (
        " " + " ".join(q.qualificadores) if q.qualificadores else "")
    detail = f"Quantidade de {unit_txt}" + (f" — {entity}" if entity else "")
    if q.valor is not None:
        detail += f": informado {'não ' if q.negada else ''}{q.texto}"
    return detail


def structure(interp, main_text, now_year):
    ents = interp.entidades
    # Só a entidade principal sai dos qualificadores; outras ("Copa do Mundo") podem ser a categoria.
    ent_tokens = frozenset(norm(ents[0]).split()) if ents else frozenset()
    qs = extract_quantities(main_text, ent_tokens)
    q = qs[0] if qs else None
    tipo = classify(main_text, q, interp.anos, now_year, interp.tempo_relativo, interp.tipo == "pergunta")
    unit_stem = q.chave_unidade if q else None
    prop_terms = [t for t in interp.termos if stem(t) != unit_stem]
    m = re.match(r"^\s*quant[oa]s\s+([^\W\d_]+)((?:\s+[^\W\d_]+){0,3})", main_text, re.I)
    if not q and m:
        unit = m.group(1)
        qwords = [w for w in m.group(2).split() if norm(w) not in STOP_NORM and norm(w) not in ent_tokens
                  and norm(w) not in QUAL_STOP][:3]
        q = Quantity(valor=None, texto=None, unidade=unit, chave_unidade=unit_key(unit), qualificadores=qwords)
    q_struct = q.to_dict() if q else None
    quals = []
    for phrase, _c in COMPARATORS:
        if phrase in norm(main_text):
            quals.append(phrase)
    for m2 in re.finditer(r"\b(segundo|de acordo com|conforme)\s+([^,.;]+)", main_text, re.I):
        quals.append(m2.group(0).strip())
    entity = ents[0] if ents else (prop_terms[0] if prop_terms else None)
    if q:
        detail = detail_text(q, entity)
    elif tipo in ("fato_historico",) and interp.anos:
        detail = f"Data/ano do fato ({', '.join(map(str, interp.anos))})"
    else:
        detail = " ".join(prop_terms[:4]) or interp.assunto
    neg_detail = bool(q.negada) if q else bool(interp.negacoes and tipo == "fato_historico")
    return {
        "detalhe_negado": neg_detail,
        "tipo": tipo,
        "tipo_texto": TYPE_LABEL[tipo],
        "entidade_principal": entity,
        "propriedade": " ".join(prop_terms[:4]),
        "quantidade": q_struct,
        "periodo": {"anos": interp.anos, "datas": [d["texto"] for d in interp.datas],
                    "relativo": interp.tempo_relativo},
        "negacao": bool(interp.negacoes),
        "negacao_texto": interp.negacoes[0] if interp.negacoes else None,
        "qualificadores": quals,
        "detalhe_verificavel": detail,
    }, q
