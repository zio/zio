---
id: zooming-in-on-nested-values
title: "Zooming In on Nested Values"
---

## Option

We can extract a value from a Some using `ZIO#some` and then we can unsome it again using `ZIO#unsome`:

```scala
ZIO.attempt(Option("something")) // ZIO[Any, Throwable, Option[String]]
  .some                          // ZIO[Any, Option[Throwable], String]
  .unsome                        // ZIO[Any, Throwable, Option[String]]
```

## Either

With `Either` ZIO values, we can zoom in or out on the left or right side of an `Either`, as well as we can do the inverse and zoom out:

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
