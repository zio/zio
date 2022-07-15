---
id: overview_basic_operations
title:  "Basic Operations"
---

Like the `String` data type, as well as the collection data types in Scala (such as `List`, `Map`, and `Set`), ZIO effects are _immutable_, and cannot be changed. 

In order to transform or combine ZIO effects, you can use the methods on the ZIO data type, which return _new effects_, with the specified transformations or combinations applied to them.

There are two categories of methods on the ZIO data type:

 - **Transformations**. Transformation functions alter an effect in some well-defined way, allowing you to customize runtime behavior. For example, calling `effect.timeout(60.seconds)` on an effect returns a new effect, which, when executed, will apply a timeout to the original effect.
 - **Combinations**. Combination functions combine two or more effects together in a single effect. For example, calling `effect1.orElse(effect2)` combines two effects in such a fashion that the returned effect, when executed, will first execute the left hand side, and if that fails, it will then execute the right hand side. This lets you specify a fallback effect in case a primary effect fails.

```scala mdoc:invisible
import zio._
import zio.Console._
import java.io.IOException
```

## Mapping

If you have an effect that succeeds with some value, you can use `ZIO#map` to obtain a new effect, which will transform the value using the function you provide.

```scala mdoc:silent
import zio._

val succeeded: ZIO[Any, Nothing, Int] = ZIO.succeed(21).map(_ * 2)
```

In a similar fashion, you can transform an effect that has one error to an effect with a different error using the `ZIO#mapError` method, which requires you supply a function to do the conversion:

```scala mdoc:silent
val failed: ZIO[Any, Exception, Unit] = 
  ZIO.fail("No no!").mapError(msg => new Exception(msg))
```

Note that mapping the error or success value of an effect does not change _whether or not_ the effect fails or succeeds. This is similar to how mapping over Scala's `Either` data type does not change whether the `Either` is `Left` or `Right`.

## Chaining

You can execute two effects sequentially with the `flatMap` method. The `flatMap` method requires that you pass a callback, which will receive the success value of the first effect, and must return a second effect, which depends on this value:

```scala mdoc:silent
val sequenced: ZIO[Any, IOException, Unit] =
  Console.readLine.flatMap(input => Console.printLine(s"You entered: $input"))
```

If the first effect fails, the callback passed to `flatMap` will never be invoked, and the effect returned by `flatMap` will also fail.

In _any_ chain of effects created with `flatMap`, the first failure will short-circuit the whole chain, just like throwing an exception will prematurely exit a sequence of statements.

## For Comprehensions

Because the ZIO data type supports both `flatMap` and `map`, you can use Scala's _for comprehensions_ to build imperative effects:

```scala mdoc:silent
val program: ZIO[Any, IOException, Unit] =
  for {
    _    <- Console.printLine("Hello! What is your name?")
    name <- Console.readLine
    _    <- Console.printLine(s"Hello, ${name}, welcome to ZIO!")
  } yield ()
```

_For comprehensions_ provide a procedural syntax for creating chains of effects, and are the fastest way for most programmers to get up to speed using ZIO.

## Zipping

You can combine two effects into a single effect with the `ZIO#zip` method. 

The method returns an effect that will execute the left effect first, followed by the right effect, and which will place both success values into a tuple:

```scala mdoc:silent
val zipped: ZIO[Any, Nothing, (String, Int)] = 
  ZIO.succeed("4").zip(ZIO.succeed(2))
```

In any `zip` operation, if either the left or right-hand side fails, the composed effect will fail, because _both_ values are required to construct the tuple. If the left side fails, the right side will not be executed at all.

Sometimes, when the success value of an effect is not useful (for example, if it is `Unit`), it can be more convenient to use the `ZIO#zipLeft` or `ZIO#zipRight` functions, which first perform a `zip` and then map over the tuple to discard one side or the other:

```scala mdoc:silent
val zipRight1: ZIO[Any, IOException, String] =
  Console.printLine("What is your name?").zipRight(Console.readLine)
```

The `zipRight` and `zipLeft` functions have symbolic aliases, known as `*>` and `<*`, respectively. Some developers find these operators easier to read:

```scala mdoc:silent
val zipRight2: ZIO[Any, IOException, String] =
  Console.printLine("What is your name?") *>
    Console.readLine
```

## Next Steps

If you are comfortable with the basic operations on ZIO effects, the next step is to learn about [error handling](handling_errors.md).
