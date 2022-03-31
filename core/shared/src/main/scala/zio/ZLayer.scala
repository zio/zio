/*
 * Copyright 2020-2022 John A. De Goes and the ZIO Contributors
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

import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.collection.mutable
import scala.collection.mutable.Builder

/**
 * A `ZLayer[E, A, B]` describes how to build one or more services in your
 * application. Services can be injected into effects via ZIO#provide. Effects
 * can require services via ZIO.service."
 *
 * Layer can be thought of as recipes for producing bundles of services, given
 * their dependencies (other services).
 *
 * Construction of services can be effectful and utilize resources that must be
 * acquired and safely released when the services are done being utilized.
 *
 * By default layers are shared, meaning that if the same layer is used twice
 * the layer will only be allocated a single time.
 *
 * Because of their excellent composition properties, layers are the idiomatic
 * way in ZIO to create services that depend on other services.
 */
sealed abstract class ZLayer[-RIn, +E, +ROut] { self =>

  /**
   * A symbolic alias for `orDie`.
   */
  final def !(implicit
    ev1: E <:< Throwable,
    ev2: CanFail[E],
    trace: ZTraceElement
  ): ZLayer[RIn, Nothing, ROut] =
    self.orDie

  final def +!+[E1 >: E, RIn2, ROut1 >: ROut, ROut2](
    that: ZLayer[RIn2, E1, ROut2]
  ): ZLayer[RIn with RIn2, E1, ROut1 with ROut2] =
    self.zipWithPar(that)(_.unionAll[ROut2](_))

  /**
   * Combines this layer with the specified layer, producing a new layer that
   * has the inputs and outputs of = both.
   */
  final def ++[E1 >: E, RIn2, ROut1 >: ROut, ROut2](
    that: ZLayer[RIn2, E1, ROut2]
  )(implicit tag: EnvironmentTag[ROut2]): ZLayer[RIn with RIn2, E1, ROut1 with ROut2] =
    self.zipWithPar(that)(_.union[ROut2](_))

  /**
   * A symbolic alias for `orElse`.
   */
  def <>[RIn1 <: RIn, E1, ROut1 >: ROut](
    that: ZLayer[RIn1, E1, ROut1]
  )(implicit ev: CanFail[E], trace: ZTraceElement): ZLayer[RIn1, E1, ROut1] =
    self.orElse(that)

  /**
   * A named alias for `++`.
   */
  final def and[E1 >: E, RIn2, ROut1 >: ROut, ROut2](
    that: ZLayer[RIn2, E1, ROut2]
  )(implicit tag: EnvironmentTag[ROut2]): ZLayer[RIn with RIn2, E1, ROut1 with ROut2] =
    self.++[E1, RIn2, ROut1, ROut2](that)

  /**
   * A named alias for `>+>`.
   */
  final def andTo[E1 >: E, RIn2 >: ROut, ROut1 >: ROut, ROut2](
    that: ZLayer[RIn2, E1, ROut2]
  )(implicit
    tagged: EnvironmentTag[ROut2],
    trace: ZTraceElement
  ): ZLayer[RIn, E1, ROut1 with ROut2] =
    self.>+>[E1, RIn2, ROut1, ROut2](that)

  /**
   * Builds a layer into a scoped value.
   */
  final def build(implicit trace: ZTraceElement): ZIO[RIn with Scope, E, ZEnvironment[ROut]] =
    ZIO.serviceWithZIO[Scope](build)

  /**
   * Builds a layer into a ZIO value. Any resources associated with this layer
   * will be released when the specified scope is closed unless their scope has
   * been extended. This allows building layers where the lifetime of some of
   * the services output by the layer exceed the lifetime of the effect the
   * layer is provided to.
   */
  final def build(scope: Scope)(implicit trace: ZTraceElement): ZIO[RIn, E, ZEnvironment[ROut]] =
    for {
      memoMap <- ZLayer.MemoMap.make
      run     <- self.scope(scope)
      value   <- run(memoMap)
    } yield value

  /**
   * Recovers from all errors.
   */
  final def catchAll[RIn1 <: RIn, E1, ROut1 >: ROut](
    handler: E => ZLayer[RIn1, E1, ROut1]
  )(implicit trace: ZTraceElement): ZLayer[RIn1, E1, ROut1] =
    foldLayer(handler, ZLayer.succeedEnvironment(_))

  /**
   * Extends the scope of this layer, returning a new layer that when provided
   * to an effect will not immediately release its associated resources when
   * that effect completes execution but instead when the scope the resulting
   * effect depends on is closed.
   */
  final def extendScope: ZLayer[RIn with Scope, E, ROut] =
    ZLayer.ExtendScope(self)

  /**
   * Constructs a layer dynamically based on the output of this layer.
   */
  final def flatMap[RIn1 <: RIn, E1 >: E, ROut2](
    f: ZEnvironment[ROut] => ZLayer[RIn1, E1, ROut2]
  )(implicit trace: ZTraceElement): ZLayer[RIn1, E1, ROut2] =
    foldLayer(ZLayer.fail, f)

  final def flatten[RIn1 <: RIn, E1 >: E, ROut1 >: ROut, ROut2](implicit
    tag: Tag[ROut1],
    ev1: ROut1 <:< ZLayer[RIn1, E1, ROut2],
    trace: ZTraceElement
  ): ZLayer[RIn1, E1, ROut2] =
    flatMap(environment => ev1(environment.get[ROut1]))

  /**
   * Feeds the error or output services of this layer into the input of either
   * the specified `failure` or `success` layers, resulting in a new layer with
   * the inputs of this layer, and the error or outputs of the specified layer.
   */
  final def foldLayer[E1, RIn1 <: RIn, ROut2](
    failure: E => ZLayer[RIn1, E1, ROut2],
    success: ZEnvironment[ROut] => ZLayer[RIn1, E1, ROut2]
  )(implicit ev: CanFail[E], trace: ZTraceElement): ZLayer[RIn1, E1, ROut2] =
    foldCauseLayer(_.failureOrCause.fold(failure, ZLayer.failCause), success)

  /**
   * Feeds the error or output services of this layer into the input of either
   * the specified `failure` or `success` layers, resulting in a new layer with
   * the inputs of this layer, and the error or outputs of the specified layer.
   */
  final def foldCauseLayer[E1, RIn1 <: RIn, ROut2](
    failure: Cause[E] => ZLayer[RIn1, E1, ROut2],
    success: ZEnvironment[ROut] => ZLayer[RIn1, E1, ROut2]
  )(implicit ev: CanFail[E]): ZLayer[RIn1, E1, ROut2] =
    ZLayer.Fold(self, failure, success)

  /**
   * Creates a fresh version of this layer that will not be shared.
   */
  final def fresh: ZLayer[RIn, E, ROut] =
    ZLayer.Fresh(self)

  /**
   * Returns the hash code of this layer.
   */
  override final lazy val hashCode: Int =
    super.hashCode

  /**
   * Builds this layer and uses it until it is interrupted. This is useful when
   * your entire application is a layer, such as an HTTP server.
   */
  final def launch(implicit trace: ZTraceElement): ZIO[RIn, E, Nothing] =
    ZIO.scoped[RIn] {
      ZIO.serviceWithZIO[Scope](build) *> ZIO.never
    }

  /**
   * Returns a new layer whose output is mapped by the specified function.
   */
  final def map[ROut1](f: ZEnvironment[ROut] => ZEnvironment[ROut1])(implicit
    trace: ZTraceElement
  ): ZLayer[RIn, E, ROut1] =
    flatMap(environment => ZLayer.succeedEnvironment(f(environment)))

