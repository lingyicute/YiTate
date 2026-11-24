// Lightweight regression tests: execute the real inline script in a small DOM double.
// No browser download, server, npm package, or Android build is required.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const html = fs.readFileSync(process.env.INDEX_HTML || path.join(__dirname, '../web/index.html'), 'utf8');
const [earlyScript, script] = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);

function element() {
  const classes = new Set();
  return {
    style: {
      writes: [],
      getPropertyValue(key) { return String(this[key] ?? ''); },
      setProperty(key, value) { this[key] = value; this.writes.push([key, value]); }
    },
    dataset: {}, children: [], listeners: {}, attributes: {},
    setAttribute(name, value) { this.attributes[name] = String(value); },
    getAttribute(name) { return name in this.attributes ? this.attributes[name] : null; },
    focus() { this.focused = true; },
    closest() { return null; },
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
    removeEventListener(name, fn) { if (this.listeners[name] === fn) delete this.listeners[name]; },
    appendChild(child) { this.children.push(child); },
    contains(child) { return this === child || this.children.includes(child); },
    remove() { this.removed = true; }
  };
}

// A document/window can have several independent listeners for the same event.
function eventTarget() {
  const listeners = {}, callbacks = new Map();
  return {
    listeners,
    addEventListener(name, fn) {
      if (!callbacks.has(name)) {
        const handlers = new Set();
        callbacks.set(name, handlers);
        listeners[name] = (...args) => [...handlers].forEach(handler => handler(...args));
      }
      callbacks.get(name).add(fn);
    },
    removeEventListener(name, fn) {
      const handlers = callbacks.get(name);
      if (!handlers) return;
      handlers.delete(fn);
      if (!handlers.size) { callbacks.delete(name); delete listeners[name]; }
    }
  };
}

function boot({
  saved = {}, blocked = false, observer = true, color = true,
  reducedMotion = false, schemeDark = false, mediaApi = 'event',
  scrollY = 0, visibilityState = 'visible', animationFrame = true
} = {}) {
  const ids = new Map();
  const calls = [];
  const timeouts = [];
  const get = selector => {
    if (!ids.has(selector)) {
      const node = element();
      if (selector === '#themeBtn') node.setAttribute('aria-label', '深色模式');
      node.hidden = selector === '#themeBtn' || selector === '#paletteControls';
      ids.set(selector, node);
    }
    return ids.get(selector);
  };
  const root = element();
  const reveals = [element(), element()];
  const storage = new Map(Object.entries(saved));
  const document = {
    ...eventTarget(), visibilityState,
    documentElement: root,
    querySelector(selector) { calls.push('query:' + selector); return get(selector); },
    querySelectorAll: selector => selector === '.swatch' ? get('#swatches').children : reveals,
    createElement: element
  };
  const mediaListeners = [], motionListeners = [];
  const windowEvents = eventTarget();
  const sandbox = {
    ...windowEvents, document, CSS: { supports: () => color },
    addEventListener(name, fn) {
      calls.push('window:' + name);
      windowEvents.addEventListener(name, fn);
    },
    setTimeout(fn, delay) { timeouts.push({ fn, delay }); return timeouts.length; },
    clearTimeout(id) { if (timeouts[id - 1]) timeouts[id - 1].cancelled = true; },
    scrollY
  };
  if (mediaApi !== 'none') {
    sandbox.matchMedia = query => {
      const isMotion = query.includes('reduced-motion');
      const list = isMotion ? motionListeners : mediaListeners;
      const media = {
        query,
        matches: isMotion ? reducedMotion
          : (query.includes('prefers-color-scheme') ? schemeDark : false)
      };
      const listen = (name, fn) => { if (name === 'change') list.push({ media, fn }); };
      if (mediaApi === 'event') media.addEventListener = listen;
      if (mediaApi === 'legacy') media.addListener = fn => listen('change', fn);
      return media;
    };
  }
  const frames = new Map();
  let frameId = 0;
  if (animationFrame) {
    sandbox.requestAnimationFrame = fn => { frames.set(++frameId, fn); return frameId; };
    sandbox.cancelAnimationFrame = id => frames.delete(id);
  }
  const observers = [];
  if (observer) sandbox.IntersectionObserver = class {
    constructor(fn, options) { this.fn = fn; this.options = options; this.observed = []; observers.push(this); }
    observe(target) { this.observed.push(target); }
    unobserve(target) { this.unobserved = target; }
    disconnect() { this.disconnected = true; }
  };
  const localStorage = {
    getItem: key => storage.has(key) ? storage.get(key) : null,
    setItem: (key, value) => storage.set(key, String(value))
  };
  Object.defineProperty(sandbox, 'localStorage', {
    get() {
      if (blocked) throw new Error('SecurityError: storage is blocked');
      return localStorage;
    }
  });
  sandbox.window = sandbox;
  vm.runInNewContext(earlyScript, sandbox, { timeout: 1000 });
  vm.runInNewContext(script, sandbox, { timeout: 1000 });
  return {
    get, root, storage, reveals, observers, calls, timeouts, document, window: sandbox, frames,
    flushFrames() {
      const pending = [...frames.values()];
      frames.clear();
      pending.forEach(fn => fn(16));
    },
    fireMedia: matches => mediaListeners.forEach(({ media, fn }) => { media.matches = matches; fn({ matches }); }),
    fireMotion: matches => motionListeners.forEach(({ media, fn }) => { media.matches = matches; fn({ matches }); })
  };
}

