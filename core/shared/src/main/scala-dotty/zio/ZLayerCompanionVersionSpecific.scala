package zio

import zio.internal.macros.ProvideLayerAutoMacros

final class FromLayerAutoPartiallyApplied[R <: Has[_]](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline layers: ZLayer[_, E, _]*): ZLayer[Any, E, R] =
    ${ProvideLayerAutoMacros.fromAutoImpl[R, E]('layers)}
}

final class FromLayerAutoDebugPartiallyApplied[R <: Has[_]](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline layers: ZLayer[_, E, _]*): ZLayer[Any, E, R] =
    ${ProvideLayerAutoMacros.fromAutoDebugImpl[R, E]('layers)}
}

trait ZLayerCompanionVersionSpecific {
      /**
   * Automatically assembles a layer for the provided type.
   * The type of the target layer[s] must be provided.
   *
   * {{{
   * ZLayer.fromAuto[A with B](A.live, B.live)
   * }}}
   */
  inline def fromAuto[R <: Has[_]]: FromLayerAutoPartiallyApplied[R] = new FromLayerAutoPartiallyApplied[R]()

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
  inline def fromAutoDebug[R <: Has[_]]: FromLayerAutoDebugPartiallyApplied[R] = new FromLayerAutoDebugPartiallyApplied[R]()
}