  /**
   * Returns a layer with its error channel mapped using the specified function.
   */
  final def mapError[E1](f: E => E1)(implicit ev: CanFail[E], trace: ZTraceElement): ZLayer[RIn, E1, ROut] =
    catchAll(e => ZLayer.fail(f(e)))

  /**
   * Returns a scoped effect that, if evaluated, will return the lazily computed
   * result of this layer.
   */
  final def memoize(implicit trace: ZTraceElement): ZIO[Scope, Nothing, ZLayer[RIn, E, ROut]] =
    ZIO.serviceWithZIO[Scope](build).memoize.map(ZLayer.Scoped[RIn, E, ROut](_))

  /**
   * Translates effect failure into death of the fiber, making all failures
   * unchecked and not a part of the type of the layer.
   */
  final def orDie(implicit
    ev1: E IsSubtypeOfError Throwable,
    ev2: CanFail[E],
    trace: ZTraceElement
  ): ZLayer[RIn, Nothing, ROut] =
    catchAll(e => ZLayer.die(ev1(e)))

  /**
   * Executes this layer and returns its output, if it succeeds, but otherwise
   * executes the specified layer.
   */
  final def orElse[RIn1 <: RIn, E1, ROut1 >: ROut](
    that: ZLayer[RIn1, E1, ROut1]
  )(implicit ev: CanFail[E], trace: ZTraceElement): ZLayer[RIn1, E1, ROut1] =
    catchAll(_ => that)

  /**
   * Retries constructing this layer according to the specified schedule.
   */
  final def retry[RIn1 <: RIn](
    schedule: Schedule[RIn1, E, Any]
  )(implicit trace: ZTraceElement): ZLayer[RIn1, E, ROut] = {
    import Schedule.Decision._

    case class State(state: schedule.State)

    def update(e: E, s: schedule.State): ZLayer[RIn1, E, State] =
      ZLayer.fromZIO {
        Clock.currentDateTime.flatMap { now =>
          schedule.step(now, e, s).flatMap {
            case (_, _, Done) => ZIO.fail(e)
            case (state, _, Continue(interval)) =>
              Clock.sleep(Duration.fromInterval(now, interval.start)).as(State(state))
          }
        }
      }

    def loop(s: schedule.State): ZLayer[RIn1, E, ROut] =
      self.catchAll(update(_, s).flatMap(environment => loop(environment.get.state).fresh))

    ZLayer.succeed(State(schedule.initial)).flatMap(environment => loop(environment.get.state))
  }

  /**
   * Performs the specified effect if this layer succeeds.
   */
  final def tap[RIn1 <: RIn, E1 >: E](f: ZEnvironment[ROut] => ZIO[RIn1, E1, Any])(implicit
    trace: ZTraceElement
  ): ZLayer[RIn1, E1, ROut] =
    flatMap(environment => ZLayer.fromZIOEnvironment(f(environment).as(environment)))

  /**
   * Performs the specified effect if this layer fails.
   */
  final def tapError[RIn1 <: RIn, E1 >: E](f: E => ZIO[RIn1, E1, Any])(implicit
    trace: ZTraceElement
  ): ZLayer[RIn1, E1, ROut] =
    catchAll(e => ZLayer.fromZIO[RIn1, E1, Nothing](f(e) *> ZIO.fail(e)))

  /**
   * A named alias for `>>>`.
   */
  final def to[E1 >: E, ROut2](that: ZLayer[ROut, E1, ROut2])(implicit
    trace: ZTraceElement
  ): ZLayer[RIn, E1, ROut2] =
    self >>> that

  /**
   * Converts a layer that requires no services into a scoped runtime, which can
   * be used to execute effects.
   */
  final def toRuntime(
    runtimeConfig: RuntimeConfig
  )(implicit ev: Any <:< RIn, trace: ZTraceElement): ZIO[Scope, E, Runtime[ROut]] =
    ZIO
      .serviceWithZIO[Scope](build)
      .provideSomeEnvironment[Scope](ZEnvironment.empty.upcast(ev).union[Scope](_))
      .map(Runtime(_, runtimeConfig))

  /**
   * Replaces the layer's output with `Unit`.
   *
   * When used with [[ZIO.provide]] and [[ZLayer.make]] macros (and their
   * variants), this will suppress the unused layer warning that is normally
   * emitted, and will actually include the layer for its side-effects.
   */
  def unit(implicit trace: ZTraceElement): ZLayer[RIn, E, Unit] =
    self.map(_ => ZEnvironment(()))

  /**
   * Updates one of the services output by this layer.
   */
  final def update[A >: ROut: Tag](
    f: A => A
  )(implicit trace: ZTraceElement): ZLayer[RIn, E, ROut] =
    map(_.update[A](f))

  /**
   * Combines this layer the specified layer, producing a new layer that has the
   * inputs of both, and the outputs of both combined using the specified
   * function.
   */
  final def zipWithPar[E1 >: E, RIn2, ROut1 >: ROut, ROut2, ROut3](
    that: ZLayer[RIn2, E1, ROut2]
  )(f: (ZEnvironment[ROut], ZEnvironment[ROut2]) => ZEnvironment[ROut3]): ZLayer[RIn with RIn2, E1, ROut3] =
    ZLayer.ZipWithPar(self, that, f)

  /**
   * Returns whether this layer is a fresh version that will not be shared.
   */
  private final def isFresh: Boolean =
    self match {
      case ZLayer.Fresh(_) => true
      case _               => false
    }

  private final def scope(scope: Scope)(implicit
    trace: ZTraceElement
  ): ZIO[Any, Nothing, ZLayer.MemoMap => ZIO[RIn, E, ZEnvironment[ROut]]] =
    self match {
      case ZLayer.ExtendScope(self) =>
        ZIO.succeed { memoMap =>
          ZIO
            .serviceWithZIO[Scope] { scope =>
              memoMap.getOrElseMemoize(scope)(self)
            }
            .asInstanceOf[ZIO[RIn, E, ZEnvironment[ROut]]]
        }
      case ZLayer.Fold(self, failure, success) =>
        ZIO.succeed { memoMap =>
          memoMap
            .getOrElseMemoize(scope)(self)
            .foldCauseZIO(
              e => memoMap.getOrElseMemoize(scope)(failure(e)),
              r => memoMap.getOrElseMemoize(scope)(success(r))
            )
        }
      case ZLayer.Fresh(self) =>
        ZIO.succeed(_ => self.build(scope))
      case ZLayer.Scoped(self) =>
        ZIO.succeed(_ => scope.extend[RIn](self))
      case ZLayer.Suspend(self) =>
        ZIO.succeed(memoMap => memoMap.getOrElseMemoize(scope)(self()))
      case ZLayer.To(self, that) =>
        ZIO.succeed(memoMap =>
          memoMap
            .getOrElseMemoize(scope)(self)
            .flatMap(r => memoMap.getOrElseMemoize(scope)(that).provideEnvironment(r)(trace))
        )
      case ZLayer.ZipWithPar(self, that, f) =>
        ZIO.succeed(memoMap =>
          memoMap.getOrElseMemoize(scope)(self).zipWithPar(memoMap.getOrElseMemoize(scope)(that))(f)
        )
    }
}

object ZLayer extends ZLayerCompanionVersionSpecific {

  private final case class ExtendScope[RIn <: Scope, E, ROut](self: ZLayer[RIn, E, ROut]) extends ZLayer[RIn, E, ROut]

  private final case class Fold[RIn, E, E2, ROut, ROut2](
    self: ZLayer[RIn, E, ROut],
    failure: Cause[E] => ZLayer[RIn, E2, ROut2],
    success: ZEnvironment[ROut] => ZLayer[RIn, E2, ROut2]
  ) extends ZLayer[RIn, E2, ROut2]

