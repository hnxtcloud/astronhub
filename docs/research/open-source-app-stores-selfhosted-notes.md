# 开源应用商店调研：自托管与服务器应用目录

核验日期：2026-10-21。仅使用项目官方代码仓库、许可证、官网和应用目录。本文件是主调研清单的来源笔记；现有 `docs/research/` 目录适合保存此类资料。

## 口径与结论

这里的应用商店是将服务器软件打包、展示并提供安装管理的目录，通常与部署面板绑定。它们面向的主要不是手机或桌面应用。以下应用是已在官方来源核实的代表项目，不是按下载量或活跃用户统计的热门榜。

1Panel 社区版、CasaOS、YunoHost、Runtipi 的主体许可证均属于常见开源许可证。1Panel Pro 另行提供商业功能，不能将社区版许可扩展到整个产品。Cloudron 官方将主体代码明确称作 source available，应作为近邻产品单列，不能混入严格开源平台名单。平台开源也不意味着目录内每个应用、镜像、插件和模型都使用同一许可证。

## 平台与许可证

| 平台 | 主要定位 | 核实的主体许可证 | 证据 |
| --- | --- | --- | --- |
| 1Panel 社区版 | Linux 服务器管理；网站、数据库、容器及 AI 工具安装 | GPLv3 | [项目介绍与 Pro 区别](https://github.com/1Panel-dev/1Panel)、[LICENSE](https://github.com/1Panel-dev/1Panel/blob/dev/LICENSE)、[官方应用仓库](https://github.com/1Panel-dev/appstore) |
| CasaOS | 个人云、家庭服务器的网页管理和 Docker 应用安装 | Apache-2.0 | [项目与 Features](https://github.com/IceWhaleTech/CasaOS)、[LICENSE](https://github.com/IceWhaleTech/CasaOS/blob/main/LICENSE) |
| YunoHost | 在自有服务器安装、维护多种网络应用 | AGPLv3 | [主体 LICENSE](https://github.com/YunoHost/yunohost/blob/dev/LICENSE)、[应用目录](https://apps.yunohost.org/)、[目录政策与结构](https://github.com/YunoHost/apps) |
| Runtipi | Docker 家庭服务器编排和网页应用管理 | GPLv3 | [主体介绍与 License](https://github.com/runtipi/runtipi)、[LICENSE](https://github.com/runtipi/runtipi/blob/master/LICENSE)、[官方商店](https://github.com/runtipi/runtipi-appstore) |
| Cloudron（近邻、排除严格开源） | 面向自托管网络应用的安装、升级、用户与备份管理 | 官方称 source available；本次没有取得可匿名读取的当前主体 LICENSE，不推断具体限制条款 | [官方 Open Source 页面](https://cloudron.io/opensource.html)、[官网功能](https://cloudron.io/) |

Cloudron 官方原文：`...all Cloudron app packages are open source at our GitLab instance`；随后说 `There you will also find Cloudron code as source available.` 因而“应用打包仓库开源”与“商店平台本身严格开源”应分开判断。

## 1Panel 已确认的代表应用

逐个读取了官方仓库的 `apps/<id>/data.yml`，以下链接指向对应打包目录。仓库 README 说明这是官方应用商店，列有上架条件；不同区域、版本和 CPU 架构可见的应用可能不同。其主体 README 也明确提示国内 V2 与国际版应用数量有差异。

| 分类 | 应用 | 用途 | 官方目录 |
| --- | --- | --- | --- |
| 建站/CMS | WordPress | 博客与内容管理 | [wordpress](https://github.com/1Panel-dev/appstore/tree/dev/apps/wordpress) |
| 文件与协作 | Nextcloud | 私有云文件与协作 | [nextcloud](https://github.com/1Panel-dev/appstore/tree/dev/apps/nextcloud) |
| 数据库 | MySQL | 关系数据库 | [mysql](https://github.com/1Panel-dev/appstore/tree/dev/apps/mysql) |
| 数据库 | PostgreSQL | 关系数据库 | [postgresql](https://github.com/1Panel-dev/appstore/tree/dev/apps/postgresql) |
| 开发工具 | Gitea | Git 代码托管 | [gitea](https://github.com/1Panel-dev/appstore/tree/dev/apps/gitea) |
| 媒体 | Jellyfin | 私人媒体库 | [jellyfin](https://github.com/1Panel-dev/appstore/tree/dev/apps/jellyfin) |
| AI | Ollama | 部署和运行模型 | [ollama](https://github.com/1Panel-dev/appstore/tree/dev/apps/ollama) |
| 运维监控 | Uptime Kuma | 服务可用性监测 | [uptime-kuma](https://github.com/1Panel-dev/appstore/tree/dev/apps/uptime-kuma) |

## CasaOS 与官方应用源的边界

CasaOS 主体 README 的 Features 明确写出 `Selected apps in the app store, one-click installation`，并列举 `Nextcloud, HomeAssistant, AdGuard, Jellyfin, *arr and more!`。这可以直接作为 CasaOS 应用方向的官方证据。

但核验时 `IceWhaleTech/CasaOS-AppStore` 仓库当前 README 标题已经是 **ZimaOS AppStore Source**，正文称其托管官方 ZimaOS 商店。不能只凭旧仓库名称，将当前 main 所有条目都无条件标为当前 CasaOS 官方可安装目录。下表前四项有 CasaOS 主体 README 的直接证据；其余为当前同一官方应用源中的代表包，需保持这一说明。

| 分类 | 应用 | 用途 | 官方来源与证据范围 |
| --- | --- | --- | --- |
| 私有云 | Nextcloud | 文件存储与协作 | [CasaOS Features](https://github.com/IceWhaleTech/CasaOS#features)、[官方源打包文件](https://github.com/IceWhaleTech/CasaOS-AppStore/blob/main/Apps/Nextcloud/docker-compose.yml) |
| 智能家居 | Home Assistant | 本地家庭自动化 | [CasaOS Features](https://github.com/IceWhaleTech/CasaOS#features)、[官方源打包文件](https://github.com/IceWhaleTech/CasaOS-AppStore/blob/main/Apps/HomeAssistant/docker-compose.yml) |
| 网络 | AdGuard Home | DNS 广告与跟踪拦截 | [CasaOS Features 的 AdGuard 项](https://github.com/IceWhaleTech/CasaOS#features)、[官方源具体包](https://github.com/IceWhaleTech/CasaOS-AppStore/blob/main/Apps/AdGuardHome/docker-compose.yml) |
| 媒体 | Jellyfin | 家庭媒体服务器 | [CasaOS Features](https://github.com/IceWhaleTech/CasaOS#features)、[官方源打包文件](https://github.com/IceWhaleTech/CasaOS-AppStore/blob/main/Apps/Jellyfin/docker-compose.yml) |
| 媒体自动化 | Sonarr（*arr 家族示例） | 剧集媒体下载与整理自动化 | [CasaOS Features 的 *arr 家族项](https://github.com/IceWhaleTech/CasaOS#features)、[当前官方源 Sonarr 包](https://github.com/IceWhaleTech/CasaOS-AppStore/blob/main/Apps/Sonarr/docker-compose.yml) |
| 文件同步 | Syncthing | 设备间点对点文件同步 | [当前官方源打包文件](https://github.com/IceWhaleTech/CasaOS-AppStore/blob/main/Apps/Syncthing/docker-compose.yml) |
| 下载 | qBittorrent | BitTorrent 下载 | [当前官方源打包文件](https://github.com/IceWhaleTech/CasaOS-AppStore/blob/main/Apps/qBittorrent/docker-compose.yml) |

[ZimaOS 官方仓库说明](https://github.com/IceWhaleTech/ZimaOS)说其从 CasaOS 演进而来，基于 Buildroot，提供 OTA、磁盘管理和面向 x86-64 UEFI 的系统镜像；该仓库还说明自己主要用于测试镜像发布。此次不把 ZimaOS 整体等同于 CasaOS 的 Apache-2.0 源码项目。

## YunoHost 已确认的代表应用

从官方 [apps.toml](https://github.com/YunoHost/apps/blob/master/apps.toml) 读取以下条目，核验时全部为 `state = "working"`。此状态只表示目录元数据，不能替代本地安装验证。官方目录 README 表明不接收非自由软件上游，同时记录 `antifeatures`，例如 WordPress 条目的非自由附加组件/付费内容提示。

| 分类 | 应用 | 用途 | 官方打包仓库 |
| --- | --- | --- | --- |
| 文件与协作 | Nextcloud | 私有云、文件、日历和联系人 | [nextcloud_ynh](https://github.com/YunoHost-Apps/nextcloud_ynh) |
| 建站/CMS | WordPress | 博客与网站 | [wordpress_ynh](https://github.com/YunoHost-Apps/wordpress_ynh) |
| 开发工具 | Gitea | Git 代码托管 | [gitea_ynh](https://github.com/YunoHost-Apps/gitea_ynh) |
| 媒体 | Jellyfin | 媒体库 | [jellyfin_ynh](https://github.com/YunoHost-Apps/jellyfin_ynh) |
| 密码管理 | Vaultwarden | Bitwarden 兼容密码管理服务 | [vaultwarden_ynh](https://github.com/YunoHost-Apps/vaultwarden_ynh) |
| 文档协作 | HedgeDoc | 协同 Markdown 文档 | [hedgedoc_ynh](https://github.com/YunoHost-Apps/hedgedoc_ynh) |
| 文件同步 | Syncthing | 设备间文件同步 | [syncthing_ynh](https://github.com/YunoHost-Apps/syncthing_ynh) |

## Runtipi 已确认的代表应用

以下均出现在[官方商店 README 的应用表](https://github.com/runtipi/runtipi-appstore)。该 README 当前同时明确：仓库仍接收更新，但不再接受新增应用，建议使用自定义应用、社区商店或建立自己的商店。不能把“有商店目录”推论为官方商店仍持续增加新品。

| 分类 | 应用 | 用途 | 官方打包目录 |
| --- | --- | --- | --- |
| 文件与协作 | Nextcloud | 私有云与协作 | [nextcloud](https://github.com/runtipi/runtipi-appstore/tree/master/apps/nextcloud) |
| 媒体 | Jellyfin | 家庭媒体库 | [jellyfin](https://github.com/runtipi/runtipi-appstore/tree/master/apps/jellyfin) |
| 照片 | Immich | 手机照片与视频备份 | [immich](https://github.com/runtipi/runtipi-appstore/tree/master/apps/immich) |
| 智能家居 | Home Assistant | 本地家庭自动化 | [homeassistant-1](https://github.com/runtipi/runtipi-appstore/tree/master/apps/homeassistant-1) |
| 文件同步 | Syncthing | 点对点文件同步 | [syncthing](https://github.com/runtipi/runtipi-appstore/tree/master/apps/syncthing) |
| 开发工具 | Gitea | Git 托管 | [gitea](https://github.com/runtipi/runtipi-appstore/tree/master/apps/gitea) |
| 运维监控 | Uptime Kuma | 服务可用性监测 | [uptime-kuma](https://github.com/runtipi/runtipi-appstore/tree/master/apps/uptime-kuma) |

## 可供主清单归纳的常见应用类别

上述已核实目录显示，自托管商店经常覆盖：文件存储与同步（Nextcloud、Syncthing）、建站（WordPress）、媒体和照片（Jellyfin、Immich）、家庭自动化（Home Assistant）、Git 托管（Gitea）、监控（Uptime Kuma）、密码和安全（Vaultwarden、AdGuard Home）、数据库（MySQL、PostgreSQL）、AI 模型运行（Ollama）。这是对本次样本目录的归纳，不是全市场覆盖率或流行度排名。

目录与许可证均可能更新；本笔记核验的是链接所指当时页面和文件，没有安装运行各个应用，也没有逐个审计应用上游及镜像许可证。
