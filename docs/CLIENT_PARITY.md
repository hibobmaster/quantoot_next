# 客户端功能对照

0.6.1 修复了首次 iOS 体验中报告的七类问题，并对照 `/Users/bobmaster/Projects/quantoot` 中的 Flutter 实现补齐了以下交互。

| 需求 | 当前行为 | 代码 |
| --- | --- | --- |
| 语言选择布局 | 全宽底部面板、当前项标记、可滚动选项和引导页 | `Foundation.kt`、`QuanTootApp.kt` |
| 空实例继续登录 | 留空使用 m.qqs.tw，登录和公开浏览都可继续 | `Accounts.kt` |
| 刷新与服务器同步 | 顶部下拉刷新，移除 Refresh 行；分开处理新帖、编辑、删除、通知和重连 | `MastodonApi.kt`、`StatusProjection.kt`、`FeedCache.kt` |
| 图片填满容器 | 根据尺寸选择预览比例并裁切填满；画廊保留完整原图 | `MediaAttachments.kt`、`MediaGallery.kt` |
| 回复展示 | 正文和回复数打开会话，上下文、回复层级和固定回复入口；发布后定位回复所属会话 | `StatusCard.kt`、`ThreadScreen.kt`、`ThreadState.kt` |
| 控件样式 | 统一选择面板、开关、圆角设置行、帖子操作面板、图标、编辑器和资料页背景图 | `Foundation.kt`、`Settings.kt`、`PostActionSheet.kt`、`Composer.kt`、`Accounts.kt` |
| 原版缺口 | 多图翻页与缩放、ALT、标签和提及内部跳转、链接预览图、引用解析、表情分类、标签补全、发帖语言、服务器规则与联系方式 | `ContentLinks.kt`、`PostContent.kt`、`EmojiPickerSheet.kt`、`ComposerTools.kt`、`ServerInformation.kt` |

服务器同步保持时间线的来源和账户隔离。编辑更新原位置，删除不会被较早的请求恢复，新内容在阅读旧帖时暂存并显示提示。前台每 20 秒进行 REST 补偿，重新连接后也会补齐数据。游客使用 REST；会话页通过上下文接口补齐不能从当前流获得的回复。后台取消活动任务。

媒体敏感标记只遮挡媒体，不再无故隐藏没有内容警告的正文。画廊的分享、ALT 和缩放对应当前附件，未支持的附件仍保留其页码。保存操作目前只支持图片。

引用展示遵循服务器返回的授权状态。只有 `accepted` 可以展示引用内容；待批准、撤回、屏蔽、静音和未知状态显示不可用提示。[Mastodon Quote 规范](https://docs.joinmastodon.org/entities/Quote/)

共享测试覆盖事件与请求交错、删除、操作回执、分页游标、引用状态、媒体索引和内部链接。iOS 界面检查使用原有测试账号，不清除其存储，不发送帖子。检查中的草稿在退出时丢弃。当前验证摘要位于 `.audit/client-polish/verification.json`，阶段提交保留在 `codex/client-polish` 分支。

最终共享测试为 90 项通过、0 项失败；iPhone 17 Pro 上的 7 项界面场景全部通过。WebSocket 的 Ktor 调度器回归测试先失败后通过；修复后的两次界面运行分别在进入公开时间线约 5.4 秒和 5.3 秒时观察到新帖提示，早于 20 秒的 REST 补偿周期。编辑和删除的交错由共享测试验证，未在真实账号上制造编辑、删除事件。

0.6.1（7）的 arm64 Release 已通过签名校验，并覆盖安装、启动于实体 iPhone 18 Pro（iOS 27.2）；设备回读确认版本为 0.6.1、构建号为 7。IPA 位于 `iosApp/build/releases/QuanToot-0.6.1-7.ipa`，源代码快照为 `4ae09f0`。设备回执、截图和 IPA 的 SHA-256 保存在上述验证摘要中。

本轮没有联网执行发帖、删帖、投票、举报及账户设置写入。图片写入系统相册的权限流程也未在这轮操作中验收。截图和只读检查不代表这些写入流程已经通过。
