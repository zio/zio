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
    new ZLayer(
      ZManaged.finalizerRef(_ => UIO.unit).map { finalizerRef => (memoMap: ZLayer.MemoMap) =>
        for {
          l <- ZLayer.getOrElseMemoize(memoMap, self, finalizerRef).toManaged_
          r <- ZLayer.getOrElseMemoize(memoMap, that, finalizerRef).provide(l).toManaged_
        } yield r
      }
    )

  /**
   * Combines this layer with the specified layer, producing a new layer that
   * has the inputs of both layers, and the outputs of both layers.
   */
  def ++[E1 >: E, RIn2, ROut1 >: ROut <: Has[_], ROut2 <: Has[_]](
    that: ZLayer[RIn2, E1, ROut2]
  )(implicit tagged: Tagged[ROut2]): ZLayer[RIn with RIn2, E1, ROut1 with ROut2] =
    new ZLayer(
      ZManaged.finalizerRef(_ => UIO.unit).map { finalizerRef => (memoMap: ZLayer.MemoMap) =>
        for {
          l <- ZLayer.getOrElseMemoize(memoMap, self, finalizerRef).toManaged_
          r <- ZLayer.getOrElseMemoize(memoMap, that, finalizerRef).toManaged_
        } yield l.union[ROut2](r)
      }
    )

  def +!+[E1 >: E, RIn2, ROut2 <: Has[_]](
    that: ZLayer[RIn2, E1, ROut2]
  ): ZLayer[RIn with RIn2, E1, ROut with ROut2] =
    new ZLayer(
      ZManaged.finalizerRef(_ => UIO.unit).map { finalizerRef => (memoMap: ZLayer.MemoMap) =>
        for {
          l <- ZLayer.getOrElseMemoize(memoMap, self, finalizerRef).toManaged_
          r <- ZLayer.getOrElseMemoize(memoMap, that, finalizerRef).toManaged_
        } yield l.unionAll[ROut2](r)
      }
    )

  /**
   * Builds a layer into a managed value.
   */
  def build: ZManaged[RIn, E, ROut] =
    for {
      memoMap <- ZLayer.MemoMap.make.toManaged_
      run     <- self.scope
      value   <- run(memoMap)
    } yield value

  def fresh: ZLayer[RIn, E, ROut] =
    new ZLayer(self.scope)

  def memoize: ZManaged[RIn, Nothing, ZLayer[Any, E, ROut]] =
    build.memoize.map(ZLayer(_))

  /**
   * Converts a layer that requires no services into a managed runtime, which
   * can be used to execute effects.
   */
  def toRuntime[RIn2 <: RIn](p: Platform)(implicit ev: Any =:= RIn2): Managed[E, Runtime[ROut]] =
    build.provide(ev).map(Runtime(_, p))

  /**
   * Updates one of the services output by this layer.
   */
  def update[A: Tagged](f: A => A)(implicit ev: ROut <:< Has[A]): ZLayer[RIn, E, ROut] =
    new ZLayer(scope.map(run => run(_).map(_.update[A](f))))
}

object ZLayer {
  type NoDeps[+E, +B <: Has[_]] = ZLayer[Any, E, B]

  /**
   * Constructs a layer from a managed resource.
   */
  def apply[RIn, E, ROut <: Has[_]](managed: ZManaged[RIn, E, ROut]): ZLayer[RIn, E, ROut] =
    new ZLayer(Managed.succeed(_ => managed))

  /**
   * Constructs a layer from the specified effect, which must produce one or
   * more services.
   */
  def fromEffect[E, A <: Has[_]](zio: IO[E, A]): ZLayer.NoDeps[E, A] =
    ZLayer(Managed.fromEffect(zio))

  /**
   * Constructs a layer from the environment using the specified function,
   * which must return one or more services.
   */
  def fromEnvironment[A, B <: Has[_]](f: A => B): ZLayer[A, Nothing, B] =
    fromEnvironmentM(a => ZIO.succeedNow(f(a)))

  /**
   * Constructs a layer from the environment using the specified effectful
   * function, which must return one or more services.
   */
  def fromEnvironmentM[A, E, B <: Has[_]](f: A => IO[E, B]): ZLayer[A, E, B] =
    fromEnvironmentManaged(a => f(a).toManaged_)

  /**
   * Constructs a layer from the environment using the specified effectful
   * resourceful function, which must return one or more services.
   */
  def fromEnvironmentManaged[A, E, B <: Has[_]](f: A => Managed[E, B]): ZLayer[A, E, B] =
    ZLayer(ZManaged.fromFunctionM(f))

