package zio
package stm

import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect.nonFlaky
import zio.test._
import zio.test._
import zio.test.environment.Live

object ZSTMSpec extends ZIOBaseSpec {

  def spec = suite("ZSTMSpec")(
    suite("Using `STM.atomically` to perform different computations and call:")(
      testM("`STM.succeed` to make a successful computation and check the value") {
        assertM(STM.succeedNow("Hello World").commit)(equalTo("Hello World"))
      },
      testM("`STM.failed` to make a failed computation and check the value") {
        assertM(STM.failNow("Bye bye World").commit.run)(fails(equalTo("Bye bye World")))
      },
      suite("`either` to convert")(
        testM("A successful computation into Right(a)") {
          import zio.CanFail.canFail
          assertM(STM.succeedNow(42).either.commit)(isRight(equalTo(42)))
        },
        testM("A failed computation into Left(e)") {
          assertM(STM.failNow("oh no!").either.commit)(isLeft(equalTo("oh no!")))
        }
      ),
      suite("fallback")(
        testM("Tries this effect first") {
          import zio.CanFail.canFail
          assertM(STM.succeedNow(1).fallback(2).commit)(equalTo(1))
        },
        testM("If it fails, succeeds with the specified value") {
          assertM(STM.failNow("fail").fallback(1).commit)(equalTo(1))
        }
      ),
      testM("`fold` to handle both failure and success") {
        import zio.CanFail.canFail
        val stm = for {
          s <- STM.succeedNow("Yes!").fold(_ => -1, _ => 1)
          f <- STM.failNow("No!").fold(_ => -1, _ => 1)
        } yield (s, f)
        assertM(stm.commit)(equalTo((1, -1)))
      },
      testM("`foldM` to fold over the `STM` effect, and handle failure and success") {
        import zio.CanFail.canFail
        val stm = for {
          s <- STM.succeedNow("Yes!").foldM(_ => STM.succeedNow("No!"), STM.succeedNow)
          f <- STM.failNow("No!").foldM(STM.succeedNow, _ => STM.succeedNow("Yes!"))
        } yield (s, f)
        assertM(stm.commit)(equalTo(("Yes!", "No!")))
      },
      suite("ifM")(
        testM("runs `onTrue` if result of `b` is `true`") {
          val transaction = ZSTM.ifM(ZSTM.succeedNow(true))(ZSTM.succeedNow(true), ZSTM.succeedNow(false))
          assertM(transaction.commit)(isTrue)
        },
        testM("runs `onFalse` if result of `b` is `false`") {
          val transaction = ZSTM.ifM(ZSTM.succeedNow(false))(ZSTM.succeedNow(true), ZSTM.succeedNow(false))
          assertM(transaction.commit)(isFalse)
        },
        testM("infers correctly") {
          trait R
          trait R1 extends R
          trait E1
          trait E extends E1
          trait A
          val b: ZSTM[R, E, Boolean]   = ZSTM.succeedNow(true)
          val onTrue: ZSTM[R1, E1, A]  = ZSTM.succeedNow(new A {})
          val onFalse: ZSTM[R1, E1, A] = ZSTM.succeedNow(new A {})
          val _                        = ZSTM.ifM(b)(onTrue, onFalse)
          ZIO.succeed(assertCompletes)
        }
      ),
      testM("`mapError` to map from one error to another") {
        assertM(STM.failNow(-1).mapError(_ => "oh no!").commit.run)(fails(equalTo("oh no!")))
      },
      testM("`orElse` to try another computation when the computation is failed") {
        import zio.CanFail.canFail
        (for {
          s <- STM.succeedNow(1) orElse STM.succeedNow(2)
          f <- STM.failNow("failed") orElse STM.succeedNow("try this")
        } yield assert((s, f))(equalTo((1, "try this")))).commit
      },
      suite("`option` to convert:")(
        testM("A successful computation into Some(a)") {
          import zio.CanFail.canFail
          assertM(STM.succeedNow(42).option.commit)(isSome(equalTo(42)))
        },
        testM("A failed computation into None") {
          assertM(STM.failNow("oh no!").option.commit)(isNone)
        }
      ),
      testM("`zip` to return a tuple of two computations") {
        assertM((STM.succeedNow(1) <*> STM.succeedNow('A')).commit)(equalTo((1, 'A')))
      },
      testM("`zipWith` to perform an action to two computations") {
        assertM(STM.succeedNow(578).zipWith(STM.succeedNow(2))(_ + _).commit)(equalTo(580))
      }
    ),
    suite("Make a new `TRef` and")(
      testM("get its initial value") {
        (for {
          intVar <- TRef.make(14)
          v      <- intVar.get
        } yield assert(v)(equalTo(14))).commit
      },
      testM("set a new value") {
        (for {
          intVar <- TRef.make(14)
          _      <- intVar.set(42)
          v      <- intVar.get
        } yield assert(v)(equalTo(42))).commit
      }
    ),
    suite("Using `STM.atomically` perform concurrent computations")(
      testM("increment `TRef` 100 times in 100 fibers") {
        for {
          tVar  <- TRef.makeCommit(0)
          fiber <- ZIO.forkAll(List.fill(10)(incrementVarN(99, tVar)))
          _     <- fiber.join
          value <- tVar.get.commit
        } yield assert(value)(equalTo(1000))
      },
      testM(
        "compute a `TRef` from 2 variables, increment the first `TRef` and decrement the second `TRef` in different fibers"
      ) {
        for {
          tVars <- STM
                    .atomically(
                      TRef.make(10000) <*> TRef.make(0) <*> TRef.make(0)
                    )
          tvar1 <*> tvar2 <*> tvar3 = tVars
          fiber                     <- ZIO.forkAll(List.fill(10)(compute3VarN(99, tvar1, tvar2, tvar3)))
          _                         <- fiber.join
          value                     <- tvar3.get.commit
        } yield assert(value)(equalTo(10000))
      }
    ),
    suite("Using `STM.atomically` perform concurrent computations that")(
      suite("have a simple condition lock should suspend the whole transaction and")(
        testM("resume directly when the condition is already satisfied") {
          for {
            tvar1 <- TRef.makeCommit(10)
            tvar2 <- TRef.makeCommit("Failed!")
            join <- (for {
                     v1 <- tvar1.get
                     _  <- STM.check(v1 > 0)
                     _  <- tvar2.set("Succeeded!")
                     v2 <- tvar2.get
                   } yield v2).commit
          } yield assert(join)(equalTo("Succeeded!"))
        },
        testM(
          "resume directly when the condition is already satisfied and change again the tvar with non satisfying value, the transaction shouldn't be suspended."
        ) {
          for {
            tvar <- TRef.makeCommit(42)
            join <- tvar.get.filter(_ == 42).commit
            _    <- tvar.set(9).commit
            v    <- tvar.get.commit
          } yield assert(v)(equalTo(9)) && assert(join)(equalTo(42))
        },
        testM("resume after satisfying the condition") {
          val barrier = new UnpureBarrier
          for {
            done  <- Promise.make[Nothing, Unit]
            tvar1 <- TRef.makeCommit(0)
            tvar2 <- TRef.makeCommit("Failed!")
            fiber <- (STM.atomically {
                      for {
                        v1 <- tvar1.get
                        _  <- STM.succeedNow(barrier.open())
                        _  <- STM.check(v1 > 42)
                        _  <- tvar2.set("Succeeded!")
                        v2 <- tvar2.get
                      } yield v2
                    } <* done
                      .succeed(())).fork
            _    <- barrier.await
            old  <- tvar2.get.commit
            _    <- tvar1.set(43).commit
            _    <- done.await
            newV <- tvar2.get.commit
            join <- fiber.join
          } yield assert(old)(equalTo("Failed!")) && assert(newV)(equalTo(join))
        },
        suite("have a complex condition lock should suspend the whole transaction and")(
          testM("resume directly when the condition is already satisfied") {
            for {
              sender    <- TRef.makeCommit(100)
              receiver  <- TRef.makeCommit(0)
              _         <- transfer(receiver, sender, 150).fork
              _         <- sender.update(_ + 100).commit
              _         <- sender.get.filter(_ == 50).commit
              senderV   <- sender.get.commit
              receiverV <- receiver.get.commit
            } yield assert(senderV)(equalTo(50)) && assert(receiverV)(equalTo(150))
          }
        )
      ),
      suite("transfer an amount to a sender and send it back the account should contains the amount to transfer")(
        testM("run both transactions sequentially in 10 fibers.") {
          for {
            sender     <- TRef.makeCommit(100)
            receiver   <- TRef.makeCommit(0)
            toReceiver = transfer(receiver, sender, 150)
            toSender   = transfer(sender, receiver, 150)
            f          <- ZIO.forkAll(List.fill(10)(toReceiver *> toSender))
            _          <- sender.update(_ + 50).commit
            _          <- f.join
            senderV    <- sender.get.commit
            receiverV  <- receiver.get.commit
          } yield assert(senderV)(equalTo(150)) && assert(receiverV)(equalTo(0))
        },
        testM("run 10 transactions `toReceiver` and 10 `toSender` concurrently.") {
          for {
            sender     <- TRef.makeCommit(50)
            receiver   <- TRef.makeCommit(0)
            toReceiver = transfer(receiver, sender, 100)
            toSender   = transfer(sender, receiver, 100)
            f1         <- IO.forkAll(List.fill(10)(toReceiver))
            f2         <- IO.forkAll(List.fill(10)(toSender))
            _          <- sender.update(_ + 50).commit
            _          <- f1.join
            _          <- f2.join
            senderV    <- sender.get.commit
            receiverV  <- receiver.get.commit
          } yield assert(senderV)(equalTo(100)) && assert(receiverV)(equalTo(0))
        },
        testM("run transactions `toReceiver` 10 times and `toSender` 10 times each in 100 fibers concurrently.") {
          for {
            sender       <- TRef.makeCommit(50)
            receiver     <- TRef.makeCommit(0)
            toReceiver10 = transfer(receiver, sender, 100).repeat(Schedule.recurs(9))
            toSender10   = transfer(sender, receiver, 100).repeat(Schedule.recurs(9))
            f            <- toReceiver10.zipPar(toSender10).fork
            _            <- sender.update(_ + 50).commit
            _            <- f.join
            senderV      <- sender.get.commit
            receiverV    <- receiver.get.commit
          } yield assert(senderV)(equalTo(100)) && assert(receiverV)(equalTo(0))
        }
      ),
      testM(
        "Perform atomically a single transaction that has a tvar for 20 fibers, each one checks the value and increment it."
      ) {
        for {
          tvar <- TRef.makeCommit(0)
          fiber <- IO.forkAll(
                    (0 to 20).map(i =>
                      (for {
                        v <- tvar.get
                        _ <- STM.check(v == i)
                        _ <- tvar.update(_ + 1)
                      } yield ()).commit
                    )
                  )
          _ <- fiber.join
          v <- tvar.get.commit
        } yield assert(v)(equalTo(21))
      },
      suite("Perform atomically a transaction with a condition that couldn't be satisfied, it should be suspended")(
        testM("interrupt the fiber should terminate the transaction") {
          val barrier = new UnpureBarrier
          for {
            tvar <- TRef.makeCommit(0)
            fiber <- (for {
                      v <- tvar.get
                      _ <- STM.succeedNow(barrier.open())
                      _ <- STM.check(v > 0)
                      _ <- tvar.update(10 / _)
                    } yield ()).commit.fork
            _ <- barrier.await
            _ <- fiber.interrupt
            _ <- tvar.set(10).commit
            v <- liveClockSleep(10.millis) *> tvar.get.commit
          } yield assert(v)(equalTo(10))
        },
        testM(
          "interrupt the fiber that has executed the transaction in 100 different fibers, should terminate all transactions"
        ) {
          val barrier = new UnpureBarrier
          for {
            tvar <- TRef.makeCommit(0)
            fiber <- IO.forkAll(List.fill(100)((for {
                      v <- tvar.get
                      _ <- STM.succeedNow(barrier.open())
                      _ <- STM.check(v < 0)
                      _ <- tvar.set(10)
                    } yield ()).commit))
            _ <- barrier.await
            _ <- fiber.interrupt
            _ <- tvar.set(-1).commit
            v <- liveClockSleep(10.millis) *> tvar.get.commit
          } yield assert(v)(equalTo(-1))
        },
        testM("interrupt the fiber and observe it, it should be resumed with Interrupted Cause") {
          for {
            selfId  <- ZIO.fiberId
            v       <- TRef.makeCommit(1)
            f       <- v.get.flatMap(v => STM.check(v == 0)).commit.fork
            _       <- f.interrupt
            observe <- f.join.sandbox.either
          } yield assert(observe)(isLeft(equalTo(Cause.interrupt(selfId))))
        }
      ),
      testM("Using `collect` filter and map simultaneously the value produced by the transaction") {
        assertM(STM.succeedNow((1 to 20).toList).collect { case l if l.forall(_ > 0) => "Positive" }.commit)(
          equalTo("Positive")
        )
      },
      testM("Using `collectM` filter and map simultaneously the value produced by the transaction") {
        assertM(
          STM
            .succeed((1 to 20).toList)
            .collectM[Any, Nothing, String] { case l if l.forall(_ > 0) => STM.succeedNow("Positive") }
            .commit
        )(equalTo("Positive"))
      }
    ),
    testM("Permute 2 variables") {
      for {
        tvar1 <- TRef.makeCommit(1)
        tvar2 <- TRef.makeCommit(2)
        _     <- permutation(tvar1, tvar2).commit
        v1    <- tvar1.get.commit
        v2    <- tvar2.get.commit
      } yield assert(v1)(equalTo(2)) && assert(v2)(equalTo(1))
    },
    testM("Permute 2 variables in 100 fibers, the 2 variables should contains the same values") {
      for {
        tvar1 <- TRef.makeCommit(1)
        tvar2 <- TRef.makeCommit(2)
        oldV1 <- tvar1.get.commit
        oldV2 <- tvar2.get.commit
        f     <- IO.forkAll(List.fill(100)(permutation(tvar1, tvar2).commit))
        _     <- f.join
        v1    <- tvar1.get.commit
        v2    <- tvar2.get.commit
      } yield assert(v1)(equalTo(oldV1)) && assert(v2)(equalTo(oldV2))
    },
    testM(
      "Using `collectAll` collect a list of transactional effects to a single transaction that produces a list of values"
    ) {
      for {
        it    <- UIO((1 to 100).map(TRef.make(_)))
        tvars <- STM.collectAll(it).commit
        res   <- UIO.collectAllPar(tvars.map(_.get.commit))
      } yield assert(res)(equalTo((1 to 100).toList))
    },
    testM(
      "Using `foreach` perform an action in each value and return a single transaction that contains the result"
    ) {
      for {
        tvar      <- TRef.makeCommit(0)
        _         <- STM.foreach(1 to 100)(a => tvar.update(_ + a)).commit
        expectedV = (1 to 100).sum
        v         <- tvar.get.commit
      } yield assert(v)(equalTo(expectedV))
    },
    testM("Using `foreach_` performs actions in order") {
      val as = List(1, 2, 3, 4, 5)
      for {
        ref <- TRef.makeCommit(List.empty[Int])
        _   <- STM.foreach_(as)(a => ref.update(_ :+ a)).commit
        bs  <- ref.get.commit
      } yield assert(bs)(equalTo(as))
    },
    testM(
      "Using `orElseEither` tries 2 computations and returns either left if the left computation succeed or right if the right one succeed"
    ) {
      import zio.CanFail.canFail
      for {
        rightV  <- STM.failNow("oh no!").orElseEither(STM.succeedNow(42)).commit
        leftV1  <- STM.succeedNow(1).orElseEither(STM.succeedNow("No me!")).commit
        leftV2  <- STM.succeedNow(2).orElseEither(STM.failNow("No!")).commit
        failedV <- STM.failNow(-1).orElseEither(STM.failNow(-2)).commit.either
      } yield assert(rightV)(isRight(equalTo(42))) &&
        assert(leftV1)(isLeft(equalTo(1))) &&
        assert(leftV2)(isLeft(equalTo(2))) &&
        assert(failedV)(isLeft(equalTo(-2)))
    },
    suite("Failure must")(
      testM("rollback full transaction") {
        for {
          tvar <- TRef.makeCommit(0)
          e <- (for {
                _ <- tvar.update(_ + 10)
                _ <- STM.failNow("Error!")
              } yield ()).commit.either
          v <- tvar.get.commit
        } yield assert(e)(isLeft(equalTo("Error!"))) && assert(v)(equalTo(0))
      },
      testM("be ignored") {
        for {
          tvar <- TRef.makeCommit(0)
          e <- (for {
                _ <- tvar.update(_ + 10)
                _ <- STM.failNow("Error!")
              } yield ()).commit.ignore
          v <- tvar.get.commit
        } yield assert(e)(equalTo(())) && assert(v)(equalTo(0))
      }
    ),
    suite("orElse must")(
      testM("rollback left retry") {
        import zio.CanFail.canFail
        for {
          tvar  <- TRef.makeCommit(0)
          left  = tvar.update(_ + 100) *> STM.retry
          right = tvar.update(_ + 100).unit
          _     <- (left orElse right).commit
          v     <- tvar.get.commit
        } yield assert(v)(equalTo(100))
      },
      testM("rollback left failure") {
        for {
          tvar  <- TRef.makeCommit(0)
          left  = tvar.update(_ + 100) *> STM.failNow("Uh oh!")
          right = tvar.update(_ + 100).unit
          _     <- (left orElse right).commit
          v     <- tvar.get.commit
        } yield assert(v)(equalTo(100))
      },
      testM("local reset, not global") {
        for {
          ref <- TRef.make(0).commit
          result <- STM.atomically(for {
                     _       <- ref.set(2)
                     newVal1 <- ref.get
                     _       <- STM.partial(throw new RuntimeException).orElse(STM.unit)
                     newVal2 <- ref.get
                   } yield (newVal1, newVal2))
        } yield assert(result)(equalTo(2 -> 2))
      }
    ),
    suite("orElseFail")(
      testM("tries this effect first") {
        import zio.CanFail.canFail
        val transaction = ZSTM.succeedNow(true).orElseFail(false)
        assertM(transaction.commit)(isTrue)
      },
      testM("if it fails, fails with the specified error") {
        val transaction = ZSTM.failNow(false).orElseFail(true).fold(identity, _ => false)
        assertM(transaction.commit)(isTrue)
      }
    ),
    suite("orElseSucceed")(
      testM("tries this effect first") {
        import zio.CanFail.canFail
        val transaction = ZSTM.succeedNow(true).orElseSucceed(false)
        assertM(transaction.commit)(isTrue)
      },
      testM("if it succeeds, succeeds with the specified value") {
        val transaction = ZSTM.failNow(false).orElseSucceed(true)
        assertM(transaction.commit)(isTrue)
      }
    ),
    suite("when combinators")(
      testM("when true") {
        for {
          ref    <- TRef.make(false).commit
          result <- (STM.when(true)(ref.set(true)) *> ref.get).commit
        } yield assert(result)(equalTo(true))
      },
      testM("when false") {
        for {
          ref    <- TRef.make(false).commit
          result <- (STM.when(false)(ref.set(true)) *> ref.get).commit
        } yield assert(result)(equalTo(false))
      },
      testM("whenM true") {
        for {
          ref    <- TRef.make(0).commit
          isZero = ref.get.map(_ == 0)
          result <- (STM.whenM(isZero)(ref.update(_ + 1)) *> ref.get).commit
        } yield assert(result)(equalTo(1))
      },
      testM("whenM false") {
        for {
          ref       <- TRef.make(0).commit
          isNotZero = ref.get.map(_ != 0)
          result    <- (STM.whenM(isNotZero)(ref.update(_ + 1)) *> ref.get).commit
        } yield assert(result)(equalTo(0))
      }
    ),
    suite("STM issue 2073") {
      testM("read only STM shouldn't return partial state of concurrent read-write STM") {
        for {
          r0       <- TRef.makeCommit(0)
          r1       <- TRef.makeCommit(0)
          sumFiber <- r0.get.flatMap(v0 => r1.get.map(_ + v0)).commit.fork
          _        <- r0.update(_ + 1).flatMap(_ => r1.update(_ + 1)).commit
          sum      <- sumFiber.join
        } yield assert(sum)(equalTo(0) || equalTo(2))
      } @@ nonFlaky(5000)
    },
    suite("STM stack safety")(
      testM("long map chains") {
        assertM(chain(10000)(_.map(_ + 1)))(equalTo(10000))
      },
      testM("long collect chains") {
        assertM(chain(10000)(_.collect { case a: Int => a + 1 }))(equalTo(10000))
      },
      testM("long collectM chains") {
        assertM(chain(10000)(_.collectM { case a: Int => STM.succeedNow(a + 1) }))(equalTo(10000))
      },
      testM("long flatMap chains") {
        assertM(chain(10000)(_.flatMap(a => STM.succeedNow(a + 1))))(equalTo(10000))
      },
      testM("long fold chains") {
        import zio.CanFail.canFail
        assertM(chain(10000)(_.fold(_ => 0, _ + 1)))(equalTo(10000))
      },
      testM("long foldM chains") {
        import zio.CanFail.canFail
        assertM(chain(10000)(_.foldM(_ => STM.succeedNow(0), a => STM.succeedNow(a + 1))))(equalTo(10000))
      },
      testM("long mapError chains") {
        def chain(depth: Int): ZIO[Any, Int, Nothing] = {
          @annotation.tailrec
          def loop(n: Int, acc: STM[Int, Nothing]): ZIO[Any, Int, Nothing] =
            if (n <= 0) acc.commit else loop(n - 1, acc.mapError(_ + 1))

          loop(depth, STM.failNow(0))
        }

        assertM(chain(10000).run)(fails(equalTo(10000)))
      },
      testM("long orElse chains") {
        def chain(depth: Int): ZIO[Any, Int, Nothing] = {
          @annotation.tailrec
          def loop(n: Int, curr: Int, acc: STM[Int, Nothing]): ZIO[Any, Int, Nothing] =
            if (n <= 0) acc.commit
            else {
              val inc = curr + 1
              loop(n - 1, inc, acc.orElse(STM.failNow(inc)))
            }

          loop(depth, 0, STM.failNow(0))
        }

        assertM(chain(10000).run)(fails(equalTo(10000)))
      },
      testM("long provide chains") {
        assertM(chain(10000)(_.provide(0)))(equalTo(0))
      }
    ),
    suite("STM environment")(
      testM("access environment and provide it outside transaction") {
        STMEnv.make(0).flatMap { env =>
          ZSTM.accessM[STMEnv](_.ref.update(_ + 1)).commit.provide(env) *>
            assertM(env.ref.get.commit)(equalTo(1))
        }
      },
      testM("access environment and provide it inside transaction") {
        STMEnv.make(0).flatMap { env =>
          ZSTM.accessM[STMEnv](_.ref.update(_ + 1)).provide(env).commit *>
            assertM(env.ref.get.commit)(equalTo(1))
        }
      }
    ),
    testM("STM collectAll ordering") {
      val tx = for {
        tq  <- TQueue.bounded[Int](3)
        _   <- tq.offer(1)
        _   <- tq.offer(2)
        _   <- tq.offer(3)
        ans <- ZSTM.collectAll(List(tq.take, tq.take, tq.take))
      } yield ans
      assertM(tx.commit)(equalTo(List(1, 2, 3)))
    },
    suite("taps")(
      testM("tap should apply the transactional function to the effect result while keeping the effect itself") {
        val tx =
          for {
            refA <- TRef.make(10)
            refB <- TRef.make(0)
            a    <- refA.get.tap(v => refB.set(v + 1))
            b    <- refB.get
          } yield (a, b)

        assertM(tx.commit)(equalTo((10, 11)))
      }
    )
  )

