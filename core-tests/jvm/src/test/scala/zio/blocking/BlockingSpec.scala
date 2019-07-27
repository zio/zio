package zio.blocking

import java.util.concurrent.atomic.AtomicBoolean

import zio.duration.Duration
import zio.{ TestRuntime, UIO }

final class BlockingSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is =
    "BlockingSpec".title ^
      s2"""
    Make a Blocking Service and
    verify that
      `effectBlocking` completes successfully $e1
      `effectBlockingCancelable` completes successfully $e2
      `effectBlocking` can be interrupted $e3
      `effectBlockingCancelable` can be interrupted $e4
    """

  def e1 =
    unsafeRun(effectBlocking(())).must_===(())

  def e2 =
    unsafeRun(effectBlockingCancelable(())(UIO.unit)).must_===(())

  def e3 = {
    val res = unsafeRun(effectBlocking(Thread.sleep(50000)).timeout(Duration.Zero))
    res must beNone
  }

  def blocking(released: AtomicBoolean) =
    while (!released.get()) {
      try {
        Thread.sleep(10L)
      } catch {
        case _: InterruptedException => ()
      }
    }

  def e4 = {
    val release = new AtomicBoolean(false)
    val cancel  = UIO.effectTotal(release.set(true))
    val res     = unsafeRun(effectBlockingCancelable(blocking(release))(cancel).timeout(Duration.Zero))
    res must beNone
  }
}
