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

import zio.internal.Platform

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
 *
 * By default layers are shared, meaning that if the same layer is used twice
 * the layer will only be allocated a single time.
 *
 * Because of their excellent composition properties, layers are the idiomatic
 * way in ZIO to create services that depend on other services.
 */
final class ZLayer[-RIn, +E, +ROut <: Has[_]] private (
  private val scope: Managed[Nothing, ZLayer.MemoMap => ZManaged[RIn, E, ROut]]
) { self =>

  /**
   * Feeds the output services of this layer into the input of the specified
   * layer, resulting in a new layer with the inputs of this layer, and the
   * outputs of the specified layer.
   */
  def >>>[E1 >: E, ROut2 <: Has[_]](that: ZLayer[ROut, E1, ROut2]): ZLayer[RIn, E1, ROut2] =
    fold(ZLayer.fromFunctionManyM(ZIO.failNow), that)

  /**
   * Combines this layer with the specified layer, producing a new layer that
   * has the inputs of both layers, and the outputs of both layers.
   */
  def ++[E1 >: E, RIn2, ROut1 >: ROut <: Has[_], ROut2 <: Has[_]](
    that: ZLayer[RIn2, E1, ROut2]
  )(implicit tagged: Tagged[ROut2]): ZLayer[RIn with RIn2, E1, ROut1 with ROut2] =
    zipWithPar(that)(_.union[ROut2](_))

  def +!+[E1 >: E, RIn2, ROut2 <: Has[_]](
    that: ZLayer[RIn2, E1, ROut2]
  ): ZLayer[RIn with RIn2, E1, ROut with ROut2] =
    zipWithPar(that)(_.unionAll[ROut2](_))

  /**
   * Builds a layer into a managed value.
   */
  def build: ZManaged[RIn, E, ROut] =
    for {
      memoMap <- ZLayer.MemoMap.make.toManaged_
      run     <- self.scope
      value   <- run(memoMap)
    } yield value

  /**
   * Feeds the error or output services of this layer into the input of either
   * the specified `failure` or `success` layers, resulting in a new layer with
   * the inputs of this layer, and the error or outputs of the specified layer.
   */
  def fold[E1, ROut2 <: Has[_]](
    failure: ZLayer[E, E1, ROut2],
    success: ZLayer[ROut, E1, ROut2]
  ): ZLayer[RIn, E1, ROut2] =
    new ZLayer(
      ZManaged.finalizerRef(_ => UIO.unit).map { finalizers => memoMap =>
        memoMap
          .getOrElseMemoize(self, finalizers)
          .foldM(
            e => memoMap.getOrElseMemoize(failure, finalizers).provide(e),
            r => memoMap.getOrElseMemoize(success, finalizers).provide(r)
          )
      }
    )

  /**
   * Creates a fresh version of this layer that will not be shared.
   */
  def fresh: ZLayer[RIn, E, ROut] =
    new ZLayer(self.scope)

  /**
   * Returns a new layer whose output is mapped by the specified function.
   */
  def map[ROut1 <: Has[_]](f: ROut => ROut1): ZLayer[RIn, E, ROut1] =
    self >>> ZLayer.fromFunctionMany(f)

  /**
   * Returns a layer with its error channel mapped using the specified
   * function.
   */
  def mapError[E1](f: E => E1): ZLayer[RIn, E1, ROut] =
    fold(ZLayer.fromFunctionManyM(f andThen ZIO.failNow), ZLayer.identity)

  /**
   * Returns a managed effect that, if evaluated, will return the lazily
   * computed result of this layer.
   */
  def memoize: ZManaged[Any, Nothing, ZLayer[RIn, E, ROut]] =
    build.memoize.map(ZLayer(_))

  /**
   * Converts a layer that requires no services into a managed runtime, which
   * can be used to execute effects.
   */
  def toRuntime(p: Platform)(implicit ev: Any <:< RIn): Managed[E, Runtime[ROut]] =
    build.provide(ev).map(Runtime(_, p))

  /**
   * Updates one of the services output by this layer.
   */
  def update[A: Tagged](f: A => A)(implicit ev: ROut <:< Has[A]): ZLayer[RIn, E, ROut] =
    self >>> ZLayer.fromFunctionMany(_.update[A](f))

  /**
   * Combines this layer with the specified layer, producing a new layer that
   * has the inputs of both layers, and the outputs of both layers combined
   * using the specified function.
   */
  def zipWithPar[E1 >: E, RIn2, ROut1 >: ROut <: Has[_], ROut2 <: Has[_], ROut3 <: Has[_]](
    that: ZLayer[RIn2, E1, ROut2]
  )(f: (ROut, ROut2) => ROut3): ZLayer[RIn with RIn2, E1, ROut3] =
    new ZLayer(
      ZManaged.finalizerRef(_ => UIO.unit).map { finalizers => memoMap =>
        memoMap.getOrElseMemoize(self, finalizers).zipWithPar(memoMap.getOrElseMemoize(that, finalizers))(f)
      }
    )
}

