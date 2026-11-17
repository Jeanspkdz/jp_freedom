// Run manually with: node test/browser-transition-smoke.mjs
// Checks MPA View Transitions in a real Chrome process with this unpacked extension.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const extension = resolve(import.meta.dirname, '..');
const profile = await mkdtemp(join(tmpdir(), 'dimky-transition-test-'));
const browserCache = join(process.env.LOCALAPPDATA ?? '', 'ms-playwright');
const installed = await readdir(browserCache).catch(() => []);
const playwrightChromium = installed.filter(name => /^chromium-\d+$/.test(name))
  .map(name => join(browserCache, name, 'chrome-win64', 'chrome.exe')).find(existsSync);
const chromePath = process.env.CHROME_PATH ?? playwrightChromium;
if (!chromePath) throw new Error('Set CHROME_PATH to a Chromium or Chrome for Testing executable.');
const chrome = spawn(chromePath, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=0', `--user-data-dir=${profile}`,
  `--disable-extensions-except=${extension}`, `--load-extension=${extension}`, 'about:blank',
], { windowsHide: true, stdio: 'ignore' });

const waitFor = async (read, timeout = 20_000) => {
  const until = Date.now() + timeout;
  while (Date.now() < until) {
    try { const result = await read(); if (result) return result; } catch { /* Chrome is starting. */ }
    await new Promise(resolveWait => setTimeout(resolveWait, 100));
  }
  throw new Error('Chrome did not expose the extension test target.');
};

