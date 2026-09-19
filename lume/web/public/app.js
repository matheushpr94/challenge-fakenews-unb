import { criteria, evaluateEvidence } from './evidence.mjs';
import { articles } from './scenarios.mjs';
import { initMascotMotion } from './motion.mjs';

const motion = initMascotMotion();

const $ = (id) => document.getElementById(id);
const sheet = $('sheet'), content = $('sheet-content');
let timer, state = 'idle', current = 0, returnFocus;
function openSheet(label) { returnFocus ||= document.activeElement; $('hint').hidden=true; $('shade').hidden=false; sheet.hidden=false; $('mascot').hidden=true; $('state-label').textContent=label; motion.state(state); }
function draw(html, label) { openSheet(label); content.innerHTML=html; sheet.scrollTop=0; requestAnimationFrame(()=>content.focus({preventScroll:true})); }
function close() { clearTimeout(timer); sheet.hidden=true; $('shade').hidden=true; $('mascot').hidden=false; state='idle'; motion.state(state); returnFocus?.focus(); returnFocus=null; }
function menu() { state='menu'; draw('<h3>O que vamos entender?</h3><p>Posso olhar este trecho e ajudar você a fazer sentido dele.</p><button class="action" data-action="capture">Ler esta tela ↗</button><p class="small">Nesta prévia, a captura é simulada. No Android, você autoriza o acesso pelo sistema.</p>','por aqui'); }
function permission() { state='permission'; draw('<span class="demo-tag">ETAPA SIMULADA DO ANDROID</span><h3>Compartilhar esta tela?</h3><p>No aplicativo, o Android pede sua autorização antes de iniciar uma sessão de captura.</p><button class="action" data-action="allow">Simular captura</button><button class="action secondary" data-action="cancel">Agora não</button>','você decide'); }
function review() { state='review'; const a=articles[current]; draw(`<h3>Vamos olhar isso juntos?</h3><p>Confira o trecho antes de continuar.</p><div class="capture-summary"><strong>${a.title}</strong><span>Trecho visível · exemplo fictício</span></div><input class="input" id="question" aria-label="Sua dúvida" placeholder="Sua dúvida (opcional)" maxlength="180"><p class="small">No app, continuar envia a captura à IA. Esta prévia usa apenas respostas de exemplo.</p><button class="action" data-action="verify">Checar evidências</button><button class="action secondary" data-action="explain">Explicar este conteúdo</button>`,'conteúdo capturado'); }
function analyze(mode) { state='loading'; const question=$('question')?.value.trim(); draw(`<h3>${mode==='verify'?'Buscando evidências…':'Lendo com atenção…'}</h3><p>Simulação do estado de análise.</p><div class="loading" aria-label="Analisando"><span></span><span></span><span></span></div>`,'um instante'); timer=setTimeout(()=>result(mode,question),1000); }
function evidenceSummary(assessment) {
  const evaluation = evaluateEvidence(assessment);
  const available = evaluation.score !== null;
  return `<section class="evidence-index ${evaluation.tone}" aria-label="Índice de evidências demonstrativo">
    <div class="index-heading">Índice de evidências <span>DEMO</span></div>
    ${available ? `<div class="index-value">${evaluation.score}<span>%</span></div>
      <div class="index-track" role="meter" aria-label="Índice de evidências demonstrativo" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${evaluation.score}" aria-valuetext="${evaluation.score} por cento no checklist demonstrativo; não é probabilidade de verdade"><span style="width:${evaluation.score}%"></span></div>` : '<div class="index-empty" aria-hidden="true">?</div>'}
    <h3 class="index-verdict">${evaluation.label}</h3>
    <p class="index-note">${available ? 'Valor demonstrativo. Não é a chance de a notícia ser verdadeira.' : 'Sem percentual. Faltam informações para uma avaliação responsável.'}</p>
  </section>`;
}
function evidenceDetails(assessment) {
  const available = evaluateEvidence(assessment).score !== null;
  return `<details class="evidence-details"><summary>Entender a avaliação e as fontes</summary>
    <p class="claim-label">Afirmação em análise</p><p class="checked-claim">${assessment.claim}</p>
    ${available ? `<ul class="criteria-list">${criteria.map(({id, label, weight}) => {
      const item = assessment.criteria[id];
      const status = item.level === 1 ? 'Atendido' : item.level === 0.5 ? 'Parcial' : 'Não atendido';
      return `<li><div><strong>${label}</strong><span>${item.level * weight}/${weight} pts</span></div><p><b>${status}.</b> ${item.reason}</p></li>`;
    }).join('')}</ul><p class="method-note">Somamos os pontos de quatro critérios. Cada um pode ser atendido, parcial ou não atendido. Pesos e faixas são uma proposta de produto, ainda sem validação com notícias reais.</p>` : ''}
    <div class="demo-sources"><strong>Fontes deste exemplo</strong><p>${assessment.sources}</p></div>
  </details>`;
}
function result(mode,question) {
  state='result';
  const a=articles[current];
  draw(`<span class="demo-tag">CENÁRIO FICTÍCIO · SEM CONSULTA À IA</span>
    ${mode==='verify' ? evidenceSummary(a.assessment) : '<h3>Vamos por partes.</h3>'}
    <p>${a.explanation}</p>
    ${mode==='verify' ? evidenceDetails(a.assessment) : ''}
    <div class="result-block"><strong>O que vale conferir</strong><p>${a.question}</p></div>
    ${question ? '<p class="small" id="question-note"></p>' : ''}
    ${mode==='explain' ? '<button class="action" data-action="verify">Ver índice de evidências</button>' : ''}
    <button class="action ${mode==='explain' ? 'secondary' : ''}" data-action="again">Perguntar sobre este conteúdo</button>
    <button class="action secondary" data-action="done">Continuar navegando</button>`,'vamos entender');
  if(question) $('question-note').textContent='Sua pergunta: “'+question+'”. A resposta personalizada fica disponível no app conectado.';
}
content.addEventListener('click',e=>{const action=e.target.closest('[data-action]')?.dataset.action;if(action==='capture')permission();if(action==='allow'||action==='again')review();if(action==='cancel'||action==='done')close();if(action==='explain'||action==='verify')analyze(action);});
$('close').onclick=close;$('shade').onclick=close;
$('reset').onclick=()=>{close();$('hint').hidden=false;$('mascot').style.left='';$('mascot').style.top='';$('page').scrollTop=0;};
function selectScenario(index) {
  close(); current=index;
  const a=articles[current];
  $('scenario-select').value=String(current);
  $('article-title').textContent=a.title;
  $('article-deck').textContent=a.deck;
  $('article-body').textContent=a.body;
  $('address').textContent=a.address;
  $('page').scrollTop=0;
  $('hint').hidden=false;
}
$('scenario').onclick=()=>selectScenario((current+1)%articles.length);
$('scenario-select').onchange=e=>selectScenario(Number(e.target.value));
document.addEventListener('keydown',e=>{if(e.key==='Escape')close();if(e.key==='Tab'&&!sheet.hidden){const elements=[...sheet.querySelectorAll('button,input,a[href],summary,select')];const first=elements[0],last=elements.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}});
const mascot=$('mascot');let origin=null,moved=false,suppressClick=false;
mascot.addEventListener('pointerdown',e=>{const r=mascot.getBoundingClientRect(),p=$('phone').getBoundingClientRect(),scale=p.width/$('phone').offsetWidth;origin={x:e.clientX,y:e.clientY,left:(r.left-p.left)/scale-7,top:(r.top-p.top)/scale-7,scale};moved=false;mascot.setPointerCapture(e.pointerId);});
mascot.addEventListener('pointermove',e=>{if(!origin)return;const dx=(e.clientX-origin.x)/origin.scale,dy=(e.clientY-origin.y)/origin.scale;if(Math.abs(dx)+Math.abs(dy)>6)moved=true;if(moved){motion.drag(mascot,true,dx);mascot.style.left=Math.max(0,Math.min($('phone').clientWidth-72,origin.left+dx))+'px';mascot.style.top=Math.max(72,Math.min($('phone').clientHeight-95,origin.top+dy))+'px';$('hint').hidden=true;}});
mascot.addEventListener('pointerup',()=>{motion.drag(mascot,false);suppressClick=moved;origin=null;});
mascot.addEventListener('pointercancel',()=>{motion.drag(mascot,false);origin=null;});
mascot.addEventListener('click',()=>{if(suppressClick){suppressClick=false;return;}menu();});
