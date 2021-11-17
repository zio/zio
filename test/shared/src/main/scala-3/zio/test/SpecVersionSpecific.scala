package zio.test

import zio.{ZIO, ZServiceBuilder}

trait SpecVersionSpecific[-R, +E, +T] { self: Spec[R, E, T] =>

  /**
   * Automatically assembles a service builder for the spec, translating it
   * up a level.
   */
  inline def inject[E1 >: E](inline serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[Any, E1, T] =
    ${SpecServiceBuilderMacros.injectImpl[Any, R, E1, T]('self, 'serviceBuilder)}

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
   * val oldLadyServiceBuilder: ZServiceBuilder[Fly, Nothing, OldLady] = ???
   * val flyServiceBuilder: ZServiceBuilder[Blocking, Nothing, Fly] = ???
   *
   * // The TestEnvironment you use later will provide both Blocking to flyServiceBuilder and
   * // Console to zio
   * val zio2 : ZIO[TestEnvironment, Nothing, Unit] =
   *   zio.injectCustom(oldLadyServiceBuilder, flyServiceBuilder)
   * }}}
   */
  inline def injectCustom[E1 >: E](inline serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    ${SpecServiceBuilderMacros.injectImpl[TestEnvironment, R, E1, T]('self, 'serviceBuilder)}

  /**
   * Automatically assembles a service builder for the spec, sharing
   * services between all tests.
   */
  inline def injectShared[E1 >: E](inline serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[Any, E1, T] =
    ${SpecServiceBuilderMacros.injectSharedImpl[Any, R, E1, T]('self, 'serviceBuilder)}

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
   * val oldLadyServiceBuilder: ZServiceBuilder[Fly, Nothing, OldLady] = ???
   * val flyServiceBuilder: ZServiceBuilder[Blocking, Nothing, Fly] = ???
   *
   * // The TestEnvironment you use later will provide both Blocking to flyServiceBuilder and
   * // Console to zio
   * val zio2 : ZIO[TestEnvironment, Nothing, Unit] =
   *   zio.injectCustom(oldLadyServiceBuilder, flyServiceBuilder)
   * }}}
   */
  inline def injectCustomShared[E1 >: E](inline serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[TestEnvironment, E1, T] =
    ${SpecServiceBuilderMacros.injectSharedImpl[TestEnvironment, R, E1, T]('self, 'serviceBuilder)}
}

private final class InjectSomePartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {
  inline def apply[E1 >: E](inline serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[R0, E1, T] =
  ${SpecServiceBuilderMacros.injectImpl[R0, R, E1, T]('self, 'serviceBuilder)}
}

private final class InjectSomeSharedPartiallyApplied[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {
  inline def apply[E1 >: E](inline serviceBuilder: ZServiceBuilder[_, E1, _]*): Spec[R0, E1, T] =
  ${SpecServiceBuilderMacros.injectSharedImpl[R0, R, E1, T]('self, 'serviceBuilder)}
}
