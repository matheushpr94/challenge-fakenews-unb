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
    const actor = { avatar, visible: false, ready: false, timer: null, opening: null, greeting: null };
    closed.addEventListener('load', () => { actor.ready = true; schedule(actor); });
    if (closed.complete && closed.naturalWidth) actor.ready = true;
    if (hero) avatar.addEventListener('click', () => react(actor));
    return actor;
  });
  const enabled = () => !paused && !media.matches && pageVisible;
  const canBlink = actor => enabled() && actor.visible && actor.ready && !actor.avatar.classList.contains('is-dragging');
  function stop(actor) {
    clearTimeout(actor.timer); clearTimeout(actor.opening); clearTimeout(actor.greeting);
    actor.timer = actor.opening = actor.greeting = null;
    actor.avatar.classList.remove('is-blinking', 'is-greeting');
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
    for (const actor of actors) { stop(actor); schedule(actor); }
  }
  const observer = new IntersectionObserver(entries => {
    for (const entry of entries) {
      const actor = actors.find(a => a.avatar === entry.target);
      actor.visible = entry.isIntersecting;
      actor.avatar.classList.toggle('is-offscreen', !actor.visible);
      stop(actor); schedule(actor);
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
      if (dragging) stop(actor); else schedule(actor);
    },
  };
}
