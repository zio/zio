package zio.test

import scala.concurrent.Future

import zio._
import zio.test.Assertion.equalTo
import zio.test.TestUtils.{ label, succeeded }

object ManagedSpec extends ZIOBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(managedResourcesCanBeUsedAcrossTests, "managed resources can be used across tests")
  )

  def managedResourcesCanBeUsedAcrossTests: Future[Boolean] =
    unsafeRunToFuture {
      val spec = suite("managed suite")(
        testM("first test") {
          for {
            _      <- ZIO.accessM[Ref[Int]](_.update(_ + 1))
            result <- ZIO.accessM[Ref[Int]](_.get)
          } yield assert(result, equalTo(2))
        },
        testM("second test") {
          for {
            _      <- ZIO.accessM[Ref[Int]](_.update(_ + 1))
            result <- ZIO.accessM[Ref[Int]](_.get)
          } yield assert(result, equalTo(3))
        },
        testM("third test") {
          for {
            _      <- ZIO.accessM[Ref[Int]](_.update(_ + 1))
            result <- ZIO.accessM[Ref[Int]](_.get)
          } yield assert(result, equalTo(4))
        }
      ).provideManagedShared(Ref.make(1).toManaged(_.set(-10)))
      succeeded(spec)
    }
}
