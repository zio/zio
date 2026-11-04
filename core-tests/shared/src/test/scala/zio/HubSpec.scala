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
    suite("shutdown")(
      test("shutdown with take fiber") {
        for {
          selfId <- ZIO.fiberId
          hub    <- Hub.bounded[Int](3)
          f      <- ZIO.scoped(hub.subscribe.flatMap(_.take)).fork
          _      <- f.status.repeatUntil(_.isInstanceOf[Fiber.Status.Suspended])
          _      <- hub.shutdown
          res    <- f.join.sandbox.either
        } yield assert(res.left.map(_.untraced))(isLeft(equalTo(Cause.interrupt(selfId))))
      },
      test("shutdown with publish fiber") {
        ZIO.scoped {
          for {
            selfId <- ZIO.fiberId
            hub    <- Hub.bounded[Int](2)
            _      <- hub.subscribe
            _      <- hub.publish(1)
            _      <- hub.publish(1)
            f      <- hub.publish(1).fork
            _      <- f.status.repeatUntil(_.isInstanceOf[Fiber.Status.Suspended])
            _      <- hub.shutdown
            res    <- f.join.sandbox.either
          } yield assert(res.left.map(_.untraced))(isLeft(equalTo(Cause.interrupt(selfId))))
        }
      },
      test("shutdown with publish") {
        for {
          selfId <- ZIO.fiberId
          hub    <- Hub.bounded[Int](1)
          _      <- hub.shutdown
          res    <- hub.publish(1).sandbox.either
        } yield assert(res)(isLeft(equalTo(Cause.interrupt(selfId))))
      },
      test("shutdown with publishAll") {
        for {
          selfId <- ZIO.fiberId
          hub    <- Hub.bounded[Int](1)
          _      <- hub.shutdown
          res    <- hub.publishAll(List(1)).sandbox.either
        } yield assert(res)(isLeft(equalTo(Cause.interrupt(selfId))))
      },
      test("shutdown with size") {
        for {
          selfId <- ZIO.fiberId
          hub    <- Hub.bounded[Int](1)
          _      <- hub.shutdown
          res    <- hub.size.sandbox.either
        } yield assert(res)(isLeft(equalTo(Cause.interrupt(selfId))))
      }
    ),
    suite("shutdownCause")(
      test("shutdown with take fiber using Cause.die") {
        for {
          hub  <- Hub.bounded[Int](3)
          f    <- ZIO.scoped(hub.subscribe.flatMap(_.take)).fork
          _    <- f.status.repeatUntil(_.isInstanceOf[Fiber.Status.Suspended])
          cause = Cause.die(new RuntimeException("test"))
          _    <- hub.shutdownCause(cause)
          res  <- f.join.sandbox.either
        } yield assert(res.left.map(_.untraced))(isLeft(equalTo(cause)))
      },
      test("shutdown with publish fiber using Cause.die") {
        ZIO.scoped {
          for {
            hub  <- Hub.bounded[Int](2)
            _    <- hub.subscribe
            _    <- hub.publish(1)
            _    <- hub.publish(1)
            f    <- hub.publish(1).fork
            _    <- f.status.repeatUntil(_.isInstanceOf[Fiber.Status.Suspended])
            cause = Cause.die(new RuntimeException("test"))
            _    <- hub.shutdownCause(cause)
            res  <- f.join.sandbox.either
          } yield assert(res.left.map(_.untraced))(isLeft(equalTo(cause)))
        }
      },
      test("shutdown with publish using Cause.die") {
        for {
          hub  <- Hub.bounded[Int](1)
          cause = Cause.die(new RuntimeException("test"))
          _    <- hub.shutdownCause(cause)
          res  <- hub.publish(1).sandbox.either
        } yield assert(res.left.map(_.untraced))(isLeft(equalTo(cause)))
      },
      test("shutdown with publishAll using Cause.die") {
        for {
          hub  <- Hub.bounded[Int](1)
          cause = Cause.die(new RuntimeException("test"))
          _    <- hub.shutdownCause(cause)
          res  <- hub.publishAll(List(1)).sandbox.either
        } yield assert(res.left.map(_.untraced))(isLeft(equalTo(cause)))
      },
      test("shutdown with size using Cause.die") {
        for {
          hub  <- Hub.bounded[Int](1)
          cause = Cause.die(new RuntimeException("test"))
          _    <- hub.shutdownCause(cause)
          res  <- hub.size.sandbox.either
        } yield assert(res.left.map(_.untraced))(isLeft(equalTo(cause)))
      }
    ),
    suite("awaitShutdown")(
      test("single") {
        for {
          hub <- Hub.bounded[Int](3)
          p   <- Promise.make[Nothing, Boolean]
          _   <- (hub.awaitShutdown *> p.succeed(true)).fork
          _   <- hub.shutdown
          res <- p.await
        } yield assert(res)(isTrue)
      },
      test("multiple") {
        for {
          hub  <- Hub.bounded[Int](3)
          p1   <- Promise.make[Nothing, Boolean]
          p2   <- Promise.make[Nothing, Boolean]
          _    <- (hub.awaitShutdown *> p1.succeed(true)).fork
          _    <- (hub.awaitShutdown *> p2.succeed(true)).fork
          _    <- hub.shutdown
          res1 <- p1.await
          res2 <- p2.await
        } yield assert(res1)(isTrue) &&
          assert(res2)(isTrue)
      },
      test("already shutdown") {
        for {
          hub <- Hub.bounded[Int](3)
          _   <- hub.shutdown
          p   <- Promise.make[Nothing, Boolean]
          _   <- (hub.awaitShutdown *> p.succeed(true)).fork
          res <- p.await
        } yield assert(res)(isTrue)
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
  )
}
