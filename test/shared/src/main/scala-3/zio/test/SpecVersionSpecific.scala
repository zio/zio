package zio.test

import zio.{ZIO, ZProvider}

trait SpecVersionSpecific[-R, +E, +T] { self: Spec[R, E, T] =>

  /**
   * Automatically assembles a provider for the spec, translating it
   * up a level.
   */
  inline def inject[E1 >: E](inline provider: ZProvider[_, E1, _]*): Spec[Any, E1, T] =
    ${SpecProviderMacros.injectImpl[Any, R, E1, T]('self, 'provider)}

  def injectSome[R0 ] =
    new InjectSomePartiallyApplied[R0, R, E, T](self)

  def injectSomeShared[R0 ] =
    new InjectSomeSharedPartiallyApplied[R0, R, E, T](self)

  /**
   * Automatically constructs the part of the environment that is not part of the
   * `TestEnvironment`, leaving an effect that only depends on the `TestEnvironment`.
   * This will also satisfy transitive `TestEnvironment` requirements with
   * `TestEnvironment.any`, allowing them to be provided later.
   *
   * {{{
   * val zio: ZIO[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyProvider: ZProvider[Fly, Nothing, OldLady] = ???
   * val flyProvider: ZProvider[Blocking, Nothing, Fly] = ???
   *
   * // The TestEnvironment you use later will provide both Blocking to flyProvider and
   * // Console to zio
   * val zio2 : ZIO[TestEnvironment, Nothing, Unit] =
   *   zio.injectCustom(oldLadyProvider, flyProvider)
   * }}}
   */
  inline def injectCustom[E1 >: E](inline provider: ZProvider[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    ${SpecProviderMacros.injectImpl[TestEnvironment, R, E1, T]('self, 'provider)}

  /**
   * Automatically assembles a provider for the spec, sharing
   * services between all tests.
   */
  inline def injectShared[E1 >: E](inline provider: ZProvider[_, E1, _]*): Spec[Any, E1, T] =
    ${SpecProviderMacros.injectSharedImpl[Any, R, E1, T]('self, 'provider)}

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
   * val oldLadyProvider: ZProvider[Fly, Nothing, OldLady] = ???
   * val flyProvider: ZProvider[Blocking, Nothing, Fly] = ???
   *
   * // The TestEnvironment you use later will provide both Blocking to flyProvider and
   * // Console to zio
   * val zio2 : ZIO[TestEnvironment, Nothing, Unit] =
   *   zio.injectCustom(oldLadyProvider, flyProvider)
   * }}}
   */
  inline def injectCustomShared[E1 >: E](inline provider: ZProvider[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    ${SpecProviderMacros.injectSharedImpl[TestEnvironment, R, E1, T]('self, 'provider)}
}

private final class InjectSomePartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {
  inline def apply[E1 >: E](inline provider: ZProvider[_, E1, _]*): Spec[R0, E1, T] =
  ${SpecProviderMacros.injectImpl[R0, R, E1, T]('self, 'provider)}
}

private final class InjectSomeSharedPartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {
  inline def apply[E1 >: E](inline provider: ZProvider[_, E1, _]*): Spec[R0, E1, T] =
  ${SpecProviderMacros.injectSharedImpl[R0, R, E1, T]('self, 'provider)}
}
