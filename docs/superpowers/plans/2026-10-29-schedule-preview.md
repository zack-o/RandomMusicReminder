# 保存前排程预览 Implementation Plan

> **Execution:** Follow this plan task by task using the tools available in the current Codex session. Steps use checkbox (`- [x]`) syntax. Use `verification-before-completion` to report evidence; no additional execution skill is required.

**Goal:** 在创建/编辑事项保存前，读取本机状态，预览未来90天内最多14条将安排的提示并解释未安排原因。

**Architecture:** 抽取保存前的安排元数据准备和日期资格判定，保存、Planner与只读预览共用。事务读取旧规则、原始实例、完成状态和日历，预览不写库、不排系统提醒、不生成随机种子；随机显示范围及补充条件，不承诺未保存的选定分钟。

**Tech Stack:** 现有Kotlin/Compose/Room/java.time，JVM17/min26/compile37/target36；不新增依赖、权限或数据库/备份版本。

**Spec:** `docs/design/2026-10-28-v4/通用事项提醒-功能规格.md`保存核对及预览部分。本轮不实现单日换班/生效版本，90天之外明确不在本次预览。

## Global Constraints

- 修改前快照`build/source-before-schedule-preview-20261029/source.zip`，保留既有工作，不提交/推送、不安装手机、不出声/试震、不联网AI，不重启模拟器。
- 固定/轮班复用真实Planner、现有规则身份与终态屏障；提前按时区真实时间回退，次日轮班按开始日归属，未知假期年份保留原规则并告知。
- 只预览当前草稿的原始提醒，不列旧稍后或TEST；停用事项明确不安排，随机未抽签不伪造具体时刻。快照变更后需刷新，保存仍以当时状态重新计算。
- UI沿用晨蓝/用户主题、系统中文字体，左对齐时间清单；先下一项、后其余提示和排除原因，显示时间范围/时区/生成时间。保留编辑器横滑和四主页面点击切换。

## Task 1: 共享规则与只读预览

**Files:** 新增domain/planner/SchedulePreparation.kt、ScheduleDateRules.kt、SchedulePreview.kt与data/repository/SchedulePreviewRepository.kt；修改EventRepository/OccurrencePlanner/AppContainer。

- [x] 抽取保存元数据准备与日期判断，共享并保持原保存行为；覆盖日历替代星期与月/年/间隔交集、轮班关闭原因。
- [x] 提供`SchedulePreviewEngine.preview(details,snapshot,now,zone)`；规则id按原offset复用，新草稿仅使用临时身份，排除终态/DONE/恢复及REOPEN边界。
- [x] 随机范围合并主/补充展示，不落盘抽签；事务读取快照，后台计算，限制90天/14提示/14排除原因。

## Task 2: 编辑器入口与结果页

**Files:** 修改AppViewModel/LexingApp/EventEditorDialog，新增ui/components/SchedulePreviewDialog.kt。

- [x] 保存和预览共用输入校验及草稿规范化，明确点击才计算，不因滚轮每次变化重算。
- [x] 全屏清单显示载入、错误重试、停用/空结果、范围及时区、随机条件与原因，返回编辑不丢草稿；预览页不提供隐式保存。

## Task 3: 验证和交付

- [x] JVM覆盖与实际Planner一致、规则身份/终态、跨日提前/轮班次日、节假日替代与未知年份、随机无精确承诺、停用/结束/密集上限。
- [x] Android只读仓库测试核对未改数据库/种子/动作与已有终态、UI载入/返回/重试/输入校验；只编译不称设备通过。
- [x] 离线JVM/Lint/Debug/Android测试编译和SQL19项，保留schema1～10，更新README/AGENTS/规格和验收，交付0.13.0及hash。

## 执行结果

2026-10-29：本轮代码、文档和0.13.0开发包已交付。165项JVM、19项SQLite通过，Lint0错误/19警告；Debug构建和新增7项Android用例编译成功。Android/界面只读断言未在设备运行，设备可靠性验收保持待办。详情见../../开发验收-0.13.0-2026-10-29.md。
