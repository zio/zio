---
id: overview_running_effects
title:  "Running Effects"
---

ZIO provides several different ways of running effects in your application.

## App

If you construct a single effect for your whole program, the most natural way to run the effect is to extend `zio.App`. 

This class provides Scala with a main function, so it can be called from IDEs and launched from the command-line. All you have to do is implement the `run` method, which will be passed all command-line arguments in a `List`:

```scala mdoc:silent
import zio._
import zio.Console._

object MyApp extends zio.ZIOAppDefault {

  def run =
    for {
      _    <- printLine("Hello! What is your name?")
      name <- readLine
      _    <- printLine(s"Hello, ${name}, welcome to ZIO!")
    } yield ()
}
```

If you are using a custom environment for your application, you will have to supply your environment to the effect (using `ZIO#provide`) before you return it from `run`. `App` does not know how to supply custom environments.

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
runtime.unsafeRun(ZIO.attempt(println("Hello World!")))
```

In addition to `unsafeRun`, there are other methods which allow the asynchonous execution of effects or the ability to transform them into `Future`s.

## Custom Runtime

If you are using a custom environment for your application, you may find it useful to create a `Runtime` specifically tailored for that environment.

A custom `Runtime[R]` can be created with an `Environment[R]`. This represents the environment that will be provided to effects when they are executed.

For example, the following creates a `Runtime` that can provide an `Int` to effects :

```scala mdoc:silent
val myRuntime: Runtime[Int] = Runtime(ZEnvironment[Int](42), FiberRefs.empty, RuntimeFlags.default)
```

## Error Reporting

In the `Runtime` there is an error reporter that is called by ZIO to report every unhandled error. It is a good idea to supply your own error reporter that can log unhandled errors to a file.

The default unhandled error reporter merely logs the error to standard error (`stderr`).

## Next Steps

If you are comfortable with running effects, congratulations! You are now ready to dive into other sections on the ZIO website covering data types, use cases, and interop with other systems. 

Refer to the Scaladoc for detailed documentation on all the core ZIO types and methods.
