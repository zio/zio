package zio.test

import scala.annotation.tailrec

import zio.random._
import zio.test.Assertion._

object PolySpec extends DefaultRunnableSpec {

  type Testing[+A] = Gen[Random with Sized, A]

  val genInt: TypeWith[Testing]    = TypeWith(Gen.anyInt)
  val genString: TypeWith[Testing] = TypeWith(Gen.anyString)

  val genPoly: Gen[Random, TypeWith[Testing]] = Gen.elements(genInt, genString)

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

  def genValue(t: TypeWith[Testing]): Gen[Random with Sized, Expr[t.Type]] =
    t.evidence.map(Value(_))

  def genMapping(t: TypeWith[Testing]): Gen[Random with Sized, Expr[t.Type]] =
    Gen.suspend {
      genPoly.flatMap { t0 =>
        genExpr(t0).flatMap { expr =>
          val genFunction: Testing[t0.Type => t.Type] = Gen.function(t.evidence)
          val genExpr1: Testing[Expr[t.Type]]         = genFunction.map(f => Mapping(expr, f))
          genExpr1
        }
      }
    }

  def genExpr(t: TypeWith[Testing]): Gen[Random with Sized, Expr[t.Type]] =
    Gen.oneOf(genMapping(t), genValue(t))

  def spec = suite("PolySpec")(
    testM("polymorphic generators can be created") {
      check(genPoly.flatMap(genExpr(_))) { expr =>
        assert(eval(fuse(expr)))(equalTo(eval(expr)))
      }
    }
  )
}
