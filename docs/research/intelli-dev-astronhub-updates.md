# Intelli Dev 使用 AstronHub 更新发布源的兼容性核查

日期：2026-10-14。范围：`C:/Project/intelli-dev-release/intelli-dev/client` 当前源码和安装的更新库；不代表当前分发包或运行实例已完成验收。未读取含实际凭据的构建配置，未启动 Electron、更新应用或访问私有 GitLab。

实施进度（2026-10-15）：本文缺口描述保留调研时的基线；软件快照及 GitLab 读取已分别按票 07、08 合入，当前能力与剩余验收见[更新渠道设计](../28-software-release-update-channels.md)及[操作指南](../skillhub/guide/software-updates.md)。尚未修改或迁移实际客户端。

## 结论

Intelli Dev 已有检测、下载和安装执行链路，使用 electron-updater generic provider；现有外围检测和文件获取仍绑定 GitLab。因此 AstronHub 能通过提供有限的 GitLab 读取兼容接口替代它的分发源，但当前软件中心尚未提供这些接口，也没有完整的发布资产模型。不能仅将已有软件详情 API 当作更新源。

用户已选择优先兼容 GitLab 接口以减少客户端改动，并按应用单独开启内网匿名更新源。上传、管理和未发布资产继续鉴权。这个选择不要求 AstronHub 模拟 GitLab 全部 API 或提供 Git 仓库功能。

## 实际客户端链路

1. 启动检查读取构建期的 distribution，查询 GitLab tags，选择最大的纯 `X.Y.Z` 版本；支持 v/V 前缀，现有逻辑忽略预发布版本。Release notes 使用 tag 的 `release.description`，其次使用 `commit.message`。当前检测并非调用 GitLab Releases API。[检测实现](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/release-checker.ts:37)、[tags 路径](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/release-checker.ts:62)、[选择结果](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/release-checker.ts:137)。
2. UI 显示更新提示；用户触发下载后启动本地 HTTP 代理，将 generic provider 的目录式文件请求翻译为 GitLab repository files raw 请求。[下载编排](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/auto-updater.ts:208)、[代理路径转换](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/gitlab-feed-proxy.ts:51)。
3. 自动下载被显式关闭，由程序调用 checkForUpdates 后再调用 downloadUpdate；存在下载进度、完成和错误事件。[provider](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/auto-updater.ts:229)、[下载执行](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/auto-updater.ts:260)。
4. UI 的重启安装入口检查运行中的 AI 任务；执行清理会话、关闭数据库、调用 quitAndInstall。它确实有安装执行能力，区别于白泽灵台当前内网 ZIP 流程。[UI 检查](C:/Project/intelli-dev-release/intelli-dev/client/src/ui/components/update/UpdatePrompt.tsx:132)、[安装执行](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/auto-updater.ts:271)。更新库还具有退出安装的默认行为，不能只由 UI 推断全部安装时机；真实退出、签名和安装结果需要实机验收。
5. 下载前保存 pendingInstallVersion，下次启动核对运行版本并在失败时重新提示。[失败感知](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/auto-updater.ts:357)。

distribution 在构建时注入。类型只允许 gitlab，注入脚本也固定推断该 provider；运行偏好只控制 enabled/checkOnStartup 等，不提供更新 host 修改入口。[类型](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/build-configs/git.ts:11)、[注入器](C:/Project/intelli-dev-release/intelli-dev/client/scripts/inject-dist-config.mjs:18)、[运行偏好](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/auto-update-config.ts:1)。提供兼容接口可保留现有源码，但仍须在后续构建中注入 AstronHub 地址；已发放客户端若地址固定，仍需首次迁移包或运维层的既有地址代理。

## 必需的 GitLab 读取兼容接口

```text
GET /api/v4/projects/{encoded-owner-repo}/repository/tags
GET /api/v4/projects/{encoded-owner-repo}/repository/files/{encoded-file-path}/raw?ref={tag}
HEAD /api/v4/projects/{encoded-owner-repo}/repository/files/{encoded-file-path}/raw?ref={tag}
```

首个接口返回裸数组，至少包含 name；可提供 release.description 和 commit.message。只返回对该应用已完整发布、允许更新分发的版本。第二类接口按配置的虚拟 project path、tag 和资产路径精确解析，不接受任意文件系统或存储路径。

