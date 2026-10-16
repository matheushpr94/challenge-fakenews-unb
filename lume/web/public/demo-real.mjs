// A "demonstração" do site é a tela real do app, gravada no emulador em 14/10/2026 (lume/web/public/videos). Sem
// simulação, sem índice em porcentagem, sem respostas fictícias: o que aparece aqui é o que o Lume 0.4.x mostra.
// Cada notícia é uma lista de trechos tocados em sequência e em laço; o seletor troca a notícia.
export const NOTICIAS = [
  { nome: 'Marco Legal dos games', trechos: ['videos/marco-legal-captura.mp4', 'videos/marco-legal-resultado.mp4'],
    legenda: 'Gravação real do Lume no emulador, 14/10/2026, sem edição: a notícia aberta no Chrome, a gotinha, “Ler esta tela”, a manchete lida, Pesquisar e o resultado: 6 outros veículos relataram o mesmo acontecimento.' },
  { nome: 'El Niño', trechos: ['videos/el-nino-resultado.mp4'],
    legenda: 'Resultado real para “Painel indica que El Niño pode ser um dos mais intensos desde 1950” (Agência Brasil): 9 veículos com o mesmo fato, cada um com o trecho que justifica.' },
  { nome: 'Confiança da indústria', trechos: ['videos/industria-resultado.mp4'],
    legenda: 'Resultado real em que o modelo 6B aponta uma fonte que a regra não tinha confirmado: o selo “Indicado pelo modelo de linguagem: provável mesmo fato”.' },
];

export function initDemoReal() {
  const video = document.getElementById('demo-video');
  const select = document.getElementById('scenario-select');
  const legenda = document.getElementById('demo-caption');
  const reset = document.getElementById('reset');
  if (!video || !select) return;
  let atual = 0, trecho = 0;
  const media = matchMedia('(prefers-reduced-motion: reduce)');
  function tocar() {
    const n = NOTICIAS[atual];
    video.src = n.trechos[trecho];
    video.load();
    if (!media.matches) video.play().catch(() => { /* autoplay pode ser bloqueado; o usuário toca para tocar */ });
  }
  function escolher(i) { atual = i; trecho = 0; select.value = String(i); if (legenda) legenda.textContent = NOTICIAS[i].legenda; tocar(); }
  video.addEventListener('ended', () => { trecho = (trecho + 1) % NOTICIAS[atual].trechos.length; tocar(); });
  video.addEventListener('click', () => { if (video.paused) video.play().catch(() => {}); else video.pause(); });
  select.addEventListener('change', e => escolher(Number(e.target.value)));
  reset?.addEventListener('click', () => escolher(atual));
  document.addEventListener('visibilitychange', () => { if (document.hidden) video.pause(); else if (!media.matches) video.play().catch(() => {}); });
  escolher(0);
}
