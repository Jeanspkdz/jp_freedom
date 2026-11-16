import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const managementCss = await readFile(new URL('management.css', root), 'utf8');
const blockedHtml = await readFile(new URL('blocked.html', root), 'utf8');

test('management uses the fixed Android dark palette with amber and periwinkle roles', () => {
  assert.match(managementCss, /--background:\s*#10182b/i);
  assert.match(managementCss, /--accent-amber:\s*#ffc27a/i);
  assert.match(managementCss, /--accent-periwinkle:\s*#aab9ff/i);
  assert.match(managementCss, /color-scheme:\s*dark/i);
});

test('management keeps focus indicators and removes decorative motion when requested', () => {
  assert.match(managementCss, /:focus-visible/);
  assert.match(managementCss, /@media\s*\(prefers-reduced-motion:\s*reduce\)/i);
  assert.match(managementCss, /@media\s*\(max-width:\s*600px\)/i);
});

test('blocked page uses the same dark palette and a keyboard-visible return link', () => {
  assert.match(blockedHtml, /#10182b/i);
  assert.match(blockedHtml, /:focus-visible/);
  assert.match(blockedHtml, /focus-terrain\.html\?blocked/);
  assert.match(blockedHtml, /Open focus management/);
});
