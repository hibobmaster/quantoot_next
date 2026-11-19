export type Language = 'en' | 'zh-Hans' | 'zh-Hant';

const LANGUAGE_KEY = 'quantoot-language';
const SUPPORTED: readonly Language[] = ['en', 'zh-Hans', 'zh-Hant'];

const LANGUAGE_LABELS: Record<Language, string> = {
  en: 'English',
  'zh-Hans': '简体中文',
  'zh-Hant': '繁體中文',
};

interface TranslationBinding {
  node: HTMLElement;
  attribute: string | undefined;
  messages: Record<Language, string>;
}

let currentLanguage: Language = 'en';
const listeners: Array<(lang: Language) => void> = [];

export function getLanguage(): Language {
  return currentLanguage;
}

export function onLanguageChange(fn: (lang: Language) => void): void {
  listeners.push(fn);
}

function browserLanguage(): Language {
  for (const tag of navigator.languages ?? []) {
    const [language, variant] = tag.toLowerCase().split('-');
    if (language === 'en') return 'en';
    if (language === 'zh') {
      return ['hant', 'tw', 'hk', 'mo'].includes(variant ?? '') ? 'zh-Hant' : 'zh-Hans';
    }
  }
  return 'en';
}

function savedLanguage(): Language | null {
  try {
    const stored = sessionStorage.getItem(LANGUAGE_KEY);
    if (stored === 'zh') return 'zh-Hans';
    if (stored && (SUPPORTED as readonly string[]).includes(stored)) {
      return stored as Language;
    }
  } catch {
    // Language selection still works when browser storage is disabled.
  }
  return null;
}

export function initI18n(): void {
  const picker = document.querySelector<HTMLElement>('[data-lang-picker]');
  const trigger = picker?.querySelector<HTMLButtonElement>('[data-lang-trigger]');
  const currentLabel = picker?.querySelector<HTMLElement>('[data-lang-current]');
  const menu = picker?.querySelector<HTMLElement>('[data-lang-menu]');
  const options = Array.from(picker?.querySelectorAll<HTMLButtonElement>('[data-lang-option]') ?? []);

  const bindings: TranslationBinding[] = Array.from(
    document.querySelectorAll<HTMLElement>('[data-zh-hans]')
  ).map((node) => {
    const attribute = node.dataset.translate;
    const enValue = attribute ? (node.getAttribute(attribute) ?? '') : (node.textContent ?? '');
    return {
      node,
      attribute,
      messages: {
        en: enValue,
        'zh-Hans': node.dataset.zhHans ?? enValue,
        'zh-Hant': node.dataset.zhHant ?? enValue,
      },
    };
  });

  function setMenuOpen(open: boolean, returnFocus = false): void {
    if (!trigger || !menu) return;
    menu.hidden = !open;
    trigger.setAttribute('aria-expanded', String(open));
    if (open) {
      const selected = options.find((opt) => opt.dataset.langOption === currentLanguage) ?? options[0];
      selected?.focus();
    } else if (returnFocus) {
      trigger.focus();
    }
  }

  function apply(lang: Language): void {
    currentLanguage = lang;
    document.documentElement.lang = lang;
    if (currentLabel) currentLabel.textContent = LANGUAGE_LABELS[lang];

    options.forEach((opt) => {
      opt.setAttribute('aria-checked', String(opt.dataset.langOption === lang));
    });

    for (const { node, attribute, messages } of bindings) {
      if (attribute) {
        node.setAttribute(attribute, messages[lang]);
      } else {
        node.textContent = messages[lang];
      }
    }

    document.querySelectorAll<HTMLElement>('[data-language]').forEach((section) => {
      section.hidden = section.dataset.language !== lang;
    });

    document.querySelectorAll<HTMLElement>('[data-section-id]').forEach((target) => {
      const container = target.closest<HTMLElement>('[data-language]');
      if (container?.hidden) {
        target.removeAttribute('id');
      } else if (target.dataset.sectionId) {
        target.id = target.dataset.sectionId;
      }
    });

    for (const fn of listeners) fn(lang);
  }

  function choose(lang: Language): void {
    if (!(SUPPORTED as readonly string[]).includes(lang)) return;
    try {
      sessionStorage.setItem(LANGUAGE_KEY, lang);
    } catch {
      // Ignore storage errors in restricted contexts.
    }
    apply(lang);
    setMenuOpen(false, true);
  }

  apply(savedLanguage() ?? browserLanguage());

  if (picker && trigger && menu) {
    picker.hidden = false;

    trigger.addEventListener('click', () => {
      setMenuOpen(menu.hidden);
    });

    trigger.addEventListener('keydown', (event) => {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        setMenuOpen(true);
      }
    });

    options.forEach((opt, index) => {
      opt.addEventListener('click', () => {
        const next = opt.dataset.langOption as Language | undefined;
        if (next) choose(next);
      });

      opt.addEventListener('keydown', (event) => {
        if (event.key === 'ArrowDown') {
          event.preventDefault();
          options[(index + 1) % options.length]?.focus();
        } else if (event.key === 'ArrowUp') {
          event.preventDefault();
          options[(index - 1 + options.length) % options.length]?.focus();
        } else if (event.key === 'Home') {
          event.preventDefault();
          options[0]?.focus();
        } else if (event.key === 'End') {
          event.preventDefault();
          options[options.length - 1]?.focus();
        } else if (event.key === 'Escape') {
          event.preventDefault();
          setMenuOpen(false, true);
        } else if (event.key === 'Tab') {
          setMenuOpen(false, false);
        }
      });
    });

    document.addEventListener('click', (event) => {
      if (!menu.hidden && !picker.contains(event.target as Node)) {
        setMenuOpen(false, false);
      }
    });

    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && !menu.hidden) {
        setMenuOpen(false, true);
      }
    });
  }

  window.addEventListener('pageshow', (event) => {
    if (event.persisted) {
      apply(savedLanguage() ?? currentLanguage);
    }
  });
}
