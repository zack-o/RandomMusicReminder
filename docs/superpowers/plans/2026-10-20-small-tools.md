# 乐醒小功能 Implementation Plan

> **Execution:** Follow this plan task by task using the tools available in the current Codex session. Steps use checkbox (`- [ ]`) syntax. Use `verification-before-completion` to report evidence; no additional execution skill is required.

**Goal:** 交付音乐管理、可配置试听、本地统计和中国大陆节假日策略，并整理竞品功能对照。

**Architecture:** 延用单模块，纯 Kotlin 承载筛选、试听校验、日历及统计聚合。Room 版本 3 仅新增日统计表及事件节假日字段；DataStore 增加试听及统计开关，MainActivity 生命周期只采集本应用主界面。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Room、DataStore、Media3、现有 JUnit 和 Python 标准库。

**Spec:** [小功能规格](../../features/2026-10-20-small-tools.md)

## Global Constraints

- 不新增依赖，不操作真机，不发声或试震；compileSdk 37、targetSdk 36、minSdk 26，JVM 17。
- 不删除、移动歌曲，不打包本机音乐进 APK；不自动提交或推送。
- 统计只限本应用；未知年份不能伪造官方日历；数据升级不破坏原表。

---

### Task 1: 音乐管理与试听

**Files:** `ui/MusicLibraryTools.kt`、`ui/screens/MusicScreen.kt`、`ui/components/PreviewOptionsDialog.kt`、`music/MusicPreviewController.kt`、`domain/music/PreviewOptions.kt`、`ui/AppViewModel.kt`、`ui/UiModels.kt`、`data/settings/SettingsRepository.kt`、`data/local/dao/Daos.kt`、`data/repository/MusicRepository.kt`。

**Interfaces:** `filterMusic(tracks: List<MusicListItem>, query: String, filter: MusicFilter, sort: MusicSort): List<MusicListItem>`；`PreviewOptions(durationSeconds: Int = 5, volumePercent: Int = 5, startSeconds: Int = 0)`；`MusicRepository.setEnabled(trackIds: List<Long>, enabled: Boolean): Int`。

- [x] 为纯函数增加测试：`assertEquals(listOf(2L), filterMusic(items, "Forest", MusicFilter.ALL, MusicSort.NEWEST).map { it.id })`；`assertThrows(IllegalArgumentException::class.java) { PreviewOptions(durationSeconds = 0) }`。
- [x] 实现搜索分词、稳定排序和原子批量修改；启用时忽略失效曲目，所有修改只作用于传入 id。
- [x] 控制器使用 `player.volume = options.volumePercent / 100f`，设置起点和短片段；主线程定时器兜底停止，停止/错误/释放均撤销回调。
- [x] UI 增加筛选与选中集合；ViewModel 导入循环逐项更新结果，失败展示文件名及原因；DataStore 原子保存试听参数。
- [x] 运行 `testDebugUnitTest --tests '*MusicLibraryToolsTest' --tests '*PreviewOptionsTest'`，检查搜索、稳定排序、参数边界及无选择批量操作。

### Task 2: 节假日和数据库升级

**Files:** `domain/calendar/MainlandHolidayCalendar.kt`、`domain/model/Models.kt`、`domain/planner/OccurrencePlanner.kt`、`data/local/DatabaseMigrations.kt`、`data/local/entity/Entities.kt`、`data/local/AppDatabase.kt`、`data/repository/EventRepository.kt`、`ui/components/EventEditorDialog.kt`、`ui/AppViewModel.kt`。

**Interfaces:** `HolidayPolicy` 枚举；`MainlandHolidayCalendar.shouldInclude(date: LocalDate, policy: HolidayPolicy, matchesOriginal: Boolean): Boolean`。

- [x] 添加官方年度数据与来源注释；测试补班周末、节假日工作日、未知年份和跨日提前提醒。
- [x] 规划器先判定事件日期，再生成所有提醒点；NONE 与未知年份保留旧规则。
- [x] 迁移增加 `holidayPolicy TEXT NOT NULL DEFAULT 'NONE'`，实体使用匹配的 `@ColumnInfo(defaultValue = "'NONE'")`；原事件默认行为不变。
- [x] 编辑器提供策略、已覆盖年份与未覆盖降级说明，列表显示生效策略。
- [x] 执行相关 JVM 测试，构建导出 Room 3 schema；Python 在内存执行真实迁移 SQL 并比对 schema。

### Task 3: 本地使用统计

**Files:** `domain/statistics/UsageStatistics.kt`、`data/local/entity/UsageDailyEntity.kt`、`data/local/dao/UsageDao.kt`、`data/repository/UsageRepository.kt`、`app/UsageTracker.kt`、`app/MainActivity.kt`、`app/AppContainer.kt`、`ui/screens/StatisticsScreen.kt`、`ui/LexingApp.kt`。

**Interfaces:** `DailyUsage(date: LocalDate, launches: Int, foregroundMillis: Long)`；`splitUsage(start: Instant, elapsedMillis: Long, zone: ZoneId): Map<LocalDate, Long>`；`UsageTracker.setVisible(visible: Boolean)`。

- [x] 测试跨午夜分摊、DST、负时长拒绝、总时长守恒和日期过滤。
- [x] Room 新表按日期聚合，仓库事务更新，跟踪器使用单调时钟每 15 秒落盘；配置变更复用同一会话。
- [x] 主界面可见时计时，设置关闭后停止新增；不读取其他应用、不回溯历史使用时长。
- [x] 顶栏新增统计入口，提供 7/30/90 天和全部范围，展示使用、前台会话及响铃结果，试响独立过滤。
- [x] 编译 Android 集成测试并保留未运行说明。

### Task 4: 调研与交付

**Files:** `docs/同类工具功能调研-2026-10-20.md`、`README.md`、`AGENTS.md`、`app/build.gradle.kts`。

- [x] 对照 Alarmy、Sleep as Android、AMdroid、TickTick 及系统时钟的官方资料，写明已确认功能、平台差异和乐醒取舍。
- [x] 升级应用至 0.4.0，README 说明新入口、参数边界、统计口径、年度日历与降级行为。
- [x] 运行 `.\gradlew.bat --offline testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --console=plain` 与内存迁移脚本；检查 APK 版本和差异格式。
- [x] 交付 APK、调研文档、本地验证结果及未做的设备验证；不安装手机。

## 执行记录

2026-10-20 本地完成：49 项 JVM 测试、9 项内存 SQLite 校验；Lint 0 错误、13 警告，Debug APK 0.4.0 与 Android 测试源码编译通过。`UsageSessionClock.kt` 额外拆出纯计时状态以验证后台、旋转、停用和系统时间变化；不变更外部接口。未执行设备测试或安装，不实际试听或试震。
