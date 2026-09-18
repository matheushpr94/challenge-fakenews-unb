import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// The downloadable APK is a release artifact, deliberately not stored in Git.
// Stop publication before an absent artifact removes the existing public download.
const html = readFileSync(new URL('../public/index.html', import.meta.url), 'utf8');
const download = html.match(/href="([^"]+\.apk)"/u)?.[1];
if (!download) throw new Error('Link de download do APK ausente.');
const publicRoot = fileURLToPath(new URL('../public/', import.meta.url));
const knownPrefix = 'https://lume-previa.vercel.app/';
const localPath = download.startsWith(knownPrefix) ? download.slice(knownPrefix.length) : download;
if (/^https:\/\//u.test(localPath)) {
  console.log('Download aponta para um artefato externo; confirme sua disponibilidade antes de publicar.');
} else {
  if (!/^downloads\/[a-zA-Z0-9._-]+\.apk$/u.test(localPath)) throw new Error('Caminho de download inesperado.');
  const apk = readFileSync(resolve(publicRoot, localPath));
  if (apk.length < 4 || apk[0] !== 0x50 || apk[1] !== 0x4b) throw new Error('Arquivo de download não é um APK/ZIP.');
  console.log('APK presente para publicação.');
}
