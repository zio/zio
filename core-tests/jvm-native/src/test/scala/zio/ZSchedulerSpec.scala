package zio

import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import java.util.concurrent.{CountDownLatch, TimeUnit}

object ZSchedulerSpec extends ZIOBaseSpec {

  def spec = suite("ZSchedulerSpec")(
    test("external submissions complete when the scheduler is saturated") {
      val completed =
        ZIO.attemptBlocking {
          val executor = Executor.makeDefault(autoBlocking = false)
          val workers  = java.lang.Runtime.getRuntime.availableProcessors()
          val started  = new CountDownLatch(workers)
          val release  = new CountDownLatch(1)
          val done     = new CountDownLatch(10000)

          try {
            Unsafe.unsafe { implicit unsafe =>
              var i = 0
              while (i < workers) {
                executor.submit(new Runnable {
                  def run(): Unit = {
                    started.countDown()
                    release.await()
                  }
                })
                i += 1
              }

              if (!started.await(10, TimeUnit.SECONDS)) {
                false
              } else {
                i = 0
                while (i < 10000) {
                  executor.submit(new Runnable {
                    def run(): Unit =
                      done.countDown()
                  })
                  i += 1
                }

                release.countDown()
                done.await(10, TimeUnit.SECONDS)
              }
            }
          } finally {
            release.countDown()
          }
        }

      assertZIO(completed)(isTrue)
    } @@ nonFlaky @@ timeout(15.seconds)
  )
}
