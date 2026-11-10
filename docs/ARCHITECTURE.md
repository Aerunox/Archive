# Archive 架构速查（2026-10-08 静态盘点；2026-11-09 更新：离线归档落地方式修正 / v9 迁移 / 死代码清理）

> 本文档是代码库只读分析产物，用于后续修改任务的定位参考。所有结论基于对源码的直接阅读；
> 标注等级：【已确认】= 有源码直接依据；【推测】= 合理推断；【未知】= 证据不足。

## 1. 项目身份

- Android 客户端（applicationId `com.archive.app`，versionName 1.0.2 / versionCode 3，minSdk 30 / targetSdk 36 / compileSdk 37）【已确认：app/build.gradle.kts】
- 三个 Gradle 模块：`:app`（UI，包 `com.archive.app`）→ 依赖 `:data`（包 `com.pawchive.data`）→ 依赖 `:core`（包 `com.pawchive.core`）；无 `:feature` 模块【已确认：模块路径与 import】
- 基于上游 Pawchive 改造：core/data 沿用上游包名与多数基础设施，app 层为全新 Compose 重写（源码注释多处「照 Pawchive」「抄自 Pawchive Application」）【已确认：代码注释】
- 仓库目录下无 `.git`（不是 git 工作树）【已确认：git log/status 报错】
- 主要技术栈【已确认：build.gradle.kts】：Hilt 2.60.1、Room 2.8.5、Retrofit 3.0.0、OkHttp 5.5.0、Coil 2.6.0、WorkManager 2.12.0、DataStore 1.2.1、ONNX Runtime（OCR）、kotlinx-serialization、Miuix（`top.yukonga.miuix.kmp`，单一 HyperOS 风格主题）

## 2. 模块职责与关键文件

### :core — 基础设施（无 UI）
| 领域 | 文件 | 要点 |
|---|---|---|
| API 装配 | `api/ApiClient.kt` | 门面：`publicApi` / `authApi(cookie)`（按 cookie 缓存实例）/ `loginApi`（不跟随重定向）；三个 OkHttp：`sharedOkHttpClient`（API/图片，60s callTimeout）、`downloadOkHttpClient`（无 callTimeout、read 60s）、`imageOkHttpClient`（轻量）【已确认】 |
| Cloudflare 过盾 | `api/CloudflareManager.kt`、`ClearanceCoordinator.kt`、`ClearanceInterceptor.kt`、`ClearanceRetryInterceptor.kt` | 隐藏 WebView 执行挑战 → 提取 `cf_clearance` + 绑定 UA（`Pawchive-Android (...)`）→ EncryptedSharedPreferences 持久化，TTL 25 分钟（`ApiConstants.CLEARANCE_TTL_MS`）；注入时剥除 `session=` 段防登录循环；403 由拦截器自动强制刷新重试一次【已确认】 |
| 请求封装 | `api/ApiCallHandler.kt`、`ApiResult.kt`、`api/ApiMemoryCache.kt` | 统一错误封装；GET JSON 内存缓存 5 分钟 / 200 条 LRU，键含账号 session hash 命名空间，`Cache-Control: no-cache` 跳过，支持 `invalidateByPathPrefix`（收藏写后失效用）【已确认】 |
| 常量 | `api/ApiConstants.kt` | 主域 pawchive.pw、API `/api/v1/`、PAGE_SIZE 50、缓存/过盾参数单源【已确认】 |
| 会话 | `store/SessionManager.kt` | EncryptedSharedPreferences；多账号（username→cookie 列表、切换/移除）；加密不可用=视为未登录【已确认】 |
| 设置 | `store/SettingsManager.kt` | DataStore `app_settings` + 启动期 SharedPreferences 轻量缓存（ARCH-007）；大量设置项（语言/外观/启动 Tab/下载并发 1–5/重试 1–3/仅 Wi-Fi/命名格式/更新通道/强调色/动态取色/UI 与字号缩放/自动备份等）【已确认】 |
| 数据库 | `db/PawchiveDatabase.kt` + 5 个 DAO | **v9**，5 张表：download_records、download_rules、creator_subscriptions、content_updates、pending_favorite_actions；迁移 1→2→…→9 全部显式 `Migration`，**禁止** destructive 回退。v9 移除了从未接入调用方的 offline_archives + FTS4 两张表（离线归档实际由 BookmarkManager 的 DataStore 承担，见下）【已确认】 |
| 模型/工具 | `model/`、`error/AppError.kt`、`util/` | Gson 模型（Post 等）；AppError 统一错误树；SafeHtmlHelper、CrashHandler、NetworkUtils 等【已确认】 |

