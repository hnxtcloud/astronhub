# 插件导入、发布和目录治理

管理入口为控制台的「插件管理」（`/dashboard/plugins`）。支持 Claude Code、Codex 和 Pi 的离线 ZIP/TGZ 导入、仓库候选预览、审核、内网 npm 发布、历史版本下载和目录治理。内网 Git 原生市场与完整跨渠道恢复由后续实施票扩展。

## 导入和提交

1. 选择本人可提交的活动命名空间，创建插件项目并确定初始可见范围。目录编辑只修改展示名称和介绍，不改写包内文件；之后可在治理区显式修改可见性并填写原因。
2. 创建版本，填写版本说明。版本号在项目内唯一，原生包版本必须相同。
3. 选择原始 ZIP 并点击「导入并检查」。服务端验证实际字节、清单、资源引用、文件路径、重名、链接及展开预算，不执行插件、脚本或依赖安装。
4. 查看清单、文件列表和摘要后提交审核。没有真实静态检查证据的草稿不能提交，超级管理员也不能绕过此要求。

命名空间成员可创建本人项目；本人仍为成员时可维护自己的项目。命名空间 OWNER/ADMIN 可维护本命名空间项目，SUPER_ADMIN 可管理。冻结或归档命名空间、归档项目均阻断写入。

## ZIP/TGZ 与仓库预览

在「离线插件导入」中选择目标命名空间和本地 ZIP、TGZ 或 `.tar.gz`。单插件上传需要命名空间提交权限；包含多个插件目录时勾选仓库导入，须为该命名空间 OWNER/ADMIN 或平台 PLUGIN_ADMIN/SUPER_ADMIN。所有候选都进入同一个目标命名空间。

预览展示目录、宿主、原生包名和严格 SemVer 版本、文件和资源清单、依赖声明、摘要、静态证据与失败原因。支持 Claude Code `.claude-plugin/plugin.json`、Codex 兼容 `.codex-plugin/plugin.json` 及 Agent Plugins 1.0.0 根 `plugin.json`；Codex `extensions.com.openai` 中的 apps、hooks、onboardingSkill 及界面资源也必须位于包内。Pi 支持 `package.json` 的 `pi` 清单或约定资源目录，并保留原始依赖和宿主名称，不把旧 `@mariozechner` 依赖自动迁移成新 `@earendil-works` 名称。当前原生名称上限为 200 字符，版本上限 64 字符。

勾选有效候选，选择已有项目或填写新项目标识及可见性，然后导入草稿。未识别的目录不入库；无效候选单独显示，顶层路径、链接、重复条目或全局预算错误会终止整次扫描。同一项目、版本和宿主的相同摘要可跳过，不同内容返回冲突，绝不覆盖。每个选中候选有独立事务和结果，一个失败不阻止其余候选。导入不会自动提交审核。

单独上传的原生 Pi npm TGZ、以插件根目录打包的原生 Claude/Codex ZIP保留完整原始字节。仓库包装目录中的候选会生成独立 ZIP 或 npm TGZ，证据明确标记 `DERIVED_FROM_ARCHIVE`，记录原归档摘要和目录；派生包保留文件内容和普通可执行权限，不声称原归档的签名适用于派生包。Pi 依赖、生命周期脚本、引擎、OS/CPU 等声明作为安装前提展示，依赖可用性为 `NOT_CHECKED_OFFLINE`，运行测试为 `UNKNOWN`，安全状态为 `NOT_SCANNED`。服务器不执行导入文件，也不自动访问公开市场或依赖源。

预览仅上传者可读和应用，24 小时后失效。扫描文件落在私有临时目录，展开内容不整体驻留堆内存，结束时清理。预览包存储在 `plugins/imports/`；导入成功后复制到独立的 `plugins/archives/` 对象，预览过期不会删除版本包。后台每小时清理过期预览；存储清理失败保留记录以便重试。

## 审核和发布

