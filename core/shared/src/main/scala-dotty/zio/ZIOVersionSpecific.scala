package zio

import zio.internal.macros.ProvideLayerMacros

trait ZIOVersionSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>
    /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val zio: ZIO[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to zio
   * val zio2 : ZIO[ZEnv, Nothing, Unit] = zio.provideCustomLayer(oldLadyLayer, flyLayer)
   * }}}
   */
  inline def provideCustomLayer[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZIO[ZEnv, E1, A] =
    ${ProvideLayerAutoMacros.provideLayerImpl[ZEnv, R, E1,A]('self, 'layers)}

    /**
   * Automatically assembles a layer for the ZIO effect, which translates it to another level.
   */
  inline def provideLayer[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZIO[Any, E1, A] =
    ${ProvideLayerAutoMacros.provideLayerImpl[Any, R,E1, A]('self, 'layers)}

}

