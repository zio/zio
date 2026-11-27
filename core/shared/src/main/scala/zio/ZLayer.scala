/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.collection.mutable
import scala.collection.mutable.Builder

/**
 * A `ZLayer[RIn, E, ROut]` describes how to build one or more services of type
 * `ROut` in your application, given the services `RIn` they depend on. Building
 * the layer may fail with an error of type `E`. Services can be injected into
 * effects via `ZIO#provide`. Effects can require services via `ZIO.service`.
 *
 * Layer can be thought of as recipes for producing bundles of services, given
 * their dependencies (other services).
 *
 * Construction of services can be effectful and utilize resources that must be
 * acquired and safely released when the services are done being utilized.
 * `ZLayer.scoped` acquires a resource in the scope of the layer, and the
 * resource is released when that scope closes, for example when the effect that
 * a layer was provided to completes.
 *
 * Within one build (one `provide` or one `build` call), a layer value that
 * appears several times is built once and its services are shared, except that
 * the layers inside a `fresh` layer are built apart from the rest of the build.
 * Each separate `provide` or `build` call builds it again. `fresh` opts a layer
 * out of sharing, and `memoize` shares a layer across several `provideLayer`
 * calls.
 *
 * `++` combines two layers horizontally: it builds them in parallel and has the
 * inputs and outputs of both. `>>>` combines them vertically: it feeds the
 * outputs of the first layer into the second one. `>+>` does the same but also
 * keeps the outputs of the first layer. With `>>>`, the resources of the second
 * layer are released before those of the first one.
 *
 * {{{
 * final case class DbConfig(url: String)
 *
 * final class Database(config: DbConfig) {
 *   def query(sql: String): UIO[String] = ZIO.succeed(config.url + " ran " + sql)
 * }
 *
 * final class UserRepo(db: Database) {
 *   def countUsers: UIO[String] = db.query("SELECT count(*) FROM users")
 * }
 *
 * val configLayer: ULayer[DbConfig] =
 *   ZLayer.succeed(DbConfig("jdbc:postgresql://localhost:5432/shop"))
 *
 * val databaseLayer: ZLayer[DbConfig, Nothing, Database] =
 *   ZLayer.scoped {
 *     for {
 *       config <- ZIO.service[DbConfig]
 *       db <- ZIO.acquireRelease(ZIO.debug("opening database").as(new Database(config)))(_ =>
 *               ZIO.debug("closing database")
 *             )
 *     } yield db
 *   }
 *
 * val userRepoLayer: ZLayer[Database, Nothing, UserRepo] =
 *   ZLayer.fromFunction((db: Database) => new UserRepo(db))
 *
 * val appLayer: ULayer[UserRepo] = configLayer >>> databaseLayer >>> userRepoLayer
 *
 * val program: UIO[String] = ZIO.serviceWithZIO[UserRepo](_.countUsers).provide(appLayer)
 * // prints "opening database", then "closing database" once countUsers is done
 * // result: jdbc:postgresql://localhost:5432/shop ran SELECT count(*) FROM users
 * }}}
 *
 * Because of their composition properties, layers are the idiomatic way in ZIO
 * to create services that depend on other services.
 *
 * @see
 *   [[https://zio.dev/reference/contextual/zlayer/ ZLayer]]
 */
sealed abstract class ZLayer[-RIn, +E, +ROut] extends ZLayerVersionSpecific[RIn, E, ROut] { self =>

  /**
   * A symbolic alias for `orDie`.
   */
  final def !(implicit
    ev1: E <:< Throwable,
    ev2: CanFail[E],
    trace: Trace
  ): ZLayer[RIn, Nothing, ROut] =
    self.orDie

  /**
   * Combines this layer with the specified layer sequentially, producing a new
   * layer that has the inputs and outputs of both.
   */
  final def <*>[E1 >: E, RIn2, ROut1 >: ROut, ROut2](
    that: => ZLayer[RIn2, E1, ROut2]
  )(implicit tag: EnvironmentTag[ROut2]): ZLayer[RIn with RIn2, E1, ROut1 with ROut2] =
    self.zipWith(that)(_.union[ROut2](_))

  /**
   * Combines this layer with the specified layer in parallel, like `++`. Unlike
   * `++`, it does not need an `EnvironmentTag` for `ROut2` and does not prune
   * the output of `that` to `ROut2`, so services that `that` builds beyond
   * `ROut2` stay in the output. When both outputs hold the same service, the
   * one from `that` is kept.
   */
  final def +!+[E1 >: E, RIn2, ROut1 >: ROut, ROut2](
    that: => ZLayer[RIn2, E1, ROut2]
  ): ZLayer[RIn with RIn2, E1, ROut1 with ROut2] =
    self.zipWithPar(that)(_.unionAll[ROut2](_))

  /**
   * Combines this layer with the specified layer in parallel, producing a new
   * layer that has the inputs and outputs of both. The two layers are built
   * concurrently, and their resources are released concurrently.
   */
  final def ++[E1 >: E, RIn2, ROut1 >: ROut, ROut2](
    that: => ZLayer[RIn2, E1, ROut2]
  )(implicit tag: EnvironmentTag[ROut2]): ZLayer[RIn with RIn2, E1, ROut1 with ROut2] =
    self.zipWithPar(that)(_.union[ROut2](_))

  /**
   * A symbolic alias for `orElse`.
   */
  def <>[RIn1 <: RIn, E1, ROut1 >: ROut](
    that: => ZLayer[RIn1, E1, ROut1]
  )(implicit ev: CanFail[E], trace: Trace): ZLayer[RIn1, E1, ROut1] =
    self.orElse(that)

  /**
   * Returns a new layer that applies the specified aspect to this layer.
   * Aspects are "transformers" that modify the behavior of their input in some
   * well-defined way (for example, `ZLayerAspect.retry` retries building the
   * layer).
   */
  final def @@[
    LowerRIn <: UpperRIn,
    UpperRIn <: RIn,
    LowerE >: E,
    UpperE >: LowerE,
    LowerROut >: ROut,
    UpperROut >: LowerROut
  ](
    aspect: => ZLayerAspect[LowerRIn, UpperRIn, LowerE, UpperE, LowerROut, UpperROut]
  )(implicit trace: Trace): ZLayer[UpperRIn, LowerE, LowerROut] =
    ZLayer.suspend(aspect(self))

  /**
   * A named alias for `++`.
   */
  final def and[E1 >: E, RIn2, ROut1 >: ROut, ROut2](
    that: => ZLayer[RIn2, E1, ROut2]
  )(implicit tag: EnvironmentTag[ROut2]): ZLayer[RIn with RIn2, E1, ROut1 with ROut2] =
    self.++[E1, RIn2, ROut1, ROut2](that)

  /**
   * A named alias for `>+>`.
   */
  final def andTo[E1 >: E, RIn2 >: ROut, ROut1 >: ROut, ROut2](
    that: => ZLayer[RIn2, E1, ROut2]
  )(implicit
    tagged: EnvironmentTag[ROut2],
    trace: Trace
  ): ZLayer[RIn, E1, ROut1 with ROut2] =
    self.>+>[E1, RIn2, ROut1, ROut2](that)

  /**
   * Builds a layer into a scoped value.
   */
  final def build(implicit trace: Trace): ZIO[RIn with Scope, E, ZEnvironment[ROut]] =
    ZIO.scopeWith(build(_))

  /**
   * Builds a layer into a ZIO value. Any resources associated with this layer
   * will be released when the specified scope is closed unless their scope has
   * been extended. This allows building layers where the lifetime of some of
   * the services output by the layer exceed the lifetime of the effect the
   * layer is provided to.
   *
   * Each call builds the layer again: a layer that appears several times in
   * this layer is built once per call, not once across calls. The layers inside
   * a `fresh` layer are built apart from the rest of the call.
   */
  final def build(scope: => Scope)(implicit trace: Trace): ZIO[RIn, E, ZEnvironment[ROut]] =
    ZIO.suspendSucceed {
      val memoMap = new ZLayer.MemoMap()(trace, Unsafe)
      self.scope(scope, memoMap)
    }

  /**
   * Recovers from the failures of type `E` by building the layer returned by
   * `handler`. Defects and interruptions are not handled: the resulting layer
   * fails with them.
   */
  final def catchAll[RIn1 <: RIn, E1, ROut1 >: ROut](
    handler: E => ZLayer[RIn1, E1, ROut1]
  )(implicit trace: Trace): ZLayer[RIn1, E1, ROut1] =
    foldLayer(handler, ZLayer.succeedEnvironment(_))

  /**
   * Recovers from all errors. The handler receives the full `Cause`, so it is
   * also called for defects.
   */
  final def catchAllCause[RIn1 <: RIn, E1, ROut1 >: ROut](
    handler: Cause[E] => ZLayer[RIn1, E1, ROut1]
  )(implicit trace: Trace): ZLayer[RIn1, E1, ROut1] =
    foldCauseLayer(handler, ZLayer.succeedEnvironment(_))

  /**
   * Taps the layer, printing the result of calling `.toString` on the value.
   * The value is the `ZEnvironment` of the outputs, so `ZLayer.succeed(8080)`
   * prints `ZEnvironment(Int -> 8080)`. When the layer fails or dies, it prints
   * `<FAIL>` followed by the `Cause`.
   */
  final def debug(implicit trace: Trace): ZLayer[RIn, E, ROut] =
    self
      .tap(value => ZIO.succeed(println(value)))
      .tapErrorCause(error => ZIO.succeed(println(s"<FAIL> $error")))

