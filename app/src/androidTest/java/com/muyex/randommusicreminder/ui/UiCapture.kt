package com.muyex.randommusicreminder.ui

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun captureUi(name: String, expectDark: Boolean? = null) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    val deadline = android.os.SystemClock.uptimeMillis() + 4000
    var bitmap: Bitmap
    while (true) {
        bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("截图失败")
        val dark = android.graphics.Color.luminance(bitmap.getPixel(8, bitmap.height / 2)) < .3f
        var minimum = 1f; var maximum = 0f
        for (y in bitmap.height / 8 until bitmap.height * 7 / 8 step 12) for (x in 24 until bitmap.width - 24 step 12) {
            val light = android.graphics.Color.luminance(bitmap.getPixel(x, y))
            minimum = minOf(minimum, light); maximum = maxOf(maximum, light)
        }
        val contentDrawn = maximum - minimum > .1f
        if ((expectDark == null || dark == expectDark) && contentDrawn) break
        bitmap.recycle()
        check(android.os.SystemClock.uptimeMillis() < deadline) { "截图内容尚未绘制或明暗模式未稳定" }
        // 语义树可能先于 GPU 合成帧更新；按实际背景检查，而非把上一帧冒充主题截图。
        Thread.sleep(50)
    }
    val directory = File(instrumentation.targetContext.cacheDir, "ui-pro-captures").apply { mkdirs() }
    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
}