### :data — 业务逻辑（无 UI）
| 领域 | 文件 | 要点 |
|---|---|---|
| 认证/收藏 | `repository/AuthRepository.kt` | 登录=302+Set-Cookie 解析；登出/切号清本地数据；收藏增删走离线队列入口；登录宽限期 60s 内 401 不判失效；写后失效收藏缓存【已确认】 |
| 下载引擎 | `repository/DownloadCenter.kt`、`HttpDownloadManager.kt`、`DownloadRepository.kt`、`DownloadHistoryManager.kt` | 协程下载（彻底移除 WorkManager/okdownload）：并发信号量 1–5（服务器合规硬限）、去重指纹 SHA-256、preview_only 跳过、仅 Wi-Fi 等待、暂停/恢复走 HTTP Range 断点（Room 检查点 + 服务端 206/总长校验）、失败删半截文件；目标路径 SAF 树 URI 优先、MediaStore 兜底（IS_PENDING 收尾）【已确认】 |
| 收藏主数据 | `repository/BookmarkManager.kt` | DataStore `pawchive_bookmarks`（有序键 + 旧 SP 迁移）；收藏帖在打开详情页时写入**全文缓存**（`post_object_<service>_<creator>_<post>` 键存完整 Post JSON）——这就是离线归档本体：`searchBookmarkedPosts`（标题/作者/正文包含匹配）供搜索页「离线归档」Tab，`getBookmarkedPost` 供详情页断网回退，`archivedPostCount`/`archivedBytes` 供存储页统计【已确认】 |
| 离线归档（退役记录） | —— | 旧方案的 Room + FTS4（offline_archives 两张表 + OfflineArchiveDao）自始至终无调用方，已在 v9 迁移中 DROP 并删除孤儿代码；不要在文档/注释里再引用 OfflineArchiveRepository/OfflineArchiveIndexer（均不存在）【已确认】 |
| 订阅/通知 | `repository/CreatorSubscriptionRepository.kt` + `work/ContentUpdateWorker.kt`、`ContentUpdateNotifier.kt` | 周期同步新帖（间隔≥15 分钟，默认 30）【已确认】 |
| 下载规则 | `repository/DownloadRuleEngine.kt`、`DownloadRuleRepository.kt` | 规则化自动下载【已确认】 |
| 备份 | `repository/BackupManager.kt` | JSON bundle schema v1；导出收藏/屏蔽/订阅/进度/下载历史/部分设置；会话 Cookie 与搜索历史永不导出；导入覆盖式【已确认】 |
| 更新检查 | `github/UpdateChecker.kt` | GithubApi；STABLE/BETA 通道；语义化版本比较；「忽略此版本」；24h/7d/每次启动【已确认】 |
| Worker | `work/FavoriteActionsSyncWorker.kt`、`CacheCleanWorker.kt`、`AutoBackupWorker.kt`、`work/ContentUpdateWorker.kt` | 收藏离线补写、缓存阈值清理、每日自动备份、订阅同步（2026-11-09 删除了无调用方的 DownloadWorker 遗留死代码）【已确认】 |
| 其他 | `repository/BlockedCreatorManager.kt`、`SearchHistoryManager.kt`、`ReadingProgressManager.kt`、`LocalDataCleaner.kt`、`CacheRepository.kt`、`CreatorNameCache.kt`、`SimilarCreatorsRepository.kt`、`download/DownloadNotificationController.kt`、`DownloadFeedback.kt` 等 | 屏蔽名单、搜索历史、阅读进度、清数据、缓存管理、昵称缓存【已确认】 |