object ZLayer {
  type NoDeps[+E, +B <: Has[_]] = ZLayer[Any, E, B]

  /**
   * Constructs a layer from a managed resource.
   */
  def apply[RIn, E, ROut <: Has[_]](managed: ZManaged[RIn, E, ROut]): ZLayer[RIn, E, ROut] =
    new ZLayer(Managed.succeed(_ => managed))

  /**
   * Constructs a layer from acquire and release actions. The acquire and
   * release actions will be performed uninterruptibly.
   */
  def fromAcquireRelease[R, E, A: Tagged](acquire: ZIO[R, E, A])(release: A => URIO[R, Any]): ZLayer[R, E, Has[A]] =
    fromManaged(ZManaged.make(acquire)(release))

  /**
   * Constructs a layer from acquire and release actions, which must return one
   * or more services. The acquire and release actions will be performed
   * uninterruptibly.
   */
  def fromAcquireReleaseMany[R, E, A <: Has[_]](acquire: ZIO[R, E, A])(release: A => URIO[R, Any]): ZLayer[R, E, A] =
    fromManagedMany(ZManaged.make(acquire)(release))

  /**
   * Constructs a layer from the specified effect.
   */
  def fromEffect[R, E, A: Tagged](zio: ZIO[R, E, A]): ZLayer[R, E, Has[A]] =
    fromEffectMany(zio.asService)

  /**
   * Constructs a layer from the specified effect, which must return one or
   * more services.
   */
  def fromEffectMany[R, E, A <: Has[_]](zio: ZIO[R, E, A]): ZLayer[R, E, A] =
    ZLayer(ZManaged.fromEffect(zio))

  /**
   * Constructs a layer from the environment using the specified function.
   */
  def fromFunction[A, B: Tagged](f: A => B): ZLayer[A, Nothing, Has[B]] =
    fromFunctionM(a => ZIO.succeedNow(f(a)))

  /**
   * Constructs a layer from the environment using the specified effectful
   * function.
   */
  def fromFunctionM[A, E, B: Tagged](f: A => IO[E, B]): ZLayer[A, E, Has[B]] =
    fromFunctionManaged(a => f(a).toManaged_)

  /**
   * Constructs a layer from the environment using the specified effectful
   * resourceful function.
   */
  def fromFunctionManaged[A, E, B: Tagged](f: A => Managed[E, B]): ZLayer[A, E, Has[B]] =
    fromManaged(ZManaged.fromFunctionM(f))

  /**
   * Constructs a layer from the environment using the specified function,
   * which must return one or more services.
   */
  def fromFunctionMany[A, B <: Has[_]](f: A => B): ZLayer[A, Nothing, B] =
    fromFunctionManyM(a => ZIO.succeedNow(f(a)))

