# 白泽灵台使用 AstronHub 作为内网更新源的源码核查

日期：2026-10-14。范围：`C:/Project/astronhub` 与 `C:/Project/baizelingtai-pi` 当前源码。只读核查应用和服务端代码、文档与测试，并调用现有清单解析器完成无网络的兼容性检查；未启动或重启服务、未访问私有 GitLab、未发布发行物、未修改白泽灵台。未读取或输出凭据与私有配置。

## 结论

**AstronHub 已有应用包管理与分发基础，但当前不能直接替换白泽灵台的 GitLab 更新源。** 主要缺口是白泽灵台兼容的原始 JSON feed、无需浏览器登录的固定 ZIP 下载地址，以及明确的更新渠道/目标版本选择。现有软件中心是仅浏览器 session 的目录与下载会话 API；不是应用更新协议。[软件路由](C:/Project/astronhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SoftwareCatalogController.java:25)、[下载接口](C:/Project/astronhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SoftwareDownloadController.java:44)、[session-only 策略](C:/Project/astronhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java:316)。

**白泽灵台当前的内网源支持版本检测、显示下载链接和独立 Agent 更新指引，不支持应用自动下载、安装或切换。** 即便 AstronHub 完整提供 feed，也仅能替代 GitLab 的更新信息与安装包托管；真正的无人值守/一键应用更新需在白泽灵台客户端或外部维护进程另行实现。[安装模式](C:/Project/baizelingtai-pi/server/pi-update-service.js:67)、[内网模式拒绝安装接口](C:/Project/baizelingtai-pi/server/pi-update-service.js:184)、[用户契约](C:/Project/baizelingtai-pi/docs/UPDATES.md:19)。

## 当前应用实际更新行为

- 设置页首次打开无缓存时自动检查一次，随后可手动检查；并不是持续后台检测应用新版本。[页面加载](C:/Project/baizelingtai-pi/public/pi-updates.js:107)、[文档](C:/Project/baizelingtai-pi/docs/UPDATES.md:11)。
- 已有“自动检查”针对 Pi 上游引擎 npm 正式版，而不是应用；配置内网 feed 后该通知功能明确 `supported:false`、不调用外网 Pi 查询。[通知服务](C:/Project/baizelingtai-pi/server/pi-update-notifications.js:22)、[内网检查短路](C:/Project/baizelingtai-pi/server/pi-update-notifications.js:37)、[检查与提醒不安装](C:/Project/baizelingtai-pi/docs/UPDATES.md:5)。
- 内网应用和配套 Pi 版本均来自同一 feed。请求合并、12 秒超时、2 MiB JSON 上限、5 分钟缓存；失败显示错误，不回退 GitHub/npm。[HTTP 查询](C:/Project/baizelingtai-pi/server/pi-update-service.js:78)、[manifest](C:/Project/baizelingtai-pi/server/pi-update-service.js:118)、[配套 Pi](C:/Project/baizelingtai-pi/server/pi-update-service.js:126)、[缓存](C:/Project/baizelingtai-pi/server/pi-update-service.js:136)。
- 版本页展示 ZIP 链接和 SHA256，安装指引要求独立 Agent/管理员重新读取清单、比较 SemVer、校验下载字节数与哈希、停机备份、在新程序目录切换、沿用数据绝对路径，失败回退。页面不负责完成包下载后的校验与安装。[页面链接](C:/Project/baizelingtai-pi/public/pi-updates.js:71)、[独立维护提示](C:/Project/baizelingtai-pi/public/pi-updates.js:23)、[更新步骤](C:/Project/baizelingtai-pi/docs/UPDATES.md:31)。
- 旧源码应用安装器是固定 GitHub tar.gz 流程：仅允许 GitHub 下载域名，固定拼出 release URL，运行 npm ci 和隔离探针。它不能通过改 feed URL 就支持内网 Windows ZIP；其 npm 环境还固定到公网 npm。[下载域限制](C:/Project/baizelingtai-pi/server/pi-application-installer.js:22)、[固定 GitHub URL](C:/Project/baizelingtai-pi/server/pi-application-installer.js:77)、[安装与探针](C:/Project/baizelingtai-pi/server/pi-application-installer.js:86)、[固定 npm 源](C:/Project/baizelingtai-pi/server/pi-update-installer.js:14)。
- Pi 独立更新是另一流程，复制应用源码、修改引擎依赖并运行 npm；绑定适配版本的应用拒绝独立 Pi 更新。与本次应用整包分发需求不同。[Pi staging](C:/Project/baizelingtai-pi/server/pi-update-installer.js:70)、[绑定检查](C:/Project/baizelingtai-pi/server/pi-update-service.js:198)。
- `pi-update-probe.js` 是安装候选后的隔离 SDK/RPC 检查，不是更新源查询器，也不是后台更新器；其新身份与无网络边界应继续保留。[隔离身份](C:/Project/baizelingtai-pi/server/pi-update-probe.js:1)、[SDK/RPC 验证](C:/Project/baizelingtai-pi/server/pi-update-probe.js:12)。

