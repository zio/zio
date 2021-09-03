package zio

import zio.test._

object ZIOAppSpec extends ZIOBaseSpec {
  def spec: ZSpec[Environment, Failure] = suite("ZIOAppSpec")(
    test("fromEffect") {
      for {
        ref <- Ref.make(0)
        _   <- ZIOApp.fromEffect(ref.update(_ + 1)).invoke(Chunk.empty)
        v   <- ref.get
      } yield assertTrue(v == 1)
    },
    test("failure translates into ExitCode.failure") {
      for {
        code <- ZIOApp.fromEffect(ZIO.fail("Uh oh!")).invoke(Chunk.empty)
      } yield assertTrue(code == ExitCode.failure)
    },
    test("success translates into ExitCode.success") {
      for {
        code <- ZIOApp.fromEffect(ZIO.succeed("Hurray!")).invoke(Chunk.empty)
      } yield assertTrue(code == ExitCode.success)
    },
    test("composed app logic runs component logic") {
      for {
        ref <- Ref.make(2)
        app1 = ZIOApp.fromEffect(ref.update(_ + 3))
        app2 = ZIOApp.fromEffect(ref.update(_ - 5))
        _   <- (app1 <> app2).invoke(Chunk.empty)
        v   <- ref.get
      } yield assertTrue(v == 0)
    },
    test("hook update platform") {
      val counter = new java.util.concurrent.atomic.AtomicInteger(0)

      val reportFailure1 = (_: Cause[Any]) => ZIO.succeed { counter.incrementAndGet(); () }

      val app1 = ZIOApp(ZIO.fail("Uh oh!"), RuntimeConfigAspect(_.copy(reportFailure = reportFailure1)))

      for {
        c <- app1.invoke(Chunk.empty)
        v <- ZIO.succeed(counter.get())
      } yield assertTrue(c == ExitCode.failure) && assertTrue(v == 1)
    }
  )
}
