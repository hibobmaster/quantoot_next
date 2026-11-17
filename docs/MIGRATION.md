# Flutter 到 Kotlin Multiplatform 的迁移

源项目位于 `/Users/bobmaster/Projects/quantoot`。本仓库独立实现 Kotlin 客户端，源项目未修改。

## 功能对应

| 原项目功能 | Kotlin 实现 |
| --- | --- |
| 首次语言选择、系统语言、简体和繁体中文、英文 | `QuanTootApp.kt`、`ui/Translations.kt`，复用 291 条原始文案 |
| 实例选择、未登录浏览 | `auth/AccountStore.kt`、`ui/Accounts.kt` |
| OAuth、可选 PKCE、state、多账户、切换与移除 | `auth/AccountStore.kt`，随机 state、10 分钟有效期、单次消费、加密提交 |
| 本站、跨站、主页、标签、账户、收藏、书签时间线 | `data/MastodonApi.kt`、`ui/Feeds.kt` |
| 分页、刷新、阅读位置、新消息暂存、恢复前台 | `ui/FeedCache.kt`、`ui/Feeds.kt`，缓存按实例、账户和时间线隔离 |
| 实时更新、编辑、删除、通知 | 带来源的事件、`StatusProjection`、`FeedPager`，后台取消，前台重连，断线退避和前台 REST 补偿 |
| 嘟文详情、上下文、回复、转发、喜欢、书签、引用 | `ui/StatusCard.kt`、`ui/ThreadScreen.kt`、`ui/ThreadState.kt`，正文和回复数可打开会话 |
| 内容警告、HTML 链接、自定义表情、预览卡 | `ui/StatusCard.kt`、`ui/RichContent.kt` |
| 多图查看、缩放、保存、分享、替代文本 | `ui/MediaAttachments.kt`、`ui/MediaGallery.kt`、原生媒体适配 |
| 视频播放 | Android VideoView、iOS AVPlayerViewController |
| 发帖、回复、编辑、引用、可见性、语言和引用策略 | `ui/Composer.kt`、`StatusDraft` |
| 附件、异步媒体处理、替代文本、投票、自定义表情 | `MastodonApi.upload()`、`ui/Composer.kt` |
| 趋势、标签、账户和嘟文搜索 | `ui/Feeds.kt`，支持搜索分页 |
| 通知分组、未读标记、关注请求批准与拒绝 | `ui/Feeds.kt`，保留原始通知分页游标 |
| 资料、关注列表、关注、静音、屏蔽、举报 | `ui/Accounts.kt` |
| 活动和媒体筛选、置顶内容、关于、个人二维码 | `ui/Accounts.kt`、`ui/RichContent.kt` |
| 修改昵称、简介、头像、背景图、自定义字段 | `ProfileUpdate`、`ui/Accounts.kt` |
| 隐私、默认发帖设置、过滤器、通知策略 | `ui/Settings.kt`，使用对应服务器 API |
| 服务器信息、隐私政策、图片缓存清理 | `ui/Settings.kt` |
| 原有配色和图标、系统字体 | `ui/Foundation.kt`、`composeResources` |

## 数据和安全

Android 应用 ID 和 iOS bundle ID 保持 `com.quanquan.quantoot`。Android 使用 Keystore AES-GCM，iOS 使用设备本地 Keychain。选择的实例和语言保存在普通偏好中。

Android 导入原项目默认 FlutterSecureStorage 11 的 RSA-OAEP 加 AES-GCM 数据。iOS 导入原项目 `flutter_secure_storage_service` Keychain 记录。两端先保存新记录，再删除旧记录。旧版明文 v1、v2 账户也在安全提交后迁移。自定义过 FlutterSecureStorage 命名空间或算法的发行版需要单独增加适配。

签名和 Keychain 访问组必须与原发行版一致，系统才允许读取原有凭据。模拟器测试使用合成凭据，不代表已经在用户的已安装发行版上完成升级验证。

请求禁止自动重定向。令牌只进入请求头和加密存储。普通响应限制为 8 MiB，OAuth 响应限制为 2 MiB，附件限制为 30 MiB。解析限制 JSON 深度、结构数量和嵌套嘟文深度。外部内容只允许无用户名、密码的 HTTP(S) URL。

收藏、书签、粉丝和关注列表使用 `Link` 响应头的服务器游标。同一账户重新登录会更新授权版本并重建客户端，避免沿用失效令牌。

## 有意调整

- OAuth 使用系统浏览器授权，替换嵌入式 WebView。
- 按用户要求，实例输入留空时使用 `m.qqs.tw`，登录和公开浏览均可继续。
- UI 共用 Compose。导航、间距和媒体控件按当前平台实现，未追求逐像素一致。
- 草稿保留在当前账户的内存状态中。离开编辑器前会确认放弃，重启后不恢复草稿。
- 时间线缓存最多保存 100 项、512 KiB。显示服务器不支持的设置时，会报告 API 错误。

## 验证记录

构建日志、回归测试证据和截图位于 `.audit/verification`。决策记录位于 `.audit/kmp-migration.tsv`。

本次通过 43 项共享测试、2 项 iOS Keychain 测试和 1 项原生 iOS 游客浏览 UI 测试。Android 设备验证通过 Keystore 加密、Flutter v3 导入、重新打开与移除。Android 和 iOS 都已实际显示公开帖子。最终测试数量与结果记录在 `.audit/verification/summary.json`。

iOS UI 测试使用 `m.qqs.tw` 的公开时间线，因此需要网络。它只在没有已选实例的模拟器上运行初次设置，否则跳过以保护已有状态。存储迁移测试遇到真实已存账户时也会跳过。此次两项存储测试和游客 UI 测试均未跳过。

共享 MockEngine 测试不访问用户账号。0.6.1 的界面修复使用 iPhone 17 Pro 模拟器上已有的测试账号，验证登录状态保留、现有回复、媒体、编辑器和服务器资料。那一轮没有向服务器发帖、编辑、删除、投票、举报或写入账户设置。

0.6.2 使用同一测试账号验证了临时帖的发布、编辑、删除和一张 JPEG 的相册保存。具体范围和限制记录在 [客户端功能对照](CLIENT_PARITY.md)。初始迁移的 43 项测试证据保留为历史基线。0.6.1 结果位于 `.audit/client-polish`，0.6.2 结果位于 `.audit/client-refinement`。
