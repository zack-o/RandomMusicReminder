# 乐醒 v3 Implementation Plan

> **Execution:** Follow this plan task by task using the tools available in the current Codex session. Steps use checkbox (`- [x]`) syntax. Use `verification-before-completion` to report evidence; no additional execution skill is required.

**Goal:** 交付时间窗随机一次、第三方AI工作台与v3界面，并保留已有数据、固定多提醒点和离线能力。

**Architecture:** 在现有Kotlin单模块中增量扩展。随机窗口的提醒参数存于事件，固定提醒点继续保留；每个实例保存选定当地分钟和窗口边界，排程恢复使用原选择。AI继续通过本机保存的Key调用用户选定服务，所有发送和修改逐次核对。

**Tech Stack:** Android、Kotlin、Jetpack Compose、Room、DataStore、已有HTTP客户端、JUnit；不新增或升级依赖。

**Spec:** `docs/design/2026-10-27-v3/随机时间与AI-详细功能规格.md`、`时间窗随机提醒-证据与产品判断.md`，基础体验承接v2。

## Global Constraints

- JVM 17，compileSdk 37、targetSdk 36、minSdk 26，沿用项目Gradle与依赖缓存。
- 不清库、不重建用户表，不改包名，不读取或输出实际Key，不自动提交、推送、安装手机或有声测试。
- 现有未提交源码已快照至 `build/source-before-v3-20261027-183527/source.zip`；回退按文件比较恢复本轮改动，不使用git reset/clean。
- 主导航不横滑，编辑器四分区可点击/横滑；离开音乐停止试听。
- 随机主提醒默认严格1次，稍后和固定补充默认关闭；停止声音不等于已起床。
- 真实API/费用测试另获授权；普通验证只使用合成响应。

## Task 1: 随机规则与数据升级

**Files:** `domain/model/Models.kt`、新增`domain/planner/RandomWindow.kt`、`OccurrencePlanner.kt`、`Entities.kt`、`DbConverters.kt`、`AppDatabase.kt`、`DatabaseMigrations.kt`、`Mappers.kt`、`EventRepository.kt`、`ScheduleReconciler.kt`、`Daos.kt`。

**Interfaces:** `TimingMode.FIXED/RANDOM_WINDOW`；`ReminderEvent`增加窗口与随机参数；`EventDetails.activeRules`生成当前有效提醒参数，保留固定配置；`PlannedOccurrence/ScheduledOccurrence`增加选定当地分钟、窗口起止。`OccurrencePlanner.plan`接收已有实例与随机规划起点。

- [x] 建立纯规则测试：端点11分钟、跨午夜16分钟、窗口内首次创建、固定种子稳定、已触发不重排、DST缺失/重复分钟、终止实例不复活。
- [x] 增量Room6迁移，为事件/实例/历史增加字段；旧字段与schema1～5保留。
- [x] 让保存生成随机种子，排程使用已选分钟；时间方式修改取消旧未来安排，暂停/恢复不重新抽取。
- [x] 随机实例不使用旧15分钟补响宽限；固定提醒保持原有策略。
- [x] 验证示例：`assertEquals(11, RandomWindow.candidates(...).size)`；重启规划应满足`assertEquals(first, restored)`。

## Task 2: 备份、编辑、日历与历史闭环

**Files:** `BackupCodec.kt`、`TransferRules.kt`、`UiModels.kt`、`AppViewModel.kt`、`EventEditorDialog.kt`、`ReminderScreen.kt`、`CalendarScreen.kt`、`RingHistoryScreen.kt`、播放相关模型与界面。

**Interfaces:** 备份版本3读取兼容1/2，保存固定配置与窗口；编辑草稿增加时间方式、窗口边界、一次主提醒参数。时间窗的摘要、历史和核对页使用相同的定义。

