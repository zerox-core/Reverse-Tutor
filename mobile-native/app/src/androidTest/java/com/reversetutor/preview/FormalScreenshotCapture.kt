package com.reversetutor.preview

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.roundToInt

internal fun ComposeTestRule.captureFormalFixture(
    fixtureTag: String,
    includePlatformWindows: Boolean = false
): Bitmap {
    waitForIdle()
    onNodeWithTag(fixtureTag).assertIsDisplayed()

    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    SystemClock.sleep(if (includePlatformWindows) 650L else 500L)
    instrumentation.waitForIdleSync()
    val density = instrumentation.targetContext.resources.displayMetrics.density
    val targetWidth = (FormalFixtureWidthDp * density).roundToInt()
    val targetHeight = (FormalFixtureHeightDp * density).roundToInt()
    return captureWithRetry {
        instrumentation.waitForIdleSync()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "Unable to capture the Android display"
        }
        check(screenshot.width >= targetWidth && screenshot.height >= targetHeight) {
            "Display ${screenshot.width}x${screenshot.height} is smaller than the " +
                "formal fixture ${targetWidth}x${targetHeight}"
        }
        val left = (screenshot.width - targetWidth) / 2
        val top = (screenshot.height - targetHeight) / 2
        val fixture = Bitmap.createBitmap(screenshot, left, top, targetWidth, targetHeight)
        screenshot.recycle()
        if (!fixture.hasVisualContent()) {
            fixture.recycle()
            error("Captured formal fixture is visually blank")
        }
        fixture
    }
}

private fun Bitmap.hasVisualContent(): Boolean {
    val sampledColors = linkedSetOf<Int>()
    repeat(ContentSampleRows) { row ->
        val y = (row * (height - 1)) / (ContentSampleRows - 1)
        repeat(ContentSampleColumns) { column ->
            val x = (column * (width - 1)) / (ContentSampleColumns - 1)
            sampledColors += getPixel(x, y)
            if (sampledColors.size >= MinimumSampledColors) return true
        }
    }
    return false
}

private fun <T> captureWithRetry(block: () -> T): T {
    var lastFailure: Throwable? = null
    repeat(CaptureAttempts) { attempt ->
        try {
            return block()
        } catch (failure: AssertionError) {
            lastFailure = failure
        } catch (failure: RuntimeException) {
            lastFailure = failure
        }
        if (attempt < CaptureAttempts - 1) {
            SystemClock.sleep(CaptureRetryDelayMillis)
        }
    }
    throw checkNotNull(lastFailure)
}

private const val FormalFixtureWidthDp = 390f
private const val FormalFixtureHeightDp = 884f
private const val CaptureAttempts = 8
private const val CaptureRetryDelayMillis = 400L
private const val ContentSampleRows = 96
private const val ContentSampleColumns = 48
private const val MinimumSampledColors = 8