test('theme is resolved before the stylesheet paints', () => {
  const head = html.slice(0, html.indexOf('<style>'));
  const headScripts = [...head.matchAll(/<script>([\s\S]*?)<\/script>/g)];
  assert.equal(headScripts.length, 1, 'exactly one early theme script');
  const runEarly = ({ stored = null, hue = null, blocked = false, schemeDark = false, hasMedia = true } = {}) => {
    const root = element();
    const ctx = {
      document: { documentElement: root },
      matchMedia: hasMedia ? () => ({ matches: schemeDark }) : undefined
    };
    Object.defineProperty(ctx, 'localStorage', {
      get() {
        if (blocked) throw new Error('blocked');
        return { getItem: key => key === 'yt-dark' ? stored : hue };
      }
    });
    ctx.window = ctx;
    vm.runInNewContext(headScripts[0][1], ctx, { timeout: 1000 });
    return { theme: root.dataset.theme, hue: root.style.getPropertyValue('--hue') };
  };
  assert.deepEqual(runEarly({ schemeDark: true }), { theme: 'dark', hue: '' });
  assert.deepEqual(runEarly({ stored: '0', schemeDark: true, hue: '0' }), { theme: 'light', hue: '0' });
  assert.deepEqual(runEarly({ stored: '1', schemeDark: false, hue: '350' }), { theme: 'dark', hue: '350' });
  assert.deepEqual(runEarly({ blocked: true, hasMedia: false }), { theme: 'light', hue: '' });
  assert.equal(runEarly({ hue: '361' }).hue, '');
  assert.match(html, /--primary: oklch\(\.48 \.16 var\(--hue\)\)/);
  assert.match(html, /--primary: oklch\(\.82 \.10 var\(--hue\)\)/);
});