## Feed 精确协议与访问要求

来源可由部署环境 `BAIZELINGTAI_UPDATE_FEED_URL`（优先）、兼容的 `PIVANE_UPDATE_FEED_URL`，或包内 `baizelingtai-portable.json` 的 `updateFeedUrl` 指定。显式空值关闭内网源；配置后需安全重启生效。本轮没有改配置或重启。[配置读取](C:/Project/baizelingtai-pi/server/pi-update-feed.js:19)、[运维契约](C:/Project/baizelingtai-pi/docs/UPDATES.md:9)。

必须返回原始 JSON 对象，不能使用 AstronHub 的 `ApiResponse` 包装。字段见解析器：[完整校验](C:/Project/baizelingtai-pi/server/pi-update-feed.js:40)。

| 字段 | 客户端要求 | AstronHub 当前可用数据/缺口 |
|---|---|---|
| `schemaVersion` | 数值 `1` | 需新兼容投影 |
| `version` | 合法 SemVer，不带 `v` | 现有 displayVersion 保持原字符串，不解析 SemVer；需更新资格校验 |
| `piVersion` | 合法 SemVer | 软件包模型/DTO 没有独立 Pi 版本字段；需导入 sidecar/已验证包元数据或管理员录入 |
| `platform` | `win32` | 当前 `WINDOWS` 需映射 |
| `arch` | `x64` | 当前 `X86_64` 需映射 |
| `channel` | `stable` 或 `preview`；stable 不能带预发布 version | 需更新渠道/目标包选择 |
| `publishedAt` | 可解析日期时间 | 包已有 publishedAt，可取被选中的包发布时间 |
| `package` | 相对或同源完整 URL；pathname 必须以 `.zip` 结尾 | 现有下载是登录后创建的 `/downloads/{sessionId}`，不满足 |
| `sha256` | 小写 64 位十六进制 | 包已有 sha256，应取准确上传对象摘要 |
| `bytes` | 正的安全整数 | 包已有 byteSize，应与实际托管 ZIP 一致 |
| `releasesUrl` | 可选，同源 URL | 可生成同源软件详情入口；不同端口/协议也不同源 |

软件包现有响应包含平台、格式、版本、名称、大小、SHA256 和发布时间，[DTO](C:/Project/astronhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/software/SoftwarePackageResponse.java:6)。displayVersion 明确不是 SemVer，[模型注释](C:/Project/astronhub/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/software/SoftwareRelease.java:14)。

URL 限制：仅 HTTP(S)，禁止 user/password、fragment、空白/控制符；所有 query key 只能为 `ref`。包和发布页必须与 feed 严格同 origin。feed HTTP 禁止重定向，只发送 Accept 和 User-Agent，没有 Authorization、Cookie 或 GitLab token。浏览器登录态不会转交部署端查询。因此现有 API token、登录跳转、签名 S3 URL或跨域存储重定向都不能直接用。[URL 校验](C:/Project/baizelingtai-pi/server/pi-update-feed.js:5)、[同源限制](C:/Project/baizelingtai-pi/server/pi-update-feed.js:32)、[无认证且禁重定向请求](C:/Project/baizelingtai-pi/server/pi-update-service.js:84)。

当前 feed 是单个候选包。stable 查询遇 preview 候选得到 no-release；不匹配平台/架构也得到 no-release，不是选择另一个包。版本关系由严格 SemVer 比较产生 available/current/ahead。[选择条件](C:/Project/baizelingtai-pi/server/pi-update-feed.js:50)、[比较器](C:/Project/baizelingtai-pi/server/pi-update-service.js:14)、[版本关系](C:/Project/baizelingtai-pi/server/pi-update-service.js:38)。若同时分发 stable 与 preview，第一版可配置两个固定 feed 路径，由管理员选择实例使用哪个；无需把 `channel` 放 query（目前被拒绝）。[文档](C:/Project/baizelingtai-pi/docs/UPDATES.md:15)。

## 包与构建发布链路

