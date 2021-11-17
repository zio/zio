package zio.test

import zio._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.sequential

object ManagedSpec extends ZIOBaseSpec {

  trait Counter {
    def incrementAndGet: UIO[Int]
  }

  object Counter {

    val live: ServiceBuilder[Nothing, Counter] =
      Ref
        .make(1)
        .toManagedWith(_.set(-10))
        .map { ref =>
          new Counter {
            val incrementAndGet: UIO[Int] = ref.updateAndGet(_ + 1)
          }
        }
        .toServiceBuilder

    val incrementAndGet: URIO[Counter, Int] =
      ZIO.serviceWith(_.incrementAndGet)
  }

  def spec: Spec[Any, TestFailure[Any], TestSuccess] = suite("ManagedSpec")(
    suite("managed shared")(
      suite("first suite")(
        test("first test") {
          assertM(Counter.incrementAndGet)(equalTo(2))
        },
        test("second test") {
          assertM(Counter.incrementAndGet)(equalTo(3))
        }
      ),
      suite("second suite")(
        test("third test") {
          assertM(Counter.incrementAndGet)(equalTo(4))
        },
        test("fourth test") {
          assertM(Counter.incrementAndGet)(equalTo(5))
        }
      )
    ).provideShared(Counter.live) @@ sequential,
    suite("managed per test")(
      suite("first suite")(
        test("first test") {
          assertM(Counter.incrementAndGet)(equalTo(2))
        },
        test("second test") {
          assertM(Counter.incrementAndGet)(equalTo(2))
        }
      ),
      suite("second suite")(
        test("third test") {
          assertM(Counter.incrementAndGet)(equalTo(2))
        },
        test("fourth test") {
          assertM(Counter.incrementAndGet)(equalTo(2))
        }
      )
    ).provide(Counter.live)
  )
}
