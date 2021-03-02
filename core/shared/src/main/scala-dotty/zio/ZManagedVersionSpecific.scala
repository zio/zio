package zio

import zio.internal.macros.ProvideLayerMacros

trait ZManagedVersionSpecific[-R, +E, +A] { self: ZManaged[R, E, A] =>
  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val zio: ZManaged[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to zio
   * val zio2 : ZManaged[ZEnv, Nothing, Unit] = zio.provideCustomLayer(oldLadyLayer, flyLayer)
   * }}}
   */
  inline def provideCustomLayer[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZManaged[ZEnv, E1, A] =
  ${ZManagedMacros.provideLayerImpl[ZEnv, R, E1, A]('self, 'layers)}

  /**
   * Automatically assembles a layer for the ZManaged effect, which translates it to another level.
   */
  inline def provideLayer[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZManaged[Any, E1, A] =
  ${ZManagedMacros.provideLayerImpl[Any, R, E1, A]('self, 'layers)}

}

object ZManagedMacros {
  import scala.quoted._

  def provideLayerImpl[R0: Type, R: Type, E: Type, A: Type](schedule: Expr[ZManaged[R, E, A]], layers: Expr[Seq[ZLayer[_, E, _]]])(using Quotes):
  Expr[ZManaged[R0, E, A]] = {
    val layerExpr = ProvideLayerAutoMacros.fromAutoImpl[R0, R, E](layers)
    '{
      $schedule.provideLayerManual($layerExpr.asInstanceOf[ZLayer[R0, E, R]])
    }
  }
}


