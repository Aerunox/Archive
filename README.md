# Archive

Archive 是 [Pawchive](https://pawchive.pw)（基于 Kemono 的内容归档站）的**第三方 Android 客户端**，
基于 **Miuix（HyperOS 风格）** 的单视觉主题 Compose 客户端，配色模式（含 Monet 动态取色）在设置中**即时切换、无需重启**。

> 本工程是 **Pawchive** 的功能拓展版本：
> 数据/业务层（`core` / `data` 两个 Gradle 模块）直接复用该项目的实现（Retrofit/Room/OkHttp/WorkManager，
> 与 UI 无关的纯 Kotlin），UI 层全部按本项目的 Miuix 设计体系重写。

> ⚠️ 站点含成人内容：首次启动有 18+ 确认，可选的缩略图模糊开关与 FLAG_SECURE。
> 本应用只是客户端：不内置、不转存任何内容，不上传任何用户数据，无统计 SDK。

## 功能

- **首页**：全站最新帖子（Paging 3 增量加载）、全站搜索（≥3 字符，防抖）
- **作者**：9 万+ 作者全量缓存到 Room，本地搜索 / service 筛选 / 按更新或收藏排序 / 一键同步；作者主页含横幅、公告、标签筛选、作者内搜索
- **帖子**：详情（HTML 正文渲染）、标签、上/下一篇、图片网格 → 全屏查看器（翻页 + 双指缩放）、视频/音频 Media3 播放、评论
- **账号登录与注销**：设置 → 账号 → 登录，在内置 WebView 中完成站点登录（`/account/login`），
  自动捕获 `session` Cookie 并仅保存在本机；「退出登录」一键清除本机会话与网页 Cookie；登录状态以
  `/account/favorites` 探测显示（已登录/失效）。也保留手动粘贴 Cookie 的高级入口
- **收藏与历史**：本地收藏 + 浏览历史（无需登录，Room 持久化）；登录后可看账号收藏的作者
- **下载**：WorkManager 附件/图片下载，断点续传（Range）+ 进度通知
- **一级界面原图直出**：首页/收藏等一级界面的帖子封面默认请求原始文件（登录后为全清晰度），
  未登录或原图 404 时自动回退缩略图，无感降级；全屏查看器同样原图优先

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
| **首页信息流** | 「同作者仅显示一条」与「隐藏已收藏创作者」两个可选项 |
| **其他** | Cloudflare 403 过盾（`CloudflareManager`，自动刷新 `cf_clearance` 并重试，图片加载同样走该通道）、全局崩溃捕获、自动清理缓存（容量阈值）、每日自动备份、应用内检查更新（GitHub Release） |

### 尚未移植的 Pawchive 功能（UI 层，随后续版本补齐）

- 多语言（中/EN/日）与启动页 Tab 选择、主题强调色 6 套、UI 缩放与全局字号滑杆
- 视频播放器高级特性：倍速、断点续播、画中画、Bilibili 风格控制器（当前用 Media3 默认控制器）
- 图片查看器的 GIF 角标、图片画廊手势细节、阅读进度记忆（仓库层已移植，UI 未接）
- 下载规则引擎 UI（按创作者/服务/文件类型自动入队，仓库层已移植）、创作者屏蔽名单 UI（仓库层已移植）

## 构建

**要求**：Android Studio（AGP 9.4）或命令行 Gradle 9.6+，**JDK 17+（建议 21）**，Android SDK Platform **37**（compileSdk；2026 年的 navigation/coil/media3 等 AAR 元数据要求 API 37）与 build-tools 36.0.0。

```bash
# Android Studio：直接 Open 项目目录，Sync 后运行
# 命令行：
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest        # 单元测试
```

产物：`app/build/outputs/apk/debug/Archive-<版本>-debug.apk`（发布包同理：`app/build/outputs/apk/release/Archive-<版本>-release.apk`）。

## 依赖版本

全部版本集中在 [`gradle/libs.versions.toml`](gradle/libs.versions.toml)（2026-10-06 与 Maven Central / Google Maven 核对）：

| 类别 | 组件 | 版本 |
|---|---|---|
| 构建 | AGP / Gradle / Kotlin / KSP | 9.4.1 / 9.8.0 / 2.3.21 / 2.3.12 |
| UI | Compose BOM（compose 1.12.1, material3 1.4.0）| 2026.09.00 |
| 主题 1 | **Miuix** `top.yukonga.miuix.kmp:*`（Apache-2.0，上游标注实验阶段） | **0.9.4（锁定）** |
| 网络 | Retrofit / OkHttp / kotlinx.serialization | 3.0.0 / 5.5.0 / 1.11.0 |
| 图片 | Coil 3（+gif/video/network-okhttp）| 3.6.3 |
| 本地 | Room / Paging / DataStore / WorkManager | 2.8.5 / 3.5.1 / 1.2.1 / 2.12.0 |
| 播放 | Media3 | 1.11.1 |
| 缩放 | zoomable（coil3 兼容）| 2.16.0 |

**minSdk = 30**（Android 11）：与 Pawchive 一致（其 core/data 模块要求 API 30+），Miuix 支持。未引入 `miuix-blur`（要求 minSdk 33 且非必需）。compileSdk = 37（依赖 AAR 元数据要求；AGP 9.4 最高支持 37），targetSdk = 36，
Java 21（core desugaring 开启）。AGP 9 使用内置 Kotlin（不应用 `org.jetbrains.kotlin.android`，KSP/Kotlin 编译器插件照常使用）。

## 如何更换 Base URL

设置 → 网络 → Base URL。默认 `https://pawchive.pw`。
修改后所有 API 请求（拦截器重写 host）与文件/缩略图/头像 URL（`img.<host>` 自动推导）即时生效，无需重启。
规则细节见 `docs/API_NOTES.md` 与 `data/remote/UrlProvider.kt`。

## 已知限制

0.**Miuix 实验性**：上游标注“API 可能变更”。已锁定 0.9.4 并对所用 API（ThemeController、
   Scaffold/SmallTopAppBar、preference 系列、OverlayDialog 等）按该版本源码逐一核对。
   升级版本前请对照 [上游 changelog](https://github.com/miuix-kotlin-multiplatform/miuix)。
2.**原图需要登录**：站点把游客可见的文件标记为 `preview_only`，原图 URL 需要会话 Cookie。
   一级界面与查看器均"原图优先、失败自动回退缩略图"，未登录时清晰度受限但不会报错空白；
   下载任务始终请求原图地址。
3.**无登录 API 端点**：站点未提供 API 登录（实测 404），采用内置 WebView 网页登录 +
   自动捕获 `session` Cookie 方案（cookie 仅存本机 DataStore，注销即清除）。
4.首页/搜索/作者帖子均为远程分页（站点无游标总数），断网时列表无法离线回放（作者库已本地缓存）。
5.桌面端/平板布局未做专门优化。

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
| 7 | 列表展开详情 | 作者条目点击原地展开详情面板（横幅、ID、收藏/索引 + 查看主页），列表下移、无跳转；箭头旋转变形 | 作者页列表 |
| 8 | 列表切换网格 | 单一 `LazyVerticalGrid`（列数 1↔2）+ `Modifier.animateItem()`：同 key 元素位置重排动画，**分页数据不重载** | 首页帖子流 |
| 9 | 顶栏随滚动收起 | 文档化 `TopAppBar + MiuixScrollBehavior` 大标题折叠；底部导航栏常驻不隐藏（主导航随时可用） | 各内容页 |
| 10 | 菜单铺满全屏 | `RevealMenuOverlay`：从触发按钮坐标圆形扩散覆盖全屏（覆盖底栏），菜单项随扩散进度错峰浮现，原路收回；触发按钮同步形变为 ✕ | 首页顶栏菜单 |

## 架构与关键设计决策

```
core/                <- 抄自 Pawchive（namespace com.pawchive.core）
                     Retrofit API / Cloudflare 过盾 / 加密会话 / Room（下载·离线归档 FTS·订阅·规则）
data/                <- 抄自 Pawchive（namespace com.pawchive.data）
                     AuthRepository / BookmarkManager / DownloadCenter 引擎 / 订阅 / 搜索历史 / 工作线程
app/src/main/java/com/archive/app/
├── data/            远端(Retrofit+kotlinx.serialization) / 本地(Room+DataStore) / 仓库 / 分页
├── di/              Hilt 模块
├── domain/          领域模型
├── download/        WorkManager 下载（断点续传 + 通知）
├── ui/
│   ├── theme/       ThemeEngine：AppThemeMode/ColorMode/GlassParams + PurrColors 令牌
│   ├── design/      设计系统抽象层 DesignComponents
│   │   └── miuix/   MiuixDesign（MiuixTheme 内渲染）
│   ├── navigation/  Navigation Compose（不依赖 miuix-nav）
│   └── screens/     业务页面（只依赖 App* 抽象组件）
└── util/            HTML 解析、时间格式化、模糊变换
```

- **单一 Compose 代码库**：业务页面只调用 `AppScaffold / AppTopBar / AppBottomBar / AppCard / …`
  抽象组件（`ui/design/DesignComponents.kt` 接口），由 `ArchiveTheme` 通过 CompositionLocal 注入
  唯一实现 `MiuixDesign`；颜色统一收敛到 `PurrColors` 令牌。配色切换 = DataStore 状态变化 → 重组，
  Activity 不重建，NavHostController 提升到主题外层，**返回栈在切换后保留**。
- **实现与 Miuix 官方文档逐条对齐**（compose-miuix-ui.github.io/miuix）：
  主题用文档的 `ThemeController + MiuixTheme(controller=…)` 写法（System/Light/Dark/MonetSystem/
  MonetLight/MonetDark 六模式、`keyColor` 种子色、`paletteStyle`）；Scaffold 是跨平台弹窗容器，
  OverlayDialog/OverlayDropdownPreference 等必须被它包裹 —— 所有 AppDialog 均在 AppScaffold 内组合；
  顶栏采用文档的折叠大标题模式 `TopAppBar + MiuixScrollBehavior`，并把 `nestedScroll` 接到内容、
  列表加 `Modifier.overScrollVertical()`；搜索框用文档的 `SearchBar + InputField`；设置页用
  SwitchPreference/SliderPreference/OverlayDropdownPreference 语义的抽象组件。
- **图标体系**：应用内全部图标统一使用 [Lucide](https://lucide.dev)（ISC 许可）。图标不是引入第三方 Compose 库，
  而是由 `tools/gen_lucide_icons.py` 拉取 Lucide 官方 SVG 源码、把 24×24 描边几何逐点转换为 Compose `ImageVector`
  （`ui/design/lucide/LucideIcons.kt`，生成文件，注释标明不要手改）。这样与 Compose 版本零耦合、可随时重新生成；
  `LucideIconsTest` 校验每个图标都有真实路径数据与 24×24 视口。四个形态动画图标（汉堡↔关闭、加号、播放↔暂停、打勾）
  也改用 Lucide 的精确坐标绘制，并随尺寸缩放。注意：Miuix 组件库内部自绘的图标（如文本框自带的清除按钮）仍由该库提供。
- **版本对齐说明**：miuix 0.9.4 依赖 JB Compose foundation 1.12.0，与 androidx compose-bom
  2026.09.00（compose 1.12.1）同线，不会产生同包名类冲突。
- **验证状态**：Miuix 顶栏折叠的嵌套滚动接线、回弹 overScroll 均按文档实现；真机体验（折叠灵敏度、
  回弹手感）可在运行时直接感受，无特殊降级逻辑。

## API 数据源

见 [`docs/API_NOTES.md`](docs/API_NOTES.md)：全部端点经 OpenAPI spec（归档于
`docs/api/pawchive_openapi.json`）与真机请求双重验证，含假设证实/推翻清单。
