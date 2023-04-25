package zio

import zio.test._

object ImportlessSpec extends ZIOBaseSpec {
  val spec = suite("Suite")(
    test("This is a test without imports")(assertCompletes),
    test("This is an effectful test without imports")(ZIO.succeed(assertCompletes))
  ) @@ TestAspect.exceptNative
}
