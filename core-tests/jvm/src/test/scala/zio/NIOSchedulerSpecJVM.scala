package zio

import zio.test.Assertion._
import zio.test._

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.blocking

object NIOSchedulerSpecJVM extends ZIOBaseSpec {

  def spec = suite("NIOSchedulerSpecJVM")(
    test("runs effects on NIO scheduler workers") {
      val executor = Executor.makeNio()
      for {
        names <- ZIO.collectAllPar(List.fill(128)(ZIO.succeed(Thread.currentThread().getName))).onExecutor(executor)
      } yield assert(names)(exists(startsWithString("NIOScheduler-Worker-")))
    },
    test("Runtime.enableNioExecutor installs the NIO scheduler") {
      for {
        name <- (ZIO.yieldNow *> ZIO.succeed(Thread.currentThread().getName)).fork
                  .flatMap(_.join)
                  .provide(
                    Runtime.enableNioExecutor
                  )
      } yield assert(name)(startsWithString("NIOScheduler-Worker-"))
    },
    test("submitAndYield resumes on the same idle worker") {
      ZIO.attempt {
        val executor = Executor.makeNio()
        val outer    = new AtomicReference[String]()
        val inner    = new AtomicReference[String]()
        val done     = new java.util.concurrent.CountDownLatch(1)

        Unsafe.unsafe { implicit unsafe =>
          executor.submitOrThrow(new Runnable {
            def run(): Unit = {
              outer.set(Thread.currentThread().getName)
              executor.submitAndYieldOrThrow(new Runnable {
                def run(): Unit = {
                  inner.set(Thread.currentThread().getName)
                  done.countDown()
                }
              })
            }
          })
        }

        assertTrue(done.await(5, TimeUnit.SECONDS), outer.get() == inner.get())
      }
    },
    test("auto-blocking replaces a blocked NIO worker") {
      ZIO.attempt {
        val executor = Executor.makeNio(autoBlocking = true)
        val started  = new java.util.concurrent.CountDownLatch(1)
        val release  = new java.util.concurrent.CountDownLatch(1)
        val done     = new java.util.concurrent.CountDownLatch(1)

        val startedInTime = Unsafe.unsafe { implicit unsafe =>
          executor.submitOrThrow(new Runnable {
            def run(): Unit =
              blocking {
                started.countDown()
                val _ = release.await(5, TimeUnit.SECONDS)
              }
          })
          started.await(5, TimeUnit.SECONDS)
        }

        Unsafe.unsafe { implicit unsafe =>
          executor.submitOrThrow(new Runnable {
            def run(): Unit =
              done.countDown()
          })
        }

        try assertTrue(startedInTime, done.await(5, TimeUnit.SECONDS))
        finally release.countDown()
      }
    }
  )
}
