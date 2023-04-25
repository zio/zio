package zio

import zio.test.Assertion._
import zio.test.TestAspect.exceptScala3
import zio.test._

object TaggedSpec extends ZIOBaseSpec {

  def spec: Spec[Any, TestFailure[Any]] = suite("TaggedSpec")(
    test("tags can be derived for polymorphic services") {
      val result = typeCheck {
        """
            trait Producer[R, K, V]

            def test[R: Tag, K: Tag, V: Tag]: Boolean = {
              val _ = implicitly[Tag[Producer[R, K, V]]]
              true
            }
            """
      }
      assertZIO(result)(isRight(isUnit))
    } @@ exceptScala3 @@ TestAspect.exceptNative
  )
}
