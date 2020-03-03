package zio.test
import zio.test.Assertion.equalTo
import zio.test.MessageMarkup.Fragment._
import zio.test.MessageMarkup._

object MessageMarkupSpec extends ZIOBaseSpec {
  def spec = suite("FailureMessageSpec")(
    suite("Line.splitOnLineBreaks")(
      splitTest("no line breaks")(green("foo") + plain("bar") + blue(" buz"))(
        green("foo") + plain("bar") + blue(" buz")
      ),
      splitTest("fragment starts with \\n")(green("\nfoo") + plain("bar") + blue(" buz"))(
        plain("").toLine,
        green("foo") + plain("bar") + blue(" buz")
      ),
      splitTest("middle fragment starts with \\n")(plain("boo") + green("\nfoo") + plain("bar") + blue(" buz"))(
        plain("boo") + plain(""),
        green("foo") + plain("bar") + blue(" buz")
      ),
      splitTest("middle fragment ends with \\n")(green("foo") + plain("bar") + blue(" buz\n") + yellow("boo"))(
        green("foo") + plain("bar") + blue(" buz"),
        yellow("boo").toLine
      )
    ),
    suite("fragment()")(
      test("escapes color codes") {
        assert(Fragment.blue(Fragment.blue("foo").render))(equalTo(Fragment.blue("\\e[34mfoo\\e[0m")))
      },
      test("strips color if text is empty") {
        assert(Fragment.red(""))(equalTo(Fragment.plain("")))
      }
    )
  )

  private def splitTest(l: String)(ln: Line)(expected: Line*) =
    test(l) {
      assert(ln.withOffset(123).splitOnLineBreaks)(
        equalTo(expected.map(_.withOffset(123)).toVector)
      )
    }
}
