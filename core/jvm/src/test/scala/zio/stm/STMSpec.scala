package zio.stm

import java.util.concurrent.CountDownLatch

import zio.Cause
import zio._

final class STMSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {
  def is = "STMSpec".title ^ s2"""
        Using `STM.atomically` to perform different computations and call:
          `STM.succeed` to make a successful computation and check the value $e1
          `STM.failed` to make a failed computation and check the value      $e2
          `either` to convert:
              A successful computation into Right(a) $e3
              A failed computation into Left(e)      $e4
           `fold` to handle both failure and success $e5
           `foldM` to fold over the `STM` effect, and handle failure and success $e6
           `mapError` to map from one error to another                           $e7
           `orElse` to try another computation when the computation is failed.   $e8
           `option` to convert:
              A successful computation into Some(a) $e9
              A failed computation into None        $e10
           `zip` to return a tuple of two computations        $e11
           `zipWith` to perform an action to two computations $e12

        Make a new `TRef` and
           get its initial value $e13
           set a new value       $e14

        Using `STM.atomically` perform concurrent computations:
            increment `TRef` 100 times in 100 fibers. $e15
            compute a `TRef` from 2 variables, increment the first `TRef` and decrement the second `TRef` in different fibers. $e16

        Using `Ref` perform the same concurrent test should return a wrong result
             increment `Ref` 100 times in 100 fibers. $e17
             compute a `Ref` from 2 variables, increment the first `Ref` and decrement the second `Ref` in different fibers. $e18

        Using `STM.atomically` perform concurrent computations that
          have a simple condition lock should suspend the whole transaction and:
              resume directly when the condition is already satisfied $e19
              resume directly when the condition is already satisfied and change again the tvar with non satisfying value,
                  the transaction shouldn't be suspended. $e20
              resume after satisfying the condition $e21
              be suspended while the condition couldn't be satisfied
          have a complex condition lock should suspend the whole transaction and:
              resume directly when the condition is already satisfied $e22
          transfer an amount to a sender and send it back the account should contains the amount to transfer!
              run both transactions sequentially in 10 fibers. $e23
              run 10 transactions `toReceiver` and 10 `toSender` concurrently. $e24
              run transactions `toReceiver` 10 times and `toSender` 10 times each in 100 fibers concurrently. $e25

          Perform atomically a single transaction that has a tvar for 20 fibers, each one checks the value and increment it. $e26
          Perform atomically a transaction with a condition that couldn't be satisfied, it should be suspended
            interrupt the fiber should terminate the transaction $e27
            interrupt the fiber that has executed the transaction in 100 different fibers, should terminate all transactions. $e28
            interrupt the fiber and observe it, it should be resumed with Interrupted Cause   $e29
          Using `collect` filter and map simultaneously the value produced by the transaction $e30
          Permute 2 variables $e31
          Permute 2 variables in 100 fibers, the 2 variables should contains the same values $e32
          Using `collectAll` collect a list of transactional effects to a single transaction that produces a list of values $e33
          Using `foreach` perform an action in each value and return a single transaction that contains the result $e34
          Using `orElseEither` tries 2 computations and returns either left if the left computation succeed or right if the right one succeed $e35


        Failure must
          rollback full transaction     $e36
          be ignored                    $e37
        orElse must
          rollback left retry           $e38
          rollback left failure         $e39
          local reset, not global       $e40
    """

  def e1 =
    unsafeRun(
      STM.succeed("Hello World").commit
    ) must_=== "Hello World"

  def e2 =
    unsafeRun(
      STM.fail("Bye bye World").commit.either
    ) must left("Bye bye World")

  def e3 =
    unsafeRun(
      STM.succeed(42).either.commit
    ) must right(42)

  def e4 =
    unsafeRun(
      STM.fail("oh no!").either.commit
    ) must left("oh no!")

  def e5 = unsafeRun(
    (for {
      s <- STM.succeed("Yes!").fold(_ => -1, _ => 1)
      f <- STM.fail("No!").fold(_ => -1, _ => 1)
    } yield (s must_=== 1) and (f must_== -1)).commit
  )

  def e6 =
    unsafeRun(
      (for {
        s <- STM.succeed("Yes!").foldM(_ => STM.succeed("No!"), STM.succeed)
        f <- STM.fail("No!").foldM(STM.succeed, _ => STM.succeed("Yes!"))
      } yield (s must_=== "Yes!") and (f must_== "No!")).commit
    )

