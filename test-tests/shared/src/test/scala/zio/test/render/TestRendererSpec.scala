package zio.test.render

import zio.test.Assertion._
import zio.test.{suite, test, DefaultRunnableSpec}

object TestRendererSpec extends DefaultRunnableSpec {

  def spec = suite("TestRenderer")(
  test("renderAssertionResult should correctly render assertion result") {
      val assertionResult: TestTrace[Boolean] = TestTrace(
        List(
          AssertionResult.Value(true, "Assertion 1"),
          AssertionResult.Value(false, "Assertion 2")
        ),
        Some(GenFailureDetails(List("Input A", "Input B"), List("Shrunken Input A", "Shrunken Input B"), 3))
      )

      val expectedResult = Message("Mocked rendered assertion result")

      val testRenderer = new TestRenderer {}
      val renderedResult = testRenderer.renderAssertionResult(assertionResult, 0)

      assert(renderedResult)(equalTo(expectedResult))
    }
  )
}