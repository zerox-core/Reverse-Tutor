# P6 双设备回归阻塞修复设计

## 目标

恢复 P6-003 中两项失效的设备证据：导入导出全流程与 Android 12 真机图谱交互；不改变既有业务协议、冻结层或真实 Provider 行为。

## 决策

采取“生产行为优先”的最小修复。

1. 导入导出 AndroidTest 以当前中文正式 UI 的可访问文本/语义为准，替换已失效的英文断言。测试仍验证 dry-run、append、new-space、overwrite、导出与 wipe，不能通过删除检查或只增加超时通过。
2. 图谱先为 Android 12 的失败场景建立可复现的测试同步和布局证据：选择节点、打开确认对话框与聊天证据回调均必须在真机可观察。若产品行为未达到既有契约，修复非冻结的 feature:memory UI/状态投影；若产品行为正确而测试未等待重组完成，仅修正测试同步与滚动方式。

## 边界

- 可修改：`mobile-native/app/src/androidTest/...`，以及确有证据表明需要时的 `mobile-native/feature/memory/...` 非冻结 UI 层。
- 不可修改：`mobile-native/core/model`、`core/protocol`、`core/llm`、`core/data`、Room schema/DAO/migration、SecretStore、PWA、Capacitor、签名和 release 配置。
- 不使用真实 Provider、URL 或 API key。

## 验收

- 模拟器 `Phase4ImportExportDeviceTest` 通过完整流程。
- 主力真机 `Phase5GraphDeviceTest` 五项全部通过，且模拟器不回归。
- 重新运行受影响 JVM/AndroidTest、`:app:test`、`:app:lint`、`:app:assembleDebug`；记录两台设备的真实结果。
- 失败时保留失败状态与日志，不以放宽断言、产品文案改写或虚构设备证据结案。
