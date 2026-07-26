package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSettingsCoordinatorTest {
    @Test
    fun groupedIndexAndStrategyControlsMatchTheConfirmedContract() {
        assertEquals(
            listOf("基本资料", "学习目标与计划", "对话策略", "资料管理", "世界树配置", "危险操作"),
            SessionSettingsSection.entries.map(SessionSettingsSection::label)
        )
        assertEquals(
            listOf("反馈强度", "追问强度", "脚手架强度", "纠错坚持度", "复习频率", "说话语气"),
            ConversationStrategyControl.entries.map(ConversationStrategyControl::label)
        )
        assertEquals(
            listOf("重新选择文件", "取消本会话引用", "删除资料文件"),
            SourceDetailAction.entries.map(SourceDetailAction::label)
        )
        assertEquals(listOf("删除当前会话"), DangerousSessionAction.entries.map(DangerousSessionAction::label))
        assertEquals(5_000L, SessionSettingsUndoWindowMillis)
    }

    @Test
    fun chatScrollPositionRoundTripsThroughSettingsNavigation() {
        val memory = ChatScrollMemory()
        memory.capture("session-a", ChatScrollPosition(index = 18, offset = 37))

        assertEquals(ChatScrollPosition(18, 37), memory.restore("session-a"))
        assertEquals(ChatScrollPosition(), memory.restore("session-b"))
    }

    @Test
    fun textWaitsForBoundaryAndProtectedChangesRequireConfirmationEvenAfterBackground() {
        val store = InMemorySessionSettingsStore()
        val coordinator = coordinator(store)
        val appliedBefore = coordinator.state.applied

        coordinator.editProfile { it.copy(title = "概率论复习", learnerRole = "会追问证明的学生") }
        assertEquals(appliedBefore, coordinator.state.applied)

        coordinator.commitTextBoundary()
        assertEquals("概率论复习", coordinator.state.applied.profile.title)
        assertEquals("谨慎的初学者", coordinator.state.applied.profile.learnerRole)
        assertNotNull(coordinator.state.pendingConfirmation)
        assertEquals(
            listOf("学习者角色"),
            coordinator.state.pendingConfirmation!!.differences.map(ConfigurationDifference::field)
        )

        coordinator.onApplicationBackgrounded()
        val restored = coordinator(store)
        assertEquals("会追问证明的学生", restored.state.form.profile.learnerRole)
        assertNotNull(restored.state.pendingConfirmation)
        assertEquals("谨慎的初学者", restored.state.applied.profile.learnerRole)

        restored.confirmProtectedChanges()
        assertEquals("会追问证明的学生", restored.state.applied.profile.learnerRole)
    }

    @Test
    fun switchesSegmentsAndTagsPersistImmediatelyWithoutRewritingOpeningMessage() {
        val coordinator = coordinator(InMemorySessionSettingsStore())
        val originalOpening = coordinator.state.applied.snapshot.openingMessage

        coordinator.setAvatarVisible(false)
        coordinator.setSpeakingTone("温和")
        coordinator.toggleQuickTag("goal", TagSelectionValue(tagId = "tag-proof", text = "证明"))

        assertFalse(coordinator.state.applied.profile.avatarVisible)
        assertEquals("温和", coordinator.state.applied.strategy.speakingTone)
        assertEquals(listOf("证明"), coordinator.state.applied.quickTags.getValue("goal").values.map { it.text })
        assertEquals(originalOpening, coordinator.state.applied.snapshot.openingMessage)
    }

    @Test
    fun sessionSnapshotEditsAreDeepCopiedAndNeverMutateTemplatesDraftsOrFavorites() {
        val source = NewSessionConfiguration(
            title = "离散数学",
            learnerRole = "谨慎的初学者",
            goal = "掌握图论",
            customColumns = listOf(CustomColumn("column-1", "验收", "能讲清欧拉路"))
        )
        val template = source.deepCopy()
        val draft = source.deepCopy()
        val favorite = source.deepCopy()
        val coordinator = coordinator(InMemorySessionSettingsStore(), source)

        coordinator.updateWorldTree { current ->
            current.withCustomColumns(current.customColumns.map { it.copy(content = "能独立证明") })
                .copy(story = "当前会话独立路线")
        }

        assertEquals("当前会话独立路线", coordinator.state.applied.snapshot.story)
        assertEquals("能独立证明", coordinator.state.applied.snapshot.customColumns.single().content)
        assertEquals("", template.story)
        assertEquals("能讲清欧拉路", draft.customColumns.single().content)
        assertEquals("能讲清欧拉路", favorite.customColumns.single().content)
        assertNotEquals(template, coordinator.state.applied.snapshot)
    }

    @Test
    fun sourceListFiltersSearchesAndSortsByMostRecentUse() {
        val coordinator = coordinator(
            store = InMemorySessionSettingsStore(),
            sources = listOf(
                source("old", "旧讲义", SourceReadState.Ready, 10),
                source("new", "新讲义", SourceReadState.Processing, 30),
                source("bad", "缺失讲义", SourceReadState.Invalid, 20)
            )
        )

        assertEquals(listOf("new", "bad", "old"), coordinator.visibleSources().map(SessionSource::id))
        assertEquals(listOf("bad"), coordinator.visibleSources(SourceFilter.Invalid).map(SessionSource::id))
        assertEquals(listOf("new"), coordinator.visibleSources(query = "新讲义").map(SessionSource::id))
    }

    @Test
    fun unlinkIsImmediateAndUndoRestoresOnlyTheCurrentSessionReference() {
        var now = 1_000L
        val shared = source("shared", "共享教材", references = listOf("session-a", "session-b", "favorite-x"))
        val coordinator = coordinator(InMemorySessionSettingsStore(), sources = listOf(shared), now = { now })

        assertTrue(coordinator.unlinkCurrentSession("shared"))
        assertEquals(listOf("session-b", "favorite-x"), coordinator.source("shared")!!.referenceOwnerIds)
        assertTrue(coordinator.source("shared")!!.bytesRetained)
        assertEquals(SourceUndoKind.Unlink, coordinator.state.sourceUndo?.kind)

        now += 4_999
        assertTrue(coordinator.undoSourceAction())
        assertEquals(listOf("session-a", "session-b", "favorite-x"), coordinator.source("shared")!!.referenceOwnerIds)
    }

    @Test
    fun unavailableDeleteCapabilityNeverChangesBytesReferencesOrSourceState() {
        val shared = source("shared", "共享教材", references = listOf("session-a", "session-b", "favorite-x"))
        val coordinator = coordinator(InMemorySessionSettingsStore(), sources = listOf(shared))

        coordinator.requestDeleteSource("shared")
        assertEquals(listOf("session-a", "session-b", "favorite-x"), coordinator.state.pendingSourceDelete!!.impactedOwnerIds)
        assertFalse(coordinator.state.pendingSourceDelete!!.deleteEnabled)
        assertFalse(coordinator.confirmDeleteSource())

        coordinator.acknowledgeDeleteImpact(true)
        assertFalse(coordinator.confirmDeleteSource())
        assertTrue(coordinator.source("shared")!!.bytesRetained)
        assertEquals(SourceReadState.Ready, coordinator.source("shared")!!.readState)
        assertEquals(listOf("session-a", "session-b", "favorite-x"), coordinator.source("shared")!!.referenceOwnerIds)
        assertNull(coordinator.state.sourceUndo)
        assertEquals("当前版本暂不支持删除资料文件。", coordinator.state.errorMessage)
    }

    @Test
    fun validOrInvalidSourceReselectCreatesImmutableRevisionForOnlyTheEditedSession() {
        val original = source(
            id = "source-v1",
            name = "旧教材",
            readState = SourceReadState.Ready,
            references = listOf("session-a", "session-b", "favorite-x")
        )
        val coordinator = coordinator(InMemorySessionSettingsStore(), sources = listOf(original))
        val replacement = source("source-v2", "新教材", SourceReadState.Ready, references = emptyList())

        assertTrue(coordinator.replaceSourceRevision("source-v1", replacement))

        assertEquals(listOf("session-b", "favorite-x"), coordinator.source("source-v1")!!.referenceOwnerIds)
        assertEquals(listOf("session-a"), coordinator.source("source-v2")!!.referenceOwnerIds)
        assertEquals("source-v1", coordinator.source("source-v2")!!.previousRevisionId)
    }

    @Test
    fun worldTreeTextStaysTemporaryUntilBoundaryAndPublishesCompleteBehaviorSnapshot() {
        val published = mutableListOf<NewSessionConfiguration>()
        val coordinator = coordinator(
            store = InMemorySessionSettingsStore(),
            onSnapshotApplied = { published += it.deepCopy() }
        )

        coordinator.editWorldTree { it.copy(story = "temporary story", builtInPresetId = "preset-v2") }
        assertEquals("", coordinator.state.applied.snapshot.story)
        assertEquals("temporary story", coordinator.state.form.snapshot.story)

        coordinator.commitTextBoundary()

        assertEquals("temporary story", coordinator.state.applied.snapshot.story)
        assertEquals("preset-v2", published.last().builtInPresetId)
    }

    @Test
    fun refreshingSourceCatalogReplacesOwnersAndParserStateWithoutLosingSessionAlias() {
        val coordinator = coordinator(
            store = InMemorySessionSettingsStore(),
            sources = listOf(source("source-a", "图论讲义", references = listOf("session-a")))
        )
        assertTrue(coordinator.setSourceAlias("source-a", "我的图论"))

        coordinator.refreshSources(
            listOf(source("source-a", "图论讲义", SourceReadState.Invalid, references = listOf("session-b", "favorite-y")))
        )

        assertEquals("我的图论", coordinator.source("source-a")!!.displayName)
        assertEquals(listOf("session-b", "favorite-y"), coordinator.source("source-a")!!.referenceOwnerIds)
        assertEquals(SourceReadState.Invalid, coordinator.source("source-a")!!.readState)
    }

    @Test
    fun emptyCatalogClearsProjectionAndOpeningDetailUpdatesFeatureOwnedLastUsed() {
        var now = 100L
        val coordinator = coordinator(
            store = InMemorySessionSettingsStore(),
            sources = listOf(
                source("older", "旧讲义", lastUsed = 10),
                source("newer", "新讲义", lastUsed = 20)
            ),
            now = { now }
        )

        assertTrue(coordinator.markSourceUsed("older"))
        assertEquals(listOf("older", "newer"), coordinator.visibleSources().map(SessionSource::id))
        coordinator.refreshSources(
            listOf(
                source("older", "旧讲义", lastUsed = 10),
                source("newer", "新讲义", lastUsed = 20)
            )
        )
        assertEquals(100L, coordinator.source("older")!!.lastUsedAtEpochMillis)

        coordinator.refreshSources(emptyList())

        assertTrue(coordinator.state.sources.isEmpty())
        coordinator.refreshSources(listOf(source("older", "旧讲义", lastUsed = 10)))
        assertEquals(100L, coordinator.source("older")!!.lastUsedAtEpochMillis)
    }

    @Test
    fun selectingImageDuringProtectedConfirmationPreservesPendingDiffAndProtectedForm() {
        val coordinator = coordinator(InMemorySessionSettingsStore())
        coordinator.editProfile { it.copy(learnerRole = "待确认的新角色") }
        coordinator.commitTextBoundary()
        assertNotNull(coordinator.state.pendingConfirmation)

        coordinator.setLearnerImageRef("content://avatar/new")

        assertEquals("谨慎的初学者", coordinator.state.applied.profile.learnerRole)
        assertEquals("待确认的新角色", coordinator.state.form.profile.learnerRole)
        assertNotNull(coordinator.state.pendingConfirmation)
        assertEquals("content://avatar/new", coordinator.state.applied.snapshot.learnerImageRef)
        assertEquals("content://avatar/new", coordinator.state.form.snapshot.learnerImageRef)
    }

    @Test
    fun undoRejectsTheExactExpiryBoundary() {
        var now = 1_000L
        val coordinator = coordinator(InMemorySessionSettingsStore(), now = { now })
        assertTrue(coordinator.unlinkCurrentSession("source-a"))

        now = 6_000L

        assertFalse(coordinator.undoSourceAction())
        assertNull(coordinator.state.sourceUndo)
        assertFalse(coordinator.source("source-a")!!.currentSessionReferenced)
    }

    private fun coordinator(
        store: InMemorySessionSettingsStore,
        snapshot: NewSessionConfiguration = NewSessionConfiguration(
            title = "离散数学",
            learnerRole = "谨慎的初学者",
            learnerProfile = "喜欢例子",
            goal = "掌握图论",
            openingMessage = "这是一条已经发送的开场消息"
        ),
        sources: List<SessionSource> = listOf(source("source-a", "图论讲义")),
        now: () -> Long = { 1_000L },
        onSnapshotApplied: (NewSessionConfiguration) -> Unit = {}
    ): SessionSettingsCoordinator = SessionSettingsCoordinator(
        sessionId = "session-a",
        initial = SessionSettingsDocument.fromSnapshot(snapshot),
        initialSources = sources,
        store = store,
        nowEpochMillis = now,
        deleteCapability = SourceFileDeleteCapability.Unavailable,
        onSnapshotApplied = onSnapshotApplied
    )

    private fun source(
        id: String,
        name: String,
        readState: SourceReadState = SourceReadState.Ready,
        lastUsed: Long = 1,
        references: List<String> = listOf("session-a")
    ) = SessionSource(
        id = id,
        displayName = name,
        managedName = name,
        typeLabel = "PDF",
        readState = readState,
        currentSessionReferenced = "session-a" in references,
        referenceOwnerIds = references,
        lastUsedAtEpochMillis = lastUsed,
        preview = "内容预览",
        bytesRetained = true
    )
}
