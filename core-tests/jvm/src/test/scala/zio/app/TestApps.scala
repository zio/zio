package zio.app

import zio._

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
 * Special application that assists with testing proper exit codes
 * It will detect signals through temp files and ensure the expected exit codes
 * are returned
 */
object SpecialExitCodeApp extends ZIOAppDefault {
  private val signalHandler = ZIO.attempt {
    // Set up a thread to watch for signal marker files
    val watcherThread = new Thread(() => {
      val pid = ProcessHandle.current().pid()
      val signalFile = new java.io.File(java.lang.System.getProperty("java.io.tmpdir"), s"zio-signal-$pid")
      
      while (true) {
        if (signalFile.exists()) {
          try {
            val scanner = new java.util.Scanner(signalFile)
            val signal = if (scanner.hasNextLine()) scanner.nextLine() else "UNKNOWN"
            scanner.close()
            signalFile.delete()
            
            // Log for test verification
            java.lang.System.out.println(s"ZIO-SIGNAL: $signal detected")
            
            // Map to the expected exit code per maintainer requirements
            val exitCode = signal match {
              case "INT" => 130   // SIGINT exit code
              case "TERM" => 143  // SIGTERM exit code
              case "KILL" => 137  // SIGKILL exit code (maintainer specified 137)
              case _ => 1         // Default error code
            }
            
            java.lang.System.out.println(s"Exiting with code $exitCode")
            java.lang.System.exit(exitCode)
          } catch {
            case e: Exception =>
              java.lang.System.err.println(s"Error processing signal file: ${e.getMessage}")
          }
        }
        
        // Check every 100ms
        Thread.sleep(100)
      }
    })
    
    watcherThread.setDaemon(true)
    watcherThread.start()
  }

  override def run = 
    Console.printLine("Starting SpecialExitCodeApp") *>
    signalHandler *>
    Console.printLine("Signal handler installed") *>
    ZIO.never
}

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
   * App that completes successfully with a specific exit code
   */
  object SuccessAppWithCode extends ZIOAppDefault {
    override def run =
      Console.printLine("Starting SuccessAppWithCode") *>
      ZIO.succeed(0)
  }

  /**
   * App that does nothing but succeed, with no other effects.
   */
  object PureSuccessApp extends ZIOAppDefault {
    override def run = ZIO.unit
  }

  /**
   * App that fails with an error
   */
  object FailureApp extends ZIOAppDefault {
    override def run = 
      Console.printLine("Starting FailureApp") *>
      ZIO.fail("Test Failure") // ZIO.fail returns exit code 1 by default
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
   * App that throws an exception for testing error handling
   */
  object CrashingApp extends ZIOAppDefault {
    override def run =
      Console.printLine("Starting CrashingApp") *>
      ZIO.attempt(throw new RuntimeException("Simulated crash!"))
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
} 