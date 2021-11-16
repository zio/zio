package zio

import zio.internal.macros.ServiceBuilderMacros

trait ZManagedVersionSpecific[-R, +E, +A] { self: ZManaged[R, E, A] =>
  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val managed: ZManaged[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyServiceBuilder: ZServiceBuilder[Fly, Nothing, OldLady] = ???
   * val flyServiceBuilder: ZServiceBuilder[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyServiceBuilder and Console to managed
   * val managed2 : ZManaged[ZEnv, Nothing, Unit] = managed.injectCustom(oldLadyServiceBuilder, flyServiceBuilder)
   * }}}
   */
  inline def injectCustom[E1 >: E](inline serviceBuilder: ZServiceBuilder[_,E1,_]*): ZManaged[ZEnv, E1, A] =
  ${ZManagedMacros.injectImpl[ZEnv, R, E1, A]('self, 'serviceBuilder)}


  /**
   * Splits the environment into two parts, assembling one part using the
   * specified service builder and leaving the remainder `R0`.
   *
   * {{{
   * val clockServiceBuilder: ZServiceBuilder[Any, Nothing, Clock] = ???
   *
   * val managed: ZIO[Clock with Random, Nothing, Unit] = ???
   *
   * val managed2 = managed.injectSome[Random](clockServiceBuilder)
   * }}}
   */
  def injectSome[R0] =
    new InjectSomeZManagedPartiallyApplied[R0, R, E, A](self)

  /**
   * Automatically assembles a service builder for the ZManaged effect,
   * which translates it to another level.
   */
  inline def inject[E1 >: E](inline serviceBuilder: ZServiceBuilder[_,E1,_]*): ZManaged[Any, E1, A] =
    ${ZManagedMacros.injectImpl[Any, R, E1, A]('self, 'serviceBuilder)}
}

private final class InjectSomeZManagedPartiallyApplied[R0, -R, +E, +A](val self: ZManaged[R, E, A]) extends AnyVal {
  inline def apply[E1 >: E](inline serviceBuilder: ZServiceBuilder[_, E1, _]*): ZManaged[R0, E1, A] =
    ${ZManagedMacros.injectImpl[R0, R, E1, A]('self, 'serviceBuilder)}
}


object ZManagedMacros {
  import scala.quoted._

  def injectImpl[R0: Type, R: Type, E: Type, A: Type](schedule: Expr[ZManaged[R, E, A]], serviceBuilder: Expr[Seq[ZServiceBuilder[_, E, _]]])(using Quotes):
  Expr[ZManaged[R0, E, A]] = {
    val serviceBuilderExpr = ServiceBuilderMacros.fromAutoImpl[R0, R, E](serviceBuilder)
    '{
      $schedule.provideServices($serviceBuilderExpr.asInstanceOf[ZServiceBuilder[R0, E, R]])
    }
  }
}




