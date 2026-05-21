package zio

import zio.test.Assertion._
import zio.test.TestAspect.timeout
import zio.test._

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.blocking

object ZSchedulerSpec extends ZIOBaseSpec {

  def spec = suite("ZSchedulerSpec")(
    test("external submissions complete after worker saturation") {
      assertZIO(ZIO.attemptBlocking(runExternalSubmissionStress(useBlockingOccupiers = false)))(isTrue)
    } @@ timeout(15.seconds),
    test("external submissions complete after blocking worker replacement") {
      assertZIO(ZIO.attemptBlocking(runExternalSubmissionStress(useBlockingOccupiers = true)))(isTrue)
    } @@ timeout(15.seconds)
  )

  private def runExternalSubmissionStress(useBlockingOccupiers: Boolean): Boolean = {
    val executor       = Executor.makeDefault()
    val workers        = java.lang.Runtime.getRuntime.availableProcessors()
    val submitters     = math.min(workers * 2, 32)
    val tasks          = 4096
    val occupied       = new CountDownLatch(workers)
    val releaseWorkers = new CountDownLatch(1)
    val ready          = new CountDownLatch(submitters)
    val start          = new CountDownLatch(1)
    val done           = new CountDownLatch(tasks)
    val submitted      = new AtomicInteger(0)
    val failure        = new AtomicReference[Throwable](null)

    def recordFailure(t: Throwable): Unit =
      if (failure.compareAndSet(null, t)) ()

    Unsafe.unsafe { implicit unsafe =>
      var i = 0
      while (i < workers) {
        executor.submit(new Runnable {
          def run(): Unit = {
            occupied.countDown()
            if (useBlockingOccupiers) {
              val _ = blocking(releaseWorkers.await(10, TimeUnit.SECONDS))
            } else {
              val _ = releaseWorkers.await(10, TimeUnit.SECONDS)
            }
          }
        })
        i += 1
      }

      if (!occupied.await(10, TimeUnit.SECONDS)) {
        releaseWorkers.countDown()
        false
      } else {
        val producerThreads = new Array[Thread](submitters)

        var j = 0
        while (j < submitters) {
          val thread = new Thread(
            new Runnable {
              def run(): Unit =
                try {
                  ready.countDown()
                  start.await()

                  var loop = true
                  while (loop) {
                    val n = submitted.getAndIncrement()
                    if (n >= tasks) {
                      loop = false
                    } else if (
                      !executor.submit(new Runnable {
                        def run(): Unit =
                          done.countDown()
                      })
                    ) {
                      recordFailure(new RuntimeException("executor rejected stress task"))
                    }
                  }
                } catch {
                  case t: Throwable => recordFailure(t)
                }
            }
          )
          thread.setDaemon(true)
          producerThreads(j) = thread
          j += 1
        }

        producerThreads.foreach(_.start())
        ready.await(10, TimeUnit.SECONDS)
        start.countDown()
        producerThreads.foreach(_.join(10000))
        releaseWorkers.countDown()

        val error = failure.get()
        if (error ne null) throw error

        done.await(10, TimeUnit.SECONDS)
      }
    }
  }
}
