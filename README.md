# Archive

Archive 是 [Pawchive](https://pawchive.pw)（基于 Kemono 的内容归档站）的**第三方 Android 客户端**，
基于 **Miuix（HyperOS 风格）** 的单视觉主题 Compose 客户端，配色模式（含 Monet 动态取色）在设置中**即时切换、无需重启**。

> 本工程是 **Pawchive** 的功能拓展版本：
> 数据/业务层（`core` / `data` 两个 Gradle 模块）直接复用该项目的实现（Retrofit/Room/OkHttp/WorkManager，
> 与 UI 无关的纯 Kotlin），UI 层全部按本项目的 Miuix 设计体系重写。

> ⚠️ 站点含成人内容：首次启动有 18+ 确认，可选的缩略图模糊开关与 FLAG_SECURE。
> 本应用只是客户端：不内置、不转存任何内容，不上传任何用户数据，无统计 SDK。

## 功能

底部四 Tab：**首页 / 发现 / 收藏 / 我**。

- **首页**：全站最新帖子（Paging 3 增量加载）、全站搜索（≥3 字符，防抖）、列表/网格双布局切换、
  「同作者仅显示一条」「隐藏已收藏创作者」两个可选项、原位展开搜索框、加号 FAB 展开面板
- **发现**：9 万+ 作者全量缓存到 Room（单次约 15MB JSON），本地搜索 / service 筛选 / 按更新或收藏排序 /
  一键同步；行内点开原地展开详情面板，箭头旋转变形；下拉刷新只用顶栏转圈动画表达进度，不叠文字提示
- **帖子**：详情（HTML 正文渲染）、标签、上/下一篇、图片网格 → 全屏查看器（翻页 + 双指缩放）、
  视频/音频 Media3 播放、评论、修订历史、附件批量下载（按钮展开为步进器）、AI 翻译
- **账号登录与注销**：设置 → 账号 → 登录，在内置 WebView 中完成站点登录（`/account/login`），
  自动捕获 `session` Cookie 并仅保存在本机；「退出登录」一键清除本机会话与网页 Cookie；登录状态以
  `/account/favorites` 探测显示（已登录/失效）。也保留手动粘贴 Cookie 的高级入口
- **收藏与历史**：本地收藏 + 浏览历史（无需登录，Room 持久化）；登录后可看账号收藏的作者
- **下载**：附件/图片下载，断点续传（Range）+ 进度通知；下载中心统一管理任务
- **一级界面原图直出**：首页/收藏等一级界面的帖子封面默认请求原始文件（登录后为全清晰度），
  未登录或原图 404 时自动回退缩略图，无感降级；全屏查看器同样原图优先
- **AI 功能**：多供应商（OpenAI Chat/Responses、Anthropic、Gemini）的帖子翻译与图片理解/问答，
  支持多模型档案、深度思考与联网搜索开关（默认全关）
- **OCR**：端上 PP-OCRv4（PaddleOCR）文字识别，ONNX Runtime 推理，模型与字典随包内置

### 从 Pawchive 移植的功能

| 模块 | 功能 |
|---|---|
| **账号** | 账号密码登录 / 注册（`POST /account/login`，含 Cloudflare 过盾）、多账号保存与一键切换（账号间收藏/历史/下载数据隔离）、退出登录；会话写入 EncryptedSharedPreferences 加密存储 |
| **云端收藏** | 登录后收藏同步到账号收藏（增删即时失效缓存），离线操作进待补写队列，联网自动补写 |
| **离线归档** | 收藏即建立 Room FTS4 全文索引（中文 bigram 分词），断网可全文检索与阅读（`离线阅读` 页） |
| **搜索** | 关键词（帖子/创作者/离线归档三 Tab）、**文件哈希反查**（MD5 / SHA-256，含 Discord 结果）、搜索历史（本机持久化、可配条数、单条删除） |
| **订阅与通知** | 订阅创作者、按可配间隔（≥15 分钟）后台增量同步新帖、系统通知 + **应用内通知中心**（首页铃铛、未读角标、单条/全部已读、订阅管理退订） |
| **下载中心** | 自研 OkHttp 流式下载引擎：HTTP Range 断点续传（重启后继续）、每任务独立通知（进度/速度/剩余时间，通知内暂停·继续·取消）、dataSync 前台保活、并发上限、仅 Wi-Fi、重试次数、SAF 自定义目录、文件命名格式、`preview_only` 自动跳过 |
| **创作者页** | 帖子 / 公告 / 标签 / **粉丝卡**（三级图源回退）/ **相似作者**（网页 recommended 页解析）/ **关联账号** 六个 Tab |
| **帖子详情** | **修订历史**、附件批量下载（步进器）、云端收藏联动、离线归档联动 |
| **其他** | Cloudflare 403 过盾（`CloudflareManager`，自动刷新 `cf_clearance` 并重试，图片加载同样走该通道）、全局崩溃捕获、自动清理缓存（容量阈值 + 四档频率）、每日自动备份、应用内检查更新（GitHub Release） |

### 尚未移植的 Pawchive 功能（UI 层，随后续版本补齐）

- 启动页 Tab 选择、主题强调色 6 套、UI 缩放与全局字号滑杆
- 视频播放器高级特性：倍速、断点续播、画中画、Bilibili 风格控制器（当前用 Media3 默认控制器）
- 图片查看器的 GIF 角标、图片画廊手势细节、阅读进度记忆（仓库层已移植，UI 未接）
- 下载规则引擎 UI（按创作者/服务/文件类型自动入队，仓库层已移植）、创作者屏蔽名单 UI（仓库层已移植）

## 设置

设置首页按分类入口排列（每行「图标 + 标题 + 摘要 + 右箭头」），顶层有搜索框；点分类**同屏切换**到该类设置项，
不新增导航路由，返回手势与预测式返回照常工作。八个分类：

| 分类 | 主要设置项 |
|---|---|
| 外观 | 配色模式（6 态含 Monet）、动态取色风格、种子色、减少动画 |
| 隐私与安全 | 模糊缩略图、禁止截屏（FLAG_SECURE） |
| 网络 | Base URL |
| 浏览与搜索 | 同作者仅显示一条、隐藏已收藏创作者、搜索历史保留条数（5–50） |
| 下载 | 下载目录、最大并发数（1–5）、失败重试次数（1–3）、仅 Wi-Fi 下载、文件命名格式 |
| 存储与清理 | 自动清理临时缓存、自动清理频率（每次启动/每日/每周/每月）、缓存容量上限 |
| AI 功能 | 模型档案、接口地址与密钥、翻译与识图行为 |
| 内容屏蔽 | 屏蔽管理（已屏蔽作者 / 已屏蔽帖子） |

> 设置项自身的标题与摘要目前是硬编码中文；分类行标题走字符串资源，12 种语言已覆盖。

## 构建

**要求**：Android Studio（AGP 9.4）或命令行 Gradle 9.6+，**JDK 17+（建议 21）**，
Android SDK Platform **37**（compileSdk；navigation/coil/media3 等 AAR 元数据要求 API 37）与 build-tools 36.0.0。

```bash
# Android Studio：直接 Open 项目目录，Sync 后运行
# 命令行：
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease        # 产出多架构 release 包
./gradlew :app:testDebugUnitTest      # 单元测试
```

### 多架构打包

release 变体通过 `splits { abi { … } }` 永久拆出**四个单架构 APK + 一个 universal 全架构包**，
覆盖 Android 支持的全部主流 ABI（`armeabi-v7a` / `arm64-v8a` / `x86` / `x86_64`）。
OCR 依赖的 ONNX Runtime 原生库四 ABI 齐全，单架构包各自只携带自己那套 `.so`。

产物（`app/build/outputs/apk/release/`）：

| 文件 | 约体积 | 说明 |
|---|---|---|
| `Archive-1.0.1-release-arm64-v8a.apk` | 37.9 MB | 主流真机（绝大多数 Android 手机） |
| `Archive-1.0.1-release-armeabi-v7a.apk` | 33.2 MB | 老 32 位 ARM 设备 |
| `Archive-1.0.1-release-x86.apk` | 41.1 MB | x86 模拟器 |
| `Archive-1.0.1-release-x86_64.apk` | 41.4 MB | x86_64 模拟器 |
| `Archive-1.0.1-release-universal.apk` | 92.3 MB | 四套 `.so` 全含，兼容性最好、体积最大 |

debug 包同样按 ABI 拆分，命名规则为 `Archive-<版本>-debug-<abi|universal>.apk`。

## 依赖版本

全部版本集中在 [`gradle/libs.versions.toml`](gradle/libs.versions.toml)：

| 类别 | 组件 | 版本 |
|---|---|---|
| 构建 | AGP / Gradle / Kotlin / KSP | 9.4.1 / 9.8.0 / 2.3.21 / 2.3.12 |
| UI | Compose BOM（compose 1.12.1, material3 1.4.0）| 2026.09.00 |
| 主题 | **Miuix** `top.yukonga.miuix.kmp:*`（Apache-2.0，上游标注实验阶段） | **0.9.4（锁定）** |
| 网络 | Retrofit / OkHttp / kotlinx.serialization | 3.0.0 / 5.5.0 / 1.11.0 |
| 图片 | Coil 3（+gif/video/network-okhttp）| 3.6.3 |
| 本地 | Room / Paging / DataStore / WorkManager | 2.8.5 / 3.5.1 / 1.2.1 / 2.12.0 |
| 播放 | Media3 | 1.11.1 |
| 缩放 | zoomable（coil3 兼容）| 2.16.0 |
| 推理 | ONNX Runtime（Android / JVM 单测）| 1.22.0 |

**minSdk = 30**（Android 11），**targetSdk = 36**，**compileSdk = 37**，Java 21（core desugaring 开启）。
AGP 9 使用内置 Kotlin（不应用 `org.jetbrains.kotlin.android`，KSP/Kotlin 编译器插件照常使用）。
未引入 `miuix-blur`（要求 minSdk 33 且非必需）。

## 如何更换 Base URL

设置 → 网络 → Base URL。默认 `https://pawchive.pw`。
修改后所有 API 请求（拦截器重写 host）与文件/缩略图/头像 URL（`img.<host>` 自动推导）即时生效，无需重启。
规则细节见 `docs/API_NOTES.md` 与 `data/remote/UrlProvider.kt`。

## 本地化

12 种语言资源：简体中文（默认）、繁體中文、English、Deutsch、Español、Français、Bahasa Indonesia、
日本語、한국어、Русский、Türkçe、العربية（整页 RTL）。切换语言后 `Activity.attachBaseContext`
同步读取语言镜像，杀进程重启仍保持。

## 已知限制

1. **Miuix 实验性**：上游标注"API 可能变更"。已锁定 0.9.4 并对所用 API（ThemeController、
   Scaffold/SmallTopAppBar、preference 系列、OverlayDialog 等）按该版本源码逐一核对。
   升级版本前请对照上游 changelog。
2. **原图需要登录**：站点把游客可见的文件标记为 `preview_only`，原图 URL 需要会话 Cookie。
   一级界面与查看器均"原图优先、失败自动回退缩略图"，未登录时清晰度受限但不会报错空白；
   下载任务始终请求原图地址。
3. **无登录 API 端点**：站点未提供 API 登录（实测 404），采用内置 WebView 网页登录 +
   自动捕获 `session` Cookie 方案（cookie 仅存本机 DataStore，注销即清除）。
4. **刷新期间列表不可滚动**：Miuix 的 `PullToRefresh` 在刷新动画进行时（内部 `RefreshState.Refreshing`/
   `RefreshComplete`）会在 `onPreScroll`/`onPostScroll` 全量消费嵌套滚动，属库的既定设计，
   0.9.4 与上游 main 分支一致、无开关可关。刷新结束后立即恢复。
5. 首页/搜索/作者帖子均为远程分页（站点无游标总数），断网时列表无法离线回放（作者库已本地缓存）。
6. 桌面端/平板布局未做专门优化。

## 动效与微交互（10 项规格）

全部为共享组件（`ui/components/`）：

| # | 交互 | 实现 | 位置 |
|---|---|---|---|
| 1 | 搜索框原位展开 | `ExpandableSearchField`：搜索按钮宽度动画展开为输入框（弹簧），`FocusRequester` 自动聚焦 + 拉起键盘，**不跳转页面**；结果就地替换下方列表 | 首页顶栏 |
| 2 | 加号展开面板 | `ExpandableFab`：悬浮加号 FAB 原位展开操作面板（同步作者库/历史/下载），加号旋变为 ✕，返回键或点选收回 | 首页右下角 |
| 3 | 提交状态反馈 | `MorphIconButton` / `MorphStateButton` / 状态胶囊：同位置在 空闲→加载→完成/失败 间切换（加载圈、打勾动画为逐段绘制） | 作者页同步按钮、首页同步反馈 |
| 4 | 图标变形 | `MenuCloseIcon`（三横线→✕ 线条形变）、`PlayPauseIcon`（三角→双竖条平滑变形）、`MorphPlusIcon`（加号旋转 45°）；均为 Canvas 逐帧绘制 | 全屏菜单触发、音频播放、FAB |
| 5 | 标签指示条 | `AppTabRow`：底部指示条以欠阻尼弹簧滑到目标标签（跟随感） | 收藏页、作者详情页（帖子/公告/标签） |
| 6 | 按钮变步进器 | `ExpandableStepperButton`：按钮点击展开为 −/数量/+ 步进器，**减到 0 自动还原为按钮**；确认后批量下载 | 帖子详情「批量下载图片」 |
| 7 | 列表展开详情 | 作者条目点击原地展开详情面板（横幅、ID、收藏/索引 + 查看主页），列表下移、无跳转；箭头旋转变形 | 发现页列表 |
| 8 | 列表切换网格 | 单一 `LazyVerticalGrid`（列数 1↔2）+ `Modifier.animateItem()`：同 key 元素位置重排动画，**分页数据不重载** | 首页帖子流 |
| 9 | 下拉刷新 | `AppPullToRefresh`：顶栏下拉转圈动画承载刷新进度，**不使用文字加载提示**；刷新完成/失败等结果反馈才以文字显示 | 首页、发现、收藏 |
| 10 | 菜单铺满全屏 | `RevealMenuOverlay`：从触发按钮坐标圆形扩散覆盖全屏（覆盖底栏），菜单项随扩散进度错峰浮现，原路收回；触发按钮同步形变为 ✕ | 首页顶栏菜单 |

## 架构与关键设计决策

```
core/                <- 抄自 Pawchive（namespace com.pawchive.core）
                     Retrofit API / Cloudflare 过盾 / 加密会话 / Room（下载·离线归档 FTS·订阅·规则）
data/                <- 抄自 Pawchive（namespace com.pawchive.data）
                     AuthRepository / BookmarkManager / DownloadCenter 引擎 / 订阅 / 搜索历史 / 工作线程
app/src/main/java/com/archive/app/
├── data/            远端(Retrofit+kotlinx.serialization) / 本地(Room+DataStore) / 仓库 / 分页 / AI
├── di/              Hilt 模块
├── domain/          领域模型
├── ocr/             PP-OCRv4 管线（检测 + 识别，ONNX Runtime）
├── ui/
│   ├── theme/       ThemeEngine：AppThemeMode/ColorMode/MonetParams + PurrColors 令牌
│   ├── design/      设计系统抽象层 DesignComponents
│   │   ├── miuix/   MiuixDesign（MiuixTheme 内渲染）
│   │   └── lucide/  LucideIcons.kt（生成文件）
│   ├── components/  共享交互组件（搜索/面板/变形图标/翻译/播放器…）
│   ├── navigation/  Navigation Compose（16 条路由，不依赖 miuix-nav）
│   └── screens/     业务页面（20 个，只依赖 App* 抽象组件）
└── util/            HTML 解析、时间格式化、模糊变换、排序与相关度
```

- **单一 Compose 代码库**：业务页面只调用 `AppScaffold / AppTopBar / AppBottomBar / AppCard / …`
  抽象组件（`ui/design/DesignComponents.kt` 接口），由 `ArchiveTheme` 通过 CompositionLocal 注入
  唯一实现 `MiuixDesign`；颜色统一收敛到 `PurrColors` 令牌。配色切换 = DataStore 状态变化 → 重组，
  Activity 不重建，NavHostController 提升到主题外层，**返回栈在切换后保留**。
- **顶栏策略**：带返回按钮的页面用单行紧凑顶栏（返回箭头与标题同一行）；可折叠大标题只用于无返回
  按钮的根页面。列表页不接折叠栏，避免滚动时顶栏高度变化引起反复重测量。
- **实现与 Miuix 官方文档逐条对齐**：主题用 `ThemeController + MiuixTheme(controller=…)` 写法
  （System/Light/Dark/MonetSystem/MonetLight/MonetDark 六模式、`keyColor` 种子色、`paletteStyle`）；
  Scaffold 是跨平台弹窗容器，OverlayDialog/OverlayDropdownPreference 等必须被它包裹 ——
  所有 AppDialog 均在 AppScaffold 内组合；搜索框用 `SearchBar + InputField`；设置页用
  SwitchPreference/SliderPreference/OverlayDropdownPreference 语义的抽象组件。
- **下拉刷新的挂载方式**：`AppPullToRefresh` 内部以 `MiuixScrollBehavior(canScroll = { false })`
  作为下拉检测挂载点——库的下拉检测依赖非空的 `topAppBarScrollBehavior`，但默认 behavior 的
  nested scroll connection 会吃掉纵向滚动；`canScroll=false` 的 behavior 不折叠顶栏、不消费滚动，
  只留下拉检测所需的那条链路。
- **图标体系**：应用内全部图标统一使用 [Lucide](https://lucide.dev)（ISC 许可）。图标不是引入第三方 Compose 库，
  而是由 `tools/gen_lucide_icons.py` 拉取 Lucide 官方 SVG 源码、把 24×24 描边几何逐点转换为 Compose `ImageVector`
  （`ui/design/lucide/LucideIcons.kt`，生成文件，注释标明不要手改）。这样与 Compose 版本零耦合、可随时重新生成；
  `LucideIconsTest` 校验每个图标都有真实路径数据与 24×24 视口。四个形态动画图标（汉堡↔关闭、加号、播放↔暂停、打勾）
  也改用 Lucide 的精确坐标绘制，并随尺寸缩放。注意：Miuix 组件库内部自绘的图标（如文本框自带的清除按钮）仍由该库提供。
- **列表安全写法**：LazyColumn 的 content lambda 会延迟执行并持有上一帧捕获的状态，因此列表内容里对可能被
  置空/移除的状态一律用 `firstOrNull` + `?.` + `orEmpty`，不用 `first{}` / `!!` / `[index]`。
- **版本对齐说明**：miuix 0.9.4 依赖 JB Compose foundation 1.12.0，与 androidx compose-bom
  2026.09.00（compose 1.12.1）同线，不会产生同包名类冲突。

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

19 个单元测试覆盖：AI 请求体与协议、翻译哨兵分段解析、HTML 解析、分页源、搜索相关度、URL 推导与链接映射、
主题规格、更新说明解析、OCR 真实推理（同一 ONNX 模型在 JVM 上跑）、图标数据完整性等。

## 目录速查

| 路径 | 内容 |
|---|---|
| `docs/ARCHITECTURE.md` | 模块职责、关键调用链、修改风险区（改动前必读） |
| `docs/API_NOTES.md` | 站点端点与契约验证记录 |
| `docs/I18N_BACKLOG.md` | 本地化待办 |
| `tools/gen_lucide_icons.py` | Lucide 图标生成脚本（加图标唯一正确入口） |
| `reports/` | 代码审计与修复记录 |

## API 数据源

见 [`docs/API_NOTES.md`](docs/API_NOTES.md)：全部端点经 OpenAPI spec（归档于
`docs/api/pawchive_openapi.json`）与真机请求双重验证，含假设证实/推翻清单。
