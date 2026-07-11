package com.reversetutor.preview.wiring

import android.content.Context
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.background.BackgroundGenerationRepository
import com.reversetutor.core.data.graph.GraphRepository
import com.reversetutor.core.data.learning.LearningRepositoryImpl
import com.reversetutor.core.data.llm.ChatGenerationRepository
import com.reversetutor.core.data.llm.LlmProfileRepository
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.migration.NativeExportRepository
import com.reversetutor.core.data.migration.NativeImportRepository
import com.reversetutor.core.data.model.ModelConnectionRepositoryImpl
import com.reversetutor.core.data.online.OnlineActivityRepository
import com.reversetutor.core.data.online.OnlineSyncTransport
import com.reversetutor.core.data.online.OnlineUpdateRepository
import com.reversetutor.core.data.preferences.AppPreferencesRepository
import com.reversetutor.core.data.run.ConversationRunRepositoryImpl
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.data.wipe.LocalDataWipeRepository
import com.reversetutor.core.domain.ConversationRunCoordinator
import com.reversetutor.core.domain.SyncCoordinator
import com.reversetutor.core.remote.HttpOnlineApi
import com.reversetutor.core.remote.OnlineAuthTokenProvider
import com.reversetutor.core.remote.UrlConnectionOnlineHttpTransport
import com.reversetutor.feature.chat.ChatRunsPortViewModelFactory
import com.reversetutor.feature.chat.ChatRunsViewModelFactory
import com.reversetutor.feature.chat.HomePortViewModelFactory
import com.reversetutor.feature.chat.HomeViewModelFactory
import com.reversetutor.feature.memory.WeeklyDashboardPortViewModelFactory
import com.reversetutor.feature.memory.WeeklyDashboardViewModelFactory
import com.reversetutor.feature.settings.ModelConnectionsPortViewModelFactory
import com.reversetutor.feature.settings.ModelConnectionsViewModelFactory
import com.reversetutor.preview.shell.DefaultWorkspaceViewModelFactory
import com.reversetutor.preview.shell.WorkspaceViewModelFactory

data class HybridFrontendFactories(
    val workspaceViewModelFactory: WorkspaceViewModelFactory,
    val homeViewModelFactory: HomeViewModelFactory,
    val chatRunsViewModelFactory: ChatRunsViewModelFactory,
    val modelConnectionsViewModelFactory: ModelConnectionsViewModelFactory,
    val weeklyDashboardViewModelFactory: WeeklyDashboardViewModelFactory
)

data class HybridOnlineConfiguration(
    val baseUrl: String,
    val authTokenProvider: OnlineAuthTokenProvider = OnlineAuthTokenProvider { null }
)

data class HybridOnlineServices(
    val activityRepository: OnlineActivityRepository,
    val syncCoordinator: SyncCoordinator,
    val updateRepository: OnlineUpdateRepository
)

