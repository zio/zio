---
id: zoom-in-out-on-either-values
title: "Zoom in/out on Either Values"
---

With `Either` ZIO values, we can zoom in or out on the left or right side of an `Either`, as well as we can do the inverse and zoom out.

```scala mdoc:compile-only
import zio._

val eitherEffect: ZIO[Any, Exception, Either[String, Int]] = ???

eitherEffect // ZIO[Any, Exception, Either[String, Int]]
  .left      // ZIO[Any, Either[Exception, Int], String]
  .unleft    // ZIO[Any, Exception, Either[String, Int]]

eitherEffect // ZIO[Any, Exception, Either[String, Int]]
  .right     // ZIO[Any, Either[String, Exception], Int]
  .unright   // ZIO[Any, Exception, Either[String, Int]]
```
