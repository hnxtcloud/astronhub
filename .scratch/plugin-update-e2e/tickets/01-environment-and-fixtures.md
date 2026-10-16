# 环境、真实身份、样本与前缀助手

**Status:** ready-for-agent

**Assignee:** unassigned

**Result:** not-run

**Blocked by:** None

**Layer:** L1/L2/L3 预检

## 前置和上下文

先读 [执行计划](../../../docs/plugin-updates-e2e-plan.md)、[环境表](../environment.example.yaml)、[既有E2E](../../../docs/e2e.md)和 AGENTS.md。只盘点用户提供的隔离环境，不启动/重置无关实例；按已提供的启动方式准备本轮服务。

## 场景

- E01-1：记录实际 HEAD、工具/镜像版本、PG16/Redis/存储连接、Flyway 到 V84、Web/API 健康和真实 Nginx 入口；每个未提供资源登记 blocker，不把 PG18/H2 作为 PG16。
- E01-2：验证真实登录、cookie、CSRF、注销；后端 mock/direct/session-bootstrap 实际关闭；helper 禁止 mock 回退。分别建立作者 MEMBER、namespace OWNER/ADMIN、PLUGIN_ADMIN、SOFTWARE_ADMIN、SUPER_ADMIN、无关用户和全新匿名上下文。
- E01-3：生成带 runId 的合法 Claude/Codex ZIP、原始 Pi TGZ、仓库多候选、重复/冲突/路径穿越/链接/预算样本及软件原始完整产物；清单登记来源/格式/原始名字版本/bytes/SHA256/SHA512。不能把 sample-skill.zip 充当插件。
- E01-4：检查匹配 Chromium 可启动和非零用例收集；补新测试的前缀感知导航、API/CSRF 助手，验证 `/`、`/hub/`、`/hub.v1/` 不丢前缀。默认 smoke 未覆盖新业务，预先登记要新增的 spec/case。
- E01-5：保存 runId 资源账本、密钥引用和受限证据路径；先单worker，确认后台发布任务启用、Inspector/扫描器和外部 Git/npm 的可用层次。

## 产物与验收

产出无秘密环境记录、样本摘要、角色清单、专用 fixture/helper、新 spec 收集列表和各层 blocker。浏览器真正访问并查询实际状态；不能只检查端口打开。01 passed 的标准是：所有资源项均已盘点，L1最小服务、真实身份和核心样本/助手可用。L2/L3尚缺资源写为对应下游阻塞，不要求01完成这些层次的业务验收；若L1本身不可用，01为blocked。高级格式/原生样本缺失不可伪造，留给对应case登记blocked。

## Execution log

尚未执行。领取时记录完整SHA、环境/runId、开始时间；结束追加实际命令、结果、证据、缺陷和清理，不覆盖首轮失败。