- [x] JSON往返与判重测试覆盖窗口字段、旧备份默认固定、无效跨日/同刻拒绝。
- [x] 增加固定/随机选择、跨日、剩余窗口确认与保存核对；切换模式保留原提醒点。
- [x] 首页范围/已选时间分开显示，日历能解释窗口，响铃/历史记录来源和实际时间。
- [x] 实现可选稍后和固定补充的显式语义；所有额外提醒关闭时严格一次。
- [x] 测试示例：`assertEquals(original, decode(encode(original)))`，种子/系统排程不作为可重放的普通备份内容。

## Task 3: 第三方连接与AI草稿

**Files:** `AiConfig.kt`、`AiSettingsStore.kt`、`AiClient.kt`、`AiDraftCodec.kt`、新增预设/任务与变更模型、`ToolsViewModel.kt`、`AiScreen.kt`。

**Interfaces:** 服务商预设决定接收方/地域/默认模型；Key与档案绑定。AI请求具有明确任务与发送上下文；随机窗口草稿通过本地规则校验，不能把一次解析成多个提醒点。

- [x] 纯协议测试覆盖预设地址、地域、随机窗口解析、歧义与无效响应；Android协议测试使用固定响应，不联网。
- [x] 简化预设Key配置，保留自定义服务；保存不请求，测试先确认，失败不跨服务重试。
- [x] 支持创建、所选修改、规则解释、冲突检查、音乐元信息建议、导入原因与匿名统计，各自核对发送范围。
- [x] 应用变更前核对旧值、作用范围和当前版本；成功修改保存操作记录、失败在页面反馈，提供所选修改的保护性撤销，避免覆盖之后的手动修改。

## Task 4: 界面与交互

**Files:** `LexingApp.kt`、`EventEditorDialog.kt`、`ReminderScreen.kt`、音乐/设置/AI屏幕、`LexingTheme.kt`与外观默认值。

- [x] 移除主页面Pager，使用显式栏目与状态保存；只在编辑器保留Pager，分别保存滚动位置。
- [x] 落地雾蓝、时间层级、分组行、主要按钮、空白/失败/忙碌状态，保留已保存主题选择。
- [x] 新建、详情、编辑和核对形成闭环，复杂参数通过分区与单项面板呈现。
- [x] 编译界面与Android测试，修正原先依赖主页面横滑的测试，新增时间窗操作断言。

## Task 5: 验收与交付

- [x] 使用现有JDK与离线缓存：`gradlew --offline testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --console=plain`。
- [x] 执行`python scripts/verify_history_migration.py`，增加Room6迁移核对与测试编译。
- [x] 若使用已有模拟器，仅明确目标、无音频、合成数据；未执行的设备/真机检查不声称通过。
- [x] 更新README、AGENTS、版本与验收记录，列明已实现、未实现和未验证的边界；保留源码回退快照。

## 基线

2026-10-27现有代码的离线`testDebugUnitTest lintDebug assembleDebug`构建成功（多数任务为缓存命中）。使用Android Studio内置JDK运行Gradle，应用目标仍为JVM17。后续改动必须重新执行相关任务。

## 本轮落地与范围记录

2026-10-27生成0.8.0开发版。109项JVM规则测试、13项内存SQLite校验通过；静音模拟器前两批46项不同的集成用例通过，包括Room4→6、备份/AI固定响应、密钥隔离、撤销保护、导航和编辑器横滑。完整构建及静态检查通过（0错误，19条提示）。

本轮在既有页面结构中落地晨蓝主题、范围/已选时间、首页搜索与详情、编辑核对和AI任务工作台；没有将Penpot的109个状态画板各建成单独页面。音乐/设置沿用已有成熟操作并应用新主题。AI修改字段限定为标题、启停、时刻、窗口结束/跨日、时长与稍后；重复/音乐池由编辑器修改。音乐建议只输出文字；创建草稿使用导入前恢复材料，只有所选修改支持保护性撤销。真实API、手机锁屏/后台/实际声音留待明确目标的实机验收。

- [ ] 2026-10-28新增的事务写入前终态保护完成设备回归；当前startup ANR待定位，已请求仅重启测试模拟器。
