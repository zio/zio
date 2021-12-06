package zio.test

import zio.{NeedsEnv, ZLayer}
import zio.internal.macros.LayerMacros

private[test] trait SpecVersionSpecific[-R, +E, +T] { self: Spec[R, E, T] =>

  /**
   * Automatically assembles a layer for the spec, translating it up a level.
   */
  def provide[E1 >: E](layer: ZLayer[_, E1, _]*): Spec[Any, E1, T] =
    macro LayerMacros.provideImpl[Spec, R, E1, T]

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
   *   spec.provideCustom(userRepoLayer, databaseLayer)
   * }}}
   */
  def provideCustom[E1 >: E](layer: ZLayer[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    macro LayerMacros.provideSomeImpl[Spec, TestEnvironment, R, E1, T]

  /**
   * Splits the environment into two parts, providing each test with one part
   * using the specified layer and leaving the remainder `R0`.
   *
   * {{{
   * val spec: ZSpec[Clock with Random, Nothing] = ???
   * val clockLayer: ZLayer[Any, Nothing, Clock] = ???
   *
   * val spec2: ZSpec[Random, Nothing] = spec.provideSome[Random](clockLayer)
   * }}}
   */
  def provideSome[R0]: provideSomePartiallyApplied[R0, R, E, T] =
    new provideSomePartiallyApplied[R0, R, E, T](self)

  /**
   * Automatically assembles a layer for the spec, sharing services between all
   * tests.
   */
  def provideShared[E1 >: E](layer: ZLayer[_, E1, _]*): Spec[Any, E1, T] =
    macro SpecLayerMacros.provideSharedImpl[R, E1, T]

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
   *   spec.provideCustomShared(userRepoLayer, databaseLayer)
   * }}}
   */
  def provideCustomShared[E1 >: E](layer: ZLayer[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    macro SpecLayerMacros.provideCustomSharedImpl[R, E1, T]

  /**
   * Splits the environment into two parts, providing all tests with a shared
   * version of one part using the specified layer and leaving the remainder
   * `R0`.
   *
   * {{{
   * val spec: ZSpec[Int with Random, Nothing] = ???
   * val intLayer: ZLayer[Any, Nothing, Int] = ???
   *
   * val spec2 = spec.provideSomeShared[Random](intLayer)
   * }}}
   */
  final def provideSomeShared[R0]: provideSomeSharedPartiallyApplied[R0, R, E, T] =
    new provideSomeSharedPartiallyApplied[R0, R, E, T](self)
}

private final class provideSomePartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {

  def provideLayer[E1 >: E](
    layer: ZLayer[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): Spec[R0, E1, T] =
    self.provideLayer(layer)

  def apply[E1 >: E](layer: ZLayer[_, E1, _]*): Spec[R0, E1, T] =
    macro LayerMacros.provideSomeImpl[Spec, R0, R, E1, T]
}

private final class provideSomeSharedPartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {

  def provideLayerShared[E1 >: E](
    layer: ZLayer[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): Spec[R0, E1, T] =
    self.provideLayerShared(layer)

  def apply[E1 >: E](layer: ZLayer[_, E1, _]*): Spec[R0, E1, T] =
    macro SpecLayerMacros.provideSomeSharedImpl[R0, R, E1, T]
}
