# Professional Rules and Interaction Implementation Plan

> **Execution:** Follow this plan task by task using the tools available in the current Codex session. Steps use checkbox (`- [x]`) syntax. Use `verification-before-completion` to report evidence; no additional execution skill is required.

**Goal:** 交付 0.7 的分区提醒编辑、高级重复、统一导航主题、可辨认日历和歌曲元数据展示。

**Architecture:** 分为规则与持久化、界面与偏好、元数据与播放三块顺序实现；保留 Repository、Planner 和播放服务边界，不重构目录、不引入框架。每块有独立测试，最后合并构建验证。

**Tech Stack:** Kotlin、Compose Material 3、Room、DataStore、已有 Media3 1.10.1、JUnit、Python 标准库 SQLite。

**Spec:** docs/features/2026-10-20-professional-ui.md

## Global Constraints

- 保留包名及现有单模块；不升级或新增依赖。
- 不操作真机、不发声、不使用真实 AI。
- 数据库仅增加列，保留旧数据；JSON 导出升级版本 2，读取兼容版本 1。
- 新增 Compose/Android 测试编译，不假称设备执行通过。

---

### Task 1: 规则、迁移与交换格式

**Files:** `domain/model/Models.kt`、`domain/planner/OccurrencePlanner.kt`、新增 `domain/planner/RecurrenceDates.kt`；`data/local/entity/Entities.kt`、`DatabaseMigrations.kt`、`DbConverters.kt`、`AppDatabase.kt`；`data/repository/EventRepository.kt`、`Mappers.kt`；`domain/transfer/TransferRules.kt`、`data/transfer/BackupCodec.kt`、`ai/AiDraftCodec.kt`；`ui/UiModels.kt`、`AppViewModel.kt`、`components/ImportPreviewLabels.kt`；测试 `domain/planner/AdvancedRecurrenceTest.kt`、迁移脚本和 Android 数据测试。

**Interfaces:** `ReminderEvent.intervalDays: Int = 1`、`missingDayPolicy: MissingDayPolicy = SKIP`、`endDate: LocalDate? = null`；`RecurrenceDates.matches(event, date): Boolean`。Room 4→5 增加同名列；JSON v2 携带三字段，v1 采用默认值。

- [x] 新增并运行规则回归：
  ```kotlin
  val monthly = ReminderEvent(1, "月末", scheduleType = ScheduleType.MONTHLY, localDate = LocalDate.of(2030, 1, 31), localTime = LocalTime.NOON)
  assertFalse(RecurrenceDates.matches(monthly, LocalDate.of(2030, 2, 28)))
  assertTrue(RecurrenceDates.matches(monthly.copy(missingDayPolicy = MissingDayPolicy.LAST_DAY), LocalDate.of(2030, 2, 28)))
  ```
- [x] 实现按锚点匹配的月/年/间隔规则和结束日期；Planner 对每日/每周保留原工作日策略，对其他规则先匹配后过滤。
- [x] 同步新增列、转换器、Repository 映射、备份校验/判重及 AI 草稿字段；在 Python 脚本中用原 schema4 升级并比对 schema5 字段/外键。
- [x] 运行 `testDebugUnitTest`、迁移 SQL 脚本，编译 Android 数据测试。

### Task 2: 分区编辑、Pager、设置中心与主题日历

**Files:** `ui/components/EventEditorDialog.kt`、`ui/LexingApp.kt`、`ui/screens/SettingsScreen.kt`、新增 `ui/screens/SettingsHubScreen.kt` 与 `AppearanceScreen.kt`；`ui/theme/LexingTheme.kt`、新增 `domain/settings/AppearanceSettings.kt`；`SettingsRepository.kt`、`UiModels.kt`、`AppViewModel.kt`；`domain/calendar/MainlandHolidayCalendar.kt`、`ui/screens/CalendarScreen.kt`。

**Interfaces:** `AppearanceSettings(mode, palette, customColor)` 由 SettingsUi 与 AppSettings 传递；`SettingsHubScreen(onOpen)` 路由到现有 AppSection；Pager 只包含四个主页面，不在子页拦截手势。

- [x] 先编写分区/UI 回调测试：
  ```kotlin
  compose.onNodeWithText("重复").performClick()
  compose.onNodeWithText("每月").assertExists()
  compose.onNodeWithText("事件名称").assertDoesNotExist()
  ```
- [x] 替换编辑器布局为固定标签＋单区内容＋固定保存栏；提醒点参数在独立小编辑器保存，支持分钟/小时/天，不改变未确认草稿。
- [x] 添加 Pager 同步及设置中心，原设置作为“提醒与权限”子页；主题参数持久化并加入 JSON；统一主界面和响铃页主题。
- [x] 增加 2026 假期名称映射与状态配色/图例，并编写日期标签规则单测。
- [x] 运行全套 JVM，编译 Compose 测试，核查无重复入口和原功能不可达问题。

### Task 3: 元数据与响铃展示

**Files:** 新增 `music/MusicMetadataReader.kt`、`music/Lyrics.kt`、`ui/components/MusicMetadataPanel.kt`；`ui/screens/MusicScreen.kt`；`playback/ReminderPlaybackService.kt`、`RingActivity.kt`；测试 `music/LyricsTest.kt` 和 Android 元数据/界面测试。

**Interfaces:** `MusicMetadataReader.read(context, uri): EmbeddedMusicMetadata` 返回标题、歌手、专辑、可选Bitmap与歌词；`Lyrics.parse(text): LyricsDocument` 返回有序时间点与文本。当前播放状态通过同进程只读StateFlow暴露，服务恢复播放后重新发布；不另行承诺跨进程播放恢复，不把大字段放入通知或Intent。

- [x] 编写 LRC 时间戳、偏移、重复时间戳和畸形输入测试：
  ```kotlin
  assertEquals(12500L, Lyrics.parse("[00:12.50]歌词").lines.single().timeMillis)
  ```
- [x] 复用已有 Media3 MetadataRetriever 提取静态标签；有超时/体积限制与取消释放，失败回空结果；平台 Retriever 作为封面/文本回退。
- [x] 加入音乐详情与响铃封面/歌词，停止和稍后始终可达；时间戳按真实播放位置高亮，无歌词明确提示。
- [x] 运行 JVM 与全量构建，编译设备测试；不读取或播放用户歌曲作为未经说明的替代验收。

### Task 4: 交付与边界

**Files:** `README.md`、`AGENTS.md`、`app/build.gradle.kts`、`docs/本地验收-0.7.0-2026-10-20.md`。

- [x] 更新 0.7.0 / code7、Room5 与 JSON2 文档，保留已发布 schema1～4；说明新数据写入后不能直接用旧版 APK 降级读取。
- [x] 执行 `./gradlew.bat --offline testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain` 与 `python scripts/verify_history_migration.py`。
- [x] 核对 APK、文档链接与测试数字；列出未执行的手机视觉、元数据格式和锁屏测试，不提交或推送。
