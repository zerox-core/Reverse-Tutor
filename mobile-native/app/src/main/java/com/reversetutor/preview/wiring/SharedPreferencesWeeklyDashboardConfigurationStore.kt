package com.reversetutor.preview.wiring

import android.content.Context
import android.content.SharedPreferences
import com.reversetutor.feature.memory.WeeklyDashboardConfiguration
import com.reversetutor.feature.memory.WeeklyDashboardConfigurationStore
import com.reversetutor.feature.memory.WeeklyConfigurationLoadResult
import com.reversetutor.feature.memory.WeeklyConfigurationSaveResult
import com.reversetutor.feature.memory.WeeklySourceMode
import com.reversetutor.feature.memory.WeeklyWidgetConfiguration
import com.reversetutor.feature.memory.WeeklyWidgetKind
import com.reversetutor.feature.memory.WeeklyWidgetSize
import com.reversetutor.feature.memory.WeeklyWidgetSource

class SharedPreferencesWeeklyDashboardConfigurationStore(
    private val preferences: WeeklyDashboardPreferences
) : WeeklyDashboardConfigurationStore {
    constructor(context: Context) : this(
        AndroidWeeklyDashboardPreferences(
            context.getSharedPreferences("weekly_dashboard_feature_state", Context.MODE_PRIVATE)
        )
    )

    override fun load(): WeeklyConfigurationLoadResult {
        val encoded = runCatching { preferences.get(ConfigurationKey) }.getOrElse { error ->
            return WeeklyConfigurationLoadResult.Failure(
                error.message?.takeIf(String::isNotBlank) ?: "无法读取组件配置"
            )
        } ?: return WeeklyConfigurationLoadResult.Missing
        return runCatching { WeeklyDashboardConfigurationCodec.decode(encoded) }
            .fold(
                onSuccess = WeeklyConfigurationLoadResult::Loaded,
                onFailure = {
                    WeeklyConfigurationLoadResult.Failure("组件配置已损坏，请重试或恢复默认配置")
                }
            )
    }

    override fun save(configuration: WeeklyDashboardConfiguration): WeeklyConfigurationSaveResult =
        runCatching {
            preferences.put(
                ConfigurationKey,
                WeeklyDashboardConfigurationCodec.encode(configuration.normalized())
            )
        }.fold(
            onSuccess = { WeeklyConfigurationSaveResult.Saved },
            onFailure = { error ->
                WeeklyConfigurationSaveResult.Failure(
                    error.message?.takeIf(String::isNotBlank) ?: "无法保存组件配置"
                )
            }
        )

    private companion object {
        const val ConfigurationKey = "weekly_dashboard_v1"
    }
}

interface WeeklyDashboardPreferences {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

private class AndroidWeeklyDashboardPreferences(
    private val preferences: SharedPreferences
) : WeeklyDashboardPreferences {
    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) {
            "Unable to persist weekly dashboard configuration"
        }
    }
}

object WeeklyDashboardConfigurationCodec {
    private const val Version = "1"

    fun encode(configuration: WeeklyDashboardConfiguration): String = pack(
        listOf(
            Version,
            encodeSource(configuration.defaultScope),
            pack(configuration.planOrderIds),
            pack(configuration.widgets.map(::encodeWidget))
        )
    )

    fun decode(value: String): WeeklyDashboardConfiguration {
        val fields = unpack(value)
        require(fields.size == 4 && fields[0] == Version) { "Unsupported weekly dashboard configuration" }
        return WeeklyDashboardConfiguration(
            widgets = unpack(fields[3]).map(::decodeWidget),
            defaultScope = decodeSource(fields[1]),
            planOrderIds = unpack(fields[2])
        ).normalized()
    }

    private fun encodeWidget(widget: WeeklyWidgetConfiguration): String = pack(
        listOf(
            widget.id,
            widget.kind.name,
            widget.size.name,
            widget.visible.toString(),
            widget.source?.let(::encodeSource).orEmpty()
        )
    )

    private fun decodeWidget(value: String): WeeklyWidgetConfiguration {
        val fields = unpack(value)
        require(fields.size == 5) { "Unexpected weekly widget field count" }
        val kind = WeeklyWidgetKind.valueOf(fields[1])
        return WeeklyWidgetConfiguration(
            id = fields[0],
            kind = kind,
            size = WeeklyWidgetSize.valueOf(fields[2]),
            visible = fields[3].toBooleanStrict(),
            source = fields[4].takeIf(String::isNotEmpty)?.let(::decodeSource)
        )
    }

    private fun encodeSource(source: WeeklyWidgetSource): String = pack(
        listOf(source.mode.name, pack(source.sessionIds.toList()))
    )

    private fun decodeSource(value: String): WeeklyWidgetSource {
        val fields = unpack(value)
        require(fields.size == 2) { "Unexpected weekly source field count" }
        return WeeklyWidgetSource(
            mode = WeeklySourceMode.valueOf(fields[0]),
            sessionIds = unpack(fields[1]).toCollection(linkedSetOf())
        ).normalized()
    }

    private fun pack(values: List<String>): String = buildString {
        values.forEach { value -> append(value.length).append(':').append(value) }
    }

    private fun unpack(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var cursor = 0
        while (cursor < value.length) {
            val separator = value.indexOf(':', cursor)
            require(separator > cursor) { "Invalid weekly configuration length" }
            val length = value.substring(cursor, separator).toInt()
            val start = separator + 1
            val end = start + length
            require(end <= value.length) { "Truncated weekly configuration" }
            result += value.substring(start, end)
            cursor = end
        }
        return result
    }
}
