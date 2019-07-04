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

package zio
package interop
package default

import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.data.NonEmptyList
import cats.syntax.either._
import cats.{ Applicative, Monad }
import zio.Exit.{ Failure, Success }
import zio.clock.Clock
import zio.duration.{ Duration => zioDuration }
import zio.interop.bio.Failed.{ Defects, Errors, Interrupt }
import zio.interop.bio.data.{ Deferred2, Ref2 }
import zio.interop.bio.{
  Async2,
  Bracket2,
  Concurrent2,
  ConcurrentData2,
  Errorful2,
  Failed,
  Fiber2,
  Guaranteed2,
  Interruption2,
  RunAsync2,
  RunSync2,
  Sync2,
  Temporal2
}
import zio.interop.default.ZioDefaultInstances._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

private[default] abstract class ZioDefaultInstances extends ZioDefaultInstances2 {

  implicit final def zioTestConcurrent2(implicit rts: Runtime[Clock]): Concurrent2[IO] =
    new ZioConcurrent2 { implicit val runtime = rts }
}

private[default] abstract class ZioDefaultInstances2 extends ZioDefaultInstances3 {

  implicit final def zioTestRunSync2(implicit rts: DefaultRuntime): RunSync2[IO] =
    new ZioRunSync2 { implicit val runtime = rts }
}

private[default] abstract class ZioDefaultInstances3 {

  implicit final def zioTestRunAsync2(implicit rts: DefaultRuntime): RunAsync2[IO] =
    new ZioRunAsync2 { implicit val runtime = rts }
}

