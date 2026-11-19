import { renderPage, tr } from './layout.mjs';

export function renderSupportPage() {
  const body = `
  <main class="doc-main" id="main">
    <header class="doc-header">
      <p class="kicker"><span class="kicker-rule" aria-hidden="true"></span><span>QuanToot</span></p>
      <h1 ${tr('Support & safety', '支持与安全', '支援與安全')}>Support &amp; safety</h1>
      <p class="doc-intro" ${tr(
        'Get help with the app, report abuse, or find the right place to manage your account.',
        '在这里获取应用帮助，了解如何举报、管理账号和联系开发者。',
        '在這裡取得應用程式支援，了解如何檢舉、管理帳號及聯絡開發者。'
      )}>Get help with the app, report abuse, or find the right place to manage your account.</p>
    </header>
    <div class="doc-contact-card">
      <div class="doc-contact-copy">
        <a class="doc-contact-mail" href="mailto:bobmaster@hibobmaster.com?subject=QuanToot%20feedback">bobmaster@hibobmaster.com</a>
        <p ${tr(
          'BobMaster handles app feedback. Include your OS, app version, server, and steps to reproduce. Keep passwords, access tokens, and private messages out of your email.',
          '应用反馈由 BobMaster 处理。请附上系统和应用版本、所用实例及重现步骤。请不要发送账号密码、访问令牌或私信。',
          '應用程式意見由 BobMaster 處理。請附上作業系統和應用程式版本、使用的站台及重現步驟。請勿寄送帳號密碼、存取權杖或私訊。'
        )}>BobMaster handles app feedback. Include your OS, app version, server, and steps to reproduce. Keep passwords, access tokens, and private messages out of your email.</p>
      </div>
      <div>
        <button class="btn btn-secondary" type="button" data-copy-email="bobmaster@hibobmaster.com" ${tr('Copy email', '复制邮箱', '複製信箱')}>Copy email</button>
        <p class="doc-meta" data-copy-feedback hidden ${tr('Copied to clipboard', '已复制到剪贴板', '已複製到剪貼簿')}>Copied to clipboard</p>
      </div>
    </div>
    <div class="doc-body">
      <section lang="en" data-language="en" id="safety" data-section-id="safety">
        <h2>Report, block, and filter</h2>
        <p>Open a post’s or profile’s menu and choose <strong>Report</strong>. The report goes to moderators on your Mastodon server. Use <strong>Block</strong> on a profile to prevent unwanted contact, or mute an account to hide its posts. In <strong>Settings → Filters</strong>, add words or phrases to hide or warn about matching content.</p>
        <p>Abuse, harassment, illegal content, and non-consensual intimate material have no place in QuanToot. Follow your server’s rules when posting. If your server’s reporting path is unavailable, or content continues to be surfaced in QuanToot after you report it, <a href="mailto:bobmaster@hibobmaster.com?subject=QuanToot%20safety%20report">contact BobMaster</a> with public post links and the relevant server. The developer can investigate app-level issues. The server’s moderators control content on that service.</p>
        <h2 id="account" data-section-id="account">Delete or remove an account</h2>
        <p>For permanent deletion, sign in with the account you want to delete, then open <strong>Settings → Delete Mastodon account</strong>. Continue to your own server’s deletion page and confirm there. If your server uses a different route, open its web preferences, select <strong>Account</strong>, and find <strong>Delete account</strong> or contact the operator. <a href="https://docs.joinmastodon.org/user/moving/#deleting-your-account">Mastodon account documentation</a>.</p>
        <p>To remove the sign-in from this device, open <strong>Accounts</strong> and use <strong>Remove account</strong>. To clear cached media, use <strong>Settings → Clear media cache</strong>.</p>
        <h2>Common questions</h2>
        <div class="doc-faq">
          <details><summary>Which server should I enter?</summary><p>Use the domain after the final @ in your Mastodon handle. Leaving the field blank connects to mastodon.social by default. If you are browsing as a guest, a server may require sign-in before showing some public timelines.</p></details>
          <details><summary>Does QuanToot ever see my Mastodon password?</summary><p>No. Sign-in opens your selected server’s OAuth page in the system browser. QuanToot stores only the resulting access token in iOS Keychain or Android Keystore.</p></details>
          <details><summary>Where can I get the app?</summary><p><a href="/#android">Download the official signed Android packages</a>. iPhone and iPad availability will be announced here after App Store approval.</p></details>
        </div>
      </section>
      <section lang="zh-Hans" data-language="zh-Hans" data-section-id="safety" hidden>
        <h2>举报、屏蔽与过滤</h2>
        <p>打开帖子或个人资料的菜单，选择<strong>举报</strong>，举报会发送给你所在实例的管理员。你也可在个人资料菜单里屏蔽或静音账号，或在<strong>设置 → 过滤器</strong>中管理关键词。</p>
        <p>请遵守所在实例的发帖规则。QuanToot 不欢迎骚扰、仇恨、违法内容及未经同意传播的私密影像。如果实例无法受理，或举报后的内容仍在 QuanToot 里异常显示，请<a href="mailto:bobmaster@hibobmaster.com?subject=QuanToot%20safety%20report">联系 BobMaster</a>，附上公开帖文链接及实例名称。开发者可以排查应用端问题，服务器上的内容由该实例管理员处理。</p>
        <h2 data-section-id="account">删除或移除账号</h2>
        <p>要永久删除账号，请登录对应账号，打开<strong>设置 → 删除 Mastodon 账号</strong>，进入所属实例的删除页面，并在实例上确认。如果实例使用不同路径，请从实例的网页偏好设置进入<strong>账号</strong>，找到删除入口或联系实例管理员。<a href="https://docs.joinmastodon.org/user/moving/#deleting-your-account">Mastodon 账号文档</a>。</p>
        <p>如只需移除此设备上的登录，打开<strong>账号</strong>页面并选择<strong>移除账号</strong>。可在<strong>设置 → 清除媒体缓存</strong>清理已缓存的媒体。</p>
        <h2>常见问题</h2>
        <div class="doc-faq">
          <details><summary>应该填写哪个实例？</summary><p>填入 Mastodon 账号最后一个 @ 后面的域名；留空时默认使用 mastodon.social。以访客身份浏览时，部分实例可能要求先登录才能读取公开时间线。</p></details>
          <details><summary>QuanToot 会获取我的 Mastodon 密码吗？</summary><p>不会。登录会通过系统浏览器跳转到你所选实例的 OAuth 授权页，应用仅使用 iOS 钥匙串或 Android Keystore 安全保存访问令牌。</p></details>
          <details><summary>在哪里下载？</summary><p><a href="/#android">下载正式签名的 Android 安装包</a>。iPhone 和 iPad 版通过 App Store 审核后会在这里公布。</p></details>
        </div>
      </section>
      <section lang="zh-Hant" data-language="zh-Hant" data-section-id="safety" hidden>
        <h2>檢舉、封鎖與篩選</h2>
        <p>開啟貼文或個人資料的選單，選擇<strong>檢舉</strong>，檢舉會送交你所在站台的管理員。你可以在個人資料選單中<strong>封鎖</strong>對方以避免不想要的聯絡，也可以將帳號靜音以隱藏貼文。在<strong>設定 → 過濾器</strong>中加入關鍵字或詞組，便可隱藏符合條件的內容或顯示警告。</p>
        <p>請遵守所在站台的發文規則。QuanToot 不容許濫用、騷擾、違法內容及未經同意散布的私密影像。如果站台無法受理檢舉，或檢舉後內容仍在 QuanToot 顯示，請<a href="mailto:bobmaster@hibobmaster.com?subject=QuanToot%20safety%20report">聯絡 BobMaster</a>，並附上公開貼文連結及相關站台。開發者可以排查應用程式的問題，站台上的內容則由該站台管理員處理。</p>
        <h2 data-section-id="account">刪除或移除帳號</h2>
        <p>若要永久刪除帳號，請先登入該帳號，開啟<strong>設定 → 刪除 Mastodon 帳號</strong>，接著前往你的站台並在刪除頁面確認。如果站台使用不同路徑，請在站台的網頁偏好設定中開啟<strong>帳號</strong>，找到<strong>刪除帳號</strong>，或聯絡站台營運者。<a href="https://docs.joinmastodon.org/user/moving/#deleting-your-account">Mastodon 帳號說明</a>。</p>
        <p>若只想從這部裝置移除登入資料，請開啟<strong>帳號</strong>並選擇<strong>移除帳號</strong>。若要清除媒體快取，請使用<strong>設定 → 清除媒體快取</strong>。</p>
        <h2>常見問題</h2>
        <div class="doc-faq">
          <details><summary>我應該輸入哪個站台？</summary><p>請輸入 Mastodon 帳號最後一個 @ 之後的網域；留空時預設連接 mastodon.social。以訪客身分瀏覽時，部分站台可能會要求先登入，才能查看某些公開時間軸。</p></details>
          <details><summary>QuanToot 會取得我的 Mastodon 密碼嗎？</summary><p>不會。登入流程會透過系統瀏覽器開啟你所選站台的 OAuth 頁面，應用程式僅以 iOS 鑰匙圈或 Android Keystore 安全儲存存取權杖。</p></details>
          <details><summary>在哪裡下載？</summary><p><a href="/#android">下載正式簽署的 Android 安裝套件</a>。iPhone 和 iPad 版通過 App Store 審核後會在這裡公布。</p></details>
        </div>
      </section>
    </div>
  </main>`;

  return renderPage({
    pageId: 'support',
    route: '/support/',
    titleEn: 'Support & safety · QuanToot',
    titleZhHans: '支持与安全 · QuanToot',
    titleZhHant: '支援與安全 · QuanToot',
    descEn: 'Get help with QuanToot, contact the developer, report abuse, or learn how to delete a Mastodon account.',
    descZhHans: '获取 QuanToot 帮助、联系开发者、举报违规内容，或了解如何删除 Mastodon 账号。',
    descZhHant: '取得 QuanToot 支援、聯絡開發者、檢舉違規內容，或了解如何刪除 Mastodon 帳號。',
    body,
  });
}
