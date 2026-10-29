param(
    [string]$Serial,
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk",
    [switch]$Install,
    [switch]$RunTests,
    [string]$TestClass
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$adbPath = Join-Path $SdkPath 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adbPath)) { throw "未找到 adb：$adbPath" }

$deviceLines = & $adbPath devices -l
if ($LASTEXITCODE -ne 0) { throw '无法读取 adb 设备列表' }
$devices = @($deviceLines | Where-Object { $_ -match '^\S+\s+device\b' -and $_ -notmatch '^emulator-' })
if (-not $Serial) {
    if ($devices.Count -ne 1) { throw '请连接并授权一台真机，或使用 -Serial 指定设备。' }
    $Serial = ($devices[0] -split '\s+')[0]
}
if (-not ($devices | Where-Object { ($_ -split '\s+')[0] -eq $Serial })) {
    throw "设备 $Serial 未连接、未授权或不是真机。"
}

function Invoke-Adb {
    param([string[]]$Arguments)
    $result = & $adbPath -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    $result
}

$reportDir = Join-Path $projectRoot ('build\device-check-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $reportDir | Out-Null
$deviceReport = @(
    "Device: $Serial"
    "Model: $(Invoke-Adb -Arguments @('shell','getprop','ro.product.model'))"
    "Android: $(Invoke-Adb -Arguments @('shell','getprop','ro.build.version.release'))"
    "API: $(Invoke-Adb -Arguments @('shell','getprop','ro.build.version.sdk'))"
)
$deviceReport | Set-Content -LiteralPath (Join-Path $reportDir 'device.txt') -Encoding utf8
$deviceReport

if ($Install) {
    $apkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $apkPath)) { throw '请先执行 assembleDebug' }
    # -r 覆盖安装保留私有数据，签名冲突时失败，绝不自动卸载旧应用。
    Invoke-Adb -Arguments @('install','-r',$apkPath)
}

if ($RunTests) {
    $testApk = Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if (-not (Test-Path -LiteralPath $testApk)) { throw '请先执行 assembleDebugAndroidTest' }
    Invoke-Adb -Arguments @('install','-r',$testApk)
    $arguments = @('shell','am','instrument','-w','-r')
    if ($TestClass) { $arguments += @('-e','class',$TestClass) }
    $arguments += 'com.muyex.randommusicreminder.test/androidx.test.runner.AndroidJUnitRunner'
    $testOutput = Invoke-Adb -Arguments $arguments
    $testOutput | Set-Content -LiteralPath (Join-Path $reportDir 'instrumentation.txt') -Encoding utf8
    $testOutput
    if (($testOutput -join "`n") -notmatch 'OK \(\d+ tests?\)') {
        throw "设备测试未全部通过，请查看 $reportDir\instrumentation.txt"
    }
}

Invoke-Adb -Arguments @('shell','dumpsys','package','com.muyex.randommusicreminder') |
    Select-String 'versionCode=|versionName=|POST_NOTIFICATIONS|USE_EXACT_ALARM|USE_FULL_SCREEN_INTENT' |
    Set-Content -LiteralPath (Join-Path $reportDir 'app-status.txt') -Encoding utf8
"结果目录：$reportDir"
