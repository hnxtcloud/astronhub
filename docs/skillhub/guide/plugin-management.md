# 插件草稿、审核和托管发布

管理入口为控制台的「插件管理」（`/dashboard/plugins`）。支持 Claude Code、Codex 和 Pi 的离线 ZIP/TGZ 导入、仓库候选预览与选择创建草稿。内网 Git/npm 发布渠道和完整渠道治理由后续实施票扩展。

## 导入和提交

1. 选择本人可提交的活动命名空间，创建插件项目。可见范围在创建时确定；此入口仅编辑目录展示名称和介绍，不改写包内文件。
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

目录展示可按权限读取已发布版本。原始文件下载始终要求会话登录并检查当前权限，只返回实际版本包（ZIP 或 TGZ）；旧版本使用固定 release ID，不随项目当前版本指针变化。

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

项目列表支持 `page`、`size`（最多 100），审核者可使用 `pendingReview=true` 过滤尚未审批的提交。

## 部署参数和限制

| Spring 配置 | 默认值 | 用途 |
| --- | --- | --- |
| 后台 `packageBytes` | `104857600` | 单插件 100 MiB |
| 后台 `repositoryBytes` | `524288000` | 仓库归档 500 MiB |
| 后台 `expandedBytes` | `2147483648` | 单次展开内容累计 2 GiB |
| 后台 `files` | `20000` | 归档条目数，含目录及 TAR 元数据 |
| 后台 `candidates` | `100` | 候选插件数量 |
| `skillhub.plugin.import-cleanup-delay-ms` | `3600000` | 过期预览清理间隔 |
| `skillhub.plugin.hosted-worker-enabled` | `true` | 托管发布任务后台处理开关 |
| `skillhub.plugin.hosted-worker-delay-ms` | `5000` | 托管任务轮询间隔 |

预算在后台持久化配置；每次预览保存快照，之后调整预算不改写已有预览。支持的服务器上限为压缩归档 2 GiB、展开 20 GiB、20 万条目及 1000 候选，管理员应按磁盘容量设置。单个原生 JSON 清单及 TAR 扩展元数据额外限制为 1 MiB；TAR 连续扩展记录最多 16 条。拒绝加密 ZIP、ZIP64、链接/特殊文件、稀疏文件、不安全或跨平台歧义路径、重复条目、文件目录冲突、ZIP 本地/中央目录不一致，以及 TAR 结束标记后的隐藏归档。ZIP 扩展元数据仅允许时间戳、NTFS 时间和 UID/GID。

导入使用有界原始请求流，不调整 Skill 的 multipart 或包业务限制。Nginx 为根路径及部署前缀下的 `/api/v1/plugin-management/imports` 配置单独的流式代理和无限代理请求体上限，由服务端鉴权并根据快照预算及时拒绝超限内容；其他入口仍保持原有 100 MiB 限制。若外层网关有更低限制，需要运维同步调整。单插件旧 `/releases/{id}/archive` 入口保留 100 MiB 上限，并遵守更低的后台包预算；更大的单插件请使用流式预览入口。

完整跨渠道恢复、组件显式关联、公共原生市场安装和真实内网宿主验收见后续实施票。
