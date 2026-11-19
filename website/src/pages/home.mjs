import { icons, renderPage, tr } from './layout.mjs';

export function renderHomePage() {
  const body = `
  <main id="main">
    <!-- 1. Monumental Editorial Hero & Sculptural 3-Device Triptych -->
    <section class="hero-section" data-hero>
      <div class="wrap">
        <div class="hero-masthead">
          <div class="hero-emblem-row">
            <span class="hero-emblem-ring"><img src="/assets/quantoot.png" width="40" height="40" alt=""></span>
            <p class="hero-kicker" ${tr('An independent Mastodon client', '独立的 Mastodon 客户端', '獨立的 Mastodon 用戶端')}>An independent Mastodon client</p>
          </div>
          <h1 class="hero-title">
            <span class="hero-line"><span ${tr('The social web,', '开放社交，', '開放社交，')}>The social web,</span></span>
            <span class="hero-line"><em ${tr('at your pace.', '自在一点。', '自在一點。')}>at your pace.</em></span>
          </h1>
          <p class="hero-intro hero-fade" ${tr(
            'A calm, tactile home for the servers and people you care about — crafted for iPhone, iPad, Android, and foldables.',
            '为你珍视的实例与人们，留一间安静、顺手、毫无打扰的阅读与交流空间——原生适配 iPhone、iPad、Android 与折叠屏。',
            '為你珍視的站台與人們，留一間安靜、順手、毫無打擾的閱讀與交流空間——原生適配 iPhone、iPad、Android 與摺疊螢幕。'
          )}>A calm, tactile home for the servers and people you care about — crafted for iPhone, iPad, Android, and foldables.</p>
          <div class="hero-actions hero-fade">
            <a class="btn btn-primary" href="#android">
              ${icons.download}
              <span ${tr('Download for Android', '下载 Android 版', '下載 Android 版')}>Download for Android</span>
            </a>
            <a class="btn btn-secondary" href="#experience">
              <span ${tr('Walk through the app', '沉浸体验界面', '沉浸體驗介面')}>Walk through the app</span>
              <span class="icon-shift">${icons.arrowRight}</span>
            </a>
          </div>
          <div class="hero-quiet-facts hero-fade">
            <span><i aria-hidden="true"></i><span ${tr('Choose any Mastodon server', '支持任意 Mastodon 实例', '支援任意 Mastodon 站台')}>Choose any Mastodon server</span></span>
            <span><i aria-hidden="true"></i><span ${tr('Browse public posts as a guest', '支持访客公开浏览', '支援訪客公開瀏覽')}>Browse public posts as a guest</span></span>
            <span><i aria-hidden="true"></i><span ${tr('No ads or tracking', '无广告、无追踪', '無廣告、無追蹤')}>No ads or tracking</span></span>
          </div>
        </div>

        <!-- Sculptural 3-Device Triptych Stage -->
        <figure class="triptych-stage" data-triptych-stage aria-label="Illustrative preview of QuanToot on phones and foldables" data-translate="aria-label" ${tr('Illustrative preview of QuanToot on phones and foldables', 'QuanToot 手机与折叠屏界面预览', 'QuanToot 手機與摺疊螢幕介面預覽')}>
          <svg class="triptych-orbit" viewBox="0 0 800 600" fill="none" aria-hidden="true">
            <g class="triptych-orbit-spin">
              <circle cx="400" cy="300" r="190" stroke="#f5f0e2" stroke-opacity="0.32" stroke-width="1"/>
              <circle cx="400" cy="300" r="270" stroke="#f5f0e2" stroke-opacity="0.2" stroke-dasharray="4 9" stroke-width="1"/>
              <circle cx="400" cy="300" r="350" stroke="#f5f0e2" stroke-opacity="0.14" stroke-width="1"/>
              <circle cx="400" cy="30" r="6" fill="#ff9c82" fill-opacity="0.88"/>
              <circle cx="670" cy="300" r="5" fill="#f5f0e2" fill-opacity="0.72"/>
              <circle cx="166" cy="435" r="5" fill="#a6d5c5" fill-opacity="0.82"/>
              <circle cx="590" cy="490" r="4" fill="#ff9c82" fill-opacity="0.65"/>
            </g>
          </svg>

          <div class="triptych-meta">
            <span class="triptych-pill"><span class="triptych-pill-dot" aria-hidden="true"></span><span ${tr('Illustrative preview', '界面预览', '介面預覽')}>Illustrative preview</span></span>
            <span class="triptych-note" ${tr('Night composer · Book-fold spread · Folded Duo side-edge chrome', '夜间发帖 · 折叠屏双页阅读 · 合盖外屏侧边悬浮导航', '夜間發文 · 摺疊螢幕雙頁閱讀 · 合蓋外螢幕側邊懸浮導覽')}>Night composer · Book-fold spread · Folded Duo side-edge chrome</span>
          </div>

          <div class="triptych-devices" aria-hidden="true">
            <!-- Left Wing: iPhone Night Composer -->
            <div class="wing-device wing-left" data-wing-left>
              <div class="wing-screen app-surface-night">
                <div class="iphone-status">
                  <span>9:41</span>
                  <span class="dynamic-island"></span>
                  <span>5G</span>
                </div>
                <div class="qt-pane-top">
                  <span ${tr('New post', '新嘟文', '新嘟文')}>New post</span>
                  <span class="wing-publish" ${tr('Publish', '发布', '發佈')}>Publish</span>
                </div>
                <div class="wing-pad">
                  <div class="wing-cw" ${tr('Content warning · Evening reading', '内容警告 · 晚间书单', '內容警告 · 晚間書單')}>Content warning · Evening reading</div>
                  <p class="qt-post-text" ${tr('How do you unwind with your timeline at the end of the day?', '一天结束时，你更喜欢怎样翻看时间线？', '一天結束時，你更喜歡怎樣翻看時間軸？')}>How do you unwind with your timeline at the end of the day?</p>
                  <div class="wing-poll">
                    <div class="wing-poll-row"><span class="wing-poll-fill wing-poll-68"></span><span ${tr('Warm tea & bookmarks', '热茶配书签', '熱茶配書籤')}>Warm tea &amp; bookmarks</span><span>68%</span></div>
                    <div class="wing-poll-row"><span class="wing-poll-fill wing-poll-32"></span><span ${tr('Quick local catch-up', '随手逛逛本站', '隨手逛逛在地')}>Quick local catch-up</span><span>32%</span></div>
                  </div>
                  <div class="wing-chips">
                    <span class="wing-chip" ${tr('Quiet public', '不公开', '不公開')}>Quiet public</span>
                    <span class="wing-chip">ALT</span>
                    <span class="wing-chip">EN</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Centerpiece: Authentic Unfolded Duo (Two-Pane Spread + Right-Edge Vertical Glass Chrome) -->
            <div class="center-duo" data-center-duo>
              <div class="duo-screen app-surface-day">
                <div class="duo-pane">
                  <div class="duo-pane-heading">
                    <strong>QuanToot</strong>
                    <small>mastodon.social</small>
                  </div>
                  <div class="qt-tabs">
                    <span class="qt-tab-item is-active" ${tr('Home', '主页', '首頁')}>Home</span>
                    <span class="qt-tab-item" ${tr('Local', '本站', '在地')}>Local</span>
                    <span class="qt-tab-item" ${tr('Federated', '跨站', '聯邦')}>Federated</span>
                  </div>
                  <article class="qt-post">
                    <div class="qt-avatar qt-avatar-pine">L</div>
                    <div class="qt-post-body">
                      <div class="qt-post-head"><strong>Lina</strong><time ${tr('14m', '14 分钟前', '14 分鐘前')}>14m</time></div>
                      <span class="qt-handle">@lina@studio.example</span>
                      <p class="qt-post-text"><span ${tr('Morning field notes. The lake was quiet enough to hear the mist drift past the pier. ', '清晨笔记：湖面静得连雾气掠过木栈道的声音都听得见。', '清晨筆記：湖面靜得連霧氣掠過木棧道的聲音都聽得見。')}>Morning field notes. The lake was quiet enough to hear the mist drift past the pier. </span><span class="qt-hashtag">#FieldNotes</span></p>
                      <div class="qt-media-wrap">
                        <img src="/assets/lake.jpg" width="800" height="450" alt="">
                        <span class="qt-alt-tag">ALT</span>
                      </div>
                      <div class="qt-post-actions">
                        <span>${icons.reply} 4</span>
                        <span>${icons.boost} 7</span>
                        <span class="is-liked">${icons.heart} 23</span>
                        <span>${icons.bookmark}</span>
                      </div>
                    </div>
                  </article>
                </div>

                <div class="duo-pane duo-pane-right">
                  <div class="duo-pane-heading">
                    <strong ${tr('Conversation', '对话', '對話')}>Conversation</strong>
                  </div>
                  <article class="qt-post">
                    <div class="qt-avatar qt-avatar-warm">A</div>
                    <div class="qt-post-body">
                      <div class="qt-post-head"><strong>Amir</strong><time ${tr('1h', '1 小时前', '1 小時前')}>1h</time></div>
                      <span class="qt-handle">@amir@journal.example</span>
                      <p class="qt-post-text" ${tr('Chronological feeds feel different once the room around them slows down.', '当阅读空间真正安静下来，按时间流淌的时间线会呈现出完全不同的质感。', '當閱讀空間真正安靜下來，按時間流淌的時間軸會呈現出完全不同的質感。')}>Chronological feeds feel different once the room around them slows down.</p>
                      <div class="qt-quote-card">
                        <div class="qt-quote-author"><strong>Mika</strong><span>@mika@notes.example</span></div>
                        <p ${tr('Good reading tools let you reach the bottom of the page and put the phone down.', '好的阅读工具，会让你安心读完最后一条，然后从容放下手机。', '好的閱讀工具，會讓你安心讀完最後一則，然後從容放下手機。')}>Good reading tools let you reach the bottom of the page and put the phone down.</p>
                      </div>
                    </div>
                  </article>
                  <article class="qt-post">
                    <div class="qt-post-body qt-reply-indent">
                      <div class="qt-post-head"><strong>Sora</strong><time ${tr('22m', '22 分钟前', '22 分鐘前')}>22m</time></div>
                      <span class="qt-handle">@sora@tea.example</span>
                      <p class="qt-post-text" ${tr('Opening a thread beside the timeline feels just like reading a printed journal.', '在时间线旁并排展开讨论，就像摊开一本纸质杂志一样舒服。', '在時間軸旁並排展開討論，就像攤開一本紙本雜誌一樣舒服。')}>Opening a thread beside the timeline feels just like reading a printed journal.</p>
                    </div>
                  </article>
                  <div class="duo-reply-bar">
                    <span class="duo-reply-avatar">${icons.user}</span>
                    <span class="duo-reply-pill"><span ${tr('Reply', '回复', '回覆')}>Reply</span>${icons.edit}</span>
                  </div>
                </div>

                <div class="duo-cover-rail duo-unfolded-rail">
                  <span class="duo-cover-time">9:41</span>
                  <span class="duo-circle-btn">${icons.close}</span>
                  <span class="duo-circle-btn">${icons.dots}</span>
                  <div class="duo-glass-pill duo-glass-pill-bottom">
                    <span class="duo-pill-icon is-active">${icons.home}</span>
                    <span class="duo-pill-icon">${icons.search}</span>
                    <span class="duo-pill-icon">${icons.bell}</span>
                    <span class="duo-pill-icon">${icons.user}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Right Wing: Authentic Folded Duo Cover Display with Vertical Side-Edge Glass Pills -->
            <div class="wing-duo-cover" data-wing-right>
              <div class="duo-cover-screen app-surface-day">
                <div class="duo-cover-main">
                  <div class="duo-cover-title">
                    <strong>QuanToot</strong>
                    <small>mastodon.social</small>
                  </div>
                  <div class="qt-tabs">
                    <span class="qt-tab-item is-active" ${tr('Home', '主页', '首頁')}>Home</span>
                    <span class="qt-tab-item" ${tr('Local', '本站', '在地')}>Local</span>
                    <span class="qt-tab-item" ${tr('Federated', '跨站', '聯邦')}>Federated</span>
                  </div>
                  <article class="qt-post">
                    <div class="qt-avatar qt-avatar-slate">M</div>
                    <div class="qt-post-body">
                      <div class="qt-post-head"><strong>Mika</strong><time ${tr('38m', '38m', '38m')}>38m</time></div>
                      <span class="qt-handle">@mika@notes.example</span>
                      <p class="qt-post-text" ${tr('Sun-lit corner table and an hour of quiet.', '午后阳光书桌，安静的一小时。', '午後陽光書桌，安靜的一小時。')}>Sun-lit corner table and an hour of quiet.</p>
                      <div class="qt-media-wrap">
                        <img src="/assets/bookshop.jpg" width="960" height="540" alt="">
                        <span class="qt-alt-tag">ALT</span>
                      </div>
                    </div>
                  </article>
                </div>
                <div class="duo-cover-rail">
                  <span class="duo-cover-cam"></span>
                  <span class="duo-cover-time">9:41</span>
                  <div class="duo-glass-pill">
                    <span class="duo-pill-icon">${icons.edit}</span>
                    <span class="duo-pill-icon">${icons.dots}</span>
                  </div>
                  <div class="duo-glass-pill duo-glass-pill-bottom">
                    <span class="duo-pill-icon is-active">${icons.home}</span>
                    <span class="duo-pill-icon">${icons.search}</span>
                    <span class="duo-pill-icon">${icons.bell}</span>
                    <span class="duo-pill-icon">${icons.user}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </figure>
      </div>
    </section>

    <!-- 2. Quiet Editorial Pause -->
    <section class="editorial-pause" aria-label="Product philosophy">
      <div class="wrap">
        <p class="pause-quote" data-reveal>
          <span ${tr('Social reading should feel like ', '打开社交应用，本该像', '打開社群應用，本該像')}>Social reading should feel like </span><em ${tr('an unhurried afternoon', '一段不被催促的午后', '一段不被催促的午後')}>an unhurried afternoon</em><span ${tr(' — pick the community that feels like home, follow voices worth lingering over, and leave the noise outside.', '——选择与你契合的社区，关注值得细读的人，把喧嚣留在门外。', '——選擇與你契合的社群，追蹤值得細讀的人，把喧囂留在門外。')}> — pick the community that feels like home, follow voices worth lingering over, and leave the noise outside.</span>
        </p>
        <div class="pause-pillars">
          <div class="pause-pillar" data-reveal>
            <strong ${tr('Start with any server — or just browse', '从任意实例出发，或先随便逛逛', '從任意站台出發，或先隨意逛逛')}>Start with any server — or just browse</strong>
            <p ${tr('Sign in to your existing Mastodon account through your server’s own browser page, or explore public conversations before signing in.', '通过所属实例的官方网页授权登录，也支持先以访客身份浏览公开内容。', '透過所屬站台的官方網頁授權登入，也支援先以訪客身分瀏覽公開內容。')}>Sign in to your existing Mastodon account through your server’s own browser page, or explore public conversations before signing in.</p>
          </div>
          <div class="pause-pillar" data-reveal data-delay="1">
            <strong ${tr('Never lose your place mid-sentence', '绝不打断你正在读的那一行', '絕不打斷你正在讀的那一行')}>Never lose your place mid-sentence</strong>
            <p ${tr('Your reading position is remembered across timelines and accounts. Incoming posts wait politely at the top until you invite them in.', '每条时间线与每个账号的阅读进度都会被妥善记住；新到达的帖子会安静等候，绝不突然跳屏。', '每條時間軸與每個帳號的閱讀進度都會妥善記住；新到達的貼文會安靜等候，絕不突然跳動。')}>Your reading position is remembered across timelines and accounts. Incoming posts wait politely at the top until you invite them in.</p>
          </div>
          <div class="pause-pillar" data-reveal data-delay="2">
            <strong ${tr('Daylight paper, midnight calm', '白日素纸，入夜沉静', '白日素紙，入夜沉靜')}>Daylight paper, midnight calm</strong>
            <p ${tr('Tuned for long-form legibility with warm daytime ivory, gentle dark charcoal, crisp system typography, and full accessibility font scaling.', '专为长文阅读调校的暖白纸色与温润夜色，搭配清晰的系统字体与完整大字号无障碍适配。', '專為長文閱讀調校的暖白紙色與溫潤夜色，搭配清晰的系統字體與完整大字體無障礙適配。')}>Tuned for long-form legibility with warm daytime ivory, gentle dark charcoal, crisp system typography, and full accessibility font scaling.</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 3. Sticky Scrollytelling Interactive Experience -->
    <section id="experience" class="story-section">
      <div class="wrap">
        <div class="section-head" data-reveal>
          <p class="kicker"><span class="kicker-rule" aria-hidden="true"></span><span ${tr('The reading experience', '阅读与书写体验', '閱讀與書寫體驗')}>The reading experience</span></p>
          <h2><span ${tr('Scroll the story, or ', '随滚动流转，也可', '隨捲動流轉，也可')}>Scroll the story, or </span><em ${tr('touch the glass.', '亲手点开试试。', '親手點開試試。')}>touch the glass.</em></h2>
          <p class="section-lead" ${tr(
            'Every control inside the preview responds just like the app — switch timelines, reveal new posts, fold Content Warnings, vote in a poll, or open photographs full-screen.',
            '右侧设备内的交互完全可点按体验：切换时间线、展开新嘟文、打开内容警告、参与投票，或点开照片细看 ALT 描述。',
            '右側裝置內的互動完全可點按體驗：切換時間軸、展開新嘟文、開啟內容警告、參與投票，或點開照片細看 ALT 描述。'
          )}>Every control inside the preview responds just like the app — switch timelines, reveal new posts, fold Content Warnings, vote in a poll, or open photographs full-screen.</p>
        </div>

        <div class="story-grid">
          <div class="story-chapters">
            <button class="story-chapter is-active" type="button" data-story-chapter="timelines" aria-pressed="true">
              <span class="chapter-num">01</span>
              <h3 ${tr('Timelines that wait for you', '懂得等候你的时间线', '懂得等候你的時間軸')}>Timelines that wait for you</h3>
              <p ${tr('Move smoothly between Home, Local, and Federated streams. While you catch up on earlier posts, new arrivals gather quietly in a single pill — ready whenever you are.', '在主页、本站与跨站之间从容切换。当你专注翻阅早先的帖子时，新到达的内容会安静聚拢在顶部提示中，等你准备好再展开。', '在首頁、本地與聯邦之間從容切換。當你專注翻閱早先的貼文時，新到達的內容會安靜聚攏在頂部提示中，等你準備好再展開。')}>Move smoothly between Home, Local, and Federated streams. While you catch up on earlier posts, new arrivals gather quietly in a single pill — ready whenever you are.</p>
              <span class="chapter-cue">${icons.sparkles}<span ${tr('Try tapping “New posts (1)” or “Show”', '试试点击“新嘟文 (1)”或“显示”', '試試點選「新嘟文 (1)」或「顯示」')}>Try tapping “New posts (1)” or “Show”</span></span>
            </button>

            <button class="story-chapter" type="button" data-story-chapter="threads" aria-pressed="false">
              <span class="chapter-num">02</span>
              <h3 ${tr('Conversations with breathing room', '脉络舒展的完整对话', '脈絡舒展的完整對話')}>Conversations with breathing room</h3>
              <p ${tr('Open any post to follow its full arc — ancestors above, a clearly framed focal post, and cleanly indented replies below. Embedded quote posts show their context while respecting each author’s quote preferences.', '点开任意帖子即可纵览完整脉络：前文回顾、清晰的焦点卡片与层次分明的缩进回复。引用帖自然内嵌呈现，同时尊重原作者的引用授权。', '點開任意貼文即可縱覽完整脈絡：前文回顧、清晰的焦點卡片與層次分明的縮排回覆。引用貼文自然內嵌呈現，同時尊重原作者的引用授權。')}>Open any post to follow its full arc — ancestors above, a clearly framed focal post, and cleanly indented replies below. Embedded quote posts show their context while respecting each author’s quote preferences.</p>
              <span class="chapter-cue">${icons.sparkles}<span ${tr('Clean hierarchy for replies and quotes', '清晰区分焦点帖、引用与多层回复', '清晰區分焦點貼文、引用與多層回覆')}>Clean hierarchy for replies and quotes</span></span>
            </button>

            <button class="story-chapter" type="button" data-story-chapter="composer" aria-pressed="false">
              <span class="chapter-num">03</span>
              <h3 ${tr('Writing without hurry', '从容落笔，细致表达', '從容落筆，細緻表達')}>Writing without hurry</h3>
              <p ${tr('Draft posts, replies, edits, and quotes with every Mastodon tool close at hand: audience visibility, post language, quote policy, Content Warnings, image ALT descriptions, server custom emojis, and multi-option polls. Unsent drafts stay safe even when you resize or fold.', '写帖、回复、编辑与引用一气呵成：可见范围、发帖语言、引用权限、内容警告、图片 ALT 描述、实例自定义表情与多选项投票一应俱全，旋转或折叠屏幕也不丢失草稿。', '發文、回覆、編輯與引用一氣呵成：可見範圍、發文語言、引用權限、內容警告、圖片 ALT 描述、站台自訂表情與多選項投票一應俱全，旋轉或摺疊螢幕也不遺失草稿。')}>Draft posts, replies, edits, and quotes with every Mastodon tool close at hand: audience visibility, post language, quote policy, Content Warnings, image ALT descriptions, server custom emojis, and multi-option polls. Unsent drafts stay safe even when you resize or fold.</p>
              <span class="chapter-cue">${icons.sparkles}<span ${tr('Try voting in the poll or cycling visibility', '试试给投票点个选项，或切换可见范围', '試試為投票點個選項，或切換可見範圍')}>Try voting in the poll or cycling visibility</span></span>
            </button>

            <button class="story-chapter" type="button" data-story-chapter="explore" aria-pressed="false">
              <span class="chapter-num">04</span>
              <h3 ${tr('Photography, given the frame it deserves', '让每一帧影像都舒展呈现', '讓每一幀影像都舒展呈現')}>Photography, given the frame it deserves</h3>
              <p ${tr('Wander trending hashtags and search across people and posts. Tap any image to step into a distraction-free full-screen gallery with fluid pinch-to-zoom, swipe-to-dismiss, native video playback, full ALT descriptions, and original-resolution photo saving.', '漫步热门话题，随时搜索感兴趣的人与讨论。轻点照片即进入沉浸式全屏画廊：流畅缩放、顺手下拉关闭、原生视频播放、完整 ALT 说明与原图保存。', '漫步熱門話題，隨時搜尋感興趣的人與討論。輕點照片即進入沉浸式全螢幕畫廊：流暢縮放、順手下拉關閉、原生影片播放、完整 ALT 說明與原圖儲存。')}>Wander trending hashtags and search across people and posts. Tap any image to step into a distraction-free full-screen gallery with fluid pinch-to-zoom, swipe-to-dismiss, native video playback, full ALT descriptions, and original-resolution photo saving.</p>
              <span class="chapter-cue">${icons.sparkles}<span ${tr('Tap either photograph inside the phone to inspect full-screen', '点击手机内任意照片打开全屏画廊与缩放', '點選裝置內任意照片開啟全螢幕畫廊與縮放')}>Tap either photograph inside the phone to inspect full-screen</span></span>
            </button>
          </div>

          <!-- Sticky Interactive Device Stage -->
          <div class="story-sticky-col">
            <div class="sim-plinth">
              <div class="sim-toolbar">
                <span class="sim-mode-tag" ${tr('Interactive preview · Fictional posts', '交互预览 · 虚构示例帖', '互動預覽 · 虛構範例貼文')}>Interactive preview · Fictional posts</span>
                <div class="sim-theme-switch" role="group" aria-label="Preview color scheme">
                  <button class="sim-theme-btn" type="button" data-preview-theme="day" aria-pressed="true">${icons.sunSmall}<span ${tr('Day', '浅色', '淺色')}>Day</span></button>
                  <button class="sim-theme-btn" type="button" data-preview-theme="night" aria-pressed="false">${icons.moonSmall}<span ${tr('Night', '暗色', '深色')}>Night</span></button>
                </div>
              </div>

              <div class="sim-phone">
                <div class="sim-screen app-surface-day" data-sim-screen>
                  <div class="iphone-status">
                    <span>9:41</span>
                    <span class="dynamic-island"></span>
                    <span>5G</span>
                  </div>
                  <div class="qt-app-header">
                    <img src="/assets/quantoot.png" width="30" height="30" alt="">
                    <div class="qt-app-title">
                      <strong>QuanToot</strong>
                      <small>mastodon.social</small>
                    </div>
                    <span class="qt-mini-avatar">L</span>
                  </div>

                  <div class="sim-screen-body" data-sim-scroll>
                    <!-- PANEL 1: TIMELINES -->
                    <div class="sim-panel" data-sim-panel="timelines">
                      <div class="sim-feed-tabs" role="tablist">
                        <button class="sim-feed-tab" type="button" role="tab" aria-selected="true" data-feed-tab="home" ${tr('Home', '主页', '首頁')}>Home</button>
                        <button class="sim-feed-tab" type="button" role="tab" aria-selected="false" data-feed-tab="local" ${tr('Local', '本站', '在地')}>Local</button>
                        <button class="sim-feed-tab" type="button" role="tab" aria-selected="false" data-feed-tab="federated" ${tr('Federated', '跨站', '聯邦')}>Federated</button>
                      </div>

                      <div data-feed-list="home">
                        <div class="sim-pending-wrap" data-pending-wrap>
                          <button class="sim-pending-pill" type="button" data-reveal-pending>
                            ${icons.arrowUp}
                            <span ${tr('New posts (1)', '新嘟文 (1)', '新嘟文 (1)')}>New posts (1)</span>
                          </button>
                        </div>

                        <article class="sim-card" data-pending-post hidden>
                          <div class="qt-avatar qt-avatar-slate">S</div>
                          <div class="sim-card-content">
                            <div class="qt-post-head"><strong>Sora</strong><time ${tr('Just now', '刚刚', '剛剛')}>Just now</time></div>
                            <span class="qt-handle">@sora@tea.example</span>
                            <p class="qt-post-text" ${tr('Arrived gently while you were reading — never jumping your place.', '在你阅读时安静抵达，绝不突然顶走你正在看的那一行。', '在你閱讀時安靜抵達，絕不突然頂走你正在看的那一行。')}>Arrived gently while you were reading — never jumping your place.</p>
                          </div>
                        </article>

                        <article class="sim-card">
                          <div class="qt-avatar qt-avatar-pine">L</div>
                          <div class="sim-card-content">
                            <div class="qt-post-head"><strong>Lina</strong><time ${tr('14m', '14 分钟前', '14 分鐘前')}>14m</time></div>
                            <span class="qt-handle">@lina@studio.example</span>
                            <p class="qt-post-text"><span ${tr('Morning field notes. The lake was quiet enough to hear the mist drift past the pier. ', '清晨笔记：湖面静得连雾气掠过木栈道的声音都听得见。', '清晨筆記：湖面靜得連霧氣掠過木棧道的聲音都聽得見。')}>Morning field notes. The lake was quiet enough to hear the mist drift past the pier. </span><span class="qt-hashtag">#FieldNotes</span></p>
                            <div class="sim-actions-row">
                              <button class="sim-action-btn" type="button" data-toggle-action aria-pressed="false">${icons.reply}<span data-count data-base="4">4</span></button>
                              <button class="sim-action-btn" type="button" data-toggle-action aria-pressed="false">${icons.boost}<span data-count data-base="7">7</span></button>
                              <button class="sim-action-btn" type="button" data-toggle-action aria-pressed="false">${icons.heart}<span data-count data-base="22">22</span></button>
                              <button class="sim-action-btn" type="button" data-toggle-action aria-pressed="false" aria-label="Bookmark">${icons.bookmark}</button>
                            </div>
                          </div>
                        </article>

                        <article class="sim-card">
                          <div class="qt-avatar qt-avatar-warm">K</div>
                          <div class="sim-card-content">
                            <div class="qt-post-head"><strong>Kenji</strong><time ${tr('52m', '52 分钟前', '52 分鐘前')}>52m</time></div>
                            <span class="qt-handle">@kenji@cinema.example</span>
                            <div class="sim-cw-banner">
                              <span ${tr('Content warning · Film ending notes', '内容警告 · 电影结局笔记', '內容警告 · 電影結局筆記')}>Content warning · Film ending notes</span>
                              <button class="sim-cw-btn" type="button" data-cw-toggle aria-expanded="false" ${tr('Show', '显示', '顯示')}>Show</button>
                            </div>
                            <p class="qt-post-text" data-cw-body hidden ${tr('The final five minutes had zero dialogue — just rain on a train window and a slow piano motif. Worth seeing in a quiet theater.', '最后五分钟没有一句台词，只有车窗雨声与缓慢的钢琴主题，值得在安静的影厅里看。', '最後五分鐘沒有一句台詞，只有車窗雨聲與緩慢的鋼琴主題，值得在安靜的影廳裡看。')}>The final five minutes had zero dialogue — just rain on a train window and a slow piano motif. Worth seeing in a quiet theater.</p>
                          </div>
                        </article>
                      </div>

                      <div data-feed-list="local" hidden>
                        <article class="sim-card">
                          <div class="qt-avatar qt-avatar-warm">M</div>
                          <div class="sim-card-content">
                            <div class="qt-post-head"><strong>Mika</strong><time ${tr('8m', '8 分钟前', '8 分鐘前')}>8m</time></div>
                            <span class="qt-handle">@mika@mastodon.social</span>
                            <p class="qt-post-text" ${tr('Local timeline check-in: what independent newsletter or book made your week better?', '本站时间线问候：这周有哪些独立通讯或书让你眼前一亮？', '本地時間軸問候：這週有哪些獨立電子報或書讓你眼前一亮？')}>Local timeline check-in: what independent newsletter or book made your week better?</p>
                          </div>
                        </article>
                        <article class="sim-card">
                          <div class="qt-avatar qt-avatar-pine">N</div>
                          <div class="sim-card-content">
                            <div class="qt-post-head"><strong>Nadia</strong><time ${tr('31m', '31 分钟前', '31 分鐘前')}>31m</time></div>
                            <span class="qt-handle">@nadia@mastodon.social</span>
                            <p class="qt-post-text"><span ${tr('Sketching a small typography poster for tonight’s reading group. ', '正在为今晚的读书会画一张字体小海报。', '正在為今晚的讀書會畫一張字體小海報。')}>Sketching a small typography poster for tonight’s reading group. </span><span class="qt-hashtag">#Typography</span></p>
                          </div>
                        </article>
                      </div>

                      <div data-feed-list="federated" hidden>
                        <article class="sim-card">
                          <div class="qt-avatar qt-avatar-slate">R</div>
                          <div class="sim-card-content">
                            <div class="qt-post-head"><strong>Ren</strong><time ${tr('4m', '4 分钟前', '4 分鐘前')}>4m</time></div>
                            <span class="qt-handle">@ren@craft.town</span>
                            <p class="qt-post-text" ${tr('Across the fediverse: finished restoring a 1974 mechanical camera shutter this afternoon.', '跨站分享：今天下午终于修好了一台 1974 年机械相机的快门。', '跨站分享：今天下午終於修好了一台 1974 年機械相機的快門。')}>Across the fediverse: finished restoring a 1974 mechanical camera shutter this afternoon.</p>
                          </div>
                        </article>
                      </div>
                    </div>

                    <!-- PANEL 2: THREADS & QUOTES -->
                    <div class="sim-panel" data-sim-panel="threads" hidden>
                      <article class="sim-card is-focal">
                        <div class="qt-avatar qt-avatar-warm">A</div>
                        <div class="sim-card-content">
                          <div class="qt-post-head"><strong>Amir</strong><time ${tr('1h ago · edited', '1 小时前 · 已编辑', '1 小時前 · 已編輯')}>1h ago · edited</time></div>
                          <span class="qt-handle">@amir@journal.example</span>
                          <p class="qt-post-text" ${tr('Chronological timelines feel different once your client stops fighting for your attention.', '当客户端不再千方百计争夺你的注意力，按时间排序的阅读体验会完全不同。', '當用戶端不再千方百計爭奪你的注意力，按時間排序的閱讀體驗會完全不同。')}>Chronological timelines feel different once your client stops fighting for your attention.</p>
                          <div class="qt-quote-card">
                            <div class="qt-quote-author"><strong>Mika</strong><span>@mika@notes.example</span></div>
                            <p ${tr('Good reading tools let you reach the bottom of the page and put the phone down.', '好的阅读工具，会让你安心读完最后一条，然后从容放下手机。', '好的閱讀工具，會讓你安心讀完最後一則，然後從容放下手機。')}>Good reading tools let you reach the bottom of the page and put the phone down.</p>
                          </div>
                        </div>
                      </article>
                      <article class="sim-card">
                        <div class="qt-avatar qt-avatar-pine">L</div>
                        <div class="sim-card-content">
                          <div class="qt-post-head"><strong>Lina</strong><time ${tr('42m', '42 分钟前', '42 分鐘前')}>42m</time></div>
                          <span class="qt-handle">@lina@studio.example</span>
                          <p class="qt-post-text" ${tr('Bookmarking long essays for evening tea has completely replaced aimless scrolling for me.', '把长帖加入书签留到晚上配茶细读，彻底取代了无意识刷屏。', '把長文加入書籤留到晚上配茶細讀，徹底取代了無意識滑手機。')}>Bookmarking long essays for evening tea has completely replaced aimless scrolling for me.</p>
                        </div>
                      </article>
                      <article class="sim-card is-indented">
                        <div class="qt-avatar qt-avatar-slate">S</div>
                        <div class="sim-card-content">
                          <div class="qt-post-head"><strong>Sora</strong><time ${tr('19m', '19 分钟前', '19 分鐘前')}>19m</time></div>
                          <span class="qt-handle">@sora@tea.example</span>
                          <p class="qt-post-text" ${tr('And keyword filters fold away anything I am not ready to read yet.', '再加上关键词过滤，暂时不想看的话题也能安静收起。', '再加上關鍵字篩選，暫時不想看的話題也能安靜收起。')}>And keyword filters fold away anything I am not ready to read yet.</p>
                        </div>
                      </article>
                    </div>

                    <!-- PANEL 3: COMPOSER & POLL -->
                    <div class="sim-panel" data-sim-panel="composer" hidden>
                      <div class="sim-composer">
                        <div class="sim-composer-top">
                          <strong ${tr('New post', '新嘟文', '新嘟文')}>New post</strong>
                          <button class="sim-publish-btn" type="button" data-composer-publish ${tr('Publish', '发布', '發佈')}>Publish</button>
                        </div>
                        <div class="sim-composer-field" data-composer-cw-box hidden ${tr('Content warning: Coffee & tea notes', '内容警告：茶与咖啡笔记', '內容警告：茶與咖啡筆記')}>Content warning: Coffee &amp; tea notes</div>
                        <div class="sim-composer-field" ${tr('Weekend poll — when do you usually catch up on Mastodon?', '周末小投票：你通常在一天中的什么时候翻看 Mastodon？', '週末小投票：你通常在一天中的什麼時候翻看 Mastodon？')}>Weekend poll — when do you usually catch up on Mastodon?</div>
                        <div class="sim-poll-card" role="group" aria-label="Interactive sample poll">
                          <button class="sim-poll-opt" type="button" data-poll-opt data-fill-class="sim-poll-w-62" data-pct="62%" aria-pressed="false">
                            <span class="sim-poll-fill sim-poll-w-0"></span>
                            <span ${tr('Morning coffee', '清晨配咖啡', '清晨配咖啡')}>Morning coffee</span>
                            <span data-poll-pct></span>
                          </button>
                          <button class="sim-poll-opt" type="button" data-poll-opt data-fill-class="sim-poll-w-26" data-pct="26%" aria-pressed="false">
                            <span class="sim-poll-fill sim-poll-w-0"></span>
                            <span ${tr('Evening unwind', '夜晚慢读', '夜晚慢讀')}>Evening unwind</span>
                            <span data-poll-pct></span>
                          </button>
                          <button class="sim-poll-opt" type="button" data-poll-opt data-fill-class="sim-poll-w-12" data-pct="12%" aria-pressed="false">
                            <span class="sim-poll-fill sim-poll-w-0"></span>
                            <span ${tr('Quiet afternoon break', '午后片刻空闲', '午後片刻空閒')}>Quiet afternoon break</span>
                            <span data-poll-pct></span>
                          </button>
                        </div>
                        <div class="sim-composer-pills">
                          <button class="sim-chip" type="button" data-composer-visibility>
                            <span data-visibility-text ${tr('Public', '公开', '公開')}>Public</span>
                          </button>
                          <button class="sim-chip" type="button" data-composer-cw aria-pressed="false">CW</button>
                          <span class="sim-chip">ALT</span>
                          <span class="sim-chip">EN / 中文</span>
                        </div>
                        <div class="sim-toast" data-composer-toast hidden ${tr('Preview only — real posts publish straight to your server.', '仅作预览：在应用中将直接发布到你的 Mastodon 实例。', '僅供預覽：在應用程式中將直接發布至你的 Mastodon 站台。')}>Preview only — real posts publish straight to your server.</div>
                      </div>
                    </div>

                    <!-- PANEL 4: EXPLORE & FULLSCREEN MEDIA -->
                    <div class="sim-panel" data-sim-panel="explore" hidden>
                      <div class="sim-search-box">${icons.search}<span ${tr('Search mastodon.social', '搜索 mastodon.social', '搜尋 mastodon.social')}>Search mastodon.social</span></div>
                      <div class="sim-tag-row">
                        <span class="sim-tag-pill"><b>#</b>FieldNotes</span>
                        <span class="sim-tag-pill"><b>#</b>SilentSunday</span>
                        <span class="sim-tag-pill"><b>#</b>BookClub</span>
                        <span class="sim-tag-pill"><b>#</b>OpenWeb</span>
                      </div>
                      <article class="sim-card">
                        <div class="qt-avatar qt-avatar-pine">L</div>
                        <div class="sim-card-content">
                          <div class="qt-post-head"><strong>Lina</strong><time ${tr('Tap a photo', '轻点查看大图', '輕點檢視大圖')}>Tap a photo</time></div>
                          <span class="qt-handle">@lina@studio.example</span>
                          <p class="qt-post-text" ${tr('Two quiet corners from this week — afternoon tea and a lantern at dusk.', '这周最喜欢的两个安静角落：午后书桌与暮色提灯。', '這週最喜歡的兩個安靜角落：午後書桌與暮色提燈。')}>Two quiet corners from this week — afternoon tea and a lantern at dusk.</p>
                          <div class="sim-photo-pair">
                            <button class="sim-media-trigger" type="button" data-open-photo="bookshop" aria-label="Open bookshop photograph full-screen">
                              <img src="/assets/bookshop.jpg" width="960" height="540" alt="Open hardcover book and steaming teacup on a sunlit wooden table">
                              <span class="qt-alt-tag">ALT</span>
                            </button>
                            <button class="sim-media-trigger" type="button" data-open-photo="twilight" aria-label="Open twilight pier photograph full-screen">
                              <img src="/assets/twilight.jpg" width="960" height="540" alt="Timber boardwalk beside calm twilight water with a single glowing lantern">
                              <span class="qt-alt-tag">ALT</span>
                            </button>
                          </div>
                        </div>
                      </article>
                    </div>
                  </div>

                  <!-- Interactive Full-Screen Media Gallery Lightbox -->
                  <div class="sim-lightbox" data-sim-lightbox hidden role="dialog" aria-label="Full-screen media preview">
                    <div class="sim-lightbox-bar">
                      <button class="sim-lightbox-btn" type="button" data-zoom-lightbox aria-pressed="false" ${tr('Zoom 1.75×', '缩放 1.75×', '縮放 1.75×')}>Zoom 1.75×</button>
                      <button class="sim-lightbox-btn" type="button" data-close-lightbox ${tr('Close', '关闭', '關閉')}>Close</button>
                    </div>
                    <div class="sim-lightbox-img-wrap">
                      <img class="sim-lightbox-img" data-lightbox-img src="/assets/bookshop.jpg" width="960" height="540" alt="">
                    </div>
                    <div class="sim-lightbox-alt">
                      <strong>ALT · </strong>
                      <span data-lightbox-alt-text>Warm late-afternoon sunlight crossing a wooden reading table with an open hardcover book and a steaming dark ceramic teacup.</span>
                    </div>
                  </div>

                  <div class="sim-dock" role="navigation" aria-label="Preview tabs">
                    <button class="sim-dock-btn" type="button" data-sim-dock="home" aria-current="true">${icons.home}<span ${tr('Home', '主页', '首頁')}>Home</span></button>
                    <button class="sim-dock-btn" type="button" data-sim-dock="threads" aria-current="false">${icons.bell}<span ${tr('Conversation', '对话', '對話')}>Conversation</span></button>
                    <button class="sim-dock-btn" type="button" data-sim-dock="composer" aria-current="false">${icons.edit}<span ${tr('New post', '新嘟文', '新嘟文')}>New post</span></button>
                    <button class="sim-dock-btn" type="button" data-sim-dock="explore" aria-current="false">${icons.search}<span ${tr('Explore', '探索', '探索')}>Explore</span></button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 4. Physical Hardware Morph Showcase (Pocket · Unfolded · Canvas) -->
    <section id="screens" class="adaptive-section">
      <div class="wrap">
        <div class="adaptive-header">
          <div class="section-head" data-reveal>
            <p class="kicker"><span class="kicker-rule" aria-hidden="true"></span><span ${tr('Shaped for your screen', '随屏幕自然舒展', '隨螢幕自然舒展')}>Shaped for your screen</span></p>
            <h2><span ${tr('Pocket, book-fold, or ', '掌中、折叠、抑或', '掌中、摺疊、抑或')}>Pocket, book-fold, or </span><em ${tr('wide desk.', '宽阔画布。', '寬闊畫布。')}>wide desk.</em></h2>
          </div>
          <div class="form-switcher" role="group" aria-label="Choose device form factor" data-reveal data-delay="1">
            <button class="form-btn" type="button" data-form-switch="phone" aria-pressed="false">${icons.phone}<span ${tr('Phone', '手机', '手機')}>Phone</span></button>
            <button class="form-btn" type="button" data-form-switch="cover" aria-pressed="true">${icons.foldable}<span ${tr('Folded Duo', '折叠外屏', '摺疊外螢幕')}>Folded Duo</span></button>
            <button class="form-btn" type="button" data-form-switch="foldable" aria-pressed="false">${icons.foldable}<span ${tr('Unfolded', '展开双页', '展開雙頁')}>Unfolded</span></button>
            <button class="form-btn" type="button" data-form-switch="tablet" aria-pressed="false">${icons.tablet}<span ${tr('iPad & Tablet', 'iPad 与平板', 'iPad 與平板')}>iPad &amp; Tablet</span></button>
          </div>
        </div>

        <div class="adaptive-stage" data-reveal="scale">
          <div class="adaptive-device" data-adaptive-device data-form="cover" aria-hidden="true">
            <div class="adaptive-viewport app-surface-day">
              <div class="adaptive-rail">
                <img src="/assets/quantoot.png" width="32" height="32" alt="">
                <span class="rail-dot is-active">${icons.home}</span>
                <span class="rail-dot">${icons.search}</span>
                <span class="rail-dot">${icons.bell}</span>
                <span class="rail-dot">${icons.user}</span>
                <span class="rail-fab">${icons.edit}</span>
              </div>

              <div class="adaptive-pane">
                <div class="pane-bar">
                  <span>QuanToot</span>
                  <small>mastodon.social</small>
                </div>
                <div class="qt-tabs">
                  <span class="qt-tab-item is-active" ${tr('Home', '主页', '首頁')}>Home</span>
                  <span class="qt-tab-item" ${tr('Local', '本站', '在地')}>Local</span>
                  <span class="qt-tab-item" ${tr('Federated', '跨站', '聯邦')}>Federated</span>
                </div>
                <div class="pane-feed">
                  <div class="pane-post">
                    <div class="pane-post-head">
                      <span class="qt-avatar qt-avatar-pine">L</span>
                      <div><strong>Lina</strong><div class="pane-post-muted">@lina@studio.example · 14m</div></div>
                    </div>
                    <p ${tr('Morning field notes. The lake was quiet enough to hear the mist move.', '清晨笔记：湖面静得连雾气流动的声音都听得见。', '清晨筆記：湖面靜得連霧氣流動的聲音都聽得見。')}>Morning field notes. The lake was quiet enough to hear the mist move.</p>
                    <div class="pane-photo"><img src="/assets/lake.jpg" width="800" height="450" alt=""></div>
                  </div>
                </div>
                <div class="pane-bottom-nav">
                  <b ${tr('Home', '主页', '首頁')}>Home</b>
                  <span ${tr('Explore', '探索', '探索')}>Explore</span>
                  <span ${tr('Notifications', '通知', '通知')}>Notifications</span>
                  <span ${tr('Profile', '个人资料', '個人檔案')}>Profile</span>
                </div>
              </div>

              <div class="adaptive-hinge"></div>
              <div class="adaptive-divider"></div>

              <div class="adaptive-pane adaptive-secondary">
                <div class="pane-bar">
                  <span ${tr('Conversation', '对话', '對話')}>Conversation</span>
                  <small>#BookClub</small>
                </div>
                <div class="pane-feed">
                  <div class="pane-post">
                    <div class="pane-post-head">
                      <span class="qt-avatar qt-avatar-slate">M</span>
                      <div><strong>Mika</strong><div class="pane-post-muted">@mika@notes.example · 22m</div></div>
                    </div>
                    <p ${tr('Unfolding a thread beside your timeline turns social reading into a two-page journal spread.', '在时间线旁并排展开讨论，让日常浏览拥有如翻开纸本画报般的从容节奏。', '在時間軸旁並排展開討論，讓日常瀏覽擁有如翻開紙本畫報般的從容節奏。')}>Unfolding a thread beside your timeline turns social reading into a two-page journal spread.</p>
                    <div class="pane-photo"><img src="/assets/bookshop.jpg" width="960" height="540" alt=""></div>
                  </div>
                </div>
                <div class="adaptive-reply-bar">
                  <span class="duo-reply-avatar">${icons.user}</span>
                  <span class="duo-reply-pill"><span ${tr('Reply', '回复', '回覆')}>Reply</span>${icons.edit}</span>
                </div>
              </div>

              <div class="adaptive-cover-rail">
                <span class="adaptive-cover-cam"></span>
                <span class="adaptive-cover-time">9:41</span>
                <div class="adaptive-glass-pill adaptive-cover-compose-pill">
                  <span class="adaptive-pill-btn">${icons.edit}</span>
                  <span class="adaptive-pill-btn">${icons.dots}</span>
                </div>
                <div class="adaptive-unfolded-btns">
                  <span class="adaptive-circle-btn">${icons.close}</span>
                  <span class="adaptive-circle-btn">${icons.dots}</span>
                </div>
                <div class="adaptive-glass-pill adaptive-glass-pill-bottom">
                  <span class="adaptive-pill-btn is-active">${icons.home}</span>
                  <span class="adaptive-pill-btn">${icons.search}</span>
                  <span class="adaptive-pill-btn">${icons.bell}</span>
                  <span class="adaptive-pill-btn">${icons.user}</span>
                </div>
              </div>
            </div>
          </div>
          <p class="adaptive-caption" data-adaptive-caption></p>
        </div>
      </div>
    </section>

    <!-- 5. Curated Interactive Feature Specimens -->
    <section id="details" class="specimens-section">
      <div class="wrap">
        <div class="section-head" data-reveal>
          <p class="kicker"><span class="kicker-rule" aria-hidden="true"></span><span ${tr('Crafted details', '细腻之处', '細膩之處')}>Crafted details</span></p>
          <h2><span ${tr('Every small decision, made for ', '每一处细微考量，都为了', '每一處細微考量，都為了')}>Every small decision, made for </span><em ${tr('quieter focus.', '更安宁的专注。', '更安寧的專注。')}>quieter focus.</em></h2>
        </div>

        <div class="specimen-grid">
          <!-- Specimen A: Grouped Notifications & Live Follow Request -->
          <article class="specimen spec-7" data-reveal>
            <div class="specimen-copy">
              <h3 ${tr('Notifications that stay calm', '克制而清晰的分组合并通知', '克制而清晰的分組合併通知')}>Notifications that stay calm</h3>
              <p ${tr('Replies show the full conversation, favorites and boosts fold neatly into avatar stacks that respect Content Warnings, and follow requests can be approved or rejected on the spot.', '回复保留完整帖文，收藏与转发自动聚合头像并尊重内容警告，关注请求无需跳转即可当场批准或拒绝。', '回覆保留完整貼文，最愛與轉發自動彙整頭像並尊重內容警告，跟隨請求無需跳轉即可當場核准或拒絕。')}>Replies show the full conversation, favorites and boosts fold neatly into avatar stacks that respect Content Warnings, and follow requests can be approved or rejected on the spot.</p>
            </div>
            <div class="notif-stack">
              <div class="notif-card">
                <div class="notif-left">
                  <div class="avatar-stack" aria-hidden="true">
                    <span class="stack-a">L</span>
                    <span class="stack-b">A</span>
                    <span class="stack-c">M</span>
                    <span class="stack-d">+5</span>
                  </div>
                  <span ${tr('Lina and 7 others favorited your field notes', 'Lina 和其他 7 人已收藏你的笔记', 'Lina 和其他 7 人已加入最愛')}>Lina and 7 others favorited your field notes</span>
                </div>
              </div>
              <div class="notif-card">
                <div class="notif-left">
                  <span class="qt-avatar qt-avatar-slate">S</span>
                  <div>
                    <strong>Sora</strong>
                    <span class="pane-post-muted" ${tr(' · poll ended · “Morning coffee” led with 62%', ' · 投票已结束 · “清晨配咖啡”以 62% 胜出', ' · 投票已結束 · 「清晨配咖啡」以 62% 勝出')}> · poll ended · “Morning coffee” led with 62%</span>
                  </div>
                </div>
              </div>
              <div class="notif-card">
                <div class="notif-left">
                  <span class="qt-avatar qt-avatar-warm">N</span>
                  <div>
                    <strong>Nadia</strong>
                    <span class="pane-post-muted" ${tr(' sent a follow request', ' 发送了关注请求', ' 傳送了跟隨請求')}> sent a follow request</span>
                  </div>
                </div>
                <div class="req-actions" data-req-actions>
                  <button class="req-btn req-btn-accept" type="button" data-req-decision="approved" ${tr('Approve', '批准', '核准')}>Approve</button>
                  <button class="req-btn" type="button" data-req-decision="rejected" ${tr('Reject', '拒绝', '拒絕')}>Reject</button>
                </div>
                <span class="req-status" data-req-status hidden></span>
              </div>
            </div>
          </article>

          <!-- Specimen B: Interactive ALT-Lens Photo Frame -->
          <article class="specimen spec-5 spec-warm" data-reveal data-delay="1">
            <div class="specimen-copy">
              <h3 ${tr('Images with context & care', '图文并重，留存原片', '圖文並重，留存原片')}>Images with context &amp; care</h3>
              <p ${tr('Read full ALT descriptions right over any photograph, zoom in cleanly, watch video natively, and save original photos with add-only library access.', '随时展开完整 ALT 图片描述、顺滑放大细节、原生播放视频，并仅按需请求写入权限保存原图。', '隨時展開完整 ALT 圖片描述、順滑放大細節、原生播放影片，並僅按需請求寫入權限儲存原圖。')}>Read full ALT descriptions right over any photograph, zoom in cleanly, watch video natively, and save original photos with add-only library access.</p>
            </div>
            <div class="alt-frame" data-alt-frame>
              <img src="/assets/twilight.jpg" width="960" height="540" alt="Timber boardwalk beside calm twilight water with a single glowing lantern">
              <div class="alt-lens-sheet" data-alt-sheet hidden ${tr(
                'A weathered timber boardwalk extending alongside misty pine trees overlooking calm blue-green twilight water, lit by a single warm lantern.',
                '暮色薄雾中的常青林畔，一座木质长廊伸向平静的蓝绿色湖面，尽头亮着一盏温暖提灯。',
                '暮色薄霧中的常青林畔，一座木質長廊伸向平靜的藍綠色湖面，盡頭亮著一盞溫暖提燈。'
              )}>A weathered timber boardwalk extending alongside misty pine trees overlooking calm blue-green twilight water, lit by a single warm lantern.</div>
              <button class="alt-lens-btn" type="button" data-alt-lens aria-expanded="false" ${tr('ALT · Inspect', 'ALT · 查看描述', 'ALT · 檢視描述')}>ALT · Inspect</button>
            </div>
          </article>

          <!-- Specimen C: Multi-Account Sanctuary (Deep Pine) -->
          <article class="specimen spec-6 spec-pine" data-reveal>
            <div class="specimen-copy">
              <h3 ${tr('Several accounts, cleanly apart', '多个账号，各自安好', '多個帳號，各自安好')}>Several accounts, cleanly apart</h3>
              <p ${tr('Carry personal, studio, and community Mastodon servers on one device. Switching accounts resets your active feeds, streams, and drafts so nothing ever crosses between identities.', '在同一部手机上同时打理日常、工作与兴趣社区账号。切换身份时，时间线、缓存与草稿彻底隔离，绝不串号误发。', '在同一部手機上同時打理日常、工作與興趣社群帳號。切換身分時，時間軸、快取與草稿徹底隔離，絕不混用誤發。')}>Carry personal, studio, and community Mastodon servers on one device. Switching accounts resets your active feeds, streams, and drafts so nothing ever crosses between identities.</p>
            </div>
            <div class="sanctuary-list" role="group" aria-label="Sample multi-account switcher">
              <button class="sanctuary-card" type="button" data-sanctuary-card aria-pressed="true">
                <div class="sanctuary-left">
                  <span class="qt-avatar qt-avatar-pine">L</span>
                  <div class="sanctuary-meta"><strong>Lina</strong><small>@lina@mastodon.social</small></div>
                </div>
                <span class="sanctuary-pill" ${tr('Active', '当前', '目前')}>Active</span>
              </button>
              <button class="sanctuary-card" type="button" data-sanctuary-card aria-pressed="false">
                <div class="sanctuary-left">
                  <span class="qt-avatar qt-avatar-warm">S</span>
                  <div class="sanctuary-meta"><strong>Studio Desk</strong><small>@notes@studio.example</small></div>
                </div>
                <span class="sanctuary-pill" ${tr('Switch', '切换', '切換')}>Switch</span>
              </button>
              <button class="sanctuary-card" type="button" data-sanctuary-card aria-pressed="false">
                <div class="sanctuary-left">
                  <span class="qt-avatar qt-avatar-slate">B</span>
                  <div class="sanctuary-meta"><strong>Book Club</strong><small>@readers@journal.example</small></div>
                </div>
                <span class="sanctuary-pill" ${tr('Switch', '切换', '切換')}>Switch</span>
              </button>
            </div>
          </article>

          <!-- Specimen D: Filters, Moderation & Trilingual Craft -->
          <article class="specimen spec-6" data-reveal data-delay="1">
            <div class="specimen-copy">
              <h3 ${tr('Shape what reaches your screen', '你看到的，由你决定', '你看到的，由你決定')}>Shape what reaches your screen</h3>
              <p ${tr('Create server-side keyword filters to hide or warn about topics you want to skip, mute or block accounts effortlessly, report abuse straight to instance moderators, and read in English, 简体中文, or 繁體中文.', '随时管理服务器端关键词过滤（隐藏或折叠提醒）、一键静音或屏蔽账号、向实例管理员举报，并在英文、简体中文与繁体中文间自由切换。', '隨時管理站台端關鍵字篩選（隱藏或摺疊提醒）、一鍵靜音或封鎖帳號、向站台管理員檢舉，並在英文、簡體中文與繁體中文間自由切換。')}>Create server-side keyword filters to hide or warn about topics you want to skip, mute or block accounts effortlessly, report abuse straight to instance moderators, and read in English, 简体中文, or 繁體中文.</p>
            </div>
            <div class="filter-demo">
              <div class="filter-demo-top">
                <span ${tr('Keyword filter: “spoilers”', '关键词过滤：「剧透」', '關鍵字篩選：「劇透」')}>Keyword filter: “spoilers”</span>
                <button class="req-btn" type="button" data-filter-toggle aria-pressed="true" ${tr('Toggle shield', '切换过滤', '切換篩選')}>Toggle shield</button>
              </div>
              <div class="filter-shield" data-filter-shield ${tr('Filtered · Spoilers — tap to peek whenever you are ready', '已折叠 · 剧透过滤 — 准备好后再手动展开', '已摺疊 · 劇透篩選 — 準備好後再手動展開')}>Filtered · Spoilers — tap to peek whenever you are ready</div>
              <p class="filter-post-plain" data-filter-plain hidden ${tr('Just finished tonight’s final episode — that quiet plot twist in the museum completely floored me.', '刚看完大结局，博物馆里那个安静的反转太令人意外了。', '剛看完大結局，博物館裡那個安靜的反轉太令人意外了。')}>Just finished tonight’s final episode — that quiet plot twist in the museum completely floored me.</p>
              <div class="swatch-row">
                <span ${tr('Day & Night · EN · 简体 · 繁體', '日间与夜间 · EN · 简体 · 繁體', '日間與夜間 · EN · 简体 · 繁體')}>Day &amp; Night · EN · 简体 · 繁體</span>
                <span class="swatch-dots" aria-hidden="true">
                  <i class="swatch-dot sw-paper"></i>
                  <i class="swatch-dot sw-vermilion"></i>
                  <i class="swatch-dot sw-pine"></i>
                  <i class="swatch-dot sw-obsidian"></i>
                  <i class="swatch-dot sw-coral"></i>
                </span>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- 6. Nocturnal Pine Privacy Interlude -->
    <section class="privacy-band" aria-labelledby="privacy-heading">
      <div class="wrap privacy-grid">
        <div data-reveal="left">
          <p class="kicker"><span class="kicker-rule" aria-hidden="true"></span><span ${tr('Private by nature', '生而尊重隐私', '生而尊重隱私')}>Private by nature</span></p>
          <h2 id="privacy-heading"><span ${tr('Your device. Your server. ', '你的设备，你的实例，', '你的裝置，你的站台，')}>Your device. Your server. </span><em ${tr('Nobody in the middle.', '中间没有第三者。', '中間沒有第三者。')}>Nobody in the middle.</em></h2>
          <p class="section-lead" ${tr(
            'QuanToot speaks straight to the Mastodon instance you choose. There is no developer server watching what you read, no ad network, and no behavioral tracking.',
            'QuanToot 直接与你所选的 Mastodon 实例对话，不存在任何监视你阅读轨迹的开发者服务器，也没有广告与行为追踪。',
            'QuanToot 直接與你所選的 Mastodon 站台對話，不存在任何監視你閱讀軌跡的開發者伺服器，也沒有廣告與行為追蹤。'
          )}>QuanToot speaks straight to the Mastodon instance you choose. There is no developer server watching what you read, no ad network, and no behavioral tracking.</p>
          <ul class="privacy-points">
            <li><span class="privacy-check">${icons.check}</span><span ${tr('Sign in through your server’s own browser page — QuanToot never sees your password, and stores your session in iOS Keychain or Android Keystore.', '通过实例官方浏览器页面完成授权，应用从不接触密码，并将登录凭据妥善保存在系统钥匙串或安全存储中。', '透過站台官方瀏覽器頁面完成授權，應用程式從不接觸密碼，並將登入憑證妥善保存在系統鑰匙圈或安全儲存中。')}>Sign in through your server’s own browser page — QuanToot never sees your password, and stores your session in iOS Keychain or Android Keystore.</span></li>
            <li><span class="privacy-check">${icons.check}</span><span ${tr('Clear cached media in one tap, remove saved device sessions cleanly, or jump straight to your server’s permanent account deletion page.', '随时一键清理媒体缓存、彻底移除本地会话，或直达所属实例的永久注销页面。', '隨時一鍵清除媒體快取、徹底移除本機工作階段，或直達所屬站台的永久刪除頁面。')}>Clear cached media in one tap, remove saved device sessions cleanly, or jump straight to your server’s permanent account deletion page.</span></li>
          </ul>
          <div class="privacy-links">
            <a class="btn btn-on-dark" href="/privacy/" ${tr('Read privacy policy', '阅读隐私政策', '閱讀隱私權政策')}>Read privacy policy</a>
            <a class="btn btn-on-dark-ghost" href="/support/" ${tr('Safety & support', '安全与支持', '安全與支援')}>Safety &amp; support</a>
          </div>
        </div>

        <div class="privacy-constellation" data-reveal="right" aria-label="Direct connection diagram">
          <div class="flow-nodes">
            <div class="flow-node">
              <div class="flow-node-icon">${icons.lock}</div>
              <strong ${tr('Your Device', '你的设备', '你的裝置')}>Your Device</strong>
              <small ${tr('System secure storage', '系统安全凭据库', '系統安全憑證庫')}>System secure storage</small>
            </div>
            <div class="flow-bridge" aria-hidden="true">
              <span class="flow-label" ${tr('Encrypted', '加密直连', '加密直連')}>Encrypted</span>
              <span class="flow-line"></span>
              <span class="flow-label" ${tr('Direct', '无中转', '無中轉')}>Direct</span>
            </div>
            <div class="flow-node">
              <div class="flow-node-icon">${icons.globe}</div>
              <strong ${tr('Your Server', '你的实例', '你的站台')}>Your Server</strong>
              <small ${tr('Your chosen community', '你选择的社区', '你選擇的社群')}>Your chosen community</small>
            </div>
          </div>
          <div class="flow-absent">
            <span ${tr('Ad networks, analytics trackers, or developer data servers', '广告网络、统计追踪器或开发者数据服务器', '廣告聯播網、分析追蹤器或開發者資料伺服器')}>Ad networks, analytics trackers, or developer data servers</span>
            <span class="flow-absent-badge" ${tr('None · 0', '无 · 0', '無 · 0')}>None · 0</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 7. Signature Download Finale -->
    <section class="download-section" aria-label="Download QuanToot">
      <div class="wrap download-grid">
        <article id="android" class="download-card download-android" aria-labelledby="android-title" data-android-downloads data-reveal>
          <div>
            <p class="kicker"><span ${tr('Android 8.0 or later', 'Android 8.0 及以上', 'Android 8.0 以上')}>Android 8.0 or later</span></p>
            <h2 id="android-title" ${tr('Download for Android', '下载 Android 版', '下載 Android 版')}>Download for Android</h2>
            <p class="release-status" role="status">
              <span data-release-loading ${tr('Checking for the latest stable release…', '正在获取最新正式版…', '正在取得最新正式版…')} hidden>Checking for the latest stable release…</span>
              <span data-release-ready hidden>
                <span ${tr('Latest official release', '最新官方签名正式版', '最新官方簽署正式版')}>Latest official release</span>
                <strong class="release-version-pill" data-release-version></strong>
              </span>
              <span data-release-missing ${tr('There is no stable APK available right now.', '暂时没有可下载的正式版 APK。', '目前沒有可下載的正式版 APK。')} hidden>There is no stable APK available right now.</span>
              <span data-release-error ${tr('Couldn’t load the latest release automatically. Browse all releases on Forgejo below.', '暂时无法自动获取最新版本，可在下方 Forgejo 页面查看所有版本。', '暫時無法自動取得最新版本，可在下方 Forgejo 頁面查看所有版本。')} hidden>Couldn’t load the latest release automatically. Browse all releases on Forgejo below.</span>
            </p>
            <ul class="apk-list" data-release-apks hidden>
              <li data-abi="arm64-v8a" hidden><a class="apk-link apk-recommended"><strong>arm64-v8a</strong><span ${tr('Recommended · Most Android phones', '推荐 · 绝大多数安卓手机', '推薦 · 絕大多數 Android 手機')}>Recommended · Most Android phones</span></a></li>
              <li data-abi="armeabi-v7a" hidden><a class="apk-link"><strong>armeabi-v7a</strong><span ${tr('32-bit ARM devices', '32 位 ARM 设备', '32 位元 ARM 裝置')}>32-bit ARM devices</span></a></li>
              <li data-abi="x86_64" hidden><a class="apk-link"><strong>x86_64</strong><span ${tr('64-bit x86 devices', '64 位 x86 设备', '64 位元 x86 裝置')}>64-bit x86 devices</span></a></li>
              <li data-abi="x86" hidden><a class="apk-link"><strong>x86</strong><span ${tr('32-bit x86 devices', '32 位 x86 设备', '32 位元 x86 裝置')}>32-bit x86 devices</span></a></li>
            </ul>
            <noscript><p>Enable JavaScript to load direct signed APK links, or browse all releases on Forgejo below.</p></noscript>
          </div>
          <div class="release-links">
            <a data-release-checksums ${tr('SHA-256 checksums', 'SHA-256 校验值', 'SHA-256 校驗值')} hidden>SHA-256 checksums</a>
            <a href="https://git.qqs.tw/bobmaster/quantoot_next/releases" ${tr('All releases on Forgejo', '在 Forgejo 查看所有版本', '在 Forgejo 查看所有版本')}>All releases on Forgejo</a>
            <span ${tr('Optional in-app updates verified with SHA-256', '支持应用内 SHA-256 校验更新', '支援應用程式內 SHA-256 驗證更新')}>Optional in-app updates verified with SHA-256</span>
          </div>
        </article>

        <article id="ios" class="download-card download-ios" aria-labelledby="ios-title" data-reveal data-delay="1">
          <div>
            <p class="kicker"><span ${tr('iPhone & iPad · iOS 15.0+', 'iPhone 与 iPad · iOS 15.0+', 'iPhone 與 iPad · iOS 15.0+')}>iPhone &amp; iPad · iOS 15.0+</span></p>
            <h2 id="ios-title" ${tr('QuanToot for iOS', 'QuanToot iOS 版', 'QuanToot iOS 版')}>QuanToot for iOS</h2>
            <p class="section-lead" ${tr(
              'Built for iPhone, iPad, and foldable displays, with Keychain security, split-screen reading, and full accessibility support. Currently preparing for App Store submission.',
              '为 iPhone、iPad 与折叠设备精心打造，支持钥匙串安全存储、双栏阅读与完整无障碍体验，现正准备提交至 App Store。',
              '為 iPhone、iPad 與摺疊裝置精心打造，支援鑰匙圈安全儲存、雙欄閱讀與完整無障礙體驗，現正準備提交至 App Store。'
            )}>Built for iPhone, iPad, and foldable displays, with Keychain security, split-screen reading, and full accessibility support. Currently preparing for App Store submission.</p>
            <ul class="ios-specs">
              <li><span ${tr('Availability', '发布状态', '發布狀態')}>Availability</span><strong ${tr('Preparing for App Store', '准备提交 App Store', '準備提交 App Store')}>Preparing for App Store</strong></li>
              <li><span ${tr('Supported devices', '支持设备', '支援裝置')}>Supported devices</span><strong ${tr('iPhone & iPad (iOS 15.0+)', 'iPhone 与 iPad（iOS 15.0+）', 'iPhone 與 iPad（iOS 15.0+）')}>iPhone &amp; iPad (iOS 15.0+)</strong></li>
              <li><span ${tr('Languages', '界面语言', '介面語言')}>Languages</span><strong>English · 简体中文 · 繁體中文</strong></li>
            </ul>
          </div>
          <div class="ios-actions">
            <a class="btn btn-primary" href="/support/" ${tr('Support & updates', '支持与动态', '支援與動態')}>Support &amp; updates</a>
            <a class="btn btn-secondary" href="https://git.qqs.tw/bobmaster/quantoot_next" ${tr('Source code', '查看源码', '檢視原始碼')}>Source code</a>
          </div>
        </article>
      </div>
    </section>
  </main>`;

  return renderPage({
    pageId: 'home',
    route: '/',
    titleEn: 'QuanToot — The social web, at your pace',
    titleZhHans: 'QuanToot · 开放社交，自在一点',
    titleZhHant: 'QuanToot · 開放社交，自在一點',
    descEn: 'QuanToot is an independent Mastodon client for iPhone, iPad, Android, and foldables. Choose your server and read the social web at your pace.',
    descZhHans: 'QuanToot 是独立的 Mastodon 客户端，适用于 iPhone、iPad、Android 与折叠屏。选择自己的实例，按自己的节奏浏览。',
    descZhHant: 'QuanToot 是獨立的 Mastodon 用戶端，適用於 iPhone、iPad、Android 與摺疊螢幕。選擇自己的站台，按自己的步調瀏覽。',
    ogTitle: 'QuanToot — The social web, at your pace',
    ogDescription: 'An independent Mastodon client for iPhone, iPad, Android, and foldables. Quiet by design — zero ads, zero tracking.',
    body,
  });
}
