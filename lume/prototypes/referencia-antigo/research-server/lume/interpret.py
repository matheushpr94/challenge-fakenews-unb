"""Interpretação da entrada por regras: entidades, termos do acontecimento, números, datas,
negações e consultas. Não usa modelo; as limitações estão documentadas no README."""
import re
from datetime import datetime, timezone
from dataclasses import asdict, dataclass, field

from .claim import structure
from .textutil import (MONTHS, NEG_NORM, STOP_NORM, WEEKDAYS, WORD_RE, norm, number_key,
                       stem, tokens)

MAX_INPUT = 5000
CONNECTORS = {"de", "da", "do", "das", "dos", "del"}
# Palavras genéricas que não descrevem o acontecimento em si.
GENERIC = {norm(w) for w in """aconteceu acontece acontecer ocorreu ocorre noticia noticias notícia
notícias informação informacao caso assunto polêmica polemica sabe saber alguém alguem pessoa
pessoas gente hoje ontem agora semana ano anos mês mes dia dias recentemente vídeo video post
publicação print foto mil milhão milhao milhões milhoes bilhão bilhao bilhões bilhoes reais
dólares dolares cento existe existem existir existia existiam pode podem poderia situação situacao situações
situacoes alguma algum""".split()}
BYLINE_RE = re.compile(r"(?:^|(?<=[.\n]))\s*(?:Por|By)\s+[A-ZÀ-Ý][\w'’]+(?:\s+(?:d[aeo]s?\s+)?[A-ZÀ-Ý][\w'’]+)*\s*[,|–-]?",
                       re.M)
TIME_WORDS = {norm(w) for w in MONTHS} | {norm(w) for w in WEEKDAYS} | {"feira"}

FRAMING_RE = re.compile(
    r"^\s*(é|e)?\s*(verdade|fato|real)\s+que\s+|^\s*ser[aá]\s+que\s+|^\s*procede\s+que\s+|"
    r"^\s*(eu\s+)?(vi|li|ouvi|recebi)\s+(dizer\s+)?que\s+|^\s*(dizem|disseram|falaram|estão\s+dizendo|"
    r"est[aã]o\s+falando)\s+que\s+|^\s*(é|e)\s+verdade\s*[:,-]?\s*",
    re.I)
RELATIVE_RE = re.compile(
    r"\b(hoje|ontem|anteontem|agora h[aá] pouco|nesta semana|esta semana|neste fim de semana|"
    r"recentemente|acaba de|acabou de|nest[ae] (?:segunda|ter[çc]a|quarta|quinta|sexta|s[aá]bado|"
    r"domingo)(?:-feira)?)\b", re.I)
MONTH_ALT = "|".join(sorted(MONTHS, key=len, reverse=True))
DATE_PATTERNS = [
    (re.compile(r"\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b"), "dmy"),
    (re.compile(rf"\b(\d{{1,2}})º?\s+de\s+({MONTH_ALT})(?:\s+de\s+(\d{{4}}))?\b", re.I), "d_month_y"),
    (re.compile(rf"\b({MONTH_ALT})\s+de\s+(\d{{4}})\b", re.I), "month_y"),
]
YEAR_RE = re.compile(r"\b(1[5-9]\d\d|20\d\d)\b")
NUMBER_RE = re.compile(
    r"(?:R\$\s*|US\$\s*)?\b\d+(?:[.,]\d+)*\b(?:\s*(?:%|por cento|mil\b|milh(?:ão|ões|ao|oes)|"
    r"bilh(?:ão|ões|ao|oes)|trilh(?:ão|ões|ao|oes)|reais|d[oó]lares|pessoas|mortos|anos))?", re.I)


@dataclass
class Interpretation:
    texto_original: str
    assunto: str
    tipo: str
    entidades: list = field(default_factory=list)
    termos: list = field(default_factory=list)
    numeros: list = field(default_factory=list)
    datas: list = field(default_factory=list)
    anos: list = field(default_factory=list)
    negacoes: list = field(default_factory=list)
    tempo_relativo: list = field(default_factory=list)
    incompleto: bool = False
    consultas: list = field(default_factory=list)
    consulta_contexto: str = ""
    consulta_contexto_propriedade: str = ""
    afirmacao: dict = field(default_factory=dict)

    def to_dict(self):
        return asdict(self)


def clean_input(text):
    text = (text or "").replace("\r", "\n")[:MAX_INPUT]
    text = re.sub(r"[ \t ]+", " ", text)
    text = re.sub(r"\n{2,}", "\n", text).strip()
    prev = None
    while prev != text:
        prev = text
        text = FRAMING_RE.sub("", text, count=1).strip()
    return text.strip(" ?¿!-–—:")


def _sentences(text):
    parts = re.split(r"(?<=[.!?])\s+|\n+", text)
    return [p.strip() for p in parts if p.strip()]


def _starts_sentence(text, start):
    return start == 0 or bool(re.search(r"(?:[.!?:\"“(]|\n)\s*$", text[:start]))


