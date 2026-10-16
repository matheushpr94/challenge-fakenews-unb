// Local animation only. No network, AI calls or capture of user content.
export function initMascotMotion() {
  const media = matchMedia('(prefers-reduced-motion: reduce)');
  const control = document.getElementById('motion-toggle');
  let paused = false, pageVisible = !document.hidden;
  try { paused = localStorage.getItem('lume-motion') === 'paused'; } catch { /* Storage may be disabled. */ }
  const actors = [...document.querySelectorAll('img[data-lume]')].map(image => {
    const hero = image.classList.contains('hero-mascot');
    const avatar = document.createElement(hero ? 'button' : 'span');
    avatar.className = `lume-avatar ${image.className}`;
    if (hero) {
      avatar.type = 'button';
      avatar.setAttribute('aria-label', 'Cumprimentar Lume');
      avatar.title = 'Toque para dizer oi';
    } else avatar.setAttribute('aria-hidden', 'true');
    const body = document.createElement('span');
    body.className = 'lume-body';
    image.replaceWith(avatar);
    image.className = 'lume-frame';
    image.alt = ''; image.removeAttribute('data-lume');
    const closed = document.createElement('img');
    closed.className = 'lume-frame lume-blink'; closed.alt = '';
    closed.src = 'assets/lume-mascot-blink.png';
    body.append(image, closed); avatar.append(body);
    const actor = { avatar, hero, visible: false, ready: false, timer: null, opening: null, greeting: null, gesture: null, gestureEnd: null, rounds: 0 };
    closed.addEventListener('load', () => { actor.ready = true; schedule(actor); });
    if (closed.complete && closed.naturalWidth) actor.ready = true;
    if (hero) avatar.addEventListener('click', () => react(actor));
    return actor;
  });
  const enabled = () => !paused && !media.matches && pageVisible;
  const canBlink = actor => enabled() && actor.visible && actor.ready && !actor.avatar.classList.contains('is-dragging');
  const GESTURES = ['is-looking-left', 'is-looking-right', 'is-hopping', 'is-leaning', 'is-waving'];
  function stop(actor) {
    clearTimeout(actor.timer); clearTimeout(actor.opening); clearTimeout(actor.greeting);
    clearTimeout(actor.gesture); clearTimeout(actor.gestureEnd);
    actor.timer = actor.opening = actor.greeting = actor.gesture = actor.gestureEnd = null;
    actor.avatar.classList.remove('is-blinking', 'is-greeting', ...GESTURES);
  }
  // Gestos de descanso (só o mascote grande): um gesto curto a cada 7 a 15 s, nunca o mesmo duas vezes seguidas, e a
  // cada 3 ou 4 gestos uma pausa longa de 25 a 45 s. A pausa quebra o ar de laço infinito; o sorteio quebra o ritmo.
  function scheduleGesture(actor) {
    clearTimeout(actor.gesture);
    if (!actor.hero || !canBlink(actor)) return;
    const resting = actor.rounds > 0 && actor.rounds % (3 + Math.floor(Math.random() * 2)) === 0;
    const wait = resting ? 25000 + Math.random() * 20000 : 7000 + Math.random() * 8000;
    actor.gesture = setTimeout(() => {
      if (!canBlink(actor)) return;
      const choices = GESTURES.filter(g => g !== actor.last);
      const pick = choices[Math.floor(Math.random() * choices.length)];
      actor.last = pick; actor.rounds += 1;
      actor.avatar.classList.add(pick);
      actor.gestureEnd = setTimeout(() => {
        actor.avatar.classList.remove(pick);
        scheduleGesture(actor);
      }, pick === 'is-hopping' ? 700 : pick === 'is-waving' ? 1100 : 1500);
    }, wait);
  }
  function schedule(actor) {
    clearTimeout(actor.timer);
    if (!canBlink(actor)) return;
    actor.timer = setTimeout(() => {
      if (!canBlink(actor)) return;
      actor.avatar.classList.add('is-blinking');
      actor.opening = setTimeout(() => {
        actor.avatar.classList.remove('is-blinking');
        schedule(actor);
      }, 140);
    }, 3600 + Math.random() * 3600);
  }
  function react(actor) {
    if (!enabled() || !actor.visible) return;
    clearTimeout(actor.greeting);
    actor.avatar.classList.add('is-greeting');
    actor.greeting = setTimeout(() => actor.avatar.classList.remove('is-greeting'), 680);
  }
  function refresh() {
    document.documentElement.classList.toggle('motion-paused', !enabled());
    if (control) {
      control.textContent = media.matches ? 'Movimento reduzido' : paused ? 'Animações: pausadas' : 'Pausar animações';
      control.disabled = media.matches;
    }
    for (const actor of actors) { stop(actor); schedule(actor); scheduleGesture(actor); }
  }
  const observer = new IntersectionObserver(entries => {
    for (const entry of entries) {
      const actor = actors.find(a => a.avatar === entry.target);
      actor.visible = entry.isIntersecting;
      actor.avatar.classList.toggle('is-offscreen', !actor.visible);
      stop(actor); schedule(actor); scheduleGesture(actor);
    }
  });
  actors.forEach(actor => observer.observe(actor.avatar));
  control?.addEventListener('click', () => {
    paused = !paused;
    try { localStorage.setItem('lume-motion', paused ? 'paused' : 'on'); } catch { /* Optional preference. */ }
    refresh();
  });
  media.addEventListener('change', refresh);
  document.addEventListener('visibilitychange', () => { pageVisible = !document.hidden; refresh(); });
  window.addEventListener('pagehide', () => { pageVisible = false; refresh(); });
  window.addEventListener('pageshow', () => { pageVisible = !document.hidden; refresh(); });
  refresh();
  return {
    state(value) {
      const actor = actors.find(a => a.avatar.closest('.sheet-header'));
      if (!actor) return;
      actor.avatar.classList.toggle('is-thinking', value === 'loading');
      if (value === 'result' || value === 'menu') react(actor);
    },
    drag(element, dragging, offset = 0) {
      const actor = actors.find(a => element.contains(a.avatar));
      if (!actor) return;
      actor.avatar.classList.toggle('is-dragging', dragging && enabled());
      actor.avatar.style.setProperty('--drag-angle', `${Math.max(-8, Math.min(8, offset / 8))}deg`);
      if (dragging) stop(actor); else { schedule(actor); scheduleGesture(actor); }
    },
  };
}
