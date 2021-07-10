package zio

import zio.Accessible.IsAny

import scala.annotation.implicitNotFound

/**
 * A simple, macro-less means of creating accessors from Services. Extend
 * the companion object with `Accessible[ServiceName]`, then simply call
 * `Companion(_.someMethod)`, to return a ZIO effect that requires the
 * Service in its environment.
 *
 * Example:
 * {{{
 *   trait FooService {
 *     def magicNumber: UIO[Int]
 *     def castSpell(chant: String): UIO[Boolean]
 *   }
 *
 *   object FooService extends Accessible[FooService]
 *
 *   val example: ZIO[Has[FooService], Nothing, Unit] =
 *     for {
 *       int  <- FooService(_.magicNumber)
 *       bool <- FooService(_.castSpell("Oogabooga!"))
 *     } yield ()
 * }}}
 */
trait Accessible[R] {
  def apply[R0, E, A](f: R => ZIO[R0, E, A])(implicit tag: Tag[R], isAny: IsAny[R0]): ZIO[Has[R], E, A] =
    ZIO.serviceWith[R](f.asInstanceOf[R => ZIO[Any, E, A]])
}

object Accessible {
  @implicitNotFound(
    "The methods of your service definition should not use the environment, because this leaks implementation details to clients of the service, and these implementation details should be hidden and free to change based on the specific nature of the implementation. In order to use this accessor, please consider refactoring your service methods so they no longer use ZIO environment."
  )
  sealed trait IsAny[R]
  implicit val anyIsAny: IsAny[Any] = new IsAny[Any] {}
}
