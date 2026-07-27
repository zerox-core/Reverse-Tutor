package com.reversetutor.preview.wiring

import com.reversetutor.feature.memory.WeeklyDashboardConfiguration
import com.reversetutor.feature.memory.WeeklyConfigurationLoadResult
import com.reversetutor.feature.memory.WeeklyConfigurationSaveResult
import com.reversetutor.feature.memory.WeeklySourceMode
import com.reversetutor.feature.memory.WeeklyWidgetKind
import com.reversetutor.feature.memory.WeeklyWidgetSize
import com.reversetutor.feature.memory.WeeklyWidgetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyDashboardConfigurationCodecTest {
    @Test
    fun layoutOrderSizeVisibilitySourceDefaultAndPlanOrderRoundTrip() {
        val defaults = WeeklyDashboardConfiguration.defaults()
        val configuration = defaults.copy(
            widgets = defaults.widgets.reversed().map { widget ->
                when (widget.kind) {
                    WeeklyWidgetKind.TodayPlan -> widget.copy(
                        size = WeeklyWidgetSize.TwoByTwo,
                        source = WeeklyWidgetSource(WeeklySourceMode.Sessions, linkedSetOf("session-b", "session-a"))
                    )
                    WeeklyWidgetKind.TokenUsage -> widget.copy(visible = false)
                    else -> widget
                }
            },
            defaultScope = WeeklyWidgetSource.None,
            planOrderIds = listOf("plan-b", "plan-a")
        ).normalized()

        val decoded = WeeklyDashboardConfigurationCodec.decode(
            WeeklyDashboardConfigurationCodec.encode(configuration)
        )

        assertEquals(configuration, decoded)
        assertEquals(WeeklyWidgetKind.DecorationImage, decoded.widgets.first().kind)
    }

    @Test
    fun productionStoreSurvivesRecreationAndRejectsCorruptSnapshots() {
        val preferences = MemoryWeeklyDashboardPreferences()
        val snapshot = WeeklyDashboardConfiguration.defaults().copy(
            defaultScope = WeeklyWidgetSource(WeeklySourceMode.Sessions, setOf("session-a"))
        )
        assertEquals(
            WeeklyConfigurationSaveResult.Saved,
            SharedPreferencesWeeklyDashboardConfigurationStore(preferences).save(snapshot)
        )

        val loaded = SharedPreferencesWeeklyDashboardConfigurationStore(preferences).load()
        assertEquals(snapshot.normalized(), (loaded as WeeklyConfigurationLoadResult.Loaded).configuration)

        preferences.put("weekly_dashboard_v1", "corrupt")
        assertTrue(SharedPreferencesWeeklyDashboardConfigurationStore(preferences).load() is WeeklyConfigurationLoadResult.Failure)
    }

    @Test
    fun productionStoreReportsWriteFailureWithoutAdvancingSnapshot() {
        val preferences = MemoryWeeklyDashboardPreferences(failWrites = true)
        val result = SharedPreferencesWeeklyDashboardConfigurationStore(preferences)
            .save(WeeklyDashboardConfiguration.defaults())

        assertTrue(result is WeeklyConfigurationSaveResult.Failure)
        assertEquals(WeeklyConfigurationLoadResult.Missing,
            SharedPreferencesWeeklyDashboardConfigurationStore(preferences).load())
    }
}

private class MemoryWeeklyDashboardPreferences(private val failWrites: Boolean = false) : WeeklyDashboardPreferences {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) {
        if (failWrites) error("disk full")
        values[key] = value
    }
}