  private final case class Fresh[RIn, E, ROut](self: ZLayer[RIn, E, ROut]) extends ZLayer[RIn, E, ROut]

  private final case class Scoped[-RIn, +E, +ROut](self: ZIO[RIn with Scope, E, ZEnvironment[ROut]])
      extends ZLayer[RIn, E, ROut]

  private final case class Suspend[-RIn, +E, +ROut](self: () => ZLayer[RIn, E, ROut]) extends ZLayer[RIn, E, ROut]

  private final case class To[RIn, E, ROut, ROut1](
    self: ZLayer[RIn, E, ROut],
    that: ZLayer[ROut, E, ROut1]
  ) extends ZLayer[RIn, E, ROut1]

  private final case class ZipWithPar[-RIn, +E, ROut, ROut2, ROut3](
    self: ZLayer[RIn, E, ROut],
    that: ZLayer[RIn, E, ROut2],
    f: (ZEnvironment[ROut], ZEnvironment[ROut2]) => ZEnvironment[ROut3]
  ) extends ZLayer[RIn, E, ROut3]

  /**
   * Constructs a layer from an effectual resource.
   */
  def apply[RIn, E, ROut: Tag](zio: ZIO[RIn, E, ROut])(implicit
    trace: ZTraceElement
  ): ZLayer[RIn, E, ROut] =
    ZLayer.fromZIO(zio)

  sealed trait Debug

  object Debug {
    private[zio] type Tree = Tree.type

    private[zio] case object Tree extends Debug

    private[zio] type Mermaid = Mermaid.type

    private[zio] case object Mermaid extends Debug

    /**
     * Including this layer in a call to a compile-time ZLayer constructor, such
     * as [[ZIO.provide]] or [[ZLayer.make]], will display a tree visualization
     * of the constructed layer graph.
     *
     * {{{
     *   val layer =
     *     ZLayer.make[OldLady](
     *       OldLady.live,
     *       Spider.live,
     *       Fly.live,
     *       Bear.live,
     *       Console.live,
     *       ZLayer.Debug.tree
     *     )
     *
     * // Including `ZLayer.Debug.tree` will generate the following compilation error:
     * //
     * // ◉ OldLady.live
     * // ├─◑ Spider.live
     * // │ ╰─◑ Fly.live
     * // │   ╰─◑ Console.live
     * // ╰─◑ Bear.live
     * //   ╰─◑ Fly.live
     * //     ╰─◑ Console.live
     *
     * }}}
     */
    val tree: ULayer[Debug] =
      ZLayer.succeed[Debug](Debug.Tree)(Tag[Debug], Tracer.newTrace)

    /**
     * Including this layer in a call to a compile-time ZLayer constructor, such
     * as [[ZIO.provide]] or [[ZLayer.make]], will display a tree visualization
     * of the constructed layer graph as well as a link to Mermaid chart.
     *
     * {{{
     *   val layer =
     *     ZLayer.make[OldLady](
     *       OldLady.live,
     *       Spider.live,
     *       Fly.live,
     *       Bear.live,
     *       Console.live,
     *       ZLayer.Debug.mermaid
     *     )
     *
     * // Including `ZLayer.Debug.mermaid` will generate the following compilation error:
     * //
     * // ◉ OldLady.live
     * // ├─◑ Spider.live
     * // │ ╰─◑ Fly.live
     * // │   ╰─◑ Console.live
     * // ╰─◑ Bear.live
     * //   ╰─◑ Fly.live
     * //     ╰─◑ Console.live
     * //
     * // Mermaid Live Editor Link
     * // https://mermaid-js.github.io/mermaid-live-editor/edit/#eyJjb2RlIjoiZ3JhcGhcbiAgICBDb25zb2xlLmxpdmVcbiAgICBTcGlkZXIubGl2ZSAtLT4gRmx5LmxpdmVcbiAgICBGbHkubGl2ZSAtLT4gQ29uc29sZS5saXZlXG4gICAgT2xkTGFkeS5saXZlIC0tPiBTcGlkZXIubGl2ZVxuICAgIE9sZExhZHkubGl2ZSAtLT4gQmVhci5saXZlXG4gICAgQmVhci5saXZlIC0tPiBGbHkubGl2ZVxuICAgICIsIm1lcm1haWQiOiAie1xuICBcInRoZW1lXCI6IFwiZGVmYXVsdFwiXG59IiwgInVwZGF0ZUVkaXRvciI6IHRydWUsICJhdXRvU3luYyI6IHRydWUsICJ1cGRhdGVEaWFncmFtIjogdHJ1ZX0=
     *
     * }}}
     */
    val mermaid: ULayer[Debug] =
      ZLayer.succeed[Debug](Debug.Mermaid)(Tag[Debug], Tracer.newTrace)
  }

  /**
   * Gathers up the ZLayer inside of the given collection, and combines them
   * into a single ZLayer containing an equivalent collection of results.
   */
  def collectAll[R, E, A: Tag, Collection[+Element] <: Iterable[Element]](
    in: Collection[ZLayer[R, E, A]]
  )(implicit
    tag: Tag[Collection[A]],
    bf: BuildFrom[Collection[ZLayer[R, E, A]], A, Collection[A]],
    trace: ZTraceElement
  ): ZLayer[R, E, Collection[A]] =
    foreach(in)(i => i)

  /**
   * Constructs a layer that dies with the specified throwable.
   */
  final def die(t: Throwable)(implicit trace: ZTraceElement): ZLayer[Any, Nothing, Nothing] =
    ZLayer.failCause(Cause.die(t))

  /**
   * A layer that does not produce any services.
   */
  val empty: ZLayer[Any, Nothing, Any] =
    ZLayer.Scoped(ZIO.succeedNow(ZEnvironment.empty))

  /**
   * Constructs a layer that fails with the specified error.
   */
  def fail[E](e: E)(implicit trace: ZTraceElement): Layer[E, Nothing] =
    failCause(Cause.fail(e))

  /**
   * Constructs a layer that fails with the specified cause.
   */
  def failCause[E](cause: Cause[E])(implicit trace: ZTraceElement): Layer[E, Nothing] =
    ZLayer(ZIO.failCause(cause))

