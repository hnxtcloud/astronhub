# 受控机器发版入口与运维说明

**What to build:** 提供单独受控的机器认证上传/发布入口和完整产物上传工具，记录审计与失败。

**Blocked by:** 07,08,09

**Status:** claimed

**Assignee:** software_ticket07_resume

**Spec coverage:** U6（机器入口部分）

- [x] 不把 session-only 管理 API 当 token API；机器身份/应用权限独立且凭据不泄露
- [x] 上传/确认/发布完整流程；客户端首次迁移限制明确；已有应用仓库只读

## Evidence

实现于 `codex/software-machine-publishing`，以 `f7090ffe` 集成提交为起点，迁移 V83。合并代理集成后关闭本票。

- 独立 `/api/v1/software-publishing/apps/{appId}` 入口；会话软件管理员创建/撤销精确应用/token 授权，原始 token 仅签发响应返回。每次请求校验 live token、当前 owner、应用绑定和当前软件管理员职责；原管理 API 保持 session-only。所有嵌套资源在操作前核对应用归属。
- 复用原始分片、配额、检测、确认和发布流程，ZIP/TAR 需明确人工元数据。工具支持完整清单、状态续传、服务端分片摘要核对、原始资产完整性和已发布不可变快照重试；显式 `--publish`、`--activate-channel` 才执行对应操作。客户端仓库未修改、未启动安装器。
- 红绿证据：身份传递缺失、授权签发缺失、机器 POST 缺失、ZIP 人工确认、raw logger 缓冲、Nginx 413、CLI userinfo 与临时文件碰撞负例均先失败后修复。
- `.scratch-machine-complete-green.log`：auth 62、logger 11、真实嵌入式 Tomcat HTTP 7，合计 80，通过。包含自选 scope 无绑定拒绝、跨应用 ID、session 拒绝、过期/撤销/停用/职责移除、分片重复/完成重复、基础检测失败、容量入场限制、明确人工元数据、完整快照及真实 Python 工具首次发版和重试。
- token 使用时间采用定点 UPDATE，仅更新 lastUsedAt；实际测试证明 stale token 对象不能恢复已撤销凭据或改写 scope。H2 JSONB fixture 使用 FORMAT JSON，生产 scope 解析未放宽。
- `.scratch-machine-cli-boundary-green.log`：2 个真实 CLI 子进程负例通过，userinfo 在网络前拒绝；状态检查点不会覆盖碰巧命名为 state.json.tmp 的原始资产；输出/状态不含凭据。
- `.scratch-machine-ui-green.log`：签发、一次性显示、清除和撤销 1 项通过；`.scratch-machine-admin-ui-green.log`：原管理页面 5 项通过。
- `.scratch-machine-proxy-green.log`：真实 Nginx `/`、`/hub/`、`/hub.v1/` 共 57 检查通过；机器 raw assets/chunks 才开放流式 body，普通 JSON 仍受 100 MiB 限制。探针没有传大 body，不代表大文件传输验收。
- 实际 Springdoc 导出后生成 SDK；当前导出 446 paths / 480 schemas，机器/grant 21 paths，两个 raw binary requestBody 均 required。最终合并后的契约与回归另记集成票。

真实 PostgreSQL/Flyway、S3、Docker staging、真实签名原生安装、完整差分及客户端迁移仍属于 11 的独立验收，不由本票的合成合同测试替代。

### 合并后复核

- 功能提交 `62a9848d`；`84710a47` 已合并集成 `a743c704`，保留真实 Intelli 原生合同和 Git 运行时部署修复。唯一 docs/28 冲突已语义合并。
- `.scratch-machine-merged-final.log`：81 项通过（auth 62、logger 11、真实机器 HTTP/工具 7、实导 OpenAPI 1），无失败/错误/跳过。实际 OpenAPI 仍为 446 paths / 480 schemas，重新生成 SDK 后无额外差异。
- `.scratch-machine-ui-final.log`：新凭据 UI 1 项再次通过；原 admin UI 5 项在 `.scratch-machine-admin-ui-green.log`。`.scratch-machine-cli-boundary-final.log` 2 项和 `.scratch-machine-proxy-final.log` 57 项再次通过。
- 范围内 ESLint 与初次完整 TypeScript typecheck 均通过；最终合并后完整 TypeScript typecheck 也通过（`.scratch-machine-merged-typecheck.log`）。用户指南已同步机器凭据、完整工具和流式代理操作。
