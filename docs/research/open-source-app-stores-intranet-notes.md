# 内网应用商店参考：企业终端软件分发

核验日期：2026-10-21。仅使用官方仓库、许可证和文档。本笔记补充服务器应用商店之外的企业终端软件自助安装场景。

## 结论

企业内部让员工安装办公软件，应优先参考 **opsi 的 Software-on-Demand / opsi-client-kiosk**（Windows 终端管理和自助目录）与 **Munki / Managed Software Center**（macOS）。**UniGetUI** 适合参考桌面商店交互，配合已有私有包源使用；其本身不是带组织、权限、审批和服务器仓库的企业应用商店。**当前 WAPT Discovery/Enterprise 是专有软件**，不应因历史 Community 的 GPL 仓库而列作当前开源选项。

## 对比

| 项目 | 内网适用性 | 自助安装能力 | 本次核验的开源边界 | 应用商店参考价值 |
| --- | --- | --- | --- | --- |
| opsi | 适合管理员维护内网软件包、终端和分发仓库；Windows 优先评估 | 官方 Software-on-Demand / opsi-client-kiosk 文档明确标为 free，管理员通过产品组指定用户可选产品 | `opsiconfd` 的 `pyproject.toml` 标 `AGPL-3.0-only`；`opsi-client-kiosk` LICENSE 为 AGPLv3。不要把整个产品所有扩展笼统视为同一免费开源范围 | 产品卡片、搜索、安装状态、授权产品组、终端部署和安装脚本 |
| Munki | 适合 macOS；使用自建 Web 仓库存放软件安装包和元数据 | Managed Software Center 展示可安装/可更新软件；可通过 manifest 管理必装和可选软件 | 主体 Apache-2.0 | 应用展示、软件清单、包元数据、测试目录与正式目录、自动安装和可选安装 |
| UniGetUI | 能与底层包管理器的内网源配合；部署和源兼容性须逐一确认 | 提供发现、安装、升级、卸载、批量操作等 GUI | 当前官方仓库 Devolutions/UniGetUI，MIT；项目明确仍 free/open source | 桌面客户端交互、多来源聚合、软件详情、安装选项、任务与更新操作 |
| WAPT（当前版本排除开源清单） | 产品本身有企业内网包仓库和自助分发功能 | 官方文档存在 Self-Service | 当前官方文档明说 Discovery 与 Enterprise 都 proprietary；历史 Community 已分叉 | 可作为商业产品功能对照，不以当前开源项目推荐 |

## opsi：内网软件自助商店更贴近的参考

