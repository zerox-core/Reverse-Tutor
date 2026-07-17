package com.reversetutor.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormalBatch6ModelTest {
    @Test
    fun searchFixturesRepresentRecentAndResultStatesWithoutRuntimeSeeding() {
        val recent = FormalBatch6PreviewFixtures.searchRecent
        val results = FormalBatch6PreviewFixtures.searchResults

        assertFalse(recent.showingResults)
        assertTrue(recent.recentSearches.isNotEmpty())
        assertTrue(results.showingResults)
        assertEquals(results.resultCount, results.resultGroups.sumOf { it.count })
        assertEquals(
            listOf("会话", "消息", "知识节点", "资料", "学习计划"),
            results.resultGroups.map { it.title }
        )
    }

    @Test
    fun importPreviewKeepsSecretsOutOfBackupAndExposesAllModes() {
        val preview = FormalBatch6PreviewFixtures.importPreview

        assertTrue(preview.isValidated)
        assertTrue(preview.canImport)
        assertEquals(3, preview.issues.size)
        assertEquals(
            setOf(FormalImportMode.Append, FormalImportMode.Overwrite, FormalImportMode.NewSpace),
            FormalImportMode.values().toSet()
        )
        assertTrue(preview.issues.any { it.severity == FormalImportIssueSeverity.Secret })
    }

    @Test
    fun diagnosticsWipeConfirmationIsAnExplicitUiState() {
        val overview = FormalBatch6PreviewFixtures.diagnostics
        val confirmation = FormalBatch6PreviewFixtures.diagnosticsWipe

        assertFalse(overview.showWipeConfirmation)
        assertTrue(confirmation.showWipeConfirmation)
        assertEquals(overview.wipeSummary, confirmation.wipeSummary)
        assertEquals(overview.systemRows, confirmation.systemRows)
    }

    @Test
    fun tokenBreakdownsStayBoundedAndUseTheRequestedPeriod() {
        val overview = FormalBatch6PreviewFixtures.tokenOverview
        val byModel = FormalBatch6PreviewFixtures.tokenByModel
        val bySession = FormalBatch6PreviewFixtures.tokenBySession

        assertEquals(FormalTokenPeriod.Week, overview.selectedPeriod)
        assertEquals(FormalTokenPeriod.Week, byModel.selectedPeriod)
        assertEquals(FormalTokenPeriod.Week, bySession.selectedPeriod)
        assertTrue(overview.trend.all { it.fraction in 0f..1f })
        assertTrue(byModel.items.all { it.fraction in 0f..1f })
        assertTrue(bySession.items.all { it.fraction in 0f..1f })
    }

    @Test
    fun updateAndSyncModalStatesAreExplicitAndReversible() {
        val current = FormalBatch6PreviewFixtures.update
        val available = FormalBatch6PreviewFixtures.updateAvailable
        val choice = FormalBatch6PreviewFixtures.syncChoice

        assertTrue(current.availableUpdate == null)
        assertTrue(available.availableUpdate != null)
        assertEquals(
            setOf(FormalSyncSource.Device, FormalSyncSource.Cloud),
            choice.options.map { it.source }.toSet()
        )
        assertTrue(choice.options.any { it.source == choice.selectedSource })
    }

    @Test
    fun allSeventeenFormalFigmaStatesHaveDedicatedFixtureRepresentations() {
        val states = listOf(
            FormalBatch6PreviewFixtures.searchRecent,
            FormalBatch6PreviewFixtures.searchResults,
            FormalBatch6PreviewFixtures.article,
            FormalBatch6PreviewFixtures.articleContinuation,
            FormalBatch6PreviewFixtures.importExport,
            FormalBatch6PreviewFixtures.importPreview,
            FormalBatch6PreviewFixtures.sessionExport,
            FormalBatch6PreviewFixtures.diagnostics,
            FormalBatch6PreviewFixtures.diagnosticReport,
            FormalBatch6PreviewFixtures.diagnosticsWipe,
            FormalBatch6PreviewFixtures.tokenOverview,
            FormalBatch6PreviewFixtures.tokenByModel,
            FormalBatch6PreviewFixtures.tokenBySession,
            FormalBatch6PreviewFixtures.update,
            FormalBatch6PreviewFixtures.updateAvailable,
            FormalBatch6PreviewFixtures.syncConflict,
            FormalBatch6PreviewFixtures.syncChoice
        )

        assertEquals(17, states.size)
        assertEquals(17, states.distinctBy { it::class.qualifiedName + it.toString() }.size)
    }
}
