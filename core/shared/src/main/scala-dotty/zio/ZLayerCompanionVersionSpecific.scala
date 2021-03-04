package zio

import zio.internal.macros.ProvideLayerMacros

final class FromAutoPartiallyApplied[R <: Has[_]](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline layers: ZLayer[_, E, _]*): ZLayer[Any, E, R] =
    ${ProvideLayerAutoMacros.fromAutoImpl[Any, R, E]('layers)}
}

final class FromSomeAutoPartiallyApplied[R0 <: Has[_], R <: Has[_]](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline layers: ZLayer[_, E, _]*): ZLayer[R0, E, R] =
    ${ProvideLayerAutoMacros.fromAutoImpl[R0, R, E]('layers)}
}

trait ZLayerCompanionVersionSpecific {
      /**
   * Automatically assembles a layer for the provided type.
   *
   * {{{
   * val layer = ZLayer.fromAuto[Car](carLayer, wheelsLayer, engineLayer)
   * }}}
   */
  inline def fromAuto[R <: Has[_]]: FromAutoPartiallyApplied[R] = 
    new FromAutoPartiallyApplied[R]()

    /**
   * Automatically assembles a layer for the provided type `R`, leaving 
   * a remainder `R0`. 
   *
   * {{{
   * val carLayer: ZLayer[Engine with Wheels, Nothing, Car] = ???
   * val wheelsLayer: ZLayer[Any, Nothing, Wheels] = ???
   * 
   * val layer = ZLayer.fromSomeAuto[Engine, Car](carLayer, wheelsLayer)
   * }}}
   */
  def fromSomeAuto[R0 <: Has[_], R <: Has[_]] =
    new FromSomeAutoPartiallyApplied[R0, R]
}