  trait STMEnv {
    val ref: TRef[Int]
  }
  object STMEnv {
    def make(i: Int): UIO[STMEnv] =
      TRef
        .makeCommit(i)
        .map { ref0 =>
          new STMEnv {
            val ref = ref0
          }
        }
  }

  def unpureSuspend(ms: Long) = STM.succeed {
    val t0 = System.currentTimeMillis()
    while (System.currentTimeMillis() - t0 < ms) {}
  }

  class UnpureBarrier {
    private var isOpen = false
    def open(): Unit   = isOpen = true
    def await =
      ZIO
        .effectSuspend(ZIO.effect(if (isOpen) () else throw new Exception()))
        .eventually
  }

  def liveClockSleep(d: Duration) = Live.live(ZIO.sleep(d))

  def incrementVarN(n: Int, tvar: TRef[Int]): ZIO[clock.Clock, Nothing, Int] =
    STM
      .atomically(for {
        v <- tvar.get
        _ <- tvar.set(v + 1)
        v <- tvar.get
      } yield v)
      .repeat(Schedule.recurs(n) *> Schedule.identity)

  def compute3VarN(
    n: Int,
    tvar1: TRef[Int],
    tvar2: TRef[Int],
    tvar3: TRef[Int]
  ): ZIO[clock.Clock, Nothing, Int] =
    STM
      .atomically(for {
        v1 <- tvar1.get
        v2 <- tvar2.get
        _  <- tvar3.set(v1 + v2)
        v3 <- tvar3.get
        _  <- tvar1.set(v1 - 1)
        _  <- tvar2.set(v2 + 1)
      } yield v3)
      .repeat(Schedule.recurs(n) *> Schedule.identity)

  def transfer(receiver: TRef[Int], sender: TRef[Int], much: Int): UIO[Int] =
    STM.atomically {
      for {
        balance <- sender.get
        _       <- STM.check(balance >= much)
        _       <- receiver.update(_ + much)
        _       <- sender.update(_ - much)
        newAmnt <- receiver.get
      } yield newAmnt
    }

  def permutation(tvar1: TRef[Int], tvar2: TRef[Int]): STM[Nothing, Unit] =
    for {
      a <- tvar1.get
      b <- tvar2.get
      _ <- tvar1.set(b)
      _ <- tvar2.set(a)
    } yield ()

  def chain(depth: Int)(next: STM[Nothing, Int] => STM[Nothing, Int]): UIO[Int] = {
    @annotation.tailrec
    def loop(n: Int, acc: STM[Nothing, Int]): UIO[Int] =
      if (n <= 0) acc.commit else loop(n - 1, next(acc))

    loop(depth, STM.succeedNow(0))
  }
}
