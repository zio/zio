package zio.test

import zio.{ZIO, ZLayer}

trait SpecVersionSpecific[-R, +E, +T] { self: Spec[R, E, T] =>

  /**
   * Automatically assembles a layer for the spec, translating it
   * up a level.
   */
  inline def provide[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[Any, E1, T] =
    ${SpecLayerMacros.provideImpl[Any, R, E1, T]('self, 'layer)}

  def provideSome[R0 ] =
    new provideSomePartiallyApplied[R0, R, E, T](self)

  def provideSomeShared[R0 ] =
    new provideSomeSharedPartiallyApplied[R0, R, E, T](self)

  /**
   * Automatically constructs the part of the environment that is not part of the
   * `TestEnvironment`, leaving an effect that only depends on the `TestEnvironment`.
   * This will also satisfy transitive `TestEnvironment` requirements with
   * `TestEnvironment.any`, allowing them to be provided later.
   *
   * {{{
   * val zio: ZIO[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The TestEnvironment you use later will provide both Blocking to flyLayer and
   * // Console to zio
   * val zio2 : ZIO[TestEnvironment, Nothing, Unit] =
   *   zio.provideCustom(oldLadyLayer, flyLayer)
   * }}}
   */
  inline def provideCustom[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    ${SpecLayerMacros.provideImpl[TestEnvironment, R, E1, T]('self, 'layer)}

  /**
   * Automatically assembles a layer for the spec, sharing
   * services between all tests.
   */
  inline def provideShared[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[Any, E1, T] =
    ${SpecLayerMacros.provideSharedImpl[Any, R, E1, T]('self, 'layer)}

  /**
   * Automatically constructs the part of the environment that is not part of the
   * `TestEnvironment`, leaving an effect that only depends on the `TestEnvironment`,
   * sharing services between all tests.
   *
   * This will also satisfy transitive `TestEnvironment` requirements with
   * `TestEnvironment.any`, allowing them to be provided later.
   *
   * {{{
   * val zio: ZIO[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The TestEnvironment you use later will provide both Blocking to flyLayer and
   * // Console to zio
   * val zio2 : ZIO[TestEnvironment, Nothing, Unit] =
   *   zio.provideCustom(oldLadyLayer, flyLayer)
   * }}}
   */
  inline def provideCustomShared[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    ${SpecLayerMacros.provideSharedImpl[TestEnvironment, R, E1, T]('self, 'layer)}
}

private final class provideSomePartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {
  inline def apply[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[R0, E1, T] =
  ${SpecLayerMacros.provideImpl[R0, R, E1, T]('self, 'layer)}
}

private final class provideSomeSharedPartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {
  inline def apply[E1 >: E](inline layer: ZLayer[_, E1, _]*): Spec[R0, E1, T] =
  ${SpecLayerMacros.provideSharedImpl[R0, R, E1, T]('self, 'layer)}
}