平台 PLUGIN_ADMIN、SUPER_ADMIN 可查看待审核项目、下载原始 ZIP 并审批。PLUGIN_ADMIN 仅凭审核角色不能代替项目维护者修改包。审核意见必填；如记录运行测试通过或失败，须附带测试环境和观察结果。未经运行测试时保持「未经运行测试」；静态清单验证不会自动变成安全扫描通过。

审批成功会创建独立的持久发布任务，审批接口不会直接发布版本。后台每 5 秒处理尚未执行的托管任务，重新核对存储对象的 SHA256；进程重启后仍可继续读取队列。文件缺失或损坏会留下失败状态，恢复原始文件后管理员可在页面重试。

- PRIVATE、NAMESPACE_ONLY 版本仅需星枢托管渠道完成，即可成为已发布版本。
- PUBLIC 版本还会等待所需 Git/npm 渠道。本阶段托管校验成功后仍显示等待外部渠道，不会提前对目录公开。
- 版本提交后不能替换原始包；需要修改包内容或增加宿主时创建新版本。审核和渠道执行记录保持分离。

目录展示可按权限读取已发布版本。原始文件下载始终要求登录并检查当前权限，只返回实际版本包（ZIP 或 TGZ）；公共版本入口也支持具有对应读取权限的 API token。历史包按固定 release ID 或版本号读取，不随项目当前版本指针变化。

## 历史版本、文件浏览和安装说明

插件目录卡片进入 `/plugins/{namespace}/{slug}`，选择已发布固定版本和宿主后可下载包、浏览清单内文本和生成安装说明。PUBLIC 元信息可匿名读取；PRIVATE 和 NAMESPACE_ONLY 每次检查当前用户和命名空间成员身份。下载、文件预览、安装说明需要鉴权；维护者离开命名空间后不会凭旧所有者身份保留访问权。隐藏、归档和下架的版本不再对该入口开放，管理员仍可在管理界面检查历史包。

文本预览先核对完整归档 SHA256，只读取清单内的精确安全路径，拒绝路径穿越、链接、二进制或无效 UTF-8；每文件最多 256 KiB。预览按纯文本显示，HTML、脚本和 Markdown 不执行。Pi TGZ 中按 `package/` 根目录读取。压缩归档、TAR 展开及条目数仍有安全上限。下载、文件查看、安装说明和治理操作分别记录审计。

安装说明不会执行命令或修改用户设置，也不代表真实宿主验收通过：

- Claude Code：验证 SHA256、解压固定版本 ZIP，再以 `claude --plugin-dir` 加载本地目录。
- Codex：验证并解压 ZIP，保存页面生成的本地 `.agents/plugins/marketplace.json`，再执行 `codex plugin marketplace add` 和固定本地市场中的插件安装命令。
- Pi：PUBLIC 且有成功 npm 回执时，按原名称和固定版本生成内网 registry 的 POSIX / PowerShell 命令。仅本次进程选择该源，PowerShell 会恢复之前的环境变量；不携带服务器发布令牌。受限版本或没有匹配成功回执时，验证并解压 TGZ 后从本地 `package/` 安装。

来源回执的名称、版本、摘要必须与发行物匹配；读取源以审批后的成功回执为准，不随当前默认 registry 改动。Node、OS/CPU、依赖和外部服务仍需用户确认。Claude Code/Codex 在 PUBLIC 发布有匹配成功 Git 回执时，生成固定 commit 的市场安装指引；没有匹配回执时使用上述本地包流程。

## 治理和显式组件关联

维护者、命名空间 OWNER/ADMIN、PLUGIN_ADMIN 和 SUPER_ADMIN 可在治理区填写原因后隐藏/恢复显示、归档/恢复项目，或下架已发布版本。只有项目维护权限可以修改可见性和关联组件；PLUGIN_ADMIN 审核身份本身不能将别人的私有插件公开或代为修改关联。冻结命名空间仍允许隐藏、归档或下架以阻断访问，恢复显示、恢复归档及修改可见性要求活动命名空间。

