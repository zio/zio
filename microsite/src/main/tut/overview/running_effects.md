---
layout: docs
section: overview
title:  "Running Effects"
---

# {{page.title}}

Once you have constructed an effect, you have a choice about how to run it.

# App

If you construct a single effect for your whole program, then the most natural way to run the effect is to extend `scalaz.zio.App`. 

This class provides Scala with a main function, so it can be called from IDEs and launched from the command-line. All you have to do is implement the `run` method, which will be passed command-line arguments in a `List`:

```tut:silent
import scalaz.zio._
import scalaz.zio.console._

object MyApp extends App {

  def run(args: List[String]) =
    myAppLogic.fold(_ => 1, _ => 0)

  val myAppLogic =
    for {
      _ <- putStrLn("Hello! What is your name?")
      n <- getStrLn
      _ <- putStrLn(s"Hello, ${n}, welcome to ZIO!")
    } yield ()
}
```

If you are using a custom environment for your application, you will have to supply your environment to the effect (using `ZIO#provide`) before you return it from `run`, because `App` does not know how to supply custom environments.

# DefaultRuntime

Most applications are not greenfield, and must integrate with legacy code, and non-functional libraries and frameworks.

In these cases, a better solution for running effects is to create a `Runtime`, which can be passed around and used to run effects wherever required.

ZIO contains a default runtime called `DefaultRuntime`. This bundles together all ZIO environments (including `Console`, `System`, `Clock`, `Random`, `Scheduler`, and on the JVM, `Blocking`), and can run effects requiring one or more of these environments.

To create a `DefaultRuntime`, merely use the `new` keyword:

```tut:silent
val runtime = new DefaultRuntime {}
```

Once you have a runtime, you can use it to execute effects:

```tut:silent
runtime.unsafeRun(putStrLn("Hello World!"))
```

# Custom Runtime

If you are using a custom environment for your application, then you may find it useful to create a `Runtime` specifically tailed for that environment.

A custom `Runtime[R]` can be created with two values:

 - **`R` Environment**. You must supply the environment that will be used to run effects.
 - **`Platform`**. You must supply a `Platform`, which contains platform-specific features required for bootstrapping ZIO.

For example, the following creates a `Runtime` that can provide an `Int` to effects, using the default `Platform` provided by ZIO:

```tut:silent
import scalaz.zio.internal.PlatformLive

val myRuntime: Runtime[Int] = Runtime(42, PlatformLive.Default)
```

# Next Steps

If you are comfortable with running effects, then congratulations! You are now ready to dive into other sections on the ZIO microsite, covering data types, use cases, and interop with other systems. Refer to the Scaladoc for detailed documentation on all the core ZIO types and methods.