# AstronHub

星枢（AstronHub）是面向组织的自托管 Agent 能力注册与发现平台。它统一呈现 Skill、Plugin 和 MCP Catalog，但不是 Agent 运行时或 MCP 网关。

## Language

**AstronHub（星枢）**:
产品及平台的统一名称，覆盖 Skill、Plugin 和 MCP Catalog 三类能力。
_Avoid_: SkillHub（作为整个产品的名称）

**Agent 能力（Agent Capability）**:
Skill、Plugin 和 MCP Server Entry 在产品导航、发现与搜索层的统称；它不是三类对象共享的领域实体或生命周期。
_Avoid_: Agent 资源、扩展、Capability 基类

### Capability Domains

**Skill**:
遵循现有 SkillHub 协议、版本与治理规则发布的可复用 Agent 指令包；其既有领域逻辑保持独立且不因平台扩展而改变。

**Plugin Project**:
面向一个或多个 Agent Runtime 提供可安装扩展的跨运行时逻辑项目；产品界面可以简称为 Plugin。
_Avoid_: Runtime Plugin、Plugin Distribution

**Plugin Release**:
Plugin Project 的一个版本，可包含一个或多个面向特定 Agent Runtime 的发行物。
_Avoid_: Plugin Version

**Plugin Distribution**:
Plugin Release 面向特定 Agent Runtime（例如 Codex、Claude Code 或 Pi）的原生发行物。发行物可以由星枢托管，或指向内容固定且可校验的外部来源。
_Avoid_: Plugin、Plugin Release

**Component Reference**:
Plugin Distribution 对独立 Skill 或 MCP Server Entry 的显式、非所有权关联；它不会自动创建目标条目，也不会让两者共享生命周期。
_Avoid_: Bundled Component、自动导入

**MCP Catalog**:
用于发现 MCP Server Entry 及其连接说明的目录；它不代理、部署或监测 MCP Server。
_Avoid_: MCP Gateway、MCP Registry

**MCP Server Entry**:
MCP Catalog 中描述一个 MCP Server 的条目，包含能力说明、传输方式、来源、支持的客户端、文档链接和不含密钥的配置模板。
_Avoid_: MCP Instance、MCP Connection

**Connection Profile**:
MCP Server Entry 提供的无密钥连接配置模板，供用户复制，或供未来 MCP Gateway 创建运行连接。
_Avoid_: MCP Connection、Credential

**MCP Gateway**:
未来负责 MCP 运行时连接与治理的独立能力，可以消费 MCP Catalog 的条目，但不属于当前 MCP Catalog 的职责。
_Avoid_: MCP Catalog

**MCP Connection**:
未来由 MCP Gateway 管理的运行连接，引用一个 MCP Server Entry 和 Connection Profile，并持有特定环境的参数、凭据与运行状态。
_Avoid_: MCP Server Entry、Connection Profile

**Agent Runtime**:
安装或加载 Plugin Distribution 的宿主产品，例如 Codex、Claude Code 或 Pi。
_Avoid_: Agent、Plugin

**Installation Recipe**:
面向特定 Agent Runtime 的可复制安装命令或配置说明；它描述安装但不代表星枢已经在用户设备上执行安装。
_Avoid_: Remote Installation、Installed Plugin

**Capability Coordinate**:
由能力类型与现有 Namespace 坐标共同确定的标识，统一场景写作 `skill:@namespace/slug`、`plugin:@namespace/slug` 或 `mcp:@namespace/slug`；不同能力类型可以使用相同 slug。
_Avoid_: 跨类型全局唯一 slug

### Governance and Discovery

**Plugin Admin**:
负责平台级 Plugin 治理的角色，不继承或扩大 Skill Admin 的职责。
_Avoid_: Skill Admin、Capability Admin

**MCP Admin**:
负责平台级 MCP Catalog 治理的角色，不继承或扩大 Skill Admin 的职责。
_Avoid_: Skill Admin、Capability Admin

**MCP Entry Revision**:
MCP Server Entry 的不可变发布修订；未来的 MCP Connection 固定引用一个已发布修订，且不会随新修订自动升级。
_Avoid_: Mutable MCP Entry、MCP Connection Version

**Catalog Search**:
在展示层聚合 Skill、Plugin 和 MCP 独立搜索结果的统一发现入口；它不拥有通用 Capability 索引或实体。
_Avoid_: Unified Capability Index

**Security Assessment**:
针对某一发行物执行并留下证据的安全检查结果；人工发布审核不是 Security Assessment。
_Avoid_: Review Approval、Verified Safe

**Distribution Availability**:
Plugin Distribution 当前能否从托管副本或外部来源取得并通过完整性校验的独立覆盖层；它不改变 Plugin Release 的发布状态。
_Avoid_: Plugin Release Status、Runtime Compatibility

**Runtime Compatibility Evidence**:
支持某个 Agent Runtime 的声明、清单验证或实际测试证据；静态清单验证不等于经过运行测试。
_Avoid_: Security Assessment、Distribution Availability

**Audit Tombstone**:
资源因恶意内容、合规或法律要求被硬删除后保留的最小审计记录，不包含已删除的发行物或非必要展示数据。
_Avoid_: Archived Resource、Soft Delete

**Registry Source**:
向 MCP Catalog 提供标准元数据及其来源身份的外部目录。
_Avoid_: Mandatory Upstream、Web Crawler

**Source Snapshot**:
从 Registry Source 或导入文件取得的不可变 MCP 原始元数据；上游变化产生新快照，而不覆盖本地治理信息。
_Avoid_: Local Curation、Mutable Import

**Local Curation**:
星枢为 MCP Server Entry 维护的分类、标签、可见性、审核结论和展示补充，与 Source Snapshot 分离。
_Avoid_: Source Snapshot、Upstream Metadata

**Credential Reference**:
未来由 MCP Gateway 使用的私密凭据引用；MCP Catalog 的 Connection Profile 只能声明变量和占位符，不能持有凭据值。
_Avoid_: Credential Value、Connection Profile