  /**
   * Constructs a layer from the environment using the specified effectful
   * function, which must return one or more services.
   */
  def fromFunctionManyM[A, E, B <: Has[_]](f: A => IO[E, B]): ZLayer[A, E, B] =
    fromFunctionManyManaged(a => f(a).toManaged_)

  /**
   * Constructs a layer from the environment using the specified effectful
   * resourceful function, which must return one or more services.
   */
  def fromFunctionManyManaged[A, E, B <: Has[_]](f: A => Managed[E, B]): ZLayer[A, E, B] =
    ZLayer(ZManaged.fromFunctionM(f))

  /**
   * Constructs a layer that purely depends on the specified service.
   */
  def fromService[A: Tagged, B: Tagged](f: A => B): ZLayer[Has[A], Nothing, Has[B]] =
    fromServiceMany(a => Has(f(a)))

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  def fromServices[A0: Tagged, A1: Tagged, B: Tagged](f: (A0, A1) => B): ZLayer[Has[A0] with Has[A1], Nothing, Has[B]] =
    fromServicesMany[A0, A1, Has[B]]((a0, a1) => Has(f(a0, a1)))

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  def fromServices[A0: Tagged, A1: Tagged, A2: Tagged, B: Tagged](
    f: (A0, A1, A2) => B
  ): ZLayer[Has[A0] with Has[A1] with Has[A2], Nothing, Has[B]] =
    fromServicesMany[A0, A1, A2, Has[B]]((a0, a1, a2) => Has(f(a0, a1, a2)))

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  def fromServices[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, B: Tagged](
    f: (A0, A1, A2, A3) => B
  ): ZLayer[Has[A0] with Has[A1] with Has[A2] with Has[A3], Nothing, Has[B]] =
    fromServicesMany[A0, A1, A2, A3, Has[B]]((a0, a1, a2, a3) => Has(f(a0, a1, a2, a3)))

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  def fromServices[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, A4: Tagged, B: Tagged](
    f: (A0, A1, A2, A3, A4) => B
  ): ZLayer[Has[A0] with Has[A1] with Has[A2] with Has[A3] with Has[A4], Nothing, Has[B]] =
    fromServicesMany[A0, A1, A2, A3, A4, Has[B]]((a0, a1, a2, a3, a4) => Has(f(a0, a1, a2, a3, a4)))

  /**
   * Constructs a layer that effectfully depends on the specified service.
   */
  def fromServiceM[A: Tagged, R, E, B: Tagged](f: A => ZIO[R, E, B]): ZLayer[R with Has[A], E, Has[B]] =
    fromServiceManyM(a => f(a).asService)

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  def fromServicesM[A0: Tagged, A1: Tagged, R, E, B: Tagged](
    f: (A0, A1) => ZIO[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1], E, Has[B]] =
    fromServicesManyM[A0, A1, R, E, Has[B]]((a0, a1) => f(a0, a1).asService)

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  def fromServicesM[A0: Tagged, A1: Tagged, A2: Tagged, R, E, B: Tagged](
    f: (A0, A1, A2) => ZIO[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1] with Has[A2], E, Has[B]] =
    fromServicesManyM[A0, A1, A2, R, E, Has[B]]((a0, a1, a2) => f(a0, a1, a2).asService)

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  def fromServicesM[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, R, E, B: Tagged](
    f: (A0, A1, A2, A3) => ZIO[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1] with Has[A2] with Has[A3], E, Has[B]] =
    fromServicesManyM[A0, A1, A2, A3, R, E, Has[B]]((a0, a1, a2, a3) => f(a0, a1, a2, a3).asService)

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  def fromServicesM[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, A4: Tagged, R, E, B: Tagged](
    f: (A0, A1, A2, A3, A4) => ZIO[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1] with Has[A2] with Has[A3] with Has[A4], E, Has[B]] =
    fromServicesManyM[A0, A1, A2, A3, A4, R, E, Has[B]]((a0, a1, a2, a3, a4) => f(a0, a1, a2, a3, a4).asService)

  /**
   * Constructs a layer that resourcefully and effectfully depends on the
   * specified service.
   */
  def fromServiceManaged[A: Tagged, R, E, B: Tagged](f: A => ZManaged[R, E, B]): ZLayer[R with Has[A], E, Has[B]] =
    fromServiceManyManaged(a => f(a).asService)

  /**
   * Constructs a layer that resourcefully and effectfully depends on the
   * specified services.
   */
  def fromServicesManaged[A0: Tagged, A1: Tagged, R, E, B: Tagged](
    f: (A0, A1) => ZManaged[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1], E, Has[B]] =
    fromServicesManyManaged[A0, A1, R, E, Has[B]]((a0, a1) => f(a0, a1).asService)

  /**
   * Constructs a layer that resourcefully and effectfully depends on the
   * specified services.
   */
  def fromServicesManaged[A0: Tagged, A1: Tagged, A2: Tagged, R, E, B: Tagged](
    f: (A0, A1, A2) => ZManaged[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1] with Has[A2], E, Has[B]] =
    fromServicesManyManaged[A0, A1, A2, R, E, Has[B]]((a0, a1, a2) => f(a0, a1, a2).asService)

  /**
   * Constructs a layer that resourcefully and effectfully depends on the
   * specified services.
   */
  def fromServicesManaged[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, R, E, B: Tagged](
    f: (A0, A1, A2, A3) => ZManaged[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1] with Has[A2] with Has[A3], E, Has[B]] =
    fromServicesManyManaged[A0, A1, A2, A3, R, E, Has[B]]((a0, a1, a2, a3) => f(a0, a1, a2, a3).asService)

  /**
   * Constructs a layer that resourcefully and effectfully depends on the
   * specified services.
   */
  def fromServicesManaged[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, A4: Tagged, R, E, B: Tagged](
    f: (A0, A1, A2, A3, A4) => ZManaged[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1] with Has[A2] with Has[A3] with Has[A4], E, Has[B]] =
    fromServicesManyManaged[A0, A1, A2, A3, A4, R, E, Has[B]]((a0, a1, a2, a3, a4) => f(a0, a1, a2, a3, a4).asService)

  /**
   * Constructs a layer that purely depends on the specified service, which
   * must return one or more services.
   */
  def fromServiceMany[A: Tagged, B <: Has[_]](f: A => B): ZLayer[Has[A], Nothing, B] =
    ZLayer(ZManaged.fromEffect(ZIO.access[Has[A]](m => f(m.get[A]))))

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services.
   */
  def fromServicesMany[A0: Tagged, A1: Tagged, B <: Has[_]](
    f: (A0, A1) => B
  ): ZLayer[Has[A0] with Has[A1], Nothing, B] =
    ZLayer(ZManaged.fromEffect {
      for {
        a0 <- ZIO.environment[Has[A0]].map(_.get[A0])
        a1 <- ZIO.environment[Has[A1]].map(_.get[A1])
      } yield f(a0, a1)
    })

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services.
   */
  def fromServicesMany[A0: Tagged, A1: Tagged, A2: Tagged, B <: Has[_]](
    f: (A0, A1, A2) => B
  ): ZLayer[Has[A0] with Has[A1] with Has[A2], Nothing, B] =
    ZLayer(ZManaged.fromEffect {
      for {
        a0 <- ZIO.environment[Has[A0]].map(_.get[A0])
        a1 <- ZIO.environment[Has[A1]].map(_.get[A1])
        a2 <- ZIO.environment[Has[A2]].map(_.get[A2])
      } yield f(a0, a1, a2)
    })

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services.
   */
  def fromServicesMany[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, B <: Has[_]](
    f: (A0, A1, A2, A3) => B
  ): ZLayer[Has[A0] with Has[A1] with Has[A2] with Has[A3], Nothing, B] =
    ZLayer(ZManaged.fromEffect {
      for {
        a0 <- ZIO.environment[Has[A0]].map(_.get[A0])
        a1 <- ZIO.environment[Has[A1]].map(_.get[A1])
        a2 <- ZIO.environment[Has[A2]].map(_.get[A2])
        a3 <- ZIO.environment[Has[A3]].map(_.get[A3])
      } yield f(a0, a1, a2, a3)
    })

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services.
   */
  def fromServicesMany[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, A4: Tagged, B <: Has[_]](
    f: (A0, A1, A2, A3, A4) => B
  ): ZLayer[Has[A0] with Has[A1] with Has[A2] with Has[A3] with Has[A4], Nothing, B] =
    ZLayer(ZManaged.fromEffect {
      for {
        a0 <- ZIO.environment[Has[A0]].map(_.get[A0])
        a1 <- ZIO.environment[Has[A1]].map(_.get[A1])
        a2 <- ZIO.environment[Has[A2]].map(_.get[A2])
        a3 <- ZIO.environment[Has[A3]].map(_.get[A3])
        a4 <- ZIO.environment[Has[A4]].map(_.get[A4])
      } yield f(a0, a1, a2, a3, a4)
    })

