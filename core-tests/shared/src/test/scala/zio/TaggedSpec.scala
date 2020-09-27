package zio

import zio.test.Assertion._
import zio.test.TestAspect.exceptDotty
import zio.test._

object TaggedSpec extends ZIOBaseSpec {

  def spec: Spec[Annotations, TestFailure[Any], TestSuccess] = suite("TaggedSpec")(
    testM("tags can be derived for polymorphic services") {
      val result = typeCheck {
        """
            trait Producer[R, K, V]

            def test[R: Tag, K: Tag, V: Tag]: Boolean = {
              val _ = implicitly[Tag[Producer[R, K, V]]]
              true
            }
            """
      }
      assertM(result)(isRight(isUnit))
    } @@ exceptDotty
  )
}
