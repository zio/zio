package zio

import zio.Clock.ClockLive
import zio.test.TestAspect.exceptJS
import zio.test.TestAspect.nonFlaky
import zio.test._

import java.util.concurrent.atomic.AtomicInteger

object SupervisorSpec extends ZIOBaseSpec {

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
    } @@ exceptJS(nonFlaky) @@ TestAspect.withLiveClock,
    suite("laws") {
      DifferSpec.diffLaws(Differ.supervisor)(genSupervisor)((left, right) =>
        Supervisor.toSet(left) == Supervisor.toSet(right)
      )
    },
    suite("onStart and onEnd are called exactly once")(
      test("sync effect") {
        for {
          s <- ZIO.succeed(new StartEndTrackingSupervisor)
          f <- ZIO.unit.fork.supervised(s)
          _ <- f.await
          // onEnd might be called after the forked fiber notifies the current fiber
          _ <- ZIO.succeed(s.onEndCalls).repeatUntil(_ > 0)
        } yield assertTrue(s.onStartCalls == 1, s.onEndCalls == 1)
      },
      test("async effect") {
        for {
          s <- ZIO.succeed(new StartEndTrackingSupervisor)
          f <- ClockLive.sleep(100.micros).fork.supervised(s)
          _ <- f.await
          // onEnd might be called after the forked fiber notifies the current fiber
          _ <- ZIO.succeed(s.onEndCalls).repeatUntil(_ > 0)
        } yield assertTrue(s.onStartCalls == 1, s.onEndCalls == 1)
      }
    ) @@ TestAspect.nonFlaky(100),
    test("patch does not add supervisor more than once") {
      val supervisor = new MyCustomSupervisor
      val addPatch   = Supervisor.Patch.diff(Supervisor.none, Supervisor.none ++ supervisor)
      val result     = Differ.supervisor.patch(addPatch)(Differ.supervisor.patch(addPatch)(Supervisor.none))
      assertTrue(Supervisor.allSupervisors(result).size == 2)
    },
    suite("adding as layer")(
      test("supervisor is added via layer") {
        val testSupervisor = new MyCustomSupervisor
        val test = for {
          supervisor    <- FiberRef.currentSupervisor.get
          supervisorSet <- ZIO.succeed(Supervisor.allSupervisors(supervisor))
        } yield {
          // expect 3 instead of 2 because zio-test also adds a supervisor
          assertTrue(supervisorSet.size == 3)
        }
        test.provide(Runtime.addSupervisor(testSupervisor))
      },
      test("supervisor is only added once in complex layer graph") {
        val testSupervisor = new MyCustomSupervisor ++ new MyCustomSupervisor
        val baseLayer      = ZLayer.succeed(1) ++ Runtime.addSupervisor(testSupervisor)
        val layerA         = ZLayer.fromFunction((i: Int) => s"Layer $i")
        val layerB         = ZLayer.fromFunction((i: Int) => i.toLong)
        val test = for {
          _             <- ZIO.service[Long]
          _             <- ZIO.service[String]
          supervisor    <- FiberRef.currentSupervisor.get
          supervisorSet <- ZIO.succeed(Supervisor.allSupervisors(supervisor))
          // ensure supervisor is called
          fiber <- ZIO.unit.fork
          _     <- fiber.join
        } yield {
          // expect 4 instead of 3 because zio-test also adds a supervisor
          assertTrue(supervisorSet.size == 4)
        }
        test.provide(layerA, layerB, baseLayer)
      }
    )
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
          promise.unsafe.done(ZIO.succeed(ref.unsafe.get))
      }
    }

  private final class StartEndTrackingSupervisor extends Supervisor[Unit] {
    private val _onStartCalls, _onEndCalls = new AtomicInteger(0)

    def value(implicit trace: Trace): UIO[Unit] = ZIO.unit

    def onStart[R, E, A](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A]
    )(implicit unsafe: Unsafe): Unit = {
      _onStartCalls.incrementAndGet()
      ()
    }

    def onEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
      _onEndCalls.incrementAndGet
      ()
    }

    def onStartCalls = _onStartCalls.get
    def onEndCalls   = _onEndCalls.get
  }

  private class MyCustomSupervisor extends Supervisor[Unit] {

    override def value(implicit trace: zio.Trace): UIO[Unit] = ZIO.unit

    override def onStart[R, E, A](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A]
    )(implicit unsafe: Unsafe): Unit = ()

    override def onEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = ()
  }

}