  /**
   * Applies the function `f` to each element of the `Collection[A]` and returns
   * the results in a new `Collection[B]`.
   */
  def foreach[R, E, A, B: Tag, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(f: A => ZLayer[R, E, B])(implicit
    tag: Tag[Collection[B]],
    bf: BuildFrom[Collection[A], B, Collection[B]],
    trace: ZTraceElement
  ): ZLayer[R, E, Collection[B]] = {
    val builder: mutable.Builder[B, Collection[B]] = bf.newBuilder(in)
    in
      .foldLeft[ZLayer[R, E, Builder[B, Collection[B]]]](ZLayer.succeed(builder))((io, a) =>
        io.zipWithPar(f(a))((left, right) => ZEnvironment(left.get += right.get))
      )
      .map(environment => ZEnvironment(environment.get.result()))
  }

  /**
   * Constructs a layer from acquire and release actions. The acquire and
   * release actions will be performed uninterruptibly.
   */
  def fromAcquireRelease[R, E, A: Tag](acquire: ZIO[R, E, A])(release: A => URIO[R, Any])(implicit
    trace: ZTraceElement
  ): ZLayer[R, E, A] =
    ZLayer.scoped[R](ZIO.acquireRelease(acquire)(release))

  /**
   * Constructs a layer from acquire and release actions, which must return one
   * or more services. The acquire and release actions will be performed
   * uninterruptibly.
   */
  def fromAcquireReleaseEnvironment[R, E, A](
    acquire: ZIO[R, E, ZEnvironment[A]]
  )(release: ZEnvironment[A] => URIO[R, Any])(implicit
    trace: ZTraceElement
  ): ZLayer[R, E, A] =
    scopedEnvironment[R](ZIO.acquireRelease(acquire)(release))

  /**
   * Constructs a layer from acquire and release actions, which must return one
   * or more services. The acquire and release actions will be performed
   * uninterruptibly.
   */
  @deprecated("use fromAcquireReleaseEnvironment", "2.0.0")
  def fromAcquireReleaseMany[R, E, A](acquire: ZIO[R, E, ZEnvironment[A]])(release: ZEnvironment[A] => URIO[R, Any])(
    implicit trace: ZTraceElement
  ): ZLayer[R, E, A] =
    fromAcquireReleaseEnvironment(acquire)(release)

  /**
   * Constructs a layer from the specified effect.
   */
  @deprecated("use fromZIO", "2.0.0")
  def fromEffect[R, E, A: Tag](zio: ZIO[R, E, A])(implicit
    trace: ZTraceElement
  ): ZLayer[R, E, A] =
    fromZIO(zio)

  /**
   * Constructs a layer from the specified effect, which must return one or more
   * services.
   */
  @deprecated("use fromZIOMany", "2.0.0")
  def fromEffectMany[R, E, A](zio: ZIO[R, E, ZEnvironment[A]])(implicit
    trace: ZTraceElement
  ): ZLayer[R, E, A] =
    fromZIOMany(zio)

  /**
   * Constructs a layer from the environment using the specified function.
   */
  def fromFunction[A, B: Tag](f: ZEnvironment[A] => B)(implicit
    trace: ZTraceElement
  ): ZLayer[A, Nothing, B] =
    fromFunctionZIO(a => ZIO.succeedNow(f(a)))

  /**
   * Constructs a layer from the environment using the specified function, which
   * must return one or more services.
   */
  def fromFunctionEnvironment[A, B](f: ZEnvironment[A] => ZEnvironment[B])(implicit
    trace: ZTraceElement
  ): ZLayer[A, Nothing, B] =
    fromFunctionEnvironmentZIO(a => ZIO.succeedNow(f(a)))

  /**
   * Constructs a layer from the environment using the specified effectful
   * function, which must return one or more services.
   */
  def fromFunctionEnvironmentZIO[A, E, B](f: ZEnvironment[A] => IO[E, ZEnvironment[B]])(implicit
    trace: ZTraceElement
  ): ZLayer[A, E, B] =
    ZLayer.fromZIOEnvironment(ZIO.environment[A].flatMap(f))

  /**
   * Constructs a layer from the environment using the specified effectful
   * function.
   */
  @deprecated("use fromFunctionZIO", "2.0.0")
  def fromFunctionM[A, E, B: Tag](f: ZEnvironment[A] => IO[E, B])(implicit
    trace: ZTraceElement
  ): ZLayer[A, E, B] =
    fromFunctionZIO(f)

  /**
   * Constructs a layer from the environment using the specified function, which
   * must return one or more services.
   */

  @deprecated("use fromFunctionEnvironment", "2.0.0")
  def fromFunctionMany[A, B](f: ZEnvironment[A] => ZEnvironment[B])(implicit
    trace: ZTraceElement
  ): ZLayer[A, Nothing, B] =
    fromFunctionEnvironment(f)

  /**
   * Constructs a layer from the environment using the specified effectful
   * function, which must return one or more services.
   */
  @deprecated("use fromFunctionManyZIO", "2.0.0")
  def fromFunctionManyM[A, E, B](f: ZEnvironment[A] => IO[E, ZEnvironment[B]])(implicit
    trace: ZTraceElement
  ): ZLayer[A, E, B] =
    fromFunctionManyZIO(f)

  /**
   * Constructs a layer from the environment using the specified effectful
   * function, which must return one or more services.
   */
  @deprecated("use fromFunctionEnvironmentZIO", "2.0.0")
  def fromFunctionManyZIO[A, E, B](f: ZEnvironment[A] => IO[E, ZEnvironment[B]])(implicit
    trace: ZTraceElement
  ): ZLayer[A, E, B] =
    fromFunctionEnvironmentZIO(f)

  /**
   * Constructs a layer from the environment using the specified effectful
   * function.
   */
  def fromFunctionZIO[A, E, B: Tag](f: ZEnvironment[A] => IO[E, B])(implicit
    trace: ZTraceElement
  ): ZLayer[A, E, B] =
    ZLayer(ZIO.environmentWithZIO(f))

  /**
   * Constructs a layer that purely depends on the specified service.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromService[A: Tag, B: Tag](f: A => B)(implicit
    trace: ZTraceElement
  ): ZLayer[A, Nothing, B] =
    fromServiceM[A, Any, Nothing, B](a => ZIO.succeedNow(f(a)))

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[A0: Tag, A1: Tag, B: Tag](
    f: (A0, A1) => B
  )(implicit trace: ZTraceElement): ZLayer[A0 with A1, Nothing, B] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    B: Tag
  ](
    f: (A0, A1, A2) => B
  )(implicit trace: ZTraceElement): ZLayer[A0 with A1 with A2, Nothing, B] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3) => B
  )(implicit trace: ZTraceElement): ZLayer[A0 with A1 with A2 with A3, Nothing, B] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4) => B
  )(implicit
    trace: ZTraceElement
  ): ZLayer[A0 with A1 with A2 with A3 with A4, Nothing, B] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5) => B
  )(implicit
    trace: ZTraceElement
  ): ZLayer[A0 with A1 with A2 with A3 with A4 with A5, Nothing, B] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6) => B
  )(implicit trace: ZTraceElement): ZLayer[A0 with A1 with A2 with A3 with A4 with A5 with A6, Nothing, B] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7) => B
  )(implicit
    trace: ZTraceElement
  ): ZLayer[A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7, Nothing, B] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8) => B
  )(implicit
    trace: ZTraceElement
  ): ZLayer[A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8, Nothing, B] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9) => B
  )(implicit
    trace: ZTraceElement
  ): ZLayer[A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9, Nothing, B] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) => B
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10,
    Nothing,
    B
  ] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) => B
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11,
    Nothing,
    B
  ] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) => B
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12,
    Nothing,
    B
  ] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) => B
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13,
    Nothing,
    B
  ] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) => B
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14,
    Nothing,
    B
  ] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) => B
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15,
    Nothing,
    B
  ] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) => B
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16,
    Nothing,
    B
  ] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) => B
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17,
    Nothing,
    B
  ] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) => B
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18,
    Nothing,
    B
  ] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    A19: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) => B
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18 with A19,
    Nothing,
    B
  ] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    A19: Tag,
    A20: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) => B
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18 with A19 with A20,
    Nothing,
    B
  ] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServices[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    A19: Tag,
    A20: Tag,
    A21: Tag,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21) => B
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18 with A19 with A20 with A21,
    Nothing,
    B
  ] = {
    val layer = fromServicesM(andThen(f)(ZIO.succeedNow(_)))
    layer
  }

  /**
   * Constructs a layer that effectfully depends on the specified service.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServiceM[A: Tag, R, E, B: Tag](f: A => ZIO[R, E, B])(implicit
    trace: ZTraceElement
  ): ZLayer[R with A, E, B] =
    ZLayer {
      for {
        a <- ZIO.service[A]
        b <- f(a)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[A0: Tag, A1: Tag, R, E, B: Tag](
    f: (A0, A1) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[R with A0 with A1, E, B] =
    ZLayer {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        b  <- f(a0, a1)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[R with A0 with A1 with A2, E, B] =
    ZLayer {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        b  <- f(a0, a1, a2)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3) => ZIO[R, E, B]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[R with A0 with A1 with A2 with A3, E, B] =
    ZLayer {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        b  <- f(a0, a1, a2, a3)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4) => ZIO[R, E, B]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[R with A0 with A1 with A2 with A3 with A4, E, B] =
    ZLayer {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        a4 <- ZIO.service[A4]
        b  <- f(a0, a1, a2, a3, a4)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5) => ZIO[R, E, B]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[R with A0 with A1 with A2 with A3 with A4 with A5, E, B] =
    ZLayer {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        a4 <- ZIO.service[A4]
        a5 <- ZIO.service[A5]
        b  <- f(a0, a1, a2, a3, a4, a5)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[R with A0 with A1 with A2 with A3 with A4 with A5 with A6, E, B] =
    ZLayer {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        a4 <- ZIO.service[A4]
        a5 <- ZIO.service[A5]
        a6 <- ZIO.service[A6]
        b  <- f(a0, a1, a2, a3, a4, a5, a6)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7) => ZIO[R, E, B]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7, E, B] =
    ZLayer {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        a4 <- ZIO.service[A4]
        a5 <- ZIO.service[A5]
        a6 <- ZIO.service[A6]
        a7 <- ZIO.service[A7]
        b  <- f(a0, a1, a2, a3, a4, a5, a6, a7)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8) => ZIO[R, E, B]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8, E, B] =
    ZLayer {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        a4 <- ZIO.service[A4]
        a5 <- ZIO.service[A5]
        a6 <- ZIO.service[A6]
        a7 <- ZIO.service[A7]
        a8 <- ZIO.service[A8]
        b  <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9) => ZIO[R, E, B]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9, E, B] =
    ZLayer {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        a4 <- ZIO.service[A4]
        a5 <- ZIO.service[A5]
        a6 <- ZIO.service[A6]
        a7 <- ZIO.service[A7]
        a8 <- ZIO.service[A8]
        a9 <- ZIO.service[A9]
        b  <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10,
    E,
    B
  ] =
    ZLayer {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11,
    E,
    B
  ] =
    ZLayer {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12,
    E,
    B
  ] =
    ZLayer {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13,
    E,
    B
  ] =
    ZLayer {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14,
    E,
    B
  ] =
    ZLayer {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15,
    E,
    B
  ] =
    ZLayer {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16,
    E,
    B
  ] =
    ZLayer {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        a16 <- ZIO.service[A16]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17,
    E,
    B
  ] =
    ZLayer {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        a16 <- ZIO.service[A16]
        a17 <- ZIO.service[A17]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18,
    E,
    B
  ] =
    ZLayer {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        a16 <- ZIO.service[A16]
        a17 <- ZIO.service[A17]
        a18 <- ZIO.service[A18]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    A19: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18 with A19,
    E,
    B
  ] =
    ZLayer {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        a16 <- ZIO.service[A16]
        a17 <- ZIO.service[A17]
        a18 <- ZIO.service[A18]
        a19 <- ZIO.service[A19]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    A19: Tag,
    A20: Tag,
    R,
    E,
    B: Tag
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18 with A19 with A20,
    E,
    B
  ] =
    ZLayer {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        a16 <- ZIO.service[A16]
        a17 <- ZIO.service[A17]
        a18 <- ZIO.service[A18]
        a19 <- ZIO.service[A19]
        a20 <- ZIO.service[A20]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    A19: Tag,
    A20: Tag,
    A21: Tag,
    R,
    E,
    B: Tag
  ](
    f: (
      A0,
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      A19,
      A20,
      A21
    ) => ZIO[R, E, B]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18 with A19 with A20 with A21,
    E,
    B
  ] =
    ZLayer {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        a16 <- ZIO.service[A16]
        a17 <- ZIO.service[A17]
        a18 <- ZIO.service[A18]
        a19 <- ZIO.service[A19]
        a20 <- ZIO.service[A20]
        a21 <- ZIO.service[A21]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21)
      } yield b
    }

  /**
   * Constructs a layer that purely depends on the specified service, which must
   * return one or more services. For the more common variant that returns a
   * single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServiceMany[A: Tag, B](f: A => ZEnvironment[B])(implicit
    trace: ZTraceElement
  ): ZLayer[A, Nothing, B] =
    fromServiceManyM[A, Any, Nothing, B](a => ZIO.succeedNow(f(a)))

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[A0: Tag, A1: Tag, B](
    f: (A0, A1) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[A0 with A1, Nothing, B] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[A0: Tag, A1: Tag, A2: Tag, B](
    f: (A0, A1, A2) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[A0 with A1 with A2, Nothing, B] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    B
  ](
    f: (A0, A1, A2, A3) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[A0 with A1 with A2 with A3, Nothing, B] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4) => ZEnvironment[B]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[A0 with A1 with A2 with A3 with A4, Nothing, B] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5) => ZEnvironment[B]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[A0 with A1 with A2 with A3 with A4 with A5, Nothing, B] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[A0 with A1 with A2 with A3 with A4 with A5 with A6, Nothing, B] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7) => ZEnvironment[B]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7, Nothing, B] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8) => ZEnvironment[B]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8, Nothing, B] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9) => ZEnvironment[B]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9, Nothing, B] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10,
    Nothing,
    B
  ] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11,
    Nothing,
    B
  ] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12,
    Nothing,
    B
  ] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13,
    Nothing,
    B
  ] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14,
    Nothing,
    B
  ] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15,
    Nothing,
    B
  ] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16,
    Nothing,
    B
  ] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17,
    Nothing,
    B
  ] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18,
    Nothing,
    B
  ] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    A19: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18 with A19,
    Nothing,
    B
  ] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    A19: Tag,
    A20: Tag,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) => ZEnvironment[
      B
    ]
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18 with A19 with A20,
    Nothing,
    B
  ] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that purely depends on the specified services, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromService`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesMany[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    A19: Tag,
    A20: Tag,
    A21: Tag,
    B
  ](
    f: (
      A0,
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      A19,
      A20,
      A21
    ) => ZEnvironment[B]
  )(implicit trace: ZTraceElement): ZLayer[
    A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18 with A19 with A20 with A21,
    Nothing,
    B
  ] = {
    val layer = fromServicesManyM(andThen(f)(ZIO.succeedNow))
    layer
  }

  /**
   * Constructs a layer that effectfully depends on the specified service, which
   * must return one or more services. For the more common variant that returns
   * a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServiceManyM[A: Tag, R, E, B](f: A => ZIO[R, E, ZEnvironment[B]])(implicit
    trace: ZTraceElement
  ): ZLayer[R with A, E, B] =
    ZLayer.fromZIOEnvironment {
      for {
        a <- ZIO.service[A]
        b <- f(a)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[A0: Tag, A1: Tag, R, E, B](
    f: (A0, A1) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[R with A0 with A1, E, B] =
    ZLayer.fromZIOEnvironment {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        b  <- f(a0, a1)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[A0: Tag, A1: Tag, A2: Tag, R, E, B](
    f: (A0, A1, A2) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[R with A0 with A1 with A2, E, B] =
    ZLayer.fromZIOEnvironment {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        b  <- f(a0, a1, a2)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[R with A0 with A1 with A2 with A3, E, B] =
    ZLayer.fromZIOEnvironment {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        b  <- f(a0, a1, a2, a3)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4) => ZIO[R, E, ZEnvironment[B]]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[R with A0 with A1 with A2 with A3 with A4, E, B] =
    ZLayer.fromZIOEnvironment {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        a4 <- ZIO.service[A4]
        b  <- f(a0, a1, a2, a3, a4)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5) => ZIO[R, E, ZEnvironment[B]]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[R with A0 with A1 with A2 with A3 with A4 with A5, E, B] =
    ZLayer.fromZIOEnvironment {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        a4 <- ZIO.service[A4]
        a5 <- ZIO.service[A5]
        b  <- f(a0, a1, a2, a3, a4, a5)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[R with A0 with A1 with A2 with A3 with A4 with A5 with A6, E, B] =
    ZLayer.fromZIOEnvironment {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        a4 <- ZIO.service[A4]
        a5 <- ZIO.service[A5]
        a6 <- ZIO.service[A6]
        b  <- f(a0, a1, a2, a3, a4, a5, a6)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7) => ZIO[R, E, ZEnvironment[B]]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7, E, B] =
    ZLayer.fromZIOEnvironment {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        a4 <- ZIO.service[A4]
        a5 <- ZIO.service[A5]
        a6 <- ZIO.service[A6]
        a7 <- ZIO.service[A7]
        b  <- f(a0, a1, a2, a3, a4, a5, a6, a7)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8) => ZIO[R, E, ZEnvironment[B]]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8, E, B] =
    ZLayer.fromZIOEnvironment {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        a4 <- ZIO.service[A4]
        a5 <- ZIO.service[A5]
        a6 <- ZIO.service[A6]
        a7 <- ZIO.service[A7]
        a8 <- ZIO.service[A8]
        b  <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9) => ZIO[R, E, ZEnvironment[B]]
  )(implicit
    trace: ZTraceElement
  ): ZLayer[R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9, E, B] =
    ZLayer.fromZIOEnvironment {
      for {
        a0 <- ZIO.service[A0]
        a1 <- ZIO.service[A1]
        a2 <- ZIO.service[A2]
        a3 <- ZIO.service[A3]
        a4 <- ZIO.service[A4]
        a5 <- ZIO.service[A5]
        a6 <- ZIO.service[A6]
        a7 <- ZIO.service[A7]
        a8 <- ZIO.service[A8]
        a9 <- ZIO.service[A9]
        b  <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10,
    E,
    B
  ] =
    ZLayer.fromZIOEnvironment {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11,
    E,
    B
  ] =
    ZLayer.fromZIOEnvironment {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12,
    E,
    B
  ] =
    ZLayer.fromZIOEnvironment {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13,
    E,
    B
  ] =
    ZLayer.fromZIOEnvironment {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14,
    E,
    B
  ] =
    ZLayer.fromZIOEnvironment {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15,
    E,
    B
  ] =
    ZLayer.fromZIOEnvironment {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16,
    E,
    B
  ] =
    ZLayer.fromZIOEnvironment {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        a16 <- ZIO.service[A16]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    R,
    E,
    B
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17,
    E,
    B
  ] =
    ZLayer.fromZIOEnvironment {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        a16 <- ZIO.service[A16]
        a17 <- ZIO.service[A17]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    R,
    E,
    B
  ](
    f: (
      A0,
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18
    ) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18,
    E,
    B
  ] =
    ZLayer.fromZIOEnvironment {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        a16 <- ZIO.service[A16]
        a17 <- ZIO.service[A17]
        a18 <- ZIO.service[A18]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    A19: Tag,
    R,
    E,
    B
  ](
    f: (
      A0,
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      A19
    ) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18 with A19,
    E,
    B
  ] =
    ZLayer.fromZIOEnvironment {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        a16 <- ZIO.service[A16]
        a17 <- ZIO.service[A17]
        a18 <- ZIO.service[A18]
        a19 <- ZIO.service[A19]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    A19: Tag,
    A20: Tag,
    R,
    E,
    B
  ](
    f: (
      A0,
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      A19,
      A20
    ) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18 with A19 with A20,
    E,
    B
  ] =
    ZLayer.fromZIOEnvironment {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        a16 <- ZIO.service[A16]
        a17 <- ZIO.service[A17]
        a18 <- ZIO.service[A18]
        a19 <- ZIO.service[A19]
        a20 <- ZIO.service[A20]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20)
      } yield b
    }

  /**
   * Constructs a layer that effectfully depends on the specified services,
   * which must return one or more services. For the more common variant that
   * returns a single service see `fromServiceM`.
   */
  @deprecated("use toLayer", "2.0.0")
  def fromServicesManyM[
    A0: Tag,
    A1: Tag,
    A2: Tag,
    A3: Tag,
    A4: Tag,
    A5: Tag,
    A6: Tag,
    A7: Tag,
    A8: Tag,
    A9: Tag,
    A10: Tag,
    A11: Tag,
    A12: Tag,
    A13: Tag,
    A14: Tag,
    A15: Tag,
    A16: Tag,
    A17: Tag,
    A18: Tag,
    A19: Tag,
    A20: Tag,
    A21: Tag,
    R,
    E,
    B
  ](
    f: (
      A0,
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      A19,
      A20,
      A21
    ) => ZIO[R, E, ZEnvironment[B]]
  )(implicit trace: ZTraceElement): ZLayer[
    R with A0 with A1 with A2 with A3 with A4 with A5 with A6 with A7 with A8 with A9 with A10 with A11 with A12 with A13 with A14 with A15 with A16 with A17 with A18 with A19 with A20 with A21,
    E,
    B
  ] =
    ZLayer.fromZIOEnvironment {
      for {
        a0  <- ZIO.service[A0]
        a1  <- ZIO.service[A1]
        a2  <- ZIO.service[A2]
        a3  <- ZIO.service[A3]
        a4  <- ZIO.service[A4]
        a5  <- ZIO.service[A5]
        a6  <- ZIO.service[A6]
        a7  <- ZIO.service[A7]
        a8  <- ZIO.service[A8]
        a9  <- ZIO.service[A9]
        a10 <- ZIO.service[A10]
        a11 <- ZIO.service[A11]
        a12 <- ZIO.service[A12]
        a13 <- ZIO.service[A13]
        a14 <- ZIO.service[A14]
        a15 <- ZIO.service[A15]
        a16 <- ZIO.service[A16]
        a17 <- ZIO.service[A17]
        a18 <- ZIO.service[A18]
        a19 <- ZIO.service[A19]
        a20 <- ZIO.service[A20]
        a21 <- ZIO.service[A21]
        b   <- f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21)
      } yield b
    }

