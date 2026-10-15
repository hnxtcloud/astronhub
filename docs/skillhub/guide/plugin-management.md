# 插件草稿、审核和托管发布

管理入口为控制台的「插件管理」（`/dashboard/plugins`）。首条可用流程支持根目录包含 `.claude-plugin/plugin.json` 的 Claude Code 单插件 ZIP；清单必须包含原生名称和与草稿一致的版本号。Codex、Pi、仓库批量导入和内网 Git/npm 渠道由后续实施票扩展。

## 导入和提交

1. 选择本人可提交的活动命名空间，创建插件项目。可见范围在创建时确定；此入口仅编辑目录展示名称和介绍，不改写包内文件。
2. 创建版本，填写版本说明。版本号在项目内唯一，原生包版本必须相同。
3. 选择原始 ZIP 并点击「导入并检查」。服务端验证实际字节、清单、资源引用、文件路径、重名、链接及展开预算，不执行插件、脚本或依赖安装。
4. 查看清单、文件列表和摘要后提交审核。没有真实静态检查证据的草稿不能提交，超级管理员也不能绕过此要求。

命名空间成员可创建本人项目；本人仍为成员时可维护自己的项目。命名空间 OWNER/ADMIN 可维护本命名空间项目，SUPER_ADMIN 可管理。冻结或归档命名空间、归档项目均阻断写入。

## 审核和发布

平台 PLUGIN_ADMIN、SUPER_ADMIN 可查看待审核项目、下载原始 ZIP 并审批。PLUGIN_ADMIN 仅凭审核角色不能代替项目维护者修改包。审核意见必填；如记录运行测试通过或失败，须附带测试环境和观察结果。未经运行测试时保持「未经运行测试」；静态清单验证不会自动变成安全扫描通过。

审批成功会创建独立的持久发布任务，审批接口不会直接发布版本。后台每 5 秒处理尚未执行的托管任务，重新核对存储对象的 SHA256；进程重启后仍可继续读取队列。文件缺失或损坏会留下失败状态，恢复原始文件后管理员可在页面重试。

- PRIVATE、NAMESPACE_ONLY 版本仅需星枢托管渠道完成，即可成为已发布版本。
- PUBLIC 版本还会等待所需 Git/npm 渠道。本阶段托管校验成功后仍显示等待外部渠道，不会提前对目录公开。
- 版本提交后不能替换原始包；需要修改包内容或增加宿主时创建新版本。审核和渠道执行记录保持分离。

目录展示可按权限读取已发布版本。原始文件下载始终要求会话登录并检查当前权限，只返回原有 ZIP；旧版本使用固定 release ID，不随项目当前版本指针变化。

## 管理 API

所有以下入口位于 `/api/v1/plugin-management`，要求会话登录；写入操作使用现有 CSRF cookie/header 机制，不接受客户端提供静态证据或存储 key。

| 操作 | 入口 |
| --- | --- |
| 项目列表、创建 | `GET/POST /projects` |
| 项目详情、目录信息编辑 | `GET/PATCH /projects/{id}` |
| 创建版本 | `POST /projects/{id}/releases` |
| 版本详情、草稿版本说明 | `GET/PATCH /releases/{id}` |
| 导入原始 ZIP | `POST /releases/{id}/archive`，`Content-Type: application/zip` |
| 管理员或维护者查看原始包 | `GET /releases/{id}/archive` |
| 提交、批准、驳回 | `POST /releases/{id}/submit`、`approve`、`reject` |
| 超级管理员直接批准草稿 | `POST /releases/{id}/direct-publish` |
| 托管渠道执行或重试 | `POST /releases/{id}/publish-hosted` |
| 已发布历史包下载 | `GET /releases/{id}/download?runtime=claude-code` |

项目列表支持 `page`、`size`（最多 100），审核者可使用 `pendingReview=true` 过滤尚未审批的提交。

## 部署参数和限制

| Spring 配置 | 默认值 | 用途 |
| --- | --- | --- |
| `skillhub.plugin.max-package-bytes` | `104857600` | 单插件原始 ZIP 最大 100 MiB |
| `skillhub.plugin.max-expanded-bytes` | `2147483648` | 展开内容累计 2 GiB，扫描不落地执行 |
| `skillhub.plugin.max-files` | `20000` | 归档条目总量，含目录 |
| `skillhub.plugin.hosted-worker-enabled` | `true` | 托管发布任务后台处理开关 |
| `skillhub.plugin.hosted-worker-delay-ms` | `5000` | 托管任务轮询间隔 |

单个原生 JSON 清单额外限制为 1 MiB。本阶段拒绝加密 ZIP、ZIP64、链接/特殊文件、不安全或跨平台歧义路径，以及仅大小写不同的重复条目、文件与目录冲突。ZIP 扩展元数据仅允许时间戳、NTFS 时间和 UID/GID，拒绝另行声明文件名或链接的扩展字段。ZIP 需以单插件目录为根，仓库包装目录由批量导入票处理。

导入走有界的原始请求流，不经过 multipart，因而不调整 Skill 的 multipart 或包业务限制。默认 Nginx `client_max_body_size 100M` 可容纳该原始请求；如修改插件包上限，须同步检查反向代理请求体限制。后续 500 MiB 仓库归档尚不在此入口支持范围。

本阶段使用 `plugins/archives/` 专用对象前缀。导入事务回滚会尝试清理新对象；对象存储失败时仍需关注清理告警。完整跨渠道恢复、仓库批量导入、组件显式关联、公共原生市场安装和真实内网宿主验收见后续实施票。
