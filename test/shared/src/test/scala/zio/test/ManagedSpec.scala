package zio.test

import scala.concurrent.Future

import zio.{ DefaultRuntime, Ref, ZIO }
import zio.test.Assertion.equalTo
import zio.test.TestUtils.{ forAllTests, label }

object ManagedSpec extends DefaultRuntime {

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
      )
      val executor = TestExecutor.managedSuite[Ref[Int], Any, String, Any](Ref.make(1).toManaged((_.set(0))))
      val results  = executor(spec, ExecutionStrategy.Sequential)
      forAllTests(results)(_.isRight)
    }
}
