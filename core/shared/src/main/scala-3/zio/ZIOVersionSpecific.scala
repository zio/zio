package zio

import zio.internal.macros.ProviderMacros

trait ZIOVersionSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>
    /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val zio: ZIO[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyProvider: ZProvider[Fly, Nothing, OldLady] = ???
   * val flyProvider: ZProvider[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyProvider and Console to zio
   * val zio2 : ZIO[ZEnv, Nothing, Unit] = zio.injectCustom(oldLadyProvider, flyProvider)
   * }}}
   */
  inline def injectCustom[E1 >: E](inline provider: ZProvider[_,E1,_]*): ZIO[ZEnv, E1, A] =
    ${ProviderMacros.injectImpl[ZEnv, R, E1,A]('self, 'provider)}

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified provider and leaving the remainder `R0`.
   *
   * {{{
   * val clockProvider: ZProvider[Any, Nothing, Clock] = ???
   *
   * val zio: ZIO[Clock with Random, Nothing, Unit] = ???
   *
   * val zio2 = zio.injectSome[Random](clockProvider)
   * }}}
   */
  def injectSome[R0] =
    new InjectSomePartiallyApplied[R0, R, E, A](self)

  /**
   * Automatically assembles a provider for the ZIO effect, which
   * translates it to another level.
   */
  inline def inject[E1 >: E](inline provider: ZProvider[_,E1,_]*): ZIO[Any, E1, A] =
    ${ProviderMacros.injectImpl[Any,R,E1, A]('self, 'provider)}

}

private final class InjectSomePartiallyApplied[R0, -R, +E, +A](val self: ZIO[R, E, A]) extends AnyVal {
  inline def apply[E1 >: E](inline provider: ZProvider[_, E1, _]*): ZIO[R0, E1, A] =
  ${ProviderMacros.injectImpl[R0, R, E1, A]('self, 'provider)}
}
