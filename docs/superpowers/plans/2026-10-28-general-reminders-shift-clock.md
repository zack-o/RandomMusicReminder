# 通用事项、秒级滚轮与轮班 Implementation Plan

> **Execution:** Follow this plan task by task using the tools available in the current Codex session. Steps use checkbox (`- [x]`) syntax. Use `verification-before-completion` to report evidence; no additional execution skill is required.

**Goal:** 将乐醒的主要体验落回通用事项提醒，提供固定时间的时分秒滚轮和可预览的逐日轮班规则。

**Architecture:** 时刻解析与轮班计算采用可脱离Android测试的领域模型；Room只新增轮班配置和实例来源字段，原调度规则保留。界面使用已有Compose及系统NumberPicker，不安装新依赖；标签/详情、回收站、音乐触感的完整规则写入v4规格，按独立模块实施，不把尚未接入的能力列为已完成。

**Tech Stack:** Kotlin、Compose、Room、java.time；JVM17，compileSdk37、targetSdk36、minSdk26。

**Spec:** `docs/design/2026-10-28-v4/通用事项提醒-功能规格.md`

## Global Constraints

- 保留已有未提交工作，变更前已保存`build/source-before-reminder-v4-20261028/source.zip`。
- 不安装/升级依赖，不提交/推送，不安装到手机，不调用真实AI，不驱动马达或播放音乐。
- 主页面仅点击导航；编辑器内部可横滑。秒级选择不承诺秒级送达。
- 随机窗口仍为整分钟；轮班第一版使用逐日固定时刻，不混合随机窗口。
- 轮班以开始日为周期第1天；夜班次日时刻按班次开始日归属，过滤不会移动周期相位。
- 0.8新增事务终态保护的设备补测仍等待启动超时定位；未获重启授权不重启测试设备。

---

### Task 1: 通用文案与时刻

**Files:** Create `domain/time/ReminderClock.kt`, `ui/components/TimeWheelPicker.kt`; modify `EventEditorDialog.kt`, `AppViewModel.kt`, `AiDraftCodec.kt`, `AiEditCodec.kt`, `ReminderScreen.kt`, `RingActivity.kt`, `ReminderNotificationFactory.kt`.

**Interfaces:** `ReminderClock.parse(value:String, allowSeconds:Boolean=true):LocalTime?`; `format(time:LocalTime):String`; `TimeWheelPicker(value:LocalTime, allowSeconds:Boolean, onChange:(LocalTime)->Unit)`.

- [x] 添加`ReminderClockTest`：`assertEquals(LocalTime.of(9,59,55), ReminderClock.parse("09:59:55"))`；`assertNull(ReminderClock.parse("09:59:55",false))`；`assertNull(ReminderClock.parse("24:00"))`。
- [x] 编写解析器：正则`^([01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$`，随后LocalTime.parse；禁止nano。格式在second!=0时使用HH:mm:ss，否则HH:mm。
- [x] 编写NumberPicker三列组件，小时0～23、分秒0～59，单列循环；秒列关闭时归零；随机模式不提供秒列。保留直接输入入口，修改后同步草稿时刻。
- [x] 编辑校验、首页摘要、提醒点、AI固定时刻解析共享领域规则；窗口仍通过RandomWindow的整分钟验证。
- [x] “已起床”改为“确认收到”，仅取消本次补充；不伪造待办完成状态。示例覆盖抢票、工作和轮班。
- [x] 运行`testDebugUnitTest --tests '*ReminderClockTest'`及编译Android界面测试。

### Task 2: 轮班规则与持久化

**Files:** Create `domain/planner/ShiftCycle.kt`, `data/transfer/ShiftCycleCodec.kt`; modify Models/Entities/Mappers/DatabaseMigrations/AppDatabase/TransferRules/BackupCodec/EventRepository/OccurrencePlanner/RecurrenceDates。

