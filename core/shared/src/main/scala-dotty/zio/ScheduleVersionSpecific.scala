package zio

import zio.internal.macros.ProvideLayerMacros

trait ScheduleVersionSpecific[-Env, -In, +Out] { self: Schedule[Env, In, Out] =>
  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val zio: Schedule[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to zio
   * val zio2 : Schedule[ZEnv, Nothing, Unit] = zio.provideCustomLayer(oldLadyLayer, flyLayer)
   * }}}
   */
  inline def provideCustomLayer(inline layers: ZLayer[_,Nothing,_]*): Schedule[ZEnv, In, Out] =
  ${ScheduleMacros.provideLayerImpl[ZEnv, Env, In, Out]('self, 'layers)}

  /**
   * Automatically assembles a layer for the Schedule effect, which translates it to another level.
   */
  inline def provideLayer(inline layers: ZLayer[_,Nothing,_]*): Schedule[Any, In, Out] =
  ${ScheduleMacros.provideLayerImpl[Any, Env, In, Out]('self, 'layers)}

}

object ScheduleMacros {
  import scala.quoted._

  def provideLayerImpl[R0: Type, R: Type, In: Type, Out: Type](schedule: Expr[Schedule[R, In, Out]], layers: Expr[Seq[ZLayer[_, Nothing, _]]])(using Quotes):
  Expr[Schedule[R0, In, Out]] = {
    val layerExpr = ProvideLayerAutoMacros.fromAutoImpl[R0, R, Nothing](layers)
    '{
      $schedule.provideLayerManual($layerExpr.asInstanceOf[ZLayer[R0, Nothing, R]])
    }
  }
}


