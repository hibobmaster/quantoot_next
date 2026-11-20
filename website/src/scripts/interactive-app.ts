import { getLanguage, onLanguageChange, type Language } from './i18n';

const VISIBILITY_ORDER = ['public', 'unlisted', 'private', 'direct'] as const;
type VisibilityKey = (typeof VISIBILITY_ORDER)[number];

const VISIBILITY_LABELS: Record<VisibilityKey, Record<Language, string>> = {
  public: { en: 'Public', 'zh-Hans': '公开', 'zh-Hant': '公開' },
  unlisted: { en: 'Quiet public', 'zh-Hans': '不公开', 'zh-Hant': '不公開' },
  private: { en: 'Followers', 'zh-Hans': '关注者', 'zh-Hant': '跟隨者' },
  direct: { en: 'Private mention', 'zh-Hans': '私信', 'zh-Hant': '私訊' },
};

const CW_BUTTON_LABELS: Record<'show' | 'hide', Record<Language, string>> = {
  show: { en: 'Show', 'zh-Hans': '显示', 'zh-Hant': '顯示' },
  hide: { en: 'Hide', 'zh-Hans': '隐藏', 'zh-Hant': '隱藏' },
};

const ADAPTIVE_CAPTIONS: Record<'phone' | 'cover' | 'foldable' | 'tablet', Record<Language, string>> = {
  phone: {
    en: 'A single serene reading column with effortless thumb navigation — made for morning commutes and quiet breaks.',
    'zh-Hans': '单栏舒展阅读，配合顺手的底部导航与发帖入口，适合通勤途中与片刻小憩。',
    'zh-Hant': '單欄舒展閱讀，搭配順手的底部導覽與發文入口，適合通勤途中與片刻小憩。',
  },
  cover: {
    en: 'On a folded Duo cover screen, QuanToot shifts navigation and compose controls into floating side-edge glass pills — keeping your whole timeline clear for one-handed reading.',
    'zh-Hans': '在合起的折叠外屏上，导航与发帖按钮自然收拢为右侧悬浮竖向胶囊，把完整宽幅留给单手阅读。',
    'zh-Hant': '在合起的摺疊外螢幕上，導覽與發文按鈕自然收攏為右側懸浮直向膠囊，把完整寬幅留給單手閱讀。',
  },
  foldable: {
    en: 'Open it like a printed book. Your timeline rests naturally on the left page while threads, profiles, and hashtags unfold beside it.',
    'zh-Hans': '如翻开一本精装书：左侧安放你的时间线，右侧自然展开对话、个人资料与话题广场。',
    'zh-Hant': '如翻開一本精裝書：左側安放你的時間軸，右側自然展開對話、個人資料與話題廣場。',
  },
  tablet: {
    en: 'On iPad and tablets, QuanToot opens into a spacious reading desk — side navigation, Home timeline, and Explore side by side.',
    'zh-Hans': '在 iPad 与平板上舒展为宽阔书桌：侧边导航、首页时间线与探索发现并排呈现。',
    'zh-Hant': '在 iPad 與平板上舒展為寬闊書桌：側邊導覽、首頁時間軸與探索發現並排呈現。',
  },
};

const REQ_FEEDBACK: Record<'approved' | 'rejected', Record<Language, string>> = {
  approved: { en: 'Approved', 'zh-Hans': '已批准', 'zh-Hant': '已核准' },
  rejected: { en: 'Rejected', 'zh-Hans': '已拒绝', 'zh-Hant': '已拒絕' },
};

const SANCTUARY_PILLS: Record<'active' | 'switch', Record<Language, string>> = {
  active: { en: 'Active', 'zh-Hans': '当前', 'zh-Hant': '目前' },
  switch: { en: 'Switch', 'zh-Hans': '切换', 'zh-Hant': '切換' },
};

