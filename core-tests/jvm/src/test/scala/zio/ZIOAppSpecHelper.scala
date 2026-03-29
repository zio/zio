package zio

import zio.duration2DurationOps

import scala.concurrent.duration._

/**
 * Helper applications used as subprocess entry-points by [[ZIOAppSpec]].
 *
 * Each object is a self-contained [[ZIOAppDefault]] whose main class can be
 * launched in a separate JVM process so that the spec can observe exit codes,
 * stdout output, and shutdown timing without interfering with the test JVM.
 */
object ZIOAppSpecHelper {

  /** Completes immediately with success. */
  object SuccessApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.unit
  }

  /** Fails immediately, causing a non-zero exit code. */
  object FailureApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.fail("boom")
  }

  /** Prints a message from its finalizer so the spec can confirm finalizers run. */
  object FinalizerApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.addFinalizer(ZIO.succeed(println("finalizer ran"))).as(())
  }

  /** Registers a finalizer and then self-interrupts, verifying finalizers run on
    * interruption too.
    */
  object InterruptedFinalizerApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.addFinalizer(ZIO.succeed(println("finalizer ran"))) *> ZIO.interrupt
  }

  /**
   * App with a finalizer that sleeps much longer than the app's
   * `gracefulShutdownTimeout`.  ZIO should force-terminate the finalizer once
   * the timeout elapses rather than blocking indefinitely.
   *
   * The app overrides [[gracefulShutdownTimeout]] to 2 seconds while the
   * finalizer sleeps for 30 seconds.  The spec asserts that the process exits
   * well before 30 seconds, proving that the timeout mechanism works.
   *
   * Note: we do NOT call `System.exit` from inside the finalizer – that would
   * bypass ZIO's shutdown logic and make the test vacuous.  Instead we rely on
   * ZIO's own `gracefulShutdownTimeout` to abort the slow finalizer.
   */
  object SlowFinalizerApp extends ZIOAppDefault {

    override def gracefulShutdownTimeout: Duration = 2.seconds

    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO
        .addFinalizer(
          ZIO.succeed(println("finalizer started")) *>
            ZIO.sleep(30.seconds) *>
            ZIO.succeed(println("finalizer finished"))
        )
        .as(())
  }
}
