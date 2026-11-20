# 开源应用商店及常见应用清单

调研日期：2026-10-21。

应用商店可以分为终端软件分发和服务器应用部署两类。终端侧可参考 Flathub、F-Droid，以及 WinGet、Scoop、Homebrew 的软件仓库；服务器侧可参考 1Panel、CasaOS、Runtipi、YunoHost。以下是有代表性的项目和应用示例，不是完整名录或下载量排名。

“商店开源”与“商店只收录开源应用”是两个维度。F-Droid 默认仓库专门收录自由软件；Flathub、WinGet、Scoop、Homebrew Cask 的目录还会包含专有软件。服务器商店中每个应用也有各自许可证，不能由商店许可证推导应用的分发授权。[F-Droid 客户端说明](https://f-droid.org/en/packages/org.fdroid.fdroid/)、[Flathub 目录](https://flathub.org/en/apps/collection/popular/1)、[Scoop VS Code 清单](https://github.com/ScoopInstaller/Extras/blob/master/bucket/vscode.json)。

## 终端软件商店和仓库

WinGet、Scoop、Homebrew 是命令行包管理器及其仓库，纳入比较是因为它们提供软件发现、安装和更新能力，不代表它们都是独立的图形应用商店。

| 项目 | 系统与定位 | 已核对的代表软件 | 开源范围与来源 |
|---|---|---|---|
| Flathub | Linux，基于 Flatpak 的桌面应用目录 | Firefox、LibreOffice、VLC、GIMP、OBS Studio、LocalSend | 网站与 API 仓库 Apache-2.0；应用许可证各异。[代码](https://github.com/flathub-infra/website)、[许可证](https://github.com/flathub-infra/website/blob/main/LICENSE)、[应用目录](https://flathub.org/en/apps/collection/popular/1) |
| F-Droid | Android，自由开源应用商店 | VLC、KeePassDX、AntennaPod、Organic Maps | 客户端 GPL-3.0-or-later，默认仓库专门收录自由软件。[客户端与许可证](https://f-droid.org/en/packages/org.fdroid.fdroid/)、[项目介绍](https://f-droid.org/en/about/) |
| WinGet | Windows，软件搜索、安装和升级 | Firefox、7-Zip、VLC、Visual Studio Code | 客户端 MIT；社区安装清单公开，所安装软件许可证各异。[客户端](https://github.com/microsoft/winget-cli)、[许可证](https://github.com/microsoft/winget-cli/blob/master/LICENSE)、[清单仓库](https://github.com/microsoft/winget-pkgs) |
| Scoop | Windows，偏开发工具和用户目录安装 | Firefox、VLC、LibreOffice、Visual Studio Code，图形软件通常来自 Extras bucket | 安装器 Unlicense 或 MIT；应用许可证各异。[安装器](https://github.com/ScoopInstaller/Scoop)、[许可证](https://github.com/ScoopInstaller/Scoop/blob/master/LICENSE)、[Extras](https://github.com/ScoopInstaller/Extras) |
| Homebrew | macOS、Linux、WSL；命令行工具与服务，macOS 另有 Cask 应用 | Git、Node.js、Python；macOS Cask 中有 Firefox、Visual Studio Code | 工具 BSD-2-Clause；core 与 Cask 收录规则不同。[官网](https://brew.sh/)、[许可证](https://github.com/Homebrew/brew/blob/master/LICENSE.txt)、[软件目录](https://formulae.brew.sh/) |

具体应用来源：

- F-Droid：[VLC](https://f-droid.org/en/packages/org.videolan.vlc/)、[KeePassDX](https://f-droid.org/en/packages/com.kunzisoft.keepass.libre/)、[AntennaPod](https://f-droid.org/en/packages/de.danoeh.antennapod/)、[Organic Maps](https://f-droid.org/en/packages/app.organicmaps/)。
- WinGet：[Firefox 清单](https://github.com/microsoft/winget-pkgs/blob/master/manifests/m/Mozilla/Firefox/133.0.3/Mozilla.Firefox.yaml)、[7-Zip 清单](https://github.com/microsoft/winget-pkgs/blob/master/manifests/7/7zip/7zip/24.09/7zip.7zip.yaml)、[VLC 清单](https://github.com/microsoft/winget-pkgs/blob/master/manifests/v/VideoLAN/VLC/3.0.21/VideoLAN.VLC.installer.yaml)、[VS Code 清单](https://github.com/microsoft/winget-pkgs/blob/master/manifests/m/Microsoft/VisualStudioCode/1.95.3/Microsoft.VisualStudioCode.yaml)。这些固定版本链接用于证明目录收录，不代表推荐安装该历史版本。
- Scoop Extras：[Firefox](https://github.com/ScoopInstaller/Extras/blob/master/bucket/firefox.json)、[VLC](https://github.com/ScoopInstaller/Extras/blob/master/bucket/vlc.json)、[LibreOffice](https://github.com/ScoopInstaller/Extras/blob/master/bucket/libreoffice.json)、[VS Code](https://github.com/ScoopInstaller/Extras/blob/master/bucket/vscode.json)。
- Homebrew：[Git](https://formulae.brew.sh/formula/git)、[Node.js](https://formulae.brew.sh/formula/node)、[Python](https://formulae.brew.sh/formula/python@3.14)、[Firefox Cask](https://formulae.brew.sh/cask/firefox)、[VS Code Cask](https://formulae.brew.sh/cask/visual-studio-code)。Cask 示例要求 macOS，不能据此推断 Linux/WSL 也支持这些桌面安装包。

## 自托管服务器应用商店

这类平台将应用部署到用户自己的服务器，常以容器或打包脚本完成安装，应用随后通过浏览器或配套客户端使用。

| 项目 | 定位 | 已核对的代表应用 | 开源范围与来源 |
|---|---|---|---|
| 1Panel | Linux 运维面板，集成应用商店 | WordPress、Nextcloud、MySQL、PostgreSQL、Gitea、Jellyfin、Ollama、Uptime Kuma | 社区版 GPLv3，专业版另有授权。[平台](https://github.com/1Panel-dev/1Panel)、[许可证](https://github.com/1Panel-dev/1Panel/blob/dev/LICENSE)、[应用商店](https://github.com/1Panel-dev/appstore/tree/dev/apps) |
| CasaOS | 个人云和家庭服务器入口，提供 Docker 应用管理 | Nextcloud、Home Assistant、AdGuard、Jellyfin、*arr 系列 | 平台 Apache-2.0。[平台及功能](https://github.com/IceWhaleTech/CasaOS#features)、[许可证](https://github.com/IceWhaleTech/CasaOS/blob/main/LICENSE) |
| Runtipi | 面向个人自托管的一键应用部署平台 | Nextcloud、Jellyfin、Immich、Home Assistant、Syncthing、Gitea、Uptime Kuma | 平台 GPLv3。[平台](https://github.com/runtipi/runtipi)、[许可证](https://github.com/runtipi/runtipi/blob/master/LICENSE)、[应用商店](https://github.com/runtipi/runtipi-appstore) |
| YunoHost | 自托管服务器发行版与应用管理，覆盖个人和组织服务 | Nextcloud、WordPress、Gitea、Jellyfin、Vaultwarden、HedgeDoc、Syncthing | 平台 AGPLv3。[平台](https://github.com/YunoHost/yunohost)、[许可证](https://github.com/YunoHost/yunohost/blob/dev/LICENSE)、[应用目录](https://apps.yunohost.org/catalog)、[目录元数据](https://github.com/YunoHost/apps/blob/master/apps.toml) |

两个目录状态会影响选型：

- CasaOS 官方 README 仍列出上述应用，但名为 CasaOS-AppStore 的仓库当前 README 已使用 ZimaOS 品牌描述，不能把其 main 分支所有应用直接视为当前 CasaOS 的兼容承诺。[共享商店仓库](https://github.com/IceWhaleTech/CasaOS-AppStore)。
- Runtipi 官方应用仓库当前说明继续维护现有应用更新，但不再接受新应用，新增应用可以考虑社区商店或自定义应用。[官方商店说明](https://github.com/runtipi/runtipi-appstore)。

服务器平台的逐项来源与补充说明见[自托管应用商店核对记录](open-source-app-stores-selfhosted-notes.md)。

## 按用途整理的常见应用

本表归纳上述官方目录中的代表条目，表示用途覆盖，不表示每一家商店都提供全部软件，也不表示跨商店统计出的热度。

| 应用类别 | 代表应用 | 主要用途 | 可参考的目录 |
|---|---|---|---|
| 浏览器 | Firefox、Chromium | 网页浏览与 Web 应用入口 | [Flathub](https://flathub.org/en/apps/collection/popular/1) |
| 办公套件 | LibreOffice、ONLYOFFICE Desktop Editors | 文档、表格、演示文稿 | [Flathub](https://flathub.org/en/apps/collection/popular/1) |
| 图像与影音创作 | GIMP、darktable、OBS Studio | 图片编辑、照片处理、录屏直播 | [Flathub](https://flathub.org/en/apps/collection/popular/1) |
| 本地影音与播客 | VLC、AntennaPod | 音视频播放、播客订阅 | [Flathub](https://flathub.org/en/apps/collection/popular/1)、[F-Droid](https://f-droid.org/en/packages/de.danoeh.antennapod/) |
| 开发工具与运行时 | Visual Studio Code、Git、Node.js、Python | 编程、版本控制、脚本和应用运行 | [Homebrew](https://formulae.brew.sh/)、[Scoop Extras](https://github.com/ScoopInstaller/Extras) |
| 压缩与文件传输 | 7-Zip、LocalSend | 压缩解压、局域网文件传输 | [WinGet 清单](https://github.com/microsoft/winget-pkgs/blob/master/manifests/7/7zip/7zip/24.09/7zip.7zip.yaml)、[Flathub](https://flathub.org/en/apps/collection/popular/1) |
| 网盘与文件同步 | Nextcloud、Syncthing | 私有文件空间、设备间同步 | [YunoHost](https://apps.yunohost.org/catalog)、[Runtipi](https://github.com/runtipi/runtipi-appstore) |
| 媒体库与相册 | Jellyfin、Immich | 家庭影音服务、照片备份与浏览 | [Runtipi](https://github.com/runtipi/runtipi-appstore) |
| 网站与内容发布 | WordPress | 企业网站、博客和内容管理 | [1Panel](https://github.com/1Panel-dev/appstore/tree/dev/apps/wordpress)、[YunoHost](https://apps.yunohost.org/catalog) |
| 团队研发与协作 | Gitea、HedgeDoc | 代码托管、协作 Markdown 文档 | [YunoHost](https://apps.yunohost.org/catalog) |
| 密码管理 | Vaultwarden、KeePassDX | 服务端密码库、手机本地密码库 | [YunoHost](https://apps.yunohost.org/catalog)、[F-Droid](https://f-droid.org/en/packages/com.kunzisoft.keepass.libre/) |
| 数据库 | MySQL、PostgreSQL | 业务数据存储 | [1Panel](https://github.com/1Panel-dev/appstore/tree/dev/apps) |
| 智能家居与网络 | Home Assistant、AdGuard Home | 设备自动化、DNS 广告及跟踪拦截 | [CasaOS](https://github.com/IceWhaleTech/CasaOS#features)、[YunoHost](https://apps.yunohost.org/catalog) |
| 监控与状态页 | Uptime Kuma | 服务可用性检查和状态展示 | [1Panel](https://github.com/1Panel-dev/appstore/tree/dev/apps/uptime-kuma)、[Runtipi](https://github.com/runtipi/runtipi-appstore) |
| AI 模型服务 | Ollama | 本地模型运行与服务接口 | [1Panel](https://github.com/1Panel-dev/appstore/tree/dev/apps/ollama) |
| 离线地图 | Organic Maps | 离线地图查询与导航 | [F-Droid](https://f-droid.org/en/packages/app.organicmaps/) |

## 单独比较的源码可见平台

| 项目 | 不纳入严格开源主表的原因 | 来源 |
|---|---|---|
| Umbrel | 当前平台使用 PolyForm Noncommercial 1.0.0，含非商业用途限制 | [平台许可证](https://github.com/getumbrel/umbrel/blob/master/LICENSE.md) |
| Cloudron | 官方明确区分平台 source available 与应用包 open source | [官方开源说明](https://cloudron.io/opensource.html) |

## 对 AstronHub 软件中心的参考建议

若用于当前软件中心的产品对标，建议先看 WinGet、Scoop、Homebrew 的应用元数据、发行文件与更新管理，再看 Flathub 的分类、详情和来源展示。若后续增加服务器上一键部署服务，再比较 1Panel、Runtipi、YunoHost 的应用包与部署配置。

首批应用目录可以按办公、浏览器、开发工具、影音图像、文件工具、安全工具六类组织；服务器应用另设网盘同步、媒体相册、建站协作、数据库、监控网络、AI 服务分类。这个分类是基于上述目录覆盖面的产品建议，并非目录官方共同标准。

## 企业内网的应用与商店参考

内网选品应同时考虑完整安装包、运行依赖、功能是否依赖公网账号，以及升级源。将包管理器索引搬入内网，并不自动把安装包、插件、驱动和依赖包一起搬入。完全隔离网络还需要预先导入这些内容，并在断开公网的客户端验证。

### 员工电脑应用候选

| 工作 | 建议候选 | 内网准备重点 |
|---|---|---|
| 文档与表格 | LibreOffice、ONLYOFFICE Desktop Editors | 完整安装包、字体、模板；用实际业务文件验证兼容性 |
| PDF、压缩与截图 | SumatraPDF、7-Zip、ShareX | 固定发布包与配置，截图上传目标按公司要求设置 |
| 本地笔记与密码 | Joplin、KeePassXC | 本地可用；跨设备同步如有需求，另行配置内部服务 |
| 编码与版本管理 | VSCodium、Git | 编辑器扩展、语言服务器、Git LFS 等依赖按技术栈导入 |
| API 与数据库调试 | Bruno、DBeaver Community | API 集合放内部 Git；数据库驱动预先准备 |
| 终端与远程连接 | Windows Terminal、PowerShell、OpenSSH、WinSCP | 运行时、内部证书、远程主机连接配置 |
| 研发运行环境 | JDK、Python、Node.js、Maven、Gradle | SDK、构建工具和项目依赖分别管理，不能只提供语言安装包 |

这些是内网试点候选，不代表每个功能或插件都可在隔离环境中直接使用。[LibreOffice](https://www.libreoffice.org/)提供本地办公套件；[Bruno](https://www.usebruno.com/)将集合保存为本地文本文件，并明确其核心流程不要求云同步或账号；[VSCodium](https://vscodium.com/)提供 MIT 许可的编辑器发行版，扩展仍须另行核实和导入。

### 更贴近企业软件中心的开源参考

| 项目或组合 | 适用场景 | 值得参考的能力 | 内网边界 |
|---|---|---|---|
| UniGetUI | Windows 优先的员工软件安装界面 | 软件发现、批量安装、升级、卸载与包管理器聚合 | MIT 开源；它是客户端界面，需要搭配私有包源，不单独提供完整的企业商店后端。[官方项目](https://github.com/Devolutions/UniGetUI) |
| WinGet 加私有源 | Windows 安装包分发 | 发行文件清单、安装参数、哈希、升级与源管理 | 客户端支持添加源；安装包地址也要内网化。官方 REST 源示例使用 Azure Functions/Cosmos DB，不能视作纯内网开箱即用服务。[源管理](https://learn.microsoft.com/en-us/windows/package-manager/winget/source)、[官方参考实现](https://github.com/microsoft/winget-cli-restsource) |
| Scoop 加内部 bucket | 研发工具和便携软件 | Git 管理 JSON 安装清单、自建应用集合 | bucket 可托管到内部 Git；下载 URL、依赖、脚本和安装器自身的更新来源也要检查。[bucket 文档](https://github.com/ScoopInstaller/Scoop/wiki/Buckets) |
| Munki | macOS 企业软件分发 | Managed Software Center、软件仓库、安装与移除 | 使用 Web 服务器上的包与元数据仓库；软件自身的激活、更新和外部依赖仍需逐项处理。[官方项目](https://github.com/munki/munki) |
| opsi | 企业终端软件分发与运维 | 集中部署和软件自助安装 kiosk | Software-on-Demand 模块免费；Linux/macOS Agent、SSO、角色与审计等扩展的授权要单独核实。[自助安装文档](https://docs.opsi.org/opsi-docs-en/4.3/opsi-modules/software-on-demand.html)、[扩展说明](https://opsi.org/en/product/extensions/) |
| Flatpak 私有仓库 | Linux 桌面软件分发 | 应用、运行时、签名与仓库元数据 | 支持用 Web 服务器托管仓库；需同时准备所需运行时及扩展。[官方托管文档](https://docs.flatpak.org/en/latest/hosting-a-repository.html) |

企业终端管理工具 opsi 及其他候选的开源范围、商店和授权边界见[内网商店核对记录](open-source-app-stores-intranet-notes.md)。

WAPT 当前官方文档将 Discovery 和 Enterprise 均列为 proprietary。旧 Community 仓库的开源许可证不能直接用于描述当前产品，因此本表不将当前 WAPT 纳入开源方案。[当前产品说明](https://www.wapt.fr/en/doc/wapt-concept.html)。

### 内部共享服务

除了电脑安装包，内网常需要浏览器直接访问的团队服务。例如 [BookStack](https://www.bookstackapp.com/)用于内部知识库，支持 LDAP/OIDC/SAML2 和权限管理；[Harbor](https://goharbor.io/)用于容器制品仓库；[Verdaccio](https://www.verdaccio.org/)用于内部 npm 包和公共包缓存。缓存只涵盖已取得的内容，完全隔离网络需要提前导入项目依赖。

对 AstronHub 的建议是先建设应用目录和完整安装包分发，再按需要对接 WinGet/Scoop；批量静默安装、权限提升和终端安装回执需要客户端或终端管理工具协同。服务器应用部署可以另行参考 1Panel，不将服务器容器部署与员工电脑软件安装混为同一流程。
