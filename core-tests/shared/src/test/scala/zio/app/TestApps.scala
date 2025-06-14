package zio.app

import zio._

/**
 * Test applications for ZIOApp testing.
 * This file contains pre-compiled test applications used by ZIOAppSpec and ZIOAppProcessSpec.
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
      Console.printLine("Resource acquired").orDie
    )(_ => Console.printLine("Resource released").orDie)

    override def run = 
      Console.printLine("Starting ResourceApp") *>
      resource *> ZIO.succeed(())
  }

  /**
   * App with resource that will be interrupted
   */
  object ResourceWithNeverApp extends ZIOAppDefault {
    val resource = ZIO.acquireRelease(
      Console.printLine("Resource acquired").orDie
    )(_ => Console.printLine("Resource released").orDie)

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
      Console.printLine("Resource acquired").orDie
    )(_ => Console.printLine("Starting slow finalizer").orDie *> ZIO.sleep(2.seconds) *> Console.printLine("Resource released").orDie)

    override def run =
      Console.printLine("Starting SlowFinalizerApp") *>
      resource *> ZIO.never
  }

  /**
   * App that registers a JVM shutdown hook to ensure its execution on termination
   */
  object ShutdownHookApp extends ZIOAppDefault {
    val registerShutdownHook = ZIO.attempt {
      java.lang.Runtime.getRuntime.addShutdownHook(new Thread(() => {
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
      Console.printLine("Inner resource acquired").orDie
    )(_ => Console.printLine("Inner resource released").orDie)

    val outerResource = ZIO.acquireRelease(
      Console.printLine("Outer resource acquired").orDie *> innerResource
    )(_ => Console.printLine("Outer resource released").orDie)

    override def run =
      Console.printLine("Starting NestedFinalizersApp") *>
      outerResource *> ZIO.never
  }

  /**
   * App with both finalizers and shutdown hooks to test race conditions
   */
  object FinalizerAndHooksApp extends ZIOAppDefault {
    val registerShutdownHook = ZIO.attempt {
      java.lang.Runtime.getRuntime.addShutdownHook(new Thread(() => {
        println("JVM shutdown hook executed")
        Thread.sleep(100) // Small delay to test race conditions
      }))
    }

    val resource = ZIO.acquireRelease(
      Console.printLine("Resource acquired").orDie
    )(_ => Console.printLine("Resource released").orDie *> ZIO.sleep(100.millis))

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
  
  /**
   * ZIOAppSpec-specific test applications in a nested object to match namespace
   */
  object ziotest {
    /**
     * App that successfully returns exit code 0
     */
    object SuccessApp extends ZIOAppDefault {
      def run = ZIO.succeed(println("Success!"))
    }

    /**
     * App that fails with exit code 42
     */
    object FailingApp extends ZIOAppDefault {
      def run = ZIO.fail("Deliberate failure").mapError(_ => 42)
    }

    /**
     * App that throws an unhandled exception to test exit code 1
     */
    object ErrorApp extends ZIOAppDefault {
      def run = ZIO.attempt(throw new RuntimeException("Boom!"))
    }

    /**
     * App with finalizer to test normal completion
     */
    object FinalizerApp extends ZIOAppDefault {
      def run = {
        ZIO.acquireReleaseWith(
          ZIO.succeed(println("Resource acquired"))
        )(
          _ => ZIO.succeed(println("FINALIZER_EXECUTED"))
        )(
          _ => ZIO.succeed(println("Using resource"))
        )
      }
    }

    /**
     * App that can be interrupted
     */
    object InterruptibleApp extends ZIOAppDefault {
      def run = {
        ZIO.acquireReleaseWith(
          ZIO.succeed(println("Resource acquired"))
        )(
          _ => ZIO.succeed(println("FINALIZER_EXECUTED"))
        )(
          _ => ZIO.succeed(println("Starting infinite wait")) *> ZIO.never
        )
      }
    }

    /**
     * App with slow finalizer
     */
    object SlowFinalizerApp extends ZIOAppDefault {
      def run = {
        ZIO.acquireReleaseWith(
          ZIO.succeed(println("Resource acquired"))
        )(
          _ => ZIO.succeed(println("SLOW_FINALIZER_START")) *> 
               ZIO.sleep(5.seconds) *> 
               ZIO.succeed(println("SLOW_FINALIZER_END"))
        )(
          _ => ZIO.succeed(println("Starting infinite wait")) *> ZIO.never
        )
      }
    }
    
    /**
     * App with a finalizer that completes within the timeout
     */
    object LongFinalizerApp extends ZIOAppDefault {
      def run = {
        ZIO.acquireReleaseWith(
          ZIO.succeed(println("Resource acquired"))
        )(
          _ => ZIO.succeed(println("LONG_FINALIZER_START")) *> 
               ZIO.sleep(2.seconds) *> 
               ZIO.succeed(println("LONG_FINALIZER_END"))
        )(
          _ => ZIO.succeed(println("Starting infinite wait")) *> ZIO.never
        )
      }
    }
    
    /**
     * App with nested finalizers to test execution order
     */
    object NestedFinalizerApp extends ZIOAppDefault {
      def run = {
        ZIO.acquireReleaseWith(
          ZIO.succeed(println("Outer resource acquired"))
        )(
          _ => ZIO.succeed(println("OUTER_FINALIZER_EXECUTED"))
        )(
          _ => ZIO.acquireReleaseWith(
            ZIO.succeed(println("Inner resource acquired"))
          )(
            _ => ZIO.succeed(println("INNER_FINALIZER_EXECUTED"))
          )(
            _ => ZIO.succeed(println("Starting infinite wait")) *> ZIO.never
          )
        )
      }
    }
  }
} 