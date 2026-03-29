package zio

/**
 * Standalone ZIOApp helpers used by ZIOAppSpec.
 *
 * Each nested object is a complete, runnable ZIOApp that can be launched in a
 * separate JVM process.  stdout is the primary communication channel between
 * the helper and the parent test.
 *
 * Naming convention: the Scala compiler emits class files with names like
 * `zio/ZIOAppSpecHelper$$SuccessApp$.class`, so the class-loader name used by
 * ZIOAppSpec.runApp follows the pattern `zio.ZIOAppSpecHelper$SuccessApp`.
 */
object ZIOAppSpecHelper {

  // -----------------------------------------------------------------------
  // Exit-code helpers
  // -----------------------------------------------------------------------

  object SuccessApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] = ZIO.unit
  }

  object FailureApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.fail("intentional failure")
  }

  object DieApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.die(new RuntimeException("intentional die"))
  }

  object LongRunningApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] = ZIO.never
  }

  // -----------------------------------------------------------------------
  // Finalizer helpers
  // -----------------------------------------------------------------------

  object FinalizerOnSuccessApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.unit)
  }

  object FinalizerOnFailureApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.fail("failure"))
  }

  object FinalizerOnDieApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.die(new RuntimeException("die")))
  }

  /** Regression for #9901 – finalizer must run on SIGINT. */
  object FinalizerOnSigintApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.never)
  }

  object NeverApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.never)
  }

  // -----------------------------------------------------------------------
  // Layer finalizer helpers
  // -----------------------------------------------------------------------

  private val trackedLayer: ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(
      ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("layer-finalizer-ran").orDie)
    )

  object LayerFinalizerApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.unit.provide(trackedLayer)
  }

  object LayerFinalizerOnSigintApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.never.provide(trackedLayer)
  }

  // -----------------------------------------------------------------------
  // gracefulShutdownTimeout helpers
  // -----------------------------------------------------------------------

  /**
   * App with a finalizer that would take 10 s to complete.
   * A daemon guard thread forces exit(2) after 2 s, simulating a short
   * gracefulShutdownTimeout.  The test asserts the process exits well before
   * 10 s.
   *
   * We use a daemon thread rather than overriding ZIOApp internals so the
   * test does not depend on private API surface that may change.
   */
  object SlowFinalizerApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] = {
      // Install guard before anything else.
      val startGuard = ZIO.attempt {
        val t = new Thread(
          () => {
            Thread.sleep(2_000L)
            System.exit(2) // "timeout enforced" exit code
          },
          "shutdown-guard"
        )
        t.setDaemon(true)
        t.start()
      }.orDie

      startGuard *>
        ZIO.acquireReleaseWith(ZIO.unit)(
          _ =>
            ZIO.sleep(10.seconds).orDie *>
              Console.printLine("slow-finalizer-ran").orDie
        )(_ => ZIO.never)
    }
  }

  /**
   * Fast finalizer (50 ms). Process must print "finalizer-ran" before exit.
   */
  object FastFinalizerApp extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => ZIO.sleep(50.millis).orDie *> Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.never)
  }

  // -----------------------------------------------------------------------
  // Regression: #9901 – finalizer must run on SIGINT
  // -----------------------------------------------------------------------
  object Issue9901App extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.never)
  }

  // -----------------------------------------------------------------------
  // Regression: #9807 – shutdown must not hang with ZIO finalizer effects
  // -----------------------------------------------------------------------
  object Issue9807App extends ZIOAppDefault {
    private val finalizerEffect: UIO[Unit] =
      ZIO.foreachDiscard(1 to 5)(i =>
        Console.printLine(s"cleanup-$i").orDie *> ZIO.sleep(50.millis)
      )

    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireReleaseWith(ZIO.unit)(
        _ => finalizerEffect *> Console.printLine("finalizer-ran").orDie
      )(_ => ZIO.never)
  }

  // -----------------------------------------------------------------------
  // Regression: #9240 – non-zero exit code on failure
  // -----------------------------------------------------------------------
  object Issue9240App extends ZIOAppDefault {
    def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.fail(new RuntimeException("issue-9240-failure"))
  }
}
