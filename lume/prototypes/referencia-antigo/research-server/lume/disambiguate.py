"""Desambiguação antes da pesquisa: "O que você quer saber?".

Dois caminhos:
- modelo de linguagem (opcional, llm.py): cobre qualquer tipo de ambiguidade; saída validada.
- regras (sempre disponível): cobre só padrões estruturais que podem ser fundamentados:
  * termo com vários significados ou nome compartilhado — sentidos vindos da Wikipédia;
  * característica x aparência em entradas telegráficas ("substantivo + adjetivo perceptivo");
  * pergunta geral x episódio específico (ação no passado sem sujeito nomeado, sem data).
Não há lista de assuntos: os sentidos vêm das fontes e os padrões são gramaticais."""
import re
from dataclasses import asdict, dataclass, field

from . import llm, sources
from .evidence import ARTICLE_TYPE_WORDS
from .interpret import WORD_RE, clean_input, interpret
from .textutil import STOP_NORM, jaccard, norm, stem

MAX_ROUNDS = 2
MAX_OPTIONS = 3
QUESTION = "O que você quer saber?"
NOT_THIS = "Não é isso — adicionar detalhes"

# Léxico gramatical de adjetivos perceptivos (cor, brilho, forma). Não é uma lista de assuntos.
_PERCEPTUAL_BASE = ["azul", "vermelho", "verde", "amarelo", "laranja", "roxo", "rosa", "branco", "preto",
                    "cinza", "marrom", "dourado", "prateado", "violeta", "brilhante", "escuro", "claro",
                    "transparente", "plano", "redondo", "quadrado", "esférico", "oval"]
COPULAS = {"e", "sao", "fica", "ficam", "ficou", "esta", "estao", "parece", "parecem", "era", "foi"}
PAST_RE = re.compile(r"^[a-zà-ú]{3,}(ou|eu|iu|aram|eram|iram)$")


def _forms(base):
    b = norm(base)
    out = {b}
    if b.endswith("o"):
        out |= {b[:-1] + "a", b + "s", b[:-1] + "as"}
    elif b.endswith("l"):
        out |= {b[:-1] + "is"}
    elif b.endswith("e") or b.endswith("a"):
        out |= {b + "s"}
    else:
        out |= {b + "es", b + "s"}
    return out


PERCEPTUAL = {f: b for b in _PERCEPTUAL_BASE for f in _forms(b)}  # forma normalizada -> forma base


@dataclass
class Option:
    texto: str
    diferenca: str


@dataclass
class Result:
    precisa_escolher: bool
    texto_pesquisa: str
    entrada_original: str
    detalhes: str = ""
    rodada: int = 0
    pergunta: str = QUESTION
    opcoes: list = field(default_factory=list)
    opcao_detalhes: str = NOT_THIS
    metodo: str = "regras"
    motivo: str = ""
    aviso: str = ""

    def to_dict(self):
        return asdict(self)


def combine(texto, detalhes):
    t = (texto or "").strip()
    d = (detalhes or "").strip()
    if not d:
        return t
    return f"{t.rstrip(' ?.!')}. {d}"


def plural(word):
    w = word
    lw = norm(w)
    if lw.endswith("ao"):
        return w[:-2] + "ões"
    if lw.endswith(("al", "el", "ol", "ul")):
        return w[:-1] + "is"
    if lw.endswith("m"):
        return w[:-1] + "ns"
    if lw.endswith(("r", "z")):
        return w + "es"
    if lw.endswith("s"):
        return w
    return w + "s"


def agree(adj, feminine):
    """Concorda o adjetivo perceptivo com o gênero (plana/plano, vermelha/vermelho)."""
    base = PERCEPTUAL.get(norm(adj))
    if not base or not norm(base).endswith("o"):
        return adj
    return base[:-1] + "a" if feminine else base


_SKIP_BEFORE_NOUN = {"maior", "menor", "principal", "unico", "unica", "grande", "pequeno", "pequena", "mais",
                     "menos", "antigo", "antiga", "famoso", "famosa", "importante"}


def _definition(extract):
    """'O Sol é a estrela central...' -> {'categoria': 'estrela', 'feminino': True, 'artigo': 'O'}."""
    from .claim import ORDINALS
    text = re.sub(r"\s*\([^)]*\)", "", extract or "")
    m = re.match(r"^\s*(O|A|Os|As)?\b[^.;]{0,80}?\s(?:é|são|foi)\s+(um|uma|o|a|uns|umas)\s+((?:[^\W\d_]+\s+){0,2}[^\W\d_]+)",
                 text)
    if not m:
        return None
    noun = next((w for w in m.group(3).split() if norm(w) not in ORDINALS and norm(w) not in _SKIP_BEFORE_NOUN), None)
    if not noun or len(noun) < 3:
        return None
    return {"categoria": noun.lower(), "feminino": m.group(2) in ("uma", "a", "umas"), "artigo": m.group(1)}


