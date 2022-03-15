package zio.test

import zio._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.sequential

object ScopedSpec extends ZIOBaseSpec {

  trait Counter {
    def incrementAndGet: UIO[Int]
  }

  object Counter {

    val live: ZLayer[Scope, Nothing, Counter] =
      ZLayer {
        ZIO.acquireRelease(Ref.make(1))(_.set(-10)).map { ref =>
          new Counter {
            val incrementAndGet: UIO[Int] = ref.updateAndGet(_ + 1)
          }
        }
      }

    val incrementAndGet: URIO[Counter, Int] =
      ZIO.serviceWithZIO(_.incrementAndGet)
  }

  def spec: Spec[Annotations, TestFailure[Any], TestSuccess] = suite("ScopedSpec")(
    suite("scoped shared")(
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
    ).provideLayerShared(Scope.layer >>> Counter.live) @@ sequential,
    suite("scoped per test")(
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
    ).provideLayer(Scope.layer >>> Counter.live)
  )
}
