# 前缀、代理、S3与真实大传输

**Status:** ready-for-agent

**Assignee:** unassigned

**Result:** not-run

**Blocked by:** 04,05

**Layer:** L2

## 前置和上下文

[软件更新代理](../../../docs/skillhub/guide/software-updates.md)、[部署与容量](../../../docs/26-software-center-deployment.md)。需真实Nginx/外层网关，根/两种前缀配置，LocalFile与S3两环境，专用盘和网络预算。5GiB实体与20路下载分别记录；只发请求头不算传输成功。

## 场景

- E07-1：`/`、`/hub/`、`/hub.v1/`实际UI/资源/API与全部更新入口。编码project/file/ref、空格/加号/Unicode一次解码，非法路径/双编码/遍历拒绝；真实上传和下载经过入口，不只直连8080。
- E07-2：GET/HEAD/Range/多区间/If-Range/ETag/日期条件、Content-Length/类型/原始字节；更新入口无SPA回退、错误状态不被吞、压缩/缓冲/缓存行为符合协议；目标回退和关闭源即时受治理。
- E07-3：插件stream import、软件辅助文件/机器chunks专用流式路径经代理实际发送超过全局100MiB但在业务限额内的合法实体；普通路径保留限额，外层网关也匹配。按压缩与展开预算生成合法样本，不拿zipbomb当正例。
- E07-4：真实5GiB单文件完成上传/中断续传/确认/完整下载摘要；实际超限拒绝，预留/已用/取消/失败/清理账目一致。磁盘和对象存储读写、heap不随包大小整体加载；记录实测，缺资源blocked。
- E07-5：20路真实受控下载，逐路状态/字节/摘要和稳定性，记录p50/p95/吞吐/资源及失败；不凭空制定未确认性能SLO，不为测试关闭配额/鉴权/限流。
- E07-6：LocalFile与S3分别执行关键闭环；S3断连/失败恢复不丢账、不提前公开，断开旧存储读取不能指向任意对象。Docker镜像/staging/release-config完整套、Helm lint/template按实际工具条件单独记录。

## 证据与清理

真实传输bytes/摘要/网络路径、代理配置及错误日志、存储对象/配额前后值、资源曲线、Docker/Helm命令退出码。可参考既有57项代理脚本，但其header-only探针不能替代E07-3/4。恢复本轮策略/服务，只清理本run对象。

## Execution log

尚未执行。领取时记录完整SHA、环境/runId、开始时间；结束追加实际命令、结果、证据、缺陷和清理，不覆盖首轮失败。
