# 发布恢复、重试与渠道撤回

**What to build:** 持久化领取和恢复发布任务，只重试失败渠道；管理员显式渠道治理。

**Blocked by:** 03,04,05

**Status:** claimed

**Assignee:** plugin_ticket02 / codex/plugin-publication-recovery

**Migration reservation:** V82

**Spec coverage:** P7

- [x] 成功渠道保留；所需渠道齐全才公开；不确定结果先核对；并发互斥
- [x] 普通下架不删除 npm；已有外部分发明确显示；审计和恢复故障验证

## Evidence

Ready for merger at 236a7af9 (latest integration25ecf5c9 merged), clean worktree; status remains claimed until root merger closes. Final HTTP13/13: real TCP heartbeat, original-target Git withdrawal, durable plans/attempt pagination, npm unsupported disclosure; prior related run42/43 passed, its sole H2 lock failure was fixed and final rerun passed. Final actual Springdoc453 paths/493 schemas generated SDK with zero drift; full web tsc, UI2/2, scoped lint and diffcheck passed. Full evidence: C:/Users/adolbook/.codex/worktrees/plugin-update-notes/ticket06.md. No Docker/full staging/real PostgreSQL/native runtime acceptance on this host; root11 retains these boundaries.
