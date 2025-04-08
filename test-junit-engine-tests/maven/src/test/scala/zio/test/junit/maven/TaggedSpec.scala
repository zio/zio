package zio.test.junit.maven

import zio.test.junit._
import zio.test._
import zio.test.Assertion._

object TaggedSpec extends ZIOSpecDefault {
  override def spec = suite("TaggedSpec")(
    test("should run for tag tagged") {
      assert(12)(equalTo(12))
    },
    test("should run for tag a and tagged") {
      assert(12)(equalTo(12))
    } @@ TestAspect.tag("a"),
    test("should run for tag b and tagged") {
      assert(12)(equalTo(12))
    } @@ TestAspect.tag("b"),
    test("should run for tag a b tagged") {
      assert(12)(equalTo(12))
    } @@ TestAspect.tag("a")  @@ TestAspect.tag("b"),
  ) @@ TestAspect.tag("tagged")
}
