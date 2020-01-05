/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

/**
 * A `ZLayer[A, E, B]` describes a layer of an application: every layer in an
 * application requires some services (the input) and produces some services
 * (the output).
 *
 * Layers can be thought of as recipes for producing bundles of services, given
 * their dependencies (other services).
 *
 * Construction of layers can be effectful and utilize resources that must be
 * acquired and safetly released when the services are done being utilized.
 */
final case class ZLayer[-RIn <: Has[_], +E, +ROut <: Has[_]](value: ZManaged[RIn, E, ROut]) { self =>
  def >>>[E1 >: E, ROut2 <: Has[_]](that: ZLayer[ROut, E1, ROut2]): ZLayer[RIn, E1, ROut2] =
    ZLayer(self.value.flatMap(v => that.value.provide(v)))

  def ++[E1 >: E, RIn2 <: Has[_], ROut2 <: Has[_]](
    that: ZLayer[RIn2, E1, ROut2]
  ): ZLayer[RIn with RIn2, E1, ROut with ROut2] =
    ZLayer(
      ZManaged.accessManaged[RIn with RIn2] { env =>
        (self.value.provide(env) zipWith that.value.provide(env))((l, r) => l.merge[ROut2](r))
      }
    )

  def build[RIn2 <: RIn](implicit ev: Has.Any =:= RIn2): Managed[E, ROut] = value.provide(ev(Has.any))
}
object ZLayer {
  def fromEffect[E, A <: Has[_]](zio: IO[E, A]): ZLayer[Has.Any, E, A] = ZLayer(ZManaged.fromEffect(zio))

  def fromFunction[A <: Has[_], E, B <: Has[_]: Tagged](f: A => B): ZLayer[A, E, B] =
    ZLayer(ZManaged.fromEffect(ZIO.access[A](m => f(m))))

  def fromFunctionM[A <: Has[_], R <: Has[_], E, B <: Has[_]: Tagged](f: A => ZIO[R, E, B]): ZLayer[R with A, E, B] =
    ZLayer(ZManaged.fromEffect(ZIO.accessM[R with A](m => f(m))))

  def fromFunctionManaged[A <: Has[_], E, B <: Has[_]: Tagged](f: A => Managed[E, B]): ZLayer[A, E, B] =
    ZLayer(ZManaged.accessManaged[A](m => f(m)))

  def fromManaged[E, A <: Has[_]](m: Managed[E, A]): ZLayer[Has.Any, E, A] = ZLayer(m)

  def succeed[A: Tagged](a: A): ZLayer[Has.Any, Nothing, Has[A]] = ZLayer(ZManaged.succeed(Has(a)))
}
