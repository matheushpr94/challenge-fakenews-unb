"""Compara o detalhe verificável com o que as fontes dizem.

Por regras, só compara formatos estruturados: quantidade + unidade (+ categoria e período) e
ano de um fato. Para outros tipos devolve 'sem_comparacao', a menos que um modelo opcional
(llm.py) esteja configurado. Nunca usa contagem de resultados como evidência."""
import re
from dataclasses import dataclass

from .claim import (GENERIC_UNITS, YEAR_RE, compatible, extract_quantities, fmt_value, qualifier_keys)
from .interpret import extract_entities
from .relevance import _acronyms, entity_match
from .textutil import norm, stem

STRONG_CONTENT = ("completo", "resumo")
# Primeiras palavras típicas de páginas de lista/tema na Wikipédia (não são nomes de entidade).
ARTICLE_TYPE_WORDS = {"lista", "listas", "titulos", "confrontos", "historia", "temporada", "temporadas",
                      "resultados", "estatisticas", "elenco", "jogadores", "presidentes", "treinadores",
                      "eleicoes", "eleicao", "controversias", "cronologia"}
SITUACAO_FRASE = {
    "apoiam": "As fontes consultadas apoiam essa informação.",
    "contradizem": "As fontes consultadas contradizem essa informação.",
    "divergentes": "As fontes apresentam informações divergentes.",
    "insuficiente": "Não encontramos evidência suficiente para esclarecer esse detalhe.",
    "resposta": "As fontes consultadas indicam o valor abaixo.",
    "nao_verificavel": "Esta entrada expressa opinião ou previsão; não há um fato único para comparar.",
    "sem_comparacao": ("Encontramos fontes sobre o assunto, mas o Lume não compara automaticamente o sentido "
                       "deste tipo de afirmação sem um modelo de linguagem. Leia o que cada fonte diz abaixo."),
}


@dataclass
class EvidenceItem:
    group: int
    veiculo: str
    url: str
    conteudo: str
    publicado: object
    valor: float
    texto_valor: str
    trecho: str
    qualificadores: list
    anos: list
    relacao: str  # apoia | contradiz | valor | outra_categoria | outro_periodo | sem_categoria
    explicito: bool = True  # a frase cita a entidade (e não só o título da página)
    entidade_fonte: str = None  # nome mais longo que contém a entidade (ex.: outra organização homônima)

    def to_dict(self):
        return {"veiculo": self.veiculo, "url": self.url, "conteudo": self.conteudo,
                "publicado_em": self.publicado.isoformat(timespec="seconds") if self.publicado else None,
                "valor": self.valor, "texto_valor": self.texto_valor, "trecho": self.trecho,
                "qualificadores": self.qualificadores, "periodo_citado": self.anos, "relacao": self.relacao,
                "entidade_fonte": self.entidade_fonte}


def _sentences(text):
    return [s.strip() for s in re.split(r"(?<=[.!?;])\s+|\n+", text or "") if len(s.strip()) >= 15]


def _source_texts(cand):
    if cand.content_kind == "completo" and cand.page_text:
        yield "completo", cand.page_text
    elif cand.snippet:
        yield ("resumo" if cand.origin == "wikipedia" else "trecho"), cand.snippet
    yield "titulo", cand.title


def _page_is_entity(cand, entity):
    """Artigo de enciclopédia cujo título é a própria entidade: o texto trata dela."""
    if not entity or cand.origin != "wikipedia":
        return False
    return norm(re.sub(r"\s*\(.*?\)\s*$", "", cand.title)) == norm(entity)


def _mentions_entity(entity, text):
    cn = norm(text)
    return entity_match(entity, cn, set(cn.split()), _acronyms(text))


