# 插件管理与应用更新 E2E 交接计划

2026-10-20执行更新：[清单验收报告](../.scratch/plugin-update-e2e/manual-acceptance-results.md)已完成7组/127步及BAIZE版本切换，原全量Wayfinder仍partial。下文2026-10-19“尚未执行”是当时交接状态，不能代表当前执行结果；各次运行单独保留SHA/源码摘要和证据。

更新：2026-10-19（初建2026-10-16）。产品范围基线：`dd28864c`；本次交接 checkout：`704328918f5017483f055f8bfbce56ff47b62679`，分支 `codex/plugin-update-integration`。执行者必须先记录自己实际 checkout 的完整 SHA；修改代码后重新记录被测 SHA。

**尚未执行本计划 E2E**。当前已提供根目录 `.env`，最近连接检查为 PostgreSQL17.9（数据库 `astronhub`，认证及只读SELECT1通过）和Redis7.2.11（DB0，认证及PING通过）；未运行迁移、启动服务或执行浏览器。首先阅读[当前环境与启动步骤](../.scratch/plugin-update-e2e/start-here.md)，先验收实际PG17环境下的基础业务。此前的后端、Vitest、HTTP/原生CLI合同证据见[已有验收记录](plugin-updates-validation.md)，不转记为本计划通过。

执行入口：[Wayfinder](../.scratch/plugin-update-e2e/map.md)。逐票领取、环境盘点、结果登记及报告模板均在该目录。测试对象为新增插件管理和多应用更新源，并回归软件中心上传/识别/下载、登录、命名空间、Skill/MCP 既有读取和权限；不扩展新产品功能。

## 交给其他 coding agent 的启动指令

```text
请先读取 AGENTS.md、.scratch/plugin-update-e2e/start-here.md、
docs/plugin-updates-e2e-plan.md 和 .scratch/plugin-update-e2e/map.md，领取01。
根目录.env已配置外部PG和Redis，最近连接检查通过；凭据仅本地读取，不输出或提交。
先加载.env并启动独立后端/前端，完成真实登录、插件HOSTED闭环、手工ZIP软件管理、
基本权限与CSRF；按01→02/04基础子项→05权限子项→09基础报告执行。
当前PG为17.9，结论标明实际版本，PG16兼容性另列待验，不因此停住基础业务。
Git/npm、Inspector、扫描器、Nginx/S3和原生终端缺失时仅阻塞对应子项。
这是执行真实服务 E2E 的任务：先盘点环境、记录实际 HEAD、准备隔离账号和样本，
再编写/执行 web/e2e 下的真实 API Playwright 场景及必要的协议/原生验收。
已有验收记录仅作参考，不计入本轮通过。每项记录环境、步骤、断言与证据。
缺环境标 blocked，继续其他独立票；失败先登记，不得靠 mock 响应、跳过、放宽权限、
改写摘要或无限重试变绿。发现缺陷按仓库规范做最小修复并重验受影响流程。
只操作本轮专用数据和测试服务；不改动用户已有 CONTEXT.md、无关 .scratch，
不推送、部署生产或修改两个原始客户端仓库。用报告模板交付执行结果和阻塞清单。
```

## 1. 验证层次和环境

| 层次 | 必须真实使用的边界 | 可形成的结论 |
| --- | --- | --- |
| L1：业务浏览器 E2E | Chromium → Web → 实际 Spring Boot → PostgreSQL / Redis7 / 对象存储；本次提供PG17.9；真实会话与CSRF | 实际数据库版本环境的业务、权限和持久化闭环；不代表PG16兼容、真实Git/npm或安装成功 |
| L2：发布与部署 E2E | 专用内网 Git/npm、实际 Nginx/网关、S3/MinIO、Inspector、重启/故障注入 | 对应内网发布、恢复、代理和存储链路；不代表终端应用安装 |
| L3：原生客户端验收 | 隔离 Claude/Codex/Pi；可重置 Windows/macOS 测试机和真实应用发行物 | 所提供客户端版本的安装/更新；差分及回退仍须各自实测 |

没有Docker也可以对已提供的隔离服务运行L1/L2浏览器测试；**Docker staging、镜像构建、Compose配置检查和Docker/Testcontainers测试仍要求可用Docker**。当前PG17.9可形成该版本下的基础验收结论，PG16兼容性需另用PG16实测；H2/其他PG版本均不能替代它。L2/L3缺条件时保留blocked，其他独立组仍可执行。

### 需要提供的资源清单