  /**
   * Constructs a layer that effectfully depends on the specified service,
   * which must return one or more services.
   */
  def fromServiceManyM[A: Tagged, R, E, B <: Has[_]](f: A => ZIO[R, E, B]): ZLayer[R with Has[A], E, B] =
    ZLayer(ZManaged.fromEffect(ZIO.accessM[R with Has[A]](m => f(m.get[A]))))

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services.
   */
  def fromServicesManyM[A0: Tagged, A1: Tagged, R, E, B <: Has[_]](
    f: (A0, A1) => ZIO[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1], E, B] =
    ZLayer(ZManaged.fromEffect {
      for {
        a0 <- ZIO.environment[Has[A0]].map(_.get[A0])
        a1 <- ZIO.environment[Has[A1]].map(_.get[A1])
        b  <- f(a0, a1)
      } yield b
    })

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services.
   */
  def fromServicesManyM[A0: Tagged, A1: Tagged, A2: Tagged, R, E, B <: Has[_]](
    f: (A0, A1, A2) => ZIO[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1] with Has[A2], E, B] =
    ZLayer(ZManaged.fromEffect {
      for {
        a0 <- ZIO.environment[Has[A0]].map(_.get[A0])
        a1 <- ZIO.environment[Has[A1]].map(_.get[A1])
        a2 <- ZIO.environment[Has[A2]].map(_.get[A2])
        b  <- f(a0, a1, a2)
      } yield b
    })

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services.
   */
  def fromServicesManyM[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, R, E, B <: Has[_]](
    f: (A0, A1, A2, A3) => ZIO[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1] with Has[A2] with Has[A3], E, B] =
    ZLayer(ZManaged.fromEffect {
      for {
        a0 <- ZIO.environment[Has[A0]].map(_.get[A0])
        a1 <- ZIO.environment[Has[A1]].map(_.get[A1])
        a2 <- ZIO.environment[Has[A2]].map(_.get[A2])
        a3 <- ZIO.environment[Has[A3]].map(_.get[A3])
        b  <- f(a0, a1, a2, a3)
      } yield b
    })

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services.
   */
  def fromServicesManyM[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, A4: Tagged, R, E, B <: Has[_]](
    f: (A0, A1, A2, A3, A4) => ZIO[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1] with Has[A2] with Has[A3] with Has[A4], E, B] =
    ZLayer(ZManaged.fromEffect {
      for {
        a0 <- ZIO.environment[Has[A0]].map(_.get[A0])
        a1 <- ZIO.environment[Has[A1]].map(_.get[A1])
        a2 <- ZIO.environment[Has[A2]].map(_.get[A2])
        a3 <- ZIO.environment[Has[A3]].map(_.get[A3])
        a4 <- ZIO.environment[Has[A4]].map(_.get[A4])
        b  <- f(a0, a1, a2, a3, a4)
      } yield b
    })

