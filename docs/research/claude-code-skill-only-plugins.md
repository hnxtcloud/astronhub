# Claude Code 纯 Skill 插件与 Skill Suite 的重叠核查

日期：2026-10-20。范围：Claude Code 当前官方文档与本仓库已有定义；没有修改产品代码或模型设计。本次未运行 Claude Code 实机安装，以下上游行为属于官方文档核查。

## 结论

**Claude Code Plugin 可以只含一个或多个 Skill，无须同时包含 Agent、Hook、MCP 或其他组件。** 官方入门教程直接构建单 Skill 插件，称其 “only component is one skill”，并明确各组件 “none is required”。多个 Skill 分别放进 `skills/<名称>/SKILL.md` 即可。[官方创建教程](https://code.claude.com/docs/en/plugins/create#create-your-first-plugin)、[组件规范](https://code.claude.com/docs/en/plugins/components#skills)。

因此，“Suite 只含 Skills，而 Plugin 含其他类型组件”不能作为两者的互斥定义。面向 Claude Code 的多个技能组合分发、整体安装场景，二者存在实质重叠。这是根据官方插件能力与本地 Suite 定义得出的产品判断，并非 Claude Code 官方对 SkillHub Suite 的评价。

## 官方规范事实

### 最小目录与命名空间

下面是采用标准目录的纯 Skill 插件示例；它没有 Hook、MCP、Agent 文件：

```text
engineering-tools/
├── .claude-plugin/
│   └── plugin.json
└── skills/
    ├── review/
    │   └── SKILL.md
    └── testing/
        └── SKILL.md
```

示例 manifest：

```json
{
  "name": "engineering-tools",
  "version": "1.0.0"
}
```

Skill 按插件名加前缀调用，如 `/engineering-tools:review`、`/engineering-tools:testing`。技能前言的 `name` 可以替换技能名称部分，插件名前缀仍保留。这提供插件间同名技能隔离。[命名规则](https://code.claude.com/docs/en/plugins/components#skills)。

严格来说，以上不是上游运行时的绝对最小目录。官方 manifest 参考明确 “The manifest is optional.”；缺省时按标准位置发现组件，从 marketplace 条目或 `--plugin-dir` 目录取得插件名。若提供 manifest，`name` 是唯一必填字段，`version` 可省略。标准布局还允许根目录 `SKILL.md` 作为单 Skill，但要求没有 `skills/` 目录或 `skills` manifest 字段。不同自动加载方式可能额外要求 manifest，不能把这项运行时能力理解为所有加载路径都不需要它。[manifest 规则](https://code.claude.com/docs/en/plugins-reference#manifest-file)、[字段表](https://code.claude.com/docs/en/plugins-reference#fields)、[标准布局](https://code.claude.com/docs/en/plugins-reference#standard-layout)。

### 安装、更新与宿主范围

插件作为一个单位安装和加载；marketplace 提供插件发现与获取位置，也可用 `--plugin-dir` 直接加载本地目录或 ZIP。后者仅对该会话生效。[插件概述](https://code.claude.com/docs/en/plugins)、[本地加载](https://code.claude.com/docs/en/plugins/create#develop-without-a-marketplace)。

Claude Code 提供原生插件安装、启停、卸载及更新命令。自动更新是否开启取决于 marketplace 设置；第三方 marketplace 默认关闭。当前会话继续使用已加载版本，更新后通过 `/reload-plugins` 或新会话应用。[官方安装管理和更新规则](https://code.claude.com/docs/en/discover-plugins#keep-plugins-updated)。

上述规范不能证明同一个 Claude Code 原生插件包能被其他 Agent 宿主直接安装。官方说明同一插件格式也适用于 claude.ai 和 Cowork，但各表面的组件支持不同；插件名称空间和本机安装设置也有 Claude Code 自身语义。[官方宿主范围说明](https://code.claude.com/docs/en/plugins#understand-install-scopes)。跨 Codex、Pi 等宿主是否可用，需要分别验证其格式和组件支持，不能从“纯 Skill 内容相同”直接推导出“原生插件包通用”。

## 与仓库现有模型的比较

本仓库 [Suite 定义](../25-skill-suites.md)将 Suite 定义为引用已发布 Skill 精确版本的集合，固定成员 Skill ID、SkillVersion ID、坐标、版本和 fingerprint。成员可独立发布、复用和安装；CLI inventory 追踪直接安装及 Suite 安装来源，移除 Suite 不应破坏仍被其他来源使用的成员。这些是当前本地模型与安装行为，并非上游 Plugin 的组成限制。

本仓库 [Claude ZIP 校验器](../../server/skillhub-app/src/main/java/com/iflytek/skillhub/service/plugin/ClaudePluginZipValidator.java)同样没有强制要求非 Skill 组件：`skills`、`agents`、`commands`、`hooks`、`mcpServers` 等字段均可缺省。不过它要求提供 manifest，名称须符合格式，且 `version` 须与上传版本一致，即本地发行物校验比上述上游最小运行时格式更严格。

**产品建议（尚未实施）：** 不再用“仅技能 / 多类型组件”作为 Suite 和 Plugin 的核心分界。若保留 Suite，应明确它解决的是仓库内已治理技能的精确版本组合、独立复用与安装归属；Plugin 解决宿主原生格式的整体分发。若主要目标仅为 Claude Code 的多个 Skill 一键交付，应优先评估用纯 Skill Plugin 承载，避免要求用户为同一目标理解两套资源。也可研究把 Suite 作为组合源并生成 Claude 插件发行物，但这是后续设计选择，当前没有实现或迁移。
