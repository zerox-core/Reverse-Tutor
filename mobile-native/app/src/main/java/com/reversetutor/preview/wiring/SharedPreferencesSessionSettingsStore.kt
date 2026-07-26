package com.reversetutor.preview.wiring

import android.content.Context
import android.content.SharedPreferences
import com.reversetutor.feature.chat.ConfigurationDifference
import com.reversetutor.feature.chat.ConversationStrategy
import com.reversetutor.feature.chat.PendingSourceDelete
import com.reversetutor.feature.chat.ProtectedChangeConfirmation
import com.reversetutor.feature.chat.SessionGoalPlan
import com.reversetutor.feature.chat.SessionSettingsDocument
import com.reversetutor.feature.chat.SessionSettingsProfile
import com.reversetutor.feature.chat.SessionSettingsStore
import com.reversetutor.feature.chat.SessionSettingsStoredState
import com.reversetutor.feature.chat.SessionSource
import com.reversetutor.feature.chat.SourceReadState
import com.reversetutor.feature.chat.SourceUndo
import com.reversetutor.feature.chat.SourceUndoKind
import com.reversetutor.feature.chat.TagFieldSelection
import com.reversetutor.feature.chat.TagSelectionValue
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesSessionSettingsStore internal constructor(
    private val preferences: SharedPreferences
) : SessionSettingsStore {
    constructor(context: Context) : this(
        context.getSharedPreferences("session_settings_feature_state", Context.MODE_PRIVATE)
    )

    override fun load(sessionId: String): SessionSettingsStoredState? =
        preferences.getString(key(sessionId), null)?.let { encoded ->
            runCatching { decodeState(JSONObject(encoded)) }.getOrNull()
        }?.withSourceUsage(sourceLastUsedAt(sessionId))

    override fun save(sessionId: String, state: SessionSettingsStoredState) {
        val usage = mergeUsage(sourceLastUsedAt(sessionId), state.sourceLastUsedAt)
        val persisted = state.withSourceUsage(usage)
        check(preferences.edit()
            .putString(key(sessionId), encodeState(persisted).toString())
            .putString(sourceUsageKey(sessionId), encodeSourceUsage(usage))
            .commit()) {
            "Unable to persist session settings"
        }
    }

    override fun sourceLastUsedAt(sessionId: String): Map<String, Long> =
        preferences.getString(sourceUsageKey(sessionId), null)?.let { encoded ->
            runCatching { decodeSourceUsage(encoded) }.getOrNull()
        }.orEmpty()

    override fun recordSourcesUsed(sessionId: String, sourceIds: Set<String>, usedAtEpochMillis: Long) {
        if (sourceIds.isEmpty()) return
        val usage = mergeUsage(
            sourceLastUsedAt(sessionId),
            sourceIds.associateWith { usedAtEpochMillis }
        )
        check(preferences.edit().putString(sourceUsageKey(sessionId), encodeSourceUsage(usage)).commit()) {
            "Unable to persist Source usage"
        }
    }

    override fun remove(sessionId: String) {
        check(preferences.edit().remove(key(sessionId)).remove(sourceUsageKey(sessionId)).commit()) {
            "Unable to remove session settings"
        }
    }

    private fun key(sessionId: String): String = "session_settings_v1_$sessionId"
    private fun sourceUsageKey(sessionId: String): String = "session_source_usage_v1_$sessionId"
}

private fun SessionSettingsStoredState.withSourceUsage(usage: Map<String, Long>): SessionSettingsStoredState = copy(
    sources = sources.map { source ->
        source.copy(lastUsedAtEpochMillis = maxOf(source.lastUsedAtEpochMillis, usage[source.id] ?: Long.MIN_VALUE))
    },
    sourceLastUsedAt = mergeUsage(sourceLastUsedAt, usage)
)

private fun mergeUsage(first: Map<String, Long>, second: Map<String, Long>): Map<String, Long> =
    (first.keys + second.keys).associateWith { sourceId ->
        maxOf(first[sourceId] ?: Long.MIN_VALUE, second[sourceId] ?: Long.MIN_VALUE)
    }

private fun encodeSourceUsage(usage: Map<String, Long>): String = packValues(
    usage.entries.flatMap { (sourceId, epochMillis) -> listOf(sourceId, epochMillis.toString()) }
)

private fun decodeSourceUsage(encoded: String): Map<String, Long> = unpackValues(encoded)
    .chunked(2)
    .associate { pair ->
        require(pair.size == 2) { "Incomplete Source usage entry" }
        pair[0] to pair[1].toLong()
    }

