package zio

/**
 * Helper ZIOApp definitions used as subprocess entry-points by ZIOAppSpec.
 *
 * Each object extends ZIOAppDefault and is run as a separate JVM process so
 * that `sys.exit` calls and OS-level signal handlers work correctly.
 *
 * Conventions:
 *  - Print "app-started"         once the main body is ready (so the test can send SIGINT).
 *  - Print "finalizer-ran"       from resource/scope finalizers.
 *  - Print "resource-acquired"   when a scoped resource is acquired.
 *  - Print "resource-released"   when that resource's finalizer runs.
 *  - Print "blocking-started"    just before entering a blocking op.
 */
object ZIOAppSpecHelpers {

  // ── Exit-code helpers ────────────────────────────────────────────────────────

  /** Exits 0 after a trivial success. */
  object SuccessApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      Console.printLine("success")
  }

  /** Fails with a typed error – ZIOApp should exit with code 1. */
  object FailureApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.fail("intentional-failure")
  }

  /** Dies with an exception – ZIOApp should exit with code 1. */
  object DefectApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.die(new RuntimeException("intentional-defect"))
  }

  // ── Finalizer helpers ────────────────────────────────────────────────────────

  /** Finalizer must run when the app succeeds. */
  object FinalizerOnSuccessApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("finalizer-ran").orDie)
        .flatMap(_ => Console.printLine("main-done"))
  }

  /** Finalizer must run even when the app fails. */
  object FinalizerOnFailureApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("finalizer-ran").orDie)
        .flatMap(_ => ZIO.fail("failure-after-acquire"))
  }

  /** Finalizer must run even when the app produces a defect. */
  object FinalizerOnDefectApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("finalizer-ran").orDie)
        .flatMap(_ => ZIO.die(new RuntimeException("defect-after-acquire")))
  }

  // ── Signal / SIGINT helpers ──────────────────────────────────────────────────

  /**
   * Long-running app.
   * Prints "app-started" so the test knows it's safe to send SIGINT, then
   * sleeps 60 s.  A finalizer prints "finalizer-ran".
   * Regression: #9807 – finalizers not called on SIGINT.
   */
  object LongRunningApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireRelease(
        Console.printLine("app-started").orDie
      )(_ => Console.printLine("finalizer-ran").orDie)
        .flatMap(_ => ZIO.sleep(Duration.fromSeconds(60)))
  }

  /**
   * Acquires a scoped resource, prints "resource-acquired", then sleeps.
   * On any interruption the finalizer prints "resource-released".
   */
  object ScopedResourceApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.scoped {
        ZIO.acquireRelease(
          Console.printLine("resource-acquired").orDie
        )(_ => Console.printLine("resource-released").orDie)
          .flatMap(_ => ZIO.sleep(Duration.fromSeconds(60)))
      }
  }

  // ── Non-hanging helpers (#9901) ───────────────────────────────────────────────

  /**
   * Executes a blocking Thread.sleep inside ZIO.attemptBlockingInterrupt.
   * Prints "blocking-started" so the test can send SIGINT right after.
   * Regression: #9901 – ZIOApp used to hang in this scenario.
   */
  object BlockingOpApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireRelease(
        Console.printLine("blocking-started").orDie
      )(_ => Console.printLine("blocking-finalizer-ran").orDie)
        .flatMap { _ =>
          ZIO.attemptBlockingInterrupt(Thread.sleep(60_000)).ignore
        }
  }

  /** Completes immediately – verifies the JVM exits without hanging. */
  object QuickApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      Console.printLine("quick-done")
  }

  // ── gracefulShutdownTimeout helpers (#9240) ───────────────────────────────────

  /**
   * The finalizer sleeps for 60 s, but the app is expected to be invoked with
   * a 2-second gracefulShutdownTimeout via a system property, so it should be
   * cut off well before the 60 s complete.
   * Regression: #9240.
   */
  object SlowFinalizerApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireRelease(
        Console.printLine("app-started").orDie
      ) { _ =>
        Console.printLine("slow-finalizer-started").orDie *>
          ZIO.sleep(Duration.fromSeconds(60)) *>
          Console.printLine("slow-finalizer-done").orDie
      }.flatMap(_ => ZIO.sleep(Duration.fromSeconds(60)))
  }

  /**
   * The finalizer sleeps only 500 ms – with a 5 s graceful timeout it should
   * always complete, printing "fast-finalizer-ran".
   */
  object FastFinalizerWithTimeoutApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireRelease(
        Console.printLine("app-started").orDie
      ) { _ =>
        ZIO.sleep(Duration.fromMillis(500)) *>
          Console.printLine("fast-finalizer-ran").orDie
      }.flatMap(_ => ZIO.sleep(Duration.fromSeconds(60)))
  }

  // ── ZIOApp composition ────────────────────────────────────────────────────────

  /**
   * First component app used in the composition test.
   */
  object App1 extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireRelease(
        Console.printLine("app1-started").orDie
      )(_ => Console.printLine("app1-finalizer-ran").orDie)
        .flatMap(_ => Console.printLine("app1-done"))
  }

  /**
   * Second component app used in the composition test.
   */
  object App2 extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.acquireRelease(
        Console.printLine("app2-started").orDie
      )(_ => Console.printLine("app2-finalizer-ran").orDie)
        .flatMap(_ => Console.printLine("app2-done"))
  }

  /**
   * Composed app: runs App1 then App2 sequentially via `<>` operator.
   * Both finalizers should be printed.
   */
  object ComposedAppsApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      (App1.run *> App2.run)
  }
}
