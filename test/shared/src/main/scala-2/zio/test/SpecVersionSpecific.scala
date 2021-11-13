package zio.test

import zio.{Has, NeedsEnv, ZServiceBuilder}
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
   * val spec: ZIO[Has[UserRepo] with Has[Console], Nothing, Unit] = ???
   * val userRepoServiceBuilder: ZServiceBuilder[Has[Database], Nothing, Has[UserRepo] = ???
   * val databaseServiceBuilder: ZServiceBuilder[Has[Clock], Nothing, Has[Database]] = ???
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
   * val spec: ZSpec[Has[Clock] with Has[Random], Nothing] = ???
   * val clockServiceBuilder: ZServiceBuilder[Any, Nothing, Has[Clock]] = ???
   *
   * val spec2: ZSpec[Has[Random], Nothing] = spec.injectSome[Has[Random]](clockServiceBuilder)
   * }}}
   */
  def injectSome[R0 <: Has[_]]: InjectSomePartiallyApplied[R0, R, E, T] =
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
   * val spec: ZIO[Has[UserRepo] with Has[Console], Nothing, Unit] = ???
   * val userRepoServiceBuilder: ZServiceBuilder[Has[Database], Nothing, Has[UserRepo] = ???
   * val databaseServiceBuilder: ZServiceBuilder[Has[Clock], Nothing, Has[Database]] = ???
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
   * val spec: ZSpec[Has[Int] with Has[Random], Nothing] = ???
   * val intServiceBuilder: ZServiceBuilder[Any, Nothing, Has[Int]] = ???
   *
   * val spec2 = spec.injectSomeShared[Has[Random]](intServiceBuilder)
   * }}}
   */
  final def injectSomeShared[R0 <: Has[_]]: InjectSomeSharedPartiallyApplied[R0, R, E, T] =
    new InjectSomeSharedPartiallyApplied[R0, R, E, T](self)
}

private final class InjectSomePartiallyApplied[R0 <: Has[_], -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {

  def provideService[E1 >: E, R1](
    serviceBuilder: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): Spec[R0, E1, T] =
    self.provideService(serviceBuilder)

  @deprecated("use provideService", "2.0.0")
  def provideLayer[E1 >: E, R1](
    layer: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): Spec[R0, E1, T] =
    provideService(layer)

  def apply[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[R0, E1, T] =
    macro ServiceBuilderMacros.injectSomeImpl[Spec, R0, R, E1, T]
}

private final class InjectSomeSharedPartiallyApplied[R0 <: Has[_], -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {

  def provideServiceShared[E1 >: E, R1](
    serviceBuilder: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): Spec[R0, E1, T] =
    self.provideServiceShared(serviceBuilder)

  @deprecated("use provideServiceShared", "2.0.0")
  def provideLayerShared[E1 >: E, R1](
    layer: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): Spec[R0, E1, T] =
    provideServiceShared(layer)

  def apply[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[R0, E1, T] =
    macro SpecServiceBuilderMacros.injectSomeSharedImpl[R0, R, E1, T]
}
