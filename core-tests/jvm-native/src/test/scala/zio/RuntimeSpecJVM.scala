package zio

import zio.test._

object RuntimeSpecJVM extends ZIOBaseSpec {
  def spec =
    suite("RuntimeSpecJVM")(
      test("Runtime.unsafe.run doesn't deadlock when run within a fiber") {
        val rtm                     = Runtime.default.unsafe
        implicit val unsafe: Unsafe = Unsafe.unsafe
        val promise                 = Promise.unsafe.make[Nothing, Unit](FiberId.None)
        val effects                 = List.fill(50)(ZIO.succeed(rtm.run(ZIO.yieldNow *> promise.await)))

        for {
          f <- ZIO.collectAllPar(effects).forkDaemon
          _ <- ZIO.yieldNow
          _ <- promise.succeed(())
          _ <- f.join
        } yield assertCompletes
      } @@ TestAspect.timeout(5.seconds) @@ TestAspect.nonFlaky(100)
    )
}
