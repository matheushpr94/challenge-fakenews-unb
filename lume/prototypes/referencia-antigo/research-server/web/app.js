import { createSearchController } from "./controller.js";

const $ = (id) => document.getElementById(id);
const form = $("form"), texto = $("texto"), btn = $("btn"), cancelBtn = $("cancel");
const statusEl = $("status"), out = $("out");

// Todo conteúdo externo entra via textContent: nada vindo das fontes é interpretado como HTML.
function el(tag, attrs = {}, ...children) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v == null || v === false) continue;
    if (k === "class") n.className = v;
    else if (k === "text") n.textContent = v;
    else if (k === "onclick") n.addEventListener("click", v);
    else n.setAttribute(k, v);
  }
  for (const c of children.flat(Infinity)) if (c != null && c !== false) n.append(c instanceof Node ? c : String(c));
  return n;
}

function safeHref(url) {
  try {
    const u = new URL(url);
    return u.protocol === "http:" || u.protocol === "https:" ? u.href : null;
  } catch { return null; }
}

function link(url, label) {
  const href = safeHref(url);
  return href ? el("a", { href, target: "_blank", rel: "noopener noreferrer nofollow", text: label }) : el("span", { text: label });
}

function fmtDate(iso) {
  if (!iso) return null;
  const d = new Date(iso);
  return isNaN(d) ? null : d.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" });
}

const TYPE_LABEL = {
  noticia: "Notícia", instituicao_publica: "Instituição pública", cientifica: "Instituição científica",
  organismo_internacional: "Organismo internacional", enciclopedia: "Enciclopédia", web: "Página web",
  rede_social: "Rede social",
};
const PERIOD_LABEL = { recente: "Recente (até 30 dias)", anterior: "Mais de 30 dias", sem_data: "Sem data" };

function setStatus(kind, message) {
  statusEl.hidden = !message;
  statusEl.className = `status ${kind}`;
  statusEl.textContent = message || "";
}

function resultCard(r) {
  const cls = (r.relacao === "direto" || r.relacao === "responde") ? "" : r.relacao === "anterior" ? "old" : "ctx";
  const meta = el("div", { class: "meta" },
    el("span", { text: r.veiculo }),
    fmtDate(r.data) && el("span", { text: fmtDate(r.data) }),
    r.autor && el("span", { text: `Autor: ${r.autor}` }),
  );
  const badges = el("div", { class: "badges" },
    el("span", { class: `badge ${r.relacao === "anterior" ? "old" : "rel"}`, text: r.relacao_texto }),
    el("span", { class: "badge", text: r.conteudo_texto }),
    el("span", { class: "badge", text: TYPE_LABEL[r.tipo_fonte] || r.tipo_fonte }),
    r.data && el("span", { class: "badge", text: PERIOD_LABEL[r.periodo] }),
  );
  const body = [];
  if (r.trechos_pagina && r.trechos_pagina.length) {
    body.push(el("div", { class: "quote-label", text: "Trechos da página:" }));
    r.trechos_pagina.forEach((t) => body.push(el("blockquote", { text: t })));
  } else if (r.trecho) {
    body.push(el("div", { class: "quote-label", text: r.relacao === "contexto" && r.tipo_fonte === "enciclopedia" ? "Resumo da Wikipédia:" : "Trecho do buscador:" }));
    body.push(el("blockquote", { text: r.trecho }));
  }
  (r.alertas || []).forEach((a) => body.push(el("p", { class: "alert", text: "⚠ " + a })));
  if (r.republicacoes && r.republicacoes.length) {
    body.push(el("details", {},
      el("summary", { text: `Mesmo conteúdo em mais ${r.republicacoes.length} veículo(s) — não são confirmações independentes` }),
      el("ul", { class: "clean" }, r.republicacoes.map((p) => el("li", {}, link(p.url, p.veiculo), ` — ${p.titulo}`)))));
  }
  if (r.conteudo === "titulo") body.push(el("p", { class: "small", text: "O Lume não leu o conteúdo desta página; abra o link para conferir." }));
  if (r.observacao_link) body.push(el("p", { class: "small", text: r.observacao_link }));
  return el("article", { class: `card result ${cls}` }, el("h3", {}, link(r.url, r.titulo)), meta, badges, body);
}