修改可见性前必须完成或显式恢复现存的审批发布任务，不能重写批准时的渠道快照。收紧可见性会立即限制 AstronHub 读取，但已通过 Git、npm 或下载分发的副本不会自动消失，页面保留外部渠道回执和发布历史。变为 PUBLIC 也不会把旧的 HOSTED-only 任务自动变成原生发布任务；需新版本或恢复流程。全部已发布版本的当前指针按持久审批任务 ID 选取最新意图，迟到完成的旧任务不会覆盖较新的版本；下架后重新选择剩余已发布版本，全部下架则清空指针。

组件关联表单填写 `SKILL` 或 `MCP`、命名空间、标识和固定版本（MCP 使用修订号）。它只关联已存在且当前可读的已发布组件，不创建组件、不复制文件、不转移所有权，也不会自动发布或安装组件。每次展示重新检查组件独立权限和生命周期；受限或已下架的组件完全不显示，PLUGIN_ADMIN 不会因插件审核权限获得 MCP/Skill 管理权限。解除关联不修改目标组件。

## 管理 API

所有以下入口位于 `/api/v1/plugin-management`，要求会话登录；写入操作使用现有 CSRF cookie/header 机制，不接受客户端提供静态证据或存储 key。

| 操作 | 入口 |
| --- | --- |
| 项目列表、创建 | `GET/POST /projects` |
| 项目详情、目录信息编辑 | `GET/PATCH /projects/{id}` |
| 创建版本 | `POST /projects/{id}/releases` |
| 版本详情、草稿版本说明 | `GET/PATCH /releases/{id}` |
| 上传 ZIP/TGZ 并预览 | `POST /imports?namespaceId={id}&format=ZIP或TGZ&repository=true或false`，原始 `application/octet-stream` 请求体 |
| 查看预览 | `GET /imports/{id}` |
| 应用选中候选 | `POST /imports/{id}/apply`，`selections` 每项包含 `candidateId`、可选 `projectId`/`slug`/`visibility` |
| 导入预算查询/后台配置 | `GET/PUT /import-settings`，修改限 PLUGIN_ADMIN/SUPER_ADMIN |
| 导入原始 ZIP | `POST /releases/{id}/archive`，`Content-Type: application/zip` |
| 管理员或维护者查看原始包 | `GET /releases/{id}/archive` |
| 提交、批准、驳回 | `POST /releases/{id}/submit`、`approve`、`reject` |
| 超级管理员直接批准草稿 | `POST /releases/{id}/direct-publish` |
| 托管渠道执行或重试 | `POST /releases/{id}/publish-hosted` |
| 已发布历史包下载 | `GET /releases/{id}/download?runtime=claude-code` |
| 管理者安全文件查看 | `GET /releases/{id}/files`、`/file?path={path}`，均支持 `runtime` |
| 目录治理 | `POST /projects/{id}/governance`，`action` 为 HIDE/SHOW/ARCHIVE/RESTORE/VISIBILITY，含必填 `reason`；VISIBILITY 另传 `visibility` |
| 下架版本 | `POST /releases/{id}/yank`，必填 `reason` |
| 组件关联列表/创建 | `GET/POST /projects/{id}/components`，创建传 `kind`、`namespace`、`slug`、`version` |
| 解除关联 | `DELETE /projects/{id}/components/{linkId}` |

项目列表支持 `page`、`size`（最多 100），审核者可使用 `pendingReview=true` 过滤尚未审批的提交。

已发布读取前缀 `/api/v1/plugins/{namespace}/{slug}/versions`：`GET` 返回可读历史和组件；`GET /{version}` 返回固定版本元信息；后缀 `/files`、`/file?path=...`、`/download` 和 `/install` 支持 `runtime=claude-code|codex|pi`，上述四类内容按前述鉴权策略读取。受限或已撤回对象不泄漏元信息。

## 部署参数和限制

