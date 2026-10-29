# 乐醒开发约定

- Android 单 app 模块，Kotlin、Jetpack Compose、Gradle Kotlin DSL；JVM 17，compileSdk 37、targetSdk 36、minSdk 26。版本以 app/build.gradle.kts 和版本目录为准，不套用 C#/.NET 规则。
- 回复使用中文，先解释思路；删除文件、重构目录或安装升级依赖前询问。功能、权限和构建方式改变时同步 README.md。
- 修改前检查备份或 Git 状态，保留已有工作；不要自动提交、签名发布或安装到真机。
- 本地代码验证：`.\gradlew.bat --offline testDebugUnitTest lintDebug assembleDebug`。先使用已有 JDK、SDK 和缓存；缺失依赖或 Wrapper 分发包时报告，下载或安装前询问。
- 响铃历史或数据库升级变化后，另运行 `python .\scripts\verify_history_migration.py`（已有 Python 3，标准库内存 SQLite，无用户数据），并编译 `compileDebugAndroidTestKotlin`；SQL 校验和测试编译不等同设备集成测试通过。历史升级保留原表，不使用破坏性重建；已发布的 Room schema 保留。
- 设备验证：有明确目标模拟器或已授权真机时执行 `.\gradlew.bat connectedDebugAndroidTest`。先确认设备，不自行安装新系统镜像或操作用户手机。
- 真机已安装乐醒且需保留数据时，不使用可能在测试结束后卸载目标包的 Gradle 设备测试流程。先备份原 APK 和私有数据，再用 `scripts/Test-AndroidDevice.ps1 -Serial <序列号> -Install -RunTests` 覆盖安装与执行 instrumentation；不卸载、清空数据或改系统音量。
- 办公室设备自动化只使用静音 WAV；人工试响使用 TEST 实例（最多 2 秒、播放器音量 5%）。重启、强制 Doze、蓝牙/勿扰或系统音量修改须另行取得明确授权。
- 提醒、随机队列、持久化和合并策略变化需运行对应单元测试；界面及系统权限变化按 README 的验收步骤验证。
- 模拟器通过不代表真机的锁屏、Doze、重启、蓝牙和实际出声通过，交付时分别说明。
- 不提交 local.properties、缓存、构建产物、签名密钥和用户音乐；保留现有包名与本地数据边界。
- 0.4 起节假日数据在 `domain/calendar/MainlandHolidayCalendar.kt`；新增年份必须核对官方放假和补班，保留来源、未知年份降级提示和跨日提前提醒测试，不用农历公式猜测调休。
- 使用统计只采集本应用 MainActivity 可见会话，单调时钟计时、配置变更不另计启动；不新增设备使用情况、录音或定位权限。统计本身不自动联网，只有用户确认的 AI 汇总解读可发送匿名计数。统计口径、默认开关、落盘间隔变化同步 README。
- 音乐页筛选不改变曲目；批量启停必须有显式选中集合。试听参数与真正提醒、低音量 TEST 实例相互独立；旧试听请求离开页面后不得重新发声。
- 0.5 起默认关闭的可选 AI 已获用户授权新增 INTERNET。只用用户配置的 HTTPS Chat Completions、禁重定向、无自动重试；不读取环境密钥或把 Key 写入源码、日志、备份、测试报告。真实 API/付费测试另获明确授权，不要求用户在聊天发送 Key。
- AI 与文件只生成可校验草稿，写入前展示具体变更并确认；不执行任意代码/SQL，不自动认证官方假期。数据合并只新增，冲突保留；参数设置独立确认，不假称跨 Room/DataStore 原子事务。
- 自定义日历覆盖只影响已启用节假日策略的事件，NONE 必须不受影响；恢复默认只清所选覆盖。年度官方数据与自定义覆盖保持分层。
- JSON/HTTP/Keystore/文件选择器和 Compose 的 Android 测试只编译时不得称通过。JVM只覆盖可脱离 Android 的规则；本地 SQL脚本不替代设备 Room升级测试。
- 0.6 地址补全仅限根路径及 `/v1`，自定义路径不猜测；每次确认页显示 `requestEndpoint`。DeepSeek 专有 thinking 只对精确官方主机发送，不能改模型或跨主机重试；服务原始错误不回显。新增真实 AI 测试默认跳过，只有明确授权后指定 `AiLiveDeviceTest` 并传 `-e lexingLiveAi 1` 才联网；只发送合成数据，不写业务任务、不输出密钥。普通协议/UI 子集使用固定响应，不联网也不播放。
- 0.7 的重复类型包含MONTHLY/YEARLY/INTERVAL；以开始日期锚定，缺日策略显式，结束日期含当天。仅每日/每周允许工作日策略替代星期；月/年/间隔先匹配再过滤，不得扩展成每天。新字段同步实体、映射、编辑、AI、备份校验与判重。Room5仅新增列，保留schema1～4；JSON2读取兼容1，不让旧版静默误读新规则。
- 0.8四个主页面仅点击底部导航切换并保留页面状态，不使用主页面Pager；仅提醒编辑器四个分区可横滑、分别保存滚动。工具集中设置中心，离开音乐页必须停止试听。主题参数在SettingsRepository统一持久化/导入导出，主界面与响铃页同源；任意自定义色须保证强调文字和按钮的可读性。
- 元数据仅从应用私有音乐副本延迟读取。只读标签，不创建播放器做探测；歌词/封面失败不能阻止响铃或停止，不能把Bitmap/歌词文本塞进Intent。保持等待、并发、文本和图片采样限制，不宣称所有标签、加密格式或旁挂歌词受支持。
- 本地模拟器验收使用已有`Lexing_API_36`，启动显式`-no-audio`，ADB必须`-s <明确模拟器序列号>`；不使用未指定设备的安装/测试命令。新增样本测试需传`-e lexingMetadataSample 1`并在目标缓存提供明确选择的副本，不读取测试环境密钥、不上传音乐。

