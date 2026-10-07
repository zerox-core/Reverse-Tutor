# 会话分支宿主与临时管理页设计

## 目标

在原生 Android 的会话页面提供可用但可整体替换的分支管理宿主。页面必须通过稳定 Port 调用真实会话、拓扑和 heartbeat 能力；它不能把 Room、DAO、Entity、Worker 或 Provider 暴露给 UI。

## 范围

本轮交付一个从当前聊天页进入的临时“分支管理”页，包含：创建子分支、显示当前窗口身份与直接子分支、返回直接父分支、显式开启或关闭当前分支 heartbeat、删除当前子分支，以及合并状态展示。

本轮不做最终视觉风格、不创建独立分支树画布、不修改 `core:model`、`core:protocol`、`core:llm`、`core:data`、Room、迁移、Worker 或 SecretStore。后续前端可以替换页面布局、色彩、组件和导航表现，但必须继续消费本设计定义的 feature Port 与 UI 状态。

## 用户行为

### 创建分支

用户从聊天页进入分支管理并选择“创建分支”。系统创建一个新的子会话：

- 复制父会话的可见配置；
- 记录父窗口、fork 时刻和不可变 fork 快照；
- 子会话使用独立 ID；
- 创建后直接进入子会话；
- 子会话在消息读取中可见 fork 时刻以前的父历史与自己的本地历史；父会话之后的新消息不进入子会话。

页面不可通过复制父消息实体实现继承，避免重复消息、跨分支写入和后续删除误伤。消息继承是只读投影；子分支的新增消息只写入子会话。

### 分支管理

页面显示：当前窗口的标题、根会话标识、父窗口入口、窗口类型、heartbeat 状态，以及当前窗口的直接子分支列表。父分支入口只在子分支出现；直接子分支点击后进入相应会话。

### Heartbeat

根窗口按现有策略默认启用；子窗口默认关闭。页面只对当前窗口发出显式 enable/disable 命令并显示持久化后的状态，不能自行启动计时器、调用 Provider 或创建 assistant 消息。

### 删除

删除仅允许当前子分支。页面必须给出确认；确认后调用已有分支删除协调器，清理该分支局部会话、delta、局部记忆、heartbeat 与待执行任务，然后返回直接父会话。删除不得触碰父级已合并回执、全局学习台账或兄弟分支。

### 合并

页面保留“合并到父分支”的状态区和未来 Port，但在没有真实持久化 branch delta 时显示“暂无可归并的结构化记忆”且禁用确认。不得生成空 delta、不得把普通聊天文本伪造成可合并记忆、不得用 UI 成功提示掩盖未发生的合并。

## 结构

```text
ChatRoute
  -> WindowBranchHost（feature:chat）
       -> WindowBranchUiState / WindowBranchAction
       -> WindowBranchPort
            -> DefaultWindowBranchPort（app/wiring）
                 -> session/topology/heartbeat/delete capability seams
```

`feature:chat` 只持有可渲染状态和用户意图。`app/wiring` 持有创建会话、读取窗口、读取历史、设置 heartbeat、删除分支、导航所需的编排逻辑。数据层仅继续通过已发布 Repository 返回领域模型；UI 不获得数据库对象。

## 错误与安全

- 父窗口不存在、不是同一 space、不是直接父关系或 fork 快照无效：返回安全业务失败状态，不导航；
- 子分支创建失败：保留原会话并展示通用失败提示；
- 子分支删除失败：保留当前页面，不执行部分成功导航；
- heartbeat 状态不可读或命令失败：显示“状态暂不可用”，不得乐观写成已开启；
- 所有页面状态和提示不得包含 Provider、URL、Authorization、密钥、Room 错误或原始消息全文。

## 验收

1. 从聊天页可进入分支管理，当前根/子窗口身份正确。
2. 创建子分支后进入新会话；父子窗口关系、fork 快照与 heartbeat 默认值正确。
3. 子分支只读取 fork 前父历史及自身消息；父后续消息与兄弟消息均不可见。
4. 子分支可返回父级；直接子分支可从列表进入。
5. 子分支 heartbeat 默认关闭，显式开启后只影响自身。
6. 删除子分支需确认，完成后回到父级且不影响父、兄弟与全局学习台账。
7. 无 delta 时合并操作不可执行且说明真实原因。
8. UI 模块不直接依赖 DAO、Entity、Room、Repository、Worker、Provider 或 SecretStore。
9. 相关 JVM 测试、`git diff --check` 和冻结路径检查通过；真机运行只验证 UI/导航，不替代迁移或 Worker 的既有设备证据。
