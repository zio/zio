package zio.test

import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.TestAspect.ignore

object DefaultMutableRunnableSpecSpec extends DefaultMutableRunnableSpec {

  test("top level test") {
    assert(0)(equalTo(0))
  }

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
      assert(1)(equalTo(123))
    } @@ ignore

    suite("nested suite") {
      test("test in nested suite") {
        assert(3)(equalTo(3))
      }
    }
  }

  suite("ignored suite") {

    test("failing test 2") {
      assert(2)(equalTo(123))
    }
  } @@ ignore

  test("last") {
//    test("test in test must be commented not to fail") {
//      assert(0)(equalTo(0))
//    }
    assert(0)(equalTo(0))
  }

}
