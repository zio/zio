package zio

import zio.test.Assertion.equalTo
import zio.test.assert

object UnifiedSpec extends ZIOBaseSpec {

  def spec = suite("UnifiedSpec")(
    suite("toThrowable")(
      test("preserves original exception type") {
        val t1 = unifiedThrowable(new RuntimeException("foo"))
        val t2 = unifiedThrowable(new IllegalArgumentException("bar"))
        assert(t1.toString)(equalTo("java.lang.RuntimeException: foo")) &&
        assert(t2.toString)(equalTo("java.lang.IllegalArgumentException: bar"))
      },
      test("preserves stack trace in `printStackTrace`") {
        val boom = generateRuntimeBoom
        // TODO - is it ok to use `exceptionHasTrace` from another test suite, which under the hood calls `printStackTrace`?
        assert(boom)(zio.StackTracesSpec.exceptionHasTrace {
          """java.lang.RuntimeException: boom
            |	at zio.UnifiedSpec$.generateRuntimeBoom
            |	at zio.UnifiedSpec$.$anonfun$spec
            |""".stripMargin
        })
      }
    )
  )

  def unifiedThrowable(defect: Throwable): Throwable =
    Cause.die(defect).unified.head.toThrowable

  def generateRuntimeBoom: Throwable = {
    val defect = new RuntimeException("boom")
    unifiedThrowable(defect)
  }
}
