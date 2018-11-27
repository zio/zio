package scalaz.zio
package interop

import cats.effect.{Concurrent, ContextShift, Effect, ExitCase}
import cats.{effect, _}
import scalaz.zio.ExitResult.{Cause, Failed, Succeeded}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, NANOSECONDS, TimeUnit}

abstract class CatsPlatform extends CatsInstances {
  val console = interop.console.cats
}

abstract class CatsInstances extends CatsInstances1 {
  implicit def ioContextShift[E]: ContextShift[IO[E, ?]] = new ContextShift[IO[E, ?]] {
    override def shift: IO[E, Unit] =
      IO.shift

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
  override def runCancelable[A](fa: Task[A])(
    cb: Either[Throwable, A] => effect.IO[Unit]
  ): effect.SyncIO[effect.CancelToken[Task]] =
      effect.SyncIO {
//        val fiber = this.unsafeRun(fa.run.flatMap(r => IO.sync(cb(exitResultToEither(r)).unsafeRunAsync(_ => ()))).fork)
//        fiber.interrupt

//        this.unsafeRunAsync[Throwable, Unit](fa.run.map { exit =>
//          cb(exitResultToEither(exit)).unsafeRunAsync(_ => ())
//        })(_ => ())
//        Task(())

        // deliberately incorrect impl, any use of .fork in unsafeRuns seems to cause breakage...
        this.unsafeRunAsync[Throwable, Unit] {
          for {
            _ <- IO.sync {
              this.unsafeRunAsync(fa) { exit =>
                val value = exitResultToEither(exit)
//                println(value)
                cb(value).unsafeRunAsync(_ => ())
              }
              ()
            } // FIXME: Uncommenting the fork breaks toIO/liftIO tests, even though it shouldn't impact them
//               .fork
          } yield ()
        }(_ => ())
        Task(())
      }

  // TODO
//  override def toIO[A](fa: Task[A]): effect.IO[A] =
//    effect.IO.cancelable { cb =>
//      runCancelable(fa)(r => effect.IO(cb(r))).unsafeRunSync()
//      effect.IO(())
//    }

//    effect.IO.cancelable { cb =>
//      runAsync(fa)(r => effect.IO(cb(r))).unsafeRunSync()
//      unsafeRun(fa.run.flatMap(r => IO.sync(cb(exitResultToEither(r)))).fork)
//    }

//    effect.IO.cancelable { k =>
//      val f = unsafeRun(fa.run.flatMap(r => IO.sync(k(exitResultToEither(r)))).fork)
//      effect.IO(unsafeRun(f.interrupt))
//    }

}

private class CatsConcurrent extends CatsEffect with Concurrent[Task] {
  private[this] def toFiber[A](f: Fiber[Throwable, A]): effect.Fiber[Task, A] = new effect.Fiber[Task, A] {
    override val cancel: Task[Unit] =
      Task(System.out println "interrupt running") *>
        f.interrupt.peek(_ => Task(System.out println "interrupt ran"))

    override val join: Task[A] = f.join
  }

  private[this] def toFiberFlatMapped[E1, E >: E1, A, B](f: Fiber[E1, A], flatMap: A => IO[E, B]): effect.Fiber[IO[E, ?], B] = new effect.Fiber[IO[E, ?], B] {
    override val cancel: IO[E, Unit] =
      IO.sync(System.out println "interruptFlatMapped running") *>
        f.interrupt.peek(_ => IO.sync(System.out println "interruptFlatMapped ran"))

    override val join: IO[E, B] = f.join.flatMap(flatMap)
  }

  override def cancelable[A](k: (Either[Throwable, A] => Unit) => effect.CancelToken[Task]): Task[A] =
    IO.async0 { kk: Callback[Throwable, A] =>
      System.out println "cancelable running"
      val token = try {
        k(e => kk(eitherToExitResult(e)))
      } catch {
        case e: Throwable =>
          System.out println s"cancelable error $e"
          throw e
      }
      val token0: Async[Nothing, A] = Async.maybeLater {
        IO.sync(System.out println "cancel token running") *>
        token.catchAll(IO.terminate)
      }
      System.out println "cancelable ran"
      token0
    }.peek(_ => Task(System.out println "cancelable finished"))