def source_entity(cand, entity):
    """Se o título nomeia uma entidade mais longa que contém a da afirmação, devolve esse nome."""
    if not entity:
        return None
    names = [cand.title] if cand.origin == "wikipedia" else extract_entities(cand.title)
    for name in names:
        name = re.sub(r"\s*\(.*?\)\s*$", "", name).strip()
        if norm(name).split()[:1] and norm(name).split()[0] in ARTICLE_TYPE_WORDS:
            continue  # página de lista/tema sobre a entidade, não outra entidade
        if norm(name) != norm(entity) and _mentions_entity(entity, name) and len(norm(name)) > len(norm(entity)):
            return name
    return None


def _other_subject(sent, interp, known_stems):
    """A frase fala de outro sujeito nomeado (e não da entidade da afirmação)?"""
    for e in extract_entities(sent):
        es = {stem(w) for w in norm(e).split() if len(w) >= 3}
        if es and not (es & known_stems):
            return True
    return False


def quantity_evidence(interp, groups):
    """Extrai de cada grupo (republicações contam uma vez) valores da mesma unidade."""
    q = interp.quantity
    entity = interp.entidades[0] if interp.entidades else None
    ent_tokens = frozenset(norm(entity).split()) if entity else frozenset()
    qual_stems = {stem(w) for w in q.qualificadores}
    prop_stems = {stem(t) for t in interp.termos if stem(t) != q.chave_unidade} - qual_stems
    generic = q.chave_unidade == "%" or norm(q.unidade) in GENERIC_UNITS
    claim_quals = qualifier_keys(q)
    known = {stem(w) for e in interp.entidades for w in norm(e).split()} | qual_stems | prop_stems | {q.chave_unidade}
    items = []
    for gi, g in enumerate(groups):
        for cand, _a in g.members:
            title_has_entity = bool(entity) and _mentions_entity(entity, cand.title)
            seen = set()
            for kind, text in _source_texts(cand):
                for sent in _sentences(text) if kind != "titulo" else [text]:
                    found = [x for x in extract_quantities(sent, ent_tokens, weak_units={q.chave_unidade})
                             if x.chave_unidade == q.chave_unidade]
                    if not found:
                        continue
                    sstems = {stem(w) for w in norm(sent).split() if len(w) >= 3}
                    in_sent = bool(entity) and _mentions_entity(entity, sent)
                    if entity and not in_sent and not (title_has_entity and not _other_subject(sent, interp, known)):
                        continue
                    if (generic or not entity) and prop_stems and not (sstems & prop_stems) \
                            and not (qual_stems and qual_stems & sstems):
                        continue  # "%" ou "pessoas" sem a propriedade na frase: pode ser outro número
                    years = sorted({int(y) for y in YEAR_RE.findall(sent)})
                    for x in found:
                        key = (x.valor, sent[:60])
                        if key in seen:
                            continue
                        seen.add(key)
                        xq = qualifier_keys(x)
                        if claim_quals and xq and not (claim_quals & xq):
                            rel = "outra_categoria"
                        elif claim_quals and not xq:
                            rel = "sem_categoria"  # não diz a qual categoria o número se refere
                        elif not claim_quals and xq:
                            rel = "categoria_nao_informada"  # a fonte especifica a categoria; a entrada não
                        elif interp.anos and years and not (set(interp.anos) & set(years)):
                            rel = "outro_periodo"
                        elif q.valor is None:
                            rel = "valor" if x.comparador == "eq" else "limite"
                        elif x.comparador in ("min", "min_excl", "max", "max_excl") and compatible(q, x):
                            rel = "limite"  # "mais de 1 milhão" é compatível, mas não confirma o valor
                        else:
                            rel = "apoia" if compatible(q, x) else "contradiz"
                        items.append(EvidenceItem(
                            group=gi, veiculo=cand.source_name, url=cand.url, conteudo=kind,
                            publicado=cand.published, valor=x.valor, texto_valor=x.texto,
                            trecho=sent[:320], qualificadores=x.qualificadores, anos=years, relacao=rel,
                            explicito=in_sent or not entity or _page_is_entity(cand, entity)))
    return items


