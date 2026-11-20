# MCP 发布侧实现(doc 25 Slice 5/6 落地)

本文记录 MCP 目录发布侧(条目/修订/连接模板的创建、审核、治理)的实现,
权威状态模型见 `docs/25-astronhub-capability-platform-design.md`。消费侧
(统一目录搜索、详情页、连接模板复制)此前已上线,发布条目经审核后自动
出现在既有读路径中,读侧代码零改动。

## 状态机与不变量

- 修订状态:`DRAFT → PENDING_REVIEW → PUBLISHED / REJECTED`;无 YANKED。
- 已发布修订不可变;再次编辑产生新草稿修订,发布期间旧版持续可见。
- 每个条目同时至多存在一个活跃草稿(`DRAFT`/`PENDING_REVIEW`)。
- 可见性不绕过审核:`PRIVATE/NAMESPACE_ONLY` 条目同样必须审核后发布。
- 密钥变量只存占位符:secret 变量禁止 `defaultValue`,HTTP 端点模板必须
  以 `${NAME}` 形式引用;STDIO 密钥经生成的 env 映射在客户端绑定,
  命令模板不要求占位符。凭据值从不入库、不打日志、不进索引。

## REST 路由(`/api/v1/mcp-management`)

| 方法与路径 | 说明 |
|---|---|
| `GET /entries?page&size&pendingReview` | 管理列表;`pendingReview=true` 需审核身份 |
| `POST /entries` | 创建条目+首个修订(可选 `serverJson` 导入) |
| `GET /entries/{id}` | 条目详情(含修订历史与权限标记) |
| `PATCH /entries/{id}` | 更新 displayName/summary |
| `POST /entries/{id}/revisions` | 新建草稿修订(profiles 和/或 serverJson) |
| `GET /revisions/{id}` / `PUT /revisions/{id}` / `DELETE /revisions/{id}` | 修订读/改/删(改删仅 DRAFT) |
| `POST /revisions/{id}/submit` / `withdraw` | 提交审核 / 撤回 |
| `POST /revisions/{id}/approve` / `reject` | 审核通过(重指 latestRevisionId)/ 驳回(notes 必填) |
| `POST /revisions/{id}/direct-publish` | SUPER_ADMIN 直发 |
| `POST /entries/{id}/governance` | HIDE/SHOW/ARCHIVE/RESTORE/VISIBILITY,reason 必填 |

路由在 `RouteSecurityPolicyRegistry` 注册为 authenticated 且 SESSION_ONLY
(仅会话 Cookie,禁 API token),与 plugin-management 一致。

## 权限矩阵

| 操作 | MEMBER(成员) | 命名空间 ADMIN/OWNER | MCP_ADMIN | SUPER_ADMIN |
|---|---|---|---|---|
| 创建条目/草稿、提交、撤回 | ✓(本命名空间) | ✓ | ✓ | ✓ |
| 编辑条目元数据、维护草稿 | owner-with-membership | ✓ | ✓ | ✓ |
| 审核(approve/reject,含待审收件箱) | ✗ | ✓ | ✓ | ✓ |
| 直发 direct-publish | ✗ | ✗ | ✗ | ✓ |
| 治理 HIDE/SHOW/ARCHIVE/RESTORE | ✗ | ✓(可维护者) | ✓ | ✓ |
| 调整可见性 VISIBILITY | owner-with-membership | ✓ | ✗ | ✓ |

`McpActor`(domain 层)集中判定;视图携带 `canManage/canReview/canGovern`,
前端据此显隐操作。

## server.json 导入字段映射(`ServerJsonImportParser`)

| server.json 字段 | 映射结果 |
|---|---|
| `name`/`title`/`description` | displayName/summary 预填,原始文档全文存 `source_snapshot` |
| `remotes[type=streamable-http/http]` | `STREAMABLE_HTTP` 端点模板 |
| `remotes[type=sse]` | `LEGACY_HTTP_SSE` 端点模板 |
| `remotes[其他类型]` | 跳过(可后补 CUSTOM) |
| `packages[registry_type=npm]` | STDIO:`npx -y id@version` |
| `packages[registry_type=pypi]` | STDIO:`uvx id@version` |
| `package_arguments` | 位置/命名参数展开为 arguments |
| `environment_variables` 无 value | 变量(required 按声明,secret=true) |
| 凭据形名字 + 字面 value(key/token/secret/password/credential) | 仍建 secret 变量,**值丢弃**,validationHint 注明 |
| 非凭据字面 value | 非密钥 defaultValue |
| URL 中 `${VAR}` 占位 | 模板变量(凭据形名字标 secret) |

解析失败返回 `error.mcp.importInvalid`(400);原文始终按 JSON 快照保存,
`source_kind=SERVER_JSON`,手工条目为 `MANUAL`。

## 前端

- 管理页 `/dashboard/mcp-servers`(requireAuth,侧边栏「MCP 服务」):
  创建表单(namespace/slug/visibility/server.json 粘贴或上传)、条目列表、
  修订面板(模板编辑器 + `buildMcpClientConfiguration` 实时预览)、
  提交/撤回/审核/直发/治理按视图权限标记显隐。hooks 见
  `web/src/features/mcp/mcp-management-api.ts`。
- 插件详情页新增「MCP 组件清单」:解析 distribution `inventoryJson` 的
  `resources.mcpServers`(路径/内联)与 `files` 中的 `.mcp.json`/`mcp.json`,
  让用户在安装前看到本机会获得哪些 MCP 服务;无 MCP 组件时整段不渲染。

## 测试与文档

- 域单测:`McpEntryRevisionTest`、`McpCatalogWorkflowTest`(状态机、
  校验矩阵、一人一草稿、直发与治理)。
- 解析单测:`ServerJsonImportParserTest`(映射、凭据值永不持久化、坏文档)。
- 集成测试:`McpManagementIntegrationTest`(权限矩阵、slug 冲突 409、
  生命周期、旧版持续可见、待审收件箱、治理)。
- `web/e2e/capabilities-zh.spec.ts` 的 MCP 用例仍只覆盖读路径;M01–M03
  fixtures 说明已随本变更更新,写路径由后端集成测试保证。
