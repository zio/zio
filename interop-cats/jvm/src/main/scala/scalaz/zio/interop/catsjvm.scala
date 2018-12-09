package scalaz.zio
package interop

import cats.effect.{ Concurrent, ContextShift, Effect, ExitCase }
import cats.{ effect, _ }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ FiniteDuration, NANOSECONDS, TimeUnit }

abstract class CatsPlatform extends CatsInstances {
  val console = interop.console.cats
}

abstract class CatsInstances extends CatsInstances1 {
  implicit def ioContextShift[E]: ContextShift[IO[E, ?]] = new ContextShift[IO[E, ?]] {
    override def shift: IO[E, Unit] =
      IO.yieldNow

    override def evalOn[A](ec: ExecutionContext)(fa: IO[E, A]): IO[E, A] =
      fa.on(ec)
  }

  implicit def ioTimer[E](implicit zioClock: Clock): effect.Timer[IO[E, ?]] = new effect.Timer[IO[E, ?]] {
    override def clock: cats.effect.Clock[IO[E, ?]] = new effect.Clock[IO[E, ?]] {
      override def monotonic(unit: TimeUnit): IO[E, Long] =
        zioClock.nanoTime.map(unit.convert(_, NANOSECONDS))

      override def realTime(unit: TimeUnit): IO[E, Long] =
        zioClock.currentTime(unit)
    }

    override def sleep(duration: FiniteDuration): IO[E, Unit] =
      zioClock.sleep(duration.length, duration.unit)
  }

  implicit val taskEffectInstances: effect.ConcurrentEffect[Task] with SemigroupK[Task] =
    new CatsConcurrentEffect

  implicit val taskParallelInstance: Parallel[Task, Task.Par] =
    parallelInstance(taskEffectInstances)
}

sealed abstract class CatsInstances1 extends CatsInstances2 {
  implicit def ioMonoidInstances[E: Monoid]: MonadError[IO[E, ?], E] with Bifunctor[IO] with Alternative[IO[E, ?]] =
    new CatsAlternative[E] with CatsBifunctor

  implicit def parallelInstance[E](implicit M: Monad[IO[E, ?]]): Parallel[IO[E, ?], ParIO[E, ?]] =
    new CatsParallel[E](M)
}

sealed abstract class CatsInstances2 {
  implicit def ioInstances[E]: MonadError[IO[E, ?], E] with Bifunctor[IO] with SemigroupK[IO[E, ?]] =
    new CatsMonadError[E] with CatsSemigroupK[E] with CatsBifunctor
}

private class CatsConcurrentEffect extends CatsConcurrent with effect.ConcurrentEffect[Task] {
  def runCancelable[A](
    fa: Task[A]
  )(cb: Either[Throwable, A] => effect.IO[Unit]): effect.SyncIO[effect.CancelToken[Task]] =
    effect.SyncIO {
      this.unsafeRun {
        fa.fork.flatMap { f =>
          f.observe
            .flatMap(exit => IO.syncThrowable(cb(exitResultToEither(exit)).unsafeRunAsync(_ => ())))
            .fork
            .const(f.interrupt.void)
        }
      }
    }

//      def runCancelable[A](fa: Task[A])(cb: Either[Throwable, A] => effect.IO[Unit]): effect.SyncIO[effect.CancelToken[Task]] = {
//    effect.SyncIO {
//      this.unsafeRun {
//        fa.sandboxed.redeemPure(ExitResult.Failed(_), ExitResult.Succeeded(_))
//          .flatMap(exit => IO.syncThrowable(cb(exitResultToEither(exit)).unsafeRunAsync(_ => ())))
//          .fork.flatMap { f =>
//            IO.now(f.interrupt.void)
//        }
//      }
//    }
  // misses ExitCase.Canceled

//    var canceler: effect.CancelToken[Task] = null
//    runAsync(fa.fork.flatMap {
//      f => Task { canceler = f.interrupt.void } *> f.join
//    })(cb).flatMap(_ => effect.SyncIO {
//      var cont = true
//      while (cont) {
//        if (canceler != null) {
//          cont = false
//        }
//      }
//      canceler
//    })

  override def toIO[A](fa: Task[A]): effect.IO[A] =
    effect.ConcurrentEffect.toIOFromRunCancelable(fa)(this)
//    effect.Effect.toIOFromRunAsync(fa)(this)
}

