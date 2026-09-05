# NEWMP-V1-003 后续计划：资料驱动回合与工具体验

当前阶段已完成资料检查候选的受限解析、本地版本校验、台账投影和重启后的 artifact 恢复。下一阶段继续保持“算法在后台、会话只显示自然聊天”的原则。

## 执行清单

- [ ] 资料热更新：导入或重处理资料后生成新的 sourceRevision；新回合使用新版本，已入队任务继续使用旧快照。
- [ ] 资料候选生成：只允许模型提出 checkPlan 候选；候选必须通过 source handle 白名单和 SourceGroundedCheckPolicy。
- [ ] 本地验证闭环：ExactText、NumericTolerance、RequiredConcepts 由本地验证；Rubric 和版本漂移保持 Unverified，不写学习台账。
- [ ] 重启与幂等：完成后的 assistant artifact 能恢复 checkPlan 和候选文本；重复 Worker 不重复写 assistant、工具回执或学习事实。
- [ ] 工具最小闭环：文档/表格工具继续使用白名单、参数限制、session 范围和幂等 receipt；聊天页只显示自然语言结果。
- [ ] 兼容设备验收：优先 Android 12/13 真机或 API≤34 AVD；API 36 只做 app 行为验证，不作为 Room 迁移证据。
- [ ] 统一回归：定向 JVM → core:data/app/llm 全量 → lint/assembleDebug → 兼容设备单次场景 → 清理测试包并恢复宿主前台。

## 通过标准

相同输入得到稳定教学动作；资料版本不会串线；模型自评不会直接产生学习事实；Provider 失败不泄漏诊断；重启和重试不重复写入；普通用户只看到学生式自然聊天。
