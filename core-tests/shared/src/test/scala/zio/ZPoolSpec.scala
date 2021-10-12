package zio

import zio.test._
import zio.test.environment.Live

object ZPoolSpec extends ZIOBaseSpec {
  final case class FakeResource(released: Ref[Int])

  def acquire[S](ref: Ref[S], f: S => (Option[String], S)): IO[String, FakeResource] =
    ref.modify(f).flatMap {
      case Some(value) => IO.fail(value)
      case None =>
        for {
          released <- Ref.make(0)
        } yield FakeResource(released)
    }

  def release(fr: FakeResource): UIO[Any] = fr.released.update(_ + 1)

  def allocate[S](ref: Ref[S], f: S => (Option[String], S)): ZManaged[Any, String, FakeResource] =
    ZManaged.acquireReleaseWith(acquire(ref, f))(release(_))

  def doTest[R, E, S](n: Int, initial: S)(
    update: S => (Option[String], S)
  )(use: (Ref[S], ZPool[String, FakeResource]) => ZIO[R, E, Any])(finish: S => TestResult): ZIO[R, E, TestResult] =
    for {
      ref <- Ref.make(initial)
      _ <- ZPool.make(allocate(ref, update), n).use { pool =>
             use(ref, pool)
           }
      result <- ref.get.map(finish)
    } yield result

  def spec: ZSpec[Environment, Failure] =
    suite("ZPoolSpec") {
      test("preallocates pool items") {
        for {
          count   <- Ref.make(0)
          get      = ZManaged.acquireRelease(count.updateAndGet(_ + 1))(count.update(_ - 1))
          reserve <- ZPool.make(get, 10).reserve
          _       <- reserve.acquire
          _       <- count.get.repeatUntil(_ == 10)
          value   <- count.get
        } yield assertTrue(value == 10)
      } +
        test("cleans up items when shut down") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.acquireRelease(count.updateAndGet(_ + 1))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            _       <- reserve.acquire
            _       <- count.get.repeatUntil(_ == 10)
            _       <- reserve.release(Exit.succeed(()))
            _       <- count.get.repeatUntil(_ == 0)
            value   <- count.get
          } yield assertTrue(value == 0)
        } +
        test("acquire one item") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.acquireRelease(count.updateAndGet(_ + 1))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            pool    <- reserve.acquire
            _       <- count.get.repeatUntil(_ == 10)
            item    <- pool.get.use(ZIO.succeed(_))
          } yield assertTrue(item == 1)
        } +
        test("reports failures via get") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.acquireRelease(count.updateAndGet(_ + 1).flatMap(ZIO.fail(_)))(count.update(_ - 1))
            reserve <- ZPool.make[Int, String](get, 10).reserve
            pool    <- reserve.acquire
            _       <- count.get.repeatUntil(_ == 10)
            values  <- ZIO.collectAll(List.fill(10)(pool.get.reserve.flatMap(_.acquire.flip)))
          } yield assertTrue(values == List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        } +
        test("blocks when item not available") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.acquireRelease(count.updateAndGet(_ + 1))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            pool    <- reserve.acquire
            _       <- count.get.repeatUntil(_ == 10)
            _       <- ZIO.collectAll(List.fill(10)(pool.get.reserve.flatMap(_.acquire)))
            result  <- Live.live(pool.get.use(_ => ZIO.unit).disconnect.timeout(1.millis))
          } yield assertTrue(result == None)
        } +
        test("reuse released items") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.acquireRelease(count.updateAndGet(_ + 1))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            pool    <- reserve.acquire
            _       <- pool.get.use(_ => ZIO.unit).repeatN(99)
            result  <- count.get
          } yield assertTrue(result == 10)
        } +
        test("invalidate item") {
          for {
            count   <- Ref.make(0)
            get      = ZManaged.acquireRelease(count.updateAndGet(_ + 1))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            pool    <- reserve.acquire
            _       <- pool.invalidate(1)
            result  <- pool.get.use(ZIO.succeed(_))
          } yield assertTrue(result == 2)
        } +
        test("compositional retry") {
          def cond(i: Int) = if (i <= 10) ZIO.fail(i) else ZIO.succeed(i)
          for {
            count   <- Ref.make(0)
            get      = ZManaged.acquireRelease(count.updateAndGet(_ + 1).flatMap(cond(_)))(count.update(_ - 1))
            reserve <- ZPool.make(get, 10).reserve
            pool    <- reserve.acquire
            result  <- pool.get.use(ZIO.succeed(_)).eventually
          } yield assertTrue(result == 11)
        }
    }
}
