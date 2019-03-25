package scalaz
package zio
package interop

import org.scalacheck.{ Arbitrary, Cogen }
import org.specs2.ScalaCheck
import scalaz.Scalaz._
import scalaz.scalacheck.ScalazProperties._
import scalaz.zio.interop.scalaz72._

class scalaz72Spec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with ScalaCheck with GenIO {

  def is = s2"""
    laws must hold for
      Bifunctor              ${bifunctor.laws[ZIO[Any, ?, ?]]}
      BindRec                ${bindRec.laws[ZIO[Any, Int, ?]]}
      Plus                   ${plus.laws[ZIO[Any, Int, ?]]}
      MonadPlus              ${monadPlus.laws[ZIO[Any, Int, ?]]}
      MonadPlus (Monoid)     ${monadPlus.laws[ZIO[Any, Option[Unit], ?]]}
      MonadError             ${monadError.laws[ZIO[Any, Int, ?], Int]}
      Applicative (Parallel) ${applicative.laws[scalaz72.ParIO[Any, Int, ?]]}
  """

  implicit def ioEqual[E: Equal, A: Equal]: Equal[ZIO[Any, E, A]] =
    new Equal[ZIO[Any, E, A]] {
      override def equal(io1: ZIO[Any, E, A], io2: ZIO[Any, E, A]): Boolean =
        unsafeRun(io1.either) === unsafeRun(io2.either)
    }

  implicit def ioArbitrary[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] =
    Arbitrary(genIO[E, A])

  implicit def ioParEqual[E: Equal, A: Equal]: Equal[scalaz72.ParIO[Any, E, A]] =
    ioEqual[E, A].contramap(Tag.unwrap)

  implicit def ioParArbitrary[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[scalaz72.ParIO[Any, E, A]] =
    Arbitrary(genIO[E, A].map(Tag.apply))
}
