# Reverse Tutor 契约先行三线并行设计

日期：2026-07-16  
状态：待用户书面复核  
任务代号：`RT-CONTRACT-FIRST-001`

## 1. 目标

以正式 Figma 文件 `VPV0vclAhALpF8QIGvxYlo`、总览页面 `714:10` 和原型起点 `716:13` 为交互事实源，采用“主窗口 + 前端、FastAPI 协议、数据库三个执行窗口”的方式并行开发。

本设计先冻结跨层契约，再按垂直业务切片并行实现，避免三个执行窗口各自推断接口。

## 2. 已冻结原则

### 2.1 本地优先

手机端是以下核心学习数据的唯一事实源：

- 会话与聊天；
- 世界树；
- 学习资料及资料正文；
- 全局与单会话图谱；
- 模型配置和模型密钥。

上述数据不进入普通在线同步。FastAPI 不默认拥有、控制或要求上传这些数据。

### 2.2 FastAPI 在线职责

FastAPI `/api/v1` 负责：

- 公益内容和公告；
- 挑战定义、参与、进度和排行榜；
- 应用更新元数据；
- 匿名设备账号授权；
- 明确授权的白名单同步；
- 可选周报增强。

社区在 V1 仍为静态占位，不新增帖子、评论、点赞、关注、发布或媒体上传 API。

### 2.3 同步白名单

普通同步只允许：

```text
activity_progress
study_plan
sync_summary
user_setting
```

服务端必须按实体类型和 payload 字段双重白名单校验，禁止使用任意 JSON 绕过边界上传会话、消息、世界树、资料正文、图谱、模型配置或秘密。

### 2.4 匿名设备账号

V1 不引入可见登录、注册或第三方账号绑定页面。首次使用受保护的在线能力时建立匿名设备账号。

- 公益内容和更新元数据允许公开读取；
- 挑战参与、同步和可选周报必须使用 Bearer Access Token；
- Refresh Token 必须轮换，服务端只保存带 pepper 的 keyed hash；
- Bearer subject 是账号身份真值；请求体中的 `userId` 不得作为授权依据；
- 客户端设备 ID 必须是随机不透明 ID，不得使用 IMEI、序列号或其他硬件标识。

## 3. 前端空间导航

### 3.1 页面拓扑

正式工作区采用二维空间关系：

```text
                    挑战页
                      ↑
本周学习副屏 ← 会话首页 ←→ 全局图谱 ←→ 社区静态页
```

横向页面的固定索引顺序为：

```text
0  本周学习副屏
1  会话首页（默认起点）
2  全局图谱
3  社区静态页
```

交互语义：

- 会话首页向右拖动，显示其左侧的本周学习副屏；
- 会话首页向左拖动，显示其右侧的全局图谱；
- 全局图谱继续向左拖动，显示社区静态页；
- 反向手势按原空间位置返回；
- 边界页继续向外拖动只提供克制的阻尼，不循环跳页；
- 通过其他入口进入这四页时，Pager 必须定位到对应索引并保持空间关系。

现有 `WorkspacePage` 顺序和 `HorizontalPager` 已符合该拓扑，应在其上完善，不另建一套平行导航。

### 3.2 横向动画

- 页面位移必须跟手，释放时根据位移与速度共同决定完成或回弹；
- 使用整页平移，不使用不符合空间关系的淡入淡出；
- 默认过渡使用平台 Pager 的弹簧/衰减物理，不写死 Figma 像素时长；
- 页面切换期间保留各页滚动位置、图谱选中节点和缓存内容；
- 系统“减少动态效果”开启时缩短位移动画，不移除可达性。

### 3.3 图谱手势仲裁

全局图谱内部需要平移、缩放、碰撞与惯性，不能让横向 Pager 抢占所有拖动。

- 双指缩放、节点拖动和图谱画布平移期间锁定横向翻页；
- 图谱空闲时允许从屏幕边缘发起页面切换；
- 从左边缘向右拖动返回首页，从右边缘向左拖动进入社区；
- 手势开始后按主轴锁定，横纵轴不得在一次手势中反复切换；
- 图谱仍必须使用“层级锚点约束 + 引力/斥力 + 碰撞 + 惯性”，不得退化为圆形力导向图。

