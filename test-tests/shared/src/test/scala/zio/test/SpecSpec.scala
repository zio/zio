package zio.test

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.TestUtils._

object SpecSpec extends ZIOBaseSpec {

  val provider: ZProvider[Any, Nothing, Unit] =
    ZProvider.succeed(())

  def spec: Spec[TestEnvironment, TestFailure[Nothing], TestSuccess] = suite("SpecSpec")(
    suite("provideCustom")(
      test("provides the part of the environment that is not part of the `TestEnvironment`") {
        for {
          _ <- ZIO.environment[TestEnvironment]
          _ <- ZIO.service[Unit]
        } yield assertCompletes
      }.provideCustom(provider)
    ),
    suite("provide")(
      test("does not have early initialization issues") {
        for {
          _ <- ZIO.service[Unit]
        } yield assertCompletes
      }.provide(provider)
    ),
    suite("provideShared")(
      test("gracefully handles fiber death") {
        val spec = suite("suite")(
          test("test") {
            assert(true)(isTrue)
          }
        ).provideShared(ZProvider.fromZIOEnvironment(ZIO.dieMessage("everybody dies")))
        for {
          _ <- execute(spec)
        } yield assertCompletes
      },
      test("does not acquire the environment if the suite is ignored") {
        val spec = suite("suite")(
          test("test1") {
            assertM(ZIO.service[Ref[Boolean]].flatMap(_.get))(isTrue)
          },
          test("test2") {
            assertM(ZIO.service[Ref[Boolean]].flatMap(_.get))(isTrue)
          }
        )
        for {
          ref     <- Ref.make(true)
          provider = ZProvider.fromZIO(ref.set(false).as(ref))
          _       <- execute(spec.provideCustomShared(provider) @@ ifEnvSet("foo"))
          result  <- ref.get
        } yield assert(result)(isTrue)
      },
      test("is not interfered with by test level failures") {
        val spec = suite("suite")(
          test("test1") {
            assert(1)(Assertion.equalTo(2))
          },
          test("test2") {
            assert(1)(Assertion.equalTo(1))
          },
          test("test3") {
            assertM(ZIO.service[Int])(Assertion.equalTo(42))
          }
        ).provideShared(ZProvider.succeed(43))
        for {
          executedSpec <- execute(spec)
          successes = executedSpec.fold[Int] { c =>
                        c match {
                          case ExecutedSpec.LabeledCase(_, count) => count
                          case ExecutedSpec.MultipleCase(counts)  => counts.sum
                          case ExecutedSpec.TestCase(test, _)     => if (test.isRight) 1 else 0
                        }
                      }
          failures = executedSpec.fold[Int] { c =>
                       c match {
                         case ExecutedSpec.LabeledCase(_, count) => count
                         case ExecutedSpec.MultipleCase(counts)  => counts.sum
                         case ExecutedSpec.TestCase(test, _)     => if (test.isLeft) 1 else 0
                       }
                     }
        } yield assert(successes)(equalTo(1)) && assert(failures)(equalTo(2))
      }
    ),
    suite("provideSomeShared")(
      test("leaves the remainder of the environment") {
        for {
          ref <- Ref.make[Set[Int]](Set.empty)
          spec = suite("suite")(
                   test("test1") {
                     for {
                       n <- Random.nextInt
                       _ <- ref.update(_ + n)
                     } yield assertCompletes
                   },
                   test("test2") {
                     for {
                       n <- Random.nextInt
                       _ <- ref.update(_ + n)
                     } yield assertCompletes
                   },
                   test("test3") {
                     for {
                       n <- Random.nextInt
                       _ <- ref.update(_ + n)
                     } yield assertCompletes
                   }
                 ).provideSomeShared[TestEnvironment](provider) @@ nondeterministic
          _      <- execute(spec)
          result <- ref.get
        } yield assert(result)(hasSize(isGreaterThan(1)))
      },
      test("does not cause the remainder to be shared") {
        val spec = suite("suite")(
          test("test1") {
            for {
              _      <- Console.printLine("Hello, World!")
              output <- TestConsole.output
            } yield assert(output)(equalTo(Vector("Hello, World!\n")))
          },
          test("test2") {
            for {
              _      <- Console.printLine("Hello, World!")
              output <- TestConsole.output
            } yield assert(output)(equalTo(Vector("Hello, World!\n")))
          }
        ).provideSomeShared[TestEnvironment](provider) @@ silent
        assertM(succeeded(spec))(isTrue)
      },
      test("releases resources as soon as possible") {
        for {
          ref     <- Ref.make[List[String]](List.empty)
          acquire  = ref.update("Acquiring" :: _)
          release  = ref.update("Releasing" :: _)
          update   = ZIO.service[Ref[Int]].flatMap(_.updateAndGet(_ + 1))
          provider = ZManaged.acquireReleaseWith(acquire *> Ref.make(0))(_ => release).toProvider
          spec = suite("spec")(
                   suite("suite1")(
                     test("test1") {
                       assertM(update)(equalTo(1))
                     },
                     test("test2") {
                       assertM(update)(equalTo(2))
                     }
                   ).provideCustomShared(provider),
                   suite("suite2")(
                     test("test1") {
                       assertM(update)(equalTo(1))
                     },
                     test("test2") {
                       assertM(update)(equalTo(2))
                     }
                   ).provideCustomShared(provider)
                 ) @@ sequential
          succeeded <- succeeded(spec)
          log       <- ref.get.map(_.reverse)
        } yield assert(succeeded)(isTrue) &&
          assert(log)(equalTo(List("Acquiring", "Releasing", "Acquiring", "Releasing")))
      },
      test("correctly handles nested suites") {
        val spec =
          suite("a")(
            suite("b")(
              suite("c")(
                suite("d") {
                  test("test") {
                    for {
                      n <- ZIO.service[Ref[Int]].flatMap(_.updateAndGet(_ + 1))
                    } yield assert(n)(equalTo(1))
                  }
                }
              )
            )
          ).provideCustomShared(ZManaged.acquireReleaseWith(Ref.make(0))(_.set(-1)).toProvider)
        assertM(succeeded(spec))(isTrue)
      }
    )
  )
}
