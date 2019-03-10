package scalaz.zio

import scalaz.zio.STM.TVar

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
              resume directly when the condition is already satisfied $e22
              resume directly when the condition is already satisfied and change again the tvar with non satisfying value,
                  the transaction shouldn't be suspended. $e23
              resume after satisfying the condition $e24
              be suspended while the condition couldn't be satisfied $e25

    """

  def e1 =
    unsafeRun(
      STM.atomically(
        STM.succeed("Hello World")
      )
    ) must_=== "Hello World"

  def e2 =
    unsafeRun(
      STM
        .atomically(
          STM.fail("Bye bye World")
        )
        .either
    ) must left("Bye bye World")

  def e3 =
    unsafeRun(
      STM.atomically(
        STM.succeed(42).either
      )
    ) must right(42)

  def e4 =
    unsafeRun(
      STM.atomically(
        STM.fail("oh no!").either
      )
    ) must left("oh no!")

  def e5 = unsafeRun(
    STM.atomically(
      for {
        s <- STM.succeed("Yes!").fold(_ => -1, _ => 1)
        f <- STM.fail("No!").fold(_ => -1, _ => 1)
      } yield (s must_=== 1) and (f must_== -1)
    )
  )

  def e6 =
    unsafeRun(
      STM.atomically(
        for {
          s <- STM.succeed("Yes!").foldM(_ => STM.succeed("No!"), STM.succeed)
          f <- STM.fail("No!").foldM(STM.succeed, _ => STM.succeed("Yes!"))
        } yield (s must_=== "Yes!") and (f must_== "No!")
      )
    )

  def e7 =
    unsafeRun(
      STM
        .atomically(
          STM.fail(-1).mapError(_ => "oh no!")
        )
        .either
    ) must left("oh no!")

  def e8 =
    unsafeRun(
      STM.atomically(
        for {
          s <- STM.succeed(1) orElse STM.succeed(2)
          f <- STM.fail("failed") orElse STM.succeed("try this")
        } yield (s must_=== 1) and (f must_=== "try this")
      )
    )

  def e9 =
    unsafeRun(
      STM.atomically(
        STM.succeed(42).option
      )
    ) must some(42)

  def e10 =
    unsafeRun(
      STM.atomically(
        STM.fail("oh no!").option
      )
    ) must none

  def e11 =
    unsafeRun(
      STM.atomically(
        STM.succeed(1) ~ STM.succeed('A')
      )
    ) must_=== ((1, 'A'))

  def e12 =
    unsafeRun(
      STM.atomically(
        STM.succeed(578).zipWith(STM.succeed(2))(_ + _)
      )
    ) must_=== 580

  def e13 =
    unsafeRun(
      STM.atomically(
        for {
          intVar <- TVar.make(14)
          v      <- intVar.get
        } yield v must_== 14
      )
    )

  def e14 =
    unsafeRun(
      STM.atomically(
        for {
          intVar <- TVar.make(14)
          _      <- intVar.set(42)
          v      <- intVar.get
        } yield v must_== 42
      )
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
      STM.atomically(
        unsafeRun(
          for {
            tVar  <- STM.atomically(TVar.make(0))
            fiber <- ZIO.forkAll(List.fill(100)(incrementVarN(99, tVar)))
            _     <- fiber.join
          } yield tVar.get
        )
      )
    ) must_=== 10000

  def e16 =
    unsafeRun(
      STM.atomically(
        unsafeRun(
          for {
            tVars <- STM
                      .atomically(
                        TVar.make(10000) ~ TVar.make(0) ~ TVar.make(0)
                      )
            tvar1 ~ tvar2 ~ tvar3 = tVars
            fiber                 <- ZIO.forkAll(List.fill(100)(compute3VarN(99, tvar1, tvar2, tvar3)))
            _                     <- fiber.join
          } yield tvar3.get
        )
      )
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
        fiber <- ZIO.forkAll(List.fill(1)(incrementRefN(99, ref)))
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

  import scalaz.zio.duration._

  def e19 =
    unsafeRun {
      for {
        tvar1 <- STM.atomically(TVar.make(10))
        tvar2 <- STM.atomically(TVar.make("Failed!"))
        fiber <- STM.atomically {
                  for {
                    v1 <- tvar1.get
                    _  <- STM.check(v1 > 0)
                    _  <- tvar2.set("Succeeded!")
                    v2 <- tvar2.get
                  } yield v2
                }.fork
        join <- fiber.join
      } yield join must_=== "Succeeded!"
    }

  def e20 =
    unsafeRun {
      for {
        tvar <- STM.atomically(TVar.make(42))
        fiber <- STM.atomically {
                  for {
                    v <- tvar.get
                    _ <- STM.check(v == 42)
                  } yield v
                }.fork
        join <- fiber.join
        _    <- STM.atomically(tvar.set(9))
        v    <- STM.atomically(tvar.get)
      } yield (v must_=== 9) and (join must_=== 42)
    }

  def e21 =
    unsafeRun {

      for {
        done  <- Promise.make[Nothing, Unit]
        tvar1 <- STM.atomically(TVar.make(0))
        tvar2 <- STM.atomically(TVar.make("Failed!"))
        fiber <- (STM.atomically {
                  for {
                    v1 <- tvar1.get
                    _  <- STM.check(v1 > 42)
                    _  <- tvar2.set("Succeeded!")
                    v2 <- tvar2.get
                  } yield v2
                } <* done.succeed(())).fork
        _    <- clock.sleep(10.millis)
        old  <- STM.atomically(tvar2.get)
        _    <- STM.atomically(tvar1.set(43))
        _    <- done.await
        newV <- STM.atomically(tvar2.get)
        join <- fiber.join
      } yield (old must_=== "Failed!") and (newV must_=== join)
    }

  def e22 = todo
  def e23 = todo
  def e24 = todo
  def e25 = todo

}
