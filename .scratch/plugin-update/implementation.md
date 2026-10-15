# 插件管理与应用更新实施索引

Tracker: local-markdown
Status: implementing
Integration branch: codex/plugin-update-integration
Base branch: codex/software-center-phase1

## Specifications

- [插件规格](../../docs/27-plugin-management-implementation-plan.md)
- [更新规格](../../docs/28-software-release-update-channels.md)
- [决策地图](map.md)

## Tracker operations

- 每票独立文件，Status 使用 ready-for-agent、claimed、done；Assignee 表示认领。
- 读取每票 Blocked by；只有所有阻塞票 done 才能认领。失败/缺环境保留未验收，不自动关闭。
- 实施子代理在由 integration tip 创建的独立工作树和分支工作；先读仓库技能，按 TDD 做一条行为再扩展；合并前更新 integration。
- 合并代理顺序合并，记录提交；统一代码审查和修复后才关闭最后集成票。
- 本次只使用本地跟踪器，不要求推送或创建外部 PR。全仓库跟踪器可另用 /setup-matt-pocock-skills 配置。
- 已有用户改动与 .scratch 其他内容不归本工作提交；规范和研究作为本工作上下文。

## Task graph

| 任务 | 阻塞 | 规格验收分组 |
| --- | --- | --- |
| [插件草稿、版本与审核管理闭环](tickets/01-plugin-drafts-review.md) | None | P1、P4、P2/P3 首条单包路径 |
| [离线单包和仓库批量导入](tickets/02-offline-plugin-import.md) | 01 | P2、P3 |
| [插件下载、历史版本与组件治理](tickets/03-plugin-download-governance.md) | 01,02 | P4 |
| [内网 npm 插件发布及依赖上传](tickets/04-npm-publication.md) | 01,02 | P5 |
| [内网 Git 原生市场发布](tickets/05-git-marketplaces.md) | 01,02 | P6 |
| [发布恢复、重试与渠道撤回](tickets/06-publication-recovery.md) | 03,04,05 | P7 |
| [软件发行资产上传与完整发布](tickets/07-software-release-assets.md) | None | U1、U2、U6（页面部分） |
| [按应用匿名的 GitLab 更新读取](tickets/08-gitlab-update-reads.md) | 07 | U3、U4 |
| [Generic 与白泽灵台更新源](tickets/09-native-update-feeds.md) | 08 | U5 |
| [受控机器发版入口与运维说明](tickets/10-software-machine-publishing.md) | 07,08,09 | U6（机器入口部分） |
| [完整集成与原生渠道验收](tickets/11-integrate-and-verify.md) | 06,10 | P8、U7 |

当前 frontier：内网 Git 原生市场发布，以及 Generic 与白泽灵台更新源。票 01、02、03、04、07、08 已完成并合入；票 06 仍等待票 05，其余由明确阻塞关系推进。

票 03 已合入并关闭；后续恢复票复用其实施工作树。08 代理路由子任务已随 08 主实施分支合入。

票 05 已认领，由票 04 实施代理在 `codex/plugin-git-marketplaces` 独立分支继续，复用已合入的渠道适配器和单队列；迁移预留 V80。

## Migration reservations

- 已合入：01 使用 V74、07 使用 V75、02 使用 V76。
- 在途/后续预留：08 使用 V77（如需要）、03 使用 V78、04 使用 V79、05 使用 V80。分配新迁移前重新检查集成和在途工作树；后续票不得复用这些编号。
- 票 09 已认领，分支 `codex/native-update-feeds`，迁移预留 V81（如需要）；以已合入 08 的选择、治理和传输规则扩展 Generic／白泽更新源。
- 后续预留：票 06 使用 V82、票 10 使用 V83（如需要）；认领前仍须核对集成和在途迁移。预留编号不表示票已解除阻塞或认领。

## Verification seams