private fun packValues(values: List<String>): String = buildString {
    values.forEach { value ->
        append(value.length)
        append(':')
        append(value)
    }
}

private fun unpackValues(value: String): List<String> {
    if (value.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    var cursor = 0
    while (cursor < value.length) {
        val separator = value.indexOf(':', cursor)
        require(separator > cursor) { "Invalid length-prefixed value" }
        val length = value.substring(cursor, separator).toInt()
        val start = separator + 1
        val end = start + length
        require(end <= value.length) { "Truncated length-prefixed value" }
        result += value.substring(start, end)
        cursor = end
    }
    return result
}

private fun encodeState(state: SessionSettingsStoredState): JSONObject = JSONObject()
    .put("applied", encodeDocument(state.applied))
    .put("form", encodeDocument(state.form))
    .put("sources", JSONArray().also { array -> state.sources.forEach { array.put(encodeSource(it)) } })
    .put("confirmation", state.pendingConfirmation?.let(::encodeConfirmation) ?: JSONObject.NULL)
    .put("pendingDelete", state.pendingSourceDelete?.let(::encodePendingDelete) ?: JSONObject.NULL)
    .put("undo", state.sourceUndo?.let(::encodeUndo) ?: JSONObject.NULL)
    .put("error", state.errorMessage ?: JSONObject.NULL)
    .put("sourceLastUsed", JSONObject().also { values ->
        state.sourceLastUsedAt.forEach { (sourceId, epochMillis) -> values.put(sourceId, epochMillis) }
    })

private fun decodeState(json: JSONObject): SessionSettingsStoredState = SessionSettingsStoredState(
    applied = decodeDocument(json.getJSONObject("applied")),
    form = decodeDocument(json.getJSONObject("form")),
    sources = json.getJSONArray("sources").objects(::decodeSource),
    pendingConfirmation = json.nullableObject("confirmation")?.let(::decodeConfirmation),
    pendingSourceDelete = json.nullableObject("pendingDelete")?.let(::decodePendingDelete),
    sourceUndo = json.nullableObject("undo")?.let(::decodeUndo),
    errorMessage = json.nullableString("error"),
    sourceLastUsedAt = json.optJSONObject("sourceLastUsed")?.let { values ->
        values.keys().asSequence().associateWith(values::getLong)
    }.orEmpty()
)

private fun encodeDocument(document: SessionSettingsDocument): JSONObject = JSONObject()
    .put("profile", JSONObject()
        .put("title", document.profile.title)
        .put("name", document.profile.learnerDisplayName)
        .put("role", document.profile.learnerRole)
        .put("avatar", document.profile.avatarVisible)
        .put("personality", document.profile.personality)
        .put("habits", document.profile.interactionHabits))
    .put("goal", JSONObject()
        .put("primary", document.goalPlan.primaryGoal)
        .put("deadline", document.goalPlan.deadline)
        .put("scope", document.goalPlan.learningScope)
        .put("modules", document.goalPlan.modules)
        .put("milestones", document.goalPlan.stageMilestones)
        .put("weekly", document.goalPlan.weeklyPlan)
        .put("state", document.goalPlan.currentState))
    .put("strategy", JSONObject()
        .put("feedback", document.strategy.feedbackIntensity)
        .put("probing", document.strategy.probingIntensity)
        .put("scaffolding", document.strategy.scaffoldingIntensity)
        .put("correction", document.strategy.correctionPersistence)
        .put("review", document.strategy.reviewFrequency)
        .put("tone", document.strategy.speakingTone))
    .put("snapshot", NewSessionSnapshotCodec.encodeConfiguration(document.snapshot))
    .put("tags", JSONObject().also { tags ->
        document.quickTags.forEach { (field, selection) ->
            tags.put(field, JSONArray().also { values ->
                selection.values.forEach { value ->
                    values.put(JSONObject().put("id", value.tagId ?: JSONObject.NULL).put("text", value.text))
                }
            })
        }
    })

private fun decodeDocument(json: JSONObject): SessionSettingsDocument {
    val profile = json.getJSONObject("profile")
    val goal = json.getJSONObject("goal")
    val strategy = json.getJSONObject("strategy")
    val tags = json.getJSONObject("tags")
    return SessionSettingsDocument(
        profile = SessionSettingsProfile(
            title = profile.getString("title"),
            learnerDisplayName = profile.getString("name"),
            learnerRole = profile.getString("role"),
            avatarVisible = profile.getBoolean("avatar"),
            personality = profile.getString("personality"),
            interactionHabits = profile.getString("habits")
        ),
        goalPlan = SessionGoalPlan(
            primaryGoal = goal.getString("primary"),
            deadline = goal.getString("deadline"),
            learningScope = goal.getString("scope"),
            modules = goal.getString("modules"),
            stageMilestones = goal.getString("milestones"),
            weeklyPlan = goal.getString("weekly"),
            currentState = goal.getString("state")
        ),
        strategy = ConversationStrategy(
            feedbackIntensity = strategy.getInt("feedback"),
            probingIntensity = strategy.getInt("probing"),
            scaffoldingIntensity = strategy.getInt("scaffolding"),
            correctionPersistence = strategy.getString("correction"),
            reviewFrequency = strategy.getString("review"),
            speakingTone = strategy.getString("tone")
        ),
        snapshot = NewSessionSnapshotCodec.decodeConfiguration(json.getString("snapshot")),
        quickTags = tags.keys().asSequence().associateWith { field ->
            TagFieldSelection(tags.getJSONArray(field).objects { value ->
                TagSelectionValue(value.nullableString("id"), value.getString("text"))
            })
        }
    )
}

private fun encodeSource(source: SessionSource): JSONObject = JSONObject()
    .put("id", source.id)
    .put("display", source.displayName)
    .put("managed", source.managedName)
    .put("type", source.typeLabel)
    .put("read", source.readState.name)
    .put("current", source.currentSessionReferenced)
    .put("owners", JSONArray(source.referenceOwnerIds))
    .put("lastUsed", source.lastUsedAtEpochMillis)
    .put("preview", source.preview)
    .put("bytes", source.bytesRetained)
    .put("previous", source.previousRevisionId ?: JSONObject.NULL)
    .put("pending", source.deletionPending)

private fun decodeSource(json: JSONObject): SessionSource = SessionSource(
    id = json.getString("id"),
    displayName = json.getString("display"),
    managedName = json.getString("managed"),
    typeLabel = json.getString("type"),
    readState = SourceReadState.valueOf(json.getString("read")),
    currentSessionReferenced = json.getBoolean("current"),
    referenceOwnerIds = json.getJSONArray("owners").strings(),
    lastUsedAtEpochMillis = json.getLong("lastUsed"),
    preview = json.getString("preview"),
    bytesRetained = json.getBoolean("bytes"),
    previousRevisionId = json.nullableString("previous"),
    deletionPending = json.getBoolean("pending")
)

private fun encodeConfirmation(value: ProtectedChangeConfirmation): JSONObject = JSONObject()
    .put("differences", JSONArray().also { array -> value.differences.forEach { array.put(encodeDifference(it)) } })

private fun decodeConfirmation(json: JSONObject): ProtectedChangeConfirmation =
    ProtectedChangeConfirmation(json.getJSONArray("differences").objects(::decodeDifference))

private fun encodeDifference(value: ConfigurationDifference): JSONObject = JSONObject()
    .put("field", value.field).put("before", value.before).put("after", value.after)

private fun decodeDifference(json: JSONObject) = ConfigurationDifference(
    json.getString("field"), json.getString("before"), json.getString("after")
)

private fun encodePendingDelete(value: PendingSourceDelete): JSONObject = JSONObject()
    .put("sourceId", value.sourceId)
    .put("owners", JSONArray(value.impactedOwnerIds))
    .put("enabled", value.deleteEnabled)
    .put("requiresConfirmation", value.requiresImpactConfirmation)

private fun decodePendingDelete(json: JSONObject) = PendingSourceDelete(
    json.getString("sourceId"),
    json.getJSONArray("owners").strings(),
    json.getBoolean("enabled"),
    json.optBoolean("requiresConfirmation", false)
)

private fun encodeUndo(value: SourceUndo): JSONObject = JSONObject()
    .put("kind", value.kind.name)
    .put("sourceId", value.sourceId)
    .put("previous", encodeSource(value.previous))
    .put("expires", value.expiresAtEpochMillis)
    .put("selections", JSONArray(value.previousSelectionIds))

private fun decodeUndo(json: JSONObject) = SourceUndo(
    SourceUndoKind.valueOf(json.getString("kind")),
    json.getString("sourceId"),
    decodeSource(json.getJSONObject("previous")),
    json.getLong("expires"),
    json.getJSONArray("selections").strings()
)

private fun JSONObject.nullableObject(name: String): JSONObject? =
    if (isNull(name)) null else getJSONObject(name)

private fun JSONObject.nullableString(name: String): String? =
    if (isNull(name)) null else getString(name)

private fun JSONArray.strings(): List<String> = List(length()) { index -> getString(index) }

private fun <T> JSONArray.objects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }
