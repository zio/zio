package zio

import zio.test.Assertion._
import zio.test._
object ZHubSpec extends ZIOBaseSpec {

  val smallInt: Gen[Has[Random] with Has[Sized], Int] =
    Gen.small(Gen.const(_), 1)

  def spec: ZSpec[Environment, Failure] = suite("ZHubSpec")(
    suite("sequential publishers and subscribers")(
      testM("with one publisher and one subscriber") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.bounded[Int](n)
            subscriber <- hub.subscribe.use { subscription =>
                            promise1.succeed(()) *> promise2.await *> ZIO.foreach(as.take(n))(_ => subscription.take)
                          }.fork
            _      <- promise1.await
            _      <- ZIO.foreach(as.take(n))(hub.publish)
            _      <- promise2.succeed(())
            values <- subscriber.join
          } yield assert(values)(equalTo(as.take(n)))
        }
      },
      testM("with one publisher and two subscribers") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            promise3 <- Promise.make[Nothing, Unit]
            hub      <- Hub.bounded[Int](n)
            subscriber1 <-
              hub.subscribe
                .use(subscription =>
                  promise1.succeed(()) *> promise3.await *> ZIO.foreach(as.take(n))(_ => subscription.take)
                )
                .fork
            subscriber2 <-
              hub.subscribe
                .use(subscription =>
                  promise2.succeed(()) *> promise3.await *> ZIO.foreach(as.take(n))(_ => subscription.take)
                )
                .fork
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
      testM("one to one") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise <- Promise.make[Nothing, Unit]
            hub     <- Hub.bounded[Int](n)
            subscriber <-
              hub.subscribe.use { subscription =>
                promise.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
              }.fork
            _      <- promise.await
            _      <- ZIO.foreach(as.take(n))(hub.publish).fork
            values <- subscriber.join
          } yield assert(values)(equalTo(as.take(n)))
        }
      },
      testM("one to many") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.bounded[Int](n)
            subscriber1 <-
              hub.subscribe.use { subscription =>
                promise1.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
              }.fork
            subscriber2 <-
              hub.subscribe.use { subscription =>
                promise2.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
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
      testM("many to many") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.bounded[Int](n * 2)
            subscriber1 <-
              hub.subscribe.use { subscription =>
                promise1.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.take)
              }.fork
            subscriber2 <-
              hub.subscribe.use { subscription =>
                promise2.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.take)
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
      testM("one to one") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise <- Promise.make[Nothing, Unit]
            hub     <- Hub.bounded[Int](n)
            subscriber <-
              hub.subscribe.use { subscription =>
                promise.succeed(()) *> ZIO.foreach(as)(_ => subscription.take)
              }.fork
            _      <- promise.await
            _      <- ZIO.foreach(as)(hub.publish).fork
            values <- subscriber.join
          } yield assert(values)(equalTo(as))
        }
      },
      testM("one to many") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.bounded[Int](n)
            subscriber1 <-
              hub.subscribe.use { subscription =>
                promise1.succeed(()) *> ZIO.foreach(as)(_ => subscription.take)
              }.fork
            subscriber2 <-
              hub.subscribe.use { subscription =>
                promise2.succeed(()) *> ZIO.foreach(as)(_ => subscription.take)
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
      testM("many to many") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.bounded[Int](n * 2)
            subscriber1 <-
              hub.subscribe.use { subscription =>
                promise1.succeed(()) *> ZIO.foreach((as ::: as))(_ => subscription.take)
              }.fork
            subscriber2 <-
              hub.subscribe.use { subscription =>
                promise2.succeed(()) *> ZIO.foreach((as ::: as))(_ => subscription.take)
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
      testM("one to one") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise <- Promise.make[Nothing, Unit]
            hub     <- Hub.dropping[Int](n)
            subscriber <-
              hub.subscribe.use { subscription =>
                promise.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
              }.fork
            _      <- promise.await
            _      <- ZIO.foreach(as)(hub.publish).fork
            values <- subscriber.join
          } yield assert(values)(equalTo(as.take(n)))
        }
      },
      testM("one to many") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.dropping[Int](n)
            subscriber1 <-
              hub.subscribe.use { subscription =>
                promise1.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
              }.fork
            subscriber2 <-
              hub.subscribe.use { subscription =>
                promise2.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
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
      testM("many to many") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.dropping[Int](n * 2)
            subscriber1 <-
              hub.subscribe.use { subscription =>
                promise1.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.take)
              }.fork
            subscriber2 <-
              hub.subscribe.use { subscription =>
                promise2.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.take)
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
      testM("one to one") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise <- Promise.make[Nothing, Unit]
            hub     <- Hub.sliding[Int](n)
            subscriber <-
              hub.subscribe.use { subscription =>
                promise.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
              }.fork
            _         <- promise.await
            publisher <- ZIO.foreach(as.sorted)(hub.publish).fork
            _         <- publisher.join
            values    <- subscriber.join
          } yield assert(values)(isSorted)
        }
      },
      testM("one to many") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.sliding[Int](n)
            subscriber1 <-
              hub.subscribe.use { subscription =>
                promise1.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
              }.fork
            subscriber2 <-
              hub.subscribe.use { subscription =>
                promise2.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.take)
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
      testM("many to many") {
        checkM(smallInt, Gen.listOf(smallInt)) { (n, as) =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.sliding[Int](n * 2)
            subscriber1 <-
              hub.subscribe.use { subscription =>
                promise1.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.take)
              }.fork
            subscriber2 <-
              hub.subscribe.use { subscription =>
                promise2.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.take)
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
      testM("one to one") {
        checkM(Gen.listOf(smallInt)) { as =>
          for {
            promise <- Promise.make[Nothing, Unit]
            hub     <- Hub.unbounded[Int]
            subscriber <-
              hub.subscribe.use { subscription =>
                promise.succeed(()) *> ZIO.foreach(as)(_ => subscription.take)
              }.fork
            _      <- promise.await
            _      <- ZIO.foreach(as)(hub.publish).fork
            values <- subscriber.join
          } yield assert(values)(equalTo(as))
        }
      },
      testM("one to many") {
        checkM(Gen.listOf(smallInt)) { as =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.unbounded[Int]
            subscriber1 <-
              hub.subscribe.use { subscription =>
                promise1.succeed(()) *> ZIO.foreach(as)(_ => subscription.take)
              }.fork
            subscriber2 <-
              hub.subscribe.use { subscription =>
                promise2.succeed(()) *> ZIO.foreach(as)(_ => subscription.take)
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
      testM("many to many") {
        checkM(Gen.listOf(smallInt)) { as =>
          for {
            promise1 <- Promise.make[Nothing, Unit]
            promise2 <- Promise.make[Nothing, Unit]
            hub      <- Hub.unbounded[Int]
            subscriber1 <-
              hub.subscribe.use { subscription =>
                promise1.succeed(()) *> ZIO.foreach((as ::: as))(_ => subscription.take)
              }.fork
            subscriber2 <-
              hub.subscribe.use { subscription =>
                promise2.succeed(()) *> ZIO.foreach((as ::: as))(_ => subscription.take)
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
