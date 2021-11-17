package zio

import zio.test._

object TagsSpec extends DefaultRunnableSpec {

  def spec =
    suite("TagsSpec")(
      suite("ServiceTag")(
        test("ServiceTag works") {
          trait Animal
          trait Dog extends Animal
          val tag  = ServiceTag[Animal]
          val tag2 = ServiceTag[Dog]
          assertTrue(
            tag2.tag <:< tag.tag,
            tag.tag.toString contains "Animal",
            tag2.tag.toString contains "Dog"
          )
        },
        test("ServiceTag works when not an intersection type") {
          typeCheck("ServiceTag[Int]").map { result =>
            assertTrue(result.isRight)
          }
        },
        test("ServiceTag fails to compile when is an intersection type") {
          typeCheck("ServiceTag[Int with String]").map { result =>
            assertTrue(result.isLeft)
          }
        }
      ),
      suite("EnvironmentTag")(
        test("EnvironmentTag works") {
          trait Animal
          trait Dog extends Animal
          val tag  = EnvironmentTag[Animal]
          val tag2 = EnvironmentTag[Dog]
          assertTrue(
            tag2.tag <:< tag.tag,
            tag.tag.toString contains "Animal",
            tag2.tag.toString contains "Dog"
          )
        },
        test("EnvironmentTag works when not an intersection type") {
          typeCheck("EnvironmentTag[Int]").map { result =>
            assertTrue(result.isRight)
          }
        },
        test("EnvironmentTag works with intersection types") {
          typeCheck("EnvironmentTag[Int with String]").map { result =>
            assertTrue(result.isRight)
          }
        }
      )
    )
}
