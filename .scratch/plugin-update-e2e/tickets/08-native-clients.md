# 原生CLI和真实应用更新验收

**Status:** ready-for-agent

**Assignee:** unassigned

**Result:** not-run

**Blocked by:** 03,04,05

**Layer:** L3

## 前置和上下文

[客户端与协议边界](../../../docs/28-software-release-update-channels.md)、[机器发版/首次迁移](../../../docs/software-machine-publishing.md)。用户需提供可重置测试终端、已装旧版和新旧真实完整产物/签名验证材料、原生CLI版本及内网认证/CA。原始客户端仓库只读，缺新迁移构建不改源仓库凑测试。

## 场景

- E08-1：隔离配置下按AstronHub生成的固定指引真实安装Claude/Codex/Pi，原始清单/Skill字节一致；发布第二版后刷新/更新，旧Gitcommit/npm固定版仍可取得；namespace市场身份正确，不改正常CLI配置或公开npm回退。
- E08-2：Intelli保留GitLab provider/tags/raw/本地proxy的真实应用从旧版发现正确目标→下载→Windows NSIS或macOS ZIP更新→重启后版本/数据/签名正确；分别记录OS/架构/provider和产物摘要，不把HTTP惰性ZIP探针算安装。
- E08-3：白泽使用实际feed配置发现/校验/下载，按现有维护窗口安装并复验；不要求或宣称新增无人值守安装能力。
- E08-4：用实际旧版blockmap与新版产物验证真正差分路径；再受控制造blockmap不可达/无效等故障，验证实际完整下载fallback成功且最终摘要/签名/版本正确。两条路径各有字节请求证据，不只证明Range端点可读。
- E08-5：首次来源迁移使用用户提供新构建或原域名测试代理，实际已装客户端转向AstronHub；不能仅改测试脚本URL宣称已迁移。关闭源/撤出目标不能导致跨应用或越权下载；不强制客户端降级。

## 证据与清理

终端快照/OS/架构/CLI/更新器版本、前后版本、实际应用日志、差分/全量请求字节及摘要、签名/证书验证结果、重启后观察。重置专用测试终端、清理本轮CLI配置并保留证据。不得在开发者日常系统/生产终端安装，不提供签名私钥；只有真实已签名构建及验证材料才可形成签名验收结论。

## Execution log

尚未执行。领取时记录完整SHA、环境/runId、开始时间；结束追加实际命令、结果、证据、缺陷和清理，不覆盖首轮失败。
