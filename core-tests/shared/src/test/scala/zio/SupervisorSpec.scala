package zio

import zio.test._

object SupervisorSpec extends ZIOSpecDefault {

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
      } yield assertTrue(value == 1)
    } @@ TestAspect.nonFlaky @@ TestAspect.withLiveClock,
    suite("laws") {
      DifferSpec.diffLaws(Differ.supervisor)(genSupervisor)((left, right) =>
        Supervisor.toSet(left) == Supervisor.toSet(right)
      )
    }
  )

  val genSupervisor: Gen[Any, Supervisor[Any]] =
    Gen.fromZIO {
      ZIO.succeed {
        new Supervisor[Any] {
          def value(implicit trace: zio.Trace): UIO[Any] =
            ZIO.unit
          def onStart[R, E, A](
            environment: ZEnvironment[R],
            effect: ZIO[R, E, A],
            parent: Option[Fiber.Runtime[Any, Any]],
            fiber: Fiber.Runtime[E, A]
          )(implicit unsafe: Unsafe): Unit =
            ()
          def onEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
            ()
        }
      }
    }

  def makeSupervisor(ref: Ref.Atomic[Int]): UIO[Supervisor[Any]] =
    ZIO.succeed {
      new Supervisor[Any] {
        def value(implicit trace: zio.Trace): UIO[Any] =
          ZIO.unit
        def onStart[R, E, A](
          environment: ZEnvironment[R],
          effect: ZIO[R, E, A],
          parent: Option[Fiber.Runtime[Any, Any]],
          fiber: Fiber.Runtime[E, A]
        )(implicit unsafe: Unsafe): Unit =
          ref.unsafe.update(_ + 1)
        def onEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
          ()
      }
    }

  def makeOnEndSupervisor(ref: Ref.Atomic[Int]): UIO[Supervisor[Any]] =
    ZIO.succeed {
      new Supervisor[Any] {
        def value(implicit trace: zio.Trace): UIO[Any] =
          ZIO.unit
        def onStart[R, E, A](
          environment: ZEnvironment[R],
          effect: ZIO[R, E, A],
          parent: Option[Fiber.Runtime[Any, Any]],
          fiber: Fiber.Runtime[E, A]
        )(implicit unsafe: Unsafe): Unit = ()

        def onEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
          ref.unsafe.update(_ + 1)
      }
    }

  def makeOnResumeSupervisor(ref: Ref.Atomic[Int]): UIO[Supervisor[Int]] =
    Promise.make[Nothing, Int].map { promise =>
      new Supervisor[Int] {
        def value(implicit trace: zio.Trace): UIO[Int] =
          promise.await
        def onStart[R, E, A](
          environment: ZEnvironment[R],
          effect: ZIO[R, E, A],
          parent: Option[Fiber.Runtime[Any, Any]],
          fiber: Fiber.Runtime[E, A]
        )(implicit unsafe: Unsafe): Unit = ()

        def onEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = ()

        override def onResume[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
          promise.unsafe.done(ZIO.succeedNow(ref.unsafe.get))
      }
    }
}
