// Nova pesquisa antes de a anterior terminar: só a mais recente pode aparecer na tela.
// Rodar com Node 18+:  node --test tests/controller.test.mjs
// (Sem Node, o mesmo código foi executado no navegador importando /controller.js.)
import { test } from "node:test";
import assert from "node:assert/strict";
import { createSearchController } from "../web/controller.js";

function fakeFetch(delays, honorAbort) {
  return (url, opts) => {
    const body = JSON.parse(opts.body);
    return new Promise((resolve, reject) => {
      const t = setTimeout(() => resolve({ status: 200,
        json: async () => ({ status: "ok", id_consulta: body.id_consulta, texto: body.texto }) }), delays[body.texto]);
      if (honorAbort) opts.signal.addEventListener("abort", () => {
        clearTimeout(t); const e = new Error("abort"); e.name = "AbortError"; reject(e);
      });
    });
  };
}

for (const honorAbort of [true, false]) {
  test(`resposta antiga é descartada (abort respeitado: ${honorAbort})`, async () => {
    const shown = [];
    const c = createSearchController(fakeFetch({ A: 200, B: 20 }, honorAbort), (u) => u.data && shown.push(u.data.texto));
    const [r1, r2] = await Promise.all([c.search("A"), c.search("B")]);
    assert.equal(r1, "descartada");
    assert.equal(r2, "exibida");
    assert.deepEqual(shown, ["B"]);
  });
}

test("interpretação antiga não substitui a pesquisa escolhida depois", async () => {
  const seen = [];
  const f = (url, opts) => {
    const body = JSON.parse(opts.body);
    const delay = url.includes("interpretar") ? 200 : 20;
    return new Promise((res) => setTimeout(() => res({ status: 200, json: async () => (
      url.includes("interpretar")
        ? { id_consulta: body.id_consulta, precisa_escolher: true, opcoes: [{ texto: "X?" }] }
        : { id_consulta: body.id_consulta, status: "ok", texto: body.texto }) }), delay));
  };
  const c = createSearchController(f, (u) => u.data && seen.push(u.state));
  const [r1, r2] = await Promise.all([c.interpret("entrada antiga"), c.search("Opção escolhida?")]);
  assert.equal(r1, "descartada");
  assert.equal(r2, "exibida");
  assert.deepEqual(seen, ["ok"]);
});
