package scalaz.zio
package interop
package bio
package instances

import cats.syntax.either._
import cats.syntax.option._
import scalaz.zio.Exit.{ Failure, Success }

object ZioFiber2 {

  @inline def fromFiber[E, A](fiber: Fiber[E, A]): Fiber2[IO, E, A] =
    new Fiber2[IO, E, A] {

      def await: IO[Nothing, Option[Either[E, A]]] =
        fiber.await >>= fromExit

      def cancel: IO[Nothing, Option[Either[E, A]]] =
        fiber.interrupt >>= fromExit

      def join: IO[E, A] =
        fiber.join
    }

  private[this] def fromExit[E, A](exit: Exit[E, A]): IO[Nothing, Option[Either[E, A]]] =
    exit match {
      case Success(a) =>
        IO.succeed(a.asRight.some)

      case Failure(cause) =>
        if (cause.failed)
          IO.succeed(
            cause.failures.headOption map { e: E =>
              e.asLeft
            }
          )
        else
          IO.succeed(None)
    }
}