### :app — Compose UI + 站点数据层 + AI/OCR（包 `com.archive.app`）
| 领域 | 文件 | 要点 |
|---|---|---|
| 入口/DI | `ArchiveApp.kt`、`MainActivity.kt`、`di/AppModule.kt` | 启动：WorkManager 初始化、CrashHandler、CloudflareManager.init、屏蔽快照预热、ClearanceCoordinator.preheat、缓存清理/内容同步/收藏队列/自动备份调度；语言在 attachBaseContext 落地；AppModule 只提供 AppDatabase（v1，**fallbackToDestructiveMigration(false)**，改 schema 不加迁移=打开即崩）【已确认】 |
| 根组合 | `ui/ArchiveRoot.kt`、`ui/navigation/AppNavHost.kt` | AgeGate 在最外层；主题切换只重组不重建 Activity；多选/屏蔽操作条全局；导航 15 条路由（main/settings/search/downloads/notifications/blocked/translation/about/storage/history/login/creator/post/viewer），全屏菜单含全站搜索/历史/下载【已确认】 |
| 站点 API/数据 | `data/remote/PawchiveApi.kt`、`data/repo/PostRepository.kt`、`PostPagingSource.kt`、`CreatorRepository.kt`、`Mappers.kt`、`domain/Models.kt` | Offset 分页（PAGE_SIZE 50，短页即结尾）；**PagingConfig：initialLoadSize=一页、prefetchDistance=8、无占位**（对齐推特/JHenTai 的适量加载，2026-11-10）；**列表封面一律缩略图**（PostCard 默认 preferOriginal=false，原图仅查看器）；创作者库约 9.6 万行全量入 Room（15MB JSON），本地搜索/排序/分页；PostDetail 含 deferred 附件（tempUrl HLS 等，约 30 天过期）【已确认】 |
| 本地数据 | `data/local/AppDatabase.kt`、`repo/LocalStore.kt` | Room v1：creators / favorite_posts / history（三张表，无迁移）【已确认】 |
| 收藏动作 | `data/repo/FavoriteActions.kt`、`FavoriteSeqRepository.kt` | 本地收藏 + 云端 + 收藏全文缓存（BookmarkManager）+ 收藏即订阅；云端收藏序号索引（faved_seq，仅登录后）【已确认】 |
| 屏蔽 | `data/BlockedContentFilter.kt`、`BlockedPostManager.kt`、`data/repo/…` | 作者屏蔽（DataStore）+ 帖子屏蔽（独立 DataStore）；进程内快照供分页源同步判断；**屏蔽过滤放在展示层而非分页源**（取消屏蔽不丢已加载条目）【已确认】 |
| AI/翻译 | `data/AiProtocol.kt`、`AiCaller.kt`、`TranslationClient.kt`、`VisionClient.kt`、`ModelProfile.kt`、`TranslationSettingsRepository.kt` | 多供应商（OpenAI Chat/Responses、Anthropic、Gemini）；独立 OkHttp（read 180s）；翻译走「哨兵分段」解析（`<<<TITLE`/`<<<HTML`）而非 JSON；图片理解走 base64 data URL + 缓存；多模型档案（v1/v2 迁移）；深度思考/联网搜索按端点追加字段（默认全关不下发）【已确认】 |
| 语言 | `data/AppLanguage.kt`、`LanguageMirror.kt` | 10 种语言 + 跟随系统；DataStore 异步 → SharedPreferences 同步镜像供 attachBaseContext【已确认】 |
| 设置页数据 | `data/SettingsRepository.kt` | `archive_settings`：色模式/Monet 风格/种子色/baseUrl/session cookie/模糊缩略图/防截屏/年龄确认【已确认】 |
| OCR | `ocr/OcrEngine.kt`、`OcrService.kt`、assets（PaddleOCR ONNX） | 纯 Kotlin PP-OCRv4（DB 检测 + CRNN/CTC 识别），ONNX Runtime，可在 JVM 单测跑真实推理【已确认】 |
| 主题/组件 | `ui/theme/ArchiveTheme.kt`（MiuixTheme 单一主题 + Monet）、`ui/components/`、`ui/design/` | AppColorMode（6 态）、AppMonetStyle、种子色、模糊缩略图；Lucide 图标【已确认】 |
| 工具 | `util/`：HtmlParser、PostSort、SearchRelevance、BlurTransformation、ShareFiles、ExternalLinks、Fmt | 排序/相关度在 UI 层实现【已确认】 |

## 3. 关键调用链（修改前必读）

