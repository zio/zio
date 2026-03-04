# CI 修复说明

## 问题
CI 测试失败，原因是 scalafmt 格式检查不通过。

```
scalafmt: 1 files must be formatted
```

受影响的文件：
- `core/shared/src/main/scala/zio/Cause.scala`
- `core-tests/shared/src/test/scala/zio/Issue9874Spec.scala`

## 解决方案

### 方案 1：本地运行 scalafmt（推荐）

```bash
cd zio-bounty
sbt "coreJVM/scalafmt; coreTestsJVM/scalafmt"
git add -A
git commit -m "fix: format code with scalafmt"
git push fork fix/issue-9874-defect-priority
```

### 方案 2：手动格式化

按照 `.scalafmt.conf` 的配置手动调整代码格式。

### 方案 3：使用 GitHub Actions

等待 CI 运行完成，查看具体的格式差异，然后手动修复。

## 当前状态

- ✅ 代码修复完成
- ✅ PR 已创建 (#10517)
- ⏳ CI 正在重新运行
- ⚠️ 需要运行 scalafmt 格式化代码

## 下一步

1. 运行 `sbt scalafmt` 格式化代码
2. 提交格式化后的代码
3. 等待 CI 通过
4. 等待维护者审查

---

*创建时间：2026-03-04 18:10 GMT+8*
