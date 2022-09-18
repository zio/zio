package zio.test

import zio._
import zio.test.TestAspect.withLiveClock

object ExampleSpec extends ZIOSpecDefault {
  case class Console(ref: Ref[Boolean]) {
    def print(s: String): Task[Unit] = for {
      ok <- ref.get
      _  <- if (ok) ZIO.debug(s) else ZIO.fail(new Exception("DEAD"))
    } yield ()

    val start = ZIO.never.ensuring(ZIO.debug("INTERRUPTED") *> ref.update(_ => false)).interruptible.forkScoped
  }

  val consolePool: ULayer[ZPool[Nothing, Console]] = ZLayer.scoped {
    val create = for {
      ref    <- Ref.make(true)
      console = Console(ref)
      _      <- console.start
    } yield console
    ZPool.make(create, 0 to 10, 60.seconds)
  }

  def spec = suite("Test2")(
    test("1") {
      for {
        pool <- ZIO.service[ZPool[Throwable, Console]]
        _    <- ZIO.scoped(pool.get.flatMap(_.print("1 doing something")))
        _    <- ZIO.debug("1 DONE")
      } yield assertTrue(1 == 1)
    },
    test("2") {
      for {
        pool <- ZIO.service[ZPool[Throwable, Console]]
        _    <- ZIO.scoped(pool.get.flatMap(_.print("2 doing something")))
        _    <- Clock.sleep(10.millis)
        _    <- ZIO.debug("2 DONE")
      } yield assertTrue(1 == 1)
    },
    test("3") {
      for {
        pool <- ZIO.service[ZPool[Throwable, Console]]
        _    <- Clock.sleep(10.millis)
        _    <- ZIO.scoped(pool.get.flatMap(_.print("3 doing something")))
        _    <- Clock.sleep(10.millis)
        _    <- ZIO.debug("3 DONE")
      } yield assertTrue(1 == 1)
    },
    test("4") {
      for {
        pool <- ZIO.service[ZPool[Throwable, Console]]
        _    <- Clock.sleep(500.millis)
        _    <- ZIO.scoped(pool.get.flatMap(_.print("4 doing something")))
        _    <- Clock.sleep(10.millis)
        _    <- ZIO.debug("4 DONE")
      } yield assertTrue(1 == 1)
    },
    test("5") {
      for {
        pool <- ZIO.service[ZPool[Throwable, Console]]
        _    <- ZIO.scoped(pool.get.flatMap(_.print("5 doing something")))
        _    <- Clock.sleep(100.millis)
        _    <- ZIO.debug("5 DONE")
      } yield assertTrue(1 == 1)
    }
  ).provideShared(consolePool) @@ withLiveClock
}
