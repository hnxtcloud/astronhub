# Suite 分阶段退出实施记录

Status: implemented
Verification: partial (Docker-dependent validation unavailable)
Branch: codex/plugin-update-integration
Started: 2026-11-06

## Authorization and scope

用户在 Q1–Q7 确认后，于 2026-11-06 明确调用 implement 技能。实施依据为 [规格](../../docs/suite-retirement-plan.md) 和 [ADR-0005](../../docs/adr/0005-consolidate-skill-bundles-under-plugins.md)。决策图 resolved 只表示决定完整，不代表运行验收通过。

## Implementation

- 领域与 app 服务拒绝新建、编辑、新版本和重开；Bundle 预览与新确认停止。旧确认的幂等重放按持久化资格校验，后台执行和重试独立验证资格。
- V85 为版本及 Bundle 操作新增收尾资格，回填切换时待审版本、已确认操作和其已生成草稿。新行默认没有资格。旧 Bundle 通过绑定操作创建草稿，普通创建接口不能获得例外资格。
- 审核提交、私有发布和审批使用同一退出策略；拒绝或撤回后不恢复新创作。读取、安装、下架及原有成员状态校验继续使用已有流程。为保留历史，退出模式不开放硬删除。
- 前端移除 Suite 推广和新建入口，保留历史路由与停用提示；新建、编辑和新版本地址不再渲染创作表单。取消或要求重预览的任务引导到 Plugin，不重启 Suite 创作。
- 独立 Skill 和插件包组件继续独立，现有三宿主包导入保留；新增 Claude 纯 Skill 插件正向校验覆盖。
- CLI 代码和 inventory 格式保持兼容，服务端继续声明原有 Suite 能力。

## Verification seams

沿用规格验收边界：领域公开命令覆盖停写与资格例外；真实 Spring MVC 服务链覆盖 API 两别名和管理员拒绝；迁移 SQL 覆盖资格回填；React 页面交互覆盖旧路由与入口；CLI 既有安装/卸载测试覆盖共享成员和 inventory。H2 不代表生产 PostgreSQL/Flyway 验收，静态包校验不代表宿主安装。

## Progress and evidence

- 初始红测 [domain](red-domain.log)：新 Suite 未被停用策略拒绝；随后 [green](green-domain.log) 通过。
- 页面红测 [web](red-web.log)：旧目录仍有创建按钮；随后 [green](green-web.log) 通过。
- [定向后端](focused-backend-2.log)：80 项 app 测试通过，包含 HTTP 门禁、迁移和纯 Skill 包；同轮领域测试通过。
- [定向页面](focused-web-2.log)：9 文件、80 项测试通过。
- [前端全量](full-web.log)：241 文件、984 项测试通过。
- [类型检查](typecheck-final.log)：通过。
- [最终定向后端](focused-backend-final.log)：领域 29 项、app 63 项，共 92 项通过，0 failures/errors。
- [后端全量](full-backend-final.log)：7 个模块共记录 2431 项，2411 通过、8 跳过、12 errors，0 assertion failures。12 个 errors 均为 Testcontainers 缺少 Docker 导致初始化失败；整次命令未通过，不作为全绿验收。
- [CLI 全量](full-cli.log)：50 文件、568 项通过，0 失败。
- [Lint](lint-final.log)：通过。
- [浏览器](e2e-ui.log)：桌面 1280px 与移动 390px 两项通过，验证旧目录提示、插件入口、创建入口移除及布局。它们是前端浏览器检查，不代表完整带后端业务的端到端验收。
- [Staging 前置检查](staging-preflight.log)：当前没有 Docker CLI/engine，`make staging` 的容器构建、烟测和生产 PostgreSQL/Flyway 未执行。
- [两轴代码审查](review.md)：Standards 0 项、Spec 0 项可执行问题；完整旧 Bundle 跨进程重启验收的限制单独记录。
- 暂存区只包含本次任务变化；已比较本次范围外 70 个已修改的跟踪文件，内容与任务开始时一致。共享文件通过三方合并仅暂存本次变化，其他用户改动留在工作区。
- API 请求/响应结构未变化，继续使用既有 allowedActions 和原有路由，未手工修改或纳入其他工作中的生成 schema。

原始日志为当前工作区的本地证据，不作为功能源码提交。首次定向后端检查中缺 Docker 的 Testcontainers 错误及未更新的测试桩失败保留在 `focused-backend-1.log`；测试桩已修正并由下一轮通过验证，环境错误未冒充通过。初次全量检查还发现新增测试夹具的 clock 作用域编译错误，修正后重新完整运行；该失败记录保留在 `full-backend-make.log`。

## Deployment boundary

使用停机升级：停止旧写入实例后执行 V85，再启动新版本。部署、生产数据迁移和真实三宿主安装不在此次执行中冒充完成；完整操作约束见规格的部署切换节。
