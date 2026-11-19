import { initI18n } from './i18n';
import { initThemeToggle } from './theme';
import { initMotion } from './motion';
import { initInteractiveApp } from './interactive-app';
import { initAndroidDownloads } from './downloads';

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
  if (menuBtn && drawer) {
    const setOpen = (open: boolean) => {
      drawer.hidden = !open;
      menuBtn.setAttribute('aria-expanded', String(open));
    };

    menuBtn.addEventListener('click', () => {
      setOpen(drawer.hidden);
    });

    drawer.querySelectorAll('a').forEach((link) => {
      link.addEventListener('click', () => setOpen(false));
    });

    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && !drawer.hidden) {
        setOpen(false);
        menuBtn.focus();
      }
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