Windows portable ZIP 内含安装完毕的依赖及 Node/Git/rg/fd，自带 package.json、TRIAL_MANIFEST 与 PORTABLE_MANIFEST。构建后对完整 ZIP 计算 SHA256 和 size，生成 `.sha256`、`.update.json`；feed 用与运行端一致的解析器验证。它不是可直接 npm publish 的 Pi 扩展包。[便携运行时](C:/Project/baizelingtai-pi/scripts/package-windows-portable.cjs:179)、[依赖准备](C:/Project/baizelingtai-pi/scripts/package-windows-portable.cjs:197)、[完整 ZIP 身份](C:/Project/baizelingtai-pi/scripts/package-windows-portable.cjs:210)、[feed 生成](C:/Project/baizelingtai-pi/scripts/package-windows-portable.cjs:89)。

默认本机构建配置只接受 GitLab project URL；显式 CLI 参数可完整覆盖为任意合法内网 HTTP(S) feed/package/release URL。因此替换分发端无需修改核心打包器，现有 CLI 即可指定 AstronHub；若希望简化日常配置，可增加 AstronHub build 配置格式。[通用参数](C:/Project/baizelingtai-pi/scripts/package-windows-portable.cjs:54)、[GitLab 默认配置](C:/Project/baizelingtai-pi/scripts/package-windows-portable.cjs:68)。构建器只输出包和 sidecar，并没有这里可复用的“发布到任意服务”客户端；正式上传仍是独立步骤。[输出](C:/Project/baizelingtai-pi/scripts/package-windows-portable.cjs:218)、[发布契约](C:/Project/baizelingtai-pi/docs/development/RELEASING.md:21)。

## AstronHub 可复用基础与关键缺口

1. 应用/版本/包可以继续使用软件中心。ZIP 属于当前支持格式，由管理员填写并确认归档元数据；包状态确认、发布、下架、恢复和推荐都已有管理接口。[格式](C:/Project/astronhub/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/software/SoftwarePackageFormat.java:3)、[管理接口](C:/Project/astronhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/admin/SoftwareAdminReleaseController.java:93)。
2. 推荐按“release 内的 OS/arch/distro/format”绑定已发布包，并不是应用跨版本更新指针；目录版本按 ID 倒序，不按 SemVer。不可把推荐列表第一个包或最高 release ID 自动当 latest。[推荐模型](C:/Project/astronhub/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/software/SoftwareRecommendationService.java:12)、[发布包校验](C:/Project/astronhub/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/software/SoftwareRecommendationService.java:59)、[目录排序](C:/Project/astronhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/repository/JpaSoftwareCatalogQueryRepository.java:145)。
3. 现有 portal GET 返回包装响应，目录仅登录用户；下载先 POST 创建用户 session，再 GET/HEAD，校验 owner、过期、应用/包发布状态。不存在可直接给 feed 的稳定匿名 ZIP URL。[详情响应](C:/Project/astronhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SoftwareCatalogController.java:50)、[下载创建](C:/Project/astronhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SoftwareDownloadController.java:44)、[返回会话地址](C:/Project/astronhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SoftwareDownloadAppService.java:32)、[重新校验](C:/Project/astronhub/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/software/SoftwareDownloadSessionService.java:66)。
4. 现有 software 路由不仅要求登录，还明确 session-only，Bearer API token 不可用。仅添加客户端 Authorization 也不能复用这些路由。[authenticated 策略](C:/Project/astronhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java:158)、[session-only](C:/Project/astronhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java:316)、[token unsupported fallback](C:/Project/astronhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java:381)。
5. 存储流、ETag、Content-Length、Range、HEAD 已可复用，但新更新下载不能直接通过 open(sessionId,userId)；应有独立状态检查和审计入口。[流式传输](C:/Project/astronhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SoftwareDownloadController.java:62)、[存储读](C:/Project/astronhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SoftwareDownloadAppService.java:44)。

## 建议的最小兼容方案（尚未实现）

在软件中心增加“应用更新渠道”，与插件 Git/npm 市场分开；渠道绑定 `app + platform + arch + stable/preview + 已发布 packageRevisionId`，记录经过验证的 SemVer 和 Pi 配套版本。管理员显式推进当前目标，避免无序版本字符串和推荐模型被误用；下架目标立即停止 feed 和固定包分发。上传可附 Windows `.update.json` sidecar，校验 version/piVersion/platform/arch 与包身份，`sha256/bytes` 必须与服务端实际 ZIP 匹配，不能信任任意上传 URL。

建议路径（设计示例，当前不存在）：

```text
GET /updates/baizelingtai-pi/windows-x64/stable/latest.json
GET /updates/baizelingtai-pi/packages/{immutable-package-id}/baizelingtai-VERSION.zip
GET /software/apps/baizelingtai-pi
```

