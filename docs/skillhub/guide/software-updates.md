# 软件更新快照发布

软件中心的展示版本和客户端更新版本分别管理。展示版本可以使用业务名称；更新快照必须有 SemVer 机器版本、对应的原生 tag，以及明确的系统、架构、stable/preview 渠道和变体。相同应用和目标组合中，机器版本和 tag 各自唯一；更换 v/V 前缀不能创建同版本的另一份内容。

## 管理页面

使用 SOFTWARE_ADMIN 或 SUPER_ADMIN 浏览器会话进入软件管理页，选择应用和展示版本。空展示版本也提供安装包上传和更新草稿入口。

1. 按现有软件包流程上传、确认并发布安装包。ZIP、DMG 使用管理员手动确认，DMG 不会被标记为已经自动检查或验签；它可以和 macOS ZIP 一起出现在原生更新元数据中。
2. 创建更新草稿，填写机器版本和目标。页面使用 `v{机器版本}` tag；管理 API 也接受无前缀或大写 V 前缀的对应 tag。
3. 关联相同展示版本、应用、系统和架构下已经确认并发布的安装包，填写原始相对路径。关联复用原对象，不重复计算安装包容量。Windows Electron 还须主动勾选 NSIS 安装器确认（默认未勾选）；确认人和时间保存在不可变资产中，独立于原始版本元数据。该声明不等于自动识别安装器、验签或实际安装通过。
4. 上传原始 YAML/JSON、blockmap、签名或其他辅助文件。可以一次选择多文件；单文件时路径是完整相对路径，多文件时路径是目标目录。所有文件属于同一个草稿，不需要为每个辅助文件创建软件展示版本。不同用途的文件可分批上传到同一草稿。
5. 校验，然后发布快照。服务端再次检查元数据与实际资产的版本、字节数、SHA256/SHA512。发布前的活动上传必须完成。发布后不能增删或替换文件；内容变化必须创建新机器版本。
6. 在应用的更新源区域，按需开启匿名读取，选择已发布快照，保存渠道标识和虚拟 GitLab project path。虚拟项目是更新协议映射，不会创建 Git 仓库。可以重新选择目标，也可以清除目标；撤出更新不会删除已有安装包。

发布快照和启用匿名源是独立操作。管理 API 始终要求浏览器会话、CSRF 和软件管理角色；旧 GitLab token 不授予任何软件管理权限。

## 原始元数据和路径

保留构建产物的原始字节和相对目录。例如 `dist/latest-mac.yml` 中的 `app.zip` 指向 `dist/app.zip`，而不是根目录的 `app.zip`。文件名中的 URL 编码空格可以写为 `%20`；查询、fragment、绝对地址、路径穿越、编码分隔符和双重编码均被拒绝。

Electron 元数据的 `files` 中每个文件都必须关联受管安装包，大小和 SHA512 base64 必须匹配存储的实际字节。`blockMapSize` 表示包尾嵌入 blockmap 的长度，不表示外部 `.blockmap` 文件大小。外部 blockmap 保持独立原始资产。当前不接收包含 `packages` 的 web-installer 元数据，需使用完整安装包发行方式。

白泽灵台 manifest 使用 schemaVersion 1、win32/x64、匹配的 channel/piVersion、有效 ISO 发布时间、相对 ZIP 路径、实际 SHA256 和 bytes。可选 `releasesUrl` 使用本源的相对路径，必须解析到同一快照中实际上传的资产（相对 metadata 所在目录解析，支持编码空格、加号和 Unicode）。缺失目标会阻止发布；说明文件的固定下载地址同样受应用匿名开关和分发治理约束。

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

## GitLab 更新读取

为应用开启匿名更新源，并为渠道配置虚拟 project path（例如 `apps/intelli-dev`）及已发布目标后，客户端可使用以下只读入口。部署前缀应加在这些路径之前。

| 入口 | 行为 |
| --- | --- |
| `GET /api/v4/projects/apps%2Fintelli-dev/repository/tags` | 裸数组，只含渠道当前选定的一个 tag、发布说明和 commit.message |
| `GET/HEAD /api/v4/projects/apps%2Fintelli-dev/repository/files/dist%2Flatest.yml/raw?ref=v1.2.3` | 按精确 tag 和原始相对路径读取 |
| `GET/HEAD /apps/intelli-dev/-/raw/v1.2.3/dist/latest.yml` | 两段虚拟项目路径的辅助 raw 入口 |
| `GET/HEAD /releases/{app}/{snapshotId}/assets/{relativePath}` | 按应用 slug 和不可变快照 ID 读取历史资产 |

