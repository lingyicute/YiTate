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
    dataset: {}, children: [], listeners: {}, attributes: {},
    setAttribute(name, value) { this.attributes[name] = String(value); },
    getAttribute(name) { return name in this.attributes ? this.attributes[name] : null; },
    focus() { this.focused = true; },
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

function boot({ saved = {}, blocked = false, observer = true, color = true, reducedMotion = false, schemeDark = false, mediaApi = 'event' } = {}) {
  const ids = new Map();
  const calls = [];
  const get = selector => {
    if (!ids.has(selector)) ids.set(selector, element());
    return ids.get(selector);
  };
  const root = element();
  const reveals = [element(), element()];
  const storage = new Map(Object.entries(saved));
  const document = {
    documentElement: root,
    querySelector(selector) { calls.push('query:' + selector); return get(selector); },
    querySelectorAll: selector => selector === '.swatch' ? get('#swatches').children : reveals,
    createElement: element,
    addEventListener(name, fn) { this.listeners[name] = fn; },
    listeners: {},
    fonts: { ready: Promise.resolve() }
  };
  const mediaListeners = [];
  const sandbox = {
    document, CSS: { supports: () => color },
    matchMedia: query => {
      const media = {
        query,
        matches: query.includes('reduced-motion') ? reducedMotion
          : (query.includes('prefers-color-scheme') ? schemeDark : false)
      };
      const listen = (name, fn) => { if (name === 'change') mediaListeners.push(fn); };
      if (mediaApi === 'event') media.addEventListener = listen;
      if (mediaApi === 'legacy') media.addListener = fn => listen('change', fn);
      return media;
    },
    addEventListener(name) { calls.push('window:' + name); }, setTimeout() { return 1; }, clearTimeout() {},
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
  return {
    get, root, storage, reveals, calls, document,
    fireMedia: matches => mediaListeners.forEach(fn => fn({ matches }))
  };
}

test('theme is resolved before the stylesheet paints', () => {
  const head = html.slice(0, html.indexOf('<style>'));
  const headScripts = [...head.matchAll(/<script>([\s\S]*?)<\/script>/g)];
  assert.equal(headScripts.length, 1, 'exactly one early theme script');
  assert.match(headScripts[0][1], /localStorage\.getItem\('yt-dark'\)/);
  assert.match(headScripts[0][1], /dataset\.theme/);
});

test('palette reports its open state and closes on Escape', () => {
  const page = boot();
  const trigger = page.get('#paletteBtn'), palette = page.get('#palette');
  assert.equal(trigger.attributes['aria-expanded'], 'false');
  trigger.listeners.click({ stopPropagation() {} });
  assert.equal(palette.classList.contains('open'), true);
  assert.equal(trigger.attributes['aria-expanded'], 'true');
  page.document.listeners.keydown({ key: 'Escape' });
  assert.equal(palette.classList.contains('open'), false);
  assert.equal(trigger.attributes['aria-expanded'], 'false');
  assert.equal(trigger.focused, true, 'focus returns to the trigger');
});

test('palette uses a labelled group rather than claiming to be a menu', () => {
  assert.match(html, /id="palette" role="group" aria-labelledby="paletteHeading"/);
  assert.match(html, /<h4 id="paletteHeading">/);
  assert.doesNotMatch(html, /aria-haspopup="true"/);
});

test('swatches retain a visible keyboard focus ring', () => {
  assert.match(html, /\.swatch:focus-visible\s*\{\s*outline-color:\s*var\(--on-surface\)/);
});

test('swatches carry a label and a single pressed state', () => {
  const swatches = boot().get('#swatches').children;
  assert.equal(swatches.length, 10);
  assert.equal(swatches[0].attributes['aria-label'], '色相 290°');
  assert.equal(swatches[0].attributes['aria-pressed'], 'true');
  assert.equal(swatches[1].attributes['aria-pressed'], 'false');
  swatches[3].listeners.click();
  assert.equal(swatches[3].attributes['aria-pressed'], 'true');
  assert.equal(swatches[0].attributes['aria-pressed'], 'false');
});

test('dragging the hue slider only stores the committed value', () => {
  const page = boot();
  const range = page.get('#hueRange');
  range.value = 33;
  range.listeners.input({ target: range });
  assert.equal(page.storage.has('yt-hue'), false, 'input events must not be persisted');
  range.listeners.change({ target: range });
  assert.equal(page.storage.get('yt-hue'), '33');
});

test('theme button keeps its name while exposing the selected state', () => {
  const page = boot();
  const button = page.get('#themeBtn');
  assert.equal(button.attributes['aria-pressed'], 'false');
  assert.equal(button.attributes['aria-label'], '深色模式');
  button.listeners.click();
  assert.equal(button.attributes['aria-pressed'], 'true');
  assert.equal(button.attributes['aria-label'], '深色模式');
});

test('system theme changes are followed until a theme is chosen explicitly', () => {
  const page = boot();
  page.fireMedia(true);
  assert.equal(page.root.dataset.theme, 'dark');
  assert.equal(page.storage.has('yt-dark'), false, 'following the system is not an explicit choice');

  const pinned = boot({ saved: { 'yt-dark': '0' } });
  pinned.fireMedia(true);
  assert.equal(pinned.root.dataset.theme, 'light');
});

test('manual theme choices stay selected when storage is blocked', () => {
  const page = boot({ blocked: true });
  page.get('#themeBtn').listeners.click();
  assert.equal(page.root.dataset.theme, 'dark');
  page.fireMedia(false);
  assert.equal(page.root.dataset.theme, 'dark', 'system changes must not override an in-memory choice');
});

test('legacy MediaQueryList.addListener receives system theme changes', () => {
  const page = boot({ mediaApi: 'legacy' });
  page.fireMedia(true);
  assert.equal(page.root.dataset.theme, 'dark');
});

test('hero screenshot declares its intrinsic size', () => {
  assert.match(html, /<img[^>]+screenshot-for-web\.webp[^>]+width="\d+"[^>]+height="\d+"/,
    'width/height keep the phone frame from jumping while the image decodes');
});

test('splash stays hidden until JavaScript starts and printing never shows it', () => {
  assert.match(html, /\.loading\s*\{[^}]*display:\s*none/, 'CSS hides the splash by default');
  assert.match(html, /\.loading\.show\s*\{\s*display:\s*grid/);
  assert.match(html, /<noscript>[\s\S]*?\.loading\s*\{\s*display:\s*none/, 'noscript hides the splash');
  assert.match(html, /@media print\s*\{[\s\S]*?\.loading[^}]*display:\s*none/,
    'print media hides the splash');
  assert.match(html, /@media print\s*\{[\s\S]*?\.reveal\.pending:not\(\.in\)\s*\{[^}]*opacity:\s*1/,
    'print media shows sections that were never scrolled into view');
});

test('splash failsafe is registered before widgets and the overlay is then enabled', () => {
  const page = boot();
  const load = page.calls.indexOf('window:load');
  const firstQuery = page.calls.findIndex(call => call.startsWith('query:'));
  assert.ok(load >= 0, 'the load handler is registered');
  assert.ok(firstQuery >= 0, 'the script did query the DOM');
  assert.ok(load < firstQuery, `load handler must come first, got ${page.calls.slice(0, 3).join(', ')}`);
  assert.equal(page.get('#loading').classList.contains('show'), true);
});

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
