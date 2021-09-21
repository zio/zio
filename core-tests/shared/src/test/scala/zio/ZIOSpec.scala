package zio

import zio.Cause._
import zio.LatchOps._
import zio.clock.Clock
import zio.duration._
import zio.internal.Platform
import zio.random.Random
import zio.test.TestAspect.{flaky, forked, ignore, jvm, jvmOnly, nonFlaky, scala2Only}
import zio.test._
import zio.test.environment.{Live, TestClock}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object ZIOSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec: ZSpec[Environment, Failure] = suite("ZIOSpec")(
    suite("&&")(
      testM("true and true is true") {
        (ZIO.succeed(true) && ZIO.succeed(true)).map { res =>
          assertTrue(res)
        }
      },
      testM("true and false is false") {
        (ZIO.succeed(true) && ZIO.succeed(false)).map { res =>
          assertTrue(!res)
        }
      },
      testM("false and true is false") {
        (ZIO.succeed(false) && ZIO.succeed(true)).map { res =>
          assertTrue(!res)
        }
      },
      testM("false and false is false") {
        (ZIO.succeed(false) && ZIO.succeed(false)).map { res =>
          assertTrue(!res)
        }
      },
      testM("short circuiting") {
        (ZIO.succeed(false) && ZIO.fail("fail")).map { res =>
          assertTrue(!res)
        }
      }
    ),
    suite("***")(
      testM("splits the environment") {
        val zio1 = ZIO.fromFunction((n: Int) => n + 2)
        val zio2 = ZIO.fromFunction((n: Int) => n * 3)
        val zio3 = zio1 *** zio2
        zio3.provide((4, 5)).map { result =>
          assertTrue(result == (6 -> 15))
        }
      }
    ),
    suite("unary_!")(
      testM("not true is false") {
        (!ZIO.succeed(true)).map(res => assertTrue(!res))
      },
      testM("not false is true") {
        (!ZIO.succeed(false)).map(res => assertTrue(res))
      }
    ),
    suite("||")(
      testM("true or true is true") {
        (ZIO.succeed(true) || ZIO.succeed(true)).map { res =>
          assertTrue(res)
        }
      },
      testM("true or false is true") {
        (ZIO.succeed(true) || ZIO.succeed(false)).map { res =>
          assertTrue(res)
        }
      },
      testM("false or true is true") {
        (ZIO.succeed(false) || ZIO.succeed(true)).map { res =>
          assertTrue(res)
        }
      },
      testM("false or false is false") {
        (ZIO.succeed(false) || ZIO.succeed(false)).map { res =>
          assertTrue(!res)
        }
      },
      testM("short circuiting") {
        (ZIO.succeed(true) || ZIO.fail("fail")).map { res =>
          assertTrue(res)
        }
      }
    ),
    suite("absorbWith")(
      testM("on fail") {
        TaskExampleError.absorbWith(identity).run.map { exit =>
          assertTrue(exit.$fail == ExampleError)
        }
      },
      testM("on die") {
        TaskExampleDie.absorbWith(identity).run.map { exit =>
          assertTrue(exit.$fail == ExampleError)
        }
      },
      testM("on success") {
        ZIO.succeed(1).absorbWith(_ => ExampleError).map { res =>
          assertTrue(res == 1)
        }
      }
    ) @@ zioTag(errors),
    suite("bracket")(
      testM("bracket happy path") {
        for {
          release  <- Ref.make(false)
          result   <- ZIO.bracket(IO.succeed(42), (_: Int) => release.set(true), (a: Int) => ZIO.effectTotal(a + 1))
          released <- release.get
        } yield assertTrue(result == 43 && released)
      },
      testM("bracket_ happy path") {
        for {
          release  <- Ref.make(false)
          result   <- IO.succeed(42).bracket_(release.set(true), ZIO.effectTotal(0))
          released <- release.get
        } yield assertTrue(result == 0 && released)
      },
      testM("bracketExit happy path") {
        for {
          release <- Ref.make(false)
          result <- ZIO.bracketExit(
                      IO.succeed(42),
                      (_: Int, _: Exit[Any, Any]) => release.set(true),
                      (_: Int) => IO.succeed(0L)
                    )
          released <- release.get
        } yield assertTrue(result == 0L && released)
      },
      testM("bracketExit error handling") {
        val releaseDied: Throwable = new RuntimeException("release died")
        for {
          exit <- ZIO
                    .bracketExit[Any, String, Int, Int](
                      ZIO.succeed(42),
                      (_, _) => ZIO.die(releaseDied),
                      _ => ZIO.fail("use failed")
                    )
                    .run
          cause <- exit.foldM(cause => ZIO.succeed(cause), _ => ZIO.fail("effect should have failed"))
        } yield assertTrue(
          cause.failures == List("use failed"),
          cause.defects == List(releaseDied)
        )
      } @@ zioTag(errors)
    ),
    suite("bracket + disconnect")(
      testM("bracket happy path") {
        for {
          release <- Ref.make(false)
          result <- ZIO
                      .bracket(IO.succeed(42), (_: Int) => release.set(true), (a: Int) => ZIO.effectTotal(a + 1))
                      .disconnect
          released <- release.get
        } yield assertTrue(result == 43 && released)
      },
      testM("bracket_ happy path") {
        for {
          release  <- Ref.make(false)
          result   <- IO.succeed(42).bracket_(release.set(true), ZIO.effectTotal(0)).disconnect
          released <- release.get
        } yield assertTrue(result == 0 && released)
      },
      testM("bracketExit happy path") {
        for {
          release <- Ref.make(false)
          result <- ZIO
                      .bracketExit(
                        IO.succeed(42),
                        (_: Int, _: Exit[Any, Any]) => release.set(true),
                        (_: Int) => IO.succeed(0L)
                      )
                      .disconnect
          released <- release.get
        } yield assertTrue(result == 0L && released)
      },
      testM("bracketExit error handling") {
        val releaseDied: Throwable = new RuntimeException("release died")
        for {
          exit <- ZIO
                    .bracketExit[Any, String, Int, Int](
                      ZIO.succeed(42),
                      (_, _) => ZIO.die(releaseDied),
                      _ => ZIO.fail("use failed")
                    )
                    .disconnect
                    .run
          cause <- exit.foldM(cause => ZIO.succeed(cause), _ => ZIO.fail("effect should have failed"))
        } yield assertTrue(
          cause.failures == List("use failed"),
          cause.defects == List(releaseDied)
        )
      } @@ zioTag(errors),
      testM("bracketExit beast mode error handling") {
        val releaseDied: Throwable = new RuntimeException("release died")
        for {
          released <- ZRef.make(false)
          exit <- ZIO
                    .bracketExit[Any, String, Int, Int](
                      ZIO.succeed(42),
                      (_, _) => released.set(true),
                      _ => throw releaseDied
                    )
                    .disconnect
                    .run
          cause      <- exit.foldM(cause => ZIO.succeed(cause), _ => ZIO.fail("effect should have failed"))
          isReleased <- released.get
        } yield assertTrue(cause.defects == List(releaseDied) && isReleased)
      } @@ zioTag(errors)
    ),
    suite("cached")(
      testM("returns new instances after duration") {
        def incrementAndGet(ref: Ref[Int]): UIO[Int] = ref.updateAndGet(_ + 1)
        for {
          ref   <- Ref.make(0)
          cache <- incrementAndGet(ref).cached(60.minutes)
          a     <- cache
          _     <- TestClock.adjust(59.minutes)
          b     <- cache
          _     <- TestClock.adjust(1.minute)
          c     <- cache
          _     <- TestClock.adjust(59.minutes)
          d     <- cache
        } yield assertTrue(a == b, b != c, c == d)
      },
      testM("correctly handles an infinite duration time to live") {
        for {
          ref            <- Ref.make(0)
          getAndIncrement = ref.modify(curr => (curr, curr + 1))
          cached         <- getAndIncrement.cached(Duration.Infinity)
          a              <- cached
          b              <- cached
          c              <- cached
        } yield assertTrue(a == 0, b == 0, c == 0)
      }
    ),
    suite("cachedInvalidate")(
      testM("returns new instances after duration") {
        def incrementAndGet(ref: Ref[Int]): UIO[Int] = ref.updateAndGet(_ + 1)
        for {
          ref                 <- Ref.make(0)
          tuple               <- incrementAndGet(ref).cachedInvalidate(60.minutes)
          (cached, invalidate) = tuple
          a                   <- cached
          _                   <- TestClock.adjust(59.minutes)
          b                   <- cached
          _                   <- invalidate
          c                   <- cached
          _                   <- TestClock.adjust(1.minute)
          d                   <- cached
          _                   <- TestClock.adjust(59.minutes)
          e                   <- cached
        } yield assertTrue(a == b, b != c, c == d, d != e)
      }
    ),
    suite("catchAllDefect")(
      testM("recovers from all defects") {
        val s   = "division by zero"
        val zio = ZIO.die(new IllegalArgumentException(s))
        for {
          result <- zio.catchAllDefect(e => ZIO.succeed(e.getMessage))
        } yield assertTrue(result == s)
      },
      testM("leaves errors") {
        val t   = new IllegalArgumentException("division by zero")
        val zio = ZIO.fail(t)
        for {
          exit <- zio.catchAllDefect(e => ZIO.succeed(e.getMessage)).run
        } yield assertTrue(exit.$fail == t)
      },
      testM("leaves values") {
        val t   = new IllegalArgumentException("division by zero")
        val zio = ZIO.succeed(t)
        for {
          result <- zio.catchAllDefect(e => ZIO.succeed(e.getMessage))
        } yield assertTrue(result == t)
      }
    ) @@ zioTag(errors),
    suite("catchSomeCause")(
      testM("catches matching cause") {
        ZIO.interrupt.catchSomeCause {
          case c if c.interrupted => ZIO.succeed(true)
        }.sandbox.map(result => assertTrue(result))
      },
      testM("halts if cause doesn't match") {
        ZIO.fiberId.flatMap { fiberId =>
          ZIO.interrupt.catchSomeCause {
            case c if !c.interrupted => ZIO.succeed(true)
          }.sandbox.either.map(result => assertTrue(result.$left == Cause.interrupt(fiberId)))
        }
      }
    ) @@ zioTag(errors),
    suite("catchSomeDefect")(
      testM("recovers from some defects") {
        val s   = "division by zero"
        val zio = ZIO.die(new IllegalArgumentException(s))
        for {
          result <- zio.catchSomeDefect { case e: IllegalArgumentException =>
                      ZIO.succeed(e.getMessage)
                    }
        } yield assertTrue(result == s)
      },
      testM("leaves the rest") {
        val t   = new IllegalArgumentException("division by zero")
        val zio = ZIO.die(t)
        for {
          exit <- zio.catchSomeDefect { case e: NumberFormatException =>
                    ZIO.succeed(e.getMessage)
                  }.run
        } yield assertTrue(exit.$die == t)
      },
      testM("leaves errors") {
        val t   = new IllegalArgumentException("division by zero")
        val zio = ZIO.fail(t)
        for {
          exit <- zio.catchSomeDefect { case e: IllegalArgumentException =>
                    ZIO.succeed(e.getMessage)
                  }.run
        } yield assertTrue(exit.$fail == t)
      },
      testM("leaves values") {
        val t   = new IllegalArgumentException("division by zero")
        val zio = ZIO.succeed(t)
        for {
          result <- zio.catchSomeDefect { case e: IllegalArgumentException =>
                      ZIO.succeed(e.getMessage)
                    }
        } yield assertTrue(result == t)
      }
    ) @@ zioTag(errors),
    suite("collect")(
      testM("returns failure ignoring value") {
        for {
          goodCase <-
            exactlyOnce(0)(_.collect(s"value was not 0") { case v @ 0 => v }).sandbox.either
          badCase <-
            exactlyOnce(1)(_.collect(s"value was not 0") { case v @ 0 => v }).sandbox.either
              .map(_.left.flatMap(_.failureOrCause))
        } yield assertTrue(
          goodCase.$right == 0,
          badCase.$left == "value was not 0"
        )
      }
    ),
    suite("collectAllPar")(
      testM("returns the list in the same order") {
        val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
        IO.collectAllPar(list).map { result =>
          assertTrue(result == List(1, 2, 3))
        }

      },
      testM("is referentially transparent") {
        for {
          counter <- Ref.make(0)
          op       = counter.getAndUpdate(_ + 1)
          ops3     = ZIO.collectAllPar(List(op, op, op))
          ops6     = ops3.zipPar(ops3)
          res     <- ops6
        } yield assertTrue(res._1 != res._2)
      }
    ),
    suite("collectAllParN")(
      testM("returns results in the same order") {
        val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
        IO.collectAllParN(2)(list).map { result =>
          assertTrue(result == List(1, 2, 3))
        }
      }
    ),
    suite("collectAllParN_")(
      testM("preserves failures") {
        val tasks = List.fill(10)(ZIO.fail(new RuntimeException))
        ZIO.collectAllParN_(5)(tasks).flip.map { e =>
          assertTrue(e.$is[RuntimeException])
        }
      }
    ),
    suite("collectFirst")(
      testM("collects the first value for which the effectual functions returns Some") {
        checkM(Gen.listOf(Gen.anyInt), Gen.partialFunction(Gen.anyInt)) { (as, pf) =>
          def f(n: Int): UIO[Option[Int]] = UIO.succeed(pf.lift(n))
          ZIO.collectFirst(as)(f).map { result =>
            assertTrue(result == as.collectFirst(pf))
          }
        }
      }
    ),
    suite("collectM")(
      testM("returns failure ignoring value") {
        for {
          goodCase <-
            exactlyOnce(0)(
              _.collectM[Any, String, Int]("Predicate failed!") { case v @ 0 => ZIO.succeed(v) }
            ).sandbox.either
          partialBadCase <-
            exactlyOnce(0)(
              _.collectM("Predicate failed!") { case v @ 0 => ZIO.fail("Partial failed!") }
            ).sandbox.either
              .map(_.left.flatMap(_.failureOrCause))
          badCase <-
            exactlyOnce(1)(_.collectM("Predicate failed!") { case v @ 0 => ZIO.succeed(v) }).sandbox.either
              .map(_.left.flatMap(_.failureOrCause))
        } yield assertTrue(
          goodCase.$right == 0,
          partialBadCase.$left == "Partial failed!",
          badCase.$left == "Predicate failed!"
        )
      }
    ),
    suite("companion object method consistency")(
      testM("absolve") {
        checkM(Gen.alphaNumericString) { str =>
          val ioEither: UIO[Either[Nothing, String]] = IO.succeed(Right(str))
          for {
            abs1 <- ioEither.absolve
            abs2 <- IO.absolve(ioEither)
          } yield assertTrue(abs1 == abs2)
        }
      },
      testM("firstSuccessOf") {
        val io  = IO.effectTotal(testString)
        val ios = List.empty[UIO[String]]
        for {
          race1 <- io.firstSuccessOf(ios)
          race2 <- IO.firstSuccessOf(io, ios)
        } yield assertTrue(race1 == race2)
      },
      testM("flatten") {
        checkM(Gen.alphaNumericString) { str =>
          for {
            flatten1 <- IO.effectTotal(IO.effectTotal(str)).flatten
            flatten2 <- IO.flatten(IO.effectTotal(IO.effectTotal(str)))
          } yield assertTrue(flatten1 == flatten2)
        }
      },
      testM("raceAll") {
        val io  = IO.effectTotal(testString)
        val ios = List.empty[UIO[String]]
        for {
          race1 <- io.raceAll(ios)
          race2 <- IO.raceAll(io, ios)
        } yield assertTrue(race1 == race2)
      }
    ),
    suite("done")(
      testM("Check done lifts exit result into IO") {

        val fiberId = Fiber.Id(0L, 123L)
        val error   = exampleError

        for {
          completed   <- IO.done(Exit.succeed(1))
          interrupted <- IO.done(Exit.interrupt(fiberId)).run
          terminated  <- IO.done(Exit.die(error)).run
          failed      <- IO.done(Exit.fail(error)).run
        } yield assertTrue(
          completed == 1,
          // TODO: Create $isInterrupted
          interrupted.$interrupt.$is[Fiber.Id],
          terminated.$die == error,
          failed.$fail == error
        )
      }
    ),
    suite("executor")(
      testM("retrieves the current executor for this effect") {
        val executor = zio.internal.Executor.fromExecutionContext(100) {
          scala.concurrent.ExecutionContext.Implicits.global
        }
        for {
          default <- ZIO.executor
          global  <- ZIO.executor.lock(executor)
        } yield assertTrue(default != global)
      }
    ),
    suite("repeatUntil")(
      testM("repeatUntil repeats until condition is true") {
        for {
          in     <- Ref.make(10)
          out    <- Ref.make(0)
          _      <- (in.updateAndGet(_ - 1) <* out.update(_ + 1)).repeatUntil(_ == 0)
          result <- out.get
        } yield assertTrue(result == 10)
      },
      testM("repeatUntil always evaluates effect at least once") {
        for {
          ref    <- Ref.make(0)
          _      <- ref.update(_ + 1).repeatUntil(_ => true)
          result <- ref.get
        } yield assertTrue(result == 1)
      }
    ),
    suite("repeatUntilEquals")(
      testM("repeatUntilEquals repeats until result is equal to predicate") {
        for {
          q      <- Queue.unbounded[Int]
          _      <- q.offerAll(List(1, 2, 3, 4, 5, 6))
          acc    <- Ref.make(0)
          _      <- (q.take <* acc.update(_ + 1)).repeatUntilEquals(5)
          result <- acc.get
        } yield assertTrue(result == 5)
      }
    ),
    suite("repeatUntilM")(
      testM("repeatUntilM repeat until effectful condition is true") {
        for {
          in     <- Ref.make(10)
          out    <- Ref.make(0)
          _      <- (in.updateAndGet(_ - 1) <* out.update(_ + 1)).repeatUntilM(v => UIO.succeed(v == 0))
          result <- out.get
        } yield assertTrue(result == 10)
      },
      testM("repeatUntilM always evaluates effect at least once") {
        for {
          ref    <- Ref.make(0)
          _      <- ref.update(_ + 1).repeatUntilM(_ => UIO.succeed(true))
          result <- ref.get
        } yield assertTrue(result == 1)
      }
    ),
    suite("repeatWhile")(
      testM("repeatWhile repeats while condition is true") {
        for {
          in     <- Ref.make(10)
          out    <- Ref.make(0)
          _      <- (in.updateAndGet(_ - 1) <* out.update(_ + 1)).repeatWhile(_ >= 0)
          result <- out.get
        } yield assertTrue(result == 11)
      },
      testM("repeatWhile always evaluates effect at least once") {
        for {
          ref    <- Ref.make(0)
          _      <- ref.update(_ + 1).repeatWhile(_ => false)
          result <- ref.get
        } yield assertTrue(result == 1)
      }
    ),
    suite("repeatWhileEquals")(
      testM("repeatWhileEquals repeats while result equals predicate") {
        for {
          q      <- Queue.unbounded[Int]
          _      <- q.offerAll(List(0, 0, 0, 0, 1, 2))
          acc    <- Ref.make(0)
          _      <- (q.take <* acc.update(_ + 1)).repeatWhileEquals(0)
          result <- acc.get
        } yield assertTrue(result == 5)
      }
    ),
    suite("repeatWhileM")(
      testM("repeatWhileM repeats while condition is true") {
        for {
          in     <- Ref.make(10)
          out    <- Ref.make(0)
          _      <- (in.updateAndGet(_ - 1) <* out.update(_ + 1)).repeatWhileM(v => UIO.succeed(v >= 0))
          result <- out.get
        } yield assertTrue(result == 11)
      },
      testM("repeatWhileM always evaluates effect at least once") {
        for {
          ref    <- Ref.make(0)
          _      <- ref.update(_ + 1).repeatWhileM(_ => UIO.succeed(false))
          result <- ref.get
        } yield assertTrue(result == 1)
      }
    ),
    suite("eventually")(
      testM("succeeds eventually") {
        def effect(ref: Ref[Int]) =
          ref.get.flatMap(n => if (n < 10) ref.update(_ + 1) *> IO.fail("Ouch") else UIO.succeed(n))

        for {
          ref <- Ref.make(0)
          n   <- effect(ref).eventually
        } yield assertTrue(n == 10)
      }
    ),
    suite("exists")(
      testM("determines whether any element satisfies the effectual predicate") {
        checkM(Gen.listOf(Gen.anyInt), Gen.function(Gen.boolean)) { (as, f) =>
          val expected = as.exists(f)
          IO.exists(as)(a => IO.succeed(f(a))).map { result =>
            assertTrue(result == expected)
          }
        }
      }
    ),
    suite("filter")(
      testM("filters a collection using an effectual predicate") {
        val as = Iterable(2, 4, 6, 3, 5, 6)
        for {
          ref     <- Ref.make(List.empty[Int])
          results <- ZIO.filter(as)(a => ref.update(a :: _).as(a % 2 == 0))
          effects <- ref.get.map(_.reverse)
        } yield assertTrue(
          results == List(2, 4, 6, 6),
          effects == List(2, 4, 6, 3, 5, 6)
        )
      },
      testM("filters a set using an effectual predicate") {
        val as = Set(2, 3, 4, 5, 6, 7)
        for {
          ref     <- Ref.make(Set.empty[Int])
          results <- ZIO.filter(as)(a => ref.update(_ + a).as(a % 2 == 0))
          effects <- ref.get.map(_.map(_ + 1))
        } yield assertTrue(
          results == Set(2, 4, 6),
          effects == Set(3, 4, 5, 6, 7, 8)
        )
      }
    ),
    suite("filterNot")(
      testM("filters a collection using an effectual predicate") {
        val as = Iterable(2, 4, 6, 3, 5, 6)
        for {
          ref     <- Ref.make(List.empty[Int])
          results <- ZIO.filterNot(as)(a => ref.update(a :: _).as(a % 2 == 0))
          effects <- ref.get.map(_.reverse)
        } yield assertTrue(
          results == List(3, 5),
          effects == List(2, 4, 6, 3, 5, 6)
        )
      },
      testM("filters a set using an effectual predicate") {
        val as = Set(2, 3, 4, 5, 6, 7)
        for {
          ref     <- Ref.make(Set.empty[Int])
          results <- ZIO.filterNot(as)(a => ref.update(_ + a).as(a % 2 == 0))
          effects <- ref.get.map(_.map(_ + 1))
        } yield assertTrue(
          results == Set(3, 5, 7),
          effects == Set(3, 4, 5, 6, 7, 8)
        )
      }
    ),
    suite("filterPar")(
      testM("filters a collection in parallel using an effectual predicate") {
        val as = Iterable(2, 4, 6, 3, 5, 6, 10, 11, 15, 17, 20, 22, 23, 25, 28)
        for {
          results <- ZIO.filterPar(as)(a => UIO(a % 2 == 0))
        } yield assertTrue(results == List(2, 4, 6, 6, 10, 20, 22, 28))
      }
    ),
    suite("filterNotPar")(
      testM(
        "filters a collection in parallel using an effectual predicate, removing all elements that satisfy the predicate"
      ) {
        val as = Iterable(2, 4, 6, 3, 5, 6, 10, 11, 15, 17, 20, 22, 23, 25, 28)
        for {
          results <- ZIO.filterNotPar(as)(a => UIO(a % 2 == 0))
        } yield assertTrue(results == List(3, 5, 11, 15, 17, 23, 25))
      }
    ),
    suite("filterOrElse")(
      testM("returns checked failure from held value") {
        for {
          goodCase <-
            exactlyOnce(0)(_.filterOrElse[Any, String, Int](_ == 0)(a => ZIO.fail(s"$a was not 0"))).sandbox.either

          badCase <-
            exactlyOnce(1)(_.filterOrElse[Any, String, Int](_ == 0)(a => ZIO.fail(s"$a was not 0"))).sandbox.either
              .map(_.left.flatMap(_.failureOrCause))

        } yield assertTrue(
          goodCase.$right == 0,
          badCase.$left == "1 was not 0"
        )
      }
    ),
    suite("filterOrElse_")(
      testM("returns checked failure ignoring value") {
        for {
          goodCase <-
            exactlyOnce(0)(_.filterOrElse_[Any, String, Int](_ == 0)(ZIO.fail(s"Predicate failed!"))).sandbox.either

          badCase <-
            exactlyOnce(1)(_.filterOrElse_[Any, String, Int](_ == 0)(ZIO.fail(s"Predicate failed!"))).sandbox.either
              .map(_.left.flatMap(_.failureOrCause))

        } yield assertTrue(
          goodCase.$right == (0),
          badCase.$left == "Predicate failed!"
        )
      }
    ),
    suite("filterOrFail")(
      testM("returns failure ignoring value") {
        for {
          goodCase <-
            exactlyOnce(0)(_.filterOrFail(_ == 0)("Predicate failed!")).sandbox.either
          badCase <-
            exactlyOnce(1)(_.filterOrFail(_ == 0)("Predicate failed!")).sandbox.either
              .map(_.left.flatMap(_.failureOrCause))
        } yield assertTrue(
          goodCase.$right == 0,
          badCase.$left == "Predicate failed!"
        )
      }
    ) @@ zioTag(errors),
    suite("flattenErrorOption")(
      testM("fails when given Some error") {
        IO.fail(Some("Error")).flattenErrorOption("Default").run.map { exit =>
          assertTrue(exit.$fail == "Error")
        }
      },
      testM("fails with Default when given None error") {
        IO.fail(None).flattenErrorOption("Default").run.map { exit =>
          assertTrue(exit.$fail == "Default")
        }
      },
      testM("succeeds when given a value") {
        IO.succeed(1).flattenErrorOption("Default").map { result =>
          assertTrue(result == 1)
        }
      }
    ) @@ zioTag(errors),
    suite("foldLeft")(
      testM("with a successful step function sums the list properly") {
        checkM(Gen.listOf(Gen.anyInt)) { l =>
          IO.foldLeft(l)(0)((acc, el) => IO.succeed(acc + el)).map { result =>
            assertTrue(result == l.sum)
          }
        }
      },
      testM("`with a failing step function returns a failed IO") {
        checkM(Gen.listOf1(Gen.anyInt)) { l =>
          IO.foldLeft(l)(0)((_, _) => IO.fail("fail")).run.map { exit =>
            assertTrue(exit.$fail == "fail")
          }
        }
      } @@ zioTag(errors),
      testM("run sequentially from left to right") {
        checkM(Gen.listOf1(Gen.anyInt)) { l =>
          IO.foldLeft(l)(List.empty[Int])((acc, el) => IO.succeed(el :: acc)).map { res =>
            assertTrue(res == l.reverse)
          }
        }
      }
    ),
    suite("foldRight")(
      testM("with a successful step function sums the list properly") {
        checkM(Gen.listOf(Gen.anyInt)) { l =>
          IO.foldRight(l)(0)((el, acc) => IO.succeed(acc + el)).map { res =>
            assertTrue(res == l.sum)
          }
        }
      },
      testM("`with a failing step function returns a failed IO") {
        checkM(Gen.listOf1(Gen.anyInt)) { l =>
          IO.foldRight(l)(0)((_, _) => IO.fail("fail")).run.map { exit =>
            assertTrue(exit.$fail == "fail")
          }
        }
      } @@ zioTag(errors),
      testM("run sequentially from right to left") {
        checkM(Gen.listOf1(Gen.anyInt)) { l =>
          IO.foldRight(l)(List.empty[Int])((el, acc) => IO.succeed(el :: acc)).map { res =>
            assertTrue(res == l)
          }
        }
      }
    ),
    suite("forall")(
      testM("determines whether all elements satisfy the effectual predicate") {
        checkM(Gen.listOf(Gen.anyInt), Gen.function(Gen.boolean)) { (as, f) =>
          val expected = as.forall(f)
          IO.forall(as)(a => IO.succeed(f(a))).map { actual =>
            assertTrue(actual == expected)
          }
        }
      }
    ),
    suite("foreach")(
      testM("returns the list of results") {
        checkAllM(functionIOGen, listGen) { (f, list) =>
          IO.foreach(list)(f).map { res =>
            assertTrue(res.$is[List[Int]], res.size == 100)
          }
        }
      },
      testM("both evaluates effects and returns the list of results in the same order") {
        val list = List("1", "2", "3")
        for {
          ref     <- Ref.make(List.empty[String])
          res     <- IO.foreach(list)(x => ref.update(_ :+ x) *> IO.effectTotal[Int](x.toInt))
          effects <- ref.get
        } yield assertTrue(effects == list && res == List(1, 2, 3))
      },
      testM("fails if one of the effects fails") {
        val list = List("1", "h", "3")
        IO.foreach(list)(x => IO.effectTotal[Int](x.toInt)).run.map { exit =>
          assertTrue(exit.$die.$is[NumberFormatException])
        }
      } @@ zioTag(errors)
    ),
    suite("foreach_")(
      testM("runs effects in order") {
        val as = List(1, 2, 3, 4, 5)
        for {
          ref <- Ref.make(List.empty[Int])
          _   <- ZIO.foreach_(as)(a => ref.update(_ :+ a))
          rs  <- ref.get
        } yield assertTrue(rs == as)
      },
      testM("can be run twice") {
        val as = List(1, 2, 3, 4, 5)
        for {
          ref <- Ref.make(0)
          zio  = ZIO.foreach_(as)(a => ref.update(_ + a))
          _   <- zio
          _   <- zio
          sum <- ref.get
        } yield assertTrue(sum == 30)
      }
    ),
    suite("foreach for Option")(
      testM("succeeds with None given None") {
        IO.foreach(None)((str: String) => IO.succeed(str.length)).map { result =>
          assertTrue(result.isEmpty)
        }
      },
      testM("succeeds with Some given Some") {
        for {
          optRes <- IO.foreach(Some("success"))(str => IO.succeed(str.length))
        } yield assertTrue(optRes.$some == 7)
      },
      testM("fails if the optional effect fails") {
        val opt = Some("h")
        IO.foreach(opt)(x => IO.effectTotal[Int](x.toInt)).run.map { res =>
          assertTrue(res.$die.$is[NumberFormatException])
        }
      }
    ),
    suite("foreachPar")(
      testM("runs single task") {
        val as = List(2)
        IO.foreachPar(as)(a => IO.succeed(2 * a)).map { results =>
          assertTrue(results == List(4))
        }
      },
      testM("runs two tasks") {
        val as = List(2, 3)
        IO.foreachPar(as)(a => IO.succeed(2 * a)).map { results =>
          assertTrue(results == List(4, 6))
        }
      },
      testM("runs many tasks") {
        val as = 1 to 1000
        IO.foreachPar(as)(a => IO.succeed(2 * a)).map { results =>
          assertTrue(results == as.map(2 * _))
        }
      },
      testM("runs a task that fails") {
        val as = 1 to 10
        IO
          .foreachPar(as) {
            case 5 => IO.fail("Boom!")
            case a => IO.succeed(2 * a)
          }
          .flip
          .map { results =>
            assertTrue(results == "Boom!")
          }
      },
      testM("runs two failed tasks") {
        val as = 1 to 10
        IO
          .foreachPar(as) {
            case 5 => IO.fail("Boom1!")
            case 8 => IO.fail("Boom2!")
            case a => IO.succeed(2 * a)
          }
          .flip
          .map { results =>
            assertTrue(results == "Boom1!" || results == "Boom2!")
          }
      },
      testM("runs a task that dies") {
        val as = 1 to 10
        IO
          .foreachPar(as) {
            case 5 => IO.dieMessage("Boom!")
            case a => IO.succeed(2 * a)
          }
          .run
          .map { exit =>
            assertTrue(exit.$die.getMessage == "Boom!")
          }
      },
      testM("runs a task that is interrupted") {
        val as = 1 to 10
        IO
          .foreachPar(as) {
            case 5 => IO.interrupt
            case a => IO.succeed(2 * a)
          }
          .run
          .map { exit =>
            assertTrue(exit.$interrupt.$is[Fiber.Id])
          }
      } @@ zioTag(interruption),
      testM("runs a task that throws an unsuspended exception") {
        def f(i: Int): Task[Int] = throw new Exception(i.toString)
        for {
          _ <- IO.foreachPar(1 to 1)(f).run
        } yield assertCompletes
      },
      testM("returns results in the same order") {
        val list = List("1", "2", "3")
        IO.foreachPar(list)(x => IO.effectTotal[Int](x.toInt)).map { res =>
          assertTrue(res == List(1, 2, 3))
        }
      },
      testM("returns results in the same order for Chunk") {
        val chunk = Chunk("1", "2", "3")
        IO.foreachPar(chunk)(x => IO.effectTotal[Int](x.toInt)).map { res =>
          assertTrue(res == Chunk(1, 2, 3))
        }
      },
      testM("runs effects in parallel") {
        for {
          p <- Promise.make[Nothing, Unit]
          _ <- UIO.foreachPar(List(UIO.never, p.succeed(())))(a => a).fork
          _ <- p.await
        } yield assertCompletes
      },
      testM("runs effects in parallel for Chunk") {
        for {
          p <- Promise.make[Nothing, Unit]
          _ <- UIO.foreachPar(Chunk(UIO.never, p.succeed(()), UIO.never))(a => a).fork
          _ <- p.await
        } yield assertCompletes
      },
      testM("propagates error") {
        val ints = List(1, 2, 3, 4, 5, 6)
        ZIO.foreachPar(ints)(n => if (n % 2 != 0) ZIO.succeed(n) else ZIO.fail("not odd")).flip.map { res =>
          assertTrue(res == "not odd")
        }
      } @@ zioTag(errors),
      testM("interrupts effects on first failure") {
        for {
          ref     <- Ref.make(false)
          promise <- Promise.make[Nothing, Unit]
          actions = List(
                      ZIO.never,
                      ZIO.succeed(1),
                      ZIO.fail("C"),
                      promise.await *> ref.set(true)
                    )
          e <- ZIO.foreachPar(actions)(a => a).flip
          v <- ref.get
        } yield assertTrue(e == "C" && !v)
      } @@ zioTag(interruption),
      testM("does not kill fiber when forked on the parent scope") {
        for {
          ref    <- Ref.make(0)
          fibers <- ZIO.foreachPar(1 to 100)(_ => ref.update(_ + 1).fork)
          _      <- ZIO.foreach(fibers)(_.await)
          value  <- ref.get
        } yield assertTrue(value == 100)
      }
    ),
    suite("foreachPar_")(
      testM("accumulates errors") {
        def task(started: Ref[Int], trigger: Promise[Nothing, Unit])(i: Int): IO[Int, Unit] =
          started.updateAndGet(_ + 1) >>= { count =>
            IO.when(count == 3)(trigger.succeed(())) *> trigger.await *> IO.fail(i)
          }

        for {
          started <- Ref.make(0)
          trigger <- Promise.make[Nothing, Unit]

          errors <- IO
                      .foreachPar_(1 to 3)(i => task(started, trigger)(i).uninterruptible)
                      .foldCause(cause => cause.failures.toSet, _ => Set.empty[Int])
        } yield assertTrue(errors == Set(1, 2, 3))
      } @@ zioTag(errors),
      testM("runs all effects") {
        val as = Seq(1, 2, 3, 4, 5)
        for {
          ref <- Ref.make(Seq.empty[Int])
          _   <- ZIO.foreachPar_(as)(a => ref.update(_ :+ a))
          rs  <- ref.get
        } yield assertTrue(
          rs.length == as.length,
          rs.toSet == as.toSet
        )
      },
      testM("runs all effects for Chunk") {
        val as = Chunk(1, 2, 3, 4, 5)
        for {
          ref <- Ref.make(Seq.empty[Int])
          _   <- ZIO.foreachPar_(as)(a => ref.update(_ :+ a))
          rs  <- ref.get
        } yield assertTrue(
          rs.length == as.length,
          rs.toSet == as.toSet
        )
      },
      testM("completes on empty input") {
        ZIO.foreachPar_(Nil)(_ => ZIO.unit).as(assertCompletes)
      }
    ),
    suite("foreachParN")(
      testM("returns the list of results in the appropriate order") {
        val list = List(1, 2, 3)
        IO.foreachParN(2)(list)(x => IO.effectTotal(x.toString)).map { res =>
          assertTrue(res == List("1", "2", "3"))
        }
      },
      testM("works on large lists") {
        val n   = 10
        val seq = List.range(0, 100000)
        IO.foreachParN(n)(seq)(UIO.succeed(_)).map { res =>
          assertTrue(res == seq)
        }
      },
      testM("runs effects in parallel") {
        for {
          p <- Promise.make[Nothing, Unit]
          _ <- UIO.foreachParN(2)(List(UIO.never, p.succeed(())))(identity).fork
          _ <- p.await
        } yield assertCompletes
      },
      testM("propagates error") {
        val ints = List(1, 2, 3, 4, 5, 6)
        ZIO.foreachParN(4)(ints)(n => if (n % 2 != 0) ZIO.succeed(n) else ZIO.fail("not odd")).either.map { res =>
          assertTrue(res.$left == "not odd")
        }
      } @@ zioTag(errors),
      testM("interrupts effects on first failure") {
        val actions = List(
          ZIO.never,
          ZIO.succeed(1),
          ZIO.fail("C")
        )
        ZIO.foreachParN(4)(actions)(a => a).either.map { result =>
          assertTrue(result.$left == "C")
        }
      } @@ zioTag(errors, interruption)
    ),
    suite("foreachParN_")(
      testM("runs all effects") {
        val as = Seq(1, 2, 3, 4, 5)
        for {
          ref <- Ref.make(Seq.empty[Int])
          _   <- ZIO.foreachParN_(2)(as)(a => ref.update(_ :+ a))
          rs  <- ref.get
        } yield assertTrue(
          rs.length == as.length,
          rs.toSet == as.toSet
        )
      }
    ),
    suite("forkAll")(
      testM("returns the list of results in the same order") {
        val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
        IO.forkAll(list).flatMap[Any, Nothing, List[Int]](_.join).map { res =>
          assertTrue(res == List(1, 2, 3))
        }
      },
      testM("happy-path") {
        val list = (1 to 1000).toList
        ZIO.forkAll(list.map(a => ZIO.effectTotal(a))).flatMap(_.join).map { res =>
          assertTrue(res == list)
        }
      },
      testM("empty input") {
        ZIO.forkAll(List.empty[ZIO[Any, Nothing, Unit]]).flatMap(_.join).map { res =>
          assertTrue(res == List.empty)
        }
      },
      testM("propagate failures") {
        val boom             = new Exception
        val fail: Task[Unit] = ZIO.fail(boom)
        for {
          fiber  <- ZIO.forkAll(List(fail))
          result <- fiber.join.flip
        } yield assertTrue(result == boom)
      },
      testM("propagates defects") {
        val boom                                 = new Exception("boom")
        val die: UIO[Unit]                       = ZIO.die(boom)
        def joinDefect(fiber: Fiber[Nothing, _]) = fiber.join.sandbox.flip
        for {
          fiber1 <- ZIO.forkAll(List(die))
          fiber2 <- ZIO.forkAll(List(die, ZIO.succeed(42)))
          fiber3 <- ZIO.forkAll(List(die, ZIO.succeed(42), ZIO.never))

          result1 <- joinDefect(fiber1)
          result2 <- joinDefect(fiber2)
          result3 <- joinDefect(fiber3)
        } yield {
          assertTrue(
            result1.$die == boom,
            result2.$die == boom || result2.interrupted,
            result3.$die == boom || result3.interrupted
          )
        }
      } @@ nonFlaky,
      testM("infers correctly") {
        for {
          ref    <- Ref.make(0)
          worker  = ZIO.never
          workers = List.fill(4)(worker)
          fiber  <- ZIO.forkAll(workers)
          _      <- fiber.interrupt
          value  <- ref.get
        } yield assertTrue(value == 0)
      }
    ),
    suite("forkAs")(
      testM("child has specified name") {
        for {
          fiber <- Fiber.fiberName.get.forkAs("child")
          name  <- fiber.join
        } yield assertTrue(name.$some == "child")
      },
      testM("parent name is unchanged") {
        for {
          _    <- ZIO.unit.forkAs("child")
          name <- Fiber.fiberName.get
        } yield assertTrue(name.isEmpty)
      },
      testM("parent does not inherit child name on join") {
        for {
          fiber <- ZIO.unit.forkAs("child")
          _     <- fiber.join
          name  <- Fiber.fiberName.get
        } yield assertTrue(name.isEmpty)
      }
    ),
    suite("forkIn") {
      testM("fiber forked in a closed scope does not run") {
        for {
          ref   <- Ref.make(false)
          open  <- ZScope.make[Exit[Any, Any]]
          _     <- open.close(Exit.unit)
          fiber <- ref.set(true).forkIn(open.scope)
          exit  <- fiber.await
          value <- ref.get
        } yield assertTrue(exit.interrupted && !value)
      }
    },
    suite("forkWithErrorHandler")(
      testM("calls provided function when task fails") {
        for {
          p <- Promise.make[Nothing, Unit]
          _ <- ZIO.fail(()).forkWithErrorHandler(p.succeed(_).unit)
          _ <- p.await
        } yield assertCompletes
      }
    ) @@ zioTag(errors),
    suite("fromFutureInterrupt")(
      testM("running Future can be interrupted") {
        import java.util.concurrent.atomic.AtomicInteger

        import scala.concurrent.{ExecutionContext, Future}
        def infiniteFuture(ref: AtomicInteger)(implicit ec: ExecutionContext): Future[Nothing] =
          Future(ref.getAndIncrement()).flatMap(_ => infiniteFuture(ref))
        for {
          ref   <- ZIO.effectTotal(new AtomicInteger(0))
          fiber <- ZIO.fromFutureInterrupt(ec => infiniteFuture(ref)(ec)).fork
          _     <- fiber.interrupt
          v1    <- ZIO.effectTotal(ref.get)
          _     <- Live.live(clock.sleep(10.milliseconds))
          v2    <- ZIO.effectTotal(ref.get)
        } yield assertTrue(v1 == v2)
      },
      testM("interruption blocks on interruption of the Future") {
        import scala.concurrent.Promise
        for {
          latch  <- ZIO.effectTotal(Promise[Unit]())
          fiber  <- ZIO.fromFutureInterrupt { _ => latch.success(()); Promise[Unit]().future }.forkDaemon
          _      <- ZIO.fromFuture(_ => latch.future).orDie
          result <- Live.withLive(fiber.interrupt)(_.timeout(10.milliseconds))
        } yield assertTrue(result.isEmpty)
      }
    ) @@ zioTag(future, interruption) @@ jvmOnly,
    suite("head")(
      testM("on non empty list") {
        ZIO.succeed(List(1, 2, 3)).head.either.map { res =>
          assertTrue(res.$right == 1)
        }
      },
      testM("on empty list") {
        ZIO.succeed(List.empty).head.either.map { res =>
          assertTrue(res.$left.isEmpty)
        }
      },
      testM("on failure") {
        ZIO.fail("Fail").head.run.map { exit =>
          assertTrue(exit.$fail.$some == "Fail")
        }
      } @@ zioTag(errors)
    ),
    suite("ifM")(
      testM("runs `onTrue` if result of `b` is `true`") {
        ZIO.ifM(ZIO.succeed(true))(ZIO.succeed(true), ZIO.succeed(false)).map { res =>
          assertTrue(res)
        }
      },
      testM("runs `onFalse` if result of `b` is `false`") {
        ZIO.ifM(ZIO.succeed(false))(ZIO.succeed(true), ZIO.succeed(false)).map { res =>
          assertTrue(!res)
        }
      },
      testM("infers correctly") {
        trait R
        trait R1 extends R
        trait E1
        trait E extends E1
        trait A
        val b: ZIO[R, E, Boolean]   = ZIO.succeed(true)
        val onTrue: ZIO[R1, E1, A]  = ZIO.succeed(new A {})
        val onFalse: ZIO[R1, E1, A] = ZIO.succeed(new A {})
        val _                       = ZIO.ifM(b)(onTrue, onFalse)
        ZIO.succeed(assertCompletes)
      }
    ),
    suite("ignore")(
      testM("return success as Unit") {
        ZIO.succeed(11).ignore.map { res =>
          assertTrue(res == ())
        }
      },
      testM("return failure as Unit") {
        ZIO.fail(123).ignore.map { res =>
          assertTrue(res == ())
        }
      } @@ zioTag(errors),
      testM("not catch throwable") {
        ZIO.die(ExampleError).ignore.run.map { res =>
          assertTrue(res.$die == ExampleError)
        }
      } @@ zioTag(errors)
    ),
    suite("isFailure")(
      testM("returns true when the effect is a failure") {
        ZIO.fail("fail").isFailure.map { res =>
          assertTrue(res)
        }
      },
      testM("returns false when the effect is a success") {
        ZIO.succeed("succeed").isFailure.map { res =>
          assertTrue(!res)
        }
      }
    ),
    suite("isSuccess")(
      testM("returns false when the effect is a failure") {
        ZIO.fail("fail").isSuccess.map { res =>
          assertTrue(!res)
        }
      },
      testM("returns true when the effect is a success") {
        ZIO.succeed("succeed").isSuccess.map { res =>
          assertTrue(res)
        }
      }
    ),
    suite("iterate")(
      testM("iterates with the specified effectual function") {
        for {
          result <- ZIO.iterate(100)(_ > 0)(a => ZIO.succeed(a - 1))
        } yield assertTrue(result == 0)
      }
    ),
    suite("left")(
      testM("on Left value") {
        ZIO.succeed(Left("Left")).left.map { res =>
          assertTrue(res == "Left")
        }
      },
      testM("on Right value") {
        ZIO.succeed(Right("Right")).left.run.map { exit =>
          assertTrue(exit.$fail.isEmpty)
        }
      },
      testM("on failure") {
        ZIO.fail("Fail").left.either.map { res =>
          assertTrue(res.$left.$some == "Fail")
        }
      } @@ zioTag(errors)
    ),
    suite("leftOrFail")(
      testM("on Left value") {
        UIO(Left(42)).leftOrFail(ExampleError).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("on Right value") {
        UIO(Right(12)).leftOrFail(ExampleError).flip.map { res =>
          assertTrue(res == ExampleError)
        }
      } @@ zioTag(errors)
    ),
    suite("leftOrFailWith")(
      testM("on Left value") {
        Task(Left(42)).leftOrFailWith((x: Throwable) => x).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("on Right value") {
        Task(Right(ExampleError)).leftOrFailWith((x: Throwable) => x).flip.map { res =>
          assertTrue(res == ExampleError)
        }
      } @@ zioTag(errors)
    ),
    suite("leftOrFailException")(
      testM("on Left value") {
        ZIO.succeed(Left(42)).leftOrFailException.map { res =>
          assertTrue(res == 42)
        }
      },
      testM("on Right value") {
        ZIO.succeed(Right(2)).leftOrFailException.run.map { exit =>
          assertTrue(exit.$fail.$is[NoSuchElementException])
        }
      } @@ zioTag(errors)
    ),
    suite("loop")(
      testM("loops with the specified effectual function") {
        for {
          ref    <- Ref.make(List.empty[Int])
          _      <- ZIO.loop(0)(_ < 5, _ + 1)(a => ref.update(a :: _))
          result <- ref.get.map(_.reverse)
        } yield assertTrue(result == List(0, 1, 2, 3, 4))
      },
      testM("collects the results into a list") {
        for {
          result <- ZIO.loop(0)(_ < 5, _ + 2)(a => ZIO.succeed(a * 3))
        } yield assertTrue(result == List(0, 6, 12))
      }
    ),
    suite("loop_")(
      testM("loops with the specified effectual function") {
        for {
          ref    <- Ref.make(List.empty[Int])
          _      <- ZIO.loop_(0)(_ < 5, _ + 1)(a => ref.update(a :: _))
          result <- ref.get.map(_.reverse)
        } yield assertTrue(result == List(0, 1, 2, 3, 4))
      }
    ),
    suite("mapBoth")(
      testM("maps over both error and value channels") {
        checkM(Gen.anyInt) { i =>
          IO.fail(i).mapBoth(_.toString, identity).either.map { res =>
            assertTrue(res.$left == i.toString)
          }
        }
      }
    ),
    suite("mapEffect")(
      testM("returns an effect whose success is mapped by the specified side effecting function") {
        ZIO.succeed("123").mapEffect(_.toInt).map { res =>
          assertTrue(res == 123)
        }
      },
      testM("translates any thrown exceptions into typed failed effects") {
        ZIO.succeed("hello").mapEffect(_.toInt).run.map { res =>
          assertTrue(res.$fail.$is[NumberFormatException])
        }
      }
    ),
    suite("mapN")(
      testM("with Tuple2") {
        checkM(Gen.anyInt, Gen.alphaNumericString) { (int: Int, str: String) =>
          def f(i: Int, s: String): String = i.toString + s
          val expected                     = f(int, str)
          ZIO.mapN(ZIO.succeed(int), ZIO.succeed(str))(f).map { actual =>
            assertTrue(actual == expected)
          }
        }
      },
      testM("with Tuple3") {
        checkM(Gen.anyInt, Gen.alphaNumericString, Gen.alphaNumericString) { (int: Int, str1: String, str2: String) =>
          def f(i: Int, s1: String, s2: String): String = i.toString + s1 + s2
          val expected                                  = f(int, str1, str2)
          ZIO.mapN(ZIO.succeed(int), ZIO.succeed(str1), ZIO.succeed(str2))(f).map { actual =>
            assertTrue(actual == expected)
          }
        }
      },
      testM("with Tuple4") {
        checkM(Gen.anyInt, Gen.alphaNumericString, Gen.alphaNumericString, Gen.alphaNumericString) {
          (int: Int, str1: String, str2: String, str3: String) =>
            def f(i: Int, s1: String, s2: String, s3: String): String = i.toString + s1 + s2 + s3
            val expected                                              = f(int, str1, str2, str3)
            ZIO.mapN(ZIO.succeed(int), ZIO.succeed(str1), ZIO.succeed(str2), ZIO.succeed(str3))(f).map { actual =>
              assertTrue(actual == expected)
            }
        }
      }
    ),
    suite("mapParN")(
      testM("with Tuple2") {
        checkM(Gen.anyInt, Gen.alphaNumericString) { (int: Int, str: String) =>
          def f(i: Int, s: String): String = i.toString + s
          val expected                     = f(int, str)
          ZIO.mapParN(ZIO.succeed(int), ZIO.succeed(str))(f).map { actual =>
            assertTrue(actual == expected)
          }
        }
      },
      testM("with Tuple3") {
        checkM(Gen.anyInt, Gen.alphaNumericString, Gen.alphaNumericString) { (int: Int, str1: String, str2: String) =>
          def f(i: Int, s1: String, s2: String): String = i.toString + s1 + s2
          val expected                                  = f(int, str1, str2)
          ZIO.mapParN(ZIO.succeed(int), ZIO.succeed(str1), ZIO.succeed(str2))(f).map { actual =>
            assertTrue(actual == expected)
          }
        }
      },
      testM("with Tuple4") {
        checkM(Gen.anyInt, Gen.alphaNumericString, Gen.alphaNumericString, Gen.alphaNumericString) {
          (int: Int, str1: String, str2: String, str3: String) =>
            def f(i: Int, s1: String, s2: String, s3: String): String = i.toString + s1 + s2 + s3
            val expected                                              = f(int, str1, str2, str3)
            ZIO.mapParN(ZIO.succeed(int), ZIO.succeed(str1), ZIO.succeed(str2), ZIO.succeed(str3))(f).map { actual =>
              assertTrue(actual == expected)
            }
        }
      }
    ),
    suite("memoize")(
      testM("non-memoized returns new instances on repeated calls") {
        val io = random.nextString(10)
        (io <*> io)
          .map(tuple => assertTrue(tuple._1 != tuple._2))
      },
      testM("memoized returns the same instance on repeated calls") {
        val ioMemo = random.nextString(10).memoize
        ioMemo
          .flatMap(io => io <*> io)
          .map(tuple => assertTrue(tuple._1 == tuple._2))
      },
      testM("memoized function returns the same instance on repeated calls") {
        for {
          memoized <- ZIO.memoize((n: Int) => random.nextString(n))
          a        <- memoized(10)
          b        <- memoized(10)
          c        <- memoized(11)
          d        <- memoized(11)
        } yield assertTrue(a == b, b != c, c == d)
      }
    ),
    suite("merge")(
      testM("on flipped result") {
        val zio: IO[Int, Int] = ZIO.succeed(1)

        for {
          a <- zio.merge
          b <- zio.flip.merge
        } yield assertTrue(a == b)
      }
    ),
    suite("mergeAll")(
      testM("return zero element on empty input") {
        val zeroElement = 42
        val nonZero     = 43
        UIO.mergeAll(Nil)(zeroElement)((_, _) => nonZero).map { res =>
          assertTrue(res == zeroElement)
        }
      },
      testM("merge list using function") {
        val effects = List(3, 5, 7).map(UIO.succeed(_))
        UIO.mergeAll(effects)(zero = 1)(_ + _).map { res =>
          assertTrue(res == 1 + 3 + 5 + 7)
        }
      },
      testM("return error if it exists in list") {
        val effects = List(UIO.unit, ZIO.fail(1))
        ZIO.mergeAll(effects)(zero = ())((_, _) => ()).run.map { exit =>
          assertTrue(exit.$fail == 1)
        }
      } @@ zioTag(errors)
    ),
    suite("mergeAllPar")(
      testM("return zero element on empty input") {
        val zeroElement = 42
        val nonZero     = 43
        UIO.mergeAllPar(Nil)(zeroElement)((_, _) => nonZero).map { res =>
          assertTrue(res == zeroElement)
        }
      },
      testM("merge list using function") {
        val effects = List(3, 5, 7).map(UIO.succeed(_))
        UIO.mergeAllPar(effects)(zero = 1)(_ + _).map { res =>
          assertTrue(res == 1 + 3 + 5 + 7)
        }
      },
      testM("return error if it exists in list") {
        val effects = List(UIO.unit, ZIO.fail(1))
        ZIO.mergeAllPar(effects)(zero = ())((_, _) => ()).run.map { exit =>
          assertTrue(exit.$fail == 1)
        }
      } @@ zioTag(errors)
    ),
    suite("none")(
      testM("on Some fails with None") {
        Task(Some(1)).none.run.map { exit =>
          assertTrue(exit.$fail.isEmpty)
        }
      },
      testM("on None succeeds with ()") {
        Task(None).none.map { res =>
          assertTrue(res == ())
        }
      },
      testM("fails with Some(ex) when effect fails with ex") {
        val ex = new RuntimeException("Failed Task")
        Task.fail(ex).none.run.map { exit =>
          assertTrue(exit.$fail.$some == ex)
        }
      } @@ zioTag(errors)
    ),
    suite("negate")(
      testM("on true returns false") {
        ZIO.succeed(true).negate.map { res =>
          assertTrue(res == false)
        }
      },
      testM("on false returns true") {
        ZIO.succeed(false).negate.map { res =>
          assertTrue(res == true)
        }
      }
    ),
    suite("noneOrFail")(
      testM("on None succeeds with Unit") {
        val option: Option[String] = None
        ZIO.noneOrFail(option).map { res =>
          assertTrue(res == ())
        }
      },
      testM("on Some fails") {
        for {
          value <- ZIO.noneOrFail(Some("v")).catchAll(e => ZIO.succeed(e))
        } yield assertTrue(value == "v")
      } @@ zioTag(errors)
    ),
    suite("noneOrFailWith")(
      testM("on None succeeds with Unit") {
        val option: Option[String]       = None
        val adaptError: String => String = identity
        for {
          value <- ZIO.noneOrFailWith(option)(adaptError)
        } yield assertTrue(value == ())
      },
      testM("on Some fails") {
        for {
          value <- ZIO.noneOrFailWith(Some("value"))((v: String) => v + v).catchAll(e => ZIO.succeed(e))
        } yield assertTrue(value == "valuevalue")
      } @@ zioTag(errors)
    ),
    suite("once")(
      testM("returns an effect that will only be executed once") {
        for {
          ref    <- Ref.make(0)
          zio    <- ref.update(_ + 1).once
          _      <- ZIO.collectAllPar(ZIO.replicate(100)(zio))
          result <- ref.get
        } yield assertTrue(result == 1)
      }
    ),
    suite("onExit")(
      testM("executes that a cleanup function runs when effect succeeds") {
        for {
          ref <- Ref.make(false)
          _ <- ZIO.unit.onExit {
                 case Exit.Success(_) => ref.set(true)
                 case _               => UIO.unit
               }
          p <- ref.get
        } yield assertTrue(p)
      },
      testM("ensures that a cleanup function runs when an effect fails") {
        for {
          ref <- Ref.make(false)
          _ <- ZIO
                 .die(new RuntimeException)
                 .onExit {
                   case Exit.Failure(c) if c.died => ref.set(true)
                   case _                         => UIO.unit
                 }
                 .sandbox
                 .ignore
          p <- ref.get
        } yield assertTrue(p)
      },
      testM("ensures that a cleanup function runs when an effect is interrupted") {
        for {
          latch1 <- Promise.make[Nothing, Unit]
          latch2 <- Promise.make[Nothing, Unit]
          fiber <- (latch1.succeed(()) *> ZIO.never).onExit {
                     case Exit.Failure(c) if c.interrupted => latch2.succeed(())
                     case _                                => UIO.unit
                   }.fork
          _ <- latch1.await
          _ <- fiber.interrupt
          _ <- latch2.await
        } yield assertCompletes
      } @@ zioTag(interruption)
    ),
    suite("option")(
      testM("return success in Some") {
        implicit val canFail = CanFail
        ZIO.succeed(11).option.map { res =>
          assertTrue(res.$some == 11)
        }
      },
      testM("return failure as None") {
        ZIO.fail(123).option.map { res =>
          assertTrue(res.isEmpty)
        }
      } @@ zioTag(errors),
      testM("not catch throwable") {
        implicit val canFail = CanFail
        ZIO.die(ExampleError).option.run.map { exit =>
          assertTrue(exit.$die == ExampleError)
        }
      } @@ zioTag(errors),
      testM("catch throwable after sandboxing") {
        ZIO.die(ExampleError).sandbox.option.map { exit =>
          assertTrue(exit.isEmpty)
        }
      } @@ zioTag(errors)
    ),
    suite("optional")(
      testM("fails when given Some error") {
        IO.fail(Some("Error")).optional.run.map { exit =>
          assertTrue(exit.$fail == "Error")
        }
      } @@ zioTag(errors),
      testM("succeeds with None given None error") {
        IO.fail(None).optional.map { res =>
          assertTrue(res.isEmpty)
        }
      } @@ zioTag(errors),
      testM("succeeds with Some given a value") {
        IO.succeed(1).optional.map { res =>
          assertTrue(res.$some == 1)
        }
      }
    ),
    suite("orElse")(
      testM("does not recover from defects") {
        val ex               = new Exception("Died")
        val fiberId          = Fiber.Id(0L, 123L)
        implicit val canFail = CanFail
        for {
          plain <- (ZIO.die(ex) <> IO.unit).run
          both  <- (ZIO.halt(Cause.Both(interrupt(fiberId), die(ex))) <> IO.unit).run
          thn   <- (ZIO.halt(Cause.Then(interrupt(fiberId), die(ex))) <> IO.unit).run
          fail  <- (ZIO.fail(ex) <> IO.unit).run
        } yield assertTrue(plain.$die == ex, both.$die == ex, thn.$die == ex, fail.$success == ())
      },
      testM("left failed and right died with kept cause") {
        val z1                = Task.fail(new Throwable("1"))
        val z2: Task[Nothing] = Task.die(new Throwable("2"))
        z1.orElse(z2)
          .catchAllCause {
            case Traced(Die(e: Throwable), _) => Task(e.getMessage == "2")
            case _                            => Task(false)
          }
          .map { res =>
            assertTrue(res)
          }
      },
      testM("left failed and right failed with kept cause") {
        val z1                = Task.fail(new Throwable("1"))
        val z2: Task[Nothing] = Task.fail(new Throwable("2"))
        z1.orElse(z2)
          .catchAllCause {
            case Traced(Fail(e: Throwable), _) => Task(e.getMessage == "2")
            case _                             => Task(false)
          }
          .map { res =>
            assertTrue(res)
          }
      },
      testM("is associative") {
        val smallInts = Gen.int(0, 100)
        val causes    = Gen.causes(smallInts, Gen.throwable)
        val successes = Gen.successes(smallInts)
        val exits     = Gen.either(causes, successes).map(_.fold(Exit.halt, Exit.succeed))
        checkM(exits, exits, exits) { (exit1, exit2, exit3) =>
          val zio1  = ZIO.done(exit1)
          val zio2  = ZIO.done(exit2)
          val zio3  = ZIO.done(exit3)
          val left  = (zio1 orElse zio2) orElse zio3
          val right = zio1 orElse (zio2 orElse zio3)
          for {
            left  <- left.run
            right <- right.run
          } yield assertTrue(left == right)
        }
      }
    ) @@ zioTag(errors),
    suite("orElseFail")(
      testM("executes this effect and returns its value if it succeeds") {
        implicit val canFail = CanFail
        ZIO.succeed(true).orElseFail(false).map { res =>
          assertTrue(res)
        }
      },
      testM("otherwise fails with the specified error") {
        ZIO.fail(false).orElseFail(true).flip.map { res =>
          assertTrue(res)
        }
      } @@ zioTag(errors)
    ),
    suite("orElseOptional")(
      testM("produces the value of this effect if it succeeds") {
        ZIO.succeed("succeed").orElseOptional(ZIO.succeed("orElse")).map { res =>
          assertTrue(res == "succeed")
        }
      },
      testM("produces the value of this effect if it fails with some error") {
        ZIO.fail(Some("fail")).orElseOptional(ZIO.succeed("orElse")).run.map { exit =>
          assertTrue(exit.$fail.$some == "fail")
        }
      },
      testM("produces the value of the specified effect if it fails with none") {
        ZIO.fail(None).orElseOptional(ZIO.succeed("orElse")).map { res =>
          assertTrue(res == "orElse")
        }
      }
    ),
    suite("orElseSucceed")(
      testM("executes this effect and returns its value if it succeeds") {
        implicit val canFail = CanFail
        ZIO.succeed(true).orElseSucceed(false).map { res =>
          assertTrue(res)
        }
      },
      testM("otherwise succeeds with the specified value") {
        ZIO.fail(false).orElseSucceed(true).map { res =>
          assertTrue(res)
        }
      } @@ zioTag(errors)
    ),
    suite("parallelErrors")(
      testM("oneFailure") {
        for {
          f1     <- IO.fail("error1").fork
          f2     <- IO.succeed("success1").fork
          errors <- f1.zip(f2).join.parallelErrors[String].flip
        } yield assertTrue(errors == List("error1"))
      },
      testM("allFailures") {
        for {
          f1     <- IO.fail("error1").fork
          f2     <- IO.fail("error2").fork
          errors <- f1.zip(f2).join.parallelErrors[String].flip
        } yield assertTrue(
          errors == List("error1", "error2") ||
            errors == List("error1") ||
            errors == List("error2")
        )
      } @@ nonFlaky
    ) @@ zioTag(errors),
    suite("partition")(
      testM("collects only successes") {
        implicit val canFail = CanFail
        val in               = List.range(0, 10)
        for {
          res <- ZIO.partition(in)(a => ZIO.succeed(a))
        } yield assertTrue(res._1.isEmpty && res._2 == in)
      },
      testM("collects only failures") {
        val in = List.fill(10)(0)
        for {
          res <- ZIO.partition(in)(a => ZIO.fail(a))
        } yield assertTrue(res._1 == in && res._2.isEmpty)
      } @@ zioTag(errors),
      testM("collects failures and successes") {
        val in = List.range(0, 10)
        for {
          res <- ZIO.partition(in)(a => if (a % 2 == 0) ZIO.fail(a) else ZIO.succeed(a))
        } yield assertTrue(res._1 == List(0, 2, 4, 6, 8) && res._2 == List(1, 3, 5, 7, 9))
      } @@ zioTag(errors),
      testM("evaluates effects in correct order") {
        implicit val canFail = CanFail
        val as               = List(2, 4, 6, 3, 5, 6)
        for {
          ref     <- Ref.make(List.empty[Int])
          _       <- ZIO.partition(as)(a => ref.update(a :: _))
          effects <- ref.get.map(_.reverse)
        } yield assertTrue(effects == List(2, 4, 6, 3, 5, 6))
      }
    ),
    suite("partitionPar")(
      testM("collects a lot of successes") {
        implicit val canFail = CanFail
        val in               = List.range(0, 1000)
        for {
          res <- ZIO.partitionPar(in)(a => ZIO.succeed(a))
        } yield assertTrue(res._1.isEmpty && res._2 == in)
      },
      testM("collects failures") {
        val in = List.fill(10)(0)
        for {
          res <- ZIO.partitionPar(in)(a => ZIO.fail(a))
        } yield assertTrue(res._1 == in && res._2.isEmpty)
      } @@ zioTag(errors),
      testM("collects failures and successes") {
        val in = List.range(0, 10)
        for {
          res <- ZIO.partitionPar(in)(a => if (a % 2 == 0) ZIO.fail(a) else ZIO.succeed(a))
        } yield assertTrue(res._1 == List(0, 2, 4, 6, 8) && res._2 == List(1, 3, 5, 7, 9))
      } @@ zioTag(errors)
    ),
    suite("partitionParN")(
      testM("collects a lot of successes") {
        implicit val canFail = CanFail
        val in               = List.range(0, 1000)
        for {
          res <- ZIO.partitionParN(3)(in)(a => ZIO.succeed(a))
        } yield assertTrue(res._1.isEmpty && res._2 == in)
      },
      testM("collects failures") {
        val in = List.fill(10)(0)
        for {
          res <- ZIO.partitionParN(3)(in)(a => ZIO.fail(a))
        } yield assertTrue(res._1 == in && res._2.isEmpty)
      } @@ zioTag(errors),
      testM("collects failures and successes") {
        val in = List.range(0, 10)
        for {
          res <- ZIO.partitionParN(3)(in)(a => if (a % 2 == 0) ZIO.fail(a) else ZIO.succeed(a))
        } yield assertTrue(res._1 == List(0, 2, 4, 6, 8) && res._2 == List(1, 3, 5, 7, 9))
      } @@ zioTag(errors)
    ),
    suite("provideCustomLayer")(
      testM("provides the part of the environment that is not part of the `ZEnv`") {
        val loggingLayer: ZLayer[Any, Nothing, Logging] = Logging.live
        val zio: ZIO[ZEnv with Logging, Nothing, Unit]  = ZIO.unit
        zio.provideCustomLayer(loggingLayer).as(assertCompletes)
      }
    ),
    suite("provideSomeLayer")(
      testM("can split environment into two parts") {
        val clockLayer: ZLayer[Any, Nothing, Clock]    = Clock.live
        val zio: ZIO[Clock with Random, Nothing, Unit] = ZIO.unit
        zio.provideSomeLayer[Random](clockLayer).as(assertCompletes)
      }
    ),
    suite("raceAll")(
      testM("returns first success") {
        ZIO.fail("Fail").raceAll(List(IO.succeed(24))).map { res =>
          assertTrue(res == 24)
        }
      },
      testM("returns last failure") {
        Live.live(ZIO.sleep(100.millis) *> ZIO.fail(24)).raceAll(List(ZIO.fail(25))).flip.map { res =>
          assertTrue(res == 24)
        }
      } @@ flaky @@ zioTag(errors),
      testM("returns success when it happens after failure") {
        ZIO.fail(42).raceAll(List(IO.succeed(24) <* Live.live(ZIO.sleep(100.millis)))).map { res =>
          assertTrue(res == 24)
        }
      } @@ zioTag(errors)
    ),
    suite("reduceAllPar")(
      testM("return zero element on empty input") {
        val zeroElement = 42
        val nonZero     = 43
        UIO.reduceAllPar(UIO.succeed(zeroElement), Nil)((_, _) => nonZero).map { res =>
          assertTrue(res == zeroElement)
        }
      },
      testM("reduce list using function") {
        val zeroElement  = UIO.succeed(1)
        val otherEffects = List(3, 5, 7).map(UIO.succeed(_))
        UIO.reduceAllPar(zeroElement, otherEffects)(_ + _).map { res =>
          assertTrue(res == 1 + 3 + 5 + 7)
        }
      },
      testM("return error if zero is an error") {
        val zeroElement  = ZIO.fail(1)
        val otherEffects = List(UIO.unit, UIO.unit)
        ZIO.reduceAllPar(zeroElement, otherEffects)((_, _) => ()).run.map { exit =>
          assertTrue(exit.$fail == 1)
        }
      } @@ zioTag(errors),
      testM("return error if it exists in list") {
        val zeroElement = UIO.unit
        val effects     = List(UIO.unit, ZIO.fail(1))
        ZIO.reduceAllPar(zeroElement, effects)((_, _) => ()).run.map { exit =>
          assertTrue(exit.$fail == 1)
        }
      } @@ zioTag(errors)
    ),
    suite("replicate")(
      testM("zero") {
        val lst: Iterable[UIO[Int]] = ZIO.replicate(0)(ZIO.succeed(12))
        ZIO.collectAll(lst).map { res =>
          assertTrue(res == List.empty)
        }
      },
      testM("negative") {
        val anotherList: Iterable[UIO[Int]] = ZIO.replicate(-2)(ZIO.succeed(12))
        ZIO.collectAll(anotherList).map { res =>
          assertTrue(res == List.empty)
        }
      },
      testM("positive") {
        val lst: Iterable[UIO[Int]] = ZIO.replicate(2)(ZIO.succeed(12))
        ZIO.collectAll(lst).map { res =>
          assertTrue(res == List(12, 12))
        }
      }
    ),
    suite("retryUntil")(
      testM("retryUntil retries until condition is true") {
        for {
          in     <- Ref.make(10)
          out    <- Ref.make(0)
          _      <- (in.updateAndGet(_ - 1) <* out.update(_ + 1)).flipWith(_.retryUntil(_ == 0))
          result <- out.get
        } yield assertTrue(result == 10)
      },
      testM("retryUntil runs at least once") {
        for {
          ref    <- Ref.make(0)
          _      <- ref.update(_ + 1).flipWith(_.retryUntil(_ => true))
          result <- ref.get
        } yield assertTrue(result == 1)
      }
    ),
    suite("retryUntilEquals")(
      testM("retryUntilEquals retries until error equals predicate") {
        for {
          q      <- Queue.unbounded[Int]
          _      <- q.offerAll(List(1, 2, 3, 4, 5, 6))
          acc    <- Ref.make(0)
          _      <- (q.take <* acc.update(_ + 1)).flipWith(_.retryUntilEquals(5))
          result <- acc.get
        } yield assertTrue(result == 5)
      }
    ),
    suite("retryUntilM")(
      testM("retryUntilM retries until condition is true") {
        for {
          in     <- Ref.make(10)
          out    <- Ref.make(0)
          _      <- (in.updateAndGet(_ - 1) <* out.update(_ + 1)).flipWith(_.retryUntilM(v => UIO.succeed(v == 0)))
          result <- out.get
        } yield assertTrue(result == 10)
      },
      testM("retryUntilM runs at least once") {
        for {
          ref    <- Ref.make(0)
          _      <- ref.update(_ + 1).flipWith(_.retryUntilM(_ => UIO.succeed(true)))
          result <- ref.get
        } yield assertTrue(result == 1)
      }
    ),
    suite("retryWhile")(
      testM("retryWhile retries while condition is true") {
        for {
          in     <- Ref.make(10)
          out    <- Ref.make(0)
          _      <- (in.updateAndGet(_ - 1) <* out.update(_ + 1)).flipWith(_.retryWhile(_ >= 0))
          result <- out.get
        } yield assertTrue(result == 11)
      },
      testM("retryWhile runs at least once") {
        for {
          ref    <- Ref.make(0)
          _      <- ref.update(_ + 1).flipWith(_.retryWhile(_ => false))
          result <- ref.get
        } yield assertTrue(result == 1)
      }
    ),
    suite("retryWhileEquals")(
      testM("retryWhileEquals retries while error equals predicate") {
        for {
          q      <- Queue.unbounded[Int]
          _      <- q.offerAll(List(0, 0, 0, 0, 1, 2))
          acc    <- Ref.make(0)
          _      <- (q.take <* acc.update(_ + 1)).flipWith(_.retryWhileEquals(0))
          result <- acc.get
        } yield assertTrue(result == 5)
      }
    ),
    suite("retryWhileM")(
      testM("retryWhileM retries while condition is true") {
        for {
          in     <- Ref.make(10)
          out    <- Ref.make(0)
          _      <- (in.updateAndGet(_ - 1) <* out.update(_ + 1)).flipWith(_.retryWhileM(v => UIO.succeed(v >= 0)))
          result <- out.get
        } yield assertTrue(result == 11)
      },
      testM("retryWhileM runs at least once") {
        for {
          ref    <- Ref.make(0)
          _      <- ref.update(_ + 1).flipWith(_.retryWhileM(_ => UIO.succeed(false)))
          result <- ref.get
        } yield assertTrue(result == 1)
      }
    ),
    suite("right")(
      testM("on Right value") {
        ZIO.succeed(Right("Right")).right.map { res =>
          assertTrue(res == "Right")
        }
      },
      testM("on Left value") {
        ZIO.succeed(Left("Left")).right.either.map { res =>
          assertTrue(res.$left.isEmpty)
        }
      },
      testM("on failure") {
        ZIO.fail("Fail").right.either.map { res =>
          assertTrue(res.$left.$some == "Fail")
        }
      } @@ zioTag(errors)
    ),
    suite("refineToOrDie")(
      testM("does not compile when refined type is not subtype of error type") {
        val result = typeCheck {
          """
          ZIO
            .fail(new RuntimeException("BOO!"))
            .refineToOrDie[Error]
            """
        }
        val expected =
          "type arguments [Error] do not conform to method refineToOrDie's type parameter bounds [E1 <: RuntimeException]"
        result.map { res =>
          assertTrue(res.$left == expected)
        }
      } @@ scala2Only
    ) @@ zioTag(errors),
    suite("rightOrFail")(
      testM("on Right value") {
        UIO(Right(42)).rightOrFail(ExampleError).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("on Left value") {
        UIO(Left(1)).rightOrFail(ExampleError).flip.map { res =>
          assertTrue(res == ExampleError)
        }
      } @@ zioTag(errors)
    ),
    suite("rightOrFailWith")(
      testM("on Right value") {
        Task(Right(42)).rightOrFailWith((x: Throwable) => x).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("on Left value") {
        Task(Left(ExampleError)).rightOrFailWith((x: Throwable) => x).flip.map { res =>
          assertTrue(res == ExampleError)
        }
      } @@ zioTag(errors)
    ),
    suite("rightOrFailException")(
      testM("on Right value") {
        ZIO.succeed(Right(42)).rightOrFailException.map { res =>
          assertTrue(res == 42)
        }
      },
      testM("on Left value") {
        ZIO.succeed(Left(2)).rightOrFailException.run.map { exit =>
          assertTrue(exit.$fail.$is[NoSuchElementException])
        }
      } @@ zioTag(errors)
    ),
    suite("some")(
      testM("extracts the value from Some") {
        Task(Some(1)).some.map { res =>
          assertTrue(res == 1)
        }
      },
      testM("make a task from a defined option") {
        Task.getOrFail(Some(1)).map { res =>
          assertTrue(res == 1)
        }
      },
      testM("make a task from an empty option") {
        Task.getOrFail(None).run.map { exit =>
          assertTrue(exit.$fail.$is[NoSuchElementException])
        }
      } @@ zioTag(errors),
      testM("fails on None") {
        Task(None).some.run.map { exit =>
          assertTrue(exit.$fail.isEmpty)
        }
      } @@ zioTag(errors),
      testM("fails when given an exception") {
        val ex = new RuntimeException("Failed Task")
        Task.fail(ex).some.run.map { exit =>
          assertTrue(exit.$fail.$some == ex)
        }
      } @@ zioTag(errors)
    ),
    suite("someOrFailException")(
      testM("extracts the optional value") {
        ZIO.some(42).someOrFailException.map { res =>
          assertTrue(res == 42)
        }
      },
      testM("fails when given a None") {
        ZIO.succeed(Option.empty[Int]).someOrFailException.run.map { exit =>
          assertTrue(exit.$fail.$is[NoSuchElementException])
        }
      } @@ zioTag(errors),
      suite("without another error type")(
        testM("succeed something") {
          ZIO.succeed(Option(3)).someOrFailException.map { res =>
            assertTrue(res == 3)
          }
        },
        testM("succeed nothing") {
          ZIO.succeed(None: Option[Int]).someOrFailException.run.map { exit =>
            assertTrue(exit.$fail.$is[NoSuchElementException])
          }
        } @@ zioTag(errors)
      ),
      suite("with throwable as base error type")(
        testM("return something") {
          Task(Option(3)).someOrFailException.map { res =>
            assertTrue(res == 3)
          }
        }
      ),
      suite("with exception as base error type")(
        testM("return something") {
          (ZIO.succeed(Option(3)): IO[Exception, Option[Int]]).someOrFailException.map { res =>
            assertTrue(res == 3)
          }
        }
      )
    ),
    suite("reject")(
      testM("returns failure ignoring value") {
        for {
          goodCase <-
            exactlyOnce(0)(_.reject { case v if v != 0 => "Partial failed!" }).sandbox.either
          badCase <-
            exactlyOnce(1)(_.reject { case v if v != 0 => "Partial failed!" }).sandbox.either
              .map(_.left.flatMap(_.failureOrCause))
        } yield assertTrue( //
          goodCase.$right == 0,
          badCase.$left == "Partial failed!"
        )
      }
    ) @@ zioTag(errors),
    suite("rejectM")(
      testM("Check `rejectM` returns failure ignoring value") {
        for {
          goodCase <-
            exactlyOnce(0)(
              _.rejectM[Any, String] { case v if v != 0 => ZIO.succeed("Partial failed!") }
            ).sandbox.either
          partialBadCase <-
            exactlyOnce(1)(_.rejectM { case v if v != 0 => ZIO.fail("Partial failed!") }).sandbox.either
              .map(_.left.flatMap(_.failureOrCause))
          badCase <-
            exactlyOnce(1)(_.rejectM { case v if v != 0 => ZIO.fail("Partial failed!") }).sandbox.either
              .map(_.left.flatMap(_.failureOrCause))

        } yield assertTrue( //
          goodCase.$right == 0,
          partialBadCase.$left == "Partial failed!",
          badCase.$left == "Partial failed!"
        )
      }
    ),
    suite("RTS synchronous correctness")(
      testM("widen Nothing") {
        val op1 = IO.effectTotal[String]("1")
        val op2 = IO.effectTotal[String]("2")

        op1.zipWith(op2)(_ + _).map { res =>
          assertTrue(res == "12")
        }
      },
      testM("succeed must be lazy") {
        val io =
          try {
            IO.succeed(throw ExampleError)
            IO.succeed(true)
          } catch {
            case _: Throwable => IO.succeed(false)
          }

        io.map(res => assertTrue(res))
      },
      testM("effectSuspend must be lazy") {
        val io =
          try {
            IO.effectSuspend(throw ExampleError)
            IO.succeed(false)
          } catch {
            case _: Throwable => IO.succeed(true)
          }

        io.map(res => assertTrue(!res))
      },
      testM("effectSuspendTotal must not catch throwable") {
        ZIO.effectSuspendTotal[Any, Nothing, Any](throw ExampleError).sandbox.either.map { res =>
          assertTrue(res.$left == Cause.die(ExampleError))
        }
      },
      testM("effectSuspend must catch throwable") {
        ZIO.effectSuspend[Any, Nothing](throw ExampleError).either.map { res =>
          assertTrue(res.$left == ExampleError)
        }
      },
      testM("effectSuspendWith must catch throwable") {
        ZIO.effectSuspendWith[Any, Nothing]((_, _) => throw ExampleError).either.map { res =>
          assertTrue(res.$left == ExampleError)
        }
      },
      testM("effectSuspendTotal must be evaluatable") {
        IO.effectSuspendTotal(IO.effectTotal(42)).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("point, bind, map") {
        def fibIo(n: Int): Task[BigInt] =
          if (n <= 1) IO.succeed(n)
          else
            for {
              a <- fibIo(n - 1)
              b <- fibIo(n - 2)
            } yield a + b

        fibIo(10).map { res =>
          assertTrue(res == fib(10))
        }
      },
      testM("effect, bind, map") {
        def fibIo(n: Int): Task[BigInt] =
          if (n <= 1) IO.effect(n)
          else
            for {
              a <- fibIo(n - 1)
              b <- fibIo(n - 2)
            } yield a + b

        fibIo(10).map { res =>
          assertTrue(res == fib(10))
        }
      },
      testM("effect, bind, map, redeem") {
        def fibIo(n: Int): Task[BigInt] =
          if (n <= 1) Task.effect[BigInt](throw ExampleError).catchAll(_ => Task.effect(n))
          else
            for {
              a <- fibIo(n - 1)
              b <- fibIo(n - 2)
            } yield a + b

        fibIo(10).map { res =>
          assertTrue(res == fib(10))
        }
      },
      testM("sync effect") {
        def sumIo(n: Int): Task[Int] =
          if (n <= 0) IO.effectTotal(0)
          else IO.effectTotal(n).flatMap(b => sumIo(n - 1).map(a => a + b))

        sumIo(1000).map { res =>
          assertTrue(res == sum(1000))
        }
      },
      testM("deep effects") {
        def incLeft(n: Int, ref: Ref[Int]): Task[Int] =
          if (n <= 0) ref.get
          else incLeft(n - 1, ref) <* ref.update(_ + 1)

        def incRight(n: Int, ref: Ref[Int]): Task[Int] =
          if (n <= 0) ref.get
          else ref.update(_ + 1) *> incRight(n - 1, ref)

        val l =
          for {
            ref <- Ref.make(0)
            v   <- incLeft(100, ref)
          } yield v == 0

        val r =
          for {
            ref <- Ref.make(0)
            v   <- incRight(1000, ref)
          } yield v == 1000

        l.zipWith(r)(_ && _).map { res =>
          assertTrue(res)
        }
      },
      testM("flip must make error into value") {
        IO.fail(ExampleError).flip.map { res =>
          assertTrue(res == ExampleError)
        }
      } @@ zioTag(errors),
      testM("flip must make value into error") {
        IO.succeed(42).flip.either.map { res =>
          assertTrue(res.$left == 42)
        }
      } @@ zioTag(errors),
      testM("flipping twice returns identical value") {
        IO.succeed(42).flip.flip.map { res =>
          assertTrue(res == 42)
        }
      }
    ),
    suite("RTS failure")(
      testM("error in sync effect") {
        IO.effect[Unit](throw ExampleError).fold[Option[Throwable]](Some(_), _ => None).map { res =>
          assertTrue(res.$some == ExampleError)
        }
      } @@ zioTag(errors),
      testM("attempt . fail") {
        val io1 = TaskExampleError.either
        val io2 = IO.effectSuspendTotal(IO.effectSuspendTotal(TaskExampleError).either)

        io1.zipWith(io2) { case (r1, r2) =>
          assertTrue(r1.$left == ExampleError && r2.$left == ExampleError)
        }
      } @@ zioTag(errors),
      testM("deep attempt sync effect error") {
        deepErrorEffect(100).either.map { res =>
          assertTrue(res.$left == ExampleError)
        }
      } @@ zioTag(errors),
      testM("deep attempt fail error") {
        deepErrorFail(100).either.map { res =>
          assertTrue(res.$left == ExampleError)
        }
      } @@ zioTag(errors),
      testM("attempt . sandbox . terminate") {
        IO.effectTotal[Int](throw ExampleError).sandbox.either.map { res =>
          assertTrue(res.$left == Cause.die(ExampleError))
        }
      } @@ zioTag(errors),
      testM("fold . sandbox . terminate") {
        IO.effectTotal[Int](throw ExampleError).sandbox.fold(Some(_), Function.const(None)).map { res =>
          assertTrue(res.$some == Cause.die(ExampleError))
        }
      } @@ zioTag(errors),
      testM("catch sandbox terminate") {
        IO.effectTotal(throw ExampleError).sandbox.merge.map { res =>
          assertTrue(res == Cause.die(ExampleError))
        }
      } @@ zioTag(errors),
      testM("uncaught fail") {
        TaskExampleError.run.map { exit =>
          assertTrue(exit.$fail == ExampleError)
        }
      } @@ zioTag(errors),
      testM("uncaught sync effect error") {
        IO.effectTotal[Int](throw ExampleError).run.map { exit =>
          assertTrue(exit.$die == ExampleError)
        }
      } @@ zioTag(errors),
      testM("deep uncaught sync effect error") {
        deepErrorEffect(100).run.map { exit =>
          assertTrue(exit.$fail == ExampleError)
        }
      } @@ zioTag(errors),
      testM("catch failing finalizers with fail") {
        val io = IO
          .fail(ExampleError)
          .ensuring(IO.effectTotal(throw InterruptCause1))
          .ensuring(IO.effectTotal(throw InterruptCause2))
          .ensuring(IO.effectTotal(throw InterruptCause3))

        val expectedCause = Cause.fail(ExampleError) ++
          Cause.die(InterruptCause1) ++
          Cause.die(InterruptCause2) ++
          Cause.die(InterruptCause3)

        io.run.map { exit =>
          assertTrue(exit == Exit.halt(expectedCause))
        }
      } @@ zioTag(errors),
      testM("catch failing finalizers with terminate") {
        val io = IO
          .die(ExampleError)
          .ensuring(IO.effectTotal(throw InterruptCause1))
          .ensuring(IO.effectTotal(throw InterruptCause2))
          .ensuring(IO.effectTotal(throw InterruptCause3))

        val expectedCause = Cause.die(ExampleError) ++
          Cause.die(InterruptCause1) ++
          Cause.die(InterruptCause2) ++
          Cause.die(InterruptCause3)

        io.run.map { exit =>
          assertTrue(exit == Exit.halt(expectedCause))
        }
      } @@ zioTag(errors),
      testM("run preserves interruption status") {
        for {
          p    <- Promise.make[Nothing, Unit]
          f    <- (p.succeed(()) *> IO.never).run.fork
          _    <- p.await
          _    <- f.interrupt
          test <- f.await.map(_.interrupted)
        } yield assertTrue(test)
      } @@ zioTag(interruption),
      testM("run swallows inner interruption") {
        for {
          p   <- Promise.make[Nothing, Int]
          _   <- IO.interrupt.run *> p.succeed(42)
          res <- p.await
        } yield assertTrue(res == 42)
      } @@ zioTag(interruption),
      testM("timeout a long computation") {
        Live.live((clock.sleep(5.seconds) *> IO.succeed(true)).timeout(10.millis)).map { res =>
          assertTrue(res.isEmpty)
        }
      },
      testM("timeout repetition of uninterruptible effect") {
        val effect = ZIO.unit.uninterruptible.forever

        Live.live(effect.timeout(1.second)).map { res =>
          assertTrue(res.isEmpty)
        }
      } @@ jvmOnly @@ zioTag(interruption),
      testM("timeout in uninterruptible region") {
        ZIO.unit.timeout(Duration.Infinity).uninterruptible.map { res =>
          assertTrue(res.$some == ())
        }
      } @@ zioTag(interruption),
      testM("catchAllCause") {
        val io =
          for {
            _ <- ZIO.succeed(42)
            f <- ZIO.fail("Uh oh!")
          } yield f

        io.catchAllCause(ZIO.succeed(_)).map { res =>
          assertTrue(res == Cause.fail("Uh oh!"))
        }
      } @@ zioTag(errors),
      testM("exception in fromFuture does not kill fiber") {
        ZIO.fromFuture(_ => throw ExampleError).either.map { res =>
          assertTrue(res.$left == ExampleError)
        }
      } @@ zioTag(errors, future)
    ),
    suite("RTS finalizers")(
      testM("fail ensuring") {
        var finalized = false

        Task.fail(ExampleError).ensuring(IO.effectTotal { finalized = true; () }).run.map { exit =>
          assertTrue(exit.$fail == ExampleError && finalized)
        }
      } @@ zioTag(errors),
      testM("fail on error") {
        @volatile var finalized = false

        val cleanup: Cause[Throwable] => UIO[Unit] =
          _ => IO.effectTotal[Unit] { finalized = true; () }

        Task.fail(ExampleError).onError(cleanup).run.map { exit =>
          assertTrue(exit.$fail == ExampleError && finalized)
        }
      } @@ zioTag(errors),
      testM("finalizer errors not caught") {
        val e2 = new Error("e2")
        val e3 = new Error("e3")

        val io = TaskExampleError.ensuring(IO.die(e2)).ensuring(IO.die(e3))

        val expectedCause: Cause[Throwable] =
          Cause.Then(Cause.fail(ExampleError), Cause.Then(Cause.die(e2), Cause.die(e3)))

        io.sandbox.flip.map { res =>
          assertTrue(res == expectedCause)
        }
      } @@ zioTag(errors),
      testM("finalizer errors reported") {
        @volatile var reported: Exit[Nothing, Int] = null

        IO
          .succeed[Int](42)
          .ensuring(IO.die(ExampleError))
          .fork
          .flatMap(_.await.flatMap[Any, Nothing, Any](e => UIO.effectTotal { reported = e }))
          .as {
            assertTrue(!reported.succeeded)
          }
      } @@ zioTag(errors),
      testM("bracket exit is usage result") {
        IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.succeed[Int](42)).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("error in just acquisition") {
        IO.bracket(TaskExampleError)(_ => IO.unit)(_ => IO.unit).run.map { exit =>
          assertTrue(exit.$fail == ExampleError)
        }
      } @@ zioTag(errors),
      testM("error in just release") {
        IO.bracket(IO.unit)(_ => IO.die(ExampleError))(_ => IO.unit).run.map { exit =>
          assertTrue(exit.$die == ExampleError)
        }
      } @@ zioTag(errors),
      testM("error in just usage") {
        IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.fail(ExampleError)).run.map { exit =>
          assertTrue(exit.$fail == ExampleError)
        }
      } @@ zioTag(errors),
      testM("rethrown caught error in acquisition") {
        IO.absolve(IO.bracket(TaskExampleError)(_ => IO.unit)(_ => IO.unit).either).flip.map { res =>
          assertTrue(res == ExampleError)
        }
      } @@ zioTag(errors),
      testM("rethrown caught error in release") {
        IO.bracket(IO.unit)(_ => IO.die(ExampleError))(_ => IO.unit).run.map { exit =>
          assertTrue(exit.$die == ExampleError)
        }
      } @@ zioTag(errors),
      testM("rethrown caught error in usage") {
        IO.absolve(IO.unit.bracket_(IO.unit)(TaskExampleError).either).run.map { exit =>
          assertTrue(exit.$fail == ExampleError)
        }
      } @@ zioTag(errors),
      testM("test eval of async fail") {
        val io1 = IO.unit.bracket_(AsyncUnit[Nothing])(asyncExampleError[Unit])
        val io2 = AsyncUnit[Throwable].bracket_(IO.unit)(asyncExampleError[Unit])

        for {
          exit1 <- io1.run
          exit2 <- io2.run
          exit3 <- IO.absolve(io1.either).run
          exit4 <- IO.absolve(io2.either).run
        } yield assertTrue(
          exit1.$fail == ExampleError,
          exit2.$fail == ExampleError,
          exit3.$fail == ExampleError,
          exit4.$fail == ExampleError
        )
      },
      testM("bracket regression 1") {
        def makeLogger: Ref[List[String]] => String => UIO[Unit] =
          (ref: Ref[List[String]]) => (line: String) => ref.update(_ ::: List(line))

        val io =
          for {
            ref <- Ref.make[List[String]](Nil)
            log  = makeLogger(ref)
            f <- ZIO
                   .bracket(
                     ZIO.bracket(ZIO.unit)(_ => log("start 1") *> clock.sleep(10.millis) *> log("release 1"))(_ =>
                       ZIO.unit
                     )
                   )(_ => log("start 2") *> clock.sleep(10.millis) *> log("release 2"))(_ => ZIO.unit)
                   .fork
            _ <- (ref.get <* clock.sleep(1.millis)).repeatUntil(_.contains("start 1"))
            _ <- f.interrupt
            _ <- (ref.get <* clock.sleep(1.millis)).repeatUntil(_.contains("release 2"))
            l <- ref.get
          } yield l

        Live.live(io).map { res =>
          // TODO: $sameElements
          assertTrue(res == List("start 1", "release 1", "start 2", "release 2"))
        }
      } @@ zioTag(regression) @@ jvmOnly,
      testM("interrupt waits for finalizer") {
        val io =
          for {
            r  <- Ref.make(false)
            p1 <- Promise.make[Nothing, Unit]
            p2 <- Promise.make[Nothing, Int]
            s <- (p1.succeed(()) *> p2.await)
                   .ensuring(r.set(true) *> clock.sleep(10.millis))
                   .fork
            _    <- p1.await
            _    <- s.interrupt
            test <- r.get
          } yield test

        Live.live(io).map { res =>
          assertTrue(res)
        }
      } @@ zioTag(interruption)
    ),
    suite("RTS synchronous stack safety")(
      testM("deep map of now") {
        deepMapNow(10000).map { res =>
          assertTrue(res == 10000)
        }
      },
      testM("deep map of sync effect") {
        deepMapEffect(10000).map { res =>
          assertTrue(res == 10000)
        }
      },
      testM("deep attempt") {
        (0 until 10000).foldLeft(IO.effect(()))((acc, _) => acc.either.unit).map { res =>
          assertTrue(res == ())
        }
      },
      testM("deep flatMap") {
        def fib(n: Int, a: BigInt = 0, b: BigInt = 1): IO[Error, BigInt] =
          IO.succeed(a + b).flatMap { b2 =>
            if (n > 0)
              fib(n - 1, b, b2)
            else
              IO.succeed(b2)
          }

        val expected = BigInt(
          "113796925398360272257523782552224175572745930353730513145086634176691092536145985470146129334641866902783673042322088625863396052888690096969577173696370562180400527049497109023054114771394568040040412172632376"
        )

        fib(1000).map { res =>
          assertTrue(res == expected)
        }
      },
      testM("deep absolve/attempt is identity") {
        implicit val canFail = CanFail
        (0 until 1000).foldLeft(IO.succeed(42))((acc, _) => IO.absolve(acc.either)).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("deep async absolve/attempt is identity") {
        (0 until 1000)
          .foldLeft(IO.effectAsync[Int, Int](k => k(IO.succeed(42)))) { (acc, _) =>
            IO.absolve(acc.either)
          }
          .map { res =>
            assertTrue(res == 42)
          }
      }
    ),
    suite("RTS asynchronous correctness")(
      testM("simple effectAsync must return") {
        IO.effectAsync[Throwable, Int](k => k(IO.succeed(42))).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("simple effectAsyncM must return") {
        val io = IO.effectAsyncM[Throwable, Int](k => IO.effectTotal(k(IO.succeed(42))))
        io.map { res =>
          assertTrue(res == 42)
        }
      },
      testM("deep effectAsyncM doesn't block threads") {
        def stackIOs(count: Int): URIO[Clock, Int] =
          if (count <= 0) IO.succeed(42)
          else asyncIO(stackIOs(count - 1))

        def asyncIO(cont: URIO[Clock, Int]): URIO[Clock, Int] =
          ZIO.effectAsyncM[Clock, Nothing, Int] { k =>
            clock.sleep(5.millis) *> cont *> IO.effectTotal(k(IO.succeed(42)))
          }

        val procNum = java.lang.Runtime.getRuntime.availableProcessors()

        Live.live(stackIOs(procNum + 1)).map { res =>
          assertTrue(res == 42)
        }
      } @@ jvmOnly,
      testM("interrupt of effectAsyncM register") {
        for {
          release <- Promise.make[Nothing, Unit]
          acquire <- Promise.make[Nothing, Unit]
          fiber <- IO
                     .effectAsyncM[Nothing, Unit] { _ =>
                       // This will never complete because we never call the callback
                       acquire.succeed(()).bracket_(release.succeed(()))(IO.never)
                     }
                     .disconnect
                     .fork
          _ <- acquire.await
          _ <- fiber.interruptFork
          a <- release.await
        } yield assertTrue(a == ())
      } @@ zioTag(interruption) @@ ignore,
      testM("effectAsync should not resume fiber twice after interruption") {
        for {
          step            <- Promise.make[Nothing, Unit]
          unexpectedPlace <- Ref.make(List.empty[Int])
          runtime         <- ZIO.runtime[Live]
          fork <- ZIO
                    .effectAsync[Any, Nothing, Unit] { k =>
                      runtime.unsafeRunAsync_ {
                        step.await *> ZIO.effectTotal(k(unexpectedPlace.update(1 :: _)))
                      }
                    }
                    .ensuring(ZIO.effectAsync[Any, Nothing, Unit] { _ =>
                      runtime.unsafeRunAsync_ {
                        step.succeed(())
                      }
                    //never complete
                    })
                    .ensuring(unexpectedPlace.update(2 :: _))
                    .forkDaemon
          result     <- Live.withLive(fork.interrupt)(_.timeout(5.seconds))
          unexpected <- unexpectedPlace.get
        } yield {
          assertTrue(
            unexpected.isEmpty,
            result.isEmpty
          ) // timeout happens
        }
      } @@ zioTag(interruption) @@ flaky,
      testM("effectAsyncMaybe should not resume fiber twice after synchronous result") {
        for {
          step            <- Promise.make[Nothing, Unit]
          unexpectedPlace <- Ref.make(List.empty[Int])
          runtime         <- ZIO.runtime[Live]
          fork <- ZIO
                    .effectAsyncMaybe[Any, Nothing, Unit] { k =>
                      runtime.unsafeRunAsync_ {
                        step.await *> ZIO.effectTotal(k(unexpectedPlace.update(1 :: _)))
                      }
                      Some(IO.unit)
                    }
                    .flatMap { _ =>
                      ZIO.effectAsync[Any, Nothing, Unit] { _ =>
                        runtime.unsafeRunAsync_ {
                          step.succeed(())
                        }
                      //never complete
                      }
                    }
                    .ensuring(unexpectedPlace.update(2 :: _))
                    .uninterruptible
                    .forkDaemon
          result     <- Live.withLive(fork.interrupt)(_.timeout(5.seconds))
          unexpected <- unexpectedPlace.get
        } yield assertTrue(
          unexpected.isEmpty,
          result.isEmpty
        ) // timeout happens
      } @@ flaky,
      testM("sleep 0 mLive.live(clock.sleep(1.nanos))ust return") {
        Live.live(clock.sleep(1.nanos)).map { res =>
          assertTrue(res == ())
        }
      },
      testM("shallow bind of async chain") {
        (0 until 10)
          .foldLeft[Task[Int]](IO.succeed[Int](0)) { (acc, _) =>
            acc.flatMap(n => IO.effectAsync[Throwable, Int](_(IO.succeed(n + 1))))
          }
          .map { res =>
            assertTrue(res == 10)
          }
      },
      testM("effectAsyncM can fail before registering") {
        ZIO
          .effectAsyncM[Any, String, Nothing](_ => ZIO.fail("Ouch"))
          .flip
          .map { res =>
            assertTrue(res == "Ouch")
          }
      } @@ zioTag(errors),
      testM("effectAsyncM can defect before registering") {
        ZIO
          .effectAsyncM[Any, String, Unit](_ => ZIO.effectTotal(throw new Error("Ouch")))
          .run
          .map { res =>
            assertTrue(res.$die.getMessage == "Ouch")
          }
      } @@ zioTag(errors)
    ),
    suite("RTS concurrency correctness")(
      testM("shallow fork/join identity") {
        for {
          f <- IO.succeed(42).fork
          r <- f.join
        } yield assertTrue(r == 42)
      },
      testM("deep fork/join identity") {
        val n = 20
        concurrentFib(n).map { res =>
          assertTrue(res == fib(n))
        }
      } @@ jvmOnly,
      testM("effectAsyncM creation is interruptible") {
        for {
          release <- Promise.make[Nothing, Int]
          acquire <- Promise.make[Nothing, Unit]
          task = IO.effectAsyncM[Nothing, Unit] { _ =>
                   // This will never complete because the callback is never invoked
                   IO.bracket(acquire.succeed(()))(_ => release.succeed(42).unit)(_ => IO.never)
                 }
          fiber <- task.fork
          _     <- acquire.await
          _     <- fiber.interrupt
          a     <- release.await
        } yield assertTrue(a == 42)
      } @@ zioTag(interruption) @@ ignore,
      testM("asyncInterrupt runs cancel token on interrupt") {
        for {
          release <- Promise.make[Nothing, Int]
          latch    = scala.concurrent.Promise[Unit]()
          async    = IO.effectAsyncInterrupt[Nothing, Nothing] { _ => latch.success(()); Left(release.succeed(42).unit) }
          fiber   <- async.fork
          _ <- IO.effectAsync[Throwable, Unit] { k =>
                 latch.future.onComplete {
                   case Success(a) => k(IO.succeed(a))
                   case Failure(t) => k(IO.fail(t))
                 }(scala.concurrent.ExecutionContext.global)
               }
          _      <- fiber.interrupt
          result <- release.await
        } yield assertTrue(result == 42)
      } @@ zioTag(interruption),
      testM("daemon fiber is unsupervised") {
        def child(ref: Ref[Boolean]) = withLatch(release => (release *> UIO.never).ensuring(ref.set(true)))

        for {
          ref   <- Ref.make[Boolean](false)
          fiber <- child(ref).forkDaemon.fork
          _     <- fiber.join
          b     <- ref.get
        } yield assertTrue(!b)
      } @@ zioTag(supervision),
      testM("daemon fiber race interruption") {
        def plus1(latch: Promise[Nothing, Unit], finalizer: UIO[Any]) =
          (latch.succeed(()) *> ZIO.sleep(1.hour)).onInterrupt(finalizer)

        for {
          interruptionRef <- Ref.make(0)
          latch1Start     <- Promise.make[Nothing, Unit]
          latch2Start     <- Promise.make[Nothing, Unit]
          inc              = interruptionRef.update(_ + 1)
          left             = plus1(latch1Start, inc)
          right            = plus1(latch2Start, inc)
          fiber           <- (left race right).fork
          _               <- latch1Start.await *> latch2Start.await *> fiber.interrupt
          interrupted     <- interruptionRef.get
        } yield assertTrue(interrupted == 2)
      } @@ zioTag(interruption) @@ flaky,
      testM("race in daemon is executed") {
        for {
          latch1 <- Promise.make[Nothing, Unit]
          latch2 <- Promise.make[Nothing, Unit]
          p1     <- Promise.make[Nothing, Unit]
          p2     <- Promise.make[Nothing, Unit]
          loser1  = ZIO.bracket(latch1.succeed(()))(_ => p1.succeed(()))(_ => ZIO.infinity)
          loser2  = ZIO.bracket(latch2.succeed(()))(_ => p2.succeed(()))(_ => ZIO.infinity)
          fiber  <- (loser1 race loser2).forkDaemon
          _      <- latch1.await
          _      <- latch2.await
          _      <- fiber.interrupt
          res1   <- p1.await
          res2   <- p2.await
        } yield assertTrue(res1 == () && res2 == ())
      },
      testM("supervise fibers") {
        def makeChild(n: Int): URIO[Clock, Fiber[Nothing, Unit]] =
          (clock.sleep(20.millis * n.toDouble) *> ZIO.infinity).fork

        val io =
          for {
            counter <- Ref.make(0)
            _ <- (makeChild(1) *> makeChild(2)).ensuringChildren { fs =>
                   fs.foldLeft(IO.unit)((acc, f) => acc *> f.interrupt *> counter.update(_ + 1))
                 }
            value <- counter.get
          } yield value
        Live.live(io).map { res =>
          assertTrue(res == 2)
        }
      } @@ zioTag(supervision) @@ forked @@ flaky, // Due to weak supervision, this test is expected to fail sometimes
      testM("race of fail with success") {
        IO.fail(42).race(IO.succeed(24)).either.map { res =>
          assertTrue(res.$right == 24)
        }
      },
      testM("race of terminate with success") {
        IO.die(new Throwable {}).race(IO.succeed(24)).map { res =>
          assertTrue(res == 24)
        }
      },
      testM("race of fail with fail") {
        IO.fail(42).race(IO.fail(42)).either.map { res =>
          assertTrue(res.$left == 42)
        }
      },
      testM("race of value & never") {
        IO.effectTotal(42).race(IO.never).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("race in uninterruptible region") {
        ZIO.unit.race(ZIO.infinity).uninterruptible.map { res =>
          assertTrue(res == ())
        }
      },
      testM("race of two forks does not interrupt winner") {
        for {
          ref    <- Ref.make(0)
          fibers <- Ref.make(Set.empty[Fiber[Any, Any]])
          latch  <- Promise.make[Nothing, Unit]
          scope  <- ZIO.descriptor.map(_.scope)
          effect = ZIO.uninterruptibleMask { restore =>
                     restore(latch.await.onInterrupt(ref.update(_ + 1))).forkIn(scope).tap(f => fibers.update(_ + f))
                   }
          awaitAll = fibers.get.flatMap(Fiber.awaitAll(_))
          _       <- effect race effect
          value2  <- latch.succeed(()) *> awaitAll *> ref.get
        } yield assertTrue(value2 <= 1)
      },
      testM("firstSuccessOf of values") {
        IO.firstSuccessOf(IO.fail(0), List(IO.succeed(100))).either.map { res =>
          assertTrue(res.$right == 100)
        }
      },
      testM("firstSuccessOf of failures") {
        Live.live(ZIO.firstSuccessOf(IO.fail(0).delay(10.millis), List(IO.fail(101))).either).map { res =>
          assertTrue(res.$left == 101)
        }
      },
      testM("firstSuccessOF of failures & 1 success") {
        Live.live(ZIO.firstSuccessOf(IO.fail(0), List(IO.succeed(102).delay(1.millis))).either).map { res =>
          assertTrue(res.$right == 102)
        }
      },
      testM("raceFirst interrupts loser on success") {
        for {
          s      <- Promise.make[Nothing, Unit]
          effect <- Promise.make[Nothing, Int]
          winner  = s.await *> IO.fromEither(Right(()))
          loser   = ZIO.bracket(s.succeed(()))(_ => effect.succeed(42))(_ => ZIO.infinity)
          race    = winner raceFirst loser
          _      <- race
          b      <- effect.await
        } yield assertTrue(b == 42)
      } @@ zioTag(interruption),
      testM("raceFirst interrupts loser on failure") {
        for {
          s      <- Promise.make[Nothing, Unit]
          effect <- Promise.make[Nothing, Int]
          winner  = s.await *> IO.fromEither(Left(new Exception))
          loser   = ZIO.bracket(s.succeed(()))(_ => effect.succeed(42))(_ => ZIO.infinity)
          race    = winner raceFirst loser
          _      <- race.either
          b      <- effect.await
        } yield assertTrue(b == 42)
      } @@ zioTag(interruption),
      testM("mergeAll") {
        IO.mergeAll(List("a", "aa", "aaa", "aaaa").map(IO.succeed[String](_)))(0)((b, a) => b + a.length).map { res =>
          assertTrue(res == 10)
        }
      },
      testM("mergeAllkEmpty") {
        IO.mergeAll(List.empty[UIO[Int]])(0)(_ + _).map { res =>
          assertTrue(res == 0)
        }
      },
      testM("reduceAll") {
        IO.reduceAll(IO.effectTotal(1), List(2, 3, 4).map(IO.succeed[Int](_)))(_ + _).map { res =>
          assertTrue(res == 10)
        }
      },
      testM("reduceAll Empty List") {
        IO.reduceAll(IO.effectTotal(1), Seq.empty)(_ + _).map { res =>
          assertTrue(res == 1)
        }
      },
      testM("timeout of failure") {
        Live.live(IO.fail("Uh oh").timeout(1.hour)).run.map { res =>
          assertTrue(res.$fail == "Uh oh")
        }
      },
      testM("timeout of terminate") {
        Live.live(IO.die(ExampleError).timeout(1.hour)).run.map { exit =>
          assertTrue(exit.$die == ExampleError)
        }
      }
    ),
    suite("RTS option tests")(
      testM("lifting a value to an option") {
        ZIO.some(42).map { res =>
          assertTrue(res.$some == 42)
        }
      },
      testM("using the none value") {
        ZIO.none.map { res =>
          assertTrue(res.isEmpty)
        }
      }
    ),
    suite("RTS either helper tests")(
      testM("lifting a value into right") {
        ZIO.right(42).map { res =>
          assertTrue(res.$right == 42)
        }
      },
      testM("lifting a value into left") {
        ZIO.left(42).map { res =>
          assertTrue(res.$left == 42)
        }
      }
    ),
    suite("RTS interruption")(
      testM("sync forever is interruptible") {
        for {
          f <- IO.effectTotal[Int](1).forever.fork
          _ <- f.interrupt
        } yield assertCompletes
      },
      testM("interrupt of never") {
        for {
          fiber <- IO.never.fork
          _     <- fiber.interrupt
        } yield assertCompletes
      },
      testM("effectAsyncM is interruptible") {
        for {
          fiber <- IO.effectAsyncM[Nothing, Nothing](_ => IO.never).fork
          _     <- fiber.interrupt
        } yield assertCompletes
      },
      testM("effectAsync is interruptible") {
        for {
          fiber <- IO.effectAsync[Nothing, Nothing](_ => ()).fork
          _     <- fiber.interrupt
        } yield assertCompletes
      },
      testM("bracket is uninterruptible") {
        val io =
          for {
            promise <- Promise.make[Nothing, Unit]
            fiber   <- (promise.succeed(()) <* IO.never).bracket(_ => IO.unit)(_ => IO.unit).forkDaemon
            res     <- promise.await *> fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
          } yield res
        Live.live(io).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("bracketExit is uninterruptible") {
        val io =
          for {
            promise <- Promise.make[Nothing, Unit]
            fiber <- IO
                       .bracketExit(promise.succeed(()) *> IO.never *> IO.succeed(1))((_, _: Exit[Any, Any]) =>
                         IO.unit
                       )(_ => IO.unit: IO[Nothing, Unit])
                       .forkDaemon
            res <- promise.await *> fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
          } yield res
        Live.live(io).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("bracket use is interruptible") {
        for {
          fiber <- IO.unit.bracket(_ => IO.unit)(_ => IO.never).fork
          res   <- fiber.interrupt
        } yield assertTrue(res.$interrupt.$is[Fiber.Id])
      },
      testM("bracketExit use is interruptible") {
        for {
          fiber <- IO.bracketExit(IO.unit)((_, _: Exit[Any, Any]) => IO.unit)(_ => IO.never).fork
          res   <- fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
        } yield assertTrue(res == 0)
      },
      testM("bracket release called on interrupt") {
        val io =
          for {
            p1    <- Promise.make[Nothing, Unit]
            p2    <- Promise.make[Nothing, Unit]
            fiber <- IO.bracket(IO.unit)(_ => p2.succeed(()) *> IO.unit)(_ => p1.succeed(()) *> IO.never).fork
            _     <- p1.await
            _     <- fiber.interrupt
            _     <- p2.await
          } yield ()

        io.timeoutTo(42)(_ => 0)(1.second).map { res =>
          assertTrue(res == 0)
        }
      },
      testM("bracketExit release called on interrupt") {
        for {
          done <- Promise.make[Nothing, Unit]
          fiber <- withLatch { release =>
                     IO.bracketExit(IO.unit)((_, _: Exit[Any, Any]) => done.succeed(()))(_ => release *> IO.never).fork
                   }

          _ <- fiber.interrupt
          r <- done.await.timeoutTo(42)(_ => 0)(60.second)
        } yield assertTrue(r == 0)
      },
      testM("bracket acquire returns immediately on interrupt") {
        for {
          p1 <- Promise.make[Nothing, Unit]
          p2 <- Promise.make[Nothing, Int]
          p3 <- Promise.make[Nothing, Unit]
          s <- (p1.succeed(()) *> p2.await)
                 .bracket(_ => p3.await)(_ => IO.unit)
                 .disconnect
                 .fork
          _   <- p1.await
          res <- s.interrupt
          _   <- p3.succeed(())
        } yield assertTrue(res.$interrupt.$is[Fiber.Id])
      },
      testM("bracketExit disconnect acquire returns immediately on interrupt") {
        for {
          p1 <- Promise.make[Nothing, Unit]
          p2 <- Promise.make[Nothing, Unit]
          p3 <- Promise.make[Nothing, Unit]
          s <- IO
                 .bracketExit(p1.succeed(()) *> p2.await)((_, _: Exit[Any, Any]) => p3.await)(_ =>
                   IO.unit: IO[Nothing, Unit]
                 )
                 .disconnect
                 .fork
          _   <- p1.await
          res <- s.interrupt
          _   <- p3.succeed(())
        } yield assertTrue(res.$interrupt.$is[Fiber.Id])
      },
      testM("bracket disconnect use is interruptible") {
        for {
          fiber <- IO.unit.bracket(_ => IO.unit)(_ => IO.never).disconnect.fork
          res   <- fiber.interrupt
        } yield assertTrue(res.$interrupt.$is[Fiber.Id])
      },
      testM("bracketExit disconnect use is interruptible") {
        for {
          fiber <- IO.bracketExit(IO.unit)((_, _: Exit[Any, Any]) => IO.unit)(_ => IO.never).disconnect.fork
          res   <- Live.live(fiber.interrupt.timeoutTo(42)(_ => 0)(1.second))
        } yield assertTrue(res == 0)
      },
      testM("bracket disconnect release called on interrupt in separate fiber") {
        val io =
          for {
            p1 <- Promise.make[Nothing, Unit]
            p2 <- Promise.make[Nothing, Unit]
            fiber <- IO
                       .bracket(IO.unit)(_ => p2.succeed(()) *> IO.unit)(_ => p1.succeed(()) *> IO.never)
                       .disconnect
                       .fork
            _ <- p1.await
            _ <- fiber.interrupt
            _ <- p2.await
          } yield ()

        Live.live(io).timeoutTo(false)(_ => true)(10.seconds).map { res =>
          assertTrue(res)
        }
      },
      testM("bracketExit disconnect release called on interrupt in separate fiber") {
        for {
          done <- Promise.make[Nothing, Unit]
          fiber <- withLatch { release =>
                     IO.bracketExit(IO.unit)((_, _: Exit[Any, Any]) => done.succeed(()))(_ => release *> IO.never)
                       .disconnect
                       .fork
                   }

          _ <- fiber.interrupt
          r <- Live.live(done.await.timeoutTo(false)(_ => true)(10.seconds))
        } yield assertTrue(r)
      },
      testM("catchAll + ensuring + interrupt") {
        implicit val canFail = CanFail
        for {
          cont <- Promise.make[Nothing, Unit]
          p1   <- Promise.make[Nothing, Boolean]
          f1   <- (cont.succeed(()) *> IO.never).catchAll(IO.fail).ensuring(p1.succeed(true)).fork
          _    <- cont.await
          _    <- f1.interrupt
          res  <- p1.await
        } yield assertTrue(res)
      },
      testM("finalizer can detect interruption") {
        for {
          p1 <- Promise.make[Nothing, Boolean]
          c  <- Promise.make[Nothing, Unit]
          f1 <- (c.succeed(()) *> ZIO.never)
                  .ensuring(IO.descriptor.flatMap(d => p1.succeed(d.interrupters.nonEmpty)))
                  .fork
          _   <- c.await
          _   <- f1.interrupt
          res <- p1.await
        } yield assertTrue(res)
      },
      testM("interruption of raced") {
        for {
          ref   <- Ref.make(0)
          cont1 <- Promise.make[Nothing, Unit]
          cont2 <- Promise.make[Nothing, Unit]
          make   = (p: Promise[Nothing, Unit]) => (p.succeed(()) *> ZIO.infinity).onInterrupt(ref.update(_ + 1))
          raced <- (make(cont1) race make(cont2)).fork
          _     <- cont1.await *> cont2.await
          _     <- raced.interrupt
          count <- ref.get
        } yield assertTrue(count == 2)
      },
      testM("recovery of error in finalizer") {
        for {
          recovered <- Ref.make(false)
          fiber <- withLatch { release =>
                     (release *> ZIO.never)
                       .ensuring(
                         (ZIO.unit *> ZIO.fail("Uh oh")).catchAll(_ => recovered.set(true))
                       )
                       .fork
                   }
          _     <- fiber.interrupt
          value <- recovered.get
        } yield assertTrue(value)
      } @@ zioTag(errors),
      testM("recovery of interruptible") {
        for {
          recovered <- Ref.make(false)
          fiber <- withLatch { release =>
                     (release *> ZIO.never.interruptible)
                       .foldCauseM(
                         cause => recovered.set(cause.interrupted),
                         _ => recovered.set(false)
                       )
                       .uninterruptible
                       .fork
                   }
          _     <- fiber.interrupt
          value <- recovered.get
        } yield assertTrue(value)
      },
      testM("sandbox of interruptible") {
        for {
          selfId    <- ZIO.fiberId
          recovered <- Ref.make[Option[Either[Cause[Nothing], Any]]](None)
          fiber <- withLatch { release =>
                     (release *> ZIO.never.interruptible).sandbox.either
                       .flatMap(exit => recovered.set(Some(exit)))
                       .uninterruptible
                       .fork
                   }
          _     <- fiber.interrupt
          value <- recovered.get
        } yield assertTrue(value.$some.$left == Cause.interrupt(selfId))
      },
      testM("run of interruptible") {
        for {
          recovered <- Ref.make[Option[Exit[Nothing, Any]]](None)
          fiber <- withLatch { release =>
                     (release *> ZIO.never.interruptible).run
                       .flatMap(exit => recovered.set(Some(exit)))
                       .uninterruptible
                       .fork
                   }
          _     <- fiber.interrupt
          value <- recovered.get
        } yield assertTrue(value.$some.$interrupt.$is[Fiber.Id])
      },
      testM("alternating interruptibility") {
        for {
          counter <- Ref.make(0)
          fiber <- withLatch { release =>
                     ((release *> ZIO.never.interruptible.run *> counter
                       .update(_ + 1)).uninterruptible.interruptible.run
                       *> counter.update(_ + 1)).uninterruptible.fork
                   }
          _     <- fiber.interrupt
          value <- counter.get
        } yield assertTrue(value == 2)
      },
      testM("interruption after defect") {
        for {
          ref <- Ref.make(false)
          fiber <- withLatch { release =>
                     (ZIO.effect(throw new Error).run *> release *> ZIO.never)
                       .ensuring(ref.set(true))
                       .fork
                   }
          _     <- fiber.interrupt
          value <- ref.get
        } yield assertTrue(value)
      },
      testM("interruption after defect 2") {
        for {
          ref <- Ref.make(false)
          fiber <- withLatch { release =>
                     (ZIO.effect(throw new Error).run *> release *> ZIO.unit.forever)
                       .ensuring(ref.set(true))
                       .fork
                   }
          _     <- fiber.interrupt
          value <- ref.get
        } yield assertTrue(value)
      },
      testM("disconnect returns immediately on interrupt") {
        for {
          p1 <- Promise.make[Nothing, Unit]
          s <- (p1.succeed(()) *> ZIO.never)
                 .ensuring(ZIO.never)
                 .disconnect
                 .fork
          _   <- p1.await
          res <- s.interrupt
        } yield assertTrue(res.$interrupt.$is[Fiber.Id])
      },
      testM("disconnected effect that is then interrupted eventually performs interruption") {
        Live.live(
          for {
            r  <- Ref.make(false)
            p1 <- Promise.make[Nothing, Unit]
            p3 <- Promise.make[Nothing, Unit]
            s <- (p1.succeed(()) *> ZIO.never)
                   .ensuring(r.set(true) *> clock.sleep(10.millis) *> p3.succeed(()))
                   .disconnect
                   .fork
            _    <- p1.await
            _    <- s.interrupt
            _    <- p3.await
            test <- r.get
          } yield assertTrue(test)
        )
      },
      testM("cause reflects interruption") {
        for {
          finished <- Ref.make(false)
          fiber    <- withLatch(release => (release *> ZIO.fail("foo")).catchAll(_ => finished.set(true)).fork)
          exit     <- fiber.interrupt
          finished <- finished.get
        } yield assertTrue(exit.interrupted == true || finished == true)
      } @@ jvm(nonFlaky),
      testM("bracket use inherits interrupt status") {
        Live.live(
          for {
            ref <- Ref.make(false)
            fiber1 <- withLatch { (release2, await2) =>
                        withLatch { release1 =>
                          release1
                            .bracket_(ZIO.unit, await2 *> clock.sleep(10.millis) *> ref.set(true))
                            .uninterruptible
                            .fork
                        } <* release2
                      }
            _     <- fiber1.interrupt
            value <- ref.get
          } yield assertTrue(value)
        )
      },
      testM("bracket use inherits interrupt status 2") {
        Live.live(
          for {
            latch1 <- Promise.make[Nothing, Unit]
            latch2 <- Promise.make[Nothing, Unit]
            ref    <- Ref.make(false)
            fiber1 <- latch1
                        .succeed(())
                        .bracketExit[Clock, Nothing, Unit](
                          (_: Boolean, _: Exit[Any, Any]) => ZIO.unit,
                          (_: Boolean) => latch2.await *> clock.sleep(10.millis) *> ref.set(true).unit
                        )
                        .uninterruptible
                        .fork
            _     <- latch1.await
            _     <- latch2.succeed(())
            _     <- fiber1.interrupt
            value <- ref.get
          } yield assertTrue(value)
        )
      },
      testM("async can be uninterruptible") {
        Live.live(
          for {
            ref <- Ref.make(false)
            fiber <- withLatch { release =>
                       (release *> clock.sleep(10.millis) *> ref.set(true).unit).uninterruptible.fork
                     }
            _     <- fiber.interrupt
            value <- ref.get
          } yield assertTrue(value)
        )
      },
      testM("closing scope is uninterruptible") {
        for {
          ref     <- Ref.make(false)
          promise <- Promise.make[Nothing, Unit]
          child    = promise.succeed(()) *> Live.live(ZIO.sleep(10.milliseconds)) *> ref.set(true)
          parent   = child.uninterruptible.fork *> promise.await
          fiber   <- parent.fork
          _       <- promise.await
          _       <- fiber.interrupt
          value   <- ref.get
        } yield assertTrue(value)
      },
      testM("effectAsyncInterrupt cancelation") {
        for {
          ref <- ZIO.effectTotal(new java.util.concurrent.atomic.AtomicInteger(0))
          effect = ZIO.effectAsyncInterrupt[Any, Nothing, Any] { _ =>
                     ref.incrementAndGet()
                     Left(ZIO.effectTotal(ref.decrementAndGet()))
                   }
          _     <- ZIO.unit.race(effect)
          value <- ZIO.effectTotal(ref.get())
        } yield assertTrue(value == 0)
      } @@ jvm(nonFlaky(100000))
    ) @@ zioTag(interruption),
    suite("RTS environment")(
      testM("provide is modular") {
        val zio =
          for {
            v1 <- ZIO.environment[Int]
            v2 <- ZIO.environment[Int].provide(2)
            v3 <- ZIO.environment[Int]
          } yield (v1, v2, v3)
        val expected = (4, 2, 4)
        zio.provide(4).map { res =>
          assertTrue(res == expected)
        }
      },
      testM("effectAsync can use environment") {
        val zio = ZIO.effectAsync[Int, Nothing, Int](cb => cb(ZIO.environment[Int]))
        zio.provide(10).map { res =>
          assertTrue(res == 10)
        }
      }
    ),
    suite("RTS forking inheritability")(
      testM("interruption status is heritable") {
        for {
          latch <- Promise.make[Nothing, Unit]
          ref   <- Ref.make(InterruptStatus.interruptible)
          _     <- ZIO.uninterruptible((ZIO.checkInterruptible(ref.set) *> latch.succeed(())).fork *> latch.await)
          v     <- ref.get
        } yield assertTrue(v == InterruptStatus.uninterruptible)
      } @@ zioTag(interruption),
      testM("executor is heritable") {
        val executor = zio.internal.Executor.fromExecutionContext(100) {
          scala.concurrent.ExecutionContext.Implicits.global
        }
        val pool = ZIO.effectTotal(Platform.getCurrentThreadGroup)
        val io = for {
          parentPool <- pool
          childPool  <- pool.fork.flatMap(_.join)
        } yield assertTrue(parentPool == childPool)
        io.lock(executor)
      } @@ jvm(nonFlaky(100))
    ),
    suite("serviceWith")(
      testM("effectfully accesses a service in the environment") {
        val zio = ZIO.serviceWith[Int](int => UIO(int + 3))
        zio.provideLayer(ZLayer.succeed(0)).map { res =>
          assertTrue(res == 3)
        }
      }
    ),
    suite("schedule")(
      testM("runs effect for each recurrence of the schedule") {
        for {
          ref     <- Ref.make[List[Duration]](List.empty)
          effect   = clock.nanoTime.flatMap(duration => ref.update(duration.nanoseconds :: _))
          schedule = Schedule.spaced(1.second) && Schedule.recurs(5)
          _       <- effect.schedule(schedule).fork
          _       <- TestClock.adjust(5.seconds)
          value   <- ref.get.map(_.reverse)
        } yield assertTrue(value == List(1.second, 2.seconds, 3.seconds, 4.seconds, 5.seconds))
      }
    ),
    suite("someOrElse")(
      testM("extracts the value from Some") {
        UIO.succeed(Some(1)).someOrElse(2).map { res =>
          assertTrue(res == 1)
        }
      },
      testM("falls back to the default value if None") {
        UIO.succeed(None).someOrElse(42).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("does not change failed state") {
        ZIO.fail(ExampleError).someOrElse(42).run.map { res =>
          assertTrue(res.$fail == ExampleError)
        }
      } @@ zioTag(errors)
    ),
    suite("someOrElseM")(
      testM("extracts the value from Some") {
        UIO.succeed(Some(1)).someOrElseM(UIO.succeed(2)).map { res =>
          assertTrue(res == 1)
        }
      },
      testM("falls back to the default effect if None") {
        UIO.succeed(None).someOrElseM(UIO.succeed(42)).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("does not change failed state") {
        ZIO.fail(ExampleError).someOrElseM(UIO.succeed(42)).run.map { res =>
          assertTrue(res.$fail == ExampleError)
        }
      } @@ zioTag(errors)
    ),
    suite("someOrFail")(
      testM("extracts the optional value") {
        UIO(Some(42)).someOrFail(exampleError).map { res =>
          assertTrue(res == 42)
        }
      },
      testM("fails when given a None") {
        UIO(Option.empty[Int]).someOrFail(exampleError).run.map { exit =>
          assertTrue(exit.$fail == exampleError)
        }
      } @@ zioTag(errors)
    ),
    suite("summarized")(
      testM("returns summary and value") {
        for {
          counter  <- Ref.make(0)
          increment = counter.updateAndGet(_ + 1)
          result   <- increment.summarized(increment)((_, _))
        } yield {
          val ((start, end), value) = result
          assertTrue(start == 1, value == 2, end == 3)
        }
      }
    ),
    suite("tapCause")(
      testM("effectually peeks at the cause of the failure of this effect") {
        for {
          ref    <- Ref.make(false)
          result <- ZIO.dieMessage("die").tapCause(_ => ref.set(true)).run
          effect <- ref.get
        } yield assertTrue(result.$die.getMessage == "die" && effect)
      }
    ),
    suite("tapDefect")(
      testM("effectually peeks at the cause of the failure of this effect") {
        for {
          ref    <- Ref.make(false)
          result <- ZIO.dieMessage("die").tapCause(_ => ref.set(true)).run
          effect <- ref.get
        } yield assertTrue(result.$die.getMessage == "die" && effect)
      }
    ),
    suite("tapEither")(
      testM("effectually peeks at the failure of this effect") {
        for {
          ref <- Ref.make(0)
          _ <- IO.fail(42)
                 .tapEither {
                   case Left(value) => ref.set(value)
                   case Right(_)    => ref.set(-1)
                 }
                 .run
          effect <- ref.get
        } yield assertTrue(effect == 42)
      },
      testM("effectually peeks at the success of this effect") {
        for {
          ref <- Ref.make(0)
          _ <- Task(42).tapEither {
                 case Left(_)      => ref.set(-1)
                 case Right(value) => ref.set(value)
               }.run
          effect <- ref.get
        } yield assertTrue(effect == 42)
      }
    ),
    suite("tapSome")(
      testM("is identity if the function doesn't match") {
        for {
          ref    <- Ref.make(false)
          result <- ref.set(true).as(42).tapSome(PartialFunction.empty)
          effect <- ref.get
        } yield assertTrue(result == 42 && effect)
      },
      testM("runs the effect if the function matches") {
        for {
          ref    <- Ref.make(0)
          result <- ref.set(10).as(42).tapSome { case r => ref.set(r) }
          effect <- ref.get
        } yield assertTrue(result == 42 && effect == 42)
      }
    ),
    suite("timeout disconnect")(
      testM("returns `Some` with the produced value if the effect completes before the timeout elapses") {
        ZIO.unit.disconnect.timeout(100.millis).map { res =>
          assertTrue(res.$some == ())
        }
      },
      testM("returns `None` otherwise") {
        for {
          fiber  <- ZIO.never.uninterruptible.disconnect.timeout(100.millis).fork
          _      <- TestClock.adjust(100.millis)
          result <- fiber.join
        } yield assertTrue(result.isEmpty)
      }
    ),
    suite("transplant")(
      testM("preserves supervision relationship of nested fibers") {
        for {
          latch1 <- Promise.make[Nothing, Unit]
          latch2 <- Promise.make[Nothing, Unit]
          fiber <- ZIO.transplant { grafter =>
                     grafter {
                       val zio = for {
                         _ <- (latch1.succeed(()) *> ZIO.infinity).onInterrupt(latch2.succeed(())).fork
                         _ <- ZIO.infinity
                       } yield ()
                       zio.fork
                     }
                   }
          _ <- latch1.await
          _ <- fiber.interrupt
          _ <- latch2.await
        } yield assertCompletes
      }
    ),
    suite("unless")(
      testM("executes correct branch only") {
        for {
          effectRef <- Ref.make(0)
          _         <- effectRef.set(1).unless(true)
          val1      <- effectRef.get
          _         <- effectRef.set(2).unless(false)
          val2      <- effectRef.get
          failure    = new Exception("expected")
          _         <- IO.fail(failure).unless(true)
          failed    <- IO.fail(failure).unless(false).either
        } yield assertTrue(val1 == 0, val2 == 2, failed.$left == failure)
      }
    ),
    suite("unlessM")(
      testM("executes condition effect and correct branch") {
        for {
          effectRef     <- Ref.make(0)
          conditionRef  <- Ref.make(0)
          conditionTrue  = conditionRef.update(_ + 1).as(true)
          conditionFalse = conditionRef.update(_ + 1).as(false)
          _             <- effectRef.set(1).unlessM(conditionTrue)
          val1          <- effectRef.get
          conditionVal1 <- conditionRef.get
          _             <- effectRef.set(2).unlessM(conditionFalse)
          val2          <- effectRef.get
          conditionVal2 <- conditionRef.get
          failure        = new Exception("expected")
          _             <- IO.fail(failure).unlessM(conditionTrue)
          failed        <- IO.fail(failure).unlessM(conditionFalse).either
        } yield assertTrue(val1 == 0, conditionVal1 == 1, val2 == 2, conditionVal2 == 2, failed.$left == failure)
      },
      testM("infers correctly") {
        trait R
        trait R1 extends R
        trait E1
        trait E extends E1
        trait A
        val b: ZIO[R, E, Boolean] = ZIO.succeed(true)
        val zio: ZIO[R1, E1, A]   = ZIO.succeed(new A {})
        val _                     = ZIO.unlessM(b)(zio)
        ZIO.succeed(assertCompletes)
      }
    ),
    suite("unrefine")(
      testM("converts some fiber failures into errors") {
        val s    = "division by zero"
        val zio1 = ZIO.die(new IllegalArgumentException(s))
        zio1.unrefine { case e: IllegalArgumentException => e.getMessage }.run.map { res =>
          assertTrue(res.$fail == s)
        }
      },
      testM("leaves the rest") {
        val t    = new IllegalArgumentException("division by zero")
        val zio1 = ZIO.die(t)
        zio1.unrefine { case e: NumberFormatException => e.getMessage }.run.map { res =>
          assertTrue(res.$die == t)
        }
      }
    ),
    suite("unrefineTo")(
      testM("converts some fiber failures into errors") {
        val t    = new IllegalArgumentException("division by zero")
        val zio1 = ZIO.die(t)
        val zio2 = zio1.unrefineTo[IllegalArgumentException]
        zio2.run.map { res =>
          assertTrue(res.$fail == t)
        }
      },
      testM("leaves the rest") {
        val t    = new IllegalArgumentException("division by zero")
        val zio1 = ZIO.die(t)
        val zio2 = zio1.unrefineTo[NumberFormatException]
        zio2.run.map { res =>
          assertTrue(res.$die == t)
        }
      }
    ) @@ zioTag(errors),
    suite("unrefineWith")(
      testM("converts some fiber failures into errors") {
        val s    = "division by zero"
        val zio1 = ZIO.die(new IllegalArgumentException(s))
        val zio2 = zio1.unrefineWith { case e: IllegalArgumentException => Option(e.getMessage) }(_ => None)
        zio2.run.map { res =>
          assertTrue(res.$fail.$some == s)
        }
      },
      testM("leaves the rest") {
        val t    = new IllegalArgumentException("division by zero")
        val zio1 = ZIO.die(t)
        val zio2 = zio1.unrefineWith { case e: NumberFormatException => Option(e.getMessage) }(_ => None)
        zio2.run.map { res =>
          assertTrue(res.$die == t)
        }
      },
      testM("uses the specified function to convert the `E` into an `E1`") {
        val zio1 = ZIO.fail("fail")
        val zio2 = zio1.unrefineWith { case e: IllegalArgumentException => Option(e.getMessage) }(_ => None)
        zio2.run.map { res =>
          assertTrue(res.$fail.isEmpty)
        }
      }
    ) @@ zioTag(errors),
    suite("unsandbox")(
      testM("unwraps exception") {
        val failure: IO[Cause[Exception], String] = IO.fail(fail(new Exception("fail")))
        val success: IO[Cause[Any], Int]          = IO.succeed(100)
        for {
          message <- failure.unsandbox.foldM(e => IO.succeed(e.getMessage), _ => IO.succeed("unexpected"))
          result  <- success.unsandbox
        } yield assertTrue(message == "fail" && result == 100)
      },
      testM("no information is lost during composition") {
        val causes = Gen.causes(Gen.anyString, Gen.throwable)
        def cause[R, E](zio: ZIO[R, E, Nothing]): ZIO[R, Nothing, Cause[E]] =
          zio.foldCauseM(ZIO.succeed(_), ZIO.fail)
        checkM(causes) { c =>
          for {
            result <- cause(ZIO.halt(c).sandbox.mapErrorCause(e => e.untraced).unsandbox)
          } yield assertTrue(result == c, result.prettyPrint == c.prettyPrint)
        }
      }
    ),
    suite("updateService")(
      testM("updates a service in the environment") {
        val zio = for {
          a <- ZIO.service[Int].updateService[Int](_ + 1)
          b <- ZIO.service[Int]
        } yield (a, b)
        val expected = (1, 0)
        zio.provideLayer(ZLayer.succeed(0)).map { res =>
          assertTrue(res == expected)
        }
      }
    ),
    suite("validate")(
      testM("returns all errors if never valid") {
        val in                      = List.fill(10)(0)
        def fail[A](a: A): IO[A, A] = IO.fail(a)
        IO.validate(in)(fail).flip.map { res =>
          assertTrue(res == in)
        }

      } @@ zioTag(errors),
      testM("accumulate errors and ignore successes") {
        implicit val canFail = CanFail
        val in               = List.range(0, 10)
        ZIO.validate(in)(a => if (a % 2 == 0) ZIO.succeed(a) else ZIO.fail(a)).flip.map { res =>
          assertTrue(res == List(1, 3, 5, 7, 9))
        }
      } @@ zioTag(errors),
      testM("accumulate successes") {
        implicit val canFail = CanFail
        val in               = List.range(0, 10)
        IO.validate(in)(a => ZIO.succeed(a)).map { res =>
          assertTrue(res == in)
        }
      }
    ),
    suite("validate_")(
      testM("returns all errors if never valid") {
        val in                            = List.fill(10)(0)
        def fail[A](a: A): IO[A, Nothing] = IO.fail(a)
        IO.validate(in)(fail).flip.map { res =>
          assertTrue(res == in)
        }

      } @@ zioTag(errors)
    ),
    suite("validatePar")(
      testM("returns all errors if never valid") {
        val in                      = List.fill(1000)(0)
        def fail[A](a: A): IO[A, A] = IO.fail(a)
        IO.validatePar(in)(fail).flip.map { res =>
          assertTrue(res == in)
        }
      } @@ zioTag(errors),
      testM("accumulate errors and ignore successes") {
        implicit val canFail = CanFail
        val in               = List.range(0, 10)
        ZIO.validatePar(in)(a => if (a % 2 == 0) ZIO.succeed(a) else ZIO.fail(a)).flip.map { res =>
          assertTrue(res == List(1, 3, 5, 7, 9))
        }
      } @@ zioTag(errors),
      testM("accumulate successes") {
        implicit val canFail = CanFail
        val in               = List.range(0, 10)
        IO.validatePar(in)(a => ZIO.succeed(a)).map { res =>
          assertTrue(res == in)
        }
      }
    ),
    suite("validatePar_")(testM("returns all errors if never valid") {
      val in                            = List.fill(10)(0)
      def fail[A](a: A): IO[A, Nothing] = IO.fail(a)
      IO.validatePar_(in)(fail).flip.map { res =>
        assertTrue(res == in)
      }

    } @@ zioTag(errors)),
    suite("validateFirst")(
      testM("returns all errors if never valid") {
        val in = List.fill(10)(0)
        IO.validateFirst(in)(a => ZIO.fail(a)).flip.map { res =>
          assertTrue(res == in)
        }
      } @@ zioTag(errors),
      testM("runs sequentially and short circuits on first success validation") {
        implicit val canFail = CanFail
        val in               = List.range(1, 10)
        val f                = (a: Int) => if (a == 6) ZIO.succeed(a) else ZIO.fail(a)

        for {
          counter <- Ref.make(0)
          result  <- ZIO.validateFirst(in)(a => counter.update(_ + 1) *> f(a))
          count   <- counter.get
        } yield assertTrue(result == 6 && count == 6)
      },
      testM("returns errors in correct order") {
        val as = List(2, 4, 6, 3, 5, 6)
        for {
          results <- ZIO.validateFirst(as)(ZIO.fail(_)).flip
        } yield assertTrue(results == List(2, 4, 6, 3, 5, 6))
      } @@ zioTag(errors)
    ),
    suite("validateFirstPar")(
      testM("returns all errors if never valid") {
        val in = List.fill(1000)(0)
        IO.validateFirstPar(in)(a => ZIO.fail(a)).flip.map { res =>
          assertTrue(res == in)
        }
      } @@ zioTag(errors),
      testM("returns success if valid") {
        implicit val canFail = CanFail
        val in               = List.range(1, 10)
        val f                = (a: Int) => if (a == 6) ZIO.succeed(a) else ZIO.fail(a)
        ZIO.validateFirstPar(in)(f(_)).map { res =>
          assertTrue(res == 6)
        }
      }
    ),
    suite("validateWith")(
      testM("succeeds") {
        ZIO(1).validateWith(ZIO(2))(_ + _).map { res =>
          assertTrue(res == 3)
        }
      },
      testM("fails") {
        ZIO(1).validate(ZIO.fail(2)).run.map { exit =>
          assertTrue(exit.$fail == 2)
        }
      },
      testM("combines both cause") {
        ZIO.fail(1).validate(ZIO.fail(2)).run.map { exit =>
          assertTrue(exit.$cause == Cause.Then(Cause.Fail(1), Cause.Fail(2)))
        }
      }
    ),
    suite("when")(
      testM("executes correct branch only") {
        for {
          effectRef <- Ref.make(0)
          _         <- effectRef.set(1).when(false)
          val1      <- effectRef.get
          _         <- effectRef.set(2).when(true)
          val2      <- effectRef.get
          failure    = new Exception("expected")
          _         <- IO.fail(failure).when(false)
          failed    <- IO.fail(failure).when(true).either
        } yield {
          assertTrue(val1 == 0, val2 == 2, failed.$left == failure)
        }
      }
    ),
    suite("whenCase")(
      testM("executes correct branch only") {
        val v1: Option[Int] = None
        val v2: Option[Int] = Some(0)
        for {
          ref  <- Ref.make(false)
          _    <- ZIO.whenCase(v1) { case Some(_) => ref.set(true) }
          res1 <- ref.get
          _    <- ZIO.whenCase(v2) { case Some(_) => ref.set(true) }
          res2 <- ref.get
        } yield assertTrue(!res1 && res2)
      }
    ),
    suite("whenCaseM")(
      testM("executes condition effect and correct branch") {
        val v1: Option[Int] = None
        val v2: Option[Int] = Some(0)
        for {
          ref  <- Ref.make(false)
          _    <- ZIO.whenCaseM(IO.succeed(v1)) { case Some(_) => ref.set(true) }
          res1 <- ref.get
          _    <- ZIO.whenCaseM(IO.succeed(v2)) { case Some(_) => ref.set(true) }
          res2 <- ref.get
        } yield assertTrue(!res1 && res2)
      }
    ),
    suite("whenM")(
      testM("executes condition effect and correct branch") {
        for {
          effectRef     <- Ref.make(0)
          conditionRef  <- Ref.make(0)
          conditionTrue  = conditionRef.update(_ + 1).as(true)
          conditionFalse = conditionRef.update(_ + 1).as(false)
          _             <- effectRef.set(1).whenM(conditionFalse)
          val1          <- effectRef.get
          conditionVal1 <- conditionRef.get
          _             <- effectRef.set(2).whenM(conditionTrue)
          val2          <- effectRef.get
          conditionVal2 <- conditionRef.get
          failure        = new Exception("expected")
          _             <- IO.fail(failure).whenM(conditionFalse)
          failed        <- IO.fail(failure).whenM(conditionTrue).either
        } yield {
          assertTrue(val1 == 0, conditionVal1 == 1, val2 == 2, conditionVal2 == 2, failed.$left == failure)
        }
      },
      testM("infers correctly") {
        trait R
        trait R1 extends R
        trait E1
        trait E extends E1
        trait A
        val b: ZIO[R, E, Boolean] = ZIO.succeed(true)
        val zio: ZIO[R1, E1, A]   = ZIO.succeed(new A {})
        val _                     = ZIO.whenM(b)(zio)
        ZIO.succeed(assertCompletes)
      }
    ),
    suite("withFilter")(
      testM("tuple value is extracted correctly from task") {
        val expected = (1, 2, 3)
        for {
          (i, j, k) <- Task((1, 2, 3))
        } yield {
          assertTrue((i, j, k) == expected)
        }
      },
      testM("condition in for-comprehension syntax works correctly for task") {
        for {
          n <- Task(3) if n > 0
        } yield assertTrue(n == 3)
      },
      testM("unsatisfied condition should fail with NoSuchElementException") {
        val task =
          for {
            n <- Task(3) if n > 10
          } yield n
        task.run.map { res =>
          assertTrue(res.$fail.$is[NoSuchElementException])
        }
      },
      testM("withFilter doesn't compile with IO that fails with type other than Throwable") {
        val result = typeCheck {
          """
            import zio._
            val io: IO[String, Int] = IO.succeed(1)
            for {
              n <- io if n > 0
            } yield n
              """
        }

        val expected =
          "Pattern guards are only supported when the error type is a supertype of NoSuchElementException. However, your effect has String for the error type."

        result.map { res =>
          if (TestVersion.isScala2) assertTrue(res.$left == expected)
          else assertTrue(res.$left.$is[String])
        }
      }
    ),
    suite("zipPar")(
      testM("does not swallow exit causes of loser") {
        ZIO.interrupt.zipPar(IO.interrupt).run.map {
          case Exit.Failure(cause) => assertTrue(cause.interruptors.nonEmpty)
          case _                   => assertTrue(false)
        }
      },
      testM("does not report failure when interrupting loser after it succeeded") {
        val io = ZIO.interrupt.zipPar(IO.succeed(1))
        io.sandbox.either.map(_.left.map(_.interrupted)).map { res =>
          assertTrue(res.$left)
        }
      } @@ zioTag(interruption),
      testM("passes regression 1") {
        IO.succeed[Int](1).zipPar(IO.succeed[Int](2)).flatMap(t => IO.succeed(t._1 + t._2)).map(_ == 3).map { res =>
          assertTrue(res)
        }
      } @@ zioTag(regression) @@ jvm(nonFlaky),
      testM("paralellizes simple success values") {
        def countdown(n: Int): UIO[Int] =
          if (n == 0) IO.succeed(0)
          else
            IO.succeed[Int](1).zipPar(IO.succeed[Int](2)).flatMap(t => countdown(n - 1).map(y => t._1 + t._2 + y))

        countdown(50).map { res =>
          assertTrue(res == 150)
        }
      },
      testM("does not kill fiber when forked on parent scope") {

        for {
          latch1 <- Promise.make[Nothing, Unit]
          latch2 <- Promise.make[Nothing, Unit]
          latch3 <- Promise.make[Nothing, Unit]
          ref1   <- Ref.make(false)
          left = ZIO
                   .uninterruptibleMask(restore => latch2.succeed(()) *> restore(latch1.await *> ZIO.succeed("foo")))
                   .onInterrupt(ref1.set(true))
          right                         = latch3.succeed(()).as(42)
          _                            <- (latch2.await *> latch3.await *> latch1.succeed(())).fork
          result                       <- left.fork zipPar right
          (leftInnerFiber, rightResult) = result
          leftResult                   <- leftInnerFiber.await
          interrupted                  <- ref1.get
        } yield assertTrue(!interrupted && leftResult.$success == "foo" && rightResult == 42)
      }
    ),
    suite("toFuture")(
      testM("should fail with ZTrace attached") {
        for {
          future <- ZIO.fail(new Throwable(new IllegalArgumentException)).toFuture
          result <- ZIO.fromFuture(_ => future).either
        } yield assertTrue(result.$left.getSuppressed.exists(_.getMessage.contains("Fiber:Id(")))
      }
    ) @@ zioTag(future),
    suite("resurrect")(
      testM("should fail checked") {
        val error: Exception = new Exception("msg")
        ZIO.fail(error).unit.orDie.resurrect.either.map { res =>
          assertTrue(res.$left == error)
        }
      }
    ),
    suite("options")(
      testM("basic option test") {
        for {
          value <- ZIO.getOrFailUnit(Some("foo"))
        } yield assertTrue(value == "foo")
      },
      testM("side effect unit in option test") {
        for {
          value <- ZIO.getOrFailUnit(None).catchAll(_ => ZIO.succeed("Controlling unit side-effect"))
        } yield assertTrue(value == "Controlling unit side-effect")
      }
    ),
    suite("promises")(
      testM("promise test") {
        val func: String => String = s => s.toUpperCase
        for {
          promise <- ZIO.succeed(scala.concurrent.Promise[String]())
          _ <- ZIO.effect {
                 Try(func("hello world from future")) match {
                   case Success(value)     => promise.success(value)
                   case Failure(exception) => promise.failure(exception)
                 }
               }.fork
          value <- ZIO.fromPromiseScala(promise)
        } yield assertTrue(value == "HELLO WORLD FROM FUTURE")
      },
      testM("promise supplier test") {
        val func: Unit => String = _ => "hello again from future"
        for {
          promise <- ZIO.succeed(scala.concurrent.Promise[String]())
          _ <- ZIO.effect {
                 Try(func(())) match {
                   case Success(value)     => promise.success(value)
                   case Failure(exception) => promise.failure(exception)
                 }
               }.fork
          value <- ZIO.fromPromiseScala(promise)
        } yield assertTrue(value == "hello again from future")
      },
      testM("promise ugly path test") {
        val func: String => String = s => s.toUpperCase
        for {
          promise <- ZIO.succeed(scala.concurrent.Promise[String]())
          _ <- ZIO.effect {
                 Try(func(null)) match {
                   case Success(value)     => promise.success(value)
                   case Failure(exception) => promise.failure(exception)
                 }
               }.fork
          value <- ZIO
                     .fromPromiseScala(promise)
                     .catchAll(_ => ZIO.succeed("Controlling side-effect of function passed to promise"))
        } yield assertTrue(value == "Controlling side-effect of function passed to promise")
      }
    )
  )

  def functionIOGen: Gen[Random with Sized, String => Task[Int]] =
    Gen.function[Random with Sized, String, Task[Int]](Gen.successes(Gen.anyInt))

  def listGen: Gen[Random with Sized, List[String]] =
    Gen.listOfN(100)(Gen.alphaNumericString)

  val exampleError = new Error("something went wrong")

  def exactlyOnce[R, A, A1](value: A)(func: UIO[A] => ZIO[R, String, A1]): ZIO[R, String, A1] =
    Ref.make(0).flatMap { ref =>
      for {
        res   <- func(ref.update(_ + 1) *> ZIO.succeed(value))
        count <- ref.get
        _ <- if (count != 1) {
               ZIO.fail("Accessed more than once")
             } else {
               ZIO.unit
             }
      } yield res
    }

  val testString = "supercalifragilisticexpialadocious"

  val ExampleError    = new Throwable("Oh noes!")
  val InterruptCause1 = new Throwable("Oh noes 1!")
  val InterruptCause2 = new Throwable("Oh noes 2!")
  val InterruptCause3 = new Throwable("Oh noes 3!")

  val TaskExampleError: Task[Int] = IO.fail[Throwable](ExampleError)

  val TaskExampleDie: Task[Int] = IO.effectTotal(throw ExampleError)

  def asyncExampleError[A]: Task[A] =
    IO.effectAsync[Throwable, A](_(IO.fail(ExampleError)))

  def sum(n: Int): Int =
    if (n <= 0) 0
    else n + sum(n - 1)

  def deepMapNow(n: Int): UIO[Int] = {
    @tailrec
    def loop(n: Int, acc: UIO[Int]): UIO[Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, IO.succeed(0))
  }

  def deepMapEffect(n: Int): UIO[Int] = {
    @tailrec
    def loop(n: Int, acc: UIO[Int]): UIO[Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, IO.effectTotal(0))
  }

  def deepErrorEffect(n: Int): Task[Unit] =
    if (n == 0) IO.effect(throw ExampleError)
    else IO.unit *> deepErrorEffect(n - 1)

  def deepErrorFail(n: Int): Task[Unit] =
    if (n == 0) IO.fail(ExampleError)
    else IO.unit *> deepErrorFail(n - 1)

  def fib(n: Int): BigInt =
    if (n <= 1) n
    else fib(n - 1) + fib(n - 2)

  def concurrentFib(n: Int): Task[BigInt] =
    if (n <= 1) IO.succeed[BigInt](n)
    else
      for {
        f1 <- concurrentFib(n - 1).fork
        f2 <- concurrentFib(n - 2).fork
        v1 <- f1.join
        v2 <- f2.join
      } yield v1 + v2

  def AsyncUnit[E]: IO[E, Unit] = IO.effectAsync[E, Unit](_(IO.unit))

  type Logging = Has[Logging.Service]

  object Logging {
    trait Service
    val live: ZLayer[Any, Nothing, Logging] = ZLayer.succeed(new Logging.Service {})
  }
}