官方[软件按需安装文档](https://docs.opsi.org/opsi-docs-en/4.3/opsi-modules/software-on-demand.html)的标题为 `OPSI Software On Demand - opsi-client-kiosk (free)`，说明管理员可允许用户自行选择并安装一组产品，不需管理员逐次操作。文档包含产品组配置、客户端参数、产品卡片、搜索、详情页和安装状态。

官方[集成自有软件文档](https://docs.opsi.org/opsi-docs-en/4.3/clients/windows-client/softwareintegration.html)要求将安装文件、元数据与 opsi-script 脚本打为产品包，上传服务器后分发。其例子包含静默 MSI/EXE 安装；这套模型适合将组织审核过的完整安装包放入内部仓库。官方[核心功能](https://opsi.org/en/product/features/)覆盖自动软件分发、配置管理、资产信息与多站点管理。

许可来源：

- [opsiconfd v4.3 pyproject.toml](https://github.com/opsi-org/opsiconfd/blob/v4.3/pyproject.toml)：`license = "AGPL-3.0-only"`。
- [opsi-client-kiosk LICENSE](https://github.com/opsi-org/opsi-client-kiosk/blob/master/LICENSE)：GNU Affero General Public License Version 3。

商业边界应保留：官网[扩展列表](https://opsi.org/en/product/extensions/)明确称其是 paid extensions，当前列有 Linux Agent、macOS Agent、Single Sign-On、User Roles、Audit Log、Directory Connector、WAN/VPN、License Management 等。不能因为自助 Kiosk 免费，就宣称跨平台代理、企业 SSO、分权与审计全部免费；也不能把这些当前商业功能自动归入上述已核验开源仓库的许可范围。官方还销售预打包标准软件和更新订阅，自己集成包与购买打包服务应分开。

## Munki：macOS 企业自助软件中心

官方[README](https://github.com/munki/munki)说明其基于 Web 服务器的软件包和元数据仓库，管理 macOS 软件安装/卸载，支持 Apple 安装包与拖放式磁盘镜像。官方[LICENSE.md](https://github.com/munki/munki/blob/main/LICENSE.md)明确 Apache-2.0。

官方[演示仓库与客户端配置](https://github.com/munki/munki/wiki/Demonstration-Setup)演示在本机 Apache 上建立 `catalogs`、`manifests`、`pkgs`、`pkgsinfo`、`icons`，用 `munkiimport` 导入完整 Firefox DMG，并创建测试 catalog 和客户端 manifest。因此，自建 HTTP(S) 仓库可以放在企业内部网络；不要求将安装程序留在公网下载源。

自助安装界面与配置可参考官方 [Managed Software Center 展示](https://github.com/munki/munki)、[manifest 文档的 optional_installs 与 catalog 配置](https://github.com/munki/munki/wiki/Manifests)及[可选/按需安装按钮文档](https://github.com/munki/munki/wiki/Customizing-Buttons-for-On-Demand-or-Optional-Installs)。manifest 文档还直接给出了 testing/production 目录分批验证和上线的例子。这里是管理员维护目录的部署工具，不是直接接入即拥有所有公共商店软件的 SaaS。

## UniGetUI：开源界面参考，内网能力取决于下层

当前官方[README](https://github.com/Devolutions/UniGetUI)明确它是 CLI 包管理器的图形界面，支持发现、安装、更新、卸载，当前仍 free、open source、MIT-licensed。官方[LICENSE](https://github.com/Devolutions/UniGetUI/blob/main/LICENSE)核实为 MIT。

README 明确包由第三方提供，UniGetUI 与其集成的包管理器并无隶属关系。因此应把“GUI 可管理软件包”与“平台自带企业内网仓库、身份、权限和审批”区分开。项目提供软件清单导出/导入、详情查看、批量操作、更新等可供参考的交互；如需用于内网，必须配置并验证底层 WinGet、Scoop、Chocolatey、pip、npm 等实际使用的内部源，检查包安装脚本和下载地址。

备注：当前项目已转由 Devolutions 维护，旧 `marticliment/UniGetUI` 地址可能重定向；引用和二次开发应使用当前官方仓库与当前许可证。

## WAPT：当前不能列入开源推荐

官方当前[基本概念文档](https://www.wapt.fr/en/doc/wapt-concept.html)原文：`WAPT exists in two versions, Discovery and Enterprise, both proprietary, the Community version having been friendly forked to the Opensource Community.`

官方当前[EULA](https://www.wapt.fr/en/doc/wapt-license.html)将软件称为 proprietary software；进一步限制源代码再利用和衍生作品，仅包含用于安全审计的源码提供说明。故不能因[历史 GitHub README](https://github.com/tranquilit/WAPT)写着 core GPLv3，而把当前 Discovery/Enterprise 推荐为开源。若将来单列 Community 分叉，须另外核实具体分叉名称、版本、许可证和维护状态，本次不扩展。

## 私有源与全离线的区别

私有仓库只表示目录或包源可由企业维护；全离线还要求完整安装程序、依赖、运行时、插件和升级文件均已进入内部网络，并且安装脚本不会再调用公网下载。对于必须联网激活的软件、Apple/系统更新、签名与证书检查、公开源自动更新等，也要分别验证。本次没有完成这些产品的实际断网安装测试，因此不写“开箱即用、全离线保证”。

对已有 WinGet 路线，官方 Azure 参考实现也应描述为参考架构，不能直接写成适用于纯内网的开箱即用服务器。本笔记不重复 WinGet/Scoop 的既有调研。
