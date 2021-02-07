package zio

import zio.internal.macros.ProvideLayerAutoMacros

final class FromAutoPartiallyApplied[R <: Has[_]](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline layers: ZLayer[_, E, _]*): ZLayer[Any, E, R] =
    ${ProvideLayerAutoMacros.fromAutoImpl[Any, R, E]('layers)}
}

final class FromSomeAutoPartiallyApplied[R0 <: Has[_], R <: Has[_]](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline layers: ZLayer[_, E, _]*): ZLayer[R0, E, R] =
    ${ProvideLayerAutoMacros.fromAutoImpl[R0, R, E]('layers)}
}

final class FromAutoDebugPartiallyApplied[R <: Has[_]](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline layers: ZLayer[_, E, _]*): ZLayer[Any, E, R] =
    ${ProvideLayerAutoMacros.fromAutoDebugImpl[R, E]('layers)}
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

    /**
   * Generates a visualization of the automatically assembled
   * final ZLayer. The type of the target layer[s] must be provided.
   *
   * {{{
   * ZLayer.fromAutoDebug[App](App.live, UserService.live, ...)
   *
   * >                            App.live
   * >                        ┌──────┴──────────────────────┐
   * >                UserService.live                Console.live
   * >        ┌──────────────┴┬──────────────┐
   * >  UserRepo.live  Analytics.live  Console.live
   * >        │
   * >  Console.live
   * >  }}}
   */
  inline def fromAutoDebug[R <: Has[_]]: FromAutoDebugPartiallyApplied[R] = new FromAutoDebugPartiallyApplied[R]()
}