  /**
   * Constructs a layer from the specified effect.
   */
  def fromZIO[R, E, A: Tag](zio: ZIO[R, E, A])(implicit
    trace: ZTraceElement
  ): ZLayer[R, E, A] =
    fromZIOEnvironment(zio.map(ZEnvironment(_)))

  /**
   * Constructs a layer from the specified effect, which must return one or more
   * services.
   */
  def fromZIOEnvironment[R, E, A](zio: ZIO[R, E, ZEnvironment[A]])(implicit
    trace: ZTraceElement
  ): ZLayer[R, E, A] =
    ZLayer.Scoped[R, E, A](zio)

  /**
   * Constructs a layer from the specified effect, which must return one or more
   * services.
   */
  @deprecated("use fromZIOEnvironment", "2.0.0")
  def fromZIOMany[R, E, A](zio: ZIO[R, E, ZEnvironment[A]])(implicit trace: ZTraceElement): ZLayer[R, E, A] =
    fromZIOEnvironment(zio)

  /**
   * An identity layer that passes along its inputs. Note that this represents
   * an identity with respect to the `>>>` operator. It represents an identity
   * with respect to the `++` operator when the environment type is `Any`.
   */
  @deprecated("use environment", "2.0.0")
  def identity[A: EnvironmentTag](implicit trace: ZTraceElement): ZLayer[A, Nothing, A] =
    ZLayer.environment[A]

