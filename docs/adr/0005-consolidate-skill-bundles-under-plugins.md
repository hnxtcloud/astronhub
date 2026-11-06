# 将多技能组合交付收敛到 Plugin

状态：已确认；2026-11-06 用户调用 implement 后开始实施。决策确认日期：2026-10-20。

决策跟踪：[Suite 退出 Wayfinder](../../.scratch/suite-retirement/map.md)。

Claude Code 原生 Plugin 可以仅包含一个或多个 Skill，因此不能以“Suite 仅含 Skill、Plugin 包含其他组件”区分两者。对于将多个技能作为一个产品发布、安装和更新的场景，AstronHub 默认采用 Plugin。Suite 不作为必须保留的独立产品能力；本轮访谈确认可放弃其组合独立技能精确版本的专用产品能力，以减少重复概念和发布流程。依据见[官方规范核查](../research/claude-code-skill-only-plugins.md)。

继续支持 Claude Code、Codex、Pi，沿用 [ADR-0003](0003-model-cross-runtime-plugin-distributions.md) 的 Plugin Project、Release、Distribution 关系，由对应宿主发行物承载原生安装格式；不推定同一个插件包能跨宿主安装。

第一阶段停止接收新的 Suite 创建、编辑、新版本和导入请求，移除前台主动入口，保留历史记录和过渡兼容。不直接清库，不自动将历史 Suite 转为 Plugin，旧接口的最终清理属于后续工作。

过渡期保留旧详情链接、历史读取以及 CLI 对已有发布版本的安装、检查和升级；保留旧 CLI 安装来源记录及本地卸载能力。原有权限、成员可用性检查和下架治理继续生效，旧页面展示停用提示并引导新内容通过 Plugin 发布。

切换前已提交审核的 Suite 版本和已确认执行的批量导入任务，可以完成既有流程的最后一次发布。允许此类导入任务产出的 Suite 草稿完成对应审核和发布，但不据此开放任意新草稿或新版本。尚未提交、也不属于已确认导入任务的草稿保留供查看，不再提交；新编辑、提交和导入请求受停写策略约束。收尾流程继续遵守原有成员审核、权限和状态约束。

首期由发布者提供 Claude Code、Codex、Pi 对应的原生插件包，平台沿用插件校验、审核和分发流程。一个插件版本可以只支持部分宿主；继续支持三个宿主不代表每个版本必须同时提供三份发行物。首期不新增从 Skill 集合自动生成插件包的能力，后续如有需求独立评估。

实施范围和验收条件见 [Suite 分阶段退出实施说明](../suite-retirement-plan.md)。历史 Suite 协议继续兼容。本记录不授权清除数据，实现与验证状态见 Wayfinder 的独立实施记录。
