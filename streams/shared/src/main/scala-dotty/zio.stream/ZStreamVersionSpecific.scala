package zio.stream

import zio.{ZLayer, ZEnv}
import zio.internal.macros.ProvideLayerAutoMacros

trait ZStreamVersionSpecific[-R, +E, +O] { self: ZStream[R, E, O] =>
  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val stream: ZStream[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to stream
   * val stream2 : ZStream[ZEnv, Nothing, Unit] = stream.provideCustomLayerAuto(oldLadyLayer, flyLayer)
   * }}}
   */
  inline def provideCustomLayerAuto[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZStream[ZEnv, E1, O] =
    ${ZStreamProvideMacro.provideLayerAutoImpl[ZEnv, R, E1, O]('self, 'layers)}

  /**
   * Automatically assembles a layer for the ZStream effect, which translates it to another level.
   */
  inline def provideLayerAuto[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZStream[Any, E1, O] =
    ${ZStreamProvideMacro.provideLayerAutoImpl[Any, R, E1, O]('self, 'layers)}

}

object ZStreamProvideMacro {
  import scala.quoted._

  def provideLayerAutoImpl[R0: Type, R: Type, E: Type, A: Type](zstream: Expr[ZStream[R,E,A]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZStream[R0,E,A]] = {
    val layerExpr = ProvideLayerAutoMacros.fromAutoImpl[R0, R, E](layers)
    '{$zstream.provideLayer($layerExpr.asInstanceOf[ZLayer[R0,E,R]])}
  }
}