通过 AstronHub 同一 origin 输出原始 JSON 和 ZIP 数据流，不重定向到 S3、不放会话 ID、签名参数或 token。feed 不应长期缓存；准确版本 ZIP 可用 ETag/HEAD/Range。只为管理员明确启用“内网公开更新”的应用开放该独立路由，不把整个软件中心 API 改为匿名。用户在随后 Intelli Dev 兼容性讨论中已确认按应用开启内网匿名更新源；通用实现范围见 [软件发布和更新渠道设计](../28-software-release-update-channels.md)。如果未来改为要求认证，则须同时增加新的受 token 策略支持的机器 API，并修改白泽灵台 feed/下载认证，不能声称配置 URL 即可使用。

示例兼容 feed（版本为占位值；哈希与 bytes 必须由实际包生成）：

```json
{
  "schemaVersion": 1,
  "version": "1.2.3",
  "piVersion": "1.1.0",
  "platform": "win32",
  "arch": "x64",
  "channel": "stable",
  "publishedAt": "2026-10-14T00:00:00Z",
  "package": "/updates/baizelingtai-pi/packages/123/baizelingtai-1.2.3.zip",
  "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
  "bytes": 123456,
  "releasesUrl": "/software/apps/baizelingtai-pi"
}
```

### 客户端修改程度

- **仅替换 GitLab 托管**：完成上述匿名同源 feed/ZIP 后，支持当前协议的白泽灵台实例只需换 feed 配置及可选 releases URL；安全重启后沿用现有检测和人工/独立 Agent 安装。后续便携包使用现有显式构建参数内置新源即可。未支持内网 feed 的旧版首次升级仍需管理员处理。[旧版边界](C:/Project/baizelingtai-pi/docs/UPDATES.md:29)。
- **定期自动发现应用新版本**：另加内网应用的定期检查/通知逻辑，目前自动 Pi 通知在 feed 模式禁用。需要决定周期、后台运行条件和提醒策略，不应混淆为自动安装。
- **应用自动安装/切换**：需要白泽灵台外部维护进程或启动器支持 Windows ZIP 整包 staging、字节数/hash、ZIP 路径检查、空闲/预约/草稿、停机备份、数据绝对路径、切换、启动验收与回退；现有 GitHub tar.gz 安装器及 npm 发布渠道不能代替。AstronHub 仅托管与声明更新，不应执行用户机器停机。该范围需单独规格与原生 Windows 验收。[维护约束](C:/Project/baizelingtai-pi/docs/UPDATES.md:33)、[禁止自停机](C:/Project/baizelingtai-pi/docs/UPDATES.md:35)。

## 验证依据与后续验收

主代理已直接调用当前 `pi-update-feed.js` 的 `parseFeed`，使用固定合成清单验证四项结果：原生裸 JSON 清单被接受；AstronHub 风格的包装响应被拒绝；现有 `/api/v1/software/downloads/{sessionId}` 路径因不以 `.zip` 结尾被拒绝；不同 origin 的 ZIP 地址被拒绝。检查没有网络请求、服务启动或更新执行，只证明当前解析契约；不构成部署联调或应用升级验收。

已阅读现有测试证明设计意图，**未执行它们**：feed 测试覆盖同源、SemVer、平台/渠道、重定向拒绝、登录 HTML 拒绝及内网旧安装接口 409；隔离 GitLab 演练覆盖 ZIP 下载/hash/bytes/目标变化及旧包切换。演练中的下载/安装逻辑不意味着产品已有自动安装。[feed HTTP 用例](C:/Project/baizelingtai-pi/test/pi-update-service.test.js:133)、[字段拒绝用例](C:/Project/baizelingtai-pi/test/pi-update-service.test.js:78)、[隔离演练失败路径](C:/Project/baizelingtai-pi/test/release/gitlab-portable-update.cjs:311)、[ZIP 解压与清单验证](C:/Project/baizelingtai-pi/test/release/gitlab-portable-update.cjs:78)。

实现最小兼容源后，验收应使用独立实例和合成数据证明：旧客户端读取 AstronHub 原始 feed；匿名/鉴权边界按配置；包同源、无重定向且 pathname 为 ZIP；真实 bytes/hash 一致；stable/preview 与非适用平台明确；下架撤出；渠道指针切换不覆盖已有准确包；失败不外网回退。实际应用自动安装如纳入范围，须追加原生 Windows 旧版到新版及回退验收，不能用目录页面测试代替。
