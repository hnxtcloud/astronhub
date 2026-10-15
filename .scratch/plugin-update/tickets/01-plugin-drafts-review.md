# 插件草稿、版本与审核管理闭环

**What to build:** 成员创建项目/草稿，通过第一条 Claude Code 单插件 ZIP 路径导入真实包、取得静态检查证据并提交；管理员查看和审批。批准启动独立发布任务，未完成渠道前不对目录公开。首条完整验收采用受限可见性和星枢托管渠道，公开 Git/npm 渠道由后续票补齐。

**Blocked by:** None

**Status:** done

**Assignee:** plugin_ticket01 / codex/plugin-drafts-review

**Spec coverage:** P1、P4，以及 P2/P3 的首条单包导入路径

- [x] 命名空间和所有者权限、冻结/归档阻断写入；平台审核角色隔离
- [x] 已发布版本不可替换，审批证据与渠道任务分离且审计；管理页可操作
- [x] 真实单包安全导入，未验证的发行物不能提交；不接受客户端自报验证结果或任意存储 key

## Evidence

首条 Claude ZIP 与受限托管纵向路径实现并合入集成；整个插件规格仍由后续任务验收。统一生成契约及接入生成类型保留在集成票 11。

### Implementation — 2026-10-15

- 已同步 `codex/plugin-update-integration` 至 `28f13c06`，包含软件票的 V75；本票使用 V74，未修改其他应用仓库。
- domain 提供命名空间/项目权限、草稿不可变规则、提交、独立人工审核、持久发布任务；发布前不更新目录指针。PLUGIN_ADMIN 不因审核角色获得他人项目维护权限，SUPER_ADMIN 也不能绕过结构检查或冻结/归档限制。
- app 的 Claude ZIP 原生适配器有界读取原始请求体，检查中心目录/文件类型、路径、链接、大小写重复、文件/目录冲突、展开量和条目数，拒绝路径别名及未知扩展字段。保存真实清单、文件清单和 SHA256，不执行代码，不接收客户端静态证据或存储 key。静态结果保持 MANIFEST_VALIDATED，安全扫描保持 NOT_SCANNED，运行测试另行记录。
- `plugins/archives/` 对象前缀与 Skill 分离。托管后台任务重新检查实际存储 SHA256；缺失/损坏持久记失败，恢复后可重试。PRIVATE/NAMESPACE_ONLY 所需托管渠道完成后发布；PUBLIC 仍等待 Git/npm 渠道，不制造外部成功。
- 管理 API 为 `/api/v1/plugin-management/**`，会话与 CSRF 规则沿用平台；历史包按固定 release ID 鉴权下载。归档查看/下载、创建、编辑、导入、提交、审批和托管执行均记录审计。
- `/dashboard/plugins` 提供项目目录信息、版本、真实 ZIP 导入、检查证据、提交/审核、发布进度/失败重试及原始包下载。新增中/英/俄文案，后台规则不依赖按钮可见性。
- 操作、API、配置、格式限制与后续范围见 [管理指南](../../../docs/skillhub/guide/plugin-management.md)，已加入用户指南侧栏。

### Verification

日志均在仓库外 `C:/Users/adolbook/.codex/worktrees/plugin-update-notes/`，未纳入源码提交。

- TDD 首条越权规则先红后绿：`ticket01-red1.log`、`ticket01-green1.log`。
- 审批与发布分离先红后绿：`ticket01-red2.log`、`ticket01-green2.log`。
- 真实 ZIP 验证先红后绿：`ticket01-red3.log`、`ticket01-green3.log`，随后将归档/原生 JSON 实现及测试移至 app 外层。
- HTTP 首条完整流程先红后实现。`ticket01-http-native-lock.log` 中领域 2、HTTP 4、ZIP 7 共 13 条通过。使用仓库既有 native FOR UPDATE 方式；测试专用 dialect 已移除。
- 同步最新集成后，`ticket01-final-reactor.log` 记录领域 2、既有项目约束 1、路由政策 33、HTTP 4 全部通过；该轮唯一失败是新补的父目录冲突红测，并非业务流回归。归档歧义的两个实际缺口在 `ticket01-ambiguity-red.log` 中重现，修复后 `ticket01-ambiguity-green.log` 的 ZIP 9 条全部通过。
- HTTP 验证空草稿及超级管理员未验证草稿禁止发布、普通成员禁止审批、审核角色隔离、冻结/归档写阻断、审批与渠道分离、PUBLIC 等待 Git、内容损坏失败/恢复重试、两次发布后历史 ZIP 字节一致和匿名/越权拒绝。
- 页面通过真实 fetch 边界的创建项目交互测试：`ticket01-ui-red.log` → `ticket01-ui-green.log`。全前端 `tsc --noEmit` 通过；新增插件 API/页面/测试的定向 ESLint 通过，最终日志 `ticket01-typecheck-final.log`、`ticket01-eslint-final.log`。`git diff --check` 通过。

### Integration follow-up and honest limits

- 合并代理核查上述日志及工作树后，以 `91e2962a` 合入 `codex/plugin-update-integration`。`git diff --check` 通过；合并前后 `CONTEXT.md` 与既有未跟踪文件共 90 个 SHA256 完全一致。本票勾选仅对应已验证的首条路径，不代表公开 Git/npm 和原生安装完成。

- 未手改 `schema.d.ts`。新端点需由集成代理统一导出 OpenAPI，并将 `features/plugin/plugin-api.ts` 中注明的临时严格类型接到生成类型；当前使用现有 fetch/CSRF 客户端，无 any。
- 本票只验收 Claude Code 单 ZIP 和受限托管路径。Codex/Pi、仓库批量导入、依赖管理、组件显式关联、Git/npm 配置及渠道恢复分别由后续票验收。
- 未运行真实内网 registry/Git 发布、原生宿主安装、进程重启/多实例 PostgreSQL 争用测试；H2 行为不代替生产数据库和 Flyway 迁移实测。
- 首条持久任务固定内容/可见性/所需渠道；真实 Git/npm 目标及凭据配置快照需在后续渠道票接入，不回写已完成历史记录。
- 未重复既有 90 分钟全量基线，未运行依赖 Docker 的 staging。已有基线缺 Docker 的 Testcontainers 错误见实施索引，不作为本票通过项。
