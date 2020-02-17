package zio.test.diff

import zio.test.Assertion._
import zio.test._
import zio.test.diff.DiffElement._

object StringDifferSpec extends ZIOBaseSpec {
  override def spec = suite("diff when")(
    testCase("nothing matches")("foo", "bar")(C("foo", "bar")),
    testCase("word moved")("foo bar", "bar foo")(D("foo "), U("bar"), I(" foo")),
    testCase("char deleted in the middle")("foo-bar", "foobar")(U("foo"), D("-"), U("bar")),
    testCase("word added to the middle")("foo bar", "foo moo bar")(U("foo "), I("moo "), U("bar")),
    testCase("char doubled at the end")("foo bar", "foo barr")(U("foo bar"), I("r")),
    testCase("char doubled at the beginning")("foo bar", "ffoo bar")(U("f"), I("f"), U("oo bar"))
  )

  private def U(t: String)            = Unchanged(t)
  private def C(a: String, b: String) = Changed(a, b)
  private def I(t: String)            = Inserted(t)
  private def D(t: String)            = Deleted(t)

  private def testCase(l: String)(a: String, b: String)(expected: DiffElement*) =
    test(l) {
      assert(StringDiffer.default.diff(a, b))(equalTo(Vector(expected: _*)))
    }
}
