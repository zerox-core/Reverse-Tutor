# 移动端全局字体调节契约

## 1. 目标

为 Figma 定稿后的真机视觉验收保留统一排版调节入口。调整一次配置即可作用于全部页面，避免逐个组件修改字号，同时保留 Android 系统字体缩放与无障碍能力。

该能力是开发和验收接口，不是正式产品设置项。

## 2. 基准

- Figma 基准画板：`390 x 884`。
- Figma 排版缩放：`1.0`。
- 默认字体：`Noto Sans SC`。
- 字距固定为 `0`。
- 默认内部缩放：`globalScale = 1.0`、`lineHeightScale = 1.0`。

## 3. 前端接口

建议使用单一 CompositionLocal 或等价的主题注入点：

```kotlin
@Immutable
data class TypographyTuning(
    val globalScale: Float = 1.0f,
    val lineHeightScale: Float = 1.0f,
    val displayScale: Float = 1.0f,
    val titleScale: Float = 1.0f,
    val bodyScale: Float = 1.0f,
    val metaScale: Float = 1.0f,
)
```

语义字号由基础令牌、全局缩放和语义缩放共同计算：

```text
resolvedFontSize = baseFontSize * globalScale * semanticScale
resolvedLineHeight = baseLineHeight * globalScale * lineHeightScale * semanticScale
```

`semanticScale` 只允许对应 `display`、`title`、`body` 和 `meta` 四类，不新增页面级倍率。

## 4. 令牌边界

所有页面只能使用下列语义角色：

- `display`：页面级重点标题。
- `title`：导航标题、栏目标题、重要列表标题。
- `body`：正文、消息和主要说明。
- `meta`：时间、来源、状态和辅助信息。
- `action`：按钮与可执行菜单文字；默认跟随 `body`，可单独保持字重。

组件不得直接使用未经主题封装的字号常量。图标尺寸、触控区域、卡片边距和圆角不参与字体缩放。

## 5. Android 规则

- 继续使用 `sp`，让 Android 系统 `fontScale` 正常参与最终渲染。
- 不手动除以 `fontScale`，不锁死系统字号。
- 内部 `globalScale` 建议限制在 `0.92-1.12`，超出范围必须重新检查布局而不是继续放大倍率。
- 调试构建可通过内部开关、启动参数或开发菜单修改配置；正式设置页不显示该入口。
- 配置由应用主题根节点统一注入，切换后整棵 Compose UI 重组，不逐页保存倍率。

## 6. 验收

每次调节后检查：

- `360dp`、`390dp` 和约 `423dp` 宽度。
- 系统字体缩放 `1.0`、`1.15` 和更大字号。
- 中文长标题、英文模型名、数字与 Token 文本。
- 顶部导航、底部安全区、固定按钮、双列组件和搜索结果列表。
- 文本无裁切、无重叠，不推动固定图标或改变触控区域尺寸。

调整结果应记录为新的全局默认值，不在单独页面留下临时补丁。
