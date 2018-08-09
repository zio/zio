---
layout: docs
section: usage
title:  "Brackets"
---

# Brackets

Brackets are a built-in primitive that let you safely acquire and release resources.

Brackets are used for a similar purpose as try/catch/finally, only brackets work with synchronous and asynchronous actions, work seamlessly with fiber interruption, and are built on a different error model that ensures no errors are ever swallowed.

Brackets consist of an *acquire* action, a *utilize* action (which uses the acquired resource), and a *release* action.

The release action is guaranteed to be executed by the runtime system, even if the utilize action throws an exception or the executing fiber is interrupted.

```tut:silent
import scalaz.zio._
```

```tut:invisible
import java.io.{ File, IOException }

def openFile(s: String): IO[IOException, File] = IO.point(???)
def closeFile(f: File): IO[Nothing, Unit] = IO.point(???)
def decodeData(f: File): IO[IOException, Unit] = IO.unit
def groupData(u: Unit): IO[IOException, Unit] = IO.unit
```

```tut:silent
val z: IO[IOException, Unit] = openFile("data.json").bracket(closeFile(_)) { file =>
  for {
    data    <- decodeData(file)
    grouped <- groupData(data)
  } yield grouped
}
```

Brackets have compositional semantics, so if a bracket is nested inside another bracket, and the outer bracket acquires a resource, then the outer bracket's release will always be called, even if, for example, the inner bracket's release fails.

A helper method called `ensuring` provides a simpler analogue of `finally`:

```scala
val composite = action1.ensuring(cleanupAction)
```