| Spring 配置 | 默认值 | 用途 |
| --- | --- | --- |
| 后台 `packageBytes` | `104857600` | 单插件 100 MiB |
| 后台 `repositoryBytes` | `524288000` | 仓库归档 500 MiB |
| 后台 `expandedBytes` | `2147483648` | 单次展开内容累计 2 GiB |
| 后台 `files` | `20000` | 归档条目数，含目录及 TAR 元数据 |
| 后台 `candidates` | `100` | 候选插件数量 |
| `skillhub.plugin.import-cleanup-delay-ms` | `3600000` | 过期预览清理间隔 |
| `skillhub.plugin.hosted-worker-enabled` | `true` | 插件托管及原生渠道发布任务后台处理开关 |
| `skillhub.plugin.hosted-worker-delay-ms` | `5000` | 插件发布任务轮询间隔 |

预算在后台持久化配置；每次预览保存快照，之后调整预算不改写已有预览。支持的服务器上限为压缩归档 2 GiB、展开 20 GiB、20 万条目及 1000 候选，管理员应按磁盘容量设置。单个原生 JSON 清单及 TAR 扩展元数据额外限制为 1 MiB；TAR 连续扩展记录最多 16 条。拒绝加密 ZIP、ZIP64、链接/特殊文件、稀疏文件、不安全或跨平台歧义路径、重复条目、文件目录冲突、ZIP 本地/中央目录不一致，以及 TAR 结束标记后的隐藏归档。ZIP 扩展元数据仅允许时间戳、NTFS 时间和 UID/GID。

导入使用有界原始请求流，不调整 Skill 的 multipart 或包业务限制。Nginx 为根路径及部署前缀下的 `/api/v1/plugin-management/imports` 配置单独的流式代理和无限代理请求体上限，由服务端鉴权并根据快照预算及时拒绝超限内容；其他入口仍保持原有 100 MiB 限制。若外层网关有更低限制，需要运维同步调整。单插件旧 `/releases/{id}/archive` 入口保留 100 MiB 上限，并遵守更低的后台包预算；更大的单插件请使用流式预览入口。

完整跨渠道恢复和真实部署内网宿主验收见恢复及集成实施票。

## 内网 npm 发布与独立依赖

运维先在部署 YAML 中提供精确允许的 registry 基地址及发布凭据引用；管理员只选择这些地址和引用，不提交或读取发布令牌。例如：

```yaml
skillhub:
  plugin:
    publication:
      allowed-registries:
        - https://npm.internal.example/team/
      credentials:
        team-publisher: ${PLUGIN_TEAM_NPM_PUBLISH_TOKEN:}
```

`credentials` 是此功能专用的显式映射。引用名不会被当作任意环境变量名解析，不能引用 OAuth、S3 或引导管理员凭据。安装用户在自己的 npm 配置中处理安装认证；服务器发布令牌不会进入安装命令、管理响应、审计或包。管理员配置每次新增不可变修订，可设为之后审批的默认源，也可在草稿中选择源。

公开 Pi 发行物必须保留原始 `package/` npm TGZ，原始包名及版本不变。由仓库归档派生的 Pi 重新打包产物不能直接进入 npm；需要显式导入原始 TGZ。批准时固定目标修订、凭据引用和包摘要。缺少目标时计划显示 `BLOCKED / MISSING_CONFIGURATION`；后续修改默认源不会改写该审批历史，需另行显式、审计化重新规划（恢复票实现）。受限插件只使用鉴权托管渠道。

发布通过 registry HTTP 协议上传原 TGZ，文件方式流式生成 base64 请求，不执行 `npm pack`、包脚本或任何导入代码。最多处理 100 MiB TGZ、4 MiB registry 元数据和 100 条直接依赖声明；网络和响应体有超时。仅访问配置源，不跟随跳转，不接受其他 origin 或基路径外的 tarball URL，不采用包内 `publishConfig`。固定名称/版本已存在时下载并逐字节比较 TGZ，相同则复用，不同则拒绝；写入失败或结果不确定时先读取实际内容核对。不会覆盖或自动重命名。同包新版本不会删除原版本。