- 0.8随机时间窗为分钟精度、包含两端、每窗口一个主提醒，稍后与补充默认关闭。跨日按开始日归属；已选时间持久化，已处理记录不能因时钟/时区/规则修改或固定点删除复活。删除承载规则前须把随机窗口记录转移到保留规则，再删除固定点；不要级联丢失防重记录。
- Room6仅增列和AI操作表；备份JSON3兼容1/2，普通备份不含种子、调度身份、Key或已选时间。随机派送迟到宽限60秒，超过标遗漏，不在剩余时间重抽；固定遗漏策略保持原值。
- AI密钥绑定连接档案与实际接收URL，不隐式跟随改地址；请求确认前后配置必须一致。所选修改严格限制字段和ID，写前校验旧版本，撤销遇到后续修改整批拒绝。新建草稿继续只新增合并，不用修改入口绕过导入冲突策略。
- 0.8验证需包含V3IntegrationTest、AiProfilesIntegrationTest、V3UiIntegrationTest及导航/迁移测试。协议测试仅固定响应；使用已有Lexing_API_36、明确emulator序列号、-no-audio。截图需确认内容实际绘制，空白过渡帧不作为界面验证证据。

- 0.9增加SHIFT：1～60个日历日，以开始日为第1天；每天0～8个唯一(time,nextDay)，启用日必须有时刻且全周期至少一个启用日。休息标记与启用独立；次日时刻按原班次开始日匹配结束日期和假期，过滤不移动相位。每个时刻使用所有提醒点，稳定slot key在编辑时间时保留，已处理实例不可因重排复活。
- 固定时间统一使用ReminderClock解析/格式化，允许HH:mm或HH:mm:ss，禁止纳秒；随机窗口仍只允许整分钟。关闭秒列或切到随机明确归零；不要承诺系统保证秒级送达。主导航无Pager，编辑器内部保留横滑。
- Room7只增events.shiftCycleJson和scheduled_occurrences.sourceLabel；JSON4读取兼容1～3。轮班配置同步编辑、AI草稿、备份、判重和历史来源；AI单一time修改必须拒绝SHIFT。标签、回收站、事项完成与音乐触感目前仅为设计，不得写成已实现。
- 0.9验收包含ReminderClockTest、ShiftCycleTest、ShiftOccurrencePlannerTest和SQL6→7；Android补测目标包括V4ShiftAndClockIntegrationTest、V4EditorIntegrationTest及AdvancedMigrationIntegrationTest。编译不等于运行；原模拟器启动超时未解决，重启仍需明确授权，不以新功能优先级回答代替设备重启授权。