  def e7 =
    unsafeRun(
      STM.fail(-1).mapError(_ => "oh no!").commit.either
    ) must left("oh no!")

  def e8 =
    unsafeRun(
      (
        for {
          s <- STM.succeed(1) orElse STM.succeed(2)
          f <- STM.fail("failed") orElse STM.succeed("try this")
        } yield (s must_=== 1) and (f must_=== "try this")
      ).commit
    )

  def e9 =
    unsafeRun(
      STM.succeed(42).option.commit
    ) must some(42)

  def e10 =
    unsafeRun(
      STM.fail("oh no!").option.commit
    ) must none

  def e11 =
    unsafeRun(
      (
        STM.succeed(1) <*> STM.succeed('A')
      ).commit
    ) must_=== ((1, 'A'))

  def e12 =
    unsafeRun(
      STM.succeed(578).zipWith(STM.succeed(2))(_ + _).commit
    ) must_=== 580

  def e13 =
    unsafeRun(
      (
        for {
          intVar <- TRef.make(14)
          v      <- intVar.get
        } yield v must_== 14
      ).commit
    )

  def e14 =
    unsafeRun(
      (
        for {
          intVar <- TRef.make(14)
          _      <- intVar.set(42)
          v      <- intVar.get
        } yield v must_== 42
      ).commit
    )

  private def incrementVarN(n: Int, tvar: TRef[Int]): ZIO[clock.Clock, Nothing, Int] =
    STM
      .atomically(for {
        v <- tvar.get
        _ <- tvar.set(v + 1)
        v <- tvar.get
      } yield v)
      .repeat(Schedule.recurs(n) *> Schedule.identity)

  private def compute3VarN(
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

  def e15 =
    unsafeRun(
      for {
        tVar  <- TRef.makeCommit(0)
        fiber <- ZIO.forkAll(List.fill(10)(incrementVarN(99, tVar)))
        _     <- fiber.join
        value <- tVar.get.commit
      } yield value
    ) must_=== 1000

  def e16 =
    unsafeRun(
      for {
        tVars <- STM
                  .atomically(
                    TRef.make(10000) <*> TRef.make(0) <*> TRef.make(0)
                  )
        tvar1 <*> tvar2 <*> tvar3 = tVars
        fiber                     <- ZIO.forkAll(List.fill(10)(compute3VarN(99, tvar1, tvar2, tvar3)))
        _                         <- fiber.join
        value                     <- tvar3.get.commit
      } yield value
    ) must_=== 10000

  def e17 =
    unsafeRun(
      for {
        ref   <- Ref.make(0)
        fiber <- ZIO.forkAll(List.fill(100)(incrementRefN(99, ref)))
        _     <- fiber.join
        v     <- ref.get
      } yield v must_!== 10000
    )

  def e18 =
    unsafeRun(
      for {
        ref1  <- Ref.make(10000)
        ref2  <- Ref.make(0)
        ref3  <- Ref.make(0)
        fiber <- ZIO.forkAll(List.fill(100)(compute3RefN(99, ref1, ref2, ref3)))
        _     <- fiber.join
        v3    <- ref3.get
      } yield v3 must_!== 10000
    )

  def e19 =
    unsafeRun {
      for {
        tvar1 <- TRef.makeCommit(10)
        tvar2 <- TRef.makeCommit("Failed!")
        join <- (for {
                 v1 <- tvar1.get
                 _  <- STM.check(v1 > 0)
                 _  <- tvar2.set("Succeeded!")
                 v2 <- tvar2.get
               } yield v2).commit
      } yield join must_=== "Succeeded!"
    }

  def e20 =
    unsafeRun {
      for {
        tvar <- TRef.makeCommit(42)
        join <- tvar.get.filter(_ == 42).commit
        _    <- tvar.set(9).commit
        v    <- tvar.get.commit
      } yield (v must_=== 9) and (join must_=== 42)
    }

  def e21 =
    unsafeRun {
      val latch = new CountDownLatch(1)

      for {
        done  <- Promise.make[Nothing, Unit]
        tvar1 <- TRef.makeCommit(0)
        tvar2 <- TRef.makeCommit("Failed!")
        fiber <- (STM.atomically {
                  for {
                    v1 <- tvar1.get
                    _  <- STM.succeedLazy(latch.countDown())
                    _  <- STM.check(v1 > 42)
                    _  <- tvar2.set("Succeeded!")
                    v2 <- tvar2.get
                  } yield v2
                } <* done.succeed(())).fork
        _    <- UIO(latch.await())
        old  <- tvar2.get.commit
        _    <- tvar1.set(43).commit
        _    <- done.await
        newV <- tvar2.get.commit
        join <- fiber.join
      } yield (old must_=== "Failed!") and (newV must_=== join)
    }

  def e22 =
    unsafeRun {
      for {
        sender    <- TRef.makeCommit(100)
        receiver  <- TRef.makeCommit(0)
        _         <- transfer(receiver, sender, 150).fork
        _         <- sender.update(_ + 100).commit
        _         <- sender.get.filter(_ == 50).commit
        senderV   <- sender.get.commit
        receiverV <- receiver.get.commit
      } yield (senderV must_=== 50) and (receiverV must_=== 150)
    }

  def e23 =
    unsafeRun {
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
      } yield (senderV must_=== 150) and (receiverV must_=== 0)
    }

