# 提醒方式与本机音乐节奏 Implementation Plan

> **Execution:** Follow this plan task by task using the tools available in the current Codex session. Steps use checkbox (`- [x]`) syntax. Use `verification-before-completion` to report evidence; no additional execution skill is required.

**Goal:** 提供音乐、仅震动、音乐加震动、仅通知四种方式，并可从应用私有歌曲片段生成可缓存的震动节奏。

**Architecture:** 事项保存独立输出方式和触感参数；领域层负责输出合并、脉冲限额及能量变化分析。MediaExtractor/MediaCodec只解码所选10～30秒片段，结果按曲目缓存；提醒服务分开启动音频与马达，纯震动不初始化播放器、音频焦点或铃声兜底。Room9只增列，JSON6读取兼容1～5；缓存不作业务备份。

**Tech Stack:** 现有Kotlin/Compose/Room/Media3，Android原生MediaCodec/Vibrator，JVM17/min26/compile37/target36，无新增依赖。

**Spec:** `docs/design/2026-10-28-v4/通用事项提醒-功能规格.md`纯震动与本机节奏章节。

## Global Constraints

- 修改前快照`build/source-before-haptics-20261028/source.zip`；保留用户工作，不提交/推送/安装手机/真实AI/实际出声或试震。
- 自动化使用合成PCM或静音WAV；设备用例使用注入的马达替身，不驱动真实马达。现有模拟器启动超时，未获授权不重启。
- 纯震动与仅通知不创建音频播放器、不请求焦点、不走系统铃声；无马达/失败时保持静音并显示原因。
- 每脉冲最长160ms、总占空比不超过35%、相邻脉冲至少40ms空隙；循环附加500～5000ms间隔，服务时限沿用5～1800秒，试震最多2秒且明确确认。
- 只读取应用私有imported_music；不录音、不联网、不声称精确节拍/睡眠检测。生成可取消、有时限与输入格式/采样上限；旧生成请求不能覆盖新结果。

## Task 1: 领域规则与持久化

**Files:** 新增domain/haptics中的ReminderOutput、RhythmPattern、EnergyRhythm、OutputRouting、HapticOwnership及测试；修改Models/Entities/Mappers/DbConverters/Daos/DatabaseMigrations/AppDatabase/EventRepository/TransferRules/BackupCodec。

**Interfaces:** `ReminderOutputMode`四值；`HapticSettings(preset,strength,repeatGapMillis,useMusicRhythm)`；`RhythmPattern(durationMillis,pulses).validate()`；`EnergyRhythm.fromRms(frames:List<Double>):RhythmPattern`。

- [x] 测试四种输出合并、不同曲目来源、静音/恒定信号拒绝、合成脉冲间隔/占空比、无振幅控制二值降级、参数边界。
- [x] Room9增加outputMode/hapticPreset/hapticStrength/hapticGapMillis/hapticUseMusicRhythm，旧事项默认MUSIC；同步播放投影与所有映射。
- [x] JSON6保存参数，读兼容1～5；判重/AI版本核对包含输出行为。纯震动与通知不强制自选音乐可用，缺节奏使用所选标准预设并说明。

## Task 2: 无声音解码与缓存

**Files:** 新增music/PcmRhythmAnalyzer.kt、HapticPatternStore.kt；新增ui/components/HapticRhythmDialog.kt。

**Interfaces:** `PcmRhythmAnalyzer.analyze(file:File,startSeconds:Int,lengthSeconds:Int):RhythmPattern`；`HapticPatternStore.read(trackId:String,uri:String)`与`generate(trackId,uri,start,length)`；缓存包含来源标识、算法版本、片段范围和脉冲。

- [x] 限定私有曲目文件；MediaExtractor找音轨、seek、MediaCodec解码PCM16/float，按20ms累计RMS；最长30秒、采样/声道/壁钟有界；取消或格式不支持释放资源，不覆盖旧缓存。
- [x] 提取能量起伏转短脉冲，缓存原子写入；读缓存验证文件来源、大小、版本、时长与脉冲安全约束，不在提醒时重新解码。
- [x] 音乐详情添加片段起点/时长、生成/取消、结果摘要与明确确认的最多2秒试震；关闭/后台/换曲取消试震与旧生成。

## Task 3: 服务与界面

**Files:** 新增playback/ReminderHaptics.kt和ui/components/OutputOptions.kt；修改PlaybackContract/ReminderPlaybackService/ReminderNotificationFactory/RingActivity/AppContainer/AlarmContract/ScheduleReconciler/MusicPreviewController及编辑、列表、导入界面。

**Interfaces:** `HapticOutput.start(pattern:RhythmPattern,settings:HapticSettings):String`与`stop():Unit`（服务计时器控制截止，并使用受限WakeLock）；`PlaybackServiceDependencies.preparePlayback(id)`原子认领后按当前组描述输出；`PlaybackOccurrenceInfo`带输出、来源及触感参数。

- [x] 音频播放器与MediaSession延迟到实际音乐模式；所有停止/替换/销毁同时取消马达、计时器与焦点。预览播放器也延迟创建。
- [x] 同刻组声音/震动独立合并，分别从需要该通道的事项选曲；通知展示来源。失败保持通道约束，音乐换曲时重启匹配的触感缓存。
- [x] Android14+纯震动按官方要求声明并选择systemExempted前台服务类型；不满足条件时保留无声通知，不改用音乐。
- [x] 新建编辑器选择方式、预设/强度/间隔/音乐节奏；列表、核对和响铃页明确方式，纯震动不显示“正在播放”。静默通知频道不借系统振动/声音绕过选择。

## Task 4: 验证与交付

- [x] 离线运行testDebugUnitTest/lintDebug/assembleDebug/compileDebugAndroidTestKotlin；SQL增加8→9保留/default/schema校验。
- [x] Android新增合成WAV离线解码/缓存、备份参数、静音服务与假马达停止用例；仅编译则明确待设备验证。
- [x] 更新README/AGENTS/规格状态/验收；交付新0.11.0开发包，核对版本/SHA256/旧schema与链接，保留0.10包。

参考：[前台服务类型](https://developer.android.com/about/versions/14/changes/fgs-types-required)、[震动能力](https://developer.android.com/develop/ui/views/haptics/haptics-apis)、[PCM编码格式](https://developer.android.com/reference/android/media/MediaFormat#KEY_PCM_ENCODING)。系统权限/马达行为须设备验证。

## 实施边界与待补验收

音乐节奏采用片段能量模板；音乐加震动独立循环，尚未实现逐拍同步。每首缓存一份最新成功结果；不把波形当精确节拍或音高。通知新频道继承旧频道重要性，默认声音/马达由应用通道控制。

- [ ] 在可正常启动且明确指定的设备运行HapticsDataIntegrationTest、SilentOutputIntegrationTest、HapticOptionsIntegrationTest和已有播放/通知/升级用例；目前仅编译。
- [ ] 验证真实音乐格式、取消/后台/缓存写入、无振幅设备、无马达设备及熄屏停止时限；当前未安装手机，未试震/出声。
- [ ] 如需真实音乐样本或实际试震，先取得明确授权；模拟器启动超时仍未解决，不重启设备代替用户授权。