private[default] object ZioDefaultInstances {

  private[default] sealed trait ZioInterruption2 extends Interruption2[IO] {

    override def uninterruptible[E, A](fa: IO[E, A]): IO[E, A] =
      fa.uninterruptible
  }

  private[default] sealed trait ZioGuaranteed2 extends Guaranteed2[IO] with ZioInterruption2 {

    override def applicative[E]: Applicative[IO[E, ?]] =
      new Applicative[IO[E, ?]] {

        def pure[A](x: A): IO[E, A] =
          IO.succeed(x)

        def ap[A, B](ff: IO[E, A => B])(fa: IO[E, A]): IO[E, B] =
          fa >>= (a => ff map (f => f(a)))
      }

    override def guarantee[E, A](fa: IO[E, A], finalizer: IO[Nothing, Unit]): IO[E, A] =
      fa.ensuring(finalizer)
  }

  private[default] sealed trait ZioErrorful2 extends ZioGuaranteed2 with Errorful2[IO] {

    override def monad[E]: Monad[IO[E, ?]] =
      new Monad[IO[E, ?]] {
        def pure[A](x: A): IO[E, A] =
          IO.succeed(x)

        def flatMap[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] =
          fa >>= f

        def tailRecM[A, B](a: A)(f: A => IO[E, Either[A, B]]): IO[E, B] =
          IO.suspend(f(a)) >>= {
            case Left(x)  => tailRecM(x)(f)
            case Right(b) => IO.succeed(b)
          }
      }

    override def unsuccessful[E, A](e: Failed[E]): IO[E, Nothing] =
      e match {
        case Errors(fs)  => IO.halt(fs map Cause.fail reduceLeft (_ && _))
        case Defects(ts) => IO.halt(ts map Cause.die reduceLeft (_ && _))
        case Interrupt   => IO.interrupt
      }

    override def redeemWith[E1, E2, A, B](fa: IO[E1, A])(
      failure: E1 => IO[E2, B],
      success: A => IO[E2, B]
    ): IO[E2, B] =
      fa.foldM(failure, success)
  }

  private[default] sealed trait ZioBracket2 extends Bracket2[IO] with ZioGuaranteed2 with ZioErrorful2

  private[default] sealed trait ZioTemporal2 extends Temporal2[IO] with ZioBracket2 {

    implicit def runtime: Runtime[Clock]

    override def now: IO[Nothing, Instant] =
      runtime.Environment.clock.currentTime(TimeUnit.MILLISECONDS) map Instant.ofEpochMilli

    override def sleep(duration: Duration): IO[Nothing, Unit] =
      runtime.Environment.clock.sleep(zioDuration.fromScala(duration))
  }

  private[default] sealed trait ZioConcurrent2 extends Concurrent2[IO] with ZioTemporal2 {

    override def concurrentData: ConcurrentData2[IO] =
      new ZioConcurrentData2 {}

    override def start[E, A](fa: IO[E, A]): IO[Nothing, Fiber2[IO, E, A]] =
      fa.fork map fromFiber

    override def yieldTo[E, A](fa: IO[E, A]): IO[E, A] =
      IO.yieldNow *> fa

    override def evalOn[E, A](fa: IO[E, A], ec: ExecutionContext): IO[E, A] =
      fa.on(ec)

    private[this] def fromFiber[E, A](fiber: Fiber[E, A]): Fiber2[IO, E, A] =
      new Fiber2[IO, E, A] {

        def await: IO[Nothing, Either[Failed[E], A]] =
          fiber.await >>= fromExit

        def cancel: IO[Nothing, Either[Failed[E], A]] =
          fiber.await >>= fromExit

        def join: IO[E, A] =
          fiber.join
      }

    private[this] def fromExit[E, A](exit: Exit[E, A]): IO[Nothing, Either[Failed[E], A]] =
      exit match {
        case Success(a) =>
          IO.succeed(a.asRight)

        case Failure(cause) =>
          if (cause.died || cause.interrupted || cause.failures.isEmpty) IO.succeed(Interrupt.asLeft)
          else IO.succeed(Errors(NonEmptyList.fromListUnsafe(cause.failures)).asLeft)
      }
  }

  private[default] sealed trait ZioSync2 extends Sync2[IO] with ZioErrorful2 {

    override def delay[A](a: => A): IO[Nothing, A] =
      IO.effectTotal(a)
  }

  private[default] sealed trait ZioRunSync2 extends RunSync2[IO] with ZioSync2 {

    implicit def runtime: DefaultRuntime

    override def runSync[G[+_, +_], E, A](fa: IO[E, A])(implicit sync: Sync2[G]): G[E, A] =
      sync.suspend(
        runtime.unsafeRunSync(fa) match {
          case Success(a) => sync.monad pure a
          case Failure(c) => sync.unsuccessful(failedCause(c))
        }
      )
  }

  private[default] sealed trait ZioAsync2 extends Async2[IO] with ZioSync2 {

    override def asyncMaybe[E, A](k: (IO[E, A] => Unit) => Option[IO[E, A]]): IO[E, A] =
      IO.effectAsyncMaybe(k)

    override def asyncF[E, A](k: (IO[E, A] => Unit) => IO[Nothing, Unit]): IO[E, A] =
      IO.effectAsyncM(k)
  }

  private[default] sealed trait ZioRunAsync2 extends RunAsync2[IO] with ZioAsync2 {

    implicit def runtime: DefaultRuntime

    override def runAsync[G[+_, +_], E, A](fa: IO[E, A], k: Either[E, A] => G[Nothing, Unit])(
      implicit async: Async2[G]
    ): G[Nothing, Unit] =
      async.async { cb =>
        runtime.unsafeRunSync(fa.either) match {
          case Success(a) => cb(k(a))
          case Failure(c) => cb(async.unsuccessful(failedCause(c)))
        }
      }
  }

  private[default] sealed trait ZioConcurrentData2 extends ConcurrentData2[IO] {

    def ref[A](a: A): IO[Nothing, Ref2[IO, A]] =
      Ref.make(a) map { r =>
        new Ref2[IO, A] {
          def get: IO[Nothing, A] = r.get

          def set(a: A): IO[Nothing, Unit] = r set a

          def setAsync(a: A): IO[Nothing, Unit] = r setAsync a

          def update(f: A => A): IO[Nothing, A] = r update f

          def updateSome(pf: PartialFunction[A, A]): IO[Nothing, A] = r updateSome pf

          def modify[B](f: A => (B, A)): IO[Nothing, B] = r modify f

          def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): IO[Nothing, B] = r.modifySome(default)(pf)
        }
      }

    def deferred[E, A]: IO[Nothing, Deferred2[IO, E, A]] =
      Promise.make[E, A] map { p =>
        new Deferred2[IO, E, A] {
          def await: IO[E, A] = p.await

          def done(fa: IO[E, A]): IO[Nothing, Boolean] = p.done(fa)
        }
      }
  }

  private[this] def failedCause[E](c: Cause[E]): Failed[E] =
    if (c.died) Defects(NonEmptyList.fromListUnsafe(c.defects))
    else if (c.interrupted) Interrupt
    else if (c.failed) Errors(NonEmptyList.fromListUnsafe(c.failures))
    else Defects(NonEmptyList.one(new RuntimeException("Inconsistent error state"))) // This failure message can be improved
}
