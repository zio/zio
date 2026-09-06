package zio.test

import java.nio.file.Paths

object SmartAssertionJavaStaticSpec extends ZIOBaseSpec {
  def spec: Spec[Any, Nothing] =
    suite("Java static method calls")(
      test("Paths.get") {
        assertTrue(Paths.get("a") == Paths.get("a"))
      },
      test("Math.abs") {
        assertTrue(java.lang.Math.abs(-1) == 1)
      },
      test("Integer.parseInt") {
        assertTrue(java.lang.Integer.parseInt("42") == 42)
      },
      test("generic static method") {
        assertTrue(
          java.util.Collections.emptyList[String]().isEmpty
        )
      },
      test("nested inside boolean operators") {
        assertTrue(
          java.lang.Math.abs(-1) == 1 &&
            java.lang.Integer.parseInt("42") == 42
        )
      }
    )
}
