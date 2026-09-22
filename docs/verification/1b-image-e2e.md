# 1b 图片上传端到端验证记录

> 日期：2026-09-23 ｜ 环境：emulator-5554（AVD rt_test）｜ 构建：debug（包名 com.reversetutor.preview.memtest，:app:assembleDebug @ 0a99ef2）
> 性质：大阶段一·小阶段 1b 的验证记录。按拍板流程，发现的 bug 只记录不修复。

## 验证用例与结果

| # | 用例 | 步骤 | 结果 |
|---|------|------|------|
| 1 | 相册选图进输入框 | 会话页 → 添加图片或资料 → 系统权限框 Allow all → app 附件面板「相册」→ 系统 Photo Picker 选 aily_e2e_img.png（400×300, 1.1KB） | ✅ 输入框出现附件 chip「图片 · aily_e2e_img.png · 已准备」，附带「移除aily_e2e_img.png」按钮 |
| 2 | 附件移除 | 点 chip 的移除按钮 | ✅ chip 消失，输入框回到无附件态 |
| 3 | 单张 >20MB 拒绝 | 推入 21,065,363 字节（≈20.1MB）随机噪声 PNG → 相册选中 | ✅ 无 chip，输入框提示「单张图片不能超过 20 MB。」 |
| 4 | 权限未授权路径 | 首次点添加图片或资料 | ✅ 弹系统照片权限框（Allow limited access / Allow all / Don't allow），授权后进入 app 附件面板 |

## 观察记录（非阻断，留待产品/修复阶段定夺）

- O1 附件面板只有「相册」「文件」两个入口，未见「拍照」入口。ChatComposerContracts 支持 Camera 类型附件，但该演示壳面板未暴露拍照。是否补拍照入口待产品确认。
- O2 9 附件上限未做机械 E2E（需连续选 10 张），该分支由 ChatAttachmentPolicy 既有单测覆盖（TooMany →「每条消息最多添加 9 个附件。」）。
- O3 发送环节的 image_url 载荷装配由 core:llm 既有单测覆盖（base64/URL 两种形态）。本次 E2E 未点「发送」：模拟器配置的「本地测试默认模型」计费档未确认，遵守「测试不烧付费额度」铁律，真实发送留待人工或免费档渠道确认后补验。
- O4 验证期间模拟器被外部关闭过一次（日志显示优雅 shutdown），重启 rt_test 后验证继续完成；设备侧原因未查明。

## 测试资产

- 脚本：docs/verification/scripts/1b/（pick-image-flow.py 用例 1/2/4，oversize-reject.py 用例 3，含 21MB PNG 的纯 stdlib 生成器）
- 小测试图：docs/verification/scripts/1b/assets/aily_e2e_img.png（1.1KB）；21MB 大图由 oversize-reject.py 现场生成不入库