  def e24 =
    unsafeRun {
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
      } yield (senderV must_=== 100) and (receiverV must_=== 0)
    }

  def e25 =
    unsafeRun {
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
      } yield (senderV must_=== 100) and (receiverV must_=== 0)
    }

  def e26 =
    unsafeRun(
      for {
        tvar <- TRef.makeCommit(0)
        fiber <- IO.forkAll(
                  (0 to 20).map(
                    i =>
                      (for {
                        v <- tvar.get
                        _ <- STM.check(v == i)
                        _ <- tvar.update(_ + 1)
                      } yield ()).commit
                  )
                )
        _ <- fiber.join
        v <- tvar.get.commit
      } yield v must_=== 21
    )
  import zio.duration._

  def e27 =
    unsafeRun {
      val latch = new CountDownLatch(1)
      for {
        tvar <- TRef.makeCommit(0)
        fiber <- (for {
                  v <- tvar.get
                  _ <- STM.succeedLazy(latch.countDown())
                  _ <- STM.check(v > 0)
                  _ <- tvar.update(10 / _)
                } yield ()).commit.fork
        _ <- UIO(latch.await())
        _ <- fiber.interrupt
        _ <- tvar.set(10).commit
        v <- clock.sleep(10.millis) *> tvar.get.commit
      } yield v must_=== 10
    }

  def e28 =
    unsafeRun {
      val latch = new CountDownLatch(1)
      for {
        tvar <- TRef.makeCommit(0)
        fiber <- IO.forkAll(List.fill(100)((for {
                  v <- tvar.get
                  _ <- STM.succeedLazy(latch.countDown())
                  _ <- STM.check(v < 0)
                  _ <- tvar.set(10)
                } yield ()).commit))
        _ <- UIO(latch.await())
        _ <- fiber.interrupt
        _ <- tvar.set(-1).commit
        v <- tvar.get.commit.delay(10.millis)
      } yield v must_=== -1
    }

  def e29 =
    unsafeRun(
      for {
        v       <- TRef.makeCommit(1)
        f       <- v.get.flatMap(v => STM.check(v == 0)).commit.fork
        _       <- f.interrupt
        observe <- f.poll
      } yield observe must be some Exit.Failure(Cause.interrupt)
    )

  def e30 =
    unsafeRun(
      STM.succeed((1 to 20).toList).collect { case l if l.forall(_ > 0) => "Positive" }.commit
    ) must_=== "Positive"

  def e31 =
    unsafeRun(
      for {
        tvar1 <- TRef.makeCommit(1)
        tvar2 <- TRef.makeCommit(2)
        _     <- permutation(tvar1, tvar2).commit
        v1    <- tvar1.get.commit
        v2    <- tvar2.get.commit
      } yield (v1 must_=== 2) and (v2 must_=== 1)
    )

  def e32 =
    unsafeRun(
      for {
        tvar1 <- TRef.makeCommit(1)
        tvar2 <- TRef.makeCommit(2)
        oldV1 <- tvar1.get.commit
        oldV2 <- tvar2.get.commit
        f     <- IO.forkAll(List.fill(100)(permutation(tvar1, tvar2).commit))
        _     <- f.join
        v1    <- tvar1.get.commit
        v2    <- tvar2.get.commit
      } yield (v1 must_=== oldV1) and (v2 must_=== oldV2)
    )

  def e33 =
    unsafeRun(
      for {
        it    <- UIO((1 to 100).map(TRef.make(_)))
        tvars <- STM.collectAll(it).commit
        res   <- UIO.collectAllPar(tvars.map(_.get.commit))
      } yield res must_=== (1 to 100).toList
    )

  def e34 =
    unsafeRun(
      for {
        tvar      <- TRef.makeCommit(0)
        _         <- STM.foreach(1 to 100)(a => tvar.update(_ + a)).commit
        expectedV = (1 to 100).sum
        v         <- tvar.get.commit
      } yield v must_=== expectedV
    )

  def e35 =
    unsafeRun(
      for {
        rightV  <- STM.fail("oh no!").orElseEither(STM.succeed(42)).commit
        leftV1  <- STM.succeed(1).orElseEither(STM.succeed("No me!")).commit
        leftV2  <- STM.succeed(2).orElseEither(STM.fail("No!")).commit
        failedV <- STM.fail(-1).orElseEither(STM.fail(-2)).commit.either
      } yield (rightV must beRight(42)) and (leftV1 must beLeft(1)) and (leftV2 must beLeft(2)) and (failedV must beLeft(
        -2
      ))
    )

  def e36 =
    unsafeRun(
      for {
        tvar <- TRef.makeCommit(0)
        e <- (for {
              _ <- tvar.update(_ + 10)
              _ <- STM.fail("Error!")
            } yield ()).commit.either
        v <- tvar.get.commit
      } yield (e must be left "Error!") and
        (v must_=== 0)
    )

  def e37 =
    unsafeRun(
      for {
        tvar <- TRef.makeCommit(0)
        e <- (for {
              _ <- tvar.update(_ + 10)
              _ <- STM.fail("Error!")
            } yield ()).commit.ignore
        v <- tvar.get.commit
      } yield (e must be_==(())) and (v must_=== 0)
    )
  def e38 =
    unsafeRun(
      for {
        tvar  <- TRef.makeCommit(0)
        left  = tvar.update(_ + 100) *> STM.retry
        right = tvar.update(_ + 100).unit
        _     <- (left orElse right).commit
        v     <- tvar.get.commit
      } yield v must_=== 100
    )

  def e39 =
    unsafeRun(
      for {
        tvar  <- TRef.makeCommit(0)
        left  = tvar.update(_ + 100) *> STM.fail("Uh oh!")
        right = tvar.update(_ + 100).unit
        _     <- (left orElse right).commit
        v     <- tvar.get.commit
      } yield v must_=== 100
    )

  def e40 =
    unsafeRun(for {
      ref <- TRef.make(0).commit
      result <- STM.atomically(for {
                 _       <- ref.set(2)
                 newVal1 <- ref.get
                 _       <- STM.partial(throw new RuntimeException).orElse(STM.unit)
                 newVal2 <- ref.get
               } yield (newVal1, newVal2))
    } yield result must_=== (2 -> 2))

  private def incrementRefN(n: Int, ref: Ref[Int]): ZIO[clock.Clock, Nothing, Int] =
    (for {
      v <- ref.get
      _ <- ref.set(v + 1)
      v <- ref.get
    } yield v)
      .repeat(Schedule.recurs(n) *> Schedule.identity)

  private def compute3RefN(n: Int, ref1: Ref[Int], ref2: Ref[Int], ref3: Ref[Int]): ZIO[clock.Clock, Nothing, Int] =
    (for {
      v1 <- ref1.get
      v2 <- ref2.get
      _  <- ref3.set(v1 + v2)
      v3 <- ref3.get
      _  <- ref1.set(v1 - 1)
      _  <- ref2.set(v2 + 1)
    } yield v3)
      .repeat(Schedule.recurs(n) *> Schedule.identity)

  private def transfer(receiver: TRef[Int], sender: TRef[Int], much: Int): UIO[Int] =
    STM.atomically {
      for {
        balance <- sender.get
        _       <- STM.check(balance >= much)
        _       <- receiver.update(_ + much)
        _       <- sender.update(_ - much)
        newAmnt <- receiver.get
      } yield newAmnt
    }

  private def permutation(tvar1: TRef[Int], tvar2: TRef[Int]): STM[Nothing, Unit] =
    for {
      a <- tvar1.get
      b <- tvar2.get
      _ <- tvar1.set(b)
      _ <- tvar2.set(a)
    } yield ()

}