private class CatsConcurrent extends CatsEffect with Concurrent[Task] {

  protected[this] final def toFiber[A](f: Fiber[Throwable, A]): effect.Fiber[Task, A] = new effect.Fiber[Task, A] {
    override val cancel: Task[Unit] = f.interrupt.void

    override val join: Task[A] = f.join
  }

  override def liftIO[A](ioa: cats.effect.IO[A]): Task[A] =
    Concurrent.liftIO(ioa)(this)

  override def cancelable[A](k: (Either[Throwable, A] => Unit) => effect.CancelToken[Task]): Task[A] =
    IO.asyncInterrupt { (kk: IO[Throwable, A] => Unit) =>
      val token: effect.CancelToken[Task] = {
        k(e => kk(eitherToIO(e)))
      }

      Left(token.orDie)
    }

  override def race[A, B](fa: Task[A], fb: Task[B]): Task[Either[A, B]] =
    racePair(fa, fb).flatMap {
      case Left((a, fiberB)) =>
        fiberB.cancel.const(Left(a))
      case Right((fiberA, b)) =>
        fiberA.cancel.const(Right(b))
    }

  override def start[A](fa: Task[A]): Task[effect.Fiber[Task, A]] =
    fa.fork.map(toFiber)

  override def racePair[A, B](
    fa: Task[A],
    fb: Task[B]
  ): Task[Either[(A, effect.Fiber[Task, B]), (effect.Fiber[Task, A], B)]] =
    (fa raceWith fb)(
      { case (l, f) => l.fold(f.interrupt *> IO.halt(_), IO.succeed).map(lv => Left((lv, toFiber(f)))) },
      { case (r, f) => r.fold(f.interrupt *> IO.halt(_), IO.succeed).map(rv => Right((toFiber(f), rv))) }
    )
}

private class CatsEffect extends CatsMonadError[Throwable] with Effect[Task] with CatsSemigroupK[Throwable] with RTS {
  @inline final protected def exitToEither[A](e: Exit[Throwable, A]): Either[Throwable, A] =
    e.fold(_.checked[Throwable] match {
      case t :: Nil => Left(t)
      case _        => e.toEither
    }, Right(_))

  @inline final protected def eitherToIO[A]: Either[Throwable, A] => IO[Throwable, A] = {
    case Left(t)  => IO.fail(t)
    case Right(r) => IO.succeed(r)
  }

  @inline final protected def exitToExitCase[A]: Exit[Throwable, A] => ExitCase[Throwable] = {
    case Exit.Success(_)                          => ExitCase.Completed
    case Exit.Failure(cause) if cause.interrupted => ExitCase.Canceled
    case Exit.Failure(cause) =>
      cause.checkedOrRefail match {
        case Left(t) => ExitCase.Error(t)
        case _       => ExitCase.Error(FiberFailure(cause))
      }
  }

  override def never[A]: Task[A] =
    IO.never

  override def toIO[A](
    fa: Task[A]
  ): effect.IO[A] =
    effect.Effect.toIOFromRunAsync(fa)(this)

  override def runAsync[A](
    fa: Task[A]
  )(cb: Either[Throwable, A] => effect.IO[Unit]): effect.SyncIO[Unit] =
    effect.SyncIO {
      toIO(fa).runAsync(cb)
      this.unsafeRunAsync(fa) { exit =>
        cb(exitToEither(exit)).unsafeRunAsync(_ => ())
      }
    }

  override def async[A](k: (Either[Throwable, A] => Unit) => Unit): Task[A] =
    IO.async { (kk: IO[Throwable, A] => Unit) =>
      k(eitherToIO andThen kk)
    }

  override def asyncF[A](k: (Either[Throwable, A] => Unit) => Task[Unit]): Task[A] =
    IO.asyncIO { (kk: IO[Throwable, A] => Unit) =>
      k(eitherToIO andThen kk).orDie
    }

  override def suspend[A](thunk: => Task[A]): Task[A] =
    IO.flatten(IO.syncThrowable(thunk))

  override def delay[A](thunk: => A): Task[A] =
    IO.syncThrowable(thunk)

  override def bracket[A, B](acquire: Task[A])(use: A => Task[B])(
    release: A => Task[Unit]
  ): Task[B] =
    IO.bracket(acquire)(release(_).orDie)(use)

