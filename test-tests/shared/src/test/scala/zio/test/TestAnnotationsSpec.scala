package zio.test

import zio.duration._
import zio.test.Assertion._

object TestAnnotationsSpec extends ZIOBaseSpec {

  def spec = suite("TestAspectsSpec")(
    testM("make constructs a new TestAnnotations instance") {
      assertM(TestAnnotations.testAnnotationMap, equalTo(TestAnnotationMap.empty))
    },
    testM("annotate appends the specified annotation to the test annotation map") {
      for {
        _      <- TestAnnotations.annotate(TestAnnotation.Timing, 1.second)
        _      <- TestAnnotations.annotate(TestAnnotation.Timing, 1.second)
        result <- TestAnnotations.get(TestAnnotation.Timing)
      } yield assert(result, equalTo(2.seconds))
    },
    testM("get retrieves the default value if there is no annotation of the specified type") {
      assertM(TestAnnotations.get(TestAnnotation.Timing), equalTo(Duration.Zero))
    }
  )
}
