package zio

import zio.internal.macros.ProviderMacros

final class WirePartiallyApplied[R](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline provider: ZProvider[_, E, _]*): ZProvider[Any, E, R] =
    ${ProviderMacros.fromAutoImpl[Any, R, E]('provider)}
}

final class WireSomePartiallyApplied[R0, R](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline provider: ZProvider[_, E, _]*): ZProvider[R0, E, R] =
    ${ProviderMacros.fromAutoImpl[R0, R, E]('provider)}
}

trait ZProviderCompanionVersionSpecific {

  /**
   * Automatically assembles a provider for the provided type.
   *
   * {{{
   * val provider = ZProvider.wire[Car](carProvider, wheelsProvider, engineProvider)
   * }}}
   */
  inline def wire[R]: WirePartiallyApplied[R] =
    new WirePartiallyApplied[R]()

    /**
   * Automatically assembles a provider for the provided type `R`,
   * leaving a remainder `R0`.
   *
   * {{{
   * val carProvider: ZProvider[Engine with Wheels, Nothing, Car] = ???
   * val wheelsProvider: ZProvider[Any, Nothing, Wheels] = ???
   *
   * val provider = ZProvider.wireSome[Engine, Car](carProvider, wheelsProvider)
   * }}}
   */
  def wireSome[R0, R] =
    new WireSomePartiallyApplied[R0, R]
}
