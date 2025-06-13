package zio.app

import zio._

/**
 * Test applications for ZIOApp testing.
 */
object TestApps {
  /**
   * App that completes successfully
   */
  object SuccessApp extends ZIOAppDefault {
    override def run = 
      Console.printLine("Starting SuccessApp") *>
      ZIO.succeed(())
  }

  /**
   * App that fails with an error
   */
  object FailureApp extends ZIOAppDefault {
    override def run = 
      Console.printLine("Starting FailureApp") *>
      ZIO.fail("Test Failure")
  }

  /**
   * App that runs forever
   */
  object NeverEndingApp extends ZIOAppDefault {
    override def run = 
      Console.printLine("Starting NeverEndingApp") *>
      ZIO.never
  }

  /**
   * App with resource that needs cleanup
   */
  object ResourceApp extends ZIOAppDefault {
    val resource = ZIO.acquireRelease(
      Console.printLine("Resource acquired")
    )(_ => Console.printLine("Resource released"))

    override def run = 
      Console.printLine("Starting ResourceApp") *>
      resource *> ZIO.succeed(())
  }

  /**
   * App with resource that will be interrupted
   */
  object ResourceWithNeverApp extends ZIOAppDefault {
    val resource = ZIO.acquireRelease(
      Console.printLine("Resource acquired")
    )(_ => Console.printLine("Resource released"))

    override def run =
      Console.printLine("Starting ResourceWithNeverApp") *>
      resource *> ZIO.never
  }

  /**
   * App with a specific graceful shutdown timeout
   */
  object TimeoutApp extends ZIOAppDefault {
    override def gracefulShutdownTimeout = Duration.fromMillis(500)

    override def run =
      Console.printLine("Starting TimeoutApp") *>
      Console.printLine(s"Graceful shutdown timeout: ${gracefulShutdownTimeout.render}") *>
      ZIO.never
  }

  /**
   * App with slow finalizers to test timeout behavior
   */
  object SlowFinalizerApp extends ZIOAppDefault {
    override def gracefulShutdownTimeout = Duration.fromMillis(1000)

    val resource = ZIO.acquireRelease(
      Console.printLine("Resource acquired")
    )(_ => Console.printLine("Starting slow finalizer") *> ZIO.sleep(2.seconds) *> Console.printLine("Resource released"))

    override def run =
      Console.printLine("Starting SlowFinalizerApp") *>
      resource *> ZIO.never
  }

  /**
   * App that registers a JVM shutdown hook to ensure its execution on termination
   */
  object ShutdownHookApp extends ZIOAppDefault {
    val registerShutdownHook = ZIO.attempt {
      Runtime.getRuntime.addShutdownHook(new Thread(() => {
        println("JVM shutdown hook executed")
      }))
    }

    override def run =
      Console.printLine("Starting ShutdownHookApp") *>
      registerShutdownHook *>
      ZIO.never
  }

  /**
   * App with nested finalizers to test execution order
   */
  object NestedFinalizersApp extends ZIOAppDefault {
    val innerResource = ZIO.acquireRelease(
      Console.printLine("Inner resource acquired")
    )(_ => Console.printLine("Inner resource released"))

    val outerResource = ZIO.acquireRelease(
      Console.printLine("Outer resource acquired") *> innerResource
    )(_ => Console.printLine("Outer resource released"))

    override def run =
      Console.printLine("Starting NestedFinalizersApp") *>
      outerResource *> ZIO.never
  }

  /**
   * App with both finalizers and shutdown hooks to test race conditions
   */
  object FinalizerAndHooksApp extends ZIOAppDefault {
    val registerShutdownHook = ZIO.attempt {
      Runtime.getRuntime.addShutdownHook(new Thread(() => {
        println("JVM shutdown hook executed")
        Thread.sleep(100) // Small delay to test race conditions
      }))
    }

    val resource = ZIO.acquireRelease(
      Console.printLine("Resource acquired")
    )(_ => Console.printLine("Resource released") *> ZIO.sleep(100.millis))

    override def run =
      Console.printLine("Starting FinalizerAndHooksApp") *>
      registerShutdownHook *>
      resource *>
      ZIO.never
  }

  /**
   * App that throws an exception for testing error handling
   */
  object CrashingApp extends ZIOAppDefault {
    override def run =
      Console.printLine("Starting CrashingApp") *>
      ZIO.attempt(throw new RuntimeException("Simulated crash!"))
  }
} 