package zio.test.magnolia

import java.time.{Instant, LocalDate, LocalDateTime}
import java.util.UUID
import zio._
import zio.test.Assertion._
import zio.test.GenUtils._
import zio.test.Sized
import zio.test._
import zio.test.magnolia.DeriveGen._

object DeriveGenSpec extends DefaultRunnableSpec {

  final case class Person(name: String, age: Int)

  val genPerson: Gen[Has[Random] with Has[Sized], Person] = DeriveGen[Person]

  sealed trait Color

  object Color {
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color
  }

  val genColor: Gen[Has[Random] with Has[Sized], Color] = DeriveGen[Color]

  sealed trait NonEmptyList[+A] { self =>
    def foldLeft[S](s: S)(f: (S, A) => S): S =
      self match {
        case NonEmptyList.Cons(h, t) => t.foldLeft(f(s, h))(f)
        case NonEmptyList.Single(h)  => f(s, h)
      }
    def length: Int =
      foldLeft(0)((s, _) => s + 1)
  }

  object NonEmptyList {
    final case class Cons[+A](head: A, tail: NonEmptyList[A]) extends NonEmptyList[A]
    final case class Single[+A](value: A)                     extends NonEmptyList[A]

    implicit def deriveGen[A: DeriveGen]: DeriveGen[NonEmptyList[A]] = DeriveGen.gen
  }

  def genNonEmptyList[A](implicit ev: DeriveGen[A]): Gen[Has[Random] with Has[Sized], NonEmptyList[A]] =
    DeriveGen[NonEmptyList[A]]

  def assertDeriveGen[A: DeriveGen]: TestResult = assertCompletes

  def spec = suite("DeriveGenSpec")(
    suite("derivation")(
      testM("case classes can be derived") {
        checkSample(genPerson)(isGreaterThan(1), _.distinct.length)
      },
      testM("sealed traits can be derived") {
        checkSample(genColor)(equalTo(3), _.distinct.length)
      },
      testM("recursive types can be derived") {
        check(genNonEmptyList[Int])(as => assert(as.length)(isGreaterThan(0)))
      }
    ),
    suite("instances")(
      test("boolean")(assertDeriveGen[Boolean]),
      test("byte")(assertDeriveGen[Byte]),
      test("char")(assertDeriveGen[Char]),
      test("double")(assertDeriveGen[Double]),
      test("float")(assertDeriveGen[Float]),
      test("function")(assertDeriveGen[Int => Int]),
      test("int")(assertDeriveGen[Int]),
      test("iterable")(assertDeriveGen[Iterable[Int]]),
      test("list")(assertDeriveGen[List[Int]]),
      test("long")(assertDeriveGen[Long]),
      test("map")(assertDeriveGen[Map[Int, Int]]),
      test("option")(assertDeriveGen[Option[Int]]),
      test("partialFunction")(assertDeriveGen[PartialFunction[Int, Int]]),
      test("seq")(assertDeriveGen[Seq[Int]]),
      test("set")(assertDeriveGen[Set[Int]]),
      test("short")(assertDeriveGen[Short]),
      test("string")(assertDeriveGen[String]),
      test("tuple2")(assertDeriveGen[(Int, Int)]),
      test("tuple3")(assertDeriveGen[(Int, Int, Int)]),
      test("tuple4")(assertDeriveGen[(Int, Int, Int, Int)]),
      test("tuple5")(assertDeriveGen[(Int, Int, Int, Int, Int)]),
      test("tuple6")(assertDeriveGen[(Int, Int, Int, Int, Int, Int)]),
      test("tuple7")(assertDeriveGen[(Int, Int, Int, Int, Int, Int, Int)]),
      test("tuple8")(assertDeriveGen[(Int, Int, Int, Int, Int, Int, Int, Int)]),
      test("tuple9")(assertDeriveGen[(Int, Int, Int, Int, Int, Int, Int, Int, Int)]),
      test("tuple10")(assertDeriveGen[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)]),
      test("unit")(assertDeriveGen[Unit]),
      test("uuid")(assertDeriveGen[UUID]),
      test("vector")(assertDeriveGen[Vector[Int]]),
      test("instant")(assertDeriveGen[Instant]),
      test("localDateTime")(assertDeriveGen[LocalDateTime]),
      test("localDate")(assertDeriveGen[LocalDate]),
      test("bigDecimal")(assertDeriveGen[BigDecimal])
    ),
    suite("shrinking")(
      testM("derived generators shrink to smallest value") {
        checkShrink(genPerson)(Person("", 0))
      }
    )
  )
}
