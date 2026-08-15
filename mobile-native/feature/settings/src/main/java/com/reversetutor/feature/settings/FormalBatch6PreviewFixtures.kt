package com.reversetutor.feature.settings

import androidx.compose.ui.graphics.Color

object FormalBatch6PreviewFixtures {
    val searchRecent = FormalSearchUiState(
        indexStatus = "内容索引已更新 · 14:35",
        recentSearches = listOf("GDP 核算范围", "导数与变化率", "Python 参数作用域"),
        recentVisits = listOf(
            FormalSearchVisit("session-economy", "宏观经济学基础", "会话 · 停在 GDP 核算边界", "14:32", FormalSearchKind.Session),
            FormalSearchVisit("node-gdp", "GDP 核算范围", "知识节点 · 宏观经济学基础", "今天", FormalSearchKind.KnowledgeNode),
            FormalSearchVisit("material-notes", "宏观经济学课堂笔记.pdf", "资料 · 第 12–18 页", "昨天", FormalSearchKind.Material),
            FormalSearchVisit("plan-gdp", "复述 GDP 的三种核算方法", "学习计划 · 待完成", "周五", FormalSearchKind.StudyPlan)
        )
    )

    val searchResults = FormalSearchUiState(
        query = "GDP",
        resultCount = 12,
        resultGroups = listOf(
            FormalSearchResultGroup(
                "会话",
                2,
                listOf(
                    FormalSearchResult("session-economy", "宏观经济学基础", "最近消息包含 GDP · 14:32", FormalSearchKind.Session),
                    FormalSearchResult("session-policy", "GDP 与财政政策推演", "8 条相关消息 · 昨天", FormalSearchKind.Session)
                )
            ),
            FormalSearchResultGroup(
                "消息",
                4,
                listOf(
                    FormalSearchResult("message-1", "GDP 记录的是一个国家在一段时间内新产生的最终产品和服务价值。", "宏观经济学基础 · 小P · 14:30", FormalSearchKind.Message),
                    FormalSearchResult("message-2", "交易平台收取的服务费属于本期服务，因此应计入 GDP。", "宏观经济学基础 · 小P · 14:32", FormalSearchKind.Message)
                )
            ),
            FormalSearchResultGroup(
                "知识节点",
                3,
                listOf(
                    FormalSearchResult("node-range", "GDP 核算范围", "全局图谱 · 5 条引用", FormalSearchKind.KnowledgeNode),
                    FormalSearchResult("node-nominal", "名义 GDP", "当前会话图谱 · 2 条引用", FormalSearchKind.KnowledgeNode)
                )
            ),
            FormalSearchResultGroup(
                "资料",
                2,
                listOf(
                    FormalSearchResult("material-notes", "宏观经济学课堂笔记.pdf", "第 12–18 页出现 6 次", FormalSearchKind.Material),
                    FormalSearchResult("material-sheet", "GDP 计算示例.xlsx", "资料库 · 3 个工作表", FormalSearchKind.Material)
                )
            ),
            FormalSearchResultGroup(
                "学习计划",
                1,
                listOf(FormalSearchResult("plan-gdp", "复述 GDP 的三种核算方法", "今日计划 · 待完成", FormalSearchKind.StudyPlan))
            )
        )
    )

    val article = FormalPublicArticleUiState(
        title = "把放学后的两小时，\n留给安静阅读",
        lead = "一间不必很大的自习室，也能让山里的孩子在放学后有桌可用、有灯可读。",
        publisher = "灯塔教育公益计划",
        publishLabel = "2026年7月12日",
        readTimeLabel = "6分钟阅读",
        progressPercent = 54,
        bodyParagraphs = listOf(
            "傍晚六点，村里的天已经暗下来。过去，一些孩子只能趴在饭桌边写作业，电视声、做饭声混在一起，想把一道题读完并不容易。",
            "这间学习室由闲置教室改造，保留旧黑板，只添了护眼灯、书架和隔音窗帘。每天放学后开放两小时，由本地老师轮流照看。"
        ),
        continuationTitle = "这里没有额外的补课安排",
        continuationParagraphs = listOf("孩子可以写作业、读一本书，也可以在遇到难题时举手求助。对他们来说，规律而安静的时间，本身就是一种支持。"),
        quote = "门关上以后，\n能听见自己翻书的声音。",
        quoteAttribution = "小宇 · 学习室使用者",
        trialWeeks = "8周",
        participantCount = "28名",
        averageMinutes = "19人",
        relatedArticles = listOf(
            FormalRelatedArticle("article-spring", "旧课桌的新去处", "一间村校阅览室的改造记录"),
            FormalRelatedArticle("article-read", "放学以后，谁来陪孩子读书", "阅读陪伴项目的值班手记")
        )
    )
    val articleContinuation = article.copy(initialSection = FormalArticleInitialSection.Continuation)

