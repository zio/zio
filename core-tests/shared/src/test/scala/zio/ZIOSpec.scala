package zio

import scala.annotation.tailrec
import scala.util.{ Failure, Success }

import zio.Cause._
import zio.LatchOps._
import zio.clock.Clock
import zio.duration._
import zio.random.Random
import zio.test.Assertion._
import zio.test.TestAspect.{ flaky, jvm, nonFlaky, scala2Only }
import zio.test._
import zio.test.environment._

object ZIOSpec extends ZIOBaseSpec {

  def spec = suite("ZIO")(
    suite("absorbWith")(
      testM("on fail") {
        assertM(TaskExampleError.absorbWith(identity).run)(fails(equalTo(ExampleError)))
      },
      testM("on die") {
        assertM(TaskExampleDie.absorbWith(identity).run)(fails(equalTo(ExampleError)))
      },
      testM("on success") {
        assertM(ZIO.succeed(1).absorbWith(_ => ExampleError))(equalTo(1))
      }
    ),
    suite("bimap")(
      testM("maps over both error and value channels") {
        checkM(Gen.anyInt) { i =>
          val res = IO.fail(i).bimap(_.toString, identity).either
          assertM(res)(isLeft(equalTo(i.toString)))
        }
      }
    ),
    suite("bracket")(
      testM("bracket happy path") {
        for {
          release  <- Ref.make(false)
          result   <- ZIO.bracket(IO.succeed(42), (_: Int) => release.set(true), (a: Int) => ZIO.effectTotal(a + 1))
          released <- release.get
        } yield assert(result)(equalTo(43)) && assert(released)(isTrue)
      },
      testM("bracket_ happy path") {
        for {
          release  <- Ref.make(false)
          result   <- IO.succeed(42).bracket_(release.set(true), ZIO.effectTotal(0))
          released <- release.get
        } yield assert(result)(equalTo(0)) && assert(released)(isTrue)
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
        } yield assert(result)(equalTo(0L)) && assert(released)(isTrue)
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
        } yield assert(cause.failures)(equalTo(List("use failed"))) &&
          assert(cause.defects)(equalTo(List(releaseDied)))
      }
    ),
    suite("bracketFork")(
      testM("bracketFork happy path") {
        for {
          release  <- Ref.make(false)
          result   <- ZIO.bracketFork(IO.succeed(42), (_: Int) => release.set(true), (a: Int) => ZIO.effectTotal(a + 1))
          released <- release.get
        } yield assert(result)(equalTo(43)) && assert(released)(isTrue)
      },
      testM("bracketFork_ happy path") {
        for {
          release  <- Ref.make(false)
          result   <- IO.succeed(42).bracketFork_(release.set(true), ZIO.effectTotal(0))
          released <- release.get
        } yield assert(result)(equalTo(0)) && assert(released)(isTrue)
      },
      testM("bracketForkExit happy path") {
        for {
          release <- Ref.make(false)
          result <- ZIO.bracketForkExit(
                     IO.succeed(42),
                     (_: Int, _: Exit[Any, Any]) => release.set(true),
                     (_: Int) => IO.succeed(0L)
                   )
          released <- release.get
        } yield assert(result)(equalTo(0L)) && assert(released)(isTrue)
      },
      testM("bracketForkExit error handling") {
        val releaseDied: Throwable = new RuntimeException("release died")
        for {
          exit <- ZIO
                   .bracketForkExit[Any, String, Int, Int](
                     ZIO.succeed(42),
                     (_, _) => ZIO.die(releaseDied),
                     _ => ZIO.fail("use failed")
                   )
                   .run
          cause <- exit.foldM(cause => ZIO.succeed(cause), _ => ZIO.fail("effect should have failed"))
        } yield assert(cause.failures)(equalTo(List("use failed"))) &&
          assert(cause.defects)(equalTo(List(releaseDied)))
      }
    ),
    suite("cached")(
      testM("returns new instances after duration") {
        def incrementAndGet(ref: Ref[Int]): UIO[Int] = ref.update(_ + 1)
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
        } yield assert(a)(equalTo(b)) && assert(b)(not(equalTo(c))) && assert(c)(equalTo(d))
      }
    ),
    suite("catchSomeCause")(
      testM("catches matching cause") {
        ZIO.interrupt.catchSomeCause {
          case c if c.interrupted => ZIO.succeed(true)
        }.sandbox.map(
          assert(_)(isTrue)
        )
      },
      testM("halts if cause doesn't match") {
        ZIO.fiberId.flatMap { fiberId =>
          ZIO.interrupt.catchSomeCause {
            case c if (!c.interrupted) => ZIO.succeed(true)
          }.sandbox.either.map(
            assert(_)(isLeft(equalTo(Cause.interrupt(fiberId))))
          )
        }
      }
    ),
    suite("collect")(
      testM("returns failure ignoring value") {
        val goodCase =
          exactlyOnce(0)(_.collect(s"value was not 0")({ case v @ 0 => v })).sandbox.either

        val badCase =
          exactlyOnce(1)(_.collect(s"value was not 0")({ case v @ 0 => v })).sandbox.either
            .map(_.left.map(_.failureOrCause))

        assertM(goodCase)(isRight(equalTo(0))) &&
        assertM(badCase)(isLeft(isLeft(equalTo("value was not 0"))))
      }
    ),
    suite("collectAllPar")(
      testM("returns the list in the same order") {
        val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
        val res  = IO.collectAllPar(list)
        assertM(res)(equalTo(List(1, 2, 3)))
      }
    ),
    suite("collectAllParN")(
      testM("returns results in the same order") {
        val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
        val res  = IO.collectAllParN(2)(list)
        assertM(res)(equalTo(List(1, 2, 3)))
      }
    ),
    suite("collectM")(
      testM("returns failure ignoring value") {
        val goodCase =
          exactlyOnce(0)(_.collectM[Any, String, Int]("Predicate failed!")({ case v @ 0 => ZIO.succeed(v) })).sandbox.either

        val partialBadCase =
          exactlyOnce(0)(_.collectM("Predicate failed!")({ case v @ 0 => ZIO.fail("Partial failed!") })).sandbox.either
            .map(_.left.map(_.failureOrCause))

        val badCase =
          exactlyOnce(1)(_.collectM("Predicate failed!")({ case v @ 0 => ZIO.succeed(v) })).sandbox.either
            .map(_.left.map(_.failureOrCause))

        assertM(goodCase)(isRight(equalTo(0))) &&
        assertM(partialBadCase)(isLeft(isLeft(equalTo("Partial failed!")))) &&
        assertM(badCase)(isLeft(isLeft(equalTo("Predicate failed!"))))
      }
    ),
    suite("companion object method consistency")(
      testM("absolve") {
        checkM(Gen.alphaNumericString) { str =>
          val ioEither: UIO[Either[Nothing, String]] = IO.succeed(Right(str))
          for {
            abs1 <- ioEither.absolve
            abs2 <- IO.absolve(ioEither)
          } yield assert(abs1)(equalTo(abs2))
        }
      },
      testM("firstSuccessOf") {
        val io  = IO.effectTotal(testString)
        val ios = List.empty[UIO[String]]
        for {
          race1 <- io.firstSuccessOf(ios)
          race2 <- IO.firstSuccessOf(io, ios)
        } yield assert(race1)(equalTo(race2))
      },
      testM("flatten") {
        checkM(Gen.alphaNumericString) { str =>
          for {
            flatten1 <- IO.effectTotal(IO.effectTotal(str)).flatten
            flatten2 <- IO.flatten(IO.effectTotal(IO.effectTotal(str)))
          } yield assert(flatten1)(equalTo(flatten2))
        }
      },
      testM("raceAll") {
        val io  = IO.effectTotal(testString)
        val ios = List.empty[UIO[String]]
        for {
          race1 <- io.raceAll(ios)
          race2 <- IO.raceAll(io, ios)
        } yield assert(race1)(equalTo(race2))
      }
    ),
    suite("done")(
      testM("Check done lifts exit result into IO") {

        val fiberId                       = Fiber.Id(0L, 123L)
        val error                         = exampleError
        val completed                     = Exit.succeed(1)
        val interrupted: Exit[Error, Int] = Exit.interrupt(fiberId)
        val terminated: Exit[Error, Int]  = Exit.die(error)
        val failed: Exit[Error, Int]      = Exit.fail(error)

        assertM(IO.done(completed))(equalTo(1)) &&
        assertM(IO.done(interrupted).run)(isInterrupted) &&
        assertM(IO.done(terminated).run)(dies(equalTo(error))) &&
        assertM(IO.done(failed).run)(fails(equalTo(error)))
      }
    ),
    suite("doUntil")(
      testM("doUntil repeats until condition is true") {
        for {
          in     <- Ref.make(10)
          out    <- Ref.make(0)
          _      <- (in.update(_ - 1) <* out.update(_ + 1)).doUntil(_ == 0)
          result <- out.get
        } yield assert(result)(equalTo(10))
      },
      testM("doUntil always evaluates effect once") {
        for {
          ref    <- Ref.make(0)
          _      <- ref.update(_ + 1).doUntil(_ => true)
          result <- ref.get
        } yield assert(result)(equalTo(1))
      }
    ),
    suite("doWhile")(
      testM("doWhile repeats while condition is true") {
        for {
          in     <- Ref.make(10)
          out    <- Ref.make(0)
          _      <- (in.update(_ - 1) <* out.update(_ + 1)).doWhile(_ >= 0)
          result <- out.get
        } yield assert(result)(equalTo(11))
      },
      testM("doWhile always evaluates effect once") {
        for {
          ref    <- Ref.make(0)
          _      <- ref.update(_ + 1).doWhile(_ => false)
          result <- ref.get
        } yield assert(result)(equalTo(1))
      }
    ),
    suite("eventually")(
      testM("succeeds eventually") {
        def effect(ref: Ref[Int]) =
          ref.get.flatMap(n => if (n < 10) ref.update(_ + 1) *> IO.fail("Ouch") else UIO.succeed(n))

        val test = for {
          ref <- Ref.make(0)
          n   <- effect(ref).eventually
        } yield n

        assertM(test)(equalTo(10))
      }
    ),
    suite("fallback")(
      testM("executes an effect and returns its value if it succeeds") {
        import zio.CanFail.canFail
        assertM(ZIO.succeed(1).fallback(2))(equalTo(1))
      },
      testM("returns the specified value if the effect fails") {
        assertM(ZIO.fail("fail").fallback(1))(equalTo(1))
      }
    ),
    suite("filterOrElse")(
      testM("returns checked failure from held value") {
        val goodCase =
          exactlyOnce(0)(_.filterOrElse[Any, String, Int](_ == 0)(a => ZIO.fail(s"$a was not 0"))).sandbox.either

        val badCase =
          exactlyOnce(1)(_.filterOrElse[Any, String, Int](_ == 0)(a => ZIO.fail(s"$a was not 0"))).sandbox.either
            .map(_.left.map(_.failureOrCause))

        assertM(goodCase)(isRight(equalTo(0))) &&
        assertM(badCase)(isLeft(isLeft(equalTo("1 was not 0"))))
      }
    ),
    suite("filterOrElse_")(
      testM("returns checked failure ignoring value") {
        val goodCase =
          exactlyOnce(0)(_.filterOrElse_[Any, String, Int](_ == 0)(ZIO.fail(s"Predicate failed!"))).sandbox.either

        val badCase =
          exactlyOnce(1)(_.filterOrElse_[Any, String, Int](_ == 0)(ZIO.fail(s"Predicate failed!"))).sandbox.either
            .map(_.left.map(_.failureOrCause))

        assertM(goodCase)(isRight(equalTo(0))) &&
        assertM(badCase)(isLeft(isLeft(equalTo("Predicate failed!"))))
      }
    ),
    suite("filterOrFail")(
      testM("returns failure ignoring value") {
        val goodCase =
          exactlyOnce(0)(_.filterOrFail(_ == 0)("Predicate failed!")).sandbox.either

        val badCase =
          exactlyOnce(1)(_.filterOrFail(_ == 0)("Predicate failed!")).sandbox.either
            .map(_.left.map(_.failureOrCause))

        assertM(goodCase)(isRight(equalTo(0))) &&
        assertM(badCase)(isLeft(isLeft(equalTo("Predicate failed!"))))
      }
    ),
    suite("flattenErrorOption")(
      testM("fails when given Some error") {
        val task: IO[String, Int] = IO.fail(Some("Error")).flattenErrorOption("Default")
        assertM(task.run)(fails(equalTo("Error")))
      },
      testM("fails with Default when given None error") {
        val task: IO[String, Int] = IO.fail(None).flattenErrorOption("Default")
        assertM(task.run)(fails(equalTo("Default")))
      },
      testM("succeeds when given a value") {
        val task: IO[String, Int] = IO.succeed(1).flattenErrorOption("Default")
        assertM(task)(equalTo(1))
      }
    ),
    suite("foldLeft")(
      testM("with a successful step function sums the list properly") {
        checkM(Gen.listOf(Gen.anyInt)) { l =>
          val res = IO.foldLeft(l)(0)((acc, el) => IO.succeed(acc + el))
          assertM(res)(equalTo(l.sum))
        }
      },
      testM("`with a failing step function returns a failed IO") {
        checkM(Gen.listOf1(Gen.anyInt)) { l =>
          val res = IO.foldLeft(l)(0)((_, _) => IO.fail("fail"))
          assertM(res.run)(fails(equalTo("fail")))
        }
      },
      testM("run sequentially from left to right") {
        checkM(Gen.listOf1(Gen.anyInt)) { l =>
          val res = IO.foldLeft(l)(List.empty[Int])((acc, el) => IO.succeed(el :: acc))
          assertM(res)(equalTo(l.reverse))
        }
      }
    ),
    suite("foldRight")(
      testM("with a successful step function sums the list properly") {
        checkM(Gen.listOf(Gen.anyInt)) { l =>
          val res = IO.foldRight(l)(0)((el, acc) => IO.succeed(acc + el))
          assertM(res)(equalTo(l.sum))
        }
      },
      testM("`with a failing step function returns a failed IO") {
        checkM(Gen.listOf1(Gen.anyInt)) { l =>
          val res = IO.foldRight(l)(0)((_, _) => IO.fail("fail"))
          assertM(res.run)(fails(equalTo("fail")))
        }
      },
      testM("run sequentially from right to left") {
        checkM(Gen.listOf1(Gen.anyInt)) { l =>
          val res = IO.foldRight(l)(List.empty[Int])((el, acc) => IO.succeed(el :: acc))
          assertM(res)(equalTo(l))
        }
      }
    ),
    suite("foreach")(
      testM("returns the list of results") {
        checkAllM(functionIOGen, listGen) { (f, list) =>
          val res = IO.foreach(list)(f)
          assertM(res)(isSubtype[List[Int]](anything) && hasSize(equalTo(100)))
        }
      },
      testM("both evaluates effects and returns the list of results in the same order") {
        val list = List("1", "2", "3")
        for {
          ref     <- Ref.make(List.empty[String])
          res     <- IO.foreach(list)(x => ref.update(_ :+ x) *> IO.effectTotal[Int](x.toInt))
          effects <- ref.get
        } yield assert(effects)(equalTo(list)) && assert(res)(equalTo(List(1, 2, 3)))
      },
      testM("fails if one of the effects fails") {
        val list = List("1", "h", "3")
        val res  = IO.foreach(list)(x => IO.effectTotal[Int](x.toInt))
        assertM(res.run)(dies(isSubtype[NumberFormatException](anything)))
      }
    ),
    suite("foreach_")(
      testM("runs effects in order") {
        val as = List(1, 2, 3, 4, 5)
        for {
          ref <- Ref.make(List.empty[Int])
          _   <- ZIO.foreach_(as)(a => ref.update(_ :+ a))
          rs  <- ref.get
        } yield assert(rs)(equalTo(as))
      },
      testM("can be run twice") {
        val as = List(1, 2, 3, 4, 5)
        for {
          ref <- Ref.make(0)
          zio = ZIO.foreach_(as)(a => ref.update(_ + a))
          _   <- zio
          _   <- zio
          sum <- ref.get
        } yield assert(sum)(equalTo(30))
      }
    ),
    suite("foreachPar")(
      testM("returns results in the same order") {
        val list = List("1", "2", "3")
        val res  = IO.foreachPar(list)(x => IO.effectTotal[Int](x.toInt))
        assertM(res)(equalTo(List(1, 2, 3)))
      },
      testM("runs effects in parallel") {
        assertM(for {
          p <- Promise.make[Nothing, Unit]
          _ <- UIO.foreachPar(List(UIO.never, p.succeed(())))(a => a).fork
          _ <- p.await
        } yield true)(isTrue)
      },
      testM("propagates error") {
        val ints = List(1, 2, 3, 4, 5, 6)
        val odds = ZIO.foreachPar(ints) { n =>
          if (n % 2 != 0) ZIO.succeed(n) else ZIO.fail("not odd")
        }
        assertM(odds.flip)(equalTo("not odd"))
      },
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
        } yield assert(e)(equalTo("C")) && assert(v)(isFalse)
      }
    ),
    suite("foreachPar_")(
      testM("runs all effects") {
        val as = Seq(1, 2, 3, 4, 5)
        for {
          ref <- Ref.make(Seq.empty[Int])
          _   <- ZIO.foreachPar_(as)(a => ref.update(_ :+ a))
          rs  <- ref.get
        } yield assert(rs)(hasSize(equalTo(as.length))) &&
          assert(rs.toSet)(equalTo(as.toSet))
      }
    ),
    suite("foreachParN")(
      testM("returns the list of results in the appropriate order") {
        val list = List(1, 2, 3)
        val res  = IO.foreachParN(2)(list)(x => IO.effectTotal(x.toString))
        assertM(res)(equalTo(List("1", "2", "3")))
      },
      testM("works on large lists") {
        val n   = 10
        val seq = List.range(0, 100000)
        val res = IO.foreachParN(n)(seq)(UIO.succeed)
        assertM(res)(equalTo(seq))
      },
      testM("runs effects in parallel") {
        val io = for {
          p <- Promise.make[Nothing, Unit]
          _ <- UIO.foreachParN(2)(List(UIO.never, p.succeed(())))(identity).fork
          _ <- p.await
        } yield true
        assertM(io)(isTrue)
      },
      testM("propagates error") {
        val ints = List(1, 2, 3, 4, 5, 6)
        val odds = ZIO.foreachParN(4)(ints) { n =>
          if (n % 2 != 0) ZIO.succeed(n) else ZIO.fail("not odd")
        }
        assertM(odds.either)(isLeft(equalTo("not odd")))
      },
      testM("interrupts effects on first failure") {
        val actions = List(
          ZIO.never,
          ZIO.succeed(1),
          ZIO.fail("C")
        )
        val io = ZIO.foreachParN(4)(actions)(a => a)
        assertM(io.either)(isLeft(equalTo("C")))
      }
    ),
    suite("foreachParN_")(
      testM("runs all effects") {
        val as = Seq(1, 2, 3, 4, 5)
        for {
          ref <- Ref.make(Seq.empty[Int])
          _   <- ZIO.foreachParN_(2)(as)(a => ref.update(_ :+ a))
          rs  <- ref.get
        } yield assert(rs)(hasSize(equalTo(as.length))) &&
          assert(rs.toSet)(equalTo(as.toSet))
      }
    ),
    suite("forkAll")(
      testM("returns the list of results in the same order") {
        val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
        val res  = IO.forkAll(list).flatMap[Any, Nothing, List[Int]](_.join)
        assertM(res)(equalTo(List(1, 2, 3)))
      },
      testM("happy-path") {
        val list = (1 to 1000).toList
        assertM(ZIO.forkAll(list.map(a => ZIO.effectTotal(a))).flatMap(_.join))(equalTo(list))
      },
      testM("empty input") {
        assertM(ZIO.forkAll(List.empty).flatMap(_.join))(equalTo(List.empty))
      },
      testM("propagate failures") {
        val boom = new Exception
        for {
          fiber  <- ZIO.forkAll(List(ZIO.fail(boom)))
          result <- fiber.join.flip
        } yield assert(result)(equalTo(boom))
      },
      testM("propagates defects") {
        val boom = new Exception("boom")
        for {
          fiber  <- ZIO.forkAll(List(ZIO.die(boom)))
          result <- fiber.join.sandbox.flip
        } yield assert(result)(equalTo(Cause.die(boom)))
      } @@ flaky
    ),
    suite("forkWithErrorHandler")(
      testM("calls provided function when task fails") {
        for {
          p <- Promise.make[Nothing, Unit]
          _ <- ZIO.fail(()).forkWithErrorHandler(p.succeed(_).unit)
          _ <- p.await
        } yield assertCompletes
      }
    ),
    suite("fromFutureInterrupt")(
      testM("running Future can be interrupted") {
        import java.util.concurrent.atomic.AtomicInteger

        import scala.concurrent.{ ExecutionContext, Future }
        def infiniteFuture(ref: AtomicInteger)(implicit ec: ExecutionContext): Future[Nothing] =
          Future(ref.getAndIncrement()).flatMap(_ => infiniteFuture(ref))
        for {
          ref   <- ZIO.effectTotal(new AtomicInteger(0))
          fiber <- ZIO.fromFutureInterrupt(ec => infiniteFuture(ref)(ec)).fork
          _     <- fiber.interrupt
          v1    <- ZIO.effectTotal(ref.get)
          _     <- Live.live(clock.sleep(10.milliseconds))
          v2    <- ZIO.effectTotal(ref.get)
        } yield assert(v1)(equalTo(v2))
      },
      testM("interruption blocks on interruption of the Future") {
        import scala.concurrent.Promise
        for {
          latch <- ZIO.effectTotal(Promise[Unit]())
          fiber <- ZIO.fromFutureInterrupt { _ =>
                    latch.success(()); Promise[Unit]().future
                  }.fork
          _      <- ZIO.fromFuture(_ => latch.future).orDie
          result <- Live.withLive(fiber.interrupt)(_.timeout(10.milliseconds))
        } yield assert(result)(isNone)
      }
    ),
    suite("head")(
      testM("on non empty list") {
        assertM(ZIO.succeed(List(1, 2, 3)).head.either)(isRight(equalTo(1)))
      },
      testM("on empty list") {
        assertM(ZIO.succeed(List.empty).head.either)(isLeft(isNone))
      },
      testM("on failure") {
        assertM(ZIO.fail("Fail").head.either)(isLeft(isSome(equalTo("Fail"))))
      }
    ),
    suite("ignore")(
      testM("return success as Unit") {
        assertM(ZIO.succeed(11).ignore)(equalTo(()))
      },
      testM("return failure as Unit") {
        assertM(ZIO.fail(123).ignore)(equalTo(()))
      },
      testM("not catch throwable") {
        assertM(ZIO.die(ExampleError).ignore.run)(dies(equalTo(ExampleError)))
      }
    ),
    suite("left")(
      testM("on Left value") {
        assertM(ZIO.succeed(Left("Left")).left)(equalTo("Left"))
      },
      testM("on Right value") {
        assertM(ZIO.succeed(Right("Right")).left.either)(isLeft(isNone))
      },
      testM("on failure") {
        assertM(ZIO.fail("Fail").left.either)(isLeft(isSome(equalTo("Fail"))))
      }
    ),
    suite("leftOrFail")(
      testM("on Left value") {
        assertM(UIO(Left(42)).leftOrFail(ExampleError))(equalTo(42))
      },
      testM("on Right value") {
        assertM(UIO(Right(12)).leftOrFail(ExampleError).flip)(equalTo(ExampleError))
      }
    ),
    suite("leftOrFailException")(
      testM("on Left value") {
        assertM(ZIO.succeed(Left(42)).leftOrFailException)(equalTo(42))
      },
      testM("on Right value") {
        assertM(ZIO.succeed(Right(2)).leftOrFailException.run)(fails(Assertion.anything))
      }
    ),
    suite("mapN")(
      testM("with Tuple2") {
        checkM(Gen.anyInt, Gen.alphaNumericString) { (int: Int, str: String) =>
          def f(i: Int, s: String): String = i.toString + s
          val actual                       = ZIO.mapN(ZIO.succeed(int), ZIO.succeed(str))(f)
          val expected                     = f(int, str)
          assertM(actual)(equalTo(expected))
        }
      },
      testM("with Tuple3") {
        checkM(Gen.anyInt, Gen.alphaNumericString, Gen.alphaNumericString) { (int: Int, str1: String, str2: String) =>
          def f(i: Int, s1: String, s2: String): String = i.toString + s1 + s2
          val actual                                    = ZIO.mapN(ZIO.succeed(int), ZIO.succeed(str1), ZIO.succeed(str2))(f)
          val expected                                  = f(int, str1, str2)
          assertM(actual)(equalTo(expected))
        }
      },
      testM("with Tuple4") {
        checkM(Gen.anyInt, Gen.alphaNumericString, Gen.alphaNumericString, Gen.alphaNumericString) {
          (int: Int, str1: String, str2: String, str3: String) =>
            def f(i: Int, s1: String, s2: String, s3: String): String = i.toString + s1 + s2 + s3
            val actual                                                = ZIO.mapN(ZIO.succeed(int), ZIO.succeed(str1), ZIO.succeed(str2), ZIO.succeed(str3))(f)
            val expected                                              = f(int, str1, str2, str3)
            assertM(actual)(equalTo(expected))
        }
      }
    ),
    suite("mapParN")(
      testM("with Tuple2") {
        checkM(Gen.anyInt, Gen.alphaNumericString) { (int: Int, str: String) =>
          def f(i: Int, s: String): String = i.toString + s
          val actual                       = ZIO.mapParN(ZIO.succeed(int), ZIO.succeed(str))(f)
          val expected                     = f(int, str)
          assertM(actual)(equalTo(expected))
        }
      },
      testM("with Tuple3") {
        checkM(Gen.anyInt, Gen.alphaNumericString, Gen.alphaNumericString) { (int: Int, str1: String, str2: String) =>
          def f(i: Int, s1: String, s2: String): String = i.toString + s1 + s2
          val actual                                    = ZIO.mapParN(ZIO.succeed(int), ZIO.succeed(str1), ZIO.succeed(str2))(f)
          val expected                                  = f(int, str1, str2)
          assertM(actual)(equalTo(expected))
        }
      },
      testM("with Tuple4") {
        checkM(Gen.anyInt, Gen.alphaNumericString, Gen.alphaNumericString, Gen.alphaNumericString) {
          (int: Int, str1: String, str2: String, str3: String) =>
            def f(i: Int, s1: String, s2: String, s3: String): String = i.toString + s1 + s2 + s3
            val actual                                                = ZIO.mapParN(ZIO.succeed(int), ZIO.succeed(str1), ZIO.succeed(str2), ZIO.succeed(str3))(f)
            val expected                                              = f(int, str1, str2, str3)
            assertM(actual)(equalTo(expected))
        }
      }
    ),
    suite("memoize")(
      testM("non-memoized returns new instances on repeated calls") {
        val io = random.nextString(10)
        (io <*> io)
          .map(tuple => assert(tuple._1)(not(equalTo(tuple._2))))
      },
      testM("memoized returns the same instance on repeated calls") {
        val ioMemo = random.nextString(10).memoize
        ioMemo
          .flatMap(io => io <*> io)
          .map(tuple => assert(tuple._1)(equalTo(tuple._2)))
      }
    ),
    suite("merge")(
      testM("on flipped result") {
        val zio: IO[Int, Int] = ZIO.succeed(1)

        for {
          a <- zio.merge
          b <- zio.flip.merge
        } yield assert(a)(equalTo(b))
      }
    ),
    suite("none")(
      testM("on Some fails with None") {
        val task: IO[Option[Throwable], Unit] = Task(Some(1)).none
        assertM(task.run)(fails(isNone))
      },
      testM("on None succeeds with ()") {
        val task: IO[Option[Throwable], Unit] = Task(None).none
        assertM(task)(isUnit)
      },
      testM("fails with Some(ex) when effect fails with ex") {
        val ex                                = new RuntimeException("Failed Task")
        val task: IO[Option[Throwable], Unit] = Task.fail(ex).none
        assertM(task.run)(fails(isSome(equalTo(ex))))
      }
    ),
    suite("option")(
      testM("return success in Some") {
        import zio.CanFail.canFail
        assertM(ZIO.succeed(11).option)(equalTo(Some(11)))
      },
      testM("return failure as None") {
        assertM(ZIO.fail(123).option)(equalTo(None))
      },
      testM("not catch throwable") {
        import zio.CanFail.canFail
        assertM(ZIO.die(ExampleError).option.run)(dies(equalTo(ExampleError)))
      },
      testM("catch throwable after sandboxing") {
        assertM(ZIO.die(ExampleError).sandbox.option)(equalTo(None))
      }
    ),
    suite("optional")(
      testM("fails when given Some error") {
        val task: IO[String, Option[Int]] = IO.fail(Some("Error")).optional
        assertM(task.run)(fails(equalTo("Error")))
      },
      testM("succeeds with None given None error") {
        val task: IO[String, Option[Int]] = IO.fail(None).optional
        assertM(task)(isNone)
      },
      testM("ucceeds with Some given a value") {
        val task: IO[String, Option[Int]] = IO.succeed(1).optional
        assertM(task)(isSome(equalTo(1)))
      }
    ),
    suite("orElse")(
      testM("does not recover from defects") {
        val ex      = new Exception("Died")
        val fiberId = Fiber.Id(0L, 123L)
        import zio.CanFail.canFail
        for {
          plain <- (ZIO.die(ex) <> IO.unit).run
          both  <- (ZIO.halt(Cause.Both(interrupt(fiberId), die(ex))) <> IO.unit).run
          thn   <- (ZIO.halt(Cause.Then(interrupt(fiberId), die(ex))) <> IO.unit).run
          fail  <- (ZIO.fail(ex) <> IO.unit).run
        } yield assert(plain)(dies(equalTo(ex))) &&
          assert(both)(dies(equalTo(ex))) &&
          assert(thn)(dies(equalTo(ex))) &&
          assert(fail)(succeeds(isUnit))
      },
      testM("left failed and right died with kept cause") {
        val z1                = Task.fail(new Throwable("1"))
        val z2: Task[Nothing] = Task.die(new Throwable("2"))
        val orElse: Task[Boolean] = z1.orElse(z2).catchAllCause {
          case Then(Die(FiberFailure(Traced(Fail(a: Throwable), _))), Traced(Die(b: Throwable), _)) =>
            Task(a.getMessage == "1" && b.getMessage == "2")
          case _ =>
            Task(false)
        }
        assertM(orElse)(equalTo(true))
      },
      testM("left failed and right failed with kept cause") {
        val z1                = Task.fail(new Throwable("1"))
        val z2: Task[Nothing] = Task.fail(new Throwable("2"))
        val orElse: Task[Boolean] = z1.orElse(z2).catchAllCause {
          case Then(Die(FiberFailure(Traced(Fail(a: Throwable), _))), Traced(Fail(b: Throwable), _)) =>
            Task(a.getMessage == "1" && b.getMessage == "2")
          case _ =>
            Task(false)
        }
        assertM(orElse)(equalTo(true))
      }
    ),
    suite("parallelErrors")(
      testM("oneFailure") {
        for {
          f1     <- IO.fail("error1").fork
          f2     <- IO.succeed("success1").fork
          errors <- f1.zip(f2).join.parallelErrors[String].flip
        } yield assert(errors)(equalTo(List("error1")))
      },
      testM("allFailures") {
        for {
          f1     <- IO.fail("error1").fork
          f2     <- IO.fail("error2").fork
          errors <- f1.zip(f2).join.parallelErrors[String].flip
        } yield assert(errors)(
          equalTo(List("error1", "error2")) ||
            equalTo(List("error1")) ||
            equalTo(List("error2"))
        )
      } @@ nonFlaky
    ),
    suite("partitionM")(
      testM("collects only successes") {
        import zio.CanFail.canFail
        val in = List.range(0, 10)
        for {
          res <- ZIO.partitionM(in)(a => ZIO.succeed(a))
        } yield assert(res._1)(isEmpty) && assert(res._2)(equalTo(in))
      },
      testM("collects only failures") {
        val in = List.fill(10)(0)
        for {
          res <- ZIO.partitionM(in)(a => ZIO.fail(a))
        } yield assert(res._1)(equalTo(in)) && assert(res._2)(isEmpty)
      },
      testM("collects failures and successes") {
        val in = List.range(0, 10)
        for {
          res <- ZIO.partitionM(in)(a => if (a % 2 == 0) ZIO.fail(a) else ZIO.succeed(a))
        } yield assert(res._1)(equalTo(List(0, 2, 4, 6, 8))) && assert(res._2)(equalTo(List(1, 3, 5, 7, 9)))
      }
    ),
    suite("partitionMPar")(
      testM("collects a lot of successes") {
        import zio.CanFail.canFail
        val in = List.range(0, 1000)
        for {
          res <- ZIO.partitionMPar(in)(a => ZIO.succeed(a))
        } yield assert(res._1)(isEmpty) && assert(res._2)(equalTo(in))
      },
      testM("collects failures") {
        val in = List.fill(10)(0)
        for {
          res <- ZIO.partitionMPar(in)(a => ZIO.fail(a))
        } yield assert(res._1)(equalTo(in)) && assert(res._2)(isEmpty)
      },
      testM("collects failures and successes") {
        val in = List.range(0, 10)
        for {
          res <- ZIO.partitionMPar(in)(a => if (a % 2 == 0) ZIO.fail(a) else ZIO.succeed(a))
        } yield assert(res._1)(equalTo(List(0, 2, 4, 6, 8))) && assert(res._2)(equalTo(List(1, 3, 5, 7, 9)))
      }
    ),
    suite("partitionMParN")(
      testM("collects a lot of successes") {
        import zio.CanFail.canFail
        val in = List.range(0, 1000)
        for {
          res <- ZIO.partitionMParN(3)(in)(a => ZIO.succeed(a))
        } yield assert(res._1)(isEmpty) && assert(res._2)(equalTo(in))
      },
      testM("collects failures") {
        val in = List.fill(10)(0)
        for {
          res <- ZIO.partitionMParN(3)(in)(a => ZIO.fail(a))
        } yield assert(res._1)(equalTo(in)) && assert(res._2)(isEmpty)
      },
      testM("collects failures and successes") {
        val in = List.range(0, 10)
        for {
          res <- ZIO.partitionMParN(3)(in)(a => if (a % 2 == 0) ZIO.fail(a) else ZIO.succeed(a))
        } yield assert(res._1)(equalTo(List(0, 2, 4, 6, 8))) && assert(res._2)(equalTo(List(1, 3, 5, 7, 9)))
      }
    ),
    suite("raceAll")(
      testM("returns first success") {
        assertM(ZIO.fail("Fail").raceAll(List(IO.succeed(24))))(equalTo(24))
      },
      testM("returns last failure") {
        assertM(live(ZIO.sleep(100.millis) *> ZIO.fail(24)).raceAll(List(ZIO.fail(25))).flip)(equalTo(24))
      } @@ flaky,
      testM("returns success when it happens after failure") {
        assertM(ZIO.fail(42).raceAll(List(IO.succeed(24) <* live(ZIO.sleep(100.millis)))))(equalTo(24))
      }
    ),
    suite("replicate")(
      testM("zero") {
        val lst: Iterable[UIO[Int]] = ZIO.replicate(0)(ZIO.succeed(12))
        assertM(ZIO.sequence(lst))(equalTo(List.empty))
      },
      testM("negative") {
        val anotherList: Iterable[UIO[Int]] = ZIO.replicate(-2)(ZIO.succeed(12))
        assertM(ZIO.sequence(anotherList))(equalTo(List.empty))
      },
      testM("positive") {
        val lst: Iterable[UIO[Int]] = ZIO.replicate(2)(ZIO.succeed(12))
        assertM(ZIO.sequence(lst))(equalTo(List(12, 12)))
      }
    ),
    suite("retryUntil")(
      testM("retryUntil retries until condition is true") {
        for {
          in     <- Ref.make(10)
          out    <- Ref.make(0)
          _      <- (in.update(_ - 1) <* out.update(_ + 1)).flipWith(_.retryUntil(_ == 0))
          result <- out.get
        } yield assert(result)(equalTo(10))
      },
      testM("retryUntil doesn't retry when condition is true") {
        for {
          ref    <- Ref.make(0)
          _      <- ref.update(_ + 1).flipWith(_.doUntil(_ => true))
          result <- ref.get
        } yield assert(result)(equalTo(1))
      }
    ),
    suite("retryWhile")(
      testM("retryWhile retries while condition is true") {
        for {
          in     <- Ref.make(10)
          out    <- Ref.make(0)
          _      <- (in.update(_ - 1) <* out.update(_ + 1)).flipWith(_.retryWhile(_ >= 0))
          result <- out.get
        } yield assert(result)(equalTo(11))
      },
      testM("retryWhile doesn't retry when condition is false") {
        for {
          ref    <- Ref.make(0)
          _      <- ref.update(_ + 1).flipWith(_.retryWhile(_ => false))
          result <- ref.get
        } yield assert(result)(equalTo(1))
      }
    ),
    suite("right")(
      testM("on Right value") {
        assertM(ZIO.succeed(Right("Right")).right)(equalTo("Right"))
      },
      testM("on Left value") {
        assertM(ZIO.succeed(Left("Left")).right.either)(isLeft(isNone))
      },
      testM("on failure") {
        assertM(ZIO.fail("Fail").right.either)(isLeft(isSome(equalTo("Fail"))))
      }
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
        assertM(result)(isLeft(equalTo(expected)))
      } @@ scala2Only
    ),
    suite("rightOrFail")(
      testM("on Right value") {
        assertM(UIO(Right(42)).rightOrFail(ExampleError))(equalTo(42))
      },
      testM("on Left value") {
        assertM(UIO(Left(1)).rightOrFail(ExampleError).flip)(equalTo(ExampleError))
      }
    ),
    suite("rightOrFailException")(
      testM("on Right value") {
        assertM(ZIO.succeed(Right(42)).rightOrFailException)(equalTo(42))
      },
      testM("on Left value") {
        assertM(ZIO.succeed(Left(2)).rightOrFailException.run)(fails(Assertion.anything))
      }
    ),
    suite("some")(
      testM("extracts the value from Some") {
        val task: IO[Option[Throwable], Int] = Task(Some(1)).some
        assertM(task)(equalTo(1))
      },
      testM("fails on None") {
        val task: IO[Option[Throwable], Int] = Task(None).some
        assertM(task.run)(fails(isNone))
      },
      testM("fails when given an exception") {
        val ex                               = new RuntimeException("Failed Task")
        val task: IO[Option[Throwable], Int] = Task.fail(ex).some
        assertM(task.run)(fails(isSome(equalTo(ex))))
      }
    ),
    suite("someOrFailException")(
      testM("extracts the optional value") {
        assertM(ZIO.succeed(Some(42)).someOrFailException)(equalTo(42))
      },
      testM("fails when given a None") {
        val task = ZIO.succeed(Option.empty[Int]).someOrFailException
        assertM(task.run)(fails(isSubtype[NoSuchElementException](anything)))
      },
      suite("without another error type")(
        testM("succeed something") {
          assertM(ZIO.succeed(Option(3)).someOrFailException)(equalTo(3))
        },
        testM("succeed nothing") {
          assertM(ZIO.succeed(None: Option[Int]).someOrFailException.run)(fails(Assertion.anything))
        }
      ),
      suite("with throwable as base error type")(
        testM("return something") {
          assertM(Task(Option(3)).someOrFailException)(equalTo(3))
        }
      ),
      suite("with exception as base error type")(
        testM("return something") {
          assertM((ZIO.succeed(Option(3)): IO[Exception, Option[Int]]).someOrFailException)(equalTo(3))
        }
      )
    ),
    suite("reject")(
      testM("returns failure ignoring value") {
        val goodCase =
          exactlyOnce(0)(_.reject({ case v if v != 0 => "Partial failed!" })).sandbox.either

        val badCase =
          exactlyOnce(1)(_.reject({ case v if v != 0 => "Partial failed!" })).sandbox.either
            .map(_.left.map(_.failureOrCause))

        assertM(goodCase)(isRight(equalTo(0))) &&
        assertM(badCase)(isLeft(isLeft(equalTo("Partial failed!"))))
      }
    ),
    suite("rejectM")(
      testM("Check `rejectM` returns failure ignoring value") {
        val goodCase =
          exactlyOnce(0)(_.rejectM[Any, String]({ case v if v != 0 => ZIO.succeed("Partial failed!") })).sandbox.either

        val partialBadCase =
          exactlyOnce(1)(_.rejectM({ case v if v != 0 => ZIO.fail("Partial failed!") })).sandbox.either
            .map(_.left.map(_.failureOrCause))

        val badCase =
          exactlyOnce(1)(_.rejectM({ case v if v != 0 => ZIO.fail("Partial failed!") })).sandbox.either
            .map(_.left.map(_.failureOrCause))

        assertM(goodCase)(isRight(equalTo(0))) &&
        assertM(partialBadCase)(isLeft(isLeft(equalTo("Partial failed!")))) &&
        assertM(badCase)(isLeft(isLeft(equalTo("Partial failed!"))))
      }
    ),
    suite("RTS synchronous correctness")(
      testM("widen Nothing") {
        val op1 = IO.effectTotal[String]("1")
        val op2 = IO.effectTotal[String]("2")

        assertM(op1.zipWith(op2)(_ + _))(equalTo("12"))
      },
      testM("now must be eager") {
        val io =
          try {
            IO.succeed(throw ExampleError)
            IO.succeed(false)
          } catch {
            case _: Throwable => IO.succeed(true)
          }

        assertM(io)(isTrue)
      },
      testM("effectSuspend must be lazy") {
        val io =
          try {
            IO.effectSuspend(throw ExampleError)
            IO.succeed(false)
          } catch {
            case _: Throwable => IO.succeed(true)
          }

        assertM(io)(isFalse)
      },
      testM("effectSuspendTotal must not catch throwable") {
        val io = ZIO.effectSuspendTotal[Any, Nothing, Any](throw ExampleError).sandbox.either
        assertM(io)(isLeft(equalTo(Cause.die(ExampleError))))
      },
      testM("effectSuspend must catch throwable") {
        val io = ZIO.effectSuspend[Any, Nothing](throw ExampleError).either
        assertM(io)(isLeft(equalTo(ExampleError)))
      },
      testM("effectSuspendWith must catch throwable") {
        val io = ZIO.effectSuspendWith[Any, Nothing]((_, _) => throw ExampleError).either
        assertM(io)(isLeft(equalTo(ExampleError)))
      },
      testM("effectSuspendTotal must be evaluatable") {
        assertM(IO.effectSuspendTotal(IO.effectTotal(42)))(equalTo(42))
      },
      testM("point, bind, map") {
        def fibIo(n: Int): Task[BigInt] =
          if (n <= 1) IO.succeed(n)
          else
            for {
              a <- fibIo(n - 1)
              b <- fibIo(n - 2)
            } yield a + b

        assertM(fibIo(10))(equalTo(fib(10)))
      },
      testM("effect, bind, map") {
        def fibIo(n: Int): Task[BigInt] =
          if (n <= 1) IO.effect(n)
          else
            for {
              a <- fibIo(n - 1)
              b <- fibIo(n - 2)
            } yield a + b

        assertM(fibIo(10))(equalTo(fib(10)))
      },
      testM("effect, bind, map, redeem") {
        def fibIo(n: Int): Task[BigInt] =
          if (n <= 1) Task.effect[BigInt](throw ExampleError).catchAll(_ => Task.effect(n))
          else
            for {
              a <- fibIo(n - 1)
              b <- fibIo(n - 2)
            } yield a + b

        assertM(fibIo(10))(equalTo(fib(10)))
      },
      testM("sync effect") {
        def sumIo(n: Int): Task[Int] =
          if (n <= 0) IO.effectTotal(0)
          else IO.effectTotal(n).flatMap(b => sumIo(n - 1).map(a => a + b))

        assertM(sumIo(1000))(equalTo(sum(1000)))
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

        assertM(l.zipWith(r)(_ && _))(isTrue)
      },
      testM("flip must make error into value") {
        val io = IO.fail(ExampleError).flip
        assertM(io)(equalTo(ExampleError))
      },
      testM("flip must make value into error") {
        val io = IO.succeed(42).flip
        assertM(io.either)(isLeft(equalTo(42)))
      },
      testM("flipping twice returns identical value") {
        val io = IO.succeed(42)
        assertM(io.flip.flip)(equalTo(42))
      }
    ),
    suite("RTS failure")(
      testM("error in sync effect") {
        val io = IO.effect[Unit](throw ExampleError).fold[Option[Throwable]](Some(_), _ => None)
        assertM(io)(isSome(equalTo(ExampleError)))
      },
      testM("attempt . fail") {
        val io1 = TaskExampleError.either
        val io2 = IO.effectSuspendTotal(IO.effectSuspendTotal(TaskExampleError).either)

        io1.zipWith(io2) {
          case (r1, r2) =>
            assert(r1)(isLeft(equalTo(ExampleError))) && assert(r2)(isLeft(equalTo(ExampleError)))
        }
      },
      testM("deep attempt sync effect error") {
        assertM(deepErrorEffect(100).either)(isLeft(equalTo(ExampleError)))
      },
      testM("deep attempt fail error") {
        assertM(deepErrorFail(100).either)(isLeft(equalTo(ExampleError)))
      },
      testM("attempt . sandbox . terminate") {
        val io = IO.effectTotal[Int](throw ExampleError).sandbox.either
        assertM(io)(isLeft(equalTo(Cause.die(ExampleError))))
      },
      testM("fold . sandbox . terminate") {
        val io = IO.effectTotal[Int](throw ExampleError).sandbox.fold(Some(_), Function.const(None))
        assertM(io)(isSome(equalTo(Cause.die(ExampleError))))
      },
      testM("catch sandbox terminate") {
        val io = IO.effectTotal(throw ExampleError).sandbox.merge
        assertM(io)(equalTo(Cause.die(ExampleError)))
      },
      testM("uncaught fail") {
        assertM(TaskExampleError.run)(fails(equalTo(ExampleError)))
      },
      testM("uncaught sync effect error") {
        val io = IO.effectTotal[Int](throw ExampleError)
        assertM(io.run)(dies(equalTo(ExampleError)))
      },
      testM("deep uncaught sync effect error") {
        assertM(deepErrorEffect(100).run)(fails(equalTo(ExampleError)))
      },
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

        assertM(io.run)(equalTo(Exit.halt(expectedCause)))
      },
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

        assertM(io.run)(equalTo(Exit.halt(expectedCause)))
      },
      testM("run preserves interruption status") {
        for {
          p    <- Promise.make[Nothing, Unit]
          f    <- (p.succeed(()) *> IO.never).run.fork
          _    <- p.await
          _    <- f.interrupt
          test <- f.await.map(_.interrupted)
        } yield assert(test)(isTrue)
      },
      testM("run swallows inner interruption") {
        for {
          p   <- Promise.make[Nothing, Int]
          _   <- IO.interrupt.run *> p.succeed(42)
          res <- p.await
        } yield assert(res)(equalTo(42))
      },
      testM("timeout a long computation") {
        val io = (clock.sleep(5.seconds) *> IO.succeed(true)).timeout(10.millis)
        assertM(io.provide(Clock.Live))(isNone)
      },
      testM("catchAllCause") {
        val io =
          for {
            _ <- ZIO.succeed(42)
            f <- ZIO.fail("Uh oh!")
          } yield f

        assertM(io.catchAllCause(ZIO.succeed))(equalTo(Cause.fail("Uh oh!")))
      },
      testM("exception in fromFuture does not kill fiber") {
        val io = ZIO.fromFuture(_ => throw ExampleError).either
        assertM(io)(isLeft(equalTo(ExampleError)))
      }
    ),
    suite("RTS finalizers")(
      testM("fail ensuring") {
        var finalized = false

        val io = Task.fail(ExampleError).ensuring(IO.effectTotal { finalized = true; () })

        for {
          a1 <- assertM(io.run)(fails(equalTo(ExampleError)))
          a2 = assert(finalized)(isTrue)
        } yield a1 && a2
      },
      testM("fail on error") {
        @volatile var finalized = false

        val cleanup: Cause[Throwable] => UIO[Unit] =
          _ => IO.effectTotal[Unit] { finalized = true; () }

        val io = Task.fail(ExampleError).onError(cleanup)

        for {
          a1 <- assertM(io.run)(fails(equalTo(ExampleError)))
          a2 = assert(finalized)(isTrue)
        } yield a1 && a2
      },
      testM("finalizer errors not caught") {
        val e2 = new Error("e2")
        val e3 = new Error("e3")

        val io = TaskExampleError.ensuring(IO.die(e2)).ensuring(IO.die(e3))

        val expectedCause: Cause[Throwable] =
          Cause.Then(Cause.fail(ExampleError), Cause.Then(Cause.die(e2), Cause.die(e3)))

        assertM(io.sandbox.flip)(equalTo(expectedCause))
      },
      testM("finalizer errors reported") {
        @volatile var reported: Exit[Nothing, Int] = null

        val io = IO
          .succeed[Int](42)
          .ensuring(IO.die(ExampleError))
          .fork
          .flatMap(_.await.flatMap[Any, Nothing, Any](e => UIO.effectTotal { reported = e }))

        for {
          a1 <- assertM(io)(anything)
          a2 = assert(reported.succeeded)(isFalse)
        } yield a1 && a2
      },
      testM("bracket exit is usage result") {
        val io = IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.succeed[Int](42))
        assertM(io)(equalTo(42))
      },
      testM("error in just acquisition") {
        val io = IO.bracket(TaskExampleError)(_ => IO.unit)(_ => IO.unit)
        assertM(io.run)(fails(equalTo(ExampleError)))
      },
      testM("error in just release") {
        val io = IO.bracket(IO.unit)(_ => IO.die(ExampleError))(_ => IO.unit)
        assertM(io.run)(dies(equalTo(ExampleError)))
      },
      testM("error in just usage") {
        val io = IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.fail(ExampleError))
        assertM(io.run)(fails(equalTo(ExampleError)))
      },
      testM("rethrown caught error in acquisition") {
        val io = IO.absolve(IO.bracket(TaskExampleError)(_ => IO.unit)(_ => IO.unit).either)
        assertM(io.flip)(equalTo(ExampleError))
      },
      testM("rethrown caught error in release") {
        val io = IO.bracket(IO.unit)(_ => IO.die(ExampleError))(_ => IO.unit)
        assertM(io.run)(dies(equalTo(ExampleError)))
      },
      testM("rethrown caught error in usage") {
        val io = IO.absolve(IO.unit.bracket_(IO.unit)(TaskExampleError).either)
        assertM(io.run)(fails(equalTo(ExampleError)))
      },
      testM("test eval of async fail") {
        val io1 = IO.unit.bracket_(AsyncUnit[Nothing])(asyncExampleError[Unit])
        val io2 = AsyncUnit[Throwable].bracket_(IO.unit)(asyncExampleError[Unit])

        for {
          a1 <- assertM(io1.run)(fails(equalTo(ExampleError)))
          a2 <- assertM(io2.run)(fails(equalTo(ExampleError)))
          a3 <- assertM(IO.absolve(io1.either).run)(fails(equalTo(ExampleError)))
          a4 <- assertM(IO.absolve(io2.either).run)(fails(equalTo(ExampleError)))
        } yield a1 && a2 && a3 && a4
      },
      testM("bracket regression 1") {
        def makeLogger: Ref[List[String]] => String => UIO[Unit] =
          (ref: Ref[List[String]]) => (line: String) => ref.update(_ ::: List(line)).unit

        val io =
          for {
            ref <- Ref.make[List[String]](Nil)
            log = makeLogger(ref)
            f <- ZIO
                  .bracket(
                    ZIO.bracket(ZIO.unit)(_ => log("start 1") *> clock.sleep(10.millis) *> log("release 1"))(
                      _ => ZIO.unit
                    )
                  )(_ => log("start 2") *> clock.sleep(10.millis) *> log("release 2"))(_ => ZIO.unit)
                  .fork
            _ <- (ref.get <* clock.sleep(1.millis)).repeat(Schedule.doUntil[List[String]](_.contains("start 1")))
            _ <- f.interrupt
            _ <- (ref.get <* clock.sleep(1.millis)).repeat(Schedule.doUntil[List[String]](_.contains("release 2")))
            l <- ref.get
          } yield l

        assertM(io.provide(Clock.Live))(hasSameElements(List("start 1", "release 1", "start 2", "release 2")))
      },
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

        assertM(io.provide(Clock.Live))(isTrue)
      }
    ),
    suite("RTS synchronous stack safety")(
      testM("deep map of now") {
        assertM(deepMapNow(10000))(equalTo(10000))
      },
      testM("deep map of sync effect") {
        assertM(deepMapEffect(10000))(equalTo(10000))
      },
      testM("deep attempt") {
        val io = (0 until 10000).foldLeft(IO.effect(())) { (acc, _) =>
          acc.either.unit
        }
        assertM(io)(equalTo(()))
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

        assertM(fib(1000))(equalTo(expected))
      },
      testM("deep absolve/attempt is identity") {
        import zio.CanFail.canFail
        val io = (0 until 1000).foldLeft(IO.succeed(42)) { (acc, _) =>
          IO.absolve(acc.either)
        }

        assertM(io)(equalTo(42))
      },
      testM("deep async absolve/attempt is identity") {
        val io = (0 until 1000).foldLeft(IO.effectAsync[Int, Int](k => k(IO.succeed(42)))) { (acc, _) =>
          IO.absolve(acc.either)
        }

        assertM(io)(equalTo(42))
      }
    ),
    suite("RTS asynchronous correctness")(
      testM("simple async must return") {
        val io = IO.effectAsync[Throwable, Int](k => k(IO.succeed(42)))
        assertM(io)(equalTo(42))
      },
      testM("simple asyncIO must return") {
        val io = IO.effectAsyncM[Throwable, Int](k => IO.effectTotal(k(IO.succeed(42))))
        assertM(io)(equalTo(42))
      },
      testM("deep asyncIO doesn't block threads") {
        def stackIOs(clock: Clock.Service[Any], count: Int): UIO[Int] =
          if (count <= 0) IO.succeed(42)
          else asyncIO(clock, stackIOs(clock, count - 1))

        def asyncIO(clock: Clock.Service[Any], cont: UIO[Int]): UIO[Int] =
          IO.effectAsyncM[Nothing, Int] { k =>
            clock.sleep(5.millis) *> cont *> IO.effectTotal(k(IO.succeed(42)))
          }

        val procNum = java.lang.Runtime.getRuntime.availableProcessors()

        val io = clock.clockService.flatMap(stackIOs(_, procNum + 1))

        assertM(io.provide(Clock.Live))(equalTo(42))
      },
      testM("interrupt of asyncPure register") {
        for {
          release <- Promise.make[Nothing, Unit]
          acquire <- Promise.make[Nothing, Unit]
          fiber <- IO
                    .effectAsyncM[Nothing, Unit] { _ =>
                      acquire.succeed(()).bracket(_ => release.succeed(()))(_ => IO.never)
                    }
                    .fork
          _ <- acquire.await
          _ <- fiber.interrupt.fork
          a <- release.await
        } yield assert(a)(isUnit)
      },
      testM("effectAsync should not resume fiber twice after interruption") {
        for {
          step            <- Promise.make[Nothing, Unit]
          unexpectedPlace <- Ref.make(List.empty[Int])
          runtime         <- ZIO.runtime[Live[Clock]]
          fork <- ZIO
                   .effectAsync[Any, Nothing, Unit] { k =>
                     runtime.unsafeRunAsync_ {
                       step.await *> ZIO.effectTotal(k(unexpectedPlace.update(1 :: _).unit))
                     }
                   }
                   .ensuring(ZIO.effectAsync[Any, Nothing, Unit] { _ =>
                     runtime.unsafeRunAsync_ {
                       step.succeed(())
                     }
                   //never complete
                   })
                   .ensuring(unexpectedPlace.update(2 :: _))
                   .fork
          result     <- withLive(fork.interrupt)(_.timeout(5.seconds))
          unexpected <- unexpectedPlace.get
        } yield {
          assert(unexpected)(isEmpty) &&
          assert(result)(isNone) // timeout happens
        }
      } @@ flaky,
      testM("effectAsyncMaybe should not resume fiber twice after synchronous result") {
        for {
          step            <- Promise.make[Nothing, Unit]
          unexpectedPlace <- Ref.make(List.empty[Int])
          runtime         <- ZIO.runtime[Live[Clock]]
          fork <- ZIO
                   .effectAsyncMaybe[Any, Nothing, Unit] { k =>
                     runtime.unsafeRunAsync_ {
                       step.await *> ZIO.effectTotal(k(unexpectedPlace.update(1 :: _).unit))
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
                   .fork
          result     <- withLive(fork.interrupt)(_.timeout(5.seconds))
          unexpected <- unexpectedPlace.get
        } yield {
          assert(unexpected)(isEmpty) &&
          assert(result)(isNone) // timeout happens
        }
      } @@ flaky,
      testM("sleep 0 must return") {
        assertM(clock.sleep(1.nanos).provide(Clock.Live))(isUnit)
      },
      testM("shallow bind of async chain") {
        val io = (0 until 10).foldLeft[Task[Int]](IO.succeed[Int](0)) { (acc, _) =>
          acc.flatMap(n => IO.effectAsync[Throwable, Int](_(IO.succeed(n + 1))))
        }

        assertM(io)(equalTo(10))
      },
      testM("effectAsyncM can fail before registering") {
        val zio = ZIO
          .effectAsyncM[Any, String, Nothing](_ => ZIO.fail("Ouch"))
          .flip

        assertM(zio)(equalTo("Ouch"))
      },
      testM("effectAsyncM can defect before registering") {
        val zio = ZIO
          .effectAsyncM[Any, String, Unit](_ => ZIO.effectTotal(throw new Error("Ouch")))
          .run
          .map(_.fold(_.defects.headOption.map(_.getMessage), _ => None))

        assertM(zio)(isSome(equalTo("Ouch")))
      }
    ),
    suite("RTS concurrency correctness")(
      testM("shallow fork/join identity") {
        for {
          f <- IO.succeed(42).fork
          r <- f.join
        } yield assert(r)(equalTo(42))
      },
      testM("deep fork/join identity") {
        val n = 20
        assertM(concurrentFib(n))(equalTo(fib(n)))
      },
      testM("asyncPure creation is interruptible") {
        for {
          release <- Promise.make[Nothing, Int]
          acquire <- Promise.make[Nothing, Unit]
          task = IO.effectAsyncM[Nothing, Unit] { _ =>
            IO.bracket(acquire.succeed(()))(_ => release.succeed(42).unit)(_ => IO.never)
          }
          fiber <- task.fork
          _     <- acquire.await
          _     <- fiber.interrupt
          a     <- release.await
        } yield assert(a)(equalTo(42))
      },
      testM("asyncInterrupt runs cancel token on interrupt") {
        for {
          release <- Promise.make[Nothing, Int]
          latch   = scala.concurrent.Promise[Unit]()
          async = IO.effectAsyncInterrupt[Nothing, Nothing] { _ =>
            latch.success(()); Left(release.succeed(42).unit)
          }
          fiber <- async.fork
          _ <- IO.effectAsync[Throwable, Unit] { k =>
                latch.future.onComplete {
                  case Success(a) => k(IO.succeed(a))
                  case Failure(t) => k(IO.fail(t))
                }(scala.concurrent.ExecutionContext.global)
              }
          _      <- fiber.interrupt
          result <- release.await
        } yield assert(result)(equalTo(42))
      },
      testM("daemon fiber is unsupervised") {
        for {
          ref  <- Ref.make(Option.empty[Fiber[Any, Any]])
          _    <- withLatch(release => (release *> UIO.never).fork.daemon.tap(fiber => ref.set(Some(fiber))))
          fibs <- ZIO.children
        } yield assert(fibs)(isEmpty)
      },
      testM("daemon fiber race interruption") {
        def plus1(ref: Ref[Int], latch: Promise[Nothing, Unit]) =
          latch.succeed(()) *> ZIO.never *> ref.update(_ + 1)
        def interruptHandler(ref: Ref[Int]) =
          ref.update(_ + 1)

        val io = for {
          ref             <- Ref.make(0)
          interruptionRef <- Ref.make(0)
          latch1Start     <- Promise.make[Nothing, Unit]
          latch2Start     <- Promise.make[Nothing, Unit]
          fiber <- plus1(ref, latch1Start)
                    .onInterrupt(interruptHandler(interruptionRef))
                    .race(plus1(ref, latch2Start).onInterrupt(interruptHandler(interruptionRef)))
                    .fork
          _           <- latch1Start.await
          _           <- latch2Start.await
          _           <- fiber.interrupt
          res         <- ref.get
          interrupted <- interruptionRef.get
        } yield assert(interrupted)(equalTo(2)) && assert(res)(equalTo(0))

        io.daemon
      },
      testM("daemon mask") {
        def forkAwait =
          for {
            latch    <- Promise.make[Nothing, Unit]
            latchEnd <- Promise.make[Nothing, Unit]
            _        <- latchEnd.await.fork *> latch.succeed(())
          } yield (latch, latchEnd)

        def handleLatch(latches: (Promise[Nothing, Unit], Promise[Nothing, Unit])) =
          latches._1.await.as(latches._2)

        val io = for {
          latches1    <- forkAwait
          (l1, l1End) = latches1
          _           <- l1.await
          children1   <- ZIO.children
          latchEnds <- ZIO.daemonMask { restore =>
                        for {
                          latches1 <- ZIO.sequence(
                                       List(
                                         forkAwait.flatMap(handleLatch),
                                         forkAwait.flatMap(handleLatch),
                                         forkAwait.flatMap(handleLatch)
                                       )
                                     )
                          latches2 <- restore(
                                       ZIO.sequence(
                                         List(
                                           forkAwait.flatMap(handleLatch),
                                           forkAwait.flatMap(handleLatch)
                                         )
                                       )
                                     )
                        } yield latches1 ++ latches2
                      }
          children2 <- ZIO.children
          _         <- l1End.succeed(())
          _         <- ZIO.traverse_(latchEnds)(_.succeed(()))
        } yield assert(children1.size)(equalTo(1)) && assert(children2.size)(equalTo(3))

        io.nonDaemon
      },
      testM("nonDaemon mask") {
        def forkAwait =
          for {
            latch    <- Promise.make[Nothing, Unit]
            latchEnd <- Promise.make[Nothing, Unit]
            _        <- latchEnd.await.fork *> latch.succeed(())
          } yield (latch, latchEnd)

        def handleLatch(latches: (Promise[Nothing, Unit], Promise[Nothing, Unit])) =
          latches._1.await.as(latches._2)

        val io =
          for {
            latches     <- forkAwait
            (l1, l1End) = latches
            _           <- l1.await
            children1   <- ZIO.children
            childrenWithLatches <- ZIO.nonDaemonMask { restore =>
                                    for {
                                      latches1 <- ZIO.sequence(
                                                   List(
                                                     forkAwait.flatMap(handleLatch),
                                                     forkAwait.flatMap(handleLatch),
                                                     forkAwait.flatMap(handleLatch)
                                                   )
                                                 )
                                      latches2 <- restore(
                                                   ZIO.sequence(
                                                     List(
                                                       forkAwait.flatMap(handleLatch),
                                                       forkAwait.flatMap(handleLatch)
                                                     )
                                                   )
                                                 )
                                      children2 <- ZIO.children
                                    } yield (children2, latches1 ++ latches2)
                                  }
            (children2, latchEnds) = childrenWithLatches
            latches2               <- forkAwait
            (l2, l2End)            = latches2
            _                      <- l2.await
            children3              <- ZIO.children
            _                      <- l1End.succeed(())
            _                      <- l2End.succeed(())
            _                      <- ZIO.traverse_(latchEnds)(_.succeed(()))
          } yield assert(children1.size)(equalTo(0)) && assert(children2.size)(equalTo(3)) && assert(children3.size)(
            equalTo(3)
          )

        io.daemon
      },
      testM("race in daemon is executed") {
        for {
          latch1 <- Promise.make[Nothing, Unit]
          latch2 <- Promise.make[Nothing, Unit]
          p1     <- Promise.make[Nothing, Unit]
          p2     <- Promise.make[Nothing, Unit]
          loser1 = ZIO.bracket(latch1.succeed(()))(_ => p1.succeed(()))(_ => ZIO.never)
          loser2 = ZIO.bracket(latch2.succeed(()))(_ => p2.succeed(()))(_ => ZIO.never)
          fiber  <- (loser1 race loser2).fork.daemon
          _      <- latch1.await
          _      <- latch2.await
          _      <- fiber.interrupt
          res1   <- p1.await
          res2   <- p2.await
        } yield assert(res1)(isUnit) && assert(res2)(isUnit)
      },
      testM("supervise fibers") {
        def makeChild(n: Int): URIO[Clock, Fiber[Nothing, Unit]] =
          (clock.sleep(20.millis * n.toDouble) *> IO.never).fork

        val io =
          for {
            counter <- Ref.make(0)
            _ <- (makeChild(1) *> makeChild(2)).handleChildrenWith { fs =>
                  fs.foldLeft(IO.unit)((acc, f) => acc *> f.interrupt.ignore *> counter.update(_ + 1).unit)
                }
            value <- counter.get
          } yield value

        assertM(io.provide(Clock.Live))(equalTo(2))
      } @@ nonFlaky(100),
      testM("race of fail with success") {
        val io = IO.fail(42).race(IO.succeed(24)).either
        assertM(io)(isRight(equalTo(24)))
      },
      testM("race of terminate with success") {
        val io = IO.die(new Throwable {}).race(IO.succeed(24))
        assertM(io)(equalTo(24))
      },
      testM("race of fail with fail") {
        val io = IO.fail(42).race(IO.fail(42)).either
        assertM(io)(isLeft(equalTo(42)))
      },
      testM("race of value & never") {
        val io = IO.effectTotal(42).race(IO.never)
        assertM(io)(equalTo(42))
      },
      testM("firstSuccessOf of values") {
        val io = IO.firstSuccessOf(IO.fail(0), List(IO.succeed(100))).either
        assertM(io)(isRight(equalTo(100)))
      },
      testM("firstSuccessOf of failures") {
        val io = ZIO.firstSuccessOf(IO.fail(0).delay(10.millis), List(IO.fail(101))).either
        assertM(io.provide(Clock.Live))(isLeft(equalTo(101)))
      },
      testM("firstSuccessOF of failures & 1 success") {
        val io = ZIO.firstSuccessOf(IO.fail(0), List(IO.succeed(102).delay(1.millis))).either
        assertM(io.provide(Clock.Live))(isRight(equalTo(102)))
      },
      testM("raceAttempt interrupts loser on success") {
        for {
          s      <- Promise.make[Nothing, Unit]
          effect <- Promise.make[Nothing, Int]
          winner = s.await *> IO.fromEither(Right(()))
          loser  = IO.bracket(s.succeed(()))(_ => effect.succeed(42))(_ => IO.never)
          race   = winner raceAttempt loser
          _      <- race
          b      <- effect.await
        } yield assert(b)(equalTo(42))
      },
      testM("raceAttempt interrupts loser on failure") {
        for {
          s      <- Promise.make[Nothing, Unit]
          effect <- Promise.make[Nothing, Int]
          winner = s.await *> IO.fromEither(Left(new Exception))
          loser  = IO.bracket(s.succeed(()))(_ => effect.succeed(42))(_ => IO.never)
          race   = winner raceAttempt loser
          _      <- race.either
          b      <- effect.await
        } yield assert(b)(equalTo(42))
      },
      testM("par regression") {
        val io = IO.succeed[Int](1).zipPar(IO.succeed[Int](2)).flatMap(t => IO.succeed(t._1 + t._2)).map(_ == 3)
        assertM(io)(isTrue)
      } @@ jvm(nonFlaky),
      testM("par of now values") {
        def countdown(n: Int): UIO[Int] =
          if (n == 0) IO.succeed(0)
          else
            IO.succeed[Int](1).zipPar(IO.succeed[Int](2)).flatMap(t => countdown(n - 1).map(y => t._1 + t._2 + y))

        assertM(countdown(50))(equalTo(150))
      },
      testM("mergeAll") {
        val io = IO.mergeAll(List("a", "aa", "aaa", "aaaa").map(IO.succeed[String](_)))(0) { (b, a) =>
          b + a.length
        }

        assertM(io)(equalTo(10))
      },
      testM("mergeAllEmpty") {
        val io = IO.mergeAll(List.empty[UIO[Int]])(0)(_ + _)
        assertM(io)(equalTo(0))
      },
      testM("reduceAll") {
        val io = IO.reduceAll(IO.effectTotal(1), List(2, 3, 4).map(IO.succeed[Int](_)))(_ + _)
        assertM(io)(equalTo(10))
      },
      testM("reduceAll Empty List") {
        val io = IO.reduceAll(IO.effectTotal(1), Seq.empty)(_ + _)
        assertM(io)(equalTo(1))
      },
      testM("timeout of failure") {
        val io = IO.fail("Uh oh").timeout(1.hour)
        assertM(io.provide(Clock.Live).run)(fails(equalTo("Uh oh")))
      },
      testM("timeout of terminate") {
        val io: ZIO[Clock, Nothing, Option[Int]] = IO.die(ExampleError).timeout(1.hour)
        assertM(io.provide(Clock.Live).run)(dies(equalTo(ExampleError)))
      }
    ),
    suite("RTS option tests")(
      testM("lifting a value to an option") {
        assertM(ZIO.some(42))(isSome(equalTo(42)))
      },
      testM("using the none value") {
        assertM(ZIO.none)(isNone)
      }
    ),
    suite("RTS either helper tests")(
      testM("lifting a value into right") {
        assertM(ZIO.right(42))(isRight(equalTo(42)))
      },
      testM("lifting a value into left") {
        assertM(ZIO.left(42))(isLeft(equalTo(42)))
      }
    ),
    suite("RTS interruption")(
      testM("sync forever is interruptible") {
        val io =
          for {
            f <- IO.effectTotal[Int](1).forever.fork
            _ <- f.interrupt
          } yield true

        assertM(io)(isTrue)
      },
      testM("interrupt of never") {
        val io =
          for {
            fiber <- IO.never.fork
            _     <- fiber.interrupt
          } yield 42

        assertM(io)(equalTo(42))
      },
      testM("asyncPure is interruptible") {
        val io =
          for {
            fiber <- IO.effectAsyncM[Nothing, Nothing](_ => IO.never).fork
            _     <- fiber.interrupt
          } yield 42

        assertM(io)(equalTo(42))
      },
      testM("async is interruptible") {
        val io =
          for {
            fiber <- IO.effectAsync[Nothing, Nothing](_ => ()).fork
            _     <- fiber.interrupt
          } yield 42

        assertM(io)(equalTo(42))
      },
      testM("bracket is uninterruptible") {
        val io =
          for {
            promise <- Promise.make[Nothing, Unit]
            fiber   <- (promise.succeed(()) <* IO.never).bracket(_ => IO.unit)(_ => IO.unit).fork
            res     <- promise.await *> fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
          } yield res

        assertM(io.provide(Clock.Live))(equalTo(42))
      },
      testM("bracketExit is uninterruptible") {
        val io =
          for {
            promise <- Promise.make[Nothing, Unit]
            fiber <- IO
                      .bracketExit(promise.succeed(()) *> IO.never *> IO.succeed(1))(
                        (_, _: Exit[Any, Any]) => IO.unit
                      )(
                        _ => IO.unit: IO[Nothing, Unit]
                      )
                      .fork
            res <- promise.await *> fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
          } yield res

        assertM(io.provide(Clock.Live))(equalTo(42))
      },
      testM("bracket use is interruptible") {
        for {
          fiber <- IO.unit.bracket(_ => IO.unit)(_ => IO.never).fork
          res   <- fiber.interrupt
        } yield assert(res)(isInterrupted)
      },
      testM("bracketExit use is interruptible") {
        for {
          fiber <- IO.bracketExit(IO.unit)((_, _: Exit[Any, Any]) => IO.unit)(_ => IO.never).fork
          res   <- fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
        } yield assert(res)(equalTo(0))
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

        assertM(io.timeoutTo(42)(_ => 0)(1.second))(equalTo(0))
      },
      testM("bracketExit release called on interrupt") {
        for {
          done <- Promise.make[Nothing, Unit]
          fiber <- withLatch { release =>
                    IO.bracketExit(IO.unit)((_, _: Exit[Any, Any]) => done.succeed(()))(
                        _ => release *> IO.never
                      )
                      .fork
                  }

          _ <- fiber.interrupt
          r <- done.await.timeoutTo(42)(_ => 0)(60.second)
        } yield assert(r)(equalTo(0))
      },
      testM("bracketFork acquire returns immediately on interrupt") {
        for {
          p1 <- Promise.make[Nothing, Unit]
          p2 <- Promise.make[Nothing, Int]
          p3 <- Promise.make[Nothing, Unit]
          s <- (p1.succeed(()) *> p2.await)
                .bracketFork(_ => p3.await)(_ => IO.unit)
                .fork
          _   <- p1.await
          res <- s.interrupt
          _   <- p3.succeed(())
        } yield assert(res)(isInterrupted)
      },
      testM("bracketForkExit acquire returns immediately on interrupt") {
        for {
          p1 <- Promise.make[Nothing, Unit]
          p2 <- Promise.make[Nothing, Unit]
          p3 <- Promise.make[Nothing, Unit]
          s <- IO
                .bracketForkExit(p1.succeed(()) *> p2.await)((_, _: Exit[Any, Any]) => p3.await)(
                  _ => IO.unit: IO[Nothing, Unit]
                )
                .fork
          _   <- p1.await
          res <- s.interrupt
          _   <- p3.succeed(())
        } yield assert(res)(isInterrupted)
      },
      testM("bracketFork use is interruptible") {
        for {
          fiber <- IO.unit.bracketFork(_ => IO.unit)(_ => IO.never).fork
          res   <- fiber.interrupt
        } yield assert(res)(isInterrupted)
      },
      testM("bracketForkExit use is interruptible") {
        for {
          fiber <- IO.bracketForkExit(IO.unit)((_, _: Exit[Any, Any]) => IO.unit)(_ => IO.never).fork
          res   <- fiber.interrupt.timeoutTo(42)(_ => 0)(1.second).provide(Clock.Live)
        } yield assert(res)(equalTo(0))
      },
      testM("bracketFork release called on interrupt in separate fiber") {
        val io =
          for {
            p1    <- Promise.make[Nothing, Unit]
            p2    <- Promise.make[Nothing, Unit]
            fiber <- IO.bracketFork(IO.unit)(_ => p2.succeed(()) *> IO.unit)(_ => p1.succeed(()) *> IO.never).fork
            _     <- p1.await
            _     <- fiber.interrupt
            _     <- p2.await
          } yield ()

        assertM(io.timeoutTo(42)(_ => 0)(1.second))(equalTo(0)).provide(Clock.Live)
      } @@ flaky,
      testM("bracketForkExit release called on interrupt in separate fiber") {
        for {
          done <- Promise.make[Nothing, Unit]
          fiber <- withLatch { release =>
                    IO.bracketForkExit(IO.unit)((_, _: Exit[Any, Any]) => done.succeed(()))(
                        _ => release *> IO.never
                      )
                      .fork
                  }

          _ <- fiber.interrupt
          r <- done.await.timeoutTo(42)(_ => 0)(1.second).provide(Clock.Live)
        } yield assert(r)(equalTo(0))
      },
      testM("catchAll + ensuring + interrupt") {
        import zio.CanFail.canFail
        for {
          cont <- Promise.make[Nothing, Unit]
          p1   <- Promise.make[Nothing, Boolean]
          f1   <- (cont.succeed(()) *> IO.never).catchAll(IO.fail).ensuring(p1.succeed(true)).fork
          _    <- cont.await
          _    <- f1.interrupt
          res  <- p1.await
        } yield assert(res)(isTrue)
      },
      testM("finalizer can detect interruption") {
        for {
          p1 <- Promise.make[Nothing, Boolean]
          c  <- Promise.make[Nothing, Unit]
          f1 <- (c.succeed(()) *> IO.never)
                 .ensuring(IO.descriptor.flatMap(d => p1.succeed(d.interruptors.nonEmpty)))
                 .fork
          _   <- c.await
          _   <- f1.interrupt
          res <- p1.await
        } yield assert(res)(isTrue)
      },
      testM("interruption of raced") {
        for {
          ref   <- Ref.make(0)
          cont1 <- Promise.make[Nothing, Unit]
          cont2 <- Promise.make[Nothing, Unit]
          make  = (p: Promise[Nothing, Unit]) => (p.succeed(()) *> IO.never).onInterrupt(ref.update(_ + 1))
          raced <- (make(cont1) race (make(cont2))).fork
          _     <- cont1.await *> cont2.await
          _     <- raced.interrupt
          count <- ref.get
        } yield assert(count)(equalTo(2))
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
        } yield assert(value)(isTrue)
      },
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
        } yield assert(value)(isTrue)
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
        } yield assert(value)(isSome(isLeft(equalTo(Cause.interrupt(selfId)))))
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
        } yield assert(value)(isSome(isInterrupted))
      },
      testM("alternating interruptibility") {
        for {
          counter <- Ref.make(0)
          fiber <- withLatch { release =>
                    ((((release *> ZIO.never.interruptible.run *> counter
                      .update(_ + 1)).uninterruptible).interruptible).run
                      *> counter.update(_ + 1)).uninterruptible.fork
                  }
          _     <- fiber.interrupt
          value <- counter.get
        } yield assert(value)(equalTo(2))
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
        } yield assert(value)(isTrue)
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
        } yield assert(value)(isTrue)
      },
      testM("interruptibleFork returns immediately on interrupt") {
        for {
          p1 <- Promise.make[Nothing, Unit]
          p2 <- Promise.make[Nothing, Int]
          p3 <- Promise.make[Nothing, Unit]
          s <- (p1.succeed(()) *> p2.await)
                .ensuring(p3.await)
                .interruptibleFork
                .fork
          _   <- p1.await
          res <- s.interrupt
          _   <- p3.succeed(())
        } yield assert(res)(isInterrupted)
      },
      testM("interruptibleFork forks execution and interrupts fork") {
        val io =
          for {
            r  <- Ref.make(false)
            p1 <- Promise.make[Nothing, Unit]
            p2 <- Promise.make[Nothing, Int]
            p3 <- Promise.make[Nothing, Unit]
            s <- (p1.succeed(()) *> p2.await)
                  .ensuring(r.set(true) *> clock.sleep(10.millis) *> p3.succeed(()))
                  .interruptibleFork
                  .fork
            _    <- p1.await
            _    <- s.interrupt
            _    <- p3.await
            test <- r.get
          } yield test

        assertM(io.provide(Clock.Live))(isTrue)
      },
      testM("cause reflects interruption") {
        val io =
          for {
            finished <- Ref.make(false)
            fiber <- withLatch { release =>
                      (release *> ZIO.fail("foo")).catchAll(_ => finished.set(true)).fork
                    }
            exit     <- fiber.interrupt
            finished <- finished.get
          } yield exit.interrupted == true || finished == true

        assertM(io)(isTrue)
      } @@ jvm(nonFlaky),
      testM("bracket use inherits interrupt status") {
        val io =
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
          } yield value

        assertM(io.provide(Clock.Live))(isTrue)
      },
      testM("bracket use inherits interrupt status 2") {
        val io =
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
          } yield value

        assertM(io.provide(Clock.Live))(isTrue)
      },
      testM("async can be uninterruptible") {
        val io =
          for {
            ref <- Ref.make(false)
            fiber <- withLatch { release =>
                      (release *> clock.sleep(10.millis) *> ref.set(true).unit).uninterruptible.fork
                    }
            _     <- fiber.interrupt
            value <- ref.get
          } yield value

        assertM(io.provide(Clock.Live))(isTrue)
      }
    ),
    suite("RTS environment")(
      testM("provide is modular") {
        val zio =
          for {
            v1 <- ZIO.environment[Int]
            v2 <- ZIO.environment[Int].provide(2)
            v3 <- ZIO.environment[Int]
          } yield (v1, v2, v3)

        assertM(zio.provide(4))(equalTo((4, 2, 4)))
      },
      testM("provideManaged is modular") {
        def managed(v: Int): ZManaged[Any, Nothing, Int] =
          ZManaged.make(IO.succeed(v))(_ => IO.effectTotal(()))

        val zio =
          for {
            v1 <- ZIO.environment[Int]
            v2 <- ZIO.environment[Int].provideManaged(managed(2))
            v3 <- ZIO.environment[Int]
          } yield (v1, v2, v3)

        assertM(zio.provideManaged(managed(4)))(equalTo((4, 2, 4)))
      },
      testM("effectAsync can use environment") {
        val zio = ZIO.effectAsync[Int, Nothing, Int](cb => cb(ZIO.environment[Int]))
        assertM(zio.provide(10))(equalTo(10))
      }
    ),
    suite("RTS forking inheritability")(
      testM("interruption status is heritable") {
        for {
          latch <- Promise.make[Nothing, Unit]
          ref   <- Ref.make(InterruptStatus.interruptible)
          _     <- ZIO.uninterruptible((ZIO.checkInterruptible(ref.set) *> latch.succeed(())).fork *> latch.await)
          v     <- ref.get
        } yield assert(v)(equalTo(InterruptStatus.uninterruptible))
      },
      testM("executor is heritable") {
        val io =
          for {
            ref  <- Ref.make(Option.empty[internal.Executor])
            exec = internal.Executor.fromExecutionContext(100)(scala.concurrent.ExecutionContext.Implicits.global)
            _ <- withLatch(
                  release => IO.descriptor.map(_.executor).flatMap(e => ref.set(Some(e)) *> release).fork.lock(exec)
                )
            v <- ref.get
          } yield v.contains(exec)

        assertM(io)(isTrue)
      } @@ jvm(nonFlaky(100))
    ),
    suite("someOrFail")(
      testM("extracts the optional value") {
        val task: Task[Int] = UIO(Some(42)).someOrFail(exampleError)
        assertM(task)(equalTo(42))
      },
      testM("fails when given a None") {
        val task: Task[Int] = UIO(Option.empty[Int]).someOrFail(exampleError)
        assertM(task.run)(fails(equalTo(exampleError)))
      }
    ),
    suite("summarized")(
      testM("returns summary and value") {
        for {
          counter   <- Ref.make(0)
          increment = counter.update(_ + 1)
          result    <- increment.summarized((a: Int, b: Int) => (a, b))(increment)
        } yield {
          val ((start, end), value) = result
          assert(start)(equalTo(1)) &&
          assert(value)(equalTo(2)) &&
          assert(end)(equalTo(3))
        }
      }
    ),
    suite("timeoutFork")(
      testM("returns `Right` with the produced value if the effect completes before the timeout elapses") {
        assertM(ZIO.unit.timeoutFork(100.millis))(isRight(isUnit))
      },
      testM("returns `Left` with the interrupting fiber otherwise") {
        for {
          fiber  <- ZIO.never.uninterruptible.timeoutFork(100.millis).fork
          _      <- TestClock.adjust(100.millis)
          result <- fiber.join
        } yield assert(result)(isLeft(anything))
      }
    ),
    suite("unsandbox")(
      testM("unwraps exception") {
        val failure: IO[Cause[Exception], String] = IO.fail(fail(new Exception("fail")))
        val success: IO[Cause[Any], Int]          = IO.succeed(100)
        for {
          message <- failure.unsandbox.foldM(e => IO.succeed(e.getMessage), _ => IO.succeed("unexpected"))
          result  <- success.unsandbox
        } yield assert(message)(equalTo("fail")) && assert(result)(equalTo(100))
      },
      testM("no information is lost during composition") {
        val causes = Gen.causes(Gen.anyString, Gen.throwable)
        def cause[R, E](zio: ZIO[R, E, Nothing]): ZIO[R, Nothing, Cause[E]] =
          zio.foldCauseM(ZIO.succeed, ZIO.fail)
        checkM(causes) { c =>
          for {
            result <- cause(ZIO.halt(c).sandbox.mapErrorCause(e => e.untraced).unsandbox)
          } yield assert(result)(equalTo(c)) &&
            assert(result.prettyPrint)(equalTo(c.prettyPrint))
        }
      }
    ),
    suite("validateM")(
      testM("returns all errors if never valid") {
        val in  = List.fill(10)(0)
        val res = IO.validateM(in)(a => ZIO.fail(a)).flip
        assertM(res)(equalTo(in))
      },
      testM("accumulate errors and ignore successes") {
        import zio.CanFail.canFail
        val in  = List.range(0, 10)
        val res = ZIO.validateM(in)(a => if (a % 2 == 0) ZIO.succeed(a) else ZIO.fail(a))
        assertM(res.flip)(equalTo(List(1, 3, 5, 7, 9)))
      },
      testM("accumulate successes") {
        import zio.CanFail.canFail
        val in  = List.range(0, 10)
        val res = IO.validateM(in)(a => ZIO.succeed(a))
        assertM(res)(equalTo(in))
      }
    ),
    suite("validateMPar")(
      testM("returns all errors if never valid") {
        val in  = List.fill(1000)(0)
        val res = IO.validateMPar(in)(a => ZIO.fail(a)).flip
        assertM(res)(equalTo(in))
      },
      testM("accumulate errors and ignore successes") {
        import zio.CanFail.canFail
        val in  = List.range(0, 10)
        val res = ZIO.validateMPar(in)(a => if (a % 2 == 0) ZIO.succeed(a) else ZIO.fail(a))
        assertM(res.flip)(equalTo(List(1, 3, 5, 7, 9)))
      },
      testM("accumulate successes") {
        import zio.CanFail.canFail
        val in  = List.range(0, 10)
        val res = IO.validateMPar(in)(a => ZIO.succeed(a))
        assertM(res)(equalTo(in))
      }
    ),
    suite("validateFirstM")(
      testM("returns all errors if never valid") {
        val in  = List.fill(10)(0)
        val res = IO.validateFirstM(in)(a => ZIO.fail(a)).flip
        assertM(res)(equalTo(in))
      },
      testM("runs sequentially and short circuits on first success validation") {
        import zio.CanFail.canFail
        val in = List.range(1, 10)
        val f  = (a: Int) => if (a == 6) ZIO.succeed(a) else ZIO.fail(a)

        for {
          counter    <- Ref.make(0)
          res        <- ZIO.validateFirstM(in)(a => counter.update(_ + 1) *> f(a))
          assertions <- assertM(ZIO.succeed(res))(equalTo(6)) && assertM(counter.get)(equalTo(6))
        } yield assertions
      }
    ),
    suite("validateFirstMPar")(
      testM("returns all errors if never valid") {
        val in  = List.fill(1000)(0)
        val res = IO.validateFirstMPar(in)(a => ZIO.fail(a)).flip
        assertM(res)(equalTo(in))
      },
      testM("returns success if valid") {
        import zio.CanFail.canFail
        val in  = List.range(1, 10)
        val f   = (a: Int) => if (a == 6) ZIO.succeed(a) else ZIO.fail(a)
        val res = ZIO.validateFirstMPar(in)(f(_))
        assertM(res)(equalTo(6))
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
          failure   = new Exception("expected")
          _         <- IO.fail(failure).when(false)
          failed    <- IO.fail(failure).when(true).either
        } yield {
          assert(val1)(equalTo(0)) &&
          assert(val2)(equalTo(2)) &&
          assert(failed)(isLeft(equalTo(failure)))
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
        } yield assert(res1)(isFalse) && assert(res2)(isTrue)
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
        } yield assert(res1)(isFalse) && assert(res2)(isTrue)
      }
    ),
    suite("whenM")(
      testM("executes condition effect and correct branch") {
        for {
          effectRef      <- Ref.make(0)
          conditionRef   <- Ref.make(0)
          conditionTrue  = conditionRef.update(_ + 1).map(_ => true)
          conditionFalse = conditionRef.update(_ + 1).map(_ => false)
          _              <- effectRef.set(1).whenM(conditionFalse)
          val1           <- effectRef.get
          conditionVal1  <- conditionRef.get
          _              <- effectRef.set(2).whenM(conditionTrue)
          val2           <- effectRef.get
          conditionVal2  <- conditionRef.get
          failure        = new Exception("expected")
          _              <- IO.fail(failure).whenM(conditionFalse)
          failed         <- IO.fail(failure).whenM(conditionTrue).either
        } yield {
          assert(val1)(equalTo(0)) &&
          assert(conditionVal1)(equalTo(1)) &&
          assert(val2)(equalTo(2)) &&
          assert(conditionVal2)(equalTo(2)) &&
          assert(failed)(isLeft(equalTo(failure)))
        }
      }
    ),
    suite("withFilter")(
      testM("tuple value is extracted correctly from task") {
        for {
          (i, j, k) <- Task((1, 2, 3))
        } yield assert((i, j, k))(equalTo((1, 2, 3)))
      },
      testM("condition in for-comprehension syntax works correctly for task") {
        for {
          n <- Task(3) if n > 0
        } yield assert(n)(equalTo(3))
      },
      testM("unsatisfied condition should fail with NoSuchElementException") {
        val task =
          for {
            n <- Task(3) if n > 10
          } yield n
        assertM(task.run)(fails(isSubtype[NoSuchElementException](anything)))
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
        val expected = "Cannot prove that NoSuchElementException <:< String."
        if (TestVersion.isScala2) assertM(result)(isLeft(equalTo(expected)))
        else assertM(result)(isLeft(anything))
      }
    ),
    suite("zipPar")(
      testM("does not swallow exit causes of loser") {
        ZIO.interrupt.zipPar(IO.interrupt).run.map {
          case Exit.Failure(cause) => assert(cause.interruptors)(not(isEmpty))
          case _                   => assert(false)(isTrue)
        }
      },
      testM("does not report failure when interrupting loser after it succeeded") {
        val io          = ZIO.interrupt.zipPar(IO.succeed(1))
        val interrupted = io.sandbox.either.map(_.left.map(_.interrupted))
        assertM(interrupted)(isLeft(isTrue))
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
              ZIO.succeed(())
            }
      } yield res
    }

  val testString = "supercalifragilisticexpialadocious"

  implicit class ZioOfTestResultOps[R, E](val res: ZIO[R, E, TestResult]) {
    def &&[R1](that: ZIO[R1, E, TestResult]): ZIO[R1 with R, E, TestResult] = res.zipWith(that)(_ && _)
  }

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

  def AsyncUnit[E] = IO.effectAsync[E, Unit](_(IO.unit))
}
