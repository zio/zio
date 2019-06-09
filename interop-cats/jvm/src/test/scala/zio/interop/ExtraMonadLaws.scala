package zio.interop

import cats.Monad
import cats.implicits._
import cats.kernel.laws.IsEq
import cats.laws.IsEqArrow

trait ExtraMonadLaws[F[_]] {
  implicit def F: Monad[F]

  lazy val tailRecMConstructionStackSafety: IsEq[F[Int]] = {
    val n = 50000
    def f(i: Int): F[Either[Int, Int]] =
      if (i < n) F.tailRecM(i + 1)(f).map(Either.left)
      else F.pure(Either.right(i))
    val res = F.tailRecM(0)(f)
    res <-> F.pure(n)
  }
}

object ExtraMonadLaws {
  def apply[F[_]](implicit ev: Monad[F]): ExtraMonadLaws[F] =
    new ExtraMonadLaws[F] { def F: Monad[F] = ev }
}
