package zio.test

import zio._

// Regression tests for https://github.com/zio/zio/issues/8668
// assertTrue (and other TestResult-returning expressions) should compile
// when used directly as the body of ZIO#flatMap callbacks.
object TestResultFlatMapSpec extends ZIOSpecDefault {

  def spec = suite("TestResult in flatMap (issue #8668)")(
    test("assertTrue compiles and succeeds when used directly in flatMap") {
      val foo = ZIO.succeed(1)
      foo.flatMap(result => assertTrue(result == 1))
    },
    test("assertTrue compiles and fails correctly when used directly in flatMap") {
      val foo = ZIO.succeed(1)
      foo.flatMap(result => assertTrue(result == 2))
    } @@ TestAspect.failing,
    test("assertTrue works in flatMap chain") {
      for {
        x <- ZIO.succeed(42)
        _ <- ZIO.succeed(x).flatMap(n => assertTrue(n == 42))
        y <- ZIO.succeed("hello")
      } yield assertTrue(y == "hello")
    }
  )
}
