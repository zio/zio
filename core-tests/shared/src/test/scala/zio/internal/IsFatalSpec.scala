package zio.internal

import zio.test.Assertion._
import zio.test._
import zio.ZIOBaseSpec

object IsFatalSpec extends ZIOBaseSpec {
  def spec =
    suite("IsFatal")(
      suite("empty")(
        test("apply(VirtualMachineError)") {
          val isFatal = IsFatal.empty
          assert(isFatal(new OutOfMemoryError()))(isTrue) &&
          assert(isFatal(new StackOverflowError()))(isTrue)
        },
        test("apply(Non-VirtualMachineError)") {
          val isFatal = IsFatal.empty
          assert(isFatal(new RuntimeException()))(isFalse) &&
          assert(isFatal(new IllegalArgumentException()))(isFalse)
        },
        test("|") {
          val tag = IsFatal(classOf[RuntimeException])
          assert(IsFatal.empty | tag)(equalTo(tag)) &&
          assert(tag | IsFatal.empty)(equalTo(tag))
        }
      ),
      suite("Tag")(
        test("matches assignable types") {
          val isFatal = IsFatal(classOf[RuntimeException])
          assert(isFatal(new RuntimeException()))(isTrue) &&
          assert(isFatal(new IllegalArgumentException()))(isTrue) &&
          assert(isFatal(new Exception()))(isFalse)
        }
      ),
      suite("Both")(
        test("matches any of the two") {
          val first    = IsFatal(classOf[RuntimeException])
          val second   = IsFatal(classOf[Exception])
          val combined = first | second
          assert(combined(new RuntimeException()))(isTrue) &&
          assert(combined(new IllegalArgumentException()))(isTrue) &&
          assert(combined(new Exception()))(isTrue) &&
          assert(combined(new Throwable()))(isFalse)
        }
      )
    )
}
