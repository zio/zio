package zio

import zio.test.{DefaultRunnableSpec, ZSpec, assertCompletes}

object ImportlessSpec extends DefaultRunnableSpec {
  val spec: ZSpec[Environment, Failure] = suite("Suite")(
    test("This is a test without imports")(assertCompletes),
    testM("This is an effectful test without imports")(ZIO.succeed(assertCompletes))
  )
}