渠道只发现显式选定的目标，不自动选择更高版本。重新选择目标后，同渠道历史发布仍可通过精确 ref 读取；清除目标则关闭该渠道 tags/raw。固定历史地址独立于渠道指针。所有入口均要求应用匿名开关开启、应用已发布、快照已发布且全部关联安装包仍可分发；撤出快照后恢复安装包不会自动恢复该快照。旧 `PRIVATE-TOKEN` 不绕过这些条件。

文件保持原始字节，无对象存储跳转。响应提供 Content-Length、SHA256 ETag、Last-Modified、Accept-Ranges 和 `Cache-Control: no-store`。即使携带条件请求或 Range，服务端也先检查治理状态。支持单区间及多区间；多区间最多 16 个、合计最多 32 MiB。超限或无效 Range 返回完整 200，全部有效区间越界返回 416；If-Range 不匹配时返回完整内容。

原 Intelli Dev 保留 GitLab 分发配置，后续构建调整 baseUrl、owner/repo 和 artifactPath。已发放客户端不会自动发现新地址，首次迁移需要新构建或由运维代理旧域名。受控机器发布入口见下节。真实 Windows/macOS 安装、签名和完整差分更新不能由服务端 HTTP 测试或 multipart 解析器契约检查替代。

## 机器发版与凭据

软件管理员在应用的「机器发版凭据」中填写名称、到期日期，创建绑定当前账号和该应用的 token。原始凭据仅显示一次，保存到 CI 密钥库后清除页面显示；凭据列表不能恢复原始 token。到期、撤销、账号停用或软件管理职责移除都会阻止后续机器请求。普通 API token 自行申请 `software:publish` 不会获得应用发版权限。

机器 API 使用 `/api/v1/software-publishing/apps/{applicationId}`，原软件后台 API 仍只允许会话管理。安装包沿用分片、配额、检查、明确确认和发布流程。自动检查未完成不能填写元数据绕过；基础检查失败不能发布。ZIP/TAR 没有自动检查任务时，须在清单中明确提供 `rawVersionFields` 并声明 `confirmMetadata: true`。

仓库工具要求 Python 3.11+，使用标准库。通过密钥库注入 `ASTRONHUB_PUBLISH_TOKEN`，不要将 token 放在命令行、清单或仓库中：

```bash
python scripts/publish-software-release.py --server https://hub.example/hub --app 12 --manifest release.json --state release-state.json --publish
```

清单含展示版本、发布说明、完整 update 身份、installers 和 assets。Windows Electron 的每个 installer 还须明确填写 `confirmedInstallerFamily: "NSIS"`，脚本不会推断或自动补上；断点续发会检查服务端资产声明及其确认记录。每项指向一个原始本地文件和应保留的相对发布路径；YAML/JSON、安装包和 blockmap 的路径、大小、摘要须一致。完整清单样例位于仓库 `docs/software-machine-publishing.md`。

工具默认校验草稿，只有 `--publish` 才发布快照；另加 `--activate-channel` 才将清单指定的渠道移动到该快照。匿名开关仍由会话管理员单独设置。检查未完成时退出码为 2，保存不含 token 的状态文件，用同一清单和状态文件可重试；`--wait-seconds` 可有限等待。状态绑定服务、应用、清单及所有本地文件摘要，不能拿已改变的产物继续同一次发布。已发布资产不能原地替换。

## Generic 和白泽更新源

管理页在已选定目标的渠道下展示完整清单地址和客户端配置，并提示匿名源是否关闭。规范路径为 `/updates/{appSlug}/{channelSlug}/{metadataName}`；channelSlug 为管理员保存的标识，明确绑定系统、架构、stable/preview 和变体。例如 `/updates/intelli-dev/mac-stable/latest-mac.yml`，而不是按文件名自动选择平台或最高版本。

| 渠道类型 | metadataName | 原生安装格式 |
| --- | --- | --- |
| Electron Windows stable / preview | latest.yml / preview.yml | 已确认并发布的 NSIS EXE |
| Electron macOS stable / preview | latest-mac.yml / preview-mac.yml | 元数据必须引用 ZIP，可附 DMG |
| 白泽 Windows x64 stable / preview | latest.json | 已确认并发布的 ZIP |

Linux Electron 自动更新暂未支持。受管安装包格式与快照路径后缀必须相符；DMG 不能重命名为 ZIP。为兼容原生更新库，machineVersion 和 piVersion 的核心分量及纯数字预发布标识均限制为安全整数（不超过 9007199254740991）。白泽两个版本还须不超过 100 个字符；这些限制在发布时检查。