**Interfaces:** `ShiftTime(time:LocalTime,nextDay:Boolean=false,key:String=UUID)`；`ShiftDay(label:String,rest:Boolean,enabled:Boolean,times:List<ShiftTime>)`；`ShiftCycle(days:List<ShiftDay>)`；`dayIndex(anchor:LocalDate,date:LocalDate):Int?`；`dayOn(anchor,date):ShiftDay?`；`ShiftCycleCodec.encode(cycle:ShiftCycle?):String`与`decode(value:String):ShiftCycle?`。

- [x] 添加`ShiftCycleTest`：6日模板跨月，开始日前null；休息日可关闭/独立开启；跨闰日与周期长1/60；非法长度和重复时间拒绝。
- [x] 模型实现以ChronoUnit.DAYS作日历日差，负值不触发；1～60天、每天0～8个唯一(time,nextDay)、enabled必须有时间、稳定key唯一。
- [x] 增加ScheduleType.SHIFT及事件shiftCycle字段；新建Room7迁移6→7，只增events.shiftCycleJson（默认空串）与scheduled_occurrences.sourceLabel（可空）。保留schema1～6。
- [x] Planner对SHIFT按命中日的每个ShiftTime生成实例，nextDay使用baseDate.plusDays(1)。ID包含event/rule/baseDate/稳定key，时刻编辑不改key；已处理实例不能因时间更新重新响。
- [x] JSON升为4，读取兼容1～3；轮班完整配置写入，非轮班读取默认null；普通备份继续不含随机调度身份。判重纳入轮班配置但不使用随机生成的key。
- [x] 历史/播放摘要保留sourceLabel；AI在不理解轮班时不得假改为每日，也不能用单一time冒充逐日修改。
- [x] 运行轮班/Planner测试，新增SQL脚本6→7字段保留校验；编译Room schema与Android测试。

### Task 3: 轮班配置与核对

**Files:** Create `ui/components/ShiftCycleEditor.kt`; modify UiModels/AppViewModel/EventEditorDialog/ImportPreviewLabels/ImportPreviewCard/ToolsViewModel。

**Interfaces:** `ShiftCycleEditor(cycle:ShiftCycle,anchor:LocalDate,onChange:(ShiftCycle)->Unit)`；UI草稿保留cycle，重复类型新增轮班。

- [x] 在重复页加入轮班，提供做二休一、白夜休休、白白夜夜休休模板；说明替换草稿范围。
- [x] 每天可编辑名称、休息标记、是否提醒、多个时刻和次日标记；添加/删除时间保留其他稳定key，关闭日保留配置。
- [x] 周期长度缩短前提示受影响天数；开始日复用日期选择器；两周期预览显示每一天的班次、时间与关闭原因，过滤说明分层。
- [x] 保存前校验至少一个启用日、轮班与随机窗口不混用；核对页列周期长度、开始日、每日时刻与休息日规则；保存后在事项详情/日历核对实际下次安排。
- [x] 编写并编译UI/备份测试，覆盖秒值保留、轮班核对、次日时间与配置往返；Android运行待补，不称通过。

### Task 4: 验证与交付

- [x] 运行现有离线检查：`gradlew.bat --offline testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --console=plain`，核对日志中的真正结果。
- [x] 运行`python scripts/verify_history_migration.py`；新迁移核对旧字段、默认值、外键和Room7 schema。
- [x] 更新README/AGENTS及迭代记录，明确本轮已实现、设计已完成但未接入、设备与硬件未验证的部分。
- [x] 核对文档本地链接、实例措辞和输出文件；不把0.8安装包当新功能包。

## 尚未完成的设备验收

- [ ] 在可正常启动的明确目标模拟器上运行V4ShiftAndClockIntegrationTest、V4EditorIntegrationTest及升级测试。现有AVD启动超时，尚未得到重启授权。
- [ ] 检查小屏/大字体/深浅色下的时分秒滚轮、逐日编辑与两周期预览；静态编译不能替代此项。
- [ ] 真机锁屏、后台、实际响铃及新规则长期续排；不自动安装手机或触发音频/震动。

保存前含全部日历例外的实际触发预览、单日换班覆盖、生效日版本保留为后续扩展；本轮保存前核对原周期，保存后展示实际排程。
