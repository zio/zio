package zio.test

import zio.test.Assertion._
import zio.test.TestOnly._
import zio.test.TestOnlySpecData._
import zio.test.TestUtils.succeeded

object TestOnlySpec
    extends ZIOBaseSpec(
      suite("TestOnlySpec")(
        testM("ignores all tests except one matching the given label") {
          for {
            passed1 <- succeeded(mixedSpec.only(_ == passingTest))
            passed2 <- succeeded(mixedSpec.only(_ == failingTest))
          } yield assert(passed1, isTrue) && assert(passed2, isFalse)
        },
        testM("ignores all tests except ones in the suite matching the given label") {
          for {
            passed1 <- succeeded(mixedSpec.only(_ == passingSuite))
            passed2 <- succeeded(mixedSpec.only(_ == failingSuite))
          } yield assert(passed1, isTrue) && assert(passed2, isFalse)
        },
        testM("runs everything if top suite label given") {
          for {
            passed <- succeeded(mixedSpec.only(_ == "suite"))
          } yield assert(passed, isFalse)
        },
        testM("fails if no such label") {
          for {
            passed <- succeeded(mixedSpec.only(_ == "no such label"))
          } yield assert(passed, isFalse)
        }
      )
    )

object TestOnlySpecData {
  val failingTest  = "failing test"
  val failingSuite = "failing suite"
  val passingTest  = "passing test"
  val passingSuite = "passing suite"
  val mixedSpec = suite("suite")(
    suite(failingSuite)(test(failingTest) {
      assert(1, equalTo(2))
    }),
    suite(passingSuite)(test(passingTest) {
      assert(1, equalTo(1))
    })
  )

  val passingSpec = suite("suite")(
    test(passingTest + "1") {
      assert(1, equalTo(1))
    },
    test(passingTest + "2") {
      assert(1, equalTo(1))
    }
  )
}
