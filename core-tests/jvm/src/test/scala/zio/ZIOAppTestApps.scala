package zio

/**
 * Standalone ZIOApp programs used by [[ZIOAppIntegrationSpec]].
 *
 * Each object is compiled as part of the test sources and launched
 * in a separate JVM process via [[ProcessBuilder]].  The parent
 * test communicates with the child through stdout markers.
 *
 * Naming convention:
 *   - App*           – test fixture
 *   - "READY"        – child is waiting and can be interrupted
 *   - "FINALIZED"    – a finalizer has completed
 *   - "ACQUIRED"     – a resource has been acquired
 */

// ---------------------------------------------------------------------------
// Normal completion
// ---------------------------------------------------------------------------

/** Succeeds immediately. Expected exit code: 0. */
object AppSuccess extends ZIOAppDefault {
  val run: ZIO[Any, Nothing, Unit] = Console.printLine("SUCCESS").orDie
}

/** Fails immediately. Expected exit code: 1. */
object AppFailure extends ZIOAppDefault {
  val run: ZIO[Any, String, Nothing] = ZIO.fail("boom")
}

/** Dies with an unchecked defect. Expected exit code: 1. */
object AppDie extends ZIOAppDefault {
  val run: ZIO[Any, Nothing, Nothing] = ZIO.die(new RuntimeException("defect"))
}

/** Does some work, then succeeds. Expected exit code: 0. */
object AppExitAfterWork extends ZIOAppDefault {
  val run: ZIO[Any, Nothing, Unit] = for {
    _ <- Console.printLine("WORKING").orDie
    _ <- ZIO.sleep(100.millis)
    _ <- Console.printLine("DONE").orDie
  } yield ()
}

// ---------------------------------------------------------------------------
// Finalizers on normal completion
// ---------------------------------------------------------------------------

/** Acquires a resource with a finalizer, then succeeds. */
object AppFinalizerOnSuccess extends ZIOAppDefault {
  val run: ZIO[Scope, Nothing, Unit] =
    ZIO.acquireRelease(Console.printLine("ACQUIRED").orDie)(_ => Console.printLine("FINALIZED").orDie)
      .unit
}

/** Acquires a resource with a finalizer, then fails. */
object AppFinalizerOnFailure extends ZIOAppDefault {
  val run: ZIO[Scope, String, Nothing] =
    ZIO.acquireRelease(Console.printLine("ACQUIRED").orDie)(_ => Console.printLine("FINALIZED").orDie) *>
      ZIO.fail("boom")
}

// ---------------------------------------------------------------------------
// Signal handling (SIGINT / SIGTERM)
// ---------------------------------------------------------------------------

/** Waits forever after printing "READY"; finalizer prints "FINALIZED". */
object AppHangsUntilInterrupted extends ZIOAppDefault {
  val run: ZIO[Scope, Nothing, Nothing] =
    ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("FINALIZED").orDie) *>
      Console.printLine("READY").orDie *>
      ZIO.never
}

/** Same as above but the finalizer takes 3 seconds to complete. */
object AppSlowFinalizer extends ZIOAppDefault {
  val run: ZIO[Scope, Nothing, Nothing] =
    ZIO.acquireRelease(ZIO.unit)(_ =>
      Console.printLine("FINALIZING").orDie *>
        ZIO.sleep(3.seconds) *>
        Console.printLine("FINALIZED").orDie
    ) *>
      Console.printLine("READY").orDie *>
      ZIO.never
}

/**
 * Three nested [[acquireRelease]] blocks. Finalizers should execute
 * in LIFO order: FINAL-3, FINAL-2, FINAL-1.
 */
object AppMultipleFinalizers extends ZIOAppDefault {
  val run: ZIO[Scope, Nothing, Nothing] =
    ZIO.acquireRelease(Console.printLine("ACQ-1").orDie)(_ => Console.printLine("FINAL-1").orDie) *>
      ZIO.acquireRelease(Console.printLine("ACQ-2").orDie)(_ => Console.printLine("FINAL-2").orDie) *>
      ZIO.acquireRelease(Console.printLine("ACQ-3").orDie)(_ => Console.printLine("FINAL-3").orDie) *>
      Console.printLine("READY").orDie *>
      ZIO.never
}

// ---------------------------------------------------------------------------
// Bootstrap layer
// ---------------------------------------------------------------------------

/** Bootstrap layer owns a resource; its finalizer must run on shutdown. */
object AppBootstrapFinalizer extends ZIOApp {
  type Environment = Unit
  val environmentTag: EnvironmentTag[Unit] = EnvironmentTag[Unit]

  val bootstrap: ZLayer[ZIOAppArgs, Any, Unit] =
    ZLayer.scoped(
      ZIO.acquireRelease(Console.printLine("BOOTSTRAP-ACQUIRED").orDie)(_ =>
        Console.printLine("BOOTSTRAP-FINALIZED").orDie
      )
    )

  val run: ZIO[Scope, Nothing, Nothing] =
    Console.printLine("READY").orDie *> ZIO.never
}

// ---------------------------------------------------------------------------
// gracefulShutdownTimeout
// ---------------------------------------------------------------------------

/**
 * Overrides [[gracefulShutdownTimeout]] to 1 second while the finalizer
 * needs 10 seconds.  The JVM should exit after ~1 s, the "FINALIZED"
 * marker should *not* appear.
 */
object AppShutdownTimeout extends ZIOAppDefault {
  override def gracefulShutdownTimeout: Duration = 1.second

  val run: ZIO[Scope, Nothing, Nothing] =
    ZIO.acquireRelease(ZIO.unit)(_ =>
      Console.printLine("FINALIZING").orDie *>
        ZIO.sleep(10.seconds) *>
        Console.printLine("FINALIZED").orDie
    ) *>
      Console.printLine("READY").orDie *>
      ZIO.never
}

// ---------------------------------------------------------------------------
// Background fibers
// ---------------------------------------------------------------------------

/** Spawns a daemon fiber whose finalizer should run on shutdown. */
object AppBackgroundFiber extends ZIOAppDefault {
  val run = for {
    _ <- ZIO.never.ensuring(Console.printLine("BG-FINALIZED").orDie).forkDaemon
    _ <- Console.printLine("READY").orDie
    _ <- ZIO.never
  } yield ()
}
