# ZIO #9874 修复方案

## 问题分析

### 问题描述
当 `Cause` 同时包含 failure 和 defect 时，`catchAll` 会静默忽略 defect，只处理 failure。

### 复现代码
```scala
val dieCause: Cause[String] = Cause.die(new RuntimeException("boom"))
val combinedCause = dieCause && Cause.fail("boom")

ZIO.failCause(combinedCause).catchAll { e =>
  ZIO.debug(e)
} *> ZIO.debug("Success")
```

**当前输出：**
```
handled: boom
Success
```

**预期输出：** 应该抛出 defect（RuntimeException），不应该被 catchAll 捕获。

### 根本原因

问题出在 `Cause.failureOrCause` 方法（`core/shared/src/main/scala/zio/Cause.scala` 第 130-134 行）：

```scala
final def failureOrCause: Either[E, Cause[Nothing]] = failureOption match {
  case Some(error) => Left(error)
  case None        => Right(self.asInstanceOf[Cause[Nothing]])
}
```

**问题：**
1. 当 Cause 同时包含 failure 和 defect 时，`failureOption` 返回 `Some(error)`
2. 方法直接返回 `Left(error)`，完全忽略了 defect
3. `catchAll` 使用 `failureOrCause` 来决定是否处理，导致 defect 被静默忽略

### 调用链
```
catchAll
  └─> foldZIO
       └─> foldCauseZIO(c => c.failureOrCause.fold(failure, Exit.failCause), success)
            └─> failureOrCause  // ← 问题在这里
```

## 修复方案

### 方案 1：修改 failureOrCause（推荐）

在返回 failure 之前，先检查是否有 defect。如果有 defect，返回包含 defect 的 Cause。

```scala
final def failureOrCause: Either[E, Cause[Nothing]] = {
  failureOption match {
    case Some(error) =>
      // Check if there are defects in the cause
      if (self.isDie) {
        // Return the full cause (including defects) instead of just the failure
        Right(self.asInstanceOf[Cause[Nothing]])
      } else {
        Left(error)
      }
    case None => Right(self.asInstanceOf[Cause[Nothing]])
  }
}
```

**优点：**
- ✅ 修复点在底层，所有使用 `failureOrCause` 的地方都受益
- ✅ 逻辑清晰：如果有 defect，返回整个 Cause
- ✅ 向后兼容：只在有 defect 时改变行为

**缺点：**
- ⚠️ 可能影响其他依赖此行为的代码（需要测试）

### 方案 2：修改 foldZIO

在 `foldZIO` 中特殊处理 defect：

```scala
final def foldZIO[R1 <: R, E2, B](failure: E => ZIO[R1, E2, B], success: A => ZIO[R1, E2, B])(implicit
  ev: CanFail[E],
  trace: Trace
): ZIO[R1, E2, B] =
  foldCauseZIO(
    c => c.failureOrCause.fold(
      failure,
      cause => 
        // If there are defects, fail with the full cause
        if (cause.isDie) Exit.failCause(cause) 
        else Exit.failCause(cause)
    ), 
    success
  )
```

**缺点：**
- ⚠️ 逻辑重复，不够优雅
- ⚠️ 只在 `foldZIO` 中修复，其他地方还有问题

### 方案 3：添加新的方法 failureOrDefectOrCause

添加新方法，不改变现有行为：

```scala
final def failureOrDefectOrCause: Either[Either[E, Throwable], Cause[Nothing]] = {
  failureOption match {
    case Some(error) =>
      dieOption match {
        case Some(defect) => Left(Right(defect))  // Has defect, prioritize it
        case None => Left(Left(error))  // Only failure
      }
    case None =>
      dieOption match {
        case Some(defect) => Left(Right(defect))
        case None => Right(self.asInstanceOf[Cause[Nothing]])
      }
  }
}
```

**缺点：**
- ⚠️ 需要修改多个调用点
- ⚠️ API 变更较大

## 推荐实现

采用**方案 1**，修改 `failureOrCause` 方法：

```scala
/**
 * Retrieve the first checked error on the `Left` if available, if there are
 * no checked errors return the rest of the `Cause` that is known to contain
 * only `Die` or `Interrupt` causes.
 * 
 * Note: If the cause contains both failures and defects, the full cause is
 * returned on the `Right` to ensure defects are not silently ignored.
 * This preserves the invariant that defects should always be prioritized.
 */
final def failureOrCause: Either[E, Cause[Nothing]] = {
  failureOption match {
    case Some(error) =>
      // If there are defects in the cause, return the full cause
      // to ensure defects are not silently ignored by error handlers
      if (self.isDie) {
        Right(self.asInstanceOf[Cause[Nothing]])
      } else {
        Left(error)
      }
    case None => Right(self.asInstanceOf[Cause[Nothing]])
  }
}
```

## 测试用例

### 现有测试（需要修改）
- 查找 `CauseSpec.scala` 中的相关测试

### 新增测试
```scala
test("failureOrCause should return full cause when defects are present") {
  val dieCause = Cause.die(new RuntimeException("boom"))
  val failCause = Cause.fail("error")
  val combined = dieCause && failCause
  
  val result = combined.failureOrCause
  assert(result.isRight) && assert(result.toOption.exists(_.isDie))
}

test("catchAll should not catch defects") {
  val dieCause = Cause.die(new RuntimeException("boom"))
  val failCause = Cause.fail("error")
  val combined = dieCause && failCause
  
  val effect = ZIO.failCause(combined).catchAll(e => ZIO.succeed(s"handled: $e"))
  
  for {
    result <- effect.exit
  } yield assert(result)(failsCause(hasDie))
}
```

## 影响范围

### 受影响的 API
- `catchAll` - 不再捕获包含 defect 的 cause
- `foldZIO` - 同上
- `fold` - 同上
- 所有依赖 `failureOrCause` 的内部实现

### 向后兼容性
- ⚠️ **Breaking change** - 之前能捕获的 defect 现在不会被捕获
- ✅ **正确行为** - 这本来就是预期的行为（defect 不应该被捕获）
- ✅ **文档说明** - 需要在 release notes 中说明

## 验证步骤

1. 运行现有测试套件
2. 添加新的测试用例
3. 验证复现代码行为正确
4. 检查是否有其他项目依赖此行为

## 参考

- Issue: https://github.com/zio/zio/issues/9874
- ZIO Cause 文档：https://zio.dev/reference/error-handling/cause/
- Defect 处理原则：Defects should never be caught, they should be logged and the fiber should be terminated

---

*创建时间：2026-03-04 16:20 GMT+8*
