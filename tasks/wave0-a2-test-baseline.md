# Wave 0 · A2 测试基线报告

> 执行时间：2026-08-14
> 执行范围：`Android` 主线，Wave 0 治理文档变更后
> 验收标准：记录 Native JVM 与 Python 基线；不修改业务代码。

## Native JVM

命令：

```powershell
.\gradlew.bat test
```

结果：通过。已汇总 231 份 JUnit XML 报告，共 1101 个测试，0 failures、0 errors、0 skipped。

覆盖模块：`app`、全部 `core:*` 模块与全部 `feature:*` 模块。

## Python

命令：

```powershell
py -m pytest -q --ignore=tests/test_project_homepage.py
```

结果：通过。

```text
510 passed, 28 skipped in 356.62s (0:05:56)
```

跳过项来自可选在线/数据库集成路径；本次无 failure 或 error。

## A2 完成自检

- [x] Native JVM 全量测试通过。
- [x] Python 基线测试通过。
- [x] 已记录跳过项数量与耗时。
- [x] 未改业务代码。

## 后续使用规则

后续每个垂直切片至少运行其受影响 Native 模块测试与相关 Python 测试；合并门禁前重新执行本报告中的两条全量命令。
