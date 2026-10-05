"""Avaliação de relevância pelo acontecimento (entidades + termos + detalhes), agrupamento de
republicações e classificação em: direto, anterior, contexto ou descartado."""
import urllib.parse
from dataclasses import dataclass, field
from datetime import timedelta

from .interpret import CONNECTORS, extract_entities
from .textutil import STOP_NORM, has_negation, jaccard, norm, numbers_in, stem

RECENT_DAYS = 30
RELATIVE_MAX_AGE = timedelta(days=14)
CONTENT_RANK = {"completo": 3, "resumo": 2, "trecho": 2, "titulo": 1}


@dataclass
class Assessment:
    category: str
    score: float
    relation: str
    ent_hits: list = field(default_factory=list)
    ent_missing: list = field(default_factory=list)
    term_hits: list = field(default_factory=list)
    num_hits: list = field(default_factory=list)
    period: str = "sem_data"
    alerts: list = field(default_factory=list)
    reason: str = ""


def _acronyms(text):
    out = set()
    for e in extract_entities(text):
        words = [w for w in norm(e).split() if w not in CONNECTORS]
        if len(words) >= 2:
            out.add("".join(w[0] for w in words))
    return out


def entity_match(entity, cand_norm, cand_tokens, cand_acronyms):
    n = norm(entity)
    if f" {n} " in f" {cand_norm} ":
        return True
    toks = [t for t in n.split() if len(t) >= 3 and t not in CONNECTORS]
    if len(toks) <= 1:
        return (entity.isupper() and n in cand_acronyms) or (bool(toks) and toks[0] in cand_tokens)
    hits = [t for t in toks if t in cand_tokens]
    if len(hits) >= (len(toks) + 1) // 2 and any(len(t) >= 4 for t in hits):
        return True
    return "".join(t[0] for t in toks) in cand_tokens


def _period(cand, now):
    if not cand.published:
        return "sem_data"
    return "recente" if now - cand.published <= timedelta(days=RECENT_DAYS) else "anterior"


