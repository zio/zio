package zio

import zio.test.TestAspect.{jvm, nonFlaky}
import zio.test._

import scala.collection.mutable

object SupervisorSpec extends ZIOBaseSpec {

  private val LogAllErrorsSupervisorSpec = {
    val logs   = mutable.ListBuffer.empty[String]
    val logger = ZLogger.simple[String, Any] { message => logs += message; () }
    val withLogger: TestAspectPoly =
      TestAspect.fromLayer(Runtime.removeDefaultLoggers >>> Runtime.addLogger(logger))
    val withSupervisor: TestAspectPoly =
      TestAspect.fromLayer(ZLayer(ZIO.runtime[Any].map(new Supervisor.LogAllErrorsSupervisor(_))))

    suite("LogAllErrorsSupervisor")(
      test("toto") {
        for {
          _         <- ZIO.log("first log")
          _          = println("============= TOTOTOOTOTOT")
          app        = ZIO.succeed("succeed") *> ZIO.fail("failure")
          _         <- app.ignore
        } yield assertTrue(
          logs.nonEmpty,
          logs.size == 2,
          logs(0) == "first log",
          logs(1) == "failure"
        )
      } @@ withLogger @@ withSupervisor
    )
  }

  def spec = suite("SupervisorSpec")(
    test("++") {
      for {
        ref       <- ZIO.succeedUnsafe(implicit unsafe => Ref.unsafe.make(0))
        left      <- makeSupervisor(ref)
        right     <- makeSupervisor(ref)
        supervisor = left ++ right
        fiber     <- ZIO.unit.fork.supervised(supervisor)
        _         <- fiber.join
        value     <- ref.get
      } yield assertTrue(value == 2)
    },
    test("Supervisor#onEnd is invoked before awaiting fibers are resumed") {
      for {
        ref         <- ZIO.succeedUnsafe(implicit u => Ref.unsafe.make(0))
        onEndSup    <- makeOnEndSupervisor(ref)
        onResumeSup <- makeOnResumeSupervisor(ref)
        f           <- ZIO.sleep(10.millis).fork.supervised(onEndSup)
        _           <- (f.await).supervised(onResumeSup)
        value       <- onResumeSup.value
      } yield assertTrue(value > 0)
    } @@ jvm(nonFlaky) @@ TestAspect.withLiveClock,
    suite("laws") {
      DifferSpec.diffLaws(Differ.supervisor)(genSupervisor)((left, right) =>
        Supervisor.toSet(left) == Supervisor.toSet(right)
      )
    },
    LogAllErrorsSupervisorSpec
  )

  val genSupervisor: Gen[Any, Supervisor[Any]] =
    Gen.fromZIO {
      ZIO.succeed {
        new Supervisor[Any] {
          override def value(implicit trace: zio.Trace): UIO[Any] =
            ZIO.unit
          override def onStart[R, E, A](
            environment: ZEnvironment[R],
            effect: ZIO[R, E, A],
            parent: Option[Fiber.Runtime[Any, Any]],
            fiber: Fiber.Runtime[E, A]
          )(implicit unsafe: Unsafe): Unit =
            ()
          override def onEnd[E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
            ()
        }
      }
    }

  def makeSupervisor(ref: Ref.Atomic[Int]): UIO[Supervisor[Any]] =
    ZIO.succeed {
      new Supervisor[Any] {
        override def value(implicit trace: zio.Trace): UIO[Any] =
          ZIO.unit
        override def onStart[R, E, A](
          environment: ZEnvironment[R],
          effect: ZIO[R, E, A],
          parent: Option[Fiber.Runtime[Any, Any]],
          fiber: Fiber.Runtime[E, A]
        )(implicit unsafe: Unsafe): Unit =
          ref.unsafe.update(_ + 1)
        override def onEnd[E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
          ()
      }
    }

  def makeOnEndSupervisor(ref: Ref.Atomic[Int]): UIO[Supervisor[Any]] =
    ZIO.succeed {
      new Supervisor[Any] {
        override def value(implicit trace: zio.Trace): UIO[Any] =
          ZIO.unit
        override def onStart[R, E, A](
          environment: ZEnvironment[R],
          effect: ZIO[R, E, A],
          parent: Option[Fiber.Runtime[Any, Any]],
          fiber: Fiber.Runtime[E, A]
        )(implicit unsafe: Unsafe): Unit = ()

        override def onEnd[E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
          ref.unsafe.update(_ + 1)
      }
    }

  def makeOnResumeSupervisor(ref: Ref.Atomic[Int]): UIO[Supervisor[Int]] =
    Promise.make[Nothing, Int].map { promise =>
      new Supervisor[Int] {
        override def value(implicit trace: zio.Trace): UIO[Int] =
          promise.await
        override def onStart[R, E, A](
          environment: ZEnvironment[R],
          effect: ZIO[R, E, A],
          parent: Option[Fiber.Runtime[Any, Any]],
          fiber: Fiber.Runtime[E, A]
        )(implicit unsafe: Unsafe): Unit = ()

        override def onEnd[E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = ()

        override def onResume[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
          promise.unsafe.done(ZIO.succeed(ref.unsafe.get))
      }
    }
}
