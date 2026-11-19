import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { generateReleaseNotes, versionTag } from './release-notes.mjs';

const {
  FORGEJO_API_URL: apiUrl,
  FORGEJO_EVENT_NAME: event,
  FORGEJO_REF: ref,
  FORGEJO_REF_NAME: tag,
  FORGEJO_REPOSITORY: repository,
  FORGEJO_SERVER_URL: serverUrl,
  FORGEJO_TOKEN: token,
} = process.env;

const version = versionTag.exec(tag)?.[1];
if (!version || event !== 'push' || ref !== `refs/tags/${tag}` || !apiUrl || !serverUrl || !repository || !token) {
  throw new Error('Expected a Forgejo Actions version tag push');
}

const repoApi = `${apiUrl.replace(/\/$/, '')}/repos/${repository}`;
const headers = { Authorization: `token ${token}`, Accept: 'application/json' };
const assetDir = 'androidApp/build/ci-release';
const checksum = 'SHA256SUMS.txt';
const apkNames = ['armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64']
  .map(abi => `QuanToot-${version}-${abi}.apk`);
const files = new Map(await Promise.all([...apkNames, checksum].map(async name =>
  [name, await readFile(`${assetDir}/${name}`)]
)));
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const manifest = apkNames.map(name => `${sha256(files.get(name))}  ${name}\n`).join('');
if (manifest !== files.get(checksum).toString('utf8')) {
  throw new Error('The four APKs do not match SHA256SUMS.txt');
}
const fingerprint = sha256(files.get(checksum));
const releaseMarker = `<!-- quantoot-android-manifest: ${fingerprint} -->`;
const defaultNotes = () => generateReleaseNotes(tag, `${serverUrl.replace(/\/$/, '')}/${repository}`);

async function api(method, path, { json, body, missingOk = false } = {}) {
  const response = await fetch(repoApi + path, {
    method,
    headers: json === undefined ? headers : { ...headers, 'Content-Type': 'application/json' },
    body: json === undefined ? body : JSON.stringify(json),
    signal: AbortSignal.timeout(120_000),
  });
  if (missingOk && response.status === 404) return null;
  if (!response.ok) throw new Error(`Forgejo ${method} ${path} returned HTTP ${response.status}`);
  return response.json();
}

async function verifyTag() {
  const remoteTag = await api('GET', `/tags/${encodeURIComponent(tag)}`);
  const commit = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
  if (remoteTag.commit?.sha !== commit) {
    throw new Error(`Forgejo tag ${tag} no longer points to the built commit`);
  }
}

await verifyTag();
const release = await api('GET', `/releases/tags/${encodeURIComponent(tag)}`, { missingOk: true })
  ?? await api('POST', '/releases', {
    json: {
      tag_name: tag, name: tag, body: `${defaultNotes()}\n\n${releaseMarker}`,
      draft: true, prerelease: version.includes('-'),
    },
  });
const releasePath = `/releases/${release.id}`;
const assetsPath = `${releasePath}/assets`;
const notes = release.body ?? '';
const previousFingerprint = /^<!-- quantoot-android-manifest: ([0-9a-f]{64}) -->$/m.exec(notes)?.[1];
if (previousFingerprint && previousFingerprint !== fingerprint) {
  throw new Error(`The signed APK checksums differ from the ones recorded for ${tag}`);
}

function missingFiles(assets, recordedFingerprint) {
  const missing = [];
  for (const [name, bytes] of files) {
    const matches = assets.filter(asset => asset.name === name);
    if (!matches.length) {
      missing.push([name, bytes]);
      continue;
    }
    const [asset] = matches;
    if (!recordedFingerprint || matches.length !== 1 || asset.type !== 'attachment' || asset.size !== bytes.length) {
      throw new Error(`Release ${tag} has a conflicting attachment named ${name}`);
    }
  }
  return missing;
}

// The Actions token can list private release assets but cannot download them.
// Record the verified manifest before writing assets so a retry can resume safely.
const missingAssets = missingFiles(await api('GET', assetsPath), previousFingerprint);
const notesWithoutMarker = notes.replace(releaseMarker, '').trim();
if (!previousFingerprint || !notesWithoutMarker) {
  const body = `${notesWithoutMarker ? notes : defaultNotes()}\n\n${releaseMarker}`;
  await api('PATCH', releasePath, { json: { body } });
}

for (const [name, bytes] of missingAssets) {
  const form = new FormData();
  form.set('attachment', new Blob([bytes]), name);
  const uploaded = await api('POST', assetsPath, { body: form });
  if (uploaded.name !== name || uploaded.size !== bytes.length) {
    throw new Error(`Forgejo returned unexpected metadata for ${name}`);
  }
  console.log(`Uploaded ${name}`);
}

if (missingFiles(await api('GET', assetsPath), fingerprint).length) {
  throw new Error(`Release ${tag} is missing Android attachments`);
}
if (release.draft) {
  await verifyTag();
  const published = await api('PATCH', releasePath, { json: { draft: false } });
  if (published.draft) throw new Error(`Release ${tag} is still a draft`);
}
console.log(`Forgejo release ${tag} has all five Android attachments`);
