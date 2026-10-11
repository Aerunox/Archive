# Archive — 阶段 0 API 调研结论

> 数据来源：`https://pawchive.pw/api/swagger_schema`（页面内嵌 `swagger_spec` JS 对象，
> OpenAPI 3.0.1，Pawchive API 1.0.0）。完整 spec 已归档于
> [`docs/api/pawchive_openapi.json`](api/pawchive_openapi.json)。
> 所有“待验证假设”均于 2026-10-06 通过真机 HTTP 请求逐一核实，未凭记忆编造任何端点。

## 1. 基本信息

| 项 | 结论 |
|---|---|
| Base URL | `https://pawchive.pw/api/v1`（做成设置项，默认值即此） |
| 鉴权 | **Cookie 会话**：OpenAPI securitySchemes = `cookieAuth`，cookie 名 `session` |
| 登录端点 | **不存在**（`POST /account/login` 返回 Flask 404）。App 的方案：用户在网页登录后把 `session` cookie 值粘贴进设置，请求拦截器自动附加 |
| 错误格式 | 401 → 空对象 `{}`；404 → `{"error": "Creator not found."}`（具体端点返回 enum 错误文案） |
| 分页 | `o`（offset）步进 **50**；无总数、无 count 字段 → “短页即结尾”策略 |
| 搜索 `q` | 服务端声明 minLength 3，实测 **<3 字符的 q 被忽略**（返回全量页），客户端需自行过滤；大小写不敏感 |
| 内容编码 | 正文为 HTML 片段（`<p>` `<b>` `<img>` 等） |

## 2. 端点清单（全部经 spec + 实测双重确认）

| 方法 | 路径 | 参数 | 用途 | 实测 |
|---|---|---|---|---|
| GET | `/creators` | — | 全部作者（**约 9.6 万条 / 15MB 单次返回**）→ 必须 Room 缓存 | 200 |
| GET | `/posts` | `q?`(≥3), `o` | 全站最近帖子 / 全站搜索 | 200，50 条/页 |
| GET | `/{service}/user/{creatorId}` | `q?`, `tag?`(可多值), `o` | 作者帖子分页 | 200，50 条/页；o=0 与 o=50 无重叠；`tag=NSFW` 生效 |
| GET | `/{service}/user/{creatorId}/post/{postId}` | — | 帖子详情（字段超集，含 tags/prev/next/poll） | 200 |
| GET | `/{service}/user/{creatorId}/profile` | — | 作者资料 | 200（indexed/updated 为 ISO 字符串，与 /creators 的 Unix 数字不同！） |
| GET | `/{service}/user/{creatorId}/announcements` | — | 作者公告（HTML） | 200 |
| GET | `/{service}/user/{creatorId}/tags` | — | 作者标签 + 计数 | 200 |
| GET | `/{service}/user/{creatorId}/links` | — | 关联账号 | 200 |
| GET | `/{service}/user/{creatorId}/post/{postId}/comments` | — | 评论 | 200 |
| GET | `/account/favorites` | `type`(post/artist) | 账号收藏 | 401 `{}`（无会话），需 session cookie |
| GET | `/search_hash/{fileHash}` | 64 位 hex | 哈希查文件 | spec 确认（未在 App 中使用） |
| GET | `/app_version` | — | 站点 git hash（text/plain，实测返回 `custom`） | 200 |
| POST/DELETE | `/favorites/post/...`、`/favorites/creator/...`、`/{...}/post/{id}/flag` | — | 收藏/标记（写操作，需会话） | spec 确认 |

## 3. 假设证实 / 推翻记录

| 假设 | 结论 |
|---|---|
| 作者帖子分页用 offset，每页 50 | ✅ 证实（`o` 步进 50） |
| 鉴权为 Cookie 会话 | ✅ 证实（cookie 名 `session`） |
| 登录端点 `/account/login` | ❌ **推翻**（404，App 改为粘贴 cookie 方案） |
| 全站“热门 / 标签 / 随机”端点 | ❌ **推翻**（spec 无这些端点，实测 404；App 未实现，首页用 `/posts` 时间线代替） |
| discord service | ⚠️ 部分推翻：spec 保留 discord 路径参数，但当前 creators 全量数据中只有 patreon(71250)/fanbox(24938)，discord 消息端点 404。App 按 spec 支持三值，但列表实际只有两种 |
| 文件与缩略图域名 | ✅ 已查明（见下） |

## 4. 文件 / 缩略图 / 头像 URL 规则（关键）

API 的 `file.path` / `attachment.path` 形如 `/a9/d7/<64hex>.jpg`（无 `/data` 前缀）：

| 资源 | URL 规则 | 访问性 |
|---|---|---|
| 缩略图 | `https://img.pawchive.pw/thumbnail/data{path}` | ✅ 游客可访问（实测 200 image/jpeg） |
| 原始文件 | `https://file.pawchive.pw/data{path}` | ✅ 游客可访问（实测 200/206）。**注意主域与 img 域的 `/data{path}` 都是 404** —— 缩略图在 img 域、文件在 file 域，混用会"所有原图/视频都加载不出来"。Pawchive 里写死的也是 `file.pawchive.pw/data{path}` |
| 作者头像 | `https://pawchive.pw/icons/{service}/{creatorId}` | ✅ 游客可访问 |
| 作者横幅 | `https://pawchive.pw/banners/{service}/{creatorId}` | ✅ 游客可访问 |

