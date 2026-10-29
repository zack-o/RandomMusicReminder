# 乐醒日历、数据与 AI Implementation Plan

> **Execution:** Follow this plan task by task using the tools available in the current Codex session. Steps use checkbox (`- [ ]`) syntax. Use `verification-before-completion` to report evidence; no additional execution skill is required.

**Goal:** 交付可编辑月历、可预览数据合并和可配置 AI 草稿助手。

**Architecture:** 三部分独立实现。日历使用 Room 覆盖表；JSON/CSV 使用标准库和 Android JSON，数据合并事务与设置应用分开确认；AI 使用 HTTPS、Keystore、严格本地草稿验证，所有界面由新的 ToolsViewModel 组织，避免继续堆入原 AppViewModel。

**Tech Stack:** Kotlin、Compose、Room、DataStore、HttpsURLConnection、Android Keystore；无新依赖。

**Spec:** [功能规格](../../features/2026-10-20-calendar-data-ai.md)

## Global Constraints

- compileSdk 37、targetSdk 36、minSdk 26，JVM 17；不安装升级依赖，不操作手机。
- 保留未提交源码；备份位于 build/change-backups/2026-10-20-calendar-ai，不提交或推送。
- 只新增可选 AI INTERNET 权限；不调用真实服务、不记录密钥、不整库覆盖。

### Task 1 日历

**Files:** domain/calendar、data/local/{AppDatabase,DatabaseMigrations}、data/repository/CalendarRepository.kt、domain/planner/OccurrencePlanner.kt、alarm/ScheduleReconciler.kt、ui/screens/CalendarScreen.kt。

**Interfaces:** `CalendarOverride(date: LocalDate, type: CalendarDayType, note: String, source: String, updatedAt: Long)`；`CalendarRepository.observe(): Flow<List<CalendarOverride>>`；`OccurrencePlanner.plan(..., overrides: Map<LocalDate, CalendarDayType> = emptyMap())`。

- [x] 增加休息/工作覆盖表与 3→4 迁移，保留原表。
- [x] 日历覆盖以事件日期判定；验证 `assertFalse(shouldInclude(date, MAINLAND_WORKDAYS, true, REST))` 和 `NONE` 不受覆盖影响。
- [x] 月视图支持日期多选及确认保存，恢复默认只移除所选覆盖；重排失败单独报告。
- [x] 运行全部 JVM 测试和实际 SQL 迁移校验。

### Task 2 数据

**Files:** data/transfer/{BackupCodec,DataTransferService}.kt、domain/transfer/TransferRules.kt、data/settings/SettingsRepository.kt、ui/screens/DataScreen.kt。

**Interfaces:** `BackupPackage(settings: AppSettings?, events: List<BackupEvent>, days: List<CalendarOverride>)`；`decode(text: String): BackupPackage`；`prepare(package): ImportPreview`；`merge(preview, preserveEnabled): ImportResult`。

- [x] JSON 编解码设版本、大小和数量上限，测试非法版本、边界、重复规则和音乐引用。
- [x] 导出不含密钥/音乐路径/调度实例；CSV 文本转义公式前缀。
- [x] 数据合并只新增、不覆盖，Room 事务中重新去重；设置独立确认应用。
- [x] 编译 Android 编解码和仓库测试，不称设备已通过。

### Task 3 AI

**Files:** ai/{AiConfig,AiSettingsStore,AiClient,AiDraftCodec}.kt、ui/screens/AiScreen.kt、ui/ToolsViewModel.kt、AndroidManifest.xml、res/xml/*backup*。

**Interfaces:** `AiClient.complete(config, secret, system, input): String`；`AiDraftCodec.decode(text): AiDraft`；`AiSettingsStore.save(config, replacementKey)`。

- [x] 测试 `https`、控制字符、超时和输出上限；拒绝含账号、查询参数和 fragment 的接口 URL。
- [x] AES-GCM 加密 Key、备份排除；HTTP 禁重定向/限体积/可取消，无自动重试。
- [x] 提供文字识别、匿名汇总分析、最小连接测试；每次发送前预览，写入前另确认具体任务/日期。
- [x] 编写固定响应和模拟配置测试并编译；设备端未运行，不实网请求。

### Task 4 整合与交付

**Files:** ui/LexingApp.kt、app/{MainActivity,AppContainer}.kt、README.md、AGENTS.md、app/build.gradle.kts、scripts/verify_history_migration.py。

- [x] 整合底部日历和顶栏菜单，保留原有提醒/音乐/设置。
- [x] 执行 `.\gradlew.bat --offline testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --console=plain` 和迁移脚本。
- [x] 更新 0.5.0 说明与未验证边界，核对 APK 元数据和差异；不安装设备。

## 执行记录

2026-10-20：代码、文档和 0.5.0 APK 已交付候选；70 项 JVM、11 项内存 SQL校验通过，Lint 0错误/16警告，Android 测试源码编译通过。JSON/Keystore/固定响应/合并/界面测试未在设备执行，实网与真机未测。详细边界见本地验收记录。
