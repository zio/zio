---
id: overview_running_effects
title:  "Running Effects"
---

ZIO effects are precise plans that _describe_ a computation or interaction. Ultimately, every effect must be _executed_ by the ZIO runtime.

In this section, you will learn about the several ways that ZIO provides for you to execute effects in your application.

## App

If you construct a single effect for your whole program, the most natural way to run the effect is to extend `ZIOAppDefault`. 

This class provides Scala with a JVM-compatible main function, so it can be called from IDEs and launched from the command-line. All you have to do is implement the `run` method by returning the effect to run.

```scala mdoc:silent
import zio._
import zio.Console._

object MyApp extends ZIOAppDefault {

  def run =
    for {
      _    <- printLine("Hello! What is your name?")
      name <- readLine
      _    <- printLine(s"Hello, ${name}, welcome to ZIO!")
    } yield ()
}
```

If you are using a custom environment for your application, you will have to supply your environment to the effect (using `ZIO#provideEnvironment` or, if you are using layers, `ZIO#provide`) before you return it from `run`. 

`ZIOAppDefault` does not know how to supply custom environments.

## Default Runtime

Most applications are not greenfield, meaning they must integrate with legacy code and procedural libraries and frameworks.

In these cases, a better solution for running effects is to create a `Runtime`, which can be passed around and used to run effects wherever required.

ZIO contains a default runtime called `Runtime.default`.

To access it, merely use

```scala mdoc:silent
val runtime = Runtime.default
```

Once you have a runtime, you can use it to execute effects:

```scala mdoc:silent
Unsafe.unsafe { implicit unsafe =>
    runtime.unsafe.run(ZIO.attempt(println("Hello World!"))).getOrThrowFiberFailure()
}
```

In addition to `run`, which is for synchronous execution, there are other methods available on `Runtime` that support asynchronous execution.

## Custom Runtime

If you are using a custom environment for your application, you may find it useful to create a `Runtime` specifically tailored for that environment.

A custom `Runtime[R]` can be created with a `ZEnvironment[R]` (which holds the context required in order to execute your effects), as well as fiber refs and runtime flags (which can generally be set to default values).

For example, the following creates a `Runtime` that can provide an `Int` to effects :

```scala mdoc:silent
val myRuntime: Runtime[Int] = 
  Runtime(ZEnvironment[Int](42), FiberRefs.empty, RuntimeFlags.default)
```

## Error Reporting

The ZIO runtime system automatically logs all errors encountered when executing your effects, so long as those errors are not handled by your ZIO code.

You can specify a custom logger easily using _ZIO Logging_, which can intercept these logged errors and handle them as configured by your logging backend.

## Next Steps

If you are comfortable with running effects, congratulations!

You are now ready to dive into other sections on the ZIO website covering data types, use cases, and interop with other systems. 

Refer to the Scaladoc for detailed documentation on all the core ZIO types and methods.
