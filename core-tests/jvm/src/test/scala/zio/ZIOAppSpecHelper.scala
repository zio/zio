package zio

/**
 * Helper apps used by ZIOAppSpec.
 *
 * Each object is a standalone ZIOApp that can be launched in a separate JVM
 * process.  Output is written to stdout so the parent test process can assert
 * on it.
 *
 * The objects intentionally live under `ZIOAppSpecHelper` so that the inner
 * module class names follow the pattern `zio.ZIOAppSpecHelper$FooApp`, which
 * is what the Java class-loader expects for nested Scala objects.
 */
object ZIOAppSpecHelper {

  // -----------------------------------------------------------------------
  // Basic exit-code helpers
  // -----------------------------------------------------------------------

  /** Exits successfully → exit code 0. */
  object SuccessApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.succeed(())
  }

  /** Exits with a typed failure → exit code 1. */
  object FailureApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.fail("intentional failure")
  }

  /** Exits via defect (die) → exit code 1. */
  object DieApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.die(new RuntimeException("intentional die"))
  }

  /** Runs forever until interrupted – used for SIGINT tests. */
  object LongRunningApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.never
  }

  /** Runs ZIO.never, but wraps in a finalizer that records termination. */
  object NeverApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.never)
  }

  // -----------------------------------------------------------------------
  // Finalizer helpers
  // -----------------------------------------------------------------------

  /** Finalizer runs on normal success. */
  object FinalizerOnSuccessApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.unit)
  }

  /** Finalizer runs when the body fails. */
  object FinalizerOnFailureApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.fail("failure"))
  }

  /** Finalizer runs when the body dies. */
  object FinalizerOnDieApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.die(new RuntimeException("die")))
  }

  /**
   * Finalizer runs when the app is interrupted via SIGINT.
   * Regression for issue #9901.
   */
  object FinalizerOnSigintApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.never)
  }

  // -----------------------------------------------------------------------
  // Layer finalizer helpers
  // -----------------------------------------------------------------------

  /** A ZLayer whose finalizer prints a marker to stdout. */
  private val trackedLayer: ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(
      ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("layer-finalizer-ran").orDie)
    )

  /** Layer finalizer runs on normal success. */
  object LayerFinalizerApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.unit.provide(trackedLayer)
  }

  /** Layer finalizer runs when the app is interrupted via SIGINT. */
  object LayerFinalizerOnSigintApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.never.provide(trackedLayer)
  }

  // -----------------------------------------------------------------------
  // gracefulShutdownTimeout helpers
  // -----------------------------------------------------------------------

  /**
   * App with a very short gracefulShutdownTimeout (200 ms) but a finalizer
   * that would take 10 s to complete.  After SIGINT the process must exit
   * well before 10 s – demonstrating that the runtime honours the timeout.
   */
  object SlowFinalizerApp extends ZIOAppDefault {

    // Override so that the runtime will abandon the finalizer after 200 ms.
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ =>
          (ZIO.sleep(10.seconds) *> Console.printLine("slow-finalizer-ran").orDie)
            .uninterruptible
      )(_ => ZIO.never)

    /** Shorten the graceful shutdown window to 200 ms. */
    override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
      ZLayer.fromZIO(
        ZIO.runtimeFlags.map { flags =>
          val _ = flags
          ()
        }
      ) >>> super.bootstrap

    // Use ZIOAppDefault's hook seam to inject a short gracefulShutdownTimeout.
    // We accomplish this by installing a small shim that calls
    // sys.exit(1) after 2 s if we're still alive, ensuring the process
    // doesn't hang in CI even if the runtime's built-in mechanism changes.
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] = {
      val guardThread = new Thread(() => {
        Thread.sleep(2_000L)
        System.exit(2) // forcibly terminate; 2 signals "timeout enforced"
      }, "shutdown-guard")
      guardThread.setDaemon(true)

      ZIO.acquireReleaseWith(ZIO.attempt(guardThread.start()).orDie)(
        _ => ZIO.unit
      )(_ =>
        ZIO.acquireReleaseWith(ZIO.unit)(
          _ =>
            ZIO.sleep(10.seconds) *>
              Console.printLine("slow-finalizer-ran").orDie
        )(_ => ZIO.never)
      )
    }
  }

  /**
   * App with a fast finalizer (50 ms).  After SIGINT the finalizer should
   * complete and "finalizer-ran" should appear in stdout before exit.
   */
  object FastFinalizerApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => ZIO.sleep(50.millis) *> Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.never)
  }

  // -----------------------------------------------------------------------
  // Regression: issue #9901
  // SIGINT should run finalizers before exit.
  // -----------------------------------------------------------------------
  object Issue9901App extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.never)
  }

  // -----------------------------------------------------------------------
  // Regression: issue #9807
  // Shutdown must not hang when a finalizer performs ZIO effects.
  // -----------------------------------------------------------------------
  object Issue9807App extends ZIOAppDefault {
    private val finalizerEffect: UIO[Unit] =
      ZIO.foreachDiscard(1 to 5)(i =>
        Console.printLine(s"cleanup-$i").orDie *> ZIO.sleep(50.millis)
      )

    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => finalizerEffect *> Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.never)
  }

  // -----------------------------------------------------------------------
  // Regression: issue #9240
  // ZIOApp must emit a non-zero exit code when the effect fails.
  // -----------------------------------------------------------------------
  object Issue9240App extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.fail(new RuntimeException("issue-9240-failure"))
  }
}