### 4.1 子域 UA 要求（实测 2026-10-07）

`file.pawchive.pw` / `t1.pawchive.pw` 等子域**只认应用专属 UA**：
`Archive-Android (third-party client, no tracking)` → 200；
浏览器 UA（Chrome 串）或空 UA → **403**，正文是"Are you a bot or using download tools? …
preferably with a way to contact you"。`ClearanceInterceptor` 对所有 `*.pawchive.pw`
子域注入该 UA，因此播放器/下载器必须走 `ApiClient.sharedOkHttpClient`（或同一套拦截器的客户端），
换默认 HTTP 栈就会被 403。

### 4.2 deferred 附件（站点未导入原件）——视频基本都是这种

列表接口里能看到没有 `path` 的附件：

```json
{"name": "755699804.mp4", "deferred": true}
```

**详情接口**（`/{service}/user/{user}/post/{id}`）会补上带签名的临时地址：

```json
{"name": "755699804.mp4", "deferred": true,
 "temp_url": "https://t1.pawchive.pw/v/ff202a8c6e2c55d6/master.m3u8?e=1791392960&s=…",
 "temp_download_url": "https://t1.pawchive.pw/d/ff202a8c6e2c55d6/755699804.mp4?e=1791392960&s=…",
 "temp_expires": "2026-11-07T00:00:00+00:00"}
```

实测行为：
- `temp_url` 是 **HLS**（master playlist 含字幕轨，子 playlist 与 `.ts` 分片都带同样的签名参数），
  ExoPlayer 需要 `media3-exoplayer-hls`（`DefaultMediaSourceFactory` 按类路径反射发现该模块），
  并用同一个 OkHttp 数据源工厂（UA/会话）。
- `temp_download_url` 是**直链整文件**（实测 GET/HEAD 200 `video/mp4`），
  但**不支持 Range**（带 `Range` → 416，无 `Accept-Ranges`）→ 下载器断点续传会退化为整段重下，
  这也是 `HttpDownloadManager` 现有行为（416/206 校验不通过就从头下载）。
- 有效期约 30 天（`temp_expires`），过期后重新拉一次详情即可换新链接。
- **早期实现用 `filter { path 非空 }` 把这类附件整个丢弃**，视频便永远不显示；
  现在保留无 path 附件并用 `UrlProvider.playbackUrl/downloadUrl` 优先取临时地址。

App 的对应实现：`com.archive.app.data.remote.UrlProvider`（纯函数，有单元测试）；
img 子域从 Base URL 自动推导（`img.<host>`），站点换域名时改一个设置即可。

## 5. 数据模型要点

- **列表项与详情项字段不同**：列表项精简（`substring`、`preview_state`，无 tags）；
  详情项多 `tags` / `prev` / `next` / `poll` / `captions` / `detail_fetched`。
  → DTO 全字段可空 + `ignoreUnknownKeys`，映射到 `PostSummary` / `PostDetail` 两个领域模型。
- `/creators` 的 `indexed/updated` 是 Unix 秒（数字），`/profile` 是 ISO 字符串 → `FlexLongSerializer` 兼容两种。
- 收藏条目模型为作者形（`faved_seq/id/indexed/last_imported/name/service/updated`）。
- `origin` / `preview_state` / `poll` 形状未定 → 解码为 `JsonElement` 透传忽略。
- **`tags` 字段类型不一致（实测 2026-10-07）**：spec 声明为字符串数组，但真实数据里
  **多数帖子返回 Postgres 数组字面量字符串**（如 `"{\"Atelier Ryza\",Benefits,NSFW}"`，抽样 12 个帖子中 9 个是字符串、
  1 个是数组）。客户端用 `FlexStringListSerializer` 同时接受两种形态，否则详情页整页 JSON 解析失败。
- **评论者昵称在 `commenter_name`，不在 `commenter`（实测 2026-10-08）**：评论对象把评论者拆成
  `"commenter": "105541049"`（数字 ID）与 `"commenter_name": "Captain Keyes"`（昵称）两段，
  而 spec 的评论示例只有 `commenter`、且示例值就是数字 → 照着 spec 写 DTO 会把评论区渲染成一串数字。
  客户端在 `CommentDto` 同时映射两者，界面优先昵称、该字段缺失时回退到 ID。
- 另外 `substring` / `content` 是**原始 HTML 片段**，列表摘要需转纯文本后再展示。

## 6. 其他

- 站点前端是**服务端渲染 + htmx**（非 SPA），API 网页会内嵌 spec；无额外反爬头要求，
  普通 UA 即可访问 API（App 仍发送自定义 UA：`Archive/1.0 (Android; third-party client, no tracking)`）。
- 网页会加载第三方统计脚本（phonydepth.com），**App 不加载任何网页 JS**，仅调用 JSON API。
