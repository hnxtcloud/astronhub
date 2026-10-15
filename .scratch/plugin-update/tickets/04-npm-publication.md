# 内网 npm 插件发布及依赖上传

**What to build:** 配置内网 npm 源，批准的 Pi 发行物由服务器发布；独立管理入口上传依赖 TGZ。

**Blocked by:** 01,02

**Status:** implemented-awaiting-integration

**Assignee:** npm_publication / codex/plugin-npm-publication

**Spec coverage:** P5

- [x] 原包名/版本保留；同内容复用、冲突拒绝；不运行 lifecycle scripts、不回退公网（真实本机无 uplink registry 和 wire 验证）
- [x] 发布与用户认证分离；凭据不输出；依赖不成为插件项目；实际本机 registry 集成证据

## Evidence

2026-10-15：实现允许内网源及部署凭据引用配置、不可变审批渠道快照/成功记录、原始 Pi TGZ 发布、独立依赖上传/确认及管理界面。V79。外部接口及恢复边界见 `C:/Users/adolbook/.codex/worktrees/plugin-update-notes/ticket04.md`。

- TDD 红绿记录：频道完成 API 编译失败→通过；registry API 缺失→通过；管理 API/公平查询缺失→通过。实际注册库为临时 loopback Verdaccio6.1.6，配置无 uplinks，已停止进程。
- `ticket04-final-native.log`：domain2 + app24 测试通过，包括实际 scoped 包发布、相同原字节复用、冲突拒绝、第二新版本发布后首版原字节仍可复用、认证失败、直接依赖可用/缺失；wire 覆盖不确定 PUT 核对、package publishConfig 不改写目标、跨 origin tarball 零请求。
- HTTP 覆盖会话/CSRF/RBAC、引用响应无发布秘密、独立依赖确认幂等/零插件项目、PUBLIC Pi 审批→固定目标→渠道成功后目录可见、21 个 BLOCKED 计划不阻塞第22个可执行任务。成功 HOSTED/NPM/GIT 状态合并与过期尝试令牌防覆盖有领域测试。
- 实际 Springdoc HTTP 导出生成 SDK；新增二进制上传契约；前端 TanStack Query/i18n、配置及草稿操作测试。最终 frontend/integration 日志及提交见外部记录。
- 边界：没有真实部署内网 registry 或 Pi 原生安装验收；Docker 缺失导致完整 staging 不可用，未重跑已知90分钟全基线。版本范围明确未解析，不声称完整离线可安装或运行通过。Git adapter05、完整尝试历史/心跳/显式重新规划/治理06继续独立实现。缺配置计划不能靠后续改默认源静默重定向。npm latest 是可变标签，不保证迟到发布时单调递增，用户指引固定原生版本。

最终提交：实现 `e24ee60c`，合入最新 integration `5401e574` 后 `5b41a680`；实际合并 Springdoc 契约导出及领域/HTTP 7 测试通过（`ticket04-merged-contract-flow.log`）。合并后 TypeScript、2 UI 操作、scoped lint 通过（`ticket04-merged-tsc.log` / `ticket04-merged-ui.log` / `ticket04-merged-lint.log`）。最终验收由集成合并者关闭；工作树无临时 fixture 或真实凭据。
