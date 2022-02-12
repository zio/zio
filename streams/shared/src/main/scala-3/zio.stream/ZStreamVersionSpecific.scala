package zio.stream

import zio.{ZLayer, ZEnv, NeedsEnv}
import zio.internal.macros.LayerMacros

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
   * val stream2 : ZStream[ZEnv, Nothing, Unit] = stream.provideCustom(oldLadyLayer, flyLayer)
   * }}}
   */
  inline def provideCustom[E1 >: E](inline layer: ZLayer[_,E1,_]*)(using ev: NeedsEnv[R]): ZStream[ZEnv, E1, O] =
    ${ZStreamProvideMacro.provideImpl[ZEnv, R, E1, O]('self, 'layer)}

  /**
   * Automatically assembles a layer for the ZStream effect,
   * which translates it to another level.
   */
  inline def provide[E1 >: E](inline layer: ZLayer[_,E1,_]*)(using ev: NeedsEnv[R]): ZStream[Any, E1, O] =
    ${ZStreamProvideMacro.provideImpl[Any, R, E1, O]('self, 'layer)}

}

object ZStreamProvideMacro {
  import scala.quoted._

  def provideImpl[R0: Type, R: Type, E: Type, A: Type](zstream: Expr[ZStream[R,E,A]], layer: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZStream[R0,E,A]] = {
    val layerExpr = LayerMacros.constructLayer[R0, R, E](layer)
    '{$zstream.provideLayer($layerExpr.asInstanceOf[ZLayer[R0,E,R]])}
  }
}