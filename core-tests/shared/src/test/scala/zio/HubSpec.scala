package zio

import zio.test._
import zio.test.Assertion._

object HubSpec extends ZIOBaseSpec {

  /**
   * A hub whose bulk `publishAll` rejects its first `failures` attempts,
   * returning every value unplaced, simulating a competing publisher that
   * claims the space freed by a concurrent `slide`.
   *
   * Only the members `unsafeSlidingPublish` actually calls are implemented.
   */
  final class FlakyPublishHub[A](failures: Int) extends zio.internal.Hub[A] {
    private[this] var remaining: Int   = failures
    private[this] var acceptedReversed = List.empty[A]

    var publishAllCalls: Int = 0
    var slideCalls: Int      = 0

    def accepted: List[A] = acceptedReversed.reverse

    val capacity: Int = 2

    def publishAll[A1 <: A](as: Iterable[A1]): Chunk[A1] = {
      publishAllCalls += 1
      if (remaining > 0) {
        remaining -= 1
        Chunk.fromIterable(as)
      } else {
        acceptedReversed = as.toList.reverse ::: acceptedReversed
        Chunk.empty
      }
    }

    def slide(): Unit = slideCalls += 1

    // the stub never actually holds values, so it always has room
    def size(): Int = 0

    def publish(a: A): Boolean                        = ???
    def isEmpty(): Boolean                            = ???
    def isFull(): Boolean                             = ???
    def subscribe(): zio.internal.Hub.Subscription[A] = ???
  }

  val smallInt: Gen[Any, Int] =
    Gen.small(Gen.const(_), 1)

