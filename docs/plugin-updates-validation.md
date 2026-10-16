# 插件管理与应用更新验收记录

日期：2026-10-16。集成分支：`codex/plugin-update-integration`。规格：[插件管理](27-plugin-management-implementation-plan.md)、[软件更新](28-software-release-update-channels.md)。完整逐票证据见[实施索引](../.scratch/plugin-update/implementation.md)和[集成验收票](../.scratch/plugin-update/tickets/11-integrate-and-verify.md)。

## 集成验证

最终功能修复为 `f69384ad`，合并为 `b4609328`；全量回归发现的空迁移和测试样本问题另由 `b9cb83b4` 修正，合并为 `ed497ae2`。Git 临时对象计量竞态另由 `6c2e6936` 修复并以 `ba9739e1` 合并。统一验证固定在包含这些合并和验收认领的 `898384ab`；后续文档更新不改变产品代码。

| 检查 | 结果与边界 |
| --- | --- |
| Standards 审查 | 4 项规范问题和 1 项重复路径校验建议已处理；原审查者定向复审无遗留问题 |
| Spec 审查 | NSIS 显式确认和白泽说明资产完整性两项已修复；原审查者复审无遗留问题；Git 容量扫描竞态修复再次经两轴复核 |
| 最终修复定向回归 | 后端 50、CLI 4、页面 9 项通过；实际 Springdoc 453 paths / 493 schemas，生成类型、完整类型检查和定向 lint 通过 |
| PostgreSQL 18.6 / 实际 Flyway | 全新库 84 个连续迁移至 V84；V83 再升级 V84；两条路径 validate 通过且第二次 migrate 均为 0。独立临时实例已停止；Flyway 提示 PG18 超出其官方验证版本，不代替 PG16 生产验收；最终提交的 84 份 SQL 摘要与该迁移证明逐项一致 |
| 完整后端回归 | 2408 项（2406 通过、2 跳过），381 suites，Maven exit0；12 个依赖 Docker 的既有测试类未执行。先前两轮各 2402 项：首轮 3 失败、次轮 1 错误，均有 2 项环境跳过；原始报告保留。三项迁移/样本问题经定向 22 项修复，Git 生产竞态经确定性红绿及实际 Git/HTTP/恢复 25 项修复，不拼接为单轮全绿 |
| 完整前端与构建 | 237 文件 / 967 测试全部通过；TypeScript、全量 ESLint、tsc -b 和 Vite 生产构建通过；实际契约生成 SDK，Git 无内容差异（仅行尾刷新） |
| 配置与反向代理 | 子路径路由、真实 Nginx 57 项和机器发版 CLI 4 项通过；代理准入探针未传大 body。发布配置测试在 line75 因缺少 Docker 停止，整套未通过；未执行 Docker staging 或 Helm |

后端两项跳过分别为缺少 `REDIS_CLUSTER_TEST_NODES` 的 Redis 集群测试，以及未启用 `astronhub.git.native-fixture` 的可选原生夹具导出。原生 Git 安装另有下述独立证据；不能将其算作本轮跳过测试通过。

## 已完成的原生边界验证

- Pi 1.2.0：实际发布组件向无上游的本地 Verdaccio 发布，隔离安装固定版本；原始 TGZ、名称、版本和安装后的 Skill 字节一致。
- Claude Code 2.1.273、Codex 0.162.1：实际 Git 发布组件生成各自索引，经隔离 HTTPS 市场安装 1.0.0，再发布和更新到 1.1.0；清单和 Skill 字节一致，旧 commit 可读。
- 原始 Intelli Dev 两个更新模块：根路径与 `/hub` 均接受 GitLab tags/raw，实际 electron-updater 6.8.9 验证完整下载摘要、HEAD、单区间和 multipart。Generic 与白泽原始解析器也已接受真实服务端响应。

上述探针使用惰性样本和独立配置，没有启动插件代码或执行应用安装器；各测试服务已停止。它们证明对应发布、安装或传输边界，不代表任意插件或安装包都可运行。

后续独立 E2E 的任务、环境要求和交接入口见[E2E 执行计划](plugin-updates-e2e-plan.md)及[Wayfinder](../.scratch/plugin-update-e2e/map.md)；新计划尚未执行，不能转用上表结论替它标记通过。

## 部署前仍需验收

- 用户真实内网 Git/npm、发布凭据与企业 CA；服务器和客户端应分别具备访问条件。
- PostgreSQL 16、Docker staging、实际 S3/MinIO、Helm 渲染及生产并发环境。
- Windows NSIS/macOS 签名和原生安装、完整差分及全量回退。NSIS 声明是管理员对格式的显式确认，服务端不自动检测安装器家族或验签。
- 已安装客户端首次迁移至新来源。Intelli Dev 需新构建更改来源，或由运维对原域名配置代理；白泽读取来源不赋予其无人值守安装能力。

操作入口与配置示例见[插件管理指南](skillhub/guide/plugin-management.md)、[软件更新指南](skillhub/guide/software-updates.md)和[机器发版说明](software-machine-publishing.md)。本轮未修改两个客户端仓库，也未推送、创建 PR 或部署。
