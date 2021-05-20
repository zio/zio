package zio

/**
 * A simple, macro-less means of creating accessors from Services. Extend
 * the companion object with `Accessor[ServiceName]`, then simply call
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
 *   object FooService extends Accessor[FooService]
 *
 *   val example: ZIO[Has[FooService], Nothing, Unit] =
 *     for {
 *       int  <- FooService(_.magicNumber)
 *       bool <- FooService(_.castSpell("Oogabooga!"))
 *     } yield ()
 * }}}
 */
trait Accessor[R] {
  def apply[E, A](f: R => ZIO[Any, E, A])(implicit tag: Tag[R]): ZIO[Has[R], E, A] =
    ZIO.serviceWith[R](f)
}
