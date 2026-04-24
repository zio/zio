package zio.test.magnolia

import zio._
import zio.test.Assertion._
import zio.test.GenUtils._
import zio.test.magnolia.DeriveGen._
import zio.test._
import zio.test.TestAspect.samples

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID

object DeriveGenSpec_3 extends ZIOBaseSpec {

  sealed trait Color
  object Color {
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color
  }

  val genColor: Gen[Any, Color] = DeriveGen[Color]

  type UnionType = Color | Boolean
  given DeriveGen[UnionType]            = DeriveGen.unionType[UnionType]
  val anyUnionType: Gen[Any, UnionType] = DeriveGen[UnionType]

  type NestedUnionType = String | UnionType
  given DeriveGen[NestedUnionType]                  = DeriveGen.unionType[NestedUnionType]
  val anyNestedUnionType: Gen[Any, NestedUnionType] = DeriveGen[NestedUnionType]

  def assertDeriveGen[A](implicit ev: DeriveGen[A]): TestResult = assertCompletes

  def spec = suite("DeriveGenSpec_3")(
    suite("union type")(
      test("derivation")(assertDeriveGen[UnionType]),
      test("generates Color values") {
        check(Gen.listOfN(100)(anyUnionType)) { vs =>
          assertTrue(vs.exists(_.isInstanceOf[Color]))
        }
      },
      test("generates Boolean values") {
        check(Gen.listOfN(100)(anyUnionType)) { vs =>
          assertTrue(vs.exists(_.isInstanceOf[Boolean]))
        }
      }
    ),
    suite("nested union type")(
      test("derivation")(assertDeriveGen[NestedUnionType]),
      test("generates Color values") {
        check(Gen.listOfN(100)(anyNestedUnionType)) { vs =>
          assertTrue(vs.exists(_.isInstanceOf[Color]))
        }
      },
      test("generates Boolean values") {
        check(Gen.listOfN(100)(anyNestedUnionType)) { vs =>
          assertTrue(vs.exists(_.isInstanceOf[Boolean]))
        }
      },
      test("generates String values") {
        check(Gen.listOfN(100)(anyNestedUnionType)) { vs =>
          assertTrue(vs.exists(_.isInstanceOf[String]))
        }
      }
    )
  )
}
