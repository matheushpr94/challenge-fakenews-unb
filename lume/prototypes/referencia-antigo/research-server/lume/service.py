"""Orquestra interpretação, busca, avaliação e montagem da resposta estruturada."""
import copy
import logging
import re
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, wait
from datetime import datetime, timezone

from . import context, disambiguate, evidence, llm, netfetch, sources
from .claim import detail_text, extract_quantities
from .interpret import BYLINE_RE, _sentences, clean_input, extract_entities, interpret
from .relevance import CONTENT_RANK, _period, _title_key, assess, group_candidates
from .textutil import STOP_NORM, jaccard, norm, numbers_in, stem

log = logging.getLogger("lume")

NEWS_TTL = 10 * 60
WEB_TTL = 30 * 60
WIKI_TTL = 6 * 3600
PAGE_TTL = 3600
TOTAL_BUDGET = 25.0
STABLE_TYPES = ("contagem", "quantidade_periodo", "fato_historico")
CATEGORY_RANK = {"direto": 3, "anterior": 2, "contexto": 1, "descartado": 0}
CONTENT_LABEL = {
    "completo": "Conteúdo da página consultado",
    "trecho": "Trecho fornecido pelo buscador",
    "titulo": "Apenas título disponível",
    "resumo": "Resumo da página (Wikipédia)",
}
SOURCE_LABEL = {"google_news": "Google Notícias (RSS)", "bing_news": "Bing Notícias (RSS)",
                "bing_web": "Bing Web (RSS)", "wikipedia": "Wikipédia (API)", "pagina": "Página do veículo"}


def _iso(dt):
    return dt.astimezone(timezone.utc).isoformat(timespec="seconds") if dt else None


def source_type(domain, origin):
    d = domain or ""
    if origin == "wikipedia":
        return "enciclopedia"
    if re.search(r"\.(gov|gob|mil|leg|jus|mp|def)(\.[a-z]{2})?$", d):
        return "instituicao_publica"
    if re.search(r"\.edu(\.[a-z]{2})?$|\.ac\.[a-z]{2}$", d) or d.endswith((
            "scielo.org", "scielo.br", "nih.gov", "fiocruz.br", "nature.com", "science.org",
            "thelancet.com", "nejm.org", "bmj.com", "cochrane.org", "arxiv.org", "usp.br", "unb.br")):
        return "cientifica"
    if re.search(r"(^|\.)u[a-z]{1,8}\.br$", d):
        return "cientifica"
    if d.endswith(("instagram.com", "facebook.com", "x.com", "twitter.com", "tiktok.com", "youtube.com",
                   "threads.net", "kwai.com")):
        return "rede_social"
    if d.endswith(".int"):
        return "organismo_internacional"
    return "noticia" if origin in ("google_news", "bing_news") else "web"


class TTLCache:
    def __init__(self, max_items=500):
        self._data, self._lock, self.max_items = {}, threading.Lock(), max_items

    def get(self, key):
        with self._lock:
            item = self._data.get(key)
            if not item:
                return None
            value, stored, expires = item
            if time.time() > expires:
                del self._data[key]
                return None
            return copy.deepcopy(value), stored

    def set(self, key, value, ttl):
        with self._lock:
            if len(self._data) >= self.max_items:
                self._data.pop(next(iter(self._data)))
            now = time.time()
            self._data[key] = (copy.deepcopy(value), now, now + ttl)


