---
layout: docs
section: usage
title:  "Mapping/Chaining"
---

# Mapping

You can change an `IO[E, A]` to an `IO[E, B]` by calling the `map` method with a function `A => B`. This lets you transform values produced by actions into other values.

```tut:silent
import scalaz.zio._

val _: IO[Nothing, Int] = IO.point(21).map(_ * 2)
```

You can transform an `IO[E, A]` into an `IO[E2, A]` by calling the `leftMap` method with a function `E => E2`:

```tut:silent
val _: IO[Exception, String] = IO.fail("No no!").leftMap(msg => new Exception(msg))
```

# Chaining

You can execute two actions in sequence with the `flatMap` method. The second action may depend on the value produced by the first action.

```tut:silent
val _: IO[Nothing, List[Int]] = IO.point(List(1, 2, 3)).flatMap { list =>
  IO.point(list.map(_ + 1))
}
```

You can use Scala's `for` comprehension syntax to make this type of code more compact:

```tut:silent
val _: IO[Nothing, List[Int]] = for {
  list <- IO.point(List(1, 2, 3))
  added <- IO.point(list.map(_ + 1))
} yield added
```
