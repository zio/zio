package scalaz.zio

import scalaz.zio.syntax.IOSyntax.{ IOCreationEagerSyntax, IOCreationLazySyntax }

import scala.language.implicitConversions

package object syntax {
  implicit final def ioEagerSyntax[A](a: A): IOCreationEagerSyntax[A]  = new IOCreationEagerSyntax[A](a)
  implicit final def ioLazySyntax[A](a: => A): IOCreationLazySyntax[A] = new IOCreationLazySyntax[A](() => a)
}
