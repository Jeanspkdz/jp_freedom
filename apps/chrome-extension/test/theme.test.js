import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const extensionFile = name => new URL(`../${name}`, import.meta.url);
const readExtensionFile = name => readFile(extensionFile(name), 'utf8');

test('separate management pages follow the approved focus, sites, and connection compositions', async () => {
  const [html, sites, connection, css] = await Promise.all([
    readExtensionFile('management.html'),
    readExtensionFile('sites.html'),
    readExtensionFile('connection.html'),
    readExtensionFile('management.css'),
  ]);

  assert.match(html, /href="management\.css"/);
  assert.match(html, /id="view-plan"/);
  assert.match(sites, /id="view-sites"/);
  assert.match(connection, /id="view-connection"/);
  assert.match(html, /class="brand-icon" viewBox="0 0 100 100"/);
  assert.equal((html.match(/class="brand-icon-piece brand-icon-piece--/g) ?? []).length, 3);
  assert.match(connection, /class="connection-flow" aria-hidden="true"><div class="connection-beams">/);

  const root = css.match(/:root\s*\{([^}]+)\}/)?.[1];
  assert.ok(root, 'management.css defines root palette tokens');
  assert.match(root, /color-scheme:\s*dark/);
  assert.match(root, /background:\s*#10182b\b/i);
  assert.match(root, /--surface:\s*#18233a\b/i);
  assert.match(root, /--surface-variant:\s*#222e47\b/i);
  assert.match(root, /--ink:\s*#f2f0ea\b/i);
  assert.match(root, /--muted:\s*#b4bed8\b/i);
  assert.match(root, /--amber:\s*#ffc27a\b/i);
  assert.match(root, /--on-amber:\s*#402d12\b/i);
  assert.match(root, /--amber-container:\s*#563c1d\b/i);
  assert.match(root, /--purple:\s*#b39ddb\b/i);
  assert.match(root, /--purple-container:\s*#47395f\b/i);
  assert.match(root, /--on-purple-container:\s*#ebddff\b/i);

  assert.match(css, /\.focus-board\s*\{[^}]*grid-template-columns:\s*minmax\(0, 1fr\)/s);
  assert.match(css, /\.focus-progress\s*>\s*span\s*\{[^}]*background:\s*var\(--purple\)/s);
  assert.match(css, /\.day-plans\s*\{[^}]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\)/s);
  assert.match(css, /\.day-plan-row\.is-current\s*\{[^}]*background:\s*var\(--amber-container\)/s);
  assert.match(css, /#common-sites, #custom-sites\s*\{[^}]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\)/s);
  assert.match(css, /\.site-row input:checked ~ \.site-action\s*\{[^}]*background:\s*var\(--purple\)/s);
  assert.match(css, /\.connection-rail\s*\{[^}]*grid-template-columns:\s*minmax\(0, 1\.2fr\) minmax\(340px, \.9fr\)/s);
  assert.match(css, /\.connection-diamond\s*\{[^}]*border-top:\s*2px solid var\(--amber\)/s);
  assert.match(css, /\.connection-beams \.seg\s*\{[^}]*background:\s*linear-gradient\(90deg,[^}]*#ffe2bd/s);
  assert.match(css, /\.view-tab\.is-selected:hover:not\(:disabled\)\s*\{[^}]*color:\s*var\(--on-purple\)/s);
  assert.match(css, /font:\s*400 18px\/1\.5/);
  assert.doesNotMatch(css, /@view-transition\s*\{\s*navigation:\s*auto/);
  assert.match(css, /html\[data-nav="back"\]::view-transition-new\(root\)/);
  assert.match(css, /\.page-header\s*\{\s*view-transition-name:\s*page-header/);
  assert.match(css, /\.brand:hover \.brand-icon-piece--left\s*\{[^}]*transform:/s);
  assert.match(css, /\.brand:hover \.brand-icon-piece--top\s*\{[^}]*transform:/s);
  assert.match(css, /\.brand:hover \.brand-icon-piece--bottom\s*\{[^}]*transform:/s);
  assert.match(css, /@keyframes connection-beam\s*\{/);
  assert.match(css, /\.connection-device svg\s*\{\s*width:\s*58px;\s*height:\s*58px;/);
  assert.doesNotMatch(css, /#faf6ee|#f7f6[26]|#fffdf8|#fffefe|#fff7eb|#fff0dd|#eee7f8|#76599b|rgb\(170\s+185\s+255/i);
});

test('plan, sites, and connection are separate extension documents', async () => {
  const [plan, sites, connection] = await Promise.all([
    readExtensionFile('management.html'),
    readExtensionFile('sites.html'),
    readExtensionFile('connection.html'),
  ]);

  assert.match(plan, /href="sites\.html"/);
  assert.match(plan, /href="connection\.html"/);
  assert.match(sites, /href="management\.html"/);
  assert.match(connection, /href="management\.html"/);
  assert.match(plan, /id="view-plan"/);
  assert.match(sites, /id="view-sites"/);
  assert.match(connection, /id="view-connection"/);
  assert.doesNotMatch(plan, /id="view-sites"|id="view-connection"/);
  assert.doesNotMatch(sites, /id="view-plan"|id="view-connection"/);
  assert.doesNotMatch(connection, /id="view-plan"|id="view-sites"/);
  for (const html of [plan, sites, connection]) {
    assert.match(html, /href="management\.css"/);
    assert.match(html, /src="src\/page-entry\.js"/);
    assert.doesNotMatch(html, /<script>/);
    assert.match(html, /src="src\/management\.js"/);
  }
});

test('blocked page and both scenes use the amber and purple roles', async () => {
  const [blocked, blockedCss, terrain, puddle] = await Promise.all([
    readExtensionFile('blocked.html'),
    readExtensionFile('blocked.css'),
    readExtensionFile('focus-terrain.html'),
    readExtensionFile('water-puddle.html'),
  ]);

  assert.match(blocked, /focus-terrain\.html\?blocked/);
  assert.match(blocked, /water/);
  assert.match(blocked, /href="blocked\.css"/);
  assert.match(blocked, /src="src\/page-entry\.js"/);
  assert.match(blockedCss, /--background:\s*#10182b\b/i);
  assert.match(blockedCss, /--surface:\s*#18233a\b/i);
  assert.match(blockedCss, /\.blocked-card\s*\{/);
  assert.match(blocked, /Current activity/);
  assert.match(blocked, /Available again at/);
  assert.match(blocked, /Particles/);
  assert.match(blocked, /Rainy puddle/);
  assert.match(blockedCss, /color:\s*var\(--amber\)/i);
  assert.match(blockedCss, /background:\s*var\(--purple-container\)/i);
  assert.match(terrain, /html, body \{[^}]*background: #10182b/);
  assert.match(terrain, /body \{ color: #f2f0ea/);
  assert.match(terrain, /STEAM\s*=\s*vec3\(180,\s*190,\s*216\)\s*\/\s*255\.0/);
  assert.match(terrain, /DARK_GREEN\s*=\s*vec3\(34,\s*46,\s*71\)\s*\/\s*255\.0/);
  assert.match(terrain, /GREEN\s*=\s*vec3\(71,\s*57,\s*95\)\s*\/\s*255\.0/);
  assert.match(terrain, /MINT\s*=\s*vec3\(235,\s*221,\s*255\)\s*\/\s*255\.0/);
  assert.match(terrain, /WHITE_MINT\s*=\s*vec3\(242,\s*240,\s*234\)\s*\/\s*255\.0/);
  assert.match(terrain, /AMBER\s*=\s*vec3\(255,\s*194,\s*122\)\s*\/\s*255\.0/);
  assert.match(terrain, /PURPLE\s*=\s*vec3\(179,\s*157,\s*219\)\s*\/\s*255\.0/);
  assert.match(puddle, /html, body \{[^}]*background: #10182b/);
  assert.match(puddle, /#fallback[^{]*\{[^}]*background: #10182b/);
  assert.match(puddle, /REFLECTION_TINT\s*=\s*\[179\s*\/\s*255,\s*157\s*\/\s*255,\s*219\s*\/\s*255\]/);
});