const LIGHTBOX_PHOTOS: Record<string, { src: string; alt: Record<Language, string> }> = {
  bookshop: {
    src: '/assets/bookshop.jpg',
    alt: {
      en: 'Warm late-afternoon sunlight crossing a wooden reading table with an open hardcover book and a steaming dark ceramic teacup.',
      'zh-Hans': '午后斜阳穿过木质阅读桌，桌上是摊开的精装书与一杯冒着热气的深色陶杯热茶。',
      'zh-Hant': '午後斜陽灑落木質閱讀桌，桌上是攤開的精裝書與一杯冒著熱氣的深色陶杯熱茶。',
    },
  },
  twilight: {
    src: '/assets/twilight.jpg',
    alt: {
      en: 'A quiet timber boardwalk beside misty evergreens overlooking calm twilight water, lit by a single warm lantern.',
      'zh-Hans': '暮色薄雾中的常青林畔，一座木质长廊伸向平静的湖面，尽头亮着一盏温暖提灯。',
      'zh-Hant': '暮色薄霧中的常青林畔，一座木質長廊伸向平靜的湖面，盡頭亮著一盞溫暖提燈。',
    },
  },
};

export function initInteractiveApp(): void {
  // 1. Scroll-Synchronized + Clickable Narrative Chapters
  const chapters = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-story-chapter]'));
  const mobileTabs = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-story-tab]'));
  const panels = Array.from(document.querySelectorAll<HTMLElement>('[data-sim-panel]'));
  const dockButtons = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-sim-dock]'));
  let activeChapter = 'timelines';
  let userPinnedUntil = 0;

  function selectChapter(name: string, fromUser = false): void {
    if (fromUser) userPinnedUntil = Date.now() + 1100;
    if (activeChapter === name && !fromUser) return;
    activeChapter = name;

    chapters.forEach((btn) => {
      const active = btn.dataset.storyChapter === name;
      btn.classList.toggle('is-active', active);
      btn.setAttribute('aria-pressed', String(active));
    });
    mobileTabs.forEach((tab) => {
      tab.setAttribute('aria-selected', String(tab.dataset.storyTab === name));
    });
    panels.forEach((panel) => {
      panel.hidden = panel.dataset.simPanel !== name;
    });

    const dockMap: Record<string, string> = {
      timelines: 'home',
      threads: 'threads',
      composer: 'composer',
      explore: 'explore',
    };
    const activeDock = dockMap[name] ?? 'home';
    dockButtons.forEach((btn) => {
      btn.setAttribute('aria-current', String(btn.dataset.simDock === activeDock));
    });

    const screenBody = document.querySelector<HTMLElement>('[data-sim-scroll]');
    if (screenBody) screenBody.scrollTop = 0;
  }

  chapters.forEach((btn) => {
    btn.addEventListener('click', () => {
      const target = btn.dataset.storyChapter;
      if (target) selectChapter(target, true);
    });
  });

  mobileTabs.forEach((tab, index) => {
    tab.addEventListener('click', () => {
      const target = tab.dataset.storyTab;
      if (target) selectChapter(target, true);
    });
    tab.addEventListener('keydown', (event) => {
      if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
      event.preventDefault();
      const step = event.key === 'ArrowRight' ? 1 : -1;
      const next = mobileTabs[(index + step + mobileTabs.length) % mobileTabs.length];
      if (!next) return;
      const target = next.dataset.storyTab;
      if (target) selectChapter(target, true);
      next.focus();
    });
  });

  dockButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      const dock = btn.dataset.simDock;
      if (dock === 'home') selectChapter('timelines', true);
      else if (dock === 'threads') selectChapter('threads', true);
      else if (dock === 'composer') selectChapter('composer', true);
      else if (dock === 'explore') selectChapter('explore', true);
    });
  });

  // Scroll-triggered chapter sync on desktop viewports
  if ('IntersectionObserver' in window && chapters.length > 0) {
    const chapterObserver = new IntersectionObserver(
      (entries) => {
        if (window.innerWidth < 960 || Date.now() < userPinnedUntil) return;
        for (const entry of entries) {
          if (entry.isIntersecting) {
            const name = (entry.target as HTMLElement).dataset.storyChapter;
            if (name) selectChapter(name, false);
          }
        }
      },
      { rootMargin: '-34% 0px -46% 0px', threshold: 0.05 }
    );
    chapters.forEach((ch) => chapterObserver.observe(ch));
  }

  // 2. In-Phone Day / Night Theme Toggle
  const simScreen = document.querySelector<HTMLElement>('[data-sim-screen]');
  const previewThemeBtns = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-preview-theme]'));
  previewThemeBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      const mode = btn.dataset.previewTheme;
      if (!simScreen || !mode) return;
      simScreen.classList.toggle('app-surface-day', mode === 'day');
      simScreen.classList.toggle('app-surface-night', mode === 'night');
      previewThemeBtns.forEach((b) => {
        b.setAttribute('aria-pressed', String(b.dataset.previewTheme === mode));
      });
    });
  });

  // 3. Home / Local / Federated Sub-Tabs
  const feedTabs = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-feed-tab]'));
  const feedLists = Array.from(document.querySelectorAll<HTMLElement>('[data-feed-list]'));
  feedTabs.forEach((btn) => {
    btn.addEventListener('click', () => {
      const feed = btn.dataset.feedTab;
      feedTabs.forEach((t) => t.setAttribute('aria-selected', String(t.dataset.feedTab === feed)));
      feedLists.forEach((list) => {
        list.hidden = list.dataset.feedList !== feed;
      });
    });
  });

  // 4. Deferred New Posts Pill
  const pendingBtn = document.querySelector<HTMLButtonElement>('[data-reveal-pending]');
  const pendingPost = document.querySelector<HTMLElement>('[data-pending-post]');
  const pendingWrap = document.querySelector<HTMLElement>('[data-pending-wrap]');
  if (pendingBtn && pendingPost && pendingWrap) {
    pendingBtn.addEventListener('click', () => {
      pendingPost.hidden = false;
      pendingWrap.hidden = true;
    });
  }

  // 5. Interactive Like / Boost / Bookmark Microinteractions
  document.querySelectorAll<HTMLButtonElement>('[data-toggle-action]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const active = btn.classList.toggle('is-active');
      btn.setAttribute('aria-pressed', String(active));
      const countNode = btn.querySelector<HTMLElement>('[data-count]');
      if (countNode) {
        const base = Number(countNode.dataset.base ?? countNode.textContent ?? '0');
        countNode.textContent = String(active ? base + 1 : base);
      }
    });
  });

  // 6. Content Warning Toggle
  const cwToggle = document.querySelector<HTMLButtonElement>('[data-cw-toggle]');
  const cwBody = document.querySelector<HTMLElement>('[data-cw-body]');
  function updateCwLabel(): void {
    if (!cwToggle || !cwBody) return;
    const isHidden = cwBody.hidden;
    cwToggle.textContent = CW_BUTTON_LABELS[isHidden ? 'show' : 'hide'][getLanguage()];
    cwToggle.setAttribute('aria-expanded', String(!isHidden));
  }
  if (cwToggle && cwBody) {
    cwToggle.addEventListener('click', () => {
      cwBody.hidden = !cwBody.hidden;
      updateCwLabel();
    });
    onLanguageChange(updateCwLabel);
  }

  // 7. Interactive Composer Controls
  const visBtn = document.querySelector<HTMLButtonElement>('[data-composer-visibility]');
  const visLabel = visBtn?.querySelector<HTMLElement>('[data-visibility-text]');
  let visIndex = 0;

  function renderVisibility(): void {
    if (!visLabel) return;
    visLabel.textContent = VISIBILITY_LABELS[VISIBILITY_ORDER[visIndex]][getLanguage()];
  }
  if (visBtn) {
    visBtn.addEventListener('click', () => {
      visIndex = (visIndex + 1) % VISIBILITY_ORDER.length;
      renderVisibility();
    });
    onLanguageChange(renderVisibility);
  }

  const cwChip = document.querySelector<HTMLButtonElement>('[data-composer-cw]');
  const cwInputPreview = document.querySelector<HTMLElement>('[data-composer-cw-box]');
  if (cwChip && cwInputPreview) {
    cwChip.addEventListener('click', () => {
      const active = cwInputPreview.hidden;
      cwInputPreview.hidden = !active;
      cwChip.setAttribute('aria-pressed', String(active));
    });
  }

  const pollOptions = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-poll-opt]'));
  pollOptions.forEach((opt) => {
    opt.addEventListener('click', () => {
      pollOptions.forEach((item) => {
        item.setAttribute('aria-pressed', String(item === opt));
        const fill = item.querySelector<HTMLElement>('.sim-poll-fill');
        const pct = item.querySelector<HTMLElement>('[data-poll-pct]');
        if (fill && item.dataset.fillClass) {
          fill.classList.remove('sim-poll-w-0');
          fill.classList.add(item.dataset.fillClass);
        }
        if (pct && item.dataset.pct) pct.textContent = item.dataset.pct;
      });
    });
  });

  const publishBtn = document.querySelector<HTMLButtonElement>('[data-composer-publish]');
  const publishToast = document.querySelector<HTMLElement>('[data-composer-toast]');
  let toastTimer: number | undefined;
  if (publishBtn && publishToast) {
    publishBtn.addEventListener('click', () => {
      publishToast.hidden = false;
      window.clearTimeout(toastTimer);
      toastTimer = window.setTimeout(() => {
        publishToast.hidden = true;
      }, 2600);
    });
  }

  // 8. Multi-Photo Full-Screen Gallery Lightbox inside Phone
  const lightbox = document.querySelector<HTMLElement>('[data-sim-lightbox]');
  const lightboxImg = document.querySelector<HTMLImageElement>('[data-lightbox-img]');
  const lightboxAlt = document.querySelector<HTMLElement>('[data-lightbox-alt-text]');
  const closeLightboxBtn = document.querySelector<HTMLButtonElement>('[data-close-lightbox]');
  const zoomLightboxBtn = document.querySelector<HTMLButtonElement>('[data-zoom-lightbox]');
  let activePhotoKey = 'bookshop';

  function renderLightboxPhoto(): void {
    const photo = LIGHTBOX_PHOTOS[activePhotoKey];
    if (!photo) return;
    if (lightboxImg) lightboxImg.src = photo.src;
    if (lightboxAlt) lightboxAlt.textContent = photo.alt[getLanguage()];
  }

  document.querySelectorAll<HTMLButtonElement>('[data-open-photo]').forEach((btn) => {
    btn.addEventListener('click', () => {
      activePhotoKey = btn.dataset.openPhoto ?? 'bookshop';
      renderLightboxPhoto();
      if (lightbox) lightbox.hidden = false;
    });
  });
  onLanguageChange(renderLightboxPhoto);

  if (closeLightboxBtn && lightbox) {
    closeLightboxBtn.addEventListener('click', () => {
      lightbox.hidden = true;
      lightboxImg?.classList.remove('is-zoomed');
      zoomLightboxBtn?.setAttribute('aria-pressed', 'false');
    });
  }
  if (zoomLightboxBtn && lightboxImg) {
    zoomLightboxBtn.addEventListener('click', () => {
      const zoomed = lightboxImg.classList.toggle('is-zoomed');
      zoomLightboxBtn.setAttribute('aria-pressed', String(zoomed));
    });
  }

  // 9. Adaptive Hardware Morph Showcase
  const formButtons = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-form-switch]'));
  const adaptiveDevice = document.querySelector<HTMLElement>('[data-adaptive-device]');
  const adaptiveCaption = document.querySelector<HTMLElement>('[data-adaptive-caption]');
  let currentForm: 'phone' | 'cover' | 'foldable' | 'tablet' = 'cover';

  function renderAdaptive(): void {
    if (adaptiveDevice) adaptiveDevice.dataset.form = currentForm;
    formButtons.forEach((btn) => {
      btn.setAttribute('aria-pressed', String(btn.dataset.formSwitch === currentForm));
    });
    if (adaptiveCaption) {
      adaptiveCaption.textContent = ADAPTIVE_CAPTIONS[currentForm][getLanguage()];
    }
  }

  if (adaptiveDevice && formButtons.length > 0) {
    renderAdaptive();
    formButtons.forEach((btn) => {
      btn.addEventListener('click', () => {
        const next = btn.dataset.formSwitch as 'phone' | 'cover' | 'foldable' | 'tablet';
        if (!next) return;
        currentForm = next;
        renderAdaptive();
      });
    });
    onLanguageChange(renderAdaptive);
  }

  // 10. Curated Feature Specimens Interactions
  // A. Follow Request Approve / Reject
  const reqActions = document.querySelector<HTMLElement>('[data-req-actions]');
  const reqStatus = document.querySelector<HTMLElement>('[data-req-status]');
  let reqState: 'approved' | 'rejected' | null = null;
  function renderReqStatus(): void {
    if (!reqStatus || !reqState) return;
    reqStatus.textContent = REQ_FEEDBACK[reqState][getLanguage()];
  }
  document.querySelectorAll<HTMLButtonElement>('[data-req-decision]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const decision = btn.dataset.reqDecision as 'approved' | 'rejected';
      reqState = decision;
      if (reqActions) reqActions.hidden = true;
      if (reqStatus) {
        reqStatus.hidden = false;
        renderReqStatus();
      }
    });
  });
  onLanguageChange(renderReqStatus);

  // B. Interactive ALT Lens over Twilight Photo
  const altFrame = document.querySelector<HTMLElement>('[data-alt-frame]');
  const altLensBtn = document.querySelector<HTMLButtonElement>('[data-alt-lens]');
  const altLensSheet = document.querySelector<HTMLElement>('[data-alt-sheet]');
  if (altFrame && altLensBtn && altLensSheet) {
    altLensBtn.addEventListener('click', () => {
      const open = altLensSheet.hidden;
      altLensSheet.hidden = !open;
      altFrame.classList.toggle('is-alt-open', open);
      altLensBtn.setAttribute('aria-expanded', String(open));
    });
  }

  // C. Multi-Account Sanctuary Switcher
  const sanctuaryCards = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-sanctuary-card]'));
  function renderSanctuaryPills(): void {
    const lang = getLanguage();
    sanctuaryCards.forEach((item) => {
      const active = item.getAttribute('aria-pressed') === 'true';
      const pill = item.querySelector<HTMLElement>('.sanctuary-pill');
      if (pill) {
        pill.textContent = SANCTUARY_PILLS[active ? 'active' : 'switch'][lang];
      }
    });
  }
  sanctuaryCards.forEach((card) => {
    card.addEventListener('click', () => {
      sanctuaryCards.forEach((item) => item.setAttribute('aria-pressed', String(item === card)));
      renderSanctuaryPills();
    });
  });
  onLanguageChange(renderSanctuaryPills);

  // D. Keyword Filter Shield Toggle
  const filterBtn = document.querySelector<HTMLButtonElement>('[data-filter-toggle]');
  const filterShield = document.querySelector<HTMLElement>('[data-filter-shield]');
  const filterPlain = document.querySelector<HTMLElement>('[data-filter-plain]');
  if (filterBtn && filterShield && filterPlain) {
    filterBtn.addEventListener('click', () => {
      const shielded = filterShield.hidden;
      filterShield.hidden = !shielded;
      filterPlain.hidden = shielded;
      filterBtn.setAttribute('aria-pressed', String(shielded));
    });
  }
}