function section(title, hint, items) {
  if (!items || !items.length) return null;
  return el("section", {}, el("h2", { text: title }), hint && el("p", { class: "hint", text: hint }), items.map(resultCard));
}

// Estado da conversa de esclarecimento. A entrada original e os detalhes são sempre preservados.
const flow = { original: "", detalhes: "", rejeitadas: [], rodada: 0, opcoes: [], pergunta: "" };

function research(t) {
  startSearch(t);
}

function startSearch(q) {
  flow.pergunta = q;
  out.replaceChildren(queryBar(q)); // nada da pesquisa anterior fica visível como resposta
  controller.search(q, { entrada_original: flow.original });
}

function queryBar(q, chips) {
  return el("div", { class: "querybar", id: "querybar" },
    el("p", {}, el("span", { class: "small", text: "Pesquisando: " }), el("b", { text: q }), " ",
      el("button", { type: "button", class: "linkbtn", text: "Corrigir", onclick: showCorrection })),
    chips && chips.length ? el("div", { class: "chips" }, chips) : null);
}

function showCorrection() {
  controller.invalidate();
  setStatus("", "");
  const input = el("input", { type: "text", value: flow.pergunta, "aria-label": "Corrigir a pergunta", id: "fix-q" });
  out.replaceChildren(el("section", { class: "card" },
    el("label", { for: "fix-q", text: "Corrigir a pergunta" }), input,
    el("div", { class: "actions" }, el("button", { type: "button", text: "Pesquisar",
      onclick: () => startSearch(input.value.trim() || flow.pergunta) }))));
  if (flow.opcoes.length) out.append(chooser());
  input.focus();
}

function chooser() {
  const sec = el("section", { class: "card chooser", role: "group", "aria-labelledby": "chooser-h" },
    el("h2", { text: "O que você quer saber?", tabindex: "-1", id: "chooser-h" }),
    el("div", { class: "options" }, flow.opcoes.map((o) =>
      el("button", { type: "button", class: "option-btn", text: o.texto, onclick: () => startSearch(o.texto) }))));
  sec.append(el("button", { type: "button", class: "option-btn other", text: "Não é isso — adicionar detalhes",
    onclick: () => showDetails(sec) }));
  return sec;
}

function showDetails(sec) {
  if (sec.querySelector("#detalhes")) return;
  const ta = el("textarea", { rows: "3", id: "detalhes", maxlength: "2000" });
  sec.append(el("div", { class: "details-form" },
    el("label", { for: "detalhes", text: "Conte um pouco mais sobre o que você quis dizer." }), ta,
    el("div", { class: "actions" }, el("button", { type: "button", text: "Continuar", onclick: () => {
      const d = ta.value.trim();
      if (!d) { ta.focus(); return; }
      flow.rejeitadas.push(...flow.opcoes.map((o) => o.texto));
      flow.detalhes = [flow.detalhes, d].filter(Boolean).join(". ");
      flow.rodada += 1;
      controller.interpret(flow.original, { detalhes: flow.detalhes, rejeitadas: flow.rejeitadas, rodada: flow.rodada });
    } }))));
  ta.focus();
}

function showChooser(opcoes, aviso) {
  flow.opcoes = opcoes;
  setStatus(aviso ? "warn" : "", aviso || "");
  out.replaceChildren(chooser());
  const h = document.getElementById("chooser-h");
  if (h) h.focus();
}

const IND_CLASS = { tende_verdadeira: "sit-ok", tende_falsa: "sit-no", inconclusiva: "sit-none",
  resposta: "sit-info", sem_resposta: "sit-none", falha_tecnica: "sit-err" };
const METHOD = { regras: "Comparação por regras (quantidade, unidade, categoria, período ou ano).",
  modelo: "Comparação feita por modelo de linguagem a partir dos trechos listados; pode conter erros.",
  nenhum: "" };
const AVISO = "Indicação baseada nas fontes consultadas; não é uma garantia de veracidade.";

