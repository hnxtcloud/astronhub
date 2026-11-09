# 统一发布实施记录

Status: implemented-with-environment-limits
Date: 2026-11-09

用户明确调用 implement，按 [规格入口](spec.md) 实施；决策完成与代码完成分别记录。

## 工作区

- 当前分支：codex/plugin-update-integration。
- 开始时已有大量其他工作的未提交修改；本次基线保存在 `.dev/unified-publication/baseline/`，提交时只纳入本任务变更。

## 执行顺序

1. 持久化任务 API、所有权、并发控制、恢复与真实领域执行。
2. 材料识别与各领域流程编排，保留校验、审核、审计及分发规则。
3. 统一发现、发布、我的发布和兼容入口。
4. 公开边界测试、类型检查、全量回归、界面验证、双轴审查及当前分支提交。

## 验证

### 交付

- 统一发现包含软件；顶部与页脚日常搜索进入统一发现，发布与我的发布共用入口。
- 材料识别、核对、持久化任务及自动保存；提供分块恢复、所有权校验、修订冲突、当前权限复核、失败续跑、替换材料和取消。
- Skill 复用原领域发布及扫描审核流程，已有目标需要确认；插件按勾选候选导入并提交，保留待审核状态；软件串联应用、版本、上传、检查、确认和发布。
- npm 仓库在提交前选择，分发结果保留既有 Git/npm 面板；旧管理链接保留目标上下文，详情使用动作连接实际安装或下载控件。
- MCP 保留既有目录和配置能力；独立 MCP 发布、远程导入器及 Suite 新发布不在本期范围。

### 测试证据

2026-11-09，日志位于本地 `.dev/unified-publication/`，未将日志、凭证或验收数据加入提交。

- TDD 定向循环覆盖任务保存/所有权/修订冲突、Skill、插件、软件、分块重放、会话边界、权限撤销与材料替换；最终工作区定向集成 15 项通过（`review-fixes-green.log`）。
- `make test-backend-app` 在 Windows 构建并发期间受打包产物影响中断；改为串行 Windows Maven wrapper 全量执行：应用模块 1284 项，0 断言失败、12 项 Docker 环境错误、8 项跳过，其他模块通过（`backend-full-windows.log`）。12 项均为 Testcontainers 启动失败；未将环境错误视为通过。
- `pnpm test` 全量 242 个文件、987 项：984 项通过，3 项旧导航断言失败；更新断言后，4 个相关文件 14 项通过。后续即时离开/恢复草稿测试通过（`frontend-full.log`、`frontend-final-focused.log`、`editor-final.log`）。
- `make typecheck-web`、TypeScript 项目构建检查、`make lint-web` 通过。
- `make generate-api` 已从重建后的真实服务重新生成 OpenAPI 类型。
- `scripts/publication-smoke.py` 在真实 PostgreSQL、会话/CSRF 和对象存储链路通过：上传重放、持久化恢复、发布与取消保留内容（`operator-smoke.log`）。
- 浏览器场景分轮通过：主页搜索、匿名发布跳转、390px 主页、统一发现、Skill 即时刷新/提交/我的发布、插件待审核/旧管理链接、软件完整发布/详情/统一检索。最后插件与软件两项稳定复验通过（`browser-domain.log`）；Skill 与发现通过见 `browser-verified.log`，主页三个场景通过见 `browser-e2e-final.log`。早期运行中的定位、等待和登录限流失败保留在日志中，未隐藏失败。
- 桌面与 390px 移动端实际截图已目视检查，无横向溢出；Impeccable detector 执行一次，结果为空列表。
- 为防止提交依赖其他未提交工作，以起始 HEAD 加本次增量构造独立快照：后端定向集成 13 项与认证策略 33 项通过，前端 6 个文件 21 项通过，类型检查通过（`backend-standalone.log`、`frontend-standalone.log`、`typecheck-standalone.log`）。

### 独立审查

- Standards：初审发现任务投影层边界、两条上传入口策略差异及 smoke 覆盖缺口。已提取 `PublicationTaskQueryRepository`、统一大小/当前角色策略、保留可见性并补充部署 smoke。复查无严重剩余项；仍有少量材料最终附加逻辑重复，未阻断交付。
- Spec：初审发现已有 Skill 目标确认、快速离开草稿恢复、提交前 npm 仓库选择及识别失败说明不足。均已修正，独立复查无新增阻断项。

### 环境与运行限制

- 此机器未安装 Docker；`make staging` 在完成 JAR 构建后停止于 `docker build`。容器部署和上述 12 项 PostgreSQL Testcontainers 测试仍需 Docker 环境验证。
- 软件自动检查仍依赖原有检查服务；浏览器覆盖手工 ZIP 路径，检查失败与元数据修复继续使用已有领域规则。
- 页面打开时推进任务；离开后保留最后确认的步骤，返回点击继续。没有新增后台常驻发布工作器。
- 中断的浏览器上传需要重新选择同一文件以恢复；不会把未完成材料标为已保存。未确认的文本在当前浏览器标签页本地暂存，服务器草稿仍为跨登录恢复依据。
- 本地服务已重启并应用 V86 迁移。验收创建带唯一前缀的内容，未重置共享数据。