  /**
   * Constructs a layer that passes along the specified environment as an
   * output.
   */
  @deprecated("use environment", "2.0.0")
  def requires[A: EnvironmentTag](implicit trace: ZTraceElement): ZLayer[A, Nothing, A] =
    ZLayer.environment[A]

  /**
   * Constructs a layer that passes along the specified environment as an
   * output.
   */
  def environment[A](implicit trace: ZTraceElement): ZLayer[A, Nothing, A] =
    ZLayer.fromZIOEnvironment(ZIO.environment[A])

  /**
   * A layer that constructs a scope and closes it when the workflow the layer
   * is provided to completes execution, whether by success, failure, or
   * interruption. This can be used to close a scope when providing a layer to a
   * workflow.
   */
  val scope: ZLayer[Any, Nothing, Scope.Closeable] =
    ZLayer.Scoped[Any, Nothing, Scope.Closeable](
      ZIO
        .acquireReleaseExit(Scope.make)((scope, exit) => scope.close(exit))(ZTraceElement.empty)
        .map(ZEnvironment(_))(ZTraceElement.empty)
    )

  /**
   * Constructs a layer from the specified scoped effect.
   */
  def scoped[R]: ScopedPartiallyApplied[R] =
    new ScopedPartiallyApplied[R]

  /**
   * Constructs a layer from the specified scoped effect, which must return one
   * or more services.
   */
  def scopedEnvironment[R]: ScopedEnvironmentPartiallyApplied[R] =
    new ScopedEnvironmentPartiallyApplied[R]

