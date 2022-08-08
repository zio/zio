---
id: exceptional-and-unexceptional-effects
title: "Exceptional and Unexceptional Effects"
---

Besides the `IO` type alias, ZIO has four different type aliases which can be categorized into two different categories:

- **Exceptional Effect**— `Task` and `RIO` are two effects whose error parameter is fixed to `Throwable`, so we call them exceptional effects.
- **Unexceptional Effect**— `UIO` and `URIO` have error parameters that are fixed to `Nothing`, indicating that they are unexceptional effects. So they can't fail, and the compiler knows about it.

So when we compose different effects together, at any point of the codebase we can determine this piece of code can fail or cannot. As a result, typed errors offer a compile-time transition point between this can fail and this can't fail.

For example, the `ZIO.acquireReleaseWith` API asks us to provide three different inputs: _require_, _release_, and _use_. The `release` parameter requires a function from `A` to `URIO[R, Any]`. So, if we put an exceptional effect, it will not compile:

```scala
object ZIO {
  def acquireReleaseWith[R, E, A, B](
    acquire: => ZIO[R, E, A],
    release: A => URIO[R, Any],
    use: A => ZIO[R, E, B]
  ): ZIO[R, E, B]
}
```