| 资源 | 最小内容 | 用途/缺失影响 |
| --- | --- | --- |
| 测试实例或可启动主机 | 专用实例、源码 SHA、Web/API 地址、启动/停止方式；Java 21、Maven、Node.js、pnpm、Python、Git、Bash/make 或等价命令 | 全部流程。建议 16 GiB 起、32 GiB 更合适；先单 worker，按实际内存调整，不并行堆叠 JVM/Node |
| 离线构建物 | Maven 依赖、pnpm store/npm 包、Python wheels（涉及 scanner 时）、容器基础镜像/服务镜像；锁文件和镜像摘要 | 内网不能依赖临时公网下载；提供内部镜像库/缓存或预装运行环境 |
| Playwright | 与安装的 `@playwright/test` 匹配的 Chromium 和系统依赖 | 浏览器 E2E；缓存目录存在不等于版本匹配或可启动 |
| 数据与会话服务 | 已提供PG17.9的`astronhub`库及Redis7.2.11 DB0；执行者复核独立库/账号/迁移权限、备份恢复及Redis共享范围；PG16另供兼容性测试 | 真正持久化、会话、锁；PING不证明全部Redis操作权限；提供管理员可创建测试账号和授予角色的能力 |
| 存储 | 独立 LocalFile 目录用于最小层；L2 提供 S3/MinIO endpoint、专用 bucket/prefix、凭据引用、容量、清理边界 | 必须分别记录 LocalFile 与 S3 结果；不可访问时相应层 blocked |
| HTTPS 入口 | 根路径及 `/hub/`（另测 `/hub.v1/`）入口、Nginx/Ingress 配置、DNS、服务端/浏览器/Java/Git/npm 所需 CA | 编码路径、同源更新源、Range、缓存和流式上传；不可关闭 TLS 校验代替配置 |
| 账号和角色 | SUPER_ADMIN；可由其创建 PLUGIN_ADMIN、SOFTWARE_ADMIN、作者 MEMBER、namespace OWNER/ADMIN、无关用户；匿名浏览器独立上下文 | 不能只用超级管理员覆盖全部路径；账号可停用/撤权并恢复 |
| 内网 Git | 至少两个专用 HTTPS public 可读仓库/分支，服务器写入凭据引用，允许故障注入的目标；服务器可调用 Git | Claude/Codex 市场、冲突、原目标核对、重新规划和撤回；不使用 SSH 或公网回退 |
| 内网 npm | 无公网 uplink 的独立测试 scope/registry，允许发布、读取及认证的凭据引用；第二目标用于重新规划 | Pi 原 TGZ、依赖上传、字节冲突与恢复；npm 版本不可覆盖/假设可删除，用独立 runId 名称 |
| Inspector | Linux worker（msiinfo/msitools、dpkg-deb、rpm）、独立临时盘；后端和 worker 同一 inspector token 引用 | MSI/EXE/DEB/RPM 自动静态识别；缺 worker 时只能验证手工 ZIP/DMG，不算识别通过 |
| 安全扫描器 | 若运行既有 Skill 扫描闭环，提供 scanner 服务和任务处理条件 | 插件静态检查不等于安全扫描或运行测试；不要为得到绿状态伪造扫描结果 |
| 软件/插件样本 | 三宿主合法原始包、批量仓库包、恶意路径/链接/超限/同版冲突样本；软件合法 MSI/EXE/DEB/RPM、ZIP/DMG，原始 YAML/JSON/blockmap/校验文件 | 每个包保存来源、版本、大小及 SHA256/SHA512；生成样本只证明声明的边界 |
| 原生更新样本 | 旧版和新版真实 NSIS、macOS ZIP（可附 DMG）、签名及原始更新元数据；至少两个机器版本与旧版 blockmap | 签名、原生安装、差分/全量回退；不得把重命名的伪 EXE 当 NSIS |
| 测试终端 | 隔离 CLI 配置；可重置 Windows x64/macOS 对应架构机器、已装旧应用及来源切换方式 | 原生安装和首次来源迁移；不在日常开发机或生产终端执行安装 |
| 故障与容量 | 本轮服务重启权限、受控断流/延迟代理、测试盘及网络预算、可查询日志和进程指标 | 不确定推送、续租、重试、5 GiB 实体和 20 路下载；若每路保存 5 GiB，单客户端侧约需 100 GiB，可改流式校验减少留盘 |
| Helm/集群（如验收此部署形态） | Helm、已离线准备的 chart 依赖；若实际部署还需专用 namespace/kubecontext/Ingress | lint/template 与真正部署分别记录，缺 Helm/依赖不自动公网拉取 |

