"""Seleção conservadora de contexto: entidade + propriedade (+ período), frase completa, com fonte.

Só entra uma frase que (1) trata da entidade, (2) cita a propriedade perguntada e (3) explica o dado:
definição, método de medição, período ou condição. Frases com o próprio valor comparado ficam de fora
(já aparecem na resposta). Sem frase que cumpra tudo, não há contexto."""
import re

from .claim import extract_quantities
from .interpret import extract_entities
from .relevance import _acronyms, entity_match
from .textutil import STOP_NORM, norm, stem

MAX_ITEMS = 2
MIN_LEN, MAX_LEN = 50, 320
# Expressões gerais que indicam explicação do dado (não são assuntos).
METHOD_RE = re.compile(r"\b(segundo (?:o|a|os|as|dados|estimativas?|levantamentos?|registros?)|de acordo com|conforme|estimad\w*|estimativ\w*|censo|metodolog\w*|metodo|"
                       r"calculad\w*|calculo|consider\w*|inclu\w*|exclu\w*|defin\w*|contabiliz\w*|"
                       r"reconhecid\w*|criterio\w*|medid\w*|medicao|levantamento|apurad\w*|contagem|"
                       r"referencia|refere se|oficial|oficialmente)\b")
PERIOD_RE = re.compile(r"\b(desde|anual\w*|mensal\w*|acumulad\w*|"
                       r"periodo|data de referencia)\b")
# Frase que começa retomando algo anterior não se sustenta sozinha como trecho.
ANAPHORIC_START = re.compile(r"^[\"“(]?(ele|ela|eles|elas|isso|isto|este|esta|estes|estas|esse|essa|esses|essas|"
                             r"aquele|aquela|al[eé]m disso|no entanto|por[eé]m|contudo|assim|tamb[eé]m|antes disso|"
                             r"depois disso|nesse|nessa|neste|nesta|dessa|desse|desta|deste)\b", re.I)
SENTENCE_RE = re.compile(r"(?<=[.!?])[\"”)]?\s+(?=[\"“(]?[A-ZÀ-Ý0-9])")


def complete_sentences(text):
    """Frases inteiras: começam com maiúscula/dígito e terminam com pontuação final (sem reticências)."""
    out = []
    for s in SENTENCE_RE.split(text or ""):
        s = s.strip()
        if not (MIN_LEN <= len(s) <= MAX_LEN):
            continue
        if not re.match(r"[\"“(]?[A-ZÀ-Ý0-9]", s) or not re.search(r"[.!?][\"”)]?$", s):
            continue  # começo ou fim cortado
        if s.endswith("...") or "…" in s or ANAPHORIC_START.match(s):
            continue
        out.append(s)
    return out


def _is_cue(word):
    n = norm(word)
    return bool(METHOD_RE.search(n) or PERIOD_RE.search(n))


def _property_stems(interp):
    q = getattr(interp, "quantity", None)
    # Palavras de método/período ("oficial", "anual") não identificam a propriedade.
    ent_tokens = {t for e in interp.entidades for t in norm(e).split()} | \
        {norm(t) for t in interp.termos if _is_cue(t)}
    if q is not None:
        props = {q.chave_unidade} if q.chave_unidade != "%" else set()
        props |= {stem(w) for w in q.qualificadores if norm(w) not in ent_tokens}
        props |= {stem(t) for t in interp.termos if norm(t) not in ent_tokens} if q.chave_unidade == "%" else set()
        props = {p for p in props if p}
        return props, len(props)  # unidade e todas as categorias precisam aparecer na frase
    terms = {stem(t) for t in interp.termos if norm(t) not in ent_tokens and norm(t) not in STOP_NORM}
    return terms, min(2, len(terms))


def _about_entity(sentence, cand, entity, props):
    if not entity:
        return True
    sn = norm(sentence)
    named = [e for e in extract_entities(sentence)
             if not ({stem(w) for w in norm(e).split()} & props)]  # "Copa do Mundo" é categoria, não sujeito
    if named and not _entity_is(named[0], entity):
        return False  # a frase fala primeiro de outra entidade (ex.: "A França ... derrotou o Brasil")
    if entity_match(entity, sn, set(sn.split()), _acronyms(sentence)):
        return True
    # Artigo de enciclopédia cujo título é a própria entidade.
    return cand.origin == "wikipedia" and norm(re.sub(r"\s*\(.*?\)\s*$", "", cand.title)) == norm(entity)


def _entity_is(name, entity):
    n = norm(name)
    return entity_match(entity, n, set(n.split()), set())


def select(interp, groups, answer_texts=()):
    """Devolve até 2 trechos de contexto úteis, cada um com fonte e link; [] se nada for útil."""
    entity = interp.entidades[0] if interp.entidades else None
    props, need = _property_stems(interp)
    if not props or need == 0:
        return []
    q = getattr(interp, "quantity", None)
    answers = {norm(a) for a in answer_texts}
    picked, used_urls = [], set()
    for g in groups:
        for cand, _a in g.members:
            if cand.content_kind == "completo" and cand.page_text:
                text = cand.page_text
            elif cand.content_kind == "resumo" and cand.snippet:
                text = cand.snippet  # resumo de enciclopédia (conteúdo da página, não do buscador)
            else:
                continue  # título ou trecho de buscador: não serve de contexto
            for s in complete_sentences(text):
                sn = norm(s)
                if sn in answers:
                    continue
                if q is not None and any(x.chave_unidade == q.chave_unidade for x in extract_quantities(s)):
                    continue  # traz um valor: é resposta (ou valor alternativo), não contexto
                sstems = {stem(w) for w in sn.split() if len(w) >= 3}
                if len(sstems & props) < need or not _about_entity(s, cand, entity, props):
                    continue
                method = bool(METHOD_RE.search(sn))
                # Ano solto não é "período": só conta o ano da própria afirmação ou expressões de período.
                period = bool(PERIOD_RE.search(sn)) or any(str(y) in s for y in interp.anos)
                if not (method or period):
                    continue  # não explica o dado
                score = 2 * len(sstems & props) + 2 * method + period
                picked.append((score, s, cand, "definição ou método" if method else "período"))
    picked.sort(key=lambda p: -p[0])
    out = []
    for _score, s, cand, why in picked:
        if cand.url in used_urls or any(norm(s) == norm(o["texto"]) for o in out):
            continue
        used_urls.add(cand.url)
        out.append({"texto": s, "veiculo": cand.source_name, "url": cand.url, "titulo": cand.title,
                    "motivo": why, "conteudo": cand.content_kind})
        if len(out) >= MAX_ITEMS:
            break
    return out
