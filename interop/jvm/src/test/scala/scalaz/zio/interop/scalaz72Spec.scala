package scalaz
package zio
package interop

import org.specs2.{ ScalaCheck, Specification }
import org.scalacheck.{ Arbitrary, Cogen }
import scalaz.scalacheck.ScalazProperties._
import Scalaz._

import scalaz72._

class scalaz72Spec extends Specification with ScalaCheck with GenIO with RTS {

  def is = s2"""
    laws must hold for
      Bifunctor              ${bifunctor.laws[IO]}
      BindRec                ${bindRec.laws[IO[Int, ?]]}
      MonadPlus              ${monadPlus.laws[IO[Int, ?]]}
      MonadError             ${monadError.laws[IO[Int, ?], Int]}
      Applicative (Parallel) ${applicative.laws[ParIO[Int, ?]]}
  """

  implicit def ioEqual[E: Equal, A: Equal]: Equal[IO[E, A]] =
    new Equal[IO[E, A]] {
      override def equal(io1: IO[E, A], io2: IO[E, A]): Boolean =
        unsafePerformIO(io1.attempt) === unsafePerformIO(io2.attempt)
    }

  implicit def ioArbitrary[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] =
    Arbitrary(genIO[E, A])

  implicit def ioParEqual[E: Equal, A: Equal]: Equal[ParIO[E, A]] =
    ioEqual[E, A].contramap(Tag.unwrap)

  implicit def ioParArbitrary[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ParIO[E, A]] =
    Arbitrary(genIO[E, A].map(Tag.apply))
}