环境详细输入表为 [environment.example.yaml](../.scratch/plugin-update-e2e/environment.example.yaml)。这是**交接盘点表，不是现成应用或 Playwright 配置**；账号/密钥填引用，不填写真实秘密到仓库。机器 token 由管理员在测试应用签发，不能拿 inspector token 或任意自报 scope 代替。

### 本机现状

2026-10-19更新：PG/Redis连接结果与启动方式以[start-here](../.scratch/plugin-update-e2e/start-here.md)为准。根目录`.env`已存在且被Git忽略；执行者加载后端`SPRING_*`变量。连接通过不等于Flyway、会话读写、限流Lua、任务队列或业务E2E通过。后续账号/代理/扫描器/渠道和浏览器环境仍需01盘点。

以下保留2026-10-16的历史工具盘点，执行者重新核对：

已发现 Java/Maven/Git/Python/Node 可执行文件；Node 为 24.19.0，`web/node_modules/@playwright/test` 为 1.58.2，并有浏览器缓存目录。尚未验证浏览器可启动或与缓存匹配。项目 `packageManager` 声明 pnpm 10.33.0；现有 CI 模板仍有 Node20/pnpm9，执行者需记录实际版本并核对锁文件，不盲目照搬。

当时Docker、Helm未在PATH找到；当时尚未提供PG/Redis连接，其他S3、运行中应用、测试账号、内网Git/npm和测试终端亦未确认。此前PG18临时迁移/本机Git/npm探针不代表这些资源已经提供。本计划没有启动服务、访问真实发布目标或运行E2E。

## 2. 启动和现有测试入口

先阅读 [既有 E2E 说明](e2e.md)、[软件中心部署](26-software-center-deployment.md)、[Inspector](../inspector/README.md)。内网预先准备依赖，不默认执行联网安装命令。

**本次外部PG/Redis优先按[start-here的Windows步骤](../.scratch/plugin-update-e2e/start-here.md)加载`.env`、启动后端和Vite**。下列Docker命令用于另有隔离Compose环境时，不能直接套到本次连接配置：release Compose的datasource URL仍指向容器内postgres，根目录`.env`不会自动使它改连外部PG；Makefile启动后端还会显式启用scanner。

```bash
# 已准备 Docker/镜像/依赖且使用本轮隔离配置时，二选一启动方式
make dev-all
make dev-status
# 或生产形态回归；会构建镜像/静态资源并运行 smoke
make staging
```

`make dev-all` 的 local profile 默认启用 mock。严格权限场景须在**实际生效的 Spring 配置**关闭 `skillhub.auth.mock.enabled`、`direct.enabled`、`session-bootstrap.enabled` 并通过实际请求验证，而不能只看环境变量（启动脚本或其他配置来源可能改变实际设置）。修改测试实例的配置后按仓库方式重启，不影响其他用户服务。

```bash
# 已有实际 Web/Nginx 环境；在 web/ 执行
PLAYWRIGHT_BASE_URL=https://astronhub-e2e.internal.example PLAYWRIGHT_WORKERS=1 pnpm exec playwright test --project=chromium --workers=1 --retries=0 --trace=on --reporter=list,html

# 列出现有用例、先跑既有 smoke（均不代表新增插件/更新流已覆盖）
pnpm exec playwright test --list
pnpm run test:e2e:smoke
```

PowerShell可使用`$env:PLAYWRIGHT_BASE_URL='https://...'`、`$env:PLAYWRIGHT_WORKERS='1'`后执行相同pnpm命令。地址以执行者实际启动的实例为准；本次只提供连接配置和启动步骤，没有已验证运行的应用实例。

关键差异：