    val importExport = FormalImportExportUiState(
        localSessionCount = 12,
        localMessageCount = 468,
        localMaterialCount = 7,
        localKnowledgeNodeCount = 86,
        recentRecords = listOf(
            FormalTransferRecord("export-all", "全部本地数据", "导出 · 2026-07-14 22:18 · 18.4 MB", FormalTransferStatus.Completed),
            FormalTransferRecord("export-session", "宏观经济学基础", "导出 · 2026-07-13 19:06 · 2.1 MB", FormalTransferStatus.Completed),
            FormalTransferRecord("import-backup", "backup-2026-07-11.rtbak", "导入 · 12 个会话 · 1 项警告", FormalTransferStatus.Warning)
        )
    )

    val importPreview = FormalImportPreviewUiState(
        fileName = "reverse-tutor-2026-07-15.rtbak",
        fileMeta = "18.4 MB · 2026-07-15 09:42 · 格式 v1",
        isValidated = true,
        sessionCount = 12,
        messageCount = 468,
        materialCount = 7,
        planCount = 3,
        issues = listOf(
            FormalImportIssue("duplicate", "2 个同名会话", "按所选导入方式处理，不覆盖原内容", FormalImportIssueSeverity.Warning),
            FormalImportIssue("attachment", "1 个附件格式不兼容", "导入时跳过该附件，其余资料保持可用", FormalImportIssueSeverity.Blocking),
            FormalImportIssue("secret", "模型连接密钥需重新填写", "敏感信息不会写入备份文件", FormalImportIssueSeverity.Secret)
        ),
        selectedMode = FormalImportMode.Append,
        targetSpaceLabel = "默认空间",
        estimatedSizeLabel = "18.4 MB",
        summaryLabel = "预计新增 12 个会话 · 不覆盖现有数据"
    )

    val sessionExport = FormalSessionExportUiState(
        searchQuery = "",
        totalCount = 12,
        selectedCount = 2,
        selectedSizeLabel = "3.2 MB",
        sessions = listOf(
            FormalExportSessionItem("economy", "宏观经济学基础", "最近学习 · 18.6 MB", true, Color(0xFF5B92D0)),
            FormalExportSessionItem("python", "Python 学习路径", "昨天 · 6.2 MB", true, Color(0xFF5B92D0)),
            FormalExportSessionItem("math", "高中数学 · 函数", "7 月 12 日 · 4.8 MB", false, Color(0xFFC68B31)),
            FormalExportSessionItem("linear", "大学数学 · 线性代数", "7 月 10 日 · 9.4 MB", false, Color(0xFF43A17D)),
            FormalExportSessionItem("ielts", "英语表达训练", "7 月 08 日 · 3.1 MB", false, Color(0xFF8298B4))
        )
    )