def year_evidence(interp, groups):
    """Fato histórico com ano: procura o ano logo após o termo do fato (ex.: 'fundado em 1895')."""
    entity = interp.entidades[0] if interp.entidades else None
    term_stems = {stem(t) for t in interp.termos}
    items = []
    for gi, g in enumerate(groups):
        for cand, _a in g.members:
            for kind, text in _source_texts(cand):
                if kind == "titulo":
                    continue
                title_has_entity = bool(entity) and _mentions_entity(entity, cand.title)
                for sent in _sentences(text):
                    in_sent = bool(entity) and _mentions_entity(entity, sent)
                    if entity and not (in_sent or title_has_entity):
                        continue
                    words = re.findall(r"\w+", sent)
                    for i, w in enumerate(words):
                        if len(w) < 3 or stem(w) not in term_stems:
                            continue
                        window = " ".join(words[i + 1:i + 16])
                        m = YEAR_RE.search(window)
                        if not m:
                            continue
                        y = int(m.group(1))
                        rel = "apoia" if y in interp.anos else "contradiz"
                        items.append(EvidenceItem(
                            group=gi, veiculo=cand.source_name, url=cand.url, conteudo=kind,
                            publicado=cand.published, valor=float(y), texto_valor=str(y), trecho=sent[:320],
                            qualificadores=[], anos=[y], relacao=rel,
                            explicito=in_sent or not entity or _page_is_entity(cand, entity)))
                        break
    return items


def _tag_variants(items, groups, entity):
    for i in items:
        cand = next((c for c, _ in groups[i.group].members if c.url == i.url), groups[i.group].lead[0])
        i.entidade_fonte = source_entity(cand, entity)
    return items


def _period_of(item):
    if item.anos:
        return max(item.anos)
    return item.publicado.year if item.publicado else None


def _fact_period(item):
    """Período do fato = ano citado no trecho. A data da página não conta como período do fato."""
    return max(item.anos) if item.anos else None


def _describe(item):
    what = f"{item.texto_valor}"
    if item.qualificadores:
        what += " (" + " ".join(item.qualificadores) + ")"
    when = []
    if item.anos:
        when.append("período citado: " + ", ".join(map(str, item.anos)))
    if item.publicado:
        when.append(f"publicado em {item.publicado:%d/%m/%Y}")
    label = {"completo": "página consultada", "resumo": "resumo da página", "trecho": "trecho do buscador",
             "titulo": "apenas título"}[item.conteudo]
    return f"{item.veiculo} ({label}{'; ' + '; '.join(when) if when else ''}): {what}"


def _src(item):
    return {"veiculo": item.veiculo, "url": item.url}


def synthesize(interp, groups, direct_count):
    """Decide qual das situações descreve as evidências e explica a comparação."""
    af = interp.afirmacao
    tipo = af.get("tipo")
    base = {"detalhe": af.get("detalhe_verificavel"), "metodo": "regras", "explicacao": [], "evidencias": []}
    if tipo in ("opiniao", "previsao"):
        return {**base, "situacao": "nao_verificavel", "frase": SITUACAO_FRASE["nao_verificavel"], "metodo": "regras",
                "explicacao": [{"texto": ("Previsões e promessas só podem ser comparadas com o que for "
                                          "anunciado oficialmente; opiniões não têm valor verdadeiro único."),
                                "fontes": []}]}
    q = getattr(interp, "quantity", None)
    entity = interp.entidades[0] if interp.entidades else None
    if q:
        items = _tag_variants(quantity_evidence(interp, groups), groups, entity)
    elif tipo == "fato_historico" and interp.anos:
        items = _tag_variants(year_evidence(interp, groups), groups, entity)
    else:
        if direct_count:
            return {**base, "situacao": "sem_comparacao", "frase": SITUACAO_FRASE["sem_comparacao"], "metodo": "nenhum"}
        return {**base, "situacao": "insuficiente", "frase": SITUACAO_FRASE["insuficiente"], "metodo": "nenhum",
                "explicacao": [{"texto": ("Nenhuma publicação encontrada trata diretamente deste detalhe. Isso não "
                                          "indica que seja falso."), "fontes": []}]}
    return _decide(interp, q, items, base, groups)


