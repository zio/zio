package zio.test

import zio.test.Assertion._
import zio.ZIO

object AnnotatedSpec extends ZIOBaseSpec {

  def spec = suite("TestAnnotationsSpec")(
    testM("withAnnotation executes specified effect with an empty annotation map") {
      for {
        _   <- Annotated.annotate(count, 1)
        a   <- Annotated.get(count)
        map <- Annotated.withAnnotation(ZIO.unit <* Annotated.annotate(count, 2)).map(_._1)
        b   = map.get(count)
      } yield assert(a, equalTo(1)) && assert(b, equalTo(2))
    },
    testM("withAnnotation returns annotation map with result") {
      for {
        map <- Annotated.withAnnotation(Annotated.annotate(count, 3) *> ZIO.fail("fail")).flip.map(_._1)
        c   = map.get(count)
      } yield assert(c, equalTo(3))
    }
  )

  val count = TestAnnotation[Int]("count", 0, _ + _)
}
