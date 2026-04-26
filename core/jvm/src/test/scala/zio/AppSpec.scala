import zio.{App, Fiber, RIO, Runtime, ZIO}
import zio.test._
import zio.test.environment.{Console, TestConsole}
import zio.test.specs._
import zio.test.{assert, spec}

object AppSpec extends ZIOSpecDefault {
  override def spec: Spec = {
    suite("ZIOApp") {
      testM("app completes on its own") {
        val app = ZIO.succeed(42)
        val result = ZIO.runtimeDefault.orDie.flatMap { runtime =>
          val fiber = runtime.unsafe.runApp(app)
          fiber.join
        }
        assert(result)(equalTo(42))
      }

      testM("app completes due to external signal") {
        val app = ZIO.succeed(42)
        val result = ZIO.runtimeDefault.orDie.flatMap { runtime =>
          val fiber = runtime.unsafe.runApp(app)
          val signal = ZIO.succeed(System.exit(0))
          ZIO.succeed(fiber.interrupt).fork.join
        }
        assert(result)(equalTo(0))
      }

      testM("correct error code is emitted") {
        val app = ZIO.succeed(42).orDie
        val result = ZIO.runtimeDefault.orDie.flatMap { runtime =>
          val fiber = runtime.unsafe.runApp(app)
          fiber.join
        }
        assert(result)(equalTo(0))
      }

      testM("application finalizers are run") {
        val app = ZIO.succeed(42).finally(ZIO.succeed(Console.writeLine("Finalizer ran")))
        val result = ZIO.runtimeDefault.orDie.flatMap { runtime =>
          val fiber = runtime.unsafe.runApp(app)
          fiber.join
        }
        assert(result)(equalTo(42))
      }

      testM("shutdown sequence doesn't hang") {
        val app = ZIO.succeed(42).interrupt
        val result = ZIO.runtimeDefault.orDie.flatMap { runtime =>
          val fiber = runtime.unsafe.runApp(app)
          fiber.join
        }
        assert(result)(equalTo(0))
      }

      testM("gracefulShutdownTimeout is respected") {
        val app = ZIO.succeed(42).interrupt
        val result = ZIO.runtimeDefault.orDie.flatMap { runtime =>
          val fiber = runtime.unsafe.runApp(app, 1)
          fiber.join
        }
        assert(result)(equalTo(0))
      }

      testM("issue #9901") {
        val app = ZIO.succeed(42).interrupt
        val result = ZIO.runtimeDefault.orDie.flatMap { runtime =>
          val fiber = runtime.unsafe.runApp(app)
          fiber.join
        }
        assert(result)(equalTo(0))
      }

      testM("issue #9807") {
        val app = ZIO.succeed(42).interrupt
        val result = ZIO.runtimeDefault.orDie.flatMap { runtime =>
          val fiber = runtime.unsafe.runApp(app)
          fiber.join
        }
        assert(result)(equalTo(0))
      }

      testM("issue #9240") {
        val app = ZIO.succeed(42).interrupt
        val result = ZIO.runtimeDefault.orDie.flatMap { runtime =>
          val fiber = runtime.unsafe.runApp(app)
          fiber.join
        }
        assert(result)(equalTo(0))
      }
    }
  }
}