def extract_entities(text):
    toks = [(m.group(0), m.start(), m.end()) for m in WORD_RE.finditer(text)]
    alpha = [t for t, _, _ in toks if t[:1].isalpha() and len(t) >= 3]
    if alpha and sum(1 for t in alpha if t.isupper()) / len(alpha) > 0.6:
        return []  # texto todo em maiúsculas: a capitalização não indica nomes próprios
    ents, cur = [], []

    def flush():
        while cur and norm(toks[cur[-1]][0]) in CONNECTORS:
            cur.pop()
        if cur:
            first = toks[cur[0]]
            phrase = text[first[1]:toks[cur[-1]][2]]
            acronym = len(cur) == 1 and first[0].isupper() and len(first[0]) >= 2
            if len(cur) > 1 or acronym or not _starts_sentence(text, first[1]):
                n = norm(phrase)
                if len(n) >= 2 and n not in STOP_NORM and n not in TIME_WORDS:
                    ents.append(phrase)
        cur.clear()

    for i, (t, s, _e) in enumerate(toks):
        contiguous = not cur or text[toks[cur[-1]][2]:s].strip() == ""
        if not contiguous:
            flush()
        low = norm(t)
        if t[0].isupper() and not t[0].isdigit() and low not in STOP_NORM:
            cur.append(i)
        elif (cur and low in CONNECTORS and i + 1 < len(toks) and toks[i + 1][0][0].isupper()
              and text[toks[i][2]:toks[i + 1][1]].strip() == ""):
            cur.append(i)
        else:
            flush()
    flush()
    out = []
    for e in sorted(ents, key=lambda x: -len(x)):
        n = norm(e)
        if not any(n == norm(o) or f" {n} " in f" {norm(o)} " for o in out):
            out.append(e)
    return sorted(out, key=lambda e: text.find(e))


def _terms(main, rest, entity_tokens):
    scores, order = {}, {}
    for weight, chunk in ((2, main), (1, rest)):
        for m in WORD_RE.finditer(chunk):
            n = norm(m.group(0))
            if (len(n) < 3 or n in STOP_NORM or n in NEG_NORM or n in GENERIC or n in TIME_WORDS
                    or n in entity_tokens or re.fullmatch(r"[\d,]+", n)):
                continue
            key = stem(n)
            if key not in scores:
                scores[key] = 0
                order[key] = (len(order), m.group(0).lower())
            scores[key] += weight
    ranked = sorted(scores, key=lambda k: (-scores[k], order[k][0]))
    return [order[k][1] for k in ranked]


def _dates(text):
    found = []
    for pattern, kind in DATE_PATTERNS:
        for m in pattern.finditer(text):
            d = {"texto": m.group(0), "dia": None, "mes": None, "ano": None}
            if kind == "dmy":
                d["dia"], d["mes"] = int(m.group(1)), int(m.group(2))
                if m.group(3):
                    y = int(m.group(3))
                    d["ano"] = y + 2000 if y < 100 else y
                if not (1 <= d["dia"] <= 31 and 1 <= d["mes"] <= 12):
                    continue
            elif kind == "d_month_y":
                d["dia"], d["mes"] = int(m.group(1)), MONTHS[m.group(2).lower()]
                d["ano"] = int(m.group(3)) if m.group(3) else None
            else:
                d["mes"], d["ano"] = MONTHS[m.group(1).lower()], int(m.group(2))
            if not any(m.group(0) in f["texto"] for f in found):
                found.append(d)
    return found


def interpret(text, now=None):
    now_year = (now or datetime.now(timezone.utc)).year
    clean = clean_input(text)
    original = (text or "").strip()[:MAX_INPUT]
    stripped = original.rstrip()
    is_question = stripped.endswith("?") or bool(
        re.match(r"^\s*(quem|qual|quais|quando|onde|como|por que|porque|o que|é verdade|será)\b",
                 original, re.I))
    sents = _sentences(clean)
    main = next((s for s in sents if len([t for t in tokens(s) if t not in STOP_NORM]) >= 3),
                sents[0] if sents else "")
    rest = " ".join(s for s in sents if s is not main)
    ents = extract_entities(BYLINE_RE.sub(" ", clean))
    # Entidades da frase principal vêm primeiro; as demais só complementam.
    main_n = f" {norm(main)} "
    ents.sort(key=lambda e: 0 if f" {norm(e)} " in main_n else 1)
    interp_main_ents = [e for e in ents if f" {norm(e)} " in main_n]
    ent_tokens = set(tokens(" ".join(ents)))
    terms = _terms(main, rest, ent_tokens)[:6]

    dates = _dates(clean)
    date_spans = " ".join(d["texto"] for d in dates)
    years = sorted({int(y) for y in YEAR_RE.findall(clean)} |
                   {d["ano"] for d in dates if d["ano"]})
    nums = []
    for m in NUMBER_RE.finditer(clean):
        raw = m.group(0).strip()
        key = number_key(raw)
        if not key or raw in date_spans or (key.isdigit() and int(key) in years and len(key) == 4):
            continue
        if not any(n["chave"] == key for n in nums):
            nums.append({"texto": raw, "chave": key})

    negs = []
    words = WORD_RE.findall(clean)
    for i, w in enumerate(words):
        if norm(w) in NEG_NORM:
            negs.append(" ".join(words[i:i + 4]))
    relative = [m.group(0) for m in RELATIVE_RE.finditer(original)]

    interp = Interpretation(
        texto_original=original, assunto=main if main else clean, tipo="pergunta" if is_question else "afirmacao",
        entidades=ents[:6], termos=terms, numeros=nums[:4], datas=dates[:3], anos=years[:3],
        negacoes=negs[:3], tempo_relativo=relative[:2])
    interp.afirmacao, interp.quantity = structure(interp, main or clean, now_year)
    # Incompleta = sem acontecimento ou propriedade identificável (não por falta de resultados).
    interp.incompleto = not interp.quantity and (not terms or len(ents) + len(terms) <= 1)
    interp.consultas = build_queries(interp, clean, interp_main_ents or ents)
    interp.consulta_contexto = ents[0] if ents else " ".join(terms[:2])
    interp.consulta_contexto_propriedade = context_query(interp)
    return interp


