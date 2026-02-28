package zio

import zio.test._

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object ZIOAppMainSpec extends ZIOBaseSpec {

  def spec = suite("ZIOAppMainSpec")(
    test("simulated shutdown signal interrupts app and runs finalizers") {
      val harness = new MainHarness(Duration.Infinity, ZIO.unit)

      for {
        _ <- ZIO.attempt(harness.start())
        _ <- awaitLatch(harness.started, "app start")
        hook <- awaitHook(harness)
        _ <- ZIO.attemptBlocking(hook())
        _ <- awaitLatch(harness.finalized, "finalizer completion")
        _ <- awaitLatch(harness.mainDone, "main completion")
      } yield assertTrue(harness.exitCode.get() == ExitCode.failure)
    },
    test("gracefulShutdownTimeout bounds shutdown hook wait time") {
      val finalizerDelay = 800.millis
      val harness = new MainHarness(50.millis, ZIO.uninterruptible(ZIO.sleep(finalizerDelay)))

      for {
        _ <- ZIO.attempt(harness.start())
        _ <- awaitLatch(harness.started, "app start")
        hook <- awaitHook(harness)
        elapsedMs <- ZIO.attemptBlocking {
                       val startedAt = java.lang.System.nanoTime()
                       hook()
                       (java.lang.System.nanoTime() - startedAt) / 1000000L
                     }
        _ <- awaitLatch(harness.finalized, "finalizer completion")
        _ <- awaitLatch(harness.mainDone, "main completion")
      } yield assertTrue(elapsedMs < 400L) && assertTrue(harness.exitCode.get() == ExitCode.failure)
    }
  )

  private final class MainHarness(timeout: Duration, finalizer: UIO[Any]) {
    val started: CountDownLatch   = new CountDownLatch(1)
    val finalized: CountDownLatch = new CountDownLatch(1)
    val mainDone: CountDownLatch  = new CountDownLatch(1)

    val hook: AtomicReference[() => Unit] = new AtomicReference[() => Unit](null)
    val exitCode: AtomicReference[ExitCode] = new AtomicReference[ExitCode](null)

    private val app = new ZIOAppDefault {
      override val gracefulShutdownTimeout: Duration = timeout

      override protected[zio] def registerShutdownHook(callback: () => Unit)(implicit unsafe: Unsafe): Unit =
        hook.set(callback)

      override protected[zio] def exitUnsafe(code: ExitCode)(implicit unsafe: Unsafe): Unit =
        exitCode.set(code)

      override protected[zio] def interruptRootFibers(mainFiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
        ZIO.unit

      override val run: ZIO[ZIOAppArgs & Scope, Any, Any] =
        ZIO.succeed(started.countDown()) *> ZIO.never.ensuring(finalizer *> ZIO.succeed(finalized.countDown()))
    }

    def start(): Unit = {
      val thread = new Thread(
        () => {
          try app.main(Array.empty)
          finally mainDone.countDown()
        },
        "zio-app-main-spec"
      )
      thread.setDaemon(true)
      thread.start()
    }
  }

  private def awaitLatch(latch: CountDownLatch, label: String): ZIO[Any, Throwable, Unit] =
    ZIO
      .attemptBlocking(latch.await(5, TimeUnit.SECONDS))
      .flatMap(ok => ZIO.fail(new RuntimeException(s"timed out waiting for $label")).unless(ok).unit)

  private def awaitHook(harness: MainHarness): ZIO[Any, Throwable, () => Unit] =
    ZIO.attemptBlocking {
      val deadlineNanos = java.lang.System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      var callback: (() => Unit) = harness.hook.get()
      while (callback == null && java.lang.System.nanoTime() < deadlineNanos) {
        Thread.sleep(10)
        callback = harness.hook.get()
      }
      callback
    }.flatMap(callback => ZIO.fromOption(Option(callback)).orElseFail(new RuntimeException("shutdown hook not installed")))
}