  /**
   * Constructs a layer that purely depends on the specified service.
   */
  def fromService[A: Tagged, B <: Has[_]](f: A => B): ZLayer[Has[A], Nothing, B] =
    ZLayer(ZManaged.fromEffect(ZIO.access[Has[A]](m => f(m.get[A]))))

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  def fromServices[A0: Tagged, A1: Tagged, B <: Has[_]](f: (A0, A1) => B): ZLayer[Has[A0] with Has[A1], Nothing, B] =
    ZLayer(ZManaged.fromEffect {
      for {
        a0 <- ZIO.environment[Has[A0]].map(_.get[A0])
        a1 <- ZIO.environment[Has[A1]].map(_.get[A1])
      } yield f(a0, a1)
    })

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  def fromServices[A0: Tagged, A1: Tagged, A2: Tagged, B <: Has[_]](
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
   * Constructs a layer that purely depends on the specified services.
   */
  def fromServices[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, B <: Has[_]](
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
   * Constructs a layer that purely depends on the specified services.
   */
  def fromServices[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, A4: Tagged, B <: Has[_]](
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
   * Constructs a layer that effectfully depends on the specified service.
   */
  def fromServiceM[A: Tagged, R, E, B <: Has[_]](f: A => ZIO[R, E, B]): ZLayer[R with Has[A], E, B] =
    ZLayer(ZManaged.fromEffect(ZIO.accessM[R with Has[A]](m => f(m.get[A]))))

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  def fromServicesM[A0: Tagged, A1: Tagged, R, E, B <: Has[_]](
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
   * Constructs a layer that effectfully depends on the specified services.
   */
  def fromServicesM[A0: Tagged, A1: Tagged, A2: Tagged, R, E, B <: Has[_]](
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
   * Constructs a layer that effectfully depends on the specified services.
   */
  def fromServicesM[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, R, E, B <: Has[_]](
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
   * Constructs a layer that effectfully depends on the specified services.
   */
  def fromServicesM[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, A4: Tagged, R, E, B <: Has[_]](
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
   * specified service.
   */
  def fromServiceManaged[A: Tagged, E, B <: Has[_]](f: A => Managed[E, B]): ZLayer[Has[A], E, B] =
    ZLayer(ZManaged.accessManaged[Has[A]](m => f(m.get[A])))

  /**
   * Constructs a layer that resourcefully and effectfully depends on the
   * specified services.
   */
  def fromServicesManaged[A0: Tagged, A1: Tagged, E, B <: Has[_]](
    f: (A0, A1) => Managed[E, B]
  ): ZLayer[Has[A0] with Has[A1], E, B] =
    ZLayer {
      for {
        a0 <- ZManaged.environment[Has[A0]].map(_.get[A0])
        a1 <- ZManaged.environment[Has[A1]].map(_.get[A1])
        b  <- f(a0, a1)
      } yield b
    }

  /**
   * Constructs a layer that resourcefully and effectfully depends on the
   * specified services.
   */
  def fromServicesManaged[A0: Tagged, A1: Tagged, A2: Tagged, E, B <: Has[_]](
    f: (A0, A1, A2) => Managed[E, B]
  ): ZLayer[Has[A0] with Has[A1] with Has[A2], E, B] =
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
   * specified services.
   */
  def fromServicesManaged[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, E, B <: Has[_]](
    f: (A0, A1, A2, A3) => Managed[E, B]
  ): ZLayer[Has[A0] with Has[A1] with Has[A2] with Has[A3], E, B] =
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
   * specified services.
   */
  def fromServicesManaged[A0: Tagged, A1: Tagged, A2: Tagged, A3: Tagged, A4: Tagged, E, B <: Has[_]](
    f: (A0, A1, A2, A3, A4) => Managed[E, B]
  ): ZLayer[Has[A0] with Has[A1] with Has[A2] with Has[A3] with Has[A4], E, B] =
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
   * Constructs a layer that has no dependencies but must be created from a
   * managed resource.
   */
  def fromManaged[E, A <: Has[_]](m: Managed[E, A]): ZLayer.NoDeps[E, A] = ZLayer(m)

  /**
   * An identity layer that passes along its inputs.
   */
  def identity[A <: Has[_]]: ZLayer[A, Nothing, A] = ZLayer.requires[A]

  /**
   * Constructs a layer that passes along the specified environment as an output.
   */
  def requires[A <: Has[_]]: ZLayer[A, Nothing, A] = ZLayer(ZManaged.environment[A])

  /**
   * Constructs a layer that accesses and returns the specified service from
   * the environment.
   */
  def service[A]: ZLayer[Has[A], Nothing, Has[A]] = ZLayer(ZManaged.environment[Has[A]])

  /**
   * Constructs a layer from the specified value.
   */
  def succeed[A: Tagged](a: => A): ZLayer.NoDeps[Nothing, Has[A]] = ZLayer(ZManaged.succeed(Has(a)))

  private trait MemoMap {
    def get[E, A, B <: Has[_]](layer: ZLayer[A, E, B]): UIO[Option[B]]
    def memoize[E, A, B <: Has[_]](layer: ZLayer[A, E, B], b: B): UIO[Unit]
  }

  private object MemoMap {
    def make: UIO[MemoMap] =
      Ref.make[Map[ZLayer[Nothing, Any, Has[_]], Any]](Map.empty).map { ref =>
        new MemoMap {
          def get[E, A, B <: Has[_]](layer: ZLayer[A, E, B]): UIO[Option[B]] =
            ref.get.map(_.get(layer).asInstanceOf[Option[B]])
          def memoize[E, A, B <: Has[_]](layer: ZLayer[A, E, B], b: B): UIO[Unit] =
            ref.update(_ + (layer -> b))
        }
      }
  }

  private def getOrElseMemoize[E, A, B <: Has[_]](
    memoMap: MemoMap,
    layer: ZLayer[A, E, B],
    finalizerRef: Ref[Exit[Any, Any] => ZIO[Any, Nothing, Any]]
  ): ZIO[A, E, B] =
    memoMap.get(layer).flatMap {
      case Some(b) => ZIO.succeed(b)
      case None =>
        layer.scope.flatMap(_.apply(memoMap)).reserve.flatMap {
          case Reservation(acquire, release) =>
            ZIO.bracket[A, E, B, B](
              acquire.tap(b => memoMap.memoize(layer, b)),
              _ =>
                ZIO.accessM[A] { a =>
                  finalizerRef.update { finalizer => exit =>
                    release(exit).provide(a) *> finalizer(exit)
                  }
                },
              ZIO.succeedNow
            )
        }
    }
}
