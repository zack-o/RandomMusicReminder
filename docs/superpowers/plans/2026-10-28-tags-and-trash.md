# 标签与回收站 Implementation Plan

> **Execution:** Follow this plan task by task using the tools available in the current Codex session. Steps use checkbox (`- [x]`) syntax. Use `verification-before-completion` to report evidence; no additional execution skill is required.

**Goal:** 完成事项标签编辑/筛选/管理，以及移入回收站/停用恢复/明确永久删除的闭环。

**Architecture:** 标签采用有界JSON列表保存在事项，标签管理从正常与回收事项汇总；不另建空标签目录。回收使用本机删除时刻，普通读写、AI与排程均排除回收项；恢复设置本机时间下界，防止恢复前时刻被遗漏宽限重新安排。Room8仅增列，备份JSON5包含标签，普通导出不含回收项，导入判重包含回收项但不以标签差异制造重复。

**Tech Stack:** 现有Kotlin/Compose/Room/java.time，无新增依赖或权限。

**Spec:** `docs/design/2026-10-28-v4/通用事项提醒-功能规格.md`中标题/标签与回收站章节；本轮普通备份暂不提供回收项导出。

## Global Constraints

- 保存既有未提交工作；变更前快照`build/source-before-organizer-20261028/source.zip`。不提交、推送、安装到手机、联网AI或触发声/震动。
- 标签最多8个，每个1～20个Unicode码点，首尾去空格、空白折叠、忽略大小写去重。分类搜索不改变提醒规则。
- 移入回收站取消待触发的原提醒、稍后与TEST；恢复默认停用，不恢复旧稍后；历史快照保留。永久删除仅针对已回收项；清空仅作用于用户核对的ID集合。
- 设备启动超时仍待处理；未获授权不重启。Android测试可编译但不得称设备通过。

## 视觉与操作

延续现有主题：浅底#F5F7FC、正文#1C2538、辅助字#626C80、分隔#DEE5F0；强调色跟随用户主题。标题沿用Material标题字级，清单正文左对齐。标签使用短文本Chip，回收站用紧凑列表，恢复作为主要操作，永久删除收于独立确认；不新增主导航或主页面横滑。

## Task 1: 标签规则与持久化

**Files:** 新增`domain/organizer/EventTags.kt`、`data/transfer/EventTagsCodec.kt`、`domain/organizer/EventTagsTest.kt`；修改Models/Entities/Mappers/DatabaseMigrations/AppDatabase/BackupCodec/TransferRules。

**Interfaces:** `EventTags.normalize(values:List<String>):List<String>`；`key(value:String):String`；`matches(title:String,note:String,tags:List<String>,query:String,selectedTags:Set<String>):Boolean`；`EventTagsCodec.encode/decode`。

- [x] 测试并实现规范化和交集筛选，例如`assertEquals(listOf("工作"), normalize(listOf(" 工作 ","工作")))`，9标签及21码点拒绝。
- [x] 新增`tagsJson TEXT NOT NULL DEFAULT '[]'`、`deletedAtEpochMillis INTEGER`、`resumedAfterEpochMillis INTEGER`；domain tags默认空，迁移1～7保留。
- [x] JSON5写tags，读取1～4默认空；普通备份不写删除状态/恢复边界。判重忽略分类差异，AI修改核对额外比较tags。

## Task 2: 回收与安全恢复

**Files:** 修改EventRepository、Daos、OccurrencePlanner、DataTransferService、AiActionRepository；新增Android存储集成与Planner恢复测试。

**Interfaces:** `getEventsIncludingDeleted()`、`observeDeletedEvents()`；`deleteEvent(id)`改移入回收站；`restoreEvent(id):Boolean`；`permanentlyDeleteEvents(ids:List<Long>):Int`；`renameTag(old,new):Int`；`removeTag(tag):Int`。

- [x] 正常查询与单项读写排除回收项；回收在事务内关闭事项并取消待触发实例，旧计划写入前校验事项仍正常启用。
- [x] 恢复仅清删除标记并设enabled=false，设置恢复时刻下界；Planner忽略删除项和下界前触发。永久删除先验证整批均已回收，再删除；历史表不级联删除。
- [x] 标签管理事务内按现值改名或移除，已有同名标签合并；只更新标签与修改时间，不改变启用、时刻、规则或调度身份。
- [x] 导入判重扫描包含回收项的现有集合，不能通过改标签重新导入回收事项；普通备份仍只导出正常项。

## Task 3: 编辑、筛选与管理界面

**Files:** 新增EventTagsEditor/OrganizerScreens；修改UiModels/AppViewModel/EventEditorDialog/ReminderScreen/SettingsHubScreen/LexingApp/ImportPreviewCard。

- [x] 事项编辑器逐个添加/移除标签，显示限额与可选建议；详情与列表展示标签。
- [x] 首页搜索标题/详细事项/标签，多个所选标签按交集筛选；清空筛选有明确入口，标签更新后去除失效选择。
- [x] 设置加入标签管理与回收站；标签改名/移除列影响正常项与回收项数量。回收站搜索、详情、逐项恢复/永久删除及清空均有具体确认。
- [x] 恢复反馈默认停用，引导在提醒页查看配置后启用；清空提交的是打开确认时的ID快照，不扩大到随后新增的回收项。

## Task 4: 验证与交付

- [x] 运行`gradlew.bat --offline testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --console=plain`，SQL迁移脚本新增7→8保留/回收查询检查。
- [x] 更新README/AGENTS/规格状态/验收记录；打包新0.10.0开发APK并核对版本与SHA256，保留0.9交付物。
- [x] 核对本地链接和差异格式；Android回收/JSON/UI链路只编译时明确待设备验证。

## 验证状态

最终离线构建、131项JVM测试与16项SQL检查通过；Android用例编译成功。设备运行尚未通过，不能据编译声称完成。

- [ ] 在可正常启动的明确测试设备上运行新增存储/UI/历史/升级用例；原模拟器启动超时，重启仍待授权。
- [ ] 补充0.9时分秒滚轮、轮班与0.10标签/回收站的实际布局、手势与无声流程检查。
