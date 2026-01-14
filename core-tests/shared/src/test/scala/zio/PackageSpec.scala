package zio

import zio.test.Assertion._
import zio.test._

object PackageSpec extends ZIOBaseSpec {
  def spec =
    suite("nonFatal")(
      test("identifies non-fatal exceptions") {
        assert(nonFatal(new RuntimeException()))(isTrue) &&
        assert(nonFatal(new IllegalArgumentException()))(isTrue) &&
        assert(nonFatal(new Exception()))(isTrue)
      },
      test("identifies fatal exceptions") {
        assert(nonFatal(new OutOfMemoryError()))(isFalse) &&
        assert(nonFatal(new StackOverflowError()))(isFalse)
      }
    )
}
