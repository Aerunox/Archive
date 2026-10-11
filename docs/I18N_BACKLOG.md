# 界面多语言（i18n）迁移底账

> 现状基线（2026-10-08）：**50 个 Kotlin 文件、572 条硬编码简体中文字面量**。
> 已迁移：8 条（底部导航 4 条 + 语言设置卡 4 条）。机制已可用：`设置 → 语言` 切换后立即生效。

## 规则（哪些要翻、哪些不翻）

| 类别 | 处理 |
|---|---|
| 界面文案（菜单/按钮/设置项/提示/Toast/对话框） | 必须抽成资源字符串，随所选语言 |
| AI 提示词与输出契约（AiProtocol / VisionClient / TranslationClient 的 system 文本） | 不随界面语言（那是给模型的任务指令，改动会影响输出质量） |
| 接口返回内容（帖子标题/正文/作者名/评论） | 永不翻译（用户生成内容，按需求明确排除） |
| 语言名本身（简体中文 / English / 日本語…） | 固定用本语言写法（约定俗成） |
| 系统/框架文案（系统权限对话框、系统分享面板） | 跟随系统语言，应用无法控制（Android 惯例） |

## 逐文件清单（按文案量排序）

| 文件 | 条数 | 建议批次 |
|---|---:|---|
| TranslationSettingsScreen.kt | 55 | 批 2 |
| PostDetailScreen.kt | 44 | 批 2 |
| SettingsScreen.kt | 44 | 批 2 |
| AccountScreen.kt | 42 | 批 3 |
| StorageScreen.kt | 29 | 批 3 |
| DownloadsScreen.kt | 28 | 批 3 |
| GlobalSearchScreen.kt | 27 | 批 3 |
| CreatorDetailScreen.kt | 21 | 批 4 |
| ViewerSheets.kt | 19 | 批 4 |
| MediaViewerScreen.kt | 19 | 批 4 |
| FavoritesScreen.kt | 19 | 批 3 |
| PostSort.kt | 18 | 批 4 |
| AboutScreen.kt | 18 | 批 5 |
| NotificationsScreen.kt | 16 | 批 5 |
| AiImageSheets.kt | 14 | 批 4 |
| MineScreen.kt | 11 | 批 3 |
| PostTranslation.kt | 11 | 批 4 |
| CreatorsScreen.kt | 11 | 批 4 |
| HomeScreen.kt | 10 | 批 5 |
| VisionClient.kt | 10 | 不迁移（AI 提示词） |
| HistoryScreen.kt | 8 | 批 5 |
| BlockedContentScreen.kt | 8 | 批 5 |
| TranslationClient.kt | 7 | 错误信息迁移（批 5），契约文本不迁移 |
| Player.kt | 6 | 批 5 |
| AiRequestParams.kt | 5 | 批 2（开关的标题/说明） |
| AiAdvancedSettings.kt | 5 | 批 2 |
| ImageActionsDialog.kt | 5 | 批 4 |
| PostCard.kt | 5 | 批 4 |
| Previews.kt | 5 | 批 5 |
| Fmt.kt | 5 | 批 5（相对时间等） |
| PostSelection.kt | 4 | 批 4 |
| TranslationSettingsRepository.kt | 4 | 批 2（错误提示） |
| ArchiveRoot.kt | 4 | 批 5 |
| AgeGateScreen.kt | 4 | 批 5 |
| AppNavHost.kt | 3 | 批 5 |
| AiProtocol.kt | 3 | 不迁移（AI 提示词） |
| AppLanguage.kt | 3 | 不迁移（语言名固定本语言写法） |
| PostSortMenu.kt | 3 | 批 4 |
| MorphActions.kt | 3 | 批 5 |
| OfflineReaderScreen.kt | 2 | 批 5 |
| ExpandableSearch.kt | 2 | 批 5 |
| MiuixDesign.kt | 2 | 批 5 |
| ModelProfile.kt | 2 | 批 2 |
| OcrService.kt | 2 | 批 4（错误提示） |
| SearchRelevance.kt | 1 | 不迁移（非界面） |
| ExpandableStepperButton.kt | 1 | 批 5 |
| AiCaller.kt | 1 | 批 5（错误提示） |
| HtmlText.kt | 1 | 批 5 |
| CommonStates.kt | 1 | 批 5 |
| ScrollToTopButton.kt | 1 | 批 5 |

## 分批方案

| 批次 | 内容 | 条数（约） |
|---|---|---:|
| 已完成 | 机制 + 底部导航 + 语言卡片 | 8 |
| 批 2 | 设置页全套（含 AI 设置页）+ 帖子详情 | 约 110 |
| 批 3 | 我 / 收藏 / 存储 / 下载 / 搜索 / 历史 | 约 140 |
| 批 4 | 查看器、作者页、AI 面板、卡片、菜单 | 约 110 |
| 批 5 | 其余长尾 + 错误提示 | 约 120 |

每批的固定动作：抽成 values/strings.xml 的键，10 个语言目录同步补齐，编译装机验证。