客户端还提供 `buildFeedUrl`，格式为 `/{owner}/{repo}/-/raw/{tag}/{artifactPath}`；当前自动下载通过本地代理使用上面的 API raw。web raw 路径可作为发布页面和其他现有工具的辅助兼容入口。[web 路径](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/release-checker.ts:74)。

原有 PRIVATE-TOKEN 仅在客户端主进程向 GitLab 请求时使用，不进入前端 feed URL。已确认第一版按应用启用匿名读取，不能把它视为 AstronHub 的管理令牌；管理接口仍使用原来的权限体系。[头处理](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/release-checker.ts:51)、[代理请求头](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/gitlab-feed-proxy.ts:60)。

## 发布资产与更新器协议

Windows 主链路使用 latest.yml、NSIS EXE 和对应 blockmap。macOS generic feed 使用 latest-mac.yml 和可更新 ZIP，具体签名条件由构建及客户端执行器决定。不能只保留软件中心识别为安装包的文件。[发行手册](C:/Project/intelli-dev-release/intelli-dev/client/AUTO-UPDATE-GUIDE.md:127)、[代理说明](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/gitlab-feed-proxy.ts:1)、[构建目标](C:/Project/intelli-dev-release/intelli-dev/client/electron-builder.json:89)。

本机更新库的 GenericProvider 按 channel 读取 YAML；Provider 解析 files/url/size/checksum 并拼接下载地址。通常需要 SHA512 base64，不能把 SHA256 字符串填到 SHA512 字段。应保留 electron-builder 输出的原始 YAML、安装包和辅助文件，并验证它们指向同一套资产。[实际库 provider](C:/Project/intelli-dev-release/intelli-dev/client/node_modules/electron-updater/out/providers/GenericProvider.js:18)、[校验与解析](C:/Project/intelli-dev-release/intelli-dev/client/node_modules/electron-updater/out/providers/Provider.js:124)。安装库属于本机依赖证据，换版本后应重新确认，不能代替产品版本承诺。

代理透传 GET/HEAD、Range 和内容长度、范围、ETag、Last-Modified 等头。差分下载会涉及 blockmap 和多区间请求；AstronHub 原下载解析器仅支持单区间，多区间返回全量 200。因此“已有 Range”不意味着已兼容差分更新。[代理头](C:/Project/intelli-dev-release/intelli-dev/client/src/electron/libs/gitlab-feed-proxy.ts:60)、[当前软件 Range](C:/Project/astronhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SoftwareRangeParser.java:3)。

## AstronHub 缺口

- 软件 API 需要浏览器会话，并明确不支持 API Token；更新器需要独立的、按应用开启的匿名读取表面。[策略](C:/Project/astronhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java:311)。
- 软件包 enum 当前仅有 MSI/EXE/DEB/RPM/ZIP/TAR_GZ/TAR_XZ，没有 YAML/blockmap/DMG 这样的发布资产表达；target OS 当前仅 WINDOWS/LINUX。[格式](C:/Project/astronhub/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/software/SoftwarePackageFormat.java:8)、[系统](C:/Project/astronhub/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/software/SoftwareTargetOs.java:7)。
- 缺少独立的 release tag、机器可比较版本、更新 channel/目标和 release asset 集合。现有 displayVersion 明确不按 SemVer 解释，不能直接用目录排序做版本发现。[版本模型](C:/Project/astronhub/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/software/SoftwareRelease.java:14)。
- 缺少 GitLab tags/raw 读取兼容接口、固定文件名地址及 generic feed。

## 本轮无网络验证

使用本机 TypeScript 将当前 release-checker.ts 转为内存模块，替换 fetch 为固定合成 tags 响应。验证即使 baseUrl 为 AstronHub，当前客户端仍请求 GitLab 的 `/api/v4/projects/.../repository/tags`，并生成 GitLab web raw 路径。

调用已安装 electron-updater 的 parseUpdateInfo/resolveFiles，验证带合成 SHA512 的原生 YAML 能解析为 AstronHub generic 目录下的 EXE 地址。检查成功；没有网络、Electron 启动、文件下载、更新或安装。相关现有 Vitest 测试仅阅读，未运行；本轮结果不构成 Windows/macOS 更新验收。

通用服务方案见 [软件发布和更新渠道设计](../28-software-release-update-channels.md)。
