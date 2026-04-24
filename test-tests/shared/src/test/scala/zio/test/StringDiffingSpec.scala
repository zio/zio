package zio.test

import zio.internal.macros.StringUtils.StringOps
import zio.test.diff.{Diff, DiffResult}

object StringDiffingSpec extends ZIOBaseSpec {

  def diff(a: String, b: String): DiffResult = Diff[String].diff(a, b)

  def spec = suite("SmartAssertionSpec")(
    test("simple difference") {
      val result: DiffResult = diff("a", "b")
      val expected           = DiffResult.Different("a", "b")
      assertTrue(result == expected)
    },
    test("identical") {
      val result: DiffResult = diff("a", "a")
      val expected           = DiffResult.Identical("a")
      assertTrue(result == expected)
    },
    test("word diffing") {
      val result: DiffResult = diff(
        "all my life I've been searching for something",
        "all my life I've been hunting for beans"
      )
      val expected = "all my life I've been -searching+hunting for -something+beans"
      assertTrue(result.render.unstyled == expected)
    },
    test("word diffing") {
      val result: DiffResult = diff(
        "thiswouldbebestifitwereassimpleasitcouldbe",
        "thiswouldbeworstifitwereascomplexasitcouldbewow"
      )
      val expected = "thiswouldbe-b-e+w+o+rstifitwereas-s-i+c+omple+xasitcouldbe+w+o+w"
      assertTrue(result.render.unstyled == expected)
    }
  )
}
