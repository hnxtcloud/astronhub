# 软件上传识别与多应用更新源

**Status:** ready-for-agent

**Assignee:** unassigned

**Result:** not-run

**Blocked by:** 01

**Layer:** L1/L2

## 前置和上下文

[软件部署](../../../docs/26-software-center-deployment.md)、[更新指南](../../../docs/skillhub/guide/software-updates.md)、[Inspector](../../../inspector/README.md)。入口 `/admin/software`、`/software`。至少两个应用、两个机器版本、独立渠道/架构。手工ZIP可先跑；自动识别缺Inspector/合法样本时对应case blocked。

## 场景

- E04-1：UI创建应用/展示版本→分片上传（乱序/重传/冲突/中断续传）→确认/发布→浏览器受控下载；真实MSI/EXE/DEB/RPM识别任务及失败分支；ZIP/DMG人工元数据不标为自动识别或验签。
- E04-2：UI创建更新草稿、关联同应用/展示版本/OS/架构的已确认已发布包，上传原始YAML/JSON/blockmap/校验/说明；保留相对目录。bytes/摘要/版本/引用不符、活动上传未结束、缺Baize说明资产必须阻止发布。
- E04-3：Windows Electron显式NSIS确认，未选不能成功；Mac必须实际引用ZIP，DMG-only拒绝；Linux Electron拒绝。若验历史缺声明快照，需提供合法旧库/升级样本，不能直接改已发布资产来制造场景。
- E04-4：匿名默认关闭→管理员逐应用开启；显式选定目标才发现版本，不自动选最高版。多应用/channel/arch/stable-preview不串；关闭/清空/下架后发现与固定资产均按规则拒绝；指针回退不代表客户端必须降级。
- E04-5：实际GitLab tags/raw（编码project/file/ref）、generic和白泽清单；GET/HEAD/单区间/多区间及完整下载摘要一致，历史精确ref与固定snapshot按治理读取；缺失文件不能返回SPA HTML200。
- E04-6：发布后资产/同机器版本/tag不可替换，原始元数据字节不改写，投影URL含正确部署前缀；可变feed ETag/no-store，目标回退不能被旧日期条件错误304；固定文件条件与If-Range另验。

## 证据与完成条件

截图/trace、上传/检测/快照/channel ID、原始metadata与实际响应、完整下载SHA256/SHA512、治理前后状态。协议通过不等于真实安装；无样本/Inspector子项明确blocked，不能勾全票passed。

## Execution log

尚未执行。领取时记录完整SHA、环境/runId、开始时间；结束追加实际命令、结果、证据、缺陷和清理，不覆盖首轮失败。
