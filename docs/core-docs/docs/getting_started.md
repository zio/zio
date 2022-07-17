---
id: getting_started
title:  "Getting Started"
---

Include ZIO in your project by adding the following to your `build.sbt` file:

```scala mdoc:passthrough
println(s"""```""")
println(s"""libraryDependencies += "dev.zio" %% "zio" % "${zio.BuildInfo.version.split('+').head}"""")
println(s"""```""")
```

If you want to use ZIO streams, you should also include the following dependency:

```scala mdoc:passthrough
println(s"""```""")
println(s"""libraryDependencies += "dev.zio" %% "zio-streams" % "${zio.BuildInfo.version.split('+').head}"""")
println(s"""```""")
```

## Main

Your application can extend `ZIOAppDefault`, which provides a complete runtime system and allows you to write your whole program using ZIO:

```scala mdoc:compile-only
import zio._
import zio.Console._

object MyApp extends ZIOAppDefault {

  def run = myAppLogic

  val myAppLogic =
    for {
      _    <- printLine("Hello! What is your name?")
      name <- readLine
      _    <- printLine(s"Hello, ${name}, welcome to ZIO!")
    } yield ()
}
```
The `run` method should return a ZIO value which has all its errors handled,  
which, in ZIO parlance, is an unexceptional ZIO value.  

One way to do this is to invoke `fold` over a ZIO value, to get an unexceptional ZIO value.
That requires two handler functions: `eh: E => B` (the error handler) and `ah: A => B` (the success handler).

If `myAppLogic` fails, `eh` will be used to get from `e: E` to `b: B`;
if it succeeds, `ah` will be used to get from `a: A` to `b: B`. 

`myAppLogic`, as folded above, produces an unexceptional ZIO value, with `B` being `Int`.  
If `myAppLogic` fails, there will be a 1; if it succeeds, there will be a 0.

---

If you are integrating ZIO into an existing application, using dependency injection, or do not control your main function, then you can create a runtime system in order to execute your ZIO programs:

```scala mdoc:compile-only
import zio._

object IntegrationExample {
  val runtime = Runtime.default

  Unsafe.unsafe { implicit unsafe =>
    runtime.unsafe.run(ZIO.attempt(println("Hello World!"))).getOrThrowFiberFailure()
  }
}
```

Ideally, your application should have a _single_ runtime, because each runtime has its own resources (including thread pool and unhandled error reporter).

## Console

ZIO provides a module for interacting with the console. You can import the functions in this module with the following code snippet:

If you need to print text to the console, you can use `print` and `printLine`:

```scala mdoc:compile-only
import zio._

// Print without trailing line break
Console.print("Hello World")

// Print string and include trailing line break
Console.printLine("Hello World")
```

If you need to read input from the console, you can use `readLine`:

```scala mdoc:compile-only
import zio._

val echo = Console.readLine.flatMap(line => Console.printLine(line))
```

## Learning More

To learn more about ZIO, see the [Overview](overview/index.md).