def _decide(interp, q, items, base, groups=()):
    base["evidencias"] = [i.to_dict() for i in items[:12]]
    base["_items"] = [i for i in items if i.relacao in ("apoia", "contradiz", "valor") and i.conteudo in STRONG_CONTENT]
    expl = base["explicacao"]
    claimed = None
    if q is not None and q.valor is not None:
        claimed = f"{q.texto}" + (f" ({' '.join(q.qualificadores)})" if q.qualificadores else "")
    elif q is None and interp.anos:
        claimed = ", ".join(map(str, interp.anos))
    if claimed:
        expl.append({"texto": f"Informado na entrada: {claimed}.", "fontes": []})
    # Valores deixados de fora da comparação: explicados DEPOIS da comparação, no máximo 2.
    excluded_notes = []
    reasons = {"outra_categoria": "refere-se a outra categoria",
               "outro_periodo": "refere-se a outro período",
               "categoria_nao_informada": "a fonte especifica uma categoria que a entrada não informa"}
    for i in items:
        if i.relacao in reasons and i.conteudo in STRONG_CONTENT and len(excluded_notes) < 2:
            excluded_notes.append({"texto": f"Não comparado — {_describe(i)}: {reasons[i.relacao]}.",
                                   "fontes": [_src(i)]})
    rel = [i for i in items if i.relacao in ("apoia", "contradiz", "valor")]
    strong = [i for i in rel if i.conteudo in STRONG_CONTENT]

    negated = bool(interp.afirmacao.get("detalhe_negado"))

    def out(sit, extra=None):
        for e in (extra or []) + excluded_notes:
            expl.append(e)
        if negated and sit in ("apoiam", "contradizem"):
            # A entrada NEGA o valor: fontes com o mesmo valor contradizem a entrada, e vice-versa.
            sit = "contradizem" if sit == "apoiam" else "apoiam"
            expl.append({"texto": ("A entrada nega esse valor (“" + (interp.afirmacao.get("negacao_texto") or "não") +
                                   "…”); a indicação considera essa negação."), "fontes": []})
        return {**base, "situacao": sit, "frase": SITUACAO_FRASE[sit]}

    if not rel:
        return out("insuficiente", [{"texto": ("As fontes encontradas não informam esse valor de forma comparável. "
                                               "Ausência de informação não é contradição."), "fontes": []}])
    if not strong:
        return out("insuficiente", [{"texto": "Pista, sem leitura da página: " + _describe(i),
                                     "fontes": [_src(i)]} for i in rel[:3]] +
                   [{"texto": ("Só havia título ou trecho do buscador; isso não basta para concluir. "
                               "Abra os links para conferir."), "fontes": []}])

    # Um valor por grupo independente (republicações já estão agrupadas).
    by_group = {}
    for i in strong:
        by_group.setdefault(i.group, []).append(i)
    # Páginas agrupadas por título parecido que informam valores diferentes não são cópias: separa por URL.
    for g, its in list(by_group.items()):
        per_url = {}
        for i in its:
            per_url.setdefault(i.url, set()).add(i.valor)
        url_values = [min(v) for v in per_url.values()]
        if len(per_url) > 1 and any(not _same(url_values[0], v) for v in url_values[1:]):
            del by_group[g]
            for i in its:
                by_group.setdefault((g, i.url), []).append(i)
    reps = []
    for _g, its in by_group.items():
        its = [i for i in its if i.explicito] or its  # frases que nomeiam a entidade têm prioridade
        vals = [i.valor for i in its]
        best = max(set(vals), key=vals.count)
        reps.append(next(i for i in its if i.valor == best))
    if not any(r.explicito for r in reps):
        return out("insuficiente", [{"texto": "Valor encontrado sem citar a entidade na mesma frase: " + _describe(r),
                                     "fontes": [_src(r)]} for r in reps[:2]] +
                   [{"texto": ("Sem menção explícita à entidade, não dá para afirmar que o número se refere a ela."),
                     "fontes": []}])
    # Só frases que nomeiam a entidade (ou artigos sobre ela) entram na comparação.
    reps = [r for r in reps if r.explicito]
    distinct = []
    for v in sorted(r.valor for r in reps):
        if not distinct or not _same(distinct[-1], v):
            distinct.append(v)
    named = [r for r in reps if r.entidade_fonte]
    variants = {r.entidade_fonte for r in named}
    if len(variants) > 1 and len({r.valor for r in named}) > 1:
        # O mesmo nome aparece ligado a entidades diferentes: é ambiguidade real, não contradição.
        entity = interp.entidades[0] if interp.entidades else ""
        names = [v for v in variants if v]
        for g in groups:
            c = g.lead[0]
            if c.origin == "wikipedia" and not c.disambiguation:
                if norm(c.title).split()[:1] and norm(c.title).split()[0] in ARTICLE_TYPE_WORDS:
                    continue  # páginas de lista/tema, não nomes de entidade
                v = source_entity(c, entity)
                if v and v not in names:
                    names.append(v)
        subject = re.sub(r"^(o|a|os|as)\s+", "", interp.assunto, flags=re.I)
        base["opcoes"] = [{"texto": re.sub(re.escape(entity), n, subject, count=1, flags=re.I) if entity else n,
                           "entidade": n} for n in names[:4]]
        return out("insuficiente", [{"texto": (f"O nome “{entity}” aparece ligado a entidades diferentes nas fontes: " +
                                               "; ".join(_describe(r) + (f" [{r.entidade_fonte}]" if r.entidade_fonte else "")
                                                         for r in reps[:4]) +
                                               ". Escolha a qual delas a afirmação se refere."),
                                     "fontes": [_src(r) for r in reps[:4]]}])
    lines = [{"texto": _describe(r), "fontes": [_src(r)]} for r in reps[:5]]
    if len(reps) == 1:
        lines.append({"texto": "Apenas uma fonte independente com conteúdo lido informou esse valor.", "fontes": []})
    weak_other = [i for i in rel if i.conteudo not in STRONG_CONTENT and not any(_same(i.valor, v) for v in distinct)]
    if weak_other:
        lines.append({"texto": "Títulos ou trechos não lidos mencionam outro valor (não confirmado): " +
                               "; ".join(_describe(i) for i in weak_other[:2]),
                      "fontes": [_src(i) for i in weak_other[:2]]})

    if q is not None and q.valor is None:  # pergunta sem valor alegado
        if len(distinct) == 1:
            return out("resposta", lines)
        return out("divergentes", lines + [_period_note(reps)])

    sup = [r for r in reps if r.relacao == "apoia"]
    con = [r for r in reps if r.relacao == "contradiz"]
    tipo = interp.afirmacao.get("tipo")
    if con and not sup:
        # Nenhuma fonte lida confirma o valor informado. Se elas divergem entre si, isso é explicado.
        extra = [] if len(distinct) == 1 else [{"texto": (
            "As fontes não concordam entre si (" + ", ".join(sorted({r.texto_valor for r in reps})) +
            "), mas nenhuma indica o valor informado. " + _period_note(reps)["texto"]), "fontes": []}]
        return out("contradizem", lines + extra)
    if len(distinct) > 1 and tipo in ("contagem", "acontecimento_recente"):
        # Quantidade que muda com o tempo: só vale "o dado mais recente" se o PERÍODO DO FATO estiver
        # explícito nos trechos. Data de publicação mais nova não torna um valor mais correto.
        dated = [r for r in reps if _fact_period(r) is not None]
        if len(dated) == len(reps):
            latest = max(_fact_period(r) for r in reps)
            newest = [r for r in reps if _fact_period(r) == latest]
            if len({r.relacao for r in newest}) == 1 and all(_same(r.valor, newest[0].valor) for r in newest):
                older = [r for r in reps if r not in newest]
                note = {"texto": (f"Os valores diferentes se referem a períodos anteriores "
                                  f"({', '.join(sorted({str(_fact_period(r)) for r in older}))}); o trecho mais recente "
                                  f"se refere a {latest}: {newest[0].texto_valor} ({newest[0].veiculo})."),
                        "fontes": [_src(r) for r in older[:3]]}
                return out("apoiam" if newest[0].relacao == "apoia" else "contradizem", lines + [note])
        return out("divergentes", lines + [_period_note(reps)])
    if sup and not con:
        return out("apoiam", lines)
    return out("divergentes", lines + [_period_note(reps)])


