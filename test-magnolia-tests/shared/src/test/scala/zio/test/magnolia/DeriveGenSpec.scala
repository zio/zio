package zio.test.magnolia

import java.util.UUID

import zio.random.Random
import zio.test.Assertion._
import zio.test.GenUtils._
import zio.test.Sized
import zio.test._
import zio.test.magnolia.DeriveGen._

object DeriveGenSpec extends DefaultRunnableSpec {

  final case class Person(name: String, age: Int)

  val genPerson: Gen[Random with Sized, Person] = DeriveGen[Person]

  sealed trait Color
  case object Red   extends Color
  case object Green extends Color
  case object Blue  extends Color

  val genColor: Gen[Random with Sized, Color] = DeriveGen[Color]

  def assertDeriveGen[A: DeriveGen]: TestResult = assertCompletes

  def spec = suite("DeriveGenSpec")(
    suite("derivation")(
      testM("case classes can be derived") {
        checkSample(genPerson)(isGreaterThan(1), _.distinct.length)
      },
      testM("sealed traits can be derived") {
        checkSample(genColor)(equalTo(3), _.distinct.length)
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
      test("unit")(assertDeriveGen[Unit]),
      test("uuid")(assertDeriveGen[UUID]),
      test("vector")(assertDeriveGen[Vector[Int]])
    ),
    suite("shrinking")(
      testM("derived generators shrink to smallest value") {
        checkShrink(genPerson)(Person("", 0))
      },
    )
  )
}
