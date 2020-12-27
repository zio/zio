package zio.test

import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.TestAspect.ignore

object MutableRunnableSpecSpec extends MutableRunnableSpec {

  suite("first") {

    test("simple") {
      assert(1)(equalTo(1))
    }

    testM("effect") {
      for {
        res <- ZIO.succeed(10)
      } yield assert(res)(equalTo(10))
    }
  }

  suite("second") {

    test("simple 2") {
      assert(2)(equalTo(2))
    }

    test("ignoring this test") {
      assert(1)(equalTo(1))
    } @@ ignore
  }

}
