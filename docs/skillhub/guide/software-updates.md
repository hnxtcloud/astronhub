# 软件更新快照发布

软件中心的展示版本和客户端更新版本分别管理。展示版本可以使用业务名称；更新快照必须有 SemVer 机器版本、对应的原生 tag，以及明确的系统、架构、stable/preview 渠道和变体。相同应用和目标组合中，机器版本和 tag 各自唯一；更换 v/V 前缀不能创建同版本的另一份内容。

## 管理页面

使用 SOFTWARE_ADMIN 或 SUPER_ADMIN 浏览器会话进入软件管理页，选择应用和展示版本。空展示版本也提供安装包上传和更新草稿入口。

1. 按现有软件包流程上传、确认并发布安装包。ZIP、DMG 使用管理员手动确认，DMG 不会被标记为已经自动检查或验签；它可以和 macOS ZIP 一起出现在原生更新元数据中。
2. 创建更新草稿，填写机器版本和目标。页面使用 `v{机器版本}` tag；管理 API 也接受无前缀或大写 V 前缀的对应 tag。
3. 关联相同展示版本、应用、系统和架构下已经确认并发布的安装包，填写原始相对路径。关联复用原对象，不重复计算安装包容量。
4. 上传原始 YAML/JSON、blockmap、签名或其他辅助文件。可以一次选择多文件；单文件时路径是完整相对路径，多文件时路径是目标目录。所有文件属于同一个草稿，不需要为每个辅助文件创建软件展示版本。不同用途的文件可分批上传到同一草稿。
5. 校验，然后发布快照。服务端再次检查元数据与实际资产的版本、字节数、SHA256/SHA512。发布前的活动上传必须完成。发布后不能增删或替换文件；内容变化必须创建新机器版本。
6. 在应用的更新源区域，按需开启匿名读取，选择已发布快照，保存渠道标识和虚拟 GitLab project path。虚拟项目是更新协议映射，不会创建 Git 仓库。可以重新选择目标，也可以清除目标；撤出更新不会删除已有安装包。

发布快照和启用匿名源是独立操作。管理 API 始终要求浏览器会话、CSRF 和软件管理角色；旧 GitLab token 不授予任何软件管理权限。

## 原始元数据和路径

保留构建产物的原始字节和相对目录。例如 `dist/latest.yml` 中的 `app.zip` 指向 `dist/app.zip`，而不是根目录的 `app.zip`。文件名中的 URL 编码空格可以写为 `%20`；查询、fragment、绝对地址、路径穿越、编码分隔符和双重编码均被拒绝。

Electron 元数据的 `files` 中每个文件都必须关联受管安装包，大小和 SHA512 base64 必须匹配存储的实际字节。`blockMapSize` 表示包尾嵌入 blockmap 的长度，不表示外部 `.blockmap` 文件大小。外部 blockmap 保持独立原始资产。当前不接收包含 `packages` 的 web-installer 元数据，需使用完整安装包发行方式。

白泽灵台 manifest 使用 schemaVersion 1、win32/x64、匹配的 channel/piVersion、有效 ISO 发布时间、相对 ZIP 路径、实际 SHA256 和 bytes。可选 `releasesUrl` 使用本源的相对路径。

标为 CHECKSUM 的文件支持标准 `sha256sum`/`sha512sum` 清单（十六进制摘要、空格、相对文件路径），或以 `.sha256`/`.sha512` 为后缀、仅含一个摘要的伴随文件。摘要会与所指资产交叉核对。签名文件作为原始资产保存；保存签名并不代表服务器已完成原生签名验证。

## 容量和失败处理

辅助资产沿用软件容量账户和上传限制；上传前预留，完成后转入已用容量。辅助文件通过单次原始请求上传，服务端按有界块写入存储。反向代理的请求体限制也必须覆盖辅助文件大小；大安装包继续使用已有分块上传流程。已支持安装格式的文件后缀不能通过辅助文件入口上传（大小写不敏感），应先完成软件包确认；`.exe.blockmap`、`.zip.blockmap`、签名和摘要伴随文件仍可作为辅助文件。尚不支持的原生安装格式需要增加明确的格式能力，不能通过改扩展名获得原生更新支持。

中断上传、存储 abort 或删除失败时，保留持久化清理记录和容量占用。定时任务重试后才释放对应容量。草稿移除辅助文件会先从草稿中分离；如果对象删除失败，其容量仍保留。安装包的分离只移除快照关联，原软件包及其容量保持由软件包流程管理。

## 管理接口

接口基路径为 `/api/v1/admin/software`，返回现有 API envelope。

| 操作 | 接口 |
| --- | --- |
| 创建 / 列出快照 | `POST /update-releases`；`GET /update-releases?catalogReleaseId={id}` |
| 列出应用已发布快照 | `GET /update-releases?applicationId={id}` |
| 查看快照 | `GET /update-releases/{id}` |
| 关联安装包 | `POST /update-releases/{id}/package-assets` |
| 上传原始辅助文件 | `POST /update-releases/{id}/assets?path={relativePath}&role={role}`，`application/octet-stream` 和明确 Content-Length |
| 草稿移除文件 | `DELETE /update-releases/{id}/assets/{assetId}` |
| 校验 / 发布 / 撤出 | `POST /update-releases/{id}/validate`、`/publish`、`/withdraw` |
| 查询 / 修改匿名开关 | `GET /apps/{id}/update-source`；`PUT /apps/{id}/update-source` |
| 保存渠道和目标 | `PUT /apps/{id}/update-channels` |

本页描述发布管理能力。GitLab 兼容读取、generic/白泽灵台更新源和受限机器发布入口分别由后续实施票提供；真实 Windows/macOS 安装和签名验收不能由服务端单元或 HTTP 测试替代。