def assess(interp, cand, now):
    text = f"{cand.title}. {cand.snippet}"
    cn = norm(text)
    ctoks = set(cn.split())
    cstems = {stem(t) for t in ctoks if len(t) >= 3}
    ents, terms = interp.entidades[:4], interp.termos[:5]
    ent_hits = [e for e in ents if entity_match(e, cn, ctoks, _acronyms(text))]
    term_hits = [t for t in terms if stem(t) in cstems]
    cand_nums = numbers_in(text)
    num_hits = [n["texto"] for n in interp.numeros if n["chave"] in cand_nums]
    ent_cov = len(ent_hits) / len(ents) if ents else 1.0
    term_cov = len(term_hits) / len(terms) if terms else 0.0
    score = 0.5 * ent_cov + 0.5 * term_cov + 0.1 * len(num_hits) + 0.02 * CONTENT_RANK[cand.content_kind]
    a = Assessment(category="descartado", score=round(score, 3), relation="",
                   ent_hits=ent_hits, ent_missing=[e for e in ents if e not in ent_hits],
                   term_hits=term_hits, num_hits=num_hits, period=_period(cand, now))

    need = 1 if len(terms) <= 1 else 2
    if not ents:
        # Sem nomes próprios, os termos carregam todo o sentido: exige quase todos.
        need = len(terms) if len(terms) <= 3 else -(-2 * len(terms) // 3)
    # Números citados contam como um detalhe do acontecimento (ex.: placar, valor).
    num_detail = bool(num_hits) and (len(num_hits) == len(interp.numeros) or
                                     any(len(n["chave"]) >= 2 for n in interp.numeros if n["texto"] in num_hits))
    term_hits_n = len(term_hits) + (1 if num_detail else 0)

    if cand.origin == "wikipedia":
        if cand.disambiguation:
            a.reason = "página de desambiguação"
        elif ent_hits or term_hits:
            a.category, a.relation = "contexto", "Contexto geral (enciclopédia)"
        else:
            a.reason = "enciclopédia sem relação com os termos"
        return a

    if interp.incompleto:
        if ent_hits or term_hits:
            a.category, a.relation = "contexto", "Relacionado aos termos informados"
        else:
            a.reason = "nenhum termo da entrada"
        return a

    if ents and ent_hits and not term_hits_n:
        reference_claim = getattr(interp, "quantity", None) is not None or \
            interp.afirmacao.get("tipo") == "fato_historico"
        if reference_claim and cand.origin == "bing_web":
            # Página de referência sobre a entidade: pode conter o valor/ano; será lida, não é resposta direta.
            a.category, a.relation = "contexto", "Página de referência sobre a entidade"
            return a
        a.reason = "apenas o nome coincide; outro assunto"
        return a
    full_ents = not ents or ent_cov == 1.0
    if (full_ents and term_hits_n >= need) or (ent_cov >= 0.5 and term_hits_n >= need + 1):
        a.category, a.relation = "direto", "Trata do mesmo acontecimento"
    elif ent_hits and term_hits_n:
        a.category = "contexto"
        a.relation = "Mesma pessoa/instituição, outro aspecto ou só parte do acontecimento"
    elif not ents and len(term_hits) >= 2 and term_cov >= 0.5:
        a.category, a.relation = "contexto", "Mesmo tema, sem todos os termos da entrada"
    elif ents and not ent_hits and len(term_hits) >= need and term_cov >= 0.6:
        a.category = "contexto"
        a.relation = "Mesmo tema, sem citar " + ", ".join(ents[:2])
    else:
        a.reason = f"poucos detalhes em comum (entidades {len(ent_hits)}/{len(ents)}, termos {len(term_hits)}/{len(terms)})"
        return a

    # Tempo: não tratar publicação antiga como confirmação de algo recente.
    if a.category == "direto" and cand.published:
        text_years = {y for y in interp.anos if str(y) in cn}
        if interp.anos and cand.published.year not in interp.anos and not text_years:
            a.category, a.relation = "anterior", f"Mesmo assunto, publicado em {cand.published.year} (a entrada cita {', '.join(map(str, interp.anos))})"
        elif interp.tempo_relativo and now - cand.published > RELATIVE_MAX_AGE:
            a.category = "anterior"
            a.relation = f"Mesmo assunto, mas publicado em {cand.published:%d/%m/%Y}; a entrada fala em “{interp.tempo_relativo[0]}”"

    if interp.negacoes and not has_negation(text):
        a.alerts.append("A entrada contém negação e este texto não; confira se dizem o mesmo.")
    elif not interp.negacoes and has_negation(text) and a.category != "contexto":
        a.alerts.append("Este texto contém negação (ex.: “não”, “nega”); confira o sentido.")
    other_nums = {n for n in cand_nums if not (len(n) == 4 and n.isdigit() and n.startswith(("19", "20")))}
    if interp.numeros and not num_hits and other_nums and a.category != "contexto":
        a.alerts.append("Cita números diferentes dos informados; confira os detalhes.")
    if a.ent_missing and a.category == "direto":
        a.alerts.append("Não menciona: " + ", ".join(a.ent_missing))
    return a


# --------- duplicatas e republicações ---------------------------------------------------

def canonical(url):
    try:
        p = urllib.parse.urlsplit(url)
    except ValueError:
        return url
    host = (p.hostname or "").lower()
    host = host[4:] if host.startswith("www.") else host
    path = p.path.rstrip("/")
    if host.endswith("news.google.com"):
        return host + path
    return host + path.lower()


def _title_key(t):
    return {w for w in norm(t).split() if w not in STOP_NORM and len(w) >= 3}


@dataclass
class Group:
    members: list  # [(candidate, assessment)]

    @property
    def lead(self):
        return max(self.members, key=lambda m: (CONTENT_RANK[m[0].content_kind], m[1].score))


def group_candidates(pairs):
    """Remove URLs repetidas e agrupa o mesmo conteúdo publicado por vários veículos."""
    seen, unique = set(), []
    for cand, a in sorted(pairs, key=lambda p: -p[1].score):
        key = canonical(cand.url)
        if key in seen:
            continue
        seen.add(key)
        unique.append((cand, a))
    groups = []
    for cand, a in unique:
        tk = _title_key(cand.title)
        target = None
        for g in groups:
            lead_c = g.members[0][0]
            same_title = jaccard(tk, _title_key(lead_c.title)) >= 0.7
            same_snip = (cand.snippet and lead_c.snippet and len(cand.snippet) > 60 and
                         jaccard(_title_key(cand.snippet), _title_key(lead_c.snippet)) >= 0.8)
            if same_title or same_snip:
                target = g
                break
        if target:
            target.members.append((cand, a))
        else:
            groups.append(Group(members=[(cand, a)]))
    return groups
