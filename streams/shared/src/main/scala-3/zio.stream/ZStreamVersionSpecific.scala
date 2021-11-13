package zio.stream

import zio.{ZServiceBuilder, ZEnv}
import zio.internal.macros.ServiceBuilderMacros

trait ZStreamVersionSpecific[-R, +E, +O] { self: ZStream[R, E, O] =>
  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val stream: ZStream[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyServiceBuilder: ZServiceBuilder[Fly, Nothing, OldLady] = ???
   * val flyServiceBuilder: ZServiceBuilder[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyServiceBuilder and Console to stream
   * val stream2 : ZStream[ZEnv, Nothing, Unit] = stream.injectCustom(oldLadyServiceBuilder, flyServiceBuilder)
   * }}}
   */
  inline def injectCustom[E1 >: E](inline serviceBuilder: ZServiceBuilder[_,E1,_]*): ZStream[ZEnv, E1, O] =
    ${ZStreamProvideMacro.injectImpl[ZEnv, R, E1, O]('self, 'serviceBuilder)}

  /**
   * Automatically assembles a service builder for the ZStream effect,
   * which translates it to another level.
   */
  inline def inject[E1 >: E](inline serviceBuilder: ZServiceBuilder[_,E1,_]*): ZStream[Any, E1, O] =
    ${ZStreamProvideMacro.injectImpl[Any, R, E1, O]('self, 'serviceBuilder)}

}

object ZStreamProvideMacro {
  import scala.quoted._

  def injectImpl[R0: Type, R: Type, E: Type, A: Type](zstream: Expr[ZStream[R,E,A]], serviceBuilder: Expr[Seq[ZServiceBuilder[_,E,_]]])(using Quotes): Expr[ZStream[R0,E,A]] = {
    val serviceBuilderExpr = ServiceBuilderMacros.fromAutoImpl[R0, R, E](serviceBuilder)
    '{$zstream.provideServices($serviceBuilderExpr.asInstanceOf[ZServiceBuilder[R0,E,R]])}
  }
}