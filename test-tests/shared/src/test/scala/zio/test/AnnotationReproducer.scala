package zio.test

import zio.test.TestAspect._

object AnnotationReproducer extends ZIOSpecDefault {
  override def spec = suite("Suite1")(
    test("a")(assertTrue(true)),
    test("b")(assertTrue(false)) @@ tag("Important"),
    test("c")(assertTrue(false)) @@ ignore,
  ) @@ timed
}
