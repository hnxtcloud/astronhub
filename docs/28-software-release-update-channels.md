# AstronHub 软件发布和更新渠道设计

状态：兼容优先级和访问方式已确认；本设计为待实施方案。日期：2026-10-14。

实施授权：用户已要求同步文档和 Wayfinder 后开始实现。沿用 [本地决策索引](../.scratch/plugin-update/map.md) 和 [实施任务依赖图](../.scratch/plugin-update/implementation.md)，统一使用 `codex/plugin-update-integration`；真实应用仓库保持只读，客户端迁移和安装验收不隐含授权修改客户端。

## 目标与已确认决策

软件中心需要像发布页一样管理应用版本、发布说明和一组发行文件，并让应用更新器检索版本、读取元数据、下载准确文件后执行自己的安装流程。支持多个应用，不将白泽灵台的 JSON 格式固化为所有应用的通用协议。

用户确认优先提供 GitLab 读取兼容接口，以减少现有客户端改动。应用可单独开启内网匿名更新源；只开放完整发布的资产，上传、后台管理及未发布文件继续鉴权。插件的 Git/npm 原生市场继续按 [插件规格](27-plugin-management-implementation-plan.md) 实施，应用更新属于独立的软件中心能力。

客户端案例与原始证据：

- [白泽灵台](research/baizelingtai-pi-astronhub-updates.md)：单候选原始 JSON，Windows ZIP，同源匿名访问；当前安装由独立维护窗口完成。
- [Intelli Dev](research/intelli-dev-astronhub-updates.md)：GitLab tags 发现版本、raw 文件获取、electron-updater YAML/安装包/blockmap；已有安装执行器。

发布管理操作见[软件更新快照发布](skillhub/guide/software-updates.md)，其中明确原始文件集合、安装包确认、元数据校验、匿名开关和渠道目标的管理边界。读取协议和原生安装按后续实施票独立验收。

## 领域与分发边界

```text
Software Application
  -> Release（更新版本、tag、说明、发布时间）
       -> Installable Package（复用现有包修订）
       -> Release Asset（YAML、JSON、blockmap、签名、校验文件等）
  -> Update Channel（系统、架构、渠道、协议、发布目标）
       -> 兼容读取接口和固定资产地址
```

现有软件应用、版本、包、存储、容量和发布治理可以复用。需要新增发布资产和更新渠道，不能把 YAML/blockmap 等文件伪装为 EXE/ZIP，也不能重用 Skill/Plugin 生命周期。

保留现有展示版本原文；更新协议使用明确且经过验证的机器版本和 tag。渠道目标绑定不可变的完整发布快照，包含资产身份与摘要。普通软件目录的列表顺序和 release 内的推荐包不能作为跨版本更新选择规则。

发布资产记录 release、相对路径/文件名、用途、平台/架构、大小、摘要及不可变对象。安装包可关联已有 SoftwarePackageRevision，避免重复保存内容；辅助资产使用独立对象，仍计入软件容量。macOS 更新能力须补充目标平台及相应资产表达。

## 首批读取协议

| 协议 | 首批使用方 | 读取行为 |
| --- | --- | --- |
| GitLab 读取兼容 | Intelli Dev 原有客户端 | tags 数组、按 tag 获取 raw 文件，GET/HEAD/Range |
| electron-updater generic | 原生 generic 客户端和后续迁移客户端 | latest.yml/latest-mac.yml、原始安装包、blockmap |
| 白泽灵台 manifest v1 | 白泽灵台内网便携版 | latest.json、同源 ZIP、SHA256 和 bytes |

三者由同一 Release/Asset 数据投影，不创建相互独立且可漂移的发布记录。GitLab 兼容范围限定为更新读取，不实现仓库写操作、Issue、Pipeline 等 GitLab 产品能力。

### GitLab 兼容入口

为应用配置虚拟 project path，例如 `apps/intelli-dev`，映射到真实软件应用；不是在服务器建立同名 Git 仓库。

```text
GET /api/v4/projects/{encoded-project-path}/repository/tags
GET /api/v4/projects/{encoded-project-path}/repository/files/{encoded-file-path}/raw?ref={tag}
HEAD /api/v4/projects/{encoded-project-path}/repository/files/{encoded-file-path}/raw?ref={tag}
GET /{owner}/{repo}/-/raw/{tag}/{asset-path}   # 辅助兼容入口
```

tags 返回裸数组，name 为原生 tag，可提供 release.description 与 commit.message。仅列出已发布且更新分发启用的版本；不列出草稿、尚未齐全或已撤出的更新。按 project/ref/asset 精确解析原始文件，不接受任意存储或文件系统路径。

当前 Intelli Dev 只识别纯稳定 `X.Y.Z` tag；不能在服务端增加 preview 就宣称该旧客户端已支持 preview。虚拟 project path 可绑定渠道和发布变体，避免 internal/external 或平台产物混用。具体命名由管理员配置。

### Generic 与白泽灵台入口

示例路径，尚未实现：

```text
GET /updates/intelli-dev/windows-x64/stable/latest.yml
GET /updates/intelli-dev/macos-arm64/stable/latest-mac.yml
GET /updates/baizelingtai/windows-x64/stable/latest.json
GET /releases/{app}/{immutable-release-id}/assets/{asset-path}
```

白泽灵台返回 schemaVersion/version/piVersion/platform/arch/channel/publishedAt/package/sha256/bytes，以及可选 releasesUrl；地址同源、ZIP 路径以 .zip 结尾、不重定向、不使用带鉴权 query 的对象存储 URL。

