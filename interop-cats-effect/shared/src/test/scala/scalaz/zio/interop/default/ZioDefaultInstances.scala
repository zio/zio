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
package default

import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.{ Applicative, Monad }
import scalaz.zio.Exit.{ Failure, Success }
import scalaz.zio.clock.Clock
import scalaz.zio.duration.{ Duration => zioDuration }
import scalaz.zio.interop.bio.instances.ZioFiber2
import scalaz.zio.interop.bio.{
  Async2,
  Concurrent2,
  Errorful2,
  Fiber2,
  Guaranteed2,
  RunAsync2,
  RunSync2,
  Sync2,
  Temporal2
}
import scalaz.zio.interop.default.ZioDefaultInstances._

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

  implicit final val zioTestRunAsync2: RunAsync2[IO] =
    new ZioRunAsync2 {}
}

private[default] object ZioDefaultInstances {

  private[default] sealed trait ZioGuaranteed2 extends Guaranteed2[IO] {

    override def applicative[E]: Applicative[IO[E, ?]] =
      new Applicative[IO[E, ?]] {

        def pure[A](x: A): IO[E, A] =
          IO.succeed(x)

        def ap[A, B](ff: IO[E, A => B])(fa: IO[E, A]): IO[E, B] =
          fa >>= (a => ff map (f => f(a)))
      }

    override def guarantee[E, A](fa: IO[E, A], finalizer: IO[Nothing, Unit]): IO[E, A] =
      fa.ensuring(finalizer)

    override def bimap[A, B, C, D](fab: IO[A, B])(f: A => C, g: B => D): IO[C, D] =
      fab.bimap(f, g)
  }

  private[default] sealed trait ZioErrorful2 extends Errorful2[IO] with ZioGuaranteed2 {

    override def monad[E]: Monad[IO[E, ?]] =
      new Monad[IO[E, ?]] {
        def pure[A](x: A): IO[E, A] =
          IO.succeed(x)

        def flatMap[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] =
          fa >>= f

        def tailRecM[A, B](a: A)(f: A => IO[E, Either[A, B]]): IO[E, B] =
          f(a) >>= {
            case Left(x)  => tailRecM(x)(f)
            case Right(b) => IO.succeed(b)
          }
      }

    override def raiseError[E](e: E): IO[E, Nothing] =
      IO.fail(e)

    override def redeemWith[E1, E2, A, B](fa: IO[E1, A])(
      failure: E1 => IO[E2, B],
      success: A => IO[E2, B]
    ): IO[E2, B] =
      fa.foldM(failure, success)
  }

  private[default] sealed trait ZioTemporal2 extends Temporal2[IO] with ZioErrorful2 {

    implicit def runtime: Runtime[Clock]

    override def now: IO[Nothing, Instant] =
      runtime.Environment.clock.currentTime(TimeUnit.MILLISECONDS) map Instant.ofEpochMilli

    override def sleep(duration: Duration): IO[Nothing, Unit] =
      runtime.Environment.clock.sleep(zioDuration.fromScala(duration))
  }

  private[default] sealed trait ZioConcurrent2 extends Concurrent2[IO] with ZioTemporal2 {

    override def start[E, A](fa: IO[E, A]): IO[Nothing, Fiber2[IO, E, A]] =
      fa.fork map ZioFiber2.fromFiber

    override def uninterruptible[E, A](fa: IO[E, A]): IO[E, A] =
      fa.uninterruptible

    override def interrupted: IO[Nothing, Nothing] =
      IO.interrupt

    override def yieldTo[E, A](fa: IO[E, A]): IO[E, A] =
      IO.yieldNow *> fa

    override def evalOn[E, A](fa: IO[E, A], ec: ExecutionContext): IO[E, A] =
      fa.on(ec)
  }

  private[default] sealed trait ZioSync2 extends Sync2[IO] with ZioErrorful2 {

    override def delay[A](a: => A): IO[Nothing, A] =
      IO.effectTotal(a)
  }

  private[default] sealed trait ZioRunSync2 extends RunSync2[IO] with ZioSync2 {

    implicit def runtime: DefaultRuntime

    override def runSync[G[+ _, + _], E, A](fa: IO[E, A])(implicit SG: Sync2[G], CG: Concurrent2[G]): G[E, A] = {

      import scalaz.zio.interop.bio._

      SG.delay(runtime.unsafeRunSync(fa.either)) >>= {
        case Success(ea) => ea.fold(SG.raiseError, SG.monad.pure(_))
        case Failure(_)  => CG.interrupted
      }
    }
  }

  private[default] sealed trait ZioAsync2 extends Async2[IO] with ZioSync2 {

    override def asyncMaybe[E, A](k: (IO[E, A] => Unit) => Option[IO[E, A]]): IO[E, A] =
      IO.effectAsyncMaybe(k)

    override def asyncF[E, A](k: (IO[E, A] => Unit) => IO[Nothing, Unit]): IO[E, A] =
      IO.effectAsyncM(k)
  }

  private[default] sealed trait ZioRunAsync2 extends RunAsync2[IO] with ZioAsync2 {

    override def runAsync[G[+ _, + _], E, A](fa: IO[E, A], k: Either[E, A] => G[Nothing, Unit])(
      implicit G: Sync2[G]
    ): G[Nothing, Unit] = ???
  }
}
