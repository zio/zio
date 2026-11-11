package zio.internal

import zio._
import zio.test._
import zio.test.TestAspect.{flaky, jvmOnly}

object FiberSetSpec extends ZIOBaseSpec {

  def spec =
    suite("FiberSetSpec")(
      test("add, contains, and remove live fibers") {
        for {
          promise <- Promise.make[Nothing, Unit]
          fiber   <- promise.await.forkDaemon
          set      = FiberSet()
          added    = set.add(fiber.asInstanceOf[Fiber.Runtime[_, _]])
          present  = set.contains(fiber)
          removed  = set.remove(fiber)
          after    = set.contains(fiber)
          _       <- fiber.interrupt
        } yield assertTrue(added && present && removed && !after && set.isEmpty)
      },
      test("concurrent add, iterate, and remove across multiple fibers") {
        for {
          promise <- Promise.make[Nothing, Unit]
          fibers  <- ZIO.foreachPar(1 to 128)(_ => promise.await.forkDaemon)
          set      = FiberSet(64)
          _       <- ZIO.foreachParDiscard(fibers)(f => ZIO.succeed(set.add(f.asInstanceOf[Fiber.Runtime[_, _]])))
          snapshot = Chunk.fromIterator(set.scalaIterator)
          _       <- ZIO.foreachParDiscard(fibers.take(64))(f => ZIO.succeed(set.remove(f)))
          remain   = Chunk.fromIterator(set.scalaIterator)
          _       <- ZIO.foreachParDiscard(fibers)(_.interrupt)
        } yield assertTrue(snapshot.size == 128 && remain.size == 64)
      },
      test("automatically filters out completed fibers during iteration") {
        for {
          p1 <- Promise.make[Nothing, Unit]
          p2 <- Promise.make[Nothing, Unit]
          f1 <- p1.await.forkDaemon
          f2 <- p2.await.forkDaemon
          set = FiberSet()
          _   = set.add(f1.asInstanceOf[Fiber.Runtime[_, _]])
          _   = set.add(f2.asInstanceOf[Fiber.Runtime[_, _]])
          _  <- p1.succeed(())
          _  <- f1.join
          live = Chunk.fromIterator(set.scalaIterator)
          _  <- f2.interrupt
        } yield assertTrue(live == Chunk(f2.asInstanceOf[Fiber.Runtime[_, _]]))
      },
      test("garbage collection reclaims unreachable suspended fibers via ReferenceQueue") {
        ZIO.succeed {
          val set = FiberSet(128)
          var i   = 0
          while (i < 256) {
            val scope = Unsafe.unsafe(implicit u => FiberRuntime(FiberId.Gen.Live.make(Trace.empty), FiberRefs.empty, RuntimeFlags.default))
            set.add(scope)
            i += 1
          }
          System.gc()
          Thread.sleep(50)
          set.gc()
          assertTrue(set.size() < 256)
        }
      } @@ flaky
    ) @@ jvmOnly
}