def _same(a, b):
    """Valores praticamente iguais (arredondamento: 214,2 milhões ≈ 214.211.951)."""
    return abs(a - b) <= 0.005 * max(abs(a), abs(b), 1e-9)


def _period_note(reps):
    periods = sorted({str(_fact_period(r)) for r in reps if _fact_period(r)})
    if len(periods) > 1:
        return {"texto": ("Os valores vêm de períodos diferentes (" + ", ".join(periods) +
                          "); confira a qual data cada fonte se refere antes de compará-los."), "fontes": []}
    return {"texto": ("As fontes indicam valores diferentes para o mesmo período ou sem período informado; "
                      "podem usar definições ou contagens diferentes."), "fontes": []}


def value_summary(q):
    return fmt_value(q.valor) if q and q.valor is not None else None


INDICACAO = {
    "tende_verdadeira": "Tende a ser verdadeira",
    "tende_falsa": "Tende a ser falsa",
    "inconclusiva": "Inconclusiva",
    "resposta": "Resposta encontrada",
    "sem_resposta": "Informação insuficiente",
    "falha_tecnica": "Falha técnica",
}
AVISO_INDICACAO = "Indicação baseada nas fontes consultadas; não é uma garantia de veracidade."


def indicate(sintese, interp):
    """Converte a situação das evidências na indicação provisória. Nunca parte da conclusão."""
    sit = sintese.get("situacao")
    q = getattr(interp, "quantity", None)
    question_without_claim = q is not None and q.valor is None
    n_sources = len({e.get("url") for e in sintese.get("evidencias", [])
                     if e.get("relacao") in ("apoia", "contradiz", "valor")})
    if sit == "erro_tecnico":
        key, why = "falha_tecnica", "Não foi possível consultar as fontes. Isso é uma falha técnica, não falta de evidências."
    elif question_without_claim:
        if sit == "resposta":
            key, why = "resposta", "Valor informado por fontes com conteúdo lido."
        elif sit == "divergentes":
            key, why = "sem_resposta", "As fontes indicam valores diferentes."
        else:
            key, why = "sem_resposta", "As fontes consultadas não informam esse valor de forma comparável."
    elif sit == "apoiam":
        key, why = "tende_verdadeira", "Os trechos consultados sustentam o detalhe informado."
    elif sit == "contradizem":
        key, why = "tende_falsa", "Os trechos consultados apresentam informação incompatível com o detalhe informado."
    elif sit == "divergentes":
        key, why = "inconclusiva", "As fontes apresentam informações divergentes."
    elif sit == "sem_comparacao":
        key, why = "inconclusiva", ("As regras do Lume só comparam quantidades, unidades, períodos e anos. Este tipo de "
                                    "afirmação exige compreensão do texto que as regras não oferecem.")
    elif sit == "nao_verificavel":
        key, why = "inconclusiva", "Opinião ou previsão: não há um fato único para comparar."
    elif sintese.get("opcoes"):
        key, why = "inconclusiva", "O nome corresponde a entidades diferentes; escolha a que você quis dizer."
    else:
        key, why = "inconclusiva", "Faltam evidências comparáveis sobre este detalhe. Ausência de evidência não indica falsidade."
    if key in ("tende_verdadeira", "tende_falsa") and n_sources == 1:
        why += " Baseada em uma única fonte independente."
    return {"rotulo": key, "texto": INDICACAO[key], "motivo": why, "aviso": AVISO_INDICACAO}
