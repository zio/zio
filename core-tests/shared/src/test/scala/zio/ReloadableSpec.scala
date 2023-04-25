package zio

import zio.test._

object ReloadableSpec extends ZIOBaseSpec {
  case class Counter(ref: Ref[(Int, Int)]) {
    def incrementAcquire: UIO[Int] = ref.modify { case (l, r) => (l + 1) -> ((l + 1, r)) }
    def incrementRelease: UIO[Int] = ref.modify { case (l, r) => (r + 1) -> ((l, r + 1)) }

    def acquired: UIO[Int] = ref.get.map(_._1)

    def released: UIO[Int] = ref.get.map(_._2)

    def acquire: ZIO[Scope, Nothing, Int] =
      ZIO.uninterruptible(incrementAcquire *> ZIO.addFinalizer(incrementRelease) *> acquired)

    def dummyService = acquire.as(DummyService)
  }
  object Counter {
    def make: UIO[Counter] = Ref.make((0, 0)).map(Counter(_))
  }

  trait DummyService
  val DummyService: DummyService = new DummyService {}

  def spec =
    suite("ReloadableSpec") {
      suite("manual")(
        test("initialization") {
          for {
            counter <- Counter.make
            layer    = Reloadable.manual(ZLayer.scoped(counter.dummyService))
            _       <- ZIO.service[Reloadable[DummyService]].provide(layer)
            acquire <- counter.acquired
          } yield assertTrue(acquire == 1)
        },
        test("reload") {
          for {
            counter <- Counter.make
            layer    = Reloadable.manual(ZLayer.scoped(counter.dummyService))
            _       <- Reloadable.reload[DummyService].provide(layer)
            acquire <- counter.acquired
          } yield assertTrue(acquire == 2)
        }
      )
    } @@ TestAspect.exceptNative
}
