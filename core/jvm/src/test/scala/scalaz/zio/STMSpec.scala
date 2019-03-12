package scalaz.zio

import scalaz.zio.STM.TVar

import java.util.concurrent.CountDownLatch

class STMSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {
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

       Make a new `TVar` and
           get its initial value $e13
           set a new value       $e14

       Using `STM.atomically` perform concurrent computations:
            increment `TVar` 100 times in 100 fibers. $e15
            compute a `TVar` from 2 variables, increment the first `TVar` and decrement the second `TVar` in different fibers. $e16

       Using `Ref` perform the same concurrent test should return a wrong result
             increment `TVar` 100 times in 100 fibers. $e17
             compute a `TVar` from 2 variables, increment the first `TVar` and decrement the second `TVar` in different fibers. $e18
       Using `STM.atomically` perform concurrent computations that
          have a simple condition lock should suspend the whole transaction and:
              resume directly when the condition is already satisfied $e19
              resume directly when the condition is already satisfied and change again the tvar with non satisfying value,
                  the transaction shouldn't be suspended. $e20
              resume after satisfying the condition $e21
              be suspended while the condition couldn't be satisfied
          have a complex condition lock should suspend the whole transaction and:
              resume directly when the condition is already satisfied e22
              resume directly when the condition is already satisfied and change again the tvar with non satisfying value,
              test1      e23
              test2      e24
              test3      e25
              test4      e26

