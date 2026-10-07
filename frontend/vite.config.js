import { defineConfig } from 'vite';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';

export default defineConfig({
  plugins: [{
    name: 'expenseflow-offline-shell',
    apply: 'build',
    generateBundle(_options, bundle) {
      const assets = ['/offline.html', '/manifest.webmanifest', '/icons/icon-192.png', '/icons/icon-512.png', '/icons/maskable-512.png', ...Object.keys(bundle).filter(name => name.startsWith('assets/')).map(name => '/' + name)];
      const template = readFileSync(new URL('./sw-template.js', import.meta.url), 'utf8');
      const hash = createHash('sha256').update(JSON.stringify(assets) + template).digest('hex').slice(0, 16);
      this.emitFile({ type: 'asset', fileName: 'sw.js', source: template.replace('__CACHE_NAME__', 'expenseflow-static-' + hash).replace('__ASSETS__', JSON.stringify(assets)) });
    }
  }]
});
