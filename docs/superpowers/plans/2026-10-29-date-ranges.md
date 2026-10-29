# 连续日期暂停与恢复 Implementation Plan

> **Execution:** Follow this plan task by task using the tools available in the current Codex session. Steps use checkbox (`- [x]`) syntax. Use `verification-before-completion` to report evidence; no additional execution skill is required.

**Goal:** 一次选择起止日期暂停多天提醒，并可按区间恢复跳过，保留其他安排和周期相位。

**Architecture:** 区间操作在草稿中展开为既有SKIP日期，保存仍复用0.14日期签名与本机时间下界；Room11/JSON8不变。纯领域计划记录before/after/影响日期，确认时重新核对原草稿。默认保留REPLACE_SHIFT，显式选择才覆盖；恢复只移除SKIP，不找回已被覆盖的临时班次。

**Tech Stack:** 现有Kotlin/Compose/java.time，JVM17/min26/compile37/target36，无新增依赖、权限或数据库迁移。

**Spec:** `docs/design/2026-10-28-v4/通用事项提醒-功能规格.md`日期区间请假方向。本轮仅当前事项，不扩展到批量事项、未来生效版本或自动跳过下一次。

## Global Constraints

- 快照`build/source-before-date-ranges-20261029/source.zip`，保留未提交工作，不提交/推送、不安装手机、不出声或试震、不联网AI，不重启模拟器。
- 起止含两端，按事项/班次开始日归属，暂停最多100个日历日且合并后不超过100个调整日期，超限整段拒绝；单次只允许其发生日期。恢复可跨当前起止以清理旧SKIP，不受100天展开限制。
- 默认保留已有临时班次，确认页展示冲突/新增/已跳过/保留数量；显式覆盖后恢复只回原规则，不回旧临时班次。恢复只清SKIP，其他日期及REPLACE_SHIFT保留。
- 先核对加入草稿，再保存生效；不直接写业务库。过时确认拒绝，不移动周期、不清待办、不结束RINGING，恢复不补过去/旧稍后/已开始随机窗口。
- UI沿用晨蓝/用户主题与系统中文字体，独立全屏区间编辑，支持日期输入/选择、操作说明、长内容滚动与确认。

## Task 1: 纯领域区间计划

**Files:** 新增domain/planner/DayOverrideRanges.kt及DayOverrideRangesTest.kt。

- [x] 校验区间/起止/单次/100天和合并配额；生成只读before/after及影响计数，保留原slot keys。
- [x] 提供暂停、仅恢复SKIP与确认版本检查，超限/过时整段不应用；验证预览/Planner/准备边界沿用且周期相位不变。

## Task 2: 区间编辑界面

**Files:** 新增ui/components/DayOverrideRangeDialog.kt，修改DayOverrideEditor入口与版本。

- [x] 起止输入和日历、暂停/恢复选择、冲突策略、确认影响清单，错误就地提示；无变更不能误称成功。
- [x] 保留单日管理、返回不保存、只有确认才修改父草稿；导入/备份继续使用已验证的日期数组，说明不保存区间分组。

## Task 3: 验证与交付

- [x] JVM覆盖含两端/闰日/跨年、冲突与配额、恢复范围/不删班次、旧确认、次日/提前/相位与未来边界。
- [x] Android仓库与UI用例编译，明确未运行；离线JVM/Lint/Debug/Android编译、既有20项SQL、旧schema1～11校验。
- [x] 更新README/AGENTS/规格/使用说明/验收与版本，交付0.15.0开发包、核对hash/链接。

## 执行结果

2026-10-29：完成0.15.0区间暂停/恢复、文档与开发包。194项JVM、20项SQLite通过，Lint0错误/19警告，Debug及新增6项Android测试编译成功；设备未运行。Room11/JSON8不变，schema1～11基线匹配。详情见../../开发验收-0.15.0-2026-10-29.md。
