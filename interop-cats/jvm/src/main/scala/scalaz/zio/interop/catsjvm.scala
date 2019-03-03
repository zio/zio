/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scalaz.zio
package interop

import cats.effect.{ Concurrent, ContextShift, Effect, ExitCase }
import cats.{ effect, _ }
import scalaz.zio.{ clock => zioClock, ZIO }
import scalaz.zio.clock.Clock

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ FiniteDuration, NANOSECONDS, TimeUnit }

abstract class CatsPlatform extends CatsInstances {
  val console = interop.console.cats
}

abstract class CatsInstances extends CatsInstances1 {
  implicit def ioContextShift[R]: ContextShift[TaskR[R, ?]] = new ContextShift[TaskR[R, ?]] {
    override def shift: TaskR[R, Unit] =
      ZIO.yieldNow

    override def evalOn[A](ec: ExecutionContext)(fa: TaskR[R, A]): TaskR[R, A] =
      fa.on(ec)
  }

  implicit def ioTimer[R <: Clock]: effect.Timer[TaskR[R, ?]] = new effect.Timer[TaskR[R, ?]] {
    override def clock: cats.effect.Clock[TaskR[R, ?]] = new effect.Clock[TaskR[R, ?]] {
      override def monotonic(unit: TimeUnit): TaskR[R, Long] =
        zioClock.nanoTime.map(unit.convert(_, NANOSECONDS))

      override def realTime(unit: TimeUnit): TaskR[R, Long] =
        zioClock.currentTime(unit)
    }

    override def sleep(duration: FiniteDuration): TaskR[R, Unit] =
      zioClock.sleep(scalaz.zio.duration.Duration.fromNanos(duration.toNanos))
  }

  implicit def taskEffectInstances[R]: effect.ConcurrentEffect[TaskR[R, ?]] with SemigroupK[TaskR[R, ?]] =
    new CatsConcurrentEffect[R]

  implicit val taskParallelInstance: Parallel[Task, Util.Par] =
    parallelInstance(taskEffectInstances)
}

sealed abstract class CatsInstances1 extends CatsInstances2 {
  implicit def ioMonoidInstances[R, E: Monoid]
    : MonadError[ZIO[R, E, ?], E] with Bifunctor[ZIO[R, ?, ?]] with Alternative[ZIO[R, E, ?]] =
    new CatsAlternative[R, E] with CatsBifunctor[R]

  implicit def parallelInstance[R, E](implicit M: Monad[ZIO[R, E, ?]]): Parallel[ZIO[R, E, ?], ParIO[E, ?]] =
    new CatsParallel[R, E](M)
}

sealed abstract class CatsInstances2 {
  implicit def ioInstances[R]
    : MonadError[TaskR[R, ?], Throwable] with Bifunctor[ZIO[R, ?, ?]] with SemigroupK[TaskR[R, ?]] =
    new CatsMonadError[R, Throwable] with CatsSemigroupK[R, Throwable] with CatsBifunctor[R]
}

private class CatsConcurrentEffect[R] extends CatsConcurrent[R] with effect.ConcurrentEffect[TaskR[R, ?]] {
  override final def runCancelable[A](
    fa: TaskR[R, A]
  )(cb: Either[Throwable, A] => effect.IO[Unit]): effect.SyncIO[effect.CancelToken[Task]] =
    effect.SyncIO {
      this.unsafeRun {
        fa.asInstanceOf[Task[A]].fork.flatMap { f =>
          f.await
            .flatMap(exit => IO.effect(cb(exitToEither(exit)).unsafeRunAsync(_ => ())))
            .fork
            .const(f.interrupt.void)
        }
      }
    }

  override final def toIO[A](fa: TaskR[R, A]): effect.IO[A] =
    effect.ConcurrentEffect.toIOFromRunCancelable(fa)(this)
}

private class CatsConcurrent[R] extends CatsEffect[R] with Concurrent[TaskR[R, ?]] {

  private[this] final def toFiber[A](f: Fiber[Throwable, A]): effect.Fiber[TaskR[R, ?], A] =
    new effect.Fiber[TaskR[R, ?], A] {
      override final val cancel: TaskR[R, Unit] = f.interrupt.void

      override final val join: TaskR[R, A] = f.join
    }