安装指引固定 `pi install npm:@scope/name@1.2.3`，例如通过单次调用的 `npm_config_registry` 指定内网源。`latest` 是 npm registry 的可变标签，本实现沿 npm 发布协议更新它，不保证迟到发布时标签单调递增；应使用固定版本指引。直接依赖中的精确版本显示内网可用/缺失，版本范围显示 `UNRESOLVED_RANGE`，Git、URL、本地依赖显示不支持且不会抓取。Node、OS、CPU、peer 依赖和外部服务仍需单独确认，静态检查和 registry 可用性不证明完整离线安装或运行测试通过。

依赖管理入口支持多选 TGZ，每包独立上传、检查并等待管理员确认。它保留上传者、来源、原摘要、固定目标、尝试次数与结果，不创建插件项目或进入插件审核。依赖包硬上限为 100 MiB，后台包预算可以降低它；提高导入预算不会提高此上限。重复确认已经成功的项只返回已有结果。

| API（前缀 `/api/v1/plugin-management`） | 用途 |
| --- | --- |
| `GET /npm/settings`、`POST /npm/registries` | 管理员读取允许配置/凭据引用，新增源配置修订 |
| `GET /npm/registries`、`PUT /releases/{id}/npm-registry` | 已登录用户读取配置源，维护者为草稿选择源 |
| `GET/POST /npm/dependencies` | 管理员查看依赖记录或上传原始二进制 TGZ（`registryId`、`source` 查询参数） |
| `POST /npm/dependencies/{id}/publish` | 确认/重试独立依赖发布 |
| `GET /releases/{id}/publication-channels` | 查看批准计划和已成功的外部分发凭据记录 |
| `POST /releases/{id}/publish-channels` | 管理员重试未完成渠道，保留已有成功记录 |
| `POST /releases/{id}/npm-prerequisites` | 按固定内网源检查直接 npm 依赖 |

HOSTED、npm、Git 共享一个持久任务及渠道记录。成功渠道不会因其他渠道失败而删除或重复发布，全部必需渠道完成后目录才显示已发布。轮询排除缺配置的阻塞计划，并按尝试次数公平调度；外部网络调用在数据库锁之外，短事务领取的尝试令牌防止过期执行覆盖新结果。完整尝试历史、长任务心跳、治理撤回及显式重新规划由恢复实施票继续补齐。

本机实际验证使用无 uplink 的临时 Verdaccio 6.1.6；该开发夹具不是产品依赖或公网回退。真实部署 registry、原生 Pi 安装和完整 staging 应由部署环境另行验收。


## 内网 Git 原生市场发布

运维预先创建目标 Git 仓库，部署服务器需要 `git` 命令。管理员在插件管理页为 namespace 选择允许的远端、分支及凭据引用。每次保存创建不可变目标修订；同一远端和分支不能由其他 namespace 认领，旧认领记录保留。配置修改只影响之后的审批，不改写已经批准、失败或阻塞的计划。

仓库的生产和 staging 后端镜像均安装 Git 与系统 CA 证书。使用企业 CA 时，应在自定义镜像构建阶段加入系统信任库；仅在 Java truststore 中信任不能保证 Git 的 HTTPS 信任。应用仍以非 root 用户运行，不在运行时安装依赖或关闭 TLS 验证。手工运行 JAR 的部署须自行提供 Git 和系统 CA。

```yaml
skillhub:
  plugin:
    publication:
      allowed-git-remotes:
        - https://git.internal.example/team/plugins.git
      credentials:
        git-publisher: ${PLUGIN_GIT_PUBLISH_TOKEN:}
```

