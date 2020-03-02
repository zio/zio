package zio.test

import zio.test.Assertion.equalTo
import zio.test.MessageMarkup.Fragment._
import zio.test.MessageMarkup._
import zio.test.diff.DiffComponent
import zio.test.diff.DiffComponent.{ Changed, Deleted, Inserted, Unchanged }

object DiffRendererSpec extends ZIOBaseSpec {

  def spec = suite("DiffRendererSpec")(
    testCase("single change")(C("foo", "bar"))(plain("[-") + red("foo") + plain("+") + blue("bar") + plain("]")),
    testCase("deleted - unchanged - inserted")(D("foo "), U("bar"), I(" foo"))(
      plain("[-") + red("foo ") + plain("]") + green("bar") + plain("[+") + red(" foo") + plain("]")
    ),
    testCase("multiline diff")(D("foo "), U("\nbar"), I(" foo"))(
      plain("[-") + red("foo ") + plain("]") + green("\\n"),
      green("bar") + plain("[+") + red(" foo") + plain("]")
    ),
    testCase("multiline diff 2")(U("Hello,"), C(" ", "\n"), U("World!"))(
      green("Hello,") + plain("[-") + red(" ") + plain("+") + blue("\\n"),
      plain("]") + green("World!")
    )
  )

  private def U(t: String)            = Unchanged(t)
  private def C(a: String, b: String) = Changed(a, b)
  private def I(t: String)            = Inserted(t)
  private def D(t: String)            = Deleted(t)

  private def testCase(l: String)(diffs: DiffComponent*)(expected: Line*) =
    test(l) {
      assert(DiffRenderer.renderDiff(diffs.toVector))(equalTo(Message(expected.toVector)))
    }
}
