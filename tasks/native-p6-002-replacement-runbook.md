# NATIVE-P6-002 · 原生替换包 Runbook

> 创建时间：2026-08-17（周一）
> 执行人：本地开发搭档（feishu_mcp）
> 分支：Android | 基线提交：b6c3307 | 冻结层：零修改
> 参考：`tasks/native-first-launch-import-prompt-runbook.md`、`tasks/native-p6-001-legacy-audit.md`、`tasks/native-legacy-entry-inventory.md`
> **声明：本 Runbook 完成不等于 PWA/Capacitor 退出或替换就绪。**

---

## 1. 构建命令

所有命令在 PowerShell 中执行，工作目录为 `F:\xw\reverse-tutor\mobile-native`。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain --no-daemon
```

完整验证构建（含测试和 Lint）：

```powershell
.\gradlew.bat :app:test :app:lint :app:assembleDebug :app:assembleDebugAndroidTest --console=plain --no-daemon
```

Python 后端回归（工作目录 `F:\xw\reverse-tutor`）：

```powershell
py -m pytest -q --ignore=tests/test_project_homepage.py
```

---

## 2. APK 路径、applicationId、版本与构建元数据

| 属性 | 值 |
|---|---|
| applicationId | `com.reversetutor.preview`（预览包；正式替换包须经 Phase 6 批准后切换为 `com.reversetutor.app`） |
| Debug APK 路径 | `mobile-native/app/build/outputs/apk/debug/app-debug.apk` |
| AndroidTest APK 路径 | `mobile-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| 签名密钥 | `mobile/android/app/release.jks`（alias `reverse-tutor`）— 不得修改 |
| 版本号 | 见 `app/build.gradle.kts` 的 `versionCode` / `versionName` |
| 构建时间 | 构建完成时记录（Gradle 输出含时间戳） |
| Git commit | `git log -1 --oneline` 获取当前 HEAD |

构建完成后记录 APK SHA-256：

```powershell
Get-FileHash mobile-native\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
Get-FileHash mobile-native\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk -Algorithm SHA256
```

---

## 3. 安装前备份

在安装新版本前，必须备份当前数据。

### 3.1 导出当前会话备份

1. 在当前版本（PWA/Capacitor 或旧原生版本）中进入设置 → 导入/导出。
2. 选择"导出全量备份"。
3. 保存导出 JSON 文件到安全位置（如 `F:\backups\reverse-tutor\export-YYYYMMDD-HHmmss.json`）。
4. 记录文件 SHA-256：

```powershell
Get-FileHash F:\backups\reverse-tutor\export-YYYYMMDD-HHmmss.json -Algorithm SHA256
```

### 3.2 备份记录

| 备份项 | 文件名 | SHA-256 | 保存位置 | 时间 |
|---|---|---|---|---|
| 全量导出 JSON | `export-YYYYMMDD-HHmmss.json` | （记录哈希） | 安全目录 | （记录时间） |

---

## 4. 两设备安装流程

### 4.1 设备范围

仅允许以下设备：

1. `emulator-5554`（Pixel_8_Pro AVD）— 稳定自动化设备
2. 主力真机 — 由用户提供 ADB serial 后执行：

```powershell
$MainDeviceSerial = "用户提供的主力真机 serial"
```

其他设备（旧 Android、小屏机、备用机、平板）统一记为 `not_available`，不得冒充通过。

### 4.2 模拟器安装

```powershell
# 确认模拟器在线
adb -s emulator-5554 devices

# 安装 Debug APK
adb -s emulator-5554 install -r mobile-native\app\build\outputs\apk\debug\app-debug.apk

# 安装 AndroidTest APK（设备测试用）
adb -s emulator-5554 install -r mobile-native\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

### 4.3 主力真机安装

仅当 `adb devices` 显示主力真机状态为 `device` 时执行：

```powershell
$MainDeviceSerial = "用户提供的主力真机 serial"

adb -s $MainDeviceSerial install -r mobile-native\app\build\outputs\apk\debug\app-debug.apk
adb -s $MainDeviceSerial install -r mobile-native\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

真机未连接时，安装步骤标记为 `not_run`，不得标记完成。

---

## 5. 首次启动流程

### 5.1 权限

首次启动时，应用可能请求以下权限：
- 通知权限（Android 13+）— 后台生成完成通知需要
- 存储访问（文档选择器）— 来源导入需要

权限请求按系统标准流程处理，不强制授予。

### 5.2 导入提示

首次启动导入提示**在预览构建中默认禁用**。仅在 Phase 6 批准后的正式替换构建中启用。

启用条件（须全部满足）：
- 官方包名/签名 Runbook 已批准
- P0 遗留 parity 行已验证或明确豁免
- 迁移指南已发布
- 设备矩阵覆盖导入、导出、擦除和首次启动提示流程
- 用户明确批准 PWA/Capacitor 退出或替换

### 5.3 导入流程

提示出现时，用户可选：