- 0.10增加标签与回收站。标签最多8个、每个1～20个Unicode码点，去空白/忽略大小写判重；分类不参与安排的导入身份。标签管理只改tags与updatedAt，保留规则和启用。AI版本核对额外比较tags，普通JSON5读兼容1～4，默认不含回收项与本机恢复边界。
- Room8仅增events.tagsJson（默认[]）、deletedAtEpochMillis与resumedAfterEpochMillis，保留schema1～7。正常查询、AI和Planner排除回收项；旧排程写入须核对当前启用/删除状态与恢复边界。恢复默认停用，跳过恢复前时刻及已开始的随机窗口，不恢复旧稍后。永久删除整批校验均已回收，清空必须使用确认时的ID快照，不扩展到新移入项；历史快照保留，不自动按天清理。
- 0.10离线检查增加EventTagsTest、OrganizerPlanningTest与SQL7→8/回收查询；设备补测目标OrganizerIntegrationTest、OrganizerUiIntegrationTest、RingHistoryIntegrationTest及升级测试。普通备份只导出正常项，导入判重必须包含回收项，不能改变标签后绕过回收状态。事项完成与纯震动/音乐触感仍未实现。

- 0.11输出方式为MUSIC/VIBRATION/MUSIC_AND_VIBRATION/NOTIFICATION，事项级持久化，与提醒点强度独立。旧事项默认MUSIC。纯震动/仅通知不能初始化播放器、请求音频焦点或音频兜底；播放器与预览均延迟创建。通知使用新无声频道，不通过频道振动绕过输出设置；仅通知不自动全屏。FGS类型按实际通道选择，systemExempted需既有精确提醒资格，失败保持无声通知。
- 同刻输出分别合并，声源/震动源只从请求相应通道的事项选择；震动配置采用稳定排序首个震动事项并显示来源。preparePlayback在事务认领后使用实际组数据，避免旧Intent方式覆盖当前方式。停止/稍后/替换/销毁取消马达、受限WakeLock、计时与焦点；试震不能抢占提醒，旧持有者不能取消新持有者。
- 触感每脉冲20～160ms、相邻至少40ms空隙、总占空比<=35%，周期500～30000ms，追加500～5000ms循环间隔。无振幅控制用显式二值短脉冲，不能无提示把所有弱振幅当全强度。生成只读imported_music私有副本，10～30秒、采样8k～192k/1～8声道、PCM16/float；解码过程可取消并检查20秒预算，Native阻塞与格式支持需设备验证。不宣称精确节拍或逐拍同步。
- Room9只增五个输出/触感列，保留schema1～8；JSON6读1～5。缓存按算法/曲目文件身份核对，原子写入，每首一份，取消/失败保留此前成功结果；普通备份与系统备份不携带派生缓存，缺缓存使用预设，绝不切成声音。
- 0.11离线检查增加HapticRulesTest及SQL8→9。Android补测目标HapticsDataIntegrationTest、SilentOutputIntegrationTest、HapticOptionsIntegrationTest及原音频/升级/通知测试。自动化只使用静音WAV、合成能量与假马达，不能实际出声或驱动马达。解码/缓存成功或硬件效果不能据测试编译称通过；真实音乐样本/真实试震另行明确授权。

- 0.12增加默认关闭的trackCompletion。开启从本机今天起记录可识别排程；停用不新增。非轮班按eventId+baseDate，轮班按eventId+班次开始日+稳定slot key，各提醒点/稍后共用身份，TEST排除。未完成发生独立保留，不能随旧排程清理；停止/确认收到/超时与完成严格分开。
- 完成需明确选择一次发生并核对，事务校验事项版本、发生时刻与状态revision，幂等追加DONE，只取消同key后续提醒、结束同key RINGING。混合会话保留其他RINGING，来源/标题/按钮同步刷新，保留原入口和剩余时限；最后一个完成使用TASK_COMPLETED。
- REOPEN只允许最新DONE动作id，追加记录，保持停用状态；当前配置下仅恢复操作之后的未来时刻，不恢复过去、已开始随机窗口或旧稍后。Planner和排程写入前均检查状态；状态先后使用自增id，不依赖可回拨的墙钟。确认后的保存与重排不随响铃页关闭而取消，重排失败须明确告知。
- Room10仅增列及task_instances/task_actions，保留schema1～9；实例FK随事项删除，操作快照不设事项FK。JSON7读1～6，只传跟踪开关，不传本机边界/完成状态/记录，开关不参与导入判重但参与AI旧版本核对。记录UI最近1000条、CSV最多10000条且防公式执行；不显示全量完成率。
- 0.12验证包含TaskCompletionRulesTest、SQL9→10及动作/删除语义；Android补测TaskCompletionIntegrationTest、TaskCompletionUiIntegrationTest、TaskPlaybackIntegrationTest与升级测试。静音WAV/假马达仍为唯一自动化输出样本；仅编译时不称Room、界面、混合会话或硬件实测通过。