function indicatorCard(s, title) {
  const ind = s.indicacao || { rotulo: "inconclusiva", texto: "Inconclusiva", motivo: s.frase || "" };
  return el("section", { class: `card synth ${IND_CLASS[ind.rotulo] || ""}`, id: title ? null : "synth" },
    el("div", { class: "quote-label", text: title || "Indicação provisória" }),
    el("p", { class: "synth-title", text: ind.texto }),
    ind.motivo && el("p", { class: "small", text: ind.motivo }),
    s.detalhe && el("p", { class: "small" }, el("b", { text: "Detalhe comparado: " }), s.detalhe),
    (s.explicacao || []).length > 0 && [
      el("h3", { class: "found-h", text: "O que encontramos" }),
      el("ul", { class: "clean" }, s.explicacao.map((e) =>
        el("li", {}, e.texto, (e.fontes || []).length ? [" — ", e.fontes.map((f, k) => [k ? ", " : "", link(f.url, f.veiculo)])] : "")))],
    el("p", { class: "small disclaimer", text: [ind.aviso || AVISO, METHOD[s.metodo] || ""].join(" ").trim() }));
}
const synthesisCard = (s) => indicatorCard(s);

function structureChips(i) {
  const a = i.afirmacao || {};
  const chips = [];
  const add = (k, v) => v && chips.push(el("span", { class: "chip" }, el("b", { text: k }), String(v)));
  add("tipo", a.tipo_texto);
  add("entidade", a.entidade_principal);
  if (a.quantidade) add("quantidade", a.quantidade.texto ? `${a.quantidade.negada ? "não " : ""}${a.quantidade.texto}${a.quantidade.qualificadores.length ? " (" + a.quantidade.qualificadores.join(" ") + ")" : ""}` : `? ${a.quantidade.unidade}`);
  else add("propriedade", a.propriedade);
  (i.datas || []).forEach((d) => add("data", d.texto));
  (i.anos || []).forEach((y) => add("ano", y));
  (i.tempo_relativo || []).forEach((n) => add("tempo", n));
  add("negação", a.negacao_texto);
  return chips;
}

// Um resultado de pesquisa (uma afirmação). `part` = título quando a entrada tem várias afirmações.
function resultBlock(data, part) {
  const i = data.interpretacao || {};
  const res = data.resultados || {};
  const box = el("div", { class: part ? "part" : "" });
  if (part) box.append(el("h2", { class: "part-h", text: part }));
  if (data.sintese) {
    const card = indicatorCard(data.sintese, part ? "Indicação provisória" : null);
    const obs = (data.achados || {}).observacoes || [];
    if (obs.length) card.querySelector(".disclaimer").before(el("ul", { class: "clean small" }, obs.map((o) => el("li", { text: o }))));
    box.append(card);
  }
  (data.detalhes_adicionais || []).forEach((s) => box.append(indicatorCard(s, "Indicação provisória — outro detalhe da mesma frase")));

  const src = section("Fontes que esclarecem o detalhe", "Trechos de onde os valores comparados foram retirados.", res.responde);
  if (src) box.append(src);

  // Contexto: só trechos completos que explicam o detalhe (definição, método, período), com fonte.
  const ctx = data.contexto || [];
  if (ctx.length) {
    box.append(el("section", {}, el("h2", { text: "Contexto" }),
      ctx.map((c) => el("div", { class: "card ctx-card" },
        el("blockquote", { text: c.texto }),
        el("p", { class: "small" }, `${c.motivo[0].toUpperCase()}${c.motivo.slice(1)} — `, link(c.url, c.veiculo),
          c.titulo ? ` (${c.titulo})` : "")))));
  }

  const related = [...(res.direto || []), ...(res.anterior || [])].slice(0, 6);
  if (related.length) {
    box.append(el("details", { class: "card" },
      el("summary", { text: `Outras publicações relacionadas (${related.length}) — mesmo assunto, sem o detalhe comparado` }),
      related.map(resultCard)));
  }

  const fontes = data.fontes_consultadas || [];
  box.append(el("details", { class: "card" },
    el("summary", { text: `Detalhes da pesquisa (consultada em ${new Date(data.consultado_em).toLocaleString("pt-BR")})` }),
    el("p", { class: "small", text: "Consultas: " + (i.consultas || []).map((q) => `“${q.texto}”`).join(" · ") }),
    el("div", { class: "table-wrap" }, el("table", {},
      el("tr", {}, ["Fonte", "Consulta", "Situação", "Itens", "Obtido em"].map((h) => el("th", { text: h }))),
      fontes.map((f) => el("tr", {},
        el("td", { text: f.nome }), el("td", { text: f.consulta }),
        el("td", { text: f.status === "erro" ? `falha (${f.erro})` : f.status }),
        el("td", { text: String(f.quantidade ?? 0) }),
        el("td", { text: (f.obtido_em ? new Date(f.obtido_em).toLocaleTimeString("pt-BR") : "") + (f.em_cache ? " (cache)" : "") }))))),
    data.descartados && el("p", { class: "small", text: `${data.descartados.quantidade} resultado(s) descartado(s) por não tratarem do assunto.` })));
  return box;
}

function render(data, onModel) {
  if (data.modo === "partes") {
    out.replaceChildren(queryBar(flow.pergunta || ""));
    out.append(el("p", { class: "small", text: data.mensagem }));
    data.partes.forEach((p, k) => out.append(resultBlock(p, `Afirmação ${k + 1}: “${p.trecho_da_entrada}”`)));
    return;
  }
  const i = data.interpretacao || {};
  out.replaceChildren(queryBar(flow.pergunta || i.texto_original || "", structureChips(i)));
  if (data.status === "ambigua" && (data.sugestoes || []).length) {
    // Homônimos só percebidos nas fontes: mesma pergunta "O que você quer saber?".
    flow.opcoes = data.sugestoes.map((s) => ({ texto: s.texto, diferenca: "significado_ou_entidade" }));
    out.append(chooser());
  }
  out.append(resultBlock(data));
  if (data.sintese_modelo && data.sintese_modelo.pendente) {
    const synth = document.getElementById("synth");
    const wait = el("p", { class: "status loading", id: "model-wait", text: "Comparando os trechos com o modelo de linguagem…" });
    if (synth) synth.after(wait); else out.append(wait);
    onModel(data.id_consulta);
  }
}

function onUpdate(u) {
  const busy = u.state === "loading" || u.state === "interpreting";
  btn.disabled = busy;
  cancelBtn.hidden = !busy;
  if (u.state === "interpreting") {
    out.replaceChildren();
    setStatus("loading", "Entendendo a pergunta…");
    return;
  }
  if (u.state === "loading") { setStatus("loading", "Pesquisando notícias e fontes…"); return; }
  if (u.state === "cancelled") { setStatus("warn", "Pesquisa cancelada."); return; }
  if (!u.data) { setStatus("err", u.message || "Erro inesperado."); out.replaceChildren(); return; }
  const d = u.data;
  if (u.state === "interpreted") {
    if (d.precisa_escolher && (d.opcoes || []).length) showChooser(d.opcoes, d.aviso);
    else startSearch(d.texto_pesquisa || flow.original);
    return;
  }
  const kind = { ok: "", ambigua: "warn", insuficiente: "", erro: "err", entrada_invalida: "warn" }[d.status] ?? "err";
  const avisos = (d.avisos || []).join(" ");
  // A mensagem de busca só aparece quando é útil (falha técnica, aviso, entrada inválida ou ambiguidade).
  const showMsg = d.status === "erro" || d.status === "entrada_invalida" || d.status === "ambigua";
  setStatus(kind, [showMsg ? d.mensagem : "", avisos].filter(Boolean).join(" "));
  if (d.status === "entrada_invalida") { out.replaceChildren(); return; }
  render(d, requestModel);
}

async function requestModel(id) {
  let s;
  try {
    const r = await fetch("/api/sintese", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id_consulta: id }) });
    s = await r.json();
  } catch {
    s = { situacao: "erro_tecnico", frase: "Não foi possível obter a análise do modelo.", metodo: "modelo" };
  }
  if (!controller.isCurrent(id)) return; // resposta de uma pesquisa antiga: descartada
  const wait = document.getElementById("model-wait");
  if (wait) wait.remove();
  const old = document.getElementById("synth");
  const card = synthesisCard(s);
  if (old) old.replaceWith(card); else out.prepend(card);
}

const controller = createSearchController((...a) => fetch(...a), onUpdate);
form.addEventListener("submit", (e) => {
  e.preventDefault();
  const t = texto.value.trim();
  if (!t) { setStatus("warn", "Digite uma notícia, afirmação ou pergunta."); return; }
  Object.assign(flow, { original: t, detalhes: "", rejeitadas: [], rodada: 0, opcoes: [], pergunta: "" });
  controller.interpret(t);
});
texto.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) form.requestSubmit();
});
cancelBtn.addEventListener("click", () => controller.cancel());
