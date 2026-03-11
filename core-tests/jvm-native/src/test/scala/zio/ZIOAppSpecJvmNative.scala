package zio

import zio.test._

// JVM and Native specific tests for ZIOApp behavior (issue #9909)
// Note: gracefulShutdownTimeout is used in the JVM shutdown hook (triggered by SIGTERM/SIGINT),
// not in invoke(). These tests verify the shutdown behavior via invoke() with interruption.
object ZIOAppSpecJvmNative extends ZIOBaseSpec {

  def spec = suite("ZIOAppSpec JVM/Native")(
    // Verify that finalizers run when the app fiber is interrupted
    // (simulates behavior when OS sends shutdown signal)
    test("finalizers run on fiber interruption") {
      for {
        started   <- Promise.make[Nothing, Unit]
        finalized <- Ref.make(false)
        app = new ZIOAppDefault {
                val run = ZIO.acquireRelease(started.succeed(()))(_ => finalized.set(true)) *> ZIO.never
              }
        fiber       <- app.invoke(Chunk.empty).fork
        _           <- started.await
        _           <- fiber.interrupt
        didFinalize <- finalized.get
      } yield assertTrue(didFinalize)
    } @@ TestAspect.timeout(10.seconds),

    // Verify that a successful app exits with success code
    test("successful app yields success exit") {
      for {
        result <- ZIOApp.fromZIO(ZIO.succeed("ok")).invoke(Chunk.empty)
      } yield assertTrue(result == "ok")
    },

    // Verify that a failed app yields a failure (exit code handling)
    test("failed app exit is failure") {
      for {
        exit <- ZIOApp.fromZIO(ZIO.fail("error")).invoke(Chunk.empty).exit
      } yield assertTrue(exit.isFailure)
    },

    // Verify that ZIOAppDefault.run can access ZIOAppArgs
    test("args are accessible in app run") {
      val args = Chunk("arg1", "arg2")
      for {
        result <- ZIOApp.fromZIO(ZIOAppArgs.getArgs).invoke(args)
      } yield assertTrue(result == args)
    },

    // Verify that multiple finalizers all run on interruption (regression #9901)
    test("multiple finalizers all run on interruption (regression #9901)") {
      for {
        started <- Promise.make[Nothing, Unit]
        counter <- Ref.make(0)
        app = new ZIOAppDefault {
                val run =
                  ZIO.acquireRelease(ZIO.unit)(_ => counter.update(_ + 1)) *>
                    ZIO.acquireRelease(started.succeed(()))(_ => counter.update(_ + 1)) *>
                    ZIO.never
              }
        fiber <- app.invoke(Chunk.empty).fork
        _     <- started.await
        _     <- fiber.interrupt
        count <- counter.get
      } yield assertTrue(count == 2)
    } @@ TestAspect.timeout(10.seconds)
  )
}