1. **跳过** — 不导入，直接进入应用主界面。提示在本次启动隐藏。
2. **导入** — 跳转到原生导入/导出界面：
   - 选择导出 JSON 文件
   - **dry-run 预览**：显示可导入、跳过、冲突、错误和预计写入数量
   - 选择导入模式：append（追加）、overwrite（覆盖）、new-space（新空间）
   - **确认** — 二次确认后执行导入
   - **结果页** — 显示导入结果摘要（成功/跳过/失败数量）

### 5.4 关键规则

- API key **不从旧导出迁移**，必须在 LLM Profiles 中重新输入
- 导入提示文案必须明确说明 API key 不迁移
- 不使用直接 IndexedDB 抓取作为主要迁移路径
- 支持路径：从现有版本导出 JSON → 在原生 Android 中导入 JSON

---

## 6. 导入失败回滚

### 6.1 原则

- **保留原始 JSON** — 导入过程不修改源文件
- **不覆盖原数据** — append 模式不修改已有数据；overwrite 模式在执行前已有备份
- **记录错误摘要** — 导入失败时记录错误类型、受影响条目和恢复动作

### 6.2 回滚步骤

1. 导入失败时，应用显示错误页，包含错误摘要和恢复建议
2. 检查原始 JSON 是否完好（对比 SHA-256）
3. 如果 overwrite 模式导致部分数据被覆盖：
   - 使用步骤 3.1 的全量备份恢复
   - 在设置 → 导入/导出中选择"导入全量备份"
   - 使用 overwrite 模式恢复
4. 如果 append 模式失败：
   - 原数据未受影响，可直接重试
   - 幂等规则保证重复 source/message/session 不产生不可控重复
5. 如果 new-space 模式失败：
   - 新空间未创建或部分创建，不影响原空间数据
   - 可删除失败的新空间后重试

### 6.3 Partial Import 报告

导入部分成功时，结果页显示：
- 成功导入条目数
- 跳过条目数及原因
- 失败条目数及原因
- 建议的后续操作

---

## 7. 升级失败回滚

### 7.1 原则

- **保留旧 APK** — 升级前保存当前可用版本 APK
- **停止继续安装** — 升级失败后不继续后续步骤
- **恢复备份** — 使用全量备份恢复数据
- **重新验证** — 恢复后验证会话列表、消息时间线、来源、记忆、图谱和设置

### 7.2 回滚步骤

1. 卸载失败版本：

```powershell
adb -s emulator-5554 uninstall com.reversetutor.preview
# 或主力真机：
adb -s $MainDeviceSerial uninstall com.reversetutor.preview
```

2. 安装旧版本 APK：

```powershell
adb -s emulator-5554 install F:\backups\reverse-tutor\old-app-debug.apk
```

3. 启动应用，进入设置 → 导入/导出
4. 导入全量备份（使用 overwrite 模式恢复）
5. 验证顺序：
   - 会话列表完整
   - 消息时间线正确
   - 来源记录存在
   - 记忆条目存在
   - 图谱节点存在
   - 设置项正确

---

## 8. 密钥规则

- **API key 不从旧导出迁移** — 导入 JSON 中的 secret/key 字段在 `ProtocolImportReader` 中被拒绝
- **不写入日志** — API key 不出现在 Logcat、诊断页、错误日志或崩溃报告中
- **不写入截图** — 截图前确认不含密钥相关 UI
- **不写入诊断文本** — `FormalDiagnosticsScreens` 的剪贴板脱敏策略确保密钥不出现在诊断文本中
- **不写入提交内容** — Git 提交中不含 API key、secret 或凭据
- SecretStore 使用 Android Keystore 加密存储，alias 和加密策略为冻结层，不得修改

---

## 9. 签名铁律

| 属性 | 值 | 规则 |
|---|---|---|
| 密钥库 | `mobile/android/app/release.jks` | 不得修改 |
| Alias | `reverse-tutor` | 不得修改 |
| applicationId（预览） | `com.reversetutor.preview` | 当前预览构建使用 |
| applicationId（正式） | `com.reversetutor.app` | 正式替换构建使用，须经 Phase 6 批准 |

签名密钥、alias 和 applicationId 一律不得修改。正式替换包的签名切换须经用户明确批准。

---

## 10. 不代表替换就绪

**本 Runbook 的完成不代表 PWA/Capacitor 退出或替换就绪。**

替换就绪须满足以下全部条件（见 P6-005 决策文档）：
- P0 遗留入口全部 `verified` 或有明确 `waived`
- 模拟器设备矩阵完成
- 主力真机设备矩阵完成，或明确记录 `not_run` 并由用户批准风险
- 迁移指南经过至少一轮 fixture 导入演练
- 回滚和备份流程可执行
- 签名、applicationId、PWA 生产路径无未授权改动

未获用户明确批准前，PWA/Capacitor 退出决策保持 `proposed` 或 `blocked`。

---

## 11. 验收清单

- [ ] 新人只按 Runbook 能完成模拟器安装
- [ ] 新人只按 Runbook 能完成导入 dry-run
- [ ] 新人只按 Runbook 能完成确认导入
- [ ] 新人只按 Runbook 能完成失败回滚演练
- [ ] Runbook 中所有命令使用 PowerShell
- [ ] Python 命令只写 `py`，不写 `python`
- [ ] 无真实 Provider 请求
- [ ] 所有自动化生成场景使用 Fake Runtime
