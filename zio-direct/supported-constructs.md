# Scala Constructs Supported by ZIO-Direct

> ZIO-direct supports using the following constructs inside of a `defer` block. Approximate translations of the what the Scala code looks like are available below. In order to see the exact translations for any code in a defer block, use `defer.info`.

# Supported Constructs

ZIO-direct supports using the following constructs inside of a `defer` block. Approximate translations of the what the Scala code looks like are available below. In order to see the exact translations for any code in a defer block, use `defer.info`.

### blocks

```scala
defer {
  val a = ZIO.succeed("Hello").run
  val b = ZIO.succeed("World").run
  a + " " + b
}
```
Translation:
```scala
ZIO.succeed("Hello").flatMap { a =>
  ZIO.succeed("World").map { b =>
    a + " " + b
  }
}
```

Blocks can also have nested blocks.
```scala
defer {
  val a = ZIO.succeed("Hello").run
  val b = {
    val x = ZIO.succeed("to").run
    val y = ZIO.succeed("World").run
    x + " " + y
  }
  a + " " + b
}
```
Translation:
```scala
ZIO.succeed("Hello").flatMap { a =>
  {
    ZIO.succeed("to").flatMap { x =>
      ZIO.succeed("World").map { y =>
        x + " " + y
      }
    }
  }.map { b =>
    a + " " + b
  }
}
```

### if/else
If statements with one or multiple ZIO.run values in the condition(s) and action(s).

```scala
defer {
  if (ZIO.succeed(123).run < 456 && ZIO.succeed("foo") == "foo")
    ZIO.succeed("a").run
  else
    ZIO.succeed("b").run
}
```

Translation:
> Note that each condition is separated into it's own nested flatMap chain step
(from left-to-right) so if earlier conditions yield `false` ZIO computations of
later ones will not be executed.
```scala
ZIO.succeed(123).flatMap { a =>
  if (a < 456)
    ZIO.succeed("foo").flatMap { b =>
      if (b == "foo")
        ZIO.succeed("a")
      else
        ZIO.succeed("b")
    }
  else
    ZIO.succeed("b")
}
```

### match

Match statements with ZIO.run in the left-hand-side (before "match") and/or the right-hand-side (after the "=>").
ZIO.run calls inside of match guards (i.e. if-statements after `case Clause`) are not supported yet.

```scala
defer {
  ZIO.succeed("Hello").run match {
    case hello @ "Hello" =>
      val world = ZIO.succeed(" World").run
      hello + " " + world
    case _ =>
      "Nothing"
  }
}
```

Translation:
```scala
ZIO.succeed("Hello").flatMap { x =>
  x match {
    case hello @ "Hello" =>
      ZIO.succeed(" World").flatMap { world =>
        hello + " " + world
      }
    case _ =>
      ZIO.succeed("Nothing")
  }
}
```

### try

Try statements with ZIO.run in the left-hand-side (before "try") and/or the right-hand-side (after the "=>").

```scala
defer {
  try {
    val a = ZIO.succeed(123).run
    val b = ZIO.attempt(somethingUnsafe).run
    a + b
  } catch {
    case e: Exception =>
      ZIO.succeed(789).run
  }
}
```

Translation:
```scala
ZIO.succeed(123).flatMap { a =>
  ZIO.attempt(somethingUnsafe).map { b =>
    a + b
  }.catchAll { e =>
    ZIO.succeed(789)
  }
}
```
Note that because try-statements are translated into ZIO.catchAll, errors that go into fail fail-channel
will not be caught by the catch block. For example:

```scala
def throwsException() = throw new Exception("foo")

defer {
  try {
    // Will not be caught!!
    ZIO.succeed(throwsException()).run
  } catch {
    case e: Exception => 123
  }
}

defer {
  try {
    // WILL be caught!!
    ZIO.attemt(throwsException()).run
  } catch {
    case e: Exception => 123
  }
}
```

In cases where methods that throw exceptions not not wrapped into ZIO computations, they will also
not be caught because the assumption is that they are pure-computations hence can be wrapped into `ZIO.succeed` blocks.

