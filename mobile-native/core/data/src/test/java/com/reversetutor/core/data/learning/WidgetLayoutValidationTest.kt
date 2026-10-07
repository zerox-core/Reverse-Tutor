package com.reversetutor.core.data.learning

import com.reversetutor.core.model.WidgetLayoutPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WidgetLayoutValidationTest {
    @Test
    fun rejectsBlankSpaceId() {
        assertThrows(IllegalArgumentException::class.java) {
            requireSpaceId("  ")
        }
    }

    @Test
    fun rejectsPreferenceSpaceMismatch() {
        assertInvalid(
            "space-1",
            listOf(preference(spaceId = "space-2"))
        )
    }

    @Test
    fun rejectsBlankAndDuplicateWidgetIds() {
        assertInvalid("space-1", listOf(preference(widgetId = "  ")))
        assertInvalid(
            "space-1",
            listOf(
                preference(widgetId = "summary", order = 0),
                preference(widgetId = "summary", order = 1)
            )
        )
    }

    @Test
    fun rejectsNegativeAndDuplicateOrders() {
        assertInvalid("space-1", listOf(preference(order = -1)))
        assertInvalid(
            "space-1",
            listOf(
                preference(widgetId = "summary", order = 0),
                preference(widgetId = "progress", order = 0)
            )
        )
    }

    @Test
    fun acceptsEmptyAndCompleteLayouts() {
        validateCompleteLayout("space-1", emptyList())
        validateCompleteLayout(
            "space-1",
            listOf(
                preference(widgetId = "summary", order = 1),
                preference(widgetId = "progress", order = 0)
            )
        )

        assertEquals("space-1", requireSpaceId(" space-1 "))
    }

    private fun assertInvalid(
        spaceId: String,
        preferences: List<WidgetLayoutPreference>
    ) {
        assertThrows(IllegalArgumentException::class.java) {
            validateCompleteLayout(spaceId, preferences)
        }
    }

    private fun preference(
        spaceId: String = "space-1",
        widgetId: String = "summary",
        order: Int = 0
    ): WidgetLayoutPreference = WidgetLayoutPreference(
        spaceId = spaceId,
        widgetId = widgetId,
        order = order
    )
}