  override def race[A, B](fa: Task[A], fb: Task[B]): Task[Either[A, B]] =
    racePair(fa, fb).flatMap {
      case Left((a, fiberB))  =>
        fiberB.cancel.const(Left(a)).peek(_ => Task(System.out println "race: won A"))
      case Right((fiberA, b)) =>
        fiberA.cancel.const(Right(b)).peek(_ => Task(System.out println "race: won B"))
    }
      .supervised
      .catchAll(Task(System.out println "race: failed, got exception") *> IO.fail(_))

  override def start[A](fa: Task[A]): Task[effect.Fiber[Task, A]] =
    fa.fork.map(toFiber).peek(_ => Task(System.out println "start ran"))

  override def racePair[A, B](fa: Task[A],
                              fb: Task[B]): Task[Either[(A, effect.Fiber[Task, B]), (effect.Fiber[Task, A], B)]] =
    Ref(false).flatMap { finished =>
      (fa.sandboxWith(_.attempt) raceWith fb.sandboxWith(_.attempt))(
        { case (l, f) =>
          finished.set(true) *>
            fromEitherCancelOnError(l, f).map(lv => Left((lv, unsandboxFiber(f)))) },
        { case (r, f) =>
          finished.set(true) *>
            fromEitherCancelOnError(r, f).map(rv => Right((unsandboxFiber(f), rv))) }
      ).supervised { fibers =>
        for {
          finished <- finished.get
          _ <- IO.sync(System.out println s"On race interrupt, race got child fibers: ${fibers.size}, notCancel=$finished")
          _ <- IO.when(!finished)(Fiber.interruptAll(fibers))
          _ <- IO.sync(System.out println s"On race interrupt, TERMINATE FINISHED")
        } yield ()
      }
    }

  private def unsandboxFiber[E, A](f: Fiber[Nothing, Either[Cause[E], A]]): effect.Fiber[IO[E, ?], A] =
    toFiberFlatMapped[Nothing, E, Either[Cause[E], A], A](f, res => IO.unsandbox(IO.fromEither(res)))

  @inline final protected def fromEitherCancelOnError[E, A, B](res: ExitResult[Nothing, Either[Cause[E], A]], other: Fiber[Nothing, B]): IO[E, A] =
    res match {
      case Failed(e) =>
        other.interrupt *> IO.fail0(e)
      case Succeeded(Left(e)) =>
        other.interrupt *> IO.fail0(e)
      case Succeeded(Right(v)) =>
        IO.now(v)
    }
}

private class CatsEffect extends CatsMonadError[Throwable] with Effect[Task] with CatsSemigroupK[Throwable] with RTS {
  @inline final protected def exitResultToEither[A](e: ExitResult[Throwable, A]): Either[Throwable, A] =
    e.fold(Right(_), _.checked[Throwable] match {
      case t :: Nil => Left(t)
      case _        => e.toEither
    })

  @inline final protected def eitherToExitResult[A]: Either[Throwable, A] => ExitResult[Throwable, A] = {
    case Left(t)  => ExitResult.checked(t)
    case Right(r) => ExitResult.succeeded(r)
  }

  @inline final protected def exitResultToExitCase[A]: ExitResult[Throwable, A] => ExitCase[Throwable] = {
    case ExitResult.Succeeded(_)                         => ExitCase.Completed
    case ExitResult.Failed(cause) if cause.interrupted   => ExitCase.Canceled
    case ExitResult.Failed(cause)                        =>
      cause.checked match {
        case t :: Nil => ExitCase.Error(t)
        case _        => ExitCase.Error(FiberFailure(cause))
      }
  }

  override def never[A]: Task[A] =
    IO.never

  override def runAsync[A](
    fa: Task[A]
  )(cb: Either[Throwable, A] => effect.IO[Unit]): effect.SyncIO[Unit] =
    effect.SyncIO {
      this.unsafeRunAsync(fa) { exit =>
        cb(exitResultToEither(exit)).unsafeRunAsync(_ => ())
      }
    }