  override def bracketCase[A, B](
    acquire: Task[A]
  )(use: A => Task[B])(release: (A, ExitCase[Throwable]) => Task[Unit]): Task[B] =
    IO.bracket0[Throwable, A, B](acquire) { (a, exit) =>
      val exitCase = exitToExitCase(exit)
      release(a, exitCase).orDie
    }(use)

  override def uncancelable[A](fa: Task[A]): Task[A] =
    fa.uninterruptible

  override def guarantee[A](fa: Task[A])(finalizer: Task[Unit]): Task[A] =
    fa.ensuring(finalizer.orDie)
}

private class CatsMonad[E] extends Monad[IO[E, ?]] {
  override def pure[A](a: A): IO[E, A]                                 = IO.succeed(a)
  override def map[A, B](fa: IO[E, A])(f: A => B): IO[E, B]            = fa.map(f)
  override def flatMap[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] = fa.flatMap(f)
  override def tailRecM[A, B](a: A)(f: A => IO[E, Either[A, B]]): IO[E, B] =
    f(a).flatMap {
      case Left(l)  => tailRecM(l)(f)
      case Right(r) => IO.succeed(r)
    }
}

private class CatsMonadError[E] extends CatsMonad[E] with MonadError[IO[E, ?], E] {
  override def handleErrorWith[A](fa: IO[E, A])(f: E => IO[E, A]): IO[E, A] = fa.catchAll(f)
  override def raiseError[A](e: E): IO[E, A]                                = IO.fail(e)
}

/** lossy, throws away errors using the "first success" interpretation of SemigroupK */
private trait CatsSemigroupK[E] extends SemigroupK[IO[E, ?]] {
  override def combineK[A](a: IO[E, A], b: IO[E, A]): IO[E, A] = a.orElse(b)
}

private class CatsAlternative[E: Monoid] extends CatsMonadError[E] with Alternative[IO[E, ?]] {
  override def combineK[A](a: IO[E, A], b: IO[E, A]): IO[E, A] =
    a.catchAll { e1 =>
      b.catchAll { e2 =>
        IO.fail(Monoid[E].combine(e1, e2))
      }
    }
  override def empty[A]: IO[E, A] = raiseError(Monoid[E].empty)
}

trait CatsBifunctor extends Bifunctor[IO] {
  override def bimap[A, B, C, D](fab: IO[A, B])(f: A => C, g: B => D): IO[C, D] =
    fab.bimap(f, g)
}

private class CatsParallel[E](final override val monad: Monad[IO[E, ?]]) extends Parallel[IO[E, ?], ParIO[E, ?]] {

  final override val applicative: Applicative[ParIO[E, ?]] =
    new CatsParApplicative[E]

  final override val sequential: ParIO[E, ?] ~> IO[E, ?] =
    new (ParIO[E, ?] ~> IO[E, ?]) { def apply[A](fa: ParIO[E, A]): IO[E, A] = Par.unwrap(fa) }

  final override val parallel: IO[E, ?] ~> ParIO[E, ?] =
    new (IO[E, ?] ~> ParIO[E, ?]) { def apply[A](fa: IO[E, A]): ParIO[E, A] = Par(fa) }
}

private class CatsParApplicative[E] extends Applicative[ParIO[E, ?]] {

  final override def pure[A](x: A): ParIO[E, A] =
    Par(IO.succeed(x))

  final override def map2[A, B, Z](fa: ParIO[E, A], fb: ParIO[E, B])(f: (A, B) => Z): ParIO[E, Z] =
    Par(Par.unwrap(fa).zipPar(Par.unwrap(fb)).map(f.tupled))

  final override def ap[A, B](ff: ParIO[E, A => B])(fa: ParIO[E, A]): ParIO[E, B] =
    Par(Par.unwrap(ff).flatMap(Par.unwrap(fa).map))

  final override def product[A, B](fa: ParIO[E, A], fb: ParIO[E, B]): ParIO[E, (A, B)] =
    map2(fa, fb)(_ -> _)

  final override def map[A, B](fa: ParIO[E, A])(f: A => B): ParIO[E, B] =
    Par(Par.unwrap(fa).map(f))

  final override def unit: ParIO[E, Unit] =
    Par(IO.unit)
}
