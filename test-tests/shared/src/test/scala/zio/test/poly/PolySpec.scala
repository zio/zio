package zio.test.poly

import zio.Has
import zio.Random._
import zio.test.Assertion._
import zio.test._

import scala.annotation.tailrec

object PolySpec extends DefaultRunnableSpec {

  sealed trait Expr[+A]

  final case class Value[+A](a: A)                          extends Expr[A]
  final case class Mapping[A, +B](expr: Expr[A], f: A => B) extends Expr[B]

  def eval[A](expr: Expr[A]): A = expr match {
    case Value(a)      => a
    case Mapping(x, f) => f(eval(x))
  }

  @tailrec
  def fuse[A](expr: Expr[A]): Expr[A] = expr match {
    case Mapping(Mapping(x, f), g) => fuse(Mapping(x, f andThen g))
    case x                         => x
  }

  def genValue(t: GenPoly): Gen[Has[Random] with Has[Sized], Expr[t.T]] =
    t.genT.map(Value(_))

  def genMapping(t: GenPoly): Gen[Has[Random] with Has[Sized], Expr[t.T]] =
    Gen.suspend {
      GenPoly.genPoly.flatMap { t0 =>
        genExpr(t0).flatMap { expr =>
          val genFunction: Gen[Has[Random] with Has[Sized], t0.T => t.T] = Gen.function(t.genT)
          val genExpr1: Gen[Has[Random] with Has[Sized], Expr[t.T]]      = genFunction.map(f => Mapping(expr, f))
          genExpr1
        }
      }
    }

  def genExpr(t: GenPoly): Gen[Has[Random] with Has[Sized], Expr[t.T]] =
    Gen.oneOf(genMapping(t), genValue(t))

  def spec: ZSpec[Environment, Failure] = suite("PolySpec")(
    testM("map fusion") {
      check(GenPoly.genPoly.flatMap(genExpr(_)))(expr => assert(eval(fuse(expr)))(equalTo(eval(expr))))
    }
  )
}
