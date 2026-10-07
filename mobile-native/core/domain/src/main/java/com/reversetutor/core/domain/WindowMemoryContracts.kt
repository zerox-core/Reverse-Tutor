package com.reversetutor.core.domain

/**
 * Window-local memory: sliding window + layering contracts.
 *
 * Design source: product decisions locked with the user on 2026-09-17.
 *  - Sliding window over recent raw messages preserves detail; structured
 *    memory layers sit on top (observation -> pattern -> active value).
 *  - Extraction runs in batches when messages leave the window, never
 *    per sentence and never blocking the chat loop.
 *  - Rolling summary of evicted batches stays ON (details matter for a
 *    teaching product).
 *  - Rule-first extraction; cloud cheap-model extraction arrives later
 *    via user-provided API keys (BYOK), so early versions carry no
 *    platform-side model cost.
 *
 * This file is pure and Android-free: no clocks, no LLM calls. Callers
 * pass estimated token counts and hours; the policy only decides.
 */

/** Sliding window configuration for one window's raw message buffer. */
data class WindowMemoryConfig(
    val tokenBudget: Int = DEFAULT_TOKEN_BUDGET,
    val messageCap: Int = DEFAULT_MESSAGE_CAP,
    val rollingSummaryEnabled: Boolean = true,
) {
    companion object {
        /** Industry-average order of magnitude for a verbatim recent-turn window. */
        const val DEFAULT_TOKEN_BUDGET: Int = 6000

        /** Secondary guard so many tiny messages cannot blow past the budget. */
        const val DEFAULT_MESSAGE_CAP: Int = 60
    }
}

/** Minimal view of a message the window policy needs to reason about. */
data class WindowMessageView(
    val messageId: String,
    val estimatedTokens: Int,
    val occurredAtEpochMillis: Long,
)

/**
 * Result of a window eviction pass.
 * [kept] stays verbatim in the window (oldest-first, as input order);
 * [evicted] is the batch handed to the compression/extraction pipeline,
 * also oldest-first so summaries and observation batches stay chronological.
 */
data class WindowEvictionResult(
    val kept: List<WindowMessageView>,
    val evicted: List<WindowMessageView>,
)

/** How much a signal matters for memory, computed from category and hour. */
enum class SignalSalience { LOW, NORMAL, HIGH }

/** Category dimension for window-local (Layer-1) observations. */
enum class WindowObservationCategory {
    /** What the user is doing right now, e.g. late-night coding. */
    ACTIVITY_CONTEXT,

    /** Everyday-life mentions, e.g. meals, sleep. */
    DAILY_LIFE,

    /** Learning moments: a careless mistake, a key question, a breakthrough. */
    LEARNING_EVENT,

    /** Explicit goals the user states. */
    GOAL_STATEMENT,

    /** User responses to agent reminders; first-class feedback signal. */
    REMINDER_FEEDBACK,

    /** Taste/preference signals. */
    PREFERENCE_SIGNAL,
}

/** A Layer-1 observation reduced to what the pattern layer needs. */
data class CategorizedObservation(
    val category: WindowObservationCategory,
    val occurredAtEpochMillis: Long,
    val hourOfDay: Int,
)

/**
 * Layer-2 pattern: repeated Layer-1 observations of one category, clustered
 * into count + time spread + hour distribution. This is the layer that keeps
 * "codes at night, around 2-3am, three times this week" without keeping any
 * raw text, and the only layer below active values that feeds global projection.
 */
data class ObservationPattern(
    val category: WindowObservationCategory,
    val occurrenceCount: Int,
    val firstSeenEpochMillis: Long,
    val lastSeenEpochMillis: Long,
    val activeHours: Set<Int>,
)

/** Pure policy for the window memory layers. Deterministic, testable. */
object WindowMemoryPolicy {

    const val LATE_NIGHT_START_HOUR: Int = 23
    const val LATE_NIGHT_END_HOUR: Int = 6

    /**
     * Keep the newest messages that fit the token budget and message cap;
     * everything older evicts. Input is oldest-first; output preserves that.
     */
    fun evict(
        messages: List<WindowMessageView>,
        config: WindowMemoryConfig = WindowMemoryConfig(),
    ): WindowEvictionResult {
        val kept = ArrayDeque<WindowMessageView>()
        var tokens = 0
        for (message in messages.asReversed()) {
            val fits = kept.size < config.messageCap &&
                tokens + message.estimatedTokens <= config.tokenBudget
            if (!fits) break
            kept.addFirst(message)
            tokens += message.estimatedTokens
        }
        val keptList = kept.toList()
        return WindowEvictionResult(
            kept = keptList,
            evicted = messages.dropLast(keptList.size),
        )
    }

    /**
     * Salience of a signal given its category and the local hour it happened.
     * Feedback on reminders and explicit goals are always high-weight;
     * late-night hours raise activity and learning signals (the user's
     * all-nighter example); everything else defaults to normal.
     */
    fun salienceOf(category: WindowObservationCategory, hourOfDay: Int): SignalSalience {
        if (category == WindowObservationCategory.REMINDER_FEEDBACK ||
            category == WindowObservationCategory.GOAL_STATEMENT
        ) {
            return SignalSalience.HIGH
        }
        val lateNight = hourOfDay >= LATE_NIGHT_START_HOUR || hourOfDay < LATE_NIGHT_END_HOUR
        if (lateNight && (
                category == WindowObservationCategory.ACTIVITY_CONTEXT ||
                    category == WindowObservationCategory.LEARNING_EVENT
                )
        ) {
            return SignalSalience.HIGH
        }
        return SignalSalience.NORMAL
    }

    /** Cluster Layer-1 observations into Layer-2 patterns, one per category. */
    fun aggregatePatterns(observations: List<CategorizedObservation>): List<ObservationPattern> =
        observations.groupBy { it.category }.map { (category, group) ->
            ObservationPattern(
                category = category,
                occurrenceCount = group.size,
                firstSeenEpochMillis = group.minOf { it.occurredAtEpochMillis },
                lastSeenEpochMillis = group.maxOf { it.occurredAtEpochMillis },
                activeHours = group.map { it.hourOfDay }.toSet(),
            )
        }
}
