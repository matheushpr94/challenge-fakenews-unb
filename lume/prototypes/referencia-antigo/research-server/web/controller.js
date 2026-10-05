// Controla interpretação e pesquisa com uma única sequência: qualquer ação nova cancela a anterior
// e descarta respostas que não pertencem à ação atual (inclusive respostas fora de ordem).
export function createSearchController(fetchImpl, onUpdate, timeoutMs = 45000) {
  let seq = 0;
  let current = null;
  let currentId = null;

  async function request(url, payload, kind) {
    const my = ++seq;
    const id = `q${Date.now().toString(36)}-${my}`;
    currentId = id;
    if (current) current.abort();
    const ctrl = new AbortController();
    current = ctrl;
    const timer = setTimeout(() => ctrl.abort("timeout"), timeoutMs);
    onUpdate({ state: kind === "interpretar" ? "interpreting" : "loading", texto: payload.texto });
    try {
      const resp = await fetchImpl(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...payload, id_consulta: id }),
        signal: ctrl.signal,
      });
      let data = null;
      try { data = await resp.json(); } catch { data = null; }
      if (my !== seq) return "descartada";
      if (!data) {
        onUpdate({ state: "error", message: `O serviço respondeu com erro (HTTP ${resp.status}).` });
      } else if (data.id_consulta && data.id_consulta !== id) {
        return "descartada";
      } else if (kind === "interpretar") {
        onUpdate({ state: "interpreted", data });
      } else {
        onUpdate({ state: data.status || "error", data });
      }
      return "exibida";
    } catch (e) {
      if (my !== seq) return "descartada";
      if (ctrl.signal.aborted && ctrl.signal.reason === "timeout") {
        onUpdate({ state: "error", message: "A pesquisa demorou demais. Tente novamente." });
      } else if (e && e.name === "AbortError") {
        onUpdate({ state: "cancelled" });
      } else {
        onUpdate({ state: "error", message: "Não foi possível conectar ao serviço do Lume. Ele está em execução?" });
      }
      return "erro";
    } finally {
      clearTimeout(timer);
      if (current === ctrl) current = null;
    }
  }

  const search = (texto, extra = {}) => request("/api/search", { ...extra, texto }, "pesquisar");
  const interpret = (texto, extra = {}) => request("/api/interpretar", { ...extra, texto }, "interpretar");

  function cancel() {
    seq++;
    currentId = null;
    if (current) current.abort();
    current = null;
    onUpdate({ state: "cancelled" });
  }

  // Invalida a ação em andamento sem mostrar "cancelada" (ex.: usuário voltou às opções).
  function invalidate() {
    seq++;
    currentId = null;
    if (current) current.abort();
    current = null;
  }

  const isCurrent = (id) => id != null && id === currentId;
  return { search, interpret, cancel, invalidate, isCurrent };
}
