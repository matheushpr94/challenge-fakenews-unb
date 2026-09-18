import http from 'node:http';
import { timingSafeEqual } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { ApiError, validateInput, buildRequest, normalizeResponse } from './core.mjs';

const MAX_BODY = 4800000;
export function createServer({ apiKey = process.env.OPENAI_API_KEY, clientToken = process.env.LUME_CLIENT_TOKEN,
  model = process.env.OPENAI_MODEL || 'gpt-5.6-luna', fetchFn = fetch } = {}) {
  let running = 0;
  let windowStart = Date.now(), used = 0;
  return http.createServer(async (req, res) => {
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    const send = (status, body) => { res.writeHead(status); res.end(JSON.stringify(body)); };
    if (req.url === '/health' && req.method === 'GET') return send(200, { status: 'ok', ready: Boolean(apiKey && clientToken?.length >= 32) });
    if (req.url !== '/analyze' || req.method !== 'POST') return send(404, { error: 'Rota não encontrada.' });
    if (!apiKey || !clientToken || clientToken.length < 32) return send(503, { error: 'Configure a chave da IA e o token do piloto no servidor.' });
    const received = Buffer.from(req.headers.authorization || '');
    const expected = Buffer.from(`Bearer ${clientToken}`);
    if (received.length !== expected.length || !timingSafeEqual(received, expected)) return send(401, { error: 'Token do piloto inválido.' });
    if (!req.headers['content-type']?.startsWith('application/json')) return send(415, { error: 'Envie JSON.' });
    if (Date.now() - windowStart > 60000) { windowStart = Date.now(); used = 0; }
    if (running >= 2 || used >= 10) return send(429, { error: 'Aguarde um momento antes de tentar novamente.' });
    used++; running++;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 65000);
    res.on('close', () => { if (!res.writableEnded) controller.abort(); });
    try {
      if (Number(req.headers['content-length']) > MAX_BODY) throw new ApiError(413, 'Conteúdo muito grande.');
      const chunks = []; let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        if (size > MAX_BODY) throw new ApiError(413, 'Conteúdo muito grande.');
        chunks.push(chunk);
      }
      let value;
      try { value = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { throw new ApiError(400, 'JSON inválido.'); }
      const input = validateInput(value);
      const upstream = await fetchFn('https://api.openai.com/v1/responses', {
        method: 'POST', signal: controller.signal,
        headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(buildRequest(input, model))
      });
      if (!upstream.ok) throw new ApiError(502, upstream.status === 429 ? 'O limite da IA foi atingido. Tente novamente mais tarde.' : 'A IA está indisponível. Confira chave, modelo e acesso no servidor.');
      send(200, normalizeResponse(await upstream.json(), input.mode));
    } catch (error) {
      if (!res.destroyed) send(error.status || (controller.signal.aborted ? 504 : 502), {
        error: error instanceof ApiError ? error.message : controller.signal.aborted ? 'A análise demorou demais. Tente novamente.' : 'Não foi possível concluir a análise.'
      });
    } finally { clearTimeout(timer); running--; }
  });
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.env.PORT || 8787);
  const host = process.env.HOST || '127.0.0.1';
  const server = createServer();
  server.requestTimeout = 80000;
  server.listen(port, host, () => console.log(`Lume: http://${host}:${port} • sem armazenamento de capturas`));
}
