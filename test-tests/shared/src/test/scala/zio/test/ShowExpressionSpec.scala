package zio.test

import zio.test.Assertion._

object ShowExpressionSpec extends ZIOBaseSpec {
  val fooVal = "foo"
  case class SomeData(foo: String, more: Seq[Nested], nested: Nested)
  case class Nested(bar: Int)
  class SomeClass(val str: String = "")

  override def spec: ZSpec[Annotations, Any] = suite("ShowExprSpec")(
    test("Some(1)", showExpression(Some(1)), "Some(1)"),
    test("Some(Right(1))", showExpression(Some(Right(1))), "Some(Right(1))"),
    test("class member val", showExpression(fooVal), "fooVal"),
    test("tuple", showExpression(("foo", true, 1, 1.2, fooVal)), "(\"foo\", true, 1, 1.2, fooVal)"),
    test(
      "nested case classes",
      showExpression(SomeData("foo barvaz", Seq(Nested(1), Nested(2)), Nested(3))),
      "SomeData(\"foo barvaz\", Seq(Nested(1), Nested(2)), Nested(3))"
    ),
    test("multiline string literal", showExpression("foo\nbar\n\nbaz"), "\"foo\\nbar\\n\\nbaz\""),
    test("throw new + upcast", showExpression((throw new RuntimeException): String), "throw new RuntimeException()"),
    test(
      "match clause",
      showExpression(fooVal match { case "foo" => fooVal; case _ => new SomeClass("bar") }),
      """fooVal match {
        |  case "foo" => fooVal
        |  case _ => new SomeClass("bar")
        |}""".stripMargin
    ),
    test(
      "if else",
      showExpression(if (fooVal == "foo") Nested(1) else new SomeClass("bar")),
      """if (fooVal.==("foo"))
        |  Nested(1)
        |else
        |  new SomeClass("bar")""".stripMargin
    ),
    test("method with default arg", showExpression(methodWithDefaultArgs()), "methodWithDefaultArgs()"),
    test("constructor with default arg", showExpression(new SomeClass()), "new SomeClass()"),
    test(
      "single arg anon function with underscores",
      showExpression(Option("").filterNot(_.isEmpty)),
      "Option(\"\").filterNot((_.isEmpty()))"
    ),
    test(
      "two arg anon function with underscores",
      showExpression(List(1, 2, 3).reduce(_ + _)),
      "List(1, 2, 3).reduce((_.+(_)))"
    ),
    test(
      "two arg anon function with named args",
      showExpression(List(1, 2, 3).reduce((a, b) => a + b)),
      "List(1, 2, 3).reduce(((a, b) => a.+(b)))"
    )
  ) @@ TestAspect.exceptDotty

  def methodWithDefaultArgs(arg: String = "") = arg

  def test(desc: String, actual: String, expected: String): ZSpec[Any, Nothing] =
    test(desc) {
      val code = actual
      assert(code)(equalTo(expected))
    }
}
