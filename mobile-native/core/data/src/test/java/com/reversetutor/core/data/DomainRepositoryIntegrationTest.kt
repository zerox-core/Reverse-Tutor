package com.reversetutor.core.data

import com.reversetutor.core.data.learning.LearningRepositoryImpl
import com.reversetutor.core.data.model.ModelConnectionRepositoryImpl
import com.reversetutor.core.data.run.ConversationRunRepositoryImpl
import com.reversetutor.core.data.search.RoomGlobalSearchRepository
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.data.sync.RoomSyncRepository
import com.reversetutor.core.domain.ConversationRunRepository
import com.reversetutor.core.domain.GlobalSearchRepository
import com.reversetutor.core.domain.LearningInsightRepository
import com.reversetutor.core.domain.ModelConnectionRepository
import com.reversetutor.core.domain.StudyPlanRepository
import com.reversetutor.core.domain.SyncRepository
import com.reversetutor.core.domain.TokenUsageRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainRepositoryIntegrationTest {
    @Test
    fun dataRepositoriesAreSubstitutableForSuspendDomainContracts() {
        assertAssignable<com.reversetutor.core.domain.SessionRepository, SessionRepository>()
        assertAssignable<ConversationRunRepository, ConversationRunRepositoryImpl>()
        assertAssignable<ModelConnectionRepository, ModelConnectionRepositoryImpl>()
        assertAssignable<StudyPlanRepository, LearningRepositoryImpl>()
        assertAssignable<LearningInsightRepository, LearningRepositoryImpl>()
        assertAssignable<TokenUsageRepository, LearningRepositoryImpl>()
        assertAssignable<GlobalSearchRepository, RoomGlobalSearchRepository>()
        assertAssignable<SyncRepository, RoomSyncRepository>()
    }

    private inline fun <reified Contract : Any, reified Implementation : Any> assertAssignable() {
        assertTrue(
            "${Implementation::class.java.name} must implement ${Contract::class.java.name}",
            Contract::class.java.isAssignableFrom(Implementation::class.java)
        )
    }
}
