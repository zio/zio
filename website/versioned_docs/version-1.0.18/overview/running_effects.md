---
id: overview_running_effects
title: "Running Effects"
---

ZIO provides several different ways of running effects in your application.

## App

If you construct a single effect for your whole program, then the most natural way to run the effect is to extend `zio.App`. 

This class provides Scala with a main function, so it can be called from IDEs and launched from the command-line. All you have to do is implement the `run` method, which will be passed all command-line arguments in a `List`:

```scala
import zio._
import zio.console._

object MyApp extends zio.App {

  def run(args: List[String]) =
    myAppLogic.exitCode

  val myAppLogic =
    for {
      _    <- putStrLn("Hello! What is your name?")
      name <- getStrLn
      _    <- putStrLn(s"Hello, ${name}, welcome to ZIO!")
    } yield ()
}
```

If you are using a custom environment for your application, you will have to supply your environment to the effect (using `ZIO#provide`) before you return it from `run`, because `App` does not know how to supply custom environments.

## Default Runtime

Most applications are not greenfield, and must integrate with legacy code, and procedural libraries and frameworks.

In these cases, a better solution for running effects is to create a `Runtime`, which can be passed around and used to run effects wherever required.

ZIO contains a default runtime called `Runtime.default`. This `Runtime` bundles together production implementations of all ZIO modules (including `Console`, `System`, `Clock`, `Random`, `Scheduler`, and on the JVM, `Blocking`), and it can run effects that require any combination of these modules.

To access it, merely use

```scala
val runtime = Runtime.default
```

Once you have a runtime, you can use it to execute effects:

```scala
runtime.unsafeRun(ZIO(println("Hello World!")))
```

In addition to the `unsafeRun` method, there are other methods that allow executing effects asynchronously or into `Future` values.

## Custom Runtime

If you are using a custom environment for your application, then you may find it useful to create a `Runtime` specifically tailored for that environment.

A custom `Runtime[R]` can be created with two values:

 - **`R` Environment**. This is the environment that will be provided to effects when they are executed.
 - **`Platform`**. This is a platform that is required by ZIO in order to bootstrap the runtime system.

For example, the following creates a `Runtime` that can provide an `Int` to effects, using the default `Platform` provided by ZIO:

```scala
import zio.internal.Platform

val myRuntime: Runtime[Int] = Runtime(42, Platform.default)
```

## Error Reporting

In the `Platform` that is a part of every runtime, there is an error reporter that will be called by ZIO to report every unhandled error. It is a good idea to supply your own error reporter, which can log unhandled errors to a file.

The default unhandled error reporter merely logs the error to standard error.

## Next Steps

If you are comfortable with running effects, then congratulations!

You are now ready to dive into other sections on the ZIO website, covering data types, use cases, and interop with other systems. 

Refer to the Scaladoc for detailed documentation on all the core ZIO types and methods.