  /**
   * Taps the layer, printing the result of calling `.toString` on the value.
   * Prefixes the output with the given message, as `<prefix>: <value>`, or as
   * `<FAIL> <prefix>: <cause>` when the layer fails.
   */
  final def debug(prefix: => String)(implicit trace: Trace): ZLayer[RIn, E, ROut] =
    self
      .tap(value => ZIO.succeed(println(s"$prefix: $value")))
      .tapErrorCause(error => ZIO.succeed(println(s"<FAIL> $prefix: $error")))

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
  )(implicit trace: Trace): ZLayer[RIn1, E1, ROut2] =
    foldLayer(ZLayer.fail(_), f)

  /**
   * Returns a layer that performs the outer layer first, followed by the inner
   * layer, yielding the value of the inner layer.
   *
   * This method can be used to "flatten" nested layers.
   */
  final def flatten[RIn1 <: RIn, E1 >: E, ROut1 >: ROut, ROut2](implicit
    tag: Tag[ROut1],
    ev1: ROut1 <:< ZLayer[RIn1, E1, ROut2],
    trace: Trace
  ): ZLayer[RIn1, E1, ROut2] =
    flatMap(environment => ev1(environment.get[ROut1]))

  /**
   * Feeds the error or output services of this layer into the input of either
   * the specified `failure` or `success` layers, resulting in a new layer with
   * the inputs of this layer, and the error or outputs of the specified layer.
   * `failure` receives the failures of type `E` only: when this layer dies or
   * is interrupted, the resulting layer fails with the same cause.
   */
  final def foldLayer[E1, RIn1 <: RIn, ROut2](
    failure: E => ZLayer[RIn1, E1, ROut2],
    success: ZEnvironment[ROut] => ZLayer[RIn1, E1, ROut2]
  )(implicit ev: CanFail[E], trace: Trace): ZLayer[RIn1, E1, ROut2] =
    foldCauseLayer(_.failureOrCause.fold(failure, ZLayer.failCause(_)), success)

  /**
   * Builds this layer, then builds the layer returned by `failure` with the
   * full `Cause` of the failure, or the layer returned by `success` with the
   * output services. The result has the inputs of this layer and the error and
   * outputs of the chosen layer. Unlike `foldLayer`, `failure` is also called
   * when this layer dies, with a `Cause` that contains the defect. When this
   * layer is `fresh`, the result is fresh too.
   */
  final def foldCauseLayer[E1, RIn1 <: RIn, ROut2](
    failure: Cause[E] => ZLayer[RIn1, E1, ROut2],
    success: ZEnvironment[ROut] => ZLayer[RIn1, E1, ROut2]
  )(implicit ev: CanFail[E]): ZLayer[RIn1, E1, ROut2] = {
    val foldLayer = ZLayer.Fold(self, failure, success)
    if (isFresh) ZLayer.Fresh(foldLayer) else foldLayer
  }

  /**
   * Creates a fresh version of this layer that will not be shared.
   *
   * Each occurrence of the fresh layer in a build builds it again, instead of
   * reusing the services of the same layer built elsewhere in that build. The
   * layers inside the fresh layer are built again as well, even when the same
   * layers are also built elsewhere in that build; within the fresh layer they
   * are still shared.
   *
   * {{{
   * val buildCounts: UIO[(Int, Int)] =
   *   for {
   *     builds <- Ref.make(0)
   *     cache   = ZLayer(builds.updateAndGet(_ + 1))
   *     _      <- ZIO.scoped((cache ++ cache).build)
   *     shared <- builds.getAndSet(0)
   *     _      <- ZIO.scoped((cache ++ cache.fresh).build)
   *     fresh  <- builds.get
   *   } yield (shared, fresh)
   * // (1,2)
   * }}}
   */
  final def fresh: ZLayer[RIn, E, ROut] =
    ZLayer.Fresh(self)

  private[this] var _hashCode: Int = 0

  /**
   * Returns the hash code of this layer.
   */
  override final def hashCode: Int = {
    var hc = _hashCode
    if (hc == 0) {
      hc = super.hashCode
      _hashCode = hc
    }
    hc
  }

  /**
   * Builds this layer and uses it until it is interrupted. This is useful when
   * your entire application is a layer, such as an HTTP server. The resources
   * of the layer are released when the returned effect is interrupted.
   */
  final def launch(implicit trace: Trace): ZIO[RIn, E, Nothing] =
    ZIO.scopedWith(build(_) *> ZIO.never)

  /**
   * Returns a new layer whose output is mapped by the specified function.
   */
  final def map[ROut1](f: ZEnvironment[ROut] => ZEnvironment[ROut1])(implicit
    trace: Trace
  ): ZLayer[RIn, E, ROut1] =
    flatMap(environment => ZLayer.succeedEnvironment(f(environment)))

  /**
   * Returns a layer with its error channel mapped using the specified function.
   */
  final def mapError[E1](f: E => E1)(implicit ev: CanFail[E], trace: Trace): ZLayer[RIn, E1, ROut] =
    catchAll(e => ZLayer.fail(f(e)))

  /**
   * Returns a layer with its full cause of failure mapped using the specified
   * function. This can be used to transform errors while preserving the
   * original structure of `Cause`.
   *
   * @see
   *   [[catchAllCause]]
   */
  final def mapErrorCause[E2](h: Cause[E] => Cause[E2])(implicit trace: Trace): ZLayer[RIn, E2, ROut] =
    self.foldCauseLayer(c => ZLayer.failCause(h(c)), a => ZLayer.succeedEnvironment(a))

  /**
   * Returns a scoped effect that produces a layer which builds this layer the
   * first time it is used and returns the same services on every later use.
   * Nothing is built when the returned layer is never used. The resources of
   * this layer are released when the scope of the returned effect closes.
   */
  final def memoize(implicit trace: Trace): ZIO[Scope, Nothing, ZLayer[RIn, E, ROut]] =
    ZIO.scopeWith(build(_).memoize.map(ZLayer.fromZIOEnvironment(_)))

  /**
   * Translates effect failure into death of the fiber, making all failures
   * unchecked and not a part of the type of the layer.
   */
  final def orDie(implicit
    ev1: E IsSubtypeOfError Throwable,
    ev2: CanFail[E],
    trace: Trace
  ): ZLayer[RIn, Nothing, ROut] =
    catchAll(e => ZLayer.die(ev1(e)))

  /**
   * Executes this layer and returns its output, if it succeeds, but otherwise
   * executes the specified layer. Only failures of type `E` switch to `that`:
   * when this layer dies, the resulting layer dies too.
   */
  final def orElse[RIn1 <: RIn, E1, ROut1 >: ROut](
    that: => ZLayer[RIn1, E1, ROut1]
  )(implicit ev: CanFail[E], trace: Trace): ZLayer[RIn1, E1, ROut1] =
    catchAll(_ => that)

  /**
   * Retries constructing this layer according to the specified schedule. The
   * schedule receives each failure of type `E`, so with `Schedule.recurs(2)` a
   * layer that keeps failing is built three times before the resulting layer
   * fails with the last error. Defects are not retried. The first attempt
   * shares its layers with the rest of the build; each later attempt is
   * `fresh`, so it builds this layer and the layers inside it again.
   */
  final def retry[RIn1 <: RIn](
    schedule0: => Schedule[RIn1, E, Any]
  )(implicit trace: Trace): ZLayer[RIn1, E, ROut] =
    ZLayer.suspend {
      import Schedule.Decision._

      val schedule = schedule0

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
   * Performs the specified effect if this layer succeeds. When the effect
   * fails, the resulting layer fails with its error.
   */
  final def tap[RIn1 <: RIn, E1 >: E](f: ZEnvironment[ROut] => ZIO[RIn1, E1, Any])(implicit
    trace: Trace
  ): ZLayer[RIn1, E1, ROut] =
    flatMap(environment => ZLayer.fromZIOEnvironment(f(environment).as(environment)))

  /**
   * Performs the specified effect if this layer fails with an error of type
   * `E`, then fails with the same error. The effect does not run when this
   * layer dies.
   */
  final def tapError[RIn1 <: RIn, E1 >: E](f: E => ZIO[RIn1, E1, Any])(implicit
    trace: Trace
  ): ZLayer[RIn1, E1, ROut] =
    catchAll(e => ZLayer.fromZIO[RIn1, E1, Nothing](f(e) *> ZIO.fail(e)))

  /**
   * Performs the specified effect if this layer fails. The effect receives the
   * full `Cause`, so it also runs when this layer dies.
   */
  final def tapErrorCause[RIn1 <: RIn, E1 >: E](f: Cause[E] => ZIO[RIn1, E1, Any])(implicit
    trace: Trace
  ): ZLayer[RIn1, E1, ROut] =
    catchAllCause(e => ZLayer.fromZIO[RIn1, E1, Nothing](f(e) *> Exit.failCause(e)))

  /**
   * A named alias for `>>>`.
   */
  final def to[E1 >: E, ROut2](that: => ZLayer[ROut, E1, ROut2])(implicit
    trace: Trace
  ): ZLayer[RIn, E1, ROut2] =
    self >>> that

  /**
   * Converts a layer that requires no services into a scoped runtime, which can
   * be used to execute effects.
   */
  final def toRuntime(implicit ev: Any <:< RIn, trace: Trace): ZIO[Scope, E, Runtime[ROut]] =
    for {
      scope       <- ZIO.scope
      layer        = ZLayer.succeedEnvironment(ZEnvironment.empty.asInstanceOf[ZEnvironment[RIn]])
      environment <- (layer >>> self).build(scope)
      runtime     <- ZIO.runtime[ROut].provideEnvironment(environment)
    } yield runtime

  /**
   * Replaces the layer's output with `Unit`.
   *
   * When used with `ZIO#provide` and [[ZLayer.make]] macros (and their
   * variants), this will suppress the unused layer warning that is normally
   * emitted, and will actually include the layer for its side-effects.
   */
  def unit(implicit trace: Trace): ZLayer[RIn, E, Unit] =
    self.map(_ => ZEnvironment(()))

  /**
   * Updates one of the services output by this layer.
   */
  final def update[A >: ROut: Tag](
    f: A => A
  )(implicit trace: Trace): ZLayer[RIn, E, ROut] =
    map(_.update[A](f))

  /**
   * Combines this layer with the specified layer sequentially, producing a new
   * layer that has the inputs of both, and the outputs of both combined using
   * the specified function.
   */
  final def zipWith[E1 >: E, RIn2, ROut1 >: ROut, ROut2, ROut3](
    that: => ZLayer[RIn2, E1, ROut2]
  )(f: (ZEnvironment[ROut], ZEnvironment[ROut2]) => ZEnvironment[ROut3]): ZLayer[RIn with RIn2, E1, ROut3] =
    ZLayer.suspend(ZLayer.ZipWith(self, that, f))

  /**
   * Combines this layer with the specified layer in parallel, producing a new
   * layer that has the inputs of both, and the outputs of both combined using
   * the specified function.
   */
  final def zipWithPar[E1 >: E, RIn2, ROut1 >: ROut, ROut2, ROut3](
    that: => ZLayer[RIn2, E1, ROut2]
  )(f: (ZEnvironment[ROut], ZEnvironment[ROut2]) => ZEnvironment[ROut3]): ZLayer[RIn with RIn2, E1, ROut3] =
    ZLayer.suspend(ZLayer.ZipWithPar(self, that, f))

  /**
   * Returns whether this layer is a fresh version that will not be shared.
   */
  private final def isFresh: Boolean =
    self.isInstanceOf[ZLayer.Fresh[?, ?, ?]]

  private final def scope(
    scope: Scope,
    memoMap: ZLayer.MemoMap
  )(implicit trace: Trace): ZIO[RIn, E, ZEnvironment[ROut]] =
    self match {
      case ZLayer.Suspend(self) =>
        ZIO.suspendSucceed(memoMap.getOrElseMemoize(scope)(self()))
      case ZLayer.Apply(self) =>
        self
      case ZLayer.ExtendScope(self) =>
        ZIO
          .scopeWith(memoMap.getOrElseMemoize(_)(self))
          .asInstanceOf[ZIO[RIn, E, ZEnvironment[ROut]]]
      case ZLayer.Fold(self, failure, success) =>
        memoMap
          .getOrElseMemoize(scope)(self)
          .foldCauseZIO(
            e => memoMap.getOrElseMemoize(scope)(failure(e)),
            r => memoMap.getOrElseMemoize(scope)(success(r))
          )
      case ZLayer.Fresh(self) =>
        self.build(scope)
      case ZLayer.Scoped(self) =>
        scope.extend[RIn](self)
      case ZLayer.To(self, that) =>
        memoMap
          .getOrElseMemoize(scope)(self)
          .flatMap(r => memoMap.getOrElseMemoize(scope)(that).provideEnvironment(r))
      case ZLayer.ZipWith(self, that, f) =>
        memoMap.getOrElseMemoize(scope)(self).zipWith(memoMap.getOrElseMemoize(scope)(that))(f)
      case ZLayer.ZipWithPar(self, that, f) =>
        for {
          parallel <- scope.forkWith(ExecutionStrategy.Parallel)
          left     <- parallel.forkWith(scope.executionStrategy)
          right    <- parallel.forkWith(scope.executionStrategy)
          out      <- memoMap.getOrElseMemoize(left)(self).zipWithPar(memoMap.getOrElseMemoize(right)(that))(f)
        } yield out
    }
}