  override def async[A](k: (Either[Throwable, A] => Unit) => Unit): Task[A] =
    IO.async { kk: Callback[Throwable, A] =>
      k(eitherToExitResult andThen kk)
    }

  override def asyncF[A](k: (Either[Throwable, A] => Unit) => Task[Unit]): Task[A] =
    IO.asyncPure { kk: Callback[Throwable, A] =>
      k(eitherToExitResult andThen kk).catchAll(IO.terminate)
    }

  override def suspend[A](thunk: => Task[A]): Task[A] =
    IO.suspend(
      try {
        thunk
      } catch {
        case e: Throwable => IO.fail(e)
      }
    )

  override def delay[A](thunk: => A): Task[A] =
    IO.syncThrowable(thunk)

  override def bracket[A, B](acquire: Task[A])(use: A => Task[B])(
    release: A => Task[Unit]
  ): Task[B] =
    IO.bracket {
      Task(System.out println "acquire running") *>
        acquire.peek(_ => Task(System.out println "acquire ran"))
    } {
      release(_)
        .peek(_ => Task(System.out println "release ran"))
        .catchAll(IO.terminate)
    } { a =>
      Task(System.out println "use running") *>
        use(a)
          .peek(_ => Task(System.out println "use ran"))
      // FIXME: uncommenting one of these two lines breaks 'race cancels both' or 'race cancels loser' tests

//          .catchAll(IO.fail)
//          .run.flatMap(r => Task(System.out println s"use ran with $r") *> r.fold(IO.now, IO.fail0))
    }

  override def bracketCase[A, B](
    acquire: Task[A]
  )(use: A => Task[B])(release: (A, ExitCase[Throwable]) => Task[Unit]): Task[B] =
    IO.bracket0[Throwable, A, B] {
      Task(System.out println "acquire running") *>
        acquire.peek(_ => Task(System.out println "acquire ran"))
    } { (a, exitResult) =>
      val exitCase = exitResultToExitCase(exitResult)
      release(a, exitCase)
        .peek(_ => Task(System.out println "release ran"))
        .catchAll(IO.terminate)
    } { a =>
      Task(System.out println "use running") *>
        use(a)
          .peek(_ => Task(System.out println "use ran"))
//          .catchAll(IO.fail)
//          .run.flatMap(r => Task(System.out println s"use ran with $r") *> r.fold(IO.now, IO.fail0))
    }

  override def uncancelable[A](fa: Task[A]): Task[A] =
    fa.uninterruptibly

  override def guarantee[A](fa: Task[A])(finalizer: Task[Unit]): Task[A] =
    fa.ensuring(finalizer.catchAll(IO.terminate))
}

private class CatsMonad[E] extends Monad[IO[E, ?]] {
  override def pure[A](a: A): IO[E, A]                                 = IO.now(a)
  override def map[A, B](fa: IO[E, A])(f: A => B): IO[E, B]            = fa.map(f)
  override def flatMap[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] = fa.flatMap(f)
  override def tailRecM[A, B](a: A)(f: A => IO[E, Either[A, B]]): IO[E, B] =
    f(a).flatMap {
      case Left(l)  => tailRecM(l)(f)
      case Right(r) => IO.now(r)
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
    Par(IO.now(x))

  final override def map2[A, B, Z](fa: ParIO[E, A], fb: ParIO[E, B])(f: (A, B) => Z): ParIO[E, Z] =
    Par(Par.unwrap(fa).par(Par.unwrap(fb)).map(f.tupled))

  final override def ap[A, B](ff: ParIO[E, A => B])(fa: ParIO[E, A]): ParIO[E, B] =
    Par(Par.unwrap(ff).flatMap(Par.unwrap(fa).map))

  final override def product[A, B](fa: ParIO[E, A], fb: ParIO[E, B]): ParIO[E, (A, B)] =
    map2(fa, fb)(_ -> _)

  final override def map[A, B](fa: ParIO[E, A])(f: A => B): ParIO[E, B] =
    Par(Par.unwrap(fa).map(f))

  final override def unit: ParIO[E, Unit] =
    Par(IO.unit)
}
