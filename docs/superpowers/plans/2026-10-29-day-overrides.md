# 单日临时调整 Implementation Plan

> **Execution:** Follow this plan task by task using the tools available in the current Codex session. Steps use checkbox (`- [x]`) syntax. Use `verification-before-completion` to report evidence; no additional execution skill is required.

**Goal:** 事项可跳过一个指定日期，轮班可替换该班次日的时刻，可恢复原规则，其他日期周期保持。

**Architecture:** Event保存可移植的dayOverrides及本机per-date时间屏障；Planner、预览、入库和稍后均核对日期配置/签名。恢复移除覆盖但保留本机屏障，随机当前窗口不重抽、不补响，已有终态和完成身份继续保护。Room11增列，JSON8读1～7，编辑器集中管理，先改草稿再保存。

**Tech Stack:** 现有Kotlin/Compose/Room/java.time，无新依赖或权限，JVM17/min26/compile37/target36。

**Spec:** `docs/design/2026-10-28-v4/通用事项提醒-功能规格.md`轮班单日覆盖及周期相位。本轮是单日调整，不实现日期区间、未来生效版本或自动下一次跳过。

## Global Constraints

- 快照`build/source-before-day-overrides-20261029/source.zip`；保留未提交工作，不提交/推送、不安装手机、不出声/试震、不联网AI、不重启模拟器。
- SKIP适用于所有重复类型；REPLACE_SHIFT仅限固定轮班。开始/结束及日历过滤仍适用；替换仅影响所选开始日及其跨日提前/次日提示，原周期不移动。
- 原有slot key在修改时刻时保留，显式新增时刻另设key；保留已处理实例和DONE，不因覆盖变更复活。当前RINGING不强制结束；修改日期的待触发原提醒/稍后取消，不能由旧计划复写。
- 恢复原规则保留每日期本机屏障，跳过过去与已开始随机窗口；可移植备份不带本机屏障。随机其他日期种子/选择不受跳过影响。
- UI沿用晨蓝/用户主题，在重复页列原周期与单日调整，确认范围后仅写草稿，预览/保存仍明确分开。

## Task 1: 领域、保存和持久化

- [x] 新增DayOverrides领域/Codec，验证日期、种类、稳定时刻key、上限与判重；共享准备计算变更日期的本机下界。
- [x] Room11增events.dayOverridesJson/dayOverrideBarriersJson与scheduled.dayOverrideSignature，保留schema1～10，JSON8仅携带覆盖配置，默认兼容旧版；同步映射、导入判重与预览。
- [x] Planner/日期资格/轮班有效日使用覆盖；入库核对当前签名/屏障；保存取消所改日期SCHEDULED，旧稍后不能复活，正在提示保留。

## Task 2: 界面和原规则恢复

- [x] 编辑器重复页管理最多100个单日调整：跳过、编辑轮班日、确认后恢复原规则；日期归属与日历边界明确，修改留在草稿。
- [x] 保存/预览使用覆盖，列表摘要和导入确认显示；复用已有时分秒/轮班日编辑器，AI可保留但不新增此类修改。

## Task 3: 验证与交付

- [x] JVM覆盖相位不动、次日/提前归属、休息日替换、日历优先、身份与DONE、恢复时间、随机稳定与不同日期隔离。
- [x] Android编译用例覆盖迁移/备份/回收、旧计划签名拒绝、旧稍后、草稿确认与恢复；未运行不能称设备通过。
- [x] 离线JVM/Lint/Debug/Android测试编译、SQL10→11、旧schema/文档链接/hash核对；更新使用说明/README/AGENTS/规格并交付0.14.0开发包。

## 执行结果

2026-10-29：完成本轮代码、文档与0.14.0开发包。180项JVM、20项SQLite通过，Lint0错误/19警告；Debug和新增8项Android测试编译成功，设备未运行。增加生产认领到时检查以拦截改晚同ID的旧广播；该设备时序链路仍待验收。详见../../开发验收-0.14.0-2026-10-29.md。
