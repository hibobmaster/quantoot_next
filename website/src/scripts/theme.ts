export function initThemeToggle(): void {
  const toggle = document.querySelector<HTMLButtonElement>('[data-site-theme-toggle]');
  const metaTheme = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  const sunIcon = toggle?.querySelector<SVGElement>('[data-icon-sun]');
  const moonIcon = toggle?.querySelector<SVGElement>('[data-icon-moon]');
  const colorQuery = window.matchMedia?.('(prefers-color-scheme: dark)');

  let isDark = colorQuery?.matches ?? false;
  let manualOverride = false;

  function render(): void {
    if (isDark) {
      document.documentElement.dataset.theme = 'dark';
      if (metaTheme) metaTheme.content = '#111311';
    } else {
      delete document.documentElement.dataset.theme;
      if (metaTheme) metaTheme.content = '#f6f2e9';
    }

    if (toggle) {
      toggle.setAttribute('aria-pressed', String(isDark));
    }
    if (sunIcon && moonIcon) {
      sunIcon.toggleAttribute('hidden', !isDark);
      moonIcon.toggleAttribute('hidden', isDark);
    }
  }

  render();

  colorQuery?.addEventListener('change', (event) => {
    if (manualOverride) return;
    isDark = event.matches;
    render();
  });

  if (toggle) {
    toggle.hidden = false;
    toggle.addEventListener('click', () => {
      manualOverride = true;
      isDark = !isDark;
      render();
    });
  }
}