领域服务（规则与不可变性）、HTTP（权限与完整流程）、原生解析器/外部渠道适配器（协议与冲突恢复）、页面操作（管理流程）。用户在上述边界已说明后于 2026-10-15 再次明确调用 implement-spec，按这些推荐边界继续；无需重复询问。真实客户端安装保留独立验收。

## Environment acceptance

真实内网 registry、Git 目标和发布凭据；安装样本与签名；NSIS/macOS 原生安装和差分请求分别验收。模拟通过只记服务端证据。

## Preparation checkpoint — 2026-10-14

- 规格、研究与地图初始提交：`afe11f58`。本地文档链接与任务图无环检查已通过。
- 已有 `CONTEXT.md` 用户改动和其他 `.scratch/` 内容未纳入提交；本次补充术语保留在工作副本。
- 票 01 准备工作树：`C:/Users/adolbook/.codex/worktrees/plugin-drafts/astronhub`，分支 `codex/plugin-drafts-review`。
- 票 07 准备工作树：`C:/Users/adolbook/.codex/worktrees/software-updates/astronhub`，分支 `codex/software-release-assets`。
- 两代理只读勘查；计划笔记位于仓库外 `C:/Users/adolbook/.codex/worktrees/plugin-update-notes/`。迁移编号预留 01 使用 V74，07 使用 V75，编码前重新检查。
- 使用等价 Maven reactor 命令（`-pl skillhub-app -am test`）跑既有基线，日志在 `.scratch/plugin-update-baseline.log`。完整结果尚待确认，不能描述为全部通过。
- 尚无新增测试或功能代码。TDD 测试边界问题仍等待用户实际答复，不能用超时或产品范围确认代替该答复。
- 勘查修正首条插件票：必须包含真实 Claude Code 单 ZIP 静态导入和受限托管发布，否则“先审批、后验证”的空草稿会造成隐含循环依赖。后续导入票扩展其他宿主和仓库批量流程，不降低提交门禁。

## Implementation checkpoint — 2026-10-15

- 用户重新调用 implement-spec，继续执行已列测试边界；上节等待确认描述为历史记录。
- 既有基线：storage/domain/auth/notification/infra/search 全部通过；app 共 1101 个测试，0 assertion failures、12 errors、1 skipped，12 errors 均为缺少可用 Docker 导致的 Testcontainers 初始化失败。此问题发生于新代码之前。
- 已启动票 01 和票 07 的独立工作树 TDD 实现。新行为先定向 reactor 检查，不重复 90 分钟的无 Docker 全量基线；集成阶段仍须记录完整检查与环境限制。

## Contract checkpoint — 2026-10-15

- 票 01 已合并并关闭（`91e2962a`、`49cc3fc3`），票 02 开始扩展三宿主和批量归档；票 07 继续补齐完整资产管理，未提前关闭。
- `scripts/export-openapi-test.ps1` 成功导出实际 Springdoc 契约，再以 openapi-typescript 生成前端类型。首批插件管理页面已改为引用生成类型，既有软件接口的 MACOS 枚举同时进入生成文件；软件快照 DTO 尚无控制器引用，须在票 07 管理端点合并后重新生成。
- 全前端 `tsc --noEmit`、插件 API/页面定向 ESLint、管理页面 fetch 边界交互测试 1 条均通过；日志为 `.scratch/plugin-update-{openapi,typecheck,contract-lint,ui-contract}.log`。这些检查不代替最终全量检查或 Docker staging。

## Merge checkpoint — ticket 01

- `819c566c` 经合并代理核查日志与实际提交后，以合并提交 `91e2962a` 进入集成分支。票 01 首条 Claude ZIP、审核与受限托管路径验收完成；票 02 解除阻塞。
- 验证：领域 2、既有项目约束 1、路由政策 33、HTTP 4 通过；新增归档歧义红测修复后 ZIP 9 通过；页面交互 1、TypeScript 和定向 ESLint 通过。原始最终 reactor 曾包含归档红测失败，不将该轮整体描述为成功。
- 生成 OpenAPI、临时前端类型替换及统一回归属于票 11；不手改生成文件。真实 npm/Git、生产 PostgreSQL 争用与客户端安装尚未验收。
- 合并未触及用户原有 `CONTEXT.md` 和无关 `.scratch/`，90 个既有文件 SHA256 前后一致；未清理实施工作树。