let socket;
try {
  const port = await waitFor(async () => Number((await readFile(join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]));
  const base = `http://127.0.0.1:${port}`;
  const id = await waitFor(async () => {
    const targets = await fetch(`${base}/json`).then(response => response.json());
    if (process.env.DEBUG_BROWSER_SMOKE) console.log(targets.map(target => `${target.type}: ${target.url}`));
    return targets.filter(target => target.url.endsWith('/src/service-worker.js'))
      .map(target => /chrome-extension:\/\/([^/]+)/.exec(target.url)?.[1]).find(Boolean);
  });
  const target = await fetch(`${base}/json/new?about:blank`, { method: 'PUT' }).then(response => response.json());
  socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolveOpen, rejectOpen) => {
    socket.addEventListener('open', resolveOpen, { once: true });
    socket.addEventListener('error', rejectOpen, { once: true });
  });
  let nextId = 0;
  const pending = new Map();
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    if (!message.id) return;
    const entry = pending.get(message.id);
    if (!entry) return;
    pending.delete(message.id);
    if (message.error) entry.reject(new Error(message.error.message));
    else entry.resolve(message.result);
  });
  const cdp = (method, params = {}) => new Promise((resolveCall, rejectCall) => {
    const id = ++nextId;
    pending.set(id, { resolve: resolveCall, reject: rejectCall });
    socket.send(JSON.stringify({ id, method, params }));
  });
  const evaluate = async expression => (await cdp('Runtime.evaluate', {
    expression, returnByValue: true, awaitPromise: true,
  })).result.value;
  await cdp('Page.enable');
  await cdp('Runtime.enable');
  await cdp('Page.addScriptToEvaluateOnNewDocument', { source: `
    addEventListener('pagereveal', event => {
      sessionStorage.setItem('smoke-view-transition', String(Boolean(event.viewTransition)));
    });
  ` });
  const navigation = await cdp('Page.navigate', { url: `chrome-extension://${id}/management.html` });
  if (navigation.errorText) throw new Error(`Plan navigation failed: ${navigation.errorText}`);
  await waitFor(() => evaluate('document.documentElement.dataset.page === "plan"')).catch(async error => {
    throw new Error(`${error.message} Current page: ${await evaluate('location.href')}; ${await evaluate('document.body?.innerText?.slice(0, 200)')}`);
  });
  await waitFor(() => evaluate(`document.querySelector('#sync-state')?.textContent?.startsWith('Not connected')`));
  assert.equal(await evaluate('document.querySelectorAll(".flip-card").length'), 2,
    'The Plan page must show the two split clock cards');
  assert.equal(await evaluate('document.querySelector("#flip-minutes .flip-face--top")?.textContent'), '30');
  await waitFor(() => evaluate('document.querySelector("#study-timer-form")?.dataset.ready === "true"'));
  if (process.env.SCREENSHOT_PATH) {
    await new Promise(resolveWait => setTimeout(resolveWait, 1000));
    await cdp('Emulation.setDeviceMetricsOverride', { width: 1487, height: 1058, deviceScaleFactor: 1, mobile: false });
    const shot = await cdp('Page.captureScreenshot', { format: 'png' });
    await writeFile(process.env.SCREENSHOT_PATH, Buffer.from(shot.data, 'base64'));
  }
  await cdp('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true,
    'The clock overflows the mobile viewport');
  await cdp('Emulation.clearDeviceMetricsOverride');
  await evaluate(`document.querySelector('[data-duration="25"]').click()`);
  if (process.env.DEBUG_BROWSER_SMOKE) console.log('clock click', await evaluate('JSON.stringify({value:document.querySelector("#study-duration").value, card:document.querySelector("#flip-minutes").dataset.value, classes:document.querySelector("#flip-minutes").className, reduced:matchMedia("(prefers-reduced-motion: reduce)").matches, last:document.documentElement.dataset.lastPreset})'));
  assert.equal(await evaluate('document.querySelector("#flip-minutes").classList.contains("is-flipping")'), true,
    'Changing minutes should flip the minutes card');
  assert.equal(await evaluate('document.querySelector("#flip-seconds").classList.contains("is-flipping")'), false,
    'Changing minutes must not flip the seconds card');
  await waitFor(() => evaluate('!document.querySelector("#flip-minutes").classList.contains("is-flipping")'));
  assert.equal(await evaluate('document.querySelector("#flip-minutes .flip-face--top")?.textContent'), '25');
  await cdp('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
  await evaluate(`document.querySelector('[data-duration="45"]').click()`);
  assert.equal(await evaluate('document.querySelector("#flip-minutes").classList.contains("is-flipping")'), false,
    'Reduced motion must update the number without rotating the card');
  assert.equal(await evaluate('document.querySelector("#flip-minutes").dataset.value'), '45');
  await cdp('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'no-preference' }] });
  assert.equal(await evaluate('document.documentElement.classList.contains("js")'), true,
    'The CSP-safe page entry script did not run');
  const pill = await evaluate(`(document.querySelector('a[href="sites.html"]').click(),
    document.documentElement.classList.contains('nav-pill-ready'))`);
  assert.equal(pill, true, 'The selected tab did not begin its pill transition');
  assert.equal(await evaluate(`getComputedStyle(document.querySelector('.view-nav [aria-current="page"] .tab-pill-bg')).opacity`), '0',
    'The destination pill background overlaps the moving pill');
  await new Promise(resolveWait => setTimeout(resolveWait, 110));
  const pillHeight = await evaluate(`document.querySelector('.nav-pill').getBoundingClientRect().height`);
  assert.ok(pillHeight <= 54, `The pill grew vertically to ${pillHeight}px`);
  const pillScaleY = await evaluate(`new DOMMatrix(getComputedStyle(document.querySelector('.nav-pill')).transform).m22`);
  assert.ok(Math.abs(pillScaleY - 1) < .03, `The pill scaled vertically by ${pillScaleY}`);
  await waitFor(() => evaluate('document.documentElement.dataset.page === "sites"'));
  await waitFor(() => evaluate('Boolean(document.querySelector("[data-page-heading]"))'));
  assert.equal(await evaluate('document.documentElement.classList.contains("page-fallback-enter")'), false,
    'A full-page entrance ran after the tab pill finished');
  const transition = await waitFor(() => evaluate('sessionStorage.getItem("smoke-view-transition")'), 3_000).catch(() => null);
  assert.equal(transition, 'false', 'A cross-document View Transition still scales the Sites page');
  if (process.env.DEBUG_BROWSER_SMOKE) console.log(await evaluate(`JSON.stringify({
    url: location.href, pagereveal: 'onpagereveal' in window, startViewTransition: 'startViewTransition' in document,
    storage: {...sessionStorage}, classes: document.documentElement.className,
    nav: performance.getEntriesByType('navigation')[0]?.type,
  })`));
  const fallback = await evaluate('document.documentElement.classList.contains("tab-pill-only")');
  assert.equal(await evaluate('document.querySelector("[data-page-heading]").classList.contains("is-entering")'), false,
    'The heading moved after the pill transition');
  await waitFor(() => evaluate('document.activeElement?.id === "blocklist-heading"'));
  if (process.env.SITES_SCREENSHOT_PATH) {
    await new Promise(resolveWait => setTimeout(resolveWait, 700));
    await cdp('Emulation.setDeviceMetricsOverride', { width: 1465, height: 930, deviceScaleFactor: 1, mobile: false });
    const shot = await cdp('Page.captureScreenshot', { format: 'png' });
    await writeFile(process.env.SITES_SCREENSHOT_PATH, Buffer.from(shot.data, 'base64'));
  }
  assert.equal(await evaluate('getComputedStyle(document.activeElement).outlineStyle'), 'none',
    'Programmatic heading focus drew the white rectangle');
  assert.equal(await evaluate('getComputedStyle(document.activeElement).viewTransitionName'), 'none',
    'The title should not morph between unrelated page headings');
  assert.equal(transition === 'true' || fallback, true,
    'Chrome displayed the new page without a View Transition or fallback animation');
  await waitFor(() => evaluate(`document.querySelector('#sync-state')?.textContent?.startsWith('Not connected')`));
  await evaluate(`document.querySelector('a[href="connection.html"]').click()`);
  await new Promise(resolveWait => setTimeout(resolveWait, 110));
  const connectionPillHeight = await evaluate(`document.querySelector('.nav-pill').getBoundingClientRect().height`);
  const connectionPillScaleY = await evaluate(`new DOMMatrix(getComputedStyle(document.querySelector('.nav-pill')).transform).m22`);
  assert.ok(connectionPillHeight <= 54 && Math.abs(connectionPillScaleY - 1) < .03,
    `Sites → Connection pill grew: ${connectionPillHeight}px, scale ${connectionPillScaleY}`);
  await waitFor(() => evaluate('document.documentElement.dataset.page === "connection"'));
  assert.equal(await evaluate('sessionStorage.getItem("smoke-view-transition")'), 'false',
    'A cross-document View Transition still scales the Connection page');
  await waitFor(() => evaluate('document.documentElement.classList.contains("tab-pill-only")'));
  await waitFor(() => evaluate('document.activeElement?.id === "connection-heading"'));
  assert.equal(await evaluate('document.documentElement.dataset.nav'), 'forward');
  await waitFor(() => evaluate(`document.querySelector('#sync-state')?.textContent?.startsWith('Not connected')`));
  await evaluate(`document.querySelector('a[href="management.html"]').click()`);
  await new Promise(resolveWait => setTimeout(resolveWait, 110));
  if (process.env.PILL_MID_SCREENSHOT_PATH) {
    const shot = await cdp('Page.captureScreenshot', { format: 'png' });
    await writeFile(process.env.PILL_MID_SCREENSHOT_PATH, Buffer.from(shot.data, 'base64'));
  }
  const planPillHeight = await evaluate(`document.querySelector('.nav-pill').getBoundingClientRect().height`);
  const planPillScaleY = await evaluate(`new DOMMatrix(getComputedStyle(document.querySelector('.nav-pill')).transform).m22`);
  assert.ok(planPillHeight <= 54 && Math.abs(planPillScaleY - 1) < .03,
    `Connection → Plan pill grew: ${planPillHeight}px, scale ${planPillScaleY}`);
  await waitFor(() => evaluate('document.documentElement.dataset.page === "plan"'));
  await waitFor(() => evaluate('document.documentElement.classList.contains("tab-pill-only")'));
  await waitFor(() => evaluate('document.activeElement?.id === "focus-heading"'));
  assert.equal(await evaluate('document.documentElement.dataset.nav'), 'back');
  await waitFor(() => evaluate(`document.querySelector('#sync-state')?.textContent?.startsWith('Not connected')`));
  await evaluate(`document.querySelector('#study-timer-form').requestSubmit()`);
  await waitFor(() => evaluate('!document.querySelector("#study-timer-remaining").hidden'));
  await waitFor(() => evaluate('document.querySelector("#flip-seconds").dataset.value === "59"'), 3_000);
  assert.equal(await evaluate('document.querySelector("#flip-minutes").dataset.value'), '29');
  await waitFor(() => evaluate('document.querySelector("#flip-seconds").dataset.value === "58"'), 3_000);
  assert.equal(await evaluate('document.querySelector("#flip-minutes").classList.contains("is-flipping")'), false,
    'The minutes card rotated when only seconds changed');
  const rulesWhileRunning = await evaluate('(async () => (await chrome.declarativeNetRequest.getDynamicRules()).length)()');
  assert.equal(rulesWhileRunning, 5, 'The timer did not block the initial desktop site list');
  await evaluate('document.querySelector("#stop-study-timer").click()');
  await waitFor(() => evaluate('document.querySelector("#study-timer-remaining").hidden'));
  const rulesAfterStop = await evaluate('(async () => (await chrome.declarativeNetRequest.getDynamicRules()).length)()');
  assert.equal(rulesAfterStop, 0, 'Stopping the timer left desktop blocking active');
  console.log('PASS: Plan → Sites → Connection → Plan slides the tab pill without a full-page entrance.');
  console.log('PASS: Offline study timer displayed its countdown and activated/released site blocking.');
} finally {
  socket?.close();
  chrome.kill();
  if (profile.startsWith(tmpdir()) && profile.includes('dimky-transition-test-')) {
    await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }).catch(() => {});
  }
}