    val diagnostics = FormalDiagnosticsUiState(
        appVersionLabel = "0.18.0 (180) · Huawei Mate 60",
        deviceLabel = "Huawei Mate 60 · Android 14",
        currentSpaceLabel = "默认空间",
        localUsageLabel = "1.28 GB",
        modelLabel = "DeepSeek V3",
        modelStatusLabel = "已连接",
        systemRows = listOf(
            FormalDiagnosticStatusRow("database", "数据库与迁移", "Room v4 · 最近迁移成功", "正常", FormalDiagnosticTone.Success, FormalDiagnosticIcon.Database),
            FormalDiagnosticStatusRow("parser", "资料解析", "PDF · Word · Markdown", "可用", FormalDiagnosticTone.Info, FormalDiagnosticIcon.Parser),
            FormalDiagnosticStatusRow("model", "默认模型", "DeepSeek V3 · 上次测试 1.4s", "已连接", FormalDiagnosticTone.Success, FormalDiagnosticIcon.Model),
            FormalDiagnosticStatusRow("error", "最近模型错误", "连接超时 · 2 小时前", "1 次", FormalDiagnosticTone.Warning, FormalDiagnosticIcon.Warning)
        ),
        storageUsedLabel = "1.28 GB",
        storageSegments = listOf(
            FormalStorageSegment("会话", "486 MB", .38f, Color(0xFF508CC7)),
            FormalStorageSegment("资料", "694 MB", .54f, Color(0xFF42A17D)),
            FormalStorageSegment("缓存", "102 MB", .08f, Color(0xFFD29A39))
        ),
        wipeSummary = "将永久删除 12 个会话、468 条消息、7 份资料和全部知识图谱。模型连接密钥与本地缓存也会一并移除。"
    )
    val diagnosticsWipe = diagnostics.copy(showWipeConfirmation = true)

    val diagnosticReport = FormalDiagnosticReportUiState(
        reportId = "RT-0715-1728",
        createdAtLabel = "2026-07-15 17:28",
        appVersionLabel = "0.18.0 (180)",
        deviceLabel = "Huawei Mate 60 · Android 14",
        spaceLabel = "默认空间",
        databaseLabel = "Room v4 · 迁移正常",
        localDataLabel = "12 会话 · 468 消息 · 7 资料",
        parserLabel = "PDF · Word · Markdown 可用",
        recentEvents = listOf(
            FormalDiagnosticEvent("timeout", "模型连接超时", "2 小时前 · 自动重试后恢复", "已恢复", true),
            FormalDiagnosticEvent("migration", "数据库迁移 v3 → v4", "2026-07-14 22:08", "成功", false)
        )
    )

    private val tokenSummary = FormalTokenSummary(
        totalLabel = "1.24M tokens",
        comparisonLabel = "较上周 +18%",
        inputLabel = "312K",
        outputLabel = "428K",
        cacheLabel = "96K",
        reasoningLabel = "404K"
    )

    val tokenOverview = FormalTokenOverviewUiState(
        selectedPeriod = FormalTokenPeriod.Week,
        summary = tokenSummary,
        peakLabel = "214K",
        averageLabel = "177K",
        trend = listOf(
            FormalTokenTrendPoint("7/09", "92K", .38f),
            FormalTokenTrendPoint("7/10", "146K", .62f),
            FormalTokenTrendPoint("7/11", "117K", .48f),
            FormalTokenTrendPoint("7/12", "184K", .76f),
            FormalTokenTrendPoint("7/13", "158K", .67f),
            FormalTokenTrendPoint("7/14", "214K", .92f),
            FormalTokenTrendPoint("今", "176K", .72f)
        ),
        modelSummary = "DeepSeek V3 42% · Qwen3 28%",
        sessionSummary = "宏观经济学基础 31% · Python 学习 18%"
    )

    val tokenByModel = FormalTokenByModelUiState(
        selectedPeriod = FormalTokenPeriod.Week,
        summary = tokenSummary,
        leadingModelLabel = "DeepSeek V3 占 42%",
        items = listOf(
            FormalTokenModelItem("deepseek", "DeepSeek V3", "521K", "42%", "输入 131K · 输出 189K · 缓存 64K · 推理 137K", .42f, Color(0xFF4F83C5)),
            FormalTokenModelItem("qwen", "Qwen3", "347K", "28%", "输入 86K · 输出 121K · 缓存 28K · 推理 112K", .28f, Color(0xFF7379B8)),
            FormalTokenModelItem("glm", "GLM-4.5", "223K", "18%", "输入 54K · 输出 75K · 缓存 2K · 推理 92K", .18f, Color(0xFF3D9B7B)),
            FormalTokenModelItem("kimi", "Kimi K2", "149K", "12%", "输入 41K · 输出 43K · 缓存 2K · 推理 63K", .12f, Color(0xFFC0842C))
        )
    )

