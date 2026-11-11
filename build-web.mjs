import { mkdir } from 'node:fs/promises';
import { build } from 'esbuild';

await mkdir('www/js', { recursive: true });

await build({
  entryPoints: ['src/main.ts'],
  outfile: 'www/js/app.js',
  bundle: true,
  format: 'iife',
  target: ['es2019'],
  minify: true,
  legalComments: 'none',
  define: { 'process.env.NODE_ENV': '"production"' },
  logLevel: 'info'
});

console.log('[hketa] www/js/app.js built');
