// Lightweight regression tests: execute the real inline script in a small DOM double.
// No browser download, server, npm package, or Android build is required.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const html = fs.readFileSync(process.env.INDEX_HTML || path.join(__dirname, '../web/index.html'), 'utf8');
const script = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].at(-1)[1];

function element() {
  const classes = new Set();
  return {
    style: { setProperty(key, value) { this[key] = value; } },
    dataset: {}, children: [], listeners: {},
    classList: {
      add(...names) { names.forEach(n => classes.add(n)); },
      remove(name) { classes.delete(name); },
      contains(name) { return classes.has(name); },
      toggle(name, force) {
        const on = force === undefined ? !classes.has(name) : force;
        if (on) classes.add(name); else classes.delete(name);
        return on;
      }
    },
    addEventListener(name, fn) { this.listeners[name] = fn; },
    appendChild(child) { this.children.push(child); },
    contains(child) { return this === child || this.children.includes(child); },
    remove() { this.removed = true; }
  };
}

function boot({ saved = {}, blocked = false, observer = true, color = true, reducedMotion = false } = {}) {
  const ids = new Map();
  const get = selector => {
    if (!ids.has(selector)) ids.set(selector, element());
    return ids.get(selector);
  };
  const root = element();
  const reveals = [element(), element()];
  const storage = new Map(Object.entries(saved));
  const document = {
    documentElement: root,
    querySelector: get,
    querySelectorAll: selector => selector === '.swatch' ? get('#swatches').children : reveals,
    createElement: element,
    addEventListener() {},
    fonts: { ready: Promise.resolve() }
  };
  const sandbox = {
    document, CSS: { supports: () => color },
    matchMedia: query => ({ matches: reducedMotion && query.includes('reduced-motion') }),
    addEventListener() {}, setTimeout() { return 1; }, clearTimeout() {},
    performance: { now: () => 0 }, scrollY: 0
  };
  if (observer) sandbox.IntersectionObserver = class {
    constructor(fn) { this.fn = fn; }
    observe() {} unobserve() {}
  };
  Object.defineProperty(sandbox, 'localStorage', {
    get() {
      if (blocked) throw new Error('SecurityError: storage is blocked');
      return {
        getItem: key => storage.has(key) ? storage.get(key) : null,
        setItem: (key, value) => storage.set(key, String(value))
      };
    }
  });
  sandbox.window = sandbox;
  vm.runInNewContext(script, sandbox, { timeout: 1000 });
  return { get, root, storage, reveals };
}

test('blocked localStorage does not abort initialization or theme clicks', () => {
  const page = boot({ blocked: true });
  assert.equal(page.get('#swatches').children.length, 10);
  page.get('#themeBtn').listeners.click();
  assert.equal(page.root.style.colorScheme, 'dark');
});

test('zero-degree hue survives reload', () => {
  assert.equal(Number(boot({ saved: { 'yt-hue': '0' } }).get('#hueRange').value), 0);
});

test('corrupt or out-of-range hue falls back to 290', () => {
  for (const hue of ['NaN', 'Infinity', '-1', '361', '', ' ']) {
    assert.equal(Number(boot({ saved: { 'yt-hue': hue } }).get('#hueRange').value), 290);
  }
});

test('missing IntersectionObserver leaves content visible', () => {
  const page = boot({ observer: false });
  assert.ok(page.reveals.every(el => !el.classList.contains('pending')));
});

test('reduced-motion preference avoids reveal animations', () => {
  const page = boot({ reducedMotion: true });
  assert.ok(page.reveals.every(el => !el.classList.contains('pending')));
});

test('unsupported OKLCH uses CSS fallback instead of invalid palette values', () => {
  const page = boot({ color: false });
  assert.equal(page.root.style['--primary'], undefined);
  page.get('#themeBtn').listeners.click();
  assert.equal(page.root.dataset.theme, 'dark');
});

test('default system theme is not saved as an explicit user preference', () => {
  const page = boot();
  assert.equal(page.storage.has('yt-dark'), false);
  page.get('#themeBtn').listeners.click();
  assert.equal(page.storage.get('yt-dark'), '1');
});
