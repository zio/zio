package zio.autowire

import zio.test.{
  Annotations,
  Assertion,
  DefaultRunnableSpec,
  Spec,
  TestAspect,
  TestFailure,
  TestSuccess,
  ZIOSpecDefault
}

object SimpleFailingSharedSpecX extends ZIOSpecDefault {
  def spec: Spec[Annotations, TestFailure[Any], TestSuccess] = zio.test.suite("some suite")(
    test("failing test") {
      zio.test.assert(1)(Assertion.equalTo(2))
    },
    test("passing test") {
      zio.test.assert(1)(Assertion.equalTo(1))
    },
    test("ignored test") {
      zio.test.assert(1)(Assertion.equalTo(2))
    } @@ TestAspect.ignore
  )
}

object SimpleFailingSharedSpecZ extends DefaultRunnableSpec {
  def spec: Spec[Annotations, TestFailure[Any], TestSuccess] = zio.test.suite("some suite")(
    test("failing test") {
      zio.test.assert(1)(Assertion.equalTo(2))
    },
    test("passing test") {
      zio.test.assert(1)(Assertion.equalTo(1))
    },
    test("ignored test") {
      zio.test.assert(1)(Assertion.equalTo(2))
    } @@ TestAspect.ignore
  )
}