  /**
   * Constructs a layer that accesses and returns the specified service from the
   * environment.
   */
  def service[A: Tag](implicit trace: ZTraceElement): ZLayer[A, Nothing, A] =
    ZLayer.fromZIO(ZIO.service[A])

  /**
   * Constructs a layer from the specified value.
   */
  def succeed[A: Tag](a: A)(implicit trace: ZTraceElement): ULayer[A] =
    ZLayer.fromZIOEnvironment(ZIO.succeedNow(ZEnvironment(a)))

  /**
   * Constructs a layer from the specified value, which must return one or more
   * services.
   */
  def succeedEnvironment[A](a: ZEnvironment[A])(implicit trace: ZTraceElement): ULayer[A] =
    ZLayer.fromZIOEnvironment(ZIO.succeedNow(a))

  /**
   * Constructs a layer from the specified value, which must return one or more
   * services.
   */
  @deprecated("use succeedEnvironment", "2.0.0")
  def succeedMany[A](a: ZEnvironment[A])(implicit trace: ZTraceElement): ULayer[A] =
    succeedEnvironment(a)

  /**
   * Lazily constructs a layer. This is useful to avoid infinite recursion when
   * creating layers that refer to themselves.
   */
  def suspend[RIn, E, ROut](layer: => ZLayer[RIn, E, ROut]): ZLayer[RIn, E, ROut] = {
    lazy val self = layer
    Suspend(() => self)
  }

  implicit final class ZLayerInvariantOps[RIn, E, ROut](private val self: ZLayer[RIn, E, ROut]) extends AnyVal {

    /**
     * Returns a new layer that produces the outputs of this layer but also
     * passes through the inputs.
     */
    def passthrough(implicit
      in: EnvironmentTag[RIn],
      out: EnvironmentTag[ROut],
      trace: ZTraceElement
    ): ZLayer[RIn, E, RIn with ROut] =
      ZLayer.environment[RIn] ++ self

    /**
     * Projects out part of one of the services output by this layer using the
     * specified function.
     */
    def project[ROut2: Tag](
      f: ROut => ROut2
    )(implicit tag: Tag[ROut], trace: ZTraceElement): ZLayer[RIn, E, ROut2] =
      self.map(environment => ZEnvironment(f(environment.get)))

    /**
     * Returns a layer that produces a reloadable version of this service.
     */
    def reloadableAuto(schedule: Schedule[RIn, Any, Any])(implicit
      tagOut: Tag[ROut],
      trace: ZTraceElement
    ): ZLayer[RIn, E, Reloadable[ROut]] =
      Reloadable.auto(self, schedule)

    /**
     * Returns a layer that produces a reloadable version of this service, where
     * the reloading schedule is derived from the layer input.
     */
    def reloadableAutoFromConfig[RIn2](scheduleFromConfig: ZEnvironment[RIn2] => Schedule[RIn with RIn2, Any, Any])(
      implicit
      tagOut: Tag[ROut],
      trace: ZTraceElement
    ): ZLayer[RIn with RIn2, E, Reloadable[ROut]] =
      Reloadable.autoFromConfig(self, scheduleFromConfig)

    /**
     * Returns a layer that produces a reloadable version of this service.
     */
    def reloadableManual(implicit
      tagOut: Tag[ROut],
      trace: ZTraceElement
    ): ZLayer[RIn, E, Reloadable[ROut]] =
      Reloadable.manual(self)
  }

  /**
   * A `MemoMap` memoizes layers.
   */
  private abstract class MemoMap { self =>

    /**
     * Checks the memo map to see if a layer exists. If it is, immediately
     * returns it.'' Otherwise, obtains the layer, stores it in the memo map,
     * and adds a finalizer to the `Scope`.
     */
    def getOrElseMemoize[E, A, B](scope: Scope)(layer: ZLayer[A, E, B]): ZIO[A, E, ZEnvironment[B]]
  }

  private object MemoMap {

    /**
     * Constructs an empty memo map.
     */
    def make(implicit trace: ZTraceElement): UIO[MemoMap] =
      Ref.Synchronized
        .make[Map[ZLayer[Nothing, Any, Any], (IO[Any, Any], Exit[Any, Any] => UIO[Any])]](Map.empty)
        .map { ref =>
          new MemoMap { self =>
            final def getOrElseMemoize[E, A, B](scope: Scope)(
              layer: ZLayer[A, E, B]
            ): ZIO[A, E, ZEnvironment[B]] =
              ref.modifyZIO { map =>
                map.get(layer) match {
                  case Some((acquire, release)) =>
                    val cached: ZIO[Any, E, ZEnvironment[B]] = acquire
                      .asInstanceOf[IO[E, ZEnvironment[B]]]
                      .onExit {
                        case Exit.Success(_) => scope.addFinalizerExit(release)
                        case Exit.Failure(_) => UIO.unit
                      }

                    UIO.succeed((cached, map))
                  case None =>
                    for {
                      observers    <- Ref.make(0)
                      promise      <- Promise.make[E, ZEnvironment[B]]
                      finalizerRef <- Ref.make[Exit[Any, Any] => UIO[Any]](_ => ZIO.unit)

                      resource = ZIO.uninterruptibleMask { restore =>
                                   for {
                                     a          <- ZIO.environment[A]
                                     outerScope  = scope
                                     innerScope <- Scope.make
                                     tp <-
                                       restore(
                                         layer
                                           .scope(innerScope)
                                           .flatMap(_.apply(self))
                                       ).exit.flatMap {
                                         case e @ Exit.Failure(cause) =>
                                           promise.failCause(cause) *> innerScope.close(e) *> ZIO
                                             .failCause(cause)

                                         case Exit.Success(b) =>
                                           for {
                                             _ <- finalizerRef.set { (e: Exit[Any, Any]) =>
                                                    ZIO.whenZIO(observers.modify(n => (n == 1, n - 1)))(
                                                      innerScope.close(e)
                                                    )
                                                  }
                                             _ <- observers.update(_ + 1)
                                             outerFinalizer <-
                                               outerScope.addFinalizerExit(e => finalizerRef.get.flatMap(_.apply(e)))
                                             _ <- promise.succeed(b)
                                           } yield b
                                       }
                                   } yield tp
                                 }

                      memoized = (
                                   promise.await.onExit {
                                     case Exit.Failure(_) => UIO.unit
                                     case Exit.Success(_) => observers.update(_ + 1)
                                   },
                                   (exit: Exit[Any, Any]) => finalizerRef.get.flatMap(_(exit))
                                 )
                    } yield (resource, if (layer.isFresh) map else map + (layer -> memoized))

                }
              }.flatten
          }
        }
  }