electron-updater 保留并校验 electron-builder 的原始元数据与文件。SHA512 使用实际对象计算的 base64 摘要；不能用 SHA256 替代。路径重定位只能按兼容投影规则处理，不能改变准确安装包或破坏签名。安装格式和签名要求由各更新器的目标平台决定。

更新下载需验证 GET/HEAD、Content-Length、Content-Range、ETag 和 Range 行为。现有软件单区间解析器不等于差分更新支持。首个完整下载验收与差分验收分开；保留历史 blockmap，并针对原生请求实现多区间响应，或明确验证更新器的全量回退。不能在未测试前宣称差分完成。

## 上传、校验与发布

发版者可以上传一次构建的多文件产物集，或带目录的发行归档；目录结构、文件名、安装包和元数据作为同一个快照管理。不要要求发版者分别把每个 blockmap/YAML 当成独立软件版本。

建议流程：

1. 创建应用版本和目标平台/变体。
2. 上传安装包及 JSON/YAML/blockmap/校验文件等资产，保留原始路径和内容摘要。
3. 解析声明，核对版本、大小、摘要、引用目标和完整性；受管引用不能指向未授权外部源。
4. 完成软件包现有确认/发布要求及资产校验，形成不可变发布快照。
5. 所需资产可读后再推进更新目标或对外暴露 tag，防止客户端发现新版却拿不到文件。
6. 通过发布页面或受控上传工具/API 完成后续发版；CI 上传需要另设机器认证入口，不能直接把浏览器 session-only 管理 API 当成现有 token API。

支持旧版本的固定资产地址。同名同版本内容不能被静默覆盖。撤出更新目标不删除历史包，也不强迫已安装客户端降级。目标指针退回旧版本可以恢复服务端发布选择，但现有 Intelli Dev 的防降级行为仍然有效；客户端回退属于它自己的安装策略。

## 访问与迁移

软件应用默认不开放匿名更新读取，管理员逐应用启用。公开的独立更新入口读取发布快照；原软件中心浏览器登录和管理权限不被放宽。应用关闭更新源或包被下架后，版本发现和固定资产请求都重新核对治理状态。

第一版匿名更新源不使用内嵌 GitLab token 授予管理权限。实例反向代理和部署地址仍定义真实网络边界，产品的“内网匿名”开关本身不保证外部网络不可达。

匿名读取默认关闭。未开启的应用不因携带旧 GitLab token 而获得匿名访问；兼容入口必须重新核对应用、发布快照及关联安装包的当前治理状态。管理接口继续复用软件中心鉴权规则，更新读取不能变成任意文件读取或管理认证入口。多个渠道各自固定平台、架构和变体，不从客户端文件名推测权限或目标。

迁移方式：

- Intelli Dev 保留 gitlab provider、现有 tags 检测、本地代理和安装执行逻辑；后续构建只调整源地址及虚拟 project path。已发放包的地址是构建期固定的，首次迁移仍需新包或既有域名的运维代理，不能承诺未改动客户端会自动发现新 host。
- 白泽灵台使用现有环境/构建参数配置兼容 manifest 地址；更换源不赋予它自动安装 Windows ZIP 的能力。
- 后续应用可接入 generic 或其他明确支持的协议适配器。没有通用的“读任意发布页 HTML 就能自动安装”协议。

## 实施任务和验收边界

| 顺序 | 工作 | 验收 |
| --- | --- | --- |
| U1 | Release Asset、更新目标、项目映射及可比较版本 | 多应用隔离；版本/资产不可变；错误引用拒绝；复用包存储和容量 |
| U2 | 多文件上传和元数据校验 | 原始 YAML/JSON、安装包、blockmap；SHA256/SHA512/bytes 一致；缺文件不能发布 |
| U3 | 按应用匿名更新读取及流式资产服务 | 未开启/未发布/下架拒绝；稳定路径；GET/HEAD/Range；后台仍鉴权 |
| U4 | GitLab tags/raw 最小兼容 | 使用现有 Intelli Dev 原有 provider，验证 project/ref/path、notes 及无修改更新链路 |
| U5 | 白泽灵台 manifest 与 generic feed | 实际解析器接受；同源；平台/架构；稳定与预发布规则；缓存刷新 |
| U6 | 发布页、更新地址展示、运维及机器发布入口 | 发版一次上传完整产物；客户端配置示例；渠道切换；审计和失败状态 |
| U7 | 原生客户端更新验收 | Intelli Dev Windows NSIS 与 macOS ZIP/签名；白泽灵台检测/下载；历史版本和差分/全量回退 |

U7 必须区分服务端协议检查与真实客户端安装。白泽灵台无人值守安装、额外更新器协议、灰度发布和强制更新没有在本轮确认，不作为当前源兼容范围的隐含承诺。

## 实施约束

- U1–U7 为验收分组，实施票按完整发布和读取流程拆分，明确阻塞关系；不先批量写完全部实体再考虑权限和用户流程。
- 软件包的既有容量与确认政策继续生效。辅助资产、上传预留和临时对象同样计入账目，不能靠更新接口绕开容量限制。
- 匿名读取入口单独允许 GET/HEAD，并由业务授权规则控制；不得放宽 `/api/v1/admin/software/**` 或现有受控下载会话。
- GitLab project/file 的编码路径需在真实 Servlet 容器及反向代理验证，保留严格路径检查；不得为兼容编码斜杠全局关闭防火墙检查。
- 测试覆盖领域服务、HTTP、协议解析器/适配器及管理页面的公开边界。使用原有客户端解析器的契约检查，不等于实际下载、差分更新或安装已通过。