/**
 * Constructors and helpers for [[ZLayer]]: `succeed`, `fromZIO`, `scoped` and
 * `fromFunction` build a layer from a value, an effect, a scoped resource or a
 * function; `make` and `derive` assemble or derive layers at compile time.
 */
object ZLayer extends ZLayerCompanionVersionSpecific {

  private final case class Apply[-RIn, +E, +ROut](self: ZIO[RIn, E, ZEnvironment[ROut]]) extends ZLayer[RIn, E, ROut]

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

  private final case class ZipWith[-RIn, +E, ROut, ROut2, ROut3](
    self: ZLayer[RIn, E, ROut],
    that: ZLayer[RIn, E, ROut2],
    f: (ZEnvironment[ROut], ZEnvironment[ROut2]) => ZEnvironment[ROut3]
  ) extends ZLayer[RIn, E, ROut3]

  private final case class ZipWithPar[-RIn, +E, ROut, ROut2, ROut3](
    self: ZLayer[RIn, E, ROut],
    that: ZLayer[RIn, E, ROut2],
    f: (ZEnvironment[ROut], ZEnvironment[ROut2]) => ZEnvironment[ROut3]
  ) extends ZLayer[RIn, E, ROut3]

  /**
   * Constructs a layer from the specified effect, like `fromZIO`. The effect
   * runs each time the layer is built. For an effect that acquires a resource
   * and requires a `Scope`, use `ZLayer.scoped`: here the `Scope` stays in the
   * input of the layer.
   *
   * {{{
   * final case class Greeter(greeting: String)
   *
   * val greeterLayer: ZLayer[String, Nothing, Greeter] =
   *   ZLayer {
   *     for {
   *       greeting <- ZIO.service[String]
   *     } yield Greeter(greeting)
   *   }
   * }}}
   */
  def apply[RIn, E, ROut: Tag](zio: => ZIO[RIn, E, ROut])(implicit
    trace: Trace
  ): ZLayer[RIn, E, ROut] =
    ZLayer.fromZIO(zio)

