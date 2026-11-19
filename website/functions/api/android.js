const repository = 'https://git.qqs.tw/bobmaster/quantoot_next';
const latestRelease = 'https://git.qqs.tw/api/v1/repos/bobmaster/quantoot_next/releases/latest';
const androidAbis = ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'];

function releaseResponse(body, status = 200) {
  return Response.json(body, {
    status,
    headers: { 'Cache-Control': status === 200 ? 'public, max-age=300' : 'no-store' },
  });
}

export async function onRequestGet() {
  try {
    const upstream = await fetch(latestRelease, {
      headers: { Accept: 'application/json' },
      redirect: 'manual',
      signal: AbortSignal.timeout(5000),
    });
    if (upstream.status === 404) return releaseResponse({ error: 'no_release' }, 404);
    if (!upstream.ok) throw new Error(`Forgejo returned ${upstream.status}`);

    const release = await upstream.json();
    const version = /^v(\d+\.\d+\.\d+)$/.exec(release.tag_name)?.[1];
    if (!version || release.draft !== false || release.prerelease !== false || !Array.isArray(release.assets)) {
      return releaseResponse({ error: 'no_release' }, 404);
    }

    const assetUrl = name => {
      const url = `${repository}/releases/download/v${version}/${name}`;
      const matches = release.assets.filter(asset => asset?.name === name);
      const [asset] = matches;
      return matches.length === 1 && asset.type === 'attachment' && asset.browser_download_url === url
        && Number.isSafeInteger(asset.size) && asset.size > 0 ? url : null;
    };
    const apks = {};
    for (const abi of androidAbis) {
      const url = assetUrl(`QuanToot-${version}-${abi}.apk`);
      if (url) apks[abi] = url;
    }
    if (!Object.keys(apks).length) return releaseResponse({ error: 'no_release' }, 404);

    return releaseResponse({ version, apks, checksums: assetUrl('SHA256SUMS.txt') });
  } catch (error) {
    console.error('Could not load the latest Android release from Forgejo', error);
    return releaseResponse({ error: 'unavailable' }, 502);
  }
}
