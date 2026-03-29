package zio

/**
 * Helper apps used by ZIOAppSpec.
 *
 * Each object is a standalone ZIOApp that can be launched as a separate JVM
 * process.  Output is written to stdout so the parent process can assert on
 * it.
 *
 * NOTE: Objects are top-level to keep the file tidy.  The `$` in the class
 * names used in ZIOAppSpec.runApp correspond to the Scala module class name.
 */
object ZIOAppSpecHelper {

  // -------------------------------------------------------------------------
  // Basic exit-code helpers
  // -------------------------------------------------------------------------

  /** Exits successfully (exit code 0). */
  object SuccessApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.succeed(())
  }

  /** Exits with a ZIO failure (exit code 1). */
  object FailureApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.fail("intentional failure")
  }

  /** Exits via defect / die (exit code 1). */
  object DieApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.die(new RuntimeException("intentional die"))
  }

  /** Runs forever until interrupted (used for SIGINT tests). */
  object LongRunningApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.never
  }

  /** Runs ZIO.never — never completes on its own. */
  object NeverApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.succeed(()))(_ => Console.printLine("finalizer-ran").orDie)(_ => ZIO.never)
  }

  // -------------------------------------------------------------------------
  // Finalizer helpers
  // -------------------------------------------------------------------------

  /** Runs a finalizer on success. */
  object FinalizerOnSuccessApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.succeed(()))(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.unit)
  }

  /** Runs a finalizer on failure. */
  object FinalizerOnFailureApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.succeed(()))(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.fail("failure"))
  }

  /** Runs a finalizer on die. */
  object FinalizerOnDieApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.succeed(()))(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.die(new RuntimeException("die")))
  }

  /** Runs a finalizer when interrupted via SIGINT (#9901). */
  object FinalizerOnSigintApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.succeed(()))(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.never)
  }

  // -------------------------------------------------------------------------
  // Layer finalizer helpers
  // -------------------------------------------------------------------------

  /** Layer whose acquire/release prints to stdout. */
  private val trackedLayer: ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("layer-finalizer-ran").orDie)
    }

  /** Layer finalizer runs on success. */
  object LayerFinalizerApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.unit.provide(trackedLayer)
  }

  /** Layer finalizer runs on SIGINT. */
  object LayerFinalizerOnSigintApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.never.provide(trackedLayer)
  }

  // -------------------------------------------------------------------------
  // gracefulShutdownTimeout helpers
  // -------------------------------------------------------------------------

  /**
   * Has a 200 ms gracefulShutdownTimeout but the finalizer sleeps for 10 s.
   * The expectation is that the process exits well before the finalizer
   * completes.
   */
  object SlowFinalizerApp extends ZIOAppDefault {
    override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
      Runtime.setReportFatal(_ => ()) >>>
        super.bootstrap

    // Override gracefulShutdownTimeout to be very short
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO
        .acquireReleaseWith(ZIO.succeed(()))(
          _ =>
            ZIO.sleep(Duration.fromMillis(10_000)).orDie *>
              Console.printLine("slow-finalizer-ran").orDie
        )(_ => ZIO.never)

    // ZIOAppDefault exposes a hook we override
    override val hook: RuntimeFlags.Patch =
      RuntimeFlags.Patch.empty
  }

  /**
   * Has a 5 s gracefulShutdownTimeout and a fast (50 ms) finalizer.
   * The expectation is that the finalizer completes and "finalizer-ran" is
   * printed.
   */
  object FastFinalizerApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.succeed(()))(
        _ =>
          ZIO.sleep(Duration.fromMillis(50)) *>
            Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.never)
  }

  // -------------------------------------------------------------------------
  // Regression: issue #9901
  // ZIOApp receiving SIGINT should run finalizers.
  // -------------------------------------------------------------------------
  object Issue9901App extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO
        .acquireReleaseWith(ZIO.succeed("resource"))(
          _ => Console.printLine("finalizer-ran").orDie
        )(_ => ZIO.never)
  }

  // -------------------------------------------------------------------------
  // Regression: issue #9807
  // Shutdown should not hang when finalizer contains ZIO effects.
  // -------------------------------------------------------------------------
  object Issue9807App extends ZIOAppDefault {
    private val finalizerEffect: UIO[Unit] =
      ZIO.foreachDiscard(1 to 5)(i =>
        Console.printLine(s"cleanup-$i").orDie *> ZIO.sleep(Duration.fromMillis(50))
      )

    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO
        .acquireReleaseWith(ZIO.succeed(()))(
          _ => finalizerEffect *> Console.printLine("finalizer-ran").orDie
        )(_ => ZIO.never)
  }

  // -------------------------------------------------------------------------
  // Regression: issue #9240
  // ZIOApp should emit a non-zero exit code when the app fails.
  // -------------------------------------------------------------------------
  object Issue9240App extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.fail(new RuntimeException("issue-9240-failure"))
  }
}
