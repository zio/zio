package zio.test

import zio.test.Assertion._

object TestAnnotationMapSpec extends DefaultRunnableSpec {

  def spec = suite("TestAnnotationMapSpec")(
    test("get retrieves the annotation of the specified type") {
      val annotations = TestAnnotationMap.empty.annotate(TestAnnotation.ignored, 1)
      assert(annotations.get(TestAnnotation.ignored))(equalTo(1)) &&
      assert(annotations.get(TestAnnotation.retried))(equalTo(0))
    }
  )
}