# ---------------- regras -------------------------------------------------------------------

def _telegraphic_appearance(clean, wiki):
    words = WORD_RE.findall(clean)
    if not 2 <= len(words) <= 5:
        return []
    art = None
    if norm(words[0]) in ("o", "a", "os", "as"):
        art, words = words[0], words[1:]
    if len(words) < 2 or any(norm(w) in COPULAS for w in words):
        return []  # com verbo de ligação a afirmação já é explícita: segue direto
    adj = words[-1]
    if norm(adj) not in PERCEPTUAL:
        return []
    subject = words[:-1]
    if any(norm(w) in STOP_NORM for w in subject) or len(subject) > 3:
        return []
    subj = " ".join(subject)
    page = next((c for c in wiki if norm(c.title) == norm(subj) and not c.disambiguation), None)
    if page:
        subj = page.title  # grafia da fonte (ex.: nome próprio com maiúscula)
    d = _definition(page.snippet) if page else None
    # Gênero do sujeito: artigo digitado > artigo da definição na fonte > forma do adjetivo digitado.
    if art:
        fem_subject = norm(art) in ("a", "as")
    elif d and d["artigo"]:
        fem_subject = norm(d["artigo"]) in ("a", "as")
    else:
        base = PERCEPTUAL.get(norm(adj), "")
        fem_subject = norm(base).endswith("o") and norm(adj).endswith("a")
    art = ("A" if fem_subject else "O") if not art else art.capitalize()
    adj_s = agree(adj, fem_subject)
    opts = [Option(f"{art} {subj} é {adj_s}?", "caracteristica_aparencia"),
            Option(f"{art} {subj} pode parecer {adj_s} em alguma situação?", "caracteristica_aparencia")]
    if d:
        opts.append(Option(f"Existem {plural(d['categoria'])} {plural(agree(adj, d['feminino']))}?", "categoria"))
    return opts


def _senses(key, other_terms, wiki):
    """Sentidos/entidades distintos na Wikipédia para o termo-chave."""
    nk = norm(key)
    has_disamb = any(c.disambiguation and norm(re.sub(r"\s*\(.*\)$", "", c.title)) == nk for c in wiki)
    senses = []
    for c in wiki:
        if c.disambiguation:
            continue
        nt = norm(c.title)
        first = nt.split()[:1]
        if first and first[0] in ARTICLE_TYPE_WORDS:
            continue
        base = norm(re.sub(r"\s*\(.*\)$", "", c.title))
        if base == nk or (f" {nk} " in f" {nt} " and len(nt) > len(nk)):
            senses.append(c)
    if len(senses) < 2 and not (has_disamb and senses):
        return []
    others = {stem(t) for t in other_terms}
    if others:
        matching = [c for c in senses if others & {stem(w) for w in norm(c.title + " " + c.snippet).split()}]
        if len(matching) == 1:
            return []  # o restante da entrada já escolhe um sentido
    return senses[:MAX_OPTIONS] if len(senses) >= 2 else []


def _sense_options(clean, key, senses):
    opts = []
    only_key = norm(clean) == norm(key)
    for c in senses:
        if only_key:
            text = f"O que é {c.title}?"
        else:
            text = re.sub(re.escape(key), c.title, clean.rstrip(" ?"), count=1, flags=re.I) + "?"
        opts.append(Option(text[0].upper() + text[1:], "significado_ou_entidade"))
    return opts


def _episode_vs_general(clean, interp):
    words = WORD_RE.findall(clean)
    if interp.entidades or interp.anos or interp.datas or interp.tempo_relativo or not 2 <= len(words) <= 10:
        return []
    if not any(PAST_RE.match(norm(w)) and norm(w) not in STOP_NORM for w in words):
        return []
    phrase = clean.rstrip(" ?.!")
    return [Option(f"Houve um caso recente: “{phrase}”?", "geral_especifico"),
            Option(f"Isso acontece em geral: “{phrase}”?", "geral_especifico")]


def rule_options(clean, interp, wiki):
    opts = _telegraphic_appearance(clean, wiki)
    if opts:
        return opts, "característica x aparência"
    short = len([t for t in WORD_RE.findall(clean) if norm(t) not in STOP_NORM]) <= 4
    key = interp.entidades[0] if interp.entidades else (interp.termos[0] if interp.incompleto and interp.termos else None)
    if key and (short or interp.incompleto):
        others = [t for t in interp.termos if norm(t) != norm(key)]
        senses = _senses(key, others, wiki)
        if senses:
            return _sense_options(clean, key, senses), "termo com vários significados ou nome compartilhado"
    opts = _episode_vs_general(clean, interp)
    if opts:
        return opts, "pergunta geral x caso específico"
    return [], ""


