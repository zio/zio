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

The `gracefulShutdownTimeout` feature in ZIO provides a configurable mechanism to control how long your application waits for resource cleanup when receiving termination signals like **SIGINT** (triggered by **Ctrl+C**). This timeout ensures your application can properly release resources while preventing indefinite hangs during shutdown. By default, it's configured to `Duration.Infinity`, allowing unlimited time for cleanup operations unless explicitly overridden.

### When to Use Graceful Shutdown Timeout

You should consider setting a timeout when:

1. Your application manages resources that need cleanup (database connections, file handles, etc.)
2. You want to ensure your application doesn't hang indefinitely during shutdown
3. You need to balance between allowing cleanup to complete and ensuring timely shutdown
4. You're running in environments where resource cleanup is critical (e.g., cloud platforms)

### Best Practices

1. **Choose Appropriate Timeout Duration**:
   - Consider the typical cleanup time of your resources
   - Add buffer time for unexpected delays
   - Account for network latency if cleanup involves remote resources
   - Consider platform-specific shutdown behavior

2. **Handle Timeout Gracefully**:
   - Log warnings when timeout occurs
   - Implement fallback cleanup mechanisms
   - Consider using `Scope` for resource management
   - Use `ZIO.acquireReleaseWith` for proper resource handling

3. **Monitor and Adjust**:
   - Log cleanup durations
   - Adjust timeout based on observed behavior
   - Consider different timeouts for different environments

### Examples

#### Example 1: Database Connection Cleanup

```scala mdoc:compile-only
import zio._
import zio.Console

object DatabaseApp extends ZIOAppDefault {
  // Wait at most 30 seconds for all finalizers to complete on SIGINT
  override def gracefulShutdownTimeout: Duration = Duration.fromSeconds(30)

  val run: ZIO[ZIOAppArgs with Scope, Any, Any] =
    // Using acquireReleaseWith to ensure proper resource cleanup
    ZIO.acquireReleaseWith(
      // Acquire: Establish database connection
      acquire = ZIO.logInfo("Connecting to database...") *> 
                ZIO.sleep(1.second) *> // Simulating connection time
                ZIO.succeed("DBConnection")
    )(
      // Release: Finalizer that runs during shutdown
      release = conn => 
        ZIO.logInfo(s"Closing database connection (5s) ...") *> 
        ZIO.sleep(5.seconds) *> // Simulating cleanup time
        ZIO.logInfo(s"Database connection $conn closed successfully")
    ) { conn =>
      // Use: Main application logic
      ZIO.logInfo(s"Running with database connection $conn, press Ctrl+C to interrupt") *> 
      ZIO.never
    }
}
```

In this example, we simulate a database application that:
1. Takes 1 second to establish a connection
2. Has a finalizer that takes 5 seconds to properly close the connection
3. Has a 30-second timeout for cleanup
4. Logs each step of the process

The cleanup will complete successfully within the timeout, ensuring proper database connection closure.

#### Example 2: File System Operations with Timeout Breach

```scala mdoc:compile-only
import zio._
import zio.Console

object FileSystemApp extends ZIOAppDefault {
  // Wait at most 5 seconds for all finalizers to complete on SIGINT
  override def gracefulShutdownTimeout: Duration = Duration.fromSeconds(5)

  val run: ZIO[ZIOAppArgs with Scope, Any, Any] =
    // Using acquireReleaseWith to ensure proper resource cleanup
    ZIO.acquireReleaseWith(
      // Acquire: Open file
      acquire = ZIO.logInfo("Opening large file...") *> 
                ZIO.sleep(1.second) *> // Simulating file open
                ZIO.succeed("LargeFile")
    )(
      // Release: Finalizer that runs during shutdown
      // This finalizer will be interrupted after 5 seconds
      release = file => 
        ZIO.logInfo(s"Flushing and closing file (10s) ...") *> 
        ZIO.sleep(10.seconds) *> // Simulating file flush and close
        ZIO.logInfo(s"File $file closed successfully")
    ) { file =>
      // Use: Main application logic
      ZIO.logInfo(s"Processing file $file, press Ctrl+C to interrupt") *> 
      ZIO.never
    }
}
```

This example demonstrates a file system application that:
1. Opens a large file
2. Has a finalizer that needs 10 seconds to properly flush and close the file
3. Has only a 5-second timeout for cleanup
4. Will be forcefully terminated if cleanup takes too long

When you press Ctrl+C, you'll see:
```bash
**** WARNING ****
Timed out waiting for ZIO application to shut down after 5 seconds. You can adjust your application's shutdown timeout by overriding the `shutdownTimeout` method
```

The finalizer will be interrupted after 5 seconds, potentially leaving the file in an inconsistent state.

#### Example 3: Multiple Resource Cleanup

```scala mdoc:compile-only
import zio._
import zio.Console

object MultiResourceApp extends ZIOAppDefault {
  // Wait at most 15 seconds for all finalizers to complete on SIGINT
  override def gracefulShutdownTimeout: Duration = Duration.fromSeconds(15)

  val run: ZIO[ZIOAppArgs with Scope, Any, Any] = ZIO.scoped {
    for {
      db <- ZIO.acquireRelease(
              ZIO.logInfo("Connecting to database...") *> 
              ZIO.sleep(1.second) *> 
              ZIO.succeed("DBConnection")
            )(conn =>
              ZIO.logInfo(s"Closing database connection (3s) ...") *> 
              ZIO.sleep(3.seconds) *> 
              ZIO.logInfo(s"Database connection $conn closed successfully")
            )
      file <- ZIO.acquireRelease(
                ZIO.logInfo("Opening file...") *> 
                ZIO.sleep(1.second) *> 
                ZIO.succeed("FileHandle")
              )(file =>
                ZIO.logInfo(s"Closing file (5s) ...") *> 
                ZIO.sleep(5.seconds) *> 
                ZIO.logInfo(s"File $file closed successfully")
              )
      _ <- ZIO.logInfo(s"Running with database $db and file $file, press Ctrl+C to interrupt") *> 
           ZIO.never
    } yield ()
  }
}
```

This example demonstrates:
1. Managing multiple resources (database and file)
2. Each resource has its own cleanup time (3s and 5s)
3. Total cleanup time is 8 seconds (well within the 15s timeout)
4. Proper resource acquisition and release using `ZIO.acquireRelease`
5. Clean shutdown with all resources properly released

### Platform-Specific Behavior

:::note
The graceful shutdown timeout behavior only applies on **JVM** and **Scala Native** platforms. Other platforms like **Scala.js** do not invoke the shutdown hook on external signals.
:::