    val tokenBySession = FormalTokenBySessionUiState(
        selectedPeriod = FormalTokenPeriod.Week,
        summary = tokenSummary,
        activeSessionCountLabel = "12 个会话有用量记录",
        searchQuery = "",
        items = listOf(
            FormalTokenSessionItem("economy", "宏观经济学基础", "DeepSeek V3", "384K", "31%", "输入 92K · 输出 138K", .31f, Color(0xFF4F83C5)),
            FormalTokenSessionItem("python", "Python 学习路径", "Qwen3", "223K", "18%", "输入 54K · 输出 77K", .18f, Color(0xFF7379B8)),
            FormalTokenSessionItem("math", "高中数学 · 函数", "GLM-4.5", "186K", "15%", "输入 43K · 输出 61K", .15f, Color(0xFFC0842C)),
            FormalTokenSessionItem("linear", "大学数学 · 线性代数", "DeepSeek V3", "161K", "13%", "输入 39K · 输出 58K", .13f, Color(0xFF3D9B7B)),
            FormalTokenSessionItem("ielts", "英语表达训练", "Kimi K2", "124K", "10%", "输入 31K · 输出 34K", .10f, Color(0xFF8298B4))
        )
    )

    val update = FormalUpdateUiState(
        currentVersionLabel = "0.18.0 (180)",
        channelLabel = "正式渠道",
        lastCheckedLabel = "2 分钟前检查",
        isLatest = true,
        automaticUpdatesEnabled = true,
        wifiOnlyEnabled = true,
        currentReleaseSummary = "稳定性与资料解析优化",
        archivedVersionLabel = "无"
    )
    val updateAvailable = update.copy(
        availableUpdate = FormalAvailableUpdate(
            versionLabel = "0.19.0",
            sizeLabel = "24.8 MB",
            changes = listOf("图谱与资料同步更稳定", "会话选择与导出体验优化", "修复若干已知问题")
        )
    )

    val syncConflict = FormalSyncConflictUiState(
        conflictCount = 2,
        detectedAtLabel = "今天 10:44",
        conflictItems = listOf(
            FormalSyncConflictItem("python", "Python 学习", "世界树设置", "目标 · 周期 · 回复策略", "此设备 10:42 · MatePad 10:31", FormalSyncConflictIcon.Tree),
            FormalSyncConflictItem("math", "高等数学", "学习计划", "章节顺序 · 每周时长", "此设备 昨天 21:18 · 云端 昨天 21:07", FormalSyncConflictIcon.Calendar)
        ),
        autoMergedCount = 64,
        autoMergedMaterialCount = 3,
        localDeviceLabel = "Huawei Mate 60",
        cloudSpaceLabel = "云端空间",
        cloudStatusLabel = "已连接 · 等待确认"
    )

    val syncChoice = FormalSyncChoiceUiState(
        title = "Python 学习",
        stepLabel = "第 1 项（共 2 项）",
        progressFraction = .5f,
        differenceCount = 3,
        differenceSummary = "目标、周期与回复策略不同",
        selectedSource = FormalSyncSource.Device,
        options = listOf(
            FormalSyncChoiceOption(
                FormalSyncSource.Device,
                "此设备内容",
                "Huawei Mate 60 · 今天 10:42",
                listOf(
                    FormalSyncChoiceValue("学习目标", "掌握异步编程"),
                    FormalSyncChoiceValue("计划周期", "8 周"),
                    FormalSyncChoiceValue("回复策略", "先追问，再给提示")
                )
            ),
            FormalSyncChoiceOption(
                FormalSyncSource.Cloud,
                "云端内容",
                "MatePad 11 · 今天 10:31",
                listOf(
                    FormalSyncChoiceValue("学习目标", "完成爬虫项目"),
                    FormalSyncChoiceValue("计划周期", "6 周"),
                    FormalSyncChoiceValue("回复策略", "先示例，再追问")
                )
            )
        )
    )
}