- 现有 `playwright.config.ts` 设置 `PLAYWRIGHT_BASE_URL` 后不会启动 Vite；未设置时仅自动起前端，**不会启动后端/PG/Redis**。默认地址为 `http://127.0.0.1:3000`。
- 当前 Vite 只代理 `/api`、OAuth 路由，不代理 `/updates`、`/releases` 等更新入口。协议 E2E 走真实 Nginx/部署入口；直连 API 可用于诊断，不能代替代理验收。
- `/hub/` 下，`page.goto('/dashboard')` 或 `request.get('/api/...')` 这样的根相对 URL 会丢失前缀。为新测试补前缀感知导航/请求助手，**仅改 baseURL 不够**；不能改生产路由来迁就测试。
- 复用 `session.ts` 时严格用例要显式 `allowMockSession:false` 或现有凭据登录函数，并审计其余数据助手、HTTP 头和后端实际配置，禁止 mock 身份回退。匿名检查使用全新无 cookie/Authorization 的上下文。
- 本地默认重试为 0，`trace:on-first-retry` 不会产生首轮失败 trace；本计划显式 `--trace=on --retries=0`。若诊断重跑，保留首轮失败与后续结果，不覆盖证据。
- 默认 smoke 只有 auth-entry/landing-navigation/route-guard/dashboard-shell，必须新增本计划业务 spec。新 spec/标签先 `--list` 确认非零用例，避免空选择误报通过。

## 3. 测试场景和断言

每条票包含前置、步骤、失败边界、证据和清理要求。UI 主路径由浏览器实际操作完成；API 可用于准备隔离数据、查询佐证和清理，但不能全程 API 执行后仅打开页面截图就宣称 UI 闭环。

- 插件：ZIP/TGZ/仓库预览 → 选择草稿 → 静态检查 → 普通作者提交 → 独立审核 → 后台持久任务 → 目录/下载/固定安装指引；含驳回再提交、不可变版本、历史包及独立组件 ACL。
- 原生渠道：真实 Git 两种市场索引、原始字节/权限、旧 commit、原 TGZ/npm 依赖和冲突；必须从实际内网目标读回比对，不只检查本地回执。
- 软件：分片上传/续传/识别/确认/发布 → 更新草稿/完整原始资产 → 校验/快照 → 每应用匿名开关/显式渠道目标；按实际平台/架构/protocol 测试。
- 更新读取：GitLab tags/raw、generic 和白泽，真实 GET/HEAD/Range/多区间与摘要；关闭源/下架/历史固定地址都重新治理，缺失文件不可返回 HTML 200。
- 权限/机器发版：跨账号/namespace/app/nested ID；真实 cookie+CSRF；绑定 token/live 角色/停用/撤销；使用 Python 工具新发、续发及不匹配状态拒绝。
- 恢复：租约/重启/未知远端结果的原目标核对/修订和审计；已成功渠道不重发、所有必要渠道成功才发布；Git 撤回只移当前索引，npm 明示不支持远程删除。
- 容量/生产形态：LocalFile/S3 分别跑；真实大文件经浏览器入口/外层网关完整传输并校验，20 路下载按软件中心范围；记录内存、磁盘、时延、吞吐和错误，不凭空新增性能 SLO。
- 原生：Claude/Codex/Pi 固定安装及新版本；Windows/macOS 真正升级、签名、差分和全量回退分别作证。白泽现有下载/维护安装流程不能写成无人值守自动安装。

## 4. 结果、证据与清理

用 [报告模板](../.scratch/plugin-update-e2e/report-template.md) 记录每个 case：ID、被测 SHA、部署/镜像/工具版本、账号角色、输入摘要、操作、期望、实际、结果、证据路径。结果只使用 passed/failed/blocked/not-run/not-applicable；重试后通过仍附首次失败和原因。报告最终结论允许 partial，不得把 blocked 变 passed。

保留 Playwright HTML、trace、截图、网络状态与 requestId、脱敏服务日志、外部 Git commit/npm TGZ 摘要、迁移记录、下载摘要及性能原始值。trace/HAR/storageState 可能含会话，原始文件放受限证据目录，分享前脱敏；不提交 token、cookie 或真实发布凭据。

所有资源带 runId，登记实际创建的 ID/路径。只清理该列表范围；npm/Git 历史不可假定能删除，使用专用目标并保留治理与审计证据。故障仅注入本轮实例和服务；恢复原配置/可用性并确认无孤儿测试进程。不全局重置数据库/Redis、不清空共享 bucket、不 force push、不修改现有 Flyway checksum/history来造绿。

两个原始客户端仓库 `C:/Project/baizelingtai-pi` 与 `C:/Project/intelli-dev-release/intelli-dev/client` 维持只读；如需首次来源迁移，使用用户提供的新测试构建或原域名测试代理，未提供则该项 blocked。

若测试中修改后端/前端，按仓库技能运行受影响的验证；控制器变更实际导出 OpenAPI 并生成类型，不能手改 `schema.d.ts`。最终报告列出最小修复、对应红绿记录、重验范围和剩余风险。既有后台状态、审核、治理规则不是为了 E2E 可以放宽的开关。