  def spec = suite("HubSpec")(
    suite("sequential publishers and subscribers")(
      test("with one publisher and one subscriber") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.bounded[Int](n)
            subscriber <- ZIO.scoped {
                            hub.subscribe.flatMap { subscription =>
                              promise1.succeed(()) *> promise2.await *> ZIO.foreach(as.take(n))(_ => subscription.take)
                            }
                          }.fork
            _      <- promise1.await
            _      <- ZIO.foreach(as.take(n))(hub.publish)
            _      <- promise2.succeed(())
            values <- subscriber.join
          } yield assert(values)(equalTo(as.take(n)))
        }
      },
      test("with one publisher and two subscribers") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            promise3 <- Promise.make[Nothing, Unit]
            hub      <- Hub.bounded[Int](n)
            subscriber1 <-
              ZIO.scoped {
                hub.subscribe
                  .flatMap(subscription =>
                    promise1.succeed(()) *> promise3.await *> ZIO.foreach(as.take(n))(_ => subscription.take)
                  )
              }.fork
            subscriber2 <-
              ZIO.scoped {
                hub.subscribe
                  .flatMap(subscription =>
                    promise2.succeed(()) *> promise3.await *> ZIO.foreach(as.take(n))(_ => subscription.take)
                  )
              }.fork
            _       <- promise1.await
            _       <- promise2.await
            _       <- ZIO.foreach(as.take(n))(hub.publish)
            _       <- promise3.succeed(())
            values1 <- subscriber1.join
            values2 <- subscriber2.join
          } yield assert(values1)(equalTo(as.take(n))) &&
            assert(values2)(equalTo(as.take(n)))
        }
      }
    ),
    suite("concurrent publishers and subscribers")(
      test("one to one") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise <- Promise.make[Nothing, Unit]
            hub     <- Hub.bounded[Int](n)
            subscriber <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
                }
              }.fork
            _      <- promise.await
            _      <- ZIO.foreach(as.take(n))(hub.publish).fork
            values <- subscriber.join
          } yield assert(values)(equalTo(as.take(n)))
        }
      },
      test("one to many") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.bounded[Int](n)
            subscriber1 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise1.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
                }
              }.fork
            subscriber2 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise2.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
                }
              }.fork
            _       <- promise1.await
            _       <- promise2.await
            _       <- ZIO.foreach(as.take(n))(hub.publish).fork
            values1 <- subscriber1.join
            values2 <- subscriber2.join
          } yield assert(values1)(equalTo(as.take(n))) &&
            assert(values2)(equalTo(as.take(n)))
        }
      },
      test("many to many") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.bounded[Int](n * 2)
            subscriber1 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise1.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.take)
                }
              }.fork
            subscriber2 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise2.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.take)
                }
              }.fork
            _       <- promise1.await
            _       <- promise2.await
            _       <- ZIO.foreach(as.take(n))(hub.publish).fork
            _       <- ZIO.foreach(as.take(n).map(-_))(hub.publish).fork
            values1 <- subscriber1.join
            values2 <- subscriber2.join
          } yield assert(values1.filter(_ > 0))(equalTo(as.take(n))) &&
            assert(values1.filter(_ < 0))(equalTo(as.take(n).map(-_))) &&
            assert(values2.filter(_ > 0))(equalTo(as.take(n))) &&
            assert(values2.filter(_ < 0))(equalTo(as.take(n).map(-_)))
        }
      }
    ),
    suite("back pressure")(
      test("one to one") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise <- Promise.make[Nothing, Unit]
            hub     <- Hub.bounded[Int](n)
            subscriber <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise.succeed(()) *> ZIO.foreach(as)(_ => subscription.take)
                }
              }.fork
            _      <- promise.await
            _      <- ZIO.foreach(as)(hub.publish).fork
            values <- subscriber.join
          } yield assert(values)(equalTo(as))
        }
      },
      test("one to many") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.bounded[Int](n)
            subscriber1 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise1.succeed(()) *> ZIO.foreach(as)(_ => subscription.take)
                }
              }.fork
            subscriber2 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise2.succeed(()) *> ZIO.foreach(as)(_ => subscription.take)
                }
              }.fork
            _       <- promise1.await
            _       <- promise2.await
            _       <- ZIO.foreach(as)(hub.publish).fork
            values1 <- subscriber1.join
            values2 <- subscriber2.join
          } yield assert(values1)(equalTo(as)) &&
            assert(values2)(equalTo(as))
        }
      },
      test("many to many") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.bounded[Int](n * 2)
            subscriber1 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise1.succeed(()) *> ZIO.foreach((as ::: as))(_ => subscription.take)
                }
              }.fork
            subscriber2 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise2.succeed(()) *> ZIO.foreach((as ::: as))(_ => subscription.take)
                }
              }.fork
            _       <- promise1.await
            _       <- promise2.await
            _       <- ZIO.foreach(as)(hub.publish).fork
            _       <- ZIO.foreach(as.map(-_))(hub.publish).fork
            values1 <- subscriber1.join
            values2 <- subscriber2.join
          } yield assert(values1.filter(_ > 0))(equalTo(as)) &&
            assert(values1.filter(_ < 0))(equalTo(as.map(-_))) &&
            assert(values2.filter(_ > 0))(equalTo(as)) &&
            assert(values2.filter(_ < 0))(equalTo(as.map(-_)))
        }
      }
    ),
    suite("dropping")(
      test("one to one") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise <- Promise.make[Nothing, Unit]
            hub     <- Hub.dropping[Int](n)
            subscriber <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
                }
              }.fork
            _      <- promise.await
            _      <- ZIO.foreach(as)(hub.publish).fork
            values <- subscriber.join
          } yield assert(values)(equalTo(as.take(n)))
        }
      },
      test("one to many") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.dropping[Int](n)
            subscriber1 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise1.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
                }
              }.fork
            subscriber2 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise2.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
                }
              }.fork
            _       <- promise1.await
            _       <- promise2.await
            _       <- ZIO.foreach(as)(hub.publish).fork
            values1 <- subscriber1.join
            values2 <- subscriber2.join
          } yield assert(values1)(equalTo(as.take(n))) &&
            assert(values2)(equalTo(as.take(n)))
        }
      },
      test("many to many") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.dropping[Int](n * 2)
            subscriber1 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise1.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.take)
                }
              }.fork
            subscriber2 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise2.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.take)
                }
              }.fork
            _       <- promise1.await
            _       <- promise2.await
            _       <- ZIO.foreach(as)(hub.publish).fork
            _       <- ZIO.foreach(as.map(-_))(hub.publish).fork
            values1 <- subscriber1.join
            values2 <- subscriber2.join
          } yield assert(as)(startsWith(values1.filter(_ > 0))) &&
            assert(as.map(-_))(startsWith(values1.filter(_ < 0))) &&
            assert(as)(startsWith(values2.filter(_ > 0))) &&
            assert(as.map(-_))(startsWith(values2.filter(_ < 0)))
        }
      }
    ),
    suite("sliding")(
      test("one to one") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise <- Promise.make[Nothing, Unit]
            hub     <- Hub.sliding[Int](n)
            subscriber <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
                }
              }.fork
            _         <- promise.await
            publisher <- ZIO.foreach(as.sorted)(hub.publish).fork
            _         <- publisher.join
            values    <- subscriber.join
          } yield assert(values)(isSorted)
        }
      },
      test("one to many") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.sliding[Int](n)
            subscriber1 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise1.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
                }
              }.fork
            subscriber2 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise2.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
                }
              }.fork
            _       <- promise1.await
            _       <- promise2.await
            _       <- ZIO.foreach(as.sorted)(hub.publish).fork
            values1 <- subscriber1.join
            values2 <- subscriber2.join
          } yield assert(values1)(isSorted) &&
            assert(values2)(isSorted)
        }
      },
      test("many to many") {
        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.sliding[Int](n * 2)
            subscriber1 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise1.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.take)
                }
              }.fork
            subscriber2 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise2.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.take)
                }
              }.fork
            _       <- promise1.await
            _       <- promise2.await
            _       <- ZIO.foreach(as.sorted)(hub.publish).fork
            _       <- ZIO.foreach(as.map(-_).sorted)(hub.publish).fork
            values1 <- subscriber1.join
            values2 <- subscriber2.join
          } yield assert(values1.filter(_ > 0))(isSorted) &&
            assert(values1.filter(_ < 0))(isSorted) &&
            assert(values2.filter(_ > 0))(isSorted) &&
            assert(values2.filter(_ < 0))(isSorted)
        }
      }
    ),
    suite("unbounded")(
      test("one to one") {
        check(Gen.listOf(smallInt)) { as =>
          for {
            promise <- Promise.make[Nothing, Unit]
            hub     <- Hub.unbounded[Int]
            subscriber <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise.succeed(()) *> ZIO.foreach(as)(_ => subscription.take)
                }
              }.fork
            _      <- promise.await
            _      <- ZIO.foreach(as)(hub.publish).fork
            values <- subscriber.join
          } yield assert(values)(equalTo(as))
        }
      },
      test("one to many") {
        check(Gen.listOf(smallInt)) { as =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.unbounded[Int]
            subscriber1 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise1.succeed(()) *> ZIO.foreach(as)(_ => subscription.take)
                }
              }.fork
            subscriber2 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise2.succeed(()) *> ZIO.foreach(as)(_ => subscription.take)
                }
              }.fork
            _       <- promise1.await
            _       <- promise2.await
            _       <- ZIO.foreach(as)(hub.publish).fork
            values1 <- subscriber1.join
            values2 <- subscriber2.join
          } yield assert(values1)(equalTo(as)) &&
            assert(values2)(equalTo(as))
        }
      },
      test("many to many") {
        check(Gen.listOf(smallInt)) { as =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.unbounded[Int]
            subscriber1 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise1.succeed(()) *> ZIO.foreach((as ::: as))(_ => subscription.take)
                }
              }.fork
            subscriber2 <-
              ZIO.scoped {
                hub.subscribe.flatMap { subscription =>
                  promise2.succeed(()) *> ZIO.foreach((as ::: as))(_ => subscription.take)
                }
              }.fork
            _       <- promise1.await
            _       <- promise2.await
            _       <- ZIO.foreach(as)(hub.publish).fork
            _       <- ZIO.foreach(as.map(-_))(hub.publish).fork
            values1 <- subscriber1.join
            values2 <- subscriber2.join
          } yield assert(values1.filter(_ > 0))(equalTo(as)) &&
            assert(values1.filter(_ < 0))(equalTo(as.map(-_))) &&
            assert(values2.filter(_ > 0))(equalTo(as)) &&
            assert(values2.filter(_ < 0))(equalTo(as.map(-_)))
        }
      }
    ),
    suite("sliding strategy loses the publish race (i10885)")(
      test("retries the unplaced values rather than dropping them") {
        // Simulates a competing publisher that claims the space freed by the
        // `slide` inside `unsafeSlidingPublish`: the first `failures` bulk
        // publishes come back entirely unplaced. The loop must retry with
        // exactly those values, or they are silently dropped.
        val failures = 1000
        val hub      = new FlakyPublishHub[Int](failures)
        Hub.Strategy.Sliding[Int]().unsafeSlidingPublish(Chunk(1, 2, 3), hub)
        assertTrue(
          // capacity is 2, so 1 cannot survive the slide
          hub.accepted == List(2, 3),
          hub.publishAllCalls == failures + 1,
          // the stub always reports room, so nothing ever needs sliding out
          hub.slideCalls == 0
        )
      },
      test("terminates when the first bulk publish succeeds") {
        val hub = new FlakyPublishHub[Int](0)
        Hub.Strategy.Sliding[Int]().unsafeSlidingPublish(Chunk(1, 2, 3), hub)
        assertTrue(
          hub.accepted == List(2, 3),
          hub.publishAllCalls == 1
        )
      }
    ) @@ TestAspect.timeout(30.seconds)
  )
}
