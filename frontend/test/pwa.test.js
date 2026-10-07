import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import vm from 'node:vm';

test('manifest possui ícones reais e atalho de despesa', () => {
  const manifest = JSON.parse(readFileSync(new URL('../public/manifest.webmanifest', import.meta.url)));
  assert.equal(manifest.display, 'standalone');
  assert.equal(manifest.shortcuts[0].url, '/?action=expense');
  for (const icon of manifest.icons) {
    const file = new URL('../public' + icon.src, import.meta.url);
    assert.ok(existsSync(file));
    const png = readFileSync(file);
    const size = Number(icon.sizes.split('x')[0]);
    assert.equal(png.readUInt32BE(16), size); assert.equal(png.readUInt32BE(20), size);
  }
});

test('service worker não intercepta API, comprovantes externos nem mutações', () => {
  const handlers = {};
  const source = readFileSync(new URL('../sw-template.js', import.meta.url), 'utf8').replace('__ASSETS__', '["/offline.html"]');
  vm.runInNewContext(source, { URL, self: { location: { origin: 'https://app.example' }, addEventListener: (name, handler) => { handlers[name] = handler; } } });
  for (const [url, method, auth] of [['https://app.example/api/accounts','GET',false], ['https://s3.example/receipt','GET',false], ['https://app.example/new','POST',false], ['https://app.example/private','GET',true]]) {
    let intercepted = false;
    handlers.fetch({ request: { url, method, headers: { has: () => auth } }, respondWith: () => { intercepted = true; } });
    assert.equal(intercepted, false);
  }
});
