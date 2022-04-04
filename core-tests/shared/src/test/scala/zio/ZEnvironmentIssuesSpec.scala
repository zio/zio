package zio

import zio.test._
import zio.test.TestAspect._

object ZEnvironmentIssuesSpec extends ZIOSpecDefault {

  def spec = suite("ZEnvironmentIssuesSpec")(
    test("Providing a subtype") {
      trait Animal
      trait Dog extends Animal

      // Accesses an Animal
      val zio: URIO[Animal, Animal] = ZIO.service[Animal]

      // Provides a Dog
      val dog: Dog                = new Dog {}
      val dogService: ULayer[Dog] = ZLayer.succeed(dog)

      zio.provideLayer(dogService).map { result =>
        assertTrue(result == dog)
      }
    } @@ ignore,
    test("tags") {
      def tagForThing[A](value: A)(implicit tag: CompositeTag[A]): CompositeTag[A] = {
        val _ = value
        tag
      }
      assertTrue(tagForThing(Clock.ClockLive).tag <:< CompositeTag[Clock].tag)
    } @@ exceptScala3
  )
}
