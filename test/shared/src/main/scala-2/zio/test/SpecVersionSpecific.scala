package zio.test

import zio.{NeedsEnv, ZServiceBuilder}
import zio.internal.macros.ServiceBuilderMacros

private[test] trait SpecVersionSpecific[-R, +E, +T] { self: Spec[R, E, T] =>

  /**
   * Automatically assembles a service builder for the spec, translating it up a
   * level.
   */
  def inject[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[Any, E1, T] =
    macro ServiceBuilderMacros.injectImpl[Spec, R, E1, T]

  /**
   * Automatically constructs the part of the environment that is not part of
   * the `TestEnvironment`, leaving an effect that only depends on the
   * `TestEnvironment`. This will also satisfy transitive `TestEnvironment`
   * requirements with `TestEnvironment.any`, allowing them to be provided
   * later.
   *
   * {{{
   * val spec: ZIO[UserRepo with Console, Nothing, Unit] = ???
   * val userRepoServiceBuilder: ZServiceBuilder[Database, Nothing, UserRepo = ???
   * val databaseServiceBuilder: ZServiceBuilder[Clock, Nothing, Database] = ???
   *
   * // The TestEnvironment you use later will provide Clock to
   * // `databaseServiceBuilder` and Console to `spec`
   * val spec2 : ZIO[TestEnvironment, Nothing, Unit] =
   *   spec.injectCustom(userRepoServiceBuilder, databaseServiceBuilder)
   * }}}
   */
  def injectCustom[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    macro ServiceBuilderMacros.injectSomeImpl[Spec, TestEnvironment, R, E1, T]

  /**
   * Splits the environment into two parts, providing each test with one part
   * using the specified service builder and leaving the remainder `R0`.
   *
   * {{{
   * val spec: ZSpec[Clock with Random, Nothing] = ???
   * val clockServiceBuilder: ZServiceBuilder[Any, Nothing, Clock] = ???
   *
   * val spec2: ZSpec[Random, Nothing] = spec.injectSome[Random](clockServiceBuilder)
   * }}}
   */
  def injectSome[R0]: InjectSomePartiallyApplied[R0, R, E, T] =
    new InjectSomePartiallyApplied[R0, R, E, T](self)

  /**
   * Automatically assembles a service builder for the spec, sharing services
   * between all tests.
   */
  def injectShared[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[Any, E1, T] =
    macro SpecServiceBuilderMacros.injectSharedImpl[R, E1, T]

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
   * val userRepoServiceBuilder: ZServiceBuilder[Database, Nothing, UserRepo = ???
   * val databaseServiceBuilder: ZServiceBuilder[Clock, Nothing, Database] = ???
   *
   * // The TestEnvironment you use later will provide Clock to
   * // `databaseServiceBuilder` and Console to `spec`
   * val spec2 : ZIO[TestEnvironment, Nothing, Unit] =
   *   spec.injectCustomShared(userRepoServiceBuilder, databaseServiceBuilder)
   * }}}
   */
  def injectCustomShared[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    macro SpecServiceBuilderMacros.injectCustomSharedImpl[R, E1, T]

  /**
   * Splits the environment into two parts, providing all tests with a shared
   * version of one part using the specified service builder and leaving the
   * remainder `R0`.
   *
   * {{{
   * val spec: ZSpec[Int with Random, Nothing] = ???
   * val intServiceBuilder: ZServiceBuilder[Any, Nothing, Int] = ???
   *
   * val spec2 = spec.injectSomeShared[Random](intServiceBuilder)
   * }}}
   */
  final def injectSomeShared[R0]: InjectSomeSharedPartiallyApplied[R0, R, E, T] =
    new InjectSomeSharedPartiallyApplied[R0, R, E, T](self)
}

private final class InjectSomePartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {

  def provide[E1 >: E, R1](
    serviceBuilder: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): Spec[R0, E1, T] =
    self.provide(serviceBuilder)

  @deprecated("use provide", "2.0.0")
  def provideLayer[E1 >: E, R1](
    layer: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): Spec[R0, E1, T] =
    provide(layer)

  def apply[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[R0, E1, T] =
    macro ServiceBuilderMacros.injectSomeImpl[Spec, R0, R, E1, T]
}

private final class InjectSomeSharedPartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {

  def provideShared[E1 >: E, R1](
    serviceBuilder: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): Spec[R0, E1, T] =
    self.provideShared(serviceBuilder)

  @deprecated("use provideShared", "2.0.0")
  def provideLayerShared[E1 >: E, R1](
    layer: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): Spec[R0, E1, T] =
    provideShared(layer)

  def apply[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[R0, E1, T] =
    macro SpecServiceBuilderMacros.injectSomeSharedImpl[R0, R, E1, T]
}
