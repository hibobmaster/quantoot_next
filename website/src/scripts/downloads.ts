interface AndroidReleaseResponse {
  version: string;
  apks: Record<string, string>;
  checksums?: string | null;
}

export async function initAndroidDownloads(): Promise<void> {
  const section = document.querySelector<HTMLElement>('[data-android-downloads]');
  if (!section) return;

  const loading = section.querySelector<HTMLElement>('[data-release-loading]');
  const ready = section.querySelector<HTMLElement>('[data-release-ready]');
  const missing = section.querySelector<HTMLElement>('[data-release-missing]');
  const errorNode = section.querySelector<HTMLElement>('[data-release-error]');
  const versionNode = section.querySelector<HTMLElement>('[data-release-version]');
  const apkList = section.querySelector<HTMLElement>('[data-release-apks]');
  const checksumLink = section.querySelector<HTMLAnchorElement>('[data-release-checksums]');

  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 8000);
  if (loading) loading.hidden = false;

  try {
    const response = await fetch('/api/android', { signal: controller.signal });
    if (response.status === 404) {
      if (missing) missing.hidden = false;
      return;
    }
    if (!response.ok) {
      throw new Error(`Android release request returned ${response.status}`);
    }

    const release = (await response.json()) as AndroidReleaseResponse;
    if (versionNode) versionNode.textContent = `v${release.version}`;

    section.querySelectorAll<HTMLElement>('[data-abi]').forEach((item) => {
      const abi = item.dataset.abi;
      const url = abi ? release.apks?.[abi] : undefined;
      const link = item.querySelector<HTMLAnchorElement>('a');
      if (!url || !link) return;
      link.href = url;
      item.hidden = false;
    });

    if (release.checksums && checksumLink) {
      checksumLink.href = release.checksums;
      checksumLink.hidden = false;
    }

    if (ready) ready.hidden = false;
    if (apkList) apkList.hidden = false;
  } catch {
    if (errorNode) errorNode.hidden = false;
  } finally {
    window.clearTimeout(timeout);
    if (loading) loading.hidden = true;
  }
}
