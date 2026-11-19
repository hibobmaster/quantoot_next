const translations = [...document.querySelectorAll('[data-zh]')].map(node => ({
  node, en: node.textContent, zh: node.dataset.zh,
}));
const switcher = document.querySelector('[data-toggle-lang]');
const browserLanguage = navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en';
let language = browserLanguage;
try {
  const stored = sessionStorage.getItem('quantoot-language');
  if (stored === 'zh' || stored === 'en') language = stored;
} catch {
  // The page still works if browser storage is unavailable.
}
function render() {
  document.documentElement.lang = language === 'zh' ? 'zh-Hans' : 'en';
  translations.forEach(({node, en, zh}) => { node.textContent = language === 'zh' ? zh : en; });
  if (switcher) {
    switcher.textContent = language === 'zh' ? 'EN' : '中文';
    switcher.setAttribute('aria-label', language === 'zh' ? 'Read in English' : '切换到简体中文');
  }
  document.querySelectorAll('[data-language]').forEach(section => {
    section.hidden = section.dataset.language !== language;
    section.querySelectorAll('[data-section-id]').forEach(heading => {
      if (section.hidden) heading.removeAttribute('id');
      else heading.id = heading.dataset.sectionId;
    });
  });
}
switcher?.addEventListener('click', () => {
  language = language === 'zh' ? 'en' : 'zh';
  try { sessionStorage.setItem('quantoot-language', language); } catch {}
  render();
});
render();
