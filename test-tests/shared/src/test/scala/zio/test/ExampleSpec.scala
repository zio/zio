package zio.test

import zio.test.Assertion._

object ExampleSpec extends DefaultRunnableSpec {

  def spec = suite("ExampleSpec")(
    test("test") {
      val result = "Hello,\nWorld!"
      assert(result, equalTo("Nothing"))
    },
    test("test1") {
      assert(Some(Left(1)), isSome(isLeft(equalTo(2))))
    }
  )
}