1. **Cloudflare 过盾**：App 启动 `CloudflareManager.init` 异步恢复加密凭据 → `ClearanceCoordinator.preheat` → 任何请求经 `ClearanceInterceptor`（注入 cookie+UA）→ 遇 403 → `ClearanceRetryInterceptor` 强制刷新（WebView 挑战，30s 超时）并重试一次。凭据与 UA 强绑定、TTL 25 分钟、剥 `session=` 段。
2. **下载**：UI → `DownloadCenter.enqueueDownload`（Mutex 串行化 + Room 去重）→ 并发许可（1–5，可配置，服务器硬限）→ 仅 Wi-Fi 等待 → `HttpDownloadManager.downloadToFile`（临时文件 + Range 续传 + 重试 1–3 次 ≥1s 退避）→ 进度经 Channel 节流（5% 落 Room + 通知栏）→ 拷到目标流（SAF/MediaStore）→ finalize → COMPLETED。暂停/取消=协程取消 + HTTP Call 取消，语义在 catch 分支区分 PAUSED/CANCELLED。
3. **收藏**：`FavoriteActions.toggle` → LocalStore（Room，列表状态）→ BookmarkManager（DataStore；有完整 Post 时写全文缓存）→ 已登录时云端写（离线进 pending_favorite_actions 队列，`FavoriteActionsSyncWorker` 联网补写）→ 收藏即订阅（设置开启）。卡片收藏时只有摘要，全文缓存在打开详情页时由 `FavoriteActions.ensureArchived` 补建；详情页网络加载失败时回退 `BookmarkManager.getBookmarkedPost` 离线阅读（PostDetailViewModel.archivedDetail）。
4. **AI 翻译/识图**：`TranslationClient.translatePost` / `VisionClient.summarize|ask` → `TranslationSettings.forPurpose` 取档案 → `AiProtocol.buildRequest`（按 apiType 拼体/鉴权头）→ `callAi`（独立 OkHttp）→ `parseReply` 解正文/推理/检索信号。
5. **内容更新**：`ContentUpdateWorker`（周期 ≥15min）→ 订阅表 → 拉新帖 → content_updates → 通知。
6. **更新检查**：`UpdateChecker` → GitHub releases/latest（STABLE）或全量（BETA）→ semver → 弹窗（Markdown→HTML）。

## 4. 修改风险区（改动前对照）

| 区域 | 硬约束 | 依据 |
|---|---|---|
| Room schema（core） | 改表/加表必须新增显式 Migration（当前 v8→v9）；不允许 destructive 回退 | PawchiveDatabase KDoc + 9 个 Migration |
| Room schema（app） | AppDatabase v1 + `fallbackToDestructiveMigration(false)`：改 schema 无迁移 → 打开崩溃 | AppModule.kt |
| 下载并发/重试 | 并发上限 5、重试 1–3（SettingsManager 钳制），服务器合规要求 + UA 可识别 | DownloadCenter/HttpDownloadManager 注释 |
| Cloudflare | 凭据 TTL 25min、UA 绑定、session= 剥离；改 UA 会导致 cf_clearance 失效 | CloudflareManager |
| 收藏缓存 | GET 收藏列表 5 分钟内存缓存；写后必须 `invalidateByPathPrefix("/api/v1/account/favorites")` | AuthRepository |
| 会话 | EncryptedSharedPreferences；登录宽限期 60s 内 401 不判失效；公开接口 401 映射为 Server(401) | SessionManager/AuthRepository |
| AI 契约 | 翻译解析依赖哨兵分段（`<<<TITLE`/`<<<HTML`）；默认提示词含占位说明（小模型会照抄）；advancedParams 全关时必须下发空对象 | TranslationClient/AiRequestParams |
| 备份 | schemaVersion=1；加字段向后兼容，改版本号则旧备份拒绝导入 | BackupManager |
| 语言 | 必须同步写 LanguageMirror（SharedPreferences），否则 attachBaseContext 取不到 | LanguageMirror |
| 屏蔽过滤 | 过滤在展示层（PostCard），分页源不过滤；BlockedContentFilter 需启动时预热快照 | PostPagingSource/ArchiveApp |
| 图片加载 | Coil 必须走 sharedOkHttpClient（img.pawchive.pw 会 403）；256MB 磁盘缓存 | ArchiveApp |
| 自定义 baseUrl | 设置里的 baseUrl 只影响 `:app` 的 API 拦截器（`NetworkModule.BaseUrlInterceptor`）与 `UrlProvider` 派生的媒体 URL；`:core` 全部硬编码 pawchive.pw（CloudflareManager 过盾、ClearanceInterceptor 域名判断、`ApiClient.publicApi` 的哈希反查与订阅同步、DownloadRuleEngine.buildFileUrl）。改成镜像域名后媒体/订阅会因无 UA+cookie 注入而 403——站点真迁移域名时需要同步改 core 的 ApiConstants/CloudflareManager，属于整体工程而非改设置 | NetworkModule/CloudflareManager/DownloadRuleEngine |
| 设置默认值 | 启动 Tab 默认 BOOKMARKS；首页排序默认 PUBLISHED_DESC；同步间隔默认 30min（≥15） | SettingsManager/HomeScreen |

## 5. 遗留/未知项

- `docs/api/pawchive_openapi.json`：站点 API 契约快照（与 PawchiveApi.kt 对照）
- 未逐行阅读：绝大多数 Screen 的完整实现、ui/components、ui/design、部分 util（HtmlParser/PostSort 等）、core/util 若干工具——涉及具体页面修改时需再读对应文件