### 3.4 挑战纵向交互

挑战页位于会话首页上方。现有“累计下拉超过阈值后立即跳转”必须升级为可视、跟手的拖拽过渡。

- 仅当首页纵向列表位于顶部时，下拉才可拉出挑战页；
- 拖动时首页随手指下移，挑战页从顶部同步进入，显示连续进度；
- 释放时根据位移与速度吸附到“首页”或“挑战”锚点；
- 未过阈值时回弹，不触发路由跳变；
- 挑战页内部滚动优先，只有处于允许退出的边界状态时才把手势交还空间导航；
- 返回键和现有返回热区必须继续可用，不能只依赖手势；
- 加入挑战后回到首页，首页挑战会话卡片由真实参与状态驱动。

### 3.5 页面指示

底部指示器反映横向工作区位置。挑战是纵向空间，不加入四点横向指示器。指示器只表达位置，不增加教学说明文字。

## 4. HTTP 接口预报

### 4.1 通用规则

- Base URL：`/api/v1`；
- JSON 字段使用 `camelCase`，枚举 wire value 使用 `snake_case`；
- ID 为不透明 UUID 字符串；
- HTTP 时间统一为 UTC `epochMillis`，PostgreSQL 内部使用带时区 UTC；
- 分页使用稳定游标；
- 写接口使用 `X-Request-Id`、`revision` 和 `idempotencyKey`；
- 非 2xx 使用统一 `ErrorEnvelope`；
- 客户端忽略未知可选字段，缺失 required 字段视为 `protocol_error`；
- API 不返回最终中文 UI 文案。

### 4.2 账号授权

```text
POST   /auth/anonymous
POST   /auth/refresh
GET    /auth/me
GET    /auth/sessions
DELETE /auth/sessions/{sessionId}
```

`POST /auth/anonymous` 使用 `deviceId + idempotencyKey` 建立或恢复匿名设备账号。`POST /auth/refresh` 执行 Refresh Token 轮换；重放已使用 token 时撤销 token family。

### 4.3 公益内容与公告

```text
GET /content/feed
GET /content/{slug}
```

Feed 支持 `cursor`、`limit`、`types`、`ETag` 和 `If-None-Match`。详情被撤下时返回 `410 content_offline`。正文只在详情接口返回。

### 4.4 挑战活动

```text
GET    /activities
GET    /activities/{activityId}
GET    /me/activity-participations
POST   /activities/{activityId}/join
POST   /activities/{activityId}/progress
DELETE /activities/{activityId}/participation
GET    /activities/{activityId}/leaderboard
```

活动定义是服务端事实。参与和进度写入必须鉴权、幂等并校验 revision。参与列表用于应用重启后恢复首页挑战会话卡片。

活动详情至少能够表达：活动状态、起止时间、规则版本、总天数、当前日任务、阶段目标、是否允许延迟提交、会话模板引用和公开反馈摘要。不得把本地聊天正文作为进度 payload 上传。

### 4.5 白名单同步与冲突

```text
POST /sync/push
POST /sync/pull
GET  /sync/conflicts
GET  /sync/conflicts/{conflictId}
POST /sync/conflicts/{conflictId}/resolve
```

`push` 按 envelope 返回单项结果，单项失败不得回滚整批。`pull` 使用服务端单调游标。文本冲突不得静默覆盖，进入冲突页由用户选择本地、远端或显式合并结果。

冲突解决请求必须携带冲突 revision 和 `idempotencyKey`；过期冲突返回新的 `revision_conflict`，不接受基于旧版本的覆盖。

### 4.6 可选周报

```text
POST /insights/weekly
```

客户端只提交经白名单约束的聚合统计和 `sourceRevision`，不提交消息正文、资料正文或世界树正文。服务不可用时，本周副屏继续展示本地统计，仅在线增强摘要降级。

### 4.7 更新与运行状态

```text
GET /app/releases/latest
GET /health
```

