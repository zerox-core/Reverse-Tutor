package com.reversetutor.preview.wiring.session

import com.reversetutor.feature.chat.FormalLearningPresets
import com.reversetutor.feature.chat.NewSessionConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快照 → 内核会话映射的确定性验收（2026-10-18）。
 *
 * 真机反馈「上下文没了」的另一半：即便进了算法内核，创建窗口采集的人设此前也整段
 * 丢失（内核写死 topic=标题、grade=初二、cardId=proud_average）。本组用例锁死映射
 * 规则，尤其是**不猜年级**这条——把大一学生误判成初二会让课标工具给出错误的「未学过」。
 */
class ReverseTutorSessionMappingTest {

    @Test
    fun mapsCreationWindowPersonaIntoKernelSession() {
        val snapshot = NewSessionConfiguration(
            title = "趣味心理学基础",
            learnerRole = "对日常生活现象充满好奇的大一学生",
            learnerProfile = "喜欢用生活里的例子提问",
            persona = "性格活泼好奇，理解卡壳时会诚实说困惑",
            dialogueStrategy = "先让他举例，再一起命名现象",
            goal = "日常生活中的趣味心理学",
            learnerDisplayName = "小明"
        )

        val session = snapshot.toReverseTutorSession("session-1")

        assertEquals("session-1", session.sessionId)
        assertEquals("趣味心理学基础", session.topic)
        assertEquals("小明", session.studentId)
        assertEquals("大一", session.grade)
        assertEquals("proud_average", session.cardId)
        assertEquals(1, session.understandingLevel)
        assertEquals(0, session.currentRound)

        assertEquals("对日常生活现象充满好奇的大一学生", session.studentProfile.role)
        assertEquals("喜欢用生活里的例子提问", session.studentProfile.profile)
        assertEquals("性格活泼好奇，理解卡壳时会诚实说困惑", session.studentProfile.persona)
        assertEquals("先让他举例，再一起命名现象", session.studentProfile.dialogueStrategy)
        assertEquals("日常生活中的趣味心理学", session.studentProfile.goal)
    }

    @Test
    fun roundTripSnapshotPersistenceRestoresFullSessionState() {
        val original = com.reversetutor.feature.reversetutor.session.RTSession(
            sessionId = "session-persist",
            topic = "立体几何公理",
            studentId = "小华",
            grade = "高二",
            understandingLevel = 3,
            initialUnderstandingLevel = 1,
            currentRound = 5,
            masteredConcepts = listOf("点线面位置关系", "平行公理"),
            confusedConcepts = listOf("异面直线夹角"),
            historySummary = "第1到4轮：学生弄清了平面的判定定理。",
            studentProfile = com.reversetutor.feature.reversetutor.session.StudentProfile(
                role = "高二理科生",
                profile = "做题细心但缺乏空间想象力",
                persona = "爱追问反例",
                dialogueStrategy = "用实物比划辅助",
                goal = "搞懂立体几何"
            )
        )

        val json = original.toSnapshotPayload()
        val restored = json.toReverseTutorSessionFromSnapshot()

        assertEquals("session-persist", restored.sessionId)
        assertEquals("立体几何公理", restored.topic)
        assertEquals("小华", restored.studentId)
        assertEquals("高二", restored.grade)
        assertEquals(3, restored.understandingLevel)
        assertEquals(1, restored.initialUnderstandingLevel)
        assertEquals(5, restored.currentRound)
        assertEquals(listOf("点线面位置关系", "平行公理"), restored.masteredConcepts)
        assertEquals(listOf("异面直线夹角"), restored.confusedConcepts)
        assertEquals("第1到4轮：学生弄清了平面的判定定理。", restored.historySummary)
        assertEquals("高二理科生", restored.studentProfile.role)
        assertEquals("做题细心但缺乏空间想象力", restored.studentProfile.profile)
        assertEquals("爱追问反例", restored.studentProfile.persona)
    }

    @Test
    fun gradeFollowsTheRoleBeforeTheTitle() {
        // 标题含「高中」但身份是「大一」——身份优先，不能被标题带跑
        val snapshot = NewSessionConfiguration(
            title = "高中数学",
            learnerRole = "认真但爱死记硬背的大一学生"
        )

        assertEquals("大一", snapshot.toReverseTutorSession("s").grade)
    }

    @Test
    fun gradeFallsBackToTheMostConcreteTokenInTheTitle() {
        val snapshot = NewSessionConfiguration(title = "高三数学讲题冲刺")

        assertEquals("高三", snapshot.toReverseTutorSession("s").grade)
    }

    @Test
    fun unknownGradeStaysBlankInsteadOfBeingGuessed() {
        val snapshot = NewSessionConfiguration(
            title = "代数基础",
            learnerRole = "对数学有点好奇的学生"
        )

        // 旧实现写死「初二」；采集不到就留空，让课标工具不做学段判断（诚实优先）
        assertEquals("", snapshot.toReverseTutorSession("s").grade)
    }

    @Test
    fun presetLearnerKeepsItsOriginalGrade() {
        // 回归：旧反转家教预设（李想·初二）推导结果必须与接入前的写死值一致
        val preset = FormalLearningPresets.byId(FormalLearningPresets.REVERSE_TUTOR_PRESET_ID)!!
        val snapshot = NewSessionConfiguration.fromPreset(preset)

        val session = snapshot.toReverseTutorSession("s")

        assertEquals("初二", session.grade)
        assertEquals("李想", session.studentId)
        assertEquals(preset.title, session.topic)
        assertTrue("预设人设也应带进内核", session.studentProfile.role.contains("李想"))
    }

    @Test
    fun nullSnapshotFallsBackToBuiltInDefaults() {
        val session = (null as NewSessionConfiguration?).toReverseTutorSession("s")

        assertEquals("反转家教", session.topic)
        assertEquals("小理", session.studentId)
        assertEquals("", session.grade)
        assertTrue(session.studentProfile.isBlank)
    }

    @Test
    fun blankSnapshotFieldsDoNotLeakWhitespaceIntoThePrompt() {
        val snapshot = NewSessionConfiguration(
            title = "  配方法  ",
            learnerRole = "  初二学生  ",
            learnerDisplayName = "  "
        )

        val session = snapshot.toReverseTutorSession("s")

        assertEquals("配方法", session.topic)
        assertEquals("初二", session.grade)
        assertEquals("小理", session.studentId)
        assertEquals("初二学生", session.studentProfile.role)
    }
}
