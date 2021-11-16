package zio

import zio.test.TestAspect.ignore
import zio.test._

object ZEnvironmentIssuesSpec extends DefaultRunnableSpec {

  def spec = suite("ZEnvironmentIssuesSpec")(
    test("Providing a subtype") {
      trait Animal
      trait Dog extends Animal

      // Accesses an Animal
      val zio: URIO[Animal, Animal] = ZIO.service[Animal]

      // Provides a Dog
      val dog: Dog                         = new Dog {}
      val dogService: UServiceBuilder[Dog] = ZServiceBuilder.succeed(dog)

      zio.provideServices(dogService).map { result =>
        assertTrue(result == dog)
      }
    } @@ ignore,
    test("tags") {
      def tagForThing[A](value: A)(implicit tag: Tag[A]): Tag[A] = tag
      assertTrue(tagForThing(Clock.ClockLive).tag <:< Tag[Clock].tag)
    }
  )
}
