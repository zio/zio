package zio.test

import zio.test.Assertion.equalTo
import zio.test.FailureRenderer.FailureMessage.Fragment._
import zio.test.FailureRenderer.FailureMessage.{ Line, Message }
import zio.test.diff.DiffElement
import zio.test.diff.DiffElement.{ Changed, Deleted, Inserted, Unchanged }

object DiffRendererSpec extends ZIOBaseSpec {

  def spec = suite("DiffRendererSpec")(
    testCase("single change")(C("foo", "bar"))(plain("[-") + red("foo") + plain("+") + blue("bar") + plain("]")),
    testCase("deleted - unchanged - inserted")(D("foo "), U("bar"), I(" foo"))(
      plain("[-") + red("foo ") + plain("]") + green("bar") + plain("[+") + red(" foo") + plain("]")
    ),
    testCase("multiline diff")(D("foo "), U("\nbar"), I(" foo"))(
      plain("[-") + red("foo ") + plain("]"),
      green("bar") + plain("[+") + red(" foo") + plain("]")
    )
  )

  private def U(t: String)            = Unchanged(t)
  private def C(a: String, b: String) = Changed(a, b)
  private def I(t: String)            = Inserted(t)
  private def D(t: String)            = Deleted(t)

  private def testCase(l: String)(diffs: DiffElement*)(expected: Line*) =
    test(l) {
      assert(DiffRenderer.renderDiff(diffs.toVector))(equalTo(Message(expected.toVector)))
    }
}
