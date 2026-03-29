package zio

/**
 * Helper ZIOApp definitions used as subprocess entry-points by ZIOAppSpec.
 *
 * Each object extends ZIOAppDefault (or ZIOApp) and is designed to exercise
 * a specific behaviour.  They are run as separate JVM processes from the test
 * suite so that `sys.exit` calls and signal handlers work correctly.
 */
object ZIOAppSpecHelpers {

  // ---------------------------------------------------------------------------
  // Exit-code helpers
  // ---------------------------------------------------------------------------

  /** Exits 0 after printing a success message. */
  object SuccessApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.succeed(println("success"))
  }

  /** Fails with a string error – should produce exit code 1. */
  object FailureApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.fail("intentional failure")
  }

  /** Dies with an exception – should produce exit code 1. */
  object DefectApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.die(new RuntimeException("intentional defect"))
  }

  // ---------------------------------------------------------------------------
  // Finalizer helpers
  // ---------------------------------------------------------------------------

  /** Runs a finalizer on success and prints a marker. */
  object FinalizerOnSuccessApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireRelease(ZIO.succeed("resource"))(_ => ZIO.succeed(println("finalizer-ran")))
        .flatMap(_ => ZIO.succeed(println("main-done")))
  }

  /** Runs a finalizer even when the effect fails. */
  object FinalizerOnFailureApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO
        .acquireRelease(ZIO.succeed("resource"))(_ => ZIO.succeed(println("finalizer-ran")))
        .flatMap(_ => ZIO.fail("failure after acquire"))
  }

  /** Runs a finalizer even when the effect dies. */
  object FinalizerOnDefectApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO
        .acquireRelease(ZIO.succeed("resource"))(_ => ZIO.succeed(println("finalizer-ran")))
        .flatMap(_ => ZIO.die(new RuntimeException("defect after acquire")))
  }

  // ---------------------------------------------------------------------------
  // Signal / SIGINT helpers
  // ---------------------------------------------------------------------------

  /**
   * A long-running app that prints "app-started" so the test process knows it's
   * safe to send SIGINT, then sleeps for 60 seconds.  A finalizer prints
   * "finalizer-ran".  Regression: #9807.
   */
  object LongRunningApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO
        .acquireRelease(ZIO.succeed(println("app-started")))(_ => ZIO.succeed(println("finalizer-ran")))
        .flatMap(_ => ZIO.sleep(Duration.fromSeconds(60)))
  }

  /**
   * Acquires a scoped resource, prints "resource-acquired", then sleeps.
   * On any interruption the finalizer prints "resource-released".
   */
  object ScopedResourceApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.scoped {
        ZIO
          .acquireRelease(
            ZIO.succeed(println("resource-acquired"))
          )(_ => ZIO.succeed(println("resource-released")))
          .flatMap(_ => ZIO.sleep(Duration.fromSeconds(60)))
      }
  }

  // ---------------------------------------------------------------------------
  // Non-hanging helpers  (#9901)
  // ---------------------------------------------------------------------------

  /**
   * Executes a blocking operation (Thread.sleep) inside ZIO.attemptBlocking.
   * Prints "blocking-started" so the test can send SIGINT right after.
   * Regression: #9901 – ZIOApp used to hang in this scenario.
   */
  object BlockingOpApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO
        .acquireRelease(ZIO.succeed(println("blocking-started")))(_ => ZIO.succeed(println("blocking-finalizer-ran")))
        .flatMap { _ =>
          ZIO.attemptBlockingInterrupt {
            Thread.sleep(60_000)
          }.orDie
        }
  }

  /** Completes immediately so we can verify no hang on normal exit. */
  object QuickApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.succeed(println("quick-done"))
  }

  // ---------------------------------------------------------------------------
  // gracefulShutdownTimeout helpers  (#9240)
  // ---------------------------------------------------------------------------

  /**
   * Overrides gracefulShutdownTimeout to 2 seconds.  The finalizer sleeps for
   * 60 seconds – the timeout should cut it off well before that.
   * Regression: #9240.
   */
  object SlowFinalizerApp extends ZIOAppDefault {
    override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
      Runtime.setConfigProvider(ConfigProvider.fromMap(Map.empty)) >>>
        Runtime.addShutdownHook(ZIO.unit)

    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO
        .acquireRelease(ZIO.succeed(println("app-started"))) { _ =>
          ZIO.succeed(println("slow-finalizer-started")) *>
            ZIO.sleep(Duration.fromSeconds(60)) *>
            ZIO.succeed(println("slow-finalizer-done"))
        }
        .flatMap(_ => ZIO.sleep(Duration.fromSeconds(60)))
  }

  /**
   * Overrides gracefulShutdownTimeout to 5 seconds.  The finalizer sleeps only
   * 500 ms – it should always complete within the timeout.
   */
  object FastFinalizerWithTimeoutApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO
        .acquireRelease(ZIO.succeed(println("app-started"))) { _ =>
          ZIO.sleep(Duration.fromMillis(500)) *>
            ZIO.succeed(println("fast-finalizer-ran"))
        }
        .flatMap(_ => ZIO.sleep(Duration.fromSeconds(60)))
  }

  // ---------------------------------------------------------------------------
  // Custom exit code
  // ---------------------------------------------------------------------------

  /**
   * Uses a custom exit code of 42 by overriding `exitCode`.
   */
  object CustomExitCodeApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.succeed(42)

    override def exitCode: Exit[Any, Any] => UIO[ExitCode] =
      _ => ZIO.succeed(ExitCode(42))
  }

  // ---------------------------------------------------------------------------
  // ZIOApp composition
  // ---------------------------------------------------------------------------

  /** First component of a composed app. */
  object App1 extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO
        .acquireRelease(ZIO.succeed(println("app1-started")))(_ => ZIO.succeed(println("app1-finalizer-ran")))
        .flatMap(_ => ZIO.succeed(println("app1-done")))
  }

  /** Second component of a composed app. */
  object App2 extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO
        .acquireRelease(ZIO.succeed(println("app2-started")))(_ => ZIO.succeed(println("app2-finalizer-ran")))
        .flatMap(_ => ZIO.succeed(println("app2-done")))
  }

  /**
   * Composed app: both App1 and App2 run sequentially.  Their finalizers should
   * both be printed.
   */
  object ComposedAppsApp extends ZIOApp {
    override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = ZLayer.empty

    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      (App1.run <* App2.run).provide(ZLayer.empty[ZIOAppArgs], Scope.default)
  }
}
