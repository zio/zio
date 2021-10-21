package zio

import zio.internal.macros.LayerMacros

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
   * val zio2 : ZIO[ZEnv, Nothing, Unit] = zio.injectCustom(oldLadyLayer, flyLayer)
   * }}}
   */
  inline def injectCustom[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZIO[ZEnv, E1, A] =
    ${LayerMacros.injectImpl[ZEnv, R, E1,A]('self, 'layers)}

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified layers and leaving the remainder `R0`.
   *
   * {{{
   * val clockLayer: ZLayer[Any, Nothing, Clock] = ???
   *
   * val zio: ZIO[Clock with Random, Nothing, Unit] = ???
   *
   * val zio2 = zio.injectSome[Random](clockLayer)
   * }}}
   */
  def injectSome[R0 <: Has[_]] =
    new InjectSomePartiallyApplied[R0, R, E, A](self)

  /**
   * Automatically assembles a layer for the ZIO effect, which translates it to another level.
   */
  inline def inject[E1 >: E](inline layers: ZLayer[_,E1,_]*): ZIO[Any, E1, A] =
    ${LayerMacros.injectImpl[Any,R,E1, A]('self, 'layers)}

}

private final class InjectSomePartiallyApplied[R0 <: Has[_], -R, +E, +A](val self: ZIO[R, E, A]) extends AnyVal {
  inline def apply[E1 >: E](inline layers: ZLayer[_, E1, _]*): ZIO[R0, E1, A] =
  ${LayerMacros.injectImpl[R0, R, E1, A]('self, 'layers)}
}
