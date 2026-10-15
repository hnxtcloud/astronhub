# 发布恢复、重试与渠道撤回

**What to build:** 持久化领取和恢复发布任务，只重试失败渠道；管理员显式渠道治理。

**Blocked by:** 03,04,05

**Status:** done

**Assignee:** plugin_ticket02 / codex/plugin-publication-recovery

**Migration reservation:** V82

**Spec coverage:** P7

- [x] 成功渠道保留；所需渠道齐全才公开；不确定结果先核对；并发互斥
- [x] 普通下架不删除 npm；已有外部分发明确显示；审计和恢复故障验证

## Evidence

完整工作分支 tip `3d74be0d`（代码/契约 `236a7af9`，含最新集成 `25ecf5c9`）以 `582e8317` 合入，合并代理已关闭本票。

- 最终真实 HTTP/实际导出 13/13 通过：Git HTTP 6、恢复 5、真实 TCP 心跳 1、Springdoc 1，零失败/错误/跳过；保留原目标撤回、持久计划/分页和 npm 不支持披露。此前 43 项轮次仅 42 项通过，其 H2 锁失败修正后最终重跑通过，不把旧轮整体标记成功。
- 最终实际导出 453 paths / 493 schemas，SDK 重新生成零漂移，SHA256 `9EC99916D9C2C8FAD90F5FC3D035AF1CD80915DF63995AFB359D3E3FD74B6B10`。合并代理核查实际文件及生成日志，未手改 schema。
- 串行完整类型检查、UI 2/2、定向 ESLint 与 `git diff --check` 通过；早轮 worker 启动超时与不存在 tsconfig 的命令未计通过。完整外部证据见 `C:/Users/adolbook/.codex/worktrees/plugin-update-notes/ticket06.md`。
- 合并无冲突，90 个用户原有文件 SHA256 与合并前及票 02 清单一致。未额外构建、推送、建 PR 或清理工作树。票 11 解除阻塞但未关闭；生产 PostgreSQL/Flyway、Docker staging、真实部署和原生运行验收继续由票 11 分别记录。
