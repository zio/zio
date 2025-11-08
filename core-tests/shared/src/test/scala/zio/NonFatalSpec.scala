package zio

import zio.test.Assertion._
import zio.test._

object NonFatalSpec extends ZIOBaseSpec {
  def spec =
    suite("NonFatal.apply")(
      test("identifies non-fatal exceptions") {
        assert(NonFatal(new RuntimeException()))(isTrue) &&
        assert(NonFatal(new IllegalArgumentException()))(isTrue) &&
        assert(NonFatal(new Exception()))(isTrue)
      },
      test("identifies fatal exceptions") {
        assert(NonFatal(new OutOfMemoryError()))(isFalse) &&
        assert(NonFatal(new StackOverflowError()))(isFalse)
      }
    )
}
