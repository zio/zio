package zio

import zio.test._

object ScopedRefSpec extends ZIOBaseSpec {
  case class Counter(ref: Ref[(Int, Int)]) {
    def incrementAcquire: UIO[Int] = ref.modify { case (l, r) => (l + 1) -> ((l + 1, r)) }
    def incrementRelease: UIO[Int] = ref.modify { case (l, r) => (r + 1) -> ((l, r + 1)) }

    def acquired: UIO[Int] = ref.get.map(_._1)

    def released: UIO[Int] = ref.get.map(_._2)

    def acquire: ZIO[Scope, Nothing, Int] =
      ZIO.uninterruptible(incrementAcquire *> ZIO.addFinalizer(incrementRelease) *> acquired)
  }
  object Counter {
    def make: UIO[Counter] = Ref.make((0, 0)).map(Counter(_))
  }

  def spec = suite("ScopedRefSpec")(
    suite("setting") {
      test("single set") {
        for {
          counter <- Counter.make
          ref     <- ScopedRef.make(0)
          _       <- ref.set(counter.acquire)
          value   <- ref.get
        } yield assertTrue(value == 1)
      } +
        test("dual set") {
          for {
            counter <- Counter.make
            ref     <- ScopedRef.make(0)
            _       <- ref.set(counter.acquire) *> ref.set(counter.acquire)
            value   <- ref.get
          } yield assertTrue(value == 2)
        }
    } +
      suite("releasing") {
        test("release on swap") {
          for {
            counter <- Counter.make
            ref     <- ScopedRef.make(0)
            _       <- ref.set(counter.acquire) *> ref.set(counter.acquire)
            acquire <- counter.acquired
            release <- counter.released
          } yield assertTrue(acquire == 2 && release == 1)
        } +
          test("double release on double swap") {
            for {
              counter <- Counter.make
              ref     <- ScopedRef.make(0)
              _       <- ref.set(counter.acquire) *> ref.set(counter.acquire) *> ref.set(counter.acquire)
              acquire <- counter.acquired
              release <- counter.released
            } yield assertTrue(acquire == 3 && release == 2)
          } +
          test("full release") {
            for {
              counter <- Counter.make
              _ <- ZIO.scoped(
                     ScopedRef
                       .make(0)
                       .flatMap(ref => ref.set(counter.acquire) *> ref.set(counter.acquire) *> ref.set(counter.acquire))
                   )
              acquire <- counter.acquired
              release <- counter.released
            } yield assertTrue(acquire == 3 && release == 3)
          } +
          test("eager release") {
            for {
              ref <- Ref.make(0)
              acquire = ZIO.acquireRelease {
                          ref.modify(n => if (n == 0) (ZIO.unit, 1) else (ZIO.dieMessage("die"), n)).flatten
                        } { _ =>
                          ref.update(_ - 1)
                        }
              scopedRef <- ScopedRef.fromAcquire(acquire)
              _         <- scopedRef.set(acquire)
            } yield assertCompletes
          }
      }
  )
}
