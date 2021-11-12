package zio.test

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.TestUtils._

object SpecSpec extends ZIOBaseSpec {

  val deps: ZDeps[Any, Nothing, Has[Unit]] =
    ZDeps.succeed(())

  def spec: Spec[TestEnvironment, TestFailure[Nothing], TestSuccess] = suite("SpecSpec")(
    suite("provideCustomDeps")(
      test("provides the part of the environment that is not part of the `TestEnvironment`") {
        for {
          _ <- ZIO.environment[TestEnvironment]
          _ <- ZIO.environment[Has[Unit]]
        } yield assertCompletes
      }.provideCustomDeps(deps)
    ),
    suite("provideDeps")(
      test("does not have early initialization issues") {
        for {
          _ <- ZIO.environment[Has[Unit]]
        } yield assertCompletes
      }.provideDeps(deps)
    ),
    suite("provideDepsShared")(
      test("gracefully handles fiber death") {
        val spec = suite("suite")(
          test("test") {
            assert(true)(isTrue)
          }
        ).provideDepsShared(ZDeps.fromZIOMany(ZIO.dieMessage("everybody dies")))
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
          ref    <- Ref.make(true)
          deps    = ZDeps.fromZIO(ref.set(false).as(ref))
          _      <- execute(spec.provideCustomDepsShared(deps) @@ ifEnvSet("foo"))
          result <- ref.get
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
        ).provideDepsShared(ZDeps.succeed(43))
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
    suite("provideSomeDepsShared")(
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
                 ).provideSomeDepsShared[TestEnvironment](deps) @@ nondeterministic
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
        ).provideSomeDepsShared[TestEnvironment](deps) @@ silent
        assertM(succeeded(spec))(isTrue)
      },
      test("releases resources as soon as possible") {
        for {
          ref    <- Ref.make[List[String]](List.empty)
          acquire = ref.update("Acquiring" :: _)
          release = ref.update("Releasing" :: _)
          update  = ZIO.service[Ref[Int]].flatMap(_.updateAndGet(_ + 1))
          deps    = ZManaged.acquireReleaseWith(acquire *> Ref.make(0))(_ => release).toDeps
          spec = suite("spec")(
                   suite("suite1")(
                     test("test1") {
                       assertM(update)(equalTo(1))
                     },
                     test("test2") {
                       assertM(update)(equalTo(2))
                     }
                   ).provideCustomDepsShared(deps),
                   suite("suite2")(
                     test("test1") {
                       assertM(update)(equalTo(1))
                     },
                     test("test2") {
                       assertM(update)(equalTo(2))
                     }
                   ).provideCustomDepsShared(deps)
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
          ).provideCustomDepsShared(ZManaged.acquireReleaseWith(Ref.make(0))(_.set(-1)).toDeps)
        assertM(succeeded(spec))(isTrue)
      }
    )
  )
}
