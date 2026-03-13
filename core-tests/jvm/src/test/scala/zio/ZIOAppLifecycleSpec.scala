package zio

import zio.test._
import zio.test.Assertion._

import scala.annotation.nowarn

object ZIOAppLifecycleSpec extends ZIOBaseSpec {

  def spec = suite("ZIOAppLifecycleSpec")(
    test("successful app exits with ExitCode.success") {
      val app = ZIOAppDefault.fromZIO(ZIO.succeed("ok"))
      for {
        code <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assert(code)(equalTo(ExitCode.success))
    },
    test("failing app exits with ExitCode.failure") {
      val app = ZIOAppDefault.fromZIO(ZIO.fail("boom"))
      for {
        code <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assert(code)(equalTo(ExitCode.failure))
    },
    test("defect exits with ExitCode.failure") {
      val app = ZIOAppDefault.fromZIO(ZIO.die(new RuntimeException("defect")))
      for {
        code <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assert(code)(equalTo(ExitCode.failure))
    },
    test("finalizer runs on normal completion") {
      for {
        ref <- Ref.make(false)
        app = ZIOAppDefault.fromZIO(
                ZIO.acquireReleaseWith(ZIO.unit)(_ => ref.set(true))(_ => ZIO.succeed("done"))
              )
        _         <- app.invoke(Chunk.empty)
        finalized <- ref.get
      } yield assert(finalized)(isTrue)
    },
    test("finalizer runs on failure") {
      for {
        ref <- Ref.make(false)
        app = ZIOAppDefault.fromZIO(
                ZIO.acquireReleaseWith(ZIO.unit)(_ => ref.set(true))(_ => ZIO.fail("failure"))
              )
        _         <- app.invoke(Chunk.empty).ignore
        finalized <- ref.get
      } yield assert(finalized)(isTrue)
    },
    test("finalizer runs on interruption") {
      for {
        running   <- Promise.make[Nothing, Unit]
        ref       <- Ref.make(false)
        effect     = (running.succeed(()) *> ZIO.never).ensuring(ref.set(true))
        app        = ZIOAppDefault.fromZIO(effect)
        fiber     <- app.invoke(Chunk.empty).fork
        _         <- running.await
        _         <- fiber.interrupt
        finalized <- ref.get
      } yield assert(finalized)(isTrue)
    },
    test("regression #9901: app with failing effect and finalizer does not hang") {
      val app = ZIOAppDefault.fromZIO(
        ZIO.acquireReleaseWith(ZIO.unit)(_ => ZIO.unit)(_ => ZIO.fail("9901"))
      )
      for {
        code <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assert(code)(equalTo(ExitCode.failure))
    },
    test("regression #9807: defect propagates as failure exit code") {
      val app = ZIOAppDefault.fromZIO(ZIO.die(new RuntimeException("9807")))
      for {
        code <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assert(code)(equalTo(ExitCode.failure))
    },
    test("finalizers run in scope of bootstrap layer") {
      for {
        ref1 <- Ref.make(false)
        ref2 <- Ref.make(false)
        app = new ZIOAppDefault {
                override val bootstrap = ZLayer.scoped(ZIO.acquireRelease(ref1.set(true))(_ => ref1.set(false)))
                val run                = ZIO.acquireRelease(ZIO.unit)(_ => ref1.get.flatMap(ref2.set))
              }
        _     <- app.invoke(Chunk.empty)
        value <- ref2.get
      } yield assert(value)(isTrue)
    }
  ) @@ TestAspect.sequential
}