  override final def liftIO[A](ioa: cats.effect.IO[A]): TaskR[R, A] =
    Concurrent.liftIO(ioa)(this)

  override final def cancelable[A](k: (Either[Throwable, A] => Unit) => effect.CancelToken[TaskR[R, ?]]): TaskR[R, A] =
    ZIO.accessM { r =>
      ZIO.effectAsyncInterrupt { (kk: TaskR[R, A] => Unit) =>
        val token: effect.CancelToken[Task] = {
          k(e => kk(eitherToIO(e))).provide(r)
        }

        Left(token.provide(r).orDie)
      }
    }

  override final def race[A, B](fa: TaskR[R, A], fb: TaskR[R, B]): TaskR[R, Either[A, B]] =
    racePair(fa, fb).flatMap {
      case Left((a, fiberB)) =>
        fiberB.cancel.const(Left(a))
      case Right((fiberA, b)) =>
        fiberA.cancel.const(Right(b))
    }

  override final def start[A](fa: TaskR[R, A]): TaskR[R, effect.Fiber[TaskR[R, ?], A]] =
    fa.fork.map(toFiber)

  override final def racePair[A, B](
    fa: TaskR[R, A],
    fb: TaskR[R, B]
  ): TaskR[R, Either[(A, effect.Fiber[TaskR[R, ?], B]), (effect.Fiber[TaskR[R, ?], A], B)]] =
    (fa raceWith fb)(
      { case (l, f) => l.fold(f.interrupt *> IO.halt(_), IO.succeed).map(lv => Left((lv, toFiber(f)))) },
      { case (r, f) => r.fold(f.interrupt *> IO.halt(_), IO.succeed).map(rv => Right((toFiber(f), rv))) }
    )
}

private class CatsEffect[R]
    extends CatsMonadError[R, Throwable]
    with Effect[TaskR[R, ?]]
    with CatsSemigroupK[R, Throwable]
    with DefaultRuntime {
  @inline final protected[this] def exitToEither[A](e: Exit[Throwable, A]): Either[Throwable, A] =
    e.fold(_.failures[Throwable] match {
      case t :: Nil => Left(t)
      case _        => e.toEither
    }, Right(_))

  @inline final protected[this] def eitherToIO[A]: Either[Throwable, A] => TaskR[R, A] = {
    case Left(t)  => ZIO.fail(t)
    case Right(r) => ZIO.succeed(r)
  }

  @inline final private[this] def exitToExitCase[A]: Exit[Throwable, A] => ExitCase[Throwable] = {
    case Exit.Success(_)                          => ExitCase.Completed
    case Exit.Failure(cause) if cause.interrupted => ExitCase.Canceled
    case Exit.Failure(cause) =>
      cause.failureOrCause match {
        case Left(t) => ExitCase.Error(t)
        case _       => ExitCase.Error(FiberFailure(cause))
      }
  }

  override final def never[A]: TaskR[R, A] =
    ZIO.never

  override final def runAsync[A](
    fa: TaskR[R, A]
  )(cb: Either[Throwable, A] => effect.IO[Unit]): effect.SyncIO[Unit] =
    effect.SyncIO {
      this.unsafeRunAsync(fa.asInstanceOf[Task[A]]) { exit =>
        cb(exitToEither(exit)).unsafeRunAsync(_ => ())
      }
    }

  override final def async[A](k: (Either[Throwable, A] => Unit) => Unit): TaskR[R, A] =
    ZIO.accessM { r =>
      ZIO.effectAsync { (kk: Task[A] => Unit) =>
        k(e => kk(eitherToIO(e).provide(r)))
      }
    }

  override final def asyncF[A](k: (Either[Throwable, A] => Unit) => TaskR[R, Unit]): TaskR[R, A] =
    ZIO.accessM { r =>
      ZIO.effectAsyncM { (kk: Task[A] => Unit) =>
        k(e => kk(eitherToIO(e).provide(r))).provide(r).orDie
      }
    }

  override final def suspend[A](thunk: => TaskR[R, A]): TaskR[R, A] =
    ZIO.flatten(ZIO.effect(thunk))

  override final def delay[A](thunk: => A): TaskR[R, A] =
    ZIO.effect(thunk)

  override final def bracket[A, B](acquire: TaskR[R, A])(use: A => TaskR[R, B])(
    release: A => TaskR[R, Unit]
  ): TaskR[R, B] =
    ZIO.bracket(acquire)(release(_).orDie)(use)

  override final def bracketCase[A, B](
    acquire: TaskR[R, A]
  )(use: A => TaskR[R, B])(release: (A, ExitCase[Throwable]) => TaskR[R, Unit]): TaskR[R, B] =
    ZIO.bracketExit[R, Throwable, A, B](acquire) { (a, exit) =>
      val exitCase = exitToExitCase(exit)
      release(a, exitCase).orDie
    }(use)

  override def uncancelable[A](fa: TaskR[R, A]): TaskR[R, A] =
    fa.uninterruptible

  override final def guarantee[A](fa: TaskR[R, A])(finalizer: TaskR[R, Unit]): TaskR[R, A] =
    ZIO.accessM { r =>
      fa.provide(r).ensuring(finalizer.provide(r).orDie)
    }
}

