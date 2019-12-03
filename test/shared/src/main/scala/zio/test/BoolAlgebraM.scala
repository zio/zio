package zio.test

import zio.test.BoolAlgebraM._
import zio.ZIO

final case class BoolAlgebraM[-R, +E, +A](run: ZIO[R, E, BoolAlgebra[A]]) { self =>

  final def &&[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraM[R1, E1, A1]): BoolAlgebraM[R1, E1, A1] =
    BoolAlgebraM(run.zipWith(that.run)(_ && _))

  final def ||[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraM[R1, E1, A1]): BoolAlgebraM[R1, E1, A1] =
    BoolAlgebraM(run.zipWith(that.run)(_ || _))

  final def ==>[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraM[R1, E1, A1]): BoolAlgebraM[R1, E1, A1] =
    BoolAlgebraM(run.zipWith(that.run)(_ ==> _))

  final def unary_! : BoolAlgebraM[R, E, A] =
    BoolAlgebraM(run.map(!_))

  final def as[B](b: B): BoolAlgebraM[R, E, B] =
    map(_ => b)

  final def flatMap[R1 <: R, E1 >: E, B](f: A => BoolAlgebraM[R1, E1, B]): BoolAlgebraM[R1, E1, B] =
    BoolAlgebraM(run.flatMap(_.flatMapM(f(_).run)))

  final def implies[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraM[R1, E1, A1]): BoolAlgebraM[R1, E1, A1] =
    BoolAlgebraM(run.zipWith(that.run)(_ implies _))

  final def isSuccess: ZIO[R, E, Boolean] =
    run.map(_.isSuccess)

  final def map[B](f: A => B): BoolAlgebraM[R, E, B] =
    flatMap(f andThen success)
}

object BoolAlgebraM {

  final def failure[A](a: A): BoolAlgebraM[Any, Nothing, A] =
    BoolAlgebraM(ZIO.succeed(BoolAlgebra.failure(a)))

  final def fromEffect[R, E, A](effect: ZIO[R, E, A]): BoolAlgebraM[R, E, A] =
    BoolAlgebraM(effect.map(BoolAlgebra.success))

  final def success[A](a: A): BoolAlgebraM[Any, Nothing, A] =
    BoolAlgebraM(ZIO.succeed(BoolAlgebra.success(a)))
}
