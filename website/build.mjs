import { mkdir, writeFile, access } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import { renderHomePage } from './src/pages/home.mjs';
import { renderPrivacyPage } from './src/pages/privacy.mjs';
import { renderSupportPage } from './src/pages/support.mjs';

const execFileAsync = promisify(execFile);
const root = fileURLToPath(new URL('.', import.meta.url));
const dist = resolve(root, 'dist');
const assetsDir = resolve(dist, 'assets');
const esbuildBin = resolve(root, 'node_modules/.bin/esbuild');

await mkdir(assetsDir, { recursive: true });
await mkdir(resolve(dist, 'privacy'), { recursive: true });
await mkdir(resolve(dist, 'support'), { recursive: true });

// 1. Bundle TypeScript -> dist/assets/site.js
await execFileAsync(esbuildBin, [
  resolve(root, 'src/scripts/main.ts'),
  '--bundle',
  '--minify',
  '--format=iife',
  '--target=es2020',
  `--outfile=${resolve(assetsDir, 'site.js')}`,
]);

// 2. Bundle CSS -> dist/assets/site.css
await execFileAsync(esbuildBin, [
  resolve(root, 'src/styles/main.css'),
  '--bundle',
  '--minify',
  '--target=safari15,chrome100,firefox100',
  `--outfile=${resolve(assetsDir, 'site.css')}`,
]);

// 3. Render static HTML pages
await writeFile(resolve(dist, 'index.html'), renderHomePage(), 'utf8');
await writeFile(resolve(dist, 'privacy/index.html'), renderPrivacyPage(), 'utf8');
await writeFile(resolve(dist, 'support/index.html'), renderSupportPage(), 'utf8');

// 4. Verify required static files exist
await access(resolve(dist, '_headers'));
await access(resolve(assetsDir, 'quantoot.png'));
await access(resolve(assetsDir, 'lake.jpg'));
await access(resolve(assetsDir, 'bookshop.jpg'));
await access(resolve(assetsDir, 'twilight.jpg'));

console.log('Built website/dist (index.html, privacy/, support/, assets/site.css, assets/site.js).');
