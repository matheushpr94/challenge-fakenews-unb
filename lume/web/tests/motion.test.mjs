import test from 'node:test';
import assert from 'node:assert/strict';
import { initMascotMotion } from '../public/motion.mjs';

// Minimal DOM harness: checks lifecycle/timers, not visual appearance.
class Element {
  constructor(className = '') {
    this.className = className; this.events = {}; this.children = []; this.complete = true; this.naturalWidth = 1280;
    this.style = { setProperty() {} };
    this.classList = {
      contains: value => this.className.split(' ').includes(value),
      add: (...values) => { this.className = [...new Set([...this.className.split(' '), ...values])].join(' '); },
      remove: (...values) => { this.className = this.className.split(' ').filter(v => !values.includes(v)).join(' '); },
      toggle: (value, active) => active ? this.classList.add(value) : this.classList.remove(value),
    };
  }
  append(...elements) { elements.forEach(e => { e.parent = this; this.children.push(e); }); }
  replaceWith(element) { element.parent = this.parent; this.parent.children[this.parent.children.indexOf(this)] = element; }
  setAttribute() {} removeAttribute() {}
  addEventListener(event, handler) { (this.events[event] ||= []).push(handler); }
  emit(event) { for (const handler of this.events[event] || []) handler(); }
  closest(selector) { return this.parent?.className === selector.slice(1) ? this.parent : this.parent?.closest(selector); }
  contains(element) { return this === element || this.children.some(child => child.contains(element)); }
}
function setup(t) {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  t.mock.method(Math, 'random', () => 0);
  const control = new Element(); const media = new Element(); media.matches = false;
  const doc = new Element(); doc.documentElement = new Element(); doc.hidden = false;
  const window = new Element(); const parents = [new Element(), new Element('floating-mascot'), new Element('sheet-header')];
  const images = parents.map((p, i) => { const image = new Element(i === 0 ? 'hero-mascot' : ''); p.append(image); return image; });
  doc.getElementById = () => control; doc.querySelectorAll = () => images; doc.createElement = () => new Element();
  const observed = [];
  let observer;
  const globals = { document: doc, window, matchMedia: () => media, localStorage: { getItem: () => null, setItem() {} },
    IntersectionObserver: class { constructor(callback) { this.callback = callback; observer = this; } observe(target) { observed.push(target); } } };
  const saved = Object.fromEntries(Object.keys(globals).map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
  for (const [key, value] of Object.entries(globals)) Object.defineProperty(globalThis, key, { configurable: true, value });
  t.after(() => { for (const [key, descriptor] of Object.entries(saved)) { if (descriptor) Object.defineProperty(globalThis, key, descriptor); else delete globalThis[key]; } });
  const motion = initMascotMotion();
  observer.callback(observed.map(target => ({ target, isIntersecting: true })));
  return { control, media, doc, window, parents, actors: observed, motion, observer };
}
test('blink opens again and pausing cancels pending/active blinks', t => {
  const { actors, control, doc } = setup(t);
  t.mock.timers.tick(3600);
  assert.ok(actors.every(a => a.classList.contains('is-blinking')));
  t.mock.timers.tick(140);
  assert.ok(actors.every(a => !a.classList.contains('is-blinking')));
  control.emit('click');
  t.mock.timers.tick(20000);
  assert.ok(doc.documentElement.classList.contains('motion-paused'));
  assert.ok(actors.every(a => !a.classList.contains('is-blinking')));
});
test('reduced motion and hidden pages suppress animation, then resume safely', t => {
  const { actors, media, doc, window } = setup(t);
  media.matches = true; media.emit('change');
  t.mock.timers.tick(10000);
  assert.ok(actors.every(a => !a.classList.contains('is-blinking')));
  media.matches = false; media.emit('change');
  doc.hidden = true; doc.emit('visibilitychange');
  t.mock.timers.tick(10000);
  assert.ok(doc.documentElement.classList.contains('motion-paused'));
  doc.hidden = false; window.emit('pageshow');
  t.mock.timers.tick(3600);
  assert.ok(actors.every(a => a.classList.contains('is-blinking')));
  window.emit('pagehide');
  assert.ok(actors.every(a => !a.classList.contains('is-blinking')));
});
test('drag and leaving the viewport cancel blinks without moving touch targets', t => {
  const { actors, motion, parents, observer } = setup(t);
  motion.drag(parents[1], true, 100);
  t.mock.timers.tick(3600);
  assert.ok(!actors[1].classList.contains('is-blinking'));
  assert.ok(actors[1].classList.contains('is-dragging'));
  motion.drag(parents[1], false);
  observer.callback(actors.map(target => ({ target, isIntersecting: false })));
  t.mock.timers.tick(10000);
  assert.ok(actors.every(a => !a.classList.contains('is-blinking')));
  assert.ok(!actors[1].classList.contains('is-dragging'));
});