private class CatsMonad[R, E] extends Monad[ZIO[R, E, ?]] {
  override final def pure[A](a: A): ZIO[R, E, A]                                         = ZIO.succeed(a)
  override final def map[A, B](fa: ZIO[R, E, A])(f: A => B): ZIO[R, E, B]                = fa.map(f)
  override final def flatMap[A, B](fa: ZIO[R, E, A])(f: A => ZIO[R, E, B]): ZIO[R, E, B] = fa.flatMap(f)
  override final def tailRecM[A, B](a: A)(f: A => ZIO[R, E, Either[A, B]]): ZIO[R, E, B] =
    f(a).flatMap {
      case Left(l)  => tailRecM(l)(f)
      case Right(r) => ZIO.succeed(r)
    }
}

private class CatsMonadError[R, E] extends CatsMonad[R, E] with MonadError[ZIO[R, E, ?], E] {
  override final def handleErrorWith[A](fa: ZIO[R, E, A])(f: E => ZIO[R, E, A]): ZIO[R, E, A] = fa.catchAll(f)
  override final def raiseError[A](e: E): ZIO[R, E, A]                                        = ZIO.fail(e)
}

/** lossy, throws away errors using the "first success" interpretation of SemigroupK */
private trait CatsSemigroupK[R, E] extends SemigroupK[ZIO[R, E, ?]] {
  override final def combineK[A](a: ZIO[R, E, A], b: ZIO[R, E, A]): ZIO[R, E, A] = a.orElse(b)
}

private class CatsAlternative[R, E: Monoid] extends CatsMonadError[R, E] with Alternative[ZIO[R, E, ?]] {
  override final def combineK[A](a: ZIO[R, E, A], b: ZIO[R, E, A]): ZIO[R, E, A] =
    a.catchAll { e1 =>
      b.catchAll { e2 =>
        ZIO.fail(Monoid[E].combine(e1, e2))
      }
    }
  override final def empty[A]: ZIO[R, E, A] = raiseError(Monoid[E].empty)
}

trait CatsBifunctor[R] extends Bifunctor[ZIO[R, ?, ?]] {
  override final def bimap[A, B, C, D](fab: ZIO[R, A, B])(f: A => C, g: B => D): ZIO[R, C, D] =
    fab.bimap(f, g)
}

private class CatsParallel[R, E](final override val monad: Monad[ZIO[R, E, ?]])
    extends Parallel[ZIO[R, E, ?], ParIO[E, ?]] {

  final override val applicative: Applicative[ParIO[E, ?]] =
    new CatsParApplicative[E]

  final override val sequential: ParIO[E, ?] ~> ZIO[R, E, ?] =
    new (ParIO[E, ?] ~> ZIO[R, E, ?]) { def apply[A](fa: ParIO[E, A]): ZIO[R, E, A] = Par.unwrap(fa) }

  // FIXME: asInstanceOf should not be needed
  final override val parallel: ZIO[R, E, ?] ~> ParIO[E, ?] =
    new (ZIO[R, E, ?] ~> ParIO[E, ?]) {
      def apply[A](fa: ZIO[R, E, A]): ParIO[E, A] = Par(fa.provide(().asInstanceOf[R]))
    }
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