test('the system dark palette also applies without scripts, but not to an explicit choice', () => {
  const explicit = html.match(/:root\[data-theme="dark"\]\s*\{([^}]+)\}/);
  const fallback = html.match(/@media\s*\(prefers-color-scheme:\s*dark\)\s*\{\s*:root:not\(\[data-theme\]\)\s*\{([^}]+)\}/);
  assert.ok(explicit && fallback);
  assert.equal(fallback[1].replace(/\s+/g, ''), explicit[1].replace(/\s+/g, ''));
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

test('palette stays inside narrow viewports', () => {
  const mobile = html.match(/@media \(max-width: 560px\)\s*\{([\s\S]*?)\n  \}/)[1];
  assert.match(mobile, /\.palette-wrap\s*\{\s*position:\s*static/);
  assert.match(mobile, /\.palette\s*\{[^}]*width:\s*min\(240px,\s*92vw\)/);
});

test('palette scrolls independently when the viewport is too short', () => {
  const styles = html.match(/\.palette\s*\{([^}]*)\}/)[1];
  assert.match(styles, /max-height:\s*calc\(100vh - var\(--appbar-height\) - 8px\)/);
  assert.match(styles, /max-height:\s*calc\(100dvh - var\(--appbar-height\) - 8px\)/);
  assert.match(styles, /overflow-y:\s*auto/);
  assert.match(html, /\.palette > \*\s*\{\s*flex:\s*none/);
});

test('snackbar messages are announced to assistive technology and hidden when dismissed', () => {
  assert.match(html, /<div class="snack" id="snack" role="status" aria-live="polite"[^>]*><\/div>/);
  assert.match(html, /\.snack\s*\{[^}]*opacity:\s*0;\s*pointer-events:\s*none;/);
  assert.match(html, /\.snack\.show\s*\{[^}]*opacity:\s*1;/);
  const page = boot();
  page.get('#swatches').children[0].listeners.click();
  const snack = page.get('#snack');
  assert.equal(snack.textContent, '主题色已更新 ✦');
  page.timeouts.find(t => t.delay === 2200).fn();
  assert.equal(snack.classList.contains('show'), false);
  assert.equal(snack.textContent, '主题色已更新 ✦', 'text stays while the 400ms exit transition runs');
  page.timeouts.find(t => t.delay === 450).fn();
  assert.equal(snack.textContent, '');
});

test('a new snackbar cancels the pending text clear of the previous one', () => {
  const page = boot();
  const swatches = page.get('#swatches').children;
  swatches[0].listeners.click();
  page.timeouts.find(t => t.delay === 2200).fn();
  const clear = page.timeouts.find(t => t.delay === 450);
  swatches[1].listeners.click();
  assert.equal(clear.cancelled, true, 'the stale clear must not wipe the new message');
  assert.equal(page.get('#snack').classList.contains('show'), true);
});

test('app bar reflects an already-scrolled document on initial load', () => {
  assert.equal(boot({ scrollY: 0 }).get('#appbar').classList.contains('scrolled'), false);
  assert.equal(boot({ scrollY: 24 }).get('#appbar').classList.contains('scrolled'), true);
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
  range.value = 250;
  range.listeners.change({ target: range });
  assert.equal(page.storage.get('yt-hue'), '250');
  assert.equal(page.root.style['--hue'], 250);
  assert.equal(page.get('#swatches').children[1].attributes['aria-pressed'], 'true');
});

test('closing the palette or tabbing away commits an in-progress hue drag', () => {
  const page = boot();
  const trigger = page.get('#paletteBtn'), palette = page.get('#palette'), range = page.get('#hueRange');
  trigger.listeners.click({ stopPropagation() {} });
  range.value = 145;
  range.listeners.input({ target: range });
  assert.equal(page.storage.has('yt-hue'), false);
  page.document.listeners.focusin({ target: page.get('#themeBtn') });
  assert.equal(palette.classList.contains('open'), false);
  assert.equal(page.storage.get('yt-hue'), '145');
});

test('theme button keeps its name while exposing the selected state and action tooltip', () => {
  assert.match(html, /<button type="button"[^>]+id="paletteBtn"/);
  assert.match(html, /<button type="button"[^>]+id="themeBtn"/);
  const page = boot();
  const button = page.get('#themeBtn');
  assert.equal(page.get('#swatches').children[0].type, 'button');
  assert.equal(button.attributes['aria-pressed'], 'false');
  assert.equal(button.attributes['aria-label'], '深色模式');
  assert.equal(button.title, '切换到深色模式');
  button.listeners.click();
  assert.equal(button.attributes['aria-pressed'], 'true');
  assert.equal(button.attributes['aria-label'], '深色模式');
  assert.equal(button.title, '切换到浅色模式');
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

test('public content does not wait for the page load event or fonts', () => {
  const page = boot();
  assert.doesNotMatch(html, /id="loading"/);
  assert.equal(page.window.listeners.load, undefined);
  assert.equal(page.timeouts.length, 0, 'no timer is needed before the page can be used');
  assert.match(html, /class="hero-cta"\s*>\s*<a[^>]+href="https:\/\/github.com\/lingyicute\/YiTate\/releases\/latest"/);
});

test('download decorations cannot intercept release links', () => {
  assert.match(html, /\.download::before,\s*\.download::after\s*\{[^}]*pointer-events:\s*none/);
});

test('printing hides popovers and shows unvisited content', () => {
  assert.match(html, /@media print\s*\{[\s\S]*?\.snack,\s*\.palette\s*\{\s*display:\s*none/);
  // Check where the hiding rule applies, not merely the presence of a weaker override.
  assert.match(html, /@media screen and \(prefers-reduced-motion: no-preference\)\s*\{\s*\.reveal\.pending:not\(\.in\):not\(:focus-within\)\s*\{\s*opacity:\s*0/);
  assert.equal((html.match(/\.reveal\.pending[^{}]*\{\s*opacity:\s*0/g) || []).length, 1);
  assert.match(html, /@media print\s*\{[^@]*?\.reveal\s*\{\s*transition:\s*none !important/);
});

test('missing matchMedia does not abort initialization', () => {
  const page = boot({ mediaApi: 'none' });
  assert.equal(page.root.dataset.theme, 'light');
  page.get('#themeBtn').listeners.click();
  assert.equal(page.root.dataset.theme, 'dark');
});

test('blocked localStorage does not abort initialization or theme clicks', () => {
  const page = boot({ blocked: true });
  assert.equal(page.get('#swatches').children.length, 10);
  page.get('#themeBtn').listeners.click();
  assert.equal(page.root.dataset.theme, 'dark');
});

test('zero-degree hue survives reload', () => {
  assert.equal(Number(boot({ saved: { 'yt-hue': '0' } }).get('#hueRange').value), 0);
});

test('corrupt or out-of-range hue falls back to 290', () => {
  for (const hue of ['NaN', 'Infinity', '-1', '361', '25.5', '', ' ']) {
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
  assert.match(html, /@media \(prefers-reduced-motion: reduce\)\s*\{[\s\S]*?transition-delay:\s*0s !important/);
});

test('unsupported OKLCH keeps the theme toggle but does not offer an inert palette', () => {
  const page = boot({ color: false });
  assert.equal(page.root.style.getPropertyValue('--hue'), '');
  assert.equal(page.get('#paletteControls').hidden, true);
  assert.equal(page.get('#swatches').children.length, 0);
  assert.equal(page.get('#themeBtn').hidden, false);
  assert.match(html, /\.appbar\s*\{[\s\S]*?background:\s*var\(--surface\);\s*background:\s*color-mix/);
  assert.match(html, /\.layer\.l3\s*\{[^}]*background:\s*var\(--primary\);\s*background:\s*color-mix/);
  assert.match(html, /\.download \.btn-outlined\s*\{[^}]*box-shadow:\s*inset 0 0 0 1\.5px var\(--on-primary\);\s*box-shadow:\s*inset 0 0 0 1\.5px color-mix/);
  page.get('#themeBtn').listeners.click();
  assert.equal(page.root.dataset.theme, 'dark');
});

test('theme enhancements become visible only after they initialize', () => {
  assert.match(html, /id="themeBtn" hidden/);
  assert.match(html, /id="paletteControls" hidden/);
  assert.match(html, /\.nav \[hidden\]\s*\{\s*display:\s*none/);
  const page = boot();
  assert.equal(page.get('#themeBtn').hidden, false);
  assert.equal(page.get('#paletteControls').hidden, false);
});

test('default system theme is not saved as an explicit user preference', () => {
  const page = boot();
  assert.equal(page.storage.has('yt-dark'), false);
  page.get('#themeBtn').listeners.click();
  assert.equal(page.storage.get('yt-dark'), '1');
});

test('revealed cards and steps keep their hover transitions', () => {
  assert.match(html, /\.card\.reveal\s*\{\s*transition:[^;}]*opacity[^;}]*transform[^;}]*box-shadow[^;}]*border-radius/);
  assert.match(html, /\.step\.reveal\s*\{\s*transition:[^;}]*opacity[^;}]*background[^;}]*transform/);
  assert.ok(html.indexOf('.card.reveal {') > html.indexOf('.reveal { transition'),
    'the combined rules must follow the generic .reveal rule');
});

test('an in-progress hue drag is committed when the tab is hidden', () => {
  const page = boot();
  const range = page.get('#hueRange');
  range.value = 200;
  range.listeners.input({ target: range });
  page.document.visibilityState = 'visible';
  page.document.listeners.visibilitychange();
  assert.equal(page.storage.has('yt-hue'), false, 'a visible tab keeps the drag uncommitted');
  page.document.visibilityState = 'hidden';
  page.document.listeners.visibilitychange();
  assert.equal(page.storage.get('yt-hue'), '200');
});

test('the reveal stagger delay is dropped once the fade has run', () => {
  assert.match(html, /\.reveal\.pending\s*\{\s*transition-delay:\s*var\(--reveal-delay,\s*0s\)/);
  assert.doesNotMatch(html, /style="transition-delay:/, 'an inline delay would also postpone hover transitions');
  const page = boot();
  const [target] = page.reveals;
  assert.equal(target.classList.contains('pending'), true);
  page.observers[0].fn([{ isIntersecting: false, target }]);
  assert.equal(target.classList.contains('in'), false);
  page.observers[0].fn([{ isIntersecting: true, target }]);
  assert.equal(target.classList.contains('in'), true);
  const safety = page.timeouts.find(t => t.delay === 1200);
  assert.ok(safety, 'a missing transition event has a bounded cleanup');
  assert.equal(target.classList.contains('pending'), true, 'the delay still applies while fading in');
  target.listeners.transitionend({ target: element(), propertyName: 'opacity' });
  target.listeners.transitionend({ target, propertyName: 'transform' });
  assert.equal(target.classList.contains('pending'), true, 'child or non-opacity transitions are ignored');
  target.listeners.transitionend({ target, propertyName: 'opacity' });
  assert.equal(target.classList.contains('pending'), false);
  assert.equal(target.classList.contains('in'), true);
  assert.equal(safety.cancelled, true, 'normal completion cancels the fallback');
  assert.equal(target.listeners.transitionend, undefined);
  assert.equal(target.listeners.transitioncancel, undefined);
});

test('keyboard focus immediately settles a pending reveal', () => {
  assert.match(html, /\.reveal\.pending:not\(\.in\):not\(:focus-within\)\s*\{[^}]*opacity:\s*0/);
  assert.match(html, /\.reveal:focus-within\s*\{\s*transition:\s*none/);
  const page = boot(), [target] = page.reveals;
  page.observers[0].fn([{ target, isIntersecting: true }]);
  const child = element();
  child.closest = selector => selector === '.reveal.pending' ? target : null;
  page.document.listeners.focusin({ target: child });
  assert.equal(target.classList.contains('pending'), false);
  assert.equal(page.observers[0].unobserved, target);
  assert.equal(page.timeouts.find(t => t.delay === 1200).cancelled, true);
});

test('the skip link and anchor navigation account for the sticky app bar', () => {
  assert.match(html, /<a class="skip-link" href="#main">/);
  assert.match(html, /<main id="main" tabindex="-1">/);
  assert.match(html, /scroll-padding-top:\s*var\(--appbar-height\)/);
});

test('the unfocused skip link is parked clear of the viewport edge', () => {
  const rule = html.match(/\.skip-link\s*\{([^}]*)\}/)[1];
  const inset = +rule.match(/inset-block-start:\s*(\d+)px/)[1];
  const parked = +rule.match(/transform:\s*translateY\(calc\(-100% - (\d+)px\)\)/)[1];
  // Its height is fractional (1.6 line-height); moving it by exactly the inset leaves a hairline.
  assert.ok(parked > inset, 'sub-pixel rounding must not expose the bottom edge');
  assert.match(html, /\.skip-link:focus\s*\{\s*transform:\s*none/);
});

test('enabling reduced motion after load disconnects the observer and clears pending reveals', () => {
  const page = boot();
  const [first, second] = page.reveals;
  page.observers[0].fn([{ isIntersecting: true, target: first }]);
  assert.equal(first.classList.contains('pending'), true);
  assert.ok(first.listeners.transitionend);
  const safety = page.timeouts.find(t => t.delay === 1200);
  page.fireMotion(true);
  assert.equal(page.observers[0].disconnected, true);
  assert.equal(safety.cancelled, true, 'preference changes cancel pending fallbacks');
  assert.equal(first.classList.contains('pending'), false);
  assert.equal(second.classList.contains('pending'), false);
  assert.equal(first.listeners.transitionend, undefined);
  assert.equal(first.listeners.transitioncancel, undefined);
});

test('decorative hero motion stops automatically within five seconds', () => {
  const duration = html.match(/\.blob\s*\{[^}]*animation:\s*drift\s+([\d.]+)s\s+ease-in-out\s+both\s*;/);
  assert.ok(duration, 'the drift has a finite duration and one iteration');
  assert.ok(+duration[1] > 0 && +duration[1] <= 5);
});

test('the hero does not cut its blurred blobs along a hard horizontal edge', () => {
  const modern = html.match(/@supports \(overflow: clip\)\s*\{\s*\.hero\s*\{([^}]*)\}/);
  assert.ok(modern, 'browsers with overflow: clip let the glow fade out past the section');
  assert.match(modern[1], /overflow-x:\s*clip/, 'blobs must still not widen the page');
  assert.match(modern[1], /overflow-y:\s*visible/);
  assert.ok(html.indexOf('@supports (overflow: clip)') > html.search(/\n  \.hero\s*\{[^}]*overflow:\s*hidden/),
    'the override follows the fallback for older browsers');
});

test('hero drift pauses offscreen and in hidden tabs without restarting its animation', () => {
  assert.match(html, /\.hero\.motion-paused \.blob\s*\{\s*animation-play-state:\s*paused/);
  assert.match(html, /class="blob b1" aria-hidden="true"/);
  assert.match(html, /class="blob b2" aria-hidden="true"/);
  const page = boot(), hero = page.get('.hero');
  const observer = page.observers.find(item => item.observed.includes(hero));
  assert.ok(observer, 'the hero is observed separately from one-shot reveals');
  assert.equal(observer.options.threshold, 0);
  assert.equal(hero.classList.contains('motion-paused'), true, 'wait for the first intersection');
  observer.fn([{ target: hero, isIntersecting: true }]);
  assert.equal(hero.classList.contains('motion-paused'), false);
  observer.fn([{ target: hero, isIntersecting: false }]);
  assert.equal(hero.classList.contains('motion-paused'), true);
  page.document.visibilityState = 'hidden';
  page.document.listeners.visibilitychange();
  observer.fn([{ target: hero, isIntersecting: true }]);
  assert.equal(hero.classList.contains('motion-paused'), true, 'visibility overrides intersection');
  page.document.visibilityState = 'visible';
  page.document.listeners.visibilitychange();
  assert.equal(hero.classList.contains('motion-paused'), false);
  observer.fn([{ target: hero, isIntersecting: false }, { target: hero, isIntersecting: true }]);
  assert.equal(hero.classList.contains('motion-paused'), false, 'the latest queued entry wins');
});

test('pagehide and BFCache restoration preserve motion gating and hue commits', () => {
  const page = boot(), hero = page.get('.hero'), range = page.get('#hueRange');
  const observer = page.observers.find(item => item.observed.includes(hero));
  observer.fn([{ target: hero, isIntersecting: true }]);
  range.value = 123;
  range.listeners.input({ target: range });
  page.window.listeners.pagehide({ persisted: true });
  assert.equal(page.storage.get('yt-hue'), '123', 'the existing pagehide listener still runs');
  assert.equal(hero.classList.contains('motion-paused'), true);
  page.document.visibilityState = 'hidden';
  page.window.listeners.pageshow({ persisted: true });
  assert.equal(hero.classList.contains('motion-paused'), true, 'a restored hidden tab stays paused');
  page.document.visibilityState = 'visible';
  page.document.listeners.visibilitychange();
  assert.equal(hero.classList.contains('motion-paused'), false);
  observer.fn([{ target: hero, isIntersecting: false }]);
  page.window.listeners.pagehide({ persisted: true });
  page.window.listeners.pageshow({ persisted: true });
  assert.equal(hero.classList.contains('motion-paused'), true, 'offscreen stays paused after restore');
});

test('hero motion degrades safely without an observer or with reduced motion', () => {
  const page = boot({ observer: false, visibilityState: 'hidden' });
  const hero = page.get('.hero');
  assert.equal(hero.classList.contains('motion-paused'), true);
  page.document.visibilityState = 'visible';
  page.document.listeners.visibilitychange();
  assert.equal(hero.classList.contains('motion-paused'), false, 'visible fallback remains animated');
  page.window.listeners.pagehide({ persisted: false });
  assert.equal(hero.classList.contains('motion-paused'), true);
  page.window.listeners.pageshow({ persisted: true });
  assert.equal(hero.classList.contains('motion-paused'), false);
  assert.match(html, /@media \(prefers-reduced-motion: reduce\)\s*\{[\s\S]*?animation:\s*none !important/);
});

test('hue input coalesces palette and swatch updates into one render per frame', () => {
  const page = boot(), range = page.get('#hueRange');
  const writes = () => page.root.style.writes.filter(([key]) => key === '--hue').length;
  assert.equal(writes(), 1, 'initialization paints the palette only once');
  assert.equal(page.root.style.getPropertyValue('--hue'), '290');
  for (const value of [25, 170, 250]) {
    range.value = value;
    range.listeners.input({ target: range });
  }
  assert.equal(page.frames.size, 1);
  assert.equal(writes(), 1, 'events do not synchronously rewrite the palette');
  assert.equal(page.storage.has('yt-hue'), false);
  page.flushFrames();
  assert.equal(writes(), 2);
  assert.equal(page.root.style['--hue'], 250, 'the last input wins');
  assert.equal(page.get('#swatches').children[1].attributes['aria-pressed'], 'true');
  range.listeners.change({ target: range });
  assert.equal(page.storage.get('yt-hue'), '250');
  assert.equal(writes(), 2, 'committing an already rendered value does not paint again');
});

test('commits, swatches, and theme changes supersede a queued hue frame', () => {
  const page = boot(), range = page.get('#hueRange');
  range.value = 140;
  range.listeners.input({ target: range });
  range.value = 90;
  range.listeners.change({ target: range });
  assert.equal(page.frames.size, 0);
  assert.equal(page.root.style['--hue'], 90);
  assert.equal(page.storage.get('yt-hue'), '90');
  range.value = 170;
  range.listeners.input({ target: range });
  page.get('#themeBtn').listeners.click();
  assert.equal(page.frames.size, 0);
  assert.equal(page.root.dataset.theme, 'dark');
  assert.equal(page.root.style.getPropertyValue('--hue'), '170');
  assert.equal(page.get('#swatches').children[3].attributes['aria-pressed'], 'true');
  range.value = 50;
  range.listeners.input({ target: range });
  page.get('#swatches').children[2].listeners.click();
  page.flushFrames();
  assert.equal(page.root.style['--hue'], 210);
  assert.equal(page.storage.get('yt-hue'), '210', 'stale frame cannot overwrite a swatch');
});

test('hidden pages commit and flush a pending hue before they can be frozen', () => {
  const page = boot(), range = page.get('#hueRange');
  range.value = 320;
  range.listeners.input({ target: range });
  page.document.visibilityState = 'hidden';
  page.document.listeners.visibilitychange();
  assert.equal(page.storage.get('yt-hue'), '320');
  assert.equal(page.root.style['--hue'], 320);
  assert.equal(page.frames.size, 0, 'no stale frame can be lost in the back/forward cache');
  page.document.visibilityState = 'visible';
  page.document.listeners.visibilitychange();
  range.value = 350;
  range.listeners.input({ target: range });
  assert.equal(page.frames.size, 1, 'later interactions still schedule a frame');
  page.flushFrames();
  assert.equal(page.root.style['--hue'], 350);
});

test('hue inputs still render immediately without animation-frame APIs', () => {
  const page = boot({ animationFrame: false }), range = page.get('#hueRange');
  range.value = 350;
  range.listeners.input({ target: range });
  assert.equal(page.root.style['--hue'], 350);
  assert.equal(page.get('#swatches').children[8].attributes['aria-pressed'], 'true');
  assert.equal(page.storage.has('yt-hue'), false);
  page.window.listeners.pagehide({ persisted: true });
  assert.equal(page.storage.get('yt-hue'), '350');
});

test('reveal fallback clears pending state when no transition event is dispatched', () => {
  const page = boot(), target = page.reveals[0];
  page.observers[0].fn([{ target, isIntersecting: true }]);
  const safety = page.timeouts.find(t => t.delay === 1200);
  assert.ok(safety);
  safety.fn();
  assert.equal(target.classList.contains('pending'), false);
  assert.equal(target.classList.contains('in'), true);
  assert.equal(target.listeners.transitionend, undefined);
  assert.equal(target.listeners.transitioncancel, undefined);
});

test('duplicate and stale reveal observer entries do not register extra cleanups', () => {
  const page = boot(), [first, second] = page.reveals;
  page.observers[0].fn([{ target: first, isIntersecting: true }, { target: first, isIntersecting: true }]);
  assert.equal(page.timeouts.filter(t => t.delay === 1200).length, 1);
  page.fireMotion(true);
  page.fireMotion(false);
  page.observers[0].fn([{ target: second, isIntersecting: true }]);
  assert.equal(second.classList.contains('in'), false, 'a queued entry after disconnect is ignored');
  assert.equal(page.timeouts.filter(t => t.delay === 1200).length, 1);
});


test('theme changes in another tab update this tab without writing back', () => {
  const page = boot({ schemeDark: true });
  const area = page.window.localStorage;
  page.storage.set('yt-dark', '0');
  page.window.listeners.storage({ key: 'yt-dark', storageArea: {} });
  assert.equal(page.root.dataset.theme, 'dark', 'ignore session storage');
  page.window.listeners.storage({ key: 'yt-dark', storageArea: area, newValue: '1' });
  assert.equal(page.root.dataset.theme, 'light', 'read the latest stored choice');
  assert.equal(page.get('#themeBtn').getAttribute('aria-pressed'), 'false');
  page.storage.clear();
  page.window.listeners.storage({ key: null, storageArea: area });
  assert.equal(page.root.dataset.theme, 'dark');
  page.fireMedia(false);
  assert.equal(page.root.dataset.theme, 'light');
  assert.equal(page.storage.has('yt-dark'), false);
});


test('external hue changes reconcile swatches without cancelling a local drag', () => {
  const page = boot();
  const range = page.get('#hueRange');
  const change = key => page.window.listeners.storage({ key, storageArea: page.window.localStorage });
  page.storage.set('yt-hue', '0');
  change('yt-hue');
  assert.equal(page.root.style.getPropertyValue('--hue'), '0');
  page.storage.set('yt-hue', 'bad');
  change('yt-hue');
  assert.equal(Number(range.value), 290);
  assert.equal(page.get('#swatches').children[0].getAttribute('aria-pressed'), 'true');
  range.value = 123;
  range.listeners.input({ target: range });
  page.storage.set('yt-hue', '350');
  change('yt-hue');
  page.flushFrames();
  assert.equal(page.root.style.getPropertyValue('--hue'), '123');
  range.listeners.change({ target: range });
  assert.equal(page.storage.get('yt-hue'), '123');
  page.storage.clear();
  change(null);
  assert.equal(page.root.style.getPropertyValue('--hue'), '290');
  assert.equal(page.storage.has('yt-hue'), false);
});

test('a page restored from the back/forward cache adopts choices saved in the meantime', () => {
  const page = boot();
  page.storage.set('yt-dark', '1');
  page.storage.set('yt-hue', '170');
  page.window.listeners.pageshow({ persisted: false });
  assert.equal(page.root.dataset.theme, 'light', 'an ordinary load has already read storage');
  page.window.listeners.pageshow({ persisted: true });
  assert.equal(page.root.dataset.theme, 'dark');
  assert.equal(page.root.style.getPropertyValue('--hue'), '170');
  assert.equal(page.get('#swatches').children[3].getAttribute('aria-pressed'), 'true');

  const blocked = boot({ blocked: true });
  blocked.get('#themeBtn').listeners.click();
  blocked.window.listeners.pageshow({ persisted: true });
  assert.equal(blocked.root.dataset.theme, 'dark', 'unreadable storage keeps the in-memory choice');
});

test('the download note keeps its own size next to the generic download paragraph rule', () => {
  assert.match(html, /\.download \.dl-note\s*\{[^}]*font-size:\s*\.85rem/);
  assert.doesNotMatch(html, /\n\s*\.dl-note\s*\{/, '`.download p` outranks a bare class');
});

// OKLCH to gamma-encoded sRGB, clipped to the gamut the way browsers paint it.
function srgb(lightness, chroma, hue) {
  const a = chroma * Math.cos(hue * Math.PI / 180), b = chroma * Math.sin(hue * Math.PI / 180);
  const l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (lightness - 0.0894841775 * a - 1.2914855480 * b) ** 3;
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
  ].map(v => Math.min(1, Math.max(0, v))).map(v => v <= 0.0031308 ? 12.92 * v : 1.055 * v ** (1 / 2.4) - 0.055);
}
const blend = (front, back, alpha) => front.map((v, i) => v * alpha + back[i] * (1 - alpha));
function contrast(first, second) {
  const luminance = color => color.map(v => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)
    .reduce((sum, v, i) => sum + v * [0.2126, 0.7152, 0.0722][i], 0);
  const [dim, bright] = [luminance(first), luminance(second)].sort((x, y) => x - y);
  return (bright + 0.05) / (dim + 0.05);
}

test('text on the primary tone keeps WCAG AA contrast at every selectable hue', () => {
  const tone = name => [...html.matchAll(new RegExp(`--${name}: oklch\\(([\\d.]+) ([\\d.]+) var\\(--hue\\)\\)`, 'g'))]
    .map(match => [+match[1], +match[2]]);
  const primary = tone('primary'), onPrimary = tone('on-primary'), backdrop = tone('surface-container-low');
  assert.deepEqual([primary.length, onPrimary.length, backdrop.length], [2, 2, 2], 'light and dark tones');
  const lead = +html.match(/\.download p\s*\{\s*opacity:\s*([\d.]+)/)[1];
  const note = +html.match(/\.download \.dl-note\s*\{[^}]*opacity:\s*([\d.]+)/)[1];
  const tint = html.match(/\.dl-note code\s*\{[^}]*var\(--on-primary\) (\d+)%, transparent/)[1] / 100;
  const layer = html.match(/\.layer\.l3\s*\{[^}]*var\(--primary\) (\d+)%, transparent/)[1] / 100;
  for (const theme of [0, 1]) {
    for (let hue = 0; hue <= 360; hue += 5) {
      const fill = srgb(...primary[theme], hue), text = srgb(...onPrimary[theme], hue);
      const chip = blend(text, fill, tint * note), overlay = blend(fill, srgb(...backdrop[theme], hue), layer);
      const where = `${theme ? 'dark' : 'light'} theme, hue ${hue}`;
      assert.ok(contrast(blend(text, fill, lead), fill) >= 4.5, `download lead: ${where}`);
      assert.ok(contrast(blend(text, fill, note), fill) >= 4.5, `download note: ${where}`);
      assert.ok(contrast(blend(text, chip, note), chip) >= 4.5, `permission chip: ${where}`);
      assert.ok(contrast(text, overlay) >= 4.5, `overlay layer label: ${where}`);
    }
  }
});

test('layer diagram labels stay readable on touch screens that cannot hover', () => {
  const layer = html.match(/\n  \.layer\s*\{([^}]*)\}/)[1];
  assert.match(layer, /align-items:\s*flex-end/, 'a centred label is covered by the layer above');
  assert.match(layer, /padding-bottom:\s*\d+px/);
});

test('the hero chip dot does not shrink when the label wraps on narrow screens', () => {
  const dot = html.match(/\.chip \.dot\s*\{([^}]*)\}/)[1];
  assert.match(dot, /flex:\s*none|flex-shrink:\s*0/);
});

test('body prefers overflow-x clip so sticky positioning is not affected', () => {
  assert.match(html, /@supports \(overflow: clip\)\s*\{\s*body\s*\{\s*overflow-x:\s*clip;/);
});

test('the layer diagram scales and stays contained on narrow screens', () => {
  const layers = html.match(/\n  \.layers\s*\{([^}]*)\}/)[1];
  assert.match(layers, /overflow:\s*hidden/);
  const mobile = html.match(/@media \(max-width: 560px\)\s*\{([\s\S]*?)\n  \}/)[1];
  assert.match(mobile, /\.layer\s*\{[^}]*width:\s*184px/);
});

test('footer note and links align with the start edge on narrow screens', () => {
  const mobile = html.match(/@media \(max-width: 560px\)\s*\{([\s\S]*?)\n  \}/)[1];
  assert.match(mobile, /\.foot \.foot-end\s*\{\s*align-items:\s*flex-start/);
  assert.match(mobile, /\.foot \.foot-note\s*\{[^}]*text-align:\s*left/);
});

test('reveal observer triggers with minimal intersection for short viewports', () => {
  const page = boot();
  assert.ok(page.observers[0].options.threshold <= 0.01);
});

test('reveal focus handler ignores targets without closest()', () => {
  const page = boot();
  assert.doesNotThrow(() => {
    page.document.listeners.focusin({ target: null });
    page.document.listeners.focusin({ target: {} });
  });
});
