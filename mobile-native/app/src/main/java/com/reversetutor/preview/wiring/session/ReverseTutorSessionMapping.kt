package com.reversetutor.preview.wiring.session

import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.session.StudentProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * 会话快照 → 反转家教算法内核 [RTSession] 的确定性映射（2026-10-18 接入）。
 *
 * 修复背景（真机反馈「上下文没了」）：创建窗口建出的会话此前 `builtInPresetId` 为空，
 * 被路由到普通 RAG 链路，采集到的人设也随之丢失。接入算法内核后，快照里的
 * Role / 画像 / 性格 / 对话策略 / 目标必须原样带进内核提示词——否则同一个用户在两条
 * 链路上会看到两个不同性格的学生。
 *
 * 这里刻意**不**猜：采集不到年级就留空（`CurriculumCatalog.gradeIndex` 对空串/未知
 * 学段返回 -1，即不做学段判断），而不是沿用旧的写死「初二」把大一学生误判成初二。
 */
private const val DEFAULT_TOPIC = "反转家教"
private const val DEFAULT_STUDENT_ID = "小理"

/** 内置角色卡（文采层）：提供示例声线；人设段的身份/性格优先于它。 */
private const val DEFAULT_CARD_ID = "proud_average"

/**
 * 年级词表：**从具体到笼统**，命中即返回。
 * 例「对日常生活现象充满好奇的大一学生」→ 命中「大一」；
 *    「AI 学生 李想：初二 · 概念懂一半」→ 命中「初二」（与接入前的写死值一致）。
 */
private val GRADE_TOKENS = listOf(
    "小一", "小二", "小三", "小四", "小五", "小六",
    "初一", "初二", "初三",
    "高一", "高二", "高三",
    "大一", "大二", "大三", "大四",
    "小学", "初中", "高中", "大学"
)

/**
 * 从快照文本里推导年级；找不到任何年级信号时返回空串（不猜）。
 *
 * 按 [learnerRole] → [learnerProfile] → 标题 → 目标的优先级扫描：身份描述最可能写明
 * 学段，标题最可能只是主题词，放最后以免误伤（例如标题「高中数学」但身份是「大一」）。
 */
internal fun deriveRTGrade(snapshot: NewSessionConfiguration?): String {
    if (snapshot == null) return ""
    val candidates = listOf(
        snapshot.learnerRole,
        snapshot.learnerProfile,
        snapshot.title,
        snapshot.goal
    )
    for (text in candidates) {
        if (text.isBlank()) continue
        for (token in GRADE_TOKENS) {
            if (text.contains(token)) return token
        }
    }
    return ""
}

/** 主题：沿用「标题即主题」口径（契约 currentKnowledgePoint 亦取此值）。 */
internal fun deriveRTTopic(snapshot: NewSessionConfiguration?): String =
    snapshot?.title?.trim().orEmpty().ifBlank { DEFAULT_TOPIC }

/** 学生 id：优先创建窗口采集的显示名，缺省回退内置「小理」。 */
internal fun deriveRTStudentId(snapshot: NewSessionConfiguration?): String =
    snapshot?.learnerDisplayName?.trim().orEmpty().ifBlank { DEFAULT_STUDENT_ID }

/** 组装内核会话：人设来自创建窗口，知识状态从零起步（水平 1、第 0 轮）。 */
internal fun NewSessionConfiguration?.toReverseTutorSession(sessionId: String): RTSession = RTSession(
    sessionId = sessionId,
    topic = deriveRTTopic(this),
    studentId = deriveRTStudentId(this),
    grade = deriveRTGrade(this),
    cardId = DEFAULT_CARD_ID,
    understandingLevel = 1,
    initialUnderstandingLevel = 1,
    currentRound = 0,
    studentProfile = StudentProfile(
        role = this?.learnerRole.orEmpty().trim(),
        profile = this?.learnerProfile.orEmpty().trim(),
        persona = this?.persona.orEmpty().trim(),
        dialogueStrategy = this?.dialogueStrategy.orEmpty().trim(),
        goal = this?.goal.orEmpty().trim()
    )
)

/**
 * 从长期记忆的会话快照（`rt_session_snapshots.payload`）恢复 [RTSession]。
 *
 * 解决冷启动丢失认知状态问题：如果会话此前在内核执行过并落过盘，
 * 恢复出来的理解水平、掌握概念、困惑概念、轮次与历史滚动摘要均被保留。
 */
internal fun JSONObject.toReverseTutorSessionFromSnapshot(): RTSession {
    val sid = optString("session_id")
    val topic = optString("topic", DEFAULT_TOPIC)
    val studentId = optString("student_id", DEFAULT_STUDENT_ID)
    val grade = optString("grade", "")
    val level = optInt("understanding_level", 1)
    val initialLevel = optInt("initial_understanding_level", 1)
    val round = optInt("current_round", 0)
    val historySummary = optString("history_summary", "")

    val masteredList = mutableListOf<String>()
    optJSONArray("mastered_concepts")?.let { arr ->
        for (i in 0 until arr.length()) masteredList.add(arr.optString(i))
    }

    val confusedList = mutableListOf<String>()
    optJSONArray("confused_concepts")?.let { arr ->
        for (i in 0 until arr.length()) confusedList.add(arr.optString(i))
    }

    val profileObj = optJSONObject("student_profile")
    val profile = if (profileObj != null) {
        StudentProfile(
            role = profileObj.optString("role", ""),
            profile = profileObj.optString("profile", ""),
            persona = profileObj.optString("persona", ""),
            dialogueStrategy = profileObj.optString("dialogue_strategy", ""),
            goal = profileObj.optString("goal", "")
        )
    } else {
        StudentProfile()
    }

    return RTSession(
        sessionId = sid,
        topic = topic,
        studentId = studentId,
        grade = grade,
        cardId = DEFAULT_CARD_ID,
        understandingLevel = level,
        initialUnderstandingLevel = initialLevel,
        currentRound = round,
        masteredConcepts = masteredList,
        confusedConcepts = confusedList,
        studentProfile = profile,
        historySummary = historySummary
    )
}

/**
 * 将 [RTSession] 序列化为快照 JSON，与 [toReverseTutorSessionFromSnapshot] 对称。
 */
internal fun RTSession.toSnapshotPayload(): JSONObject {
    val obj = JSONObject()
        .put("session_id", sessionId)
        .put("student_id", studentId)
        .put("topic", topic)
        .put("status", status)
        .put("grade", grade)
        .put("understanding_level", understandingLevel)
        .put("initial_understanding_level", initialLevelOrCurrent())
        .put("current_round", currentRound)
        .put("history_summary", historySummary)
        .put("mastered_concepts", org.json.JSONArray(masteredConcepts))
        .put("confused_concepts", org.json.JSONArray(confusedConcepts))

    val profileObj = JSONObject()
        .put("role", studentProfile.role)
        .put("profile", studentProfile.profile)
        .put("persona", studentProfile.persona)
        .put("dialogue_strategy", studentProfile.dialogueStrategy)
        .put("goal", studentProfile.goal)
    obj.put("student_profile", profileObj)

    val struggles = org.json.JSONArray()
    for (e in struggleEvents) {
        struggles.put(JSONObject().put("concept", e.concept).put("round", e.round))
    }
    obj.put("struggle_events", struggles)
    return obj
}

private fun RTSession.initialLevelOrCurrent(): Int =
    if (initialUnderstandingLevel > 0) initialUnderstandingLevel else understandingLevel

