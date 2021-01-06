package zio.test

import zio.test.Assertion._

object ShowExpressionSpec extends ZIOBaseSpec {
  val fooVal = "foo"
  case class SomeData(foo: String, more: Seq[Nested], nested: Nested)
  case class Nested(bar: Int)

  override def spec: ZSpec[Any, Any] = suite("ShowExprSpec")(
    test("Some(1)", showExpression(Some(1)), "Some(1)"),
    test("Some(Right(1))", showExpression(Some(Right(1))), "Some(Right(1))"),
    test("class member val", showExpression(fooVal), "fooVal"),
    test("tuple", showExpression(("foo", true, 1, 1.2, fooVal)), "(\"foo\", true, 1, 1.2, fooVal)"),
    test(
      "nested case classes",
      showExpression(SomeData("foo barvaz", Seq(Nested(1), Nested(2)), Nested(3))),
      "SomeData(\"foo barvaz\", Seq(Nested(1), Nested(2)), Nested(3))"
    ),
    test("multiline string literal", showExpression("foo\nbar\n\nbaz"), "\"foo\\nbar\\n\\nbaz\"")
  )

  def test(desc: String, actual: String, expected: String): ZSpec[Any, Nothing] =
    zio.test.test(desc) {
      val code = actual
      assert(code)(equalTo(expected))
    }
}