更新接口只返回版本、兼容性、校验和、发布说明和下载地址元数据。实际 APK 下载和安装仍走 Android 系统能力。`health` 不泄露数据库连接串、token 或内部堆栈。

### 4.8 明确不新增的移动端 API

以下能力继续由手机本地 Repository 提供：

- 全局搜索；
- Token 统计；
- 本地诊断报告；
- 会话、聊天、世界树、资料、图谱和模型配置；
- 社区实际内容。

## 5. PostgreSQL 数据设计

### 5.1 目标

PostgreSQL 是 `/api/v1` 在线业务的唯一事实源。SQLite 只作为旧数据迁移来源、本地开发或兼容测试目标，不参与生产双写。

Native Room 仍是核心学习数据的本地事实源，不迁移到 PostgreSQL。

### 5.2 Schema 演进

- 使用 SQLAlchemy 2.x 和 Alembic；
- 停止依赖运行时 `ensure_schema` 修改生产表；
- 服务启动只检查数据库是否位于 Alembic head，不自动迁移；
- 新业务主键优先 `Uuid(as_uuid=True)`；
- 时间字段使用 `DateTime(timezone=True)` 和 UTC aware datetime；
- 需要精确计算的值使用 `Numeric(p, s)` 与 `Decimal`；
- Repository 只使用 SQLAlchemy 跨方言表达；
- FTS5 和 PostgreSQL 全文搜索进入独立 adapter，索引视为可重建派生数据。

约束命名：

```text
pk_<table>
fk_<table>_<column>_<target>
uq_<table>_<columns>
ck_<table>_<rule>
ix_<table>_<columns>
```

`NOT NULL` 使用显式 `nullable=False`，默认值使用显式 `server_default`；Alembic revision 必须逐项审查。

### 5.3 在线业务表组

账号授权：

```text
anonymous_accounts
account_devices
auth_sessions
refresh_tokens
auth_audit_events
```

内容与更新：

```text
content_items
content_assets
app_releases
```

挑战：

```text
activities
activity_tasks
activity_participations
activity_progress_events
```

同步与冲突：

```text
sync_entities
sync_change_log
sync_device_cursors
sync_conflicts
idempotency_records
```

迁移审计：

```text
migration_runs
migration_validation_results
```

可选周报：

```text
weekly_insight_requests
weekly_insight_results
```

周报原始请求按最小保留原则和 TTL 清理。认证 token 原文、API Key、模型密钥及核心学习正文不得进入这些表。

旧服务端 SQLite 中的会话、消息、资料正文和图谱不因数据库迁移而自动成为新的在线业务数据。迁移程序只自动导入已确认属于账号授权、公益内容、挑战、更新和白名单同步范围的记录。遗留核心学习数据保留在带哈希的只读快照和受控导出路径中；任何后续云端导入都必须另立契约并取得用户明确授权。

## 6. SQLite 到 PostgreSQL 切换

### 6.1 冻结阶段

1. 服务进入维护或只读模式；
2. 停止后台任务、挑战提交和同步写入；
3. 备份 SQLite，并记录文件哈希；
4. Alembic 在空 PostgreSQL 上创建目标 schema；
5. 运行可重复、带 `migrationRunId` 的迁移程序；
6. 校验记录数、外键、唯一约束、UTC、JSON、业务关系、秘密排除、业务摘要和 sequence/identity。

### 6.2 验证阶段

应用连接 PostgreSQL，但不向普通用户开放写入。使用隔离的内部测试账号和可清理数据运行 API 冒烟测试、契约测试和核心业务检查。

只有在此阶段失败时，才允许安全回切只读 SQLite 快照。

### 6.3 正式开放

验证通过后开放 PostgreSQL 生产写入。此后默认向前修复，不得直接回切旧 SQLite。若必须回到 SQLite，必须先冻结 PostgreSQL 写入，将新增数据反向迁移并完成同级别校验。

## 7. 三线文件所有权

### 7.1 前端窗口