## Combined contract checkpoint — 2026-10-15

- 票 07 和 02 已分别合并关闭（`95172ec4`/`de385314`、`5e01fc77`/`852969f9`）。票 04 正在实现 npm 发布，票 08 正在实现 GitLab 读取；代理路由子任务先汇入票 08 主实施分支，不独立宣布接口完成。
- 通过实际 Springdoc 重新导出联合控制器契约：398 paths、430 schemas。三个流式上传端点补充真实二进制请求体元数据，经导出结果逐项核对 required/media type/binary schema；没有手写生成文件。
- 导入界面的临时 API 类型已替换为生成别名；初始插件写操作也引用生成请求类型。联合契约的全前端 TypeScript 检查、导入界面定向 ESLint 和 `git diff --check` 通过。日志为 `.scratch/plugin-update-{combined,binary}-openapi.log`、`.scratch/plugin-combined-contract-{typecheck,lint}.log`。

## Merge checkpoint — ticket 07

- 完整工作分支 tip `86d22c89`（含 `4f524920`、`3bc4cdda` 及最新集成）以 `95172ec4` 合入；票 07 关闭，票 08 可开始。不是只合并早期模型切片。
- 实际最终 reactor 日志通过 25 项：模拟 S3 SDK 8、领域 2、原生元数据 8、真实 H2/本地存储 HTTP 6、实际 Springdoc 导出 1；0 failures/errors。页面 8 项、全前端类型检查及定向 lint 通过；没有把 SDK 模拟描述为真实 S3。
- 实际 Springdoc 3.0.1 导出 394 paths / 420 schemas 后重新生成前端契约，软件管理控制器、页面及生成类型均已合入。最终统一契约和回归仍在票 11。
- 真实 PostgreSQL/Flyway V75、S3、Docker staging 和 Windows/macOS 原生安装尚未验证；GitLab 读取、generic/白泽 feed、机器发布继续分别由票 08–10 实施。
- 合并无冲突，`git diff --check` 通过；用户现有 90 个文件 SHA256 保持不变，未清理工作树。

## Merge checkpoint — ticket 02

- 完整工作分支 tip `4c22703d`（已包含软件集成 `de385314`）以 `5e01fc77` 合入；票 02 关闭，票 03、04、05 均解除阻塞。
- 实际合并后后端日志 24 项通过（扫描 7、导入 HTTP 4、既有 ZIP 9、管理 HTTP 4）；页面 2 项通过，定向 ESLint 和合并软件后的全前端类型检查通过。
- 实际隔离 Nginx 1.28 在根路径和 `/hub/` 均让声明 101 MiB 的导入请求进入后端，未实际传送大包实体。GitLab 编码路径及 `/updates`、`/releases` 仍保留票 08 验收，不能从此准入探针推断已通过。
- 未手改生成契约；导入临时严格类型仍须由真实 Springdoc 导出后替换。真实内网渠道、原生安装和 Docker staging 继续保留后续验收。
- 合并无冲突、`git diff --check` 通过；90 个用户原有文件 SHA256 保持不变；未清理工作树或重跑无必要构建。

## Merge checkpoint — ticket 04