- 0.13预览为明确点击后的只读快照：90天、最多14条安排/14条排除原因，时区与生成时间可见；返回不保存。保存与预览共用SchedulePreparation，Planner与原因说明共用ScheduleDateRules；保持日历替代星期、月/年/间隔交集和班次开始日归属。
- 预览规则id按已保存offset复用，终态/DONE/REOPEN/恢复下界不能复活；停用不显示将安排。仅列草稿原始提醒，不含旧稍后和TEST。随机未保存窗口用首个可用分钟检查范围，不调用抽签、不分配/落盘seed或实例，既有选定分钟只读复用；只对外显示范围/补充条件，不泄露隐藏的选定分钟。
- 未收录官方年份按事项日期检查，包括跨年提前；DST时刻解析复用Planner并说明跳时。预览不保证系统准点投递，保存时重算，不能当作长期有效的系统排程凭据。
- Room10/JSON7不变，保留schema1～10。0.13验证增加SchedulePreviewTest；Android补测SchedulePreviewIntegrationTest和SchedulePreviewUiIntegrationTest，只编译时不称数据库只读断言、刷新、返回或手势实测通过。

- 0.14单日调整最多100个日期，SKIP适用各重复类型，REPLACE_SHIFT仅固定轮班；按baseDate归属，提前与次日一起处理，周期相位不动，仍受开始/结束/日历限制。原时刻key编辑时保留，显式新增另设key，已处理/DONE不复活；跳过不算完成。
- 日期调整/恢复在SchedulePreparation为所改日期保留本机时间屏障，恢复后仍保留，过去/旧稍后/已开始随机窗口不补排。单日调整不加入全局随机scheduleIdentity，其他日期不重抽。Planner和写入核对dayOverrideSignature；保存取消所改日期SCHEDULED（含稍后、不含TEST），当前RINGING不强制停止，旧配置不能新增稍后。
- 生产播放认领使用requireDue，在事务内拒绝未来实例，防止改晚后的同ID被旧广播提前认领；默认仓库测试接口仍可显式模拟认领。不以编译替代设备广播测试。
- Room11仅增events.dayOverridesJson/dayOverrideBarriersJson与scheduled.dayOverrideSignature，保留schema1～10；JSON8读1～7，仅传覆盖配置，边界/签名不传。覆盖行为参与导入判重，slot key不参与可移植身份；AI版本核对另比较完整覆盖，AI修改保留但不能新增覆盖。
- 0.14验收增加DayOverridesTest、SQL10→11，编译DayOverrideIntegrationTest、DayOverrideUiIntegrationTest及升级/旧用例。单日管理先确认到草稿再保存，替换/恢复不隐式启用。无设备运行时明确列待验收，不安装手机、不驱动真实马达或出声。

- 0.15连续日期操作展开到原有SKIP，含两端，暂停最多100日且合并后调整总数<=100；超限整段拒绝，单次只允许其发生日。恢复仅移除区间SKIP，可跨原起止清旧设置，不删除REPLACE_SHIFT或区间外项。
- 默认保留临时班次，只有显式选择才覆盖；确认列数量与清单，覆盖后恢复仅回原规则，不伪造旧临时班次。先修改草稿，再保存；确认核对完整日期配置含slot keys与起止/类型scope，旧确认拒绝。空变更禁用确认，错误固定在操作区可见。
- Room11/JSON8不变，保留schema1～11；不增加区间分组或本机恢复格式，沿用0.14签名/时间边界、待办和RINGING语义。验证增加DayOverrideRangesTest；DateRangeIntegrationTest/DateRangeUiIntegrationTest只编译时不得称设备通过。自动跳过下一次与未来生效版本不在本轮。
