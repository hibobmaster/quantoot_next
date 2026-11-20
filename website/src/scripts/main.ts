import { getLanguage, initI18n, onLanguageChange, type Language } from './i18n';
import { initThemeToggle } from './theme';
import { initMotion } from './motion';
import { initInteractiveApp } from './interactive-app';
import { initAndroidDownloads } from './downloads';

const MENU_LABELS: Record<'open' | 'close', Record<Language, string>> = {
  open: { en: 'Open menu', 'zh-Hans': '打开菜单', 'zh-Hant': '開啟選單' },
  close: { en: 'Close menu', 'zh-Hans': '关闭菜单', 'zh-Hant': '關閉選單' },
};

function initChrome(): void {
  const header = document.querySelector<HTMLElement>('[data-site-header]');
  if (header) {
    const onScroll = () => {
      header.classList.toggle('is-scrolled', window.scrollY > 12);
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
  }

  const menuBtn = document.querySelector<HTMLButtonElement>('[data-menu-toggle]');
  const drawer = document.querySelector<HTMLElement>('[data-mobile-drawer]');
  const backdrop = document.querySelector<HTMLElement>('[data-mobile-backdrop]');
  const iconOpen = menuBtn?.querySelector<HTMLElement>('[data-menu-icon-open]');
  const iconClose = menuBtn?.querySelector<HTMLElement>('[data-menu-icon-close]');
  if (menuBtn && drawer) {
    let isDrawerOpen = false;

    const updateMenuLabel = () => {
      menuBtn.setAttribute('aria-label', MENU_LABELS[isDrawerOpen ? 'close' : 'open'][getLanguage()]);
    };

    const setOpen = (open: boolean) => {
      if (open) {
        window.dispatchEvent(new CustomEvent('quantoot:overlay-open', { detail: 'drawer' }));
      }
      isDrawerOpen = open;
      drawer.hidden = !open;
      if (backdrop) backdrop.hidden = !open;
      header?.classList.toggle('is-menu-open', open);
      menuBtn.setAttribute('aria-expanded', String(open));
      iconOpen?.toggleAttribute('hidden', open);
      iconClose?.toggleAttribute('hidden', !open);
      updateMenuLabel();
    };

    onLanguageChange(updateMenuLabel);

    menuBtn.addEventListener('click', () => {
      setOpen(!isDrawerOpen);
    });

    backdrop?.addEventListener('click', () => setOpen(false));

    drawer.querySelectorAll('a').forEach((link) => {
      link.addEventListener('click', () => setOpen(false));
    });

    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && isDrawerOpen) {
        setOpen(false);
        menuBtn.focus();
      }
    });

    window.addEventListener('quantoot:overlay-open', (event) => {
      if ((event as CustomEvent<string>).detail !== 'drawer' && isDrawerOpen) {
        setOpen(false);
      }
    });

    const desktopQuery = window.matchMedia?.('(min-width: 921px)');
    desktopQuery?.addEventListener('change', (event) => {
      if (event.matches && isDrawerOpen) setOpen(false);
    });
  }

  // Copy support email button microinteraction
  const copyBtn = document.querySelector<HTMLButtonElement>('[data-copy-email]');
  const copyFeedback = document.querySelector<HTMLElement>('[data-copy-feedback]');
  if (copyBtn && copyFeedback) {
    let resetTimer: number | undefined;
    copyBtn.addEventListener('click', async () => {
      const email = copyBtn.dataset.copyEmail ?? '';
      try {
        await navigator.clipboard.writeText(email);
        copyFeedback.hidden = false;
        window.clearTimeout(resetTimer);
        resetTimer = window.setTimeout(() => {
          copyFeedback.hidden = true;
        }, 2200);
      } catch {
        // Clipboard permission unavailable; mailto link remains directly clickable.
      }
    });
  }
}

initI18n();
initThemeToggle();
initChrome();
initMotion();
initInteractiveApp();
void initAndroidDownloads();