```scala
def throwsException() = throw new Exception("foo")

defer {
  try {
    // Will not be caught!!
    throwsException()
  } catch {
    case e: Exception => 123
  }
}

// Translation:
ZIO.succeed(throwsException()).catchAll { e =>
  case e: Exception => 123
}
```

In order to rectify this situation, a region-based operator `unsafe { ... }` can be used to wrap
all blocks of code that could potentially throw exceptions. ZIO-Direct will the know to wrap
them into `ZIO.attempt` clauses instead of `ZIO.succeed`.
```scala
def throwsException() = throw new Exception("foo")

defer {
  try {
    unsafe {
      // This WILL be caught!!
      throwsException()
    }
  } catch {
    case e: Exception => 123
  }
}

// Translation:
ZIO.attempt(throwsException()).catchAll { e =>
  case e: Exception => 123
}
```

Note that that ZIO computations with `.run` calls and other kinds of constructs supported by zio-direct
can be used inside of `unsafe` blocks, and these computations will be used as-is (i.e. if they contain
ZIO.succeed calls the will not be changed into something else).

```scala
defer {
  try {
    unsafe {
      val a = ZIO.succeed(123).run
      throwsException()
      val b = ZIO.succeed(456).run
      a + b
    }
  } catch {
    case e: Exception => 123
  }
}

// Translation:
ZIO.succeed(123).flatMap { a =>
  ZIO.attempt(throwsException()).flatMap { _ =>
    ZIO.succeed(456).map { b =>
      a + b
    }
  }
}.catchAll { e =>
  case e: Exception => 123
}
```

### while

While-clauses will be translated into recursive functions that conditionally recurse into a flatMap call based on the while-condition.

> Generally due to the presence of functions like [`ZIO.iterate`](https://zio.dev/reference/control-flow/#iterate) and [`ZIO.repeat`](https://zio.dev/reference/test/aspects/repeat-and-retry/#repeat) the critical use-case for ZIO-direct's while-loop should be limited.

```scala
// Note that because mutable variable usage is generally not allowed in zio-direct the below code can only be run in "Lenient Mode."
var i = 0
defer {
  while (i < 10) {
    ZIO.attempt(println("Hello")).run
    i += 1
  }
}

// Translation:
val i = 0
def loop(): ZIO[Any, Throwable, Unit] = {
  if (i < 10) {
    ZIO.attempt(println("Hello")).flatMap { _ =>
      i += 1
      loop()
    }
  } else {
    ZIO.unit
  }
}
loop()
```

Since mutable variables are generally not allowed in `defer { ... }` blocks, it is recommended to use mutable references from ZIO's
[`Ref`](https://zio.dev/reference/concurrency/ref/) class instead.

```scala
defer {
  val ref = Ref.make(0).run
  while (ref.get.run < 10) {
    ZIO.attempt(println("Hello")).run
    ref.update(_ + 1).run
  }
}

// Translation:
Ref.make(0).flatMap { ref =>
  def loop(): ZIO[Any, Throwable, Unit] = {
    ref.get.flatMap { x =>
      if (x < 10) {
        ZIO.attempt(println("Hello")).flatMap { _ =>
          ref.update(_ + 1).flatMap { _ =>
            loop()
          }
        }
      } else {
        ZIO.unit
      }
    }
  }
  loop()
}
```

### for-loop/foreach

Scala for-loops and collection.foreach are the same thing (the former dis desugars into the latter).
ZIO-direct will translate them into ZIO.foreach calls.

> Similar to while-loops, this construct is
largely overshawoed by ZIO's own [`foreach`](https://zio.dev/reference/control-flow/#iterate) and [`iterate`](https://zio.dev/reference/control-flow/#foreach) combinators.

```scala
defer {
  for (i <- 1 to 10) {
    ZIO.attempt(println(i)).run
  }
}

// Translation:
ZIO.foreach(1 to 10) { i =>
  ZIO.attempt(println(i))
}.map(_ => ()) // since the final result must have a type of Unit
```