class HybridAppGraph private constructor(
    val appPreferencesRepository: AppPreferencesRepository,
    val sessionRepository: SessionRepository,
    val messageRepository: MessageRepository,
    val llmProfileRepository: LlmProfileRepository,
    val chatGenerationRepository: ChatGenerationRepository,
    val backgroundGenerationRepository: BackgroundGenerationRepository,
    val sourceRepository: SourceRepository,
    val memoryRepository: MemoryRepository,
    val graphRepository: GraphRepository,
    val localDataWipeRepository: LocalDataWipeRepository,
    val nativeImportRepository: NativeImportRepository,
    val nativeExportRepository: NativeExportRepository,
    val online: HybridOnlineServices?,
    val frontend: HybridFrontendFactories
) {
    companion object {
        fun create(
            context: Context,
            onlineConfiguration: HybridOnlineConfiguration? = null
        ): HybridAppGraph {
            val appContext = context.applicationContext
            val sessionRepository = DataModule.sessionRepository(appContext)
            val conversationRunRepository = DataModule.conversationRunRepository(appContext)
            val modelConnectionRepository = DataModule.modelConnectionRepository(appContext)
            val learningRepository = DataModule.learningRepository(appContext)
            val syncRepository = DataModule.syncRepository(appContext)
            val runCoordinator = ConversationRunCoordinator(conversationRunRepository)
            val onlineServices = onlineConfiguration?.let { configuration ->
                val onlineApi = HttpOnlineApi(
                    baseUrl = configuration.baseUrl,
                    transport = UrlConnectionOnlineHttpTransport(),
                    authTokenProvider = configuration.authTokenProvider
                )
                HybridOnlineServices(
                    activityRepository = OnlineActivityRepository(onlineApi),
                    syncCoordinator = SyncCoordinator(
                        repository = syncRepository,
                        transport = OnlineSyncTransport(onlineApi)
                    ),
                    updateRepository = OnlineUpdateRepository(onlineApi)
                )
            }

            return HybridAppGraph(
                appPreferencesRepository = DataModule.appPreferencesRepository(appContext),
                sessionRepository = sessionRepository,
                messageRepository = DataModule.messageRepository(appContext),
                llmProfileRepository = DataModule.llmProfileRepository(appContext),
                chatGenerationRepository = DataModule.chatGenerationRepository(appContext),
                backgroundGenerationRepository =
                    DataModule.backgroundGenerationRepository(appContext),
                sourceRepository = DataModule.sourceRepository(appContext),
                memoryRepository = DataModule.memoryRepository(appContext),
                graphRepository = DataModule.graphRepository(appContext),
                localDataWipeRepository = DataModule.localDataWipeRepository(appContext),
                nativeImportRepository = DataModule.nativeImportRepository(appContext),
                nativeExportRepository = DataModule.nativeExportRepository(appContext),
                online = onlineServices,
                frontend = createFrontendFactories(
                    sessionRepository = sessionRepository,
                    conversationRunRepository = conversationRunRepository,
                    modelConnectionRepository = modelConnectionRepository,
                    learningRepository = learningRepository,
                    runCoordinator = runCoordinator
                )
            )
        }

        private fun createFrontendFactories(
            sessionRepository: SessionRepository,
            conversationRunRepository: ConversationRunRepositoryImpl,
            modelConnectionRepository: ModelConnectionRepositoryImpl,
            learningRepository: LearningRepositoryImpl,
            runCoordinator: ConversationRunCoordinator
        ): HybridFrontendFactories {
            val homePort = RepositoryHomePortAdapter {
                sessionRepository.listSessions()
            }
            val chatRunsPort = RepositoryChatRunsPortAdapter(
                listRuns = conversationRunRepository::listBySession,
                findRun = conversationRunRepository::findRun,
                saveRun = conversationRunRepository::saveRun,
                isWritableAttempt = conversationRunRepository::isWritableAttempt,
                runCoordinator = runCoordinator,
                setModelBinding = sessionRepository::setModelBinding
            )
            val modelConnectionsPort = RepositoryModelConnectionsPortAdapter(
                listConnections = {
                    modelConnectionRepository.listConnections(SessionRepository.defaultSpaceId)
                },
                listBindings = {
                    modelConnectionRepository.listBindingsBySpace(SessionRepository.defaultSpaceId)
                },
                setModelBinding = sessionRepository::setModelBinding
            )
            val weeklyDashboardPort = RepositoryWeeklyDashboardPortAdapter(
                listSummaries = {
                    learningRepository.listWeeklySummaries(SessionRepository.defaultSpaceId)
                },
                listTasks = {
                    learningRepository.listTasks(SessionRepository.defaultSpaceId)
                }
            )

            return HybridFrontendFactories(
                workspaceViewModelFactory = DefaultWorkspaceViewModelFactory,
                homeViewModelFactory = HomePortViewModelFactory(homePort),
                chatRunsViewModelFactory = ChatRunsPortViewModelFactory(chatRunsPort),
                modelConnectionsViewModelFactory =
                    ModelConnectionsPortViewModelFactory(modelConnectionsPort),
                weeklyDashboardViewModelFactory =
                    WeeklyDashboardPortViewModelFactory(weeklyDashboardPort)
            )
        }
    }
}
