import { renderPage, tr } from './layout.mjs';

export function renderPrivacyPage() {
  const body = `
  <main class="doc-main" id="main">
    <header class="doc-header">
      <p class="kicker"><span class="kicker-rule" aria-hidden="true"></span><span>QuanToot</span></p>
      <h1 ${tr('Privacy policy', '隐私政策', '隱私權政策')}>Privacy policy</h1>
      <p class="doc-meta" ${tr('Updated November 19, 2026', '更新日期：2026 年 11 月 19 日', '更新日期：2026 年 11 月 19 日')}>Updated November 19, 2026</p>
    </header>
    <div class="doc-body">
      <section lang="en" data-language="en">
        <p class="doc-intro">QuanToot is an independent Mastodon client developed by BobMaster. This policy covers the iOS and Android apps and this website.</p>
        <h2>Data on your device</h2>
        <p>QuanToot stores your selected server, app preferences, and cached timelines and media on the device. Your Mastodon session and access token are stored with OS-provided secure storage. The app has no advertising, analytics, or tracking SDKs. QuanToot does not operate a social network or collect this local app data on a developer backend.</p>
        <h2>Servers, content, and downloads</h2>
        <p>QuanToot communicates with the Mastodon server you select. That operator may process your IP address, account data, posts, media, interactions, and activity according to its policies. Media hosts and linked sites may receive normal connection information when content is loaded. On Android, the optional update check requests release information and packages from the Forgejo host.</p>
        <h2>Photos and media</h2>
        <p>The app uses the system picker for media you select to attach or upload. A chosen item is sent to your server when you upload it. If you explicitly save a file to the photo library, QuanToot requests the access needed for that action.</p>
        <h2>Your choices and retention</h2>
        <p>Use <strong>Accounts → Remove account</strong> to remove a saved session, its token, and its cached timelines and notifications. Clear cached media in app settings. Removing the app clears its app container; iOS Keychain items can outlive an uninstall, so remove saved accounts before uninstalling if you want to clear their credentials. For permanent account deletion and server-held data, use <strong>Settings → Delete Mastodon account</strong> or contact your instance. <a href="/support/#account">Account deletion instructions</a>.</p>
        <h2>Website and support email</h2>
        <p>This site uses session storage for its language selector and contains no analytics scripts. Its hosting provider may process routine request information needed to deliver pages and protect the service. When you email support, BobMaster receives your email address, message, and any information you choose to include, and keeps it as needed to respond or handle follow-up. You can request deletion by email.</p>
        <h2>Contact</h2>
        <p>BobMaster · <a href="mailto:bobmaster@hibobmaster.com">bobmaster@hibobmaster.com</a></p>
      </section>
      <section lang="zh-Hans" data-language="zh-Hans" hidden>
        <p class="doc-intro">QuanToot 是 BobMaster 开发的独立 Mastodon 客户端。本政策适用于 iOS、Android 应用和此网站。</p>
        <h2>保存在设备上的数据</h2>
        <p>QuanToot 会在设备上保存所选实例、应用偏好、时间线和媒体缓存。Mastodon 登录会话和访问令牌由系统安全存储保护。应用不包含广告、分析或追踪 SDK，开发者未运营社交网络或收集这些本地应用数据的后端。</p>
        <h2>实例、内容与下载</h2>
        <p>应用直接与所选的 Mastodon 实例通信。实例运营者可能按其政策处理你的 IP 地址、账号资料、帖子、媒体、互动和活动记录。加载远程媒体或访问链接时，相关网站可能收到常规连接信息。在 Android 上主动检查更新时，应用会向 Forgejo 请求版本信息或安装包。</p>
        <h2>照片和媒体</h2>
        <p>应用通过系统选择器读取你明确选择的媒体。上传时，所选文件会发送给你的实例。主动保存文件到照片图库时，应用会按需请求相应权限。</p>
        <h2>数据清理与账号删除</h2>
        <p>可在<strong>账号 → 移除账号</strong>删除保存的会话和令牌，在应用设置中清理媒体缓存。卸载应用会清除应用容器，但 iOS 钥匙串数据可能在卸载后保留，如需删除登录凭据，请先在应用中移除账号。要永久删除账号及实例持有的数据，请使用<strong>设置 → 删除 Mastodon 账号</strong>或联系实例运营者。<a href="/support/#account">查看删除步骤</a>。</p>
        <h2>网站与反馈邮件</h2>
        <p>网站使用会话存储记住语言选择，不加载分析脚本。托管服务可能处理提供页面和保护服务所需的常规请求数据。你主动发邮件时，BobMaster 会收到你的邮箱地址、邮件内容和附件，仅按处理反馈和后续沟通所需保留。你可通过邮件申请删除。</p>
        <h2>联系开发者</h2>
        <p>BobMaster · <a href="mailto:bobmaster@hibobmaster.com">bobmaster@hibobmaster.com</a></p>
      </section>
      <section lang="zh-Hant" data-language="zh-Hant" hidden>
        <p class="doc-intro">QuanToot 是 BobMaster 開發的獨立 Mastodon 用戶端。本政策適用於 iOS、Android 應用程式和本網站。</p>
        <h2>儲存在裝置上的資料</h2>
        <p>QuanToot 會在裝置上儲存所選站台、應用程式偏好，以及時間軸和媒體快取。Mastodon 登入工作階段和存取權杖由作業系統提供的安全儲存機制保護。應用程式不含廣告、分析或追蹤 SDK。QuanToot 未營運社群網路，開發者也未架設後端收集這些本機資料。</p>
        <h2>站台、內容與下載</h2>
        <p>QuanToot 會與你選擇的 Mastodon 站台通訊。站台營運者可能依其政策處理你的 IP 位址、帳號資料、貼文、媒體、互動和活動紀錄。載入遠端媒體或開啟連結時，相關網站可能會收到一般連線資訊。你在 Android 上主動檢查更新時，應用程式會向 Forgejo 請求版本資訊或安裝套件。</p>
        <h2>照片與媒體</h2>
        <p>附加或上傳媒體時，應用程式會透過系統選擇器讀取你選取的項目。你上傳時，所選檔案會傳送至你的站台。你主動將檔案儲存到照片圖庫時，QuanToot 會請求所需權限。</p>
        <h2>資料清除與帳號刪除</h2>
        <p>使用<strong>帳號 → 移除帳號</strong>可移除已儲存的登入工作階段、權杖及該帳號的時間軸與通知快取。你也可以在應用程式設定中清除媒體快取。解除安裝會清除應用程式容器，但 iOS 鑰匙圈項目可能保留。若要清除登入憑證，請先在應用程式內移除帳號。若要永久刪除帳號及站台保有的資料，請使用<strong>設定 → 刪除 Mastodon 帳號</strong>或聯絡站台營運者。<a href="/support/#account">查看帳號刪除步驟</a>。</p>
        <h2>網站與支援郵件</h2>
        <p>本網站會在瀏覽器的工作階段儲存空間記住所選語言，並且不載入分析指令碼。代管業者可能會處理提供頁面及保護服務所需的一般請求資訊。你寄信尋求支援時，BobMaster 會收到你的電子郵件地址、信件內容和你提供的資料，並依回覆及後續處理所需保留。你可以透過電子郵件提出刪除申請。</p>
        <h2>聯絡開發者</h2>
        <p>BobMaster · <a href="mailto:bobmaster@hibobmaster.com">bobmaster@hibobmaster.com</a></p>
      </section>
    </div>
  </main>`;

  return renderPage({
    pageId: 'privacy',
    route: '/privacy/',
    titleEn: 'Privacy policy · QuanToot',
    titleZhHans: '隐私政策 · QuanToot',
    titleZhHant: '隱私權政策 · QuanToot',
    descEn: 'How QuanToot handles app data, Mastodon account data, media, and support messages.',
    descZhHans: '了解 QuanToot 如何处理应用数据、Mastodon 账号数据、媒体和反馈邮件。',
    descZhHant: '了解 QuanToot 如何處理應用程式資料、Mastodon 帳號資料、媒體和支援郵件。',
    body,
  });
}
