# 事项完成与记录 Implementation Plan

> **Execution:** Follow this plan task by task using the tools available in the current Codex session. Steps use checkbox (`- [x]`) syntax. Use `verification-before-completion` to report evidence; no additional execution skill is required.

**Goal:** 可选跟踪事项的每次完成，取消该次后续提示、保留下一周期，提供独立完成/撤销记录和CSV。

**Architecture:** 发生身份按eventId+日期（非轮班）或eventId+班次起始日+稳定slot key定义，提醒点与稍后共用身份。持久化task_instances避免未完成事项随旧排程清理而消失；task_actions追加DONE/REOPEN，最新id决定状态，独立于响铃结果。Planner与落盘前均检查完成屏障。Room10只增列/表，JSON7保存跟踪开关，记录不混入配置备份。

**Tech Stack:** 现有Kotlin/Compose/Room/java.time，JVM17/min26/compile37/target36，无新增依赖或权限。

**Spec:** `docs/design/2026-10-28-v4/通用事项提醒-功能规格.md`处理记录章节；本轮跟踪为每事项默认关闭的可选开关，开启后从本机今天起记实例。

## Global Constraints

- 修改前快照`build/source-before-task-completion-20261028/source.zip`。保留既有工作，不提交/推送、不安装手机、不出声/试震、不联网AI；未获授权不重启模拟器。
- 停止、确认收到和事项完成分开，TEST不可完成；完成只作用于一个发生身份，不取消其他轮班时刻或同刻事项。
- 撤销需核对最新DONE动作id，仅恢复当前规则的未来提示，不补响过去、旧稍后或已开始的随机窗口；保留操作记录。
- 完成记录不自动删除。事项永久删除后操作快照仍留；回收项不能完成或撤销，恢复停用后可核对再操作。
- UI沿用现有晨蓝/用户主题、左对齐清单；完成核对明确事项、发生日、轮班时刻、取消范围。主导航仍四页，记录入口放设置。

## Task 1: 身份、跟踪与持久化

**Files:** 新增domain/tasks/TaskCompletion.kt、data/local/entity/TaskEntities.kt、dao/TaskDao.kt；修改Models/Entities/Mappers/DatabaseMigrations/AppDatabase/EventRepository/OccurrencePlanner/BackupCodec。

**Interfaces:** `TaskIdentity.day(date)`、`shift(date,slotKey)`；`TaskState(action,at)`；`TaskChoice(eventId,key,baseDate,label,taskAt,eventUpdatedAt,pendingCount)`；`TaskRepository.choices(eventId,range)`、`complete(choice)`、`reopen(actionId)`、`observeRecords()`。

- [x] 新增跟踪开关与本机起始边界；旧事项默认关闭，配置JSON7读1～6，仅携带开关，不携带记录/本机边界。
- [x] Planner生成task key/label/nominal time；同一日期多提前点共用，轮班每slot独立，跨午夜仍用班次开始日；SNOOZE继承、TEST排除。
- [x] Room10增加events.trackCompletion/completionTrackingStartedAt、scheduled_occurrences.taskOccurrenceKey；新增task_instances（跟随事项删除）及task_actions（无FK历史），保留schema1～9。
- [x] 旧非轮班按baseDate回填key；旧轮班由稳定UUID与slot匹配，无法匹配不猜测。开启跟踪后补录今天起的已有可识别计划。

## Task 2: 完成、撤销与排程

- [x] 完成在事务内校验事项版本/实例，幂等追加DONE，取消同key的SCHEDULED及其稍后，把同key RINGING标已处理；其他key不动。最后一个RINGING完成时明确以TASK_COMPLETED结束会话历史。
- [x] Planner接收最新task states；DONE不排程，REOPEN只接纳操作后时刻且不恢复已开始随机窗口。落盘同事务再校验，防止旧计划复活。
- [x] 撤销只接受最新DONE id，追加REOPEN，重排当前规则；已响记录不复活，未开始的未来取消实例可恢复。
- [x] 未完成实例独立留存；选择器支持今天及未来/近期逾期/最早逾期，每区最多60次，已DONE过滤。

## Task 3: 播放会话与界面

**Files:** 修改PlaybackContract/ReminderPlaybackService/AppContainer/AppViewModel/UiModels/LexingApp/ReminderScreen/RingActivity；新增TaskCompletionDialog与CompletionHistoryScreen。

- [x] 完成变化通知正在运行的服务：剩余RINGING保留，去掉已完成来源；需要时重建输出，不重复认领、不结束其他任务，保持原会话入口与剩余时限。普通完成不启动服务。
- [x] 编辑器提供“记录待办完成”；列表/详情及提醒页仅对已开启事项提供完成选择；两步核对，不一键默认完成混合组。
- [x] 设置加入完成记录，展示DONE/REOPEN快照、搜索/筛选、最新DONE保护性撤销；最近1000条显示，CSV最多导出最近10000条并明确范围。

## Task 4: 验证与交付

- [x] JVM测试覆盖日期/slot身份、完成屏障、撤销时间边界、未来周期与提前点不串联、随机旧窗口；SQL10迁移核对旧字段、默认值、FK及追加动作最新状态。
- [x] Android编译用例覆盖完成/幂等/多提醒点/独立轮班/稍后/撤销与旧计划写入、历史不随事项删除、UI确认；只编译不称运行通过。
- [x] 执行离线testDebugUnitTest/lintDebug/assembleDebug/compileDebugAndroidTestKotlin与SQL脚本，更新README/AGENTS/规格/验收，交付0.12.0开发包并核对hash/链接。

## 执行结果

2026-10-29：完成本轮代码、文档与离线交付。146项JVM、19项SQLite通过，Lint0错误/19警告；Debug构建和新增13项Android测试编译成功。Android用例未运行；设备仍因先前启动超时待授权处理，不能视作设备验收完成。交付与限制见../../开发验收-0.12.0-2026-10-29.md。
