package zio.test

import zio.ZIO
import zio.test.BoolAlgebraM._

final case class BoolAlgebraM[-R, +E, +A](run: ZIO[R, E, BoolAlgebra[A]]) { self =>

  def &&[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraM[R1, E1, A1]): BoolAlgebraM[R1, E1, A1] =
    BoolAlgebraM(run.zipWith(that.run)(_ && _))

  def ||[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraM[R1, E1, A1]): BoolAlgebraM[R1, E1, A1] =
    BoolAlgebraM(run.zipWith(that.run)(_ || _))

  def ==>[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraM[R1, E1, A1]): BoolAlgebraM[R1, E1, A1] =
    BoolAlgebraM(run.zipWith(that.run)(_ ==> _))

  def <==>[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraM[R1, E1, A1]): BoolAlgebraM[R1, E1, A1] =
    BoolAlgebraM(run.zipWith(that.run)(_ <==> _))

  def unary_! : BoolAlgebraM[R, E, A] =
    BoolAlgebraM(run.map(!_))

  def as[B](b: B): BoolAlgebraM[R, E, B] =
    map(_ => b)

  def flatMap[R1 <: R, E1 >: E, B](f: A => BoolAlgebraM[R1, E1, B]): BoolAlgebraM[R1, E1, B] =
    BoolAlgebraM(run.flatMap(_.flatMapM(f(_).run)))

  def implies[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraM[R1, E1, A1]): BoolAlgebraM[R1, E1, A1] =
    BoolAlgebraM(run.zipWith(that.run)(_ implies _))

  def isSuccess: ZIO[R, E, Boolean] =
    run.map(_.isSuccess)

  def map[B](f: A => B): BoolAlgebraM[R, E, B] =
    flatMap(f andThen success)
}

object BoolAlgebraM {

  def failure[A](a: A): BoolAlgebraM[Any, Nothing, A] =
    BoolAlgebraM(ZIO.succeedNow(BoolAlgebra.failure(a)))

  def fromEffect[R, E, A](effect: ZIO[R, E, A]): BoolAlgebraM[R, E, A] =
    BoolAlgebraM(effect.map(BoolAlgebra.success))

  def success[A](a: A): BoolAlgebraM[Any, Nothing, A] =
    BoolAlgebraM(ZIO.succeedNow(BoolAlgebra.success(a)))
}
