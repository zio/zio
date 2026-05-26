package zio

import zio.test.Assertion._
import zio.test.TestAspect.sequential
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
        val outer    = new AtomicReference[Thread]()
        val inner    = new AtomicReference[Thread]()
        val done     = new java.util.concurrent.CountDownLatch(1)

        Unsafe.unsafe { implicit unsafe =>
          executor.submitOrThrow(new Runnable {
            def run(): Unit = {
              outer.set(Thread.currentThread())
              executor.submitAndYieldOrThrow(new Runnable {
                def run(): Unit = {
                  inner.set(Thread.currentThread())
                  done.countDown()
                }
              })
            }
          })
        }

        val completed  = done.await(5, TimeUnit.SECONDS)
        val sameThread = outer.get() eq inner.get()
        assertTrue(completed, sameThread)
      }
    },
    test("submitAndYield ignores workers from other NIO scheduler instances") {
      ZIO.attempt {
        val executor1 = Executor.makeNio()
        val executor2 = Executor.makeNio()
        val caller    = new AtomicReference[Thread]()
        val inner     = new AtomicReference[Thread]()
        val done      = new java.util.concurrent.CountDownLatch(1)

        Unsafe.unsafe { implicit unsafe =>
          executor1.submitOrThrow(new Runnable {
            def run(): Unit = {
              caller.set(Thread.currentThread())
              executor2.submitAndYieldOrThrow(new Runnable {
                def run(): Unit = {
                  inner.set(Thread.currentThread())
                  done.countDown()
                }
              })
            }
          })
        }

        val completed     = done.await(5, TimeUnit.SECONDS)
        val differentPool = caller.get() ne inner.get()
        assertTrue(completed, differentPool)
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
  ) @@ sequential
}
