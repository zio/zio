package zio

import zio.test._
import zio.test.TestAspect.{jvm, nonFlaky}

object ZPoolSpec extends ZIOBaseSpec {
  def spec =
    suite("ZPoolSpec") {
      test("preallocates pool items") {
        for {
          count <- Ref.make(0)
          get    = ZIO.acquireRelease(count.updateAndGet(_ + 1))(_ => count.update(_ - 1))
          _     <- ZPool.make(get, 10)
          _     <- count.get.repeatUntil(_ == 10)
          value <- count.get
        } yield assertTrue(value == 10)
      } +
        test("cleans up items when shut down") {
          for {
            count <- Ref.make(0)
            get    = ZIO.acquireRelease(count.updateAndGet(_ + 1))(_ => count.update(_ - 1))
            scope <- Scope.make
            _     <- scope.extend(ZPool.make(get, 10))
            _     <- count.get.repeatUntil(_ == 10)
            _     <- scope.close(Exit.succeed(()))
            value <- count.get
          } yield assertTrue(value == 0)
        } +
        test("acquire one item") {
          for {
            count <- Ref.make(0)
            get    = ZIO.acquireRelease(count.updateAndGet(_ + 1))(_ => count.update(_ - 1))
            pool  <- ZPool.make(get, 10)
            _     <- count.get.repeatUntil(_ == 10)
            item  <- ZIO.scoped(pool.get)
          } yield assertTrue(item == 1)
        } +
        test("reports failures via get") {
          for {
            count  <- Ref.make(0)
            get     = ZIO.acquireRelease(count.updateAndGet(_ + 1).flatMap(ZIO.fail(_)))(_ => count.update(_ - 1))
            pool   <- ZPool.make[Scope, Int, Any](get, 10)
            _      <- count.get.repeatUntil(_ == 10)
            values <- ZIO.collectAll(List.fill(9)(pool.get.flip))
          } yield assertTrue(values == List(1, 2, 3, 4, 5, 6, 7, 8, 9))
        } @@ jvm(nonFlaky) +
        test("blocks when item not available") {
          for {
            count  <- Ref.make(0)
            get     = ZIO.acquireRelease(count.updateAndGet(_ + 1))(_ => count.update(_ - 1))
            pool   <- ZPool.make(get, 10)
            _      <- count.get.repeatUntil(_ == 10)
            _      <- ZIO.collectAll(List.fill(10)(pool.get))
            result <- Live.live(ZIO.scoped(pool.get).disconnect.timeout(1.millis))
          } yield assertTrue(result == None)
        } +
        test("reuse released items") {
          for {
            count  <- Ref.make(0)
            get     = ZIO.acquireRelease(count.updateAndGet(_ + 1))(_ => count.update(_ - 1))
            pool   <- ZPool.make(get, 10)
            _      <- ZIO.scoped(pool.get).repeatN(99)
            result <- count.get
          } yield assertTrue(result == 10)
        } +
        test("invalidate item") {
          for {
            count  <- Ref.make(0)
            get     = ZIO.acquireRelease(count.updateAndGet(_ + 1))(_ => count.update(_ - 1))
            pool   <- ZPool.make(get, 10)
            _      <- count.get.repeatUntil(_ == 10)
            _      <- pool.invalidate(1)
            result <- ZIO.scoped(pool.get)
            value  <- count.get
          } yield assertTrue(result == 2 && value == 10)
        } +
        test("reallocate invalidated item on concurrent demand and maxed out pool size") {
          ZIO.scoped {
            for {
              allocated      <- Ref.make(0)
              finalized      <- Ref.make(0)
              get             = ZIO.acquireRelease(allocated.updateAndGet(_ + 1))(_ => finalized.update(_ + 1))
              pool           <- ZPool.make(get, 0 to 1, Duration.fromSeconds(100))
              getZIO          = ZIO.scoped(pool.get.flatMap(pool.invalidate))
              _              <- Live.live(ZIO.foreachPar(0 to 9)(_ => getZIO).disconnect.timeout(1.second))
              allocatedCount <- allocated.get
              finalizedCount <- finalized.get
            } yield assertTrue(allocatedCount == 10 && finalizedCount == 10)
          }
        } +
        test("invalidate all items in pool and check that pool.get doesn't hang forever") {
          for {
            allocated      <- Ref.make(0)
            finalized      <- Ref.make(0)
            get             = ZIO.acquireRelease(allocated.updateAndGet(_ + 1))(_ => finalized.update(_ + 1))
            pool           <- ZPool.make(get, 2)
            _              <- allocated.get.repeatUntil(_ == 2)
            _              <- pool.invalidate(1)
            _              <- pool.invalidate(2)
            result         <- ZIO.scoped(pool.get)
            allocatedCount <- allocated.get
            finalizedCount <- finalized.get
          } yield assertTrue(result == 3 && allocatedCount == 4 && finalizedCount == 2)
        } +
        test("retry on failed acquire should not exhaust pool") {
          for {
            pool  <- ZPool.make(ZIO.fail(new Exception("err")).as(1), 0 to 1, Duration.Infinity)
            error <- Live.live(ZIO.scoped(pool.get.retryN(5)).timeoutFail(new Exception("timeout"))(1.second).flip)
          } yield assertTrue(error.getMessage == "err")
        } +
        test("compositional retry") {
          def cond(i: Int) = if (i <= 10) ZIO.fail(i) else ZIO.succeed(i)
          for {
            count  <- Ref.make(0)
            get     = ZIO.acquireRelease(count.updateAndGet(_ + 1).flatMap(cond(_)))(_ => count.update(_ - 1))
            pool   <- ZPool.make(get, 10)
            _      <- count.get.repeatUntil(_ == 10)
            result <- ZIO.scoped(pool.get).eventually
          } yield assertTrue(result == 11)
        } +
        test("max pool size") {
          for {
            promise <- Promise.make[Nothing, Unit]
            count   <- Ref.make(0)
            get      = ZIO.acquireRelease(count.updateAndGet(_ + 1))(_ => count.update(_ - 1))
            pool    <- ZPool.make(get, 10 to 15, 60.seconds)
            _       <- ZIO.scoped(pool.get.flatMap(_ => promise.await)).fork.repeatN(14)
            _       <- count.get.repeatUntil(_ == 15)
            _       <- promise.succeed(())
            max     <- count.get
            _       <- TestClock.adjust(60.seconds)
            min     <- count.get
          } yield assertTrue(min == 10 && max == 15)
        } +
        test("shutdown robustness") {
          for {
            count <- Ref.make(0)
            get    = ZIO.acquireRelease(count.updateAndGet(_ + 1))(_ => count.update(_ - 1))
            scope <- Scope.make
            pool  <- scope.extend(ZPool.make(get, 10))
            _     <- ZIO.scoped(pool.get).fork.repeatN(99)
            _     <- scope.close(Exit.succeed(()))
            _     <- count.get.repeatUntil(_ == 0)
          } yield assertCompletes
        } @@ jvm(nonFlaky) +
        test("get is interruptible") {
          for {
            count <- Ref.make(0)
            get    = ZIO.acquireRelease(count.updateAndGet(_ + 1))(_ => count.update(_ - 1))
            pool  <- ZPool.make(get, 10)
            _     <- pool.get.repeatN(9)
            fiber <- pool.get.fork
            _     <- fiber.interrupt
          } yield assertCompletes
        } @@ jvm(nonFlaky) +
        test("make in uninterruptible region") {
          for {
            _ <- ZIO.scoped(ZPool.make(ZIO.unit, 10 to 15, 60.seconds).uninterruptible)
          } yield assertCompletes
        } +
        test("make preserves interruptibility") {
          val get = ZIO.checkInterruptible(status => ZIO.succeed(status.isInterruptible))
          for {
            pool          <- ZPool.make(get, 10 to 15, 60.seconds)
            _             <- pool.get.repeatN(9)
            interruptible <- pool.get
          } yield assertTrue(interruptible)
        }
    }
}