```text
mobile-native/app/src/main/java/com/reversetutor/preview/theme
mobile-native/app/src/main/java/com/reversetutor/preview/ui
mobile-native/app/src/main/java/com/reversetutor/preview/shell
mobile-native/feature/*
```

负责 Compose、导航、手势、动画、UiState、Fake 状态和前端测试。不得修改 Python、Alembic、SQLAlchemy model、Room DAO 或 frozen protocol。

### 7.2 FastAPI 协议窗口

```text
adapters/online/routes.py
adapters/online/models.py
adapters/online/service.py
服务端 auth/error/idempotency middleware
docs/contracts/openapi-online-v1.yaml
mobile-native/core/remote
相关协议测试
```

负责真实 HTTP 行为、OpenAPI、DTO、鉴权、错误、分页、幂等和 Android Remote adapter。不得自行修改数据库表定义或 Compose。

### 7.3 数据库窗口

```text
服务端 SQLAlchemy online models/repositories
alembic.ini
alembic/*
SQLite -> PostgreSQL migration tooling
PostgreSQL contract/migration tests
```

负责 PostgreSQL schema、事务、约束、迁移、校验和切换工具。不得把核心本地学习数据纳入服务端 schema。

### 7.4 主窗口

主窗口独占：

- 契约冻结与变更批准；
- `.adworkflow` 任务图和验收状态；
- 跨线集成文件；
- 最终 OpenAPI/数据库/Android 对接；
- 冲突解决、全量验证和完成判定。

任何执行窗口发现契约缺口，只提交变更申请，不直接修改其他窗口拥有的文件。

## 8. 垂直切片顺序

### Slice 0：契约与基础设施

- 冻结 OpenAPI、错误码、Mock 和 DTO；
- 建立 Alembic/PostgreSQL CI；
- 建立匿名账号、统一错误、请求追踪和幂等基础；
- 前端完成二维空间导航与挑战拖拽基础，不依赖在线数据。

### Slice 1：公益内容与更新

- Feed、详情、ETag/304、410；
- 首页公益轮播和文章详情；
- 更新元数据、校验和系统下载/安装入口。

### Slice 2：挑战

- 活动列表、详情、参与恢复、加入、进度、退出和排行榜；
- 首页挑战会话卡片；
- 纵向挑战空间动画和离线/待同步状态。

### Slice 3：同步与冲突

- 四类白名单 push/pull；
- Outbox、游标、tombstone 和冲突；
- 冲突总览、选择和过期冲突处理。

### Slice 4：可选周报

- 本地统计始终可用；
- 在线增强只接收聚合统计；
- 缓存、超时和降级。

## 9. 合并门槛

每个 Slice 合并前必须同时满足：

- Canonical OpenAPI 可解析且与 FastAPI 运行时 schema 无漂移；
- Android wire DTO 与 Mock/服务端契约测试通过；
- PostgreSQL 空库 `alembic upgrade head`、逐版本升级和 metadata drift 检查通过；
- 鉴权、幂等、revision、冲突和错误 envelope 测试通过；
- 前端 Fake、离线、加载、错误、恢复和手势测试通过；
- 跨层端到端测试通过；
- 核心学习数据和秘密没有进入 HTTP、PostgreSQL、日志或诊断正文。

前端额外验收：

- 首页初始索引为 1；
- 左右滑动顺序固定且边界不循环；
- 图谱交互期间不误翻页；
- 首页下拉挑战跟手、可取消、可吸附；
- 系统返回与点击入口仍然可达；
- Mate 60 参考尺寸及窄屏、长屏、IME、系统 Insets 下无重叠。

## 10. 非目标

- 不打包签名或 release APK；
- 不退出或删除 PWA/Capacitor；
- 不实现复盘模式、陪伴模式或 TTS；
- 不实现社区实际内容；
- 不把未设计的聊天省略号菜单、收藏或系统分享伪造成可用能力；
- 不把核心学习数据上传到 PostgreSQL；
- 不建立生产双写。

## 11. 变更控制

破坏性契约变化必须先由主窗口更新本设计、OpenAPI、Mock、数据库迁移说明和兼容策略，再分发三个执行窗口。任何单线实现不得先于契约生效。
