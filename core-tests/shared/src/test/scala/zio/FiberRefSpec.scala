package zio

import zio.FiberRefSpecUtil._
import zio.test.Assertion._
import zio.test.TestAspect.flaky
import zio.test._

object FiberRefSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec = suite("FiberRefSpec")(
    suite("Create a new FiberRef with a specified value and check if:")(
      test("`delete` restores the original value") {
        for {
          fiberRef <- FiberRef.make(initial)
          _        <- fiberRef.set(update)
          _        <- fiberRef.delete
          v        <- fiberRef.get
        } yield assert(v)(equalTo(initial))
      },
      test("`get` returns the current value") {
        for {
          fiberRef <- FiberRef.make(initial)
          value    <- fiberRef.get
        } yield assert(value)(equalTo(initial))
      },
      test("`get` returns the current value for a child") {
        for {
          fiberRef <- FiberRef.make(initial)
          child    <- fiberRef.get.fork
          value    <- child.join
        } yield assert(value)(equalTo(initial))
      },
      test("`getAndUpdate` changes value") {
        for {
          fiberRef <- FiberRef.make(initial)
          value1   <- fiberRef.getAndUpdate(_ => update)
          value2   <- fiberRef.get
        } yield assert(value1)(equalTo(initial)) && assert(value2)(equalTo(update))
      },
      test("`getAndUpdateSome` changes value") {
        for {
          fiberRef <- FiberRef.make(initial)
          value1 <- fiberRef.getAndUpdateSome { case _ =>
                      update
                    }
          value2 <- fiberRef.get
        } yield assert(value1)(equalTo(initial)) && assert(value2)(equalTo(update))
      },
      test("`getAndUpdateSome` not changes value") {
        for {
          fiberRef <- FiberRef.make(initial)
          value1 <- fiberRef.getAndUpdateSome {
                      case _ if false => update
                    }
          value2 <- fiberRef.get
        } yield assert(value1)(equalTo(initial)) && assert(value2)(equalTo(initial))
      },
      test("`locally` restores original value") {
        for {
          fiberRef <- FiberRef.make(initial)
          local    <- fiberRef.locally(update)(fiberRef.get)
          value    <- fiberRef.get
        } yield assert(local)(equalTo(update)) && assert(value)(equalTo(initial))
      },
      test("`locally` restores parent's value") {
        for {
          fiberRef <- FiberRef.make(initial)
          child    <- fiberRef.locally(update)(fiberRef.get).fork
          local    <- child.join
          value    <- fiberRef.get
        } yield assert(local)(equalTo(update)) && assert(value)(equalTo(initial))
      },
      test("`locally` restores undefined value") {
        for {
          child <- FiberRef.make(initial).fork
          // Don't use join as it inherits values from child.
          fiberRef   <- child.await.flatMap(ZIO.done(_))
          localValue <- fiberRef.locally(update)(fiberRef.get)
          value      <- fiberRef.get
        } yield assert(localValue)(equalTo(update)) && assert(value)(equalTo(initial))
      },
      test("`modify` changes value") {
        for {
          fiberRef <- FiberRef.make(initial)
          value1   <- fiberRef.modify(_ => (1, update))
          value2   <- fiberRef.get
        } yield assert(value1)(equalTo(1)) && assert(value2)(equalTo(update))
      },
      test("`modifySome` not changes value") {
        for {
          fiberRef <- FiberRef.make(initial)
          value1 <- fiberRef.modifySome(2) {
                      case _ if false => (1, update)
                    }
          value2 <- fiberRef.get
        } yield assert(value1)(equalTo(2)) && assert(value2)(equalTo(initial))
      },
      test("`set` updates the current value") {
        for {
          fiberRef <- FiberRef.make(initial)
          _        <- fiberRef.set(update)
          value    <- fiberRef.get
        } yield assert(value)(equalTo(update))
      },
      test("`set` by a child doesn't update parent's value") {
        for {
          fiberRef <- FiberRef.make(initial)
          promise  <- Promise.make[Nothing, Unit]
          _        <- (fiberRef.set(update) *> promise.succeed(())).fork
          _        <- promise.await
          value    <- fiberRef.get
        } yield assert(value)(equalTo(initial))
      },
      test("`updateAndGet` changes value") {
        for {
          fiberRef <- FiberRef.make(initial)
          value1   <- fiberRef.updateAndGet(_ => update)
          value2   <- fiberRef.get
        } yield assert(value1)(equalTo(update)) && assert(value2)(equalTo(update))
      },
      test("`updateSomeAndGet` changes value") {
        for {
          fiberRef <- FiberRef.make(initial)
          value1 <- fiberRef.updateSomeAndGet { case _ =>
                      update
                    }
          value2 <- fiberRef.get
        } yield assert(value1)(equalTo(update)) && assert(value2)(equalTo(update))
      },
      test("`updateSomeAndGet` not changes value") {
        for {
          fiberRef <- FiberRef.make(initial)
          value1 <- fiberRef.updateSomeAndGet {
                      case _ if false => update
                    }
          value2 <- fiberRef.get
        } yield assert(value1)(equalTo(initial)) && assert(value2)(equalTo(initial))
      },
      test("its value is inherited on join") {
        for {
          fiberRef <- FiberRef.make(initial)
          child    <- fiberRef.set(update).fork
          _        <- child.join
          value    <- fiberRef.get
        } yield assert(value)(equalTo(update))
      },
      test("initial value is always available") {
        for {
          child    <- FiberRef.make(initial).fork
          fiberRef <- child.await.flatMap(ZIO.done(_))
          value    <- fiberRef.get
        } yield assert(value)(equalTo(initial))
      },
      test("its value is inherited after simple race") {
        for {
          fiberRef <- FiberRef.make(initial)
          _        <- fiberRef.set(update1).race(fiberRef.set(update2))
          value    <- fiberRef.get
        } yield assert(value)(equalTo(update1)) || assert(value)(equalTo(update2))
      },
      test("its value is inherited after a race with a bad winner") {
        for {
          fiberRef <- FiberRef.make(initial)
          badWinner = fiberRef.set(update1) *> ZIO.fail("ups")
          goodLoser = fiberRef.set(update2) *> looseTimeAndCpu
          _        <- badWinner.race(goodLoser)
          value    <- fiberRef.get
        } yield assert(value)(equalTo(update2))
      },
      test("its value is not inherited after a race of losers") {
        for {
          fiberRef <- FiberRef.make(initial)
          loser1    = fiberRef.set(update1) *> ZIO.fail("ups1")
          loser2    = fiberRef.set(update2) *> ZIO.fail("ups2")
          _        <- loser1.race(loser2).ignore
          value    <- fiberRef.get
        } yield assert(value)(equalTo(initial))
      } @@ zioTag(errors),
      test("the value of the loser is inherited in zipPar") {
        for {
          fiberRef <- FiberRef.make(initial)
          latch    <- Promise.make[Nothing, Unit]
          winner    = fiberRef.set(update1) *> latch.succeed(()).unit
          loser     = latch.await *> fiberRef.set(update2) *> looseTimeAndCpu
          _        <- winner.zipPar(loser)
          value    <- fiberRef.get
        } yield assert(value)(equalTo(update2))
      } @@ zioTag(errors),
      test("nothing gets inherited with a failure in zipPar") {
        for {
          fiberRef <- FiberRef.make(initial)
          success   = fiberRef.set(update)
          failure1  = fiberRef.set(update1) *> ZIO.fail(":-(")
          failure2  = fiberRef.set(update2) *> ZIO.fail(":-O")
          _        <- success.zipPar(failure1.zipPar(failure2)).orElse(ZIO.unit)
          value    <- fiberRef.get
        } yield assert(value)(equalTo(initial))
      } @@ zioTag(errors),
      test("fork function is applied on fork - 1") {
        def increment(x: Int): Int = x + 1
        for {
          fiberRef <- FiberRef.make(initial = 0, fork = increment)
          child    <- ZIO.unit.fork
          _        <- child.join
          value    <- fiberRef.get
        } yield assert(value)(equalTo(1))
      },
      test("fork function is applied on fork - 2") {
        def increment(x: Int): Int = x + 1
        for {
          fiberRef <- FiberRef.make(initial = 0, fork = increment)
          child    <- ZIO.unit.fork.flatMap(_.join).fork
          _        <- child.join
          value    <- fiberRef.get
        } yield assert(value)(equalTo(2))
      },
      test("join function is applied on join - 1") {
        for {
          fiberRef <- FiberRef.make(initial = 0, join = math.max)
          child    <- fiberRef.update(_ + 1).fork
          _        <- child.join
          value    <- fiberRef.get
        } yield assert(value)(equalTo(1))
      },
      test("join function is applied on join - 2") {
        for {
          fiberRef <- FiberRef.make(initial = 0, join = math.max)
          child    <- fiberRef.update(_ + 1).fork
          _        <- fiberRef.update(_ + 2)
          _        <- child.join
          value    <- fiberRef.get
        } yield assert(value)(equalTo(2))
      },
      test("its value is inherited in a trivial race") {
        for {
          fiberRef <- FiberRef.make(initial)
          _        <- fiberRef.set(update).raceAll(Iterable.empty)
          value    <- fiberRef.get
        } yield assert(value)(equalTo(update))
      },
      test("the value of the winner is inherited when racing two ZIOs with raceAll") {
        for {
          fiberRef <- FiberRef.make(initial)

          latch  <- Promise.make[Nothing, Unit]
          winner1 = fiberRef.set(update1) *> latch.succeed(())
          loser1  = latch.await *> fiberRef.set(update2) *> looseTimeAndCpu
          _      <- loser1.raceAll(List(winner1))
          value1 <- fiberRef.get <* fiberRef.set(initial)

          winner2 = fiberRef.set(update1)
          loser2  = fiberRef.set(update2) *> ZIO.fail(":-O")
          _      <- loser2.raceAll(List(winner2))
          value2 <- fiberRef.get <* fiberRef.set(initial)
        } yield assert((value1, value2))(equalTo((update1, update1)))
      } @@ flaky,
      test("the value of the winner is inherited when racing many ZIOs with raceAll") {
        for {
          fiberRef <- FiberRef.make(initial)
          n         = 63

          latch  <- Promise.make[Nothing, Unit]
          winner1 = fiberRef.set(update1) *> latch.succeed(())
          loser1  = latch.await *> fiberRef.set(update2) *> looseTimeAndCpu
          losers1 = Iterable.fill(n)(loser1)
          _      <- winner1.raceAll(losers1)
          value1 <- fiberRef.get <* fiberRef.set(initial)

          winner2 = fiberRef.set(update1) *> looseTimeAndCpu
          loser2  = fiberRef.set(update2) *> ZIO.fail("Nooooo")
          losers2 = Iterable.fill(n)(loser2)
          _      <- winner2.raceAll(losers2)
          value2 <- fiberRef.get <* fiberRef.set(initial)
        } yield assert((value1, value2))(equalTo((update1, update1)))
      },
      test("nothing gets inherited when racing failures with raceAll") {
        for {
          fiberRef <- FiberRef.make(initial)
          loser     = fiberRef.set(update) *> ZIO.fail("darn")
          _        <- loser.raceAll(Iterable.fill(63)(loser)).orElse(ZIO.unit)
          value    <- fiberRef.get
        } yield assert(value)(equalTo(initial))
      } @@ zioTag(errors),
      test("the value of all fibers in inherited when running many ZIOs with collectAllPar") {
        for {
          fiberRef <- FiberRef.make[Int](0, _ => 0, _ + _)
          _        <- ZIO.collectAllPar(List.fill(100000)(fiberRef.update(_ + 1)))
          value    <- fiberRef.get
        } yield assert(value)(equalTo(100000))
      },
      test("an unsafe handle is initialized and updated properly") {
        for {
          fiberRef <- FiberRef.make(initial)
          handle   <- fiberRef.unsafeAsThreadLocal
          value1   <- ZIO.succeed(handle.get())
          _        <- fiberRef.set(update1)
          value2   <- ZIO.succeed(handle.get())
          _        <- ZIO.succeed(handle.set(update2))
          value3   <- fiberRef.get
        } yield assert((value1, value2, value3))(equalTo((initial, update1, update2)))
      },
      test("unsafe handles work properly when initialized in a race") {
        for {
          fiberRef  <- FiberRef.make(initial)
          initHandle = fiberRef.unsafeAsThreadLocal
          handle    <- ZIO.raceAll(initHandle, Iterable.fill(64)(initHandle))
          value1    <- ZIO.succeed(handle.get())
          doUpdate   = fiberRef.set(update)
          _         <- ZIO.raceAll(doUpdate, Iterable.fill(64)(doUpdate))
          value2    <- ZIO.succeed(handle.get())
        } yield assert(value1)(equalTo(initial)) && assert(value2)(equalTo(update))
      },
      test("unsafe handles work properly when accessed concurrently") {
        for {
          fiberRef <- FiberRef.make(0)
          setAndGet =
            (value: Int) =>
              setRefOrHandle(fiberRef, value) *> fiberRef.unsafeAsThreadLocal.flatMap(h => ZIO.succeed(h.get()))
          n       = 64
          fiber  <- ZIO.forkAll(1.to(n).map(setAndGet))
          values <- fiber.join
        } yield assert(values)(equalTo(1.to(n)))
      },
      test("unsafe handles don't see updates from other fibers") {
        for {
          fiberRef <- FiberRef.make(initial)
          handle   <- fiberRef.unsafeAsThreadLocal
          value1   <- ZIO.succeed(handle.get())
          n         = 64
          fiber    <- ZIO.forkAll(Iterable.fill(n)(fiberRef.set(update).race(ZIO.succeed(handle.set(update)))))
          _        <- fiber.await
          value2   <- ZIO.succeed(handle.get())
        } yield assert(value1)(equalTo(initial)) && assert(value2)(equalTo(initial))
      },
      test("unsafe handles keep their values if there are async boundaries") {
        for {
          fiberRef <- FiberRef.make(0)

          test = (i: Int) =>
                   for {
                     handle <- fiberRef.unsafeAsThreadLocal
                     _      <- setRefOrHandle(fiberRef, handle, i)
                     _      <- ZIO.yieldNow
                     value  <- ZIO.succeed(handle.get())
                   } yield assert(value)(equalTo(i))

          n        = 64
          results <- ZIO.reduceAllPar(test(1), 2.to(n).map(test))(_ && _)
        } yield results
      },
      test("calling remove on unsafe handles restores their initial values") {
        for {
          fiberRef <- FiberRef.make(initial)
          _        <- fiberRef.set(update)
          handle   <- fiberRef.unsafeAsThreadLocal
          _        <- ZIO.succeed(handle.remove())
          value1   <- fiberRef.get
          value2   <- ZIO.succeed(handle.get())
        } yield assert((value1, value2))(equalTo((initial, initial)))
      }
    ),
    test("makeEnvironment") {
      for {
        testClock   <- testClock
        testConsole <- testConsole
        testRandom  <- testRandom
        testSystem  <- testSystem
        fiberRef    <- FiberRef.makeEnvironment(ZEnv.Services.live)
        _           <- fiberRef.update(_.add(testClock))
        left        <- fiberRef.update(_.add(testConsole)).fork
        right       <- fiberRef.update(_.add(testRandom)).fork
        _           <- left.join
        _           <- right.join
        _           <- fiberRef.update(_.add(testSystem))
        environment <- fiberRef.get
      } yield assertTrue(environment.get[Clock] == testClock) &&
        assertTrue(environment.get[Console] == testConsole) &&
        assertTrue(environment.get[Random] == testRandom) &&
        assertTrue(environment.get[System] == testSystem)
    } @@ TestAspect.nonFlaky,
    test("zipPar") {
      for {
        _ <- ZIO.unit.timeout(1.second) <& TestClock.adjust(1.second)
        _ <- testClock
      } yield assertCompletes
    } @@ TestAspect.nonFlaky,
    test("runtime") {
      for {
        expected <- ZIO.clock
        runtime  <- ZIO.runtime[Any]
        actual   <- ZIO.succeedBlocking(runtime.unsafeRun(ZIO.clock))
      } yield assertTrue(actual == expected)
    }
  ) @@ TestAspect.fromZIOAspect(ZIOAspect.enableCurrentFiber)
}

object FiberRefSpecUtil {
  val (initial, update, update1, update2) = ("initial", "update", "update1", "update2")
  val looseTimeAndCpu: ZIO[Live, Nothing, Unit] = Live.live {
    (ZIO.yieldNow <* Clock.sleep(1.nano)).repeatN(100)
  }

  def setRefOrHandle(fiberRef: FiberRef[Int], value: Int): UIO[Unit] =
    if (value % 2 == 0) fiberRef.set(value)
    else fiberRef.unsafeAsThreadLocal.flatMap(h => ZIO.succeed(h.set(value)))

  def setRefOrHandle(fiberRef: FiberRef[Int], handle: ThreadLocal[Int], value: Int): UIO[Unit] =
    if (value % 2 == 0) fiberRef.set(value)
    else ZIO.succeed(handle.set(value))
}