def context_query(interp):
    """Consulta de contexto voltada ao detalhe: entidade + propriedade (+ período)."""
    q = getattr(interp, "quantity", None)
    ent = interp.entidades[:1]
    ent_tokens = {t for e in interp.entidades for t in norm(e).split()}
    if q is not None:
        prop = ([] if q.chave_unidade == "%" else [q.unidade]) + q.qualificadores
        if q.chave_unidade == "%" or not ent:
            prop += [t for t in interp.termos if stem(t) != q.chave_unidade and norm(t) not in ent_tokens][:1]
    else:
        prop = [t for t in interp.termos if norm(t) not in ent_tokens][:2]
    if not prop:
        return ""
    return _join(ent + prop + [str(y) for y in interp.anos[:1]], 8)


def _join(parts, max_words=10):
    seen, out = set(), []
    for p in parts:
        for w in str(p).split():
            n = norm(w)
            if n and n not in seen:
                seen.add(n)
                out.append(w.strip(",.;:!?\"“”()"))
    return " ".join(out[:max_words]).strip()


def build_queries(interp, clean, ents):
    """Poucas consultas complementares: específica -> acontecimento -> ampliada."""
    terms = interp.termos
    nums = [n["texto"] for n in interp.numeros[:1]]
    years = [str(y) for y in interp.anos[:1]]
    q, tipo = getattr(interp, "quantity", None), interp.afirmacao.get("tipo")
    if interp.incompleto:
        candidates = [("entrada", _join([clean], 12)), ("ampliada", _join(ents[:2] + terms[:2]))]
    elif q:
        # Uma consulta com o valor alegado e outras SEM ele, para descobrir o valor documentado.
        # A comparação continua usando o valor alegado.
        qual_stems = {stem(w) for w in q.qualificadores}
        others = [t for t in terms if stem(t) != q.chave_unidade and stem(t) not in qual_stems][:2]
        generic_unit = q.chave_unidade == "%" or norm(q.unidade) in ("pessoas", "casos", "mortes", "mortos",
                                                                     "reais", "dolares", "vezes", "anos")
        subj = ents[:2] + (others if (generic_unit or not ents) else [])
        unit_words = [] if q.chave_unidade == "%" else [q.unidade]
        prop = unit_words + q.qualificadores
        candidates = []
        if q.valor is not None:
            candidates.append(("especifica", _join(subj + [q.texto] + q.qualificadores + years, 10)))
        candidates.append(("valor_documentado", _join(subj + prop + years, 9)))
        if unit_words:
            word = "quantas" if norm(q.unidade).endswith(("as", "a")) else "quantos"
            candidates.append(("pergunta_valor", _join([word] + prop + subj + years, 10)))
        else:
            candidates.append(("dados_oficiais", _join(subj + years + ["dados", "oficiais"], 8)))
    elif tipo == "fato_historico":
        candidates = [
            ("especifica", _join(ents[:3] + terms[:3] + years, 10)),
            ("sem_data", _join(ents[:2] + terms[:4], 8)),
            ("ampliada", _join(ents[:2] + terms[:1] if ents else terms[:2], 6)),
        ]
    else:
        candidates = [
            ("especifica", _join(ents[:3] + terms[:3] + nums + years, 11)),
            ("acontecimento", _join(ents[:1] + terms[:5], 8)),
            ("ampliada", _join(ents[:2] + terms[:1] if ents else terms[:2], 6)),
        ]
    out, seen = [], set()
    for purpose, q in candidates:
        key = frozenset(norm(q).split())
        if q and key not in seen:
            seen.add(key)
            out.append({"texto": q, "finalidade": purpose})
    return out
