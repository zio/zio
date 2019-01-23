package scalaz.zio

import scalaz.zio.syntax.IOSyntax._
import scala.language.implicitConversions

package object syntax {
  implicit final def ioEagerSyntax[A](a: A): IOCreationEagerSyntax[A]                        = new IOCreationEagerSyntax[A](a)
  implicit final def ioLazySyntax[A](a: => A): IOCreationLazySyntax[A]                       = new IOCreationLazySyntax[A](() => a)
  implicit final def ioIterableSyntax[E, A](ios: Iterable[IO[E, A]]): IOIterableSyntax[E, A] = new IOIterableSyntax(ios)
  implicit final def ioSyntax[E, A](io: IO[E, A]): IOSyntax[E, A]                            = new IOSyntax(io)
  implicit final def ioTuple2Syntax[E, A, B](ios: (IO[E, A], IO[E, B])): IOTuple2[E, A, B]   = new IOTuple2(ios)
  implicit final def ioTuple3Syntax[E, A, B, C](ios: (IO[E, A], IO[E, B], IO[E, C])): IOTuple3[E, A, B, C] =
    new IOTuple3(ios)
  implicit final def ioTuple4Syntax[E, A, B, C, D](
    ios: (IO[E, A], IO[E, B], IO[E, C], IO[E, D])
  ): IOTuple4[E, A, B, C, D] =
    new IOTuple4(ios)
}
