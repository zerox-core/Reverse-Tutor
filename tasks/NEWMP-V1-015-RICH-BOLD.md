# NEWMP-V1-015 聊天富文本渲染：加粗闭环（2026-09-11）

## 用户诉求（任务评论 7684182395825769746）

针对富文本输出：① AI 提问、用户打字回答时，文字里的格式内容要自动识别并转成富文本显示；② AI 输出也要富文本。语音识别转文字用户明确"后期单独做"，本期不做。

## 调查结论（先查清再动手）

生产链路 `AppShell → ChatRoute → ChatScreen → ReverseTeachingChatScreen` 中，老师（用户）与学生（AI）两侧的气泡**本来就走同一套** `RichMessageContent → ChatRichContentParser.parse`：标题 / 引用 / 列表 / 表格 / 代码块 / 公式 / 行内代码 / 链接，两侧消息同等生效。真正的缺口只有一个：**行内加粗 `**…**`**——V1-014 学生表达契约要求 AI 用 `**加粗**` 突出关键词，但渲染器不识别，会显示成裸星号。

## 改动（feature/chat，非冻结层，无需审批）

`feature/chat/src/main/java/com/reversetutor/feature/chat/ChatMessageActionContracts.kt`

- `ChatRichInline` 新增 `data class Bold(val source: String)`
- `parseInlines` 改为 `internal`（供列表/引用体与测试复用），正则追加第 5 组 `\*\*([^*\n]+)\*\*`
- when 分支兜底映射到 `Bold(groupValues[5])`

`feature/chat/src/main/java/com/reversetutor/feature/chat/ReverseTeachingChatScreen.kt`

- `buildRichInlineAnnotatedString` 新增 `Bold` 分支：`withStyle(SpanStyle(fontWeight = FontWeight.Bold))`，不再输出星号
- `ListItem` / `Quote` 气泡体从纯 `Text` 升级为 `RichInlineRow(parseInlines(block.text))`（列表、引用里也支持加粗/行内代码等）
- `RichInlineRow` 新增 `textColor: Color = ChatInk` 参数，引用体保持原灰蓝字色（0xFF4C586B）

## 刻意不做（记录在案，防返工误判）

- **单个 `*` 的斜体**：数学辅导场景 `3*4*5` 这类算式会大量误判，斜体一律不做；公式走 `$…$`。
- **语音识别转文字**：用户明确后期单独做。届时转写文本同样经 `RichMessageContent` 显示，自动获得本套富文本能力，无需重复开发。

## 验证（修必带验）

- 新增 4 测试（`ChatMessageActionsTask3BTest`）：段落识别 Bold、单个星号原样保留、加粗 span 无星号且 offset 精确、列表/引用体解析含 Bold
- 测试结果 XML：`tests="21" failures="0" errors="0" skipped="0"`（原 17 + 新 4）
- 全工程 `gradle test` BUILD SUCCESSFUL（465 tasks，exitCode 0）
- 生产文件回读确认：Bold 类型/正则/映射/渲染分支/列表引用富文本化均已落地
- 修复过程记录：`AnnotatedString.Range` 取样式字段是 `.item` 不是 `.style`（首个编译错，已改）

## 关联

- 上游：V1-014 输出表达层（`**加粗**` 关键词规矩来自那里）
- 对照矩阵：`tasks/native-companion-old-main-parity-matrix.md` 第 9 节
