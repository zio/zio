package zio
import zio.Cause.{ die, fail, interrupt }
import zio.ZIOSpecJvmUtils._
import zio.random.Random
import zio.test.Assertion._
import zio.test.environment.TestClock
import zio.test._
import zio.duration._
import zio.syntax._

object ZIOSpecJvm
    extends ZIOBaseSpec(
      suite("ZIOSpecJvm")(
        testM("`IO.foreach` returns the list of results") {
          checkAllM(functionIOGen, listGen) { (f, list) =>
            val res = IO.foreach(list)(f)
            assertM(res, isSubtype[List[Int]](anything) && hasSize(equalTo(100)))
          }
        },
        testM("`IO.foreach` both evaluates effects and returns the list of Ints in the same order") {
          val list = List("1", "2", "3")
          for {
            ref     <- Ref.make(List.empty[String])
            res     <- IO.foreach(list)(x => ref.update(_ :+ x) *> IO.effectTotal[Int](x.toInt))
            effects <- ref.get
          } yield assert(effects, equalTo(list)) && assert(res, equalTo(List(1, 2, 3)))
        },
        testM("`IO.foreach` fails with a NumberFormatException exception") {
          val list = List("1", "h", "3")
          val res  = IO.foreach(list)(x => IO.effectTotal[Int](x.toInt))
          assertM(res.run, dies(isSubtype[NumberFormatException](anything)))
        },
        testM("`IO.foreachPar` returns the list of Ints in the same order") {
          val list = List("1", "2", "3")
          val res  = IO.foreachPar(list)(x => IO.effectTotal[Int](x.toInt))
          assertM(res, equalTo(List(1, 2, 3)))
        },
        testM("For f: Int => String: `IO.bimap(f, identity)` maps an IO[Int, String] into an IO[String, String]") {
          checkM(Gen.anyInt) { i =>
            val res = IO.fail(i).bimap(_.toString, identity).either
            assertM(res, isLeft(equalTo(i.toString)))
          }
        },
        testM("`IO.collectAllPar` returns the list in the same order") {
          val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
          val res  = IO.collectAllPar(list)
          assertM(res, equalTo(List(1, 2, 3)))
        },
        testM("`IO.forkAll` returns the list of Ints in the same order") {
          val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
          val res  = IO.forkAll(list).flatMap[Any, Nothing, List[Int]](_.join)
          assertM(res, equalTo(List(1, 2, 3)))
        },
        testM("`IO.collectAllParN` returns the list of Ints in the same order") {
          val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
          val res  = IO.collectAllParN(2)(list)
          assertM(res, equalTo(List(1, 2, 3)))
        },
        testM("`IO.foreachParN` returns the list of created Strings in the appropriate order") {
          val list = List(1, 2, 3)
          val res  = IO.foreachParN(2)(list)(x => IO.effectTotal(x.toString))
          assertM(res, equalTo(List("1", "2", "3")))
        },
        testM("`IO.foldLeft` with a successful step function sums the list properly") {
          checkM(Gen.listOf(Gen.anyInt)) { l =>
            val res = IO.foldLeft(l)(0)((acc, el) => IO.succeed(acc + el))
            assertM(res, equalTo(l.sum))
          }
        },
        testM("`IO.foldLeft` with a failing step function returns a failed IO") {
          val genNonEmpty = Gen.anyInt.zipWith(Gen.listOf(Gen.anyInt))(_ :: _)
          checkM(genNonEmpty) { l =>
            val res = IO.foldLeft(l)(0)((_, _) => IO.fail("fail"))
            assertM(res.run, fails(equalTo("fail")))
          }
        },
        testM("Check done lifts exit result into IO") {

          val fiberId                       = Fiber.Id(0L, 123L)
          val error                         = exampleError
          val completed                     = Exit.succeed(1)
          val interrupted: Exit[Error, Int] = Exit.interrupt(fiberId)
          val terminated: Exit[Error, Int]  = Exit.die(error)
          val failed: Exit[Error, Int]      = Exit.fail(error)

          assertM(IO.done(completed), equalTo(1)) &&
          assertM(IO.done(interrupted).run, isInterrupted) &&
          assertM(IO.done(terminated).run, dies(equalTo(error))) &&
          assertM(IO.done(failed).run, fails(equalTo(error)))
        },
        testM("Check `catchSomeCause` catches matching cause") {
          ZIO.interrupt.catchSomeCause {
            case c if c.interrupted => ZIO.succeed(true)
          }.sandbox.map(
            assert(_, isTrue)
          )
        },
        testM("Check `catchSomeCause` halts if cause doesn't match") {
          ZIO.fiberId.flatMap { fiberId =>
            ZIO.interrupt.catchSomeCause {
              case c if (!c.interrupted) => ZIO.succeed(true)
            }.sandbox.either.map(
              assert(_, isLeft(equalTo(Cause.interrupt(fiberId))))
            )
          }
        },
        testM("Check `when` executes correct branch only") {
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
            assert(val1, equalTo(0)) &&
            assert(val2, equalTo(2)) &&
            assert(failed, isLeft(equalTo(failure)))
          }
        },
        testM("Check `whenM` executes condition effect and correct branch") {
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
            assert(val1, equalTo(0)) &&
            assert(conditionVal1, equalTo(1)) &&
            assert(val2, equalTo(2)) &&
            assert(conditionVal2, equalTo(2)) &&
            assert(failed, isLeft(equalTo(failure)))
          }
        },
        testM("Check `whenCase` executes correct branch only") {
          val v1: Option[Int] = None
          val v2: Option[Int] = Some(0)
          for {
            ref  <- Ref.make(false)
            _    <- ZIO.whenCase(v1) { case Some(_) => ref.set(true) }
            res1 <- ref.get
            _    <- ZIO.whenCase(v2) { case Some(_) => ref.set(true) }
            res2 <- ref.get
          } yield assert(res1, isFalse) && assert(res2, isTrue)
        },
        testM("Check `whenCaseM` executes condition effect and correct branch") {
          val v1: Option[Int] = None
          val v2: Option[Int] = Some(0)
          for {
            ref  <- Ref.make(false)
            _    <- ZIO.whenCaseM(IO.succeed(v1)) { case Some(_) => ref.set(true) }
            res1 <- ref.get
            _    <- ZIO.whenCaseM(IO.succeed(v2)) { case Some(_) => ref.set(true) }
            res2 <- ref.get
          } yield assert(res1, isFalse) && assert(res2, isTrue)
        },
        testM("Check `unsandbox` unwraps exception") {
          val failure: IO[Cause[Exception], String] = IO.fail(fail(new Exception("fail")))
          val success: IO[Cause[Any], Int]          = IO.succeed(100)
          for {
            message <- failure.unsandbox.foldM(e => IO.succeed(e.getMessage), _ => IO.succeed("unexpected"))
            result  <- success.unsandbox
          } yield assert(message, equalTo("fail")) && assert(result, equalTo(100))
        },
        testM("Check `flatten` method on IO[E, IO[E, String] returns the same IO[E, String] as `IO.flatten` does") {
          checkM(Gen.alphaNumericString) { str =>
            for {
              flatten1 <- IO.effectTotal(IO.effectTotal(str)).flatten
              flatten2 <- IO.flatten(IO.effectTotal(IO.effectTotal(str)))
            } yield assert(flatten1, equalTo(flatten2))
          }
        },
        testM(
          "Check `absolve` method on IO[E, Either[E, A]] returns the same IO[E, Either[E, String]] as `IO.absolve` does"
        ) {
          checkM(Gen.alphaNumericString) { str =>
            val ioEither: UIO[Either[Nothing, String]] = IO.succeed(Right(str))
            for {
              abs1 <- ioEither.absolve
              abs2 <- IO.absolve(ioEither)
            } yield assert(abs1, equalTo(abs2))
          }
        },
        testM("Check non-`memoize`d IO[E, A] returns new instances on repeated calls due to referential transparency") {
          val io = random.nextString(10)
          (io <*> io)
            .map(tuple => assert(tuple._1, not(equalTo(tuple._2))))
        },
        testM("Check `memoize` method on IO[E, A] returns the same instance on repeated calls") {
          val ioMemo = random.nextString(10).memoize
          ioMemo
            .flatMap(io => io <*> io)
            .map(tuple => assert(tuple._1, equalTo(tuple._2)))
        },
        testM("Check `cached` method on IO[E, A] returns new instances after duration") {
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
          } yield assert(a, equalTo(b)) && assert(b, not(equalTo(c))) && assert(c, equalTo(d))
        },
        testM("Check `raceAll` method returns the same IO[E, A] as `IO.raceAll` does") {
          val io  = IO.effectTotal(testString)
          val ios = List.empty[UIO[String]]
          for {
            race1 <- io.raceAll(ios)
            race2 <- IO.raceAll(io, ios)
          } yield assert(race1, equalTo(race2))
        },
        testM("Check `firstSuccessOf` method returns the same IO[E, A] as `IO.firstSuccessOf` does") {
          val io  = IO.effectTotal(testString)
          val ios = List.empty[UIO[String]]
          for {
            race1 <- io.firstSuccessOf(ios)
            race2 <- IO.firstSuccessOf(io, ios)
          } yield assert(race1, equalTo(race2))
        },
        testM("Check `zipPar` method does not swallow exit causes of loser") {
          ZIO.interrupt.zipPar(IO.interrupt).run.map {
            case Exit.Failure(cause) => assert(cause.interruptors, not(isEmpty))
            case _                   => assert(false, isTrue)
          }
        },
        testM("Check `zipPar` method does not report failure when interrupting loser after it succeeded") {
          val io          = ZIO.interrupt.zipPar(IO.succeed(1))
          val interrupted = io.sandbox.either.map(_.left.map(_.interrupted))
          assertM(interrupted, isLeft(isTrue))
        },
        testM("Check `orElse` method does not recover from defects") {
          val ex      = new Exception("Died")
          val fiberId = Fiber.Id(0L, 123L)
          import zio.CanFail.canFail
          for {
            plain <- (ZIO.die(ex) <> IO.unit).run
            both  <- (ZIO.halt(Cause.Both(interrupt(fiberId), die(ex))) <> IO.unit).run
            thn   <- (ZIO.halt(Cause.Then(interrupt(fiberId), die(ex))) <> IO.unit).run
            fail  <- (ZIO.fail(ex) <> IO.unit).run
          } yield assert(plain, dies(equalTo(ex))) &&
            assert(both, dies(equalTo(ex))) &&
            assert(thn, dies(equalTo(ex))) &&
            assert(fail, succeeds(isUnit))
        },
        testM("Check `eventually` method succeeds eventually") {
          def effect(ref: Ref[Int]) =
            ref.get.flatMap(n => if (n < 10) ref.update(_ + 1) *> IO.fail("Ouch") else UIO.succeed(n))

          val test = for {
            ref <- Ref.make(0)
            n   <- effect(ref).eventually
          } yield n

          assertM(test, equalTo(10))
        },
        testM("Check `some` method extracts the value from Some") {
          val task: IO[Option[Throwable], Int] = Task(Some(1)).some
          assertM(task, equalTo(1))
        },
        testM("Check `some` method fails on None") {
          val task: IO[Option[Throwable], Int] = Task(None).some
          assertM(task.run, fails(isNone))
        },
        testM("Check `some` method fails when given an exception") {
          val ex                               = new RuntimeException("Failed Task")
          val task: IO[Option[Throwable], Int] = Task.fail(ex).some
          assertM(task.run, fails(isSome(equalTo(ex))))
        },
        testM("Check `someOrFail` method extracts the optional value") {
          val task: Task[Int] = UIO(Some(42)).someOrFail(exampleError)
          assertM(task, equalTo(42))
        },
        testM("Check `someOrFail` method fails when given a None") {
          val task: Task[Int] = UIO(Option.empty[Int]).someOrFail(exampleError)
          assertM(task.run, fails(equalTo(exampleError)))
        },
        testM("Check `someOrFailException` method extracts the optional value") {
          assertM(ZIO.succeed(Some(42)).someOrFailException, equalTo(42))
        },
        testM("Check `someOrFailException` method fails when given a None") {
          val task = ZIO.succeed(Option.empty[Int]).someOrFailException
          assertM(task.run, fails(isSubtype[NoSuchElementException](anything)))
        },
        testM("Check `none` method on Some fails with None") {
          val task: IO[Option[Throwable], Unit] = Task(Some(1)).none
          assertM(task.run, fails(isNone))
        },
        testM("Check `none` method on None succeeds with ()") {
          val task: IO[Option[Throwable], Unit] = Task(None).none
          assertM(task, isUnit)
        },
        testM("Check `none` method fails with Some(ex) when effect fails with ex") {
          val ex                                = new RuntimeException("Failed Task")
          val task: IO[Option[Throwable], Unit] = Task.fail(ex).none
          assertM(task.run, fails(isSome(equalTo(ex))))
        },
        testM("Check `flattenErrorOption` method fails when given Some error") {
          val task: IO[String, Int] = IO.fail(Some("Error")).flattenErrorOption("Default")
          assertM(task.run, fails(equalTo("Error")))
        },
        testM("Check `flattenErrorOption` method fails with Default when given None error") {
          val task: IO[String, Int] = IO.fail(None).flattenErrorOption("Default")
          assertM(task.run, fails(equalTo("Default")))
        },
        testM("Check `flattenErrorOption` method succeeds when given a value") {
          val task: IO[String, Int] = IO.succeed(1).flattenErrorOption("Default")
          assertM(task, equalTo(1))
        },
        testM("Check `optional` method fails when given Some error") {
          val task: IO[String, Option[Int]] = IO.fail(Some("Error")).optional
          assertM(task.run, fails(equalTo("Error")))
        },
        testM("Check `optional` method succeeds with None given None error") {
          val task: IO[String, Option[Int]] = IO.fail(None).optional
          assertM(task, isNone)
        },
        testM("Check `optional` method succeeds with Some given a value") {
          val task: IO[String, Option[Int]] = IO.succeed(1).optional
          assertM(task, isSome(equalTo(1)))
        },
        testM("Check `foreach_` runs effects in order") {
          val as = List(1, 2, 3, 4, 5)
          for {
            ref <- Ref.make(List.empty[Int])
            _   <- ZIO.foreach_(as)(a => ref.update(_ :+ a))
            rs  <- ref.get
          } yield assert(rs, equalTo(as))
        },
        testM("Check `foreach_` can be run twice") {
          val as = List(1, 2, 3, 4, 5)
          for {
            ref <- Ref.make(0)
            zio = ZIO.foreach_(as)(a => ref.update(_ + a))
            _   <- zio
            _   <- zio
            sum <- ref.get
          } yield assert(sum, equalTo(30))
        },
        testM("Check `foreachPar_` runs all effects") {
          val as = Seq(1, 2, 3, 4, 5)
          for {
            ref <- Ref.make(Seq.empty[Int])
            _   <- ZIO.foreachPar_(as)(a => ref.update(_ :+ a))
            rs  <- ref.get
          } yield assert(rs, hasSize(equalTo(as.length))) &&
            assert(rs.toSet, equalTo(as.toSet))
        },
        testM("Check `foreachParN_` runs all effects") {
          val as = Seq(1, 2, 3, 4, 5)
          for {
            ref <- Ref.make(Seq.empty[Int])
            _   <- ZIO.foreachParN_(2)(as)(a => ref.update(_ :+ a))
            rs  <- ref.get
          } yield assert(rs, hasSize(equalTo(as.length))) &&
            assert(rs.toSet, equalTo(as.toSet))
        },
        testM("Check `filterOrElse` returns checked failure from held value") {
          val goodCase =
            exactlyOnce(0)(_.filterOrElse[Any, String, Int](_ == 0)(a => ZIO.fail(s"$a was not 0"))).sandbox.either

          val badCase =
            exactlyOnce(1)(_.filterOrElse[Any, String, Int](_ == 0)(a => ZIO.fail(s"$a was not 0"))).sandbox.either
              .map(_.left.map(_.failureOrCause))

          assertM(goodCase, isRight(equalTo(0))) &&
          assertM(badCase, isLeft(isLeft(equalTo("1 was not 0"))))
        },
        testM("Check `filterOrElse_` returns checked failure ignoring value") {
          val goodCase =
            exactlyOnce(0)(_.filterOrElse_[Any, String, Int](_ == 0)(ZIO.fail(s"Predicate failed!"))).sandbox.either

          val badCase =
            exactlyOnce(1)(_.filterOrElse_[Any, String, Int](_ == 0)(ZIO.fail(s"Predicate failed!"))).sandbox.either
              .map(_.left.map(_.failureOrCause))

          assertM(goodCase, isRight(equalTo(0))) &&
          assertM(badCase, isLeft(isLeft(equalTo("Predicate failed!"))))
        },
        testM("Check `filterOrFail` returns failure ignoring value") {
          val goodCase =
            exactlyOnce(0)(_.filterOrFail(_ == 0)("Predicate failed!")).sandbox.either

          val badCase =
            exactlyOnce(1)(_.filterOrFail(_ == 0)("Predicate failed!")).sandbox.either
              .map(_.left.map(_.failureOrCause))

          assertM(goodCase, isRight(equalTo(0))) &&
          assertM(badCase, isLeft(isLeft(equalTo("Predicate failed!"))))
        },
        testM("Check `collect` returns failure ignoring value") {
          val goodCase =
            exactlyOnce(0)(_.collect(s"value was not 0")({ case v @ 0 => v })).sandbox.either

          val badCase =
            exactlyOnce(1)(_.collect(s"value was not 0")({ case v @ 0 => v })).sandbox.either
              .map(_.left.map(_.failureOrCause))

          assertM(goodCase, isRight(equalTo(0))) &&
          assertM(badCase, isLeft(isLeft(equalTo("value was not 0"))))
        },
        testM("Check `collectM` returns failure ignoring value") {
          val goodCase =
            exactlyOnce(0)(_.collectM[Any, String, Int]("Predicate failed!")({ case v @ 0 => ZIO.succeed(v) })).sandbox.either

          val partialBadCase =
            exactlyOnce(0)(_.collectM("Predicate failed!")({ case v @ 0 => ZIO.fail("Partial failed!") })).sandbox.either
              .map(_.left.map(_.failureOrCause))

          val badCase =
            exactlyOnce(1)(_.collectM("Predicate failed!")({ case v @ 0 => ZIO.succeed(v) })).sandbox.either
              .map(_.left.map(_.failureOrCause))

          assertM(goodCase, isRight(equalTo(0))) &&
          assertM(partialBadCase, isLeft(isLeft(equalTo("Partial failed!")))) &&
          assertM(badCase, isLeft(isLeft(equalTo("Predicate failed!"))))
        },
        testM("Check `reject` returns failure ignoring value") {
          val goodCase =
            exactlyOnce(0)(_.reject({ case v if v != 0 => "Partial failed!" })).sandbox.either

          val badCase =
            exactlyOnce(1)(_.reject({ case v if v != 0 => "Partial failed!" })).sandbox.either
              .map(_.left.map(_.failureOrCause))

          assertM(goodCase, isRight(equalTo(0))) &&
          assertM(badCase, isLeft(isLeft(equalTo("Partial failed!"))))
        },
        testM("Check `rejectM` returns failure ignoring value") {
          val goodCase =
            exactlyOnce(0)(_.rejectM[Any, String]({ case v if v != 0 => ZIO.succeed("Partial failed!") })).sandbox.either

          val partialBadCase =
            exactlyOnce(1)(_.rejectM({ case v if v != 0 => ZIO.fail("Partial failed!") })).sandbox.either
              .map(_.left.map(_.failureOrCause))

          val badCase =
            exactlyOnce(1)(_.rejectM({ case v if v != 0 => ZIO.fail("Partial failed!") })).sandbox.either
              .map(_.left.map(_.failureOrCause))

          assertM(goodCase, isRight(equalTo(0))) &&
          assertM(partialBadCase, isLeft(isLeft(equalTo("Partial failed!")))) &&
          assertM(badCase, isLeft(isLeft(equalTo("Partial failed!"))))
        },
        testM("Check `foreachParN` works on large lists") {
          val n   = 10
          val seq = 0 to 100000
          val res = IO.foreachParN(n)(seq)(UIO.succeed)
          assertM(res, equalTo(seq))
        },
        testM("Check `foreachParN` runs effects in parallel") {
          val io = for {
            p <- Promise.make[Nothing, Unit]
            _ <- UIO.foreachParN(2)(List(UIO.never, p.succeed(())))(identity).fork
            _ <- p.await
          } yield true
          assertM(io, isTrue)
        },
        testM("Check `foreachParN` propagates error") {
          val ints = List(1, 2, 3, 4, 5, 6)
          val odds = ZIO.foreachParN(4)(ints) { n =>
            if (n % 2 != 0) ZIO.succeed(n) else ZIO.fail("not odd")
          }
          assertM(odds.either, isLeft(equalTo("not odd")))
        },
        testM("Check `foreachParN` interrupts effects on first failure") {
          val actions = List(
            ZIO.never,
            ZIO.succeed(1),
            ZIO.fail("C")
          )
          val io = ZIO.foreachParN(4)(actions)(a => a)
          assertM(io.either, isLeft(equalTo("C")))
        },
        testM("Check `summarized` returns summary and value") {
          for {
            counter   <- Ref.make(0)
            increment = counter.update(_ + 1)
            result    <- increment.summarized((a: Int, b: Int) => (a, b))(increment)
          } yield {
            val ((start, end), value) = result
            assert(start, equalTo(1)) &&
            assert(value, equalTo(2)) &&
            assert(end, equalTo(3))
          }
        },
        suite("Eager - Generate a String:")(
          testM("`.succeed` extension method returns the same UIO[String] as `IO.succeed` does") {
            checkM(Gen.alphaNumericString) { str =>
              for {
                a <- str.succeed
                b <- IO.succeed(str)
              } yield assert(a, equalTo(b))
            }
          },
          testM("`.fail` extension method returns the same IO[String, Nothing] as `IO.fail` does") {
            checkM(Gen.alphaNumericString) { str =>
              for {
                a <- str.fail.either
                b <- IO.fail(str).either
              } yield assert(a, equalTo(b))
            }
          },
          testM("`.ensure` extension method returns the same IO[E, Option[A]] => IO[E, A] as `IO.ensure` does") {
            checkM(Gen.alphaNumericString) { str =>
              val ioSome = IO.succeed(Some(42))
              for {
                a <- str.require(ioSome)
                b <- IO.require(str)(ioSome)
              } yield assert(a, equalTo(b))
            }
          }
        ),
        suite("Lazy - Generate a String:")(
          testM("`.effect` extension method returns the same UIO[String] as `IO.effect` does") {
            checkM(Gen.alphaNumericString) { str =>
              for {
                a <- str.effect
                b <- IO.effectTotal(str)
              } yield assert(a, equalTo(b))
            }
          },
          testM("`.effect` extension method returns the same Task[String] as `IO.effect` does") {
            checkM(Gen.alphaNumericString) { str =>
              for {
                a <- str.effect
                b <- IO.effect(str)
              } yield assert(a, equalTo(b))
            }
          },
          testM(
            "`.effect` extension method returns the same PartialFunction[Throwable, E] => IO[E, A] as `IO.effect` does"
          ) {
            checkM(Gen.alphaNumericString) { str =>
              val partial: PartialFunction[Throwable, Int] = { case _: Throwable => 42 }
              for {
                a <- str.effect.refineOrDie(partial)
                b <- IO.effect(str).refineOrDie(partial)
              } yield assert(a, equalTo(b))
            }
          }
        ),
        suite("Generate an Iterable of Char:")(
          testM("`.mergeAll` extension method returns the same IO[E, B] as `IO.mergeAll` does") {
            val TestData                     = testString.toList
            val ios                          = TestData.map(IO.succeed)
            val zero                         = List.empty[Char]
            def merger[A](as: List[A], a: A) = a :: as
            for {
              merged1 <- ios.mergeAll(zero)(merger)
              merged2 <- IO.mergeAll(ios)(zero)(merger)
            } yield assert(merged1, equalTo(merged2))
          },
          testM("`.parAll` extension method returns the same IO[E, List[A]] as `IO.parAll`") {
            val TestData = testString.toList
            val ios      = TestData.map(IO.effectTotal(_))
            for {
              parAll1 <- ios.collectAllPar
              parAll2 <- IO.collectAllPar(ios)
            } yield assert(parAll1, equalTo(parAll2))
          },
          testM("`.forkAll` extension method returns the same UIO[Fiber[E, List[A]]] as `IO.forkAll` does") {
            val TestData                        = testString.toList
            val ios: Iterable[IO[String, Char]] = TestData.map(IO.effectTotal(_))
            for {
              f1       <- ios.forkAll
              forkAll1 <- f1.join
              f2       <- IO.forkAll(ios)
              forkAll2 <- f2.join
            } yield assert(forkAll1, equalTo(forkAll2))
          },
          testM("`.sequence` extension method returns the same IO[E, List[A]] as `IO.sequence` does") {
            val TestData = testString.toList
            val ios      = TestData.map(IO.effectTotal(_))
            for {
              sequence1 <- ios.collectAll
              sequence2 <- IO.collectAll(ios)
            } yield assert(sequence1, equalTo(sequence2))
          }
        ),
        testM(
          "Generate a Tuple2 of (Int, String): " +
            "`.map2` extension method should combine them to an IO[E, Z] with a function (A, B) => Z"
        ) {
          checkM(Gen.anyInt, Gen.alphaNumericString) { (int: Int, str: String) =>
            def f(i: Int, s: String): String = i.toString + s
            val ios                          = (IO.succeed(int), IO.succeed(str))
            assertM(ios.map2[String](f), equalTo(f(int, str)))
          }
        },
        testM(
          "Generate a Tuple3 of (Int, String, String): " +
            "`.map3` extension method should combine them to an IO[E, Z] with a function (A, B, C) => Z"
        ) {
          checkM(Gen.anyInt, Gen.alphaNumericString, Gen.alphaNumericString) { (int: Int, str1: String, str2: String) =>
            def f(i: Int, s1: String, s2: String): String = i.toString + s1 + s2
            val ios                                       = (IO.succeed(int), IO.succeed(str1), IO.succeed(str2))
            assertM(ios.map3[String](f), equalTo(f(int, str1, str2)))
          }
        },
        testM(
          "Generate a Tuple4 of (Int, String, String, String): " +
            "`.map4` extension method should combine them to an IO[E, C] with a function (A, B, C, D) => Z"
        ) {
          checkM(Gen.anyInt, Gen.alphaNumericString, Gen.alphaNumericString, Gen.alphaNumericString) {
            (int: Int, str1: String, str2: String, str3: String) =>
              def f(i: Int, s1: String, s2: String, s3: String): String = i.toString + s1 + s2 + s3
              val ios                                                   = (IO.succeed(int), IO.succeed(str1), IO.succeed(str2), IO.succeed(str3))
              assertM(ios.map4[String](f), equalTo(f(int, str1, str2, str3)))
          }
        }
      )
    )

object ZIOSpecJvmUtils {

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
}
