package zio

import zio.internal.macros.DepsMacros

final class WirePartiallyApplied[R <: Has[_]](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline deps: ZDeps[_, E, _]*): ZDeps[Any, E, R] =
    ${DepsMacros.fromAutoImpl[Any, R, E]('deps)}
}

final class WireSomePartiallyApplied[R0 <: Has[_], R <: Has[_]](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline deps: ZDeps[_, E, _]*): ZDeps[R0, E, R] =
    ${DepsMacros.fromAutoImpl[R0, R, E]('deps)}
}

trait ZDepsCompanionVersionSpecific {

  /**
   * Automatically assembles a set of dependencies for the provided type.
   *
   * {{{
   * val deps = ZDeps.wire[Car](carDeps, wheelsDeps, engineDeps)
   * }}}
   */
  inline def wire[R <: Has[_]]: WirePartiallyApplied[R] =
    new WirePartiallyApplied[R]()

    /**
   * Automatically assembles a set of dependencies for the provided type `R`,
   * leaving a remainder `R0`.
   *
   * {{{
   * val carDeps: ZDeps[Engine with Wheels, Nothing, Car] = ???
   * val wheelsDeps: ZDeps[Any, Nothing, Wheels] = ???
   *
   * val deps = ZDeps.wireSome[Engine, Car](carDeps, wheelsDeps)
   * }}}
   */
  def wireSome[R0 <: Has[_], R <: Has[_]] =
    new WireSomePartiallyApplied[R0, R]
}
