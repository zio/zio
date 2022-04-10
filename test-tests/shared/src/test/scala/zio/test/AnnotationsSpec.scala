package zio.test

import zio.ZIO
import zio.test.Assertion._

object AnnotationsSpec extends ZIOBaseSpec {

  def spec = suite("annotationsSpec")(
    test("withAnnotation executes specified effect with an empty annotation map") {
      for {
        _   <- Annotations.annotate(count, 1)
        a   <- Annotations.get(count)
        map <- Annotations.withAnnotation(ZIO.unit <* Annotations.annotate(count, 2)).map(_._2)
        b    = map.get(count)
      } yield assert(a)(equalTo(1)) && assert(b)(equalTo(2))
    },
    test("withAnnotation returns annotation map with result") {
      for {
        map <- Annotations
                 .withAnnotation(Annotations.annotate(count, 3) *> ZIO.fail(TestFailure.fail("fail")))
                 .flip
                 .map(_.annotations)
        c = map.get(count)
      } yield assert(c)(equalTo(3))
    }
  )

  val count: TestAnnotation[Int] = TestAnnotation[Int]("count", 0, _ + _)
}
