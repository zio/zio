package zio.test

import zio.{NeedsEnv, ZLayer}
import zio.internal.macros.LayerMacros

private[test] trait SpecVersionSpecific[-R, +E, +T] { self: Spec[R, E, T] =>

  /**
   * Automatically assembles a layer for the spec, translating it up a level.
   */
  def inject[E1 >: E](layer: ZLayer[_, E1, _]*): Spec[Any, E1, T] =
    macro LayerMacros.injectImpl[Spec, R, E1, T]

  /**
   * Automatically constructs the part of the environment that is not part of
   * the `TestEnvironment`, leaving an effect that only depends on the
   * `TestEnvironment`. This will also satisfy transitive `TestEnvironment`
   * requirements with `TestEnvironment.any`, allowing them to be provided
   * later.
   *
   * {{{
   * val spec: ZIO[UserRepo with Console, Nothing, Unit] = ???
   * val userRepoLayer: ZLayer[Database, Nothing, UserRepo = ???
   * val databaseLayer: ZLayer[Clock, Nothing, Database] = ???
   *
   * // The TestEnvironment you use later will provide Clock to
   * // `databaseLayer` and Console to `spec`
   * val spec2 : ZIO[TestEnvironment, Nothing, Unit] =
   *   spec.injectCustom(userRepoLayer, databaseLayer)
   * }}}
   */
  def injectCustom[E1 >: E](layer: ZLayer[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    macro LayerMacros.injectSomeImpl[Spec, TestEnvironment, R, E1, T]

  /**
   * Splits the environment into two parts, providing each test with one part
   * using the specified layer and leaving the remainder `R0`.
   *
   * {{{
   * val spec: ZSpec[Clock with Random, Nothing] = ???
   * val clockLayer: ZLayer[Any, Nothing, Clock] = ???
   *
   * val spec2: ZSpec[Random, Nothing] = spec.injectSome[Random](clockLayer)
   * }}}
   */
  def injectSome[R0]: InjectSomePartiallyApplied[R0, R, E, T] =
    new InjectSomePartiallyApplied[R0, R, E, T](self)

  /**
   * Automatically assembles a layer for the spec, sharing services between all
   * tests.
   */
  def injectShared[E1 >: E](layer: ZLayer[_, E1, _]*): Spec[Any, E1, T] =
    macro SpecLayerMacros.injectSharedImpl[R, E1, T]

  /**
   * Automatically constructs the part of the environment that is not part of
   * the `TestEnvironment`, leaving an effect that only depends on the
   * `TestEnvironment`, sharing services between all tests.
   *
   * This will also satisfy transitive `TestEnvironment` requirements with
   * `TestEnvironment.any`, allowing them to be provided later.
   *
   * {{{
   * val spec: ZIO[UserRepo with Console, Nothing, Unit] = ???
   * val userRepoLayer: ZLayer[Database, Nothing, UserRepo = ???
   * val databaseLayer: ZLayer[Clock, Nothing, Database] = ???
   *
   * // The TestEnvironment you use later will provide Clock to
   * // `databaseLayer` and Console to `spec`
   * val spec2 : ZIO[TestEnvironment, Nothing, Unit] =
   *   spec.injectCustomShared(userRepoLayer, databaseLayer)
   * }}}
   */
  def injectCustomShared[E1 >: E](layer: ZLayer[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    macro SpecLayerMacros.injectCustomSharedImpl[R, E1, T]

  /**
   * Splits the environment into two parts, providing all tests with a shared
   * version of one part using the specified layer and leaving the remainder
   * `R0`.
   *
   * {{{
   * val spec: ZSpec[Int with Random, Nothing] = ???
   * val intLayer: ZLayer[Any, Nothing, Int] = ???
   *
   * val spec2 = spec.injectSomeShared[Random](intLayer)
   * }}}
   */
  final def injectSomeShared[R0]: InjectSomeSharedPartiallyApplied[R0, R, E, T] =
    new InjectSomeSharedPartiallyApplied[R0, R, E, T](self)
}

private final class InjectSomePartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {

  def provideLayer[E1 >: E](
    layer: ZLayer[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): Spec[R0, E1, T] =
    self.provideLayer(layer)

  def apply[E1 >: E](layer: ZLayer[_, E1, _]*): Spec[R0, E1, T] =
    macro LayerMacros.injectSomeImpl[Spec, R0, R, E1, T]
}

private final class InjectSomeSharedPartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {

  def provideShared[E1 >: E](
    layer: ZLayer[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): Spec[R0, E1, T] =
    self.provideLayerShared(layer)

  @deprecated("use provideShared", "2.0.0")
  def provideLayerShared[E1 >: E](
    layer: ZLayer[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): Spec[R0, E1, T] =
    provideLayerShared(layer)

  def apply[E1 >: E](layer: ZLayer[_, E1, _]*): Spec[R0, E1, T] =
    macro SpecLayerMacros.injectSomeSharedImpl[R0, R, E1, T]
}
