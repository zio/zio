package zio

import zio.internal.macros.LayerMacros

trait ZManagedVersionSpecific[-R, +E, +A] { self: ZManaged[R, E, A] =>
  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val managed: ZManaged[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to managed
   * val managed2 : ZManaged[ZEnv, Nothing, Unit] = managed.injectCustom(oldLadyLayer, flyLayer)
   * }}}
   */
  inline def injectCustom[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZManaged[ZEnv, E1, A] =
  ${ZManagedMacros.injectImpl[ZEnv, R, E1, A]('self, 'layers)}


  /**
   * Splits the environment into two parts, assembling one part using the
   * specified layers and leaving the remainder `R0`.
   *
   * {{{
   * val clockLayer: ZLayer[Any, Nothing, Clock] = ???
   *
   * val managed: ZIO[Clock with Random, Nothing, Unit] = ???
   *
   * val managed2 = managed.injectSome[Random](clockLayer)
   * }}}
   */
  def injectSome[R0 <: Has[_]] =
    new InjectSomeZManagedPartiallyApplied[R0, R, E, A](self)

  /**
   * Automatically assembles a layer for the ZManaged effect, which translates it to another level.
   */
  inline def inject[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZManaged[Any, E1, A] =
    ${ZManagedMacros.injectImpl[Any, R, E1, A]('self, 'layers)}
}

private final class InjectSomeZManagedPartiallyApplied[R0 <: Has[_], -R, +E, +A](val self: ZManaged[R, E, A]) extends AnyVal {
  inline def apply[E1 >: E](inline layers: ZLayer[_, E1, _]*): ZManaged[R0, E1, A] =
    ${ZManagedMacros.injectImpl[R0, R, E1, A]('self, 'layers)}
}


object ZManagedMacros {
  import scala.quoted._

  def injectImpl[R0: Type, R: Type, E: Type, A: Type](schedule: Expr[ZManaged[R, E, A]], layers: Expr[Seq[ZLayer[_, E, _]]])(using Quotes):
  Expr[ZManaged[R0, E, A]] = {
    val layerExpr = LayerMacros.fromAutoImpl[R0, R, E](layers)
    '{
      $schedule.provideLayer($layerExpr.asInstanceOf[ZLayer[R0, E, R]])
    }
  }
}




