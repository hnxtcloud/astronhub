# 插件下载、历史版本与组件治理

**What to build:** 用户获取有权访问的固定版本包和安装说明；管理者隐藏/归档/下架；显式关联独立组件。

**Blocked by:** 01,02

**Status:** done

**Assignee:** plugin_ticket02 / codex/plugin-download-governance

**Migration reservation:** V78

**Spec coverage:** P4

- [x] 受限组件不可借链接泄漏；受限插件不能进入原生公共渠道
- [x] 文件浏览安全；下架与历史分发记录展示；下载安装审计

## Evidence

实现分支 `codex/plugin-download-governance`，功能提交 `f523e845`、`59db5713`；最终 tip `0974d2c1` 已含最新集成 `c2215031`，以 `fc85802e` 合入并由合并代理关闭。

- 新增固定已发布版本详情、历史、原始 ZIP/TGZ 下载、清单内文件列表及纯文本预览。预览校验完整归档摘要，拒绝穿越、链接、二进制和超过 256 KiB 的文本；不执行脚本或渲染 HTML。
- 公共元信息允许匿名读取；下载、文件预览和安装说明鉴权。私有、命名空间内插件每次重新检查实时权限；旧所有者离开命名空间后不再保有受限访问权。
- 隐藏/显示、归档/恢复、可见性修改和版本下架均要求权限及原因并记录审计。未完成审批发布任务阻止可见性修改，保留审批快照。当前版本按持久审批任务 ID、release ID 选取，迟到完成不回退，下架重新选择剩余已发布版本。
- 组件链接仅保存固定 Skill version/MCP revision 的非所有权引用；每次按目标独立权限及生命周期过滤，受限目标不返回 ID/名称/占位计数。PLUGIN_ADMIN 不提升为 MCP/Skill 管理者，解除关联不修改目标。
- 门户 `/plugins/{namespace}/{slug}` 支持版本选择、原包下载、安全文件浏览和审计化安装说明；管理页加入治理原因、下架历史、关联组件及安全检查。复用 04 的渠道 ledger 展示外部分发回执和重试记录。明确警示外部分发副本不能被目录治理自动删除。
- 本地 Claude/Codex/Pi 配方始终标记 `runtimeVerified=false`；公共 Pi 有匹配名称、版本、摘要的成功 npm 回执时生成固定内网源的 POSIX / PowerShell 命令，受限插件仍使用本地配方。不改用户设置、不访问公网、不执行安装。Git 原生指引继续由 05 补充。

验证（2026-10-15）：

- Maven `-pl skillhub-app -am` 联合运行 `Plugin*,OfflinePlugin*,ClaudePlugin*,CapabilityCatalogIntegrationTest,OpenApiContractExportTest`：45 项通过（domain 5 + app 40），包括新增 7 项真实 HTTP 流程、安全文件、TGZ、角色隔离、审批快照、访问撤销、乱序发布和下架；01/02/04 回归一并通过。
- 使用真实 Springdoc 导出并运行 `openapi-typescript` 生成完整合约，未手工编辑生成类型。
- UI threads 单 worker 最终 5 文件 13 项通过（含目录、管理、npm、路由与新增访问/治理操作）；离线导入文件单独重跑 1 项通过。此前 worker 启动超时属于未执行的环境错误，未当作通过证据。
- 最终 TypeScript `tsc --noEmit`、相关变更 ESLint（零警告）和 `git diff --check` 均通过，日志路径记录于外部 `ticket03.md`。

本机没有 Docker CLI，未执行 PostgreSQL/Flyway + 容器 staging，也未运行真实内网 Git/npm 目标或宿主安装。回执投影使用明确持久夹具验证，不声称该测试完成了原生渠道发布；真实 npm 及隔离 Pi 安装证据沿用 04 的独立验收记录，部署与原生验证继续由 11 汇总。

合并代理核查实际最终联合日志 45 项通过（domain 5 + app 40，含 7 项新 HTTP 与真实 Springdoc）；UI 最终 13 项及离线导入独立 1 项均通过，初始 worker 启动超时轮未计通过。最终类型检查和定向零警告 ESLint 通过。合并无冲突，未手改联合 03/04/08 生成契约，`git diff --check` 通过；用户原有 90 个文件 SHA256 与合并前及票 02 清单一致。票 06 仍等待票 05。
