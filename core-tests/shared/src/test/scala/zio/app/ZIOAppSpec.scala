package zio.app

import zio._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for ZIOApp functionality that work across all platforms.
 * This test suite focuses on the core functionality of ZIOApp without
 * requiring process spawning or signal handling.
 */
object ZIOAppSpec extends ZIOBaseSpec {
  def spec = suite("ZIOAppSpec")(
    // Core functionality tests
    test("ZIOApp.fromZIO creates an app that executes the effect") {
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

    // Finalizer tests that don't require process spawning
    test("execution of finalizers on interruption") {
      for {
        running   <- Promise.make[Nothing, Unit]
        ref       <- Ref.make(false)
        effect     = (running.succeed(()) *> ZIO.never).ensuring(ref.set(true))
        app        = ZIOAppDefault.fromZIO(effect)
        fiber     <- app.invoke(Chunk.empty).fork
        _         <- running.await
        _         <- fiber.interrupt
        finalized <- ref.get
      } yield assertTrue(finalized)
    },

    test("finalizers are run in scope of bootstrap layer") {
      for {
        ref1 <- Ref.make(false)
        ref2 <- Ref.make(false)
        app = new ZIOAppDefault {
                override val bootstrap = ZLayer.scoped(ZIO.acquireRelease(ref1.set(true))(_ => ref1.set(false)))
                val run                = ZIO.acquireRelease(ZIO.unit)(_ => ref1.get.flatMap(ref2.set))
              }
        _     <- app.invoke(Chunk.empty)
        value <- ref2.get
      } yield assertTrue(value)
    },

    test("nested finalizers run in correct order") {
      for {
        results <- Ref.make(List.empty[String])
        inner = ZIO.acquireRelease(
          results.update(_ :+ "acquire-inner")
        )(_ => results.update(_ :+ "release-inner"))
        outer = ZIO.acquireRelease(
          results.update(_ :+ "acquire-outer") *> inner
        )(_ => results.update(_ :+ "release-outer"))
        app = ZIOAppDefault.fromZIO(outer *> ZIO.interrupt)
        _ <- app.invoke(Chunk.empty).ignore
        finalResults <- results.get
      } yield {
        val expectedOrder = List(
          "acquire-outer",
          "acquire-inner",
          "release-inner",
          "release-outer"
        )
        assertTrue(finalResults == expectedOrder)
      }
    },

    // Platform runtime handling tests
    test("hook update platform") {
      val counter = new java.util.concurrent.atomic.AtomicInteger(0)

      val logger1 = new ZLogger[Any, Unit] {
        def apply(
          trace: Trace,
          fiberId: zio.FiberId,
          logLevel: zio.LogLevel,
          message: () => Any,
          cause: Cause[Any],
          context: FiberRefs,
          spans: List[zio.LogSpan],
          annotations: Map[String, String]
        ): Unit = {
          counter.incrementAndGet()
          ()
        }
      }

      val app1 = ZIOApp(ZIO.fail("Uh oh!"), Runtime.addLogger(logger1))

      for {
        c <- app1.invoke(Chunk.empty).exitCode
        v <- ZIO.succeed(counter.get())
      } yield assertTrue(c == ExitCode.failure) && assertTrue(v == 1)
    },

    // Command line args tests
    test("command line arguments are passed correctly") {
      val args = Chunk("arg1", "arg2", "arg3")
      
      for {
        receivedArgs <- ZIOApp.fromZIO(ZIO.service[ZIOAppArgs].map(_.getArgs)).invoke(args)
      } yield assertTrue(receivedArgs == args)
    },

    // Error handling tests
    test("exceptions in run are converted to failures") {
      val exception = new RuntimeException("Boom!")
      val app = ZIOAppDefault.fromZIO(ZIO.attempt(throw exception))
      
      app.invoke(Chunk.empty).exit.map { exit =>
        assertTrue(exit.isFailure) &&
        assertTrue(exit.causeOption.exists(_.failureOption.exists(_.isInstanceOf[RuntimeException])))
      }
    },

    // Layer tests
    test("bootstrap layer is provided correctly") {
      val testValue = "test-value"
      val testLayer = ZLayer.succeed(testValue)
      
      val app = new ZIOApp {
        type Environment = String
        val bootstrap = ZLayer.environment[ZIOAppArgs] >>> testLayer
        def run = ZIO.service[String]
        val environmentTag = EnvironmentTag[String]
      }
      
      for {
        result <- app.invoke(Chunk.empty)
      } yield assertTrue(result == testValue)
    },

    test("multiple layers can be composed") {
      val app = new ZIOApp {
        case class ServiceA(value: String)
        case class ServiceB(value: Int)
        case class ServiceC(a: ServiceA, b: ServiceB)
        
        type Environment = ServiceC
        val bootstrap = {
          val layerA = ZLayer.succeed(ServiceA("test"))
          val layerB = ZLayer.succeed(ServiceB(42))
          val layerC = ZLayer.fromFunction(ServiceC(_, _))
          
          ZLayer.environment[ZIOAppArgs] >>> (layerA ++ layerB) >>> layerC
        }
        def run = for {
          svc <- ZIO.service[ServiceC]
          res = s"${svc.a.value}-${svc.b.value}"
        } yield res
        val environmentTag = EnvironmentTag[ServiceC]
      }
      
      for {
        result <- app.invoke(Chunk.empty)
      } yield assertTrue(result == "test-42")
    }
  )
} 