- npm 发布完整工作分支 tip `8874eb7a`（包含最新集成 `5401e574`）以 `cbb94297` 合入；票 04 关闭。共享渠道账本及 npm adapter 已进入集成，Git adapter 与完整恢复分别由票 05、06 继续；票 08 未合入。
- 实际最终日志 26 项通过：领域 2、app 24，包含真实本机无 uplink Verdaccio 发布、原字节复用/冲突及第二版本发布后首版完整性。合并最新集成后实际 Springdoc/领域/HTTP 7 项通过；页面 2 项、全量 TypeScript 和定向 ESLint 通过。
- SDK 由实际 Springdoc 导出生成；合并无冲突，无生成文件手工修改。原生 Pi 安装、用户真实内网 registry、生产 PostgreSQL 迁移、Docker staging 尚未验收；版本范围未解析、不宣称完整离线安装。完整心跳/尝试历史/显式重新规划保留票 06。
- `git diff --check` 通过；用户原有 90 个文件 SHA256 同时与合并前及票 02 旧清单一致。未推送、建 PR、清理工作树或重复构建。

## Merge checkpoint — ticket 08

- 完整工作分支 tip `76c4fc93`（含票 04 和代理 helper）以 `10526b81` 合入主集成；票 08 关闭，票 09 解除阻塞。唯一额外集成文档认领提交正常保留。
- 合并代理核查实际两轮 reactor 均通过 59 项（auth 33 + app 26），包含根路径和 `/hub` 的真实嵌入 Tomcat 各 8 项、日志边界 9 项及实际 Springdoc 导出。最后注解后的单项真实导出与票 04 合并后全前端 TypeScript 也通过。
- 实际 Nginx 48 项代理检查通过；已安装 electron-updater 6.8.9 DataSplitter 接受真实 HTTP multipart fixture，精确结果 `0189`。这仅验收解析器契约，完整差分、全量回退、原生安装和签名仍未验收。
- 生成契约无冲突合入，最终联合 Springdoc 重导出由集成统一处理；未手工修改 schema。生产数据库、真实 S3、Helm 渲染和 Docker staging 限制保持明确。
- `git diff --check` 通过，用户原有 90 个文件 SHA256 与合并前及票 02 清单完全一致；未重复构建、推送、建 PR 或清理工作树。

## Merge checkpoint — ticket 03

- 完整工作分支 tip `0974d2c1`（已含最新集成 `c2215031`）以 `fc85802e` 合入；票 03 关闭。票 06 仍阻塞于票 05，不提前认领。
- 实际最终联合 reactor 45 项通过（domain 5 + app 40），包括 7 项新 HTTP 行为、01/02/04 回归和真实 Springdoc 导出。独立组件 ACL、下架和确定性版本选择均有 HTTP 证据。
- 最终 UI 分轮 13 + 1 项通过；此前 worker 启动超时轮未计通过。完整类型检查及定向零警告 ESLint 通过。实际联合 03/04/08 契约生成已随分支合入，未手改生成文件。
- 生产 PostgreSQL/Flyway、Docker staging、真实内网 Git/npm 和客户端安装仍保留独立验收；投影中的持久回执夹具不替代实际渠道执行。
- 合并无冲突、`git diff --check` 通过；90 个用户原有文件 SHA256 与合并前及票 02 清单一致。未额外构建、推送、建 PR 或清理工作树。

## Frontend regression checkpoint — 2026-10-15

- 在已合入 01、02、07 及联合生成契约的集成分支运行完整 `pnpm test`：232 个文件、955 条测试全部通过。全量 `pnpm lint` 同时通过。日志为 `.scratch/plugin-integrated-frontend-{tests,lint}.log`。
- 这轮不包含在途 04/08 或后续票，不能作为最终集成验收。现有 pnpm 配置迁移提示与 jsdom navigation 提示未导致失败；不把提示等同于新增功能故障。

## Native Pi installation checkpoint — 2026-10-15

- 实际发布组件 → 本地无上游 Verdaccio → 隔离 Pi 1.2.0 的固定版本安装通过，原始 TGZ 和安装后 Skill 字节一致，测试 registry 已停止；完整边界及首次失败探针见票 11。此结果不代替最终集成、复杂插件运行或目标内网环境验收。
