package zio

import zio.internal.macros.DepsMacros

trait ZManagedVersionSpecific[-R, +E, +A] { self: ZManaged[R, E, A] =>
  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val managed: ZManaged[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyDeps: ZDeps[Fly, Nothing, OldLady] = ???
   * val flyDeps: ZDeps[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyDeps and Console to managed
   * val managed2 : ZManaged[ZEnv, Nothing, Unit] = managed.injectCustom(oldLadyDeps, flyDeps)
   * }}}
   */
  inline def injectCustom[E1 >: E](inline deps: ZDeps[_,E1,_]*): ZManaged[ZEnv, E1, A] =
  ${ZManagedMacros.injectImpl[ZEnv, R, E1, A]('self, 'deps)}


  /**
   * Splits the environment into two parts, assembling one part using the
   * specified dependencies and leaving the remainder `R0`.
   *
   * {{{
   * val clockDeps: ZDeps[Any, Nothing, Clock] = ???
   *
   * val managed: ZIO[Clock with Random, Nothing, Unit] = ???
   *
   * val managed2 = managed.injectSome[Random](clockDeps)
   * }}}
   */
  def injectSome[R0 <: Has[_]] =
    new InjectSomeZManagedPartiallyApplied[R0, R, E, A](self)

  /**
   * Automatically assembles a set of dependencies for the ZManaged effect,
   * which translates it to another level.
   */
  inline def inject[E1 >: E](inline deps: ZDeps[_,E1,_]*): ZManaged[Any, E1, A] =
    ${ZManagedMacros.injectImpl[Any, R, E1, A]('self, 'deps)}
}

private final class InjectSomeZManagedPartiallyApplied[R0 <: Has[_], -R, +E, +A](val self: ZManaged[R, E, A]) extends AnyVal {
  inline def apply[E1 >: E](inline deps: ZDeps[_, E1, _]*): ZManaged[R0, E1, A] =
    ${ZManagedMacros.injectImpl[R0, R, E1, A]('self, 'deps)}
}


object ZManagedMacros {
  import scala.quoted._

  def injectImpl[R0: Type, R: Type, E: Type, A: Type](schedule: Expr[ZManaged[R, E, A]], deps: Expr[Seq[ZDeps[_, E, _]]])(using Quotes):
  Expr[ZManaged[R0, E, A]] = {
    val depsExpr = DepsMacros.fromAutoImpl[R0, R, E](deps)
    '{
      $schedule.provideDeps($depsExpr.asInstanceOf[ZDeps[R0, E, R]])
    }
  }
}