    """

  def e1 =
    unsafeRun(
      STM.succeed("Hello World").run
    ) must_=== "Hello World"

  def e2 =
    unsafeRun(
      STM.fail("Bye bye World").run.either
    ) must left("Bye bye World")

  def e3 =
    unsafeRun(
      STM.succeed(42).either.run
    ) must right(42)

  def e4 =
    unsafeRun(
      STM.fail("oh no!").either.run
    ) must left("oh no!")

  def e5 = unsafeRun(
    (for {
      s <- STM.succeed("Yes!").fold(_ => -1, _ => 1)
      f <- STM.fail("No!").fold(_ => -1, _ => 1)
    } yield (s must_=== 1) and (f must_== -1)).run
  )

  def e6 =
    unsafeRun(
      (for {
        s <- STM.succeed("Yes!").foldM(_ => STM.succeed("No!"), STM.succeed)
        f <- STM.fail("No!").foldM(STM.succeed, _ => STM.succeed("Yes!"))
      } yield (s must_=== "Yes!") and (f must_== "No!")).run
    )

  def e7 =
    unsafeRun(
      STM.fail(-1).mapError(_ => "oh no!").run.either
    ) must left("oh no!")

  def e8 =
    unsafeRun(
      (
        for {
          s <- STM.succeed(1) orElse STM.succeed(2)
          f <- STM.fail("failed") orElse STM.succeed("try this")
        } yield (s must_=== 1) and (f must_=== "try this")
      ).run
    )

  def e9 =
    unsafeRun(
      STM.succeed(42).option.run
    ) must some(42)

  def e10 =
    unsafeRun(
      STM.fail("oh no!").option.run
    ) must none

  def e11 =
    unsafeRun(
      (
        STM.succeed(1) ~ STM.succeed('A')
      ).run
    ) must_=== ((1, 'A'))

  def e12 =
    unsafeRun(
      STM.succeed(578).zipWith(STM.succeed(2))(_ + _).run
    ) must_=== 580

  def e13 =
    unsafeRun(
      (
        for {
          intVar <- TVar.make(14)
          v      <- intVar.get
        } yield v must_== 14
      ).run
    )

  def e14 =
    unsafeRun(
      (
        for {
          intVar <- TVar.make(14)
          _      <- intVar.set(42)
          v      <- intVar.get
        } yield v must_== 42
      ).run
    )

  private def incrementVarN(n: Int, tvar: STM.TVar[Int]): ZIO[clock.Clock, Nothing, Int] =
    STM
      .atomically(for {
        v <- tvar.get
        _ <- tvar.set(v + 1)
        v <- tvar.get
      } yield v)
      .repeat(Schedule.recurs(n) *> Schedule.identity)

  private def compute3VarN(
    n: Int,
    tvar1: STM.TVar[Int],
    tvar2: STM.TVar[Int],
    tvar3: STM.TVar[Int]
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
      unsafeRun(
        for {
          tVar  <- TVar.makeRun(0)
          fiber <- ZIO.forkAll(List.fill(10)(incrementVarN(99, tVar)))
          _     <- fiber.join
        } yield tVar.get
      ).run
    ) must_=== 1000

  def e16 =
    unsafeRun(
      unsafeRun(
        for {
          tVars <- STM
                    .atomically(
                      TVar.make(10000) ~ TVar.make(0) ~ TVar.make(0)
                    )
          tvar1 ~ tvar2 ~ tvar3 = tVars
          fiber                 <- ZIO.forkAll(List.fill(10)(compute3VarN(99, tvar1, tvar2, tvar3)))
          _                     <- fiber.join
        } yield tvar3.get
      ).run
    ) must_=== 10000

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

  def e17 =
    unsafeRun(
      for {
        ref   <- Ref.make(0)
        fiber <- ZIO.forkAll(List.fill(10)(incrementRefN(99, ref)))
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
        fiber <- ZIO.forkAll(List.fill(10)(compute3RefN(99, ref1, ref2, ref3)))
        _     <- fiber.join
        v3    <- ref3.get
      } yield v3 must_!== 10000
    )

  def e19 =
    unsafeRun {
      for {
        tvar1 <- TVar.makeRun(10)
        tvar2 <- TVar.makeRun("Failed!")
        fiber <- (
                  for {
                    v1 <- tvar1.get
                    _  <- STM.check(v1 > 0)
                    _  <- tvar2.set("Succeeded!")
                    v2 <- tvar2.get
                  } yield v2
                ).run.fork
        join <- fiber.join
      } yield join must_=== "Succeeded!"
    }

  def e20 =
    unsafeRun {
      for {
        tvar <- TVar.makeRun(42)
        fiber <- STM.atomically {
                  tvar.get.filter(_ == 42)
                }.fork
        join <- fiber.join
        _    <- tvar.set(9).run
        v    <- tvar.get.run
      } yield (v must_=== 9) and (join must_=== 42)
    }

  def e21 =
    unsafeRun {
      val latch = new CountDownLatch(1)

      for {
        done  <- Promise.make[Nothing, Unit]
        tvar1 <- TVar.makeRun(0)
        tvar2 <- TVar.makeRun("Failed!")
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
        old  <- tvar2.get.run
        _    <- tvar1.set(43).run
        _    <- done.await
        newV <- tvar2.get.run
        join <- fiber.join
      } yield (old must_=== "Failed!") and (newV must_=== join)
    }

  def e22 =
    unsafeRun {
      for {
        sender    <- TVar.makeRun(100)
        receiver  <- TVar.makeRun(0)
        _         <- transfer(receiver, sender, 150).fork
        _         <- sender.update(_ + 100).run
        _         <- sender.get.filter(_ == 50).run
        senderV   <- sender.get.run
        receiverV <- receiver.get.run
      } yield (senderV must_=== 50) and (receiverV must_=== 150)
    }

  def transfer(receiver: TVar[Int], sender: TVar[Int], much: Int): UIO[Int] =
    STM.atomically {
      for {
        balance <- sender.get
        _       <- STM.check(balance >= much)
        _       <- receiver.update(_ + much)
        _       <- sender.update(_ - much)
        newAmnt <- receiver.get
      } yield newAmnt
    }

  import scalaz.zio.duration._
  def e23 =
    unsafeRun {
      for {
        sender     <- TVar.makeRun(100)
        receiver   <- TVar.makeRun(0)
        toReceiver = transfer(receiver, sender, 150)
        toSender   = transfer(sender, receiver, 150)
        f1         <- IO.forkAll(List.fill(10)(toReceiver *> toSender))
        _          <- sender.update(_ + 50).run
        _          <- sender.debug.delay(30.seconds).fork
        _          <- receiver.debug.delay(30.seconds).fork
        _          <- f1.join
        senderV    <- sender.get.run
        receiverV  <- receiver.get.run
      } yield (senderV must_=== 150) and (receiverV must_=== 0)
    }

  def e24 =
    unsafeRun {
      for {
        sender     <- TVar.makeRun(50)
        receiver   <- TVar.makeRun(0)
        toReceiver = transfer(receiver, sender, 100)
        toSender   = transfer(sender, receiver, 100)
        f1         <- IO.forkAll(List.fill(10)(toReceiver))
        f2         <- IO.forkAll(List.fill(10)(toSender))
        _          <- sender.update(_ + 50).run
        _          <- f1.join
        _          <- f2.join
        senderV    <- sender.get.run
        receiverV  <- receiver.get.run
      } yield (senderV must_=== 100) and (receiverV must_=== 0)
    }

  def e25 =
    unsafeRun {
      for {
        sender       <- TVar.makeRun(50)
        receiver     <- TVar.makeRun(0)
        toReceiver10 = transfer(receiver, sender, 100).repeat(Schedule.recurs(9))
        toSender10   = transfer(sender, receiver, 100).repeat(Schedule.recurs(9))
        f            <- toReceiver10.zipPar(toSender10).fork
        _            <- sender.update(_ + 950).run
        _            <- f.join
        senderV      <- sender.get.run
        receiverV    <- receiver.get.run
      } yield (senderV must_=== 1000) and (receiverV must_=== 0)
    }

  def e26 =
    unsafeRun(
      for {
        tvar <- TVar.makeRun(0)
        fiber <- IO.forkAll(
                  (0 to 20).map(
                    i =>
                      (for {
                        v <- tvar.get
                        _ <- STM.check(v == i)
                        _ <- tvar.update(_ + 1)
                      } yield ()).run
                  )
                )
        _ <- fiber.join
        v <- tvar.get.run
      } yield v must_=== 21
    )
}
