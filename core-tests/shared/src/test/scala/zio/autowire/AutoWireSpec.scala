package zio.autowire

import zio._
import zio.internal.macros.StringUtils.StringOps
import zio.test.Assertion.{equalTo, isLeft}
import zio.test.AssertionM.Render.param
import zio.test._
import zio.test.environment.TestConsole

object AutoWireSpec extends ZIOBaseSpec {

  def containsStringWithoutAnsi(element: String): Assertion[String] =
    Assertion.assertion("containsStringWithoutAnsi")(param(element))(_.removingAnsiCodes.contains(element))

  def spec: ZSpec[Environment, Failure] =
    suite("AutoWireSpec")(
      suite("ZIO")(
        suite("`zio.inject`")(
          test("automatically constructs a set of dependencies") {
            val doubleDeps: UDeps[Has[Double]] = ZDeps.succeed(100.1)
            val stringDeps                     = ZDeps.succeed("this string is 28 chars long")
            val intDeps =
              (for {
                str    <- ZIO.service[String]
                double <- ZIO.service[Double]
              } yield str.length + double.toInt).toDeps

            val program: URIO[Has[Int], Int]     = ZIO.service[Int]
            val injected: ZIO[Any, Nothing, Int] = program.inject(intDeps, stringDeps, doubleDeps)

            injected.map(result => assertTrue(result == 128))
          },
          test("automatically memoizes non-val dependencies") {
            def sideEffectingDeps(ref: Ref[Int]): ZDeps[Any, Nothing, Has[String]] =
              ref.update(_ + 1).as("Howdy").toDeps

            val depsA: URDeps[Has[String], Has[Int]]     = ZDeps.succeed(1)
            val depsB: URDeps[Has[String], Has[Boolean]] = ZDeps.succeed(true)

            for {
              ref    <- Ref.make(0)
              _      <- (ZIO.service[Int] <*> ZIO.service[Boolean]).inject(depsA, depsB, sideEffectingDeps(ref))
              result <- ref.get
            } yield assertTrue(result == 1)
          },
          test("reports duplicate dependencies") {
            val checked =
              typeCheck("ZIO.service[Int].inject(ZDeps.succeed(12), ZDeps.succeed(13))")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("Int is provided by multiple dependencies") &&
                  containsStringWithoutAnsi("ZDeps.succeed(12)") &&
                  containsStringWithoutAnsi("ZDeps.succeed(13)")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports unused, extra dependencies") {
            val someDeps: URDeps[Has[Double], Has[String]] = ZDeps.succeed("hello")
            val doubleDeps: UDeps[Has[Double]]             = ZDeps.succeed(1.0)
            val _                                          = (someDeps, doubleDeps)

            val checked =
              typeCheck("ZIO.service[Int].inject(ZDeps.succeed(12), doubleDeps, someDeps)")
            assertM(checked)(isLeft(containsStringWithoutAnsi("unused")))
          } @@ TestAspect.exceptDotty,
          test("reports missing top-level dependencies") {
            val program: URIO[Has[String] with Has[Int], String] = UIO("test")
            val _                                                = program

            val checked = typeCheck("program.inject(ZDeps.succeed(3))")
            assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
          } @@ TestAspect.exceptDotty,
          test("reports multiple missing top-level dependencies") {
            val program: URIO[Has[String] with Has[Int], String] = UIO("test")
            val _                                                = program

            val checked = typeCheck("program.inject()")
            assertM(checked)(
              isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
            )
          } @@ TestAspect.exceptDotty,
          test("reports missing transitive dependencies") {
            import TestDeps._
            val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                                    = program

            val checked = typeCheck("program.inject(OldLady.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestDeps.Fly") &&
                  containsStringWithoutAnsi("for TestDeps.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports nested missing transitive dependencies") {
            import TestDeps._
            val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                                    = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestDeps.Spider") &&
                  containsStringWithoutAnsi("for TestDeps.Fly.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports circular dependencies") {
            import TestDeps._
            val program: URIO[Has[OldLady], Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                                    = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.manEatingFly)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("TestDeps.Fly.manEatingFly") &&
                  containsStringWithoutAnsi("both requires and is transitively required by TestDeps.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty
        ),
        suite("injectCustom")(
          test("automatically constructs a set of dependencies, leaving off ZEnv") {
            val stringDeps = Console.readLine.orDie.toDeps
            val program    = ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:") *>
              program.injectCustom(stringDeps)

            assertM(provided)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("injectSome")(
          test("automatically constructs a set of dependencies, leaving off some environment") {
            val stringDeps = Console.readLine.orDie.toDeps
            val program    = ZIO.service[String].zipWith(Random.nextInt)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:") *>
              program.injectSome[Has[Random] with Has[Console]](stringDeps)

            assertM(provided)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("`ZDeps.wire`")(
          test("automatically constructs a set of dependencies") {
            val doubleDeps                     = ZDeps.succeed(100.1)
            val stringDeps: UDeps[Has[String]] = ZDeps.succeed("this string is 28 chars long")
            val intDeps = (ZIO.service[String] <*> ZIO.service[Double]).map { case (str, double) =>
              str.length + double.toInt
            }.toDeps

            val deps     = ZDeps.wire[Has[Int]](intDeps, stringDeps, doubleDeps)
            val provided = ZIO.service[Int].provideDeps(deps)
            assertM(provided)(equalTo(128))
          },
          test("reports the inclusion of non-Has types within the environment") {
            val checked = typeCheck("""ZDeps.wire[Has[String] with Int with Boolean](ZDeps.succeed("Hello"))""")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("Contains non-Has types:") &&
                  containsStringWithoutAnsi("- Int") &&
                  containsStringWithoutAnsi("- Boolean")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("correctly decomposes nested, aliased intersection types") {
            type StringAlias           = String
            type HasBooleanDoubleAlias = Has[Boolean] with Has[Double]
            type Has2[A, B]            = Has[A] with Has[B]
            type FinalAlias            = Has2[Int, StringAlias] with HasBooleanDoubleAlias
            val _ = ZIO.environment[FinalAlias]

            val checked = typeCheck("ZDeps.wire[FinalAlias]()")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing Int") &&
                  containsStringWithoutAnsi("missing String") &&
                  containsStringWithoutAnsi("missing Boolean") &&
                  containsStringWithoutAnsi("missing Double")
              )
            )
          } @@ TestAspect.exceptDotty
        ),
        suite("`ZDeps.wireSome`")(
          test("automatically constructs a set of dependencies, leaving off some remainder") {
            val stringDeps = ZDeps.succeed("this string is 28 chars long")
            val intDeps = (ZIO.service[String] <*> ZIO.service[Double]).map { case (str, double) =>
              str.length + double.toInt
            }.toDeps
            val program = ZIO.service[Int]

            val deps     = ZDeps.wireSome[Has[Double] with Has[Boolean], Has[Int]](intDeps, stringDeps)
            val provided = program.provideDeps(ZDeps.succeed(true) ++ ZDeps.succeed(100.1) >>> deps)
            assertM(provided)(equalTo(128))
          }
        )
      ),
      suite("ZManaged")(
        suite("`zmanaged.inject`")(
          test("automatically constructs a set of dependencies") {
            val doubleDeps = ZDeps.succeed(100.1)
            val stringDeps = ZDeps.succeed("this string is 28 chars long")
            val intDeps =
              (for {
                str    <- ZManaged.service[String]
                double <- ZManaged.service[Double]
              } yield str.length + double.toInt).toDeps

            val program  = ZManaged.service[Int]
            val provided = program.inject(intDeps, stringDeps, doubleDeps)
            assertM(provided.useNow)(equalTo(128))
          },
          test("automatically memoizes non-val dependencies") {
            def sideEffectingDeps(ref: Ref[Int]): ZDeps[Any, Nothing, Has[String]] =
              ref.update(_ + 1).as("Howdy").toDeps

            val depsA: URDeps[Has[String], Has[Int]]     = ZDeps.succeed(1)
            val depsB: URDeps[Has[String], Has[Boolean]] = ZDeps.succeed(true)

            (for {
              ref    <- Ref.make(0).toManaged
              _      <- (ZManaged.service[Int] <*> ZManaged.service[Boolean]).inject(depsA, depsB, sideEffectingDeps(ref))
              result <- ref.get.toManaged
            } yield assert(result)(equalTo(1))).useNow
          },
          test("reports missing top-level dependencies") {
            val program: ZManaged[Has[String] with Has[Int], Nothing, String] = ZManaged.succeed("test")
            val _                                                             = program

            val checked = typeCheck("program.inject(ZDeps.succeed(3))")
            assertM(checked)(isLeft(containsStringWithoutAnsi("missing String")))
          } @@ TestAspect.exceptDotty,
          test("reports multiple missing top-level dependencies") {
            val program: ZManaged[Has[String] with Has[Int], Nothing, String] = ZManaged.succeed("test")
            val _                                                             = program

            val checked = typeCheck("program.inject()")
            assertM(checked)(
              isLeft(containsStringWithoutAnsi("missing String") && containsStringWithoutAnsi("missing Int"))
            )
          } @@ TestAspect.exceptDotty,
          test("reports missing transitive dependencies") {
            import TestDeps._
            val program: URManaged[Has[OldLady], Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                         = program

            val checked = typeCheck("program.inject(OldLady.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestDeps.Fly") &&
                  containsStringWithoutAnsi("for TestDeps.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports nested missing transitive dependencies") {
            import TestDeps._
            val program: URManaged[Has[OldLady], Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                         = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.live)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("missing zio.autowire.AutoWireSpec.TestDeps.Spider") &&
                  containsStringWithoutAnsi("for TestDeps.Fly.live")
              )
            )
          } @@ TestAspect.exceptDotty,
          test("reports circular dependencies") {
            import TestDeps._
            val program: URManaged[Has[OldLady], Boolean] = ZManaged.service[OldLady].flatMap(_.willDie.toManaged)
            val _                                         = program

            val checked = typeCheck("program.inject(OldLady.live, Fly.manEatingFly)")
            assertM(checked)(
              isLeft(
                containsStringWithoutAnsi("TestDeps.Fly.manEatingFly") &&
                  containsStringWithoutAnsi("both requires and is transitively required by TestDeps.OldLady.live")
              )
            )
          } @@ TestAspect.exceptDotty
        ),
        suite("injectCustom")(
          test("automatically constructs a set of dependencies, leaving off ZEnv") {
            val stringDeps = Console.readLine.orDie.toDeps
            val program    = ZManaged.service[String].zipWith(Random.nextInt.toManaged)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:").toManaged *>
              program.injectCustom(stringDeps)

            assertM(provided.useNow)(equalTo("Your Lucky Number is: -1295463240"))
          }
        ),
        suite("injectSome")(
          test("automatically constructs a set of dependencies, leaving off some environment") {
            val stringDeps = Console.readLine.orDie.toDeps
            val program    = ZManaged.service[String].zipWith(Random.nextInt.toManaged)((str, int) => s"$str $int")
            val provided = TestConsole.feedLines("Your Lucky Number is:").toManaged *>
              program.injectSome[Has[Random] with Has[Console]](stringDeps)

            assertM(provided.useNow)(equalTo("Your Lucky Number is: -1295463240"))
          }
        )
      )
    )

  object TestDeps {
    trait OldLady {
      def willDie: UIO[Boolean]
    }

    object OldLady {
      def live: URDeps[Has[Fly], Has[OldLady]] = ZDeps.succeed(new OldLady {
        override def willDie: UIO[Boolean] = UIO(false)
      })
    }

    trait Fly {}
    object Fly {
      def live: URDeps[Has[Spider], Has[Fly]]          = ZDeps.succeed(new Fly {})
      def manEatingFly: URDeps[Has[OldLady], Has[Fly]] = ZDeps.succeed(new Fly {})
    }

    trait Spider {}
    object Spider {
      def live: UDeps[Has[Spider]] = ZDeps.succeed(new Spider {})
    }
  }
}
