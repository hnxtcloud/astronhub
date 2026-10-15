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

当前 frontier：离线单包和仓库批量导入；软件发行资产上传与完整发布（进行中）。插件草稿、版本与审核管理闭环已完成并合入；其余由明确阻塞关系推进。

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
- `scripts/export-openapi-test.ps1` 成功导出实际 Springdoc 契约，再以 openapi-typescript 生成前端类型。首批插件管理页面已改为引用生成类型，软件快照基础契约同时进入生成文件；后续端点合并后还须重新生成。
- 全前端 `tsc --noEmit`、插件 API/页面定向 ESLint、管理页面 fetch 边界交互测试 1 条均通过；日志为 `.scratch/plugin-update-{openapi,typecheck,contract-lint,ui-contract}.log`。这些检查不代替最终全量检查或 Docker staging。

## Merge checkpoint — ticket 01

- `819c566c` 经合并代理核查日志与实际提交后，以合并提交 `91e2962a` 进入集成分支。票 01 首条 Claude ZIP、审核与受限托管路径验收完成；票 02 解除阻塞。
- 验证：领域 2、既有项目约束 1、路由政策 33、HTTP 4 通过；新增归档歧义红测修复后 ZIP 9 通过；页面交互 1、TypeScript 和定向 ESLint 通过。原始最终 reactor 曾包含归档红测失败，不将该轮整体描述为成功。
- 生成 OpenAPI、临时前端类型替换及统一回归属于票 11；不手改生成文件。真实 npm/Git、生产 PostgreSQL 争用与客户端安装尚未验收。
- 合并未触及用户原有 `CONTEXT.md` 和无关 `.scratch/`，90 个既有文件 SHA256 前后一致；未清理实施工作树。
