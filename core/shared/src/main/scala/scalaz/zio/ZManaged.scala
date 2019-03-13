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

/**
 * A `ManagedR[R, E, A]` is a managed resource of type `A`, which may be used by
 * invoking the `use` method of the resource. The resource will be automatically
 * acquired before the resource is used, and automatically released after the
 * resource is used.
 *
 * Resources do not survive the scope of `use`, meaning that if you attempt to
 * capture the resource, leak it from `use`, and then use it after the resource
 * has been consumed, the resource will not be valid anymore and may fail with
 * some checked error, as per the type of the functions provided by the resource.
 */
final case class ZManaged[-R, +E, +A](reserve: ZIO[R, E, ZManaged.Reservation[R, E, A]]) { self =>
  import ZManaged.Reservation

  def use[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    reserve.bracket(_.release)(_.acquire.flatMap(f))

  final def use_[R1 <: R, E1 >: E, B](f: ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    use(_ => f)

  final def map[B](f0: A => B): ZManaged[R, E, B] =
    ZManaged[R, E, B] {
      self.reserve.map(token => token.copy(acquire = token.acquire.map(f0)))
    }

  def mapError[E1](f: E => E1): ZManaged[R, E1, A] =
    Managed(reserve.mapError(f).map(r => Reservation(r.acquire.mapError(f), r.release)))

  def provideSome[R0](f: R0 => R): ZManaged[R0, E, A] =
    Managed(reserve.provideSome(f).map(r => Reservation(r.acquire.provideSome(f), r.release.provideSome(f))))

  final def flatMap[R1 <: R, E1 >: E, B](f0: A => ZManaged[R1, E1, B]): ZManaged[R1, E1, B] =
    ZManaged[R1, E1, B] {
      Ref.make[ZIO[R1, Nothing, Any]](IO.unit).map { finalizers =>
        Reservation(
          acquire = for {
            resR <- self.reserve
                     .flatMap(res => finalizers.update(fs => res.release *> fs).const(res))
                     .uninterruptible
            r <- resR.acquire
            resR1 <- f0(r).reserve
                      .flatMap(res => finalizers.update(fs => res.release *> fs).const(res))
                      .uninterruptible
            r1 <- resR1.acquire
          } yield r1,
          release = ZIO.flatten(finalizers.get)
        )
      }
    }

  final def *>[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A1] =
    flatMap(_ => that)

  /**
   * Named alias for `*>`.
   */
  final def zipRight[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A1] =
    self *> that

  final def <*[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A] =
    flatMap(r => that.map(_ => r))

  /**
   * Named alias for `<*`.
   */
  final def zipLeft[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A] =
    self <* that

  final def zipWith[R1 <: R, E1 >: E, A1, A2](that: ZManaged[R1, E1, A1])(f: (A, A1) => A2): ZManaged[R1, E1, A2] =
    flatMap(r => that.map(r1 => f(r, r1)))

  final def zip[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, (A, A1)] =
    zipWith(that)((_, _))

  final def zipWithPar[R1 <: R, E1 >: E, A1, A2](that: ZManaged[R1, E1, A1])(f0: (A, A1) => A2): ZManaged[R1, E1, A2] =
    ZManaged[R1, E1, A2] {
      Ref.make[ZIO[R1, Nothing, Any]](IO.unit).map { finalizers =>
        Reservation(
          acquire = {
            val left =
              self.reserve.flatMap(res => finalizers.update(fs => res.release *> fs).const(res)).uninterruptible
            val right =
              that.reserve.flatMap(res => finalizers.update(fs => res.release *> fs).const(res)).uninterruptible

            left.flatMap(_.acquire).zipWithPar(right.flatMap(_.acquire))(f0)
          },
          release = ZIO.flatten(finalizers.get)
        )
      }
    }

  final def zipPar[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, (A, A1)] =
    zipWithPar(that)((_, _))
}

object ZManaged {
  final case class Reservation[-R, +E, +A](acquire: ZIO[R, E, A], release: ZIO[R, Nothing, _])

  /**
   * Lifts a `ZIO[R, E, R]` into `ManagedR[R, E, R]` with a release action.
   */
  final def make[R, E, A](acquire: ZIO[R, E, A])(release: A => ZIO[R, Nothing, _]): ZManaged[R, E, A] =
    ZManaged(acquire.map(r => Reservation(IO.succeed(r), release(r))))

  /**
   * Lifts a ZIO[R, E, R] into ManagedR[R, E, R] with no release action. Use
   * with care.
   */
  final def liftIO[R, E, A](fa: ZIO[R, E, A]): ZManaged[R, E, A] =
    ZManaged(IO.succeed(Reservation(fa, IO.unit)))

  /**
   * Unwraps a `ManagedR` that is inside a `ZIO`.
   */
  final def unwrap[R, E, A](fa: ZIO[R, E, ZManaged[R, E, A]]): ZManaged[R, E, A] =
    ZManaged(fa.flatMap(_.reserve))

  /**
   * Lifts a strict, pure value into a Managed.
   */
  final def succeed[R, A](r: A): ZManaged[R, Nothing, A] =
    ZManaged(IO.succeed(Reservation(IO.succeed(r), IO.unit)))

  /**
   * Lifts a by-name, pure value into a Managed.
   */
  final def succeedLazy[R, A](r: => A): ZManaged[R, Nothing, A] =
    ZManaged(IO.succeed(Reservation(IO.succeedLazy(r), IO.unit)))

  final def foreach[R, E, A1, A2](as: Iterable[A1])(f: A1 => ZManaged[R, E, A2]): ZManaged[R, E, List[A2]] =
    as.foldRight[ZManaged[R, E, List[A2]]](succeed(Nil)) { (a, m) =>
      f(a).zipWith(m)(_ :: _)
    }

  final def collectAll[R, E, A1, A2](ms: Iterable[ZManaged[R, E, A2]]): ZManaged[R, E, List[A2]] =
    foreach(ms)(identity)
}
