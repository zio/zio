package zio

import zio.test._
import zio.duration._
import zio.test.TestAspect.nonFlaky
import zio.test.environment.{Live, TestClock}

object ZPoolSpec extends ZIOBaseSpec {
  def spec =
    suite("ZPoolSpec") {
      testM("preallocates pool items") {
        for {
          count   <- Ref.make(0)
          get      = ZManaged.make_(count.updateAndGet(_ + 1))(count.update(_ - 1))
          reserve <- ZPool.make(get, 10).reserve
          _       <- reserve.acquire
          _       <- count.get.repeatUntil(_ == 10)
          value   <- count.get
        } yield assertTrue(value == 10)
      } +
        testM("cleans up items when shut down") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.make_(count.updateAndGet(_ + 1))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            _       <- reserve.acquire
            _       <- count.get.repeatUntil(_ == 10)
            _       <- reserve.release(Exit.succeed(()))
            value   <- count.get
          } yield assertTrue(value == 0)
        } +
        testM("acquire one item") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.make_(count.updateAndGet(_ + 1))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            pool    <- reserve.acquire
            _       <- count.get.repeatUntil(_ == 10)
            item    <- pool.get.use(ZIO.succeed(_))
          } yield assertTrue(item == 1)
        } +
        testM("reports failures via get") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.make_(count.updateAndGet(_ + 1).flatMap(ZIO.fail(_)))(count.update(_ - 1))
            reserve <- ZPool.make[Any, Int, String](get, 10).reserve
            pool    <- reserve.acquire
            _       <- count.get.repeatUntil(_ == 10)
            values  <- ZIO.collectAll(List.fill(10)(pool.get.reserve.flatMap(_.acquire.flip)))
          } yield assertTrue(values == List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        } +
        testM("blocks when item not available") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.make_(count.updateAndGet(_ + 1))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            pool    <- reserve.acquire
            _       <- count.get.repeatUntil(_ == 10)
            _       <- ZIO.collectAll(List.fill(10)(pool.get.reserve.flatMap(_.acquire)))
            result  <- Live.live(pool.get.use(_ => ZIO.unit).disconnect.timeout(1.millis))
          } yield assertTrue(result == None)
        } +
        testM("reuse released items") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.make_(count.updateAndGet(_ + 1))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            pool    <- reserve.acquire
            _       <- pool.get.use(_ => ZIO.unit).repeatN(99)
            result  <- count.get
          } yield assertTrue(result == 10)
        } +
        testM("invalidate item") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.make_(count.updateAndGet(_ + 1))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            pool    <- reserve.acquire
            _       <- count.get.repeatUntil(_ == 10)
            _       <- pool.invalidate(1)
            result  <- pool.get.use(ZIO.succeed(_))
          } yield assertTrue(result == 2)
        } +
        testM("compositional retry") {
          def cond(i: Int) = if (i <= 10) ZIO.fail(i) else ZIO.succeed(i)
          for {
            count   <- Ref.make(0)
            get      = ZManaged.make_(count.updateAndGet(_ + 1).flatMap(cond(_)))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            pool    <- reserve.acquire
            _       <- count.get.repeatUntil(_ == 10)
            result  <- pool.get.use(ZIO.succeed(_)).eventually
          } yield assertTrue(result == 11)
        } +
        testM("max pool size") {
          for {
            promise <- Promise.make[Nothing, Unit]
            count   <- Ref.make(0)
            get      = ZManaged.make_(count.updateAndGet(_ + 1))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10 to 15, 60.seconds).reserve
            pool    <- reserve.acquire
            _       <- pool.get.use(_ => promise.await).fork.repeatN(14)
            _       <- count.get.repeatUntil(_ == 15)
            _       <- promise.succeed(())
            max     <- count.get
            _       <- TestClock.adjust(60.seconds)
            min     <- count.get
          } yield assertTrue(min == 10 && max == 15)
        } +
        testM("shutdown robustness") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.make_(count.updateAndGet(_ + 1))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            pool    <- reserve.acquire
            _       <- pool.get.use(ZIO.succeed(_)).fork.repeatN(99)
            _       <- reserve.release(Exit.succeed(()))
            result  <- count.get
          } yield assertTrue(result == 0)
        } @@ nonFlaky
    }
}
