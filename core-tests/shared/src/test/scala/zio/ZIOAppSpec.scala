package zio

import zio.test._

object ZIOAppSpec extends ZIOBaseSpec {
  def spec: ZSpec[Environment, Failure] = suite("ZIOAppSpec")(
    test("fromZIO") {
      for {
        ref <- Ref.make(0)
        _   <- ZIOApp.fromZIO(ref.update(_ + 1)).invoke(Chunk.empty)
        v   <- ref.get
      } yield assertTrue(v == 1)
    },
    test("failure translates into ExitCode.failure") {
      for {
        code <- ZIOApp.fromZIO(ZIO.fail("Uh oh!")).invoke(Chunk.empty).exitCode
      } yield assertTrue(code == ExitCode.failure)
    },
    test("success translates into ExitCode.success") {
      for {
        code <- ZIOApp.fromZIO(ZIO.succeed("Hurray!")).invoke(Chunk.empty).exitCode
      } yield assertTrue(code == ExitCode.success)
    },
    test("composed app logic runs component logic") {
      for {
        ref <- Ref.make(2)
        app1 = ZIOApp.fromZIO(ref.update(_ + 3))
        app2 = ZIOApp.fromZIO(ref.update(_ - 5))
        _   <- (app1 <> app2).invoke(Chunk.empty)
        v   <- ref.get
      } yield assertTrue(v == 0)
    },
    test("hook update platform") {
      val counter = new java.util.concurrent.atomic.AtomicInteger(0)

      val logger1 = new ZLogger[String, Unit] {
        def apply(
          trace: ZTraceElement,
          fiberId: zio.FiberId.Runtime,
          logLevel: zio.LogLevel,
          message: () => String,
          context: Map[zio.FiberRef.Runtime[_], AnyRef],
          spans: List[zio.LogSpan]
        ): Unit = {
          counter.incrementAndGet()
          ()
        }
      }

      val app1 = ZIOAppDefault(ZIO.fail("Uh oh!"), RuntimeConfigAspect.addLogger(logger1))

      for {
        c <- app1.invoke(Chunk.empty).exitCode
        v <- ZIO.succeed(counter.get())
      } yield assertTrue(c == ExitCode.failure) && assertTrue(v == 1)
    }
  )
}
