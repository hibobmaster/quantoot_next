const languageKey = 'quantoot-language';
const languageSelect = document.querySelector('[data-select-language]');
const supportedLanguages = ['en', 'zh-Hans', 'zh-Hant'];
const translations = [...document.querySelectorAll('[data-zh-hans]')].map(node => {
  const attribute = node.dataset.translate;
  return {
    node,
    attribute,
    messages: {
      en: attribute ? node.getAttribute(attribute) : node.textContent,
      'zh-Hans': node.dataset.zhHans,
      'zh-Hant': node.dataset.zhHant,
    },
  };
});

function browserLanguage() {
  for (const tag of navigator.languages) {
    const [language, variant] = tag.toLowerCase().split('-');
    if (language === 'en') return 'en';
    if (language === 'zh') {
      return ['hant', 'tw', 'hk', 'mo'].includes(variant) ? 'zh-Hant' : 'zh-Hans';
    }
  }
  return 'en';
}

function savedLanguage() {
  try {
    const stored = sessionStorage.getItem(languageKey);
    if (stored === 'zh') return 'zh-Hans';
    if (supportedLanguages.includes(stored)) return stored;
  } catch {
    // Language selection still works when browser storage is disabled.
  }
  return null;
}

function renderLanguage(language) {
  document.documentElement.lang = language;
  languageSelect.value = language;
  translations.forEach(({ node, attribute, messages }) => {
    if (attribute) node.setAttribute(attribute, messages[language]);
    else node.textContent = messages[language];
  });
  document.querySelectorAll('[data-language]').forEach(section => {
    section.hidden = section.dataset.language !== language;
  });
  document.querySelectorAll('[data-section-id]').forEach(target => {
    if (target.closest('[data-language]').hidden) target.removeAttribute('id');
    else target.id = target.dataset.sectionId;
  });
}

renderLanguage(savedLanguage() ?? browserLanguage());
languageSelect.hidden = false;
languageSelect.addEventListener('change', () => {
  const language = languageSelect.value;
  try { sessionStorage.setItem(languageKey, language); } catch {}
  renderLanguage(language);
});
window.addEventListener('pageshow', event => {
  if (event.persisted) renderLanguage(savedLanguage() ?? languageSelect.value);
});

async function loadAndroidRelease(section) {
  const loading = section.querySelector('[data-release-loading]');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8000);
  loading.hidden = false;
  try {
    const response = await fetch('/api/android', { signal: controller.signal });
    if (response.status === 404) {
      section.querySelector('[data-release-missing]').hidden = false;
      return;
    }
    if (!response.ok) throw new Error(`Android release request returned ${response.status}`);

    const release = await response.json();
    section.querySelector('[data-release-version]').textContent = `v${release.version}`;
    section.querySelectorAll('[data-abi]').forEach(item => {
      const url = release.apks[item.dataset.abi];
      if (!url) return;
      item.querySelector('a').href = url;
      item.hidden = false;
    });
    if (release.checksums) {
      const link = section.querySelector('[data-release-checksums]');
      link.href = release.checksums;
      link.hidden = false;
    }
    section.querySelector('[data-release-ready]').hidden = false;
    section.querySelector('[data-release-apks]').hidden = false;
  } catch {
    section.querySelector('[data-release-error]').hidden = false;
  } finally {
    clearTimeout(timeout);
    loading.hidden = true;
  }
}

const androidDownloads = document.querySelector('[data-android-downloads]');
if (androidDownloads) loadAndroidRelease(androidDownloads);
