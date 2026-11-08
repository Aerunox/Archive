# Archive（Pawchive 第三方客户端）

[![release](https://img.shields.io/github/v/release/Aerunox/Archive)](https://github.com/Aerunox/Archive/releases/latest)
[![open issues](https://img.shields.io/github/issues/Aerunox/Archive?color=brightgreen)](https://github.com/Aerunox/Archive/issues?q=is%3Aopen+is%3Aissue)

* 本应用为基于 Kemono 的内容归档站 [Pawchive](https://pawchive.pw) 的安卓第三方客户端，
  Miuix（HyperOS 风格）单视觉主题的 Jetpack Compose 应用
* 项目已开源且仅供交流和学习使用，不得用于任何商业用途
* 本应用是纯客户端：不内置、不转存任何内容，不上传任何用户数据，无统计 SDK；
  所有内容版权归属原站与各著作者，推荐支持原站 Pawchive
* 站点含成人内容：首次启动有 18+ 确认，可选缩略图模糊与禁止截屏（FLAG_SECURE）

## 1. 功能

* 底部四 Tab：**首页 / 发现 / 收藏 / 我**
* 首页：全站最新帖子（Paging 3 增量加载）、全站搜索（防抖）、列表/网格双布局切换、
  「同作者仅显示一条」「隐藏已收藏创作者」选项、搜索框原位展开、加号 FAB 展开面板
* 发现：9 万+ 作者全量缓存到本地（Room），本地搜索 / service 筛选 / 排序 / 一键同步，
  行内点开原地展开详情面板
* 帖子：详情（HTML 正文渲染）、标签、上/下一篇、图片网格 → 全屏查看器（翻页 + 双指缩放）、
  视频/音频 Media3 播放、评论、修订历史、附件批量下载（按钮展开为步进器）、X 风格 AI 翻译横幅
* 账号：内置 WebView 完成站点登录、自动捕获 `session` Cookie（仅存本机）、
  多账号保存与一键切换（数据隔离）、退出登录一键清除
* 收藏与历史：本地收藏 + 浏览历史（无需登录）；登录后看账号收藏并云端同步，
  离线操作进待补写队列自动补写
* 离线归档：收藏即建 Room FTS4 全文索引（中文 bigram 分词），断网可全文检索与阅读
* 下载中心：自研 OkHttp 流式引擎，HTTP Range 断点续传，每任务独立通知（进度/速度，暂停·继续·取消）、
  并发上限、仅 Wi-Fi、SAF 自定义目录、文件命名格式、`preview_only` 自动跳过
* 订阅与通知：订阅创作者、可配间隔后台增量同步新帖、系统通知 + 应用内通知中心（未读角标、已读管理）
* 搜索：关键词（帖子/创作者/离线归档三 Tab）、文件哈希反查（MD5 / SHA-256，含 Discord 结果）、搜索历史
* 创作者页：帖子 / 公告 / 标签 / 粉丝卡 / 相似作者 / 关联账号 六个 Tab
* AI 功能：多供应商（OpenAI Chat/Responses、Anthropic、Gemini）的帖子翻译与图片理解/问答，
  多模型档案、深度思考与联网搜索开关（默认全关）
* OCR：端上 PP-OCRv4（PaddleOCR）文字识别，ONNX Runtime 推理，模型与字典随包内置
* 其他：Cloudflare 403 过盾、全局崩溃捕获、自动清理缓存（容量阈值 + 四档频率）、每日自动备份、
  应用内检查更新（GitHub Release）、内容屏蔽（已屏蔽作者 / 帖子）

## 2. 特性

* 美观整洁的界面：Miuix（HyperOS 风格）单视觉主题，全部页面走同一套抽象组件
* 配色模式 6 态（含 Monet 动态取色）：设置中即时切换、无需重启，返回栈在切换后保留
* 一级界面原图直出：封面默认请求原始文件（登录后全清晰度），404 自动无感回退缩略图
* 流畅的动效：搜索框原位展开、FAB 面板、按钮变步进器、列表↔网格同元素重排动画、
  圆形扩散全屏菜单、状态胶囊变形等 10 项规格化微交互
* 支持多国语言：12 种语言资源（简体中文默认、繁體、英、德、西、法、印尼、日、韩、俄、土、阿拉伯整页 RTL）
* 断点续传与离线阅读：下载重启后继续；收藏的帖子断网仍可全文检索阅读

## 3. 下载

[Releases](https://github.com/Aerunox/Archive/releases/latest)

| 文件 | 适用 |
|---|---|
| `Archive-<版本>-release-arm64-v8a.apk` | 主流真机（绝大多数 Android 手机） |
| `Archive-<版本>-release-armeabi-v7a.apk` | 老 32 位 ARM 设备 |
| `Archive-<版本>-release-x86_64.apk` | x86_64 模拟器 |
| `Archive-<版本>-release-x86.apk` | x86 模拟器 |
| `Archive-<版本>-release-universal.apk` | 四套 ABI 全含，兼容性最好、体积最大 |

要求 **Android 11**（minSdk 30）及以上。release 包为调试签名，可直接安装、同签名覆盖升级。

## 4. 构建

**要求**：Android Studio（AGP 9.4）或命令行 Gradle 9.6+，**JDK 17+（建议 21）**，
Android SDK Platform **37**（compileSdk）与 build-tools 36.0.0。

```bash
# Android Studio：直接 Open 项目目录，Sync 后运行
# 命令行：
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease        # 产出多架构 release 包
./gradlew :app:testDebugUnitTest      # 单元测试
```

release 变体按 ABI 永久拆分（`splits { abi { … } }`）：四个单架构 APK + 一个 universal 包，
产物命名 `Archive-<版本>-<buildType>-<abi|universal>.apk`，输出在 `app/build/outputs/apk/release/`。

## 5. 常见问题 FAQ

**Q：图片只有模糊缩略图？**
原图需要登录。站点把游客可见的文件标记为 `preview_only`，原图 URL 需要会话 Cookie；
应用「原图优先、失败自动回退缩略图」，未登录清晰度受限但不会报错空白，下载任务始终请求原图地址。

**Q：帖子评论区显示「暂无评论」？**
站点对没有评论的帖子返回 `404`（实测，OpenAPI 未注明），应用按 Kemono 语义处理为无评论。
若你在站点网页上看到某帖有评论而应用没有，欢迎提 issue 并附帖子链接。

**Q：下拉刷新时列表不能滚动？**
Miuix `PullToRefresh` 的既定设计：刷新动画期间消费嵌套滚动，刷新结束立即恢复。

**Q：怎么换镜像站点？**
设置 → 网络 → Base URL（默认 `https://pawchive.pw`）。所有 API 请求与文件/缩略图/头像 URL
即时切换，无需重启。

**Q：AI 翻译在哪配？**
我 → 设置 → 帖子翻译：接口地址 / Key / 模型 / 提示词 / 采样参数，支持多档案与「用途指派」
（翻译、识图分别指定档案）。详情页翻译横幅右侧齿轮可直达。

**Q：断网还能用吗？**
收藏的帖子建立了全文索引，离线阅读页可检索与阅读；首页/搜索/作者帖子是远程分页
（站点无游标总数），断网时列表无法离线回放（作者库已本地缓存）。

**Q：升级 Miuix 版本要注意什么？**
上游标注实验性（API 可能变更），本仓库锁定 0.9.4 并对所用 API 逐一核对过，升级前请对照上游 changelog。

## 6. 依赖版本

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

## 7. 架构

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
  Activity 不重建，**返回栈在切换后保留**。
- **顶栏策略**：带返回按钮的页面用单行紧凑顶栏；可折叠大标题只用于无返回按钮的根页面，
  列表页不接折叠栏，避免滚动时顶栏高度变化引起反复重测量。
- **图标体系**：应用内图标统一使用 [Lucide](https://lucide.dev)（ISC 许可）。由 `tools/gen_lucide_icons.py`
  拉取官方 SVG、把 24×24 描边几何逐点转换为 Compose `ImageVector`（生成文件，注释标明不要手改），
  与 Compose 版本零耦合、可随时重新生成；`LucideIconsTest` 校验每个图标的路径数据。
- **实现与 Miuix 官方文档逐条对齐**：主题用 `ThemeController + MiuixTheme(controller=…)` 写法；
  Scaffold 是跨平台弹窗容器，OverlayDialog 等必须被它包裹；下拉刷新以
  `MiuixScrollBehavior(canScroll = { false })` 作为检测挂载点，只留下拉检测所需链路、不折叠顶栏。

## 8. 测试

```bash
./gradlew :app:testDebugUnitTest
```

19 个单元测试覆盖：AI 请求体与协议、翻译哨兵分段解析、HTML 解析、分页源、搜索相关度、URL 推导与链接映射、
主题规格、更新说明解析、OCR 真实推理（同一 ONNX 模型在 JVM 上跑）、图标数据完整性等。

## 9. 相关文档

| 路径 | 内容 |
|---|---|
| `docs/ARCHITECTURE.md` | 模块职责、关键调用链、修改风险区（改动前必读） |
| `docs/API_NOTES.md` | 站点端点与契约验证记录 |
| `docs/I18N_BACKLOG.md` | 本地化待办 |
| `tools/gen_lucide_icons.py` | Lucide 图标生成脚本（加图标唯一正确入口） |

API 数据源：全部端点经 OpenAPI spec（归档于 `docs/api/pawchive_openapi.json`）与真机请求双重验证，
含假设证实/推翻清单，详见 [`docs/API_NOTES.md`](docs/API_NOTES.md)。
