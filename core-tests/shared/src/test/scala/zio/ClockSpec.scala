package zio

import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, TestAspect, ZSpec, assert}

object ClockSpec extends DefaultRunnableSpec {
  override def aspects: List[TestAspect[Nothing, Any, Nothing, Any]] = List.empty

  override def spec = test("hi") {
    assert(1)(equalTo(1))
  }
}
