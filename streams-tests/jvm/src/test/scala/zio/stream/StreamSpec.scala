package zio.stream

import scala.{ Stream => _ }
import zio.{ test => _, _ }
import zio.duration._
import zio.test._
import zio.test.Assertion.{ dies, equalTo, isTrue }
// import zio.test.Assertion.{ equalTo, fails, isFalse, isLeft, isRight, isSome, succeeds }
// import zio.random.Random

// import StreamUtils._

object ZStreamSpec
    extends ZIOSpec(
      suite("ZStreamSpec")(
        suite("Stream.aggregate")(
          testM("aggregate")(
            Stream(1, 1, 1, 1)
              .aggregate(ZSink.foldUntil(List[Int](), 3)((acc, el: Int) => el :: acc).map(_.reverse))
              .runCollect
              .map { result =>
                assert(result.flatten, equalTo(List(1, 1, 1, 1))) &&
                assert(result.forall(_.length <= 3), isTrue)
              }
          ),
          testM("error propagation") {
            val e    = new RuntimeException("Boom")
            val sink = ZSink.die(e)
            assertM(
              Stream(1, 1, 1, 1)
                .aggregate(sink)
                .runCollect
                .run,
              dies(equalTo(e))
            )
          },
          testM("error propagation") {
            val e = new RuntimeException("Boom")
            val sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]()) { (_, _) =>
              ZIO.die(e)
            }

            assertM(
              Stream(1, 1)
                .aggregate(sink)
                .runCollect
                .run,
              dies(equalTo(e))
            )
          },
          testM("interruption propagation") {
            for {
              latch     <- Promise.make[Nothing, Unit]
              cancelled <- Ref.make(false)
              sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]()) { (acc, el) =>
                if (el == 1) UIO.succeed(ZSink.Step.more(el :: acc))
                else
                  (latch.succeed(()) *> UIO.never)
                    .onInterrupt(cancelled.set(true))
              }
              fiber  <- Stream(1, 1, 2).aggregate(sink).runCollect.untraced.fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- cancelled.get
            } yield assert(result, isTrue)
          },
          testM("interruption propagation") {
            for {
              latch     <- Promise.make[Nothing, Unit]
              cancelled <- Ref.make(false)
              sink = Sink.fromEffect {
                (latch.succeed(()) *> UIO.never)
                  .onInterrupt(cancelled.set(true))
              }
              fiber  <- Stream(1, 1, 2).aggregate(sink).runCollect.untraced.fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- cancelled.get
            } yield assert(result, isTrue)
          }
        ),
        suite("Stream.aggregateWithin")(
          testM("aggregateWithin") {
            for {
              result <- Stream(1, 1, 1, 1, 2)
                         .aggregateWithin(
                           Sink.fold(List[Int]())(
                             (acc, el: Int) =>
                               if (el == 1) ZSink.Step.more(el :: acc)
                               else if (el == 2 && acc.isEmpty) ZSink.Step.done(el :: acc, Chunk.empty)
                               else ZSink.Step.done(acc, Chunk.single(el))
                           ),
                           ZSchedule.spaced(30.minutes)
                         )
                         .runCollect
            } yield assert(result, equalTo[List[Either[Int, List[Int]]]](List(Right(List(1, 1, 1, 1)), Right(List(2)))))

          },
          testM("error propagation") {

            val e    = new RuntimeException("Boom")
            val sink = ZSink.die(e)
            assertM(
              Stream(1, 1, 1, 1)
                .aggregateWithin(sink, Schedule.spaced(30.minutes))
                .runCollect
                .run,
              dies(equalTo(e))
            )

          },
          testM("error propagation") {
            val e = new RuntimeException("Boom")
            val sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]()) { (_, _) =>
              ZIO.die(e)
            }

            assertM(
              Stream(1, 1)
                .aggregateWithin(sink, Schedule.spaced(30.minutes))
                .runCollect
                .run,
              dies(equalTo(e))
            )
          },
          testM("interruption propagation") {
            for {
              latch     <- Promise.make[Nothing, Unit]
              cancelled <- Ref.make(false)
              sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]()) { (acc, el) =>
                if (el == 1) UIO.succeed(ZSink.Step.more(el :: acc))
                else
                  (latch.succeed(()) *> UIO.never)
                    .onInterrupt(cancelled.set(true))
              }
              fiber  <- Stream(1, 1, 2).aggregateWithin(sink, Schedule.spaced(30.minutes)).runCollect.untraced.fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- cancelled.get
            } yield assert(result, isTrue)

          },
          testM("interruption propagation") {
            for {
              latch     <- Promise.make[Nothing, Unit]
              cancelled <- Ref.make(false)
              sink = Sink.fromEffect {
                (latch.succeed(()) *> UIO.never)
                  .onInterrupt(cancelled.set(true))
              }
              fiber  <- Stream(1, 1, 2).aggregateWithin(sink, Schedule.spaced(30.minutes)).runCollect.untraced.fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- cancelled.get
            } yield assert(result, isTrue)

          }
        )
        //   suite("Stream.bracket")(
        //     testM("bracket") {
        //       for {
        //         done           <- Ref.make(false)
        //         iteratorStream = Stream.bracket(UIO(0 to 2))(_ => done.set(true)).flatMap(Stream.fromIterable)
        //         result         <- iteratorStream.run(Sink.collectAll[Int])
        //         released       <- done.get
        //       } yield assert(result, equalTo(List(0, 1, 2))) && assert(released, isTrue)

        //     },
        //     testM("bracket short circuits") {
        //       for {
        //         done <- Ref.make(false)
        //         iteratorStream = Stream
        //           .bracket(UIO(0 to 3))(_ => done.set(true))
        //           .flatMap(Stream.fromIterable)
        //           .take(2)
        //         result   <- iteratorStream.run(Sink.collectAll[Int])
        //         released <- done.get
        //       } yield assert(result, equalTo(List(0, 1))) && assert(released, isTrue)

        //     },
        //     testM("no acquisition when short circuiting") {
        //       for {
        //         acquired       <- Ref.make(false)
        //         iteratorStream = (Stream(1) ++ Stream.bracket(acquired.set(true))(_ => UIO.unit)).take(0)
        //         _              <- iteratorStream.run(Sink.drain)
        //         result         <- acquired.get
        //       } yield assert(result, isFalse)

        //     },
        //     testM("releases when there are defects") {
        //       for {
        //         ref <- Ref.make(false)
        //         _ <- Stream
        //               .bracket(ZIO.unit)(_ => ref.set(true))
        //               .flatMap(_ => Stream.fromEffect(ZIO.dieMessage("boom")))
        //               .run(Sink.drain)
        //               .run
        //         released <- ref.get
        //       } yield assert(released, isTrue)

        //     }
        //   ),
        //   suite("Stream.broadcast")(
        //     testM("Values") {
        //       val expected = List(0, 1, 2, 3, 4, 5)
        //       assertM(
        //         Stream
        //           .range(0, 5)
        //           .broadcast(2, 12)
        //           .use {
        //             case s1 :: s2 :: Nil =>
        //               for {
        //                 out1 <- s1.runCollect
        //                 out2 <- s2.runCollect
        //               } yield out1 -> out2
        //             case _ =>
        //               ZIO.fail("Wrong number of streams produced")
        //           }
        //           .run,
        //         succeeds(equalTo((expected, expected)))
        //       )

        //     },
        //     testM("Errors") {
        //       assertM(
        //         (Stream.range(0, 1) ++ Stream.fail("Boom")).broadcast(2, 12).use {
        //           case s1 :: s2 :: Nil =>
        //             for {
        //               out1 <- s1.runCollect.either
        //               out2 <- s2.runCollect.either
        //             } yield Some(out1 -> out2)
        //           case _ =>
        //             ZIO.succeed(None)
        //         },
        //         isSome(equalTo[(Either[String, List[Int]], Either[String, List[Int]])]((Left("Boom"), Left("Boom"))))
        //       )
        //     },
        //     testM("BackPressure") {
        //       assertM(
        //         Stream
        //           .range(0, 5)
        //           .broadcast(2, 2)
        //           .use {
        //             case s1 :: s2 :: Nil =>
        //               for {
        //                 ref       <- Ref.make[List[Int]](Nil)
        //                 latch1    <- Promise.make[Nothing, Unit]
        //                 fib       <- s1.tap(i => ref.update(i :: _) *> latch1.succeed(()).when(i == 2)).runDrain.fork
        //                 _         <- latch1.await
        //                 snapshot1 <- ref.get
        //                 _         <- s2.runDrain
        //                 _         <- fib.await
        //                 snapshot2 <- ref.get
        //               } yield snapshot1 -> snapshot2
        //             case _ =>
        //               ZIO.fail("Wrong number of streams produced")
        //           }
        //           .run,
        //         succeeds(equalTo((List(2, 1, 0), List(5, 4, 3, 2, 1, 0))))
        //       )
        //     },
        //     testM("Unsubscribe") {
        //       assertM(
        //         Stream
        //           .range(0, 5)
        //           .broadcast(2, 2)
        //           .use {
        //             case s1 :: s2 :: Nil =>
        //               for {
        //                 _    <- s1.process.use_(ZIO.unit).ignore
        //                 out2 <- s2.runCollect
        //               } yield out2
        //             case _ =>
        //               ZIO.fail("Wrong number of streams produced")
        //           }
        //           .run,
        //         succeeds(equalTo(List(0, 1, 2, 3, 4, 5)))
        //       )
        //     }
        //   ),
        //   suite("Stream.buffer")(
        //     testM("buffer the Stream") {
        //       checkM(listOfInts) { list =>
        //         assertM(
        //           Stream
        //             .fromIterable(list)
        //             .buffer(2)
        //             .run(Sink.collectAll[Int]),
        //           equalTo(list)
        //         )
        //       }
        //     },
        //     testM("buffer the Stream with Error") {
        //       val e = new RuntimeException("boom")
        //       assertM(
        //         (Stream.range(0, 10) ++ Stream.fail(e))
        //           .buffer(2)
        //           .run(Sink.collectAll[Int])
        //           .run,
        //         fails(equalTo(e))
        //       )

        //     },
        //     testM("fast producer progress independently") {
        //       for {
        //         ref   <- Ref.make(List[Int]())
        //         latch <- Promise.make[Nothing, Unit]
        //         s     = Stream.range(1, 5).tap(i => ref.update(i :: _) *> latch.succeed(()).when(i == 4)).buffer(2)
        //         l <- s.process.use { as =>
        //               for {
        //                 _ <- as
        //                 _ <- latch.await
        //                 l <- ref.get
        //               } yield l
        //             }.run
        //       } yield assert(l, succeeds(equalTo((4 to 1).toList)))

        //     }
        //   ),
        //   suite("Stream.catchAllCause")(
        //     // Getting "a type was inferred to be `Any`" with the following tests:
        //     // testM("recovery from errors") {
        //     //   val s1 = Stream(1, 2) ++ Stream.fail("Boom")
        //     //   val s2 = Stream(3, 4)

        //     //   assertM(s1.catchAllCause(_ => s2).runCollect, equalTo(List(1, 2, 3, 4)))
        //     // },
        //     // testM("recovery from defects") {
        //     //   val s1 = Stream(1, 2) ++ Stream.dieMessage("Boom")
        //     //   val s2 = Stream(3, 4)

        //     //   assertM(s1.catchAllCause(_ => s2).runCollect, equalTo(List(1, 2, 3, 4)))

        //     // },
        //     // testM("happy path") {
        //     //   val s1 = Stream(1, 2)
        //     //   val s2 = Stream(3, 4)

        //     //   assertM(s1.catchAllCause(_ => s2).runCollect, equalTo(List(1, 2)))

        //     // },
        //     testM("executes finalizers") {
        //       for {
        //         fins   <- Ref.make(List[String]())
        //         s1     = (Stream(1, 2) ++ Stream.fail("Boom")).ensuring(fins.update("s1" :: _))
        //         s2     = (Stream(3, 4) ++ Stream.fail("Boom")).ensuring(fins.update("s2" :: _))
        //         _      <- s1.catchAllCause(_ => s2).runCollect.run
        //         result <- fins.get
        //       } yield assert(result, equalTo(List("s2", "s1")))
        //     }
        //     // testM("failures on the scope") {
        //     //   val s1 = Stream(1, 2) ++ ZStream(ZManaged.fail("Boom"))
        //     //   val s2 = Stream(3, 4)

        //     //   assertM(s1.catchAllCause(_ => s2).runCollect, equalTo(List(1, 2, 3, 4)))
        //     // }
        //   ),
        //   testM("Stream.collect") {
        //     assertM(Stream(Left(1), Right(2), Left(3)).collect {
        //       case Right(n) => n
        //     }.runCollect, equalTo(List(2)))

        //   },
        //   suite("Stream.collectWhile")(
        //     testM("collectWhile") {
        //       assertM(
        //         Stream(Some(1), Some(2), Some(3), None, Some(4)).collectWhile { case Some(v) => v }.runCollect,
        //         equalTo(List(1, 2, 3))
        //       )
        //     },
        //     testM("collectWhile short circuits") {
        //       assertM(
        //         (Stream(Option(1)) ++ Stream.fail("Ouch")).collectWhile {
        //           case None => 1
        //         }.runDrain.either,
        //         isRight(equalTo(()))
        //       )

        //     }
        //   ),
        //   suite("Stream.concat")(
        //     // testM("concat") {
        //     //   checkM(streamOfBytes, streamOfBytes) { (s1, s2) =>
        //     //     for {
        //     //       listConcat   <- s1.runCollect.zipWith(s2.runCollect)(_ ++ _)
        //     //       streamConcat <- (s1 ++ s2).runCollect
        //     //     } yield {
        //     //       assert(streamConcat.succeeded && listConcat.succeeded, isTrue) ==> assert(
        //     //         streamConcat,
        //     //         equalTo(listConcat)
        //     //       )
        //     //     }
        //     //   }
        //     // },
        //     testM("finalizer order") {
        //       for {
        //         log       <- Ref.make[List[String]](Nil)
        //         _         <- (Stream.finalizer(log.update("Second" :: _)) ++ Stream.finalizer(log.update("First" :: _))).runDrain
        //         execution <- log.get
        //       } yield assert(execution, equalTo(List("First", "Second")))
        //     }
        //   ),
        //   testM("Stream.drain") {
        //     for {
        //       ref <- Ref.make(List[Int]())
        //       _   <- Stream.range(0, 10).mapM(i => ref.update(i :: _)).drain.run(Sink.drain)
        //       l   <- ref.get
        //     } yield assert(l.reverse, equalTo((0 to 10).toList))

        //   },
        //   testM("dropUntil") {
        //     def dropUntil[A](as: List[A])(f: A => Boolean): List[A] = as.dropWhile(!f(_)).drop(1)
        //     checkM(streamOfBytes, toBoolFn[Random, Byte]) { (s, p) =>
        //       for {
        //         res1 <- s.dropUntil(p).runCollect
        //         res2 <- s.runCollect.map(dropUntil(_)(p))
        //       } yield assert(res1, equalTo(res2))
        //     }
        //   },
        //   suite("Stream.dropWhile")(
        //     testM("dropWhile") {
        //       checkM(streamOfBytes, toBoolFn[Random, Byte]) { (s, p) =>
        //         for {
        //           res1 <- s.dropWhile(p).runCollect
        //           res2 <- s.runCollect.map(_.dropWhile(p))
        //         } yield assert(res1, equalTo(res2))
        //       }
        //     },
        //     testM("short circuits") {
        //       assertM(
        //         (Stream(1) ++ Stream.fail("Ouch"))
        //           .take(1)
        //           .dropWhile(_ => true)
        //           .runDrain
        //           .either,
        //         isRight(equalTo(()))
        //       )
        //     }
        //   ),
        //   suite("Stream.effectAsync")(
        //     testM("effectAsync") {
        //       checkM(listOfInts) { list =>
        //         val s = Stream.effectAsync[Throwable, Int] { k =>
        //           list.foreach(a => k(Task.succeed(a)))
        //         }

        //         assertM(s.take(list.size).runCollect.run, succeeds(equalTo(list)))

        //       }
        //     }
        //   ),
        //   suite("Stream.effectAsyncMaybe")(
        //     testM("effectAsyncMaybe signal end stream") {
        //       for {
        //         result <- Stream
        //                    .effectAsyncMaybe[Nothing, Int] { k =>
        //                      k(IO.fail(None))
        //                      None
        //                    }
        //                    .runCollect
        //       } yield assert(result, equalTo(List.empty[Int]))
        //     },
        //     testM("effectAsyncMaybe Some") {
        //       checkM(listOfInts) { list =>
        //         val s = Stream.effectAsyncMaybe[Throwable, Int] { _ =>
        //           Some(Stream.fromIterable(list))
        //         }

        //         assertM(s.runCollect.map(_.take(list.size)).run, succeeds(equalTo(list)))
        //       }
        //     },
        //     testM("effectAsyncMaybe None") {
        //       checkM(listOfInts) { list =>
        //         val s = Stream.effectAsyncMaybe[Throwable, Int] { k =>
        //           list.foreach(a => k(Task.succeed(a)))
        //           None
        //         }

        //         assertM(s.take(list.size).runCollect.run, succeeds(equalTo(list)))
        //       }
        //     }
        //   ),
        //   suite("Stream.effectAsyncM")(
        //     testM("effectAsyncM") {
        //       val list = List(1, 2, 3)
        //       assertM(
        //         (for {
        //           latch <- Promise.make[Nothing, Unit]
        //           fiber <- ZStream
        //                     .effectAsyncM[Any, Throwable, Int] { k =>
        //                       latch.succeed(()) *>
        //                         Task.succeed {
        //                           list.foreach(a => k(Task.succeed(a)))
        //                         }
        //                     }
        //                     .take(list.size)
        //                     .run(Sink.collectAll[Int])
        //                     .fork
        //           _ <- latch.await
        //           s <- fiber.join
        //         } yield s).run,
        //         succeeds(equalTo(list))
        //       )
        //     },
        //     testM("effectAsyncM signal end stream") {
        //       assertM(
        //         Stream
        //           .effectAsyncM[Nothing, Int] { k =>
        //             k(IO.fail(None))
        //             UIO.succeed(())
        //           }
        //           .runCollect
        //           .run,
        //         succeeds(equalTo(List.empty[Int]))
        //       )

        //     }
        //   ),
        //   suite("Stream.effectAsyncInterrupt")(
        //     testM("effectAsyncInterrupt Left") {
        //       for {
        //         cancelled <- Ref.make(false)
        //         latch     <- Promise.make[Nothing, Unit]
        //         fiber <- Stream
        //                   .effectAsyncInterrupt[Nothing, Unit] { offer =>
        //                     offer(ZIO.succeed(())); Left(cancelled.set(true))
        //                   }
        //                   .tap(_ => latch.succeed(()))
        //                   .run(Sink.collectAll[Unit])
        //                   .fork
        //         _      <- latch.await
        //         _      <- fiber.interrupt
        //         result <- cancelled.get
        //       } yield assert(result, isTrue)

        //     },
        //     testM("effectAsyncInterrupt Right") {
        //       checkM(listOfInts) { list =>
        //         val s = Stream.effectAsyncInterrupt[Throwable, Int] { _ =>
        //           Right(Stream.fromIterable(list))
        //         }
        //         assertM(s.take(list.size).runCollect.run, succeeds(equalTo(list)))
        //       }
        //     },
        //     testM("effectAsyncInterrupt signal end stream") {
        //       assertM(
        //         Stream
        //           .effectAsyncInterrupt[Nothing, Int] { k =>
        //             k(IO.fail(None))
        //             Left(UIO.succeed(()))
        //           }
        //           .runCollect,
        //         equalTo(List.empty[Int])
        //       )

        //     }
        //   ),
        //   testM("Stream.ensuring") {
        //     for {
        //       log <- Ref.make[List[String]](Nil)
        //       _ <- (for {
        //             _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
        //             _ <- Stream.fromEffect(log.update("Use" :: _))
        //           } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
        //       execution <- log.get
        //     } yield assert(execution, equalTo(List("Ensuring", "Release", "Use", "Acquire")))
        //   },
        //   testM("Stream.ensuringFirst") {
        //     for {
        //       log <- Ref.make[List[String]](Nil)
        //       _ <- (for {
        //             _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
        //             _ <- Stream.fromEffect(log.update("Use" :: _))
        //           } yield ()).ensuringFirst(log.update("Ensuring" :: _)).runDrain
        //       execution <- log.get
        //     } yield assert(execution, equalTo(List("Release", "Ensuring", "Use", "Acquire")))
        //   },
        //   testM("Stream.finalizer") {
        //     for {
        //       log <- Ref.make[List[String]](Nil)
        //       _ <- (for {
        //             _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
        //             _ <- Stream.finalizer(log.update("Use" :: _))
        //           } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
        //       execution <- log.get
        //     } yield assert(execution, equalTo(List("Ensuring", "Release", "Use", "Acquire")))

        //   },
        //   suite("Stream.filter")(
        //     testM("filter") {
        //       checkM(streamOfBytes, toBoolFn[Random, Byte]) { (s, p) =>
        //         for {
        //           res1 <- s.filter(p).runCollect
        //           res2 <- s.runCollect.map(_.filter(p))
        //         } yield assert(res1, equalTo(res2))
        //       }
        //     },
        //     testM("short circuits #1") {
        //       assertM(
        //         (Stream(1) ++ Stream.fail("Ouch"))
        //           .filter(_ => true)
        //           .take(1)
        //           .runDrain
        //           .either,
        //         isRight(equalTo(()))
        //       )

        //     },
        //     testM("short circuits #2") {
        //       assertM(
        //         (Stream(1) ++ Stream.fail("Ouch"))
        //           .take(1)
        //           .filter(_ => true)
        //           .runDrain
        //           .either,
        //         isRight(equalTo(()))
        //       )

        //     }
        //   ),
        //   suite("Stream.filterM")(
        //     // testM("filterM") {
        //     //   checkM(streamOfBytes, Gen[Byte => Boolean]) { (s, p) =>
        //     //     for {
        //     //       res1 <- s.filterM(s => IO.succeed(p(s))).runCollect
        //     //       res2 <- s.runCollect.map(_.filter(p))
        //     //     } yield assert(res1, equalTo(res2))
        //     //   }

        //     // },
        //     // TODO: A type was inferred to be `Any`
        //     // testM("short circuits #1") {
        //     //   assertM(
        //     //     (Stream(1) ++ Stream.fail("Ouch"))
        //     //       .take(1)
        //     //       .filterM(_ => UIO.succeed(true))
        //     //       .runDrain
        //     //       .either,
        //     //     isRight(equalTo(()))
        //     //   )
        //     // },
        //     testM("short circuits #2") {
        //       assertM(
        //         (Stream(1) ++ Stream.fail("Ouch"))
        //           .filterM(_ => UIO.succeed(true))
        //           .take(1)
        //           .runDrain
        //           .either,
        //         isRight(equalTo(()))
        //       )

        //     }
        //   ),
        //   suite("Stream.flatMap")(
        //     testM("deep flatMap stack safety") {
        //       def fib(n: Int): Stream[Nothing, Int] =
        //         if (n <= 1) Stream.succeed(n)
        //         else
        //           fib(n - 1).flatMap { a =>
        //             fib(n - 2).flatMap { b =>
        //               Stream.succeed(a + b)
        //             }
        //           }

        //       val stream   = fib(20)
        //       val expected = 6765

        //       assertM(stream.runCollect.either, isRight(equalTo(List(expected))))

        //     }
        //     // testM("left identity") {
        //     //   checkM(intGen, Gen[Int => Stream[String, Int]])(
        //     //     (x, f) =>
        //     //       for {
        //     //         res1 <- Stream(x).flatMap(f).runCollect
        //     //         res2 <- f(x).runCollect
        //     //       } yield assert(res1, equalTo(res2))
        //     //   )
        //     // },
        //     //     testM("right identity") {
        //     //       checkM(pureStreamGen(intGen))(
        //     //         m =>
        //     //           for {
        //     //             res1 <- m.flatMap(i => Stream(i)).runCollect
        //     //             res2 <- m.runCollect
        //     //           } yield assert(res1, equalTo(res2))
        //     //       )

        //     // },
        //     // testM("associativity") {
        //     //   checkM(streamOfInts, Gen[Int => Stream[String, Int]], Gen[Int => Stream[String, Int]]) {
        //     //     (m, f, g) =>
        //     //       val leftStream  = m.flatMap(f).flatMap(g)
        //     //       val rightStream = m.flatMap(x => f(x).flatMap(g))
        //     //       for {
        //     //         res1 <- leftStream.runCollect
        //     //         res2 <- rightStream.runCollect
        //     //       } yield assert(res1, equalTo(res2))
        //     //   }

        //     // }
        //   ),
        //   suite("Stream.flatMapPar/flattenPar/mergeAll")(
        //     // TODO: a type was inferred to be `Any`
        //     // testM("guarantee ordering") {
        //     //   checkM(listOfInts) { m =>
        //     //     val s: Stream[Nothing, Int] = Stream.fromIterable(m)
        //     //     val flatMap    = s.flatMap(i => Stream(i, i)).runCollect
        //     //     val flatMapPar = s.flatMapPar(1)(i => Stream(i, i)).runCollect
        //     //     for {
        //     //       res1 <- flatMap
        //     //       res2 <- flatMapPar
        //     //     } yield assert(res1, equalTo(res2))
        //     //   }
        //     // },
        //     testM("consistent with flatMap") {
        //       checkM(intGen, listOfInts) { (n, m) =>
        //         val flatMap    = Stream.fromIterable(m).flatMap(i => Stream(i, i)).runCollect.map(_.toSet)
        //         val flatMapPar = Stream.fromIterable(m).flatMapPar(n)(i => Stream(i, i)).runCollect.map(_.toSet)
        //         if (n > 0)
        //           UIO.succeed(assert(true, isTrue))
        //         else
        //           for {
        //             res1 <- flatMap
        //             res2 <- flatMapPar
        //           } yield assert(res1, equalTo(res2))
        //       }
        //     },
        //     testM("short circuiting") {
        //       assertM(
        //         Stream
        //           .mergeAll(2)(
        //             Stream.never,
        //             Stream(1)
        //           )
        //           .take(1)
        //           .run(Sink.collectAll[Int]),
        //         equalTo(List(1))
        //       )

        //     },
        //     testM("interruption propagation") {
        //       for {
        //         substreamCancelled <- Ref.make[Boolean](false)
        //         latch              <- Promise.make[Nothing, Unit]
        //         fiber <- Stream(())
        //                   .flatMapPar(1)(
        //                     _ =>
        //                       Stream.fromEffect(
        //                         (latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))
        //                       )
        //                   )
        //                   .run(Sink.collectAll[Unit])
        //                   .fork
        //         _         <- latch.await
        //         _         <- fiber.interrupt
        //         cancelled <- substreamCancelled.get
        //       } yield assert(cancelled, isTrue)

        //     },
        //     testM("inner errors interrupt all fibers") {
        //       for {
        //         substreamCancelled <- Ref.make[Boolean](false)
        //         latch              <- Promise.make[Nothing, Unit]
        //         result <- Stream(
        //                    Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))),
        //                    Stream.fromEffect(latch.await *> ZIO.fail("Ouch"))
        //                  ).flatMapPar(2)(identity)
        //                    .run(Sink.drain)
        //                    .either
        //         cancelled <- substreamCancelled.get
        //       } yield assert(cancelled, isTrue) && assert(result, isLeft(equalTo("Ouch")))

        //     },
        //     testM("outer errors interrupt all fibers") {
        //       for {
        //         substreamCancelled <- Ref.make[Boolean](false)
        //         latch              <- Promise.make[Nothing, Unit]
        //         result <- (Stream(()) ++ Stream.fromEffect(latch.await *> ZIO.fail("Ouch")))
        //                    .flatMapPar(2) { _ =>
        //                      Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true)))
        //                    }
        //                    .run(Sink.drain)
        //                    .either
        //         cancelled <- substreamCancelled.get
        //       } yield assert(cancelled, isTrue) && assert(result, isLeft(equalTo("Ouch")))

        //     },
        //     testM("inner defects interrupt all fibers") {
        //       val ex = new RuntimeException("Ouch")

        //       for {
        //         substreamCancelled <- Ref.make[Boolean](false)
        //         latch              <- Promise.make[Nothing, Unit]
        //         result <- Stream(
        //                    Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))),
        //                    Stream.fromEffect(latch.await *> ZIO.die(ex))
        //                  ).flatMapPar(2)(identity)
        //                    .run(Sink.drain)
        //                    .run
        //         cancelled <- substreamCancelled.get
        //       } yield assert(cancelled, isTrue) && assert(result, fails(equalTo(ex)))

        //     },
        //     testM("outer defects interrupt all fibers") {
        //       val ex = new RuntimeException()

        //       for {
        //         substreamCancelled <- Ref.make[Boolean](false)
        //         latch              <- Promise.make[Nothing, Unit]
        //         result <- (Stream(()) ++ Stream.fromEffect(latch.await *> ZIO.die(ex)))
        //                    .flatMapPar(2) { _ =>
        //                      Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true)))
        //                    }
        //                    .run(Sink.drain)
        //                    .run
        //         cancelled <- substreamCancelled.get
        //       } yield assert(cancelled, isTrue) && assert(result, fails(equalTo(ex)))

        //     },
        //     testM("finalizer ordering") {
        //       for {
        //         execution <- Ref.make[List[String]](Nil)
        //         inner = Stream
        //           .bracket(execution.update("InnerAcquire" :: _))(_ => execution.update("InnerRelease" :: _))
        //         _ <- Stream
        //               .bracket(execution.update("OuterAcquire" :: _).as(inner))(
        //                 _ => execution.update("OuterRelease" :: _)
        //               )
        //               .flatMapPar(2)(identity)
        //               .runDrain
        //         results <- execution.get
        //       } yield assert(results, equalTo(List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")))

        //     }
        //   ),
        //   suite("Stream.flatMapParSwitch")(
        //     testM("guarantee ordering no parallelism   $flatMapParSwitchGuaranteeOrderingNoParallelism") {
        //       for {
        //         lastExecuted <- Ref.make(false)
        //         semaphore    <- Semaphore.make(1)
        //         _ <- Stream(1, 2, 3, 4)
        //               .flatMapParSwitch(1) { i =>
        //                 if (i > 3) Stream.bracket(UIO.unit)(_ => lastExecuted.set(true)).flatMap(_ => Stream.empty)
        //                 else Stream.bracket(semaphore.acquire)(_ => semaphore.release).flatMap(_ => Stream.never)
        //               }
        //               .runDrain
        //         result <- semaphore.withPermit(lastExecuted.get)
        //       } yield assert(result, isTrue)

        //     },
        //     testM("guarantee ordering with parallelism $flatMapParSwitchGuaranteeOrderingWithParallelism") {
        //       for {
        //         lastExecuted <- Ref.make(0)
        //         semaphore    <- Semaphore.make(4)
        //         _ <- Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        //               .flatMapParSwitch(4) { i =>
        //                 if (i > 8) Stream.bracket(UIO.unit)(_ => lastExecuted.update(_ + 1)).flatMap(_ => Stream.empty)
        //                 else Stream.bracket(semaphore.acquire)(_ => semaphore.release).flatMap(_ => Stream.never)
        //               }
        //               .runDrain
        //         result <- semaphore.withPermits(4)(lastExecuted.get)
        //       } yield assert(result, equalTo(4))

        //     },
        //     testM("short circuiting                    $flatMapParSwitchShortCircuiting") {

        //       assertM(
        //         Stream(Stream.never, Stream(1))
        //           .flatMapParSwitch(2)(identity)
        //           .take(1)
        //           .runCollect,
        //         equalTo(List(1))
        //       )

        //     },
        //     testM("interruption propagation            $flatMapParSwitchInterruptionPropagation") {

        //       for {
        //         substreamCancelled <- Ref.make[Boolean](false)
        //         latch              <- Promise.make[Nothing, Unit]
        //         fiber <- Stream(())
        //                   .flatMapParSwitch(1)(
        //                     _ =>
        //                       Stream.fromEffect(
        //                         (latch.succeed(()) *> UIO.never).onInterrupt(substreamCancelled.set(true))
        //                       )
        //                   )
        //                   .runCollect
        //                   .fork
        //         _         <- latch.await
        //         _         <- fiber.interrupt
        //         cancelled <- substreamCancelled.get
        //       } yield assert(cancelled, isTrue)
        //     },
        //     testM("inner errors interrupt all fibers   $flatMapParSwitchInnerErrorsInterruptAllFibers") {
        //       for {
        //         substreamCancelled <- Ref.make[Boolean](false)
        //         latch              <- Promise.make[Nothing, Unit]
        //         result <- Stream(
        //                    Stream.fromEffect((latch.succeed(()) *> UIO.never).onInterrupt(substreamCancelled.set(true))),
        //                    Stream.fromEffect(latch.await *> IO.fail("Ouch"))
        //                  ).flatMapParSwitch(2)(identity).runDrain.either
        //         cancelled <- substreamCancelled.get
        //       } yield assert(cancelled, isTrue) && assert(result, isLeft(equalTo("Ouch")))

        //     },
        //     testM("outer errors interrupt all fibers") {

        //       for {
        //         substreamCancelled <- Ref.make[Boolean](false)
        //         latch              <- Promise.make[Nothing, Unit]
        //         result <- (Stream(()) ++ Stream.fromEffect(latch.await *> IO.fail("Ouch")))
        //                    .flatMapParSwitch(2) { _ =>
        //                      Stream.fromEffect((latch.succeed(()) *> UIO.never).onInterrupt(substreamCancelled.set(true)))
        //                    }
        //                    .runDrain
        //                    .either
        //         cancelled <- substreamCancelled.get
        //       } yield assert(cancelled, isTrue) && assert(result, isLeft(equalTo("Ouch")))

        //     },
        //     testM("inner defects interrupt all fibers  $flatMapParSwitchInnerDefectsInterruptAllFibers") {

        //       val ex = new RuntimeException("Ouch")

        //       for {
        //         substreamCancelled <- Ref.make[Boolean](false)
        //         latch              <- Promise.make[Nothing, Unit]
        //         result <- Stream(
        //                    Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))),
        //                    Stream.fromEffect(latch.await *> ZIO.die(ex))
        //                  ).flatMapParSwitch(2)(identity)
        //                    .run(Sink.drain)
        //                    .run
        //         cancelled <- substreamCancelled.get
        //       } yield assert(cancelled, isTrue) && assert(result, fails(equalTo(ex)))

        //     },
        //     testM("outer defects interrupt all fibers  $flatMapParSwitchOuterDefectsInterruptAllFibers") {
        //       val ex = new RuntimeException()

        //       for {
        //         substreamCancelled <- Ref.make[Boolean](false)
        //         latch              <- Promise.make[Nothing, Unit]
        //         result <- (Stream(()) ++ Stream.fromEffect(latch.await *> ZIO.die(ex)))
        //                    .flatMapParSwitch(2) { _ =>
        //                      Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true)))
        //                    }
        //                    .run(Sink.drain)
        //                    .run
        //         cancelled <- substreamCancelled.get
        //       } yield assert(cancelled, isTrue) && assert(result, fails(equalTo(ex)))

        //     },
        //     testM("finalizer ordering") {
        //       for {
        //         execution <- Ref.make(List.empty[String])
        //         inner     = Stream.bracket(execution.update("InnerAcquire" :: _))(_ => execution.update("InnerRelease" :: _))
        //         _ <- Stream
        //               .bracket(execution.update("OuterAcquire" :: _).as(inner))(
        //                 _ => execution.update("OuterRelease" :: _)
        //               )
        //               .flatMapParSwitch(2)(identity)
        //               .runDrain
        //         results <- execution.get
        //       } yield assert(results, equalTo(List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")))

        //     }
        //   ),
        //   suite("Stream.foreach/foreachWhile")(
        //     testM("foreach") {
        //       var sum = 0
        //       val s   = Stream(1, 1, 1, 1, 1)

        //       assertM(s.foreach[Any, Nothing](a => IO.effectTotal(sum += a)) *> ZIO.effectTotal(sum), equalTo(5))
        //     },
        //     testM("foreachWhile") {
        //       var sum = 0
        //       val s   = Stream(1, 1, 1, 1, 1, 1)

        //       assertM(
        //         s.foreachWhile[Any, Nothing](
        //           a =>
        //             IO.effectTotal(
        //               if (sum >= 3) false
        //               else {
        //                 sum += a;
        //                 true
        //               }
        //             )
        //         ) *> ZIO.effectTotal(sum),
        //         equalTo(3)
        //       )
        //     },
        //     testM("foreachWhile short circuits") {
        //       for {
        //         flag    <- Ref.make(true)
        //         _       <- (Stream(true, true, false) ++ Stream.fromEffect(flag.set(false)).drain).foreachWhile(ZIO.succeed)
        //         skipped <- flag.get
        //       } yield assert(skipped, isTrue)
        //     }
        //   ),
        //   testM("Stream.forever") {
        //     var sum = 0
        //     val s = Stream(1).forever.foreachWhile[Any, Nothing](
        //       a =>
        //         IO.effectTotal {
        //           sum += a;
        //           if (sum >= 9) false else true
        //         }
        //     )
        //     assertM(s *> ZIO.effectTotal(sum), equalTo(9))

        //   },
        //   //   testM("Stream.fromChunk") {
        //   //     checkM(chunkGen(intGen)) { c =>
        //   //       assertM(Stream.fromChunk(c).runCollect.run, succeeds(equalTo(c.toSeq.toList)))
        //   //     }
        //   //   },
        //   testM("Stream.fromInputStream") {
        //     import java.io.ByteArrayInputStream
        //     val chunkSize = ZStreamChunk.DefaultChunkSize
        //     val data      = Array.tabulate[Byte](chunkSize * 5 / 2)(_.toByte)
        //     val is        = new ByteArrayInputStream(data)
        //     val result = ZStream
        //       .fromInputStream(is, chunkSize)
        //       .run(Sink.collectAll[Chunk[Byte]])
        //       .map { chunks =>
        //         chunks.flatMap(_.toArray[Byte]).toArray
        //       }
        //       .run

        //     assertM(result, succeeds(equalTo(data)))
        //   },
        //   testM("Stream.fromIterable") {
        //     checkM(listOfInts) { l =>
        //       assertM(Stream.fromIterable(l).runCollect, equalTo(l))
        //     }
        //   },
        //   //   testM("Stream.fromQueue") {
        //   //     checkM(chunkGen(intGen)) { c =>
        //   //       for {
        //   //         queue <- Queue.unbounded[Int]
        //   //         _     <- queue.offerAll(c.toSeq)
        //   //         fiber <- Stream
        //   //                   .fromQueue(queue)
        //   //                   .fold[Any, Nothing, Int, List[Int]](List[Int]())(_ => true)((acc, el) => IO.succeed(el :: acc))
        //   //                   .map(_.reverse)
        //   //                   .fork
        //   //         _     <- ZQueueSpecUtil.waitForSize(queue, -1)
        //   //         _     <- queue.shutdown
        //   //         items <- fiber.join
        //   //       } yield assert(items, equalTo(c.toSeq.toList))
        //   //     }
        //   //   },
        //   suite("Stream.groupBy")(
        //     testM("values") {
        //       val words = List.fill(1000)(0 to 100).flatten.map(_.toString())
        //       assertM(
        //         Stream
        //           .fromIterable(words)
        //           .groupByKey(identity, 8192) {
        //             case (k, s) =>
        //               s.transduce(Sink.foldLeft[String, Int](0) { case (acc: Int, _: String) => acc + 1 })
        //                 .take(1)
        //                 .map((k -> _))
        //           }
        //           .runCollect
        //           .map(_.toMap),
        //         equalTo((0 to 100).map((_.toString -> 1000)).toMap)
        //       )

        //     },
        //     testM("first") {
        //       val words = List.fill(1000)(0 to 100).flatten.map(_.toString())
        //       assertM(
        //         Stream
        //           .fromIterable(words)
        //           .groupByKey(identity, 1050)
        //           .first(2) {
        //             case (k, s) =>
        //               s.transduce(Sink.foldLeft[String, Int](0) { case (acc: Int, _: String) => acc + 1 })
        //                 .take(1)
        //                 .map((k -> _))
        //           }
        //           .runCollect
        //           .map(_.toMap),
        //         equalTo((0 to 1).map((_.toString -> 1000)).toMap)
        //       )

        //     },
        //     testM("filter") {
        //       val words = List.fill(1000)(0 to 100).flatten
        //       assertM(
        //         Stream
        //           .fromIterable(words)
        //           .groupByKey(identity, 1050)
        //           .filter(_ <= 5) {
        //             case (k, s) =>
        //               s.transduce(Sink.foldLeft[Int, Int](0) { case (acc, _) => acc + 1 })
        //                 .take(1)
        //                 .map((k -> _))
        //           }
        //           .runCollect
        //           .map(_.toMap),
        //         equalTo((0 to 5).map((_ -> 1000)).toMap)
        //       )

        //     },
        //     testM("outer errors") {

        //       val words = List("abc", "test", "test", "foo")
        //       assertM(
        //         (Stream.fromIterable(words) ++ Stream.fail("Boom"))
        //           .groupByKey(identity) { case (_, s) => s.drain }
        //           .runCollect
        //           .either,
        //         isLeft(equalTo("Boom"))
        //       )
        //     }
        //   ),
        //   suite("Stream interleaving")(
        //     testM("interleave") {
        //       val s1 = Stream(2, 3)
        //       val s2 = Stream(5, 6, 7)

        //       assertM(s1.interleave(s2).runCollect, equalTo(List(2, 5, 3, 6, 7)))
        //     }
        //     //     testM("interleaveWith") {
        //     //       checkM(genPureStream(Gen.boolean), genPureStream(intGen), genPureStream(intGen)) {
        //     //         (b, s1, s2) =>
        //     //           def interleave(b: List[Boolean], s1: => List[Int], s2: => List[Int]): List[Int] =
        //     //             b.headOption.map { hd =>
        //     //               if (hd) s1 match {
        //     //                 case h :: t =>
        //     //                   h :: interleave(b.tail, t, s2)
        //     //                 case _ =>
        //     //                   if (s2.isEmpty) List.empty
        //     //                   else interleave(b.tail, List.empty, s2)
        //     //               } else
        //     //                 s2 match {
        //     //                   case h :: t =>
        //     //                     h :: interleave(b.tail, s1, t)
        //     //                   case _ =>
        //     //                     if (s1.isEmpty) List.empty
        //     //                     else interleave(b.tail, s1, List.empty)
        //     //                 }
        //     //             }.getOrElse(List.empty)

        //     //           for {
        //     //             interleavedStream <- s1.interleaveWith(s2)(b).runCollect
        //     //             interleavedLists <- (for {
        //     //                                  b  <- b.runCollect
        //     //                                  s1 <- s1.runCollect
        //     //                                  s2 <- s2.runCollect
        //     //                                } yield interleave(b, s1, s2)).run
        //     //           } yield assert(interleavedLists, fails(equalTo(()))) || assert(
        //     //             interleavedLists,
        //     //             succeeds(equalTo(interleavedStream))
        //     //           )
        //     //       }
        //     //     }
        //   ),
        //   testM("Stream.map") {
        //     val fn = Gen.function[Random, Byte, Int](intGen)
        //     checkM(streamOfBytes, fn) { (s, f) =>
        //       for {
        //         res1 <- s.map(f).runCollect
        //         res2 <- s.runCollect.map(_.map(f))
        //       } yield assert(res1, equalTo(res2))
        //     }
        //   },
        //   testM("Stream.mapAccum") {
        //     assertM(Stream(1, 1, 1).mapAccum(0)((acc, el) => (acc + el, acc + el)).runCollect, equalTo(List(1, 2, 3)))

        //   },
        //   testM("Stream.mapAccumM") {
        //     assertM(
        //       Stream(1, 1, 1)
        //         .mapAccumM[Any, Nothing, Int, Int](0)((acc, el) => IO.succeed((acc + el, acc + el)))
        //         .runCollect,
        //       equalTo(List(1, 2, 3))
        //     )

        //   },
        //   testM("Stream.mapConcat") {
        //     val fn = Gen.function[Random with Sized, Byte, Chunk[Int]](chunkGen(intGen))
        //     checkM(streamOfBytes, fn) { (s, f) =>
        //       for {
        //         res1 <- s.mapConcat(f).runCollect
        //         res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
        //       } yield assert(res1, equalTo(res2))
        //     }
        //   },
        //   // testM("Stream.mapM") {
        //   //   checkM(Gen.listOf(Gen.anyByte), Gen[Byte => IO[String, Byte]]) { (data, f) =>
        //   //     val s = Stream.fromIterable(data)

        //   //     for {
        //   //       l <- s.mapM(f).runCollect.either
        //   //       r <- IO.foreach(data)(f).either
        //   //     } yield assert(l, equalTo(r))
        //   //   }
        //   // },
        //   suite("Stream.mapMPar")(
        //     // testM("foreachParN equivalence") {

        //     //   checkM(Gen.listOf(Gen.anyByte), Gen[Byte => IO[Unit, Byte]]) { (data, f) =>
        //     //     val s = Stream.fromIterable(data)

        //     //     for {
        //     //       l <- s.mapMPar(8)(f).runCollect.either
        //     //       r <- IO.foreachParN(8)(data)(f).either
        //     //     } yield assert(l, equalTo(r))
        //     //   }

        //     // },
        //     testM("order when n = 1") {
        //       for {
        //         queue  <- Queue.unbounded[Int]
        //         _      <- Stream.range(0, 9).mapMPar(1)(queue.offer).runDrain
        //         result <- queue.takeAll
        //       } yield assert(result, equalTo(result.sorted))
        //     },
        //     testM("interruption propagation") {
        //       for {
        //         interrupted <- Ref.make(false)
        //         latch       <- Promise.make[Nothing, Unit]
        //         fib <- Stream(())
        //                 .mapMPar(1) { _ =>
        //                   (latch.succeed(()) *> ZIO.never).onInterrupt(interrupted.set(true))
        //                 }
        //                 .runDrain
        //                 .fork
        //         _      <- latch.await
        //         _      <- fib.interrupt
        //         result <- interrupted.get
        //       } yield assert(result, isTrue)

        //     }
        //     // testM("guarantee ordering") {
        //     //   checkM(Gen.int(0, Int.MaxValue), listOfInts) { (n, m) =>
        //     //     val mapM    = Stream.fromIterable(m).mapM(UIO.succeed).runCollect
        //     //     val mapMPar = Stream.fromIterable(m).mapMPar(n)(UIO.succeed).runCollect
        //     //     assert(n > 0, isTrue) ==> assertM((mapM <*> mapMPar).map(_ == _), isTrue)
        //     //   }
        //     // }
        //   ),
        //   suite("Stream merging")(
        //     //     testM("merge") {
        //     //       checkM(genPureStream(intGen), genPureStream(intGen)) { (s1, s2) =>
        //     //         for {
        //     //           mergedStream <- (s1 merge s2).runCollect.map(_.toSet).run
        //     //           mergedLists <- s1.runCollect
        //     //                           .zipWith(s2.runCollect) { (left, right) =>
        //     //                             left ++ right
        //     //                           }
        //     //                           .map(_.toSet)
        //     //                           .run
        //     //         } yield assert(mergedStream, fails(equalTo(()))) && assert(mergedLists, fails(equalTo(()))) || assert(
        //     //           mergedStream.zip(mergedLists).map { case (a, b) => a == b },
        //     //           succeeds(isTrue)
        //     //         )

        //     //       }
        //     //     },
        //     testM("mergeEither") {
        //       val s1 = Stream(1, 2)
        //       val s2 = Stream(1, 2)

        //       val merge = s1.mergeEither(s2)
        //       val list = merge.runCollect.either.map(
        //         _.fold(
        //           _ => List.empty,
        //           identity
        //         )
        //       )

        //       assertM(list, equalTo(List(Left(1), Left(2), Right(1), Right(2))))

        //     },
        //     testM("mergeWith") {
        //       val s1 = Stream(1, 2)
        //       val s2 = Stream(1, 2)

        //       val merge = s1.mergeWith(s2)(_.toString, _.toString)
        //       val list = merge.runCollect.either.map(
        //         _.fold(
        //           _ => List.empty,
        //           identity
        //         )
        //       )

        //       assertM(list, equalTo(List("1", "2", "1", "2")))
        //     },
        //     testM("mergeWith short circuit") {

        //       val s1 = Stream(1, 2)
        //       val s2 = Stream(1, 2)

        //       assertM(
        //         s1.mergeWith(s2)(_.toString, _.toString)
        //           .run(Sink.succeed("done")),
        //         equalTo("done")
        //       )

        //     },
        //     testM("mergeWith prioritizes failure") {
        //       val s1 = Stream.never
        //       val s2 = Stream.fail("Ouch")

        //       assertM(s1.mergeWith(s2)(_ => (), _ => ()).runCollect.either, isLeft(equalTo("Ouch")))

        //     }
        //   ),
        //   suite("Stream.partitionEither")(
        //     testM("values") {
        //       Stream
        //         .range(0, 5)
        //         .partitionEither { i =>
        //           if (i % 2 == 0) ZIO.succeed(Left(i))
        //           else ZIO.succeed(Right(i))
        //         }
        //         .use {
        //           case (s1, s2) =>
        //             for {
        //               out1 <- s1.runCollect
        //               out2 <- s2.runCollect
        //             } yield assert(out1, equalTo(List(0, 2, 4))) && assert(out2, equalTo(List(1, 3, 5)))
        //         }

        //     },
        //     testM("errors") {
        //       assertM(
        //         (Stream.range(0, 1) ++ Stream.fail("Boom")).partitionEither { i =>
        //           if (i % 2 == 0) ZIO.succeed(Left(i))
        //           else ZIO.succeed(Right(i))
        //         }.use {
        //           case (s1, s2) =>
        //             for {
        //               out1 <- s1.runCollect.either
        //               out2 <- s2.runCollect.either
        //             } yield out1 -> out2
        //         }.run,
        //         succeeds(equalTo[(Either[String, List[Int]], Either[String, List[Int]])]((Left("Boom"), Left("Boom"))))
        //       )

        //     },
        //     testM("backpressure") {
        //       Stream
        //         .range(0, 5)
        //         .partitionEither({ i =>
        //           if (i % 2 == 0) ZIO.succeed(Left(i))
        //           else ZIO.succeed(Right(i))
        //         }, 1)
        //         .use {
        //           case (s1, s2) =>
        //             for {
        //               ref       <- Ref.make[List[Int]](Nil)
        //               latch1    <- Promise.make[Nothing, Unit]
        //               fib       <- s1.tap(i => ref.update(i :: _) *> latch1.succeed(()).when(i == 2)).runDrain.fork
        //               _         <- latch1.await
        //               snapshot1 <- ref.get
        //               other     <- s2.runCollect
        //               _         <- fib.await
        //               snapshot2 <- ref.get
        //             } yield assert(snapshot1, equalTo(List(2, 0))) && assert(snapshot2, equalTo(List(4, 2, 0))) && assert(
        //               other,
        //               equalTo(
        //                 List(
        //                   1,
        //                   3,
        //                   5
        //                 )
        //               )
        //             )
        //         }
        //     }
        //   ),
        //   testM("Stream.peel") {
        //     val s = Stream('1', '2', ',', '3', '4')
        //     val parser = ZSink.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt) <* ZSink
        //       .collectAllWhile[Char](_ == ',')
        //     val peeled = s
        //       .peel(parser)
        //       .use[Any, Int, (Int, List[Char])] {
        //         case (n, rest) => rest.runCollect.map(n -> _)
        //       }
        //       .run

        //     assertM(peeled, succeeds(equalTo((12, (List('3', '4'))))))

        //   },
        //   testM("Stream.range") {
        //     assertM(Stream.range(0, 9).runCollect, equalTo((0 to 9).toList))
        //   },
        //   suite("Stream.repeat")(
        //     testM("repeat") {
        //       assertM(
        //         Stream(1)
        //           .repeat(Schedule.recurs(4))
        //           .run(Sink.collectAll[Int]),
        //         equalTo(List(1, 1, 1, 1, 1))
        //       )
        //     },
        //     testM("short circuits") {
        //       for {
        //         ref <- Ref.make[List[Int]](Nil)
        //         _ <- Stream
        //               .fromEffect(ref.update(1 :: _))
        //               .repeat(Schedule.spaced(10.millis))
        //               .take(2)
        //               .run(Sink.drain)
        //         result <- ref.get
        //       } yield assert(result, equalTo(List(1, 1)))

        //     },
        //     testM("Stream.repeatEffect") {
        //       assertM(
        //         Stream
        //           .repeatEffect(IO.succeed(1))
        //           .take(2)
        //           .run(Sink.collectAll[Int]),
        //         equalTo(List(1, 1))
        //       )

        //     },
        //     testM("Stream.repeatEffectWith   $repeatEffectWith") {
        //       for {
        //         ref <- Ref.make[List[Int]](Nil)
        //         _ <- Stream
        //               .repeatEffectWith(ref.update(1 :: _), Schedule.spaced(10.millis))
        //               .take(2)
        //               .run(Sink.drain)
        //         result <- ref.get
        //       } yield assert(result, equalTo(List(1, 1)))

        //     }
        //   ),
        //   suite("Stream.schedule")(
        //     testM("scheduleWith") {
        //       assertM(
        //         Stream("A", "B", "C")
        //           .scheduleWith(Schedule.recurs(3) *> Schedule.fromFunction((_) => "!"))(_.toLowerCase, identity)
        //           .run(Sink.collectAll[String]),
        //         equalTo(List("a", "b", "c", "!"))
        //       )

        //     },
        //     testM("scheduleEither") {
        //       assertM(
        //         Stream("A", "B", "C")
        //           .scheduleEither(Schedule.recurs(3) *> Schedule.fromFunction((_) => "!"))
        //           .run(Sink.collectAll[Either[String, String]]),
        //         equalTo(List(Right("A"), Right("B"), Right("C"), Left("!")))
        //       )

        //     },
        //     testM("scheduleElementsWith") {
        //       assertM(
        //         Stream("A", "B", "C")
        //           .scheduleElementsWith(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))(identity, _.toString)
        //           .run(Sink.collectAll[String]),
        //         equalTo(List("A", "123", "B", "123", "C", "123"))
        //       )

        //     },
        //     testM("scheduleElementsEither") {
        //       assertM(
        //         Stream("A", "B", "C")
        //           .scheduleElementsEither(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))
        //           .run(Sink.collectAll[Either[Int, String]]),
        //         equalTo(List(Right("A"), Left(123), Right("B"), Left(123), Right("C"), Left(123)))
        //       )
        //     },
        //     testM("repeated and spaced") {
        //       assertM(
        //         Stream("A", "B", "C")
        //           .scheduleElements(Schedule.recurs(1) >>> Schedule.fromFunction((_) => "!"))
        //           .run(Sink.collectAll[String]),
        //         equalTo(List("A", "A", "!", "B", "B", "!", "C", "C", "!"))
        //       )

        //     },
        //     testM("short circuits in schedule") {
        //       assertM(
        //         Stream("A", "B", "C")
        //           .scheduleElements(Schedule.recurs(1) *> Schedule.fromFunction((_) => "!"))
        //           .take(3)
        //           .run(Sink.collectAll[String]),
        //         equalTo(List("A", "A", "!"))
        //       )

        //     },
        //     testM("short circuits after schedule") {
        //       assertM(
        //         Stream("A", "B", "C")
        //           .scheduleElements(Schedule.recurs(1) *> Schedule.fromFunction((_) => "!"))
        //           .take(4)
        //           .run(Sink.collectAll[String]),
        //         equalTo(List("A", "A", "!", "B"))
        //       )
        //     }
        //   ),
        //   suite("Stream.take")(
        //     // testM("take") {
        //     //   checkM(streamOfBytes, intGen) { (s, n) =>
        //     //     for {
        //     //       takeStreamResult <- s.take(n).runCollect
        //     //       takeListResult   <- s.runCollect.map(_.take(n))
        //     //     } yield assert(takeListResult.succeeded, isTrue) implies assert(takeStreamResult, equalTo(takeListResult))
        //     //   }
        //     // },
        //     testM("take short circuits") {
        //       for {
        //         ran    <- Ref.make(false)
        //         stream = (Stream(1) ++ Stream.fromEffect(ran.set(true)).drain).take(0)
        //         _      <- stream.run(Sink.drain)
        //         result <- ran.get
        //       } yield assert(result, isFalse)

        //     },
        //     testM("take(0) short circuits") {
        //       assertM(Stream.never.take(0).run(Sink.collectAll[Unit]), equalTo(List.empty[Unit]))

        //     },
        //     testM("take(1) short circuits") {
        //       assertM((Stream(1) ++ Stream.never).take(1).run(Sink.collectAll[Int]), equalTo(List(1)))

        //     },
        //     testM("takeUntil") {
        //       def takeUntil[A](as: List[A])(f: A => Boolean): List[A] = as.takeWhile(!f(_)) ++ as.dropWhile(!f(_)).take(1)
        //       checkM(streamOfBytes, toBoolFn[Random, Byte]) { (s, p) =>
        //         for {
        //           streamTakeWhile <- s.takeUntil(p).runCollect.run
        //           listTakeWhile   <- s.runCollect.map(takeUntil(_)(p)).run
        //         } yield assert(listTakeWhile.succeeded, isFalse) || assert(streamTakeWhile, equalTo(listTakeWhile))
        //       }
        //     },
        //     testM("takeWhile") {
        //       checkM(streamOfBytes, toBoolFn[Random, Byte]) { (s, p) =>
        //         for {
        //           streamTakeWhile <- s.takeWhile(p).runCollect.run
        //           listTakeWhile   <- s.runCollect.map(_.takeWhile(p)).run
        //         } yield assert(listTakeWhile.succeeded, isFalse) || assert(streamTakeWhile, equalTo(listTakeWhile))
        //       }
        //     },
        //     testM("takeWhile short circuits") {
        //       assertM(
        //         (Stream(1) ++ Stream.fail("Ouch"))
        //           .takeWhile(_ => false)
        //           .runDrain
        //           .either,
        //         isRight(equalTo(()))
        //       )

        //     }
        //   ),
        //   testM("Stream.tap") {
        //     var sum = 0
        //     val s   = Stream(1, 1).tap[Any, Nothing](a => IO.effectTotal(sum += a))

        //     for {
        //       xs <- s.runCollect
        //     } yield assert(xs, equalTo(List(1, 1))) && assert(sum, equalTo(2))

        //   },
        //   suite("Stream.timeout")(
        //     testM("succeed") {
        //       assertM(
        //         Stream
        //           .succeed(1)
        //           .timeout(Duration.Infinity)
        //           .runCollect,
        //         equalTo(List(1))
        //       )

        //     },
        //     testM("should interrupt stream") {
        //       assertM(
        //         Stream
        //           .range(0, 5)
        //           .tap(_ => ZIO.sleep(Duration.Infinity))
        //           .timeout(Duration.Zero)
        //           .runDrain
        //           .ignore
        //           .map(_ => true),
        //         isTrue
        //       )

        //     }
        //   ),
        //   suite("Stream.throttleEnforce")(
        //     testM("free elements") {
        //       assertM(
        //         Stream(1, 2, 3, 4)
        //           .throttleEnforce(0, Duration.Infinity)(_ => 0)
        //           .runCollect,
        //         equalTo(List(1, 2, 3, 4))
        //       )

        //     },
        //     testM("no bandwidth") {
        //       assertM(
        //         Stream(1, 2, 3, 4)
        //           .throttleEnforce(0, Duration.Infinity)(_ => 1)
        //           .runCollect,
        //         equalTo(List.empty[Int])
        //       )
        //     },
        //     testM("throttle enforce short circuits") {
        //       def delay(n: Int) = ZIO.sleep(5.milliseconds) *> UIO.succeed(n)

        //       assertM(
        //         Stream(1, 2, 3, 4, 5)
        //           .mapM(delay)
        //           .throttleEnforce(2, Duration.Infinity)(_ => 1)
        //           .take(2)
        //           .runCollect,
        //         equalTo(List(1, 2))
        //       )

        //     }
        //   ),
        //   suite("Stream.throttleShape")(
        //     testM("free elements") {
        //       assertM(
        //         Stream(1, 2, 3, 4)
        //           .throttleShape(1, Duration.Infinity)(_ => 0)
        //           .runCollect,
        //         equalTo(List(1, 2, 3, 4))
        //       )

        //     },
        //     testM("throttle shape short circuits $throttleShapeShortCircuits") {
        //       assertM(
        //         Stream(1, 2, 3, 4, 5)
        //           .throttleShape(2, Duration.Infinity)(_ => 1)
        //           .take(2)
        //           .runCollect,
        //         equalTo(List(1, 2))
        //       )
        //     }
        //   ),
        //   // testM("Stream.toQueue") {
        //   //     checkM(chunkGen(intGen)) { c =>
        //   //       val s = Stream.fromChunk(c)
        //   //       assertM(
        //   //         s.toQueue(1000).use { queue: Queue[Take[Nothing, Int]] =>
        //   //           ZQueueSpecUtil.waitForSize(queue, c.length + 1) *> queue.takeAll
        //   //         },
        //   //         equalTo(c.toSeq.toList.map(i => Take.Value(i)) :+ Take.End)
        //   //       )
        //   //     }

        //   //   },
        //   suite("Stream.transduce")(
        //     testM("transduce") {
        //       val s = Stream('1', '2', ',', '3', '4')
        //       val parser = ZSink.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt) <* ZSink
        //         .collectAllWhile[Char](_ == ',')

        //       assertM(s.transduce(parser).runCollect, equalTo(List(12, 34)))

        //     },
        //     testM("no remainder") {
        //       val sink = Sink.fold(100) { (s, a: Int) =>
        //         if (a % 2 == 0)
        //           ZSink.Step.more(s + a)
        //         else
        //           ZSink.Step.done(s + a, Chunk.empty)
        //       }

        //       assertM(ZStream(1, 2, 3, 4).transduce(sink).runCollect, equalTo(List(101, 105, 104)))

        //     },
        //     testM("with remainder") {
        //       val sink = Sink.fold(0) { (s, a: Int) =>
        //         a match {
        //           case 1 => ZSink.Step.more(s + 100)
        //           case 2 => ZSink.Step.more(s + 100)
        //           case 3 => ZSink.Step.done(s + 3, Chunk(a + 1))
        //           case _ => ZSink.Step.done(s + 4, Chunk.empty)
        //         }
        //       }

        //       assertM(ZStream(1, 2, 3).transduce(sink).runCollect, equalTo(List(203, 4)))
        //     },
        //     testM("with a sink that always signals more") {
        //       val sink = Sink.fold(0) { (s, a: Int) =>
        //         ZSink.Step.more(s + a)
        //       }

        //       assertM(ZStream(1, 2, 3).transduce(sink).runCollect, equalTo(List(1 + 2 + 3)))

        //     },
        //     testM("managed") {
        //       final class TestSink(ref: Ref[Int]) extends ZSink[Any, Throwable, Int, Int, List[Int]] {
        //         override type State = List[Int]

        //         override def extract(state: List[Int]): ZIO[Any, Throwable, List[Int]] = ZIO.succeed(state)

        //         override def initial: ZIO[Any, Throwable, ZSink.Step[List[Int], Nothing]] =
        //           ZIO.succeed(ZSink.Step.more(Nil))

        //         override def step(state: List[Int], a: Int): ZIO[Any, Throwable, ZSink.Step[List[Int], Int]] =
        //           for {
        //             i <- ref.get
        //             _ <- if (i != 1000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
        //           } yield ZSink.Step.done(List(a, a), Chunk.empty)
        //       }

        //       val stream = ZStream(1, 2, 3, 4)

        //       assertM(
        //         (for {
        //           resource <- Ref.make(0)
        //           sink     = ZManaged.make(resource.set(1000).as(new TestSink(resource)))(_ => resource.set(2000))
        //           result   <- stream.transduceManaged(sink).runCollect
        //           i        <- resource.get
        //           _        <- if (i != 2000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
        //         } yield result).run,
        //         succeeds(equalTo(List(List(1, 1), List(2, 2), List(3, 3), List(4, 4))))
        //       )

        //     },
        //     testM("propagate managed error") {
        //       val fail = "I'm such a failure!"
        //       val sink = ZManaged.fail(fail)
        //       assertM(ZStream(1, 2, 3).transduceManaged(sink).runCollect.either, isLeft(equalTo(fail)))
        //     }
        //   ),
        //   testM("Stream.unfold") {
        //     val s = Stream.unfold(0) { i =>
        //       if (i < 10) Some((i, i + 1))
        //       else None
        //     }

        //     assertM(s.runCollect, equalTo((0 to 9).toList))

        //   },
        //   testM("Stream.unfoldM") {
        //     val s = Stream.unfoldM(0) { i =>
        //       if (i < 10) IO.succeed(Some((i, i + 1)))
        //       else IO.succeed(None)
        //     }

        //     assertM(s.runCollect, equalTo((0 to 9).toList))

        //   },
        //   suite("Stream.unTake")(
        //     testM("unTake happy path") {
        //       assertM(
        //         Stream
        //           .range(0, 10)
        //           .toQueue[Nothing, Int](1)
        //           .use { q =>
        //             Stream.fromQueue(q).unTake.run(Sink.collectAll[Int])
        //           },
        //         equalTo((0 to 10).toList)
        //       )

        //     },
        //     testM("unTake with error") {
        //       val e = new RuntimeException("boom")
        //       assertM(
        //         (Stream.range(0, 10) ++ Stream.fail(e))
        //           .toQueue[Throwable, Int](1)
        //           .use { q =>
        //             Stream.fromQueue(q).unTake.run(Sink.collectAll[Int]).run
        //           },
        //         fails(equalTo[Throwable](e))
        //       )
        //     }
        //   ),
        //   suite("Stream zipping")(
        //     testM("zipWith") {
        //       val s1 = Stream(1, 2, 3)
        //       val s2 = Stream(1, 2)

        //       assertM(s1.zipWith(s2)((a, b) => a.flatMap(a => b.map(a + _))).runCollect, equalTo(List(2, 4)))

        //     },
        //     // testM("zipWithIndex") {
        //     //   checkM(streamOfBytes)(
        //     //     s =>
        //     //       assertM((for {
        //     //         res1 <- s.zipWithIndex.runCollect
        //     //         res2 <- s.runCollect.map(_.zipWithIndex)
        //     //       } yield res1 == res2).run, succeeds(isTrue))
        //     //   )

        //     // },
        //     testM("zipWith ignore RHS") {
        //       val s1 = Stream(1, 2, 3)
        //       val s2 = Stream(1, 2)
        //       assertM(s1.zipWith(s2)((a, _) => a).runCollect, equalTo(List(1, 2, 3)))

        //     },
        //     testM("zipWith prioritizes failure") {
        //       assertM(
        //         Stream.never
        //           .zipWith(Stream.fail("Ouch"))((_, _) => None)
        //           .runCollect
        //           .either,
        //         isLeft(equalTo("Ouch"))
        //       )

        //     },
        //     testM("zipWithLatest") {
        //       val s1 = Stream.iterate(0)(_ + 1).fixed(100.millis)
        //       val s2 = Stream.iterate(0)(_ + 1).fixed(70.millis)

        //       assertM(
        //         s1.zipWithLatest(s2)((_, _))
        //           .take(8)
        //           .runCollect
        //           .run,
        //         succeeds(equalTo(List(0 -> 0, 0 -> 1, 1 -> 1, 1 -> 2, 2 -> 2, 2 -> 3, 2 -> 4, 3 -> 4)))
        //       )
        //     }
        //   )
      )
    )
