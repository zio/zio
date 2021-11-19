package zio

import zio.internal.macros.ServiceBuilderMacros

final class WirePartiallyApplied[R](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline serviceBuilder: ZServiceBuilder[_, E, _]*): ZServiceBuilder[Any, E, R] =
    ${ServiceBuilderMacros.fromAutoImpl[Any, R, E]('serviceBuilder)}
}

final class WireSomePartiallyApplied[R0, R](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline serviceBuilder: ZServiceBuilder[_, E, _]*): ZServiceBuilder[R0, E, R] =
    ${ServiceBuilderMacros.fromAutoImpl[R0, R, E]('serviceBuilder)}
}

trait ZServiceBuilderCompanionVersionSpecific {

  /**
   * Automatically assembles a service builder for the provided type.
   *
   * {{{
   * val serviceBuilder = ZServiceBuilder.wire[Car](carServiceBuilder, wheelsServiceBuilder, engineServiceBuilder)
   * }}}
   */
  inline def wire[R]: WirePartiallyApplied[R] =
    new WirePartiallyApplied[R]()

    /**
   * Automatically assembles a service builder for the provided type `R`,
   * leaving a remainder `R0`.
   *
   * {{{
   * val carServiceBuilder: ZServiceBuilder[Engine with Wheels, Nothing, Car] = ???
   * val wheelsServiceBuilder: ZServiceBuilder[Any, Nothing, Wheels] = ???
   *
   * val serviceBuilder = ZServiceBuilder.wireSome[Engine, Car](carServiceBuilder, wheelsServiceBuilder)
   * }}}
   */
  def wireSome[R0, R] =
    new WireSomePartiallyApplied[R0, R]
}
