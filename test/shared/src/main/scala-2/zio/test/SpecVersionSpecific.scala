package zio.test

import zio.{NeedsEnv, SubtypeOps, Tag, ZLayer, ZTraceElement}
import zio.internal.macros.LayerMacros
import zio.test.Spec.{ExecCase, LabeledCase, ManagedCase, MultipleCase, TestCase}

private[test] trait SpecVersionSpecific[-R, +E, +T] { self: Spec[R, E, T] =>

  /**
   * Automatically assembles a layer for the spec, translating it up a level.
   */
  def provide[E1 >: E](layers: ZLayer[_, E1, _]*): Spec[Any, E1, T] =
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
  def provideCustom[E1 >: E](layers: ZLayer[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    macro LayerMacros.provideSomeImpl[Spec, TestEnvironment, R, E1, T]

  /**
   * Automatically assembles a layer for the spec, sharing services between all
   * tests.
   */
  def provideShared[E1 >: E](layers: ZLayer[_, E1, _]*): Spec[Any, E1, T] =
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
  def provideCustomShared[E1 >: E](layers: ZLayer[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    macro SpecLayerMacros.provideCustomSharedImpl[R, E1, T]
}

final class ProvideSomeSpecPartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {

  def provide[E1 >: E](
    layer: ZLayer[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): Spec[R0, E1, T] =
    self.provide(layer)

  def apply[E1 >: E](layers: ZLayer[_, E1, _]*): Spec[R0, E1, T] =
    macro LayerMacros.provideSomeImpl[Spec, R0, R, E1, T]
}

final class ProvideSomeSharedSpecPartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {

  def provideShared[E1 >: E](
    layer: ZLayer[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): Spec[R0, E1, T] =
    self.provideShared(layer)

  def provideSomeShared[E1 >: E, R1](
    layer: ZLayer[R0, E1, R1]
  )(implicit ev: R0 with R1 <:< R, tagged: Tag[R0], tagged1: Tag[R1], trace: ZTraceElement): Spec[R0, E1, T] =
    self.caseValue match {
      case ExecCase(exec, spec)     => Spec.exec(exec, spec.provideSomeShared.provideSomeShared(layer))
      case LabeledCase(label, spec) => Spec.labeled(label, spec.provideSomeShared.provideSomeShared(layer))
      case ManagedCase(managed) =>
        Spec.managed(
          layer.build.flatMap { r =>
            ev.liftEnv(managed)
              .map(spec => ev.liftEnvSpec(spec).provide(r.toLayer ++ ZLayer.environment[R0]))
              .provide[E1, R0](r.toLayer ++ ZLayer.environment[R0])
          }
        )
      case MultipleCase(specs) =>
        Spec.managed(
          layer.build.map(r =>
            Spec.multiple(
              specs.map(spec => ev.liftEnvSpec(spec).provide(r.toLayer ++ ZLayer.environment[R0]))
            )
          )
        )
      case TestCase(test, annotations) =>
        Spec.test(ev.liftEnv(test).provide(layer ++ ZLayer.environment[R0]), annotations)
    }

  def apply[E1 >: E](layers: ZLayer[_, E1, _]*): Spec[R0, E1, T] =
    macro SpecLayerMacros.provideSomeSharedImpl[R0, R, E1, T]
}
