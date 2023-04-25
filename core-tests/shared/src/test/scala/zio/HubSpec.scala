package zio

import zio.test._
import zio.test.Assertion._

object HubSpec extends ZIOBaseSpec {

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
    )
  ) @@ TestAspect.exceptNative
}