Feed 的版本、大小及 SHA512/SHA256 来自同一已选定快照；只将原生 URL 字段改为包含部署前缀的同源固定资产地址。原始 YAML/JSON 可以从固定资产地址取回原始字节。客户端取得清单后，即使管理员切换目标，它仍下载该快照的原文件。清除目标关闭该渠道清单，固定历史文件仍按应用、快照和安装包的当前治理状态读取。

可变清单只提供实际投影 ETag 和 no-store，不提供 Last-Modified，也不按日期条件返回 304；这样回退目标不会被旧日期缓存隐藏。固定/raw 文件仍保留日期校验能力。

每个快照只有一个 METADATA 时，feed 可以映射其原始文件名；有多个时，必须唯一匹配该渠道的规范 metadataName，否则拒绝读取。现有 Intelli Dev 的 GitLab 方式则始终依赖原始 latest.yml/latest-mac.yml 及其目录，管理页仅在找到唯一对应文件时生成该配置。

Electron generic 客户端将 provider 设为 generic、url 设为清单所在目录；preview 还需 channel=preview，并在客户端启用 allowPrerelease。现有 Intelli Dev 保留其 GitLab 分发配置（baseUrl、owner、repo、artifactPath），artifactPath 必须对应原始 latest.yml/latest-mac.yml 所在目录；Generic 示例不意味着现有构建配置已经支持直接切换。其现有检测器忽略预发布版本。

白泽通过 `BAIZELINGTAI_UPDATE_FEED_URL` 或便携版构建配置的 updateFeedUrl 指向完整 latest.json。内网 ZIP 的实际安装仍由现有维护流程执行。服务端 no-store 不替代客户端自身的轮询间隔或内存缓存；目标切换在下一次实际请求时生效。

服务端测试使用真实 HTTP，在根路径和 `/hub` 下调用当前安装的 GenericProvider、文件选择和下载/摘要组件，以及白泽原始 parseFeed 和从原始源码隔离执行的比较函数，验证清单、同源完整下载和准确 SHA512/SHA256。没有加载白泽应用或启动真实应用。样本为合成内容；这不证明真实 NSIS/macOS 签名或安装、完整差分链路或全量回退已通过。历史 blockmap 地址可读不等同于当前原生更新器已完成旧版 blockmap 定位验收。

## 反向代理和部署前缀

内置 Web Nginx 将 `/api/v4/projects/`、`/updates/`、`/releases/` 以及 `/{owner}/{repo}/-/raw/{tag}/{asset...}` 转发到后端。缺少的更新文件保留后端错误状态，不回退到网页 HTML。更新读取关闭代理压缩、缓存和响应缓冲，保留 GET/HEAD、Range、If-Range、If-None-Match、ETag、Last-Modified 和原始内容长度。

设置 `SKILLHUB_WEB_BASE_PATH=/hub/` 后，这些入口同样位于 `/hub/` 下。GitLab project/file 中的 `%2F` 与查询必须原样传递；Nginx 使用原始 `$request_uri` 及已验证的部署前缀映射剥离前缀，不能改为 `$uri`、常规 rewrite 或再次 URL 编码。以 `.`, 如 `/hub.v1/`，组成的合法前缀也按字面量匹配。构建、运行、发布配置和 Helm 均禁止将 `updates`、`releases` 用作部署前缀的首段。

辅助文件原始上传路径 `/api/v1/admin/software/update-releases/{id}/assets`，以及机器入口的 `/apps/{appId}/update-releases/{id}/assets`、`/apps/{appId}/uploads/{id}/chunks/{number}` 使用独立流式代理，包含部署前缀的版本也在 Nginx 检查请求体之前匹配。这些路径由后端鉴权，并依照软件策略预留容量和限制大小；代理本身不再使用全局 100 MiB 上限。其他 JSON/管理路径保持原有上限。外层 Ingress、网关和客户端超时仍需由运维按实际大小配置；本改动不表示已执行 5 GiB 端到端传输。

有本地 Nginx 时，可运行 `python scripts/tests/software-update-proxy-test.py --nginx /path/to/nginx --require-nginx`。该测试仅绑定回环地址，检查原始路径、请求/响应头、HEAD、错误状态和大请求头准入，结束后停止测试进程；不发送大上传实体，也不代替应用鉴权、真实更新安装或 Docker staging 验证。

历史 Windows Electron 快照若没有独立的 NSIS 声明，将拒绝发布、渠道切换和读取；迁移不会自动认定已有 EXE 为 NSIS。草稿可移除旧关联后重新关联并明确确认；已发布快照需按新的版本身份创建完整快照并明确确认。普通软件目录的 EXE 支持不依赖此更新协议声明。
