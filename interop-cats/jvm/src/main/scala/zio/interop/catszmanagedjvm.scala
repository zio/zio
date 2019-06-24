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

import cats.arrow.FunctionK
import cats.effect.Resource.{ Allocate, Bind, Suspend }
import cats.effect.{ ConcurrentEffect, ExitCase, LiftIO, Resource, Sync, IO => CIO }
import cats.{ effect, Bifunctor, Monad, MonadError, Monoid, Semigroup, SemigroupK }

trait CatsZManagedSyntax {
  import scala.language.implicitConversions

  implicit final def catsIOResourceSyntax[F[_], A](resource: Resource[F, A]): CatsIOResourceSyntax[F, A] =
    new CatsIOResourceSyntax(resource)

  implicit final def zManagedSyntax[R, E, A](managed: ZManaged[R, E, A]): ZManagedSyntax[R, E, A] =
    new ZManagedSyntax(managed)

}

final class CatsIOResourceSyntax[F[_], A](private val resource: Resource[F, A]) extends AnyVal {

  /**
   * Convert a cats Resource into a ZManaged.
   * Beware that unhandled error during release of the resource will result in the fiber dying.
   */
  def toManaged[R](implicit l: LiftIO[ZIO[R, Throwable, ?]], ev: ConcurrentEffect[F]): ZManaged[R, Throwable, A] = {
    def convert[A1](resource: Resource[CIO, A1]): ZManaged[R, Throwable, A1] =
      resource match {
        case Allocate(res) =>
          ZManaged.unwrap(
            l.liftIO(res.bracketCase {
                case (a, r) =>
                  CIO.delay(
                    ZManaged.reserve(Reservation(ZIO.succeed(a), l.liftIO(r(ExitCase.Completed)).orDie.uninterruptible))
                  )
              } {
                case (_, ExitCase.Completed) =>
                  CIO.unit
                case ((_, release), ec) =>
                  release(ec)
              })
              .uninterruptible
          )
        case Bind(source, fs) =>
          convert(source).flatMap(s => convert(fs(s)))
        case Suspend(res) =>
          ZManaged.unwrap(l.liftIO(res).map(convert))
      }

    convert(resource.mapK(FunctionK.lift(ev.toIO)))
  }
}

final class ZManagedSyntax[R, E, A](private val managed: ZManaged[R, E, A]) extends AnyVal {

  def toResource[F[_]](implicit r: Runtime[R], S: Sync[F]): Resource[F, A] =
    Resource.suspend(S.delay {
      r.unsafeRun(managed.reserve) match {
        case Reservation(acquire, release) =>
          Resource.make(S.delay(r.unsafeRun(acquire)))(_ => S.delay(r.unsafeRun(release.unit)))
      }
    })

}

trait CatsZManagedInstances extends CatsZManagedInstances1 {

  implicit def monadErrorZManagedInstances[R, E]: MonadError[ZManaged[R, E, ?], E] =
    new CatsZManagedMonadError

  implicit def monoidZManagedInstances[R, E, A](implicit ev: Monoid[A]): Monoid[ZManaged[R, E, A]] =
    new Monoid[ZManaged[R, E, A]] {
      override def empty: ZManaged[R, E, A] = ZManaged.succeed(ev.empty)

      override def combine(x: ZManaged[R, E, A], y: ZManaged[R, E, A]): ZManaged[R, E, A] = x.zipWith(y)(ev.combine)
    }

  implicit def liftIOZManagedInstances[R, A](
    implicit ev: LiftIO[ZIO[R, Throwable, ?]]
  ): LiftIO[ZManaged[R, Throwable, ?]] =
    new LiftIO[ZManaged[R, Throwable, ?]] {
      override def liftIO[A](ioa: effect.IO[A]): ZManaged[R, Throwable, A] =
        ZManaged.fromEffect(ev.liftIO(ioa))
    }

}

sealed trait CatsZManagedInstances1 {

  implicit def monadZManagedInstances[R, E]: Monad[ZManaged[R, E, ?]] = new CatsZManagedMonad

  implicit def semigroupZManagedInstances[R, E, A](implicit ev: Semigroup[A]): Semigroup[ZManaged[R, E, A]] =
    (x: ZManaged[R, E, A], y: ZManaged[R, E, A]) => x.zipWith(y)(ev.combine)

  implicit def semigroupKZManagedInstances[R, E]: SemigroupK[ZManaged[R, E, ?]] = new CatsZManagedSemigroupK

  implicit def bifunctorZManagedInstances[R]: Bifunctor[ZManaged[R, ?, ?]] = new Bifunctor[ZManaged[R, ?, ?]] {
    override def bimap[A, B, C, D](fab: ZManaged[R, A, B])(f: A => C, g: B => D): ZManaged[R, C, D] =
      fab.mapError(f).map(g)
  }

}

private class CatsZManagedMonad[R, E] extends Monad[ZManaged[R, E, ?]] {
  override def pure[A](x: A): ZManaged[R, E, A] = ZManaged.succeed(x)

  override def flatMap[A, B](fa: ZManaged[R, E, A])(f: A => ZManaged[R, E, B]): ZManaged[R, E, B] = fa.flatMap(f)

  override def tailRecM[A, B](a: A)(f: A => ZManaged[R, E, Either[A, B]]): ZManaged[R, E, B] =
    ZManaged.succeedLazy(f(a)).flatMap(identity).flatMap {
      case Left(nextA) => tailRecM(nextA)(f)
      case Right(b)    => ZManaged.succeed(b)
    }
}

private class CatsZManagedMonadError[R, E] extends CatsZManagedMonad[R, E] with MonadError[ZManaged[R, E, ?], E] {
  override def raiseError[A](e: E): ZManaged[R, E, A] = ZManaged.fromEffect(ZIO.fail(e))

  override def handleErrorWith[A](fa: ZManaged[R, E, A])(f: E => ZManaged[R, E, A]): ZManaged[R, E, A] =
    fa.catchAll(f)
}

/**
 * lossy, throws away errors using the "first success" interpretation of SemigroupK
 */
private class CatsZManagedSemigroupK[R, E] extends SemigroupK[ZManaged[R, E, ?]] {
  override def combineK[A](x: ZManaged[R, E, A], y: ZManaged[R, E, A]): ZManaged[R, E, A] =
    x.orElse(y)
}
