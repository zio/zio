package zio

import zio.Accessible.IsAny

import scala.annotation.implicitNotFound

/**
 * A simple, macro-less means of creating accessors from Services on members
 * returning a ZManaged. Extend the companion object with
 * `ManagedAccessible[ServiceName]`, then simply call `Companion(_.someMethod)`
 * to return a ZManaged effect that requires the Service in its environment.
 *
 * Example:
 * {{{
 *   trait FooService {
 *     def readSpellBook: UManaged[String]
 *     def prepareWand: UManaged[Unit]
 *   }
 *
 *   object FooService extends ManagedAccessible[FooService]
 *
 *   val example: ZManaged[Has[FooService], Nothing, String] =
 *     for {
 *       _     <- FooService(_.prepareWand)
 *       spell <- FooService(_.readSpellBook)
 *     } yield spell
 * }}}
 */
trait ManagedAccessible[R] {
  def apply[R0, E, A](f: R => ZManaged[R0, E, A])(implicit tag: Tag[R], isAny: IsAny[R0]): ZManaged[Has[R], E, A] =
    ZManaged.serviceWithManaged[R](f.asInstanceOf[R => ZManaged[Any, E, A]])
}

object ManagedAccessible {
  @implicitNotFound(
    "The methods of your service definition should not use the environment, because this leaks implementation details to clients of the service, and these implementation details should be hidden and free to change based on the specific nature of the implementation. In order to use this accessor, please consider refactoring your service methods so they no longer use ZIO environment."
  )
  sealed trait IsAny[R]
  implicit val anyIsAny: IsAny[Any] = new IsAny[Any] {}
}
