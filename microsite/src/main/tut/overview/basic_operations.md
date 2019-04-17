---
layout: docs
section: overview
title:  "Basic Operations"
---

# {{page.title}}

```scala mdoc:invisible
import scalaz.zio._
import scalaz.zio.console._
```

# Mapping

You can map over the success channel of an effect by calling the `ZIO#map` method. This lets you transform the success values of effects into other values.

```scala mdoc:silent
import scalaz.zio._

val succeded: UIO[Int] = IO.succeed(21).map(_ * 2)
```

You can map over the error channel of an effect by calling the `ZIO#mapError` method. This lets you transform the failure values of effects into other values.

```scala mdoc:silent
val failed: IO[Exception, Unit] = 
  IO.fail("No no!").mapError(msg => new Exception(msg))
```

Note that mapping over success or error channels does not change the success or failure of the effect, in the same way that mapping over an `Either` does not change whether the `Either` is `Left` or `Right`.

# Chaining

You can execute two effects in sequence with the `flatMap` method. The second effect may depend on the success value of the first effect:

```scala mdoc:silent
val sequenced: UIO[List[Int]] = 
  IO.succeed(List(1, 2, 3)).flatMap { list =>
    IO.succeed(list.map(_ + 1))
  }
```

# For Comprehensions

Because the `ZIO` data type supports both `flatMap` and `map`, you can use Scala's _for comprehensions_ to build sequential effects:

```scala mdoc:silent
val program = 
  for {
    _ <- putStrLn("Hello! What is your name?")
    n <- getStrLn
    _ <- putStrLn(s"Hello, ${n}, welcome to ZIO!")
  } yield ()
```

# Next Step

If you are comfortable with the basic operations on ZIO effects, then the next step is to learn about [error handling](handling_errors.html).
