# AstronHub 能力分发

AstronHub 中用于描述 Agent 能力及其组合分发的产品术语。

## Language

**插件（Plugin）**：
面向 Agent 宿主分发的一组能力，可以仅包含一个或多个 Skill，也可以包含 MCP 配置、Hook、Agent 等其他组件。
_Avoid_: 将“必须包含非 Skill 组件”作为插件的定义。

**插件版本（Plugin Release）**：
一个插件在特定版本下发布的能力集合，可以提供一个或多个宿主对应的发行物。

**宿主发行物（Plugin Distribution）**：
插件版本针对特定 Agent 宿主提供的原生安装包；不同宿主的发行物不默认具有格式或运行兼容性。
_Avoid_: 通用插件包。