  /**
   * Constructs a layer that resourcefully and effectfully depends on the
   * specified service, which must return one or more services.
   */
  def fromServiceManyManaged[A: Tagged, R, E, B <: Has[_]](f: A => ZManaged[R, E, B]): ZLayer[R with Has[A], E, B] =
    ZLayer(ZManaged.accessManaged[R with Has[A]](m => f(m.get[A])))

  /**
   * Constructs a layer that resourcefully and effectfully depends on the
   * specified services, which must return one or more services.
   */
  def fromServicesManyManaged[A0: Tagged, A1: Tagged, R, E, B <: Has[_]](
    f: (A0, A1) => ZManaged[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1], E, B] =
    ZLayer {
      for {
        a0 <- ZManaged.environment[Has[A0]].map(_.get[A0])
        a1 <- ZManaged.environment[Has[A1]].map(_.get[A1])
        b  <- f(a0, a1)
      } yield b
    }

  /**
   * Constructs a layer that resourcefully and effectfully depends on the
   * specified services, which must return one or more services.
   */
  def fromServicesManyManaged[A0: Tagged, A1: Tagged, A2: Tagged, R, E, B <: Has[_]](
    f: (A0, A1, A2) => ZManaged[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1] with Has[A2], E, B] =
    ZLayer {
      for {
        a0 <- ZManaged.environment[Has[A0]].map(_.get[A0])
        a1 <- ZManaged.environment[Has[A1]].map(_.get[A1])
        a2 <- ZManaged.environment[Has[A2]].map(_.get[A2])
        b  <- f(a0, a1, a2)
      } yield b
    }

  /**
   * Constructs a layer that resourcefully and effectfully depends on the
   * specified services, which must return one or more services.
   */
  def fromServicesManyManaged[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, R, E, B <: Has[_]](
    f: (A0, A1, A2, A3) => ZManaged[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1] with Has[A2] with Has[A3], E, B] =
    ZLayer {
      for {
        a0 <- ZManaged.environment[Has[A0]].map(_.get[A0])
        a1 <- ZManaged.environment[Has[A1]].map(_.get[A1])
        a2 <- ZManaged.environment[Has[A2]].map(_.get[A2])
        a3 <- ZManaged.environment[Has[A3]].map(_.get[A3])
        b  <- f(a0, a1, a2, a3)
      } yield b
    }

  /**
   * Constructs a layer that resourcefully and effectfully depends on the
   * specified services, which must return one or more services.
   */
  def fromServicesManyManaged[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, A4: Tagged, R, E, B <: Has[_]](
    f: (A0, A1, A2, A3, A4) => ZManaged[R, E, B]
  ): ZLayer[R with Has[A0] with Has[A1] with Has[A2] with Has[A3] with Has[A4], E, B] =
    ZLayer {
      for {
        a0 <- ZManaged.environment[Has[A0]].map(_.get[A0])
        a1 <- ZManaged.environment[Has[A1]].map(_.get[A1])
        a2 <- ZManaged.environment[Has[A2]].map(_.get[A2])
        a3 <- ZManaged.environment[Has[A3]].map(_.get[A3])
        a4 <- ZManaged.environment[Has[A4]].map(_.get[A4])
        b  <- f(a0, a1, a2, a3, a4)
      } yield b
    }

  /**
   * Constructs a layer from a managed resource.
   */
  def fromManaged[R, E, A: Tagged](m: ZManaged[R, E, A]): ZLayer[R, E, Has[A]] =
    ZLayer(m.asService)