  private[zio] def andThen[A0, A1, B, C](f: (A0, A1) => B)(g: B => C): (A0, A1) => C =
    (a0, a1) => g(f(a0, a1))

  private[zio] def andThen[A0, A1, A2, B, C](f: (A0, A1, A2) => B)(g: B => C): (A0, A1, A2) => C =
    (a0, a1, a2) => g(f(a0, a1, a2))

  private[zio] def andThen[A0, A1, A2, A3, B, C](f: (A0, A1, A2, A3) => B)(g: B => C): (A0, A1, A2, A3) => C =
    (a0, a1, a2, a3) => g(f(a0, a1, a2, a3))

  private[zio] def andThen[A0, A1, A2, A3, A4, B, C](f: (A0, A1, A2, A3, A4) => B)(
    g: B => C
  ): (A0, A1, A2, A3, A4) => C =
    (a0, a1, a2, a3, a4) => g(f(a0, a1, a2, a3, a4))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, B, C](f: (A0, A1, A2, A3, A4, A5) => B)(
    g: B => C
  ): (A0, A1, A2, A3, A4, A5) => C =
    (a0, a1, a2, a3, a4, a5) => g(f(a0, a1, a2, a3, a4, a5))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, B, C](f: (A0, A1, A2, A3, A4, A5, A6) => B)(
    g: B => C
  ): (A0, A1, A2, A3, A4, A5, A6) => C =
    (a0, a1, a2, a3, a4, a5, a6) => g(f(a0, a1, a2, a3, a4, a5, a6))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, A7, B, C](f: (A0, A1, A2, A3, A4, A5, A6, A7) => B)(
    g: B => C
  ): (A0, A1, A2, A3, A4, A5, A6, A7) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7) => g(f(a0, a1, a2, a3, a4, a5, a6, a7))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, A7, A8, B, C](f: (A0, A1, A2, A3, A4, A5, A6, A7, A8) => B)(
    g: B => C
  ): (A0, A1, A2, A3, A4, A5, A6, A7, A8) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8) => g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, B, C](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9) => B
  )(
    g: B => C
  ): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9) => g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, B, C](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) => B
  )(g: B => C): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) => g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, B, C](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) => B
  )(g: B => C): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) => g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, B, C](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) => B
  )(g: B => C): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12) =>
      g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, B, C](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) => B
  )(g: B => C): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13) =>
      g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, B, C](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) => B
  )(g: B => C): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14) =>
      g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, B, C](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) => B
  )(g: B => C): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15) =>
      g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, B, C](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) => B
  )(g: B => C): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16) =>
      g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, B, C](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) => B
  )(g: B => C): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17) =>
      g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17))

  private[zio] def andThen[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, B, C](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) => B
  )(g: B => C): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18) =>
      g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18))

  private[zio] def andThen[
    A0,
    A1,
    A2,
    A3,
    A4,
    A5,
    A6,
    A7,
    A8,
    A9,
    A10,
    A11,
    A12,
    A13,
    A14,
    A15,
    A16,
    A17,
    A18,
    A19,
    B,
    C
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) => B
  )(g: B => C): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19) =>
      g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19))

  private[zio] def andThen[
    A0,
    A1,
    A2,
    A3,
    A4,
    A5,
    A6,
    A7,
    A8,
    A9,
    A10,
    A11,
    A12,
    A13,
    A14,
    A15,
    A16,
    A17,
    A18,
    A19,
    A20,
    B,
    C
  ](
    f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) => B
  )(g: B => C): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20) =>
      g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20))

  private[zio] def andThen[
    A0,
    A1,
    A2,
    A3,
    A4,
    A5,
    A6,
    A7,
    A8,
    A9,
    A10,
    A11,
    A12,
    A13,
    A14,
    A15,
    A16,
    A17,
    A18,
    A19,
    A20,
    A21,
    B,
    C
  ](f: (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21) => B)(
    g: B => C
  ): (A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21) => C =
    (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21) =>
      g(f(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21))

  implicit final class ScopedPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[E, A: Tag](zio: => ZIO[Scope with R, E, A])(implicit
      trace: ZTraceElement
    ): ZLayer[R, E, A] =
      scopedEnvironment[R](zio.map(ZEnvironment(_)))
  }

  implicit final class ScopedEnvironmentPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[E, A](zio: => ZIO[Scope with R, E, ZEnvironment[A]])(implicit
      trace: ZTraceElement
    ): ZLayer[R, E, A] =
      Scoped[R, E, A](zio)
  }

  implicit final class ZLayerProvideSomeOps[RIn, E, ROut](private val self: ZLayer[RIn, E, ROut]) extends AnyVal {

    /**
     * Provides an effect with part of its required environment, eliminating its
     * dependency on the services output by this layer.
     */
    final def apply[R, E1 >: E, A](
      zio: ZIO[ROut with R, E1, A]
    )(implicit
      ev1: EnvironmentTag[R],
      ev2: EnvironmentTag[ROut],
      ev3: EnvironmentTag[RIn],
      trace: ZTraceElement
    ): ZIO[RIn with R, E1, A] =
      ZIO.provideLayer[RIn, E1, ROut, R, A](self)(zio)

    /**
     * Feeds the output services of this layer into the input of the specified
     * layer, resulting in a new layer with the inputs of this layer as well as
     * any leftover inputs, and the outputs of the specified layer.
     */
    def >>>[RIn2, E1 >: E, ROut2](
      that: ZLayer[ROut with RIn2, E1, ROut2]
    )(implicit tag: EnvironmentTag[ROut], trace: ZTraceElement): ZLayer[RIn with RIn2, E1, ROut2] =
      ZLayer.To(ZLayer.environment[RIn2] ++ self, that)

    /**
     * Feeds the output services of this layer into the input of the specified
     * layer, resulting in a new layer with the inputs of this layer as well as
     * any leftover inputs, and the outputs of the specified layer.
     */
    def >>>[E1 >: E, ROut2](that: ZLayer[ROut, E1, ROut2])(implicit
      trace: ZTraceElement
    ): ZLayer[RIn, E1, ROut2] =
      ZLayer.To(self, that)

    /**
     * Feeds the output services of this layer into the input of the specified
     * layer, resulting in a new layer with the inputs of this layer, and the
     * outputs of both layers.
     */
    def >+>[RIn2, E1 >: E, ROut2](
      that: ZLayer[ROut with RIn2, E1, ROut2]
    )(implicit
      tagged: EnvironmentTag[ROut],
      tagged2: EnvironmentTag[ROut2],
      trace: ZTraceElement
    ): ZLayer[RIn with RIn2, E1, ROut with ROut2] =
      self ++ self.>>>[RIn2, E1, ROut2](that)

    /**
     * Feeds the output services of this layer into the input of the specified
     * layer, resulting in a new layer with the inputs of this layer, and the
     * outputs of both layers.
     */
    def >+>[E1 >: E, RIn2 >: ROut, ROut1 >: ROut, ROut2](
      that: ZLayer[RIn2, E1, ROut2]
    )(implicit
      tagged: EnvironmentTag[ROut2],
      trace: ZTraceElement
    ): ZLayer[RIn, E1, ROut1 with ROut2] =
      self.zipWithPar(self >>> that)(_.union[ROut2](_))
  }
}