# ---------------- validação (vale também para o modelo) -------------------------------------

def _proper_nouns(text):
    """Palavras com maiúscula que não iniciam frase (candidatas a nomes próprios)."""
    out = set()
    for m in re.finditer(r"[^\W\d_][\w\-]*", text):
        w = m.group(0)
        before = text[:m.start()].rstrip(" “\"'(")
        if w[0].isupper() and before and before[-1] not in ".!?:":
            out.add(norm(w))
    return out


def validate_options(opts, allowed_text, rejected, titles):
    """Descarta opções que inventam nomes/números, repetem rejeitadas ou copiam títulos de resultados."""
    allowed = set(norm(allowed_text).split())

    def content(t):
        return frozenset(w for w in norm(t).split() if w not in STOP_NORM)

    rej = [content(r) for r in rejected]
    tit = [set(norm(t).split()) for t in titles]
    out, seen = [], []
    for o in opts:
        t = " ".join((o.texto or "").split())[:140]
        if not t or re.search(r"\b(verdadeir|fals|fake|mentira)", t, re.I):
            continue
        if not t.endswith("?"):
            t += "?"
        toks = set(norm(t).split())
        if any(n not in allowed for n in _proper_nouns(t)):
            continue  # nome não presente na entrada nem no contexto
        if any(re.fullmatch(r"\d+", n) and n not in allowed for n in toks):
            continue  # número/ano inventado
        key = content(t)
        if key in rej or key in seen:
            continue  # mesma pergunta (já rejeitada ou repetida)
        if any(jaccard(toks, x) >= 0.7 for x in tit):
            continue
        seen.append(key)
        out.append(Option(t, o.diferenca))
    return out[:MAX_OPTIONS]


# ---------------- entrada principal ---------------------------------------------------------

def analyze(texto, fetch, detalhes="", rejeitadas=(), rodada=0, now=None, wiki_lookup=None, model_post=None):
    original = (texto or "").strip()
    combined = combine(original, detalhes)
    clean = clean_input(combined)
    res = Result(precisa_escolher=False, texto_pesquisa=combined, entrada_original=original,
                 detalhes=(detalhes or "").strip(), rodada=rodada)
    if not re.search(r"\w{2,}", clean):
        res.motivo = "entrada vazia"
        return res
    if rodada >= MAX_ROUNDS:
        res.motivo = "limite de rodadas atingido; pesquisando com os detalhes informados"
        return res
    interp = interpret(combined, now)
    key = interp.entidades[0] if interp.entidades else (interp.termos[0] if interp.termos else None)
    wiki = []
    if key:
        try:
            wiki = (wiki_lookup or (lambda q: sources.wikipedia(q, fetch, limit=8)))(key)
        except sources.SourceError as e:
            res.aviso = f"Contexto da Wikipédia indisponível ({e.kind}); desambiguação limitada."
    context_text = " ".join(f"{c.title} {c.snippet}" for c in wiki)
    allowed = f"{combined} {context_text}"
    titles = []  # títulos de notícias não são consultados aqui; só a Wikipédia serve de contexto

    if llm.configured():
        try:
            m = llm.disambiguation(original, detalhes, list(rejeitadas),
                                   [{"titulo": c.title, "resumo": c.snippet[:300]} for c in wiki[:6]], post=model_post)
            res.metodo = "modelo"
            if m["ambigua"]:
                opts = validate_options([Option(o["pergunta"], o["diferenca"]) for o in m["opcoes"]],
                                        allowed, rejeitadas, titles)
                if len(opts) >= 2:
                    res.precisa_escolher, res.opcoes, res.motivo = True, [asdict(o) for o in opts], m["motivo"][:200]
                    return res
            res.motivo = m["motivo"][:200] or "intenção clara"
            return res
        except llm.LlmError as e:
            res.aviso = f"Modelo indisponível ({e.kind}); usando detecção por regras."
            res.metodo = "regras"

    opts, why = rule_options(clean, interp, wiki)
    opts = validate_options(opts, allowed, rejeitadas, titles)
    if len(opts) >= 2:
        res.precisa_escolher, res.opcoes, res.motivo = True, [asdict(o) for o in opts], why
    else:
        res.motivo = "intenção clara para as regras" if not why else "opções insuficientes após filtrar; pesquisando direto"
    return res
