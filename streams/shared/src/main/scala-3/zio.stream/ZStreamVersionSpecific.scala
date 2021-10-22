package zio.stream

import zio.{ZDeps, ZEnv}
import zio.internal.macros.DepsMacros

trait ZStreamVersionSpecific[-R, +E, +O] { self: ZStream[R, E, O] =>
  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val stream: ZStream[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZDeps[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZDeps[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to stream
   * val stream2 : ZStream[ZEnv, Nothing, Unit] = stream.injectCustom(oldLadyLayer, flyLayer)
   * }}}
   */
  inline def injectCustom[E1 >: E](inline deps: ZDeps[_,E1,_]*): ZStream[ZEnv, E1, O] =
    ${ZStreamProvideMacro.injectImpl[ZEnv, R, E1, O]('self, 'deps)}

  /**
   * Automatically assembles a layer for the ZStream effect, which translates it to another level.
   */
  inline def inject[E1 >: E](inline deps: ZDeps[_,E1,_]*): ZStream[Any, E1, O] =
    ${ZStreamProvideMacro.injectImpl[Any, R, E1, O]('self, 'deps)}

}

object ZStreamProvideMacro {
  import scala.quoted._

  def injectImpl[R0: Type, R: Type, E: Type, A: Type](zstream: Expr[ZStream[R,E,A]], deps: Expr[Seq[ZDeps[_,E,_]]])(using Quotes): Expr[ZStream[R0,E,A]] = {
    val depsExpr = DepsMacros.fromAutoImpl[R0, R, E](deps)
    '{$zstream.provideDeps($depsExpr.asInstanceOf[ZDeps[R0,E,R]])}
  }
}