首版发布和读取地址必须为同一个规范 HTTPS URL；不支持 SSH、URL 内凭据、重定向、查询参数或片段。`file:///` 仅供运维明确允许的本地夹具。服务器 Git 启用 TLS 验证，企业 CA 需在部署环境信任；不能通过管理员配置关闭验证。裸 token 作为 Bearer 使用，需要 Basic 认证的 Git 服务可在部署凭据映射中提供完整 `Basic ...` 值。值只传入发布 Git 子进程的指定目标认证头，不进入数据库、参数列表、安装指引或日志。读取用户配置自己的凭据，不复用服务器发布凭据。

PUBLIC Claude Code 和 Codex ZIP 按原始文件字节和执行权限写入 Git，Pi 不复制进 Git。市场名为 global 的 `astronhub`，其他 namespace 的 `astronhub-{slug}`。Claude 索引为 `.claude-plugin/marketplace.json`，Codex 索引为 `.agents/plugins/marketplace.json`，结构分别遵守宿主格式；文件放在 `plugins/{runtime}/{nativeName}/{nativeVersion}`。同市场原生名称被其他项目占用、同版本已有内容不同、目标文件路径已有未管理内容、受管索引被外部修改，或包中包含 `.git`/`.gitmodules` 控制路径时拒绝发布，不自动改名或覆盖。

发布复用统一任务队列和批准快照。外部网络调用在数据库事务之外，推送前短事务持久化 parent/tree/commit/计划摘要意图，普通 Git push 保留远端历史，禁止 force push。远端并发前进时最多重新读取并重建三次；不确定推送结果或重启先核对远端祖先和实际 tree，再复用已确认结果。当前索引按持久审批任务 ID、发行 ID 排序；迟到旧请求只增加历史版本，不回拨当前选择。已成功渠道及历史回执继续保留。

安装接口核对成功回执中的 runtime、原生名称、版本及归档 SHA256，还核对该 commit 的原生索引是否选择此版本，再使用回执远端和固定 commit，不读取当前目标配置。迟到旧任务若只增加历史文件而索引仍选择较新版本，旧版本继续使用固定归档和本地安装，不生成会安装较新版本的市场命令。Claude 先克隆并检出固定 commit，再注册本地市场；Codex HTTPS 注册使用 `--ref <commit>`。Codex 不接受 `file://` URL，夹具因此先克隆到本地再注册目录。页面提供 POSIX / PowerShell 指引。受限内容始终不进入 Git，继续使用鉴权包下载和本地安装流程。隐藏、归档或下架不会删除用户克隆、Git 历史或已成功的渠道记录；显式索引撤回属于恢复治理流程。

Git 发布使用独立临时裸仓库和 fast-import，不检出或执行导入包，不采用系统/全局 Git 配置、hooks、过滤器或子模块。单 Git 命令限 90 秒，JSON 读取限 4 MiB，新增文件累计限 2 GiB，临时 Git 目录限 4 GiB；浅读取保留最多 256 层远端历史。超出预算或无法证明历史提交时拒绝或保留失败证据，需要管理员处理，不自动放宽限额。Git 深度及容量边界不是任意规模仓库支持承诺。

| API（前缀 `/api/v1/plugin-management`） | 用途 |
| --- | --- |
| `GET /git/settings` | PLUGIN_ADMIN/SUPER_ADMIN 查看允许的远端、凭据引用、namespace 和目标修订 |
| `POST /git/targets` | 为 namespace 创建并选择后续审批使用的新目标修订 |
| `GET /releases/{id}/git-intents` | 有管理读取权限者查看不可变推送意图，不返回凭据值 |

本机真实 Git 测试覆盖两种独立索引、原始 CRLF/BOM 字节、并发分支前进、迟到顺序、回执丢失后的重启核对、内容/身份冲突和 Git 控制路径拒绝。隔离的 Claude Code 2.1.273 与 Codex CLI 0.162.1 已使用实际生成仓库完成 HTTPS 市场注册和安装；不启动插件会话或执行插件代码，产品仍标记运行未经验证。真实内网 Git、企业认证、原生宿主运行与完整 staging 需在部署环境另行验收。
