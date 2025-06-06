---
id: zioapp 
title: "ZIOApp"
---

The `ZIOApp` trait is an entry point for a ZIO application that allows sharing layers between applications. It also
provides us the ability to compose multiple ZIO applications.

There is another simpler version of `ZIOApp` called `ZIOAppDefault`. We usually use `ZIOAppDefault` which uses the default ZIO environment (`ZEnv`).

## Running a ZIO effect

The `ZIOAppDefault` has a `run` function, which is the main entry point for running a ZIO application on the JVM:

```scala mdoc:compile-only
import zio._

object MyApp extends ZIOAppDefault {
  def run = for {
    _ <- Console.printLine("Hello! What is your name?")
    n <- Console.readLine
    _ <- Console.printLine("Hello, " + n + ", good to meet you!")
  } yield ()
}
```

## Accessing Command-line Arguments

ZIO has a service that contains command-line arguments of an application called `ZIOAppArgs`. We can access command-line arguments using the built-in `getArgs` method:

```scala mdoc:compile-only
import zio._

object HelloApp extends ZIOAppDefault {
  def run = for {
    args <- getArgs
    _ <-
      if (args.isEmpty)
        Console.printLine("Please provide your name as an argument")
      else
        Console.printLine(s"Hello, ${args.head}!")
  } yield ()
}
```

## Customized Runtime

In the ZIO app, by overriding its `bootstrap` value, we can map the current runtime to a customized one. Let's customize it by introducing our own executor:

```scala mdoc:invisible
import zio._
val myAppLogic = ZIO.succeed(???)
```

```scala mdoc:compile-only
import zio._
import zio.Executor
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

object CustomizedRuntimeZIOApp extends ZIOAppDefault {
  override val bootstrap = Runtime.setExecutor(
    Executor.fromThreadPoolExecutor(
      new ThreadPoolExecutor(
        5,
        10,
        5000,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue[Runnable]()
      )
    )
  )

  def run = myAppLogic
}
```

A detailed explanation of the ZIO runtime system can be found on the [runtime](runtime.md) page.

## Installing Low-level Functionalities

We can hook into the ZIO runtime to install low-level functionalities into the ZIO application, such as _logging_, _profiling_, and other similar foundational pieces of infrastructure.

A detailed explanation can be found on the [runtime](runtime.md) page.

## Composing ZIO Applications

To compose ZIO applications, we can use `<>` operator:

```scala mdoc:invisible
import zio._
val asyncProfiler, slf4j, loggly, newRelic = ZLayer.empty
```

```scala mdoc:compile-only
import zio._

object MyApp1 extends ZIOAppDefault {    
  def run = ZIO.succeed(???)
}

object MyApp2 extends ZIOAppDefault {
  override val bootstrap: ZLayer[Any, Any, Any] =
    asyncProfiler ++ slf4j ++ loggly ++ newRelic

  def run = ZIO.succeed(???)
}

object Main extends ZIOApp.Proxy(MyApp1 <> MyApp2)
```

The `<>` operator combines the layers of the two applications and then runs the two applications in parallel.

## Graceful Shutdown Timeout

The `gracefulShutdownTimeout` method controls how long the runtime waits for finalizers to complete when the application receives a termination signal (e.g., SIGINT from Ctrl+C). This is particularly important for applications that need to ensure proper cleanup of resources like database connections, file handles, or network sockets.

By default, it's set to `Duration.Infinity`, meaning the runtime will wait indefinitely for cleanup to complete. You can override this to set a specific timeout:

```scala mdoc:compile-only
import zio._

object MyApp extends ZIOAppDefault {
  // Wait at most 30 seconds for finalizers to complete
  override def gracefulShutdownTimeout: Duration = 30.seconds
  
  def run = ZIO.acquireReleaseWith(
    acquire = ZIO.logInfo("Acquiring resource...").as("MyResource")
  )(
    release = _ => ZIO.logInfo("Releasing resource...") *> ZIO.sleep(3.seconds)
  ) { resource =>
    ZIO.logInfo(s"Running with $resource, press Ctrl+C to interrupt") *> ZIO.never
  }
}
```

### Shutdown Process

When a termination signal is received, the runtime will:

