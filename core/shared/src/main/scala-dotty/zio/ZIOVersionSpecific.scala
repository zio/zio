package zio

import zio.internal.macros.ProvideLayerAutoMacros

trait ZIOVersionSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>
  inline def provideLayerAuto[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZIO[Any, E1, A] = 
    ${ProvideLayerAutoMacros.provideLayerAutoImpl('self, 'layers)}

  inline def provideCustomLayerAuto[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZIO[ZEnv, E1, A] = 
    ${ProvideLayerAutoMacros.provideCustomLayerAutoImpl('self, 'layers)}
}

trait ZLayerCompanionVersionSpecific {
  //
}