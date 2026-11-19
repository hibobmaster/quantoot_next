export function initThemeToggle(): void {
  const toggle = document.querySelector<HTMLButtonElement>('[data-site-theme-toggle]');
  const metaTheme = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  const sunIcon = toggle?.querySelector<SVGElement>('[data-icon-sun]');
  const moonIcon = toggle?.querySelector<SVGElement>('[data-icon-moon]');

  let isDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;

  function render(): void {
    if (isDark) {
      document.documentElement.dataset.theme = 'dark';
      if (metaTheme) metaTheme.content = '#121311';
    } else {
      delete document.documentElement.dataset.theme;
      if (metaTheme) metaTheme.content = '#f8f5ed';
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

  if (toggle) {
    toggle.hidden = false;
    toggle.addEventListener('click', () => {
      isDark = !isDark;
      render();
    });
  }
}