class LumeService:
    def __init__(self, fetch=netfetch.fetch, cache=None, clock=None, fetch_pages=True, workers=8):
        self.fetch = fetch
        self.cache = cache or TTLCache()
        self.clock = clock or (lambda: datetime.now(timezone.utc))
        self.fetch_pages = fetch_pages
        self.pool = ThreadPoolExecutor(max_workers=workers)

    # ---- fontes -----------------------------------------------------------------------
    def _call(self, name, query, rid):
        fn, ttl = {
            "google_news": (sources.google_news, NEWS_TTL),
            "bing_news": (lambda q, f: sources.bing(q, f, "news"), NEWS_TTL),
            "bing_web": (lambda q, f: sources.bing(q, f, "web"), WEB_TTL),
            "wikipedia": (sources.wikipedia, WIKI_TTL),
        }[name]
        key = (name, norm(query))
        status = {"fonte": name, "nome": SOURCE_LABEL[name], "consulta": query, "em_cache": False}
        cached = self.cache.get(key)
        if cached:
            items, stored = cached
            status.update(status="ok" if items else "vazio", quantidade=len(items), em_cache=True,
                          obtido_em=_iso(datetime.fromtimestamp(stored, timezone.utc)), ms=0)
            log.info("[%s] %s %r: %d itens (cache)", rid, name, query, len(items))
            return items, status
        t0 = time.monotonic()
        items, err = [], None
        for attempt in range(2):
            try:
                items, err = fn(query, self.fetch), None
                break
            except sources.SourceError as e:
                err = e
                if e.kind not in ("tempo_esgotado", "rede") or attempt == 1:
                    break
                log.warning("[%s] %s %r: %s; tentando de novo", rid, name, query, e)
        status["ms"] = int((time.monotonic() - t0) * 1000)
        status["obtido_em"] = _iso(self.clock())
        if err:
            status.update(status="erro", quantidade=0, erro=f"{err.kind}: {err}")
            log.warning("[%s] %s %r: FALHA %s", rid, name, query, status["erro"])
        else:
            status.update(status="ok" if items else "vazio", quantidade=len(items))
            self.cache.set(key, items, ttl)
            log.info("[%s] %s %r: %d itens em %d ms", rid, name, query, len(items), status["ms"])
        return items, status

    def _run(self, jobs, rid, deadline):
        futures = {self.pool.submit(self._call, name, q, rid): (name, q) for name, q in jobs}
        done, pending = wait(futures, timeout=max(0.1, deadline - time.monotonic()))
        cands, statuses = [], []
        for f in done:
            items, st = f.result()
            cands.extend(items)
            statuses.append(st)
        for f in pending:
            name, q = futures[f]
            f.cancel()
            statuses.append({"fonte": name, "nome": SOURCE_LABEL[name], "consulta": q, "status": "erro",
                             "quantidade": 0, "erro": "tempo_esgotado: prazo total da pesquisa",
                             "em_cache": False, "obtido_em": _iso(self.clock())})
            log.warning("[%s] %s %r: sem resposta no prazo total", rid, name, q)
        return cands, statuses

    # ---- páginas ----------------------------------------------------------------------
    def _page(self, url, rid):
        cached = self.cache.get(("pagina", url))
        if cached:
            return cached[0]
        try:
            info = sources.fetch_page(url, self.fetch)
        except sources.SourceError as e:
            log.info("[%s] página indisponível %s: %s", rid, url, e)
            return {"erro": f"{e.kind}: {e}"}
        self.cache.set(("pagina", url), info, PAGE_TTL)
        log.info("[%s] página lida %s (%d parágrafos, %d trechos suspeitos ignorados)", rid, url,
                 len(info["paragraphs"]), info["injection_dropped"])
        return info

    def _enrich(self, groups, interp, rid, deadline):
        q = getattr(interp, "quantity", None)
        stable = interp.afirmacao.get("tipo") in STABLE_TYPES
        allowed = ("bing_news", "bing_web", "wikipedia") if stable else ("bing_news", "bing_web")
        ranked = groups
        if q is not None:
            # Para quantidades, ler primeiro as páginas que citam a unidade (ex.: "títulos", "%").
            ranked = sorted(groups, key=lambda g: 0 if any(
                q.chave_unidade in {stem(w) for w in norm(c.title + " " + c.snippet).split()} or
                (q.chave_unidade == "%" and "%" in c.snippet) for c, _ in g.members) else 1)
        targets, cap = [], (8 if stable else 6)
        for g in ranked:
            # Até 2 páginas por grupo: confirma se itens de título parecido são mesmo republicações.
            per_group = 0
            for cand, _a in sorted(g.members, key=lambda m: -CONTENT_RANK[m[0].content_kind]):
                if len(targets) >= cap or per_group >= 2:
                    break
                if cand.origin in allowed:
                    targets.append(cand)
                    per_group += 1
        futures = {self.pool.submit(self._page, c.url, rid): c for c in targets}
        done, _pending = wait(futures, timeout=max(0.1, min(8.0, deadline - time.monotonic())))
        claim_stems = {stem(w) for w in interp.termos} | {stem(w) for e in interp.entidades for w in e.split()}
        for f, cand in futures.items():
            if f not in done:
                cand.alerts.append("Página não respondeu a tempo; exibido apenas o trecho do buscador.")
                continue
            info = f.result()
            if "erro" in info:
                cand.alerts.append("Página indisponível para leitura; exibido apenas o trecho do buscador.")
                continue
            if info["injection_dropped"]:
                cand.alerts.append("A página continha texto com instruções para sistemas automáticos; foi ignorado.")
            body = " ".join(info["paragraphs"])
            if len(body) < 500:
                cand.alerts.append("Não foi possível extrair o texto da matéria (conteúdo curto, fechado ou dinâmico).")
                continue
            cand.content_kind, cand.page_text = "completo", body[:20000]
            if cand.origin != "wikipedia":  # na Wikipédia a data da página é a de criação, não a da informação
                cand.published = cand.published or info["published"]
            cand.page_author = (info["author"] or "")[:120]
            if info["site_name"] and cand.origin == "bing_web":
                cand.source_name = info["site_name"][:80]
            scored = []
            for sent in re.split(r"(?<=[.!?])\s+", body):
                if 50 <= len(sent) <= 450:
                    hits = len({stem(w) for w in re.findall(r"\w+", sent) if len(w) >= 3} & claim_stems)
                    if hits >= 2:
                        scored.append((hits, sent))
            cand.page_excerpts = [s for _h, s in sorted(scored, key=lambda x: -x[0])[:2]]

    # ---- pesquisa --------------------------------------------------------------------
    def _plan(self, interp):
        queries = [q["texto"] for q in interp.consultas]
        tipo = interp.afirmacao.get("tipo")
        stable = tipo in STABLE_TYPES
        if stable:
            # Fatos estáveis e quantidades: priorizar referências e registros, não só notícias recentes.
            jobs = [("bing_web", q) for q in queries[:3]]
            jobs += [("google_news", queries[0]), ("bing_news", queries[0])]
            wiki_q = [interp.consulta_contexto_propriedade, interp.consulta_contexto] + queries[1:2]
        else:
            jobs = [(s, q) for q in queries[:2] for s in ("google_news", "bing_news")]
            jobs += [("bing_web", queries[0])]
            wiki_q = [interp.consulta_contexto_propriedade, interp.consulta_contexto]
        jobs += [("wikipedia", q) for q in dict.fromkeys(w for w in wiki_q if w)]
        extra = [] if stable or len(queries) <= 2 else [(s, queries[2]) for s in ("google_news", "bing_news", "bing_web")]
        return jobs, extra

    def search(self, text, request_id=None):
        t0 = time.monotonic()
        deadline = t0 + TOTAL_BUDGET
        rid = (request_id or uuid.uuid4().hex[:8])[:64]
        now = self.clock()
        interp = interpret(text, now)
        base = {"id_consulta": rid, "consultado_em": _iso(now), "interpretacao": interp.to_dict()}
        if not interp.assunto or not re.search(r"\w{2,}", interp.assunto):
            return {**base, "status": "entrada_invalida",
                    "mensagem": "Digite uma notícia, afirmação ou pergunta para pesquisar."}
        log.info("[%s] entrada: %r | tipo=%s | consultas: %s", rid, interp.assunto[:120],
                 interp.afirmacao.get("tipo"), [q["texto"] for q in interp.consultas])

        jobs, extra = self._plan(interp)
        cands, statuses = self._run(jobs, rid, deadline)
        pairs = [(c, assess(interp, c, now)) for c in cands]
        n_direct = sum(1 for _c, a in pairs if a.category == "direto")
        if n_direct < 3 and extra:
            log.info("[%s] %d resultados diretos; ampliando busca", rid, n_direct)
            more, st2 = self._run(extra, rid, deadline)
            cands += more
            pairs += [(c, assess(interp, c, now)) for c in more]
            statuses += st2

        discarded = [(c, a) for c, a in pairs if a.category == "descartado"]
        for c, a in discarded:
            log.debug("[%s] descartado (%s): %s — %s", rid, a.reason, c.source_name, c.title)
        kept = [(c, a) for c, a in pairs if a.category != "descartado"]
        groups = group_candidates(kept)
        groups.sort(key=lambda g: (-max(CATEGORY_RANK[a.category] for _c, a in g.members),
                                   -max(a.score for _c, a in g.members)))
        if self.fetch_pages and groups:
            self._enrich(groups, interp, rid, deadline)

        ev_groups = groups[:20]
        n_direct_groups = sum(1 for g in groups if max(CATEGORY_RANK[a.category] for _c, a in g.members) == 3)
        sintese = evidence.synthesize(interp, ev_groups, n_direct_groups)
        ev_by_group = {}
        for e in sintese.get("_items", []):
            ev_by_group.setdefault(e.group, []).append(e)
        answering = set(ev_by_group)
        sintese.pop("_items", None)

        buckets = {"responde": [], "direto": [], "anterior": [], "contexto": [], "contexto_geral": []}
        for i, g in enumerate(groups):
            cat = max((a.category for _c, a in g.members), key=CATEGORY_RANK.get)
            if i in answering:
                cat = "responde"
            elif g.lead[0].origin == "wikipedia":
                cat = "contexto_geral"
            buckets[cat].append(g)
        limits = {"responde": 6, "direto": 6, "anterior": 3, "contexto": 3, "contexto_geral": 2}
        idx = {id(g): i for i, g in enumerate(groups)}
        results = {k: [self._group_dict(g, k, interp, ev_by_group.get(idx[id(g)])) for g in v[:limits[k]]]
                   for k, v in buckets.items()}

        search_ok = any(s["status"] in ("ok", "vazio") for s in statuses
                        if s["fonte"] in ("google_news", "bing_news", "bing_web"))
        failed = [s for s in statuses if s["status"] == "erro"]
        suggestions = self._suggestions(interp, groups, cands)
        achados = self._findings(interp, buckets["direto"] + buckets["responde"], groups)

        if not search_ok:
            status = "erro"
            msg = ("Não foi possível consultar as fontes por uma falha técnica. "
                   "Isso não é um resultado da pesquisa. Tente novamente em instantes.")
            sintese = {"situacao": "erro_tecnico", "frase": "A pesquisa não pôde ser feita por falha técnica.",
                       "detalhe": interp.afirmacao.get("detalhe_verificavel"), "metodo": "nenhum",
                       "explicacao": [], "evidencias": []}
        elif sintese.get("opcoes"):
            status = "ambigua"
            msg = "O nome pesquisado corresponde a mais de uma entidade nas fontes. Você quis dizer…?"
            suggestions = [{"texto": o["texto"], "origem": o["entidade"], "url": None, "data": None}
                           for o in sintese["opcoes"]]
        elif results["responde"] or results["direto"]:
            status = "ok"
            msg = self._summary(buckets["responde"] + buckets["direto"])
        else:
            status = "insuficiente"
            msg = ("A pesquisa foi concluída, mas não encontrou publicações que tratem diretamente deste "
                   "detalhe. Isso não significa que seja falso: pode ser recente, pouco divulgado, "
                   "descrito com outras palavras ou ausente das fontes consultadas.")
            if results["anterior"]:
                msg += (" Há publicações anteriores sobre assunto semelhante, listadas à parte; "
                        "elas não mostram que o fato ocorreu agora.")
        avisos = []
        if failed and search_ok:
            nomes = sorted({s["nome"] for s in failed})
            avisos.append("Algumas fontes falharam e os resultados podem estar incompletos: " + ", ".join(nomes) + ".")

        sintese["indicacao"] = evidence.indicate(sintese, interp)
        extra_details = []
        if interp.quantity is not None and status != "erro":
            # Outros valores na mesma frase ("8 títulos nacionais e 3 estaduais") são avaliados separadamente.
            entity = interp.entidades[0] if interp.entidades else None
            ent_tokens = frozenset(norm(entity).split()) if entity else frozenset()
            first = interp.quantity
            for q2 in extract_quantities(interp.assunto, ent_tokens)[:4]:
                if q2.posicao == first.posicao or len(extra_details) >= 2:
                    continue
                i2 = copy.copy(interp)
                i2.quantity = q2
                i2.afirmacao = {**interp.afirmacao, "quantidade": q2.to_dict(), "detalhe_negado": q2.negada,
                                "detalhe_verificavel": detail_text(q2, entity)}
                s2 = evidence.synthesize(i2, ev_groups, n_direct_groups)
                s2.pop("_items", None)
                s2["indicacao"] = evidence.indicate(s2, i2)
                extra_details.append(s2)

        answer_texts = [e["trecho"] for e in sintese.get("evidencias", [])]
        answer_texts += [e["trecho"] for x in extra_details for e in x.get("evidencias", [])]
        contexto = [] if status == "erro" else context.select(interp, groups, answer_texts)

        modelo = {"disponivel": llm.configured(), "pendente": False}
        if modelo["disponivel"] and status != "erro" and sintese["situacao"] in llm.USE_FOR:
            packets = llm.build_packets(groups)
            if packets:
                self.cache.set(("sintese", rid), {"interp": interp, "packets": packets}, 15 * 60)
                modelo["pendente"] = True
        elif not modelo["disponivel"]:
            modelo["motivo"] = "Modelo de linguagem não configurado (opcional; ver README)."

        resp = {
            **base, "status": status, "mensagem": msg, "avisos": avisos, "sintese": sintese,
            "detalhes_adicionais": extra_details, "contexto": contexto,
            "sintese_modelo": modelo, "sugestoes": suggestions if status == "ambigua" else [],
            "resultados": results, "achados": achados,
            "fontes_consultadas": sorted(statuses, key=lambda s: (s["fonte"], s["consulta"])),
            "descartados": {"quantidade": len(discarded), "exemplos": [
                {"titulo": c.title[:160], "veiculo": c.source_name, "motivo": a.reason}
                for c, a in sorted(discarded, key=lambda p: -p[1].score)[:8]]},
            "duracao_ms": int((time.monotonic() - t0) * 1000),
        }
        log.info("[%s] status=%s sintese=%s responde=%d direto=%d anterior=%d contexto=%d+%d descartados=%d "
                 "falhas=%d (%d ms)", rid, status, sintese["situacao"], len(results["responde"]),
                 len(results["direto"]), len(results["anterior"]), len(results["contexto"]),
                 len(results["contexto_geral"]), len(discarded), len(failed), resp["duracao_ms"])
        return resp

    @staticmethod
    def split_claims(text, limit=3):
        """Frases com afirmação própria. Frases sem nome próprio herdam as entidades da primeira."""
        clean = BYLINE_RE.sub(" ", clean_input(text))
        claims, main_ents = [], None
        for sent in _sentences(clean):
            content = [t for t in norm(sent).split() if t not in STOP_NORM and len(t) >= 3]
            if len(content) < 4 and not extract_quantities(sent):
                continue
            ents = extract_entities(sent)
            if main_ents is None:
                main_ents = ents
            query = sent if ents or not main_ents else f"{sent.rstrip(' .')} – {', '.join(main_ents[:2])}"
            claims.append({"frase": sent, "pesquisa": query})
            if len(claims) >= limit:
                break
        return claims

    def evaluate(self, text, request_id=None):
        """Uma afirmação: pesquisa normal. Várias: cada uma é pesquisada e avaliada separadamente."""
        parts = self.split_claims(text)
        if len(parts) < 2:
            return self.search(text, request_id)
        rid = (request_id or uuid.uuid4().hex[:8])[:64]
        with ThreadPoolExecutor(max_workers=len(parts)) as ex:
            results = list(ex.map(lambda p: self.search(p["pesquisa"], rid), parts))
        for p, r in zip(parts, results):
            r["trecho_da_entrada"] = p["frase"]
        all_failed = all(r["status"] == "erro" for r in results)
        log.info("[%s] avaliação por partes: %s", rid,
                 [r["sintese"]["indicacao"]["rotulo"] for r in results if "sintese" in r])
        return {
            "id_consulta": rid, "consultado_em": _iso(self.clock()), "modo": "partes",
            "status": "erro" if all_failed else "ok",
            "mensagem": (f"A entrada tem {len(parts)} afirmações. Cada uma foi avaliada separadamente; "
                         "o Lume não classifica a notícia inteira."),
            "partes": results,
        }

    def disambiguate(self, texto, detalhes="", rejeitadas=(), rodada=0, request_id=None):
        """Etapa anterior à pesquisa: decide se é preciso perguntar "O que você quer saber?"."""
        rid = (request_id or uuid.uuid4().hex[:8])[:64]

        def wiki_lookup(q):
            key = ("wiki_sentidos", norm(q))
            cached = self.cache.get(key)
            if cached:
                return cached[0]
            items = sources.wikipedia(q, self.fetch, limit=8)
            self.cache.set(key, items, WIKI_TTL)
            return items

        res = disambiguate.analyze(texto, self.fetch, detalhes, rejeitadas, rodada, self.clock(), wiki_lookup)
        log.info("[%s] desambiguação (%s): escolher=%s opções=%s motivo=%s %s", rid, res.metodo,
                 res.precisa_escolher, [o["texto"] for o in res.opcoes], res.motivo, res.aviso)
        return {"id_consulta": rid, **res.to_dict()}

    def synthesize_with_model(self, request_id):
        """Segunda etapa opcional: o modelo compara os trechos já coletados (não navega)."""
        stored = self.cache.get(("sintese", request_id or ""))
        if not stored:
            return {"id_consulta": request_id, "situacao": "erro_tecnico",
                    "frase": "Esta pesquisa expirou; pesquise novamente.", "metodo": "modelo"}
        data = stored[0]
        try:
            result = llm.synthesize(data["interp"], data["packets"])
        except llm.LlmError as e:
            log.warning("[%s] síntese por modelo falhou: %s", request_id, e)
            return {"id_consulta": request_id, "situacao": "erro_tecnico", "metodo": "modelo",
                    "frase": f"A análise por modelo falhou ({e.kind}). Os links acima continuam válidos."}
        result["indicacao"] = evidence.indicate(result, data["interp"])
        return {"id_consulta": request_id, **result}

    # ---- montagem ---------------------------------------------------------------------
    @staticmethod
    def _group_text(g):
        return " ".join(f"{c.title}. {c.snippet} {c.page_text}" for c, _a in g.members)

    def _group_dict(self, g, category, interp, ev_items=None):
        cand, a = g.lead
        best_a = max((m[1] for m in g.members), key=lambda x: (CATEGORY_RANK[x.category], x.score))
        others, seen_src = [], {norm(cand.source_name)}
        for c, _ in g.members:
            if c is cand or norm(c.source_name) in seen_src:
                continue
            seen_src.add(norm(c.source_name))
            others.append({"veiculo": c.source_name, "url": c.url, "titulo": c.title,
                           "data": _iso(c.published)})
        return {
            "titulo": cand.title,
            "url": cand.url,
            "observacao_link": cand.link_note or None,
            "veiculo": cand.source_name,
            "autor": cand.page_author or None,
            "data": _iso(cand.published),
            "periodo": _period(cand, self.clock()),
            "tipo_fonte": source_type(cand.domain, cand.origin),
            "relacao": category,
            "relacao_texto": "Informa o detalhe pesquisado" if category == "responde" else best_a.relation,
            "conteudo": cand.content_kind,
            "conteudo_texto": CONTENT_LABEL[cand.content_kind],
            "trecho": cand.snippet[:500] if cand.snippet else None,
            "trechos_pagina": ([e.trecho for e in ev_items][:2] if ev_items else cand.page_excerpts),
            "detalhes_presentes": best_a.ent_hits + best_a.term_hits + best_a.num_hits,
            "alertas": list(dict.fromkeys(best_a.alerts + cand.alerts)),
            "republicacoes": others,
            "pontuacao_interna": best_a.score,
        }

    def _summary(self, direct_groups, shown=12):
        outlets = {norm(c.source_name) for g in direct_groups for c, _ in g.members}
        dates = [c.published for g in direct_groups for c, _ in g.members if c.published]
        msg = (f"Encontramos {len(direct_groups)} publicação(ões) diferentes que tratam diretamente do assunto, "
               f"de {len(outlets)} veículo(s)")
        if dates:
            msg += f"; a mais recente é de {max(dates):%d/%m/%Y}"
        if len(direct_groups) > shown:
            msg += f". Mostramos as {shown} mais relevantes"
        return msg + ". Veja abaixo o que cada fonte diz; o Lume não classifica a afirmação como verdadeira ou falsa."

    def _findings(self, interp, direct_groups, all_groups):
        details = ([("entidade", e) for e in interp.entidades[:4]] +
                   [("numero", n["texto"]) for n in interp.numeros] +
                   [("data", d["texto"]) for d in interp.datas] +
                   [("ano", str(y)) for y in interp.anos if not any(str(y) in d["texto"] for d in interp.datas)] +
                   [("termo", t) for t in interp.termos[:5]])
        found, missing = [], []
        texts = [(g, self._group_text(g)) for g in direct_groups]
        for kind, detail in details:
            fontes = []
            for g, raw in texts:
                t = norm(raw)
                if kind == "numero":
                    ok = any(n["chave"] in numbers_in(raw) for n in interp.numeros if n["texto"] == detail)
                elif kind == "termo":
                    ok = stem(detail) in {stem(w) for w in t.split()}
                else:
                    ok = f" {norm(detail)} " in f" {t} "
                if ok:
                    c = g.lead[0]
                    fontes.append({"veiculo": c.source_name, "url": c.url})
            (found if fontes else missing).append({"tipo": kind, "detalhe": detail, "fontes": fontes[:5]})
        obs = []
        republished = sum(len(g.members) - 1 for g in all_groups if len(g.members) > 1)
        if republished:
            obs.append(f"{republished} publicação(ões) repetiam conteúdo já listado e foram agrupadas; "
                       "republicações não são confirmações independentes.")
        if interp.negacoes:
            obs.append("A entrada contém negação (“" + interp.negacoes[0] + "…”). Confira nos trechos se as "
                       "fontes afirmam o mesmo sentido; o Lume não interpreta isso automaticamente.")
        dated = [c.published for g in direct_groups for c, _ in g.members if c.published]
        if interp.tempo_relativo and direct_groups and dated and all(
                (self.clock() - d).days > 14 for d in dated):
            obs.append(f"A entrada fala em “{interp.tempo_relativo[0]}”, mas as publicações encontradas são anteriores.")
        if dated and not interp.anos and not interp.tempo_relativo and all((self.clock() - d).days > 365 for d in dated):
            obs.append("As publicações diretamente relacionadas têm mais de um ano; não indicam que o fato se repetiu agora.")
        return {"detalhes_encontrados": found, "detalhes_sem_resposta": missing, "observacoes": obs}

    def _suggestions(self, interp, groups, cands):
        # "Você quis dizer" só para entradas sem acontecimento/propriedade identificável.
        if not interp.incompleto:
            return []
        pool = [g for g in groups if g.lead[0].origin != "wikipedia"]
        pool.sort(key=lambda g: -max(a.score for _c, a in g.members))
        chosen = []
        for g in pool:
            c = g.lead[0]
            key = _title_key(c.title)
            if all(jaccard(key, _title_key(o["texto"])) < 0.3 for o in chosen):
                chosen.append({"texto": c.title, "origem": c.source_name, "url": c.url,
                               "data": _iso(c.published)})
            if len(chosen) >= 3:
                break
        if interp.incompleto:
            wiki = [c for c in cands if c.origin == "wikipedia" and not c.disambiguation]
            for c in wiki[:3]:
                if all(norm(c.title) != norm(o["texto"]) for o in chosen):
                    chosen.append({"texto": c.title, "origem": "Wikipédia", "url": c.url, "data": None})
        return chosen[:5]