  /**
   * Constructs a layer from a managed resource, which must return one or more
   * services.
   */
  def fromManagedMany[R, E, A <: Has[_]](m: ZManaged[R, E, A]): ZLayer[R, E, A] =
    ZLayer(m)

  /**
   * An identity layer that passes along its inputs.
   */
  def identity[A <: Has[_]]: ZLayer[A, Nothing, A] =
    ZLayer.requires[A]

  /**
   * Constructs a layer that passes along the specified environment as an
   * output.
   */
  def requires[A <: Has[_]]: ZLayer[A, Nothing, A] =
    ZLayer(ZManaged.environment[A])

  /**
   * Constructs a layer that accesses and returns the specified service from
   * the environment.
   */
  def service[A]: ZLayer[Has[A], Nothing, Has[A]] =
    ZLayer(ZManaged.environment[Has[A]])

  /**
   * Constructs a layer from the specified value.
   */
  def succeed[A: Tagged](a: => A): ZLayer.NoDeps[Nothing, Has[A]] =
    ZLayer(ZManaged.succeed(Has(a)))

  /**
   * Constructs a layer from the specified value, which must return one or more
   * services.
   */
  def succeedMany[A <: Has[_]](a: => A): ZLayer.NoDeps[Nothing, A] =
    ZLayer(ZManaged.succeed(a))

  /**
   * A `MemoMap` memoizes dependencies.
   */
  private trait MemoMap { self =>

    /**
     * Checks the memo map to see if a dependency exists. If it is, immediately
     * returns it. Otherwise, obtains the dependency, stores it in the memo map,
     * and adds a finalizer to the outer `Managed`.
     */
    def getOrElseMemoize[E, A, B <: Has[_]](
      layer: ZLayer[A, E, B],
      finalizerRef: ZManaged.FinalizerRef[Any]
    ): ZManaged[A, E, B]
  }

  private object MemoMap {

    /**
     * Constructs an empty memo map.
     */
    def make: UIO[MemoMap] =
      RefM
        .make[Map[ZLayer[Nothing, Any, Has[_]], Reservation[Any, Any, Any]]](Map.empty)
        .map { ref =>
          new MemoMap { self =>
            final def getOrElseMemoize[E, A, B <: Has[_]](
              layer: ZLayer[A, E, B],
              finalizers: ZManaged.FinalizerRef[Any]
            ): ZManaged[A, E, B] =
              ZManaged {
                ref.modify { map =>
                  map.get(layer) match {
                    case Some(Reservation(acquire, release)) =>
                      val reservation = Reservation(
                        acquire = acquire.asInstanceOf[IO[E, B]],
                        release = _ => finalizers.add(release)
                      )
                      ZIO.succeedNow((reservation, map))
                    case None =>
                      for {
                        observers <- Ref.make(0)
                        finalizer <- Ref.make[Exit[Any, Any] => UIO[Any]](_ => UIO.unit)
                        promise   <- Promise.make[E, B]
                      } yield {
                        val reservation = Reservation(
                          acquire = ZIO.uninterruptibleMask { restore =>
                            for {
                              env <- ZIO.environment[A]
                              _   <- observers.update(_ + 1)
                              res <- layer.scope.flatMap(_.apply(self)).reserve
                              _ <- finalizer.set { exit =>
                                    ZIO.whenM(observers.modify(n => (n == 1, n - 1))) {
                                      res.release(exit).provide(env)
                                    }
                                  }
                              _ <- restore(res.acquire).to(promise)
                              b <- promise.await
                            } yield b
                          },
                          release = _ => finalizers.add(exit => finalizer.get.flatMap(_(exit)))
                        )
                        val memoized = Reservation(
                          acquire =
                            ZIO.uninterruptibleMask(restore => observers.update(_ + 1) *> restore(promise.await)),
                          release = exit => finalizer.get.flatMap(_(exit))
                        )
                        (reservation, map + (layer -> memoized))
                      }
                  }
                }
              }
          }
        }
  }
}
