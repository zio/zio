package zio.stream

import zio.{ZProvider, ZEnv}
import zio.internal.macros.ProviderMacros

trait ZStreamVersionSpecific[-R, +E, +O] { self: ZStream[R, E, O] =>
  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val stream: ZStream[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyProvider: ZProvider[Fly, Nothing, OldLady] = ???
   * val flyProvider: ZProvider[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyProvider and Console to stream
   * val stream2 : ZStream[ZEnv, Nothing, Unit] = stream.injectCustom(oldLadyProvider, flyProvider)
   * }}}
   */
  inline def injectCustom[E1 >: E](inline provider: ZProvider[_,E1,_]*): ZStream[ZEnv, E1, O] =
    ${ZStreamProvideMacro.injectImpl[ZEnv, R, E1, O]('self, 'provider)}

  /**
   * Automatically assembles a provider for the ZStream effect,
   * which translates it to another level.
   */
  inline def inject[E1 >: E](inline provider: ZProvider[_,E1,_]*): ZStream[Any, E1, O] =
    ${ZStreamProvideMacro.injectImpl[Any, R, E1, O]('self, 'provider)}

}

object ZStreamProvideMacro {
  import scala.quoted._

  def injectImpl[R0: Type, R: Type, E: Type, A: Type](zstream: Expr[ZStream[R,E,A]], provider: Expr[Seq[ZProvider[_,E,_]]])(using Quotes): Expr[ZStream[R0,E,A]] = {
    val providerExpr = ProviderMacros.fromAutoImpl[R0, R, E](provider)
    '{$zstream.provide($providerExpr.asInstanceOf[ZProvider[R0,E,R]])}
  }
}