1. Interrupt the main fiber using `fiber.tellInterrupt(Cause.interrupt(fiberId))`
2. Run all finalizers in an uninterruptible context
3. Wait up to the specified timeout for finalizers to complete
4. Force exit if timeout is reached

The shutdown process is uninterruptible, ensuring that cleanup operations can complete even if the application is interrupted again during shutdown. This is implemented using a `shutdownLatch` that blocks until either:
- All finalizers complete successfully
- The timeout is reached
- A catastrophic failure occurs

### Timeout Behavior

The timeout behavior is implemented differently based on the duration:

- `Duration.Infinity`: Waits indefinitely for finalizers to complete
- `Duration.Zero` or negative: Exits immediately without waiting
- Positive duration: Waits for the specified time before forcing exit

If the timeout is reached, the runtime will log a warning and force exit:
```
**** WARNING ****
Timed out waiting for ZIO application to shut down after {timeout}. 
You can adjust your application's shutdown timeout by overriding the `shutdownTimeout` method
```

### Error Handling

The runtime handles two types of shutdown scenarios:

1. **Normal Shutdown**: The application receives a termination signal and attempts to run finalizers within the timeout period.

2. **Catastrophic Failure**: If a catastrophic error occurs during shutdown, the runtime will log:
```
**** WARNING ****
Catastrophic error encountered. Application not safely interrupted. 
Resources may be leaked. Check the logs for more details and consider 
overriding `Runtime.reportFatal` to capture context.
```

### Platform Support

The graceful shutdown timeout behavior is implemented differently across platforms:

- **JVM**: Full support for graceful shutdown with timeout
- **Scala Native**: Full support for graceful shutdown with timeout
- **Scala.js**: No support for external termination signals or graceful shutdown

### Common Use Cases

1. **Database Applications**:
```scala mdoc:compile-only
import zio._

object DatabaseApp extends ZIOAppDefault {
  override def gracefulShutdownTimeout: Duration = 30.seconds
  
  def run = ZIO.acquireReleaseWith(
    acquire = ZIO.logInfo("Connecting to database...").as("DBConnection")
  )(
    release = conn => 
      ZIO.logInfo(s"Closing database connection...") *> 
      ZIO.sleep(5.seconds) *> // Simulating connection cleanup
      ZIO.logInfo(s"Database connection closed")
  ) { conn =>
    ZIO.logInfo(s"Running with database connection, press Ctrl+C to interrupt") *> 
    ZIO.never
  }
}
```

2. **HTTP Servers**:
```scala mdoc:compile-only
import zio._

object HttpServerApp extends ZIOAppDefault {
  override def gracefulShutdownTimeout: Duration = 60.seconds
  
  def run = ZIO.acquireReleaseWith(
    acquire = ZIO.logInfo("Starting HTTP server...").as("HTTPServer")
  )(
    release = server => 
      ZIO.logInfo("Stopping HTTP server...") *>
      ZIO.sleep(10.seconds) *> // Simulating server shutdown
      ZIO.logInfo("HTTP server stopped")
  ) { server =>
    ZIO.logInfo("Server running, press Ctrl+C to interrupt") *> 
    ZIO.never
  }
}
```

### Best Practices

1. **Timeout Duration**:
   - Set timeout based on your resource cleanup needs
   - Consider network latency for remote resources
   - Add buffer time for unexpected delays
   - Consider platform-specific shutdown behavior

2. **Resource Management**:
   - Use `ZIO.acquireReleaseWith` for proper resource handling
   - Log cleanup operations to monitor duration
   - Consider using `Scope` for resource management
   - Implement fallback cleanup mechanisms

3. **Monitoring and Debugging**:
   - Log cleanup durations
   - Monitor for timeout warnings
   - Track resource cleanup success/failure
   - Consider different timeouts for different environments

> **Note:** Graceful shutdown timeout is supported only on **JVM** and **Scala Native** platforms. It applies to the **entire shutdown process**, not individual resources, so account for total cleanup time. Once shutdown begins, it is **uninterruptible**, ensuring cleanup completes even if multiple termination signals are received.




> 🛡️ **Uninterruptible Shutdown**
> - 🔒 Once started, cannot be interrupted
> - ✅ Ensures cleanup completion
> - 🎯 Handles multiple termination signals