  /**
   * A layer that succeeds with a unit value.
   */
  val unit: ULayer[Unit] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.succeed(())
  }

  /**
   * Types that customize the layers built by `ZLayer.derive`: `Scoped` and
   * `AcquireRelease` attach a resource to the lifetime of the derived layer,
   * and `Default` gives a constructor parameter a value instead of requiring it
   * as a service.
   *
   * @see
   *   [[https://zio.dev/reference/contextual/automatic-zlayer-derivation/ Automatic ZLayer Derivation]]
   */
  object Derive {

    /**
     * Defines a resourceful effect that will be attached to the lifetime of the
     * `ZLayer` derived by [[ZLayer.derive]].
     *
     * The 'resourceful' effect might be a background task, a lock file, or
     * etc., that can be managed by `Scope`.
     *
     * If [[scoped]] fails during resource acquisition, the entire `ZLayer`
     * initialization process fails.
     *
     * {{{
     * trait Connection {
     *   def healthCheck: ZIO[Any, Throwable, Unit]
     * }
     *
     * class ThirdPartyService(connection: Connection) extends ZLayer.Derive.Scoped[Any, Nothing] {
     *
     *   // Repeats health check every 10 seconds in background during the layer's lifetime
     *   override def scoped(implicit trace: Trace): ZIO[Scope, Nothing, Any] =
     *     connection.healthCheck
     *       .ignoreLogged
     *       .repeat(Schedule.spaced(10.seconds))
     *       .forkScoped
     * }
     *
     * object ThirdPartyService {
     *   // `ZLayer.Derive.Scoped` should be used with `ZLayer.derive`
     *   val layer: ZLayer[Connection, Nothing, ThirdPartyService] = ZLayer.derive[ThirdPartyService]
     * }
     * }}}
     *
     * @note
     *   This trait is specifically designed to work with [[ZLayer.derive]].
     *   Using it outside this context won't inherently attach any resourceful
     *   behaviors to the type.
     */
    trait Scoped[-R, +E] {

      /**
       * The effect that `ZLayer.derive` runs in the scope of the derived layer,
       * right after it constructs the service. Its result is discarded, and the
       * finalizers it adds run when the layer is released.
       */
      def scoped(implicit trace: Trace): ZIO[R & Scope, E, Any]
    }

    /**
     * A special form of [[Scoped]] for convenience.
     *
     * When using [[ZLayer.derive]] with a type that implements
     * `AcquireRelease`, the provided `acquire` and `release` methods will be
     * automatically utilized as the lifecycle hooks.
     *
     * @note
     *   This trait's lifecycle hooks are specifically designed to work with
     *   [[ZLayer.derive]]. Using it outside this context won't inherently
     *   attach any lifecycle behaviors to the type.
     */
    trait AcquireRelease[-R, +E, A] extends Scoped[R, E] {

      /**
       * Runs `acquire` with `release` as its finalizer, using
       * `ZIO.acquireRelease`.
       */
      final def scoped(implicit trace: Trace): ZIO[R & Scope, E, A] =
        ZIO.acquireRelease(acquire)(release)

      /**
       * Acquires the resource when the derived layer is built. When it fails,
       * building the layer fails and `release` does not run.
       */
      def acquire: ZIO[R, E, A]

      /**
       * Releases the resource returned by `acquire` when the derived layer is
       * released.
       */
      def release(a: A): ZIO[R, Nothing, Any]
    }

    /**
     * Provides a default way to construct or provide an instance of type `A`.
     *
     * Used during `ZLayer` derivation to resolve dependencies. If an implicit
     * `ZLayer.Derive.Default[A]` instance exists for a type, it signifies that
     * a default value can be used, bypassing the dependency in the `ZLayer`
     * environment.
     *
     * @note
     *   When type-annotating the implicit val, ensure it's in the form
     *   `Default.WithContext[R, E, A]` rather than just `Default[A]` to ensure
     *   correct type inference and dependency resolution during `ZLayer`
     *   derivation.
     */
    trait Default[+A] {

      /** The services that `layer` requires. */
      type R

      /** The error type of `layer`. */
      type E

      /** The layer that builds the default value. */
      def layer: ZLayer[R, E, A]
    }

    /**
     * Constructors for [[Default]] values, and the predefined defaults that
     * `ZLayer.derive` finds: an implicit `Config[A]` (loaded with
     * `ZIO.config`), a new `Promise`, a new unbounded `Queue` or `Hub`, and a
     * new `Ref[A]` when `A` has a default.
     */
    object Default extends DefaultInstances0 {

      /**
       * A [[Default]] whose `R` and `E` are known. Type an implicit default
       * with `WithContext`, not with `Default[A]`, so that `ZLayer.derive` can
       * see `R` and `E`.
       */
      type WithContext[R0, E0, A] = Default[A] {
        type R = R0
        type E = E0
      }

      /**
       * Summons the implicit default for the specified type.
       */
      def apply[A](implicit ev: Default[A]): Default.WithContext[ev.R, ev.E, A] = ev

      /**
       * Constructs a default layer using the provided value.
       */
      def succeed[A: Tag](a: => A)(implicit trace: Trace): Default.WithContext[Any, Nothing, A] =
        Default.fromZIO(Exit.succeed(a))

      /**
       * Constructs a default layer using the provided ZIO value.
       */
      def fromZIO[R, E, A: Tag](zio: => ZIO[R, E, A])(implicit trace: Trace): Default.WithContext[R, E, A] =
        fromLayer(ZLayer.fromZIO(zio))

      /**
       * Uses the provided layer as the default layer.
       */
      def fromLayer[R0, E0, A: Tag](
        zlayer: => ZLayer[R0, E0, A]
      )(implicit trace: Trace): Default.WithContext[R0, E0, A] =
        new Default[A] {
          type R = R0
          type E = E0
          def layer: ZLayer[R, E, A] = zlayer
        }

      /**
       * Makes a default value that requires the specified service from the
       * environment.
       *
       * Used to discard a predefined Default instance for [[ZLayer.derive]].
       *
       * @example
       *   {{{
       * class Wheels(number: Int)
       * object Wheels {
       *   implicit val defaultWheels: ZLayer.Derive.Default.WithContext[Any, Nothing, Wheels] =
       *     ZLayer.Derive.Default.succeed(new Wheels(4))
       * }
       * class Car(wheels: Wheels)
       *
       * val carLayer1: ULayer[Car] = ZLayer.derive[Car] // wheels.number == 4
       * val carLayer2: URLayer[Wheels, Car] = locally {
       *   // The default instance is discarded
       *   implicit val newWheels: ZLayer.Derive.Default.WithContext[Wheels, Nothing, Wheels] =
       *       ZLayer.Derive.Default.service[Wheels]
       *
       *   ZLayer.derive[Car]
       * }
       *   }}}
       */
      def service[A: Tag](implicit trace: Trace): Default.WithContext[A, Nothing, A] =
        fromLayer(ZLayer.service[A])

      /**
       * Adds `map` and `mapZIO` to a [[Default]] whose `R` and `E` are known.
       */
      implicit final class ZLayerDefaultInvariantOps[R, E, A](private val self: Default.WithContext[R, E, A])
          extends AnyVal {

        /**
         * Returns a new default layer mapped by the specified function.
         */
        def map[B: Tag](f: A => B)(implicit tag: Tag[A], trace: Trace): Default.WithContext[R, E, B] =
          fromLayer(self.layer.project(f))

        /**
         * Constructs a new default layer dynamically based on the output of the
         * current default layer.
         */
        def mapZIO[R1 <: R, E1 >: E, B: Tag](
          k: A => ZIO[R1, E1, B]
        )(implicit tag: Tag[A], trace: Trace): Default.WithContext[R1, E1, B] = {
          // used explicit type parameters to prevent a random compile error
          val layer0: ZLayer[R1, E1, A] = self.layer
          fromLayer[R1, E1, B](layer0.flatMap[R1, E1, B](a => ZLayer[R1, E1, B](k(a.get[A]))))
        }
      }

      /**
       * The default for a type `A` that has an implicit `Config[A]`: it loads
       * `A` with `ZIO.config`, so the derived layer fails with a `Config.Error`
       * when the configuration can't be loaded.
       */
      implicit def deriveDefaultConfig[A: Tag](implicit
        ev: Config[A],
        trace: Trace
      ): Default.WithContext[Any, Config.Error, A] =
        fromZIO(ZIO.config(ev))

      /**
       * The default for a `Promise[E, A]`: a new, uncompleted promise made with
       * `Promise.make`.
       */
      implicit def deriveDefaultPromise[E: Tag, A: Tag](implicit
        trace: Trace
      ): Default.WithContext[Any, Nothing, Promise[E, A]] =
        fromZIO(Promise.make[E, A])

      /**
       * The default for a `Queue[A]`: a new queue made with `Queue.unbounded`.
       */
      implicit def deriveDefaultQueue[A: Tag](implicit trace: Trace): Default.WithContext[Any, Nothing, Queue[A]] =
        fromZIO(Queue.unbounded[A])

      /**
       * The default for a `Hub[A]`: a new hub made with `Hub.unbounded`.
       */
      implicit def deriveDefaultHub[A: Tag](implicit trace: Trace): Default.WithContext[Any, Nothing, Hub[A]] =
        fromZIO(Hub.unbounded[A])

      /**
       * The default for a `Ref[A]` when `A` has a default: a new `Ref` that
       * holds the default value of `A`, with the requirements and error type of
       * that default.
       */
      implicit def deriveDefaultRef[A: Tag](implicit
        ev: Default[A],
        trace: Trace
      ): Default.WithContext[ev.R, ev.E, Ref[A]] =
        fromLayer(ev.layer.project(a => Unsafe.unsafe(implicit unsafe => Ref.unsafe.make(a))))
    }

    private[Derive] trait DefaultInstances0 { this: Default.type =>

      /**
       * The default for a `Promise[Nothing, A]`: a new, uncompleted promise
       * made with `Promise.make`.
       */
      implicit def defaultPromiseNothing[A: Tag](implicit
        trace: Trace
      ): Default.WithContext[Any, Nothing, Promise[Nothing, A]] =
        this.fromZIO(Promise.make[Nothing, A])
    }

  }

  /**
   * The service of the marker layers `ZLayer.Debug.tree` and
   * `ZLayer.Debug.mermaid`. Passing one of these layers to `ZLayer.make` or
   * `ZIO#provide` makes the macro print the graph of the layers it wires as a
   * compiler warning.
   */
  sealed trait Debug

  /**
   * The layers `tree` and `mermaid`, which make `ZLayer.make` and `ZIO#provide`
   * print the graph of the layers they wire as a compiler warning.
   */
  object Debug {
    private[zio] type Tree = Tree.type

    private[zio] case object Tree extends Debug

    private[zio] type Mermaid = Mermaid.type

    private[zio] case object Mermaid extends Debug

    /**
     * Including this layer in a call to a compile-time ZLayer constructor, such
     * as `ZIO#provide` or [[ZLayer.make]], will display a tree visualization of
     * the constructed layer graph.
     *
     * {{{
     * final class Fly
     * final class Spider(fly: Fly)
     * final class Bear(fly: Fly)
     * final class OldLady(spider: Spider, bear: Bear)
     *
     * object Fly { val live: ULayer[Fly] = ZLayer.succeed(new Fly) }
     * object Spider { val live: URLayer[Fly, Spider] = ZLayer.fromFunction((fly: Fly) => new Spider(fly)) }
     * object Bear { val live: URLayer[Fly, Bear] = ZLayer.fromFunction((fly: Fly) => new Bear(fly)) }
     * object OldLady {
     *   val live: URLayer[Spider & Bear, OldLady] =
     *     ZLayer.fromFunction((spider: Spider, bear: Bear) => new OldLady(spider, bear))
     * }
     *
     * val layer: ULayer[OldLady] =
     *   ZLayer.make[OldLady](
     *     OldLady.live,
     *     Spider.live,
     *     Fly.live,
     *     Bear.live,
     *     ZLayer.Debug.tree
     *   )
     *
     * // Including `ZLayer.Debug.tree` generates the following compiler warning:
     * //
     * //   ZLayer Wiring Graph
     * //
     * // ◉ OldLady.live
     * // ├─◑ Spider.live
     * // │ ╰─◑ Fly.live
     * // ╰─◑ Bear.live
     * //   ╰─◑ Fly.live
     * }}}
     */
    val tree: ULayer[Debug] = {
      implicit val tag: Tag[Debug] = Tag(EnvironmentTag.tagFromTagMacro)
      implicit val trace: Trace    = Tracer.newTrace
      ZLayer.succeed[Debug](Debug.Tree)
    }

    /**
     * Including this layer in a call to a compile-time ZLayer constructor, such
     * as `ZIO#provide` or [[ZLayer.make]], will display a tree visualization of
     * the constructed layer graph as well as a link to Mermaid chart.
     *
     * {{{
     * final class Fly
     * final class Spider(fly: Fly)
     * final class Bear(fly: Fly)
     * final class OldLady(spider: Spider, bear: Bear)
     *
     * object Fly { val live: ULayer[Fly] = ZLayer.succeed(new Fly) }
     * object Spider { val live: URLayer[Fly, Spider] = ZLayer.fromFunction((fly: Fly) => new Spider(fly)) }
     * object Bear { val live: URLayer[Fly, Bear] = ZLayer.fromFunction((fly: Fly) => new Bear(fly)) }
     * object OldLady {
     *   val live: URLayer[Spider & Bear, OldLady] =
     *     ZLayer.fromFunction((spider: Spider, bear: Bear) => new OldLady(spider, bear))
     * }
     *
     * val layer: ULayer[OldLady] =
     *   ZLayer.make[OldLady](
     *     OldLady.live,
     *     Spider.live,
     *     Fly.live,
     *     Bear.live,
     *     ZLayer.Debug.mermaid
     *   )
     *
     * // Including `ZLayer.Debug.mermaid` generates the following compiler warning:
     * //
     * //   ZLayer Wiring Graph
     * //
     * // ◉ OldLady.live
     * // ├─◑ Spider.live
     * // │ ╰─◑ Fly.live
     * // ╰─◑ Bear.live
     * //   ╰─◑ Fly.live
     * //
     * // Mermaid Live Editor Link
     * // https://mermaid-js.github.io/mermaid-live-editor/edit/#eyJjb2RlIjoiZ3JhcGggQlRcbkwwKFwiRmx5LmxpdmVcIilcbkwwIC0tPiBMMShcIlNwaWRlci5saXZlXCIpXG5MMCAtLT4gTDIoXCJCZWFyLmxpdmVcIilcbkwxIC0tPiBMMyhcIk9sZExhZHkubGl2ZVwiKVxuTDIgLS0+IEwzIiwibWVybWFpZCI6ICJ7XCJ0aGVtZVwiOiBcImRlZmF1bHRcIn0ifQ==
     * }}}
     */
    val mermaid: ULayer[Debug] = {
      implicit val tag: Tag[Debug] = Tag(EnvironmentTag.tagFromTagMacro)
      implicit val trace: Trace    = Tracer.newTrace
      ZLayer.succeed[Debug](Debug.Mermaid)
    }
  }

  /**
   * Gathers up the ZLayer inside of the given collection, and combines them
   * into a single ZLayer containing an equivalent collection of results. The
   * layers are built in parallel, and the results keep the order of `in`.
   */
  def collectAll[R, E, A: Tag, Collection[+Element] <: Iterable[Element]](
    in: => Collection[ZLayer[R, E, A]]
  )(implicit
    tag: Tag[Collection[A]],
    bf: BuildFrom[Collection[ZLayer[R, E, A]], A, Collection[A]],
    trace: Trace
  ): ZLayer[R, E, Collection[A]] =
    foreach(in)(i => i)

  /**
   * Prints the specified message to the console for debugging purposes.
   */
  def debug(value: => Any)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.fromZIO(ZIO.debug(value))

  /**
   * Constructs a layer that dies with the specified throwable.
   */
  final def die(t: => Throwable)(implicit trace: Trace): ZLayer[Any, Nothing, Nothing] =
    ZLayer.failCause(Cause.die(t))

  /**
   * A layer that does not produce any services.
   */
  val empty: ZLayer[Any, Nothing, Any] =
    ZLayer.fromZIOEnvironment(Exit.succeed(ZEnvironment.empty))(Trace.empty)

  /**
   * Constructs a layer that fails with the specified error.
   */
  def fail[E](e: => E)(implicit trace: Trace): Layer[E, Nothing] =
    failCause(Cause.fail(e))

  /**
   * Constructs a layer that fails with the specified cause.
   */
  def failCause[E](cause: => Cause[E])(implicit trace: Trace): Layer[E, Nothing] =
    ZLayer(ZIO.failCause(cause))

  /**
   * Applies the function `f` to each element of the `Collection[A]` and returns
   * the results in a new `Collection[B]`. The layers returned by `f` are built
   * in parallel, and the results keep the order of `in`.
   */
  def foreach[R, E, A, B: Tag, Collection[+Element] <: Iterable[Element]](
    in: => Collection[A]
  )(f: A => ZLayer[R, E, B])(implicit
    tag: Tag[Collection[B]],
    bf: BuildFrom[Collection[A], B, Collection[B]],
    trace: Trace
  ): ZLayer[R, E, Collection[B]] =
    ZLayer.suspend {
      val builder: mutable.Builder[B, Collection[B]] = bf.newBuilder(in)
      in
        .foldLeft[ZLayer[R, E, Builder[B, Collection[B]]]](ZLayer.succeed(builder))((io, a) =>
          io.zipWithPar(f(a))((left, right) => ZEnvironment(left.get += right.get))
        )
        .map(environment => ZEnvironment(environment.get.result()))
    }

  /**
   * Constructs a layer from the specified function. The layer requires one
   * service for each parameter of the function, from zero to 22 parameters, and
   * produces the result of the function as its service.
   *
   * {{{
   * final case class SmtpConfig(host: String, port: Int)
   * final case class Mailer(config: SmtpConfig, sender: String)
   *
   * val mailerLayer: ZLayer[SmtpConfig & String, Nothing, Mailer] =
   *   ZLayer.fromFunction((config: SmtpConfig, sender: String) => Mailer(config, sender))
   * }}}
   */
  def fromFunction[In](in: In)(implicit constructor: FunctionConstructor[In], trace: Trace): constructor.Out =
    constructor(in)

  /**
   * Constructs a layer from the specified effect. The effect runs each time the
   * layer is built, and its result becomes the service of the layer. For an
   * effect that acquires a resource and requires a `Scope`, use
   * `ZLayer.scoped`: here the `Scope` stays in the input of the layer.
   */
  def fromZIO[R, E, A: Tag](zio: => ZIO[R, E, A])(implicit
    trace: Trace
  ): ZLayer[R, E, A] =
    fromZIOEnvironment(zio.map(ZEnvironment(_)))

  /**
   * Constructs a layer from the specified effect, which must return one or more
   * services.
   */
  def fromZIOEnvironment[R, E, A](zio: => ZIO[R, E, ZEnvironment[A]])(implicit
    trace: Trace
  ): ZLayer[R, E, A] =
    ZLayer.suspend(ZLayer.Apply[R, E, A](zio))

  /**
   * Constructs a layer that passes along the specified environment as an
   * output.
   */
  def environment[A](implicit trace: Trace): ZLayer[A, Nothing, A] =
    ZLayer.fromZIOEnvironment(ZIO.environment[A])

  /**
   * Constructs a layer that refails with the specified cause.
   */
  def refailCause[E](cause: Cause[E])(implicit trace: Trace): Layer[E, Nothing] =
    ZLayer(Exit.failCause(cause))

  /**
   * Constructs a layer from the specified scoped effect. The effect runs in the
   * scope of the layer, so the resources it acquires are released when the
   * layer is released, for example when the effect it was provided to
   * completes. `R` lists the services the effect needs besides `Scope`.
   *
   * {{{
   * final class Connection(val host: String) {
   *   def close: UIO[Unit] = ZIO.debug("closing connection to " + host)
   * }
   *
   * val connectionLayer: ULayer[Connection] =
   *   ZLayer.scoped {
   *     ZIO.acquireRelease(ZIO.succeed(new Connection("db.internal:5432")))(_.close)
   *   }
   * }}}
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
  def service[A: Tag](implicit trace: Trace): ZLayer[A, Nothing, A] =
    ZLayer.fromZIO(ZIO.service[A])

  /**
   * Constructs a layer from the specified value. The by-name value is evaluated
   * each time the layer is built, so with `ZLayer.succeed(new Counter)`, two
   * `provide` calls with the same layer get two different counters.
   */
  def succeed[A: Tag](a: => A)(implicit trace: Trace): ULayer[A] =
    ZLayer.fromZIOEnvironment(Exit.succeed(ZEnvironment(a)))

  /**
   * Constructs a layer from the specified value, which must return one or more
   * services.
   */
  def succeedEnvironment[A](a: => ZEnvironment[A])(implicit trace: Trace): ULayer[A] =
    ZLayer.fromZIOEnvironment(Exit.succeed(a))

  /**
   * Lazily constructs a layer. This is useful to avoid infinite recursion when
   * creating layers that refer to themselves.
   */
  def suspend[RIn, E, ROut](layer: => ZLayer[RIn, E, ROut]): ZLayer[RIn, E, ROut] =
    Suspend(() => layer)

  /**
   * Adds `passthrough`, `project`, `reloadableAuto`, `reloadableAutoFromConfig`
   * and `reloadableManual` to `ZLayer`.
   */
  implicit final class ZLayerInvariantOps[RIn, E, ROut](private val self: ZLayer[RIn, E, ROut]) extends AnyVal {

    /**
     * Returns a new layer that produces the outputs of this layer but also
     * passes through the inputs.
     */
    def passthrough(implicit
      in: EnvironmentTag[RIn],
      out: EnvironmentTag[ROut],
      trace: Trace
    ): ZLayer[RIn, E, RIn with ROut] =
      ZLayer.environment[RIn] <*> self

    /**
     * Projects out part of one of the services output by this layer using the
     * specified function.
     */
    def project[ROut2: Tag](
      f: ROut => ROut2
    )(implicit tag: Tag[ROut], trace: Trace): ZLayer[RIn, E, ROut2] =
      self.map(environment => ZEnvironment(f(environment.get)))

    /**
     * Returns a layer that produces a reloadable version of this service. The
     * service is rebuilt according to `schedule`, as with `Reloadable.auto`.
     */
    def reloadableAuto(schedule: Schedule[RIn, Any, Any])(implicit
      tagOut: Tag[ROut],
      trace: Trace
    ): ZLayer[RIn, E, Reloadable[ROut]] =
      Reloadable.auto(self, schedule)

    /**
     * Returns a layer that produces a reloadable version of this service, where
     * the reloading schedule is derived from the layer input.
     */
    def reloadableAutoFromConfig[RIn2](scheduleFromConfig: ZEnvironment[RIn2] => Schedule[RIn with RIn2, Any, Any])(
      implicit
      tagOut: Tag[ROut],
      trace: Trace
    ): ZLayer[RIn with RIn2, E, Reloadable[ROut]] =
      Reloadable.autoFromConfig(self, scheduleFromConfig)

    /**
     * Returns a layer that produces a reloadable version of this service. The
     * service is rebuilt each time `Reloadable#reload` runs, as with
     * `Reloadable.manual`.
     */
    def reloadableManual(implicit
      tagOut: Tag[ROut],
      trace: Trace
    ): ZLayer[RIn, E, Reloadable[ROut]] =
      Reloadable.manual(self)
  }

  /**
   * A `FunctionConstructor[Input]` knows how to construct a `ZLayer` value from
   * a function of type `Input`. This allows the type of the `ZLayer` value
   * constructed to depend on `Input`.
   */
  trait FunctionConstructor[In] {

    /**
     * The type of the `ZLayer` value.
     */
    type Out

    /**
     * Constructs a `ZLayer` value from the specified input.
     */
    def apply(in: In)(implicit trace: Trace): Out
  }

  /**
   * The `FunctionConstructor` instances that `ZLayer.fromFunction` uses, for
   * functions of 0 to 22 parameters.
   */
  object FunctionConstructor {

    /** A [[FunctionConstructor]] whose `Out` type is `Out0`. */
    type WithOut[In, Out0] = FunctionConstructor[In] { type Out = Out0 }

    /**
     * Builds a layer from a `() => A`. The layer requires no services and calls
     * the function each time it is built.
     */
    implicit def function0Constructor[A: Tag]: FunctionConstructor.WithOut[() => A, ZLayer[Any, Nothing, A]] =
      new FunctionConstructor[() => A] {
        type Out = ZLayer[Any, Nothing, A]
        def apply(f: () => A)(implicit trace: Trace): ZLayer[Any, Nothing, A] =
          ZLayer.succeed(f())
      }

    /**
     * Builds a layer from a function of one parameter. The layer requires the
     * parameter type as a service and produces the result of the function.
     */
    implicit def function1Constructor[A: Tag, B: Tag]: FunctionConstructor.WithOut[A => B, ZLayer[A, Nothing, B]] =
      new FunctionConstructor[A => B] {
        type Out = ZLayer[A, Nothing, B]
        def apply(f: A => B)(implicit trace: Trace): ZLayer[A, Nothing, B] =
          ZLayer.fromZIOEnvironment {
            ZIO.serviceWith[A](a => ZEnvironment(f(a)))
          }
      }

    /**
     * Builds a layer from a function of 2 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function2Constructor[A: Tag, B: Tag, C: Tag]
      : FunctionConstructor.WithOut[(A, B) => C, ZLayer[A with B, Nothing, C]] =
      new FunctionConstructor[(A, B) => C] {
        type Out = ZLayer[A with B, Nothing, C]
        def apply(f: (A, B) => C)(implicit trace: Trace): ZLayer[A with B, Nothing, C] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[A with B] { env =>
              ZEnvironment(
                f(env.get[A], env.get[B])
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 3 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function3Constructor[A: Tag, B: Tag, C: Tag, D: Tag]
      : FunctionConstructor.WithOut[(A, B, C) => D, ZLayer[A with B with C, Nothing, D]] =
      new FunctionConstructor[(A, B, C) => D] {
        type Out = ZLayer[A with B with C, Nothing, D]
        def apply(f: (A, B, C) => D)(implicit trace: Trace): ZLayer[A with B with C, Nothing, D] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[A with B with C] { env =>
              ZEnvironment(
                f(env.get[A], env.get[B], env.get[C])
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 4 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function4Constructor[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag]
      : FunctionConstructor.WithOut[(A, B, C, D) => E, ZLayer[A with B with C with D, Nothing, E]] =
      new FunctionConstructor[(A, B, C, D) => E] {
        type Out = ZLayer[A with B with C with D, Nothing, E]
        def apply(f: (A, B, C, D) => E)(implicit trace: Trace): ZLayer[A with B with C with D, Nothing, E] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[A with B with C with D] { env =>
              ZEnvironment(
                f(env.get[A], env.get[B], env.get[C], env.get[D])
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 5 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function5Constructor[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag]
      : FunctionConstructor.WithOut[(A, B, C, D, E) => F, ZLayer[A with B with C with D with E, Nothing, F]] =
      new FunctionConstructor[(A, B, C, D, E) => F] {
        type Out = ZLayer[A with B with C with D with E, Nothing, F]
        def apply(
          f: (A, B, C, D, E) => F
        )(implicit trace: Trace): ZLayer[A with B with C with D with E, Nothing, F] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[A with B with C with D with E] { env =>
              ZEnvironment(
                f(env.get[A], env.get[B], env.get[C], env.get[D], env.get[E])
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 6 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function6Constructor[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag]
      : FunctionConstructor.WithOut[(A, B, C, D, E, F) => G, ZLayer[A with B with C with D with E with F, Nothing, G]] =
      new FunctionConstructor[(A, B, C, D, E, F) => G] {
        type Out = ZLayer[A with B with C with D with E with F, Nothing, G]
        def apply(
          f: (A, B, C, D, E, F) => G
        )(implicit trace: Trace): ZLayer[A with B with C with D with E with F, Nothing, G] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[A with B with C with D with E with F] { env =>
              ZEnvironment(
                f(env.get[A], env.get[B], env.get[C], env.get[D], env.get[E], env.get[F])
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 7 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function7Constructor[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag]
      : FunctionConstructor.WithOut[(A, B, C, D, E, F, G) => H, ZLayer[
        A with B with C with D with E with F with G,
        Nothing,
        H
      ]] =
      new FunctionConstructor[(A, B, C, D, E, F, G) => H] {
        type Out = ZLayer[A with B with C with D with E with F with G, Nothing, H]
        def apply(
          f: (A, B, C, D, E, F, G) => H
        )(implicit trace: Trace): ZLayer[A with B with C with D with E with F with G, Nothing, H] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[A with B with C with D with E with F with G] { env =>
              ZEnvironment(
                f(env.get[A], env.get[B], env.get[C], env.get[D], env.get[E], env.get[F], env.get[G])
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 8 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function8Constructor[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag, I: Tag]
      : FunctionConstructor.WithOut[(A, B, C, D, E, F, G, H) => I, ZLayer[
        A with B with C with D with E with F with G with H,
        Nothing,
        I
      ]] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H) => I] {
        type Out = ZLayer[A with B with C with D with E with F with G with H, Nothing, I]
        def apply(
          f: (A, B, C, D, E, F, G, H) => I
        )(implicit trace: Trace): ZLayer[A with B with C with D with E with F with G with H, Nothing, I] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[A with B with C with D with E with F with G with H] { env =>
              ZEnvironment(
                f(env.get[A], env.get[B], env.get[C], env.get[D], env.get[E], env.get[F], env.get[G], env.get[H])
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 9 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function9Constructor[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag, I: Tag, J: Tag]
      : FunctionConstructor.WithOut[(A, B, C, D, E, F, G, H, I) => J, ZLayer[
        A with B with C with D with E with F with G with H with I,
        Nothing,
        J
      ]] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I) => J] {
        type Out = ZLayer[A with B with C with D with E with F with G with H with I, Nothing, J]
        def apply(f: (A, B, C, D, E, F, G, H, I) => J)(implicit
          trace: Trace
        ): ZLayer[A with B with C with D with E with F with G with H with I, Nothing, J] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[A with B with C with D with E with F with G with H with I] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I]
                )
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 10 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function10Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag
    ]: FunctionConstructor.WithOut[(A, B, C, D, E, F, G, H, I, J) => K, ZLayer[
      A with B with C with D with E with F with G with H with I with J,
      Nothing,
      K
    ]] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J) => K] {
        type Out = ZLayer[A with B with C with D with E with F with G with H with I with J, Nothing, K]
        def apply(f: (A, B, C, D, E, F, G, H, I, J) => K)(implicit
          trace: Trace
        ): ZLayer[A with B with C with D with E with F with G with H with I with J, Nothing, K] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[A with B with C with D with E with F with G with H with I with J] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I],
                  env.get[J]
                )
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 11 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function11Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag,
      L: Tag
    ]: FunctionConstructor.WithOut[(A, B, C, D, E, F, G, H, I, J, K) => L, ZLayer[
      A with B with C with D with E with F with G with H with I with J with K,
      Nothing,
      L
    ]] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J, K) => L] {
        type Out = ZLayer[A with B with C with D with E with F with G with H with I with J with K, Nothing, L]
        def apply(f: (A, B, C, D, E, F, G, H, I, J, K) => L)(implicit
          trace: Trace
        ): ZLayer[A with B with C with D with E with F with G with H with I with J with K, Nothing, L] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[A with B with C with D with E with F with G with H with I with J with K] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I],
                  env.get[J],
                  env.get[K]
                )
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 12 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function12Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag,
      L: Tag,
      M: Tag
    ]: FunctionConstructor.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L) => M, ZLayer[
      A with B with C with D with E with F with G with H with I with J with K with L,
      Nothing,
      M
    ]] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J, K, L) => M] {
        type Out = ZLayer[A with B with C with D with E with F with G with H with I with J with K with L, Nothing, M]
        def apply(f: (A, B, C, D, E, F, G, H, I, J, K, L) => M)(implicit
          trace: Trace
        ): ZLayer[A with B with C with D with E with F with G with H with I with J with K with L, Nothing, M] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[A with B with C with D with E with F with G with H with I with J with K with L] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I],
                  env.get[J],
                  env.get[K],
                  env.get[L]
                )
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 13 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function13Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag,
      L: Tag,
      M: Tag,
      N: Tag
    ]: FunctionConstructor.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M) => N, ZLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M,
      Nothing,
      N
    ]] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M) => N] {
        type Out =
          ZLayer[A with B with C with D with E with F with G with H with I with J with K with L with M, Nothing, N]
        def apply(f: (A, B, C, D, E, F, G, H, I, J, K, L, M) => N)(implicit
          trace: Trace
        ): ZLayer[A with B with C with D with E with F with G with H with I with J with K with L with M, Nothing, N] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[A with B with C with D with E with F with G with H with I with J with K with L with M] {
              env =>
                ZEnvironment(
                  f(
                    env.get[A],
                    env.get[B],
                    env.get[C],
                    env.get[D],
                    env.get[E],
                    env.get[F],
                    env.get[G],
                    env.get[H],
                    env.get[I],
                    env.get[J],
                    env.get[K],
                    env.get[L],
                    env.get[M]
                  )
                )
            }
          }
      }

    /**
     * Builds a layer from a function of 14 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function14Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag,
      L: Tag,
      M: Tag,
      N: Tag,
      O: Tag
    ]: FunctionConstructor.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M, N) => O, ZLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N,
      Nothing,
      O
    ]] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N) => O] {
        type Out = ZLayer[
          A with B with C with D with E with F with G with H with I with J with K with L with M with N,
          Nothing,
          O
        ]
        def apply(f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => O)(implicit trace: Trace): ZLayer[
          A with B with C with D with E with F with G with H with I with J with K with L with M with N,
          Nothing,
          O
        ] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[
              A with B with C with D with E with F with G with H with I with J with K with L with M with N
            ] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I],
                  env.get[J],
                  env.get[K],
                  env.get[L],
                  env.get[M],
                  env.get[N]
                )
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 15 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function15Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag,
      L: Tag,
      M: Tag,
      N: Tag,
      O: Tag,
      P: Tag
    ]: FunctionConstructor.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => P, ZLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O,
      Nothing,
      P
    ]] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => P] {
        type Out = ZLayer[
          A with B with C with D with E with F with G with H with I with J with K with L with M with N with O,
          Nothing,
          P
        ]
        def apply(f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => P)(implicit trace: Trace): ZLayer[
          A with B with C with D with E with F with G with H with I with J with K with L with M with N with O,
          Nothing,
          P
        ] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[
              A with B with C with D with E with F with G with H with I with J with K with L with M with N with O
            ] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I],
                  env.get[J],
                  env.get[K],
                  env.get[L],
                  env.get[M],
                  env.get[N],
                  env.get[O]
                )
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 16 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function16Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag,
      L: Tag,
      M: Tag,
      N: Tag,
      O: Tag,
      P: Tag,
      Q: Tag
    ]: FunctionConstructor.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => Q, ZLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P,
      Nothing,
      Q
    ]] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => Q] {
        type Out = ZLayer[
          A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P,
          Nothing,
          Q
        ]
        def apply(f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => Q)(implicit trace: Trace): ZLayer[
          A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P,
          Nothing,
          Q
        ] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[
              A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P
            ] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I],
                  env.get[J],
                  env.get[K],
                  env.get[L],
                  env.get[M],
                  env.get[N],
                  env.get[O],
                  env.get[P]
                )
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 17 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function17Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag,
      L: Tag,
      M: Tag,
      N: Tag,
      O: Tag,
      P: Tag,
      Q: Tag,
      R: Tag
    ]: FunctionConstructor.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => R, ZLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P with Q,
      Nothing,
      R
    ]] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => R] {
        type Out = ZLayer[
          A with B
            with C
            with D
            with E
            with F
            with G
            with H
            with I
            with J
            with K
            with L
            with M
            with N
            with O
            with P
            with Q,
          Nothing,
          R
        ]
        def apply(f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => R)(implicit trace: Trace): ZLayer[
          A with B
            with C
            with D
            with E
            with F
            with G
            with H
            with I
            with J
            with K
            with L
            with M
            with N
            with O
            with P
            with Q,
          Nothing,
          R
        ] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[
              A with B
                with C
                with D
                with E
                with F
                with G
                with H
                with I
                with J
                with K
                with L
                with M
                with N
                with O
                with P
                with Q
            ] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I],
                  env.get[J],
                  env.get[K],
                  env.get[L],
                  env.get[M],
                  env.get[N],
                  env.get[O],
                  env.get[P],
                  env.get[Q]
                )
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 18 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function18Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag,
      L: Tag,
      M: Tag,
      N: Tag,
      O: Tag,
      P: Tag,
      Q: Tag,
      R: Tag,
      S: Tag
    ]: FunctionConstructor.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => S, ZLayer[
      A with B
        with C
        with D
        with E
        with F
        with G
        with H
        with I
        with J
        with K
        with L
        with M
        with N
        with O
        with P
        with Q
        with R,
      Nothing,
      S
    ]] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => S] {
        type Out = ZLayer[
          A with B
            with C
            with D
            with E
            with F
            with G
            with H
            with I
            with J
            with K
            with L
            with M
            with N
            with O
            with P
            with Q
            with R,
          Nothing,
          S
        ]
        def apply(
          f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => S
        )(implicit trace: Trace): ZLayer[
          A with B
            with C
            with D
            with E
            with F
            with G
            with H
            with I
            with J
            with K
            with L
            with M
            with N
            with O
            with P
            with Q
            with R,
          Nothing,
          S
        ] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[
              A with B
                with C
                with D
                with E
                with F
                with G
                with H
                with I
                with J
                with K
                with L
                with M
                with N
                with O
                with P
                with Q
                with R
            ] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I],
                  env.get[J],
                  env.get[K],
                  env.get[L],
                  env.get[M],
                  env.get[N],
                  env.get[O],
                  env.get[P],
                  env.get[Q],
                  env.get[R]
                )
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 19 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function19Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag,
      L: Tag,
      M: Tag,
      N: Tag,
      O: Tag,
      P: Tag,
      Q: Tag,
      R: Tag,
      S: Tag,
      T: Tag
    ]: FunctionConstructor.WithOut[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => T,
      ZLayer[
        A with B
          with C
          with D
          with E
          with F
          with G
          with H
          with I
          with J
          with K
          with L
          with M
          with N
          with O
          with P
          with Q
          with R
          with S,
        Nothing,
        T
      ]
    ] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => T] {
        type Out = ZLayer[
          A with B
            with C
            with D
            with E
            with F
            with G
            with H
            with I
            with J
            with K
            with L
            with M
            with N
            with O
            with P
            with Q
            with R
            with S,
          Nothing,
          T
        ]
        def apply(
          f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => T
        )(implicit trace: Trace): ZLayer[
          A with B
            with C
            with D
            with E
            with F
            with G
            with H
            with I
            with J
            with K
            with L
            with M
            with N
            with O
            with P
            with Q
            with R
            with S,
          Nothing,
          T
        ] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[
              A with B
                with C
                with D
                with E
                with F
                with G
                with H
                with I
                with J
                with K
                with L
                with M
                with N
                with O
                with P
                with Q
                with R
                with S
            ] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I],
                  env.get[J],
                  env.get[K],
                  env.get[L],
                  env.get[M],
                  env.get[N],
                  env.get[O],
                  env.get[P],
                  env.get[Q],
                  env.get[R],
                  env.get[S]
                )
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 20 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function20Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag,
      L: Tag,
      M: Tag,
      N: Tag,
      O: Tag,
      P: Tag,
      Q: Tag,
      R: Tag,
      S: Tag,
      T: Tag,
      U: Tag
    ]: FunctionConstructor.WithOut[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => U,
      ZLayer[
        A with B
          with C
          with D
          with E
          with F
          with G
          with H
          with I
          with J
          with K
          with L
          with M
          with N
          with O
          with P
          with Q
          with R
          with S
          with T,
        Nothing,
        U
      ]
    ] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => U] {
        type Out = ZLayer[
          A with B
            with C
            with D
            with E
            with F
            with G
            with H
            with I
            with J
            with K
            with L
            with M
            with N
            with O
            with P
            with Q
            with R
            with S
            with T,
          Nothing,
          U
        ]
        def apply(
          f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => U
        )(implicit trace: Trace): ZLayer[
          A with B
            with C
            with D
            with E
            with F
            with G
            with H
            with I
            with J
            with K
            with L
            with M
            with N
            with O
            with P
            with Q
            with R
            with S
            with T,
          Nothing,
          U
        ] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[
              A with B
                with C
                with D
                with E
                with F
                with G
                with H
                with I
                with J
                with K
                with L
                with M
                with N
                with O
                with P
                with Q
                with R
                with S
                with T
            ] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I],
                  env.get[J],
                  env.get[K],
                  env.get[L],
                  env.get[M],
                  env.get[N],
                  env.get[O],
                  env.get[P],
                  env.get[Q],
                  env.get[R],
                  env.get[S],
                  env.get[T]
                )
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 21 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function21Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag,
      L: Tag,
      M: Tag,
      N: Tag,
      O: Tag,
      P: Tag,
      Q: Tag,
      R: Tag,
      S: Tag,
      T: Tag,
      U: Tag,
      V: Tag
    ]: FunctionConstructor.WithOut[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => V,
      ZLayer[
        A with B
          with C
          with D
          with E
          with F
          with G
          with H
          with I
          with J
          with K
          with L
          with M
          with N
          with O
          with P
          with Q
          with R
          with S
          with T
          with U,
        Nothing,
        V
      ]
    ] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => V] {
        type Out = ZLayer[
          A with B
            with C
            with D
            with E
            with F
            with G
            with H
            with I
            with J
            with K
            with L
            with M
            with N
            with O
            with P
            with Q
            with R
            with S
            with T
            with U,
          Nothing,
          V
        ]
        def apply(
          f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => V
        )(implicit trace: Trace): ZLayer[
          A with B
            with C
            with D
            with E
            with F
            with G
            with H
            with I
            with J
            with K
            with L
            with M
            with N
            with O
            with P
            with Q
            with R
            with S
            with T
            with U,
          Nothing,
          V
        ] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[
              A with B
                with C
                with D
                with E
                with F
                with G
                with H
                with I
                with J
                with K
                with L
                with M
                with N
                with O
                with P
                with Q
                with R
                with S
                with T
                with U
            ] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I],
                  env.get[J],
                  env.get[K],
                  env.get[L],
                  env.get[M],
                  env.get[N],
                  env.get[O],
                  env.get[P],
                  env.get[Q],
                  env.get[R],
                  env.get[S],
                  env.get[T],
                  env.get[U]
                )
              )
            }
          }
      }

    /**
     * Builds a layer from a function of 22 parameters. The layer requires each
     * parameter type as a service and produces the result of the function.
     */
    implicit def function22Constructor[
      A: Tag,
      B: Tag,
      C: Tag,
      D: Tag,
      E: Tag,
      F: Tag,
      G: Tag,
      H: Tag,
      I: Tag,
      J: Tag,
      K: Tag,
      L: Tag,
      M: Tag,
      N: Tag,
      O: Tag,
      P: Tag,
      Q: Tag,
      R: Tag,
      S: Tag,
      T: Tag,
      U: Tag,
      V: Tag,
      W: Tag
    ]: FunctionConstructor.WithOut[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) => W,
      ZLayer[
        A with B
          with C
          with D
          with E
          with F
          with G
          with H
          with I
          with J
          with K
          with L
          with M
          with N
          with O
          with P
          with Q
          with R
          with S
          with T
          with U
          with V,
        Nothing,
        W
      ]
    ] =
      new FunctionConstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) => W] {
        type Out = ZLayer[
          A with B
            with C
            with D
            with E
            with F
            with G
            with H
            with I
            with J
            with K
            with L
            with M
            with N
            with O
            with P
            with Q
            with R
            with S
            with T
            with U
            with V,
          Nothing,
          W
        ]
        def apply(
          f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) => W
        )(implicit trace: Trace): ZLayer[
          A with B
            with C
            with D
            with E
            with F
            with G
            with H
            with I
            with J
            with K
            with L
            with M
            with N
            with O
            with P
            with Q
            with R
            with S
            with T
            with U
            with V,
          Nothing,
          W
        ] =
          ZLayer.fromZIOEnvironment {
            ZIO.environmentWith[
              A with B
                with C
                with D
                with E
                with F
                with G
                with H
                with I
                with J
                with K
                with L
                with M
                with N
                with O
                with P
                with Q
                with R
                with S
                with T
                with U
                with V
            ] { env =>
              ZEnvironment(
                f(
                  env.get[A],
                  env.get[B],
                  env.get[C],
                  env.get[D],
                  env.get[E],
                  env.get[F],
                  env.get[G],
                  env.get[H],
                  env.get[I],
                  env.get[J],
                  env.get[K],
                  env.get[L],
                  env.get[M],
                  env.get[N],
                  env.get[O],
                  env.get[P],
                  env.get[Q],
                  env.get[R],
                  env.get[S],
                  env.get[T],
                  env.get[U],
                  env.get[V]
                )
              )
            }
          }
      }
  }

  /**
   * A `MemoMap` memoizes layers.
   */
  private final class MemoMap(implicit trace: Trace, unsafe: Unsafe) { self =>
    private[this] val map = new ConcurrentHashMap[ZLayer[Nothing, Any, Any], (IO[Any, Any], Exit[Any, Any] => UIO[Any])]

    /**
     * Builds `layer` once per memo map. A fresh layer is built in a new inner
     * scope each time and is not stored. Otherwise, the first caller builds the
     * layer and stores it in the memo map; later callers wait for that build
     * and reuse its services and fiber ref changes. Each successful caller adds
     * a finalizer to its `Scope`, and the resources of the layer are released
     * when the last of those finalizers runs.
     */
    def getOrElseMemoize[E, A, B](scope: Scope)(layer: ZLayer[A, E, B]): ZIO[A, E, ZEnvironment[B]] =
      if (layer.isFresh) {
        ZIO.uninterruptibleMask { restore =>
          val innerScope = Scope.unsafe.make
          restore(layer.scope(innerScope, self)).exitWith {
            case s: Exit.Success[ZEnvironment[B]] =>
              scope.addFinalizerExit(innerScope.close(_)) *> s
            case e @ Exit.Failure(cause) =>
              innerScope.close(e) *> ZIO.failCause(cause)
          }
        }
      } else {
        ZIO.fiberIdWith { fiberId =>
          var resource = null.asInstanceOf[ZIO[A, E, ZEnvironment[B]]]
          val entry @ (acquire, release) =
            map.computeIfAbsent(
              layer,
              { case layer: ZLayer[A, E, B] =>
                val promise      = Promise.unsafe.make[E, (FiberRefs.Patch, ZEnvironment[B])](fiberId)
                val observers    = new AtomicInteger(0)
                val finalizerRef = new AtomicReference[Exit[Any, Any] => UIO[Any]](ZIO.unitZIOFn)

                resource = ZIO.uninterruptibleMask { restore =>
                  val innerScope = Scope.unsafe.make
                  restore(
                    layer
                      .scope(innerScope, self)
                      .diffFiberRefs
                  ).exitWith {
                    case e @ Exit.Success((_, b)) =>
                      finalizerRef.set { (e: Exit[Any, Any]) =>
                        ZIO.suspendSucceed {
                          if (observers.getAndDecrement() == 1) innerScope.close(e)
                          else Exit.unit
                        }
                      }
                      observers.incrementAndGet()
                      scope
                        .addFinalizerExit(e => ZIO.suspendSucceed(finalizerRef.get.apply(e)))
                        .as {
                          promise.unsafe.done(e)
                          b
                        }
                    case e @ Exit.Failure(cause) =>
                      promise.unsafe.done(e)
                      innerScope.close(e) *> ZIO.failCause(cause)
                  }
                }

                (
                  promise.await.exitWith {
                    case exit: Exit.Success[?] => observers.incrementAndGet(); exit
                    case exit                  => exit
                  },
                  (exit: Exit[Any, Any]) => ZIO.suspendSucceed(finalizerRef.get.apply(exit))
                )
              }
            )

          if (resource ne null) resource
          else {
            acquire
              .asInstanceOf[IO[E, (FiberRefs.Patch, ZEnvironment[B])]]
              .flatMap { case (patch, b) => ZIO.patchFiberRefs(patch) *> scope.addFinalizerExit(release).as(b) }
          }
        }
      }
  }

  /**
   * The result of `ZLayer.scoped[R]`, which fixes the services `R` that the
   * scoped effect needs besides `Scope`.
   */
  implicit final class ScopedPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Constructs a layer from the scoped effect `zio`, whose result becomes the
     * service of the layer.
     */
    def apply[E, A: Tag](zio: => ZIO[Scope with R, E, A])(implicit
      trace: Trace
    ): ZLayer[R, E, A] =
      scopedEnvironment[R](zio.map(ZEnvironment(_)))
  }

  /**
   * The result of `ZLayer.scopedEnvironment[R]`, which fixes the services `R`
   * that the scoped effect needs besides `Scope`.
   */
  implicit final class ScopedEnvironmentPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Constructs a layer from the scoped effect `zio`, whose `ZEnvironment`
     * result becomes the services of the layer.
     */
    def apply[E, A](zio: => ZIO[Scope with R, E, ZEnvironment[A]])(implicit
      trace: Trace
    ): ZLayer[R, E, A] =
      ZLayer.suspend(ZLayer.Scoped[R, E, A](zio))
  }

  /**
   * Adds `apply`, `>>>` and `>+>` to `ZLayer`.
   */
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
      trace: Trace
    ): ZIO[RIn with R, E1, A] =
      ZIO.provideLayer[RIn, E1, ROut, R, A](self)(zio)

    /**
     * Feeds the output services of this layer into the input of the specified
     * layer, resulting in a new layer with the inputs of this layer as well as
     * any leftover inputs, and the outputs of the specified layer.
     */
    def >>>[RIn2, E1 >: E, ROut2](
      that: => ZLayer[ROut with RIn2, E1, ROut2]
    )(implicit tag: EnvironmentTag[ROut], trace: Trace): ZLayer[RIn with RIn2, E1, ROut2] =
      ZLayer.suspend(
        ZLayer.To(
          ZLayer.ZipWith[RIn with RIn2, E, RIn2, ROut, ROut with RIn2](
            ZLayer.environment[RIn2],
            self,
            _.union[ROut](_)
          ),
          that
        )
      )

    /**
     * Feeds the output services of this layer into the input of the specified
     * layer, resulting in a new layer with the inputs of this layer, and the
     * outputs of the specified layer. This layer is built first, and its
     * resources are released after those of `that`.
     */
    def >>>[E1 >: E, ROut2](that: => ZLayer[ROut, E1, ROut2])(implicit
      trace: Trace
    ): ZLayer[RIn, E1, ROut2] =
      ZLayer.suspend(ZLayer.To(self, that))

    /**
     * Feeds the output services of this layer into the input of the specified
     * layer, resulting in a new layer with the inputs of this layer as well as
     * any leftover inputs `RIn2` of the specified layer, and the outputs of
     * both layers. Unless this layer is `fresh`, it is built once and its
     * services are shared by both sides.
     */
    def >+>[RIn2, E1 >: E, ROut2](
      that: => ZLayer[ROut with RIn2, E1, ROut2]
    )(implicit
      tagged: EnvironmentTag[ROut],
      tagged2: EnvironmentTag[ROut2],
      trace: Trace
    ): ZLayer[RIn with RIn2, E1, ROut with ROut2] =
      self ++ self.>>>[RIn2, E1, ROut2](that)

    /**
     * Feeds the output services of this layer into the input of the specified
     * layer, resulting in a new layer with the inputs of this layer, and the
     * outputs of both layers. Unless this layer is `fresh`, it is built once
     * and its services are shared by both sides.
     */
    def >+>[E1 >: E, RIn2 >: ROut, ROut1 >: ROut, ROut2](
      that: => ZLayer[RIn2, E1, ROut2]
    )(implicit
      tagged: EnvironmentTag[ROut2],
      trace: Trace
    ): ZLayer[RIn, E1, ROut1 with ROut2] =
      ZLayer.ZipWith[RIn, E1, ROut1, ROut2, ROut1 with ROut2](self, self >>> that, _.union[ROut2](_))
  }
}
