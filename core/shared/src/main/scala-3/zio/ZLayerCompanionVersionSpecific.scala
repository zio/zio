package zio

import zio.internal.macros.LayerMacros

final class LayerMakePartiallyApplied[R](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline layer: ZLayer[_, E, _]*): ZLayer[Any, E, R] =
    ${LayerMacros.fromAutoImpl[Any, R, E]('layer)}
}

final class LayerMakeSomePartiallyApplied[R0, R](val dummy: Boolean = true) extends AnyVal {
  inline def apply[E](inline layer: ZLayer[_, E, _]*): ZLayer[R0, E, R] =
    ${LayerMacros.fromAutoImpl[R0, R, E]('layer)}
}

trait ZLayerCompanionVersionSpecific {

  /**
   * Automatically assembles a layer for the provided type.
   *
   * {{{
   * val layer = ZLayer.make[Car](carLayer, wheelsLayer, engineLayer)
   * }}}
   */
  inline def make[R]: LayerMakePartiallyApplied[R] =
    new LayerMakePartiallyApplied[R]()

    /**
   * Automatically assembles a layer for the provided type `R`,
   * leaving a remainder `R0`.
   *
   * {{{
   * val carLayer: ZLayer[Engine with Wheels, Nothing, Car] = ???
   * val wheelsLayer: ZLayer[Any, Nothing, Wheels] = ???
   *
   * val layer = ZLayer.makeSome[Engine, Car](carLayer, wheelsLayer)
   * }}}
   */
  def makeSome[R0, R] =
    new LayerMakeSomePartiallyApplied[R0, R]

  /**
   * Automatically constructs a layer for the provided type `R`, leaving a
   * remainder `ZEnv`. This will satisfy all transitive `ZEnv` requirements with
   * `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to zio
   * val layer : ZLayer[ZEnv, Nothing, OldLady] = ZLayer.makeCustom[OldLady](oldLadyLayer, flyLayer)
   * }}}
   */
  def makeCustom[R]: LayerMakeSomePartiallyApplied[ZEnv, R] =
    new LayerMakeSomePartiallyApplied[ZEnv, R]

}
