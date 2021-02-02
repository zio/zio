package zio

import zio.internal.macros.ProvideLayerAutoMacros

trait ZIOVersionSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>
  inline def provideLayerAuto[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZIO[Any, E1, A] = 
    ${ProvideLayerAutoMacros.provideLayerAutoImpl('self, 'layers)}

  inline def provideCustomLayerAuto[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZIO[ZEnv, E1, A] = 
    ${ProvideLayerAutoMacros.provideCustomLayerAutoImpl('self, 'layers)}
}

final class FromLayerAutoPartiallyApplied[R <: Has[_]](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline layers: ZLayer[_, E, _]*): ZLayer[Any, E, R] =
    ${ProvideLayerAutoMacros.fromAutoImpl[R, E]('layers)}
}

trait ZLayerCompanionVersionSpecific {
  inline def fromAuto[R <: Has[_]]: FromLayerAutoPartiallyApplied[R] = new FromLayerAutoPartiallyApplied[R]()
}